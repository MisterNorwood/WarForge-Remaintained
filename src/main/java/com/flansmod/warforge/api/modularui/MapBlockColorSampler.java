package com.flansmod.warforge.api.modularui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.minecraft.core.registries.BuiltInRegistries;

import com.flansmod.warforge.common.WarForgeMod;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-block map colors sampled the way JourneyMap 1.20.1 does, ported from its
 * {@code VanillaBlockSpriteProxy} / {@code ColorManager} / {@code VanillaBlockColorProxy}.
 *
 * <ol>
 *     <li><b>Base color</b> &mdash; mean RGB of the alpha&gt;0 texels of every sprite in the block's
 *     baked model. Quads are gathered across <em>all</em> chunk render layers (solid, cutout,
 *     translucent), because layer-aware models return nothing for a {@code null} render type &mdash;
 *     which is why leaves and other non-solid blocks previously sampled blank. Sprites whose in-memory
 *     atlas image was released fall back to reading their source PNG, like JourneyMap.</li>
 *     <li><b>Tint</b> &mdash; grass / foliage / water use the biome's own colors directly (no neighbour
 *     blending, so it is safe on the off-thread map daemon); everything else falls back to vanilla
 *     {@code BlockColors}. Foliage is darkened &times;0.8 and non-water fluids are multiplied by their
 *     fluid tint, just like JourneyMap.</li>
 * </ol>
 *
 * <p>Anything without a usable texture degrades to the vanilla map color, and every sample is wrapped
 * so a misbehaving modded model never kills the map thread.
 */
public final class MapBlockColorSampler {

    /** Untinted sprite-average color per block state (0xRRGGBB), or {@link #NO_COLOR}. */
    private static final ConcurrentHashMap<BlockState, Integer> BASE_COLOR_CACHE = new ConcurrentHashMap<>();
    /** Cached sprite-set averages keyed by their sorted icon names, mirroring JourneyMap's iconColorCache. */
    private static final ConcurrentHashMap<String, Integer> ICON_COLOR_CACHE = new ConcurrentHashMap<>();
    private static final int NO_COLOR = Integer.MIN_VALUE;

    /**
     * When {@code true}, the first time each distinct block id is sampled a full breakdown
     * (sprites collected, raw sprite average, tint multiplier, final color) is logged so the
     * color pipeline can be diagnosed. Bounded to one line per block id, so it never floods.
     * Toggle off once the map colors are confirmed correct.
     */
    public static volatile boolean DEBUG_LOGGING = true;
    /** Block ids already logged this session, so the verbose breakdown prints at most once each. */
    private static final Set<String> LOGGED_BLOCKS = ConcurrentHashMap.newKeySet();

    private MapBlockColorSampler() {
    }

    /** Final, biome-tinted map color (0xRRGGBB) for {@code state} at {@code pos}. Never throws. */
    public static int sampleColor(Level world, BlockState state, BlockPos pos) {
        try {
            int base = deriveBaseColor(state);
            int tinted = applyTint(world, state, pos, base);
            if (DEBUG_LOGGING) {
                logBreakdown(world, state, pos, base, tinted);
            }
            return tinted;
        } catch (Throwable t) {
            if (DEBUG_LOGGING) {
                WarForgeMod.LOGGER.warn("[MapColor] sampleColor threw for {} at {} -> vanilla map color",
                        blockId(state), pos, t);
            }
            return fallbackMapColor(world, state, pos);
        }
    }

    /** One-shot per-block-id dump of every decision the color pipeline made for this state. */
    private static void logBreakdown(Level world, BlockState state, BlockPos pos, int base, int tinted) {
        String id = blockId(state);
        if (!LOGGED_BLOCKS.add(id)) {
            return;
        }
        String spriteNames;
        int rawSprite;
        try {
            Collection<TextureAtlasSprite> sprites = getSprites(state);
            spriteNames = sprites.isEmpty() ? "<none>" : iconKey(sprites);
            rawSprite = getBaseColor(state);
        } catch (Throwable t) {
            spriteNames = "<error: " + t + ">";
            rawSprite = NO_COLOR;
        }
        int mult;
        try {
            mult = colorMultiplier(world, state, pos);
        } catch (Throwable t) {
            mult = -2;
        }
        WarForgeMod.LOGGER.info(
                "[MapColor] {} renderShape={} grass={} foliage={} fluid={} water={} | sprites=[{}] "
                        + "rawSprite={} base={} mult={} final={}",
                id, state.getRenderShape(), isGrass(state), isFoliage(state), isFluid(state), isWater(state),
                spriteNames, hex(rawSprite), hex(base), hex(mult), hex(tinted));
    }

    private static String blockId(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null ? id.toString() : state.getBlock().toString();
    }

    private static String hex(int color) {
        if (color == NO_COLOR) {
            return "NO_COLOR";
        }
        if (color == -2) {
            return "<error>";
        }
        return String.format("#%06X", color & 0x00FFFFFF);
    }

    // --- base color (JourneyMap deriveBlockColor / getSpriteColor) ---

    private static int deriveBaseColor(BlockState state) {
        int sprite = getBaseColor(state);
        if (sprite != NO_COLOR) {
            return sprite;
        }
        // Liquids whose still texture could not be resolved use JourneyMap's default fluid grey.
        if (state.getBlock() instanceof LiquidBlock) {
            return DEFAULT_FLUID_COLOR;
        }
        return materialColor(state);
    }

    private static int getBaseColor(BlockState state) {
        Integer cached = BASE_COLOR_CACHE.get(state);
        if (cached != null) {
            return cached;
        }
        int color = averageColor(getSprites(state));
        BASE_COLOR_CACHE.put(state, color);
        return color;
    }

    // --- sprite collection (JourneyMap VanillaBlockSpriteProxy.getSprites) ---

    private static Collection<TextureAtlasSprite> getSprites(BlockState state) {
        Minecraft mc = Minecraft.getInstance();

        // Liquids have no model quads; sample the fluid's still texture directly.
        if (state.getBlock() instanceof LiquidBlock) {
            TextureAtlasSprite still = fluidStillSprite(mc, state);
            return still != null ? Collections.singletonList(still) : Collections.emptyList();
        }

        // Double plants only carry their distinctive texture on the upper half.
        if (state.hasProperty(DoublePlantBlock.HALF)) {
            state = state.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
        }
        if (state.getRenderShape() != RenderShape.MODEL) {
            return Collections.emptyList();
        }

        BakedModel model = mc.getBlockRenderer().getBlockModelShaper().getBlockModel(state);
        if (model == null) {
            return Collections.emptyList();
        }

        Map<String, TextureAtlasSprite> sprites = new LinkedHashMap<>();
        outer:
        for (BlockState variant : new BlockState[]{state, null}) {
            for (Direction facing : new Direction[]{Direction.UP, null}) {
                if (collectSprites(model, variant, facing, sprites)) {
                    break outer;
                }
            }
        }

        if (sprites.isEmpty()) {
            TextureAtlasSprite particle = model.getParticleIcon();
            if (isUsable(particle)) {
                sprites.put(particle.contents().name().toString(), particle);
            }
        }
        return sprites.values();
    }

    private static boolean collectSprites(BakedModel model, BlockState state, Direction facing, Map<String, TextureAtlasSprite> out) {
        boolean added = false;
        RandomSource rand = RandomSource.create();
        for (RenderType type : RenderType.chunkBufferLayers()) {
            List<BakedQuad> quads;
            try {
                quads = model.getQuads(state, facing, rand, ModelData.EMPTY, type);
            } catch (Throwable ignored) {
                continue;
            }
            if (quads == null || quads.isEmpty()) {
                continue;
            }
            for (BakedQuad quad : quads) {
                TextureAtlasSprite sprite = quad.getSprite();
                if (!isUsable(sprite)) {
                    continue;
                }
                String name = sprite.contents().name().toString();
                if (out.putIfAbsent(name, sprite) == null) {
                    added = true;
                }
            }
        }
        return added;
    }

    private static TextureAtlasSprite fluidStillSprite(Minecraft mc, BlockState state) {
        FluidState fluidState = state.getFluidState();
        if (fluidState.isEmpty()) {
            return null;
        }
        ResourceLocation still = IClientFluidTypeExtensions.of(fluidState).getStillTexture();
        if (still == null) {
            return null;
        }
        TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(still);
        return isUsable(sprite) ? sprite : null;
    }

    // --- averaging (JourneyMap ColorManager.calculateAverageColor) ---

    private static int averageColor(Collection<TextureAtlasSprite> sprites) {
        if (sprites == null || sprites.isEmpty()) {
            return NO_COLOR;
        }
        String key = iconKey(sprites);
        Integer cached = ICON_COLOR_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        long r = 0L;
        long g = 0L;
        long b = 0L;
        long count = 0L;
        for (TextureAtlasSprite sprite : sprites) {
            // Prefer the retained atlas image; if it was released after upload, read the source PNG.
            NativeImage atlasImage = spriteImage(sprite);
            NativeImage image = atlasImage != null ? atlasImage : loadSpritePng(sprite);
            if (image == null) {
                continue;
            }
            boolean owned = atlasImage == null; // PNG-loaded images are ours to free
            try {
                int width = image.getWidth();
                int height = image.getHeight();
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int abgr = image.getPixelRGBA(x, y);
                        if (((abgr >>> 24) & 0xFF) > 0) {
                            r += abgr & 0xFF;
                            g += (abgr >> 8) & 0xFF;
                            b += (abgr >> 16) & 0xFF;
                            count++;
                        }
                    }
                }
            } finally {
                if (owned) {
                    image.close();
                }
            }
        }

        int result = count == 0L
                ? NO_COLOR
                : (((int) (r / count) << 16) | ((int) (g / count) << 8) | (int) (b / count));
        ICON_COLOR_CACHE.put(key, result);
        return result;
    }

    private static String iconKey(Collection<TextureAtlasSprite> sprites) {
        List<String> names = new ArrayList<>(sprites.size());
        for (TextureAtlasSprite sprite : sprites) {
            names.add(sprite.contents().name().toString());
        }
        Collections.sort(names);
        return String.join(",", names);
    }

    /** The sprite's retained atlas image, or {@code null} if it was released after upload. */
    private static NativeImage spriteImage(TextureAtlasSprite sprite) {
        try {
            SpriteContents contents = sprite.contents();
            NativeImage image = contents.getOriginalImage();
            return (image != null && image.getWidth() > 0) ? image : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Reads the sprite's source PNG when its atlas image is gone, mirroring JourneyMap's fallback. */
    private static NativeImage loadSpritePng(TextureAtlasSprite sprite) {
        try {
            ResourceLocation icon = sprite.contents().name();
            ResourceLocation file = ResourceLocation.fromNamespaceAndPath(icon.getNamespace(), "textures/" + icon.getPath() + ".png");
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
            if (resource.isEmpty()) {
                return null;
            }
            try (InputStream in = resource.get().open()) {
                return NativeImage.read(in);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    // --- tint (JourneyMap VanillaBlockColorProxy.getBlockColor / getColorMultiplier) ---

    private static int applyTint(Level world, BlockState state, BlockPos pos, int base) {
        if (isFoliage(state)) {
            return multiply(adjustBrightness(base, 0.8f), colorMultiplier(world, state, pos));
        }
        if (isFluid(state) && !isWater(state)) {
            return multiply(base, fluidTint(state));
        }
        return multiply(base, colorMultiplier(world, state, pos));
    }

    private static int colorMultiplier(Level world, BlockState state, BlockPos pos) {
        if (isGrass(state)) {
            return world.getBiome(pos).value().getGrassColor(pos.getX(), pos.getZ()) & 0x00FFFFFF;
        }
        if (isFoliage(state)) {
            return world.getBiome(pos).value().getFoliageColor() & 0x00FFFFFF;
        }
        if (isWater(state)) {
            return world.getBiome(pos).value().getWaterColor() & 0x00FFFFFF;
        }
        int color = Minecraft.getInstance().getBlockColors().getColor(state, world, pos, 0);
        return color == -1 ? 0xFFFFFF : (color & 0x00FFFFFF);
    }

    private static int fluidTint(BlockState state) {
        FluidState fluidState = state.getFluidState();
        if (fluidState.isEmpty()) {
            return 0xFFFFFF;
        }
        return IClientFluidTypeExtensions.of(fluidState).getTintColor() & 0x00FFFFFF;
    }

    // --- classification (JourneyMap VanillaBlockHandler flags) ---

    private static boolean isGrass(BlockState state) {
        Block block = state.getBlock();
        return block instanceof GrassBlock || block == Blocks.SHORT_GRASS;
    }

    private static boolean isFoliage(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof LeavesBlock || block instanceof VineBlock) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id != null && id.getPath().toLowerCase(Locale.ROOT).contains("leaves");
    }

    private static boolean isFluid(BlockState state) {
        return state.getBlock() instanceof LiquidBlock;
    }

    private static boolean isWater(BlockState state) {
        return state.getBlock() instanceof LiquidBlock && state.getFluidState().is(FluidTags.WATER);
    }

    // --- fallbacks ---

    private static int materialColor(BlockState state) {
        try {
            return state.getBlock().defaultMapColor().col & 0x00FFFFFF;
        } catch (Throwable ignored) {
            return 0x000000;
        }
    }

    private static int fallbackMapColor(Level world, BlockState state, BlockPos pos) {
        try {
            return state.getMapColor(world, pos).col & 0x00FFFFFF;
        } catch (Throwable ignored) {
            return materialColor(state);
        }
    }

    private static boolean isUsable(TextureAtlasSprite sprite) {
        return sprite != null
                && sprite.contents() != null
                && !MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name());
    }

    /** Per-channel multiply in normalized space (out = c1 * c2 / 255), ignoring alpha. */
    private static int multiply(int c1, int c2) {
        int r = Math.round(((c1 >> 16) & 0xFF) * (((c2 >> 16) & 0xFF) / 255.0F));
        int g = Math.round(((c1 >> 8) & 0xFF) * (((c2 >> 8) & 0xFF) / 255.0F));
        int b = Math.round((c1 & 0xFF) * ((c2 & 0xFF) / 255.0F));
        return (r << 16) | (g << 8) | b;
    }

    private static int adjustBrightness(int color, float factor) {
        int r = clamp(Math.round(((color >> 16) & 0xFF) * factor));
        int g = clamp(Math.round(((color >> 8) & 0xFF) * factor));
        int b = clamp(Math.round((color & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }

    /** JourneyMap's default grey for fluids whose still texture cannot be resolved. */
    private static final int DEFAULT_FLUID_COLOR = 0x00BCBCBC;
}

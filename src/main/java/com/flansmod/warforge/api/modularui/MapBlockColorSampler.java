package com.flansmod.warforge.api.modularui;

import com.flansmod.warforge.common.WarForgeMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockVine;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.IFluidBlock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Samples per-block map colors the way JourneyMap does, instead of the flat ~64-entry vanilla
 * {@link net.minecraft.block.material.MapColor} palette.
 *
 * <p>Mirrors JourneyMap's {@code VanillaBlockColorProxy}:
 * <ol>
 *     <li><b>Base color</b> &mdash; the average RGB of the block's texture-sprite texels (only
 *     texels with alpha &gt; 0), taken across every sprite in the block's baked model. This is
 *     expensive, so it is computed once per {@link IBlockState} and cached.</li>
 *     <li><b>Tint</b> &mdash; grass / foliage / water use the biome's colors directly, everything
 *     else falls back to vanilla {@link net.minecraft.client.renderer.color.BlockColors}.</li>
 *     <li>Foliage base colors are darkened (&times;0.8) and fluids are multiplied by their fluid
 *     color, just like JourneyMap.</li>
 * </ol>
 *
 * <p>Anything that has no usable texture (invisible blocks, sprites whose frame data was released)
 * falls back to the vanilla map color so the map never gets holes. All sampling is wrapped so a
 * misbehaving modded model degrades to the vanilla color rather than killing the map thread.
 */
public final class MapBlockColorSampler {

    /** Untinted sprite-average color per block state, in 0xRRGGBB, or {@link #NO_COLOR} if none. */
    private static final ConcurrentHashMap<IBlockState, Integer> BASE_COLOR_CACHE = new ConcurrentHashMap<IBlockState, Integer>();
    /** Cached sprite-set averages keyed by their sorted icon names. */
    private static final ConcurrentHashMap<String, Integer> ICON_COLOR_CACHE = new ConcurrentHashMap<String, Integer>();
    private static final int NO_COLOR = Integer.MIN_VALUE;

    /** JourneyMap's default grey for fluids whose still texture cannot be resolved. */
    private static final int DEFAULT_FLUID_COLOR = 0x00BCBCBC;

    public static volatile boolean DEBUG_LOGGING = false;
    private static final Set<String> LOGGED_BLOCKS = ConcurrentHashMap.newKeySet();

    private MapBlockColorSampler() {
    }

    /**
     * Final, biome-tinted map color (0xRRGGBB) for {@code state} at {@code pos}. Never throws.
     */
    public static int sampleColor(World world, IBlockState state, BlockPos pos) {
        try {
            int base = deriveBaseColor(state);
            return applyTint(world, state, pos, base);
        } catch (Throwable t) {
            if (DEBUG_LOGGING) {
                WarForgeMod.LOGGER.warn("[MapColor] sampleColor threw for {} at {} -> vanilla map color",
                        blockId(state), pos, t);
            }
            return fallbackMapColor(world, state, pos);
        }
    }

    private static int deriveBaseColor(IBlockState state) {
        int sprite = getBaseColor(state);
        if (sprite != NO_COLOR) {
            return sprite;
        }
        Block block = state.getBlock();
        if (block instanceof IFluidBlock || state.getMaterial() == Material.WATER || state.getMaterial() == Material.LAVA) {
            return DEFAULT_FLUID_COLOR;
        }
        return materialColor(state);
    }

    private static int materialColor(IBlockState state) {
        try {
            return state.getMapColor(null, null).colorValue;
        } catch (Throwable ignored) {
            return 0x000000;
        }
    }

    private static int getBaseColor(IBlockState state) {
        Integer cached = BASE_COLOR_CACHE.get(state);
        if (cached != null) {
            return cached;
        }
        int color = computeSpriteAverage(state);
        BASE_COLOR_CACHE.put(state, color);
        return color;
    }

    private static int computeSpriteAverage(IBlockState state) {
        Minecraft mc = Minecraft.getMinecraft();

        TextureAtlasSprite fluidSprite = fluidStillSprite(mc, state);
        if (fluidSprite != null) {
            return averageColor(Collections.singletonList(fluidSprite));
        }

        if (state.getRenderType() == EnumBlockRenderType.INVISIBLE) {
            return NO_COLOR;
        }
        BlockRendererDispatcher dispatcher = mc.getBlockRendererDispatcher();
        if (dispatcher == null) {
            return NO_COLOR;
        }
        IBakedModel model = dispatcher.getModelForState(state);
        if (model == null) {
            return NO_COLOR;
        }

        Set<TextureAtlasSprite> sprites = new LinkedHashSet<TextureAtlasSprite>();
        try {
            collectSprites(model.getQuads(state, EnumFacing.UP, 0L), sprites);
            if (sprites.isEmpty()) {
                collectSprites(model.getQuads(state, null, 0L), sprites);
            }
        } catch (Throwable ignored) {
        }
        if (sprites.isEmpty()) {
            TextureAtlasSprite particle = model.getParticleTexture();
            if (isUsable(particle)) {
                sprites.add(particle);
            }
        }
        return averageColor(sprites);
    }

    /** Resolves the still texture for any liquid: modded Forge fluids, plus vanilla water/lava. */
    private static TextureAtlasSprite fluidStillSprite(Minecraft mc, IBlockState state) {
        String spriteName = null;
        Fluid fluid = FluidRegistry.lookupFluidForBlock(state.getBlock());
        if (fluid != null) {
            ResourceLocation still = fluid.getStill();
            if (still != null) {
                spriteName = still.toString();
            }
        }
        if (spriteName == null) {
            Material material = state.getMaterial();
            if (material == Material.WATER) {
                spriteName = "minecraft:blocks/water_still";
            } else if (material == Material.LAVA) {
                spriteName = "minecraft:blocks/lava_still";
            }
        }
        if (spriteName == null) {
            return null;
        }
        TextureAtlasSprite sprite = mc.getTextureMapBlocks().getAtlasSprite(spriteName);
        return isUsable(sprite) ? sprite : null;
    }

    private static void collectSprites(List<BakedQuad> quads, Set<TextureAtlasSprite> out) {
        if (quads == null) {
            return;
        }
        for (BakedQuad quad : quads) {
            TextureAtlasSprite sprite = quad.getSprite();
            if (isUsable(sprite)) {
                out.add(sprite);
            }
        }
    }

    /** Mean RGB over every alpha &gt; 0 texel of the given sprites, or {@link #NO_COLOR}. */
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
            if (sprite == null || sprite.getFrameCount() <= 0) {
                continue;
            }
            int[][] frames;
            try {
                frames = sprite.getFrameTextureData(0);
            } catch (Throwable ignored) {
                continue;
            }
            if (frames == null || frames.length == 0 || frames[0] == null) {
                continue;
            }
            for (int pixel : frames[0]) {
                int alpha = (pixel >>> 24) & 0xFF;
                if (alpha > 0) {
                    r += (pixel >> 16) & 0xFF;
                    g += (pixel >> 8) & 0xFF;
                    b += pixel & 0xFF;
                    count++;
                }
            }
        }
        int result = count == 0L ? NO_COLOR : ((int) (r / count) << 16) | ((int) (g / count) << 8) | (int) (b / count);
        ICON_COLOR_CACHE.put(key, result);
        return result;
    }

    private static String iconKey(Collection<TextureAtlasSprite> sprites) {
        List<String> names = new ArrayList<String>(sprites.size());
        for (TextureAtlasSprite sprite : sprites) {
            names.add(sprite.getIconName());
        }
        Collections.sort(names);
        return String.join(",", names);
    }

    /** Filters out null and the magenta/black missing-texture sprite. */
    private static boolean isUsable(TextureAtlasSprite sprite) {
        return sprite != null
                && sprite.getIconName() != null
                && !"missingno".equals(sprite.getIconName());
    }

    private static int applyTint(World world, IBlockState state, BlockPos pos, int base) {
        Block block = state.getBlock();
        if (isFoliage(block)) {
            return multiply(adjustBrightness(base, 0.8f), getTint(world, state, pos));
        }
        if (block instanceof IFluidBlock || state.getMaterial() == Material.WATER || state.getMaterial() == Material.LAVA) {
            if (!(state.getMaterial() == Material.WATER)) {
                Fluid fluid = (block instanceof IFluidBlock) ? ((IFluidBlock) block).getFluid() : null;
                if (fluid != null) {
                    return multiply(base, fluid.getColor() & 0x00FFFFFF);
                }
            }
        }
        return multiply(base, getTint(world, state, pos));
    }

    /** Biome tint multiplier (0xRRGGBB), mirroring JourneyMap's getColorMultiplier. */
    private static int getTint(World world, IBlockState state, BlockPos pos) {
        Block block = state.getBlock();
        Biome biome = world.getBiome(pos);
        if (isGrass(block, state)) {
            return biome.getGrassColorAtPos(pos);
        }
        if (isFoliage(block)) {
            return biome.getFoliageColorAtPos(pos);
        }
        if (isWater(state)) {
            return biome.getWaterColorMultiplier();
        }
        return Minecraft.getMinecraft().getBlockColors()
                .colorMultiplier(state, world, pos, block.getRenderLayer().ordinal());
    }

    private static int fallbackMapColor(World world, IBlockState state, BlockPos pos) {
        try {
            return state.getMapColor(world, pos).colorValue;
        } catch (Throwable ignored) {
            return 0x000000;
        }
    }

    private static boolean isGrass(Block block, IBlockState state) {
        return block instanceof BlockGrass || state.getMaterial() == Material.GRASS;
    }

    private static boolean isFoliage(Block block) {
        if (block instanceof BlockLeaves || block instanceof BlockVine) {
            return true;
        }
        ResourceLocation id = Block.REGISTRY.getNameForObject(block);
        return id != null && id.getPath().toLowerCase(Locale.ROOT).contains("leaves");
    }

    private static boolean isWater(IBlockState state) {
        return state.getMaterial() == Material.WATER;
    }

    private static String blockId(IBlockState state) {
        ResourceLocation id = Block.REGISTRY.getNameForObject(state.getBlock());
        return id != null ? id.toString() : state.getBlock().toString();
    }

    /** Per-channel color multiply in normalized space (out = c1 * c2 / 255), ignoring alpha. */
    private static int multiply(int c1, int c2) {
        int r = Math.round(((c1 >> 16) & 0xFF) * (((c2 >> 16) & 0xFF) / 255.0F));
        int g = Math.round(((c1 >> 8) & 0xFF) * (((c2 >> 8) & 0xFF) / 255.0F));
        int b = Math.round((c1 & 0xFF) * ((c2 & 0xFF) / 255.0F));
        return (r << 16) | (g << 8) | b;
    }

    /** Scale every channel by {@code factor}, clamped to [0, 255]. */
    private static int adjustBrightness(int color, float factor) {
        int r = clamp(Math.round(((color >> 16) & 0xFF) * factor));
        int g = clamp(Math.round(((color >> 8) & 0xFF) * factor));
        int b = clamp(Math.round((color & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : (value > 255 ? 255 : value);
    }
}

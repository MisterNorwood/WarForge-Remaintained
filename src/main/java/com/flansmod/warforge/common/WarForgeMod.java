package com.flansmod.warforge.common;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.api.ObjectIntPair;
import com.flansmod.warforge.api.WarForgeCapabilities;
import com.flansmod.warforge.api.vein.Vein;
import com.flansmod.warforge.api.vein.init.VeinConfigHandler;
import com.flansmod.warforge.api.vein.init.VeinUtils;
import com.flansmod.warforge.client.ClientProxy;
import com.flansmod.warforge.client.PlayerNametagCache;
import com.flansmod.warforge.common.factories.WarForgeGuiFactories;
import com.flansmod.warforge.common.blocks.BlockBasicClaim;
import com.flansmod.warforge.common.blocks.IMultiBlockInit;
import com.flansmod.warforge.common.blocks.TileEntityClaim;
import com.flansmod.warforge.common.network.*;
import com.flansmod.warforge.common.potions.PotionsModule;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.common.util.FactionDisplay;
import com.flansmod.warforge.common.util.TimeHelper;
import com.flansmod.warforge.server.*;
import com.flansmod.warforge.server.Faction.Role;
import com.flansmod.warforge.server.fob.FobManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Mod(Tags.MODID)
public class WarForgeMod {
    public static final boolean PACKET_DEBUG = false;
    public static final PacketHandler NETWORK = new PacketHandler();
    public static final Leaderboard LEADERBOARD = new Leaderboard();
    public static final FactionStorage FACTIONS = new FactionStorage();
    public static final JourneyMapClaimSync JOURNEYMAP_SYNC = new JourneyMapClaimSync();
    public static final JourneyMapVeinSync JOURNEYMAP_VEIN_SYNC = new JourneyMapVeinSync();
    public static final Content CONTENT = new Content();
    public static final ProtectionsModule PROTECTIONS = new ProtectionsModule();
    public static final TeleportsModule TELEPORTS = new TeleportsModule();
    public static final PotionsModule POTIONS = new PotionsModule();
    public static final UpgradeHandler UPGRADE_HANDLER = new UpgradeHandler();
    public static final FactionChunkLoadingManager CHUNK_LOADING_MANAGER = new FactionChunkLoadingManager();
    public static final FobManager FOBS = new FobManager();
    public static final ServerFlagRegistry FLAG_REGISTRY = new ServerFlagRegistry();
    public static VeinUtils VEIN_HANDLER = null;

    private static final HashMap<String, UUID> discordUserIdMap = new HashMap<>();

    public static PlayerNametagCache NAMETAG_CACHE;
    public static WarForgeMod INSTANCE;
    public static CommonProxy proxy;

    public static Logger LOGGER;
    public static MinecraftServer MC_SERVER = null;

    public static Random rand = new Random();
    public static long numberOfSiegeDaysTicked = 0L;
    public static long numberOfYieldDaysTicked = 0L;
    public static long timestampOfFirstDay = 0L;
    public static long previousUpdateTimestamp = 0L;

    public static long serverTick = 0L;
    public static long currTickTimestamp = 0L;
    public static long serverStopTimestamp = 0L;
    public static long gameTimeMs = 0L;

    private static long getTime(boolean tickBased) {
        return tickBased ? gameTimeMs : System.currentTimeMillis();
    }

    public static long yieldClock() { return getTime(WarForgeConfig.TICK_YIELDS); }
    public static long siegeClock() { return getTime(WarForgeConfig.TICK_SIEGES); }
    public static long momentumClock() { return getTime(WarForgeConfig.TICK_MOMENTUM); }
    public static long offlineProtectionClock() { return getTime(WarForgeConfig.TICK_OFFLINE_PROTECTION); }
    public static long graceClock() { return getTime(WarForgeConfig.TICK_SIEGE_GRACE); }
    public static long citadelMoveClock() { return getTime(WarForgeConfig.TICK_CITADEL_MOVE); }
    public static long truceClock() { return getTime(WarForgeConfig.TICK_TRUCES); }

    public static boolean showBorders = true;
    public static TimeHelper timeHelper = new TimeHelper();

    public WarForgeMod() {
        INSTANCE = this;
        LOGGER = LogManager.getLogger(Tags.MODID);
        proxy = DistExecutor.unsafeRunForDist(
                () -> ClientProxy::new,
                () -> CommonProxy::new
        );

        timestampOfFirstDay = System.currentTimeMillis();
        numberOfSiegeDaysTicked = 0L;
        numberOfYieldDaysTicked = 0L;

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        Content.register(modBus);
        Sounds.register(modBus);
        POTIONS.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, WarForgeConfig.SPEC);
        modBus.addListener(this::commonSetup);
        modBus.addListener((ModConfigEvent event) -> WarForgeConfig.bake());
        modBus.addListener(WarForgeCapabilities::register);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            NAMETAG_CACHE = new PlayerNametagCache(60_000, 200);
            modBus.register(new ModelEventHandler());
            modBus.addListener(((ClientProxy) proxy)::clientSetup);
            modBus.addListener(((ClientProxy) proxy)::registerRenderers);
            modBus.addListener(((ClientProxy) proxy)::registerKeyMappings);
        });

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ServerTickHandler());
        MinecraftForge.EVENT_BUS.register(PROTECTIONS);
        MinecraftForge.EVENT_BUS.register(new SpawnModule());

        NETWORK.initialise();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Register MUI factories on BOTH sides: the server must resolve the factory by name when it
            // decodes the OpenGuiPacket a client sends via GuiManager.openFromClient(factory, data).
            WarForgeGuiFactories.init();
            CHUNK_LOADING_MANAGER.initialize();
            POTIONS.registerBrewingRecipes();
            findVaultBlocks();
            Content.bake();
            IMultiBlockInit.registerMaps();
            loadUpgradeConfig();
            WarForgeConfig.findAllProtectionBlocks();
        });
    }

    public static String reloadServerConfig() {
        WarForgeConfig.reloadFromDisk();
        findVaultBlocks();
        WarForgeConfig.findAllProtectionBlocks();

        String levelStatus;
        try {
            loadUpgradeConfig();
            levelStatus = "levels ok";
        } catch (RuntimeException e) {
            LOGGER.error("Failed to reload citadel upgrade levels", e);
            levelStatus = "levels FAILED";
        }

        String veinStatus = reloadVeins();

        FLAG_REGISTRY.reload();

        if (MC_SERVER != null) {
            for (ServerPlayer player : MC_SERVER.getPlayerList().getPlayers()) {
                resyncConfigDependentData(player);
            }
        }

        return "main config ok, " + levelStatus + ", " + veinStatus;
    }

    private static String reloadVeins() {
        try {
            CompoundTag snapshot = new CompoundTag();
            if (VEIN_HANDLER != null) {
                VEIN_HANDLER.WriteToNBT(snapshot);
            }
            VeinConfigHandler.writeStubIfEmpty();
            VeinConfigHandler.loadVeins();
            VEIN_HANDLER.readFromNBT(snapshot);
            FACTIONS.recalculateAllWealth();
            return "veins ok";
        } catch (Exception e) {
            LOGGER.error("Failed to reload veins", e);
            return "veins FAILED";
        }
    }

    private static void resyncConfigDependentData(ServerPlayer player) {
        NETWORK.sendTo(WarForgeConfig.createConfigSyncPacket(), player);
        FLAG_REGISTRY.syncToPlayer(player);

        if (UPGRADE_HANDLER.getLEVELS() != null) {
            for (int i = 0; i < UPGRADE_HANDLER.getLEVELS().length; i++) {
                final int level = i;
                final HashMap<ItemMatcher, Integer> requirements = UPGRADE_HANDLER.getLEVELS()[i];
                final int limit = UPGRADE_HANDLER.getLIMITS()[i];
                final int insuranceSlots = UPGRADE_HANDLER.getINSURANCE_SLOTS()[i];
                final int loadedChunks = UPGRADE_HANDLER.getLOADED_CHUNKS()[i];
                NETWORK.sendTo(new PacketCitadelUpgradeRequirement(level, requirements, limit, insuranceSlots, loadedChunks), player);
            }
        }

        int veinIndex = 0;
        ArrayList<Vein> veins = new ArrayList<>(VEIN_HANDLER.ID_TO_VEINS.values());
        while (veinIndex < veins.size()) {
            PacketVeinEntries currPacket = new PacketVeinEntries();
            veinIndex = currPacket.fillFrom(veins, veinIndex);
            NETWORK.sendTo(currPacket, player);
        }

        JOURNEYMAP_SYNC.onPlayerJoin(player);
        JOURNEYMAP_VEIN_SYNC.onPlayerJoin(player);
    }

    private static void findVaultBlocks() {
        WarForgeConfig.VAULT_BLOCKS.clear();
        for (String blockID : WarForgeConfig.VAULT_BLOCK_IDS) {
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockID));
            if (block != null) {
                WarForgeConfig.VAULT_BLOCKS.add(block);
                LOGGER.info("Found block with ID " + blockID + " as a valuable block for the vault");
            } else {
                LOGGER.error("Could not find block with ID " + blockID + " as a valuable block for the vault");
            }
        }
    }

    private static void loadUpgradeConfig() {
        if (!WarForgeConfig.ENABLE_CITADEL_UPGRADES) {
            return;
        }

        Path configFile = Paths.get("config", Tags.MODID, "upgrade_levels.toml");
        try {
            UpgradeHandler.writeStubIfEmpty(configFile);
            UpgradeHandler.parseConfig(configFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load citadel upgrade config", e);
        }
    }

    public static boolean containsInt(final int[] base, int compare) {
        for (int value : base) {
            if (value == compare) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsDimension(final String[] base, ResourceKey<Level> compare) {
        String dimension = compare.location().toString();
        for (String value : base) {
            if (value.equals(dimension)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsDimension(final ResourceLocation[] base, ResourceKey<Level> compare) {
        ResourceLocation dimension = compare.location();
        for (ResourceLocation value : base) {
            if (value.equals(dimension)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsDimension(final ResourceKey<Level>[] base, ResourceKey<Level> compare) {
        for (ResourceKey<Level> value : base) {
            if (value.equals(compare)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isClaim(Item item) {
        return item.equals(Content.citadelBlockItem)
                || item.equals(Content.basicClaimBlockItem)
                || item.equals(Content.reinforcedClaimBlockItem)
                || item.equals(Content.siegeCampBlockItem);
    }

    public static void syncClaimToPlayer(Player player, BlockPos pos) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (serverPlayer.serverLevel().getBlockEntity(pos) instanceof TileEntityClaim claim) {
            var packet = claim.getUpdatePacket();
            if (packet != null) {
                serverPlayer.connection.send(packet);
            }
        }
    }

    // Sends a WarForge toast notification (not chat) to one player.
    public static void notifyPlayer(ServerPlayer player, String token, String title, String subtitle, int accentColor, int durationMs) {
        if (player == null) {
            return;
        }
        NETWORK.sendTo(new com.flansmod.warforge.common.network.PacketClientNotification(token, title, subtitle, accentColor, durationMs, null), player);
    }

    // Sends a WarForge toast notification (not chat) to every online member of a faction.
    public static void notifyFaction(com.flansmod.warforge.server.Faction faction, String token, String title, String subtitle, int accentColor, int durationMs) {
        if (faction == null) {
            return;
        }
        for (Player player : faction.getOnlinePlayers(java.util.Objects::nonNull)) {
            if (player instanceof ServerPlayer serverPlayer) {
                notifyPlayer(serverPlayer, token, title, subtitle, accentColor, durationMs);
            }
        }
    }

    public static boolean isClaim(Block block, Block... notEquals) {
        for (Block invalidBlock : notEquals) {
            if (block.equals(invalidBlock)) {
                return false;
            }
        }

        return block.equals(Content.citadelBlock)
                || block.equals(Content.basicClaimBlock)
                || block.equals(Content.reinforcedClaimBlock)
                || block.equals(Content.siegeCampBlock)
                || block.equals(Content.statue)
                || block.equals(Content.dummyTranslusent);
    }

    private static Path getFactionsFile() {
        return MC_SERVER.getWorldPath(LevelResource.ROOT).resolve("warforgefactions.dat");
    }

    private static Path getFactionsFileBackup() {
        return MC_SERVER.getWorldPath(LevelResource.ROOT).resolve("warforgefactions.dat.bak");
    }

    public static UUID getUUID(Player sender) {
        return sender.getUUID();
    }

    public static boolean isOp(Player player) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && server.getPlayerList().isOp(player.getGameProfile());
    }

    public long getTimeToNextSiegeAdvanceMs() {
        return timeHelper.getTimeToNextSiegeAdvanceMs();
    }

    public long getTimeToNextYieldMs() {
        return timeHelper.getTimeToNextYieldMs();
    }

    public void updateServer() {
        previousUpdateTimestamp = currTickTimestamp;
        currTickTimestamp = siegeClock();
        if (previousUpdateTimestamp == 0L) previousUpdateTimestamp = currTickTimestamp;

        FACTIONS.updateConqueredChunks(currTickTimestamp);

        ++serverTick;

        boolean shouldUpdate = false;

        if (!WarForgeConfig.SIEGE_ENABLE_NEW_TIMER) {
            long siegeDayLength = TimeHelper.getSiegeDayLengthMS();
            long siegeDayNumber = (currTickTimestamp - timestampOfFirstDay) / siegeDayLength;

            if (siegeDayNumber > numberOfSiegeDaysTicked) {
                numberOfSiegeDaysTicked = siegeDayNumber;
                messageAll(Component.literal("Battle takes its toll, all sieges have advanced."), true);
                FACTIONS.advanceSiegeDay();
                shouldUpdate = true;
            } else if (siegeDayNumber < numberOfSiegeDaysTicked) {
                numberOfSiegeDaysTicked = siegeDayNumber;
            }
        } else {
            FACTIONS.updateSiegeTimers();
        }

        long yieldDayLength = TimeHelper.getYieldDayLengthMs();
        long yieldDayNumber = (yieldClock() - timestampOfFirstDay) / yieldDayLength;

        if (yieldDayNumber > numberOfYieldDaysTicked) {
            numberOfYieldDaysTicked = yieldDayNumber;
            messageAll(Component.literal("All passive yields have been awarded."), true);
            FACTIONS.advanceYieldDay();
            shouldUpdate = true;
        } else if (yieldDayNumber < numberOfYieldDaysTicked) {
            numberOfYieldDaysTicked = yieldDayNumber;
        }

        if (shouldUpdate) {
            PacketTimeUpdates packet = new PacketTimeUpdates();

            if (!WarForgeConfig.SIEGE_ENABLE_NEW_TIMER) {
                packet.msUntilNextSiegeDay = timeHelper.getTimeToNextSiegeAdvanceMs();
            }

            packet.msUntilNextYieldDay = timeHelper.getTimeToNextYieldMs();

            NETWORK.sendToAll(packet);
        }
    }

    @SubscribeEvent
    public void playerInteractBlock(RightClickBlock event) {
        if (!WarForgeConfig.BLOCK_ENDER_CHEST) {
            return;
        }

        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }

        if (level.getBlockState(event.getPos()).getBlock() == Blocks.ENDER_CHEST) {
            event.getEntity().sendSystemMessage(Component.literal("WarForge has disabled Ender Chests"));
            event.setCanceled(true);

            level.addFreshEntity(new ItemEntity(level, event.getPos().getX() + 0.5d, event.getPos().getY() + 1.0d, event.getPos().getZ() + 0.5d, new ItemStack(Items.ENDER_EYE)));
            level.addFreshEntity(new ItemEntity(level, event.getPos().getX() + 0.5d, event.getPos().getY() + 1.0d, event.getPos().getZ() + 0.5d, new ItemStack(Blocks.OBSIDIAN)));
            level.removeBlock(event.getPos(), false);
        }
    }

    @SubscribeEvent
    public void playerDied(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            FACTIONS.playerDied(player, event.getSource());
            FACTIONS.removePlayerFromSiegePresence(player.getUUID());
        }
    }

    @SubscribeEvent
    public void playerRespawned(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide) {
            FACTIONS.updateAttackerSiegePresence(player);
        }
    }

    @SubscribeEvent
    public void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide) {
            FACTIONS.updateAttackerSiegePresence(player);
        }
    }

    @SubscribeEvent
    public void blockRemoved(BlockEvent.BreakEvent event) {
        BlockState state = event.getState();
        if (isClaim(state.getBlock())) {
            Level level = (Level) event.getLevel();
            if (level.getBlockEntity(event.getPos()) instanceof TileEntityClaim te && te.getFaction().equals(Faction.nullUuid)) {
                return;
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void PreBlockPlaced(RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }

        Item item = event.getItemStack().getItem();

        if (item.equals(Content.FOB_BLOCK_ITEM.get())) {
            Direction fobFace = event.getFace() != null ? event.getFace() : Direction.UP;
            BlockPos fobPlacementPos = event.getPos().relative(fobFace);
            Player fobPlayer = event.getEntity();
            Faction fobFaction = FACTIONS.getFactionOfPlayer(fobPlayer.getUUID());
            DimChunkPos fobChunk = new DimBlockPos(level.dimension(), fobPlacementPos).toChunkPos();

            if (fobFaction == null) {
                fobPlayer.sendSystemMessage(Component.literal("You aren't in a faction. Craft a citadel or join a faction"));
                event.setCanceled(true);
                return;
            }
            if (!fobFaction.isPlayerRoleInFaction(fobPlayer.getUUID(), Role.OFFICER)) {
                fobPlayer.sendSystemMessage(Component.literal("You are not an officer of your faction"));
                event.setCanceled(true);
                return;
            }
            if (!FOBS.canPlaceFob(fobFaction, fobChunk)) {
                fobPlayer.sendSystemMessage(Component.literal("You cannot place a FOB here"));
                event.setCanceled(true);
                return;
            }
            if (!FOBS.hasFobCapacity(fobFaction)) {
                fobPlayer.sendSystemMessage(Component.literal(WarForgeConfig.ENABLE_CITADEL_UPGRADES
                        ? "Your faction reached it's level's FOB limit, upgrade the level to increase the limit"
                        : "Your faction has reached its FOB limit"));
                event.setCanceled(true);
            }
            return;
        }

        if (!isClaim(item)) {
            return;
        }

        Block block = ((BlockItem) item).getBlock();
        Direction face = event.getFace() != null ? event.getFace() : Direction.UP;
        BlockPos placementPos = event.getPos().relative(face);
        Player player = event.getEntity();

        Faction playerFaction = FACTIONS.getFactionOfPlayer(player.getUUID());

        DimChunkPos pos = new DimBlockPos(level.dimension(), placementPos).toChunkPos();
        if (!FACTIONS.getClaim(pos).equals(Faction.nullUuid)) {
            Faction claimingFaction = FACTIONS.getFaction(FACTIONS.getClaim(pos));
            if (claimingFaction == null || claimingFaction.getSpecificPosForClaim(pos) == null) {
                FACTIONS.getClaims().remove(pos);
            } else {
                player.sendSystemMessage(Component.literal("This chunk already has a claim"));
                event.setCanceled(true);
                return;
            }
        }

        if (FACTIONS.isChunkContested(pos)) {
            player.sendSystemMessage(Component.literal("This chunk is contested by an active siege"));
            event.setCanceled(true);
            return;
        }

        ObjectIntPair<UUID> conqueredChunkInfo = FACTIONS.conqueredChunks.get(pos);
        if (conqueredChunkInfo != null) {
            player.sendSystemMessage(Component.literal("This chunk is conquered wilderness; it becomes claimable in "
                    + TimeHelper.formatTime(conqueredChunkInfo.getInteger())));
            event.setCanceled(true);
            return;
        }

        if (!containsDimension(WarForgeConfig.CLAIM_DIM_WHITELIST, pos.dim)) {
            player.sendSystemMessage(Component.literal("You cannot claim chunks in this dimension"));
            event.setCanceled(true);
            return;
        }

        if (FOBS.isNearActiveFob(pos, WarForgeConfig.FOB_CLAIM_EXCLUSION_RADIUS)) {
            player.sendSystemMessage(Component.literal("You cannot claim within " + WarForgeConfig.FOB_CLAIM_EXCLUSION_RADIUS
                    + " chunk(s) of an active FOB"));
            event.setCanceled(true);
            return;
        }

        if (block == Content.citadelBlock) {
            if (playerFaction != null) {
                player.sendSystemMessage(Component.literal("You are already in a faction"));
                event.setCanceled(true);
            } else {
                Faction tooClose = FACTIONS.findNearbyOpposingFaction(null, pos);
                if (tooClose != null) {
                    player.sendSystemMessage(Component.literal("You cannot found a faction within " + WarForgeConfig.MIN_DISTANCE_BETWEEN_FACTIONS
                            + " chunk(s) of faction " + tooClose.name));
                    event.setCanceled(true);
                }
            }
        } else if (block == Content.basicClaimBlock
                || block == Content.reinforcedClaimBlock) {
            if (playerFaction == null) {
                player.sendSystemMessage(Component.literal("You aren't in a faction. Craft a citadel or join a faction"));
                event.setCanceled(true);
                return;
            }

            if (!playerFaction.isPlayerRoleInFaction(player.getUUID(), Role.OFFICER)) {
                player.sendSystemMessage(Component.literal("You are not an officer of your faction"));
                event.setCanceled(true);
                return;
            }

            if (!playerFaction.canPlaceClaim()) {
                player.sendSystemMessage(Component.literal(WarForgeConfig.ENABLE_CITADEL_UPGRADES
                        ? "Your faction reached it's level's claim limit, upgrade the level to incrase the limit"
                        : "Your faction has reached its claim limit"));
                event.setCanceled(true);
            }

            if (!WarForgeConfig.ENABLE_ISOLATED_CLAIMS && BlockBasicClaim.hasAdjacent(pos, playerFaction) == null) {
                player.sendSystemMessage(Component.literal("Isolated claims are disabled; you cannot put a claim here with no adjacent claims"));
                event.setCanceled(true);
            }

            Faction tooClose = FACTIONS.findNearbyOpposingFaction(playerFaction, pos);
            if (tooClose != null) {
                player.sendSystemMessage(Component.literal("You cannot claim within " + WarForgeConfig.MIN_DISTANCE_BETWEEN_FACTIONS
                        + " chunk(s) of opposing faction " + tooClose.name));
                event.setCanceled(true);
            }

            if (!event.isCanceled() && WarForgeConfig.ENABLE_CITADEL_UPGRADES && !player.getAbilities().instabuild) {
                HashMap<ItemMatcher, Integer> claimCost = UPGRADE_HANDLER.getExtraClaimCostForLevel(playerFaction.citadelLevel);
                if (claimCost != null && !claimCost.isEmpty()) {
                    StringBuilder missing = new StringBuilder();
                    for (Map.Entry<ItemMatcher, Integer> entry : claimCost.entrySet()) {
                        int have = countMatchingItems(player, entry.getKey());
                        if (have < entry.getValue()) {
                            if (missing.length() > 0) {
                                missing.append(", ");
                            }
                            missing.append(entry.getValue()).append(" ").append(describeCost(entry.getKey())).append(" (have ").append(have).append(")");
                        }
                    }
                    if (missing.length() > 0) {
                        player.sendSystemMessage(Component.literal("Claiming at citadel level " + playerFaction.citadelLevel
                                + " also costs " + missing + " besides the claim block"));
                        event.setCanceled(true);
                    } else {
                        for (Map.Entry<ItemMatcher, Integer> entry : claimCost.entrySet()) {
                            consumeMatchingItems(player, entry.getKey(), entry.getValue());
                        }
                    }
                }
            }
        } else {
            if (playerFaction == null) {
                player.sendSystemMessage(Component.literal("You aren't in a faction. Craft a citadel or join a faction"));
                event.setCanceled(true);
                return;
            }

            if (!playerFaction.isPlayerRoleInFaction(player.getUUID(), Role.OFFICER)) {
                player.sendSystemMessage(Component.literal("You are not an officer of your faction"));
                event.setCanceled(true);
                return;
            }

            if (WarForgeConfig.MAX_SIEGES > 0 && playerFaction.calcNumSieges() >= WarForgeConfig.MAX_SIEGES) {
                player.sendSystemMessage(Component.translatable("warforge.info.too_many_siege_blocks"));
                event.setCanceled(true);
                return;
            }

            ArrayList<DimChunkPos> validTargets = new ArrayList<>(Arrays.asList(new DimChunkPos[4]));
            int numTargets = FACTIONS.getAdjacentClaims(playerFaction.uuid, new DimBlockPos(level.dimension(), event.getPos()), validTargets);
            if (numTargets == 0) {
                player.sendSystemMessage(Component.literal("There are no adjacent claims to siege; Siege camp Y level must be w/in " + WarForgeConfig.VERTICAL_SIEGE_DIST + " of target."));
                event.setCanceled(true);
            }
        }
    }

    private static String describeCost(ItemMatcher target) {
        ItemStack sample = target.toStack(1);
        if (sample != null && !sample.isEmpty()) {
            return sample.getHoverName().getString();
        }
        return target.id();
    }

    private static int countMatchingItems(Player player, ItemMatcher target) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && target.matches(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void consumeMatchingItems(Player player, ItemMatcher target, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().items.size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (stack.isEmpty() || !target.matches(stack)) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        player.inventoryMenu.broadcastChanges();
    }

    @SubscribeEvent
    public void playerLeftGame(PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide) {
            FACTIONS.onFactionMemberLoggedOut(player.getUUID());
            JOURNEYMAP_SYNC.onPlayerLogout(player);
            JOURNEYMAP_VEIN_SYNC.onPlayerLogout(player);
        }
    }

    @SubscribeEvent
    public void onChunkObserved(ChunkWatchEvent.Watch event) {
        ServerPlayer player = event.getPlayer();
        ChunkPos chunkPos = event.getPos();
        ResourceKey<Level> dim = event.getLevel().dimension();
        JOURNEYMAP_SYNC.onChunkObserved(player, dim, chunkPos.x, chunkPos.z);
        JOURNEYMAP_VEIN_SYNC.onChunkObserved(player, dim, chunkPos.x, chunkPos.z);
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (!WarForgeConfig.FACTION_PREFIX_IN_CHAT) {
            return;
        }
        ServerPlayer player = event.getPlayer();
        Faction faction = FACTIONS.getFactionOfPlayer(player.getUUID());
        if (faction == null) {
            return;
        }
        Component prefix = FactionDisplay.factionPrefix(faction);
        if (prefix == null) {
            return;
        }
        Component line = Component.empty()
                .append(prefix)
                .append(Component.literal("<").append(player.getDisplayName()).append("> "))
                .append(event.getMessage());
        event.setCanceled(true);
        MC_SERVER.getPlayerList().broadcastSystemMessage(line, false);
    }

    @SubscribeEvent
    public void playerJoinedGame(PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }

        NETWORK.sendTo(WarForgeConfig.createConfigSyncPacket(), player);

        if (Double.isNaN(player.getX()) || Double.isInfinite(player.getX())
                || Double.isNaN(player.getY()) || Double.isInfinite(player.getY())
                || Double.isNaN(player.getZ()) || Double.isInfinite(player.getZ())) {
            player.teleportTo(0d, 256d, 0d);
            player.discard();
            LOGGER.info("Player moved from the void to 0,256,0");
        }

        FACTIONS.onFactionMemberLoggedIn(player.getUUID());
        FACTIONS.updateAttackerSiegePresence(player);
        VanillaTeamSync.syncPlayer(player.getUUID());

        PacketTimeUpdates packet = new PacketTimeUpdates();
        packet.msUntilNextSiegeDay = timeHelper.getTimeToNextSiegeAdvanceMs();
        packet.msUntilNextYieldDay = timeHelper.getTimeToNextYieldMs();

        NETWORK.sendTo(packet, player);
        FACTIONS.sendClaimChunks(player, new DimChunkPos(player.level().dimension(), player.blockPosition()), WarForgeConfig.CLAIM_MANAGER_RADIUS);

        NETWORK.sendTo(Siege.clearSiegeData(), player);
        FACTIONS.sendAllSiegeInfoToNearby();
        FLAG_REGISTRY.syncToPlayer(player);
        JOURNEYMAP_SYNC.onPlayerJoin(player);
        JOURNEYMAP_VEIN_SYNC.onPlayerJoin(player);

        if (UPGRADE_HANDLER.getLEVELS() != null) {
            for (int i = 0; i < UPGRADE_HANDLER.getLEVELS().length; i++) {
                final int level = i;
                final HashMap<ItemMatcher, Integer> requirements = UPGRADE_HANDLER.getLEVELS()[i];
                final int limit = UPGRADE_HANDLER.getLIMITS()[i];
                final int insuranceSlots = UPGRADE_HANDLER.getINSURANCE_SLOTS()[i];
                final int loadedChunks = UPGRADE_HANDLER.getLOADED_CHUNKS()[i];

                SyncQueueHandler.enqueue(player, () ->
                        NETWORK.sendTo(new PacketCitadelUpgradeRequirement(level, requirements, limit, insuranceSlots, loadedChunks), player)
                );
            }
        }

        int veinIndex = 0;
        ArrayList<Vein> veins = new ArrayList<>(VEIN_HANDLER.ID_TO_VEINS.values());
        while (veinIndex < veins.size()) {
            PacketVeinEntries currPacket = new PacketVeinEntries();
            veinIndex = currPacket.fillFrom(veins, veinIndex);
            SyncQueueHandler.enqueue(player, () -> NETWORK.sendTo(currPacket, player));
        }
    }

    public UUID getPlayerIDOfDiscordUser(String discordUserID) {
        if (discordUserIdMap.containsKey(discordUserID)) {
            return discordUserIdMap.get(discordUserID);
        }
        return Faction.nullUuid;
    }

    public void messageAll(Component msg, boolean sendToDiscord) {
        if (MC_SERVER != null) {
            for (ServerPlayer player : MC_SERVER.getPlayerList().getPlayers()) {
                player.sendSystemMessage(msg);
            }
        }
    }

    private void readFromNBT(CompoundTag tags) {
        FACTIONS.readFromNBT(tags);
        if (VEIN_HANDLER != null) {
            VEIN_HANDLER.readFromNBT(tags);
        }

        timestampOfFirstDay = tags.getLong("zero-timestamp");
        numberOfSiegeDaysTicked = tags.getLong("num-days-elapsed");
        numberOfYieldDaysTicked = tags.getLong("num-yields-awarded");
        serverStopTimestamp = tags.getLong("shutdown-timestamp");
        gameTimeMs = tags.getLong("game-time-ms");
    }

    private void WriteToNBT(CompoundTag tags) {
        FACTIONS.WriteToNBT(tags);
        if (VEIN_HANDLER != null) {
            VEIN_HANDLER.WriteToNBT(tags);
        }

        tags.putLong("zero-timestamp", timestampOfFirstDay);
        tags.putLong("num-days-elapsed", numberOfSiegeDaysTicked);
        tags.putLong("num-yields-awarded", numberOfYieldDaysTicked);
        tags.putLong("shutdown-timestamp", serverStopTimestamp);
        tags.putLong("game-time-ms", gameTimeMs);
    }

    @SubscribeEvent
    public void serverAboutToStart(ServerAboutToStartEvent event) {
        MC_SERVER = event.getServer();
        if (!MC_SERVER.isDedicatedServer()) {
            WarForgeConfig.reloadFromDisk();
            findVaultBlocks();
            WarForgeConfig.findAllProtectionBlocks();
        }
        FLAG_REGISTRY.reload();

        try {
            VeinConfigHandler.writeStubIfEmpty();
            VeinConfigHandler.loadVeins();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            Path dataFile = getFactionsFile();
            if (!Files.isRegularFile(dataFile)) {
                Files.createDirectories(dataFile.getParent());
                try (OutputStream output = Files.newOutputStream(dataFile)) {
                    NbtIo.writeCompressed(new CompoundTag(), output);
                }
            }

            CompoundTag tags;
            try (InputStream input = Files.newInputStream(dataFile)) {
                tags = NbtIo.readCompressed(input);
            }

            readFromNBT(tags);
            LOGGER.info("Successfully loaded " + dataFile.getFileName());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load data from warforgefactions.dat", e);
        }

        currTickTimestamp = siegeClock();
    }

    @SubscribeEvent
    public void serverStarted(ServerStartedEvent event) {
        CHUNK_LOADING_MANAGER.refreshAllFactions(FACTIONS.getAllFactions());
        // Veins are loaded by now; recompute vein-based wealth for all loaded factions.
        FACTIONS.recalculateAllWealth();
        VanillaTeamSync.syncAllTeams();
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        CommandFactions.register(event.getDispatcher());
    }

    private void save(String event) {
        if (MC_SERVER == null) {
            return;
        }

        try {
            CompoundTag tags = new CompoundTag();
            WriteToNBT(tags);

            Path factionsFile = getFactionsFile();
            Files.createDirectories(factionsFile.getParent());
            if (Files.exists(factionsFile)) {
                Files.copy(factionsFile, getFactionsFileBackup(), StandardCopyOption.REPLACE_EXISTING);
            }

            try (OutputStream output = Files.newOutputStream(factionsFile)) {
                NbtIo.writeCompressed(tags, output);
            }
            LOGGER.info("Successfully saved warforgefactions.dat on event - " + event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save warforgefactions.dat", e);
        }
    }

    @SubscribeEvent
    public void saveEvent(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level) {
            save("Level Save - DIM " + level.dimension().location());
        }
    }

    @SubscribeEvent
    public void serverStopped(ServerStoppingEvent event) {
        save("Server Stop");
        CHUNK_LOADING_MANAGER.shutdown();
        MC_SERVER = null;
    }
}

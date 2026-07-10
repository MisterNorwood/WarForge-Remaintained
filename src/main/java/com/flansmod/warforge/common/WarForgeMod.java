package com.flansmod.warforge.common;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.api.ObjectIntPair;
import com.flansmod.warforge.api.vein.Vein;
import com.flansmod.warforge.api.vein.init.VeinConfigHandler;
import com.flansmod.warforge.api.vein.init.VeinUtils;
import com.flansmod.warforge.client.PlayerNametagCache;
import com.flansmod.warforge.common.factories.WarForgeGuiFactories;
import com.flansmod.warforge.common.blocks.BlockBasicClaim;
import com.flansmod.warforge.common.blocks.IMultiBlockInit;
import com.flansmod.warforge.common.blocks.TileEntityClaim;
import com.flansmod.warforge.common.effect.EffectRegistry;
import com.flansmod.warforge.common.network.*;
import com.flansmod.warforge.common.potions.PotionsModule;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.common.util.FactionDisplay;
import com.flansmod.warforge.common.util.TimeHelper;
import com.flansmod.warforge.server.*;
import com.flansmod.warforge.server.Faction.Role;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandHandler;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Logger;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Mod(modid = Tags.MODID, name = Tags.MODNAME, version = Tags.VERSION,
        dependencies = "after:modularui"
)
@Mod.EventBusSubscriber(modid = Tags.MODID)
public class WarForgeMod implements ILateMixinLoader {
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
    public static final ServerFlagRegistry FLAG_REGISTRY = new ServerFlagRegistry();
	  public static VeinUtils VEIN_HANDLER = null;

	// Discord integration
    private static final String DISCORD_MODID = "discordintegration";
    private static final HashMap<String, UUID> discordUserIdMap = new HashMap<String, UUID>();

    @SideOnly(Side.CLIENT)
    public static PlayerNametagCache NAMETAG_CACHE;
    @Instance(Tags.MODID)

    public static WarForgeMod INSTANCE;
    @SidedProxy(clientSide = "com.flansmod.warforge.client.ClientProxy", serverSide = "com.flansmod.warforge.common.CommonProxy")
    public static CommonProxy proxy;

	// Instances of component parts of the mod
    public static Logger LOGGER;
    public static MinecraftServer MC_SERVER = null;

	//public static CombatLogHandler COMBAT_LOG = new CombatLogHandler();
    public static Random rand = new Random();
    public static long numberOfSiegeDaysTicked = 0L;
    public static long numberOfYieldDaysTicked = 0L;
    public static long timestampOfFirstDay = 0L;
    public static long previousUpdateTimestamp = 0L;

	// Timers
    public static long serverTick = 0L;
    public static long currTickTimestamp = 0L;
    public static long serverStopTimestamp = 0L;

	// Border toggle
    public static boolean showBorders = true;

    public static volatile boolean isDirty = true;
    private int lastSaveDim = Integer.MIN_VALUE;
    public static TimeHelper timeHelper = new TimeHelper();

    public static boolean containsInt(final int[] base, int compare) {
        return Arrays.stream(base).anyMatch(i -> i == compare);
    }

    public static boolean isClaim(Item item) {
        return item != null && (item.equals(Content.citadelBlockItem)
                || item.equals(Content.basicClaimBlockItem)
                || item.equals(Content.reinforcedClaimBlockItem)
                || item.equals(Content.siegeCampBlockItem));
    }

    public static boolean isClaim(Block block, Block... notEquals) {
        if (block == null) return false;

        boolean matchesAnyInvalidBlocks = false;
        if(notEquals != null) {
            for (Block invalidBlock : notEquals) {
                if (block.equals(invalidBlock)) {
                    matchesAnyInvalidBlocks = true;
                    break;
                }
            }
        }

        return !matchesAnyInvalidBlocks && (block.equals(Content.citadelBlock)
                || block.equals(Content.basicClaimBlock)
                || block.equals(Content.reinforcedClaimBlock)
                || block.equals(Content.siegeCampBlock)
                || block.equals(Content.statue)
                || block.equals(Content.dummyTranslusent));
    }

    public static void syncClaimToPlayer(EntityPlayer player, BlockPos pos) {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        net.minecraft.tileentity.TileEntity te = MC_SERVER.getWorld(serverPlayer.dimension).getTileEntity(pos);
        if (te instanceof TileEntityClaim) {
            net.minecraft.network.Packet<?> pkt = ((TileEntityClaim) te).getUpdatePacket();
            if (pkt != null) {
                serverPlayer.connection.sendPacket(pkt);
            }
        }
    }

    public static void notifyPlayer(EntityPlayerMP player, String token, String title, String subtitle, int color, int durationMs) {
        FACTIONS.sendNotificationToPlayer(player, token, title, subtitle, color, durationMs);
    }

    private static File getFactionsFile() {
        if (MC_SERVER.isDedicatedServer()) {
            return new File(MC_SERVER.getFolderName() + "/warforgefactions.dat");
        }

        return new File("saves/" + MC_SERVER.getFolderName() + "/warforgefactions.dat");
    }

    private static File getFactionsFileBackup() {
        if (MC_SERVER.isDedicatedServer()) {
            return new File(MC_SERVER.getFolderName() + "/warforgefactions.dat.bak");
        }
        return new File("saves/" + MC_SERVER.getFolderName() + "/warforgefactions.dat.bak");

        //return new File(MC_SERVER.getWorld(0).getSaveHandler().getWorldDirectory() + "/warforgefactions.dat.bak");
    }

    public static UUID getUUID(ICommandSender sender) {
        if (sender instanceof EntityPlayer)
            return ((EntityPlayer) sender).getUniqueID();
        return UUID.fromString("Unknown");
    }

    public static boolean isOp(ICommandSender sender) {
        if (sender instanceof EntityPlayer)
            return MC_SERVER.getPlayerList().canSendCommands(((EntityPlayer) sender).getGameProfile());
        return sender instanceof MinecraftServer;
    }

    @SubscribeEvent
    public static void onRegisterSounds(RegistryEvent.Register<SoundEvent> event) {
        Sounds.register(event.getRegistry());
    }


    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
		//Load config
        WarForgeConfig.syncConfig(event.getSuggestedConfigurationFile());
		
		timestampOfFirstDay = System.currentTimeMillis();
		numberOfSiegeDaysTicked = 0L;
		numberOfYieldDaysTicked = 0L;
        
		CONTENT.preInit();
		POTIONS.preInit();
        
        MinecraftForge.EVENT_BUS.register(new ServerTickHandler());
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(PROTECTIONS);
        proxy.preInit(event);
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            MinecraftForge.EVENT_BUS.register(new ModelEventHandler());
        }
        EffectRegistry.init();
        com.flansmod.warforge.api.WarForgeCapabilities.register();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(this, proxy);
        NETWORK.initialise();
        WarForgeGuiFactories.init();
        proxy.Init(event);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        NETWORK.postInitialise();
        proxy.PostInit(event);

        WarForgeConfig.VAULT_BLOCKS.clear();
        for (String blockID : WarForgeConfig.VAULT_BLOCK_IDS) {
            Block block = Block.getBlockFromName(blockID);
            if (block != null) {
                WarForgeConfig.VAULT_BLOCKS.add(block);
                LOGGER.info("Found block with ID " + blockID + " as a valuable block for the vault");
            } else
                LOGGER.error("Could not find block with ID " + blockID + " as a valuable block for the vault");

        }

        FMLInterModComms.sendRuntimeMessage(this, DISCORD_MODID, "registerListener", "");

        IMultiBlockInit.registerMaps();
        if (WarForgeConfig.ENABLE_CITADEL_UPGRADES) {
            Path configFile = Paths.get("config/" + Tags.MODID + "/upgrade_levels.yml");
            Path legacyConfigFile = Paths.get("config/" + Tags.MODID + "/upgrade_levels.cfg");
            try {
                UpgradeHandler.migrateLegacyConfigIfNeeded(legacyConfigFile, configFile);
                UpgradeHandler.writeStubIfEmpty(configFile);
                UpgradeHandler.parseConfig(configFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

		// THIS ACTUALLY CHECKS THE PHYSICAL SIDE, NOT THE LOGICAL SERVER/ CLIENT SIDE
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            NAMETAG_CACHE = new PlayerNametagCache(60_000, 200);
        }


    }

    @EventHandler
    public void onLoadComplete(FMLLoadCompleteEvent event){
        WarForgeConfig.UNCLAIMED.findBlocks();
        WarForgeConfig.SAFE_ZONE.findBlocks();
        WarForgeConfig.WAR_ZONE.findBlocks();
        WarForgeConfig.CITADEL_FRIEND.findBlocks();
        WarForgeConfig.CITADEL_FOE.findBlocks();
        WarForgeConfig.CLAIM_FRIEND.findBlocks();
        WarForgeConfig.CLAIM_FOE.findBlocks();
        WarForgeConfig.CLAIM_ALLY.findBlocks();
        WarForgeConfig.CLAIM_DEFENDED.findBlocks();
        WarForgeConfig.SIEGECAMP_SIEGER.findBlocks();
        WarForgeConfig.SIEGECAMP_OTHER.findBlocks();
        WarForgeConfig.SIEGED_FRIEND.findBlocks();
        WarForgeConfig.SIEGED_FOE.findBlocks();
        WarForgeConfig.WAR_FRIEND.findBlocks();
        WarForgeConfig.WAR_FOE.findBlocks();
    }

    public long getTimeToNextSiegeAdvanceMs() {

        return timeHelper.getTimeToNextSiegeAdvanceMs();
    }

    public long getTimeToNextYieldMs() {

        return timeHelper.getTimeToNextYieldMs();
    }

    public void updateServer() {
        currTickTimestamp = System.currentTimeMillis();
        previousUpdateTimestamp = currTickTimestamp;

        FACTIONS.updateConqueredChunks(currTickTimestamp);

        ++serverTick;

        boolean shouldUpdate = false;

        if (!WarForgeConfig.SIEGE_ENABLE_NEW_TIMER) {
            long siegeDayLength = TimeHelper.getSiegeDayLengthMS();
            long siegeDayNumber = (currTickTimestamp - timestampOfFirstDay) / siegeDayLength;

            if (siegeDayNumber > numberOfSiegeDaysTicked) {
                numberOfSiegeDaysTicked = siegeDayNumber;
                messageAll(new TextComponentString("Battle takes its toll, all sieges have advanced."), true);
                FACTIONS.advanceSiegeDay();
                shouldUpdate = true;
            }
        } else  {
            FACTIONS.updateSiegeTimers();
            //shouldUpdate = true;
        }

        //Yield timer
        long yieldDayLength = TimeHelper.getYieldDayLengthMs();
        long yieldDayNumber = (currTickTimestamp - timestampOfFirstDay) / yieldDayLength;

        if (yieldDayNumber > numberOfYieldDaysTicked) {
            numberOfYieldDaysTicked = yieldDayNumber;
            messageAll(new TextComponentString("All passive yields have been awarded."), true);
            FACTIONS.advanceYieldDay();
            shouldUpdate = true;
        }

        if (shouldUpdate) {
            isDirty = true;
            PacketTimeUpdates packet = new PacketTimeUpdates();

            if (!WarForgeConfig.SIEGE_ENABLE_NEW_TIMER)
                packet.msTimeOfNextSiegeDay = System.currentTimeMillis() + timeHelper.getTimeToNextSiegeAdvanceMs();

            packet.msTimeOfNextYieldDay = System.currentTimeMillis() + timeHelper.getTimeToNextYieldMs();

            NETWORK.sendToAll(packet);
        }
    }

    @SubscribeEvent
    public void playerInteractBlock(RightClickBlock event) {
        if (WarForgeConfig.BLOCK_ENDER_CHEST) {
            if (!event.getWorld().isRemote) {
                if (event.getWorld().getBlockState(event.getPos()).getBlock() == Blocks.ENDER_CHEST) {
                    event.getEntityPlayer().sendMessage(new TextComponentString("WarForge has disabled Ender Chests"));
                    event.setCanceled(true);

                    event.getWorld().spawnEntity(new EntityItem(event.getWorld(), event.getPos().getX() + 0.5d, event.getPos().getY() + 1.0d, event.getPos().getZ() + 0.5d, new ItemStack(Items.ENDER_EYE)));
                    event.getWorld().spawnEntity(new EntityItem(event.getWorld(), event.getPos().getX() + 0.5d, event.getPos().getY() + 1.0d, event.getPos().getZ() + 0.5d, new ItemStack(Blocks.OBSIDIAN)));
                    event.getWorld().setBlockToAir(event.getPos());
                }
            }
        }
    }

    @SubscribeEvent
    public void playerDied(LivingDeathEvent event) {
        if (event.getEntity().world.isRemote)
            return;

        if (event.getEntityLiving() instanceof EntityPlayerMP) {
            FACTIONS.playerDied((EntityPlayerMP) event.getEntityLiving(), event.getSource());
            isDirty = true;
        }
    }

    @SubscribeEvent
    public void blockRemoved(BlockEvent.BreakEvent event) {
        IBlockState state = event.getState();
        if (isClaim(state.getBlock())) {
            if (event.getWorld().getTileEntity(event.getPos()) instanceof TileEntityClaim te && te.getFaction().equals(Faction.nullUuid))
                return;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void PreBlockPlaced(RightClickBlock event) {
        if (event.getWorld().isRemote) {
            // This is a server op
            return;
        }

        Item item = event.getItemStack().getItem();
        if (!isClaim(item)) {
            // We don't care if its not one of ours
            return;
        }

        Block block = ((ItemBlock) item).getBlock();
        BlockPos placementPos = event.getPos().offset(event.getFace() != null ? event.getFace() : EnumFacing.UP);

        // Only players can place these blocks
        if (!(event.getEntity() instanceof EntityPlayer player)) {
            event.setCanceled(true);
            return;
        }

        Faction playerFaction = FACTIONS.getFactionOfPlayer(player.getUniqueID());
        // TODO : Op override

        // All block placements are cancelled if there is already a block from this mod in that chunk
        DimChunkPos pos = new DimBlockPos(event.getWorld().provider.getDimension(), placementPos).toChunkPos();
        if (!FACTIONS.getClaim(pos).equals(Faction.nullUuid)) {
            // validate stored claim record and reject placement if the claim exists.
            Faction claimingFaction = FACTIONS.getFaction(FACTIONS.getClaim(pos));
            if (claimingFaction == null || claimingFaction.getSpecificPosForClaim(pos) == null) {
                FACTIONS.getClaims().remove(pos);
            } else {
                player.sendMessage(new TextComponentString("This chunk already has a claim"));
                event.setCanceled(true);
                return;
            }
        }

        if (FACTIONS.isChunkContested(pos)) {
            player.sendMessage(new TextComponentString("This chunk is contested by an active siege"));
            event.setCanceled(true);
            return;
        }

        ObjectIntPair<UUID> conqueredChunkInfo = FACTIONS.conqueredChunks.get(pos);
        if (conqueredChunkInfo != null) {
            player.sendMessage(new TextComponentString("This chunk is conquered wilderness; it becomes claimable in "
                    + TimeHelper.formatTime(conqueredChunkInfo.getInteger())));
            event.setCanceled(true);
            return;
        }

        if (!containsInt(WarForgeConfig.CLAIM_DIM_WHITELIST, pos.dim)) {
            player.sendMessage(new TextComponentString("You cannot claim chunks in this dimension"));
            event.setCanceled(true);
            return;
        }

        // Cancel block placement for a couple of reasons
        if (block == Content.citadelBlock) {
            if (playerFaction != null) // Can't place a second citadel
            {
                player.sendMessage(new TextComponentString("You are already in a faction"));
                event.setCanceled(true);
            } else {
                Faction tooClose = FACTIONS.findNearbyOpposingFaction(null, pos);
                if (tooClose != null) {
                    player.sendMessage(new TextComponentString("You cannot found a faction within " + WarForgeConfig.MIN_DISTANCE_BETWEEN_FACTIONS
                            + " chunk(s) of faction " + tooClose.name));
                    event.setCanceled(true);
                }
            }
        } else if (block == Content.basicClaimBlock
                || block == Content.reinforcedClaimBlock) {
            if (playerFaction == null) // Can't expand your claims if you aren't in a faction
            {
                player.sendMessage(new TextComponentString("You aren't in a faction. Craft a citadel or join a faction"));
                event.setCanceled(true);
                return;
            }

            if (!playerFaction.isPlayerRoleInFaction(player.getUniqueID(), Role.OFFICER)) {
                player.sendMessage(new TextComponentString("You are not an officer of your faction"));
                event.setCanceled(true);
                return;
            }

            if (!playerFaction.canPlaceClaim()) {
                player.sendMessage(new TextComponentString(WarForgeConfig.ENABLE_CITADEL_UPGRADES
                        ? "Your faction reached it's level's claim limit, upgrade the level to incrase the limit"
                        : "Your faction has reached its claim limit"));
                event.setCanceled(true);
            }

            if (!WarForgeConfig.ENABLE_ISOLATED_CLAIMS && BlockBasicClaim.hasAdjacent(pos, playerFaction) == null) {
                player.sendMessage(new TextComponentString("Isolated claims are disabled; you cannot put a claim here with no adjacent claims"));
                event.setCanceled(true);
            }

            Faction tooClose = FACTIONS.findNearbyOpposingFaction(playerFaction, pos);
            if (tooClose != null) {
                player.sendMessage(new TextComponentString("You cannot claim within " + WarForgeConfig.MIN_DISTANCE_BETWEEN_FACTIONS
                        + " chunk(s) of opposing faction " + tooClose.name));
                event.setCanceled(true);
            }
        } else { // Must be siege block
            if (playerFaction == null) // Can't start sieges if you aren't in a faction
            {
                player.sendMessage(new TextComponentString("You aren't in a faction. Craft a citadel or join a faction"));
                event.setCanceled(true);
                return;
            }

            if (!playerFaction.isPlayerRoleInFaction(player.getUniqueID(), Role.OFFICER)) {
                player.sendMessage(new TextComponentString("You are not an officer of your faction"));
                event.setCanceled(true);
                return;
            }

            if (playerFaction.calcNumSieges() >= WarForgeConfig.MAX_SIEGES) {
                player.sendMessage(new TextComponentTranslation("warforge.info.too_many_siege_blocks"));
                event.setCanceled(true);
                return;
            }

            ArrayList<DimChunkPos> validTargets = new ArrayList<>(Arrays.asList(new DimChunkPos[4]));
            int numTargets = FACTIONS.getAdjacentClaims(playerFaction.uuid, new DimBlockPos(event.getWorld().provider.getDimension(), event.getPos()), validTargets);
            if (numTargets == 0) {
                player.sendMessage(new TextComponentString("There are no adjacent claims to siege; Siege camp Y level must be w/in " + WarForgeConfig.VERTICAL_SIEGE_DIST + " of target."));
                event.setCanceled(true);
            }

            // TODO: Check for alliances with those claims
        }

    }

    @SubscribeEvent
    public void playerLeftGame(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.player.world.isRemote) {
            FACTIONS.onFactionMemberLoggedOut(event.player.getUniqueID());
            if (event.player instanceof EntityPlayerMP) {
                JOURNEYMAP_SYNC.onPlayerLogout((EntityPlayerMP) event.player);
                JOURNEYMAP_VEIN_SYNC.onPlayerLogout((EntityPlayerMP) event.player);
            }
        }
    }

    @SubscribeEvent
    public void onChunkObserved(ChunkWatchEvent.Watch event) {
        // A chunk just entered this player's view; in PLAYER mode this is the moment we are allowed to
        // reveal that chunk's claim border / vein to them.
        EntityPlayerMP player = event.getPlayer();
        if (player != null) {
            JOURNEYMAP_SYNC.onChunkObserved(player, player.dimension, event.getChunk().x, event.getChunk().z);
            JOURNEYMAP_VEIN_SYNC.onChunkObserved(player, player.dimension, event.getChunk().x, event.getChunk().z);
        }
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (!WarForgeConfig.FACTION_PREFIX_IN_CHAT) {
            return;
        }
        Faction faction = FACTIONS.getFactionOfPlayer(event.getPlayer().getUniqueID());
        if (faction == null) {
            return;
        }
        event.setComponent(FactionDisplay.withChatPrefix(faction, event.getComponent()));
    }

    @SubscribeEvent
    public void playerJoinedGame(PlayerLoggedInEvent event) {
        NETWORK.sendTo(WarForgeConfig.createConfigSyncPacket(), (EntityPlayerMP) event.player);
        if (!event.player.world.isRemote) {
            if (Double.isNaN(event.player.posX) || Double.isInfinite(event.player.posX)
                    || Double.isNaN(event.player.posY) || Double.isInfinite(event.player.posY)
                    || Double.isNaN(event.player.posZ) || Double.isInfinite(event.player.posZ)) {
                event.player.posX = 0d;
                event.player.posY = 256d;
                event.player.posZ = 0d;
                event.player.attemptTeleport(0d, 256d, 0d);
                event.player.setDead();
                event.player.world.getSaveHandler().getPlayerNBTManager().writePlayerData(event.player);
                LOGGER.info("Player moved from the void to 0,256,0");
            }

            FACTIONS.onFactionMemberLoggedIn(event.player.getUniqueID());

            PacketTimeUpdates packet = new PacketTimeUpdates();

            packet.msTimeOfNextSiegeDay = System.currentTimeMillis() + timeHelper.getTimeToNextSiegeAdvanceMs();
            packet.msTimeOfNextYieldDay = System.currentTimeMillis() + timeHelper.getTimeToNextYieldMs();

            NETWORK.sendTo(packet, (EntityPlayerMP) event.player);
            FACTIONS.sendClaimChunks((EntityPlayerMP) event.player, new DimChunkPos(event.player.dimension, event.player.getPosition()), WarForgeConfig.CLAIM_MANAGER_RADIUS);

            // sends packet to client which clears all previously remembered sieges; identical attacking and def names = clear packet

            NETWORK.sendTo(Siege.clearSiegeData(), (EntityPlayerMP) event.player);
            FACTIONS.sendAllSiegeInfoToNearby();
            FLAG_REGISTRY.syncToPlayer((EntityPlayerMP) event.player);
            JOURNEYMAP_SYNC.onPlayerJoin((EntityPlayerMP) event.player);
            JOURNEYMAP_VEIN_SYNC.onPlayerJoin((EntityPlayerMP) event.player);

            // begin queued sync info
            // don't sync if the upgrade info doesn't exist
            if (UPGRADE_HANDLER.getLEVELS() != null) {

                for (int i = 0; i < UPGRADE_HANDLER.getLEVELS().length; i++) {
                    final int level = i;
                    final HashMap<StackComparable, Integer> requirements = UPGRADE_HANDLER.getLEVELS()[i];
                    final int limit = UPGRADE_HANDLER.getLIMITS()[i];
                    final int insuranceSlots = UPGRADE_HANDLER.getINSURANCE_SLOTS()[i];
                    final int loadedChunks = UPGRADE_HANDLER.getLoadedChunksForLevel(i);

                    SyncQueueHandler.enqueue((EntityPlayerMP) event.player, () ->
                            NETWORK.sendTo(new PacketCitadelUpgradeRequirement(level, requirements, limit, insuranceSlots, loadedChunks), (EntityPlayerMP) event.player)
                    );
                }
            }

			// go over all ordered veins that the server has and send them to the client
			int veinIndex = 0;
			ArrayList<Vein> veins = new ArrayList<>(VEIN_HANDLER.ID_TO_VEINS.values());
			while (veinIndex < veins.size()) {
				PacketVeinEntries currPacket = new PacketVeinEntries();
				veinIndex = currPacket.fillFrom(veins, veinIndex);
				SyncQueueHandler.enqueue((EntityPlayerMP) event.player, () -> NETWORK.sendTo(currPacket, (EntityPlayerMP) event.player));
			}
        }
    }

    public UUID getPlayerIDOfDiscordUser(String discordUserID) {
        if (discordUserIdMap.containsKey(discordUserID))
            return discordUserIdMap.get(discordUserID);
        return Faction.nullUuid;
    }

    public void messageAll(ITextComponent msg, boolean sendToDiscord) // TODO: optional list of pings
    {
        if (MC_SERVER != null) {
            for (EntityPlayerMP player : MC_SERVER.getPlayerList().getPlayers()) {
                player.sendMessage(msg);
            }
        }

        NBTTagCompound sendDiscordMessageTagCompound = new NBTTagCompound();
        sendDiscordMessageTagCompound.setString("message", msg.getFormattedText());
        sendDiscordMessageTagCompound.setLong("channel", WarForgeConfig.FACTIONS_BOT_CHANNEL_ID);
        FMLInterModComms.sendRuntimeMessage(this, DISCORD_MODID, "sendMessage", sendDiscordMessageTagCompound);
    }

    private void readFromNBT(NBTTagCompound tags) {
        FACTIONS.readFromNBT(tags);
		if (VEIN_HANDLER != null) VEIN_HANDLER.readFromNBT(tags);

        timestampOfFirstDay = tags.getLong("zero-timestamp");
        numberOfSiegeDaysTicked = tags.getLong("num-days-elapsed");
        numberOfYieldDaysTicked = tags.getLong("num-yields-awarded");
        serverStopTimestamp = tags.getLong("shutdown-timestamp");
    }

    private void WriteToNBT(NBTTagCompound tags) {
        FACTIONS.WriteToNBT(tags);
		if (VEIN_HANDLER != null) VEIN_HANDLER.WriteToNBT(tags);

        tags.setLong("zero-timestamp", timestampOfFirstDay);
        tags.setLong("num-days-elapsed", numberOfSiegeDaysTicked);
        tags.setLong("num-yields-awarded", numberOfYieldDaysTicked);
        tags.setLong("shutdown-timestamp", serverStopTimestamp);
    }

    // Always goes before player join/ leave events; joining when server is loading will not call those events b4 this
    @EventHandler
    public void serverAboutToStart(FMLServerAboutToStartEvent event) {
        MC_SERVER = event.getServer();
        FLAG_REGISTRY.reload();
        CommandHandler handler = ((CommandHandler) MC_SERVER.getCommandManager());
        handler.registerCommand(new CommandFactions());

		// initialize the vein data, but only on the server; we will only update from nbt after this init step
		try {
			VeinConfigHandler.writeStubIfEmpty();
			VeinConfigHandler.loadVeins();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

        try {
            // try to read from data or backup, then generates a new file if both fail
            File dataFile = getFactionsFile();
            if (!dataFile.isFile()) {
                // try to read from faction backup
                dataFile = getFactionsFileBackup();
                if (!dataFile.isFile()) {
                    dataFile = getFactionsFile(); // ensure path is correct

                    if (!dataFile.getParentFile().exists())
                        return; // only make new file to read from if world folder has been made
                    dataFile.createNewFile(); // create new data file

                    // puts file in correct format with empty tags
                    CompressedStreamTools.writeCompressed(new NBTTagCompound(), new FileOutputStream(dataFile));
                }
            }

            NBTTagCompound tags = CompressedStreamTools.readCompressed(new FileInputStream(dataFile));
            readFromNBT(tags);
            LOGGER.info("Successfully loaded " + dataFile.getName());
        } catch (Exception e) {
            LOGGER.error("Failed to load data from warforgefactions.dat and backup; restart strongly recommended");
            e.printStackTrace();
        }

        currTickTimestamp = System.currentTimeMillis(); // will cause some update time to be registered immediately
    }

    private void save(String event) {
        try {
            if (MC_SERVER != null) {
                NBTTagCompound tags = new NBTTagCompound();
                WriteToNBT(tags);

                File factionsFile = getFactionsFile();
                if (factionsFile.exists()) {
                    Files.copy(factionsFile.toPath(), getFactionsFileBackup().toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    factionsFile.createNewFile();
                }

                CompressedStreamTools.writeCompressed(tags, new FileOutputStream(factionsFile));
                LOGGER.info("Successfully saved warforgefactions.dat on event - " + event);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save warforgefactions.dat");
            e.printStackTrace();
        }
    }

//	@EventHandler
//	public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
//		/*
//		EntityPlayer player = event.player;
//		DimBlockPos playerPos = new DimBlockPos(player);
//		if(FACTIONS.isPlayerDefending(player.getUniqueID())){
//			COMBAT_LOG.add(playerPos, player.getUniqueID(), System.currentTimeMillis());
//		}
//		*/
//	}

    // Helpers

    @SubscribeEvent
    public void saveEvent(WorldEvent.Save event) {
        if (!event.getWorld().isRemote && isDirty) {
            int dimensionID = event.getWorld().provider.getDimension();
            if (dimensionID == 0 || lastSaveDim == Integer.MIN_VALUE) {
                lastSaveDim = dimensionID;
                isDirty = false;
                save("World Save - DIM " + dimensionID);
            }
        }
    }

    @EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        CHUNK_LOADING_MANAGER.initialize();
        CHUNK_LOADING_MANAGER.refreshAllFactions(FACTIONS.getAllFactions());
        FACTIONS.recalculateAllWealth();
    }

    @EventHandler
    public void serverStopped(FMLServerStoppingEvent event) {
        save("Server Stop");
        CHUNK_LOADING_MANAGER.shutdown();
        MC_SERVER = null;
    }

    @Override
    public List<String> getMixinConfigs() {
        return Arrays.asList("mixins.warforge.json", "mixins.warforge.hbm.json", "mixins.warforge.debug.json");
    }
}

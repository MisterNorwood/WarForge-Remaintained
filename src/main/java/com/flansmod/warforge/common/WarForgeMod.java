package com.flansmod.warforge.common;

import com.flansmod.warforge.server.*;
import com.flansmod.warforge.api.ObjectIntPair;
import com.flansmod.warforge.common.blocks.TileEntitySiegeCamp;
import net.minecraft.block.Block;
import net.minecraft.block.BlockNewLeaf;
import net.minecraft.block.BlockNewLog;
import net.minecraft.block.BlockPlanks.EnumType;
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
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.*;
import java.util.zip.GZIPInputStream;

import org.apache.logging.log4j.Logger;

import com.flansmod.warforge.common.network.PacketHandler;
import com.flansmod.warforge.common.network.PacketTimeUpdates;
import com.flansmod.warforge.common.potions.PotionsModule;
import com.flansmod.warforge.common.world.WorldGenAncientTree;
import com.flansmod.warforge.common.world.WorldGenBedrockOre;
import com.flansmod.warforge.common.world.WorldGenClayPool;
import com.flansmod.warforge.common.world.WorldGenDenseOre;
import com.flansmod.warforge.common.world.WorldGenNetherPillar;
import com.flansmod.warforge.common.world.WorldGenShulkerFossil;
import com.flansmod.warforge.common.world.WorldGenSlimeFountain;
import com.google.common.io.Files;
import com.flansmod.warforge.server.Faction.Role;
import zone.rong.mixinbooter.ILateMixinLoader;

@Mod(modid = WarForgeMod.MODID, name = WarForgeMod.NAME, version = WarForgeMod.VERSION)
public class WarForgeMod implements ILateMixinLoader
{
    public static final String MODID = "warforge";
    public static final String NAME = "WarForge Factions";
    public static final String VERSION = "1.2.0";
    
	@Instance(MODID)
	public static WarForgeMod INSTANCE;
	@SidedProxy(clientSide = "com.flansmod.warforge.client.ClientProxy", serverSide = "com.flansmod.warforge.common.CommonProxy")
	public static CommonProxy proxy;
	
	// Instances of component parts of the mod
	public static Logger LOGGER;
	public static final PacketHandler NETWORK = new PacketHandler();
	public static final Leaderboard LEADERBOARD = new Leaderboard();
	public static final FactionStorage FACTIONS = new FactionStorage();
	public static final Content CONTENT = new Content();
	public static final ProtectionsModule PROTECTIONS = new ProtectionsModule();
	public static final TeleportsModule TELEPORTS = new TeleportsModule();
	public static final PotionsModule POTIONS = new PotionsModule();
	
	public static MinecraftServer MC_SERVER = null;
	public static Random rand = new Random();
	public static CombatLogHandler COMBAT_LOG = new CombatLogHandler();

	
	public static long numberOfSiegeDaysTicked = 0L;
	public static long numberOfYieldDaysTicked = 0L;
	public static long timestampOfFirstDay = 0L;
	public static long previousUpdateTimestamp = 0L;

	// Timers
	public static long ServerTick = 0L;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        LOGGER = event.getModLog();
		//Load config
        WarForgeConfig.SyncConfig(event.getSuggestedConfigurationFile());
		
		timestampOfFirstDay = System.currentTimeMillis();
		numberOfSiegeDaysTicked = 0L;
		numberOfYieldDaysTicked = 0L;
        
		CONTENT.preInit();
		POTIONS.preInit();
        
        MinecraftForge.EVENT_BUS.register(new ServerTickHandler());
        MinecraftForge.EVENT_BUS.register(this);
        proxy.PreInit(event);
    }

    @EventHandler
    public void init(FMLInitializationEvent event)
    {
		NetworkRegistry.INSTANCE.registerGuiHandler(this, proxy);
		NETWORK.initialise();
		proxy.Init(event);
    }
    
	@EventHandler
	public void postInit(FMLPostInitializationEvent event)
	{
		NETWORK.postInitialise();
		proxy.PostInit(event);
		
		WarForgeConfig.VAULT_BLOCKS.clear();
		for(String blockID : WarForgeConfig.VAULT_BLOCK_IDS)
		{
			Block block = Block.getBlockFromName(blockID);
			if(block != null)
			{
				WarForgeConfig.VAULT_BLOCKS.add(block);
				LOGGER.info("Found block with ID " + blockID + " as a valuable block for the vault");
			}
			else
				LOGGER.error("Could not find block with ID " + blockID + " as a valuable block for the vault");
				
		}
		
		FMLInterModComms.sendRuntimeMessage(this, DISCORD_MODID, "registerListener", "");
		
		WarForgeConfig.UNCLAIMED.FindBlocks();
		WarForgeConfig.SAFE_ZONE.FindBlocks();
		WarForgeConfig.WAR_ZONE.FindBlocks();
		WarForgeConfig.CITADEL_FRIEND.FindBlocks();
		WarForgeConfig.CITADEL_FOE.FindBlocks();
		WarForgeConfig.CLAIM_FRIEND.FindBlocks();
		WarForgeConfig.CLAIM_FOE.FindBlocks();
		WarForgeConfig.SIEGECAMP_SIEGER.FindBlocks();
		WarForgeConfig.SIEGECAMP_OTHER.FindBlocks();		
	}
    
    public long GetSiegeDayLengthMS()
    {
    	 return (long)(
    			 WarForgeConfig.SIEGE_DAY_LENGTH // In hours
     			* 60f // In minutes
     			* 60f // In seconds
     			* 1000f); // In milliseconds
    }
    
    public long GetYieldDayLengthMS()
    {
    	 return (long)(
    			 WarForgeConfig.YIELD_DAY_LENGTH // In hours
     			* 60f // In minutes
     			* 60f // In seconds
     			* 1000f); // In milliseconds
    }

	public long GetCooldownIntoTicks(float cooldown) {
		// From Minutes
		return (long)(
				cooldown
				* 60L // Minutes -> Seconds
				* 20L // Seconds -> Ticks
				);
	}

	public long GetCooldownRemainingSeconds(float cooldown, long startOfCooldown) {
		long ticks = (long) cooldown; // why did you convert it into ticks if its already in ticks
		long elapsed = startOfCooldown - ticks;

		return (long)(
				(ServerTick - elapsed) * 20
		);
	}

	public int GetCooldownRemainingMinutes(float cooldown, long startOfCooldown) {
		long ticks = (long) cooldown;
		long elapsed = startOfCooldown - ticks;

		return (int)(
				(ServerTick - elapsed) * 20 / 60
		);
	}

	public int GetCooldownRemainingHours(float cooldown, long startOfCooldown) {
		long ticks = (long) cooldown;
		long elapsed = startOfCooldown - ticks;

		return (int)(
				(ServerTick - elapsed) * 20 / 60 / 60
		);
	}

	public long GetMSToNextSiegeAdvance() 
	{
		long elapsedMS = System.currentTimeMillis() - timestampOfFirstDay;
		long todayElapsedMS = elapsedMS % GetSiegeDayLengthMS();
		
		return GetSiegeDayLengthMS() - todayElapsedMS;
	}
    
	public long GetMSToNextYield() 
	{
		long elapsedMS = System.currentTimeMillis() - timestampOfFirstDay;
		long todayElapsedMS = elapsedMS % GetYieldDayLengthMS();
		
		return GetYieldDayLengthMS() - todayElapsedMS;
	}
    
    public void UpdateServer()
    {
    	boolean shouldUpdate = false;
    	long msTime = System.currentTimeMillis();
    	long dayLength = GetSiegeDayLengthMS();

		FACTIONS.updateConqueredChunks(msTime);

    	long dayNumber = (msTime - timestampOfFirstDay) / dayLength;

		++ServerTick;

    	if(dayNumber > numberOfSiegeDaysTicked)
    	{
    		// Time to tick a new day
    		numberOfSiegeDaysTicked = dayNumber;
    		
    		MessageAll(new TextComponentString("Battle takes its toll, all sieges have advanced."), true);
    		
    		FACTIONS.AdvanceSiegeDay();
    		shouldUpdate = true;
    	}
    	
    	dayLength = GetYieldDayLengthMS();
    	dayNumber = (msTime - timestampOfFirstDay) / dayLength;
    	
    	if(dayNumber > numberOfYieldDaysTicked)
    	{
    		// Time to tick a new day
    		numberOfYieldDaysTicked = dayNumber;
    		
    		MessageAll(new TextComponentString("All passive yields have been awarded."), true);
    		
    		FACTIONS.AdvanceYieldDay();
    		shouldUpdate = true;
    	}

		COMBAT_LOG.doEnforcements(System.currentTimeMillis());

    	if(shouldUpdate)
    	{
	    	PacketTimeUpdates packet = new PacketTimeUpdates();
	    	
	    	packet.msTimeOfNextSiegeDay = System.currentTimeMillis() + GetMSToNextSiegeAdvance();
	    	packet.msTimeOfNextYieldDay = System.currentTimeMillis() + GetMSToNextYield();
	    	
	    	NETWORK.sendToAll(packet);
    	
    	}
    	
    }

    @SubscribeEvent
    public void PlayerInteractBlock(RightClickBlock event)
    {
    	if(WarForgeConfig.BLOCK_ENDER_CHEST)
    	{
    		if(!event.getWorld().isRemote)
    		{
	    		if(event.getWorld().getBlockState(event.getPos()).getBlock() == Blocks.ENDER_CHEST)
	    		{
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
    public void PlayerDied(LivingDeathEvent event)
    {
    	if(event.getEntity().world.isRemote)
    		return;
    		
    	if(event.getEntityLiving() instanceof EntityPlayerMP)
    	{
    		FACTIONS.PlayerDied((EntityPlayerMP)event.getEntityLiving(), event.getSource());
    	}
    }
    
    private void BlockPlacedOrRemoved(BlockEvent event, IBlockState state)
    {
    	// Check for vault value
		if(WarForgeConfig.VAULT_BLOCKS.contains(state.getBlock()))
		{
			DimChunkPos chunkPos = new DimBlockPos(event.getWorld().provider.getDimension(), event.getPos()).ToChunkPos();
			UUID factionID = FACTIONS.GetClaim(chunkPos);
			if(!factionID.equals(Faction.NULL)) 
			{
				Faction faction = FACTIONS.GetFaction(factionID);
				if(faction != null)
				{
					if(faction.mCitadelPos.ToChunkPos().equals(chunkPos))
					{
						faction.EvaluateVault();
					}
				}
			}
		}
    }
    
	@SubscribeEvent
	public void BlockPlaced(BlockEvent.EntityPlaceEvent event)
	{
		if(!event.getWorld().isRemote) 
		{
			BlockPlacedOrRemoved(event, event.getPlacedBlock());
		}
	}
	
	@SubscribeEvent
	public void BlockRemoved(BlockEvent.BreakEvent event)
	{
		IBlockState state = event.getState();
		if(state.getBlock() == CONTENT.citadelBlock
		|| state.getBlock() == CONTENT.basicClaimBlock
		|| state.getBlock() == CONTENT.reinforcedClaimBlock)
		{
			event.setCanceled(true);
			return;
		}

		if(!event.getWorld().isRemote) 
		{
			if (state.getBlock() == CONTENT.siegeCampBlock) {
				TileEntitySiegeCamp siegeBlock = (TileEntitySiegeCamp) event.getWorld().getTileEntity(event.getPos());
				if (siegeBlock != null) siegeBlock.onDestroyed();
			}
			BlockPlacedOrRemoved(event, event.getState());
		}
	}

	public static boolean containsInt(final int[] base, int compare) {
		for (int val : base) if (val == compare) return true;
		return false;
	}

    @SubscribeEvent
    public void PreBlockPlaced(RightClickBlock event)
    {
    	if(event.getWorld().isRemote)
    	{
    		// This is a server op
    		return;
    	}
    	
    	Item item = event.getItemStack().getItem();
    	if(item != CONTENT.citadelBlockItem
    	&& item != CONTENT.basicClaimBlockItem
    	&& item != CONTENT.reinforcedClaimBlockItem
    	&& item != CONTENT.siegeCampBlockItem)
    	{
    		// We don't care if its not one of ours
    		return;
    	}
    	
    	Block block = ((ItemBlock)item).getBlock();
    	BlockPos placementPos = event.getPos().offset(event.getFace());
    	
    	// Only players can place these blocks
    	if(!(event.getEntity() instanceof EntityPlayer))
    	{
    		event.setCanceled(true);
    		return;
    	}
    	
    	EntityPlayer player = (EntityPlayer)event.getEntity();
    	Faction playerFaction = FACTIONS.GetFactionOfPlayer(player.getUniqueID());
    	// TODO : Op override

    	// All block placements are cancelled if there is already a block from this mod in that chunk
    	DimChunkPos pos = new DimBlockPos(event.getWorld().provider.getDimension(), placementPos).ToChunkPos();
		if(!FACTIONS.GetClaim(pos).equals(Faction.NULL))
		{
			player.sendMessage(new TextComponentString("This chunk already has a claim"));
			event.setCanceled(true);
			return;
    	}

		ObjectIntPair<UUID> conqueredChunkInfo = FACTIONS.conqueredChunks.get(pos);
		if (conqueredChunkInfo != null) {
			// remove invalid entries if necessary, and if not then do actual comparison
			if (conqueredChunkInfo.getLeft() == null || conqueredChunkInfo.getLeft().equals(Faction.NULL) || FACTIONS.GetFaction(conqueredChunkInfo.getLeft()) == null) FACTIONS.conqueredChunks.remove(pos);
			else if (!conqueredChunkInfo.getLeft().equals(playerFaction.mUUID)) {
				player.sendMessage(new TextComponentTranslation("warforge.info.chunk_is_conquered",
						WarForgeMod.FACTIONS.GetFaction(FACTIONS.conqueredChunks.get(pos).getLeft()).mName,
						formatTime(FACTIONS.conqueredChunks.get(pos).getRight())));
				event.setCanceled(true);
				return;
			}
		}

		if(!containsInt(WarForgeConfig.CLAIM_DIM_WHITELIST, pos.mDim)){
			player.sendMessage(new TextComponentString("You cannot claim chunks in this dimension"));
			event.setCanceled(true);
			return;
		}
    	
    	// Cancel block placement for a couple of reasons
    	if(block == CONTENT.citadelBlock)
    	{
    		if(playerFaction != null) // Can't place a second citadel
    		{
    			player.sendMessage(new TextComponentString("You are already in a faction"));
    			event.setCanceled(true);
    			return;
    		}
    	}
    	else if(block == CONTENT.basicClaimBlock
    		|| block == CONTENT.reinforcedClaimBlock)
    	{
    		if(playerFaction == null) // Can't expand your claims if you aren't in a faction
    		{
    			player.sendMessage(new TextComponentString("You aren't in a faction. Craft a citadel or join a faction"));
    			event.setCanceled(true);
    			return;
    		}

    		if(!playerFaction.IsPlayerRoleInFaction(player.getUniqueID(), Role.OFFICER))
    		{
    			player.sendMessage(new TextComponentString("You are not an officer of your faction"));
    			event.setCanceled(true);
    			return;
    		}
    	}
    	else // Must be siege block
    	{
    		if(playerFaction == null) // Can't start sieges if you aren't in a faction
    		{
    			player.sendMessage(new TextComponentString("You aren't in a faction. Craft a citadel or join a faction"));
    			event.setCanceled(true);
    			return;
    		}

    		if(!playerFaction.IsPlayerRoleInFaction(player.getUniqueID(), Role.OFFICER))
    		{
    			player.sendMessage(new TextComponentString("You are not an officer of your faction"));
    			event.setCanceled(true);
    			return;
    		}
    		
/*
    		if(!playerFaction.CanPlayerMoveFlag(player.getUniqueID()))
    		{
    			player.sendMessage(new TextComponentString("You have already moved your flag today. Check /f time"));
    			event.setCanceled(true);
    			return;
    		}
 */

			if (playerFaction.calcNumSieges() > 2) {
				player.sendMessage(new TextComponentTranslation("warforge.info.too_many_siege_blocks"));
				event.setCanceled(true);
				return;
			}

    		ArrayList<DimChunkPos> validTargets = new ArrayList<>(Arrays.asList(new DimChunkPos[4]));
    		int numTargets = FACTIONS.GetAdjacentClaims(playerFaction.mUUID, new DimBlockPos(event.getWorld().provider.getDimension(), event.getPos()), validTargets);
    		if(numTargets == 0)
    		{
    			player.sendMessage(new TextComponentString("There are no adjacent claims to siege; Siege camp Y level must be w/in " + WarForgeConfig.VERTICAL_SIEGE_DIST + " of target."));
    			event.setCanceled(true);
    			return;
    		}
    		
    		// TODO: Check for alliances with those claims
    	}
    	
    }

	public static String formatTime(int ms) {
		int seconds = ms / 1000;
		int minutes = seconds / 60;
		int hours = minutes / 60;
		int days = hours / 24;

		// ensure entire time left is not represented in each format, but rather only leftover amounts for that unit

		seconds -= minutes * 60;
		minutes -= hours * 60;
		hours -= days * 24;

		StringBuilder timeBuilder = new StringBuilder();
		if (days > 0) timeBuilder.append(days).append("d ");
		if (hours > 0) timeBuilder.append(hours).append("h ");
		if (minutes > 0) timeBuilder.append(minutes).append("min ");
		if (seconds > 0) timeBuilder.append(seconds).append("s ");
		timeBuilder.append(ms % 1000).append("ms");

		return timeBuilder.toString();
	}

    @SubscribeEvent
    public void PlayerJoinedGame(PlayerLoggedInEvent event)
    {
    	if(!event.player.world.isRemote)
    	{
           	if(Double.isNaN(event.player.posX) || Double.isInfinite(event.player.posX)
           			|| Double.isNaN(event.player.posY) || Double.isInfinite(event.player.posY)
           			|| Double.isNaN(event.player.posZ) || Double.isInfinite(event.player.posZ))
           	{
        		event.player.posX = 0d;
        		event.player.posY = 256d;
        		event.player.posZ = 0d;
        		event.player.attemptTeleport(0d, 256d, 0d);
        		event.player.setDead();
        		event.player.world.getSaveHandler().getPlayerNBTManager().writePlayerData(event.player);
        		LOGGER.info("Player moved from the void to 0,256,0");
           	}
    		
	    	PacketTimeUpdates packet = new PacketTimeUpdates();
	    	
	    	packet.msTimeOfNextSiegeDay = System.currentTimeMillis() + GetMSToNextSiegeAdvance();
	    	packet.msTimeOfNextYieldDay = System.currentTimeMillis() + GetMSToNextYield();
	    	
	    	NETWORK.sendTo(packet, (EntityPlayerMP)event.player);
	    	
	    	FACTIONS.SendAllSiegeInfoToNearby();
    	}
    }
    
    // Discord integration
    private static final String DISCORD_MODID = "discordintegration";
    private static HashMap<String, UUID> sDiscordUserIDMap = new HashMap<String, UUID>();
    
    public UUID GetPlayerIDOfDiscordUser(String discordUserID)
    {
    	if(sDiscordUserIDMap.containsKey(discordUserID))
    		return sDiscordUserIDMap.get(discordUserID);
    	return Faction.NULL;
    }
    
    public void MessageAll(ITextComponent msg, boolean sendToDiscord) // TODO: optional list of pings
    {
    	if(MC_SERVER != null)
    	{
	    	for(EntityPlayerMP player : MC_SERVER.getPlayerList().getPlayers())
	    	{
	    		player.sendMessage(msg);
	    	}
    	}
    	
		NBTTagCompound sendDiscordMessageTagCompound = new NBTTagCompound();
		sendDiscordMessageTagCompound.setString("message", msg.getFormattedText());
		sendDiscordMessageTagCompound.setLong("channel", WarForgeConfig.FACTIONS_BOT_CHANNEL_ID);
		FMLInterModComms.sendRuntimeMessage(this, DISCORD_MODID, "sendMessage", sendDiscordMessageTagCompound);
    }
    
    @EventHandler
    public void IMCEvent(FMLInterModComms.IMCEvent event)
    {
        for (final FMLInterModComms.IMCMessage imc : event.getMessages())
        {
        	//JSON.parseRaw(imc.getStringValue()).;
        	//Gson gson = new Gson();
        	//gson.
        }
    }
        
    // World Generation
	private WorldGenDenseOre ironGenerator, goldGenerator, redstoneGenerator;
	private WorldGenBedrockOre diamondGenerator, magmaGenerator;
	private WorldGenAncientTree ancientTreeGenerator;
	private WorldGenClayPool clayLakeGenerator;
	private WorldGenNetherPillar quartzPillarGenerator;
	private WorldGenSlimeFountain slimeGenerator;
	private WorldGenShulkerFossil shulkerGenerator;
	
	@SubscribeEvent
	public void populateOverworldChunk(PopulateChunkEvent event)
	{
		if(WarForgeConfig.ENABLE_WORLD_GEN) {
			// Overworld generators
			if (event.getWorld().provider.getDimension() == 0) {
				if (ironGenerator == null)
					ironGenerator = new WorldGenDenseOre(CONTENT.denseIronOreBlock.getDefaultState(), Blocks.IRON_ORE.getDefaultState(),
							WarForgeConfig.DENSE_IRON_CELL_SIZE, WarForgeConfig.DENSE_IRON_DEPOSIT_RADIUS, WarForgeConfig.DENSE_IRON_OUTER_SHELL_RADIUS, WarForgeConfig.DENSE_IRON_OUTER_SHELL_CHANCE,
							WarForgeConfig.DENSE_IRON_MIN_INSTANCES_PER_CELL, WarForgeConfig.DENSE_IRON_MAX_INSTANCES_PER_CELL, WarForgeConfig.DENSE_IRON_MIN_HEIGHT, WarForgeConfig.DENSE_IRON_MAX_HEIGHT);
				if (goldGenerator == null)
					goldGenerator = new WorldGenDenseOre(CONTENT.denseGoldOreBlock.getDefaultState(), Blocks.GOLD_ORE.getDefaultState(),
							WarForgeConfig.DENSE_GOLD_CELL_SIZE, WarForgeConfig.DENSE_GOLD_DEPOSIT_RADIUS, WarForgeConfig.DENSE_GOLD_OUTER_SHELL_RADIUS, WarForgeConfig.DENSE_GOLD_OUTER_SHELL_CHANCE,
							WarForgeConfig.DENSE_GOLD_MIN_INSTANCES_PER_CELL, WarForgeConfig.DENSE_GOLD_MAX_INSTANCES_PER_CELL, WarForgeConfig.DENSE_GOLD_MIN_HEIGHT, WarForgeConfig.DENSE_GOLD_MAX_HEIGHT);
				if (diamondGenerator == null)
					diamondGenerator = new WorldGenBedrockOre(CONTENT.denseDiamondOreBlock.getDefaultState(), Blocks.DIAMOND_ORE.getDefaultState(),
							WarForgeConfig.DENSE_DIAMOND_CELL_SIZE, WarForgeConfig.DENSE_DIAMOND_DEPOSIT_RADIUS, WarForgeConfig.DENSE_DIAMOND_OUTER_SHELL_RADIUS, WarForgeConfig.DENSE_DIAMOND_OUTER_SHELL_CHANCE,
							WarForgeConfig.DENSE_DIAMOND_MIN_INSTANCES_PER_CELL, WarForgeConfig.DENSE_DIAMOND_MAX_INSTANCES_PER_CELL, WarForgeConfig.DENSE_DIAMOND_MIN_HEIGHT, WarForgeConfig.DENSE_DIAMOND_MAX_HEIGHT);
				if (magmaGenerator == null)
					magmaGenerator = new WorldGenBedrockOre(CONTENT.magmaVentBlock.getDefaultState(), Blocks.LAVA.getDefaultState(),
							WarForgeConfig.MAGMA_VENT_CELL_SIZE, WarForgeConfig.MAGMA_VENT_DEPOSIT_RADIUS, WarForgeConfig.MAGMA_VENT_OUTER_SHELL_RADIUS, WarForgeConfig.MAGMA_VENT_OUTER_SHELL_CHANCE,
							WarForgeConfig.MAGMA_VENT_MIN_INSTANCES_PER_CELL, WarForgeConfig.MAGMA_VENT_MAX_INSTANCES_PER_CELL, WarForgeConfig.MAGMA_VENT_MIN_HEIGHT, WarForgeConfig.MAGMA_VENT_MAX_HEIGHT);
				if (redstoneGenerator == null)
					redstoneGenerator = new WorldGenDenseOre(CONTENT.denseRedstoneOreBlock.getDefaultState(), Blocks.REDSTONE_ORE.getDefaultState(),
							WarForgeConfig.DENSE_REDSTONE_CELL_SIZE, WarForgeConfig.DENSE_REDSTONE_DEPOSIT_RADIUS, WarForgeConfig.DENSE_REDSTONE_OUTER_SHELL_RADIUS, WarForgeConfig.DENSE_REDSTONE_OUTER_SHELL_CHANCE,
							WarForgeConfig.DENSE_REDSTONE_MIN_INSTANCES_PER_CELL, WarForgeConfig.DENSE_REDSTONE_MAX_INSTANCES_PER_CELL, WarForgeConfig.DENSE_REDSTONE_MIN_HEIGHT, WarForgeConfig.DENSE_REDSTONE_MAX_HEIGHT);


				if (ancientTreeGenerator == null)
					ancientTreeGenerator = new WorldGenAncientTree(CONTENT.ancientOakBlock.getDefaultState(), Blocks.LOG2.getDefaultState().withProperty(BlockNewLog.VARIANT, EnumType.DARK_OAK), Blocks.LEAVES2.getDefaultState().withProperty(BlockNewLeaf.VARIANT, EnumType.DARK_OAK),
							WarForgeConfig.ANCIENT_OAK_CELL_SIZE, WarForgeConfig.ANCIENT_OAK_CHANCE, WarForgeConfig.ANCIENT_OAK_HOLE_RADIUS,
							WarForgeConfig.ANCIENT_OAK_CORE_RADIUS, WarForgeConfig.ANCIENT_OAK_MAX_TRUNK_RADIUS, WarForgeConfig.ANCIENT_OAK_MAX_HEIGHT);

				if (clayLakeGenerator == null)
					clayLakeGenerator = new WorldGenClayPool(CONTENT.denseClayBlock, Blocks.CLAY, Blocks.WATER);

				if (slimeGenerator == null)
					slimeGenerator = new WorldGenSlimeFountain(CONTENT.denseSlimeBlock.getDefaultState(), Blocks.WATER.getDefaultState(), Blocks.SLIME_BLOCK.getDefaultState(),
							WarForgeConfig.SLIME_POOL_CELL_SIZE, WarForgeConfig.SLIME_POOL_LAKE_RADIUS, WarForgeConfig.SLIME_POOL_LAKE_CEILING_HEIGHT,
							WarForgeConfig.SLIME_POOL_MIN_INSTANCES_PER_CELL, WarForgeConfig.SLIME_POOL_MAX_INSTANCES_PER_CELL,
							WarForgeConfig.SLIME_POOL_MIN_HEIGHT, WarForgeConfig.SLIME_POOL_MAX_HEIGHT);

				ironGenerator.generate(event.getWorld(), event.getRand(), new BlockPos(event.getChunkX() * 16, 128, event.getChunkZ() * 16));
				goldGenerator.generate(event.getWorld(), event.getRand(), new BlockPos(event.getChunkX() * 16, 128, event.getChunkZ() * 16));
				redstoneGenerator.generate(event.getWorld(), event.getRand(), new BlockPos(event.getChunkX() * 16, 128, event.getChunkZ() * 16));
				diamondGenerator.generate(event.getWorld(), event.getRand(), new BlockPos(event.getChunkX() * 16, 128, event.getChunkZ() * 16));
				magmaGenerator.generate(event.getWorld(), event.getRand(), new BlockPos(event.getChunkX() * 16, 128, event.getChunkZ() * 16));
				ancientTreeGenerator.generate(event.getWorld(), event.getRand(), new BlockPos(event.getChunkX() * 16, 128, event.getChunkZ() * 16));
				slimeGenerator.generate(event.getWorld(), event.getRand(), new BlockPos(event.getChunkX() * 16, 128, event.getChunkZ() * 16));

				if (rand.nextInt(WarForgeConfig.CLAY_POOL_CHANCE) == 0)
					clayLakeGenerator.generate(event.getWorld(), event.getRand(), new BlockPos(event.getChunkX() * 16, 128, event.getChunkZ() * 16));
			} else if (event.getWorld().provider.getDimension() == -1) {
				if (quartzPillarGenerator == null)
					quartzPillarGenerator = new WorldGenNetherPillar(CONTENT.denseQuartzOreBlock.getDefaultState(), Blocks.QUARTZ_ORE.getDefaultState());


				if (rand.nextInt(WarForgeConfig.QUARTZ_PILLAR_CHANCE) == 0) {
					quartzPillarGenerator.generate(event.getWorld(), event.getRand(), new BlockPos(event.getChunkX() * 16, 128, event.getChunkZ() * 16));
				}
			} else if (event.getWorld().provider.getDimension() == 1) {
				if (shulkerGenerator == null)
					shulkerGenerator = new WorldGenShulkerFossil(Blocks.END_STONE.getDefaultState(), CONTENT.shulkerFossilBlock.getDefaultState(),
							WarForgeConfig.SHULKER_FOSSIL_CELL_SIZE, WarForgeConfig.SHULKER_FOSSIL_MIN_INSTANCES_PER_CELL, WarForgeConfig.SHULKER_FOSSIL_MAX_INSTANCES_PER_CELL,
							WarForgeConfig.SHULKER_FOSSIL_MIN_ROTATIONS, WarForgeConfig.SHULKER_FOSSIL_MAX_ROTATIONS, WarForgeConfig.SHULKER_FOSSIL_RADIUS_PER_ROTATION,
							WarForgeConfig.SHULKER_FOSSIL_DISC_THICKNESS, WarForgeConfig.SHULKER_FOSSIL_MIN_HEIGHT, WarForgeConfig.SHULKER_FOSSIL_MAX_HEIGHT
					);

				shulkerGenerator.generate(event.getWorld(), event.getRand(), new BlockPos(event.getChunkX() * 16, 128, event.getChunkZ() * 16));
			}
		}
	}

	private void ReadFromNBT(NBTTagCompound tags)
	{
		FACTIONS.ReadFromNBT(tags);

		timestampOfFirstDay = tags.getLong("zero-timestamp");
		numberOfSiegeDaysTicked = tags.getLong("num-days-elapsed");
		numberOfYieldDaysTicked = tags.getLong("num-yields-awarded");
	}

	private void WriteToNBT(NBTTagCompound tags)
	{
		FACTIONS.WriteToNBT(tags);

		tags.setLong("zero-timestamp", timestampOfFirstDay);
		tags.setLong("num-days-elapsed", numberOfSiegeDaysTicked);
		tags.setLong("num-yields-awarded", numberOfYieldDaysTicked);
	}

	private static File getFactionsFile()
	{
		if(MC_SERVER.isDedicatedServer())
		{
			return new File(MC_SERVER.getFolderName() + "/warforgefactions.dat");
		}

		return new File("saves/" + MC_SERVER.getFolderName() + "/warforgefactions.dat");
	}
	
	private static File getFactionsFileBackup()
	{
		if(MC_SERVER.isDedicatedServer())
		{
			return new File(MC_SERVER.getFolderName() + "/warforgefactions.dat.bak");
		}
		return new File("saves/" + MC_SERVER.getFolderName() + "/warforgefactions.dat.bak");
		
		//return new File(MC_SERVER.getWorld(0).getSaveHandler().getWorldDirectory() + "/warforgefactions.dat.bak");
	}
		
	@EventHandler
	public void ServerAboutToStart(FMLServerAboutToStartEvent event)
	{
		MC_SERVER = event.getServer();
		previousUpdateTimestamp = System.currentTimeMillis();
		CommandHandler handler = ((CommandHandler)MC_SERVER.getCommandManager());
		handler.registerCommand(new CommandFactions());
		
		try
		{
			// try to read from data or backup, then generates a new file if both fail
			File dataFile = getFactionsFile();
			if (!dataFile.isFile()) {
				// try to read from faction backup
				dataFile = getFactionsFileBackup();
				if (!dataFile.isFile()) {
					dataFile = getFactionsFile(); // ensure path is correct
					dataFile.createNewFile(); // create new data file

					// puts file in correct format with empty tags
					CompressedStreamTools.writeCompressed(new NBTTagCompound(), new FileOutputStream(dataFile));
				}
			}

			NBTTagCompound tags = CompressedStreamTools.readCompressed(new FileInputStream(dataFile));
			ReadFromNBT(tags);
			LOGGER.info("Successfully loaded " + dataFile.getName());
		}
		catch(Exception e)
		{
			LOGGER.error("Failed to load data from warforgefactions.dat and backup; restart strongly recommended");
			e.printStackTrace();
		}
	}
	
	private void Save(String event)
	{
		try
		{
			if(MC_SERVER != null)
			{
				NBTTagCompound tags = new NBTTagCompound();
				WriteToNBT(tags);
				
				File factionsFile = getFactionsFile();
				if(factionsFile.exists())
					Files.copy(factionsFile, getFactionsFileBackup());
				else
				{
					factionsFile.createNewFile();
				}
				
				CompressedStreamTools.writeCompressed(tags, new FileOutputStream(factionsFile));
				LOGGER.info("Successfully saved warforgefactions.dat on event - " + event);
			}
		}
		catch(Exception e)
		{
			LOGGER.error("Failed to save warforgefactions.dat");
			e.printStackTrace();
		}
	}
	
	@SubscribeEvent
	public void SaveEvent(WorldEvent.Save event)
	{
		if(!event.getWorld().isRemote)
		{
			int dimensionID = event.getWorld().provider.getDimension();
			Save("World Save - DIM " + dimensionID);
		}
	}
	
	@EventHandler
	public void ServerStopped(FMLServerStoppingEvent event)
	{
		Save("Server Stop");
		MC_SERVER = null;
	}

	@EventHandler
	public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		EntityPlayer player = event.player;
		DimBlockPos playerPos = new DimBlockPos(player);
		if(FACTIONS.isPlayerDefending(player.getUniqueID())){
			COMBAT_LOG.add(playerPos, player.getUniqueID(), System.currentTimeMillis());
		}
	}
    // Helpers

    public static UUID GetUUID(ICommandSender sender)
    {
    	if(sender instanceof EntityPlayer)
    		return ((EntityPlayer)sender).getUniqueID();
    	return UUID.fromString("Unknown");
    }
    
    public static boolean IsOp(ICommandSender sender)
    {
    	if(sender instanceof EntityPlayer)
    		return MC_SERVER.getPlayerList().canSendCommands(((EntityPlayer)sender).getGameProfile());
    	return sender instanceof MinecraftServer;
    }

	@Override
	public List<String> getMixinConfigs() {
		return Collections.singletonList("mixins.warforge.json");
	}
}

package com.flansmod.warforge.common;

import com.flansmod.warforge.common.WarForgeConfig.ProtectionConfig;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.FactionStorage;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraftforge.event.entity.EntityEvent.EnteringChunk;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent.PotentialSpawns;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.UUID;

public class ProtectionsModule {
    public static boolean OP_OVERRIDE = false;
    private static boolean inLoop = false;

    public ProtectionsModule() {
    }

    @Nonnull
    public static ProtectionConfig GetProtections(UUID playerID, DimBlockPos pos) {
        return GetProtections(playerID, pos.toChunkPos());
    }

    // It is generally expected that you are asking about a loaded chunk, not that that should matter
    @Nonnull
    public static ProtectionConfig GetProtections(UUID playerID, DimChunkPos pos) {
        FactionStorage.SiegeZoneResult siegeZone = WarForgeMod.FACTIONS.getSiegeZone(pos);

        UUID factionID = WarForgeMod.FACTIONS.getClaim(pos);
        if (factionID.equals(FactionStorage.SAFE_ZONE_ID))
            return WarForgeConfig.SAFE_ZONE;

        if (factionID.equals(FactionStorage.WAR_ZONE_ID))
            return WarForgeConfig.WAR_ZONE;

        Faction faction = WarForgeMod.FACTIONS.getFaction(factionID);
        boolean playerIsInFaction = faction != null && playerID != null && !playerID.equals(Faction.nullUuid) && faction.isPlayerInFaction(playerID);

        if (playerIsInFaction && faction.getClaimType(pos) == Faction.ClaimType.SIEGE)
            return WarForgeConfig.CLAIM_FRIEND;

        if (siegeZone.zone != FactionStorage.SiegeZone.NONE) {
            boolean defender = playerID != null && !playerID.equals(Faction.nullUuid)
                    && siegeZone.defendingFaction != null
                    && WarForgeMod.FACTIONS.IsPlayerInFaction(playerID, siegeZone.defendingFaction);
            if (siegeZone.zone == FactionStorage.SiegeZone.SIEGED)
                return defender ? WarForgeConfig.SIEGED_FRIEND : WarForgeConfig.SIEGED_FOE;
            return defender ? WarForgeConfig.WAR_FRIEND : WarForgeConfig.WAR_FOE;
        }

        if (faction != null) {
            if (playerIsInFaction && faction.isCurrentlyDefending)
                return WarForgeConfig.CLAIM_DEFENDED;

            if (faction.citadelPos.toChunkPos().equals(pos))
                return playerIsInFaction ? WarForgeConfig.CITADEL_FRIEND : WarForgeConfig.CITADEL_FOE;

            if (playerIsInFaction)
                return WarForgeConfig.CLAIM_FRIEND;

            // Allies of the owning faction get the CLAIM_ALLY profile, but only when that faction has
            // enabled ally interaction. Otherwise allies are treated like any other foreign player.
            if (faction.allowAllyInteraction && playerID != null && !playerID.equals(Faction.nullUuid)) {
                Faction playerFaction = WarForgeMod.FACTIONS.getFactionOfPlayer(playerID);
                if (playerFaction != null && faction.isAllyOf(playerFaction.uuid)) {
                    return WarForgeConfig.CLAIM_ALLY;
                }
            }

            return WarForgeConfig.CLAIM_FOE;
        }

        return WarForgeConfig.UNCLAIMED;
    }

    public void UpdateServer() {
		/*
		 * oof pistons are a pain
		for(World world: WarForgeMod.MC_SERVER.worlds)
		{
			ArrayList<TileEntityPiston> list = new ArrayList<TileEntityPiston>();

			for(TileEntity te : world.loadedTileEntityList)
			{
				if(te instanceof TileEntityPiston)
				{
					list.add( (TileEntityPiston)te);
				}
			}

			for(TileEntityPiston piston : list)
			{
				NBTTagCompound tags = new NBTTagCompound();
				piston.writeToNBT(tags);
				tags.setBoolean("extending", !tags.getBoolean("extending"));
				piston.readFromNBT(tags);

				piston.clearPistonTileEntity();
			}
		}
		*/

    }

    @SubscribeEvent
    public void OnDismount(EntityMountEvent event) {
        if (event.getEntity().world.isRemote)
            return;

        if (event.getEntityMounting() instanceof EntityPlayer) {
            DimBlockPos vehiclePos = new DimBlockPos(event.getEntityBeingMounted().dimension, event.getEntityBeingMounted().getPosition());
            ProtectionConfig mountConfig = GetProtections(event.getEntityMounting().getUniqueID(), vehiclePos);

            if (event.isMounting() && !mountConfig.ALLOW_MOUNT_ENTITY)
                event.setCanceled(true);

            if (event.isDismounting() && !mountConfig.ALLOW_DISMOUNT_ENTITY)
                event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void OnExplosion(ExplosionEvent.Detonate event) {
        if (event.getWorld().isRemote)
            return;

        // Check each pos, but keep a cache of configs so we don't do like 300 lookups
        int dim = event.getWorld().provider.getDimension();
        HashMap<DimChunkPos, ProtectionConfig> checkedPositions = new HashMap<DimChunkPos, ProtectionConfig>();

        for (int i = event.getAffectedBlocks().size() - 1; i >= 0; i--) {
            DimChunkPos cPos = new DimChunkPos(dim, event.getAffectedBlocks().get(i));
            if (!checkedPositions.containsKey(cPos)) {
                ProtectionConfig config = GetProtections(Faction.nullUuid, cPos);
                checkedPositions.put(cPos, config);
            }
            if (!checkedPositions.get(cPos).EXPLOSION_DAMAGE || !checkedPositions.get(cPos).BLOCK_REMOVAL) {
                //WarForgeMod.LOGGER.info("Protected block position from explosion");
                event.getAffectedBlocks().remove(i);
            }
        }
    }

    @SubscribeEvent
    public void OnDamage(LivingDamageEvent event) {
        if (event.getEntity().world.isRemote)
            return;

        DimBlockPos damagedPos = new DimBlockPos(event.getEntity().dimension, event.getEntity().getPosition());
        ProtectionConfig damagedConfig = GetProtections(event.getEntity().getUniqueID(), damagedPos);

        DamageSource source = event.getSource();
        if (source instanceof EntityDamageSource) {
            Entity attacker = source.getTrueSource();
            if (attacker instanceof EntityPlayer && event.getEntity() instanceof EntityPlayer) {
                // Factions in a post-alliance truce cannot harm each other for the truce's duration.
                Faction attackerFaction = WarForgeMod.FACTIONS.getFactionOfPlayer(attacker.getUniqueID());
                Faction victimFaction = WarForgeMod.FACTIONS.getFactionOfPlayer(event.getEntity().getUniqueID());
                if (attackerFaction != null && victimFaction != null
                        && !attackerFaction.uuid.equals(victimFaction.uuid)
                        && attackerFaction.isInTruceWith(victimFaction.uuid)) {
                    event.setCanceled(true);
                    return;
                }
            }
            if (attacker instanceof EntityPlayer) {
                if (!damagedConfig.PLAYER_TAKE_DAMAGE_FROM_PLAYER) {
                    event.setCanceled(true);
                    //WarForgeMod.LOGGER.info("Cancelled damage event from other player because we are in a safe zone");
                    return;
                }

                DimBlockPos attackerPos = new DimBlockPos(attacker.dimension, attacker.getPosition());
                ProtectionConfig attackerConfig = GetProtections(attacker.getUniqueID(), attackerPos);

                if (!attackerConfig.PLAYER_DEAL_DAMAGE) {
                    event.setCanceled(true);
                    //WarForgeMod.LOGGER.info("Cancelled damage event from player because they were in a safe zone");
                    return;
                }
            } else if (!damagedConfig.PLAYER_TAKE_DAMAGE_FROM_MOB) {
                event.setCanceled(true);
                //WarForgeMod.LOGGER.info("Cancelled damage event from mob");
                return;
            }
        } else {
            if (!damagedConfig.PLAYER_TAKE_DAMAGE_FROM_OTHER) {
                event.setCanceled(true);
                //WarForgeMod.LOGGER.info("Cancelled damage event from other source");
                return;
            }
        }
    }

    // might be useful at some point
    @SubscribeEvent
    public void EntityPlaced(BlockEvent.PlaceEvent event) {
        if (event.getWorld().isRemote) { return; }
        WarForgeMod.LOGGER.atDebug().log("Place Event: " + event);
    }

    // TODO: Make the protections module properly handle mekanism place events where the placer is actually null
    // is called twice for mekanism cables for some reason; first call has null entity, second has placer (which might be null)
    @SubscribeEvent
    public void BlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getWorld().isRemote)
            return;

        Entity eventEntity = event.getEntity();

        if (OP_OVERRIDE && WarForgeMod.isOp(eventEntity))
            return;

        // best effort compat with mekanism
        Block placedBlock = event.getPlacedBlock().getBlock();
        var blockId = ForgeRegistries.BLOCKS.getKey(placedBlock);
        if (blockId == null) { WarForgeMod.LOGGER.atDebug().log("Could not get id of block placed in event: " + event); }
        else if (blockId.getNamespace().equals("mekanism") && eventEntity == null) { return; }  // ignore mek place w/ null entity

        if (eventEntity == null) {
            WarForgeMod.LOGGER.atError().log("Detected null entity for event with detals: pos - " + event.getPos() + "; world - " + event.getWorld() + ";");
            event.setCanceled(true);
            return;
        }

        DimBlockPos pos = new DimBlockPos(eventEntity.dimension, event.getPos());
        ProtectionConfig config = GetProtections(eventEntity.getUniqueID(), pos);

        if (placeDenied(config, event.getBlockSnapshot().getCurrentBlock().getBlock()))
            event.setCanceled(true);
    }

    public static boolean placeDenied(ProtectionConfig config, Block block) {
        if (!config.PLACE_BLOCKS) {
            return !config.BLOCK_PLACE_WHITELIST.contains(block);
        }
        return config.BLOCK_PLACE_BLACKLIST.contains(block);
    }

    @SubscribeEvent
    public void BlockRemoved(BlockEvent.BreakEvent event) {
        if (event.getWorld().isRemote)
            return;

        if (OP_OVERRIDE && WarForgeMod.isOp(event.getPlayer()))
            return;

        DimBlockPos pos = new DimBlockPos(event.getPlayer().dimension, event.getPos());
        ProtectionConfig config = GetProtections(event.getPlayer().getUniqueID(), pos);
        Block block = event.getState().getBlock();

        if (!breakDenied(config, block))
            return;

        boolean slowable = config.mineTime.resolve(block) != null
                && !event.getPlayer().capabilities.isCreativeMode
                && event.getState().getBlockHardness(event.getWorld(), event.getPos()) > 0;
        if (slowable)
            return;

        event.setCanceled(true);
    }

    public static boolean breakDenied(ProtectionConfig config, Block block) {
        if (!config.BREAK_BLOCKS || !config.BLOCK_REMOVAL) {
            return !config.BLOCK_BREAK_WHITELIST.contains(block);
        }
        return config.BLOCK_BREAK_BLACKLIST.contains(block);
    }

    @SubscribeEvent
    public void OnBreakSpeed(net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.world.isRemote)
            return;

        if (OP_OVERRIDE && WarForgeMod.isOp(player))
            return;

        net.minecraft.util.math.BlockPos blockPos = event.getPos();
        if (blockPos == null)
            return;

        DimBlockPos pos = new DimBlockPos(player.dimension, blockPos);
        ProtectionConfig config = GetProtections(player.getUniqueID(), pos);
        Block block = event.getState().getBlock();

        if (!breakDenied(config, block))
            return;

        MineTime.Rule rule = config.mineTime.resolve(block);
        if (rule == null)
            return;

        event.setNewSpeed(MineTime.applySpeed(rule, event.getNewSpeed(), event.getState(), player.world, blockPos, player));
    }

    @SubscribeEvent
    public void OnPlayerInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getWorld().isRemote)
            return;

        if (OP_OVERRIDE && WarForgeMod.isOp(event.getEntityPlayer()))
            return;

        DimBlockPos pos = new DimBlockPos(event.getTarget().dimension, event.getTarget().getPosition());
        ProtectionConfig config = GetProtections(event.getEntityPlayer().getUniqueID(), pos);

        if (!config.INTERACT) {
            //WarForgeMod.LOGGER.info("Cancelled interact event");
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void OnPlayerRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld().isRemote)
            return;

        if (OP_OVERRIDE && WarForgeMod.isOp(event.getEntityPlayer()))
            return;

        DimBlockPos pos = new DimBlockPos(event.getEntity().dimension, event.getPos());
        ProtectionConfig config = GetProtections(event.getEntityPlayer().getUniqueID(), pos);


        Block block = event.getWorld().getBlockState(event.getPos()).getBlock();
        if (!config.INTERACT) {
            if (block != WarForgeMod.CONTENT.citadelBlock
                    && block != WarForgeMod.CONTENT.basicClaimBlock
                    && block != WarForgeMod.CONTENT.reinforcedClaimBlock
                    && block != WarForgeMod.CONTENT.dummyTranslusent
                    && block != WarForgeMod.CONTENT.statue
                    && !config.BLOCK_INTERACT_WHITELIST.contains(block)) {
                //WarForgeMod.LOGGER.info("Cancelled item use event while looking at block");
                event.setCanceled(true);
            }
        } else {
            if (config.BLOCK_INTERACT_BLACKLIST.contains(block))
                event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void OnPlayerRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getWorld().isRemote)
            return;

        if (OP_OVERRIDE && WarForgeMod.isOp(event.getEntityPlayer()))
            return;

        // Always allow food
        if (event.getItemStack().getItem() instanceof ItemFood)
            return;

        DimBlockPos pos = new DimBlockPos(event.getEntity().dimension, event.getPos());
        ProtectionConfig config = GetProtections(event.getEntityPlayer().getUniqueID(), pos);

        Item usedItem = event.getItemStack().getItem();
        if (!config.USE_ITEM) {
            if (!config.ITEM_USE_WHITELIST.contains(usedItem))
                event.setCanceled(true);
        } else {
            if (config.ITEM_USE_BLACKLIST.contains(usedItem))
                event.setCanceled(true);

        }
    }

    @SubscribeEvent
    public void OnMobSpawn(PotentialSpawns event) {
        ProtectionConfig config = GetProtections(Faction.nullUuid, new DimBlockPos(event.getWorld().provider.getDimension(), event.getPos()));
        if (!config.ALLOW_MOB_SPAWNS) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void LivingUpdate(EnteringChunk event) {
        if (event.getEntity() instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.getEntity();
            int dim = player.dimension;
            DimChunkPos from = new DimChunkPos(dim, event.getOldChunkX(), event.getOldChunkZ());
            DimChunkPos to = new DimChunkPos(dim, event.getNewChunkX(), event.getNewChunkZ());

            if (WarForgeMod.FACTIONS.isConqueredWilderness(to) && !WarForgeMod.FACTIONS.isConqueredWilderness(from)) {
                WarForgeMod.FACTIONS.sendNotificationToPlayer(player, "warforge.entered_conquered", "Conquered Territory",
                        "Reverts to wilderness in " + com.flansmod.warforge.common.util.TimeHelper.formatTime(WarForgeMod.FACTIONS.conqueredRemainingMs(to)),
                        0xC79A3A, 6000);
            }

            Faction faction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUniqueID());
            if (faction != null) {
                if (WarForgeMod.FACTIONS.isInOwnSiegeWarzone(faction.uuid, from)
                        && !WarForgeMod.FACTIONS.isInOwnSiegeWarzone(faction.uuid, to)) {
                    WarForgeMod.FACTIONS.sendNotificationToPlayer(player, "warforge.leaving_warzone",
                            "Leaving Siege Warzone", "Your absence will start the siege abandon timer.",
                            0xC79A3A, 6000);
                }
            }
            return;
        }

        if (!inLoop) {
            if (!(event.getEntity() instanceof EntityPlayer)) {
                ProtectionConfig config = GetProtections(Faction.nullUuid, new DimBlockPos(event.getEntity().dimension, event.getEntity().getPosition()));
                if (!config.ALLOW_MOB_ENTRY) {
                    inLoop = true;
                    boolean wasNoClip = event.getEntity().noClip;
                    event.getEntity().noClip = true;
                    event.getEntity().move(MoverType.SELF, (event.getOldChunkX() - event.getNewChunkX()), 0d, (event.getOldChunkZ() - event.getNewChunkZ()));
                    event.getEntity().noClip = wasNoClip;
                    inLoop = false;
                }
            }
        }
    }
}

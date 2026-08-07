package com.flansmod.warforge.common;

import com.flansmod.warforge.common.WarForgeConfig.ProtectionConfig;
import com.flansmod.warforge.common.network.PacketClientNotification;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.common.util.TimeHelper;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.FactionStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityEvent.EnteringSection;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.event.level.LevelEvent.PotentialSpawns;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

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

        // A faction's own siege-camp claim still behaves like a friendly claim for its members, even
        // inside a siege zone, so attackers keep control of their own camp.
        if (playerIsInFaction && faction.getClaimType(pos) == Faction.ClaimType.SIEGE)
            return WarForgeConfig.CLAIM_FRIEND;

        // Siege zones override claim ownership. Friend = member of the besieged (defending) faction;
        // everyone else (attackers, neutrals) is a foe. Inner Sieged zone takes priority over War zone.
        if (siegeZone.zone != FactionStorage.SiegeZone.NONE) {
            boolean defender = playerID != null && !playerID.equals(Faction.nullUuid)
                    && siegeZone.defendingFaction != null
                    && WarForgeMod.FACTIONS.IsPlayerInFaction(playerID, siegeZone.defendingFaction);
            if (siegeZone.zone == FactionStorage.SiegeZone.SIEGED)
                return defender ? WarForgeConfig.SIEGED_FRIEND : WarForgeConfig.SIEGED_FOE;
            return defender ? WarForgeConfig.WAR_FRIEND : WarForgeConfig.WAR_FOE;
        }

        if (faction != null) {
            // Lowest-priority siege state: the besieged faction's own claims outside the War/Sieged zones.
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
        if (event.getEntityMounting().level().isClientSide)
            return;

        if (event.getEntityMounting() instanceof Player) {
            Entity vehicle = event.getEntityBeingMounted();
            DimBlockPos vehiclePos = new DimBlockPos(vehicle.level().dimension(), vehicle.blockPosition());
            ProtectionConfig mountConfig = GetProtections(event.getEntityMounting().getUUID(), vehiclePos);

            if (event.isMounting() && !mountConfig.ALLOW_MOUNT_ENTITY)
                event.setCanceled(true);

            if (event.isDismounting() && !mountConfig.ALLOW_DISMOUNT_ENTITY)
                event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void OnExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide)
            return;

        // Check each pos, but keep a cache of configs so we don't do like 300 lookups
        var dim = event.getLevel().dimension();
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

    // Pistons can shove blocks across a claim border from outside, bypassing the per-claim break/place
    // rules. Cancel any push/pull where a moved (or destroyed) block leaves or enters a chunk claimed by
    // someone other than the piston's own chunk owner. Only runs on actual activation, so it is cold.
    @SubscribeEvent
    public void OnPistonMove(PistonEvent.Pre event) {
        if (!WarForgeConfig.BLOCK_FOREIGN_PISTON_PUSH)
            return;
        if (!(event.getLevel() instanceof Level level) || level.isClientSide)
            return;

        ResourceKey<Level> dim = level.dimension();
        UUID pistonOwner = WarForgeMod.FACTIONS.getClaim(new DimBlockPos(dim, event.getPos()));

        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver != null && resolver.resolve()) {
            Direction moveDir = resolver.getPushDirection();
            for (BlockPos pushed : resolver.getToPush()) {
                if (crossesForeignClaim(dim, pistonOwner, pushed)
                        || crossesForeignClaim(dim, pistonOwner, pushed.relative(moveDir))) {
                    event.setCanceled(true);
                    return;
                }
            }
            for (BlockPos destroyed : resolver.getToDestroy()) {
                if (crossesForeignClaim(dim, pistonOwner, destroyed)) {
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // The extending head itself occupies the cell directly in front of the piston.
        if (event.getPistonMoveType() == PistonEvent.PistonMoveType.EXTEND
                && crossesForeignClaim(dim, pistonOwner, event.getFaceOffsetPos())) {
            event.setCanceled(true);
        }
    }

    // True when pos sits in a chunk claimed by a faction other than the piston's chunk owner.
    private static boolean crossesForeignClaim(ResourceKey<Level> dim, UUID pistonOwner, BlockPos pos) {
        UUID owner = WarForgeMod.FACTIONS.getClaim(new DimBlockPos(dim, pos));
        return !owner.equals(Faction.nullUuid) && !owner.equals(pistonOwner);
    }

    @SubscribeEvent
    public void OnDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide)
            return;

        LivingEntity victim = event.getEntity();
        DimBlockPos damagedPos = new DimBlockPos(victim.level().dimension(), victim.blockPosition());
        ProtectionConfig damagedConfig = GetProtections(victim.getUUID(), damagedPos);

        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        if (attacker != null) {
            if (attacker instanceof Player && victim instanceof Player) {
                // Factions in a post-alliance truce cannot harm each other for the truce's duration.
                Faction attackerFaction = WarForgeMod.FACTIONS.getFactionOfPlayer(attacker.getUUID());
                Faction victimFaction = WarForgeMod.FACTIONS.getFactionOfPlayer(victim.getUUID());
                if (attackerFaction != null && victimFaction != null
                        && !attackerFaction.uuid.equals(victimFaction.uuid)
                        && attackerFaction.isInTruceWith(victimFaction.uuid)) {
                    event.setCanceled(true);
                    return;
                }
            }
            if (attacker instanceof Player) {
                if (!damagedConfig.PLAYER_TAKE_DAMAGE_FROM_PLAYER) {
                    event.setCanceled(true);
                    //WarForgeMod.LOGGER.info("Cancelled damage event from other player because we are in a safe zone");
                    return;
                }

                DimBlockPos attackerPos = new DimBlockPos(attacker.level().dimension(), attacker.blockPosition());
                ProtectionConfig attackerConfig = GetProtections(attacker.getUUID(), attackerPos);

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
    public void EntityPlaced(BlockEvent.EntityMultiPlaceEvent event) {
        if (event.getLevel().isClientSide()) { return; }
        WarForgeMod.LOGGER.atDebug().log("Multi Place Event: " + event);
    }

    // TODO: Make the protections module properly handle mekanism place events where the placer is actually null
    // is called twice for mekanism cables for some reason; first call has null entity, second has placer (which might be null)
    @SubscribeEvent
    public void BlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide())
            return;

        Entity eventEntity = event.getEntity();

        if (OP_OVERRIDE && eventEntity instanceof Player && WarForgeMod.isOp((Player) eventEntity))
            return;

        // best effort compat with mekanism
        Block placedBlock = event.getPlacedBlock().getBlock();
        var blockId = ForgeRegistries.BLOCKS.getKey(placedBlock);
        if (blockId == null) { WarForgeMod.LOGGER.atDebug().log("Could not get id of block placed in event: " + event); }
        else if (blockId.getNamespace().equals("mekanism") && eventEntity == null) { return; }  // ignore mek place w/ null entity

        if (eventEntity == null) {
            WarForgeMod.LOGGER.atError().log("Detected null entity for event with detals: pos - " + event.getPos() + "; world - " + event.getLevel() + ";");
            event.setCanceled(true);
            return;
        }

        DimBlockPos pos = new DimBlockPos(eventEntity.level().dimension(), event.getPos());
        ProtectionConfig config = GetProtections(eventEntity.getUUID(), pos);

        if (placeDenied(config, placedBlock))
            event.setCanceled(true);
    }

    // Mirrors the territory place rules: true when protection forbids placing this block here.
    public static boolean placeDenied(ProtectionConfig config, Block block) {
        if (!config.PLACE_BLOCKS) {
            return !config.BLOCK_PLACE_WHITELIST.contains(block);
        }
        return config.BLOCK_PLACE_BLACKLIST.contains(block);
    }

    @SubscribeEvent
    public void BlockRemoved(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide())
            return;

        if (OP_OVERRIDE && WarForgeMod.isOp(event.getPlayer()))
            return;

        DimBlockPos pos = new DimBlockPos(event.getPlayer().level().dimension(), event.getPos());
        ProtectionConfig config = GetProtections(event.getPlayer().getUUID(), pos);
        Block block = event.getState().getBlock();

        if (!breakDenied(config, block))
            return;

        // MineTime turns a denied break into a slow break (paced by OnBreakSpeed). Keep the hard cancel
        // when MineTime opts this block out, or when the player is in creative — creative instabreaks
        // every block, so un-cancelling would hand out a free break. Instant-break blocks (grass, plants:
        // hardness <= 0) cannot be paced by break speed, so the client predictor never cancels them and
        // OnBreakSpeed leaves them at natural speed; a server hard-cancel would only rubber-band, so let
        // them break.
        MineTime.Rule rule = config.mineTime.resolve(block);
        if (rule != null && !event.getPlayer().getAbilities().instabuild)
            return;

        event.setCanceled(true);
    }

    // Mirrors the territory break rules: true when protection forbids breaking this block here.
    public static boolean breakDenied(ProtectionConfig config, Block block) {
        if (!config.BREAK_BLOCKS || !config.BLOCK_REMOVAL) {
            return !config.BLOCK_BREAK_WHITELIST.contains(block);
        }
        return config.BLOCK_BREAK_BLACKLIST.contains(block);
    }

    @SubscribeEvent
    public void OnBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        Level level = player.level();
        // Server-authoritative: the client lacks claim-ownership data, and the server paces the
        // survival break, so MineTime only adjusts the speed here.
        if (level.isClientSide)
            return;

        if (OP_OVERRIDE && WarForgeMod.isOp(player))
            return;

        BlockPos blockPos = event.getPosition().orElse(null);
        if (blockPos == null)
            return;

        DimBlockPos pos = new DimBlockPos(level.dimension(), blockPos);
        ProtectionConfig config = GetProtections(player.getUUID(), pos);
        Block block = event.getState().getBlock();

        if (!breakDenied(config, block))
            return;

        MineTime.Rule rule = config.mineTime.resolve(block);
        if (rule == null)
            return; // hard-protected; BlockRemoved cancels the break outright

        event.setNewSpeed(MineTime.applySpeed(rule, event.getNewSpeed(), event.getState(), level, blockPos, player));
    }

    @SubscribeEvent
    public void OnPlayerInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide)
            return;

        if (OP_OVERRIDE && WarForgeMod.isOp(event.getEntity()))
            return;

        Entity target = event.getTarget();
        DimBlockPos pos = new DimBlockPos(target.level().dimension(), target.blockPosition());
        ProtectionConfig config = GetProtections(event.getEntity().getUUID(), pos);

        if (!config.INTERACT) {
            //WarForgeMod.LOGGER.info("Cancelled interact event");
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void OnPlayerRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide)
            return;

        if (OP_OVERRIDE && WarForgeMod.isOp(event.getEntity()))
            return;

        DimBlockPos pos = new DimBlockPos(event.getEntity().level().dimension(), event.getPos());
        ProtectionConfig config = GetProtections(event.getEntity().getUUID(), pos);


        Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
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
        if (event.getLevel().isClientSide)
            return;

        if (OP_OVERRIDE && WarForgeMod.isOp(event.getEntity()))
            return;

        // Always allow food
        if (event.getItemStack().getItem().isEdible())
            return;

        DimBlockPos pos = new DimBlockPos(event.getEntity().level().dimension(), event.getPos());
        ProtectionConfig config = GetProtections(event.getEntity().getUUID(), pos);

        Item usedItem = event.getItemStack().getItem();
        if (!config.USE_ITEM) {
            if (!config.ITEM_USE_WHITELIST.contains(usedItem))
                event.setCanceled(true);
        } else {
            if (config.ITEM_USE_BLACKLIST.contains(usedItem))
                event.setCanceled(true);

        }
    }

    // Warns a player with a toast when they cross out of a siege warzone their faction is involved in,
    // since their absence is what starts the abandon timer.
    private static void handleWarzoneBoundary(ServerPlayer player, SectionPos oldPos, SectionPos newPos) {
        Faction faction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUUID());
        if (faction == null) {
            return;
        }
        ResourceKey<Level> dim = player.level().dimension();
        DimChunkPos from = new DimChunkPos(dim, oldPos.x(), oldPos.z());
        DimChunkPos to = new DimChunkPos(dim, newPos.x(), newPos.z());
        if (WarForgeMod.FACTIONS.isInOwnSiegeWarzone(faction.uuid, from)
                && !WarForgeMod.FACTIONS.isInOwnSiegeWarzone(faction.uuid, to)) {
            WarForgeMod.notifyPlayer(player, "warforge.leaving_warzone", "Leaving Siege Warzone",
                    "Your absence will start the siege abandon timer.", PacketClientNotification.COLOR_WARNING, 6000);
        }
    }

    // Tells a player how long until a conquered chunk reverts to wilderness when they walk into one.
    private static void notifyConqueredEntry(ServerPlayer player, SectionPos oldPos, SectionPos newPos) {
        ResourceKey<Level> dim = player.level().dimension();
        DimChunkPos to = new DimChunkPos(dim, newPos.x(), newPos.z());
        if (!WarForgeMod.FACTIONS.isConqueredWilderness(to)) {
            return;
        }
        DimChunkPos from = new DimChunkPos(dim, oldPos.x(), oldPos.z());
        if (WarForgeMod.FACTIONS.isConqueredWilderness(from)) {
            return; // already inside conquered land; only notify on the way in
        }
        WarForgeMod.notifyPlayer(player, "warforge.entered_conquered", "Conquered Territory",
                "Reverts to wilderness in " + TimeHelper.formatTime(WarForgeMod.FACTIONS.conqueredRemainingMs(to)),
                PacketClientNotification.COLOR_WARNING, 6000);
    }

    @SubscribeEvent
    public void OnMobSpawn(PotentialSpawns event) {
        ResourceKey<Level> dim = ((Level) event.getLevel()).dimension();
        ProtectionConfig config = GetProtections(Faction.nullUuid, new DimBlockPos(dim, event.getPos()));
        if (!config.ALLOW_MOB_SPAWNS) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void LivingUpdate(EnteringSection event) {
        if (!event.didChunkChange())
            return;

        if (event.getEntity() instanceof ServerPlayer player) {
            WarForgeMod.FACTIONS.updateAttackerSiegePresence(player);
            handleWarzoneBoundary(player, event.getOldPos(), event.getNewPos());
            notifyConqueredEntry(player, event.getOldPos(), event.getNewPos());
            return;
        }

        if (!inLoop) {
            if (!(event.getEntity() instanceof Player)) {
                Entity entity = event.getEntity();
                ProtectionConfig config = GetProtections(Faction.nullUuid, new DimBlockPos(entity.level().dimension(), entity.blockPosition()));
                if (!config.ALLOW_MOB_ENTRY) {
                    inLoop = true;
                    boolean wasNoClip = entity.noPhysics;
                    entity.noPhysics = true;
                    SectionPos oldPos = event.getOldPos();
                    SectionPos newPos = event.getNewPos();
                    entity.move(MoverType.SELF, new Vec3((oldPos.x() - newPos.x()), 0d, (oldPos.z() - newPos.z())));
                    entity.noPhysics = wasNoClip;
                    inLoop = false;
                }
            }
        }
    }
}

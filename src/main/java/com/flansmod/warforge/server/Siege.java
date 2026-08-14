package com.flansmod.warforge.server;

import com.flansmod.warforge.api.WarforgeAPI;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.IClaim;
import com.flansmod.warforge.common.blocks.TileEntitySiegeCamp;
import com.flansmod.warforge.common.network.PacketClientNotification;
import com.flansmod.warforge.common.network.PacketSiegeCampProgressUpdate;
import com.flansmod.warforge.common.network.SiegeCampProgressInfo;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Siege {
    public static final FactionStorage FACTION_STORAGE = WarForgeMod.FACTIONS;
    public UUID attackingFaction;
    public UUID defendingFaction;
    public ArrayList<DimBlockPos> attackingCamps;
    public DimBlockPos defendingClaim;
    public long timeRemainingMillis;
    public long siegeEndTimeStamp = 999L;
    public boolean finished = false; //Used for controlling whenever siege has been concluded, Does not require saving
    public int mBaseDifficulty = 5;
    // True for UI-declared sieges that have no physical siege camp block. Such sieges are anchored
    // logically at the chosen start-from chunk (stored as attackingCamps[0]) and run their
    // attacker-presence / abandon check from the server siege loop instead of a camp TileEntity.
    public boolean campless = false;
    private int battleRadius = WarForgeConfig.SIEGE_BATTLE_RADIUS; // outer "War" zone radius (kills count, foes cannot break)
    private int siegedRadius = WarForgeConfig.SIEGE_SIEGED_RADIUS; // inner "Sieged" zone radius (chunk protection disabled)
    /**
     * The base progress comes from passive sources and must be recalculated whenever checking progress.
     * Sources for the attackers are:
     * - Additional siege camps
     * Sources of the defenders are:
     * - Adjacent claims with differing support strengths
     * - Defender's flags on the defended claim
     */
    private int mExtraDifficulty = 0;
    /**
     * The attack progress is accumulated over time based on active actions in the area of the siege
     * Sources for the attackers are:
     * - Defender deaths in or around the siege
     * - Elapsed days with no defender logins
     * - Elapsed days (there is a constant pressure from the attacker that will eventually wear down the defenders unless they push back)
     * Sources for the defenders are:
     * - Attacker deaths in or around the siege
     * - Elapsed days with no attacker logins
     */
    private int mAttackProgress = 0;
    private int attackerAbsenceTicks = 0;
    private int mStallMultiplier = 1;
    private final HashSet<UUID> presentAttackers = new HashSet<>();
    private Boolean forcedAttackerVictory = null;

    public Siege() {
        attackingCamps = new ArrayList<>(4);
    }

    public Siege(UUID attacker, UUID defender, DimBlockPos defending, long time) {
        attackingCamps = new ArrayList<>(4);
        attackingFaction = attacker;
        defendingFaction = defender;
        defendingClaim = defending;
        this.timeRemainingMillis = time;

        mBaseDifficulty = WarForgeConfig.CLAIM_STRENGTH_BASIC;
        BlockEntity te = WarForgeMod.MC_SERVER.getLevel(defending.dim).getBlockEntity(defending.toRegularPos());
        if (te instanceof IClaim) {
            mBaseDifficulty = ((IClaim) te).getDefenceStrength();
        } else {
            Faction defenders = WarForgeMod.FACTIONS.getFaction(defender);
            if (defenders != null) {
                mBaseDifficulty = defenders.getClaimType(defending.toChunkPos()).defenceStrength;
            }
        }
    }

    public static boolean isPlayerInRadius(DimChunkPos centerChunkPos, DimChunkPos playerChunkPos) {
        return isPlayerInRadius(centerChunkPos, playerChunkPos, 1);
    }

    public static boolean isPlayerInRadius(DimChunkPos centerChunkPos, DimChunkPos playerChunkPos, int radius) {
        if (playerChunkPos.dim != centerChunkPos.dim) return false;

        // Check if the player's chunk coordinates are within a 3x3 chunk area
        int minChunkX = centerChunkPos.x - radius;
        int maxChunkX = centerChunkPos.x + radius;
        int minChunkZ = centerChunkPos.z - radius;
        int maxChunkZ = centerChunkPos.z + radius;

        // Check if the player's chunk coordinates are within the 3x3 area
        return (playerChunkPos.x >= minChunkX && playerChunkPos.x <= maxChunkX)
                && (playerChunkPos.z >= minChunkZ && playerChunkPos.z <= maxChunkZ);
    }

    // sends packet to client which clears all previously remembered sieges; identical attacking and def names = clear packet
    public static PacketSiegeCampProgressUpdate clearSiegeData() {
        PacketSiegeCampProgressUpdate clearSiegesPacket = new PacketSiegeCampProgressUpdate();
        clearSiegesPacket.info = new SiegeCampProgressInfo();
        clearSiegesPacket.info.expiredTicks = 0;
        clearSiegesPacket.info.attackingName = "c"; // normally attacking and def names cannot be identical
        clearSiegesPacket.info.defendingName = "c";
        clearSiegesPacket.info.attackingPos = DimBlockPos.ZERO;
        clearSiegesPacket.info.defendingPos = DimBlockPos.ZERO;

        return clearSiegesPacket;
    }

    // Sends the WarForge abandon notification (toast) to both factions plus a global announcement.
    // attackersDeserted = the attackers left the warzone (siege fails); otherwise the defenders left.
    public static void notifyAbandoned(Faction attacking, Faction defending, boolean attackersDeserted) {
        String attackerName = attacking != null ? attacking.name : "The attackers";
        String defenderName = defending != null ? defending.name : "The defenders";
        if (attackersDeserted) {
            WarForgeMod.notifyFaction(attacking, "warforge.siege_abandoned", "Siege Abandoned",
                    "Your faction left the siege warzone for too long.", PacketClientNotification.COLOR_DANGER, 8000);
            WarForgeMod.notifyFaction(defending, "warforge.siege_abandoned", "Siege Repelled",
                    attackerName + " abandoned their siege.", PacketClientNotification.COLOR_SUCCESS, 8000);
            WarForgeMod.INSTANCE.messageAll(Component.literal(attackerName + "'s siege on " + defenderName + " was abandoned."), true);
        } else {
            WarForgeMod.notifyFaction(defending, "warforge.siege_abandoned", "Defence Abandoned",
                    "Your faction left the warzone for too long.", PacketClientNotification.COLOR_DANGER, 8000);
            WarForgeMod.notifyFaction(attacking, "warforge.siege_abandoned", "Siege Won",
                    defenderName + " abandoned their defence.", PacketClientNotification.COLOR_SUCCESS, 8000);
            WarForgeMod.INSTANCE.messageAll(Component.literal(defenderName + " abandoned the defence against " + attackerName + "."), true);
        }
    }

    // Attack progress starts at 0 and can be moved to -GetDefenceThreshold() or mAttackSuccessThreshold
    public int GetAttackProgress() {
        return mAttackProgress;
    }

    public int getBattleRadius() {
        return battleRadius;
    }

    public void setBattleRadius(int battleRadius) {
        this.battleRadius = Math.max(0, battleRadius);
    }

    public int getSiegedRadius() {
        return siegedRadius;
    }

    public void setSiegedRadius(int siegedRadius) {
        this.siegedRadius = Math.max(0, siegedRadius);
    }

    public void setAttackProgress(int progress) {
        mAttackProgress = progress;
    }

    public int GetDefenceProgress() {
        return -mAttackProgress;
    }

    public int GetAttackSuccessThreshold() {
        return Math.max(1, mBaseDifficulty + mExtraDifficulty);
    }

    public int GetDefenceThreshold() {
        return Math.max(1, WarForgeConfig.SIEGE_DEFENCE_THRESHOLD);
    }

    public boolean isCompleted() {
        return hasForcedOutcome()
                || GetAttackProgress() >= GetAttackSuccessThreshold()
                || GetDefenceProgress() >= GetDefenceThreshold();
    }

    public void forceOutcome(boolean attackersWin) {
        forcedAttackerVictory = attackersWin;
    }

    public boolean hasForcedOutcome() {
        return forcedAttackerVictory != null;
    }

    public Set<DimChunkPos> getDefenderSiegedIsland() {
        if (defendingClaim == null) return Collections.emptySet();
        return WarForgeMod.FACTIONS.collectFactionIsland(defendingFaction, defendingClaim.toChunkPos());
    }

    public boolean isChunkInAttackerPresenceZone(DimChunkPos chunk) {
        for (DimBlockPos camp : attackingCamps) {
            if (camp != null && isPlayerInRadius(camp.toChunkPos(), chunk, WarForgeConfig.SIEGE_ATTACKER_RADIUS)) {
                return true;
            }
        }
        if (!defendingFaction.equals(WarForgeMod.FACTIONS.getClaim(chunk))) {
            return false;
        }
        return getDefenderSiegedIsland().contains(chunk);
    }

    public boolean hasPresentAttacker() {
        return !presentAttackers.isEmpty();
    }

    public void setAttackerPresent(UUID playerId, boolean present) {
        if (present) {
            presentAttackers.add(playerId);
        } else {
            presentAttackers.remove(playerId);
        }
    }

    public void initAttackerPresence() {
        presentAttackers.clear();
        Faction attackers = WarForgeMod.FACTIONS.getFaction(attackingFaction);
        if (attackers == null) {
            return;
        }
        for (Player player : attackers.getOnlinePlayers(p -> p != null && !p.isRemoved())) {
            DimChunkPos chunk = new DimChunkPos(player.level().dimension(), player.blockPosition());
            if (isChunkInAttackerPresenceZone(chunk)) {
                presentAttackers.add(player.getUUID());
            }
        }
    }

    public boolean WasSuccessful() {
        if (forcedAttackerVictory != null) {
            return forcedAttackerVictory;
        }
        return GetAttackProgress() >= GetAttackSuccessThreshold();
    }

    public SiegeCampProgressInfo GetSiegeInfo() {
        Faction attackers = WarForgeMod.FACTIONS.getFaction(attackingFaction);
        Faction defenders = WarForgeMod.FACTIONS.getFaction(defendingFaction);

        if (attackers == null || defenders == null) {
            WarForgeMod.LOGGER.error("Invalid factions in siege. Can't display info");
            return null;
        }

        if (attackingCamps.isEmpty() || attackingCamps.get(0) == null) {
            WarForgeMod.LOGGER.error("Siege on {} has no anchor camp. Can't display info", defendingClaim);
            return null;
        }

        SiegeCampProgressInfo info = new SiegeCampProgressInfo();
        info.attackingPos = attackingCamps.get(0);
        info.attackingName = attackers.name;
        info.attackingColour = attackers.colour;
        info.defendingPos = defendingClaim;
        info.defendingName = defenders.name;
        info.defendingColour = defenders.colour;
        info.battleRadius = battleRadius;
        info.siegedRadius = siegedRadius;
        info.defendingFactionId = defendingFaction;
        info.attackingFactionId = attackingFaction;
        info.attackerAbandonSeconds = computeAttackerAbandonSeconds();
        info.progress = GetAttackProgress();
        info.completionPoint = GetAttackSuccessThreshold();
        info.timeProgress = timeRemainingMillis;
        info.endTimestamp = siegeEndTimeStamp;
        info.finished = finished;

        return info;
    }

    // Seconds until the siege is considered abandoned by the attackers, derived from the camp closest
    // to deserting (the largest abandon timer across camps). 0 when attackers are present (not at risk).
    private int computeAttackerAbandonSeconds() {
        // Campless (UI-declared) sieges track attacker absence themselves rather than via a camp TE.
        if (campless) {
            if (!WarForgeConfig.SIEGE_DECLARE_REQUIRE_PRESENCE || attackerAbsenceTicks <= 0) return 0;
            return Math.max(0, WarForgeConfig.ATTACKER_DESERTION_TIMER - attackerAbsenceTicks / 20);
        }
        if (WarForgeMod.MC_SERVER == null) return 0;
        int maxTimerTicks = 0;
        for (DimBlockPos campPos : attackingCamps) {
            if (campPos == null) continue;
            Level world = WarForgeMod.MC_SERVER.getLevel(campPos.dim);
            if (world == null) continue;
            BlockEntity te = world.getBlockEntity(campPos.toRegularPos());
            if (te instanceof TileEntitySiegeCamp camp) {
                maxTimerTicks = Math.max(maxTimerTicks, camp.getAttackerAbandonTickTimer());
            }
        }
        if (maxTimerTicks <= 0) return 0;
        return Math.max(0, WarForgeConfig.ATTACKER_DESERTION_TIMER - maxTimerTicks / 20);
    }

    public boolean start() {
        Faction attackers = WarForgeMod.FACTIONS.getFaction(attackingFaction);
        Faction defenders = WarForgeMod.FACTIONS.getFaction(defendingFaction);
        recalculateEndTimestamp();

        if (attackers == null || defenders == null) {
            WarForgeMod.LOGGER.error("Invalid factions in siege. Cannot start");
            return false;
        }

        calculateBasePower();
        initAttackerPresence();
        defenders.isCurrentlyDefending = true;
        WarForgeMod.INSTANCE.messageAll(Component.literal(attackers.name + " started a siege against " + defenders.name), true);
        WarForgeMod.FACTIONS.sendSiegeStartNotifications(attackers, defenders, defendingClaim);
        WarForgeMod.FACTIONS.sendSiegeInfoToNearby(defendingClaim.toChunkPos());
        return true;
    }

    private void recalculateEndTimestamp() {
        siegeEndTimeStamp = WarForgeMod.siegeClock() + timeRemainingMillis;
    }

    public boolean updateSiegeTimer() {
        if (timeRemainingMillis <= 0) {
            int tickSwing = WarForgeConfig.SIEGE_SWING_PER_DAY_ELAPSED_BASE;
            if (GetDefenceProgress() > WarForgeConfig.SIEGE_STALL_ESCALATION_THRESHOLD) {
                tickSwing *= mStallMultiplier;
                mStallMultiplier = Math.min(mStallMultiplier * 2, 1024);
            } else {
                mStallMultiplier = 1;
            }
            mAttackProgress += tickSwing;

            long momentumTime = WarForgeConfig.SIEGE_MOMENTUM_TIME
                    .get(WarForgeMod.FACTIONS.getFaction(attackingFaction).getSiegeMomentum()) * 1000L;
            timeRemainingMillis = momentumTime;

            siegeEndTimeStamp = WarForgeMod.siegeClock() + timeRemainingMillis;

            WarForgeMod.FOBS.onSiegeTimerReset(this);

            WarForgeMod.FACTIONS.sendSiegeInfoToNearby(defendingClaim.toChunkPos());
            return true;
        } else {
            timeRemainingMillis -= 50L;

            long actualRemaining = siegeEndTimeStamp - WarForgeMod.siegeClock();
            if (Math.abs(actualRemaining - timeRemainingMillis) > 1000L) {
                timeRemainingMillis = actualRemaining;
            }
            return false;
        }
    }

    // For UI-declared (camp-less) sieges, the physical camp's attacker-presence engine does not
    // exist, so enforce a minimal equivalent here from the server siege loop: if no attacking member
    // stays within the attacker radius of the anchor chunk for the desertion timer, the siege is
    // abandoned. Returns true when the siege should conclude as an attacker failure. No-op for
    // camp-backed sieges (the camp TE handles them) and when presence is not required.
    public boolean tickCamplessPresence() {
        if (!campless || !WarForgeConfig.SIEGE_DECLARE_REQUIRE_PRESENCE) return false;
        if (attackingCamps.isEmpty() || attackingCamps.get(0) == null) return false;
        int limitTicks = WarForgeConfig.ATTACKER_DESERTION_TIMER * 20;
        if (limitTicks <= 0) return false; // 0 disables the timer rather than failing instantly
        if (hasPresentAttacker()) {
            if (attackerAbsenceTicks != 0) {
                attackerAbsenceTicks = 0;
                WarForgeMod.FACTIONS.sendSiegeInfoToNearby(defendingClaim.toChunkPos()); // clear HUD countdown
            }
            return false;
        }
        boolean abandoned = ++attackerAbsenceTicks >= limitTicks;
        if (attackerAbsenceTicks % 20 == 0) {
            WarForgeMod.FACTIONS.sendSiegeInfoToNearby(defendingClaim.toChunkPos()); // live HUD countdown
        }
        return abandoned;
    }

    public void AdvanceDay() {
        Faction attackers = WarForgeMod.FACTIONS.getFaction(attackingFaction);
        Faction defenders = WarForgeMod.FACTIONS.getFaction(defendingFaction);

        if (attackers == null || defenders == null) {
            WarForgeMod.LOGGER.error("Invalid factions in siege.");
            return;
        }

        calculateBasePower();
        float totalSwing = 0.0f;
        totalSwing += WarForgeConfig.SIEGE_SWING_PER_DAY_ELAPSED_BASE;
        if (!defenders.loggedInToday)
            totalSwing += WarForgeConfig.SIEGE_SWING_PER_DAY_ELAPSED_NO_DEFENDER_LOGINS;
        if (!attackers.loggedInToday)
            totalSwing -= WarForgeConfig.SIEGE_SWING_PER_DAY_ELAPSED_NO_ATTACKER_LOGINS;

        mAttackProgress += totalSwing;

        if (totalSwing > 0) {
            attackers.messageAll(Component.literal("Your siege on " + defenders.name + " at " + defendingClaim.toFancyString() + " shifted " + totalSwing + " points in your favour. The progress is now at " + GetAttackProgress() + "/" + GetAttackSuccessThreshold()));
            defenders.messageAll(Component.literal("The siege on " + defendingClaim.toFancyString() + " by " + attackers.name + " shifted " + totalSwing + " points in their favour. The progress is now at " + GetAttackProgress() + "/" + GetAttackSuccessThreshold()));
        } else if (totalSwing < 0) {
            defenders.messageAll(Component.literal("The siege on " + defendingClaim.toFancyString() + " by " + attackers.name + " shifted " + -totalSwing + " points in your favour. The progress is now at " + GetAttackProgress() + "/" + GetAttackSuccessThreshold()));
            attackers.messageAll(Component.literal("Your siege on " + defenders.name + " at " + defendingClaim.toFancyString() + " shifted " + -totalSwing + " points in their favour. The progress is now at " + GetAttackProgress() + "/" + GetAttackSuccessThreshold()));
        } else {
            defenders.messageAll(Component.literal("The siege on " + defendingClaim.toFancyString() + " by " + attackers.name + " did not shift today. The progress is at " + GetAttackProgress() + "/" + GetAttackSuccessThreshold()));
            attackers.messageAll(Component.literal("Your siege on " + defenders.name + " at " + defendingClaim.toFancyString() + " did not shift today. The progress is at " + GetAttackProgress() + "/" + GetAttackSuccessThreshold()));
        }

        FACTION_STORAGE.sendSiegeInfoToNearby(defendingClaim.toChunkPos());
    }

    public void calculateBasePower() {
        Faction attackers = WarForgeMod.FACTIONS.getFaction(attackingFaction);
        Faction defenders = WarForgeMod.FACTIONS.getFaction(defendingFaction);

        if (attackers == null || defenders == null || WarForgeMod.MC_SERVER == null) {
            WarForgeMod.LOGGER.error("Invalid factions in siege.");
            return;
        }

        // Add a point for each defender flag in place
        mExtraDifficulty = defenders.members.size() * WarForgeConfig.SIEGE_DIFF_PER_MEMBER;
        if (mExtraDifficulty > 5) {
            mExtraDifficulty = 5;
        }  // cap at 5

        DimChunkPos defendingChunk = defendingClaim.toChunkPos();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            DimChunkPos checkChunk = defendingChunk.Offset(direction, 1);
            UUID factionInChunk = WarForgeMod.FACTIONS.getClaim(checkChunk);
            // Sum up all additional attack claims
            if (factionInChunk.equals(attackingFaction)) {
                DimBlockPos claimBlockPos = attackers.getSpecificPosForClaim(checkChunk);
                if (claimBlockPos != null) {
                    BlockEntity te = WarForgeMod.MC_SERVER.getLevel(claimBlockPos.dim).getBlockEntity(claimBlockPos.toRegularPos());
                    if (te instanceof IClaim) {
                        mExtraDifficulty += ((IClaim) te).getAttackStrength();
                    }
                }
            }
            // Sum up all defending support claims
            if (factionInChunk.equals(defendingFaction)) {
                DimBlockPos claimBlockPos = defenders.getSpecificPosForClaim(checkChunk);
                if (claimBlockPos != null) {
                    BlockEntity te = WarForgeMod.MC_SERVER.getLevel(claimBlockPos.dim).getBlockEntity(claimBlockPos.toRegularPos());
                    if (te instanceof IClaim) {
                        mExtraDifficulty -= ((IClaim) te).getSupportStrength();
                    } else {
                        mExtraDifficulty -= defenders.getClaimType(checkChunk).supportStrength;
                    }
                }
            }
        }

        // Reinforcement from chunk-reinforcer tiles in the defender's loaded claimed chunks (WarForge 2.1.0 API).
        mExtraDifficulty += WarforgeAPI.getReinforcementBonus(defendingFaction, defendingClaim.toChunkPos());
    }

    // called when siege is ended for any reason and not detected as completed normally
    public void onCancelled() {
        FACTION_STORAGE.getFaction(defendingFaction).isCurrentlyDefending = false;
        // canceling is only run inside EndSiege, which is only run in TE, so no need for this to do anything
    }

    // called when natural conclusion of siege occurs, not called from TE itself
    public void onCompleted(boolean successful) {
        // for every attacking siege camp attempt to locate it, and if an actual siege camp handle appropriately
        finished = true;
        for (DimBlockPos siegeCampPos : attackingCamps) {
            if (siegeCampPos == null) continue;
            Level world = WarForgeMod.MC_SERVER.getLevel(siegeCampPos.dim);
            if (world == null) continue;
            BlockEntity siegeCamp = world.getBlockEntity(siegeCampPos.toRegularPos());
            if (siegeCamp != null) {
                if (siegeCamp instanceof TileEntitySiegeCamp) {
                    if (successful) ((TileEntitySiegeCamp) siegeCamp).cleanupPassedSiege();
                    else ((TileEntitySiegeCamp) siegeCamp).cleanupFailedSiege();
                }
            }
        }
    }

    // The zone in which kills/deaths count is the union of the War and Sieged zones, i.e. the larger radius.
    private boolean isChunkInKillZone(DimChunkPos chunkPos) {
        int killRadius = Math.max(battleRadius, siegedRadius);
        for (DimBlockPos siegeCamp : attackingCamps) {
            if (siegeCamp != null && isPlayerInRadius(siegeCamp.toChunkPos(), chunkPos, killRadius)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPlayerInKillZone(ServerPlayer player) {
        return isChunkInKillZone(new DimChunkPos(player.level().dimension(), player.blockPosition()));
    }

    // Outer War zone (kills count, foes cannot break) around each siege camp.
    public boolean isChunkInBattleZone(DimChunkPos chunkPos) {
        for (DimBlockPos siegeCamp : attackingCamps) {
            if (siegeCamp != null && isPlayerInRadius(siegeCamp.toChunkPos(), chunkPos, battleRadius)) {
                return true;
            }
        }
        return false;
    }

    // Inner Sieged zone (chunk protection disabled) around each siege camp.
    public boolean isChunkInSiegedZone(DimChunkPos chunkPos) {
        for (DimBlockPos siegeCamp : attackingCamps) {
            if (siegeCamp != null && isPlayerInRadius(siegeCamp.toChunkPos(), chunkPos, siegedRadius)) {
                return true;
            }
        }
        return false;
    }

    // Advances siege progress from a participant death inside the kill zone. killer may be null
    // (environmental/mob death). In PRECISE mode this only counts kills where an opposing player landed
    // the blow; in SIMPLE mode any attacker/defender death in the zone counts.
    public void onParticipantDeath(ServerPlayer killer, ServerPlayer killed) {
        Faction attackers = WarForgeMod.FACTIONS.getFaction(attackingFaction);
        Faction defenders = WarForgeMod.FACTIONS.getFaction(defendingFaction);
        Faction killedFaction = WarForgeMod.FACTIONS.getFactionOfPlayer(killed.getUUID());

        if (attackers == null || defenders == null || WarForgeMod.MC_SERVER == null) {
            WarForgeMod.LOGGER.error("Invalid factions in siege.");
            return;
        }

        boolean attackValid = false; // a defender died -> attackers gain progress
        boolean defendValid = false; // an attacker died -> defenders gain progress

        if (WarForgeConfig.SIEGE_KILL_DETECTION_MODE == WarForgeConfig.SiegeKillDetectionMode.SIMPLE) {
            // Any participant death inside the zone counts, regardless of the damage source.
            if (isPlayerInKillZone(killed)) {
                if (killedFaction == defenders) attackValid = true;
                else if (killedFaction == attackers) defendValid = true;
            }
        } else {
            // Confirmed PVP only: an opposing player must have landed the kill while in the zone.
            Faction killerFaction = killer == null ? null : WarForgeMod.FACTIONS.getFactionOfPlayer(killer.getUUID());
            if (killer != null && isPlayerInKillZone(killer)) {
                if (killerFaction == attackers && killedFaction == defenders) {
                    attackValid = true;
                } else if (killerFaction == defenders && killedFaction == attackers) {
                    defendValid = true;
                }
            }
        }

        if (!attackValid && !defendValid) return; // no more logic needs to be done for invalid kill

        mStallMultiplier = 1;

        // update progress appropriately; either valid attack, or def by this point, so state of one bool implies the state of the other
        mAttackProgress += attackValid ? WarForgeConfig.SIEGE_SWING_PER_DEFENDER_DEATH : -WarForgeConfig.SIEGE_SWING_PER_ATTACKER_DEATH;
        WarForgeMod.FACTIONS.sendSiegeInfoToNearby(defendingClaim.toChunkPos());

        // build notification
        Component notification = Component.translatable("warforge.notification.siege_death",
                killed.getName(), WarForgeConfig.SIEGE_SWING_PER_ATTACKER_DEATH,
                GetAttackProgress(), GetAttackSuccessThreshold(), GetDefenceProgress());

        // send notification
        attackers.messageAll(notification);
        defenders.messageAll(notification);
    }

    public void ReadFromNBT(CompoundTag tags) {
        attackingCamps.clear();

        // Get the attacker and defender
        attackingFaction = tags.getUUID("attacker");
        defendingFaction = tags.getUUID("defender");

        // Get the important locations; DimBlockPos now serialises as a compound {dim:String, pos:int[3]}
        ListTag claimList = tags.getList("attackLocations", Tag.TAG_COMPOUND);
        for (Tag base : claimList) {
            DimBlockPos pos = DimBlockPos.readFromNBT((CompoundTag) base);
            attackingCamps.add(pos);
        }

        defendingClaim = DimBlockPos.readFromNBT(tags, "defendLocation");
        battleRadius = tags.contains("battleRadius") ? Math.max(0, tags.getInt("battleRadius")) : WarForgeConfig.SIEGE_BATTLE_RADIUS;
        siegedRadius = tags.contains("siegedRadius") ? Math.max(0, tags.getInt("siegedRadius")) : WarForgeConfig.SIEGE_SIEGED_RADIUS;
        mAttackProgress = tags.getInt("progress");
        mBaseDifficulty = tags.getInt("baseDifficulty");
        mExtraDifficulty = tags.getInt("extraDifficulty");
        campless = tags.getBoolean("campless");
        attackerAbsenceTicks = tags.getInt("attackerAbsenceTicks");
        if (WarForgeConfig.SIEGE_ENABLE_NEW_TIMER) {

            timeRemainingMillis = tags.getLong("timeElapsed");
            recalculateEndTimestamp();
        }
    }

    public void WriteToNBT(CompoundTag tags) {
        // Set attacker / defender
        tags.putUUID("attacker", attackingFaction);
        tags.putUUID("defender", defendingFaction);

        // Set important locations
        ListTag claimsList = new ListTag();
        for (DimBlockPos pos : attackingCamps) {
            claimsList.add(pos.writeToNBT());
        }

        tags.put("attackLocations", claimsList);
        tags.put("defendLocation", defendingClaim.writeToNBT());
        tags.putInt("battleRadius", battleRadius);
        tags.putInt("siegedRadius", siegedRadius);
        tags.putInt("progress", mAttackProgress);
        tags.putInt("baseDifficulty", mBaseDifficulty);
        tags.putInt("extraDifficulty", mExtraDifficulty);
        tags.putLong("timeElapsed", timeRemainingMillis);
        tags.putBoolean("campless", campless);
        tags.putInt("attackerAbsenceTicks", attackerAbsenceTicks);
    }
}

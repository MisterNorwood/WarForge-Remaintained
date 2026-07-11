package com.flansmod.warforge.server;

import com.flansmod.warforge.api.Time;
import com.flansmod.warforge.api.vein.Quality;
import com.flansmod.warforge.api.vein.Vein;
import com.flansmod.warforge.common.Content;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.IClaim;
import com.flansmod.warforge.common.blocks.TileEntityBasicClaim;
import com.flansmod.warforge.common.blocks.TileEntityCitadel;
import com.flansmod.warforge.common.blocks.TileEntityIslandCollector;
import com.flansmod.warforge.common.blocks.TileEntityReinforcedClaim;
import com.flansmod.warforge.common.blocks.TileEntitySiegeCamp;
import com.flansmod.warforge.common.blocks.TileEntityYieldCollector;
import com.flansmod.warforge.common.util.FactionDisplay;
import com.flansmod.warforge.common.network.FactionDisplayInfo;
import com.flansmod.warforge.common.network.PlayerDisplayInfo;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Leaderboard.FactionStat;
import com.flansmod.warforge.server.fob.Fob;
import com.mojang.authlib.GameProfile;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static com.flansmod.warforge.common.WarForgeMod.VEIN_HANDLER;

/**
 * Data class for faction, responsible for storing faction info
 */
public class Faction {
    public static final UUID nullUuid = new UUID(0, 0);
    public UUID uuid;
    public int onlinePlayerCount = 0; // the number of current online players
    public long lastSiegeTimestamp = 0;
    public String name;
    public String flagId = "";
    public DimBlockPos citadelPos;
    public HashMap<DimBlockPos, Integer> claims;
    public HashMap<DimBlockPos, ClaimType> claimTypes;
    public HashSet<DimChunkPos> forcedChunks;
    public HashSet<DimBlockPos> islandCollectors;
    public ArrayList<Fob> fobs = new ArrayList<>();
    public HashMap<UUID, PlayerData> members;
    public HashSet<UUID> pendingInvites;
    public HashMap<UUID, Integer> killCounter;
    public ArrayList<ItemStack> insuranceStacks;
    public HashSet<UUID> allies;                 // mutually-allied faction UUIDs
    public HashSet<UUID> pendingAllianceRequests; // incoming alliance requests from other factions
    public boolean allowAllyInteraction = false;  // whether allies may interact in this faction's claims (CLAIM_ALLY)
    public HashMap<UUID, Long> truces;            // factionId -> truce expiry (epoch ms) after a broken alliance
    public boolean loggedInToday;
    public int colour = 0xFF_FF_FF;
    public int notoriety = 0;
    public int wealth = 0;
    public int legacy = 0;
    public short citadelLevel = 0;
    public long offlineRaidProtectionUntil = 0L;
    public boolean offlineRaidProtectionDisabled = false;
    // New-faction siege grace: unsiegeable while now < this. 0 = none (never granted, expired, or
    // forfeited by the faction starting a siege of its own).
    public long siegeGraceUntil = 0L;
    public int citadelMoveCooldown = 0;
    public boolean isCurrentlyDefending = false;
    //Only for new system
    public long citadelMoveTimeStamp = 0;
    private byte siegeMomentum = 0;
    @Getter
    private long momentumExpireryTimestamp = 0L;

    public Faction() {
        members = new HashMap<UUID, PlayerData>();
        pendingInvites = new HashSet<UUID>();
        claims = new HashMap<DimBlockPos, Integer>();
        claimTypes = new HashMap<DimBlockPos, ClaimType>();
        forcedChunks = new HashSet<DimChunkPos>();
        islandCollectors = new HashSet<DimBlockPos>();
        killCounter = new HashMap<UUID, Integer>();
        insuranceStacks = new ArrayList<ItemStack>();
        allies = new HashSet<UUID>();
        pendingAllianceRequests = new HashSet<UUID>();
        truces = new HashMap<UUID, Long>();
    }

    public static UUID createUUID(String factionName) {
        return new UUID(0xfedcba0987654321L, ((long) factionName.hashCode()) * 0xa0a0b1b1c2c2d3d3L);
    }

    private static String getPlayerName(UUID playerID) {
        Player player = getPlayer(playerID);
        return player.getName().getString();
    }

    // the map used in getPlayerByUUID removes players on logout
    private static Player getPlayer(UUID playerID) {
        return WarForgeMod.MC_SERVER.getPlayerList().getPlayer(playerID);
    }

    public boolean increaseSiegeMomentum(boolean message) {
        boolean increased = false;
        if (siegeMomentum < WarForgeConfig.SIEGE_MOMENTUM_MAX) {
            siegeMomentum++;
            increased = true;
        }
        momentumExpireryTimestamp = System.currentTimeMillis() + (long) WarForgeConfig.SIEGE_MOMENTUM_DURATION * 60 * 1000;
        if (increased) {
            long nextSiegeMillis = WarForgeConfig.SIEGE_MOMENTUM_TIME.get(siegeMomentum) * 1000;
            String formattedTime = new Time(nextSiegeMillis)
                    .getFormattedTime(Time.TimeFormat.MINUTES_SECONDS, Time.Verbality.FULL);

            if (increased && message) {
                MutableComponent msg = Component.literal("")
                        .append(Component.literal("Your power grows. Siege momentum increased to "))
                        .append(Component.literal(String.valueOf(siegeMomentum)).setStyle(Style.EMPTY.withBold(true).withColor(ChatFormatting.GOLD)))
                        .append(Component.literal(". Next siege will take: "))
                        .append(Component.literal(formattedTime).setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)));

                messageAll(msg);
            } else if (!increased && message) {
                MutableComponent msg = Component.literal("")
                        .append(Component.literal("You haven't gained any more momentum, however it's time was extended by another " + WarForgeConfig.SIEGE_MOMENTUM_DURATION + "minutes"));

                messageAll(msg);
            }
        }
        return increased;
    }

    public void stopMomentum(boolean message) {
        siegeMomentum = 0;
        momentumExpireryTimestamp = 0L;
        if(message){
            messageAll(Component.literal("Your extra momentum was lost"));
        }
    }

    public byte getSiegeMomentum() {
        if (System.currentTimeMillis() > momentumExpireryTimestamp)
            return 0;
        else
            return siegeMomentum;
    }

    public int getMemberCount() {
        return members.size();
    }

    public void update() {
        if (!loggedInToday) {
            for (HashMap.Entry<UUID, PlayerData> kvp : members.entrySet()) {
                loggedInToday = true;
            }
        }
    }

    // array list needed to be able to pre-allocate size, but not know if all players will pass check
    public ArrayList<Player> getOnlinePlayers(Predicate<Player> playerCondition) {
        ArrayList<Player> players = new ArrayList<>(members.size());
        //mMembers.keySet() seems to have a null default
        for (UUID playerID : members.keySet()) {
            Player player = getPlayer(playerID);
            if (playerCondition.test(player)) players.add(player);
        }

        return players;
    }


    public void increaseLegacy() {
        if (loggedInToday) {
            legacy += WarForgeConfig.LEGACY_PER_DAY;
        }
        loggedInToday = false;
        if (citadelMoveCooldown > 0) {
            citadelMoveCooldown--;
        }
    }

    public FactionDisplayInfo createInfo() {
        FactionDisplayInfo info = new FactionDisplayInfo();
        info.factionId = uuid;
        info.factionName = name;
        info.notoriety = notoriety;
        info.wealth = wealth;
        info.legacy = legacy;
        info.lvl = citadelLevel;

        info.legacyRank = WarForgeMod.LEADERBOARD.GetOneIndexedRankOf(this, FactionStat.LEGACY);
        info.notorietyRank = WarForgeMod.LEADERBOARD.GetOneIndexedRankOf(this, FactionStat.NOTORIETY);
        info.wealthRank = WarForgeMod.LEADERBOARD.GetOneIndexedRankOf(this, FactionStat.WEALTH);
        info.totalRank = WarForgeMod.LEADERBOARD.GetOneIndexedRankOf(this, FactionStat.TOTAL);

        info.mNumClaims = claims.size();
        info.mCitadelPos = citadelPos;

        for (HashMap.Entry<UUID, PlayerData> entry : members.entrySet()) {
            if (entry.getValue().role == Role.LEADER)
                info.mLeaderID = entry.getKey();

            PlayerDisplayInfo playerInfo = new PlayerDisplayInfo();
            GameProfile profile = WarForgeMod.MC_SERVER.getProfileCache().get(entry.getKey()).orElse(null);
            playerInfo.username = profile == null ? "Unknown Player" : profile.getName();
            playerInfo.playerUuid = entry.getKey();
            playerInfo.role = entry.getValue().role;
            info.members.add(playerInfo);

        }
        return info;
    }

    public void invitePlayer(UUID playerID) {
        // Don't invite offline players
        getPlayer(playerID);
        pendingInvites.add(playerID);
    }

    public boolean isInvitingPlayer(UUID playerID) {
        return pendingInvites.contains(playerID);
    }

    public void addPlayer(UUID playerID) {
        members.put(playerID, new PlayerData());
        WarForgeMod.FACTIONS.clearInvitesToPlayer(playerID);

        // Let everyone know
        messageAll(Component.literal(getPlayerName(playerID) + " joined " + name));


        // re-check number of online players
        onlinePlayerCount = getOnlinePlayers(entityPlayer -> true).size();

        FactionDisplay.refreshTabName(playerID);
    }

    // TODO:
    // Rank up
    // Rank down

    public boolean setLeader(UUID playerID) {
        if (!members.containsKey(playerID))
            return false;

        for (HashMap.Entry<UUID, PlayerData> entry : members.entrySet()) {
            // Set the target player as leader
            if (entry.getKey().equals(playerID))
                entry.getValue().role = Role.LEADER;
                // And set any existing leaders to officers
            else if (entry.getValue().role == Role.LEADER)
                entry.getValue().role = Role.OFFICER;
        }


        messageAll(Component.literal(getPlayerName(playerID) + " was made leader of " + name));
        return true;
    }

    public UUID getLeaderId() {
        for (HashMap.Entry<UUID, PlayerData> entry : members.entrySet()) {
            if (entry.getValue().role == Role.LEADER) {
                return entry.getKey();
            }
        }
        return Faction.nullUuid;
    }

    public void removePlayer(UUID playerID) {
        members.remove(playerID);
        FactionDisplay.refreshTabName(playerID);
    }

    public void disband() {
        // Clean up remaining claims
        for (Map.Entry<DimBlockPos, Integer> kvp : claims.entrySet()) {
            ServerLevel world = WarForgeMod.MC_SERVER.getLevel(kvp.getKey().dim);
            world.removeBlock(kvp.getKey().toRegularPos(), false);
        }

        ServerLevel world = WarForgeMod.MC_SERVER.getLevel(citadelPos.dim);
        this.citadelLevel = 0;
        world.removeBlock(citadelPos.toRegularPos(), false);
        for (DimBlockPos collectorPos : islandCollectors) {
            ServerLevel collectorWorld = WarForgeMod.MC_SERVER.getLevel(collectorPos.dim);
            if (collectorWorld != null) {
                collectorWorld.removeBlock(collectorPos.toRegularPos(), false);
            }
        }


        String message = getMemberCount() > 0 ? name + " was disbanded." : name + " was abandoned and disbanded";
        WarForgeMod.FACTIONS.sendDisbandNotification(this);
        messageAll(Component.literal(message));
        java.util.List<UUID> formerMembers = new java.util.ArrayList<>(members.keySet());
        members.clear();
        for (UUID formerMember : formerMembers) {
            FactionDisplay.refreshTabName(formerMember);
        }
        claims.clear();
        claimTypes.clear();
        forcedChunks.clear();
        islandCollectors.clear();
        for (Fob fob : new ArrayList<>(fobs)) {
            WarForgeMod.FOBS.removeFob(fob);
        }
        fobs.clear();
        pendingInvites.clear();
        insuranceStacks.clear();
        allies.clear();
        pendingAllianceRequests.clear();
        truces.clear();
    }

    public boolean isPlayerInFaction(UUID playerID) {
        return members.containsKey(playerID);
    }

    public boolean isAllyOf(UUID factionID) {
        return factionID != null && allies.contains(factionID);
    }

    // True while a post-break truce with the given faction is still active. Expired truces are pruned lazily.
    public boolean isInTruceWith(UUID factionID) {
        if (factionID == null) {
            return false;
        }
        Long expiry = truces.get(factionID);
        if (expiry == null) {
            return false;
        }
        if (expiry <= System.currentTimeMillis()) {
            truces.remove(factionID);
            return false;
        }
        return true;
    }

    public long getTruceRemainingMs(UUID factionID) {
        Long expiry = truces.get(factionID);
        return expiry == null ? 0L : Math.max(0L, expiry - System.currentTimeMillis());
    }

    public boolean canPlaceClaim() {
        if (WarForgeConfig.ENABLE_CITADEL_UPGRADES) {
            int claimLimitForLevel = WarForgeMod.UPGRADE_HANDLER.getClaimLimitForLevel(citadelLevel);
            if (claimLimitForLevel != -1 && claims.size() >= claimLimitForLevel) {
                return false;
            }
        }
        if (WarForgeConfig.MAX_CLAIMS_PER_FACTION >= 0 && claims.size() >= WarForgeConfig.MAX_CLAIMS_PER_FACTION) {
            return false;
        }
        return true;
    }

    public int getMaxForceLoadedChunks() {
        if (WarForgeConfig.ENABLE_CITADEL_UPGRADES) {
            return Math.max(0, WarForgeMod.UPGRADE_HANDLER.getLoadedChunksForLevel(citadelLevel));
        }
        return Math.max(0, WarForgeConfig.FORCE_LOADED_CHUNKS_TOTAL);
    }

    public int getMaxFobs() {
        if (WarForgeConfig.ENABLE_CITADEL_UPGRADES) {
            return Math.max(0, WarForgeMod.UPGRADE_HANDLER.getMaxFobsForLevel(citadelLevel));
        }
        return Math.max(0, WarForgeConfig.MAX_FOBS);
    }

    public int getFobTicketLimit() {
        if (WarForgeConfig.ENABLE_CITADEL_UPGRADES) {
            return Math.max(0, WarForgeMod.UPGRADE_HANDLER.getFobTicketLimitForLevel(citadelLevel));
        }
        return Math.max(0, WarForgeConfig.FOB_TICKET_LIMIT);
    }

    public int getFobRegenPerSiegeTick() {
        if (WarForgeConfig.ENABLE_CITADEL_UPGRADES) {
            return Math.max(0, WarForgeMod.UPGRADE_HANDLER.getFobRegenForLevel(citadelLevel));
        }
        return Math.max(0, WarForgeConfig.FOB_TICKET_REGEN_PER_SIEGE_TICK);
    }

    public int getInsuranceSlotCount() {
        return Math.max(insuranceStacks.size(), WarForgeMod.UPGRADE_HANDLER.getInsuranceSlotsForLevel(citadelLevel));
    }

    public ItemStack getInsuranceStack(int slot) {
        ensureInsuranceSize(slot + 1);
        return insuranceStacks.get(slot);
    }

    public void setInsuranceStack(int slot, ItemStack stack) {
        ensureInsuranceSize(slot + 1);
        insuranceStacks.set(slot, stack == null ? ItemStack.EMPTY : stack);
    }

    public boolean hasInsuranceContents() {
        for (ItemStack stack : insuranceStacks) {
            if (stack != null && !stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public ArrayList<ItemStack> drainInsuranceContents() {
        ArrayList<ItemStack> contents = new ArrayList<ItemStack>();
        for (int i = 0; i < insuranceStacks.size(); i++) {
            ItemStack stack = insuranceStacks.get(i);
            if (stack != null && !stack.isEmpty()) {
                contents.add(stack.copy());
                insuranceStacks.set(i, ItemStack.EMPTY);
            }
        }
        return contents;
    }

    private void ensureInsuranceSize(int targetSize) {
        while (insuranceStacks.size() < targetSize) {
            insuranceStacks.add(ItemStack.EMPTY);
        }
    }

    public boolean canForceLoadMore() {
        return forcedChunks.size() < getMaxForceLoadedChunks();
    }

    public boolean isPlayerRoleInFaction(UUID playerID, Role role) {
        if (members.containsKey(playerID)) {
            Role thierRole = members.get(playerID).role;
            return thierRole.ordinal() >= role.ordinal();
        }
        return false;
    }

    public boolean isPlayerOutrankingOfficer(UUID playerID, UUID targetID) {
        if (members.containsKey(playerID) && members.containsKey(targetID)) {
            Role playerRole = members.get(playerID).role;
            Role targetRole = members.get(targetID).role;
            return playerRole.ordinal() >= Role.OFFICER.ordinal()
                    && playerRole.ordinal() > targetRole.ordinal();
        }
        return false;
    }

    public void onClaimPlaced(IClaim claim) {
        claims.put(claim.getClaimPos(), 0);
        claimTypes.put(claim.getClaimPos(), ClaimType.fromClaim(claim));
        wealth += chunkWealth(claim.getClaimPos().toChunkPos());
    }

    // for methods where claim block is actually being removed
    public void onClaimLost(DimBlockPos claimBlockPos) {
        onClaimLost(claimBlockPos, false, true);
    }

    // avoids duplication of claim block on siege capture, as the way capture is done is
    // by losing the claim (this method) and then creating another in its place
    public void onClaimLost(DimBlockPos claimBlockPos, boolean captureAttempted) {
        onClaimLost(claimBlockPos, captureAttempted, true);
    }

    public void onClaimLost(DimBlockPos claimBlockPos, boolean captureAttempted, boolean notifyLoss) {
        boolean removedForceLoad = forcedChunks.remove(claimBlockPos.toChunkPos());
        // Destroy our claim block if this claim has a physical block.
        ServerLevel world = WarForgeMod.MC_SERVER.getLevel(claimBlockPos.dim);
        BlockState claimBlock = world.getBlockState(claimBlockPos.toRegularPos());
        if (WarForgeMod.isClaim(claimBlock.getBlock(), Content.statue, Content.dummyTranslusent)) {
            ItemStack drop = new ItemStack(claimBlock.getBlock().asItem());
            world.removeBlock(claimBlockPos.toRegularPos(), false);
            if (!captureAttempted || !WarForgeConfig.SIEGE_CAPTURE) {
                world.addFreshEntity(new ItemEntity(
                        world,
                        claimBlockPos.getX() + 0.5d,
                        claimBlockPos.getY() + 0.5d,
                        claimBlockPos.getZ() + 0.5d,
                        drop
                ));
            }
        }

        // Uh oh
        if (claimBlockPos.equals(citadelPos)) {
            for (Fob fob : new ArrayList<>(fobs)) {
                WarForgeMod.FOBS.removeFob(fob);
            }
            fobs.clear();
            WarForgeMod.FACTIONS.FactionDefeated(this);
            WarForgeMod.INSTANCE.messageAll(Component.literal(name + "'s citadel was destroyed. " + name + " is no more."), true);
        } else {
            if (notifyLoss) {
                messageAll(Component.literal("Our faction lost a claim at " + claimBlockPos.toFancyString()));
                WarForgeMod.FACTIONS.sendClaimChangedNotification(
                        this,
                        "claim_lost_" + claimBlockPos,
                        "Claim Lost",
                        "Your faction lost the claim at " + claimBlockPos.toFancyString(),
                        0xB34747
                );
            }

            claims.remove(claimBlockPos);
            claimTypes.remove(claimBlockPos);
            wealth -= chunkWealth(claimBlockPos.toChunkPos());
            ArrayList<DimBlockPos> removedCollectors = new ArrayList<DimBlockPos>();
            for (DimBlockPos collectorPos : islandCollectors) {
                if (collectorPos.toChunkPos().equals(claimBlockPos.toChunkPos())) {
                    removedCollectors.add(collectorPos);
                    ServerLevel collectorWorld = WarForgeMod.MC_SERVER.getLevel(collectorPos.dim);
                    if (collectorWorld != null) {
                        collectorWorld.removeBlock(collectorPos.toRegularPos(), false);
                    }
                }
            }
            if (!removedCollectors.isEmpty()) {
                islandCollectors.removeAll(removedCollectors);
                WarForgeMod.CHUNK_LOADING_MANAGER.refreshFactionChunks(this);
            } else if (removedForceLoad) {
                WarForgeMod.CHUNK_LOADING_MANAGER.refreshFactionChunks(this);
            }
        }

    }

    public void claimNoTileEntity(DimChunkPos pos) {//Intetesting
        claimNoTileEntity(pos, 0, ClaimType.BASIC);
    }

    public void claimNoTileEntity(DimChunkPos pos, int y) {
        claimNoTileEntity(pos, y, ClaimType.BASIC);
    }

    public void claimNoTileEntity(DimChunkPos pos, int y, ClaimType claimType) {
        DimBlockPos blockPos = new DimBlockPos(pos.dim, pos.getMinBlockX(), y, pos.getMinBlockZ());
        claims.put(blockPos, 0);
        claimTypes.put(blockPos, claimType);
        wealth += chunkWealth(pos);
    }

    public ClaimType getClaimType(DimChunkPos pos) {
        DimBlockPos claimPos = getSpecificPosForClaim(pos);
        if (claimPos == null) {
            return ClaimType.NONE;
        }
        return claimTypes.getOrDefault(claimPos, claimPos.equals(citadelPos) ? ClaimType.CITADEL : ClaimType.BASIC);
    }


    // Messaging
    public void messageAll(Component chat) {
        for (UUID playerID : members.keySet()) {
            final Player player = getPlayer(playerID);
            if (player != null)
                player.sendSystemMessage(chat);
        }
    }

    // Plays sound to everyone within faction
    public void soundEffectAll(SoundEvent soundEvent, float volume, float pitch) {
        for (UUID playerID : members.keySet()) {
            final ServerPlayer player = (ServerPlayer) getPlayer(playerID);
            if (player != null && !player.hasDisconnected()) {
                player.connection.send(new ClientboundSoundPacket(
                        Holder.direct(soundEvent),
                        SoundSource.PLAYERS,
                        player.getX(), player.getY(), player.getZ(),
                        volume, pitch,
                        0L
                ));
            }
        }
    }

    public void soundEffectAll(SoundEvent soundEvent) {
        soundEffectAll(soundEvent, 1f, 1f);
    }

    public DimBlockPos getSpecificPosForClaim(DimChunkPos pos) {
        for (DimBlockPos claimPos : claims.keySet()) {
            if (claimPos.toChunkPos().equals(pos))
                return claimPos;
        }
        return null;
    }

    // Wealth contributed by a single claimed chunk: the wealth of the yield-providing vein it holds, or
    // 0 if the chunk has no vein. Returns 0 until the vein handler is ready (startup recompute covers it).
    public static int chunkWealth(DimChunkPos chunk) {
        if (VEIN_HANDLER == null || !VEIN_HANDLER.hasFinishedInit || WarForgeMod.MC_SERVER == null) {
            return 0;
        }
        ServerLevel level = WarForgeMod.MC_SERVER.getLevel(chunk.dim);
        if (level == null) {
            level = WarForgeMod.MC_SERVER.overworld();
        }
        if (level == null) {
            return 0;
        }
        Pair<Vein, Quality> veinInfo = VEIN_HANDLER.getVein(chunk.dim, chunk.x, chunk.z, level.getSeed());
        return veinInfo == null || veinInfo.getLeft() == null ? 0 : veinInfo.getLeft().wealth;
    }

    // Full recompute of wealth from current claims. Used on server start (migration / vein-config
    // changes); runtime claim gain/loss adjusts wealth incrementally instead to avoid redundant scans.
    public void recalculateWealth() {
        int total = 0;
        for (DimBlockPos claimPos : claims.keySet()) {
            total += chunkWealth(claimPos.toChunkPos());
        }
        wealth = total;
    }

    public void awardYields() {
        for (HashMap.Entry<DimBlockPos, Integer> kvp : claims.entrySet()) {
            DimBlockPos pos = kvp.getKey();
            ServerLevel world = WarForgeMod.MC_SERVER.getLevel(pos.dim);
            kvp.setValue(kvp.getValue() + 1);  // increment number of yields

            // If It's loaded and the handler is ready, try to process yields
            if (world.isLoaded(pos.toRegularPos()) && VEIN_HANDLER != null && VEIN_HANDLER.hasFinishedInit) {
                BlockEntity te = world.getBlockEntity(pos.toRegularPos());
                if (te instanceof TileEntityYieldCollector) {
                    ((TileEntityYieldCollector) te).processYield(claims);
                }
            }
        }

        // Hidden claims do not have local inventories. Island collectors gather pending yields.
        if (islandCollectors.isEmpty()) {
            return;
        }

        ArrayList<DimBlockPos> staleCollectors = null;
        for (DimBlockPos collectorPos : islandCollectors) {
            ServerLevel world = WarForgeMod.MC_SERVER.getLevel(collectorPos.dim);
            if (world == null || !world.isLoaded(collectorPos.toRegularPos())) {
                continue;
            }

            BlockEntity te = world.getBlockEntity(collectorPos.toRegularPos());
            if (te instanceof TileEntityIslandCollector collector) {
                collector.processIslandYields(this);
            } else {
                if (staleCollectors == null) {
                    staleCollectors = new ArrayList<DimBlockPos>();
                }
                staleCollectors.add(collectorPos);
            }
        }

        if (staleCollectors != null) {
            islandCollectors.removeAll(staleCollectors);
            WarForgeMod.CHUNK_LOADING_MANAGER.refreshFactionChunks(this);
        }
    }

    public void promote(UUID playerID) {
        PlayerData data = members.get(playerID);
        if (data != null) {
            if (data.role == Role.MEMBER) {
                data.role = Role.OFFICER;
                GameProfile profile = WarForgeMod.MC_SERVER.getProfileCache().get(playerID).orElse(null);
                if (profile != null)
                    messageAll(Component.literal(profile.getName() + " was promoted to officer"));
            }
        }
    }

    public void demote(UUID playerID) {
        PlayerData data = members.get(playerID);
        if (data != null) {
            if (data.role == Role.OFFICER) {
                data.role = Role.MEMBER;
                GameProfile profile = WarForgeMod.MC_SERVER.getProfileCache().get(playerID).orElse(null);
                if (profile != null)
                    messageAll(Component.literal(profile.getName() + " was demoted to member"));
            }
        }
    }


    public void setColour(int colour) {
        this.colour = colour;
    }


    public void readFromNBT(CompoundTag tags) {
        claims.clear();
        claimTypes.clear();
        forcedChunks.clear();
        islandCollectors.clear();
        fobs.clear();
        members.clear();
        pendingInvites.clear();
        allies.clear();
        pendingAllianceRequests.clear();
        truces.clear();

        // Get citadel pos and defining params
        uuid = tags.getUUID("uuid");
        name = tags.getString("name");
        colour = tags.getInt("colour");
        citadelLevel = tags.getShort("citadel_lvl");

        // Get our claims and citadel
        citadelPos = DimBlockPos.readFromNBT(tags, "citadelPos");


        ListTag claimList = tags.getList("claims", Tag.TAG_COMPOUND);
        for (Tag base : claimList) {
            CompoundTag claimInfo = (CompoundTag) base;
            DimBlockPos pos = DimBlockPos.readFromNBT(claimInfo.getCompound("pos"));
            int pendingYields = claimInfo.getInt("pendingYields");
            claims.put(pos, pendingYields);
            ClaimType claimType = ClaimType.fromSerialized(claimInfo.getString("type"));
            if (claimType == ClaimType.NONE && pos.equals(citadelPos)) {
                claimType = ClaimType.CITADEL;
            }
            claimTypes.put(pos, claimType);
        }
        // Neutral zones (SafeZone / WarZone) are citadel-less: they have no real citadel, so skip the
        // force-claim that would otherwise pin a phantom claim onto their (0,0,0) sentinel citadelPos.
        if (FactionStorage.IsNeutralZone(uuid)) {
            // Drop any legacy phantom claim a pre-rework save force-pinned at the sentinel citadelPos
            // (always serialized as a CITADEL claim; real zone claims are ADMIN / WARZONE).
            if (claimTypes.get(citadelPos) == ClaimType.CITADEL) {
                claims.remove(citadelPos);
                claimTypes.remove(citadelPos);
            }
        } else if (!claims.containsKey(citadelPos)) {
            WarForgeMod.LOGGER.error("Citadel was not claimed by the faction. Forcing claim");
            claims.put(citadelPos, 0);
            claimTypes.put(citadelPos, ClaimType.CITADEL);
        }

        ListTag killList = tags.getList("kills", Tag.TAG_COMPOUND);
        for (Tag base : killList) {
            CompoundTag killInfo = (CompoundTag) base;
            UUID uuid = killInfo.getUUID("id");
            int kills = killInfo.getInt("count");

            killCounter.put(uuid, kills);
        }

        ListTag forceLoadList = tags.getList("forcedChunks", Tag.TAG_COMPOUND);
        for (Tag base : forceLoadList) {
            DimChunkPos chunkPos = readChunkPosFromNBT((CompoundTag) base);
            if (chunkPos != null) {
                forcedChunks.add(chunkPos);
            }
        }

        ListTag collectorList = tags.getList("collectors", Tag.TAG_COMPOUND);
        for (Tag base : collectorList) {
            DimBlockPos collectorPos = DimBlockPos.readFromNBT((CompoundTag) base);
            if (!collectorPos.equals(DimBlockPos.ZERO)) {
                islandCollectors.add(collectorPos);
            }
        }

        ListTag fobList = tags.getList("fobs", Tag.TAG_COMPOUND);
        for (Tag base : fobList) {
            Fob fob = new Fob();
            fob.readFromNBT((CompoundTag) base);
            fob.ownerFaction = uuid;
            if (!fob.pos.equals(DimBlockPos.ZERO)) {
                fobs.add(fob);
            }
        }


        flagId = tags.getString("flagId");

        // Get gameplay params
        notoriety = tags.getInt("notoriety");
        wealth = tags.getInt("wealth");
        legacy = tags.getInt("legacy");

        offlineRaidProtectionUntil = tags.getLong("offlineRaidProtectionUntil");
        offlineRaidProtectionDisabled = tags.getBoolean("offlineRaidProtectionDisabled");
        siegeGraceUntil = tags.getLong("siegeGraceUntil");
        citadelMoveCooldown = tags.getInt("citadelMoveCooldown");
        citadelMoveTimeStamp = tags.getLong("citadelMoveTimestamp");
        lastSiegeTimestamp = tags.getLong("lastSiegeTimestamp");
        siegeMomentum = tags.getByte("siegeMomentum");
        momentumExpireryTimestamp = tags.getLong("momentumExpireryTimestamp");
        isCurrentlyDefending = tags.getBoolean("isDefending");


        // Get member data
        ListTag memberList = tags.getList("members", Tag.TAG_COMPOUND);
        for (Tag base : memberList) {
            CompoundTag memberTags = (CompoundTag) base;
            UUID uuid = memberTags.getUUID("uuid");
            PlayerData data = new PlayerData();
            data.readFromNBT(memberTags);
            members.put(uuid, data);

            // Fixup for old data
            if (data.flagPosition.equals(DimBlockPos.ZERO)) {
                data.flagPosition = citadelPos;
            }
        }

        ListTag inviteList = tags.getList("pendingInvites", Tag.TAG_COMPOUND);
        for (Tag base : inviteList) {
            CompoundTag inviteTags = (CompoundTag) base;
            pendingInvites.add(inviteTags.getUUID("uuid"));
        }

        allowAllyInteraction = tags.getBoolean("allowAllyInteraction");
        ListTag allyList = tags.getList("allies", Tag.TAG_COMPOUND);
        for (Tag base : allyList) {
            allies.add(((CompoundTag) base).getUUID("uuid"));
        }
        ListTag allyRequestList = tags.getList("pendingAllianceRequests", Tag.TAG_COMPOUND);
        for (Tag base : allyRequestList) {
            pendingAllianceRequests.add(((CompoundTag) base).getUUID("uuid"));
        }
        ListTag truceList = tags.getList("truces", Tag.TAG_COMPOUND);
        for (Tag base : truceList) {
            CompoundTag truceTag = (CompoundTag) base;
            truces.put(truceTag.getUUID("uuid"), truceTag.getLong("expiry"));
        }

        insuranceStacks.clear();
        ListTag insuranceList = tags.getList("insurance", Tag.TAG_COMPOUND);
        for (Tag base : insuranceList) {
            CompoundTag insuranceTag = (CompoundTag) base;
            int slot = insuranceTag.getInt("slot");
            ItemStack stack = ItemStack.of(insuranceTag.getCompound("stack"));
            ensureInsuranceSize(slot + 1);
            insuranceStacks.set(slot, stack);
        }
    }

    public void writeToNBT(CompoundTag tags) {
        // Set citadel pos and core params
        tags.putUUID("uuid", uuid);
        tags.putString("name", name);
        tags.putString("flagId", flagId);
        tags.putInt("colour", colour);
        tags.putShort("citadel_lvl", citadelLevel);

        // Set claims
        ListTag claimsList = new ListTag();
        for (HashMap.Entry<DimBlockPos, Integer> kvp : claims.entrySet()) {
            CompoundTag claimTags = new CompoundTag();
            claimTags.put("pos", kvp.getKey().writeToNBT());
            claimTags.putInt("pendingYields", kvp.getValue());
            claimTags.putString("type", claimTypes.getOrDefault(kvp.getKey(), kvp.getKey().equals(citadelPos) ? ClaimType.CITADEL : ClaimType.BASIC).serializedName);

            claimsList.add(claimTags);
        }
        tags.put("claims", claimsList);
        citadelPos.writeToNBT(tags, "citadelPos");

        ListTag killsList = new ListTag();
        for (HashMap.Entry<UUID, Integer> kvp : killCounter.entrySet()) {
            CompoundTag killTags = new CompoundTag();
            killTags.putUUID("id", kvp.getKey());
            killTags.putInt("count", kvp.getValue());

            killsList.add(killTags);
        }
        tags.put("kills", killsList);

        ListTag forceLoadList = new ListTag();
        for (DimChunkPos chunkPos : forcedChunks) {
            forceLoadList.add(writeChunkPosToNBT(chunkPos));
        }
        tags.put("forcedChunks", forceLoadList);

        ListTag collectorsList = new ListTag();
        for (DimBlockPos collectorPos : islandCollectors) {
            collectorsList.add(collectorPos.writeToNBT());
        }
        tags.put("collectors", collectorsList);

        ListTag fobsList = new ListTag();
        for (Fob fob : fobs) {
            if (fob.pos.equals(DimBlockPos.ZERO)) {
                continue;
            }
            CompoundTag fobTags = new CompoundTag();
            fob.writeToNBT(fobTags);
            fobsList.add(fobTags);
        }
        tags.put("fobs", fobsList);

        // Set gameplay params
        tags.putInt("notoriety", notoriety);
        tags.putInt("wealth", wealth);
        tags.putInt("legacy", legacy);

        tags.putLong("offlineRaidProtectionUntil", offlineRaidProtectionUntil);
        tags.putBoolean("offlineRaidProtectionDisabled", offlineRaidProtectionDisabled);
        tags.putLong("siegeGraceUntil", siegeGraceUntil);
        tags.putInt("citadelMoveCooldown", citadelMoveCooldown);
        tags.putLong("citadelMoveTimestamp", citadelMoveTimeStamp);
        tags.putLong("lastSiegeTimestamp", lastSiegeTimestamp);

        tags.putByte("siegeMomentum", siegeMomentum);
        tags.putLong("momentumExpireryTimestamp", momentumExpireryTimestamp);

        // Add member data
        ListTag memberList = new ListTag();
        for (HashMap.Entry<UUID, PlayerData> kvp : members.entrySet()) {
            CompoundTag memberTags = new CompoundTag();
            memberTags.putUUID("uuid", kvp.getKey());
            kvp.getValue().writeToNBT(memberTags);
            memberList.add(memberTags);
        }
        tags.put("members", memberList);

        ListTag inviteList = new ListTag();
        for (UUID invitee : pendingInvites) {
            CompoundTag inviteTags = new CompoundTag();
            inviteTags.putUUID("uuid", invitee);
            inviteList.add(inviteTags);
        }
        tags.put("pendingInvites", inviteList);

        tags.putBoolean("allowAllyInteraction", allowAllyInteraction);
        ListTag allyList = new ListTag();
        for (UUID ally : allies) {
            CompoundTag allyTag = new CompoundTag();
            allyTag.putUUID("uuid", ally);
            allyList.add(allyTag);
        }
        tags.put("allies", allyList);
        ListTag allyRequestList = new ListTag();
        for (UUID requester : pendingAllianceRequests) {
            CompoundTag requestTag = new CompoundTag();
            requestTag.putUUID("uuid", requester);
            allyRequestList.add(requestTag);
        }
        tags.put("pendingAllianceRequests", allyRequestList);
        ListTag truceList = new ListTag();
        for (Map.Entry<UUID, Long> entry : truces.entrySet()) {
            CompoundTag truceTag = new CompoundTag();
            truceTag.putUUID("uuid", entry.getKey());
            truceTag.putLong("expiry", entry.getValue());
            truceList.add(truceTag);
        }
        tags.put("truces", truceList);

        ListTag insuranceList = new ListTag();
        for (int i = 0; i < insuranceStacks.size(); i++) {
            ItemStack stack = insuranceStacks.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            CompoundTag insuranceTag = new CompoundTag();
            insuranceTag.putInt("slot", i);
            CompoundTag stackTag = new CompoundTag();
            stack.save(stackTag);
            insuranceTag.put("stack", stackTag);
            insuranceList.add(insuranceTag);
        }
        tags.put("insurance", insuranceList);
        tags.putBoolean("isDefending", isCurrentlyDefending);
    }

    // forced-chunk persistence: dimension is a ResourceKey<Level> stored as its location string
    private static CompoundTag writeChunkPosToNBT(DimChunkPos chunkPos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", chunkPos.dim.location().toString());
        tag.putIntArray("pos", new int[]{chunkPos.x, chunkPos.z});
        return tag;
    }

    private static DimChunkPos readChunkPosFromNBT(CompoundTag tag) {
        if (tag.contains("dim") && tag.contains("pos")) {
            int[] data = tag.getIntArray("pos");
            if (data.length == 2) {
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("dim")));
                return new DimChunkPos(dim, data[0], data[1]);
            }
        }
        return null;
    }

    // checks all stored claim locations to check if they are siege blocks
    public int calcNumSieges() {
        int result = 0;
        for (DimBlockPos claimPos : claims.keySet())
            if (WarForgeMod.FACTIONS.getSieges().get(claimPos) != null) ++result;
        return result;
    }

    public enum Role {
        GUEST, // ?
        MEMBER,
        OFFICER,
        LEADER,
    }

    public enum ClaimType {
        NONE("none", "", 0, 0),
        BASIC("basic", "B", WarForgeConfig.CLAIM_STRENGTH_BASIC, WarForgeConfig.SUPPORT_STRENGTH_BASIC),
        REINFORCED("reinforced", "R", WarForgeConfig.CLAIM_STRENGTH_REINFORCED, WarForgeConfig.SUPPORT_STRENGTH_REINFORCED),
        CITADEL("citadel", "C", WarForgeConfig.CLAIM_STRENGTH_CITADEL, WarForgeConfig.SUPPORT_STRENGTH_CITADEL),
        // ADMIN backs the SafeZone special claim; WARZONE backs the WarZone special claim. Both are
        // citadel-less, blockless, unsiegeable zone claims whose protection is driven by their owning
        // pseudo-faction UUID (see WarForgeConfig.SAFE_ZONE / WAR_ZONE), not by these strengths.
        ADMIN("admin", "A", 0, 0),
        WARZONE("warzone", "W", 0, 0),
        SIEGE("siege", "S", 0, 0);

        public final String serializedName;
        public final String shortLabel;
        public final int defenceStrength;
        public final int supportStrength;

        ClaimType(String serializedName, String shortLabel, int defenceStrength, int supportStrength) {
            this.serializedName = serializedName;
            this.shortLabel = shortLabel;
            this.defenceStrength = defenceStrength;
            this.supportStrength = supportStrength;
        }

        public static ClaimType fromClaim(IClaim claim) {
            if (claim instanceof TileEntityCitadel) return CITADEL;
            if (claim instanceof TileEntityReinforcedClaim) return REINFORCED;
            if (claim instanceof TileEntityBasicClaim) return BASIC;
            if (claim instanceof TileEntitySiegeCamp) return SIEGE;
            return NONE;
        }

        public static ClaimType fromSerialized(String value) {
            for (ClaimType type : values()) {
                if (type.serializedName.equals(value)) {
                    return type;
                }
            }
            return NONE;
        }
    }

    public static class PlayerData {
        public Faction.Role role = Faction.Role.MEMBER;
        @Deprecated
        public DimBlockPos flagPosition = DimBlockPos.ZERO;
        //public boolean mHasMovedFlagToday = false;
        @Deprecated
        public long moveFlagCooldown = 0; // in ms

        public void readFromNBT(CompoundTag tags) {
            // Read and write role by string so enum order can change
            role = Faction.Role.valueOf(tags.getString("role"));
            //mHasMovedFlagToday = tags.getBoolean("movedFlag");
            moveFlagCooldown = tags.getLong("flagCooldown");
            flagPosition = DimBlockPos.readFromNBT(tags, "flagPosition");
        }

        public void writeToNBT(CompoundTag tags) {
            tags.putString("role", role.name());
            tags.putLong("flagCooldown", moveFlagCooldown);
            flagPosition.writeToNBT(tags, "flagPosition");
        }

        @Deprecated
        public void addCooldown() {
            moveFlagCooldown = System.currentTimeMillis() + (long) (WarForgeConfig.FLAG_COOLDOWN * 60 * 1000);
        }
    }
}

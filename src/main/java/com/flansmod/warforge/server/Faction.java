package com.flansmod.warforge.server;

import com.flansmod.warforge.api.Time;
import com.flansmod.warforge.api.vein.Quality;
import com.flansmod.warforge.api.vein.Vein;
import com.flansmod.warforge.common.Content;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.blocks.IClaim;
import com.flansmod.warforge.common.blocks.TileEntityIslandCollector;
import com.flansmod.warforge.common.blocks.TileEntityYieldCollector;
import org.apache.commons.lang3.tuple.Pair;
import com.flansmod.warforge.common.network.FactionDisplayInfo;
import com.flansmod.warforge.common.network.PlayerDisplayInfo;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Leaderboard.FactionStat;
import com.mojang.authlib.GameProfile;
import lombok.Getter;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

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
        EntityPlayer player = getPlayer(playerID);
        return player.getName();
    }

    // the map used in getPlayerByUUID removes players on logout
    private static EntityPlayer getPlayer(UUID playerID) {
        return WarForgeMod.MC_SERVER.getPlayerList().getPlayerByUUID(playerID);
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
                ITextComponent msg = new TextComponentString("")
                        .appendSibling(new TextComponentString("Your power grows. Siege momentum increased to "))
                        .appendSibling(new TextComponentString(String.valueOf(siegeMomentum)).setStyle(new Style().setBold(true).setColor(TextFormatting.GOLD)))
                        .appendSibling(new TextComponentString(". Next siege will take: "))
                        .appendSibling(new TextComponentString(formattedTime).setStyle(new Style().setColor(TextFormatting.GREEN)));

                messageAll(msg);
            } else if (!increased && message) {
                ITextComponent msg = new TextComponentString("")
                        .appendSibling(new TextComponentString("You haven't gained any more momentum, however it's time was extended by another " + WarForgeConfig.SIEGE_MOMENTUM_DURATION + "minutes"));

                messageAll(msg);
            }
        }
        return increased;
    }

    public void stopMomentum(boolean message) {
        siegeMomentum = 0;
        momentumExpireryTimestamp = 0L;
        if(message){
            messageAll( new TextComponentString("Your extra momentum was lost"));
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
    public ArrayList<EntityPlayer> getOnlinePlayers(Predicate<EntityPlayer> playerCondition) {
        ArrayList<EntityPlayer> players = new ArrayList<>(members.size());
        //mMembers.keySet() seems to have a null default
        for (UUID playerID : members.keySet()) {
            EntityPlayer player = getPlayer(playerID);
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
            GameProfile profile = WarForgeMod.MC_SERVER.getPlayerProfileCache().getProfileByUUID(entry.getKey());
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
        messageAll(new TextComponentString(getPlayerName(playerID) + " joined " + name));


        // re-check number of online players
        onlinePlayerCount = getOnlinePlayers(entityPlayer -> true).size();

        com.flansmod.warforge.common.util.FactionDisplay.refreshTabName(playerID);
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


        messageAll(new TextComponentString(getPlayerName(playerID) + " was made leader of " + name));
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
        com.flansmod.warforge.common.util.FactionDisplay.refreshTabName(playerID);
    }

    public void disband() {
        // Clean up remaining claims
        for (Map.Entry<DimBlockPos, Integer> kvp : claims.entrySet()) {
            World world = WarForgeMod.MC_SERVER.getWorld(kvp.getKey().dim);
            world.setBlockToAir(kvp.getKey().toRegularPos());
        }

        World world = WarForgeMod.MC_SERVER.getWorld(citadelPos.dim);
        this.citadelLevel = 0;
        world.setBlockToAir(citadelPos.toRegularPos());
        for (DimBlockPos collectorPos : islandCollectors) {
            World collectorWorld = WarForgeMod.MC_SERVER.getWorld(collectorPos.dim);
            if (collectorWorld != null) {
                collectorWorld.setBlockToAir(collectorPos.toRegularPos());
            }
        }


        String message = getMemberCount() > 0 ? name + " was disbanded." : name + " was abandoned and disbanded";
        WarForgeMod.FACTIONS.sendDisbandNotification(this);
        messageAll(new TextComponentString(message));
        java.util.List<UUID> formerMembers = new java.util.ArrayList<>(members.keySet());
        members.clear();
        for (UUID formerMember : formerMembers) {
            com.flansmod.warforge.common.util.FactionDisplay.refreshTabName(formerMember);
        }
        claims.clear();
        claimTypes.clear();
        forcedChunks.clear();
        islandCollectors.clear();
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
        World world = WarForgeMod.MC_SERVER.getWorld(claimBlockPos.dim);
        IBlockState claimBlock = world.getBlockState(claimBlockPos.toRegularPos());
        if (WarForgeMod.isClaim(claimBlock.getBlock(), Content.statue, Content.dummyTranslusent)) {
            ItemStack drop = new ItemStack(Item.getItemFromBlock(claimBlock.getBlock()));
            world.setBlockToAir(claimBlockPos);
            if (!captureAttempted || !WarForgeConfig.SIEGE_CAPTURE) {
                world.spawnEntity(new EntityItem(
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
            WarForgeMod.FACTIONS.FactionDefeated(this);
            WarForgeMod.INSTANCE.messageAll(new TextComponentString(name + "'s citadel was destroyed. " + name + " is no more."), true);
        } else {
            if (notifyLoss) {
                messageAll(new TextComponentString("Our faction lost a claim at " + claimBlockPos.toFancyString()));
                WarForgeMod.FACTIONS.sendClaimChangedNotification(
                        this,
                        "claim_lost_" + claimBlockPos,
                        "Claim Lost",
                        "Your faction lost the claim at " + claimBlockPos.toFancyString(),
                        0xB34747
                );
            }

            wealth -= chunkWealth(claimBlockPos.toChunkPos());
            claims.remove(claimBlockPos);
            claimTypes.remove(claimBlockPos);
            ArrayList<DimBlockPos> removedCollectors = new ArrayList<DimBlockPos>();
            for (DimBlockPos collectorPos : islandCollectors) {
                if (collectorPos.toChunkPos().equals(claimBlockPos.toChunkPos())) {
                    removedCollectors.add(collectorPos);
                    World collectorWorld = WarForgeMod.MC_SERVER.getWorld(collectorPos.dim);
                    if (collectorWorld != null) {
                        collectorWorld.setBlockToAir(collectorPos.toRegularPos());
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
        DimBlockPos blockPos = new DimBlockPos(pos.dim, pos.getXStart(), y, pos.getZStart());
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
    public void messageAll(ITextComponent chat) {
        for (UUID playerID : members.keySet()) {
            final EntityPlayer player = getPlayer(playerID);
            if (player != null)
                player.sendMessage(chat);
        }
    }

    // Plays sound to everyone within faction
    public void soundEffectAll(SoundEvent soundEvent, float volume, float pitch) {
        for (UUID playerID : members.keySet()) {
            final EntityPlayerMP player = (EntityPlayerMP) getPlayer(playerID);
            if (player != null && !player.hasDisconnected()) {
                player.connection.sendPacket(new SPacketSoundEffect(
                        soundEvent,
                        SoundCategory.PLAYERS,
                        player.posX, player.posY, player.posZ,
                        volume, pitch
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

    public static int chunkWealth(DimChunkPos chunk) {
        if (VEIN_HANDLER == null || !VEIN_HANDLER.hasFinishedInit || WarForgeMod.MC_SERVER == null) return 0;
        World world = WarForgeMod.MC_SERVER.getWorld(chunk.dim);
        if (world == null) world = WarForgeMod.MC_SERVER.getWorld(0);
        if (world == null) return 0;
        Pair<Vein, Quality> veinInfo = VEIN_HANDLER.getVein(chunk.dim, chunk.x, chunk.z, world.getSeed());
        return veinInfo == null || veinInfo.getLeft() == null ? 0 : veinInfo.getLeft().wealth;
    }

    public void recalculateWealth() {
        int total = 0;
        for (DimBlockPos claimPos : claims.keySet()) total += chunkWealth(claimPos.toChunkPos());
        wealth = total;
    }

    public void awardYields() {
        for (HashMap.Entry<DimBlockPos, Integer> kvp : claims.entrySet()) {
            DimBlockPos pos = kvp.getKey();
            World world = WarForgeMod.MC_SERVER.getWorld(pos.dim);
            kvp.setValue(kvp.getValue() + 1);  // increment number of yields

            // If It's loaded and the handler is ready, try to process yields
            if (world.isBlockLoaded(pos) && VEIN_HANDLER != null && VEIN_HANDLER.hasFinishedInit) {
                TileEntity te = world.getTileEntity(pos.toRegularPos());
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
            World world = WarForgeMod.MC_SERVER.getWorld(collectorPos.dim);
            if (world == null || !world.isBlockLoaded(collectorPos.toRegularPos())) {
                continue;
            }

            TileEntity te = world.getTileEntity(collectorPos.toRegularPos());
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
                GameProfile profile = WarForgeMod.MC_SERVER.getPlayerProfileCache().getProfileByUUID(playerID);
                if (profile != null)
                    messageAll(new TextComponentString(profile.getName() + " was promoted to officer"));
            }
        }
    }

    public void demote(UUID playerID) {
        PlayerData data = members.get(playerID);
        if (data != null) {
            if (data.role == Role.OFFICER) {
                data.role = Role.MEMBER;
                GameProfile profile = WarForgeMod.MC_SERVER.getPlayerProfileCache().getProfileByUUID(playerID);
                if (profile != null)
                    messageAll(new TextComponentString(profile.getName() + " was demoted to member"));
            }
        }
    }


    public void setColour(int colour) {
        this.colour = colour;
    }


    public void readFromNBT(NBTTagCompound tags) {
        claims.clear();
        claimTypes.clear();
        forcedChunks.clear();
        islandCollectors.clear();
        members.clear();
        pendingInvites.clear();
        allies.clear();
        pendingAllianceRequests.clear();
        truces.clear();

        // Get citadel pos and defining params
        uuid = tags.getUniqueId("uuid");
        name = tags.getString("name");
        colour = tags.getInteger("colour");
        citadelLevel = tags.getShort("citadel_lvl");

        // Get our claims and citadel
        citadelPos = DimBlockPos.readFromNBT(tags, "citadelPos");


        NBTTagList claimList = tags.getTagList("claims", 10); // CompoundTag (see NBTBase.class)
        for (NBTBase base : claimList) {
            NBTTagCompound claimInfo = (NBTTagCompound) base;
            DimBlockPos pos = DimBlockPos.readFromNBT((NBTTagIntArray) claimInfo.getTag("pos"));
            int pendingYields = claimInfo.getInteger("pendingYields");
            claims.put(pos, pendingYields);
            ClaimType claimType = ClaimType.fromSerialized(claimInfo.getString("type"));
            if (claimType == ClaimType.NONE && pos.equals(citadelPos)) {
                claimType = ClaimType.CITADEL;
            }
            claimTypes.put(pos, claimType);
        }
        if (FactionStorage.IsNeutralZone(uuid)) {
            if (claimTypes.get(citadelPos) == ClaimType.CITADEL) {
                claims.remove(citadelPos);
                claimTypes.remove(citadelPos);
            }
        } else if (!claims.containsKey(citadelPos)) {
            WarForgeMod.LOGGER.error("Citadel was not claimed by the faction. Forcing claim");
            claims.put(citadelPos, 0);
            claimTypes.put(citadelPos, ClaimType.CITADEL);
        }

        NBTTagList killList = tags.getTagList("kills", 10); // CompoundTag (see NBTBase.class)
        for (NBTBase base : killList) {
            NBTTagCompound killInfo = (NBTTagCompound) base;
            UUID uuid = killInfo.getUniqueId("id");
            int kills = killInfo.getInteger("count");

            killCounter.put(uuid, kills);
        }

        NBTTagList forceLoadList = tags.getTagList("forcedChunks", 11);
        for (NBTBase base : forceLoadList) {
            int[] data = ((NBTTagIntArray) base).getIntArray();
            if (data.length == 3) {
                forcedChunks.add(new DimChunkPos(data[0], data[1], data[2]));
            }
        }

        NBTTagList collectorList = tags.getTagList("collectors", 11);
        for (NBTBase base : collectorList) {
            DimBlockPos collectorPos = DimBlockPos.readFromNBT((NBTTagIntArray) base);
            if (!collectorPos.equals(DimBlockPos.ZERO)) {
                islandCollectors.add(collectorPos);
            }
        }


        flagId = tags.getString("flagId");

        // Get gameplay params
        notoriety = tags.getInteger("notoriety");
        wealth = tags.getInteger("wealth");
        legacy = tags.getInteger("legacy");

        offlineRaidProtectionUntil = tags.getLong("offlineRaidProtectionUntil");
        offlineRaidProtectionDisabled = tags.getBoolean("offlineRaidProtectionDisabled");
        siegeGraceUntil = tags.getLong("siegeGraceUntil");
        citadelMoveCooldown = tags.getInteger("citadelMoveCooldown");
        citadelMoveTimeStamp = tags.getLong("citadelMoveTimestamp");
        lastSiegeTimestamp = tags.getLong("lastSiegeTimestamp");
        siegeMomentum = tags.getByte("siegeMomentum");
        momentumExpireryTimestamp = tags.getLong("momentumExpireryTimestamp");
        isCurrentlyDefending = tags.getBoolean("isDefending");


        // Get member data
        NBTTagList memberList = tags.getTagList("members", 10); // NBTTagCompound (see NBTBase.class)
        for (NBTBase base : memberList) {
            NBTTagCompound memberTags = (NBTTagCompound) base;
            UUID uuid = memberTags.getUniqueId("uuid");
            PlayerData data = new PlayerData();
            data.readFromNBT(memberTags);
            members.put(uuid, data);

            // Fixup for old data
            if (data.flagPosition.equals(DimBlockPos.ZERO)) {
                data.flagPosition = citadelPos;
            }
        }

        NBTTagList inviteList = tags.getTagList("pendingInvites", 10);
        for (NBTBase base : inviteList) {
            NBTTagCompound inviteTags = (NBTTagCompound) base;
            pendingInvites.add(inviteTags.getUniqueId("uuid"));
        }

        allowAllyInteraction = tags.getBoolean("allowAllyInteraction");
        NBTTagList allyList = tags.getTagList("allies", 10);
        for (NBTBase base : allyList) {
            allies.add(((NBTTagCompound) base).getUniqueId("uuid"));
        }
        NBTTagList allyRequestList = tags.getTagList("pendingAllianceRequests", 10);
        for (NBTBase base : allyRequestList) {
            pendingAllianceRequests.add(((NBTTagCompound) base).getUniqueId("uuid"));
        }
        NBTTagList truceList = tags.getTagList("truces", 10);
        for (NBTBase base : truceList) {
            NBTTagCompound truceTag = (NBTTagCompound) base;
            truces.put(truceTag.getUniqueId("uuid"), truceTag.getLong("expiry"));
        }

        insuranceStacks.clear();
        NBTTagList insuranceList = tags.getTagList("insurance", 10);
        for (NBTBase base : insuranceList) {
            NBTTagCompound insuranceTag = (NBTTagCompound) base;
            int slot = insuranceTag.getInteger("slot");
            ItemStack stack = new ItemStack(insuranceTag.getCompoundTag("stack"));
            ensureInsuranceSize(slot + 1);
            insuranceStacks.set(slot, stack);
        }
    }

    public void writeToNBT(NBTTagCompound tags) {
        // Set citadel pos and core params
        tags.setUniqueId("uuid", uuid);
        tags.setString("name", name);
        tags.setString("flagId", flagId);
        tags.setInteger("colour", colour);
        tags.setShort("citadel_lvl", citadelLevel);

        // Set claims
        NBTTagList claimsList = new NBTTagList();
        for (HashMap.Entry<DimBlockPos, Integer> kvp : claims.entrySet()) {
            NBTTagCompound claimTags = new NBTTagCompound();
            claimTags.setTag("pos", kvp.getKey().writeToNBT());
            claimTags.setInteger("pendingYields", kvp.getValue());
            claimTags.setString("type", claimTypes.getOrDefault(kvp.getKey(), kvp.getKey().equals(citadelPos) ? ClaimType.CITADEL : ClaimType.BASIC).serializedName);

            claimsList.appendTag(claimTags);
        }
        tags.setTag("claims", claimsList);
        citadelPos.writeToNBT(tags, "citadelPos");

        NBTTagList killsList = new NBTTagList();
        for (HashMap.Entry<UUID, Integer> kvp : killCounter.entrySet()) {
            NBTTagCompound killTags = new NBTTagCompound();
            killTags.setUniqueId("id", kvp.getKey());
            killTags.setInteger("count", kvp.getValue());

            killsList.appendTag(killTags);
        }
        tags.setTag("kills", killsList);

        NBTTagList forceLoadList = new NBTTagList();
        for (DimChunkPos chunkPos : forcedChunks) {
            forceLoadList.appendTag(new NBTTagIntArray(new int[]{chunkPos.dim, chunkPos.x, chunkPos.z}));
        }
        tags.setTag("forcedChunks", forceLoadList);

        NBTTagList collectorsList = new NBTTagList();
        for (DimBlockPos collectorPos : islandCollectors) {
            collectorsList.appendTag(collectorPos.writeToNBT());
        }
        tags.setTag("collectors", collectorsList);

        // Set gameplay params
        tags.setInteger("notoriety", notoriety);
        tags.setInteger("wealth", wealth);
        tags.setInteger("legacy", legacy);

        tags.setLong("offlineRaidProtectionUntil", offlineRaidProtectionUntil);
        tags.setBoolean("offlineRaidProtectionDisabled", offlineRaidProtectionDisabled);
        tags.setLong("siegeGraceUntil", siegeGraceUntil);
        tags.setInteger("citadelMoveCooldown", citadelMoveCooldown);
        tags.setLong("citadelMoveTimestamp", citadelMoveTimeStamp);
        tags.setLong("lastSiegeTimestamp", lastSiegeTimestamp);

        tags.setByte("siegeMomentum", siegeMomentum);
        tags.setLong("momentumExpireryTimestamp", lastSiegeTimestamp);

        // Add member data
        NBTTagList memberList = new NBTTagList();
        for (HashMap.Entry<UUID, PlayerData> kvp : members.entrySet()) {
            NBTTagCompound memberTags = new NBTTagCompound();
            memberTags.setUniqueId("uuid", kvp.getKey());
            kvp.getValue().writeToNBT(memberTags);
            memberList.appendTag(memberTags);
        }
        tags.setTag("members", memberList);

        NBTTagList inviteList = new NBTTagList();
        for (UUID invitee : pendingInvites) {
            NBTTagCompound inviteTags = new NBTTagCompound();
            inviteTags.setUniqueId("uuid", invitee);
            inviteList.appendTag(inviteTags);
        }
        tags.setTag("pendingInvites", inviteList);

        tags.setBoolean("allowAllyInteraction", allowAllyInteraction);
        NBTTagList allyList = new NBTTagList();
        for (UUID ally : allies) {
            NBTTagCompound allyTag = new NBTTagCompound();
            allyTag.setUniqueId("uuid", ally);
            allyList.appendTag(allyTag);
        }
        tags.setTag("allies", allyList);
        NBTTagList allyRequestList = new NBTTagList();
        for (UUID requester : pendingAllianceRequests) {
            NBTTagCompound requestTag = new NBTTagCompound();
            requestTag.setUniqueId("uuid", requester);
            allyRequestList.appendTag(requestTag);
        }
        tags.setTag("pendingAllianceRequests", allyRequestList);
        NBTTagList truceList = new NBTTagList();
        for (Map.Entry<UUID, Long> entry : truces.entrySet()) {
            NBTTagCompound truceTag = new NBTTagCompound();
            truceTag.setUniqueId("uuid", entry.getKey());
            truceTag.setLong("expiry", entry.getValue());
            truceList.appendTag(truceTag);
        }
        tags.setTag("truces", truceList);

        NBTTagList insuranceList = new NBTTagList();
        for (int i = 0; i < insuranceStacks.size(); i++) {
            ItemStack stack = insuranceStacks.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            NBTTagCompound insuranceTag = new NBTTagCompound();
            insuranceTag.setInteger("slot", i);
            NBTTagCompound stackTag = new NBTTagCompound();
            stack.writeToNBT(stackTag);
            insuranceTag.setTag("stack", stackTag);
            insuranceList.appendTag(insuranceTag);
        }
        tags.setTag("insurance", insuranceList);
        tags.setBoolean("isDefending", isCurrentlyDefending);
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
            if (claim instanceof com.flansmod.warforge.common.blocks.TileEntityCitadel) return CITADEL;
            if (claim instanceof com.flansmod.warforge.common.blocks.TileEntityReinforcedClaim) return REINFORCED;
            if (claim instanceof com.flansmod.warforge.common.blocks.TileEntityBasicClaim) return BASIC;
            if (claim instanceof com.flansmod.warforge.common.blocks.TileEntitySiegeCamp) return SIEGE;
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

        public void readFromNBT(NBTTagCompound tags) {
            // Read and write role by string so enum order can change
            role = Faction.Role.valueOf(tags.getString("role"));
            //mHasMovedFlagToday = tags.getBoolean("movedFlag");
            moveFlagCooldown = tags.getLong("flagCooldown");
            flagPosition = new DimBlockPos(
                    tags.getInteger("dim"),
                    tags.getInteger("x"),
                    tags.getInteger("y"),
                    tags.getInteger("z"));
        }

        public void writeToNBT(NBTTagCompound tags) {
            tags.setString("role", role.name());
            tags.setLong("flagCooldown", moveFlagCooldown);
            tags.setInteger("dim", flagPosition.dim);
            tags.setInteger("x", flagPosition.getX());
            tags.setInteger("y", flagPosition.getY());
            tags.setInteger("z", flagPosition.getZ());
        }

        @Deprecated
        public void addCooldown() {
            moveFlagCooldown = System.currentTimeMillis() + (long) (WarForgeConfig.FLAG_COOLDOWN * 60 * 1000);
        }
    }
}

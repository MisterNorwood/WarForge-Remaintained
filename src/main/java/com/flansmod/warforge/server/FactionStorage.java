package com.flansmod.warforge.server;

import com.flansmod.warforge.api.ObjectIntPair;
import com.flansmod.warforge.common.Content;
import com.flansmod.warforge.common.Sounds;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.*;
import com.flansmod.warforge.common.effect.EffectDisband;
import com.flansmod.warforge.common.effect.EffectUpgrade;
import com.flansmod.warforge.common.network.*;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.common.util.TimeHelper;
import com.flansmod.warforge.server.Faction.PlayerData;
import com.flansmod.warforge.server.Faction.Role;
import com.mojang.authlib.GameProfile;
import lombok.Getter;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentBase;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.World;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

import static com.flansmod.warforge.common.WarForgeMod.*;

public class FactionStorage {
    private static final int TOAST_INFO = 0x708A97;
    private static final int TOAST_SUCCESS = 0x55AA55;
    private static final int TOAST_WARNING = 0xC79A3A;
    private static final int TOAST_DANGER = 0xB34747;

    public enum SiegeZoneRelation {
        NONE,
        ATTACKER,
        DEFENDER,
        OTHER
    }

    public enum SiegeZone { NONE, WAR, SIEGED }

    public static final class SiegeZoneResult {
        public static final SiegeZoneResult NONE = new SiegeZoneResult(SiegeZone.NONE, null);
        public final SiegeZone zone;
        public final UUID defendingFaction;
        private SiegeZoneResult(SiegeZone zone, UUID defendingFaction) {
            this.zone = zone;
            this.defendingFaction = defendingFaction;
        }
    }

    // SafeZone and WarZone
    public static UUID SAFE_ZONE_ID = Faction.createUUID("safezone");
    public static UUID WAR_ZONE_ID = Faction.createUUID("conquered zone");
    public static Faction SAFE_ZONE = null;
    public static Faction WAR_ZONE = null;
    private final HashMap<UUID, Faction> mFactions = new HashMap<>();
    // This map contains every single claim, including siege camps.
    // So if you take one of these and try to look it up in the faction, check their active sieges too
    private final HashMap<DimChunkPos, UUID> mClaims = new HashMap<>();
    // This is all the currently active sieges, keyed by the defending position
    @Getter
    private final HashMap<DimChunkPos, Siege> sieges = new HashMap<>();
    private final Queue<DimChunkPos> finishedSiegeQueue = new ArrayDeque<>(4); //Array queue used due to small amount of data
    //This is all chunks that are under the "Grace" period
    public HashMap<DimChunkPos, ObjectIntPair<UUID>> conqueredChunks = new HashMap<>();
    private final HashMap<UUID, ArrayList<ItemStack>> redeemableInsuranceVaults = new HashMap<UUID, ArrayList<ItemStack>>();

    public FactionStorage() {
        InitNeutralZones();
    }

    public void sendNotificationToPlayer(EntityPlayerMP player, String token, String title, String subtitle, int accentColor, int durationMs) {
        sendNotificationToPlayer(player, token, title, subtitle, accentColor, durationMs, null);
    }

    public void sendNotificationToPlayer(EntityPlayerMP player, String token, String title, String subtitle, int accentColor, int durationMs, UUID actorId) {
        if (player == null) {
            return;
        }
        NETWORK.sendTo(new PacketClientNotification(token, title, subtitle, accentColor, durationMs, actorId), player);
    }

    public void sendNotificationToFaction(Faction faction, String token, String title, String subtitle, int accentColor, int durationMs) {
        sendNotificationToFaction(faction, token, title, subtitle, accentColor, durationMs, null);
    }

    public void sendNotificationToFaction(Faction faction, String token, String title, String subtitle, int accentColor, int durationMs, UUID actorId) {
        if (faction == null) {
            return;
        }
        for (EntityPlayer onlinePlayer : faction.getOnlinePlayers(entityPlayer -> entityPlayer != null)) {
            if (onlinePlayer instanceof EntityPlayerMP serverPlayer) {
                sendNotificationToPlayer(serverPlayer, token, title, subtitle, accentColor, durationMs, actorId);
            }
        }
    }

    public void sendNotificationToFaction(Faction faction, String token, String title, String subtitle, int accentColor, int durationMs, UUID actorId, Collection<UUID> exclude) {
        if (faction == null) {
            return;
        }
        for (EntityPlayer onlinePlayer : faction.getOnlinePlayers(entityPlayer -> entityPlayer != null && !exclude.contains(entityPlayer.getUniqueID()))) {
            if (onlinePlayer instanceof EntityPlayerMP serverPlayer) {
                sendNotificationToPlayer(serverPlayer, token, title, subtitle, accentColor, durationMs, actorId);
            }
        }
    }

    public void sendSiegeStartNotifications(Faction attackers, Faction defenders, DimBlockPos defendingClaim) {
        String target = defendingClaim.toFancyString();
        sendNotificationToFaction(
                attackers,
                "siege_started_attackers_" + defendingClaim,
                "Siege Started",
                "You started a siege against " + defenders.name + " at " + target,
                attackers.colour,
                6000
        );
        sendNotificationToFaction(
                defenders,
                "siege_started_defenders_" + defendingClaim,
                "Under Siege",
                attackers.name + " started a siege at " + target,
                TOAST_DANGER,
                7000
        );
    }

    private void sendSiegeOutcomeNotifications(Faction attackers, Faction defenders, DimBlockPos defendingClaim, boolean attackersWon) {
        String target = defendingClaim.toFancyString();
        if (attackersWon) {
            sendNotificationToFaction(
                    attackers,
                    "siege_won_attackers_" + defendingClaim,
                    "Siege Won",
                    "You defeated " + defenders.name + " at " + target,
                    TOAST_SUCCESS,
                    7000
            );
            sendNotificationToFaction(
                    defenders,
                    "siege_lost_defenders_" + defendingClaim,
                    "Siege Lost",
                    attackers.name + " won the siege at " + target,
                    TOAST_DANGER,
                    7000
            );
        } else {
            sendNotificationToFaction(
                    attackers,
                    "siege_lost_attackers_" + defendingClaim,
                    "Siege Lost",
                    "You failed to take " + target,
                    TOAST_DANGER,
                    7000
            );
            sendNotificationToFaction(
                    defenders,
                    "siege_won_defenders_" + defendingClaim,
                    "Siege Won",
                    "You defended " + target + " from " + attackers.name,
                    TOAST_SUCCESS,
                    7000
            );
        }
    }

    private void sendFactionMemberJoinedNotification(Faction faction, EntityPlayer player) {
        sendNotificationToFaction(
                faction,
                "faction_member_joined_" + faction.uuid + "_" + player.getUniqueID(),
                "Member Joined",
                player.getName() + " joined " + faction.name,
                faction.colour,
                5000,
                player.getUniqueID(),
                Arrays.asList(player.getUniqueID())
        );
    }

    private void sendFactionMemberLeftNotification(Faction faction, UUID playerId, String playerName, boolean wasKicked) {
        sendNotificationToFaction(
                faction,
                "faction_member_left_" + faction.uuid + "_" + playerName,
                wasKicked ? "Member Removed" : "Member Left",
                wasKicked ? playerName + " was removed from " + faction.name : playerName + " left " + faction.name,
                wasKicked ? TOAST_WARNING : TOAST_DANGER,
                5000,
                playerId
        );
    }

    public void sendFactionPresenceNotification(Faction faction, UUID playerId, String playerName, boolean online) {
        sendNotificationToFaction(
                faction,
                "faction_presence_" + faction.uuid + "_" + playerName,
                online ? "Member Online" : "Member Offline",
                playerName + (online ? " is now online" : " went offline"),
                online ? TOAST_INFO : TOAST_WARNING,
                4000,
                playerId,
                Arrays.asList(playerId)
        );
    }

    public void sendClaimChangedNotification(Faction faction, String token, String title, String subtitle, int accentColor) {
        sendClaimChangedNotification(faction, token, title, subtitle, accentColor, null);
    }

    public void sendClaimChangedNotification(Faction faction, String token, String title, String subtitle, int accentColor, UUID actorId) {
        sendNotificationToFaction(faction, token, title, subtitle, accentColor, 5000, actorId);
    }

    public void sendUpgradeNotification(Faction faction, UUID playerId, String playerName) {
        sendNotificationToFaction(
                faction,
                "faction_upgrade_" + faction.uuid + "_" + faction.citadelLevel,
                "Citadel Upgraded",
                playerName + " upgraded " + faction.name + " to level " + faction.citadelLevel,
                TOAST_SUCCESS,
                6000,
                playerId
        );
    }

    public void sendDisbandNotification(Faction faction) {
        sendNotificationToFaction(
                faction,
                "faction_disbanded_" + faction.uuid,
                "Faction Disbanded",
                faction.name + " has been disbanded",
                TOAST_DANGER,
                7000
        );
    }

    // ---------------------------------------------------------------------------------------------
    // Alliances
    // ---------------------------------------------------------------------------------------------

    // Player-facing reason the attacker may not siege the defender due to diplomacy, or null if allowed.
    private String getSiegeBlockReason(Faction attacker, Faction defender) {
        if (attacker == null || defender == null) {
            return null;
        }
        if (IsNeutralZone(defender.uuid)) {
            return "You cannot siege a protected zone.";
        }
        if (attacker.isAllyOf(defender.uuid)) {
            return "You are allied with " + defender.name + " and cannot siege them. Break the alliance first.";
        }
        if (attacker.isInTruceWith(defender.uuid)) {
            return "You are in a truce with " + defender.name + " for another "
                    + TimeHelper.formatTime(attacker.getTruceRemainingMs(defender.uuid)) + " and cannot siege them.";
        }
        return null;
    }

    // Drops the given faction from every other faction's alliance/request/truce structures so nothing
    // dangles after it is disbanded.
    private void removeFactionFromAllAlliances(UUID factionId) {
        for (Faction other : mFactions.values()) {
            other.allies.remove(factionId);
            other.pendingAllianceRequests.remove(factionId);
            other.truces.remove(factionId);
        }
    }

    // Factions that the given faction could currently invite to an alliance (excludes itself, the
    // safe/war pseudo-zones, and existing allies). Used to populate the alliance UI.
    public java.util.List<Faction> getAlliableFactions(Faction faction) {
        java.util.ArrayList<Faction> result = new java.util.ArrayList<Faction>();
        if (faction == null) {
            return result;
        }
        for (Faction other : mFactions.values()) {
            if (other.uuid.equals(faction.uuid) || IsNeutralZone(other.uuid) || faction.isAllyOf(other.uuid)) {
                continue;
            }
            result.add(other);
        }
        return result;
    }

    private boolean canManageAlliances(EntityPlayerMP player, Faction faction) {
        if (faction == null) {
            player.sendMessage(new TextComponentString("You are not in a faction"));
            return false;
        }
        if (!WarForgeMod.isOp(player) && !faction.isPlayerRoleInFaction(player.getUniqueID(), Role.OFFICER)) {
            player.sendMessage(new TextComponentString("You must be an officer to manage alliances"));
            return false;
        }
        return true;
    }

    public void requestInviteAlly(EntityPlayerMP player, UUID targetFactionId) {
        Faction faction = getFactionOfPlayer(player.getUniqueID());
        if (!canManageAlliances(player, faction)) {
            return;
        }
        Faction target = getFaction(targetFactionId);
        if (target == null || IsNeutralZone(targetFactionId)) {
            player.sendMessage(new TextComponentString("That faction no longer exists"));
            return;
        }
        if (target.uuid.equals(faction.uuid)) {
            player.sendMessage(new TextComponentString("You cannot ally with your own faction"));
            return;
        }
        if (faction.isAllyOf(target.uuid)) {
            player.sendMessage(new TextComponentString("You are already allied with " + target.name));
            return;
        }
        if (WarForgeConfig.MAX_ALLIES >= 0 && faction.allies.size() >= WarForgeConfig.MAX_ALLIES) {
            player.sendMessage(new TextComponentString("Your faction has reached the maximum number of alliances"));
            return;
        }
        // If they already requested us, treat this as an accept (mutual consent reached).
        if (faction.pendingAllianceRequests.contains(target.uuid)) {
            acceptAllianceInternal(faction, target);
            return;
        }
        if (target.pendingAllianceRequests.contains(faction.uuid)) {
            player.sendMessage(new TextComponentString("You have already sent an alliance request to " + target.name));
            return;
        }
        target.pendingAllianceRequests.add(faction.uuid);
        faction.messageAll(new TextComponentString("Sent an alliance request to " + target.name + "."));
        target.messageAll(new TextComponentString(faction.name + " has requested an alliance. Open Faction Members > Alliances to respond."));
        sendNotificationToFaction(faction, "alliance_request_sent_" + target.uuid, "Alliance Requested", "Request sent to " + target.name, TOAST_INFO, 5000);
        sendNotificationToFaction(target, "alliance_request_" + faction.uuid, "Alliance Request", faction.name + " wants to ally", faction.colour, 8000);
    }

    public void requestAcceptAlliance(EntityPlayerMP player, UUID requesterFactionId) {
        Faction faction = getFactionOfPlayer(player.getUniqueID());
        if (!canManageAlliances(player, faction)) {
            return;
        }
        if (!faction.pendingAllianceRequests.contains(requesterFactionId)) {
            player.sendMessage(new TextComponentString("There is no pending alliance request from that faction"));
            return;
        }
        Faction requester = getFaction(requesterFactionId);
        faction.pendingAllianceRequests.remove(requesterFactionId);
        if (requester == null) {
            player.sendMessage(new TextComponentString("That faction no longer exists"));
            return;
        }
        if (WarForgeConfig.MAX_ALLIES >= 0 && faction.allies.size() >= WarForgeConfig.MAX_ALLIES) {
            player.sendMessage(new TextComponentString("Your faction has reached the maximum number of alliances"));
            return;
        }
        acceptAllianceInternal(faction, requester);
    }

    private void acceptAllianceInternal(Faction a, Faction b) {
        a.allies.add(b.uuid);
        b.allies.add(a.uuid);
        a.pendingAllianceRequests.remove(b.uuid);
        b.pendingAllianceRequests.remove(a.uuid);
        // A fresh alliance clears any lingering truce between the two.
        a.truces.remove(b.uuid);
        b.truces.remove(a.uuid);
        a.messageAll(new TextComponentString("Your faction is now allied with " + b.name + "."));
        b.messageAll(new TextComponentString("Your faction is now allied with " + a.name + "."));
        sendNotificationToFaction(a, "alliance_formed_" + b.uuid, "Alliance Formed", "Now allied with " + b.name, TOAST_SUCCESS, 6000);
        sendNotificationToFaction(b, "alliance_formed_" + a.uuid, "Alliance Formed", "Now allied with " + a.name, TOAST_SUCCESS, 6000);
    }

    public void requestDeclineAlliance(EntityPlayerMP player, UUID requesterFactionId) {
        Faction faction = getFactionOfPlayer(player.getUniqueID());
        if (!canManageAlliances(player, faction)) {
            return;
        }
        if (!faction.pendingAllianceRequests.remove(requesterFactionId)) {
            player.sendMessage(new TextComponentString("There is no pending alliance request from that faction"));
            return;
        }
        Faction requester = getFaction(requesterFactionId);
        faction.messageAll(new TextComponentString("Declined the alliance request" + (requester != null ? " from " + requester.name : "") + "."));
        if (requester != null) {
            requester.messageAll(new TextComponentString(faction.name + " declined your alliance request."));
            sendNotificationToFaction(requester, "alliance_declined_" + faction.uuid, "Alliance Declined", faction.name + " declined your request", TOAST_WARNING, 5000);
        }
    }

    public void requestBreakAlliance(EntityPlayerMP player, UUID allyFactionId) {
        Faction faction = getFactionOfPlayer(player.getUniqueID());
        if (!canManageAlliances(player, faction)) {
            return;
        }
        if (!faction.isAllyOf(allyFactionId)) {
            player.sendMessage(new TextComponentString("You are not allied with that faction"));
            return;
        }
        Faction ally = getFaction(allyFactionId);
        faction.allies.remove(allyFactionId);
        if (ally != null) {
            ally.allies.remove(faction.uuid);
        }
        // Apply a mutual truce so neither side can instantly retaliate.
        long truceMs = (long) WarForgeConfig.ALLIANCE_TRUCE_DURATION_MINUTES * 60_000L;
        if (truceMs > 0) {
            long expiry = System.currentTimeMillis() + truceMs;
            faction.truces.put(allyFactionId, expiry);
            if (ally != null) {
                ally.truces.put(faction.uuid, expiry);
            }
        }
        String allyName = ally != null ? ally.name : "that faction";
        String truceMsg = truceMs > 0 ? " A truce prevents fighting for " + TimeHelper.formatTime(truceMs) + "." : "";
        faction.messageAll(new TextComponentString("Your alliance with " + allyName + " has been broken." + truceMsg));
        sendNotificationToFaction(faction, "alliance_broken_" + allyFactionId, "Alliance Broken", "No longer allied with " + allyName, TOAST_WARNING, 6000);
        if (ally != null) {
            ally.messageAll(new TextComponentString(faction.name + " broke your alliance." + truceMsg));
            sendNotificationToFaction(ally, "alliance_broken_" + faction.uuid, "Alliance Broken", faction.name + " broke the alliance", TOAST_DANGER, 6000);
        }
    }

    public void requestToggleAllyInteraction(EntityPlayerMP player) {
        Faction faction = getFactionOfPlayer(player.getUniqueID());
        if (!canManageAlliances(player, faction)) {
            return;
        }
        faction.allowAllyInteraction = !faction.allowAllyInteraction;
        faction.messageAll(new TextComponentString(faction.allowAllyInteraction
                ? "Allies may now interact within your faction's claims."
                : "Allies can no longer interact within your faction's claims."));
        sendNotificationToFaction(faction, "ally_interaction_" + faction.uuid, "Ally Access",
                faction.allowAllyInteraction ? "Allies may now use your land" : "Allies can no longer use your land",
                TOAST_INFO, 5000);
    }

    public static boolean IsNeutralZone(UUID factionID) {
        return factionID.equals(SAFE_ZONE_ID) || factionID.equals(WAR_ZONE_ID);
    }

    // copy UUID's are used in the case that the UUID referred to are nulled at some point, which is not ideal for chunk protection
    public static UUID copyUUID(final UUID source) {
        return new UUID(source.getMostSignificantBits(), source.getLeastSignificantBits());
    }

    public static boolean isValidFaction(Faction faction) {
        return faction != null && isValidFaction(faction.uuid);
    }

    public static boolean isValidFaction(UUID factionID) {
        return factionID != null && !factionID.equals(Faction.nullUuid);
    }

    // returns big endian (decreasing sig/ biggest sig first) array
    public static int[] UUIDToBEIntArray(UUID uniqueID) {
        return new int[]{
                (int) (uniqueID.getMostSignificantBits() >>> 32),
                (int) uniqueID.getMostSignificantBits(),
                (int) (uniqueID.getLeastSignificantBits() >>> 32),
                (int) uniqueID.getLeastSignificantBits()
        };
    }

    public static UUID BEIntArrayToUUID(int[] bigEndianArray) {
        // FUCKING SIGN EXTENSION
        return new UUID(
                ((bigEndianArray[0] & 0xFFFF_FFFFL) << 32) | (bigEndianArray[1] & 0xFFFF_FFFFL),
                ((bigEndianArray[2] & 0xFFFF_FFFFL) << 32) | (bigEndianArray[3] & 0xFFFF_FFFFL)
        );
    }

    private void InitNeutralZones() {
        SAFE_ZONE = new Faction();
        SAFE_ZONE.citadelPos = new DimBlockPos(0, 0, 0, 0); // Overworld origin
        SAFE_ZONE.colour = 0x00ff00;
        SAFE_ZONE.name = "SafeZone";
        SAFE_ZONE.uuid = SAFE_ZONE_ID;
        mFactions.put(SAFE_ZONE_ID, SAFE_ZONE);

        WAR_ZONE = new Faction();
        WAR_ZONE.citadelPos = new DimBlockPos(0, 0, 0, 0); // Overworld origin
        WAR_ZONE.colour = 0xff0000;
        WAR_ZONE.name = "WarZone";
        WAR_ZONE.uuid = WAR_ZONE_ID;
        mFactions.put(WAR_ZONE_ID, WAR_ZONE);
        // Note: We specifically do not put this data in the leaderboard
    }

    public boolean IsPlayerInFaction(UUID playerID, UUID factionID) {
        if (mFactions.containsKey(factionID))
            return mFactions.get(factionID).isPlayerInFaction(playerID);
        return false;
    }

    public boolean isPlayerDefending(UUID playerID) {
        Faction faction = getFactionOfPlayer(playerID);
        List<Siege> Sieges = new ArrayList<>(sieges.values());
        for (Siege siege : Sieges) {
            if (siege.defendingFaction == faction.uuid) {
                return true;
            }
        }

        return false;
    }

    public long getPlayerCooldown(UUID playerID) {
        return mFactions.get(playerID).members.get(playerID).moveFlagCooldown;
    }

    public HashMap<DimChunkPos, UUID> getClaims() {
        return mClaims;
    }

    public Collection<Faction> getAllFactions() {
        return mFactions.values();
    }

    public boolean IsPlayerRoleInFaction(UUID playerID, UUID factionID, Faction.Role role) {
        if (mFactions.containsKey(factionID))
            return mFactions.get(factionID).isPlayerRoleInFaction(playerID, role);
        return false;
    }

    public Faction getFaction(UUID factionID) {
        if (factionID == null) {
            return null;
        }  // fixes NPE when clicking on random chunk to begin siege

        if (factionID.equals(Faction.nullUuid))
            return null;

        if (mFactions.containsKey(factionID))
            return mFactions.get(factionID);

        LOGGER.error("Could not find a faction with UUID " + factionID);
        return null;
    }

    public Faction getFaction(String name) {
        for (HashMap.Entry<UUID, Faction> entry : mFactions.entrySet()) {
            if (entry.getValue().name.equals(name))
                return entry.getValue();
        }
        return null;
    }

    public Faction GetFactionWithOpenInviteTo(UUID playerID) {
        ArrayList<Faction> invites = getFactionsWithOpenInvitesTo(playerID);
        return invites.isEmpty() ? null : invites.get(0);
    }

    public ArrayList<Faction> getFactionsWithOpenInvitesTo(UUID playerID) {
        ArrayList<Faction> factions = new ArrayList<>();
        for (HashMap.Entry<UUID, Faction> entry : mFactions.entrySet()) {
            if (entry.getValue().isInvitingPlayer(playerID)) {
                factions.add(entry.getValue());
            }
        }
        factions.sort(Comparator.comparing(faction -> faction.name, String.CASE_INSENSITIVE_ORDER));
        return factions;
    }

    public Faction getFactionWithOpenInviteTo(UUID playerID, String factionName) {
        if (factionName == null || factionName.isEmpty()) {
            return null;
        }
        for (Faction faction : getFactionsWithOpenInvitesTo(playerID)) {
            if (faction.name.equalsIgnoreCase(factionName)) {
                return faction;
            }
        }
        return null;
    }

    public void clearInvitesToPlayer(UUID playerID) {
        if (playerID == null || playerID.equals(Faction.nullUuid)) {
            return;
        }
        for (Faction faction : mFactions.values()) {
            faction.pendingInvites.remove(playerID);
        }
    }

    private ITextComponent createInviteChatMessage(Faction faction) {
        TextComponentString message = new TextComponentString("You have received an invite to " + faction.name + ". ");
        TextComponentString clickPart = new TextComponentString("[Click to join]");
        clickPart.setStyle(new Style()
                .setColor(TextFormatting.GREEN)
                .setUnderlined(true)
                .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/f accept " + faction.name))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString("Join " + faction.name))));
        message.appendSibling(clickPart);
        return message;
    }

    public String[] GetFactionNames() {
        String[] names = new String[mFactions.size()];
        int i = 0;
        for (HashMap.Entry<UUID, Faction> entry : mFactions.entrySet()) {
            names[i] = entry.getValue().name;
            i++;
        }
        return names;
    }

    private boolean validateFactionName(String factionName, ICommandSender sender, UUID existingFactionId) {
        if (factionName == null || factionName.isEmpty()) {
            sender.sendMessage(new TextComponentString("You can't create or rename a faction with no name"));
            return false;
        }

        if (factionName.length() > WarForgeConfig.FACTION_NAME_LENGTH_MAX) {
            sender.sendMessage(new TextComponentString("Name is too long, must be at most " + WarForgeConfig.FACTION_NAME_LENGTH_MAX + " characters"));
            return false;
        }

        for (int i = 0; i < factionName.length(); i++) {
            char c = factionName.charAt(i);
            if ('0' <= c && c <= '9') continue;
            if ('a' <= c && c <= 'z') continue;
            if ('A' <= c && c <= 'Z') continue;

            sender.sendMessage(new TextComponentString("Invalid character [" + c + "] in faction name"));
            return false;
        }

        String lowerName = factionName.toLowerCase(Locale.ROOT);
        for (String banned : WarForgeConfig.FACTION_NAME_BANLIST) {
            if (banned != null && !banned.trim().isEmpty() && lowerName.contains(banned.trim().toLowerCase(Locale.ROOT))) {
                sender.sendMessage(new TextComponentString("That faction name is not allowed"));
                return false;
            }
        }

        Faction existing = getFaction(factionName);
        if (existing != null && (existingFactionId == null || !existing.uuid.equals(existingFactionId))) {
            sender.sendMessage(new TextComponentString("A faction with the name " + factionName + " already exists"));
            return false;
        }

        return true;
    }

    // This is called for any non-citadel claim. Citadels can be factionless, so this makes no sense
    public void onNonCitadelClaimPlaced(IClaim claim, EntityLivingBase placer) {
        Faction faction = getFactionOfPlayer(placer.getUniqueID());
        onNonCitadelClaimPlaced(claim, faction);
        if (faction != null && placer != null) {
            sendClaimChangedNotification(
                    faction,
                    "claim_added_" + claim.getClaimPos(),
                    "Chunk Claimed",
                    placer.getName() + " claimed " + claim.getClaimPos().toFancyString(),
                    faction.colour,
                    placer.getUniqueID()
            );
        }
    }

    public void onNonCitadelClaimPlaced(IClaim claim, Faction faction) {
        if (faction != null) {
            TileEntity tileEntity = claim.getAsTileEntity();
            boolean dataOnlyClaim = tileEntity instanceof TileEntityBasicClaim || tileEntity instanceof TileEntityReinforcedClaim;
            mClaims.put(claim.getClaimPos().toChunkPos(), faction.uuid);

            faction.messageAll(new TextComponentString("Claimed the chunk [" + claim.getClaimPos().toChunkPos().x + ", " + claim.getClaimPos().toChunkPos().z + "] around " + claim.getClaimPos().toFancyString()));

            if (!dataOnlyClaim) {
                claim.onServerSetFaction(faction);
            }
            faction.onClaimPlaced(claim);

            // Basic / reinforced claim blocks become data-only claims after placement.
            if (dataOnlyClaim) {
                World world = tileEntity.getWorld();
                DimBlockPos claimPos = claim.getClaimPos();
                world.setBlockToAir(claimPos.toRegularPos());
                world.setBlockToAir(claimPos.toRegularPos().up());
                world.setBlockToAir(claimPos.toRegularPos().up(2));
            }
        } else
            LOGGER.error("Invalid placer placed a claim at " + claim.getClaimPos());
    }

    public boolean requestClaimChunkNoTile(EntityPlayerMP player, DimChunkPos chunkPos) {
        Faction faction = getFactionOfPlayer(player.getUniqueID());
        if (faction == null) {
            player.sendMessage(new TextComponentString("You aren't in a faction"));
            return false;
        }
        if (!canClaimChunkNoTile(player, faction, chunkPos, true)) {
            return false;
        }

        ClaimedBlockSelection claimBlock = findFirstClaimBlock(player.inventory);
        if (claimBlock == null) {
            player.sendMessage(new TextComponentString("You need a basic or reinforced claim block in your inventory"));
            return false;
        }

        mClaims.put(chunkPos, faction.uuid);
        faction.claimNoTileEntity(chunkPos, player.getPosition().getY(), claimBlock.claimType);
        claimBlock.consume(player.inventory);
        faction.messageAll(new TextComponentString("Claimed the chunk [" + chunkPos.x + ", " + chunkPos.z + "]"));
        sendClaimChangedNotification(
                faction,
                "claim_added_" + chunkPos,
                "Chunk Claimed",
                player.getName() + " claimed [" + chunkPos.x + ", " + chunkPos.z + "]",
                faction.colour,
                player.getUniqueID()
        );
        return true;
    }

    private boolean canClaimChunkNoTile(EntityPlayerMP player, Faction faction, DimChunkPos chunkPos, boolean notify) {
        if (!isOp(player) && !faction.isPlayerRoleInFaction(player.getUniqueID(), Faction.Role.OFFICER)) {
            if (notify) {
                player.sendMessage(new TextComponentString("You are not an officer of your faction"));
            }
            return false;
        }
        if (!getClaim(chunkPos).equals(Faction.nullUuid)) {
            if (notify) {
                player.sendMessage(new TextComponentString("This chunk already has a claim"));
            }
            return false;
        }
        if (isChunkContested(chunkPos)) {
            if (notify) {
                player.sendMessage(new TextComponentString("This chunk is contested by an active siege"));
            }
            return false;
        }
        if (!containsInt(WarForgeConfig.CLAIM_DIM_WHITELIST, chunkPos.dim)) {
            if (notify) {
                player.sendMessage(new TextComponentString("You cannot claim chunks in this dimension"));
            }
            return false;
        }
        if (!faction.canPlaceClaim()) {
            if (notify) {
                player.sendMessage(new TextComponentString(WarForgeConfig.ENABLE_CITADEL_UPGRADES
                        ? "Your faction reached it's level's claim limit, upgrade the level to incrase the limit"
                        : "Your faction has reached its claim limit"));
            }
            return false;
        }
        if (!WarForgeConfig.ENABLE_ISOLATED_CLAIMS && BlockBasicClaim.hasAdjacent(chunkPos, faction) == null) {
            if (notify) {
                player.sendMessage(new TextComponentString("Isolated claims are disabled; you cannot put a claim here with no adjacent claims"));
            }
            return false;
        }

        // Prevent joining two collector-bearing islands into one.
        int collectorsInConnectedIslands = 0;
        HashSet<DimChunkPos> seen = new HashSet<DimChunkPos>();
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            DimChunkPos neighbor = chunkPos.Offset(facing, 1);
            if (!faction.uuid.equals(getClaim(neighbor)) || seen.contains(neighbor)) {
                continue;
            }

            Set<DimChunkPos> island = collectFactionIsland(faction.uuid, neighbor);
            seen.addAll(island);
            for (DimBlockPos collectorPos : faction.islandCollectors) {
                if (island.contains(collectorPos.toChunkPos())) {
                    collectorsInConnectedIslands++;
                    break;
                }
            }
        }
        if (collectorsInConnectedIslands > 1) {
            if (notify) {
                player.sendMessage(new TextComponentString("This claim would merge multiple islands that each already have a collector"));
            }
            return false;
        }

        ObjectIntPair<UUID> conqueredChunkInfo = conqueredChunks.get(chunkPos);
        if (conqueredChunkInfo != null) {
            if (notify) {
                player.sendMessage(new TextComponentString("This chunk is conquered wilderness; it becomes claimable in "
                        + TimeHelper.formatTime(conqueredChunkInfo.getInteger())));
            }
            return false;
        }

        Faction tooClose = findNearbyOpposingFaction(faction, chunkPos);
        if (tooClose != null) {
            if (notify) {
                player.sendMessage(new TextComponentString("You cannot claim within " + WarForgeConfig.MIN_DISTANCE_BETWEEN_FACTIONS
                        + " chunk(s) of opposing faction " + tooClose.name));
            }
            return false;
        }

        return true;
    }

    public UUID getClaim(DimBlockPos pos) {
        return getClaim(pos.toChunkPos());
    }

    public UUID getClaim(DimChunkPos pos) {
        if (mClaims.containsKey(pos))
            return mClaims.get(pos);
        return Faction.nullUuid;
    }

    public Faction getFactionOfPlayer(UUID playerID) {
        for (HashMap.Entry<UUID, Faction> entry : mFactions.entrySet()) {
            if (entry.getValue().isPlayerInFaction(playerID))
                return entry.getValue();
        }
        return null;
    }

    public SiegeZoneRelation getSiegeZoneRelation(UUID playerID, DimChunkPos chunkPos) {
        Faction playerFaction = playerID == null || playerID.equals(Faction.nullUuid) ? null : getFactionOfPlayer(playerID);
        SiegeZoneRelation bestRelation = SiegeZoneRelation.NONE;

        for (Siege siege : sieges.values()) {
            if (!siege.isChunkInBattleZone(chunkPos)) {
                continue;
            }

            if (playerFaction == null) {
                bestRelation = SiegeZoneRelation.OTHER;
                continue;
            }

            if (playerFaction.uuid.equals(siege.attackingFaction)) {
                return SiegeZoneRelation.ATTACKER;
            }

            if (playerFaction.uuid.equals(siege.defendingFaction)) {
                bestRelation = SiegeZoneRelation.DEFENDER;
            } else if (bestRelation == SiegeZoneRelation.NONE) {
                bestRelation = SiegeZoneRelation.OTHER;
            }
        }

        return bestRelation;
    }

    public void recalculateAllWealth() {
        for (Faction faction : mFactions.values()) faction.recalculateWealth();
    }

    public SiegeZoneResult getSiegeZone(DimChunkPos chunkPos) {
        UUID warDefender = null;
        for (Siege siege : sieges.values()) {
            if (siege.isChunkInSiegedZone(chunkPos))
                return new SiegeZoneResult(SiegeZone.SIEGED, siege.defendingFaction);
            if (warDefender == null && siege.isChunkInBattleZone(chunkPos))
                warDefender = siege.defendingFaction;
        }
        return warDefender == null ? SiegeZoneResult.NONE : new SiegeZoneResult(SiegeZone.WAR, warDefender);
    }

    public boolean isInOwnSiegeWarzone(UUID factionId, DimChunkPos pos) {
        if (factionId == null || factionId.equals(Faction.nullUuid)) return false;
        for (Siege siege : sieges.values()) {
            boolean attacker = factionId.equals(siege.attackingFaction);
            boolean defender = factionId.equals(siege.defendingFaction);
            if (!attacker && !defender) continue;
            int radius = attacker ? WarForgeConfig.SIEGE_ATTACKER_RADIUS : WarForgeConfig.SIEGE_DEFENDER_RADIUS;
            for (DimBlockPos camp : siege.attackingCamps) {
                if (camp != null && Siege.isPlayerInRadius(camp.toChunkPos(), pos, radius)) return true;
            }
        }
        return false;
    }

    public boolean isSiegeGraceProtected(Faction faction) {
        if (!WarForgeConfig.ENABLE_SIEGE_GRACE_PERIOD || faction == null) return false;
        return System.currentTimeMillis() < faction.siegeGraceUntil;
    }

    public Faction findNearbyOpposingFaction(Faction faction, DimChunkPos chunkPos) {
        int distance = WarForgeConfig.MIN_DISTANCE_BETWEEN_FACTIONS;
        if (distance <= 0) {
            return null;
        }
        UUID selfId = faction == null ? Faction.nullUuid : faction.uuid;
        for (int dx = -distance; dx <= distance; dx++) {
            for (int dz = -distance; dz <= distance; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                UUID owner = getClaim(new DimChunkPos(chunkPos.dim, chunkPos.x + dx, chunkPos.z + dz));
                if (owner.equals(Faction.nullUuid) || owner.equals(selfId) || IsNeutralZone(owner)) {
                    continue;
                }
                if (faction != null && faction.isAllyOf(owner)) {
                    continue;
                }
                Faction opposing = getFaction(owner);
                if (opposing != null) {
                    return opposing;
                }
            }
        }
        return null;
    }

    public boolean isConqueredWilderness(DimChunkPos chunk) {
        return conqueredChunks.containsKey(chunk) && getClaim(chunk).equals(Faction.nullUuid);
    }

    public int conqueredRemainingMs(DimChunkPos chunk) {
        ObjectIntPair<UUID> entry = conqueredChunks.get(chunk);
        return entry == null ? 0 : entry.getInteger();
    }

    public void setConquered(DimChunkPos chunk, UUID factionId, int periodMs) {
        conqueredChunks.put(chunk, new ObjectIntPair<>(copyUUID(factionId == null ? Faction.nullUuid : factionId), periodMs));
    }

    public boolean clearConquered(DimChunkPos chunk) {
        return conqueredChunks.remove(chunk) != null;
    }

    public void update() {
        for (HashMap.Entry<UUID, Faction> entry : mFactions.entrySet()) {
            entry.getValue().update();
        }
    }

    public void updateConqueredChunks(long msUpdateTime) {
        int msPassed = (int) (msUpdateTime - previousUpdateTimestamp); // the difference is likely less than 596h (max time storage of int using ms)

        // Iterate with an explicit iterator so expired entries are removed in-place; the previous
        // keySet()-then-remove loop threw ConcurrentModificationException (and swallowed it).
        var it = conqueredChunks.entrySet().iterator();
        while (it.hasNext()) {
            ObjectIntPair<UUID> chunkEntry = it.next().getValue();
            if (chunkEntry.getInteger() < msPassed) it.remove();
            else chunkEntry.setInteger(chunkEntry.getInteger() - msPassed);
        }
    }

    public void advanceSiegeDay() {
        pruneInvalidSieges();
        for (HashMap.Entry<DimChunkPos, Siege> kvp : sieges.entrySet()) {
            kvp.getValue().AdvanceDay();
            if (kvp.getValue().isCompleted())
                finishedSiegeQueue.add(kvp.getKey());
        }

        processCompleteSieges();

        if (!WarForgeConfig.LEGACY_USES_YIELD_TIMER) {
            for (HashMap.Entry<UUID, Faction> entry : mFactions.entrySet()) {
                entry.getValue().increaseLegacy();
            }
        }
    }

    public void updateSiegeTimers() {
        pruneInvalidSieges();
        for (HashMap.Entry<DimChunkPos, Siege> kvp : sieges.entrySet()) {
            Siege siege = kvp.getValue();
            siege.updateSiegeTimer();
            // Camp-less declared sieges have no camp TE to enforce attacker presence; do it here.
            if (siege.tickCamplessPresence()) {
                Siege.notifyAbandoned(getFaction(siege.attackingFaction), getFaction(siege.defendingFaction), true);
                siege.setAttackProgress(-5);
            }
            if (siege.isCompleted())
                finishedSiegeQueue.add(kvp.getKey());
        }
        processCompleteSieges();
    }

    public void advanceYieldDay() {
        for (HashMap.Entry<UUID, Faction> entry : mFactions.entrySet()) {
            entry.getValue().awardYields();
        }

        if (WarForgeConfig.LEGACY_USES_YIELD_TIMER) {
            for (HashMap.Entry<UUID, Faction> entry : mFactions.entrySet()) {
                entry.getValue().increaseLegacy();
            }
        }
    }

    public void playerDied(EntityPlayerMP playerWhoDied, DamageSource source) {
        EntityPlayerMP killer = source.getTrueSource() instanceof EntityPlayerMP ? (EntityPlayerMP) source.getTrueSource() : null;

        if (!sieges.isEmpty()) {
            for (HashMap.Entry<DimChunkPos, Siege> kvp : sieges.entrySet()) {
                kvp.getValue().onParticipantDeath(killer, playerWhoDied);
                if (kvp.getValue().isCompleted())
                    finishedSiegeQueue.add(kvp.getKey());
            }
            processCompleteSieges();
        }

        if (killer != null) {
            Faction killedFac = getFactionOfPlayer(playerWhoDied.getUniqueID());
            Faction killerFac = getFactionOfPlayer(killer.getUniqueID());

            if (killerFac != null) {
                int numTimesKilled = 0;
                if (killerFac.killCounter.containsKey(playerWhoDied.getUniqueID())) {
                    numTimesKilled = killerFac.killCounter.get(playerWhoDied.getUniqueID()) + 1;
                    killerFac.killCounter.replace(playerWhoDied.getUniqueID(), numTimesKilled);
                } else {
                    numTimesKilled = 1;
                    killerFac.killCounter.put(playerWhoDied.getUniqueID(), numTimesKilled);
                }

                if (numTimesKilled <= WarForgeConfig.NOTORIETY_KILL_CAP_PER_PLAYER) {
                    if (killerFac != killedFac) {
                        killer.sendMessage(new TextComponentString("Killing " + playerWhoDied.getName() + " earned your faction " + WarForgeConfig.NOTORIETY_PER_PLAYER_KILL + " notoriety"));
                        killerFac.notoriety += WarForgeConfig.NOTORIETY_PER_PLAYER_KILL;
                    }
                } else {
                    killer.sendMessage(new TextComponentString("Your faction has already killed " + playerWhoDied.getName() + " " + numTimesKilled + " times. You will not become more notorious."));
                }
            }
        }
    }

    public void clearNotoriety() {
        for (HashMap.Entry<UUID, Faction> entry : mFactions.entrySet()) {
            entry.getValue().notoriety = 0;
        }
    }

    public void clearLegacy() {
        for (HashMap.Entry<UUID, Faction> entry : mFactions.entrySet()) {
            entry.getValue().legacy = 0;
        }
    }

    public synchronized void processCompleteSieges() {
        while (!finishedSiegeQueue.isEmpty()) {
            var siege = finishedSiegeQueue.poll();
            Siege completed = sieges.get(siege);
            // Another path (TE conclusion / command termination) may have already removed this siege
            // before its queued entry drained; skip rather than NPE and abort the whole drain loop.
            if (completed == null) continue;
            completed.finished = true;
            handleCompletedSiege(siege);
        }
    }

    // cleaner separation between action to be done on completed sieges and the determination of these sieges
    public void handleCompletedSiege(DimChunkPos chunkPos) {
        handleCompletedSiege(chunkPos, true);
    }

    // Forceful siege termination, called via privileged user
    public void handleCompletedSiege(DimChunkPos chunkPos, siegeTermination termType) {
        Siege siege = sieges.get(chunkPos);
        if (siege == null) {
            LOGGER.warn("Attempted to complete non-existent siege at {}", chunkPos);
            return;
        }

        Faction attackers = getFaction(siege.attackingFaction);
        Faction defenders = getFaction(siege.defendingFaction);

        if (attackers == null || defenders == null) {
            LOGGER.error("Invalid factions in completed siege. Nothing will happen.");
            return;
        }

        DimBlockPos blockPos = defenders.getSpecificPosForClaim(chunkPos);

        switch (termType) {
            case WIN -> {
                if (WarForgeConfig.ATTACKER_CONQUERED_CHUNK_PERIOD > 0) {
                    conqueredChunks.put(chunkPos, new ObjectIntPair<>(copyUUID(attackers.uuid), WarForgeConfig.ATTACKER_CONQUERED_CHUNK_PERIOD));
                    for (DimBlockPos siegeCampPos : siege.attackingCamps) {
                        if (siegeCampPos != null)
                            conqueredChunks.put(siegeCampPos.toChunkPos(), new ObjectIntPair<>(copyUUID(attackers.uuid), WarForgeConfig.ATTACKER_CONQUERED_CHUNK_PERIOD));
                    }
                }

                defenders.onClaimLost(blockPos, true);
                mClaims.remove(blockPos.toChunkPos());

                attackers.messageAll(new TextComponentTranslation("warforge.info.siege_won_attackers", attackers.name, blockPos.toFancyString()));
                defenders.messageAll(new TextComponentTranslation("warforge.info.siege_lost_defenders", defenders.name, blockPos.toFancyString()));
                sendSiegeOutcomeNotifications(attackers, defenders, blockPos, true);
                attackers.notoriety += WarForgeConfig.NOTORIETY_PER_SIEGE_ATTACK_SUCCESS;

                if (WarForgeConfig.SIEGE_CAPTURE) {
                    MC_SERVER.getWorld(blockPos.dim).setBlockState(blockPos.toRegularPos(), Content.basicClaimBlock.getDefaultState());
                    TileEntity te = MC_SERVER.getWorld(blockPos.dim).getTileEntity(blockPos.toRegularPos());
                    onNonCitadelClaimPlaced((IClaim) te, attackers);
                }

                attackers.increaseSiegeMomentum(true);
                siege.onCompleted(true);
            }

            case LOSE -> {
                if (WarForgeConfig.DEFENDER_CONQUERED_CHUNK_PERIOD > 0) {
                    conqueredChunks.put(chunkPos, new ObjectIntPair<>(copyUUID(defenders.uuid), WarForgeConfig.DEFENDER_CONQUERED_CHUNK_PERIOD));
                    for (DimBlockPos siegeCampPos : siege.attackingCamps) {
                        if (siegeCampPos != null)
                            conqueredChunks.put(siegeCampPos.toChunkPos(), new ObjectIntPair<>(copyUUID(defenders.uuid), WarForgeConfig.DEFENDER_CONQUERED_CHUNK_PERIOD));
                    }
                }

                attackers.messageAll(new TextComponentTranslation("warforge.info.siege_lost_attackers", attackers.name, blockPos.toFancyString()));
                defenders.messageAll(new TextComponentTranslation("warforge.info.siege_won_defenders", defenders.name, blockPos.toFancyString()));
                sendSiegeOutcomeNotifications(attackers, defenders, blockPos, false);
                defenders.notoriety += WarForgeConfig.NOTORIETY_PER_SIEGE_DEFEND_SUCCESS;

                attackers.stopMomentum(true);
                attackers.messageAll(new TextComponentTranslation("warforge.info_momentum_lost"));

                siege.onCompleted(false);
            }

            case NEUTRAL -> {
                attackers.messageAll(new TextComponentTranslation("warforge.info.siege_cancelled_attackers", attackers.name, blockPos.toFancyString()));
                defenders.messageAll(new TextComponentTranslation("warforge.info.siege_cancelled_defenders", defenders.name, blockPos.toFancyString()));
                siege.onCompleted(false); // tbh Idk what to put here
            }
        }

        WarForgeMod.FACTIONS.sendSiegeInfoToNearby(siege.defendingClaim.toChunkPos());
        sieges.remove(chunkPos);
    }

    // cleanup is done by failing, passing, or cancelling siege through the TE class. If called without boolean, it is assumed to not be from inside TE
    public void handleCompletedSiege(DimChunkPos chunkPos, boolean doCleanup) {
        Siege siege = sieges.get(chunkPos);
        if (siege == null) {
            LOGGER.warn("Attempted to complete non-existent siege at {}", chunkPos);
            return;
        }

        Faction attackers = getFaction(siege.attackingFaction);
        Faction defenders = getFaction(siege.defendingFaction);
        if (attackers == null || defenders == null) {
            LOGGER.error("Invalid factions in completed siege. Nothing will happen.");
            return;
        }

        DimBlockPos blockPos = defenders.getSpecificPosForClaim(chunkPos);
        if (blockPos == null) {
            LOGGER.error("Defending claim position was null for completed siege at {} against faction {}", chunkPos, defenders.name);
            siege.onCompleted(false);
            sieges.remove(chunkPos);
            return;
        }
        boolean successful = siege.WasSuccessful();
        if (successful) {
            if (WarForgeConfig.ATTACKER_CONQUERED_CHUNK_PERIOD > 0) {
                conqueredChunks.put(chunkPos, new ObjectIntPair<>(copyUUID(attackers.uuid), WarForgeConfig.ATTACKER_CONQUERED_CHUNK_PERIOD));
                for (DimBlockPos siegeCampPos : siege.attackingCamps)
                    if (siegeCampPos != null)
                        conqueredChunks.put(siegeCampPos.toChunkPos(), new ObjectIntPair<>(copyUUID(attackers.uuid), WarForgeConfig.ATTACKER_CONQUERED_CHUNK_PERIOD));
            }

            defenders.onClaimLost(blockPos, true); // drops block if SIEGE_CAPTURE is off (claim is not overriden), or drops nothing if it is on (claim effectively removed and instantly replaced)
            mClaims.remove(blockPos.toChunkPos());
            attackers.messageAll(new TextComponentTranslation("warforge.info.siege_won_attackers", attackers.name, blockPos.toFancyString()));
            defenders.messageAll(new TextComponentTranslation("warforge.info.siege_lost_defenders", defenders.name, blockPos.toFancyString()));
            sendSiegeOutcomeNotifications(attackers, defenders, blockPos, true);
            attackers.notoriety += WarForgeConfig.NOTORIETY_PER_SIEGE_ATTACK_SUCCESS;
            if (WarForgeConfig.SIEGE_CAPTURE) {
                MC_SERVER.getWorld(blockPos.dim).setBlockState(blockPos.toRegularPos(), Content.basicClaimBlock.getDefaultState());
                TileEntity te = MC_SERVER.getWorld(blockPos.dim).getTileEntity(blockPos.toRegularPos());
                onNonCitadelClaimPlaced((IClaim) te, attackers);
            }
            attackers.increaseSiegeMomentum(true);
        } else {
            if (WarForgeConfig.DEFENDER_CONQUERED_CHUNK_PERIOD > 0) {
                conqueredChunks.put(chunkPos, new ObjectIntPair<>(copyUUID(defenders.uuid), WarForgeConfig.DEFENDER_CONQUERED_CHUNK_PERIOD)); // defenders get won claims defended
                for (DimBlockPos siegeCampPos : siege.attackingCamps)
                    if (siegeCampPos != null)
                        conqueredChunks.put(siegeCampPos.toChunkPos(), new ObjectIntPair<>(copyUUID(defenders.uuid), WarForgeConfig.DEFENDER_CONQUERED_CHUNK_PERIOD));
            }
            attackers.messageAll(new TextComponentTranslation("warforge.info.siege_lost_attackers", attackers.name, blockPos.toFancyString()));
            defenders.messageAll(new TextComponentTranslation("warforge.info.siege_won_defenders", defenders.name, blockPos.toFancyString()));
            sendSiegeOutcomeNotifications(attackers, defenders, blockPos, false);
            defenders.notoriety += WarForgeConfig.NOTORIETY_PER_SIEGE_DEFEND_SUCCESS;
            attackers.stopMomentum(true);
        }

        if (doCleanup) siege.onCompleted(successful);

        // Then remove the siege
        sieges.remove(chunkPos);

        //Remove defending status from the faction
        for (Siege activeSiege : sieges.values()) {
            if (activeSiege.defendingFaction.equals(defenders.uuid))
                return;
        }
        defenders.isCurrentlyDefending = false;
    }

    // Immediately ends every siege the given faction attacks or defends. Used when the faction is
    // disbanded / defeated so no siege (or ticking camp TE) is left referencing a faction that no
    // longer exists.
    private void terminateSiegesInvolving(UUID factionID) {
        if (factionID == null || sieges.isEmpty()) return;
        ArrayList<DimChunkPos> involved = new ArrayList<>();
        for (Map.Entry<DimChunkPos, Siege> kvp : sieges.entrySet()) {
            Siege siege = kvp.getValue();
            if (factionID.equals(siege.attackingFaction) || factionID.equals(siege.defendingFaction)) {
                involved.add(kvp.getKey());
            }
        }
        for (DimChunkPos key : involved) {
            Siege siege = sieges.remove(key);
            if (siege == null) continue;
            try {
                siege.onCompleted(false);
            } catch (Throwable t) {
                LOGGER.error("Error cleaning up siege at {} during faction removal", key, t);
            }
            Faction other = getFaction(factionID.equals(siege.attackingFaction) ? siege.defendingFaction : siege.attackingFaction);
            if (other != null) {
                other.messageAll(new TextComponentString("A siege was cancelled because the opposing faction no longer exists."));
            }
            clearDefendingFlagIfNoSieges(siege.defendingFaction);
        }
    }

    // Defensive sweep run each siege-update pass: drops any siege whose attacker/defender faction is
    // gone or whose defending claim is no longer held by the defender (admin edits, dimension unload,
    // untracked claim loss), so dangling sieges can't tick forever against dead state.
    private void pruneInvalidSieges() {
        if (sieges.isEmpty()) return;
        ArrayList<DimChunkPos> invalid = null;
        for (Map.Entry<DimChunkPos, Siege> kvp : sieges.entrySet()) {
            Siege siege = kvp.getValue();
            Faction attackers = getFaction(siege.attackingFaction);
            Faction defenders = getFaction(siege.defendingFaction);
            boolean targetGone = siege.defendingClaim == null
                    || !siege.defendingFaction.equals(getClaim(siege.defendingClaim.toChunkPos()));
            if (attackers == null || defenders == null || targetGone) {
                if (invalid == null) invalid = new ArrayList<>();
                invalid.add(kvp.getKey());
            }
        }
        if (invalid == null) return;
        for (DimChunkPos key : invalid) {
            Siege siege = sieges.remove(key);
            if (siege == null) continue;
            try {
                siege.onCompleted(false);
            } catch (Throwable t) {
                LOGGER.error("Error cleaning up dangling siege at {}", key, t);
            }
            clearDefendingFlagIfNoSieges(siege.defendingFaction);
            LOGGER.warn("Pruned dangling siege at {} (participant or target no longer valid)", key);
        }
    }

    private void clearDefendingFlagIfNoSieges(UUID defendingFaction) {
        Faction defenders = getFaction(defendingFaction);
        if (defenders == null) return;
        for (Siege other : sieges.values()) {
            if (other.defendingFaction.equals(defendingFaction)) return;
        }
        defenders.isCurrentlyDefending = false;
    }

    public boolean requestCreateFaction(TileEntityCitadel citadel, EntityPlayer player, String factionName, int colour) {
        if (citadel == null) {
            player.sendMessage(new TextComponentString("You can't create a faction without a citadel"));
            return false;
        }

        if (!validateFactionName(factionName, player, null)) {
            return false;
        }

        Faction existingFaction = getFactionOfPlayer(player.getUniqueID());
        if (existingFaction != null) {
            player.sendMessage(new TextComponentString("You are already in a faction"));
            return false;
        }

        invalidateRedeemableInsuranceVault(player.getUniqueID(), player);

        UUID proposedID = Faction.createUUID(factionName);
        while (mFactions.containsKey(proposedID)) {
            proposedID = UUID.randomUUID();
        }

        if (colour == 0xffffff) {
            colour = Color.HSBtoRGB(rand.nextFloat(), rand.nextFloat() * 0.5f + 0.5f, 1.0f);
        }

        // All checks passed, create a faction
        Faction faction = new Faction();
        faction.onlinePlayerCount = 1;
        faction.uuid = proposedID;
        faction.name = factionName;
        faction.citadelPos = new DimBlockPos(citadel);
        faction.claims.put(faction.citadelPos, 0);
        faction.claimTypes.put(faction.citadelPos, Faction.ClaimType.CITADEL);
        faction.colour = colour;
        faction.notoriety = 0;
        faction.legacy = 0;
        faction.recalculateWealth();
        if (WarForgeConfig.ENABLE_SIEGE_GRACE_PERIOD && WarForgeConfig.SIEGE_GRACE_PERIOD_HOURS > 0) {
            faction.siegeGraceUntil = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(WarForgeConfig.SIEGE_GRACE_PERIOD_HOURS);
        }

        mFactions.put(proposedID, faction);
        citadel.onServerCreateFaction(faction);
        mClaims.put(citadel.getClaimPos().toChunkPos(), proposedID);
        LEADERBOARD.RegisterFaction(faction);

        INSTANCE.messageAll(new TextComponentString(player.getName() + " created the faction " + factionName), true);

        faction.addPlayer(player.getUniqueID());
        faction.setLeader(player.getUniqueID());
        PacketNamePlateChange packet = new PacketNamePlateChange();
        packet.name = player.getName();
        packet.faction = factionName;
        packet.color = faction.colour;
        NETWORK.sendToAllAround(packet, player.posX, player.posY, player.posZ, 100, player.dimension);
        return true;
    }

    public boolean requestRenameFaction(ICommandSender sender, UUID factionId, String newName) {
        Faction faction = getFaction(factionId);
        if (faction == null) {
            sender.sendMessage(new TextComponentString("That faction doesn't exist"));
            return false;
        }
        if (!validateFactionName(newName, sender, faction.uuid)) {
            return false;
        }

        String oldName = faction.name;
        faction.name = newName;

        for (World world : MC_SERVER.worlds) {
            for (TileEntity te : world.loadedTileEntityList) {
                if (te instanceof TileEntityClaim claim && claim.getFaction().equals(faction.uuid)) {
                    claim.updateFactionName(newName);
                }
            }
        }

        ArrayList<EntityPlayer> onlinePlayers = faction.getOnlinePlayers(entityPlayer -> entityPlayer != null && entityPlayer.isEntityAlive());
        onlinePlayers.forEach(entityPlayer -> {
            PacketNamePlateChange packet = new PacketNamePlateChange();
            packet.name = entityPlayer.getName();
            packet.faction = newName;
            packet.color = faction.colour;
            NETWORK.sendToAllAround(packet, entityPlayer.posX, entityPlayer.posY, entityPlayer.posZ, 100, entityPlayer.dimension);
        });

        com.flansmod.warforge.common.util.FactionDisplay.refreshFactionTabNames(faction);

        INSTANCE.messageAll(new TextComponentString("Faction " + oldName + " was renamed to " + newName), true);
        return true;
    }

    public boolean requestLevelUp(EntityPlayerMP officer, UUID factionID) {
        Faction faction = getFaction(factionID);
        if (faction == null) {
            officer.sendMessage(new TextComponentString("That faction doesn't exist"));
            return false;
        }

        if (!faction.isPlayerInFaction(officer.getUniqueID())) {
            officer.sendMessage(new TextComponentString("You are not in that faction"));
            return false;
        }
        if (!faction.isPlayerRoleInFaction(officer.getUniqueID(), Role.OFFICER) && !faction.isPlayerRoleInFaction(officer.getUniqueID(), Role.LEADER)) {
            officer.sendMessage(new TextComponentString("You must be officer or higher to upgrade citadel"));
            return false;
        }

        Map<StackComparable, Integer> requiredItems = (Map<StackComparable, Integer>) UPGRADE_HANDLER.getRequirementsFor(faction.citadelLevel + 1).clone();

        if (requiredItems == null) {
            officer.sendMessage(new TextComponentString("You cannot level up fruther"));
            return false;
        }

        List<ItemStack> invCopy = officer.inventory.mainInventory.stream()
                .map(ItemStack::copy)
                .collect(Collectors.toList());
        //Copy for safety
        boolean passed = true;

        outer:
        for (Map.Entry<StackComparable, Integer> entry : requiredItems.entrySet()) {
            StackComparable sc = entry.getKey();
            int required = entry.getValue();

            for (ItemStack stack : invCopy) {
                if (!stack.isEmpty() && sc.equals(stack)) {
                    int consumed = Math.min(required, stack.getCount());
                    required -= consumed;
                    stack.shrink(consumed);
                }
                if (required == 0) break;
            }

            if (required > 0) {
                passed = false;
                break;
            }
        }


        if (!passed || invCopy.size() != 36) { //Schizophrenia
            officer.sendMessage(new TextComponentString("Could not upgrade: you don't have the required items"));
            return false;
        }

        for (int i = 0; i < officer.inventory.mainInventory.size(); i++) {
            officer.inventory.mainInventory.set(i, invCopy.get(i));
        }

        faction.citadelLevel++;
        faction.messageAll(new TextComponentString(
                officer.getName() + " has upgraded your facition to level " +
                        faction.citadelLevel + "! You can now claim " +
                        UPGRADE_HANDLER.getClaimLimitForLevel(faction.citadelLevel) +
                        " chunks and force-load up to " + faction.getMaxForceLoadedChunks() + " chunks."));
        sendUpgradeNotification(faction, officer.getUniqueID(), officer.getName());


        faction.soundEffectAll(Sounds.sfxUpgrade);
        EffectUpgrade.composeEffect(faction.citadelPos.dim, faction.citadelPos.toRegularPos().up(2), 100, 400, 1f, 0.2D, faction.colour, 10);
        return true;
    }

    public boolean requestChooseFactionFlag(EntityPlayerMP player, UUID factionID, String flagId) {
        Faction faction = getFaction(factionID);
        if (faction == null) {
            player.sendMessage(new TextComponentString("That faction doesn't exist"));
            return false;
        }
        if (!WarForgeMod.isOp(player) && !faction.isPlayerRoleInFaction(player.getUniqueID(), Role.LEADER)) {
            player.sendMessage(new TextComponentString("You are not the faction leader"));
            return false;
        }
        if (!faction.flagId.isEmpty()) {
            player.sendMessage(new TextComponentString("This faction has already chosen its flag"));
            return false;
        }
        if (!WarForgeMod.FLAG_REGISTRY.isAvailable(flagId)) {
            player.sendMessage(new TextComponentString("That flag is not available"));
            return false;
        }

        faction.flagId = flagId;
        for (World world : MC_SERVER.worlds) {
            for (TileEntity te : world.loadedTileEntityList) {
                if (te instanceof TileEntityClaim claim && claim.getFaction().equals(faction.uuid)) {
                    claim.updateFactionFlag(flagId);
                }
            }
        }
        if (faction.citadelPos != null) {
            WarForgeMod.syncClaimToPlayer(player, faction.citadelPos.toRegularPos());
        }
        for (EntityPlayerMP online : MC_SERVER.getPlayerList().getPlayers()) {
            sendClaimChunks(online, new DimChunkPos(online.dimension, online.getPosition()), WarForgeConfig.CLAIM_MANAGER_RADIUS);
        }
        faction.messageAll(new TextComponentString("Your faction selected its flag: " + flagId));
        return true;
    }

    public boolean requestRemovePlayerFromFaction(ICommandSender remover, UUID factionID, UUID toRemove) {
        Faction faction = getFaction(factionID);
        if (faction == null) {
            remover.sendMessage(new TextComponentString("That faction doesn't exist"));
            return false;
        }

        if (!faction.isPlayerInFaction(toRemove)) {
            remover.sendMessage(new TextComponentString("That player is not in that faction"));
            return false;
        }

        boolean canRemove = isOp(remover);
        boolean removingSelf = false;
        if (remover instanceof EntityPlayer) {
            UUID removerID = ((EntityPlayer) remover).getUniqueID();
            if (removerID.equals(toRemove)) // remove self
            {
                canRemove = true;
                removingSelf = true;
            }

            if (faction.isPlayerOutrankingOfficer(removerID, toRemove))
                canRemove = true;
        }

        if (!canRemove) {
            remover.sendMessage(new TextComponentString("You don't have permission to remove that player"));
            return false;
        }

        GameProfile userProfile = MC_SERVER.getPlayerProfileCache().getProfileByUUID(toRemove);
        if (userProfile != null) {
            if (removingSelf) {
                faction.messageAll(new TextComponentString(userProfile.getName() + " left " + faction.name));
                if (faction.getMemberCount() <= 1)
                    faction.messageAll(new TextComponentString(faction.name + "was abandoned and disbanded."));
            } else
                faction.messageAll(new TextComponentString(userProfile.getName() + " was kicked from " + faction.name));
        } else {
            remover.sendMessage(new TextComponentString("Error: Could not get user profile"));
        }

        faction.removePlayer(toRemove);
        if (userProfile != null && faction.getMemberCount() > 0) {
            sendFactionMemberLeftNotification(faction, toRemove, userProfile.getName(), !removingSelf);
        }

        if (faction.getMemberCount() < 1)
            disbandAndCleanup(faction);

        sendAllSiegeInfoToNearby();

        return true;
    }

    public boolean requestInvitePlayerToMyFaction(EntityPlayer factionOfficer, UUID invitee) {
        Faction myFaction = getFactionOfPlayer(factionOfficer.getUniqueID());
        if (myFaction != null)
            return RequestInvitePlayerToFaction(factionOfficer, myFaction.uuid, invitee);
        return false;
    }

    public boolean RequestInvitePlayerToFaction(ICommandSender factionOfficer, UUID factionID, UUID invitee) {
        Faction faction = getFaction(factionID);
        if (faction == null) {
            factionOfficer.sendMessage(new TextComponentString("That faction doesn't exist"));
            return false;
        }

        if (!isOp(factionOfficer) && !faction.isPlayerRoleInFaction(getUUID(factionOfficer), Faction.Role.OFFICER)) {
            factionOfficer.sendMessage(new TextComponentString("You are not an officer of this faction"));
            return false;
        }

        Faction existingFaction = getFactionOfPlayer(invitee);
        if (existingFaction != null) {
            factionOfficer.sendMessage(new TextComponentString("That player is already in a faction"));
            return false;
        }

        // TODO: Faction player limit - grows with claims?

        faction.invitePlayer(invitee);
        EntityPlayerMP inviteePlayer = MC_SERVER.getPlayerList().getPlayerByUUID(invitee);
        if (inviteePlayer != null) {
            inviteePlayer.sendMessage(createInviteChatMessage(faction));
            sendNotificationToPlayer(
                    inviteePlayer,
                    "faction_invite_" + faction.uuid,
                    "Faction Invite",
                    faction.name + "",
                    faction.colour,
                    7000,
                    factionOfficer instanceof EntityPlayer ? ((EntityPlayer) factionOfficer).getUniqueID() : null
            );
        }

        return true;
    }

    public void RequestAcceptInvite(EntityPlayer player) {
        RequestAcceptInvite(player, (String) null);
    }

    public void RequestAcceptInvite(EntityPlayer player, UUID factionId) {
        if (factionId == null || factionId.equals(Faction.nullUuid)) {
            RequestAcceptInvite(player, (String) null);
            return;
        }

        Faction inviter = getFaction(factionId);
        if (inviter == null || !inviter.isInvitingPlayer(player.getUniqueID())) {
            player.sendMessage(new TextComponentString("You do not have an invite from that faction"));
            return;
        }

        if (getFactionOfPlayer(player.getUniqueID()) != null) {
            player.sendMessage(new TextComponentString("You are already in a faction"));
            return;
        }

        invalidateRedeemableInsuranceVault(player.getUniqueID(), player);
        inviter.addPlayer(player.getUniqueID());
        sendFactionMemberJoinedNotification(inviter, player);
    }

    public void RequestAcceptInvite(EntityPlayer player, String factionName) {
        if (getFactionOfPlayer(player.getUniqueID()) != null) {
            player.sendMessage(new TextComponentString("You are already in a faction"));
            return;
        }

        ArrayList<Faction> invites = getFactionsWithOpenInvitesTo(player.getUniqueID());
        if (invites.isEmpty()) {
            player.sendMessage(new TextComponentString("You have no open invite to accept"));
            return;
        }

        Faction inviter;
        if (factionName != null && !factionName.trim().isEmpty()) {
            inviter = getFactionWithOpenInviteTo(player.getUniqueID(), factionName.trim());
            if (inviter == null) {
                player.sendMessage(new TextComponentString("You do not have an invite from " + factionName));
                return;
            }
        } else if (invites.size() == 1) {
            inviter = invites.get(0);
        } else {
            player.sendMessage(new TextComponentString("You have multiple faction invites. Use /f accept <factionName> or click one below:"));
            for (Faction invite : invites) {
                player.sendMessage(createInviteChatMessage(invite));
            }
            return;
        }

        invalidateRedeemableInsuranceVault(player.getUniqueID(), player);
        inviter.addPlayer(player.getUniqueID());
        sendFactionMemberJoinedNotification(inviter, player);
    }

    public boolean RequestTransferLeadership(EntityPlayer factionLeader, UUID factionID, UUID newLeaderID) {
        Faction faction = getFaction(factionID);
        if (faction == null) {
            factionLeader.sendMessage(new TextComponentString("That faction does not exist"));
            return false;
        }

        if (!isOp(factionLeader) && !faction.isPlayerRoleInFaction(factionLeader.getUniqueID(), Faction.Role.LEADER)) {
            factionLeader.sendMessage(new TextComponentString("You are not the leader of this faction"));
            return false;
        }

        if (!faction.isPlayerInFaction(newLeaderID)) {
            factionLeader.sendMessage(new TextComponentString("That player is not in your faction"));
            return false;
        }

        // Do the set
        if (!faction.setLeader(newLeaderID)) {
            factionLeader.sendMessage(new TextComponentString("Failed to set leader"));
            return false;
        }

        factionLeader.sendMessage(new TextComponentString("Successfully set leader"));
        return true;
    }

    public boolean requestPromote(EntityPlayer factionLeader, EntityPlayerMP target) {
        Faction faction = getFactionOfPlayer(factionLeader.getUniqueID());
        if (faction == null) {
            factionLeader.sendMessage(new TextComponentString("You are not in a faction"));
            return false;
        }
        if (!faction.isPlayerRoleInFaction(factionLeader.getUniqueID(), Role.LEADER)) {
            factionLeader.sendMessage(new TextComponentString("You are not the leader of this faction"));
            return false;
        }
        if (!faction.isPlayerRoleInFaction(target.getUniqueID(), Role.MEMBER)) {
            factionLeader.sendMessage(new TextComponentString("This player cannot be promoted"));
            return false;
        }

        faction.promote(target.getUniqueID());
        return true;
    }

    public boolean requestDemote(EntityPlayer factionLeader, EntityPlayerMP target) {
        Faction faction = getFactionOfPlayer(factionLeader.getUniqueID());
        if (faction == null) {
            factionLeader.sendMessage(new TextComponentString("You are not in a faction"));
            return false;
        }
        if (!faction.isPlayerRoleInFaction(factionLeader.getUniqueID(), Role.LEADER)) {
            factionLeader.sendMessage(new TextComponentString("You are not the leader of this faction"));
            return false;
        }
        if (!faction.isPlayerRoleInFaction(target.getUniqueID(), Role.OFFICER)) {
            factionLeader.sendMessage(new TextComponentString("This player cannot be demoted"));
            return false;
        }

        faction.demote(target.getUniqueID());
        return true;
    }

    public boolean requestDisbandFaction(EntityPlayer factionLeader, UUID factionID) {
        if (factionID.equals(Faction.nullUuid)) {
            Faction faction = getFactionOfPlayer(factionLeader.getUniqueID());
            if (faction != null)
                factionID = faction.uuid;
        }

        if (!IsPlayerRoleInFaction(factionLeader.getUniqueID(), factionID, Faction.Role.LEADER)) {
            factionLeader.sendMessage(new TextComponentString("You are not the leader of this faction"));
            return false;
        }
        disbandAndCleanup(factionID, false);
        return true;
    }

    // Used to remove and clean up faction;
    private void disbandAndCleanup(UUID factionID) {
        disbandAndCleanup(mFactions.get(factionID), false);
    }

    private void disbandAndCleanup(UUID factionID, boolean unlockInsurance) {
        disbandAndCleanup(mFactions.get(factionID), unlockInsurance);
    }

    private void disbandAndCleanup(Faction faction) {
        disbandAndCleanup(faction, false);
    }

    private void disbandAndCleanup(Faction faction, boolean unlockInsurance) {
        if (faction == null) {
            return;
        }
        // End any siege this faction attacks or defends before its data is torn down, otherwise the
        // siege (and any ticking camp TEs) would be left pointing at a faction that no longer exists.
        terminateSiegesInvolving(faction.uuid);
        if (unlockInsurance) {
            unlockInsuranceVault(faction);
        }
        EffectDisband.composeEffect(faction.citadelPos.dim, faction.citadelPos.toRegularPos(), 100);
        for (Map.Entry<DimBlockPos, Integer> kvp : faction.claims.entrySet()) {
            mClaims.remove(kvp.getKey().toChunkPos());
        }
        ArrayList<EntityPlayer> onlinePlayers = faction.getOnlinePlayers(
                player -> player != null);

        onlinePlayers.forEach(player -> {
            PacketNamePlateChange packet = new PacketNamePlateChange();
            packet.name = player.getName();
            packet.isRemove = true;
            NETWORK.sendToAllAround(packet, player.posX, player.posY, player.posZ, 100, player.dimension);
        });
        faction.disband();
        mFactions.remove(faction.uuid);
        removeFactionFromAllAlliances(faction.uuid);
        WarForgeMod.CHUNK_LOADING_MANAGER.releaseFaction(faction.uuid);
        LEADERBOARD.UnregisterFaction(faction);
    }

    //Use disbandAndCleanup
    public void FactionDefeated(Faction faction) {
        disbandAndCleanup(faction, true);

    }

    public boolean requestRedeemInsuranceVault(EntityPlayerMP player) {
        ArrayList<ItemStack> items = redeemableInsuranceVaults.get(player.getUniqueID());
        if (items == null || items.isEmpty()) {
            player.sendMessage(new TextComponentString("You do not have an unlocked insurance stash to redeem"));
            return false;
        }

        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ItemStack redeemStack = stack.copy();
            boolean inserted = player.inventory.addItemStackToInventory(redeemStack);
            if (!inserted && !redeemStack.isEmpty()) {
                player.dropItem(redeemStack, false);
            }
        }

        redeemableInsuranceVaults.remove(player.getUniqueID());
        player.sendMessage(new TextComponentString("Redeemed your faction insurance stash"));
        return true;
    }

    private void unlockInsuranceVault(Faction faction) {
        if (!faction.hasInsuranceContents()) {
            return;
        }

        UUID leaderId = faction.getLeaderId();
        if (leaderId.equals(Faction.nullUuid)) {
            LOGGER.warn("Could not unlock insurance stash for faction {} because no leader was found", faction.name);
            return;
        }

        ArrayList<ItemStack> payout = redeemableInsuranceVaults.computeIfAbsent(leaderId, ignored -> new ArrayList<ItemStack>());
        payout.addAll(faction.drainInsuranceContents());

        EntityPlayerMP leader = MC_SERVER.getPlayerList().getPlayerByUUID(leaderId);
        if (leader != null) {
            leader.sendMessage(new TextComponentString("Your faction insurance stash has been unlocked. Use /f vault redeem to withdraw it."));
        }
    }

    private void invalidateRedeemableInsuranceVault(UUID playerId, EntityPlayer player) {
        ArrayList<ItemStack> removed = redeemableInsuranceVaults.remove(playerId);
        if (removed != null && !removed.isEmpty() && player != null) {
            player.sendMessage(new TextComponentString("Your unlocked insurance stash was voided because you entered a new faction before redeeming it."));
        }
    }

    public boolean IsSiegeInProgress(DimChunkPos chunkPos) {

        for (Map.Entry<DimChunkPos, Siege> kvp : sieges.entrySet()) {
            if (kvp.getKey().equals(chunkPos))
                return true;

            for (DimBlockPos attackerPos : kvp.getValue().attackingCamps) {
                if (attackerPos != null && attackerPos.toChunkPos().equals(chunkPos))
                    return true;
            }
        }
        return false;
    }

    public boolean isChunkInBattleZone(DimChunkPos chunkPos) {
        for (Siege siege : sieges.values()) {
            if (siege.isChunkInBattleZone(chunkPos)) {
                return true;
            }
        }
        return false;
    }

    public boolean isChunkContested(DimChunkPos chunkPos) {
        return IsSiegeInProgress(chunkPos) || isChunkInBattleZone(chunkPos);
    }

    public void onFactionMemberLoggedIn(UUID playerID) {
        Faction faction = getFactionOfPlayer(playerID);
        if (!isValidFaction(faction)) {
            return;
        }
        faction.onlinePlayerCount += 1;
        faction.offlineRaidProtectionUntil = 0L;
        GameProfile profile = MC_SERVER.getPlayerProfileCache().getProfileByUUID(playerID);
        if (profile != null) {
            sendFactionPresenceNotification(faction, playerID, profile.getName(), true);
        }
    }

    public void onFactionMemberLoggedOut(UUID playerID) {
        Faction faction = getFactionOfPlayer(playerID);
        if (!isValidFaction(faction)) {
            return;
        }
        faction.onlinePlayerCount = Math.max(0, faction.onlinePlayerCount - 1);
        GameProfile profile = MC_SERVER.getPlayerProfileCache().getProfileByUUID(playerID);
        if (profile != null) {
            sendFactionPresenceNotification(faction, playerID, profile.getName(), false);
        }
        if (faction.onlinePlayerCount == 0 && WarForgeConfig.ENABLE_OFFLINE_RAID_PROTECTION && !faction.offlineRaidProtectionDisabled) {
            faction.offlineRaidProtectionUntil = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(WarForgeConfig.OFFLINE_RAID_PROTECTION_HOURS);
        }
    }

    public boolean isOfflineRaidProtected(Faction faction) {
        if (!WarForgeConfig.ENABLE_OFFLINE_RAID_PROTECTION || faction == null || faction.offlineRaidProtectionDisabled) {
            return false;
        }
        return faction.onlinePlayerCount <= 0 && System.currentTimeMillis() < faction.offlineRaidProtectionUntil;
    }

    // runs on the server only
    public void requestStartSiege(EntityPlayer factionOfficer, DimBlockPos siegeCampPos, Vec3i direction) {
        Faction attacking = getFactionOfPlayer(factionOfficer.getUniqueID());
        if (attacking == null) {
            factionOfficer.sendMessage(new TextComponentString("You are not in a faction"));
            return;
        }
        long currentTimeStamp = System.currentTimeMillis();

        // for some reason, server tick is in number of ticks and last siege timestamp is in ms, while siege cooldown is in mins (according to description), though through calculations looks like hours? it should be in ms
        if (attacking.getSiegeMomentum() == 0 && attacking.lastSiegeTimestamp + WarForgeConfig.SIEGE_COOLDOWN_FAIL > currentTimeStamp) {
            factionOfficer.sendMessage(new TextComponentString("Your faction is on cooldown on starting a new siege"));


            factionOfficer.sendMessage(new TextComponentString("Cooldown remaining:" + TimeHelper.formatTime(attacking.lastSiegeTimestamp + WarForgeConfig.SIEGE_COOLDOWN_FAIL - currentTimeStamp)));
            return;
        }


        if (!attacking.isPlayerRoleInFaction(factionOfficer.getUniqueID(), Faction.Role.OFFICER)) {
            factionOfficer.sendMessage(new TextComponentString("You are not an officer of this faction"));
            return;
        }

        // TODO: Verify there aren't existing alliances

        if (direction.getZ() == 0 && direction.getX() == 0) {
            factionOfficer.sendMessage(new TextComponentString("You can't siege the siege block!"));
            return;
        }

        // Server-side authority: the camp GUI only ever offers cardinally-adjacent targets, but the
        // wire packet carries raw offset bytes. Reject crafted diagonal / non-adjacent directions so a
        // malicious client can't siege a chunk that bypassed the GUI's adjacency / vertical checks.
        if (Math.abs(direction.getX()) > 1 || Math.abs(direction.getZ()) > 1
                || (direction.getX() != 0 && direction.getZ() != 0)) {
            factionOfficer.sendMessage(new TextComponentString("Invalid siege direction"));
            return;
        }

        TileEntitySiegeCamp siegeTE = (TileEntitySiegeCamp) MC_SERVER.getWorld(siegeCampPos.dim).getTileEntity(siegeCampPos.toRegularPos());
        if (siegeTE == null) {
            factionOfficer.sendMessage(new TextComponentString("Could not find that siege camp"));
            return;
        }
        if (siegeTE.getSiegeTarget() != null) {
            factionOfficer.sendMessage(new TextComponentString("That siege camp is already committed to an active siege"));
            return;
        }
        if (!attacking.uuid.equals(siegeTE.getFaction())) {
            factionOfficer.sendMessage(new TextComponentString("Your faction doesn't own this block!"));
            return;
        }
        for (Siege siege : sieges.values()) {
            if (siege.attackingCamps.contains(siegeCampPos)) {
                factionOfficer.sendMessage(new TextComponentString("That siege camp is already part of an active siege"));
                return;
            }
        }

        DimChunkPos defendingChunk = siegeCampPos.toChunkPos().Offset(direction);
        UUID defendingFactionID = mClaims.get(defendingChunk);
        Faction defending = getFaction(defendingFactionID);

        if (defending == null) {
            factionOfficer.sendMessage(new TextComponentString("Could not find a target faction at that position"));
            return;
        }

        String allianceBlock = getSiegeBlockReason(attacking, defending);
        if (allianceBlock != null) {
            factionOfficer.sendMessage(new TextComponentString(allianceBlock));
            return;
        }

        if (isSiegeGraceProtected(defending)) {
            factionOfficer.sendMessage(new TextComponentString("That faction is too new to be sieged. Grace expires in " + TimeHelper.formatTime(defending.siegeGraceUntil - System.currentTimeMillis())));
            return;
        }

        if (isOfflineRaidProtected(defending)) {
            factionOfficer.sendMessage(new TextComponentString("That faction is offline and protected until " + TimeHelper.formatTime(defending.offlineRaidProtectionUntil - System.currentTimeMillis())));
            return;
        }

        DimBlockPos defendingPos = defending.getSpecificPosForClaim(defendingChunk);
        if (defendingPos == null) {
            factionOfficer.sendMessage(new TextComponentString("Could not find a valid defending claim in that chunk"));
            return;
        }

        if (IsSiegeInProgress(defendingPos.toChunkPos())) {
            factionOfficer.sendMessage(new TextComponentString("That position is already under siege"));
            return;
        }

        if (conqueredChunks.get(defendingPos.toChunkPos()) != null) {
            factionOfficer.sendMessage(new TextComponentTranslation("warforge.info.chunk_is_conquered",
                    defending.name, TimeHelper.formatTime(conqueredChunks.get(defendingPos.toChunkPos()).getInteger())));
            return;
        }

        Siege siege = createSiege(attacking, defending, defendingPos, defendingChunk, siegeCampPos, false);
        siegeTE.setSiegeTarget(defendingPos);
        siege.start();

        attacking.lastSiegeTimestamp = currentTimeStamp;
    }

    // Shared siege construction for both the camp-driven path (requestStartSiege) and the UI-declared
    // path (requestDeclareSiege). For camp sieges anchorPos is the physical camp block; for camp-less
    // declared sieges it is the representative block of the chosen start-from chunk.
    private Siege createSiege(Faction attacking, Faction defending, DimBlockPos defendingPos,
                              DimChunkPos defendingChunk, DimBlockPos anchorPos, boolean campless) {
        attacking.siegeGraceUntil = 0L;
        long maxTime = WarForgeConfig.SIEGE_MOMENTUM_TIME.get(attacking.getSiegeMomentum()) * 1000L;
        Siege siege = new Siege(attacking.uuid, defending.uuid, defendingPos, maxTime);
        siege.setBattleRadius(WarForgeConfig.SIEGE_BATTLE_RADIUS);
        siege.campless = campless;
        siege.attackingCamps.add(anchorPos);
        sieges.put(defendingChunk, siege);
        return siege;
    }

    // UI-declared, camp-less siege. The target chunk is picked in stage 1 and the start-from chunk in
    // stage 2; this consumes one siege camp block item and anchors the siege logically at the
    // start-from chunk. Mirrors requestStartSiege's validation but enforces the configurable range
    // rule instead of camp adjacency and requires the target claim to permit being sieged.
    public void requestDeclareSiege(EntityPlayerMP officer, DimChunkPos targetChunk, DimChunkPos fromChunk) {
        if (!WarForgeConfig.SIEGE_ALLOW_UI_DECLARE) {
            officer.sendMessage(new TextComponentString("Declaring sieges from the map is disabled on this server"));
            return;
        }
        if (targetChunk == null || fromChunk == null) return;

        Faction attacking = getFactionOfPlayer(officer.getUniqueID());
        if (attacking == null) {
            officer.sendMessage(new TextComponentString("You are not in a faction"));
            return;
        }
        long currentTimeStamp = System.currentTimeMillis();
        if (attacking.getSiegeMomentum() == 0 && attacking.lastSiegeTimestamp + WarForgeConfig.SIEGE_COOLDOWN_FAIL > currentTimeStamp) {
            officer.sendMessage(new TextComponentString("Your faction is on cooldown on starting a new siege"));
            officer.sendMessage(new TextComponentString("Cooldown remaining:" + TimeHelper.formatTime(attacking.lastSiegeTimestamp + WarForgeConfig.SIEGE_COOLDOWN_FAIL - currentTimeStamp)));
            return;
        }
        if (!attacking.isPlayerRoleInFaction(officer.getUniqueID(), Faction.Role.OFFICER)) {
            officer.sendMessage(new TextComponentString("You are not an officer of this faction"));
            return;
        }

        // Same dimension only (the map data and the player are bound to one dimension) and a real,
        // in-range separation between the start-from chunk and the target.
        if (targetChunk.dim != fromChunk.dim || targetChunk.dim != officer.dimension) {
            officer.sendMessage(new TextComponentString("The target and start chunk must be in your current dimension"));
            return;
        }
        int chebyshev = Math.max(Math.abs(targetChunk.x - fromChunk.x), Math.abs(targetChunk.z - fromChunk.z));
        if (chebyshev == 0) {
            officer.sendMessage(new TextComponentString("Pick a start chunk other than the target"));
            return;
        }
        if (chebyshev > WarForgeConfig.SIEGE_DECLARE_MAX_RANGE) {
            officer.sendMessage(new TextComponentString("That target is too far from your chosen start chunk (max " + WarForgeConfig.SIEGE_DECLARE_MAX_RANGE + " chunks)"));
            return;
        }

        UUID defendingFactionID = mClaims.get(targetChunk);
        Faction defending = getFaction(defendingFactionID);
        if (defending == null) {
            officer.sendMessage(new TextComponentString("There is no faction claim to siege at that chunk"));
            return;
        }
        if (defending.uuid.equals(attacking.uuid)) {
            officer.sendMessage(new TextComponentString("You can't siege your own faction's claim"));
            return;
        }

        String allianceBlock = getSiegeBlockReason(attacking, defending);
        if (allianceBlock != null) {
            officer.sendMessage(new TextComponentString(allianceBlock));
            return;
        }
        if (isSiegeGraceProtected(defending)) {
            officer.sendMessage(new TextComponentString("That faction is too new to be sieged. Grace expires in " + TimeHelper.formatTime(defending.siegeGraceUntil - System.currentTimeMillis())));
            return;
        }

        if (isOfflineRaidProtected(defending)) {
            officer.sendMessage(new TextComponentString("That faction is offline and protected until " + TimeHelper.formatTime(defending.offlineRaidProtectionUntil - System.currentTimeMillis())));
            return;
        }

        DimBlockPos defendingPos = defending.getSpecificPosForClaim(targetChunk);
        if (defendingPos == null) {
            officer.sendMessage(new TextComponentString("Could not find a valid defending claim in that chunk"));
            return;
        }

        // Respect claims that opt out of being sieged (island collectors, admin claims, ...).
        TileEntity targetTe = MC_SERVER.getWorld(defendingPos.dim).getTileEntity(defendingPos.toRegularPos());
        if (targetTe instanceof IClaim && !((IClaim) targetTe).canBeSieged()) {
            officer.sendMessage(new TextComponentString("That claim cannot be sieged"));
            return;
        }

        if (IsSiegeInProgress(targetChunk)) {
            officer.sendMessage(new TextComponentString("That position is already under siege"));
            return;
        }
        if (conqueredChunks.get(targetChunk) != null) {
            officer.sendMessage(new TextComponentTranslation("warforge.info.chunk_is_conquered",
                    defending.name, TimeHelper.formatTime(conqueredChunks.get(targetChunk).getInteger())));
            return;
        }

        // Cost: consume one siege camp block item from the officer's inventory.
        if (!consumeSiegeDeclarationItem(officer)) {
            officer.sendMessage(new TextComponentString("You need a siege camp block to declare a siege"));
            return;
        }

        DimBlockPos anchorPos = new DimBlockPos(fromChunk.dim, fromChunk.getXStart(), 0, fromChunk.getZStart());
        Siege siege = createSiege(attacking, defending, defendingPos, targetChunk, anchorPos, true);
        siege.start();
        attacking.lastSiegeTimestamp = currentTimeStamp;
    }

    // Removes one siege camp block item from the player's main inventory. Returns false if they have
    // none. If the item registry is somehow unavailable, fails open (does not block the declaration).
    private boolean consumeSiegeDeclarationItem(EntityPlayerMP player) {
        if (Content.siegeCampBlockItem == null) return true;
        for (int i = 0; i < player.inventory.mainInventory.size(); i++) {
            ItemStack stack = player.inventory.mainInventory.get(i);
            if (!stack.isEmpty() && stack.getItem() == Content.siegeCampBlockItem) {
                stack.shrink(1);
                if (stack.isEmpty()) player.inventory.mainInventory.set(i, ItemStack.EMPTY);
                player.inventoryContainer.detectAndSendChanges();
                return true;
            }
        }
        return false;
    }

    public void endSiege(DimBlockPos getPos) {
        Siege siege = sieges.get(getPos.toChunkPos());
        if (siege != null) {
            siege.onCancelled();
            sieges.remove(getPos.toChunkPos());
        }
    }

    public void requestZoneClaim(EntityPlayer op, DimChunkPos pos, UUID zoneID) {
        Faction zone = getFaction(zoneID);
        if (zone == null || !IsNeutralZone(zoneID)) {
            op.sendMessage(new TextComponentString("Unknown zone"));
            return;
        }

        UUID existingClaim = getClaim(pos);
        if (!existingClaim.equals(Faction.nullUuid)) {
            op.sendMessage(new TextComponentString("There is already a claim here"));
            return;
        }

        Faction.ClaimType claimType = zoneID.equals(SAFE_ZONE_ID) ? Faction.ClaimType.ADMIN : Faction.ClaimType.WARZONE;
        mClaims.put(pos, zone.uuid);
        zone.claimNoTileEntity(pos, op.getPosition().getY(), claimType);

        op.sendMessage(new TextComponentString("Claimed [" + pos.x + ", " + pos.z + "] for " + zone.name));
    }

    public void requestZoneUnclaim(EntityPlayer op, DimChunkPos pos) {
        UUID existingClaim = getClaim(pos);
        if (!IsNeutralZone(existingClaim)) {
            op.sendMessage(new TextComponentString("There is no zone claim here"));
            return;
        }

        Faction zone = getFaction(existingClaim);
        DimBlockPos claimPos = zone == null ? null : zone.getSpecificPosForClaim(pos);
        if (claimPos != null) {
            zone.claims.remove(claimPos);
            zone.claimTypes.remove(claimPos);
        }
        mClaims.remove(pos);

        op.sendMessage(new TextComponentString("Removed the " + (zone == null ? "zone" : zone.name)
                + " claim at [" + pos.x + ", " + pos.z + "]"));
    }

    public void sendSiegeInfoToNearby(DimChunkPos siegePos) {
        Siege siege = sieges.get(siegePos);
        if (siege != null) {
            SiegeCampProgressInfo info = siege.GetSiegeInfo();
            if (info != null) {
                PacketSiegeCampProgressUpdate packet = new PacketSiegeCampProgressUpdate();
                packet.info = info;
                NETWORK.sendToAllAround(packet, siegePos.x * 16, 128d, siegePos.z * 16, WarForgeConfig.SIEGE_INFO_RADIUS + 128f, siegePos.dim);
            }
        }
    }

    public void sendAllSiegeInfoToNearby() {
        for (HashMap.Entry<DimChunkPos, Siege> kvp : FACTIONS.sieges.entrySet()) {
            kvp.getValue().calculateBasePower();

            sendSiegeInfoToNearby(kvp.getKey());
        }

    }

    // Returns arraylist with nulls for invalid claim directions, in horizontal + diagonal ordering
    public int getAdjacentClaims(UUID excludingFaction, DimBlockPos pos, ArrayList<DimChunkPos> positions) {
        // Ensure list has 8 entries: [N, S, W, E, NW, NE, SW, SE]
        if (positions.size() < 8)
            positions = new ArrayList<>(Arrays.asList(new DimChunkPos[8]));
        int numValidTargets = 0;

        // Cardinal directions (indices 0–3)
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            DimChunkPos targetChunkPos = pos.toChunkPos().Offset(facing, 1);
            if (!isClaimed(excludingFaction, targetChunkPos)) continue;
            UUID targetID = getClaim(targetChunkPos);
            int targetY = getFaction(targetID).getSpecificPosForClaim(targetChunkPos).getY();
            if (Math.abs(pos.getY() - targetY) > WarForgeConfig.VERTICAL_SIEGE_DIST) continue;

            positions.set(facing.getHorizontalIndex(), targetChunkPos);
            ++numValidTargets;
        }

        // Diagonal directions (indices 4–7): NW, NE, SW, SE
        EnumFacing[][] diagonalPairs = {
                {EnumFacing.NORTH, EnumFacing.WEST},  // NW
                {EnumFacing.NORTH, EnumFacing.EAST},  // NE
                {EnumFacing.SOUTH, EnumFacing.WEST},  // SW
                {EnumFacing.SOUTH, EnumFacing.EAST}   // SE
        };

        for (int i = 0; i < diagonalPairs.length; i++) {
            EnumFacing f1 = diagonalPairs[i][0];
            EnumFacing f2 = diagonalPairs[i][1];
            DimChunkPos diagonalPos = pos.toChunkPos().Offset(f1, 1).Offset(f2, 1);
            if (!isClaimed(excludingFaction, diagonalPos)) continue;
            UUID targetID = getClaim(diagonalPos);
            int targetY = getFaction(targetID).getSpecificPosForClaim(diagonalPos).getY();
            if (Math.abs(pos.getY() - targetY) > WarForgeConfig.VERTICAL_SIEGE_DIST) continue;

            positions.set(4 + i, diagonalPos);
            ++numValidTargets;
        }

        return numValidTargets;
    }


    public LinkedHashMap<DimChunkPos, Boolean> getClaimRadiusAround(UUID excludedFaction, DimBlockPos originPos, int radius) {
        LinkedHashMap<DimChunkPos, Boolean> posMap = new LinkedHashMap<>((int) Math.pow(radius * 2, 2) + 1, 0.75f, true);
        DimChunkPos centerChunk = originPos.toChunkPos();
        int centerX = centerChunk.x;
        int centerZ = centerChunk.z;
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                boolean canSiege = true;
                DimChunkPos targetChunkPos = new DimChunkPos(centerChunk.dim, x, z);
                if (!isClaimed(excludedFaction, targetChunkPos)) canSiege = false; // also screens for dimension
                UUID chunkClaim = getClaim(targetChunkPos);
                if (chunkClaim.equals(excludedFaction) || chunkClaim.equals(Faction.nullUuid)) canSiege = false;
                else {
                    int targetY = getFaction(chunkClaim).getSpecificPosForClaim(targetChunkPos).getY();
                    if (originPos.getY() < targetY - WarForgeConfig.VERTICAL_SIEGE_DIST || originPos.getY() > targetY + WarForgeConfig.VERTICAL_SIEGE_DIST)
                        canSiege = false;
                }
                posMap.put(targetChunkPos, canSiege);

            }
        }
        return posMap;
    }

    public boolean isClaimed(UUID excludingFaction, DimChunkPos pos) {
        UUID factionID = getClaim(pos);
        return factionID != null && !factionID.equals(excludingFaction) && !factionID.equals(Faction.nullUuid);
    }

    public Set<DimChunkPos> collectFactionIsland(UUID factionID, DimChunkPos start) {
        HashSet<DimChunkPos> visited = new HashSet<DimChunkPos>();
        if (!factionID.equals(getClaim(start))) {
            return visited;
        }

        ArrayDeque<DimChunkPos> queue = new ArrayDeque<DimChunkPos>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            DimChunkPos current = queue.poll();
            for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                DimChunkPos neighbor = current.Offset(facing, 1);
                if (!visited.contains(neighbor) && factionID.equals(getClaim(neighbor))) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return visited;
    }

    public boolean registerCollector(EntityPlayerMP player, DimBlockPos collectorPos) {
        Faction faction = getFactionOfPlayer(player.getUniqueID());
        if (faction == null) {
            player.sendMessage(new TextComponentString("You are not in a faction"));
            return false;
        }
        if (!isOp(player) && !faction.isPlayerRoleInFaction(player.getUniqueID(), Role.OFFICER)) {
            player.sendMessage(new TextComponentString("You are not an officer of your faction"));
            return false;
        }

        DimChunkPos collectorChunk = collectorPos.toChunkPos();
        if (!faction.uuid.equals(getClaim(collectorChunk))) {
            player.sendMessage(new TextComponentString("Collector can only be placed in your faction land"));
            return false;
        }

        Set<DimChunkPos> island = collectFactionIsland(faction.uuid, collectorChunk);
        ArrayList<DimBlockPos> staleCollectors = new ArrayList<DimBlockPos>();
        for (DimBlockPos existingCollector : faction.islandCollectors) {
            World collectorWorld = MC_SERVER.getWorld(existingCollector.dim);
            if (collectorWorld == null || !(collectorWorld.getTileEntity(existingCollector.toRegularPos()) instanceof TileEntityIslandCollector)) {
                staleCollectors.add(existingCollector);
                continue;
            }
            if (island.contains(existingCollector.toChunkPos())) {
                player.sendMessage(new TextComponentString("There is already a collector on this faction island"));
                return false;
            }
        }
        if (!staleCollectors.isEmpty()) {
            faction.islandCollectors.removeAll(staleCollectors);
            WarForgeMod.CHUNK_LOADING_MANAGER.refreshFactionChunks(faction);
        }

        TileEntity te = player.world.getTileEntity(collectorPos.toRegularPos());
        if (!(te instanceof TileEntityIslandCollector collector)) {
            return false;
        }

        collector.onServerSetFaction(faction);
        faction.islandCollectors.add(collectorPos);
        WarForgeMod.CHUNK_LOADING_MANAGER.refreshFactionChunks(faction);
        return true;
    }

    public void unregisterCollector(DimBlockPos collectorPos) {
        for (Faction faction : mFactions.values()) {
            if (faction.islandCollectors.remove(collectorPos)) {
                WarForgeMod.CHUNK_LOADING_MANAGER.refreshFactionChunks(faction);
                return;
            }
        }
    }

    public boolean requestToggleForceLoad(EntityPlayerMP player, DimChunkPos chunkPos) {
        Faction faction = getFactionOfPlayer(player.getUniqueID());
        if (faction == null) {
            player.sendMessage(new TextComponentString("You are not in a faction"));
            return false;
        }
        if (!isOp(player) && !faction.isPlayerRoleInFaction(player.getUniqueID(), Role.OFFICER)) {
            player.sendMessage(new TextComponentString("You are not an officer of your faction"));
            return false;
        }
        if (!faction.uuid.equals(getClaim(chunkPos))) {
            player.sendMessage(new TextComponentString("You can only force-load your faction chunks"));
            return false;
        }

        if (faction.forcedChunks.contains(chunkPos)) {
            faction.forcedChunks.remove(chunkPos);
            player.sendMessage(new TextComponentString("Removed force-load from chunk [" + chunkPos.x + ", " + chunkPos.z + "]"));
        } else {
            if (!faction.canForceLoadMore()) {
                player.sendMessage(new TextComponentString("Force-load chunk limit reached (" + faction.getMaxForceLoadedChunks() + ")"));
                return false;
            }
            faction.forcedChunks.add(chunkPos);
            player.sendMessage(new TextComponentString("Force-loaded chunk [" + chunkPos.x + ", " + chunkPos.z + "]"));
        }

        WarForgeMod.CHUNK_LOADING_MANAGER.refreshFactionChunks(faction);
        return true;
    }

    public PacketClaimChunksData createClaimChunksData(EntityPlayerMP player, DimChunkPos center, int radius) {
        return createClaimChunksData(player, center, radius, false);
    }

    public PacketClaimChunksData createClaimChunksData(EntityPlayerMP player, DimChunkPos center, int radius, boolean outlineOnly) {
        int maxRadius = outlineOnly ? WarForgeConfig.BORDER_SYNC_MAX_RADIUS : WarForgeConfig.CLAIM_MANAGER_RADIUS;
        int clampedRadius = Math.max(1, Math.min(radius, maxRadius));
        if (center.dim != player.dimension) {
            center = new DimChunkPos(player.dimension, player.getPosition());
        }

        Faction faction = getFactionOfPlayer(player.getUniqueID());
        boolean canManage = faction != null && (isOp(player) || faction.isPlayerRoleInFaction(player.getUniqueID(), Role.OFFICER));

        PacketClaimChunksData packet = new PacketClaimChunksData();
        packet.dim = center.dim;
        packet.centerX = center.x;
        packet.centerZ = center.z;
        packet.radius = clampedRadius;
        packet.playerFactionId = faction == null ? Faction.nullUuid : faction.uuid;
        packet.forceLoadedCount = faction == null ? 0 : faction.forcedChunks.size();
        packet.forceLoadedMax = faction == null ? 0 : faction.getMaxForceLoadedChunks();
        packet.claimCount = faction == null ? 0 : faction.claims.size();
        packet.claimMax = faction == null ? 0 : WarForgeMod.UPGRADE_HANDLER.getClaimLimitForLevel(faction.citadelLevel);
        if (packet.claimMax < 0) {
            packet.claimMax = Short.MAX_VALUE;
        }

        for (int x = center.x - clampedRadius; x <= center.x + clampedRadius; x++) {
            for (int z = center.z - clampedRadius; z <= center.z + clampedRadius; z++) {
                DimChunkPos chunk = new DimChunkPos(center.dim, x, z);
                UUID ownerId = getClaim(chunk);
                Faction ownerFaction = getFaction(ownerId);
                if (ownerFaction != null && ownerFaction.getSpecificPosForClaim(chunk) == null) {
                    mClaims.remove(chunk);
                    ownerFaction = null;
                    ownerId = Faction.nullUuid;
                }

                ClaimChunkInfo info = new ClaimChunkInfo();
                info.x = x;
                info.z = z;
                if (ownerFaction != null) {
                    info.factionId = ownerFaction.uuid;
                    info.factionName = ownerFaction.name;
                    info.flagId = ownerFaction.flagId;
                    info.colour = ownerFaction.colour;
                    info.claimType = ownerFaction.getClaimType(chunk);
                }

                ObjectIntPair<UUID> conqueredInfo = conqueredChunks.get(chunk);
                if (conqueredInfo != null) {
                    Faction conqueredFaction = getFaction(conqueredInfo.getObj());
                    if (conqueredFaction != null) {
                        info.outlineFactionId = conqueredFaction.uuid;
                        info.outlineColour = conqueredFaction.colour;
                        info.outlineStyle = ClaimChunkInfo.OUTLINE_CONQUERED;
                    }
                    if (ownerFaction == null) {
                        info.conqueredRemainingMs = conqueredInfo.getInteger();
                    }
                }
                if (!info.hasVisibleOutline() && ownerFaction != null) {
                    info.outlineFactionId = ownerFaction.uuid;
                    info.outlineColour = ownerFaction.colour;
                    info.outlineStyle = ClaimChunkInfo.OUTLINE_CLAIM;
                }

                if (!outlineOnly) {
                    var veinInfo = VEIN_HANDLER.getVein(chunk.dim, chunk.x, chunk.z, MC_SERVER.worlds[0].getSeed());
                    if (veinInfo != null) {
                        info.vein = veinInfo.getLeft();
                        info.oreQuality = veinInfo.getRight();
                    }
                }

                if (!outlineOnly && faction != null && ownerFaction != null && ownerFaction.uuid.equals(faction.uuid)) {
                    info.flags |= ClaimChunkInfo.FLAG_OWNED_BY_PLAYER;
                    if (faction.forcedChunks.contains(chunk)) {
                        info.flags |= ClaimChunkInfo.FLAG_FORCE_LOADED;
                    }
                    for (DimBlockPos collectorPos : faction.islandCollectors) {
                        if (collectorPos.toChunkPos().equals(chunk)) {
                            info.flags |= ClaimChunkInfo.FLAG_HAS_COLLECTOR;
                            break;
                        }
                    }
                }

                if (!outlineOnly && canManage) {
                    if (ownerFaction == null) {
                        if (canClaimChunkNoTile(player, faction, chunk, false)) {
                            info.flags |= ClaimChunkInfo.FLAG_CAN_CLAIM;
                        }
                    } else if (ownerFaction.uuid.equals(faction.uuid)) {
                        DimBlockPos claimPos = faction.getSpecificPosForClaim(chunk);
                        if (claimPos != null && !claimPos.equals(faction.citadelPos) && !IsSiegeInProgress(chunk)) {
                            info.flags |= ClaimChunkInfo.FLAG_CAN_UNCLAIM;
                        }
                        info.flags |= ClaimChunkInfo.FLAG_CAN_TOGGLE_FORCELOAD;
                    }
                }

                if (!outlineOnly || info.hasVisibleOutline()) {
                    packet.chunks.add(info);
                }
            }
        }

        packet.outlineOnly = outlineOnly;
        return packet;
    }

    public void sendClaimChunks(EntityPlayerMP player, DimChunkPos center, int radius) {
        sendClaimChunks(player, center, radius, false);
    }

    public void sendClaimChunks(EntityPlayerMP player, DimChunkPos center, int radius, boolean outlineOnly) {
        PacketClaimChunksData packet = createClaimChunksData(player, center, radius, outlineOnly);
        WarForgeMod.NETWORK.sendTo(packet, player);
    }

    // Samples per-block vanilla map colours + heights for a chunk region and ships them to the
    // requesting client so the claim map can render real terrain for chunks the client hasn't loaded
    // (e.g. a distant siege target). Only chunks currently loaded server-side are sampled; the rest
    // fall back to the client's faction-tint / placeholder rendering. Sampling is intentionally
    // limited (no force-loading) to avoid letting a client trigger chunk generation on demand.
    public void sendTerrainColors(EntityPlayerMP player, DimChunkPos center, int radius) {
        if (center == null) return;
        radius = Math.max(1, Math.min(radius, WarForgeConfig.CLAIM_MANAGER_RADIUS));
        // Match the claim-data constraint: only the player's current dimension.
        if (center.dim != player.dimension) {
            center = new DimChunkPos(player.dimension, player.getPosition());
        }
        World world = MC_SERVER.getWorld(center.dim);
        if (world == null) return;

        PacketTerrainColors packet = new PacketTerrainColors();
        packet.dim = center.dim;
        packet.centerX = center.x;
        packet.centerZ = center.z;
        packet.radius = radius;

        for (int cx = center.x - radius; cx <= center.x + radius; cx++) {
            for (int cz = center.z - radius; cz <= center.z + radius; cz++) {
                net.minecraft.world.chunk.Chunk chunk = world.getChunkProvider().getLoadedChunk(cx, cz);
                if (chunk == null) {
                    continue; // not loaded server-side; client keeps its placeholder for this chunk
                }
                int[] colors = new int[256];
                int[] heights = new int[256];
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int idx = lx + lz * 16;
                        int y = chunk.getHeightValue(lx, lz);
                        heights[idx] = y;
                        if (y <= 0) {
                            colors[idx] = 0;
                            continue;
                        }
                        BlockPos worldPos = new BlockPos((cx << 4) + lx, y - 1, (cz << 4) + lz);
                        try {
                            colors[idx] = chunk.getBlockState(lx, y - 1, lz).getMapColor(world, worldPos).colorValue & 0x00FFFFFF;
                        } catch (Throwable ignored) {
                            colors[idx] = 0;
                        }
                    }
                }
                packet.addChunk(cx, cz, colors, heights);
            }
        }

        WarForgeMod.NETWORK.sendTo(packet, player);
    }

    private ClaimedBlockSelection findFirstClaimBlock(InventoryPlayer inventory) {
        for (int slot = 0; slot < inventory.mainInventory.size(); slot++) {
            ItemStack stack = inventory.mainInventory.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() == Content.basicClaimBlockItem) {
                return new ClaimedBlockSelection(slot, Faction.ClaimType.BASIC);
            }
            if (stack.getItem() == Content.reinforcedClaimBlockItem) {
                return new ClaimedBlockSelection(slot, Faction.ClaimType.REINFORCED);
            }
        }
        return null;
    }

    private ItemStack claimTypeToStack(Faction.ClaimType claimType) {
        return switch (claimType) {
            case BASIC -> new ItemStack(Content.basicClaimBlockItem);
            case REINFORCED -> new ItemStack(Content.reinforcedClaimBlockItem);
            default -> ItemStack.EMPTY;
        };
    }

    public boolean requestRemoveClaim(EntityPlayerMP player, DimBlockPos pos) {
        DimChunkPos targetChunk = pos.toChunkPos();
        UUID factionID = getClaim(targetChunk);
        Faction faction = getFaction(factionID);
        if (factionID.equals(Faction.nullUuid) || faction == null) {
            player.sendMessage(new TextComponentString("Could not find a claim in that location"));
            return false;
        }

        DimBlockPos claimPos = faction.getSpecificPosForClaim(targetChunk);
        if (claimPos != null) {
            pos = claimPos;
        }

        if (pos.equals(faction.citadelPos)) {
            player.sendMessage(new TextComponentString("Can't remove the citadel without disbanding the faction"));
            return false;
        }

        if (!isOp(player) && !faction.isPlayerRoleInFaction(player.getUniqueID(), Role.OFFICER)) {
            player.sendMessage(new TextComponentString("You are not an officer of the faction"));
            return false;
        }

        for (HashMap.Entry<DimChunkPos, Siege> siege : sieges.entrySet()) {
            if (siege.getKey().equals(targetChunk)) {
                player.sendMessage(new TextComponentString("This claim is currently under siege"));
                return false;
            }

            if (siege.getValue().attackingCamps.contains(pos)) {
                player.sendMessage(new TextComponentString("This siege camp is currently in a siege"));
                return false;
            }
        }

        Faction.ClaimType claimType = faction.getClaimType(targetChunk);
        World claimWorld = MC_SERVER.getWorld(pos.dim);
        boolean dataOnlyClaim = claimWorld != null && !WarForgeMod.isClaim(
                claimWorld.getBlockState(pos.toRegularPos()).getBlock(),
                Content.statue,
                Content.dummyTranslusent
        );

        faction.onClaimLost(pos, false, false);
        mClaims.remove(targetChunk);
        faction.forcedChunks.remove(targetChunk);
        ArrayList<DimBlockPos> removedCollectors = new ArrayList<DimBlockPos>();
        for (DimBlockPos collectorPos : faction.islandCollectors) {
            if (collectorPos.toChunkPos().equals(targetChunk)) {
                removedCollectors.add(collectorPos);
                World world = MC_SERVER.getWorld(collectorPos.dim);
                if (world != null) {
                    world.setBlockToAir(collectorPos.toRegularPos());
                }
            }
        }
        faction.islandCollectors.removeAll(removedCollectors);
        WarForgeMod.CHUNK_LOADING_MANAGER.refreshFactionChunks(faction);
        if (dataOnlyClaim) {
            ItemStack refund = claimTypeToStack(claimType);
            if (!refund.isEmpty()) {
                boolean inserted = player.inventory.addItemStackToInventory(refund);
                if (!inserted && !refund.isEmpty()) {
                    player.dropItem(refund, false);
                } else {
                    player.inventoryContainer.detectAndSendChanges();
                }
            }
        }
        faction.messageAll(new TextComponentString(player.getName() + " unclaimed " + pos.toFancyString()));
        sendClaimChangedNotification(
                faction,
                "claim_removed_" + pos,
                "Chunk Unclaimed",
                player.getName() + " unclaimed " + pos.toFancyString(),
                TOAST_WARNING,
                player.getUniqueID()
        );

        return true;
    }

    public boolean requestRemoveClaimByChunk(EntityPlayerMP player, DimChunkPos chunkPos) {
        Faction faction = getFaction(getClaim(chunkPos));
        if (faction == null) {
            player.sendMessage(new TextComponentString("Could not find a claim in that location"));
            return false;
        }

        DimBlockPos claimPos = faction.getSpecificPosForClaim(chunkPos);
        if (claimPos == null) {
            player.sendMessage(new TextComponentString("Could not find a claim in that location"));
            return false;
        }

        return requestRemoveClaim(player, claimPos);
    }

    public boolean requestRemoveClaimServer(DimBlockPos pos) {
        DimChunkPos targetChunk = pos.toChunkPos();
        UUID factionID = getClaim(targetChunk);
        Faction faction = getFaction(factionID);
        if (factionID.equals(Faction.nullUuid) || faction == null) {
            return false;
        }

        DimBlockPos claimPos = faction.getSpecificPosForClaim(targetChunk);
        if (claimPos != null) {
            pos = claimPos;
        }

        if (pos.equals(faction.citadelPos)) {
            return false;
        }

        for (HashMap.Entry<DimChunkPos, Siege> siege : sieges.entrySet()) {
            if (siege.getKey().equals(targetChunk)) {
                return false;
            }

            if (siege.getValue().attackingCamps.contains(pos)) {
                return false;
            }
        }

        faction.onClaimLost(pos, false, false);
        mClaims.remove(targetChunk);
        faction.forcedChunks.remove(targetChunk);
        ArrayList<DimBlockPos> removedCollectors = new ArrayList<DimBlockPos>();
        for (DimBlockPos collectorPos : faction.islandCollectors) {
            if (collectorPos.toChunkPos().equals(targetChunk)) {
                removedCollectors.add(collectorPos);
                World world = MC_SERVER.getWorld(collectorPos.dim);
                if (world != null) {
                    world.setBlockToAir(collectorPos.toRegularPos());
                }
            }
        }
        faction.islandCollectors.removeAll(removedCollectors);
        WarForgeMod.CHUNK_LOADING_MANAGER.refreshFactionChunks(faction);
        sendClaimChangedNotification(
                faction,
                "claim_removed_" + pos,
                "Chunk Unclaimed",
                "A claim at " + pos.toFancyString() + " was removed",
                TOAST_WARNING
        );
        return true;
    }

    public boolean requestSetFactionColour(EntityPlayerMP player, int colour) {
        Faction faction = getFactionOfPlayer(player.getUniqueID());
        if (faction == null) {
            player.sendMessage(new TextComponentString("You are not in a faction"));
            return false;
        }

        if (!faction.isPlayerRoleInFaction(player.getUniqueID(), Role.LEADER)) {
            player.sendMessage(new TextComponentString("You are not the faction leader"));
            return false;
        }

        faction.setColour(colour);
        for (World world : MC_SERVER.worlds) {
            for (TileEntity te : world.loadedTileEntityList) {
                if (te instanceof IClaim) {
                    if (((IClaim) te).getFaction().equals(faction.uuid)) {
                        ((IClaim) te).updateColour(colour);
                    }
                }
            }
        }

        if (faction.citadelPos != null) {
            WarForgeMod.syncClaimToPlayer(player, faction.citadelPos.toRegularPos());
        }

        ArrayList<EntityPlayer> onlinePlayers = faction.getOnlinePlayers(
                entityPlayer -> entityPlayer != null && entityPlayer.isEntityAlive());

        onlinePlayers.forEach(entityPlayer -> {
            PacketNamePlateChange packet = new PacketNamePlateChange();
            packet.name = entityPlayer.getName();
            packet.faction = faction.name;
            packet.color = colour;
            NETWORK.sendToAllAround(packet, entityPlayer.posX, entityPlayer.posY, entityPlayer.posZ, 100, player.dimension);
        });


        return true;

    }

    public boolean requestMoveCitadel(EntityPlayerMP player, DimBlockPos pos) {
        Faction faction = getFactionOfPlayer(player.getUniqueID());
        if (faction == null) {
            player.sendMessage(new TextComponentString("You are not in a faction"));
            return false;
        }

        if (!faction.isPlayerRoleInFaction(player.getUniqueID(), Role.LEADER)) {
            player.sendMessage(new TextComponentString("You are not the faction leader"));
            return false;
        }

        for (HashMap.Entry<DimChunkPos, Siege> kvp : sieges.entrySet()) {
            if (kvp.getValue().defendingFaction.equals(faction.uuid)) {
                player.sendMessage(new TextComponentString("There is an ongoing siege against your faction"));
                return false;
            }
        }
        if (pos == null || pos.equals(DimBlockPos.ZERO)) {
            player.sendMessage(new TextComponentString("Look at the block face where you want to place the citadel"));
            return false;
        }
        if (pos.dim != player.dimension) {
            player.sendMessage(new TextComponentString("You can only move the citadel in your current dimension"));
            return false;
        }
        if (pos.equals(faction.citadelPos)) {
            player.sendMessage(new TextComponentString("The citadel is already there"));
            return false;
        }

        DimChunkPos targetChunk = pos.toChunkPos();
        UUID chunkOwner = getClaim(targetChunk);
        if (!faction.uuid.equals(chunkOwner)) {
            player.sendMessage(new TextComponentString("You must look at a position inside your claimed land"));
            return false;
        }

        if (!canPlaceMovedCitadelAt(player.world, pos, faction.citadelPos)) {
            player.sendMessage(new TextComponentString("Not enough free space to place the 3-block citadel pillar there"));
            return false;
        }

        DimChunkPos oldChunk = faction.citadelPos.toChunkPos();
        boolean movingAcrossChunks = !targetChunk.equals(oldChunk);
        if (movingAcrossChunks && faction.citadelMoveTimeStamp > 0L && faction.citadelMoveCooldown > 0) {
            player.sendMessage(new TextComponentString("You can move the citadel across chunks again in " + faction.citadelMoveCooldown + " day(s)"));
            return false;
        }

        DimBlockPos replacedClaimPos = targetChunk.equals(oldChunk) ? faction.citadelPos : faction.getSpecificPosForClaim(targetChunk);
        if (replacedClaimPos == null) {
            player.sendMessage(new TextComponentString("That claimed chunk has no stored claim position to replace"));
            return false;
        }

        TileEntityCitadel oldCitadel = (TileEntityCitadel) MC_SERVER.getWorld(faction.citadelPos.dim).getTileEntity(faction.citadelPos.toRegularPos());
        if (oldCitadel == null) {
            player.sendMessage(new TextComponentString("Could not find the current citadel tile entity"));
            return false;
        }

        Integer oldPending = faction.claims.remove(faction.citadelPos);
        if (oldPending == null) {
            oldPending = 0;
        }
        Integer targetPending = replacedClaimPos.equals(faction.citadelPos) ? oldPending : faction.claims.remove(replacedClaimPos);
        Faction.ClaimType targetClaimType = faction.claimTypes.getOrDefault(replacedClaimPos, Faction.ClaimType.BASIC);
        if (targetPending == null) {
            targetPending = 0;
        }
        if (!replacedClaimPos.equals(faction.citadelPos)) {
            faction.claimTypes.remove(replacedClaimPos);
        }

        // Set new citadel
        MC_SERVER.getWorld(pos.dim).setBlockState(pos.toRegularPos(), Content.citadelBlock.getDefaultState());
        TileEntityCitadel newCitadel = (TileEntityCitadel) MC_SERVER.getWorld(pos.dim).getTileEntity(pos.toRegularPos());
        if (newCitadel == null) {
            player.sendMessage(new TextComponentString("Failed to create the new citadel"));
            return false;
        }
        newCitadel.copyStorageFrom(oldCitadel);

        // Old citadel remains a normal claimed chunk but no longer has a physical claim block.
        DimBlockPos oldCitadelPos = faction.citadelPos;
        World oldCitadelWorld = MC_SERVER.getWorld(faction.citadelPos.dim);
        oldCitadelWorld.setBlockToAir(faction.citadelPos.toRegularPos());
        oldCitadelWorld.setBlockToAir(faction.citadelPos.toRegularPos().up());
        oldCitadelWorld.setBlockToAir(faction.citadelPos.toRegularPos().up(2));
        if (!targetChunk.equals(oldChunk)) {
            faction.claims.put(oldCitadelPos, oldPending);
            faction.claimTypes.put(oldCitadelPos, targetClaimType);
        }

        // Update pos
        faction.citadelPos = pos;
        faction.claims.put(pos, targetPending);
        faction.claimTypes.put(pos, Faction.ClaimType.CITADEL);
        newCitadel.onServerSetFaction(faction);
        if (movingAcrossChunks) {
            faction.citadelMoveCooldown = WarForgeConfig.CITADEL_MOVE_NUM_DAYS;
            faction.citadelMoveTimeStamp = System.currentTimeMillis();
        }

        INSTANCE.messageAll(new TextComponentString(faction.name + " moved their citadel"), true);
        return true;
    }

    private boolean canPlaceMovedCitadelAt(World world, DimBlockPos pos, DimBlockPos oldCitadelPos) {
        if (!world.getBlockState(pos.down()).isSideSolid(world, pos.down(), EnumFacing.UP)) {
            return false;
        }
        return isCitadelSpaceFree(world, pos, oldCitadelPos)
                && isCitadelSpaceFree(world, pos.up(), oldCitadelPos)
                && isCitadelSpaceFree(world, pos.up(2), oldCitadelPos);
    }

    private boolean isCitadelSpaceFree(World world, BlockPos pos, DimBlockPos oldCitadelPos) {
        if (oldCitadelPos != null) {
            if (pos.equals(oldCitadelPos.toRegularPos()) || pos.equals(oldCitadelPos.toRegularPos().up()) || pos.equals(oldCitadelPos.toRegularPos().up(2))) {
                return true;
            }
        }
        return world.isAirBlock(pos) || world.getBlockState(pos).getBlock().isReplaceable(world, pos);
    }

    public void readFromNBT(NBTTagCompound tags) {
        mFactions.clear();
        mClaims.clear();
        sieges.clear();
        redeemableInsuranceVaults.clear();

        InitNeutralZones();

        NBTTagList list = tags.getTagList("factions", 10); // Compound Tag
        for (NBTBase baseTag : list) {
            NBTTagCompound factionTags = ((NBTTagCompound) baseTag);
            if (!factionTags.hasKey("idMost", 4) || !factionTags.hasKey("idLeast", 4)) {
                WarForgeMod.LOGGER.warn("Skipping faction entry with missing/invalid id while loading warforgefactions.dat");
                continue;
            }
            UUID uuid = factionTags.getUniqueId("id");
            Faction faction;


            assert uuid != null;
            if (uuid.equals(SAFE_ZONE_ID)) {
                faction = SAFE_ZONE;
            } else if (uuid.equals(WAR_ZONE_ID)) {
                faction = WAR_ZONE;
            } else {
                faction = new Faction();
                faction.uuid = uuid;
                mFactions.put(uuid, faction);
                LEADERBOARD.RegisterFaction(faction);
            }

            faction.readFromNBT(factionTags);

            // Also populate the DimChunkPos lookup table
            for (DimBlockPos blockPos : faction.claims.keySet()) {
                mClaims.put(blockPos.toChunkPos(), uuid);
            }
        }

        list = tags.getTagList("sieges", 10); // Compound Tag
        for (NBTBase baseTag : list) {
            NBTTagCompound siegeTags = ((NBTTagCompound) baseTag);
            int dim = siegeTags.getInteger("dim");
            int x = siegeTags.getInteger("x");
            int z = siegeTags.getInteger("z");

            Siege siege = new Siege();
            siege.ReadFromNBT(siegeTags);

            sieges.put(new DimChunkPos(dim, x, z), siege);
        }

        readConqueredChunks(tags);
        readRedeemableInsurance(tags);
    }

    private void readConqueredChunks(NBTTagCompound tags) {
        conqueredChunks = new HashMap<>();
        NBTTagCompound conqueredChunksDataList = tags.getCompoundTag("conqueredChunks");

        // 11 is type id for int array
        int index = 0;
        while (true) {
            NBTTagList keyValPair = conqueredChunksDataList.getTagList("conqueredChunk_" + index, 11);
            if (keyValPair.isEmpty())
                break; // exit once invalid (empty, since getTagList never returns null) is found, as it is assumed this is the first non-existent/ invalid index
            int[] dimInfo = keyValPair.getIntArrayAt(0);
            DimChunkPos chunkPosKey = new DimChunkPos(dimInfo[0], dimInfo[1], dimInfo[2]);
            UUID factionID = BEIntArrayToUUID(keyValPair.getIntArrayAt(1));

            conqueredChunks.put(chunkPosKey, new ObjectIntPair<>(factionID, keyValPair.getIntArrayAt(2)[0]));
            ++index;
        }
    }

    public void WriteToNBT(NBTTagCompound tags) {
        NBTTagList factionList = new NBTTagList();
        for (HashMap.Entry<UUID, Faction> kvp : mFactions.entrySet()) {
            NBTTagCompound factionTags = new NBTTagCompound();
            factionTags.setUniqueId("id", kvp.getKey());
            kvp.getValue().writeToNBT(factionTags);
            factionList.appendTag(factionTags);
        }

        tags.setTag("factions", factionList);

        NBTTagList siegeList = new NBTTagList();
        for (HashMap.Entry<DimChunkPos, Siege> kvp : sieges.entrySet()) {
            NBTTagCompound siegeTags = new NBTTagCompound();
            siegeTags.setInteger("dim", kvp.getKey().dim);
            siegeTags.setInteger("x", kvp.getKey().x);
            siegeTags.setInteger("z", kvp.getKey().z);
            kvp.getValue().WriteToNBT(siegeTags);
            siegeList.appendTag(siegeTags);
        }

        tags.setTag("sieges", siegeList);

        writeConqueredChunks(tags);
        writeRedeemableInsurance(tags);
    }

    private void readRedeemableInsurance(NBTTagCompound tags) {
        NBTTagList payoutList = tags.getTagList("redeemableInsuranceVaults", 10);
        for (NBTBase base : payoutList) {
            NBTTagCompound payoutTag = (NBTTagCompound) base;
            UUID owner = payoutTag.getUniqueId("owner");
            ArrayList<ItemStack> items = new ArrayList<ItemStack>();
            NBTTagList itemsList = payoutTag.getTagList("items", 10);
            for (NBTBase itemBase : itemsList) {
                items.add(new ItemStack((NBTTagCompound) itemBase));
            }
            redeemableInsuranceVaults.put(owner, items);
        }
    }

    private void writeRedeemableInsurance(NBTTagCompound tags) {
        NBTTagList payoutList = new NBTTagList();
        for (Map.Entry<UUID, ArrayList<ItemStack>> entry : redeemableInsuranceVaults.entrySet()) {
            NBTTagCompound payoutTag = new NBTTagCompound();
            payoutTag.setUniqueId("owner", entry.getKey());
            NBTTagList itemsList = new NBTTagList();
            for (ItemStack stack : entry.getValue()) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                NBTTagCompound itemTag = new NBTTagCompound();
                stack.writeToNBT(itemTag);
                itemsList.appendTag(itemTag);
            }
            payoutTag.setTag("items", itemsList);
            payoutList.appendTag(payoutTag);
        }
        tags.setTag("redeemableInsuranceVaults", payoutList);
    }

    private void writeConqueredChunks(NBTTagCompound tags) {
        NBTTagCompound conqueredChunksDataList = new NBTTagCompound();
        int index = 0;
        for (DimChunkPos chunkPosKey : conqueredChunks.keySet()) {
            // values in tag list must all be same, so all types are changed to use int arrays
            NBTTagList keyValPair = new NBTTagList();
            keyValPair.appendTag(new NBTTagIntArray(new int[]{chunkPosKey.dim, chunkPosKey.x, chunkPosKey.z}));
            keyValPair.appendTag(new NBTTagIntArray(UUIDToBEIntArray(conqueredChunks.get(chunkPosKey).getObj())));
            keyValPair.appendTag(new NBTTagIntArray(new int[]{conqueredChunks.get(chunkPosKey).getInteger()}));

            conqueredChunksDataList.setTag("conqueredChunk_" + index, keyValPair);

            ++index;
        }

        tags.setTag("conqueredChunks", conqueredChunksDataList);
    }

    public void opResetFlagCooldowns() {
        for (HashMap.Entry<UUID, Faction> kvp : mFactions.entrySet()) {
            for (HashMap.Entry<UUID, PlayerData> pDataKVP : kvp.getValue().members.entrySet()) {
                //pDataKVP.getValue().mHasMovedFlagToday = false;
                pDataKVP.getValue().moveFlagCooldown = 0;
            }
        }
    }

    public void requestNamePlateCacheEntry(EntityPlayerMP playerEntity, String name) {
        PacketNamePlateChange packet = new PacketNamePlateChange();
        packet.name = name;
        EntityPlayerMP targetPlayer = MC_SERVER.getPlayerList().getPlayerByUsername(name);
        if (targetPlayer == null) {
            return; //Likely bruteforcer TODO:Kick him
        }
        if (playerEntity.dimension != targetPlayer.dimension) {
            return; //Also sus
        }


        double dx = playerEntity.posX - targetPlayer.posX;
        double dz = playerEntity.posZ - targetPlayer.posZ;
        double dy = playerEntity.posY - targetPlayer.posY;

        int viewDistanceChunks = playerEntity.world.getMinecraftServer().getPlayerList().getViewDistance();
        int maxDistanceBlocks = viewDistanceChunks * 16;
        double maxDistanceSq = maxDistanceBlocks * maxDistanceBlocks;
        int distance = (int) (dx * dx + dy * dy + dz * dz);
        if (distance > maxDistanceSq) {
            WarForgeMod.LOGGER.warn(playerEntity.getName() + "Made a nameplate request for player " + name + "who is" + distance + "blocks away.");
            packet.isRemove = false;
            WarForgeMod.NETWORK.sendTo(packet, playerEntity);
            return;
        }
        if (getFactionOfPlayer(targetPlayer.getUniqueID()) == null)
            return;

        Faction faction = getFactionOfPlayer(targetPlayer.getUniqueID());
        if (faction != null) {
            packet.faction = faction.name;
            packet.color = faction.colour;
        }
        WarForgeMod.NETWORK.sendTo(packet, playerEntity);

    }

    public static enum siegeTermination {
        WIN, LOSE, NEUTRAL
    }

    private static class ClaimedBlockSelection {
        private final int slot;
        private final Faction.ClaimType claimType;

        private ClaimedBlockSelection(int slot, Faction.ClaimType claimType) {
            this.slot = slot;
            this.claimType = claimType;
        }

        private void consume(InventoryPlayer inventory) {
            ItemStack stack = inventory.mainInventory.get(slot);
            stack.shrink(1);
            if (stack.isEmpty()) {
                inventory.mainInventory.set(slot, ItemStack.EMPTY);
            }
        }
    }
}

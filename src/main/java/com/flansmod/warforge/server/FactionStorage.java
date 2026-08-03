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
import com.flansmod.warforge.common.util.FactionDisplay;
import com.flansmod.warforge.common.util.TimeHelper;
import com.flansmod.warforge.server.Faction.PlayerData;
import com.flansmod.warforge.server.Faction.Role;
import com.flansmod.warforge.server.fob.Fob;
import com.mojang.authlib.GameProfile;
import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

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

    public void sendNotificationToPlayer(ServerPlayer player, String token, String title, String subtitle, int accentColor, int durationMs) {
        sendNotificationToPlayer(player, token, title, subtitle, accentColor, durationMs, null);
    }

    public void sendNotificationToPlayer(ServerPlayer player, String token, String title, String subtitle, int accentColor, int durationMs, UUID actorId) {
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
        for (Player onlinePlayer : faction.getOnlinePlayers(entityPlayer -> entityPlayer != null)) {
            if (onlinePlayer instanceof ServerPlayer serverPlayer) {
                sendNotificationToPlayer(serverPlayer, token, title, subtitle, accentColor, durationMs, actorId);
            }
        }
    }

    public void sendNotificationToFaction(Faction faction, String token, String title, String subtitle, int accentColor, int durationMs, UUID actorId, Collection<UUID> exclude) {
        if (faction == null) {
            return;
        }
        for (Player onlinePlayer : faction.getOnlinePlayers(entityPlayer -> entityPlayer != null && !exclude.contains(entityPlayer.getUUID()))) {
            if (onlinePlayer instanceof ServerPlayer serverPlayer) {
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

    private void sendFactionMemberJoinedNotification(Faction faction, Player player) {
        sendNotificationToFaction(
                faction,
                "faction_member_joined_" + faction.uuid + "_" + player.getUUID(),
                "Member Joined",
                player.getName().getString() + " joined " + faction.name,
                faction.colour,
                5000,
                player.getUUID(),
                List.of(player.getUUID())
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
                Collections.singletonList(playerId)
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

    private boolean canManageAlliances(ServerPlayer player, Faction faction) {
        if (faction == null) {
            player.sendSystemMessage(Component.literal("You are not in a faction"));
            return false;
        }
        if (!WarForgeMod.isOp(player) && !faction.isPlayerRoleInFaction(player.getUUID(), Role.OFFICER)) {
            player.sendSystemMessage(Component.literal("You must be an officer to manage alliances"));
            return false;
        }
        return true;
    }

    public void requestInviteAlly(ServerPlayer player, UUID targetFactionId) {
        Faction faction = getFactionOfPlayer(player.getUUID());
        if (!canManageAlliances(player, faction)) {
            return;
        }
        Faction target = getFaction(targetFactionId);
        if (target == null || IsNeutralZone(targetFactionId)) {
            player.sendSystemMessage(Component.literal("That faction no longer exists"));
            return;
        }
        if (target.uuid.equals(faction.uuid)) {
            player.sendSystemMessage(Component.literal("You cannot ally with your own faction"));
            return;
        }
        if (faction.isAllyOf(target.uuid)) {
            player.sendSystemMessage(Component.literal("You are already allied with " + target.name));
            return;
        }
        if (WarForgeConfig.MAX_ALLIES >= 0 && faction.allies.size() >= WarForgeConfig.MAX_ALLIES) {
            player.sendSystemMessage(Component.literal("Your faction has reached the maximum number of alliances"));
            return;
        }
        // If they already requested us, treat this as an accept (mutual consent reached).
        if (faction.pendingAllianceRequests.contains(target.uuid)) {
            acceptAllianceInternal(faction, target);
            return;
        }
        if (target.pendingAllianceRequests.contains(faction.uuid)) {
            player.sendSystemMessage(Component.literal("You have already sent an alliance request to " + target.name));
            return;
        }
        target.pendingAllianceRequests.add(faction.uuid);
        faction.messageAll(Component.literal("Sent an alliance request to " + target.name + "."));
        target.messageAll(Component.literal(faction.name + " has requested an alliance. Open Faction Members > Alliances to respond."));
        sendNotificationToFaction(faction, "alliance_request_sent_" + target.uuid, "Alliance Requested", "Request sent to " + target.name, TOAST_INFO, 5000);
        sendNotificationToFaction(target, "alliance_request_" + faction.uuid, "Alliance Request", faction.name + " wants to ally", faction.colour, 8000);
    }

    public void requestAcceptAlliance(ServerPlayer player, UUID requesterFactionId) {
        Faction faction = getFactionOfPlayer(player.getUUID());
        if (!canManageAlliances(player, faction)) {
            return;
        }
        if (!faction.pendingAllianceRequests.contains(requesterFactionId)) {
            player.sendSystemMessage(Component.literal("There is no pending alliance request from that faction"));
            return;
        }
        Faction requester = getFaction(requesterFactionId);
        faction.pendingAllianceRequests.remove(requesterFactionId);
        if (requester == null) {
            player.sendSystemMessage(Component.literal("That faction no longer exists"));
            return;
        }
        if (WarForgeConfig.MAX_ALLIES >= 0 && faction.allies.size() >= WarForgeConfig.MAX_ALLIES) {
            player.sendSystemMessage(Component.literal("Your faction has reached the maximum number of alliances"));
            return;
        }
        if (WarForgeConfig.MAX_ALLIES >= 0 && requester.allies.size() >= WarForgeConfig.MAX_ALLIES) {
            player.sendSystemMessage(Component.literal("Your faction has reached the maximum number of alliances"));
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
        a.messageAll(Component.literal("Your faction is now allied with " + b.name + "."));
        b.messageAll(Component.literal("Your faction is now allied with " + a.name + "."));
        sendNotificationToFaction(a, "alliance_formed_" + b.uuid, "Alliance Formed", "Now allied with " + b.name, TOAST_SUCCESS, 6000);
        sendNotificationToFaction(b, "alliance_formed_" + a.uuid, "Alliance Formed", "Now allied with " + a.name, TOAST_SUCCESS, 6000);
    }

    public void requestDeclineAlliance(ServerPlayer player, UUID requesterFactionId) {
        Faction faction = getFactionOfPlayer(player.getUUID());
        if (!canManageAlliances(player, faction)) {
            return;
        }
        if (!faction.pendingAllianceRequests.remove(requesterFactionId)) {
            player.sendSystemMessage(Component.literal("There is no pending alliance request from that faction"));
            return;
        }
        Faction requester = getFaction(requesterFactionId);
        faction.messageAll(Component.literal("Declined the alliance request" + (requester != null ? " from " + requester.name : "") + "."));
        if (requester != null) {
            requester.messageAll(Component.literal(faction.name + " declined your alliance request."));
            sendNotificationToFaction(requester, "alliance_declined_" + faction.uuid, "Alliance Declined", faction.name + " declined your request", TOAST_WARNING, 5000);
        }
    }

    public void requestBreakAlliance(ServerPlayer player, UUID allyFactionId) {
        Faction faction = getFactionOfPlayer(player.getUUID());
        if (!canManageAlliances(player, faction)) {
            return;
        }
        if (!faction.isAllyOf(allyFactionId)) {
            player.sendSystemMessage(Component.literal("You are not allied with that faction"));
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
            long expiry = WarForgeMod.truceClock() + truceMs;
            faction.truces.put(allyFactionId, expiry);
            if (ally != null) {
                ally.truces.put(faction.uuid, expiry);
            }
        }
        String allyName = ally != null ? ally.name : "that faction";
        String truceMsg = truceMs > 0 ? " A truce prevents fighting for " + TimeHelper.formatTime(truceMs) + "." : "";
        faction.messageAll(Component.literal("Your alliance with " + allyName + " has been broken." + truceMsg));
        sendNotificationToFaction(faction, "alliance_broken_" + allyFactionId, "Alliance Broken", "No longer allied with " + allyName, TOAST_WARNING, 6000);
        if (ally != null) {
            ally.messageAll(Component.literal(faction.name + " broke your alliance." + truceMsg));
            sendNotificationToFaction(ally, "alliance_broken_" + faction.uuid, "Alliance Broken", faction.name + " broke the alliance", TOAST_DANGER, 6000);
        }
    }

    public void requestToggleAllyInteraction(ServerPlayer player) {
        Faction faction = getFactionOfPlayer(player.getUUID());
        if (!canManageAlliances(player, faction)) {
            return;
        }
        faction.allowAllyInteraction = !faction.allowAllyInteraction;
        faction.messageAll(Component.literal(faction.allowAllyInteraction
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
        SAFE_ZONE.citadelPos = new DimBlockPos(Level.OVERWORLD, 0, 0, 0); // Overworld origin
        SAFE_ZONE.colour = 0x00ff00;
        SAFE_ZONE.name = "SafeZone";
        SAFE_ZONE.uuid = SAFE_ZONE_ID;
        mFactions.put(SAFE_ZONE_ID, SAFE_ZONE);

        WAR_ZONE = new Faction();
        WAR_ZONE.citadelPos = new DimBlockPos(Level.OVERWORLD, 0, 0, 0); // Overworld origin
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
        if (faction == null) return false;
        for (Siege siege : sieges.values()) {
            if (siege.defendingFaction.equals(faction.uuid)) {
                return true;
            }
        }

        return false;
    }

    public HashMap<DimChunkPos, UUID> getClaims() {
        return mClaims;
    }

    public Collection<Faction> getAllFactions() {
        return mFactions.values();
    }

    public void recalculateAllWealth() {
        for (Faction faction : mFactions.values()) {
            faction.recalculateWealth();
        }
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

    private Component createInviteChatMessage(Faction faction) {
        MutableComponent message = Component.literal("You have received an invite to " + faction.name + ". ");
        MutableComponent clickPart = Component.literal("[Click to join]");
        clickPart.setStyle(Style.EMPTY
                .withColor(ChatFormatting.GREEN)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/f accept " + faction.name))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Join " + faction.name))));
        message.append(clickPart);
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

    private boolean validateFactionName(String factionName, CommandSourceStack sender, UUID existingFactionId) {
        if (factionName == null || factionName.isEmpty()) {
            sender.sendSystemMessage(Component.literal("You can't create or rename a faction with no name"));
            return false;
        }

        if (factionName.length() > WarForgeConfig.FACTION_NAME_LENGTH_MAX) {
            sender.sendSystemMessage(Component.literal("Name is too long, must be at most " + WarForgeConfig.FACTION_NAME_LENGTH_MAX + " characters"));
            return false;
        }

        for (int i = 0; i < factionName.length(); i++) {
            char c = factionName.charAt(i);
            if ('0' <= c && c <= '9') continue;
            if ('a' <= c && c <= 'z') continue;
            if ('A' <= c && c <= 'Z') continue;

            sender.sendSystemMessage(Component.literal("Invalid character [" + c + "] in faction name"));
            return false;
        }

        String lowerName = factionName.toLowerCase(Locale.ROOT);
        for (String banned : WarForgeConfig.FACTION_NAME_BANLIST) {
            if (banned != null && !banned.trim().isEmpty() && lowerName.contains(banned.trim().toLowerCase(Locale.ROOT))) {
                sender.sendSystemMessage(Component.literal("That faction name is not allowed"));
                return false;
            }
        }

        Faction existing = getFaction(factionName);
        if (existing != null && (!existing.uuid.equals(existingFactionId))) {
            sender.sendSystemMessage(Component.literal("A faction with the name " + factionName + " already exists"));
            return false;
        }

        return true;
    }

    // Player-typed wrapper for the create path, whose sender is always a player.
    private boolean validateFactionName(String factionName, Player sender, UUID existingFactionId) {
        return validateFactionName(factionName, sender.createCommandSourceStack(), existingFactionId);
    }

    // This is called for any non-citadel claim. Citadels can be factionless, so this makes no sense
    public void onNonCitadelClaimPlaced(IClaim claim, LivingEntity placer) {
        Faction faction = getFactionOfPlayer(placer.getUUID());
        onNonCitadelClaimPlaced(claim, faction);
        if (faction != null && placer != null) {
            sendClaimChangedNotification(
                    faction,
                    "claim_added_" + claim.getClaimPos(),
                    "Chunk Claimed",
                    placer.getName().getString() + " claimed " + claim.getClaimPos().toFancyString(),
                    faction.colour,
                    placer.getUUID()
            );
        }
    }

    public void onNonCitadelClaimPlaced(IClaim claim, Faction faction) {
        if (faction != null) {
            BlockEntity tileEntity = claim.getAsTileEntity();
            boolean dataOnlyClaim = tileEntity instanceof TileEntityBasicClaim || tileEntity instanceof TileEntityReinforcedClaim;
            mClaims.put(claim.getClaimPos().toChunkPos(), faction.uuid);

            faction.messageAll(Component.literal("Claimed the chunk [" + claim.getClaimPos().toChunkPos().x + ", " + claim.getClaimPos().toChunkPos().z + "] around " + claim.getClaimPos().toFancyString()));

            if (!dataOnlyClaim) {
                claim.onServerSetFaction(faction);
            }
            faction.onClaimPlaced(claim);

            // Basic / reinforced claim blocks become data-only claims after placement.
            if (dataOnlyClaim) {
                Level world = tileEntity.getLevel();
                DimBlockPos claimPos = claim.getClaimPos();
                world.removeBlock(claimPos.toRegularPos(), false);
                world.removeBlock(claimPos.toRegularPos().above(), false);
                world.removeBlock(claimPos.toRegularPos().above(2), false);
            }
        } else
            LOGGER.error("Invalid placer placed a claim at " + claim.getClaimPos());
    }

    public boolean requestClaimChunkNoTile(ServerPlayer player, DimChunkPos chunkPos) {
        Faction faction = getFactionOfPlayer(player.getUUID());
        if (faction == null) {
            player.sendSystemMessage(Component.literal("You aren't in a faction"));
            return false;
        }
        if (!canClaimChunkNoTile(player, faction, chunkPos, true)) {
            return false;
        }

        ClaimedBlockSelection claimBlock = findFirstClaimBlock(player.getInventory());
        if (claimBlock == null) {
            player.sendSystemMessage(Component.literal("You need a basic or reinforced claim block in your inventory"));
            return false;
        }

        mClaims.put(chunkPos, faction.uuid);
        faction.claimNoTileEntity(chunkPos, player.blockPosition().getY(), claimBlock.claimType);
        claimBlock.consume(player.getInventory());
        faction.messageAll(Component.literal("Claimed the chunk [" + chunkPos.x + ", " + chunkPos.z + "]"));
        sendClaimChangedNotification(
                faction,
                "claim_added_" + chunkPos,
                "Chunk Claimed",
                player.getName().getString() + " claimed [" + chunkPos.x + ", " + chunkPos.z + "]",
                faction.colour,
                player.getUUID()
        );
        return true;
    }

    // Enforces WarForgeConfig.MIN_DISTANCE_BETWEEN_FACTIONS: returns an opposing (non-self, non-allied,
    // non-neutral) faction whose claim sits within that many chunks (square/Chebyshev radius) of chunkPos,
    // or null if the surroundings are clear. `faction` is null when founding a new faction via a citadel,
    // in which case every other faction is opposing. Only walks the small (2d+1)^2 square, on claim attempts.
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

    public boolean hasClaimWithin(DimChunkPos center, int radius) {
        if (center == null || radius < 0) {
            return false;
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!getClaim(new DimChunkPos(center.dim, center.x + dx, center.z + dz)).equals(Faction.nullUuid)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean canClaimChunkNoTile(ServerPlayer player, Faction faction, DimChunkPos chunkPos, boolean notify) {
        if (!isOp(player) && !faction.isPlayerRoleInFaction(player.getUUID(), Faction.Role.OFFICER)) {
            if (notify) {
                player.sendSystemMessage(Component.literal("You are not an officer of your faction"));
            }
            return false;
        }
        if (!getClaim(chunkPos).equals(Faction.nullUuid)) {
            if (notify) {
                player.sendSystemMessage(Component.literal("This chunk already has a claim"));
            }
            return false;
        }
        if (isChunkContested(chunkPos)) {
            if (notify) {
                player.sendSystemMessage(Component.literal("This chunk is contested by an active siege"));
            }
            return false;
        }
        if (WarForgeMod.FOBS.isNearActiveFob(chunkPos, WarForgeConfig.FOB_CLAIM_EXCLUSION_RADIUS)) {
            if (notify) {
                player.sendSystemMessage(Component.literal("You cannot claim within " + WarForgeConfig.FOB_CLAIM_EXCLUSION_RADIUS
                        + " chunk(s) of an active FOB"));
            }
            return false;
        }
        if (!isClaimDimWhitelisted(chunkPos.dim)) {
            if (notify) {
                player.sendSystemMessage(Component.literal("You cannot claim chunks in this dimension"));
            }
            return false;
        }
        if (!faction.canPlaceClaim()) {
            if (notify) {
                player.sendSystemMessage(Component.literal(WarForgeConfig.ENABLE_CITADEL_UPGRADES
                        ? "Your faction reached it's level's claim limit, upgrade the level to incrase the limit"
                        : "Your faction has reached its claim limit"));
            }
            return false;
        }
        if (!WarForgeConfig.ENABLE_ISOLATED_CLAIMS && BlockBasicClaim.hasAdjacent(chunkPos, faction) == null) {
            if (notify) {
                player.sendSystemMessage(Component.literal("Isolated claims are disabled; you cannot put a claim here with no adjacent claims"));
            }
            return false;
        }
        Faction tooClose = findNearbyOpposingFaction(faction, chunkPos);
        if (tooClose != null) {
            if (notify) {
                player.sendSystemMessage(Component.literal("You cannot claim within " + WarForgeConfig.MIN_DISTANCE_BETWEEN_FACTIONS
                        + " chunk(s) of opposing faction " + tooClose.name));
            }
            return false;
        }

        // Prevent joining two collector-bearing islands into one.
        int collectorsInConnectedIslands = 0;
        HashSet<DimChunkPos> seen = new HashSet<DimChunkPos>();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
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
                player.sendSystemMessage(Component.literal("This claim would merge multiple islands that each already have a collector"));
            }
            return false;
        }

        // Conquered chunks are unclaimable no-man's-land for everyone (including the conqueror) until the
        // timer expires and they revert to normal wilderness.
        ObjectIntPair<UUID> conqueredChunkInfo = conqueredChunks.get(chunkPos);
        if (conqueredChunkInfo != null) {
            if (notify) {
                player.sendSystemMessage(Component.literal("This chunk is conquered wilderness; it becomes claimable in "
                        + TimeHelper.formatTime(conqueredChunkInfo.getInteger())));
            }
            return false;
        }

        return true;
    }

    // CLAIM_DIM_WHITELIST is still the legacy int-id list (Overworld 0, Nether -1, End 1). Map the
    // vanilla dimension keys back to those ids so the existing config semantics are preserved.
    private static boolean isClaimDimWhitelisted(ResourceKey<Level> dim) {
        return java.util.Arrays.asList(WarForgeConfig.CLAIM_DIM_WHITELIST).contains(dim.location().toString());
    }

    private static int legacyDimId(ResourceKey<Level> dim) {
        if (dim.equals(Level.NETHER)) return -1;
        if (dim.equals(Level.END)) return 1;
        return 0; // Overworld and any non-vanilla dimension fall back to the overworld id
    }

    public UUID getClaim(DimBlockPos pos) {
        return getClaim(pos.toChunkPos());
    }

    public UUID getClaim(DimChunkPos pos) {
        if (mClaims.containsKey(pos))
            return mClaims.get(pos);
        return Faction.nullUuid;
    }

    public Fob getFobAt(DimChunkPos pos) {
        return WarForgeMod.FOBS.getFobAt(pos);
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

    public enum SiegeZone {
        NONE,
        WAR,
        SIEGED
    }

    public static final class SiegeZoneResult {
        public static final SiegeZoneResult NONE = new SiegeZoneResult(SiegeZone.NONE, null);
        public final SiegeZone zone;
        public final UUID defendingFaction;

        private SiegeZoneResult(SiegeZone zone, UUID defendingFaction) {
            this.zone = zone;
            this.defendingFaction = defendingFaction;
        }
    }

    // Resolves the siege zone a chunk sits in across all active sieges. The inner Sieged zone takes
    // priority over the outer War zone. Carries the defending faction of the governing siege so the
    // caller can decide friend (defending member) vs foe.
    public SiegeZoneResult getSiegeZone(DimChunkPos chunkPos) {
        UUID warDefender = null;
        for (Siege siege : sieges.values()) {
            if (siege.isChunkInSiegedZone(chunkPos)) {
                return new SiegeZoneResult(SiegeZone.SIEGED, siege.defendingFaction);
            }
            if (warDefender == null && siege.isChunkInBattleZone(chunkPos)) {
                warDefender = siege.defendingFaction;
            }
        }
        return warDefender == null ? SiegeZoneResult.NONE : new SiegeZoneResult(SiegeZone.WAR, warDefender);
    }

    // True if the faction is the attacker or defender of any active siege and the chunk is within that
    // side's presence radius of a siege camp (the area whose occupancy keeps the siege from abandoning).
    public boolean isInOwnSiegeWarzone(UUID factionId, DimChunkPos pos) {
        if (factionId == null || factionId.equals(Faction.nullUuid)) {
            return false;
        }
        for (Siege siege : sieges.values()) {
            boolean attacker = factionId.equals(siege.attackingFaction);
            boolean defender = factionId.equals(siege.defendingFaction);
            if (!attacker && !defender) {
                continue;
            }
            int radius = attacker ? WarForgeConfig.SIEGE_ATTACKER_RADIUS : WarForgeConfig.SIEGE_DEFENDER_RADIUS;
            for (DimBlockPos camp : siege.attackingCamps) {
                if (camp != null && Siege.isPlayerInRadius(camp.toChunkPos(), pos, radius)) {
                    return true;
                }
            }
        }
        return false;
    }

    public Siege getActiveSiegeForFaction(UUID factionId) {
        if (factionId == null || factionId.equals(Faction.nullUuid)) {
            return null;
        }
        for (Siege siege : sieges.values()) {
            if (factionId.equals(siege.attackingFaction) || factionId.equals(siege.defendingFaction)) {
                return siege;
            }
        }
        return null;
    }

    public boolean isFactionInActiveSiege(UUID factionId) {
        return getActiveSiegeForFaction(factionId) != null;
    }

    public void updateAttackerSiegePresence(ServerPlayer player) {
        if (player == null || sieges.isEmpty()) {
            return;
        }
        UUID playerId = player.getUUID();
        Faction faction = getFactionOfPlayer(playerId);
        UUID factionId = faction == null ? null : faction.uuid;
        DimChunkPos chunk = new DimChunkPos(player.level().dimension(), player.blockPosition());
        for (Siege siege : sieges.values()) {
            boolean present = factionId != null
                    && factionId.equals(siege.attackingFaction)
                    && siege.isChunkInAttackerPresenceZone(chunk);
            siege.setAttackerPresent(playerId, present);
        }
    }

    public void removePlayerFromSiegePresence(UUID playerId) {
        for (Siege siege : sieges.values()) {
            siege.setAttackerPresent(playerId, false);
        }
    }

    public void update() {
        for (HashMap.Entry<UUID, Faction> entry : mFactions.entrySet()) {
            entry.getValue().update();
        }
    }

    // A conquered chunk with no current owner is reverting-to-wilderness no-man's-land: unclaimable and
    // unprotected (it reads as UNCLAIMED everywhere) until its timer runs out.
    public boolean isConqueredWilderness(DimChunkPos chunk) {
        return conqueredChunks.containsKey(chunk) && getClaim(chunk).equals(Faction.nullUuid);
    }

    public int conqueredRemainingMs(DimChunkPos chunk) {
        ObjectIntPair<UUID> entry = conqueredChunks.get(chunk);
        return entry == null ? 0 : entry.getInteger();
    }

    // Admin control (/f conquered set/clear). factionId is cosmetic now (border colour / "conquered by").
    public void setConquered(DimChunkPos chunk, UUID factionId, int periodMs) {
        conqueredChunks.put(chunk, new ObjectIntPair<>(copyUUID(factionId == null ? Faction.nullUuid : factionId), periodMs));
    }

    public boolean clearConquered(DimChunkPos chunk) {
        return conqueredChunks.remove(chunk) != null;
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
            WarForgeMod.FOBS.onSiegeTimerReset(kvp.getValue());
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
            boolean timerElapsed = siege.updateSiegeTimer();
            // Camp-less declared sieges have no camp TE to enforce attacker presence; do it here.
            if (siege.tickCamplessPresence()) {
                Siege.notifyAbandoned(getFaction(siege.attackingFaction), getFaction(siege.defendingFaction), true);
                siege.setAttackProgress(-siege.GetDefenceThreshold()); // attacker abandoned -> defenders hold
                finishedSiegeQueue.add(kvp.getKey());
                continue;
            }
            if ((WarForgeConfig.SIEGE_END_ON_GOAL_REACHED || timerElapsed) && siege.isCompleted())
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

    public void playerDied(ServerPlayer playerWhoDied, DamageSource source) {
        ServerPlayer killer = source.getEntity() instanceof ServerPlayer p ? p : null;

        // Siege progress from deaths in the War/Sieged zone. In confirmed-kill mode this needs an
        // opposing player killer; in count-all mode any participant death in the zone counts, so the
        // handler runs even when there is no player killer (mob, fall, etc.).
        if (!sieges.isEmpty()) {
            for (HashMap.Entry<DimChunkPos, Siege> kvp : sieges.entrySet()) {
                kvp.getValue().onParticipantDeath(killer, playerWhoDied);
                if (WarForgeConfig.SIEGE_END_ON_GOAL_REACHED && kvp.getValue().isCompleted())
                    finishedSiegeQueue.add(kvp.getKey());
            }
            processCompleteSieges();
        }

        if (killer != null) {
            Faction killedFac = getFactionOfPlayer(playerWhoDied.getUUID());
            Faction killerFac = getFactionOfPlayer(killer.getUUID());

            if (killerFac != null) {
                int numTimesKilled = 0;
                if (killerFac.killCounter.containsKey(playerWhoDied.getUUID())) {
                    numTimesKilled = killerFac.killCounter.get(playerWhoDied.getUUID()) + 1;
                    killerFac.killCounter.replace(playerWhoDied.getUUID(), numTimesKilled);
                } else {
                    numTimesKilled = 1;
                    killerFac.killCounter.put(playerWhoDied.getUUID(), numTimesKilled);
                }

                if (numTimesKilled <= WarForgeConfig.NOTORIETY_KILL_CAP_PER_PLAYER) {
                    if (killerFac != killedFac) {
                        killer.sendSystemMessage(Component.literal("Killing " + playerWhoDied.getName().getString() + " earned your faction " + WarForgeConfig.NOTORIETY_PER_PLAYER_KILL + " notoriety"));
                        killerFac.notoriety += WarForgeConfig.NOTORIETY_PER_PLAYER_KILL;
                    }
                } else {
                    killer.sendSystemMessage(Component.literal("Your faction has already killed " + playerWhoDied.getName().getString() + " " + numTimesKilled + " times. You will not become more notorious."));
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
            try {
                Siege completed = sieges.get(siege);
                // Another path (TE conclusion / command termination) may have already removed this siege
                // before its queued entry drained; skip rather than NPE and abort the whole drain loop.
                if (completed == null) continue;
                completed.finished = true;
                handleCompletedSiege(siege);
            } catch (Exception e) {
                WarForgeMod.LOGGER.error("Error processing completed siege", e);
            }
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
        if (blockPos == null) {
            WarForgeMod.LOGGER.error("Defending claim position null for siege at {}", chunkPos);
            siege.onCompleted(false);
            sieges.remove(chunkPos);
            return;
        }

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

                attackers.messageAll(Component.translatable("warforge.info.siege_won_attackers", attackers.name, blockPos.toFancyString()));
                defenders.messageAll(Component.translatable("warforge.info.siege_lost_defenders", defenders.name, blockPos.toFancyString()));
                sendSiegeOutcomeNotifications(attackers, defenders, blockPos, true);
                attackers.notoriety += WarForgeConfig.NOTORIETY_PER_SIEGE_ATTACK_SUCCESS;

                if (WarForgeConfig.SIEGE_CAPTURE) {
                    ServerLevel lvl = WarForgeMod.MC_SERVER.getLevel(blockPos.dim);
                    if (lvl == null) {
                        WarForgeMod.LOGGER.error("SIEGE_CAPTURE: level null at {}", blockPos);
                    } else {
                        lvl.setBlockAndUpdate(blockPos.toRegularPos(), Content.basicClaimBlock.defaultBlockState());
                        BlockEntity te = lvl.getBlockEntity(blockPos.toRegularPos());
                        if (te instanceof IClaim iClaim) {
                            onNonCitadelClaimPlaced(iClaim, attackers);
                        } else {
                            WarForgeMod.LOGGER.error("SIEGE_CAPTURE: no IClaim TE at {}", blockPos);
                        }
                    }
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

                attackers.messageAll(Component.translatable("warforge.info.siege_lost_attackers", attackers.name, blockPos.toFancyString()));
                defenders.messageAll(Component.translatable("warforge.info.siege_won_defenders", defenders.name, blockPos.toFancyString()));
                sendSiegeOutcomeNotifications(attackers, defenders, blockPos, false);
                defenders.notoriety += WarForgeConfig.NOTORIETY_PER_SIEGE_DEFEND_SUCCESS;

                attackers.stopMomentum(true);
                attackers.messageAll(Component.translatable("warforge.info_momentum_lost"));

                siege.onCompleted(false);
            }

            case NEUTRAL -> {
                attackers.messageAll(Component.translatable("warforge.info.siege_cancelled_attackers", attackers.name, blockPos.toFancyString()));
                defenders.messageAll(Component.translatable("warforge.info.siege_cancelled_defenders", defenders.name, blockPos.toFancyString()));
                siege.onCompleted(false); // tbh Idk what to put here
            }
        }

        WarForgeMod.FACTIONS.sendSiegeInfoToNearby(siege.defendingClaim.toChunkPos());
        sieges.remove(chunkPos);
        clearDefendingFlagIfNoSieges(siege.defendingFaction);
        WarForgeMod.FOBS.onDefendingSiegeEnded(siege.defendingFaction);
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
            attackers.messageAll(Component.translatable("warforge.info.siege_won_attackers", attackers.name, blockPos.toFancyString()));
            defenders.messageAll(Component.translatable("warforge.info.siege_lost_defenders", defenders.name, blockPos.toFancyString()));
            sendSiegeOutcomeNotifications(attackers, defenders, blockPos, true);
            attackers.notoriety += WarForgeConfig.NOTORIETY_PER_SIEGE_ATTACK_SUCCESS;
            if (WarForgeConfig.SIEGE_CAPTURE) {
                ServerLevel lvl = WarForgeMod.MC_SERVER.getLevel(blockPos.dim);
                if (lvl == null) {
                    WarForgeMod.LOGGER.error("SIEGE_CAPTURE: level null at {}", blockPos);
                } else {
                    lvl.setBlockAndUpdate(blockPos.toRegularPos(), Content.basicClaimBlock.defaultBlockState());
                    BlockEntity te = lvl.getBlockEntity(blockPos.toRegularPos());
                    if (te instanceof IClaim iClaim) {
                        onNonCitadelClaimPlaced(iClaim, attackers);
                    } else {
                        WarForgeMod.LOGGER.error("SIEGE_CAPTURE: no IClaim TE at {}", blockPos);
                    }
                }
            }
            attackers.increaseSiegeMomentum(true);
        } else {
            if (WarForgeConfig.DEFENDER_CONQUERED_CHUNK_PERIOD > 0) {
                conqueredChunks.put(chunkPos, new ObjectIntPair<>(copyUUID(defenders.uuid), WarForgeConfig.DEFENDER_CONQUERED_CHUNK_PERIOD)); // defenders get won claims defended
                for (DimBlockPos siegeCampPos : siege.attackingCamps)
                    if (siegeCampPos != null)
                        conqueredChunks.put(siegeCampPos.toChunkPos(), new ObjectIntPair<>(copyUUID(defenders.uuid), WarForgeConfig.DEFENDER_CONQUERED_CHUNK_PERIOD));
            }
            attackers.messageAll(Component.translatable("warforge.info.siege_lost_attackers", attackers.name, blockPos.toFancyString()));
            defenders.messageAll(Component.translatable("warforge.info.siege_won_defenders", defenders.name, blockPos.toFancyString()));
            sendSiegeOutcomeNotifications(attackers, defenders, blockPos, false);
            defenders.notoriety += WarForgeConfig.NOTORIETY_PER_SIEGE_DEFEND_SUCCESS;
            attackers.stopMomentum(true);
        }

        if (doCleanup) siege.onCompleted(successful);

        // Then remove the siege
        sieges.remove(chunkPos);

        WarForgeMod.FOBS.onDefendingSiegeEnded(defenders.uuid);

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
                other.messageAll(Component.literal("A siege was cancelled because the opposing faction no longer exists."));
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

    public boolean requestCreateFaction(TileEntityCitadel citadel, Player player, String factionName, int colour) {
        if (citadel == null) {
            player.sendSystemMessage(Component.literal("You can't create a faction without a citadel"));
            return false;
        }

        if (!validateFactionName(factionName, player, null)) {
            return false;
        }

        Faction existingFaction = getFactionOfPlayer(player.getUUID());
        if (existingFaction != null) {
            player.sendSystemMessage(Component.literal("You are already in a faction"));
            return false;
        }

        invalidateRedeemableInsuranceVault(player.getUUID(), player);

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
        faction.recalculateWealth(); // wealth of the citadel chunk's vein (if any)
        if (WarForgeConfig.ENABLE_SIEGE_GRACE_PERIOD && WarForgeConfig.SIEGE_GRACE_PERIOD_HOURS > 0) {
            faction.siegeGraceUntil = WarForgeMod.graceClock() + TimeUnit.HOURS.toMillis(WarForgeConfig.SIEGE_GRACE_PERIOD_HOURS);
        }

        mFactions.put(proposedID, faction);
        citadel.onServerCreateFaction(faction);
        mClaims.put(citadel.getClaimPos().toChunkPos(), proposedID);
        LEADERBOARD.RegisterFaction(faction);

        INSTANCE.messageAll(Component.literal(player.getName().getString() + " created the faction " + factionName), true);

        faction.addPlayer(player.getUUID());
        faction.setLeader(player.getUUID());
        PacketNamePlateChange packet = new PacketNamePlateChange();
        packet.name = player.getName().getString();
        packet.faction = factionName;
        packet.color = faction.colour;
        NETWORK.sendToAllAround(packet, player.getX(), player.getY(), player.getZ(), 100, player.level().dimension());
        return true;
    }

    public boolean requestRenameFaction(CommandSourceStack sender, UUID factionId, String newName) {
        Faction faction = getFaction(factionId);
        if (faction == null) {
            sender.sendSystemMessage(Component.literal("That faction doesn't exist"));
            return false;
        }
        if (!validateFactionName(newName, sender, faction.uuid)) {
            return false;
        }

        String oldName = faction.name;
        faction.name = newName;

        for (DimBlockPos claimPos : collectFactionClaimPositions(faction)) {
            ServerLevel level = MC_SERVER.getLevel(claimPos.dim);
            if (level == null) continue;
            if (level.getBlockEntity(claimPos.toRegularPos()) instanceof TileEntityClaim claim && claim.getFaction().equals(faction.uuid)) {
                claim.updateFactionName(newName);
            }
        }

        ArrayList<Player> onlinePlayers = faction.getOnlinePlayers(entityPlayer -> entityPlayer != null && entityPlayer.isAlive());
        onlinePlayers.forEach(entityPlayer -> {
            PacketNamePlateChange packet = new PacketNamePlateChange();
            packet.name = entityPlayer.getName().getString();
            packet.faction = newName;
            packet.color = faction.colour;
            NETWORK.sendToAllAround(packet, entityPlayer.getX(), entityPlayer.getY(), entityPlayer.getZ(), 100, entityPlayer.level().dimension());
        });

        FactionDisplay.refreshFactionTabNames(faction);

        INSTANCE.messageAll(Component.literal("Faction " + oldName + " was renamed to " + newName), true);
        return true;
    }

    public boolean requestLevelUp(ServerPlayer officer, UUID factionID) {
        Faction faction = getFaction(factionID);
        if (faction == null) {
            officer.sendSystemMessage(Component.literal("That faction doesn't exist"));
            return false;
        }

        if (!faction.isPlayerInFaction(officer.getUUID())) {
            officer.sendSystemMessage(Component.literal("You are not in that faction"));
            return false;
        }
        if (!faction.isPlayerRoleInFaction(officer.getUUID(), Role.OFFICER) && !faction.isPlayerRoleInFaction(officer.getUUID(), Role.LEADER)) {
            officer.sendSystemMessage(Component.literal("You must be officer or higher to upgrade citadel"));
            return false;
        }

        Map<ItemMatcher, Integer> requiredItems = (Map<ItemMatcher, Integer>) UPGRADE_HANDLER.getRequirementsFor(faction.citadelLevel + 1).clone();

        if (requiredItems == null) {
            officer.sendSystemMessage(Component.literal("You cannot level up fruther"));
            return false;
        }

        List<ItemStack> invCopy = officer.getInventory().items.stream()
                .map(ItemStack::copy)
                .collect(Collectors.toList());
        //Copy for safety
        boolean passed = true;

        for (Map.Entry<ItemMatcher, Integer> entry : requiredItems.entrySet()) {
            ItemMatcher sc = entry.getKey();
            int required = entry.getValue();

            for (ItemStack stack : invCopy) {
                if (!stack.isEmpty() && sc.matches(stack)) {
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


        if (!passed) {
            officer.sendSystemMessage(Component.literal("Could not upgrade: you don't have the required items"));
            return false;
        }

        for (int i = 0; i < officer.getInventory().items.size(); i++) {
            officer.getInventory().items.set(i, invCopy.get(i));
        }

        faction.citadelLevel++;
        faction.messageAll(Component.literal(
                officer.getName().getString() + " has upgraded your facition to level " +
                        faction.citadelLevel + "! You can now claim " +
                        UPGRADE_HANDLER.getClaimLimitForLevel(faction.citadelLevel) +
                        " chunks and force-load up to " + faction.getMaxForceLoadedChunks() + " chunks."));
        sendUpgradeNotification(faction, officer.getUUID(), officer.getName().getString());


        faction.soundEffectAll(Sounds.SFX_UPGRADE.get());
        EffectUpgrade.composeEffect(faction.citadelPos.dim, faction.citadelPos.toRegularPos().above(2), 100, 400, 1f, 0.2D, faction.colour, 10);
        return true;
    }

    public boolean requestChooseFactionFlag(ServerPlayer player, UUID factionID, String flagId) {
        Faction faction = getFaction(factionID);
        if (faction == null) {
            player.sendSystemMessage(Component.literal("That faction doesn't exist"));
            return false;
        }
        if (!WarForgeMod.isOp(player) && !faction.isPlayerRoleInFaction(player.getUUID(), Role.LEADER)) {
            player.sendSystemMessage(Component.literal("You are not the faction leader"));
            return false;
        }
        if (!faction.flagId.isEmpty()) {
            player.sendSystemMessage(Component.literal("This faction has already chosen its flag"));
            return false;
        }
        if (!WarForgeMod.FLAG_REGISTRY.isAvailable(flagId, player.getUUID())) {
            player.sendSystemMessage(Component.literal("That flag is not available"));
            return false;
        }
        if (WarForgeConfig.UNIQUE_FLAGS && isFlagTakenByOtherFaction(flagId, faction.uuid)) {
            player.sendSystemMessage(Component.literal("That flag is already taken by another faction"));
            return false;
        }

        applyFactionFlag(faction, flagId);
        WarForgeMod.syncClaimToPlayer(player, faction.citadelPos.toRegularPos());
        faction.messageAll(Component.literal("Your faction selected its flag: " + flagId));
        return true;
    }

    public boolean adminSetFactionFlag(CommandSourceStack src, UUID factionID, String flagId) {
        Faction faction = getFaction(factionID);
        if (faction == null) {
            src.sendFailure(Component.literal("That faction doesn't exist"));
            return false;
        }
        String newFlagId = flagId == null ? "" : flagId;
        if (!newFlagId.isEmpty() && !WarForgeMod.FLAG_REGISTRY.isAvailable(newFlagId)) {
            src.sendFailure(Component.literal("That flag is not available: " + newFlagId));
            return false;
        }

        applyFactionFlag(faction, newFlagId);
        if (newFlagId.isEmpty()) {
            src.sendSuccess(() -> Component.literal("Reset " + faction.name + "'s flag"), true);
            faction.messageAll(Component.literal("Your faction's flag was reset by an admin"));
        } else {
            src.sendSuccess(() -> Component.literal("Set " + faction.name + "'s flag to " + newFlagId), true);
            faction.messageAll(Component.literal("Your faction's flag was changed to " + newFlagId + " by an admin"));
        }
        return true;
    }

    // Returns true if any faction other than excludeFactionId already uses this flag.
    // Used to enforce WarForgeConfig.UNIQUE_FLAGS for player-chosen flags; admin
    // overrides (adminSetFactionFlag) deliberately skip this check.
    public boolean isFlagTakenByOtherFaction(String flagId, UUID excludeFactionId) {
        if (flagId == null || flagId.isEmpty()) {
            return false;
        }
        for (Faction other : mFactions.values()) {
            if (other == null || other.uuid.equals(excludeFactionId)) {
                continue;
            }
            if (flagId.equalsIgnoreCase(other.flagId)) {
                return true;
            }
        }
        return false;
    }

    private void applyFactionFlag(Faction faction, String flagId) {
        faction.flagId = flagId;
        for (DimBlockPos claimPos : collectFactionClaimPositions(faction)) {
            ServerLevel level = MC_SERVER.getLevel(claimPos.dim);
            if (level == null) continue;
            if (level.getBlockEntity(claimPos.toRegularPos()) instanceof TileEntityClaim claim && claim.getFaction().equals(faction.uuid)) {
                claim.updateFactionFlag(flagId);
            }
        }
        for (ServerPlayer online : MC_SERVER.getPlayerList().getPlayers()) {
            sendClaimChunks(online, new DimChunkPos(online.level().dimension(), online.blockPosition()), WarForgeConfig.CLAIM_MANAGER_RADIUS);
        }
    }

    public boolean requestRemovePlayerFromFaction(CommandSourceStack remover, UUID factionID, UUID toRemove) {
        Faction faction = getFaction(factionID);
        if (faction == null) {
            remover.sendSystemMessage(Component.literal("That faction doesn't exist"));
            return false;
        }

        if (!faction.isPlayerInFaction(toRemove)) {
            remover.sendSystemMessage(Component.literal("That player is not in that faction"));
            return false;
        }

        boolean canRemove = remover.hasPermission(2);
        boolean removingSelf = false;
        if (remover.getEntity() instanceof Player removerPlayer) {
            UUID removerID = removerPlayer.getUUID();
            if (removerID.equals(toRemove)) // remove self
            {
                canRemove = true;
                removingSelf = true;
            }

            if (faction.isPlayerOutrankingOfficer(removerID, toRemove))
                canRemove = true;
        }

        if (!canRemove) {
            remover.sendSystemMessage(Component.literal("You don't have permission to remove that player"));
            return false;
        }

        if (removingSelf && faction.isPlayerRoleInFaction(toRemove, Role.LEADER) && faction.getMemberCount() > 1) {
            remover.sendSystemMessage(Component.literal("You must transfer leadership before leaving the faction"));
            return false;
        }

        GameProfile userProfile = MC_SERVER.getProfileCache().get(toRemove).orElse(null);
        if (userProfile != null) {
            if (removingSelf) {
                faction.messageAll(Component.literal(userProfile.getName() + " left " + faction.name));
                if (faction.getMemberCount() <= 1)
                    faction.messageAll(Component.literal(faction.name + "was abandoned and disbanded."));
            } else
                faction.messageAll(Component.literal(userProfile.getName() + " was kicked from " + faction.name));
        } else {
            remover.sendSystemMessage(Component.literal("Error: Could not get user profile"));
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

    public boolean requestInvitePlayerToMyFaction(Player factionOfficer, UUID invitee) {
        Faction myFaction = getFactionOfPlayer(factionOfficer.getUUID());
        if (myFaction != null)
            return RequestInvitePlayerToFaction(factionOfficer.createCommandSourceStack(), myFaction.uuid, invitee);
        return false;
    }

    public boolean RequestInvitePlayerToFaction(CommandSourceStack factionOfficer, UUID factionID, UUID invitee) {
        Faction faction = getFaction(factionID);
        if (faction == null) {
            factionOfficer.sendSystemMessage(Component.literal("That faction doesn't exist"));
            return false;
        }

        UUID officerID = factionOfficer.getEntity() instanceof Player p ? p.getUUID() : Faction.nullUuid;
        if (!factionOfficer.hasPermission(2) && !faction.isPlayerRoleInFaction(officerID, Faction.Role.OFFICER)) {
            factionOfficer.sendSystemMessage(Component.literal("You are not an officer of this faction"));
            return false;
        }

        Faction existingFaction = getFactionOfPlayer(invitee);
        if (existingFaction != null) {
            factionOfficer.sendSystemMessage(Component.literal("That player is already in a faction"));
            return false;
        }

        // TODO: Faction player limit - grows with claims?

        faction.invitePlayer(invitee);
        ServerPlayer inviteePlayer = MC_SERVER.getPlayerList().getPlayer(invitee);
        if (inviteePlayer != null) {
            inviteePlayer.sendSystemMessage(createInviteChatMessage(faction));
            sendNotificationToPlayer(
                    inviteePlayer,
                    "faction_invite_" + faction.uuid,
                    "Faction Invite",
                    faction.name,
                    faction.colour,
                    7000,
                    factionOfficer.getEntity() instanceof Player p ? p.getUUID() : null
            );
        }

        return true;
    }

    public void RequestAcceptInvite(Player player) {
        RequestAcceptInvite(player, (String) null);
    }

    public void RequestAcceptInvite(Player player, UUID factionId) {
        if (factionId == null || factionId.equals(Faction.nullUuid)) {
            RequestAcceptInvite(player, (String) null);
            return;
        }

        Faction inviter = getFaction(factionId);
        if (inviter == null || !inviter.isInvitingPlayer(player.getUUID())) {
            player.sendSystemMessage(Component.literal("You do not have an invite from that faction"));
            return;
        }

        if (getFactionOfPlayer(player.getUUID()) != null) {
            player.sendSystemMessage(Component.literal("You are already in a faction"));
            return;
        }

        invalidateRedeemableInsuranceVault(player.getUUID(), player);
        inviter.addPlayer(player.getUUID());
        sendFactionMemberJoinedNotification(inviter, player);
    }

    public void RequestAcceptInvite(Player player, String factionName) {
        if (getFactionOfPlayer(player.getUUID()) != null) {
            player.sendSystemMessage(Component.literal("You are already in a faction"));
            return;
        }

        ArrayList<Faction> invites = getFactionsWithOpenInvitesTo(player.getUUID());
        if (invites.isEmpty()) {
            player.sendSystemMessage(Component.literal("You have no open invite to accept"));
            return;
        }

        Faction inviter;
        if (factionName != null && !factionName.trim().isEmpty()) {
            inviter = getFactionWithOpenInviteTo(player.getUUID(), factionName.trim());
            if (inviter == null) {
                player.sendSystemMessage(Component.literal("You do not have an invite from " + factionName));
                return;
            }
        } else if (invites.size() == 1) {
            inviter = invites.get(0);
        } else {
            player.sendSystemMessage(Component.literal("You have multiple faction invites. Use /f accept <factionName> or click one below:"));
            for (Faction invite : invites) {
                player.sendSystemMessage(createInviteChatMessage(invite));
            }
            return;
        }

        invalidateRedeemableInsuranceVault(player.getUUID(), player);
        inviter.addPlayer(player.getUUID());
        sendFactionMemberJoinedNotification(inviter, player);
    }

    public boolean RequestTransferLeadership(Player factionLeader, UUID factionID, UUID newLeaderID) {
        Faction faction = getFaction(factionID);
        if (faction == null) {
            factionLeader.sendSystemMessage(Component.literal("That faction does not exist"));
            return false;
        }

        if (!isOp(factionLeader) && !faction.isPlayerRoleInFaction(factionLeader.getUUID(), Faction.Role.LEADER)) {
            factionLeader.sendSystemMessage(Component.literal("You are not the leader of this faction"));
            return false;
        }

        if (!faction.isPlayerInFaction(newLeaderID)) {
            factionLeader.sendSystemMessage(Component.literal("That player is not in your faction"));
            return false;
        }

        // Do the set
        if (!faction.setLeader(newLeaderID)) {
            factionLeader.sendSystemMessage(Component.literal("Failed to set leader"));
            return false;
        }

        factionLeader.sendSystemMessage(Component.literal("Successfully set leader"));
        return true;
    }

    public boolean requestPromote(Player factionLeader, ServerPlayer target) {
        Faction faction = getFactionOfPlayer(factionLeader.getUUID());
        if (faction == null) {
            factionLeader.sendSystemMessage(Component.literal("You are not in a faction"));
            return false;
        }
        if (!faction.isPlayerRoleInFaction(factionLeader.getUUID(), Role.LEADER)) {
            factionLeader.sendSystemMessage(Component.literal("You are not the leader of this faction"));
            return false;
        }
        if (!faction.isPlayerRoleInFaction(target.getUUID(), Role.MEMBER)) {
            factionLeader.sendSystemMessage(Component.literal("This player cannot be promoted"));
            return false;
        }

        faction.promote(target.getUUID());
        return true;
    }

    public boolean requestDemote(Player factionLeader, ServerPlayer target) {
        Faction faction = getFactionOfPlayer(factionLeader.getUUID());
        if (faction == null) {
            factionLeader.sendSystemMessage(Component.literal("You are not in a faction"));
            return false;
        }
        if (!faction.isPlayerRoleInFaction(factionLeader.getUUID(), Role.LEADER)) {
            factionLeader.sendSystemMessage(Component.literal("You are not the leader of this faction"));
            return false;
        }
        if (!faction.isPlayerRoleInFaction(target.getUUID(), Role.OFFICER)) {
            factionLeader.sendSystemMessage(Component.literal("This player cannot be demoted"));
            return false;
        }

        faction.demote(target.getUUID());
        return true;
    }

    public boolean requestDisbandFaction(Player factionLeader, UUID factionID) {
        if (factionID.equals(Faction.nullUuid)) {
            Faction faction = getFactionOfPlayer(factionLeader.getUUID());
            if (faction != null)
                factionID = faction.uuid;
        }

        if (!IsPlayerRoleInFaction(factionLeader.getUUID(), factionID, Faction.Role.LEADER)) {
            factionLeader.sendSystemMessage(Component.literal("You are not the leader of this faction"));
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
        ArrayList<Player> onlinePlayers = faction.getOnlinePlayers(
                player -> player != null);

        onlinePlayers.forEach(player -> {
            PacketNamePlateChange packet = new PacketNamePlateChange();
            packet.name = player.getName().getString();
            packet.isRemove = true;
            NETWORK.sendToAllAround(packet, player.getX(), player.getY(), player.getZ(), 100, player.level().dimension());
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

    public boolean requestRedeemInsuranceVault(ServerPlayer player) {
        ArrayList<ItemStack> items = redeemableInsuranceVaults.get(player.getUUID());
        if (items == null || items.isEmpty()) {
            player.sendSystemMessage(Component.literal("You do not have an unlocked insurance stash to redeem"));
            return false;
        }

        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ItemStack redeemStack = stack.copy();
            boolean inserted = player.getInventory().add(redeemStack);
            if (!inserted && !redeemStack.isEmpty()) {
                player.drop(redeemStack, false);
            }
        }

        redeemableInsuranceVaults.remove(player.getUUID());
        player.sendSystemMessage(Component.literal("Redeemed your faction insurance stash"));
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

        ServerPlayer leader = MC_SERVER.getPlayerList().getPlayer(leaderId);
        if (leader != null) {
            leader.sendSystemMessage(Component.literal("Your faction insurance stash has been unlocked. Use /f vault redeem to withdraw it."));
        }
    }

    private void invalidateRedeemableInsuranceVault(UUID playerId, Player player) {
        ArrayList<ItemStack> removed = redeemableInsuranceVaults.remove(playerId);
        if (removed != null && !removed.isEmpty() && player != null) {
            player.sendSystemMessage(Component.literal("Your unlocked insurance stash was voided because you entered a new faction before redeeming it."));
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
        GameProfile profile = MC_SERVER.getProfileCache().get(playerID).orElse(null);
        if (profile != null) {
            sendFactionPresenceNotification(faction, playerID, profile.getName(), true);
        }
    }

    public void onFactionMemberLoggedOut(UUID playerID) {
        removePlayerFromSiegePresence(playerID);
        Faction faction = getFactionOfPlayer(playerID);
        if (!isValidFaction(faction)) {
            return;
        }
        faction.onlinePlayerCount = Math.max(0, faction.onlinePlayerCount - 1);
        GameProfile profile = MC_SERVER.getProfileCache().get(playerID).orElse(null);
        if (profile != null) {
            sendFactionPresenceNotification(faction, playerID, profile.getName(), false);
        }
        if (faction.onlinePlayerCount == 0 && WarForgeConfig.ENABLE_OFFLINE_RAID_PROTECTION && !faction.offlineRaidProtectionDisabled) {
            faction.offlineRaidProtectionUntil = WarForgeMod.offlineProtectionClock() + TimeUnit.HOURS.toMillis(WarForgeConfig.OFFLINE_RAID_PROTECTION_HOURS);
        }
    }

    public boolean isOfflineRaidProtected(Faction faction) {
        if (!WarForgeConfig.ENABLE_OFFLINE_RAID_PROTECTION || faction == null || faction.offlineRaidProtectionDisabled) {
            return false;
        }
        return faction.onlinePlayerCount <= 0 && WarForgeMod.offlineProtectionClock() < faction.offlineRaidProtectionUntil;
    }

    // New-faction grace: a freshly created faction is unsiegeable until its grace window expires. Disabling
    // the feature in config drops grace for everyone immediately. A graced faction forfeits grace the moment
    // it starts a siege of its own (see createSiege).
    public boolean isSiegeGraceProtected(Faction faction) {
        if (!WarForgeConfig.ENABLE_SIEGE_GRACE_PERIOD || faction == null) {
            return false;
        }
        return WarForgeMod.graceClock() < faction.siegeGraceUntil;
    }

    // runs on the server only
    public void requestStartSiege(Player factionOfficer, DimBlockPos siegeCampPos, Vec3i direction) {
        Faction attacking = getFactionOfPlayer(factionOfficer.getUUID());
        if (attacking == null) {
            factionOfficer.sendSystemMessage(Component.literal("You are not in a faction"));
            return;
        }
        long currentTimeStamp = WarForgeMod.siegeClock();

        // for some reason, server tick is in number of ticks and last siege timestamp is in ms, while siege cooldown is in mins (according to description), though through calculations looks like hours? it should be in ms
        if (attacking.getSiegeMomentum() == 0 && attacking.lastSiegeTimestamp + WarForgeConfig.SIEGE_COOLDOWN_FAIL > currentTimeStamp) {
            factionOfficer.sendSystemMessage(Component.literal("Your faction is on cooldown on starting a new siege"));


            factionOfficer.sendSystemMessage(Component.literal("Cooldown remaining:" + TimeHelper.formatTime(attacking.lastSiegeTimestamp + WarForgeConfig.SIEGE_COOLDOWN_FAIL - currentTimeStamp)));
            return;
        }


        if (!attacking.isPlayerRoleInFaction(factionOfficer.getUUID(), Faction.Role.OFFICER)) {
            factionOfficer.sendSystemMessage(Component.literal("You are not an officer of this faction"));
            return;
        }

        // TODO: Verify there aren't existing alliances

        if (direction.getZ() == 0 && direction.getX() == 0) {
            factionOfficer.sendSystemMessage(Component.literal("You can't siege the siege block!"));
            return;
        }

        // Server-side authority: the camp GUI only ever offers cardinally-adjacent targets, but the
        // wire packet carries raw offset bytes. Reject crafted diagonal / non-adjacent directions so a
        // malicious client can't siege a chunk that bypassed the GUI's adjacency / vertical checks.
        if (Math.abs(direction.getX()) > 1 || Math.abs(direction.getZ()) > 1
                || (direction.getX() != 0 && direction.getZ() != 0)) {
            factionOfficer.sendSystemMessage(Component.literal("Invalid siege direction"));
            return;
        }

        TileEntitySiegeCamp siegeTE = (TileEntitySiegeCamp) MC_SERVER.getLevel(siegeCampPos.dim).getBlockEntity(siegeCampPos.toRegularPos());
        if (siegeTE == null) {
            factionOfficer.sendSystemMessage(Component.literal("Could not find that siege camp"));
            return;
        }
        if (siegeTE.getSiegeTarget() != null) {
            factionOfficer.sendSystemMessage(Component.literal("That siege camp is already committed to an active siege"));
            return;
        }
        if (!attacking.uuid.equals(siegeTE.getFaction())) {
            factionOfficer.sendSystemMessage(Component.literal("Your faction doesn't own this block!"));
            return;
        }
        for (Siege siege : sieges.values()) {
            if (siege.attackingCamps.contains(siegeCampPos)) {
                factionOfficer.sendSystemMessage(Component.literal("That siege camp is already part of an active siege"));
                return;
            }
        }

        DimChunkPos defendingChunk = siegeCampPos.toChunkPos().Offset(direction);
        UUID defendingFactionID = mClaims.get(defendingChunk);
        Faction defending = getFaction(defendingFactionID);

        if (defending == null) {
            factionOfficer.sendSystemMessage(Component.literal("Could not find a target faction at that position"));
            return;
        }

        String allianceBlock = getSiegeBlockReason(attacking, defending);
        if (allianceBlock != null) {
            factionOfficer.sendSystemMessage(Component.literal(allianceBlock));
            return;
        }

        if (isOfflineRaidProtected(defending)) {
            factionOfficer.sendSystemMessage(Component.literal("That faction is offline and protected until " + TimeHelper.formatTime(defending.offlineRaidProtectionUntil - WarForgeMod.offlineProtectionClock())));
            return;
        }

        if (isSiegeGraceProtected(defending)) {
            factionOfficer.sendSystemMessage(Component.literal("That faction is too new to be sieged. Grace expires in " + TimeHelper.formatTime(defending.siegeGraceUntil - WarForgeMod.graceClock())));
            return;
        }

        DimBlockPos defendingPos = defending.getSpecificPosForClaim(defendingChunk);
        if (defendingPos == null) {
            factionOfficer.sendSystemMessage(Component.literal("Could not find a valid defending claim in that chunk"));
            return;
        }

        if (IsSiegeInProgress(defendingPos.toChunkPos())) {
            factionOfficer.sendSystemMessage(Component.literal("That position is already under siege"));
            return;
        }

        ObjectIntPair<UUID> conqueredEntry = conqueredChunks.get(defendingPos.toChunkPos());
        if (conqueredEntry != null) {
            factionOfficer.sendSystemMessage(Component.translatable("warforge.info.chunk_is_conquered",
                    defending.name, TimeHelper.formatTime(conqueredEntry.getInteger())));
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
        long maxTime = WarForgeConfig.SIEGE_MOMENTUM_TIME.get(attacking.getSiegeMomentum()) * 1000L;
        attacking.siegeGraceUntil = 0L; // starting a siege forfeits any remaining new-faction grace
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
    public void requestDeclareSiege(ServerPlayer officer, DimChunkPos targetChunk, DimChunkPos fromChunk) {
        if (!WarForgeConfig.SIEGE_ALLOW_UI_DECLARE) {
            officer.sendSystemMessage(Component.literal("Declaring sieges from the map is disabled on this server"));
            return;
        }
        if (targetChunk == null || fromChunk == null) return;

        Faction attacking = getFactionOfPlayer(officer.getUUID());
        if (attacking == null) {
            officer.sendSystemMessage(Component.literal("You are not in a faction"));
            return;
        }
        long currentTimeStamp = WarForgeMod.siegeClock();
        if (attacking.getSiegeMomentum() == 0 && attacking.lastSiegeTimestamp + WarForgeConfig.SIEGE_COOLDOWN_FAIL > currentTimeStamp) {
            officer.sendSystemMessage(Component.literal("Your faction is on cooldown on starting a new siege"));
            officer.sendSystemMessage(Component.literal("Cooldown remaining:" + TimeHelper.formatTime(attacking.lastSiegeTimestamp + WarForgeConfig.SIEGE_COOLDOWN_FAIL - currentTimeStamp)));
            return;
        }
        if (!attacking.isPlayerRoleInFaction(officer.getUUID(), Faction.Role.OFFICER)) {
            officer.sendSystemMessage(Component.literal("You are not an officer of this faction"));
            return;
        }

        // Same dimension only (the map data and the player are bound to one dimension) and a real,
        // in-range separation between the start-from chunk and the target.
        if (!targetChunk.dim.equals(fromChunk.dim) || !targetChunk.dim.equals(officer.level().dimension())) {
            officer.sendSystemMessage(Component.literal("The target and start chunk must be in your current dimension"));
            return;
        }
        int chebyshev = Math.max(Math.abs(targetChunk.x - fromChunk.x), Math.abs(targetChunk.z - fromChunk.z));
        if (chebyshev == 0) {
            officer.sendSystemMessage(Component.literal("Pick a start chunk other than the target"));
            return;
        }
        if (chebyshev > WarForgeConfig.SIEGE_DECLARE_MAX_RANGE) {
            officer.sendSystemMessage(Component.literal("That target is too far from your chosen start chunk (max " + WarForgeConfig.SIEGE_DECLARE_MAX_RANGE + " chunks)"));
            return;
        }

        UUID defendingFactionID = mClaims.get(targetChunk);
        Faction defending = getFaction(defendingFactionID);
        if (defending == null) {
            officer.sendSystemMessage(Component.literal("There is no faction claim to siege at that chunk"));
            return;
        }
        if (defending.uuid.equals(attacking.uuid)) {
            officer.sendSystemMessage(Component.literal("You can't siege your own faction's claim"));
            return;
        }

        String allianceBlock = getSiegeBlockReason(attacking, defending);
        if (allianceBlock != null) {
            officer.sendSystemMessage(Component.literal(allianceBlock));
            return;
        }
        if (isOfflineRaidProtected(defending)) {
            officer.sendSystemMessage(Component.literal("That faction is offline and protected until " + TimeHelper.formatTime(defending.offlineRaidProtectionUntil - WarForgeMod.offlineProtectionClock())));
            return;
        }

        if (isSiegeGraceProtected(defending)) {
            officer.sendSystemMessage(Component.literal("That faction is too new to be sieged. Grace expires in " + TimeHelper.formatTime(defending.siegeGraceUntil - WarForgeMod.graceClock())));
            return;
        }

        DimBlockPos defendingPos = defending.getSpecificPosForClaim(targetChunk);
        if (defendingPos == null) {
            officer.sendSystemMessage(Component.literal("Could not find a valid defending claim in that chunk"));
            return;
        }

        // Respect claims that opt out of being sieged (island collectors, admin claims, ...).
        BlockEntity targetTe = MC_SERVER.getLevel(defendingPos.dim).getBlockEntity(defendingPos.toRegularPos());
        if (targetTe instanceof IClaim && !((IClaim) targetTe).canBeSieged()) {
            officer.sendSystemMessage(Component.literal("That claim cannot be sieged"));
            return;
        }

        if (IsSiegeInProgress(targetChunk)) {
            officer.sendSystemMessage(Component.literal("That position is already under siege"));
            return;
        }
        ObjectIntPair<UUID> conqueredEntry = conqueredChunks.get(targetChunk);
        if (conqueredEntry != null) {
            officer.sendSystemMessage(Component.translatable("warforge.info.chunk_is_conquered",
                    defending.name, TimeHelper.formatTime(conqueredEntry.getInteger())));
            return;
        }

        // Cost: consume one siege camp block item from the officer's inventory.
        if (!consumeSiegeDeclarationItem(officer)) {
            officer.sendSystemMessage(Component.literal("You need a siege camp block to declare a siege"));
            return;
        }

        DimBlockPos anchorPos = new DimBlockPos(fromChunk.dim, fromChunk.getMinBlockX(), 0, fromChunk.getMinBlockZ());
        Siege siege = createSiege(attacking, defending, defendingPos, targetChunk, anchorPos, true);
        siege.start();
        attacking.lastSiegeTimestamp = currentTimeStamp;
    }

    // Removes one siege camp block item from the player's main inventory. Returns false if they have
    // none. If the item registry is somehow unavailable, fails open (does not block the declaration).
    private boolean consumeSiegeDeclarationItem(ServerPlayer player) {
        if (Content.siegeCampBlockItem == null) return true;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (!stack.isEmpty() && stack.getItem() == Content.siegeCampBlockItem) {
                stack.shrink(1);
                if (stack.isEmpty()) player.getInventory().items.set(i, ItemStack.EMPTY);
                player.inventoryMenu.broadcastChanges();
                return true;
            }
        }
        return false;
    }

    // Claims a chunk for one of the citadel-less special zones (SafeZone / WarZone). These are
    // data-only claims: no physical block, no tile entity. SafeZone carries the ADMIN claim type
    // (the former admin claim); WarZone carries the WARZONE claim type.
    public void requestZoneClaim(Player op, DimChunkPos pos, UUID zoneID) {
        if (!WarForgeMod.isOp(op)) {
            op.sendSystemMessage(Component.literal("You are not the faction leader"));
            return;
        }
        Faction zone = getFaction(zoneID);
        if (zone == null || !IsNeutralZone(zoneID)) {
            op.sendSystemMessage(Component.literal("Unknown zone"));
            return;
        }

        UUID existingClaim = getClaim(pos);
        if (!existingClaim.equals(Faction.nullUuid)) {
            op.sendSystemMessage(Component.literal("There is already a claim here"));
            return;
        }

        Faction.ClaimType claimType = zoneID.equals(SAFE_ZONE_ID) ? Faction.ClaimType.ADMIN : Faction.ClaimType.WARZONE;
        mClaims.put(pos, zone.uuid);
        zone.claimNoTileEntity(pos, op.blockPosition().getY(), claimType);

        op.sendSystemMessage(Component.literal("Claimed [" + pos.x + ", " + pos.z + "] for " + zone.name));
    }

    // Removes a SafeZone / WarZone claim at the given chunk. Mirror of requestZoneClaim; data-only,
    // so there is no block or tile entity to clean up.
    public void requestZoneUnclaim(Player op, DimChunkPos pos) {
        if (!WarForgeMod.isOp(op)) {
            op.sendSystemMessage(Component.literal("You are not the faction leader"));
            return;
        }
        UUID existingClaim = getClaim(pos);
        if (!IsNeutralZone(existingClaim)) {
            op.sendSystemMessage(Component.literal("There is no zone claim here"));
            return;
        }

        Faction zone = getFaction(existingClaim);
        DimBlockPos claimPos = zone == null ? null : zone.getSpecificPosForClaim(pos);
        if (claimPos != null) {
            zone.claims.remove(claimPos);
            zone.claimTypes.remove(claimPos);
        }
        mClaims.remove(pos);

        op.sendSystemMessage(Component.literal("Removed the " + (zone == null ? "zone" : zone.name)
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
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            DimChunkPos targetChunkPos = pos.toChunkPos().Offset(facing, 1);
            if (!isClaimed(excludingFaction, targetChunkPos)) continue;
            UUID targetID = getClaim(targetChunkPos);
            int targetY = getFaction(targetID).getSpecificPosForClaim(targetChunkPos).getY();
            if (Math.abs(pos.getY() - targetY) > WarForgeConfig.VERTICAL_SIEGE_DIST) continue;

            positions.set(facing.get2DDataValue(), targetChunkPos);
            ++numValidTargets;
        }

        // Diagonal directions (indices 4–7): NW, NE, SW, SE
        Direction[][] diagonalPairs = {
                {Direction.NORTH, Direction.WEST},  // NW
                {Direction.NORTH, Direction.EAST},  // NE
                {Direction.SOUTH, Direction.WEST},  // SW
                {Direction.SOUTH, Direction.EAST}   // SE
        };

        for (int i = 0; i < diagonalPairs.length; i++) {
            Direction f1 = diagonalPairs[i][0];
            Direction f2 = diagonalPairs[i][1];
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

    // Every block position where the faction owns a claim-like tile entity (claim blocks, island
    // collectors, and the camps of sieges it is attacking). 1.20.1 has no global loaded-tile-entity
    // list, so visual refreshes (name / flag / colour) enumerate these tracked positions directly.
    private Set<DimBlockPos> collectFactionClaimPositions(Faction faction) {
        Set<DimBlockPos> positions = new HashSet<>(faction.claims.keySet());
        positions.addAll(faction.islandCollectors);
        for (Siege siege : sieges.values()) {
            if (faction.uuid.equals(siege.attackingFaction)) {
                for (DimBlockPos campPos : siege.attackingCamps) {
                    if (campPos != null) positions.add(campPos);
                }
            }
        }
        return positions;
    }

    public Set<DimChunkPos> collectFactionIsland(UUID factionID, DimChunkPos start) {
        HashSet<DimChunkPos> visited = new HashSet<>();
        if (!factionID.equals(getClaim(start))) {
            return visited;
        }

        ArrayDeque<DimChunkPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            DimChunkPos current = queue.poll();
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                DimChunkPos neighbor = current.Offset(facing, 1);
                if (!visited.contains(neighbor) && factionID.equals(getClaim(neighbor))) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return visited;
    }

    public boolean registerCollector(ServerPlayer player, DimBlockPos collectorPos) {
        Faction faction = getFactionOfPlayer(player.getUUID());
        if (faction == null) {
            player.sendSystemMessage(Component.literal("You are not in a faction"));
            return false;
        }
        if (!isOp(player) && !faction.isPlayerRoleInFaction(player.getUUID(), Role.OFFICER)) {
            player.sendSystemMessage(Component.literal("You are not an officer of your faction"));
            return false;
        }

        DimChunkPos collectorChunk = collectorPos.toChunkPos();
        if (!faction.uuid.equals(getClaim(collectorChunk))) {
            player.sendSystemMessage(Component.literal("Collector can only be placed in your faction land"));
            return false;
        }

        Set<DimChunkPos> island = collectFactionIsland(faction.uuid, collectorChunk);
        ArrayList<DimBlockPos> staleCollectors = new ArrayList<DimBlockPos>();
        for (DimBlockPos existingCollector : faction.islandCollectors) {
            ServerLevel collectorWorld = MC_SERVER.getLevel(existingCollector.dim);
            if (collectorWorld == null || !(collectorWorld.getBlockEntity(existingCollector.toRegularPos()) instanceof TileEntityIslandCollector)) {
                staleCollectors.add(existingCollector);
                continue;
            }
            if (island.contains(existingCollector.toChunkPos())) {
                player.sendSystemMessage(Component.literal("There is already a collector on this faction island"));
                return false;
            }
        }
        if (!staleCollectors.isEmpty()) {
            faction.islandCollectors.removeAll(staleCollectors);
            WarForgeMod.CHUNK_LOADING_MANAGER.refreshFactionChunks(faction);
        }

        BlockEntity te = player.level().getBlockEntity(collectorPos.toRegularPos());
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

    public boolean requestToggleForceLoad(ServerPlayer player, DimChunkPos chunkPos) {
        Faction faction = getFactionOfPlayer(player.getUUID());
        if (faction == null) {
            player.sendSystemMessage(Component.literal("You are not in a faction"));
            return false;
        }
        if (!isOp(player) && !faction.isPlayerRoleInFaction(player.getUUID(), Role.OFFICER)) {
            player.sendSystemMessage(Component.literal("You are not an officer of your faction"));
            return false;
        }
        if (!faction.uuid.equals(getClaim(chunkPos))) {
            player.sendSystemMessage(Component.literal("You can only force-load your faction chunks"));
            return false;
        }

        if (faction.forcedChunks.contains(chunkPos)) {
            faction.forcedChunks.remove(chunkPos);
            player.sendSystemMessage(Component.literal("Removed force-load from chunk [" + chunkPos.x + ", " + chunkPos.z + "]"));
        } else {
            if (!faction.canForceLoadMore()) {
                player.sendSystemMessage(Component.literal("Force-load chunk limit reached (" + faction.getMaxForceLoadedChunks() + ")"));
                return false;
            }
            faction.forcedChunks.add(chunkPos);
            player.sendSystemMessage(Component.literal("Force-loaded chunk [" + chunkPos.x + ", " + chunkPos.z + "]"));
        }

        WarForgeMod.CHUNK_LOADING_MANAGER.refreshFactionChunks(faction);
        return true;
    }

    public PacketClaimChunksData createClaimChunksData(ServerPlayer player, DimChunkPos center, int radius) {
        return createClaimChunksData(player, center, radius, false);
    }

    public PacketClaimChunksData createClaimChunksData(ServerPlayer player, DimChunkPos center, int radius, boolean outlineOnly) {
        int maxRadius = outlineOnly ? WarForgeConfig.BORDER_SYNC_MAX_RADIUS : WarForgeConfig.CLAIM_MANAGER_RADIUS;
        int clampedRadius = Math.max(1, Math.min(radius, maxRadius));
        if (!center.dim.equals(player.level().dimension())) {
            center = new DimChunkPos(player.level().dimension(), player.blockPosition());
        }

        Faction faction = getFactionOfPlayer(player.getUUID());
        boolean canManage = faction != null && (isOp(player) || faction.isPlayerRoleInFaction(player.getUUID(), Role.OFFICER));

        PacketClaimChunksData packet = new PacketClaimChunksData();
        packet.dim = center.dim;
        packet.centerX = center.x;
        packet.centerZ = center.z;
        packet.radius = clampedRadius;
        packet.outlineOnly = outlineOnly;
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
                    continue;
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
                    // Only an unclaimed conquered chunk is reverting-to-wilderness no-man's-land; a claimed
                    // one (defender held it) just carries a re-siege cooldown, so don't advertise a timer.
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
                    var veinInfo = VEIN_HANDLER.getVein(chunk.dim, chunk.x, chunk.z, MC_SERVER.overworld().getSeed());
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

                // The border sync only carries chunks that actually have an outline.
                if (!outlineOnly || info.hasVisibleOutline()) {
                    packet.chunks.add(info);
                }
            }
        }

        return packet;
    }

    public void sendClaimChunks(ServerPlayer player, DimChunkPos center, int radius) {
        sendClaimChunks(player, center, radius, false);
    }

    public void sendClaimChunks(ServerPlayer player, DimChunkPos center, int radius, boolean outlineOnly) {
        PacketClaimChunksData packet = createClaimChunksData(player, center, radius, outlineOnly);
        WarForgeMod.NETWORK.sendTo(packet, player);
    }

    // Samples per-block vanilla map colours + heights for a chunk region and ships them to the
    // requesting client so the claim map can render real terrain for chunks the client hasn't loaded
    // (e.g. a distant siege target). Only chunks currently loaded server-side are sampled; the rest
    // fall back to the client's faction-tint / placeholder rendering. Sampling is intentionally
    // limited (no force-loading) to avoid letting a client trigger chunk generation on demand.
    public void sendTerrainColors(ServerPlayer player, DimChunkPos center, int radius) {
        if (center == null) return;
        radius = Math.max(1, Math.min(radius, WarForgeConfig.CLAIM_MANAGER_RADIUS));
        // Match the claim-data constraint: only the player's current dimension.
        if (!center.dim.equals(player.level().dimension())) {
            center = new DimChunkPos(player.level().dimension(), player.blockPosition());
        }
        ServerLevel world = MC_SERVER.getLevel(center.dim);
        if (world == null) return;

        PacketTerrainColors packet = new PacketTerrainColors();
        packet.dim = center.dim;
        packet.centerX = center.x;
        packet.centerZ = center.z;
        packet.radius = radius;

        for (int cx = center.x - radius; cx <= center.x + radius; cx++) {
            for (int cz = center.z - radius; cz <= center.z + radius; cz++) {
                LevelChunk chunk = world.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue; // not loaded server-side; client keeps its placeholder for this chunk
                }
                int[] colors = new int[256];
                int[] heights = new int[256];
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int idx = lx + lz * 16;
                        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                        heights[idx] = y;
                        if (y <= world.getMinBuildHeight()) {
                            colors[idx] = 0;
                            continue;
                        }
                        // getHeight(WORLD_SURFACE) is the topmost SOLID block's Y, so sample at y
                        // (sampling y-1 grabbed the block under the surface, e.g. dirt beneath grass).
                        BlockPos worldPos = new BlockPos((cx << 4) + lx, y, (cz << 4) + lz);
                        try {
                            colors[idx] = chunk.getBlockState(worldPos).getMapColor(world, worldPos).col & 0x00FFFFFF;
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

    private ClaimedBlockSelection findFirstClaimBlock(Inventory inventory) {
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
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

    public boolean requestRemoveClaim(ServerPlayer player, DimBlockPos pos) {
        DimChunkPos targetChunk = pos.toChunkPos();
        UUID factionID = getClaim(targetChunk);
        Faction faction = getFaction(factionID);
        if (factionID.equals(Faction.nullUuid) || faction == null) {
            player.sendSystemMessage(Component.literal("Could not find a claim in that location"));
            return false;
        }

        DimBlockPos claimPos = faction.getSpecificPosForClaim(targetChunk);
        if (claimPos != null) {
            pos = claimPos;
        }

        if (!isOp(player) && !faction.isPlayerRoleInFaction(player.getUUID(), Role.OFFICER)) {
            player.sendSystemMessage(Component.literal("You are not an officer of the faction"));
            return false;
        }

        if (pos.equals(faction.citadelPos)) {
            player.sendSystemMessage(Component.literal("Can't remove the citadel without disbanding the faction"));
            return false;
        }

        for (HashMap.Entry<DimChunkPos, Siege> siege : sieges.entrySet()) {
            if (siege.getKey().equals(targetChunk)) {
                player.sendSystemMessage(Component.literal("This claim is currently under siege"));
                return false;
            }

            if (siege.getValue().attackingCamps.contains(pos)) {
                player.sendSystemMessage(Component.literal("This siege camp is currently in a siege"));
                return false;
            }
        }

        Faction.ClaimType claimType = faction.getClaimType(targetChunk);
        ServerLevel claimWorld = MC_SERVER.getLevel(pos.dim);
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
                ServerLevel world = MC_SERVER.getLevel(collectorPos.dim);
                if (world != null) {
                    world.removeBlock(collectorPos.toRegularPos(), false);
                }
            }
        }
        faction.islandCollectors.removeAll(removedCollectors);
        WarForgeMod.CHUNK_LOADING_MANAGER.refreshFactionChunks(faction);
        if (dataOnlyClaim) {
            ItemStack refund = claimTypeToStack(claimType);
            if (!refund.isEmpty()) {
                boolean inserted = player.getInventory().add(refund);
                if (!inserted && !refund.isEmpty()) {
                    player.drop(refund, false);
                } else {
                    player.inventoryMenu.broadcastChanges();
                }
            }
        }
        faction.messageAll(Component.literal(player.getName().getString() + " unclaimed " + pos.toFancyString()));
        sendClaimChangedNotification(
                faction,
                "claim_removed_" + pos,
                "Chunk Unclaimed",
                player.getName().getString() + " unclaimed " + pos.toFancyString(),
                TOAST_WARNING,
                player.getUUID()
        );

        return true;
    }

    public boolean requestRemoveClaimByChunk(ServerPlayer player, DimChunkPos chunkPos) {
        Faction faction = getFaction(getClaim(chunkPos));
        if (faction == null) {
            player.sendSystemMessage(Component.literal("Could not find a claim in that location"));
            return false;
        }

        DimBlockPos claimPos = faction.getSpecificPosForClaim(chunkPos);
        if (claimPos == null) {
            player.sendSystemMessage(Component.literal("Could not find a claim in that location"));
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
                ServerLevel world = MC_SERVER.getLevel(collectorPos.dim);
                if (world != null) {
                    world.removeBlock(collectorPos.toRegularPos(), false);
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

    public boolean requestSetFactionColour(ServerPlayer player, int colour) {
        Faction faction = getFactionOfPlayer(player.getUUID());
        if (faction == null) {
            player.sendSystemMessage(Component.literal("You are not in a faction"));
            return false;
        }

        if (!faction.isPlayerRoleInFaction(player.getUUID(), Role.LEADER)) {
            player.sendSystemMessage(Component.literal("You are not the faction leader"));
            return false;
        }

        faction.setColour(colour);
        for (DimBlockPos claimPos : collectFactionClaimPositions(faction)) {
            ServerLevel level = MC_SERVER.getLevel(claimPos.dim);
            if (level == null) continue;
            if (level.getBlockEntity(claimPos.toRegularPos()) instanceof IClaim claim) {
                if (claim.getFaction().equals(faction.uuid)) {
                    claim.updateColour(colour);
                }
            }
        }
        // Push the new colour straight to the recolourer so a freshly reopened citadel panel shows the
        // updated accent band immediately rather than after the end-of-tick block-entity sync.
        WarForgeMod.syncClaimToPlayer(player, faction.citadelPos.toRegularPos());

        ArrayList<Player> onlinePlayers = faction.getOnlinePlayers(
                entityPlayer -> entityPlayer != null && entityPlayer.isAlive());

        onlinePlayers.forEach(entityPlayer -> {
            PacketNamePlateChange packet = new PacketNamePlateChange();
            packet.name = entityPlayer.getName().getString();
            packet.faction = faction.name;
            packet.color = colour;
            NETWORK.sendToAllAround(packet, entityPlayer.getX(), entityPlayer.getY(), entityPlayer.getZ(), 100, player.level().dimension());
        });


        return true;

    }

    public boolean requestMoveCitadel(ServerPlayer player, DimBlockPos pos) {
        Faction faction = getFactionOfPlayer(player.getUUID());
        if (faction == null) {
            player.sendSystemMessage(Component.literal("You are not in a faction"));
            return false;
        }

        if (!faction.isPlayerRoleInFaction(player.getUUID(), Role.LEADER)) {
            player.sendSystemMessage(Component.literal("You are not the faction leader"));
            return false;
        }

        for (HashMap.Entry<DimChunkPos, Siege> kvp : sieges.entrySet()) {
            if (kvp.getValue().defendingFaction.equals(faction.uuid)) {
                player.sendSystemMessage(Component.literal("There is an ongoing siege against your faction"));
                return false;
            }
        }
        if (pos == null || pos.equals(DimBlockPos.ZERO)) {
            player.sendSystemMessage(Component.literal("Look at the block face where you want to place the citadel"));
            return false;
        }
        if (!pos.dim.equals(player.level().dimension())) {
            player.sendSystemMessage(Component.literal("You can only move the citadel in your current dimension"));
            return false;
        }
        if (pos.equals(faction.citadelPos)) {
            player.sendSystemMessage(Component.literal("The citadel is already there"));
            return false;
        }

        DimChunkPos targetChunk = pos.toChunkPos();
        UUID chunkOwner = getClaim(targetChunk);
        if (!faction.uuid.equals(chunkOwner)) {
            player.sendSystemMessage(Component.literal("You must look at a position inside your claimed land"));
            return false;
        }

        if (!canPlaceMovedCitadelAt(player.level(), pos, faction.citadelPos)) {
            player.sendSystemMessage(Component.literal("Not enough free space to place the 3-block citadel pillar there"));
            return false;
        }

        if (pos.getY() < WarForgeConfig.CITADEL_MIN_Y) {
            player.sendSystemMessage(Component.literal("Citadels must be placed at or above Y " + WarForgeConfig.CITADEL_MIN_Y));
            return false;
        }

        DimChunkPos oldChunk = faction.citadelPos.toChunkPos();
        boolean movingAcrossChunks = !targetChunk.equals(oldChunk);
        long citadelMoveReadyAt = faction.citadelMoveTimeStamp + TimeHelper.getCitadelMoveCooldownMs();
        if (movingAcrossChunks && faction.citadelMoveTimeStamp > 0L && WarForgeMod.citadelMoveClock() < citadelMoveReadyAt) {
            player.sendSystemMessage(Component.literal("You can move the citadel across chunks again in " + TimeHelper.formatTime(citadelMoveReadyAt - WarForgeMod.citadelMoveClock())));
            return false;
        }

        DimBlockPos replacedClaimPos = targetChunk.equals(oldChunk) ? faction.citadelPos : faction.getSpecificPosForClaim(targetChunk);
        if (replacedClaimPos == null) {
            player.sendSystemMessage(Component.literal("That claimed chunk has no stored claim position to replace"));
            return false;
        }

        TileEntityCitadel oldCitadel = (TileEntityCitadel) MC_SERVER.getLevel(faction.citadelPos.dim).getBlockEntity(faction.citadelPos.toRegularPos());
        if (oldCitadel == null) {
            player.sendSystemMessage(Component.literal("Could not find the current citadel tile entity"));
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
        MC_SERVER.getLevel(pos.dim).setBlockAndUpdate(pos.toRegularPos(), Content.citadelBlock.defaultBlockState());
        TileEntityCitadel newCitadel = (TileEntityCitadel) MC_SERVER.getLevel(pos.dim).getBlockEntity(pos.toRegularPos());
        if (newCitadel == null) {
            player.sendSystemMessage(Component.literal("Failed to create the new citadel"));
            return false;
        }
        newCitadel.copyStorageFrom(oldCitadel);

        // Old citadel remains a normal claimed chunk but no longer has a physical claim block.
        DimBlockPos oldCitadelPos = faction.citadelPos;
        ServerLevel oldCitadelWorld = MC_SERVER.getLevel(faction.citadelPos.dim);
        oldCitadelWorld.removeBlock(faction.citadelPos.toRegularPos(), false);
        oldCitadelWorld.removeBlock(faction.citadelPos.toRegularPos().above(), false);
        oldCitadelWorld.removeBlock(faction.citadelPos.toRegularPos().above(2), false);
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
            faction.citadelMoveTimeStamp = WarForgeMod.citadelMoveClock();
        }

        INSTANCE.messageAll(Component.literal(faction.name + " moved their citadel"), true);
        return true;
    }

    private boolean canPlaceMovedCitadelAt(Level world, DimBlockPos pos, DimBlockPos oldCitadelPos) {
        if (!world.getBlockState(pos.below()).isFaceSturdy(world, pos.below(), Direction.UP)) {
            return false;
        }
        return isCitadelSpaceFree(world, pos, oldCitadelPos)
                && isCitadelSpaceFree(world, pos.above(), oldCitadelPos)
                && isCitadelSpaceFree(world, pos.above(2), oldCitadelPos);
    }

    private boolean isCitadelSpaceFree(Level world, BlockPos pos, DimBlockPos oldCitadelPos) {
        if (oldCitadelPos != null) {
            if (pos.equals(oldCitadelPos.toRegularPos()) || pos.equals(oldCitadelPos.toRegularPos().above()) || pos.equals(oldCitadelPos.toRegularPos().above(2))) {
                return true;
            }
        }
        return world.isEmptyBlock(pos) || world.getBlockState(pos).canBeReplaced();
    }

    public void readFromNBT(CompoundTag tags) {
        mFactions.clear();
        mClaims.clear();
        sieges.clear();
        redeemableInsuranceVaults.clear();

        InitNeutralZones();

        ListTag list = tags.getList("factions", Tag.TAG_COMPOUND);
        for (Tag baseTag : list) {
            CompoundTag factionTags = ((CompoundTag) baseTag);
            if (!factionTags.hasUUID("id")) {
                WarForgeMod.LOGGER.warn("Skipping faction entry with missing/invalid id while loading warforgefactions.dat");
                continue;
            }
            UUID uuid = factionTags.getUUID("id");
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

        WarForgeMod.FOBS.rebuildGlobalIndex();
        WarForgeMod.FOBS.readFromNBT(tags);

        list = tags.getList("sieges", Tag.TAG_COMPOUND);
        for (Tag baseTag : list) {
            CompoundTag siegeTags = ((CompoundTag) baseTag);
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(siegeTags.getString("dim")));
            int x = siegeTags.getInt("x");
            int z = siegeTags.getInt("z");

            Siege siege = new Siege();
            siege.ReadFromNBT(siegeTags);

            sieges.put(new DimChunkPos(dim, x, z), siege);
        }

        readConqueredChunks(tags);
        readRedeemableInsurance(tags);

        for (Siege siege : sieges.values()) {
            Faction defending = getFaction(siege.defendingFaction);
            if (defending != null) {
                defending.isCurrentlyDefending = true;
            }
        }
    }

    private void readConqueredChunks(CompoundTag tags) {
        conqueredChunks = new HashMap<>();
        CompoundTag conqueredChunksDataList = tags.getCompound("conqueredChunks");

        int index = 0;
        while (true) {
            String key = "conqueredChunk_" + index;
            if (conqueredChunksDataList.getTagType(key) != Tag.TAG_COMPOUND) {
                break; // exit on the first missing or legacy/malformed (non-compound) entry; legacy data is dropped
            }
            CompoundTag entry = conqueredChunksDataList.getCompound(key);
            try {
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(entry.getString("dim")));
                int[] chunkXZ = entry.getIntArray("pos");
                if (chunkXZ.length < 2) { ++index; continue; }
                int[] factionArr = entry.getIntArray("faction");
                if (factionArr.length < 4) { ++index; continue; }
                DimChunkPos chunkPosKey = new DimChunkPos(dim, chunkXZ[0], chunkXZ[1]);
                UUID factionID = BEIntArrayToUUID(factionArr);

                conqueredChunks.put(chunkPosKey, new ObjectIntPair<>(factionID, entry.getInt("period")));
            } catch (Exception e) {
                WarForgeMod.LOGGER.warn("Skipping malformed conqueredChunk entry at index {}", index, e);
            }
            ++index;
        }
    }

    public void WriteToNBT(CompoundTag tags) {
        WarForgeMod.FOBS.writeToNBT(tags);
        ListTag factionList = new ListTag();
        for (HashMap.Entry<UUID, Faction> kvp : mFactions.entrySet()) {
            CompoundTag factionTags = new CompoundTag();
            factionTags.putUUID("id", kvp.getKey());
            kvp.getValue().writeToNBT(factionTags);
            factionList.add(factionTags);
        }

        tags.put("factions", factionList);

        ListTag siegeList = new ListTag();
        for (HashMap.Entry<DimChunkPos, Siege> kvp : sieges.entrySet()) {
            CompoundTag siegeTags = new CompoundTag();
            siegeTags.putString("dim", kvp.getKey().dim.location().toString());
            siegeTags.putInt("x", kvp.getKey().x);
            siegeTags.putInt("z", kvp.getKey().z);
            kvp.getValue().WriteToNBT(siegeTags);
            siegeList.add(siegeTags);
        }

        tags.put("sieges", siegeList);

        writeConqueredChunks(tags);
        writeRedeemableInsurance(tags);
    }

    private void readRedeemableInsurance(CompoundTag tags) {
        ListTag payoutList = tags.getList("redeemableInsuranceVaults", Tag.TAG_COMPOUND);
        for (Tag base : payoutList) {
            CompoundTag payoutTag = (CompoundTag) base;
            UUID owner = payoutTag.getUUID("owner");
            ArrayList<ItemStack> items = new ArrayList<ItemStack>();
            ListTag itemsList = payoutTag.getList("items", Tag.TAG_COMPOUND);
            for (Tag itemBase : itemsList) {
                items.add(ItemStack.of((CompoundTag) itemBase));
            }
            redeemableInsuranceVaults.put(owner, items);
        }
    }

    private void writeRedeemableInsurance(CompoundTag tags) {
        ListTag payoutList = new ListTag();
        for (Map.Entry<UUID, ArrayList<ItemStack>> entry : redeemableInsuranceVaults.entrySet()) {
            CompoundTag payoutTag = new CompoundTag();
            payoutTag.putUUID("owner", entry.getKey());
            ListTag itemsList = new ListTag();
            for (ItemStack stack : entry.getValue()) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                CompoundTag itemTag = new CompoundTag();
                stack.save(itemTag);
                itemsList.add(itemTag);
            }
            payoutTag.put("items", itemsList);
            payoutList.add(payoutTag);
        }
        tags.put("redeemableInsuranceVaults", payoutList);
    }

    private void writeConqueredChunks(CompoundTag tags) {
        CompoundTag conqueredChunksDataList = new CompoundTag();
        int index = 0;
        for (HashMap.Entry<DimChunkPos, ObjectIntPair<UUID>> conqueredEntry : conqueredChunks.entrySet()) {
            DimChunkPos chunkPosKey = conqueredEntry.getKey();
            // dim is now a ResourceKey<Level> and can no longer share an int-array slot with x/z, so each
            // entry is a compound carrying the dim string, chunk pos, owning faction, and grace period.
            CompoundTag entry = new CompoundTag();
            entry.putString("dim", chunkPosKey.dim.location().toString());
            entry.putIntArray("pos", new int[]{chunkPosKey.x, chunkPosKey.z});
            entry.putIntArray("faction", UUIDToBEIntArray(conqueredEntry.getValue().getObj()));
            entry.putInt("period", conqueredEntry.getValue().getInteger());

            conqueredChunksDataList.put("conqueredChunk_" + index, entry);

            ++index;
        }

        tags.put("conqueredChunks", conqueredChunksDataList);
    }

    public void requestNamePlateCacheEntry(ServerPlayer playerEntity, String name) {
        PacketNamePlateChange packet = new PacketNamePlateChange();
        packet.name = name;
        ServerPlayer targetPlayer = MC_SERVER.getPlayerList().getPlayerByName(name);
        if (targetPlayer == null) {
            return; //Likely bruteforcer TODO:Kick him
        }
        if (!playerEntity.level().dimension().equals(targetPlayer.level().dimension())) {
            return; //Also sus
        }


        double dx = playerEntity.getX() - targetPlayer.getX();
        double dz = playerEntity.getZ() - targetPlayer.getZ();
        double dy = playerEntity.getY() - targetPlayer.getY();

        int viewDistanceChunks = MC_SERVER.getPlayerList().getViewDistance();
        double maxDistanceBlocks = viewDistanceChunks * 16;
        double maxDistanceSq = maxDistanceBlocks * maxDistanceBlocks;
        double distance = dx * dx + dy * dy + dz * dz;
        if (distance > maxDistanceSq) {
            WarForgeMod.LOGGER.warn(playerEntity.getName().getString() + "Made a nameplate request for player " + name + "who is" + distance + "blocks away.");
            return;
        }
        Faction faction = getFactionOfPlayer(targetPlayer.getUUID());
        if (faction == null)
            return;

        packet.faction = faction.name;
        packet.color = faction.colour;
        WarForgeMod.NETWORK.sendTo(packet, playerEntity);

    }

    public enum siegeTermination {
        WIN, LOSE, NEUTRAL
    }

    private record ClaimedBlockSelection(int slot, Faction.ClaimType claimType) {

        private void consume(Inventory inventory) {
                ItemStack stack = inventory.items.get(slot);
                stack.shrink(1);
                if (stack.isEmpty()) {
                    inventory.items.set(slot, ItemStack.EMPTY);
                }
            }
        }
}

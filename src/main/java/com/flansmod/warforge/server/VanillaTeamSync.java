package com.flansmod.warforge.server;

import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VanillaTeamSync {
    private static final String TEAM_PREFIX = "wf_";

    private VanillaTeamSync() {
    }

    public static void syncAllTeams() {
        ServerScoreboard scoreboard = scoreboard();
        if (scoreboard == null) {
            return;
        }
        if (!WarForgeConfig.VANILLA_TEAM_SYNC) {
            removeManagedTeams(scoreboard);
            return;
        }

        Map<String, String> desiredAssignments = new HashMap<>();
        Set<String> desiredTeams = new HashSet<>();
        for (Faction faction : WarForgeMod.FACTIONS.getAllFactions()) {
            if (faction == null || FactionStorage.IsNeutralZone(faction.uuid)) {
                continue;
            }
            String teamName = teamNameFor(faction.uuid);
            for (UUID memberId : faction.members.keySet()) {
                String scoreboardName = scoreboardName(memberId);
                if (scoreboardName != null) {
                    desiredAssignments.put(scoreboardName, teamName);
                    desiredTeams.add(teamName);
                }
            }
        }

        for (Map.Entry<String, String> entry : desiredAssignments.entrySet()) {
            PlayerTeam team = ensureTeam(scoreboard, entry.getValue());
            if (scoreboard.getPlayersTeam(entry.getKey()) != team) {
                scoreboard.addPlayerToTeam(entry.getKey(), team);
            }
        }

        for (PlayerTeam team : new ArrayList<>(scoreboard.getPlayerTeams())) {
            if (!isManaged(team)) {
                continue;
            }
            if (!desiredTeams.contains(team.getName())) {
                scoreboard.removePlayerTeam(team);
                continue;
            }
            for (String member : new ArrayList<>(team.getPlayers())) {
                if (looksLikeEntityId(member)) {
                    continue;
                }
                if (!team.getName().equals(desiredAssignments.get(member))) {
                    scoreboard.removePlayerFromTeam(member, team);
                }
            }
        }
    }

    public static void syncPlayer(UUID playerId) {
        ServerScoreboard scoreboard = scoreboard();
        if (scoreboard == null || !WarForgeConfig.VANILLA_TEAM_SYNC) {
            return;
        }
        String scoreboardName = scoreboardName(playerId);
        if (scoreboardName == null) {
            return;
        }
        Faction faction = WarForgeMod.FACTIONS.getFactionOfPlayer(playerId);
        if (faction == null || FactionStorage.IsNeutralZone(faction.uuid)) {
            removeFromManagedTeam(scoreboard, scoreboardName);
            return;
        }
        PlayerTeam team = ensureTeam(scoreboard, teamNameFor(faction.uuid));
        if (scoreboard.getPlayersTeam(scoreboardName) != team) {
            scoreboard.addPlayerToTeam(scoreboardName, team);
        }
    }

    public static void removePlayer(UUID playerId) {
        ServerScoreboard scoreboard = scoreboard();
        if (scoreboard == null) {
            return;
        }
        String scoreboardName = scoreboardName(playerId);
        if (scoreboardName != null) {
            removeFromManagedTeam(scoreboard, scoreboardName);
        }
    }

    private static void removeFromManagedTeam(ServerScoreboard scoreboard, String scoreboardName) {
        PlayerTeam team = scoreboard.getPlayersTeam(scoreboardName);
        if (team != null && isManaged(team)) {
            scoreboard.removePlayerFromTeam(scoreboardName, team);
        }
    }

    private static PlayerTeam ensureTeam(ServerScoreboard scoreboard, String teamName) {
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
            team.setAllowFriendlyFire(true);
        }
        return team;
    }

    private static void removeManagedTeams(ServerScoreboard scoreboard) {
        for (PlayerTeam team : new ArrayList<>(scoreboard.getPlayerTeams())) {
            if (isManaged(team)) {
                scoreboard.removePlayerTeam(team);
            }
        }
    }

    private static String teamNameFor(UUID factionId) {
        return TEAM_PREFIX + clusterId(factionId).toString().replace("-", "");
    }

    private static UUID clusterId(UUID factionId) {
        UUID canonical = factionId;
        Set<UUID> visited = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>();
        visited.add(factionId);
        queue.add(factionId);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (current.compareTo(canonical) < 0) {
                canonical = current;
            }
            Faction faction = WarForgeMod.FACTIONS.getFaction(current);
            if (faction == null) {
                continue;
            }
            for (UUID allyId : faction.allies) {
                if (WarForgeMod.FACTIONS.getFaction(allyId) != null && visited.add(allyId)) {
                    queue.add(allyId);
                }
            }
        }
        return canonical;
    }

    private static boolean isManaged(PlayerTeam team) {
        return team.getName().startsWith(TEAM_PREFIX);
    }

    private static boolean looksLikeEntityId(String entry) {
        if (entry.length() != 36) {
            return false;
        }
        try {
            UUID.fromString(entry);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String scoreboardName(UUID playerId) {
        MinecraftServer server = WarForgeMod.MC_SERVER;
        if (server == null) {
            return null;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            return online.getScoreboardName();
        }
        GameProfile profile = server.getProfileCache() == null
                ? null
                : server.getProfileCache().get(playerId).orElse(null);
        return profile != null ? profile.getName() : null;
    }

    private static ServerScoreboard scoreboard() {
        MinecraftServer server = WarForgeMod.MC_SERVER;
        return server == null ? null : server.getScoreboard();
    }
}

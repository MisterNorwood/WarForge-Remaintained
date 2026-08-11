package com.flansmod.warforge.common.util;

import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.server.Faction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * Helpers for showing a player's faction as a coloured prefix in chat and the tab list.
 * <p>
 * Chat is rewritten through {@code ServerChatEvent}; the tab list display name is provided by a mixin
 * on {@code ServerPlayer#getTabListDisplayName} and pushed to clients with {@link #refreshTabName}.
 */
public final class FactionDisplay {
    private FactionDisplay() {
    }

    /** The "[FactionName] " prefix coloured to the faction's RGB colour, or {@code null} if the player has no faction. */
    public static MutableComponent factionPrefix(Faction faction) {
        if (faction == null || faction.uuid.equals(Faction.nullUuid) || faction.name == null || faction.name.isEmpty()) {
            return null;
        }
        return Component.literal("[" + faction.name + "] ")
                .withStyle(style -> style.withColor(TextColor.fromRgb(faction.colour)));
    }

    /** The tab-list display name "[FactionName] PlayerName", or {@code null} to fall back to the vanilla name. */
    public static Component tabName(Faction faction, String playerName) {
        MutableComponent prefix = factionPrefix(faction);
        if (prefix == null) {
            return null;
        }
        return Component.literal("").append(prefix).append(Component.literal(playerName));
    }

    /** Re-send a player's tab-list display name to everyone so faction prefix changes show up live. */
    public static void refreshTabName(UUID playerId) {
        // When the tab prefix is disabled the display name is always the vanilla name, so there is
        // nothing to push and we must not reveal any association to clients.
        if (!WarForgeConfig.FACTION_PREFIX_IN_TABLIST) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || playerId == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.refreshTabListName();
        }
    }

    /** Convenience: refresh the tab names of every online member of a faction (e.g. after a rename). */
    public static void refreshFactionTabNames(Faction faction) {
        if (faction == null) {
            return;
        }
        for (UUID memberId : faction.members.keySet()) {
            refreshTabName(memberId);
        }
    }
}

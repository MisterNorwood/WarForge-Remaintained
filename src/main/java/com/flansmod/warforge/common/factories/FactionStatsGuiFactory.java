package com.flansmod.warforge.common.factories;

import com.flansmod.warforge.client.ui.FactionStatsScreen;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketFactionInfo;
import com.flansmod.warforge.server.Faction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.UUID;

public final class FactionStatsGuiFactory {
    public static final FactionStatsGuiFactory INSTANCE = new FactionStatsGuiFactory();

    private FactionStatsGuiFactory() {
    }

    public static void init() {
    }

    @OnlyIn(Dist.CLIENT)
    public void openClient(UUID factionId) {
        FactionStatsScreen.open(factionId);
    }

    @OnlyIn(Dist.CLIENT)
    public void openClientChild(Runnable reopenParent, UUID factionId) {
        FactionStatsScreen.open(factionId);
    }

    @OnlyIn(Dist.CLIENT)
    public void openClientSibling(UUID factionId) {
        FactionStatsScreen.open(factionId);
    }

    public void open(Player player, UUID factionId) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        UUID effectiveFactionId = factionId;
        if (effectiveFactionId.equals(Faction.nullUuid)) {
            Faction playerFaction = WarForgeMod.FACTIONS.getFactionOfPlayer(serverPlayer.getUUID());
            effectiveFactionId = playerFaction == null ? Faction.nullUuid : playerFaction.uuid;
        }
        Faction faction = WarForgeMod.FACTIONS.getFaction(effectiveFactionId);
        if (faction != null) {
            PacketFactionInfo packet = new PacketFactionInfo();
            packet.info = faction.createInfo();
            WarForgeMod.NETWORK.sendTo(packet, serverPlayer);
        }
    }
}

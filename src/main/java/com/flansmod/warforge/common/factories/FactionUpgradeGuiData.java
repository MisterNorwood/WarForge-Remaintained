package com.flansmod.warforge.common.factories;

import com.flansmod.warforge.server.Faction;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class FactionUpgradeGuiData {
    public UUID requestedFactionId = Faction.nullUuid;
    public UUID factionId = Faction.nullUuid;
    public String factionName = "";
    public int level;
    public int color = 0xFFFFFF;
    public boolean outrankingOfficer;

    public FactionUpgradeGuiData(Player player, UUID requestedFactionId) {
        this.requestedFactionId = requestedFactionId;
    }
}

package com.flansmod.warforge.common.factories;

import com.flansmod.warforge.server.Faction;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.UUID;

public class FactionInsuranceGuiData {
    public UUID requestedFactionId = Faction.nullUuid;
    public boolean hasFaction;
    public UUID factionId = Faction.nullUuid;
    public String factionName = "";
    public int factionColor = 0xFFFFFF;
    public boolean canDeposit;
    public boolean canVoid;
    public int slotCount;
    public IItemHandlerModifiable insuranceHandler;

    public FactionInsuranceGuiData(Player player, UUID requestedFactionId) {
        this.requestedFactionId = requestedFactionId;
    }
}

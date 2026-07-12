package com.flansmod.warforge.common.factories;

import brachy.modularui.factory.GuiData;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.server.Faction;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OperationsGuiData extends GuiData {
    public boolean hasFaction;
    public UUID factionId = Faction.nullUuid;
    public String factionName = "";
    public int factionColor = 0x4E8E87;
    public int warpTicketCost = 1;
    public final List<SiegeEntry> sieges = new ArrayList<>();
    public final List<FobEntry> fobs = new ArrayList<>();

    public static class SiegeEntry {
        public String attackerName = "";
        public String defenderName = "";
        public int attackerColor = 0xFFFFFF;
        public int defenderColor = 0xFFFFFF;
        public int chunkX;
        public int chunkZ;
        public String dimName = "";
        public boolean attacking;
    }

    public static class FobEntry {
        public DimBlockPos pos = DimBlockPos.ZERO;
        public String name = "";
        public int tickets;
        public int maxTickets;
        public boolean occupied;
        public boolean canWarp;
    }

    public OperationsGuiData(Player player) {
        super(player);
    }
}

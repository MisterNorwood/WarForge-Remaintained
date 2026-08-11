package com.flansmod.warforge.common.factories;

import com.flansmod.warforge.common.util.DimBlockPos;
import net.minecraft.world.entity.player.Player;

public class FobGuiData {
    public final DimBlockPos pos;
    public boolean established;
    public String name = "";
    public int tickets;
    public int maxTickets;
    public boolean canEstablish;
    public boolean canWarp;
    public int factionColor = 0x4A4A4A;

    public FobGuiData(Player player, DimBlockPos pos) {
        this.pos = pos;
    }
}

package com.flansmod.warforge.common.factories;

import brachy.modularui.factory.GuiData;
import com.flansmod.warforge.common.util.DimBlockPos;
import net.minecraft.world.entity.player.Player;

public class CreateFactionGuiData extends GuiData {
    public DimBlockPos citadelPos;
    public int colour;
    public boolean isRecolour;

    public CreateFactionGuiData(Player player, DimBlockPos citadelPos, int colour, boolean isRecolour) {
        super(player);
        this.citadelPos = citadelPos;
        this.colour = colour;
        this.isRecolour = isRecolour;
    }
}

package com.flansmod.warforge.common.factories;

import com.flansmod.warforge.common.util.DimBlockPos;
import net.minecraft.world.entity.player.Player;

public class CreateFactionGuiData {
    public DimBlockPos citadelPos;
    public int colour;
    public boolean isRecolour;

    public CreateFactionGuiData(Player player, DimBlockPos citadelPos, int colour, boolean isRecolour) {
        this.citadelPos = citadelPos;
        this.colour = colour;
        this.isRecolour = isRecolour;
    }
}

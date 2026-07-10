package com.flansmod.warforge.common.network;

import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.server.Faction;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
public class SiegeCampProgressInfo {
    public DimBlockPos defendingPos;
    public DimBlockPos attackingPos;
    public ArrayList<ChunkPos> warzoneChunks;
    public int attackingColour;
    public int defendingColour;
    public String attackingName;
    public String defendingName;
    public int battleRadius = 1;
    public int siegedRadius = 0;
    public UUID defendingFactionId = Faction.nullUuid;
    public UUID attackingFactionId = Faction.nullUuid;
    public int attackerAbandonSeconds = 0;

    public int completionPoint = 5;
    public int mPreviousProgress = 0;
    public int progress = 0;
    public long maxTime = 0;
    public long timeProgress = 0;
    public long endTimestamp = Long.MAX_VALUE;

    public int expiredTicks = 0;
    public boolean finished = false;

    public static SiegeCampProgressInfo getDebugInfo() {
        SiegeCampProgressInfo info = new SiegeCampProgressInfo();
        info.defendingPos = new DimBlockPos(0, 100, 64, 100);
        info.attackingPos = new DimBlockPos(0, 120, 64, 100);
        info.attackingColour = 0xFF0000;
        info.defendingColour = 0x0000FF;
        info.attackingName = "Red Team";
        info.defendingName = "Blue Team";
        info.battleRadius = 2;
        info.completionPoint = 10;
        info.progress = 6;
        info.mPreviousProgress = 4;
        info.expiredTicks = 20;
        info.maxTime = 1800;
        info.timeProgress = info.maxTime/2;

        return info;
    }


    public void ClientTick() {
        if ((progress <= -5 || progress >= completionPoint) || finished)  {
            expiredTicks++;
        }
    }

    public boolean Completed() {
        return expiredTicks >= 100;
    }
}

package com.flansmod.warforge.common.network;

import com.flansmod.warforge.client.JourneyMapClaimCache;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client claim-border updates for the optional JourneyMap overlay.
 * <p>
 * Carries only chunk position + faction colour (never faction names/ids/members). The server decides
 * what each client is allowed to receive based on {@code JOURNEYMAP_CLAIM_MODE}, so a client cannot
 * learn about claims it is not entitled to see.
 */
public class PacketJourneyMapClaims extends PacketBase {
    /** When true the client wipes its claim cache before applying this packet (used for a full snapshot). */
    public boolean clear = false;
    /** Each entry: {dim, chunkX, chunkZ, colour}. */
    public final List<int[]> sets = new ArrayList<>();
    /** Each entry: {dim, chunkX, chunkZ}. */
    public final List<int[]> removes = new ArrayList<>();

    public void addSet(int dim, int x, int z, int colour) {
        sets.add(new int[]{dim, x, z, colour});
    }

    public void addRemove(int dim, int x, int z) {
        removes.add(new int[]{dim, x, z});
    }

    public boolean isEmpty() {
        return !clear && sets.isEmpty() && removes.isEmpty();
    }

    @Override
    public void encodeInto(ChannelHandlerContext ctx, ByteBuf data) {
        data.writeBoolean(clear);
        data.writeInt(sets.size());
        for (int[] s : sets) {
            data.writeInt(s[0]);
            data.writeInt(s[1]);
            data.writeInt(s[2]);
            data.writeInt(s[3]);
        }
        data.writeInt(removes.size());
        for (int[] r : removes) {
            data.writeInt(r[0]);
            data.writeInt(r[1]);
            data.writeInt(r[2]);
        }
    }

    @Override
    public void decodeInto(ChannelHandlerContext ctx, ByteBuf data) {
        clear = data.readBoolean();
        int setCount = data.readInt();
        for (int i = 0; i < setCount; i++) {
            sets.add(new int[]{data.readInt(), data.readInt(), data.readInt(), data.readInt()});
        }
        int removeCount = data.readInt();
        for (int i = 0; i < removeCount; i++) {
            removes.add(new int[]{data.readInt(), data.readInt(), data.readInt()});
        }
    }

    @Override
    public void handleServerSide(EntityPlayerMP playerEntity) {
        // client-bound only
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClientSide(EntityPlayer clientPlayer) {
        if (clear) {
            JourneyMapClaimCache.applyClear();
        }
        for (int[] s : sets) {
            JourneyMapClaimCache.set(s[0], s[1], s[2], s[3]);
        }
        for (int[] r : removes) {
            JourneyMapClaimCache.remove(r[0], r[1], r[2]);
        }
    }
}

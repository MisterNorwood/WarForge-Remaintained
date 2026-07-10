package com.flansmod.warforge.common.network;

import com.flansmod.warforge.client.JourneyMapVeinCache;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client vein updates for the optional JourneyMap overlay.
 * <p>
 * Carries only chunk position + the compressed vein id/quality short (the same encoding the existing
 * vein HUD uses); the client resolves the icon from its already-synced vein definitions. The server
 * decides what each client may receive based on {@code JOURNEYMAP_VEIN_MODE}, so a client cannot learn
 * about veins it is not entitled to see.
 */
public class PacketJourneyMapVeins extends PacketBase {
    /** When true the client wipes its vein cache before applying this packet. */
    public boolean clear = false;
    /** Each entry: {dim, chunkX, chunkZ, compressedVeinInfo}. */
    public final List<int[]> sets = new ArrayList<>();
    /** Each entry: {dim, chunkX, chunkZ}. */
    public final List<int[]> removes = new ArrayList<>();

    public void addSet(int dim, int x, int z, short veinInfo) {
        sets.add(new int[]{dim, x, z, veinInfo});
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
            data.writeShort(s[3]);
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
            sets.add(new int[]{data.readInt(), data.readInt(), data.readInt(), data.readShort()});
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
            JourneyMapVeinCache.applyClear();
        }
        for (int[] s : sets) {
            JourneyMapVeinCache.set(s[0], s[1], s[2], (short) s[3]);
        }
        for (int[] r : removes) {
            JourneyMapVeinCache.remove(r[0], r[1], r[2]);
        }
    }
}

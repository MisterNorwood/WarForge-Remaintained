package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.api.vein.Vein;
import com.flansmod.warforge.common.WarForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;

import static com.flansmod.warforge.client.ClientProxy.VEIN_ENTRIES;

public class PacketVeinEntries extends PacketBase {
    public static final CustomPacketPayload.Type<PacketVeinEntries> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetveinentries"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketVeinEntries> STREAM_CODEC =
        StreamCodec.ofMember(PacketVeinEntries::encodeInto, buf -> { PacketVeinEntries p = new PacketVeinEntries(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    private ArrayList<Vein> veinBuffer = new ArrayList<>();
    private int byteCount = 0;

    private static final int maxPacketByteCount = 1024;

    public int fillFrom(ArrayList<Vein> veins, int startIndex) {
        while (startIndex < veins.size() && tryAddVein(veins.get(startIndex))) { ++startIndex; }
        return startIndex;
    }

    public boolean tryAddVein(Vein veinToAdd) {
        if (byteCount > 0 && veinToAdd.SERIALIZED_ENTRY.readableBytes() + byteCount > maxPacketByteCount) { return false; }

        veinBuffer.add(veinToAdd);
        byteCount += veinToAdd.SERIALIZED_ENTRY.readableBytes();
        return true;
    }

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        for (Vein vein : veinBuffer) {
            final int veinBufBytes = vein.SERIALIZED_ENTRY.readableBytes();
            data.writeBytes(vein.SERIALIZED_ENTRY, 0, veinBufBytes);
        }
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        while (data.readableBytes() > 0) {
            veinBuffer.add(new Vein(data));
        }
    }

    @Override
    public void handleServerSide(ServerPlayer playerEntity) {
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
        for (Vein vein : veinBuffer) {
            VEIN_ENTRIES.put(vein.getId(), vein);
            WarForgeMod.LOGGER.atDebug().log("Received vein of id <" + vein.getId() + "> with data: \n" + vein);
        }
    }
}

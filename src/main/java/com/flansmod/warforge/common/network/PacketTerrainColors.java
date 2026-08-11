package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.client.ServerTerrainCache;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class PacketTerrainColors extends PacketBase {
    public static final CustomPacketPayload.Type<PacketTerrainColors> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetterraincolors"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketTerrainColors> STREAM_CODEC =
        StreamCodec.ofMember(PacketTerrainColors::encodeInto, buf -> { PacketTerrainColors p = new PacketTerrainColors(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public ResourceKey<Level> dim = Level.OVERWORLD;
    public int centerX;
    public int centerZ;
    public int radius;
    public final List<int[]> chunkCoords = new ArrayList<>();
    public final List<int[]> colors = new ArrayList<>();
    public final List<int[]> heights = new ArrayList<>();

    public void addChunk(int chunkX, int chunkZ, int[] chunkColors, int[] chunkHeights) {
        chunkCoords.add(new int[]{chunkX, chunkZ});
        colors.add(chunkColors);
        heights.add(chunkHeights);
    }

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        data.writeUtf(dim.location().toString());
        data.writeInt(centerX);
        data.writeInt(centerZ);
        data.writeByte(radius);
        data.writeInt(chunkCoords.size());
        for (int i = 0; i < chunkCoords.size(); i++) {
            int[] coord = chunkCoords.get(i);
            data.writeInt(coord[0]);
            data.writeInt(coord[1]);
            int[] chunkColors = colors.get(i);
            int[] chunkHeights = heights.get(i);
            for (int c = 0; c < 256; c++) {
                data.writeMedium(chunkColors[c] & 0x00FFFFFF);
            }
            for (int h = 0; h < 256; h++) {
                data.writeShort(chunkHeights[h]);
            }
        }
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(data.readUtf()));
        centerX = data.readInt();
        centerZ = data.readInt();
        radius = data.readByte();
        int count = data.readInt();
        for (int i = 0; i < count; i++) {
            int cx = data.readInt();
            int cz = data.readInt();
            int[] chunkColors = new int[256];
            int[] chunkHeights = new int[256];
            for (int c = 0; c < 256; c++) {
                chunkColors[c] = data.readUnsignedMedium();
            }
            for (int h = 0; h < 256; h++) {
                chunkHeights[h] = data.readShort();
            }
            addChunk(cx, cz, chunkColors, chunkHeights);
        }
    }

    @Override
    public void handleServerSide(ServerPlayer playerEntity) {
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
        for (int i = 0; i < chunkCoords.size(); i++) {
            int[] coord = chunkCoords.get(i);
            ServerTerrainCache.put(dim, coord[0], coord[1], colors.get(i), heights.get(i));
        }
    }
}

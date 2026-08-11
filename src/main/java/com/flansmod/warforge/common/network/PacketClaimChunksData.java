package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.api.vein.Quality;
import com.flansmod.warforge.client.ClientBorderCache;
import com.flansmod.warforge.client.ClientClaimChunkCache;
import com.flansmod.warforge.client.ClientProxy;
import com.flansmod.warforge.client.ClientTickHandler;
import com.flansmod.warforge.server.Faction;
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
import java.util.UUID;

public class PacketClaimChunksData extends PacketBase {
    public static final CustomPacketPayload.Type<PacketClaimChunksData> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetclaimchunksdata"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketClaimChunksData> STREAM_CODEC =
        StreamCodec.ofMember(PacketClaimChunksData::encodeInto, buf -> { PacketClaimChunksData p = new PacketClaimChunksData(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public ResourceKey<Level> dim = Level.OVERWORLD;
    public int centerX;
    public int centerZ;
    public int radius;
    public UUID playerFactionId = Faction.nullUuid;
    public int forceLoadedCount;
    public int forceLoadedMax;
    public int claimCount;
    public int claimMax;
    public boolean outlineOnly = false;
    public List<ClaimChunkInfo> chunks = new ArrayList<ClaimChunkInfo>();

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        data.writeUtf(dim.location().toString());
        data.writeInt(centerX);
        data.writeInt(centerZ);
        data.writeByte(radius);
        data.writeBoolean(outlineOnly);
        writeUUID(data, playerFactionId);
        data.writeShort(forceLoadedCount);
        data.writeShort(forceLoadedMax);
        data.writeShort(claimCount);
        data.writeShort(claimMax);

        data.writeShort(chunks.size());
        for (ClaimChunkInfo chunk : chunks) {
            data.writeInt(chunk.x);
            data.writeInt(chunk.z);
            writeUUID(data, chunk.factionId);
            writeUTF(data, chunk.factionName);
            writeUTF(data, chunk.flagId);
            data.writeInt(chunk.colour);
            writeUTF(data, chunk.claimType.serializedName);
            data.writeShort(chunk.vein != null ? chunk.vein.getId() : -1);
            data.writeByte(chunk.oreQuality != null ? chunk.oreQuality.ordinal() : -1);
            data.writeByte(chunk.flags);
            writeUUID(data, chunk.outlineFactionId);
            data.writeInt(chunk.outlineColour);
            data.writeByte(chunk.outlineStyle);
            data.writeInt(chunk.conqueredRemainingMs);
        }
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(data.readUtf()));
        centerX = data.readInt();
        centerZ = data.readInt();
        radius = data.readByte();
        outlineOnly = data.readBoolean();
        playerFactionId = readUUID(data);
        forceLoadedCount = data.readShort();
        forceLoadedMax = data.readShort();
        claimCount = data.readShort();
        claimMax = data.readShort();

        chunks.clear();
        int size = data.readShort();
        for (int i = 0; i < size; i++) {
            ClaimChunkInfo info = new ClaimChunkInfo();
            info.x = data.readInt();
            info.z = data.readInt();
            info.factionId = readUUID(data);
            info.factionName = readUTF(data);
            info.flagId = readUTF(data);
            info.colour = data.readInt();
            info.claimType = Faction.ClaimType.fromSerialized(readUTF(data));
            short veinId = data.readShort();
            info.vein = veinId < 0 ? null : ClientProxy.VEIN_ENTRIES.get(veinId);
            int qualityOrdinal = data.readByte();
            info.oreQuality = qualityOrdinal < 0 ? null : Quality.values()[qualityOrdinal];
            info.flags = data.readByte();
            info.outlineFactionId = readUUID(data);
            info.outlineColour = data.readInt();
            info.outlineStyle = data.readByte();
            info.conqueredRemainingMs = data.readInt();
            chunks.add(info);
        }
    }

    @Override
    public void handleServerSide(ServerPlayer playerEntity) {
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
        if (outlineOnly) {
            ClientBorderCache.replaceAll(dim, chunks);
            ClientTickHandler.CLAIMS_DIRTY = true;
            return;
        }
        ClientClaimChunkCache.replaceAll(dim, centerX, centerZ, radius, playerFactionId, forceLoadedCount, forceLoadedMax, claimCount, claimMax, chunks);
        ClientTickHandler.CLAIMS_DIRTY = true;
        com.flansmod.warforge.client.ui.ClaimManagerScreen.refreshIfOpen();
    }
}

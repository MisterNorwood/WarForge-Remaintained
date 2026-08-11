package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimChunkPos;
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

public class PacketClaimChunkAction extends PacketBase {
    public static final CustomPacketPayload.Type<PacketClaimChunkAction> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetclaimchunkaction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketClaimChunkAction> STREAM_CODEC =
        StreamCodec.ofMember(PacketClaimChunkAction::encodeInto, buf -> { PacketClaimChunkAction p = new PacketClaimChunkAction(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final byte ACTION_CLAIM = 0;
    public static final byte ACTION_UNCLAIM = 1;
    public static final byte ACTION_TOGGLE_FORCELOAD = 2;

    public DimChunkPos chunk = new DimChunkPos(Level.OVERWORLD, 0, 0);
    public DimChunkPos center = new DimChunkPos(Level.OVERWORLD, 0, 0);
    public int radius = 4;
    public byte action = ACTION_CLAIM;

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        data.writeUtf(chunk.dim.location().toString());
        data.writeInt(chunk.x);
        data.writeInt(chunk.z);
        data.writeByte(action);
        data.writeUtf(center.dim.location().toString());
        data.writeInt(center.x);
        data.writeInt(center.z);
        data.writeByte(radius);
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        ResourceKey<Level> chunkDim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(data.readUtf()));
        chunk = new DimChunkPos(chunkDim, data.readInt(), data.readInt());
        action = data.readByte();
        ResourceKey<Level> centerDim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(data.readUtf()));
        center = new DimChunkPos(centerDim, data.readInt(), data.readInt());
        radius = data.readByte();
    }

    @Override
    public void handleServerSide(ServerPlayer playerEntity) {
        switch (action) {
            case ACTION_CLAIM:
                WarForgeMod.FACTIONS.requestClaimChunkNoTile(playerEntity, chunk);
                break;
            case ACTION_UNCLAIM:
                WarForgeMod.FACTIONS.requestRemoveClaimByChunk(playerEntity, chunk);
                break;
            case ACTION_TOGGLE_FORCELOAD:
                WarForgeMod.FACTIONS.requestToggleForceLoad(playerEntity, chunk);
                break;
            default:
                break;
        }

        int clampedRadius = Math.max(1, Math.min(radius, WarForgeConfig.CLAIM_MANAGER_RADIUS));
        WarForgeMod.FACTIONS.sendClaimChunks(playerEntity, center, clampedRadius);
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
    }
}

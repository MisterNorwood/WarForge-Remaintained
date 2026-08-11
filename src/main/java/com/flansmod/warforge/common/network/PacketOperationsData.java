package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.OperationsGuiData;
import com.flansmod.warforge.common.factories.OperationsGuiFactory;
import com.flansmod.warforge.common.util.DimBlockPos;
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

public class PacketOperationsData extends PacketBase {
    public static final CustomPacketPayload.Type<PacketOperationsData> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetoperationsdata"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketOperationsData> STREAM_CODEC =
        StreamCodec.ofMember(PacketOperationsData::encodeInto, buf -> { PacketOperationsData p = new PacketOperationsData(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static PacketOperationsData latest = null;

    public boolean hasFaction = false;
    public UUID factionId = Faction.nullUuid;
    public String factionName = "";
    public int factionColor = 0x4E8E87;
    public int warpTicketCost = 1;
    public final List<OperationsGuiData.SiegeEntry> sieges = new ArrayList<>();
    public final List<OperationsGuiData.FobEntry> fobs = new ArrayList<>();

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        data.writeBoolean(hasFaction);
        writeUUID(data, factionId);
        writeUTF(data, factionName);
        data.writeInt(factionColor);
        data.writeInt(warpTicketCost);

        data.writeShort(sieges.size());
        for (OperationsGuiData.SiegeEntry siege : sieges) {
            writeUTF(data, siege.attackerName);
            writeUTF(data, siege.defenderName);
            data.writeInt(siege.attackerColor);
            data.writeInt(siege.defenderColor);
            data.writeInt(siege.chunkX);
            data.writeInt(siege.chunkZ);
            writeUTF(data, siege.dimName);
            data.writeBoolean(siege.attacking);
        }

        data.writeShort(fobs.size());
        for (OperationsGuiData.FobEntry fob : fobs) {
            writeUTF(data, fob.pos.dim.location().toString());
            data.writeInt(fob.pos.getX());
            data.writeInt(fob.pos.getY());
            data.writeInt(fob.pos.getZ());
            writeUTF(data, fob.name);
            data.writeInt(fob.tickets);
            data.writeInt(fob.maxTickets);
            data.writeBoolean(fob.occupied);
            data.writeBoolean(fob.canWarp);
        }
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        hasFaction = data.readBoolean();
        factionId = readUUID(data);
        factionName = readUTF(data);
        factionColor = data.readInt();
        warpTicketCost = data.readInt();

        int siegeCount = data.readShort();
        for (int i = 0; i < siegeCount; i++) {
            OperationsGuiData.SiegeEntry siege = new OperationsGuiData.SiegeEntry();
            siege.attackerName = readUTF(data);
            siege.defenderName = readUTF(data);
            siege.attackerColor = data.readInt();
            siege.defenderColor = data.readInt();
            siege.chunkX = data.readInt();
            siege.chunkZ = data.readInt();
            siege.dimName = readUTF(data);
            siege.attacking = data.readBoolean();
            sieges.add(siege);
        }

        int fobCount = data.readShort();
        for (int i = 0; i < fobCount; i++) {
            OperationsGuiData.FobEntry fob = new OperationsGuiData.FobEntry();
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(readUTF(data)));
            int x = data.readInt();
            int y = data.readInt();
            int z = data.readInt();
            fob.pos = new DimBlockPos(dim, x, y, z);
            fob.name = readUTF(data);
            fob.tickets = data.readInt();
            fob.maxTickets = data.readInt();
            fob.occupied = data.readBoolean();
            fob.canWarp = data.readBoolean();
            fobs.add(fob);
        }
    }

    @Override
    public void handleServerSide(ServerPlayer player) {
        WarForgeMod.LOGGER.error("Received a PacketOperationsData on server side");
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
        latest = this;
        OperationsGuiFactory.INSTANCE.openOperationsScreen();
    }
}

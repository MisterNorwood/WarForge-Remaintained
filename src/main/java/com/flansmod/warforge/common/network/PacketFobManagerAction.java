package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.TileEntityFob;
import com.flansmod.warforge.common.factories.FactionMemberManagerGuiData;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.fob.Fob;

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
import net.minecraft.world.level.block.entity.BlockEntity;

public class PacketFobManagerAction extends PacketBase {
    public static final CustomPacketPayload.Type<PacketFobManagerAction> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetfobmanageraction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketFobManagerAction> STREAM_CODEC =
        StreamCodec.ofMember(PacketFobManagerAction::encodeInto, buf -> { PacketFobManagerAction p = new PacketFobManagerAction(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public Action action = Action.WARP;
    public DimBlockPos target = DimBlockPos.ZERO;
    public String name = "";
    public FactionMemberManagerGuiData.Page page = FactionMemberManagerGuiData.Page.ALLIANCES;

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        data.writeByte(action.ordinal());
        writeUTF(data, target.dim.location().toString());
        data.writeInt(target.getX());
        data.writeInt(target.getY());
        data.writeInt(target.getZ());
        writeUTF(data, name);
        data.writeByte(page.ordinal());
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        int actionOrd = Byte.toUnsignedInt(data.readByte());
        action = actionOrd < Action.values().length ? Action.values()[actionOrd] : Action.values()[0];
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(readUTF(data, 256)));
        int x = data.readInt();
        int y = data.readInt();
        int z = data.readInt();
        target = new DimBlockPos(dim, x, y, z);
        name = readUTF(data, 32);
        int pageOrd = Byte.toUnsignedInt(data.readByte());
        page = pageOrd < FactionMemberManagerGuiData.Page.values().length ? FactionMemberManagerGuiData.Page.values()[pageOrd] : FactionMemberManagerGuiData.Page.values()[0];
    }

    @Override
    public void handleServerSide(ServerPlayer playerEntity) {
        switch (action) {
            case ESTABLISH -> handleEstablish(playerEntity);
            case WARP -> handleWarp(playerEntity);
        }
    }

    private boolean handleEstablish(ServerPlayer playerEntity) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        if (name.trim().length() > 32) {
            return false;
        }

        Faction faction = WarForgeMod.FACTIONS.getFactionOfPlayer(playerEntity.getUUID());
        if (faction == null) {
            return false;
        }

        if (!faction.isPlayerRoleInFaction(playerEntity.getUUID(), Faction.Role.OFFICER)) {
            return false;
        }

        BlockEntity te = playerEntity.level().getBlockEntity(target.toRegularPos());
        if (te instanceof TileEntityFob) {
            WarForgeMod.FOBS.requestCreateFob((TileEntityFob) te, playerEntity, name);
            return true;
        }
        return false;
    }

    private boolean handleWarp(ServerPlayer playerEntity) {
        Fob fob = WarForgeMod.FOBS.getFobAt(target.toChunkPos());
        if (fob == null) {
            return false;
        }
        WarForgeMod.FOBS.requestFobWarp(playerEntity, fob);
        return true;
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
    }

    public enum Action {
        ESTABLISH,
        WARP
    }
}

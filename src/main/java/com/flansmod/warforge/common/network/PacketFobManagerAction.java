package com.flansmod.warforge.common.network;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.TileEntityFob;
import com.flansmod.warforge.common.factories.FactionMemberManagerGuiData;
import com.flansmod.warforge.common.factories.FactionMemberManagerGuiFactory;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.fob.Fob;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class PacketFobManagerAction extends PacketBase {
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
        action = Action.values()[data.readByte()];
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(readUTF(data)));
        int x = data.readInt();
        int y = data.readInt();
        int z = data.readInt();
        target = new DimBlockPos(dim, x, y, z);
        name = readUTF(data);
        page = FactionMemberManagerGuiData.Page.values()[data.readByte()];
    }

    @Override
    public void handleServerSide(ServerPlayer playerEntity) {
        switch (action) {
            case ESTABLISH -> handleEstablish(playerEntity);
            case WARP -> handleWarp(playerEntity);
        }
        FactionMemberManagerGuiFactory.INSTANCE.open(playerEntity, page);
    }

    private void handleEstablish(ServerPlayer playerEntity) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }

        Faction faction = WarForgeMod.FACTIONS.getFactionOfPlayer(playerEntity.getUUID());
        if (faction == null) {
            return;
        }

        if (!faction.isPlayerRoleInFaction(playerEntity.getUUID(), Faction.Role.OFFICER)) {
            return;
        }

        BlockEntity te = playerEntity.level().getBlockEntity(target.toRegularPos());
        if (te instanceof TileEntityFob) {
            WarForgeMod.FOBS.requestCreateFob((TileEntityFob) te, playerEntity, name);
        }
    }

    private void handleWarp(ServerPlayer playerEntity) {
        Fob fob = WarForgeMod.FOBS.getFobAt(target.toChunkPos());
        if (fob == null) {
            return;
        }
        WarForgeMod.FOBS.requestFobWarp(playerEntity, fob);
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
    }

    public enum Action {
        ESTABLISH,
        WARP
    }
}

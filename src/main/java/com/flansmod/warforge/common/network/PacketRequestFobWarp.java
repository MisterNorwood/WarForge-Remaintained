package com.flansmod.warforge.common.network;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.server.fob.Fob;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class PacketRequestFobWarp extends PacketBase {
    public DimBlockPos mPos;

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        writeUTF(data, mPos.dim.location().toString());
        data.writeInt(mPos.getX());
        data.writeInt(mPos.getY());
        data.writeInt(mPos.getZ());
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(readUTF(data, 256)));
        int x = data.readInt();
        int y = data.readInt();
        int z = data.readInt();
        mPos = new DimBlockPos(dim, x, y, z);
    }

    @Override
    public void handleServerSide(ServerPlayer playerEntity) {
        Fob fob = WarForgeMod.FOBS.getFobAt(mPos.toChunkPos());
        if (fob == null) {
            return;
        }
        WarForgeMod.FOBS.requestFobWarp(playerEntity, fob);
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
        WarForgeMod.LOGGER.error("Recieved FOB warp request on client");
    }
}

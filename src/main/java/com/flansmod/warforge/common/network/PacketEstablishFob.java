package com.flansmod.warforge.common.network;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.TileEntityFob;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.server.Faction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class PacketEstablishFob extends PacketBase {
    public DimBlockPos mPos;
    public String mName = "";

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        writeUTF(data, mPos.dim.location().toString());
        data.writeInt(mPos.getX());
        data.writeInt(mPos.getY());
        data.writeInt(mPos.getZ());
        writeUTF(data, mName);
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(readUTF(data)));
        int x = data.readInt();
        int y = data.readInt();
        int z = data.readInt();
        mPos = new DimBlockPos(dim, x, y, z);
        mName = readUTF(data);
    }

    @Override
    public void handleServerSide(ServerPlayer playerEntity) {
        if (!playerEntity.level().dimension().equals(mPos.dim)) {
            WarForgeMod.LOGGER.error("Player requested establishing a FOB in the wrong dim");
            return;
        }

        if (mName == null || mName.trim().isEmpty()) {
            return;
        }

        Faction faction = WarForgeMod.FACTIONS.getFactionOfPlayer(playerEntity.getUUID());
        if (faction == null) {
            return;
        }

        if (!faction.isPlayerRoleInFaction(playerEntity.getUUID(), Faction.Role.OFFICER)) {
            return;
        }

        BlockEntity te = playerEntity.level().getBlockEntity(mPos.toRegularPos());
        if (te instanceof TileEntityFob) {
            WarForgeMod.FOBS.requestCreateFob((TileEntityFob) te, playerEntity, mName);
        }
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
        WarForgeMod.LOGGER.error("Recieved establish FOB message on client");
    }
}

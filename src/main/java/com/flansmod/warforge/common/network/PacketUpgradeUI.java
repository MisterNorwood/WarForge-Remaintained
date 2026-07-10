package com.flansmod.warforge.common.network;

import com.cleanroommc.modularui.factory.ClientGUI;
import com.flansmod.warforge.client.GUIUpgradePanel;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.Tags;
import com.flansmod.warforge.server.Faction;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.UUID;

public class PacketUpgradeUI extends PacketBase {

    public UUID mFactionID = Faction.nullUuid;
    public String mFactionName = "";
    int level = 0;
    int color = 0xffff;
    boolean outrankingOfficer = false;


    @Override
    public void encodeInto(ChannelHandlerContext ctx, ByteBuf data) {
        writeUUID(data, mFactionID);
        writeUTF(data, mFactionName);
        data.writeInt(level);
        data.writeInt(color);
        data.writeBoolean(outrankingOfficer);

    }

    @Override
    public void decodeInto(ChannelHandlerContext ctx, ByteBuf data) {
        mFactionID = readUUID(data);
        mFactionName = readUTF(data);
        level = data.readInt();
        color = data.readInt();
        outrankingOfficer = data.readBoolean();
    }

    @Override
    public void handleServerSide(EntityPlayerMP playerEntity) {

        WarForgeMod.LOGGER.error("Received a Upgrade UI packet on serverside");

    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClientSide(EntityPlayer clientPlayer) {
        ClientGUI.open(GUIUpgradePanel.createGui(
                mFactionID,
                mFactionName,
                level,
                color,
                outrankingOfficer
                ));
    }
}

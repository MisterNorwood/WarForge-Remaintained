package com.flansmod.warforge.common.network;

import com.flansmod.warforge.client.util.WarForgeNotifications;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.UUID;

public class PacketClientNotification extends PacketBase {
    public static final int COLOR_INFO    = 0x708A97;
    public static final int COLOR_SUCCESS = 0x55AA55;
    public static final int COLOR_WARNING = 0xC79A3A;
    public static final int COLOR_DANGER  = 0xB34747;

    public String token = "";
    public String title = "";
    public String subtitle = "";
    public int accentColor = 0x708A97;
    public int durationMs = 5000;
    public UUID playerId = null;

    public PacketClientNotification() {
    }

    public PacketClientNotification(String token, String title, String subtitle, int accentColor, int durationMs, UUID playerId) {
        this.token = token;
        this.title = title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.accentColor = accentColor;
        this.durationMs = durationMs;
        this.playerId = playerId;
    }

    @Override
    public void encodeInto(ChannelHandlerContext ctx, ByteBuf data) {
        writeUTF(data, token);
        writeUTF(data, title);
        writeUTF(data, subtitle);
        data.writeInt(accentColor);
        data.writeInt(durationMs);
        data.writeBoolean(playerId != null);
        if (playerId != null) {
            writeUUID(data, playerId);
        }
    }

    @Override
    public void decodeInto(ChannelHandlerContext ctx, ByteBuf data) {
        token = readUTF(data);
        title = readUTF(data);
        subtitle = readUTF(data);
        accentColor = data.readInt();
        durationMs = data.readInt();
        playerId = data.readBoolean() ? readUUID(data) : null;
    }

    @Override
    public void handleServerSide(EntityPlayerMP playerEntity) {
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClientSide(EntityPlayer clientPlayer) {
        WarForgeNotifications.show(token, title, subtitle.isEmpty() ? null : subtitle, accentColor, durationMs, playerId);
    }
}

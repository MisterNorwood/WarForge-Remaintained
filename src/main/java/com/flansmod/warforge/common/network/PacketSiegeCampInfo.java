package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.api.vein.Quality;
import com.flansmod.warforge.client.ClientProxy;
import com.flansmod.warforge.common.factories.SiegeCampGuiFactory;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.server.Faction;
import net.minecraft.core.Vec3i;
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

public class PacketSiegeCampInfo extends PacketBase {
    public static final CustomPacketPayload.Type<PacketSiegeCampInfo> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetsiegecampinfo"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSiegeCampInfo> STREAM_CODEC =
        StreamCodec.ofMember(PacketSiegeCampInfo::encodeInto, buf -> { PacketSiegeCampInfo p = new PacketSiegeCampInfo(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static PacketSiegeCampInfo lastReceived = null;

    public DimBlockPos mSiegeCampPos;
    public List<SiegeCampAttackInfo> mPossibleAttacks = new ArrayList<>();
    public byte momentum;
    public int color;

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        data.writeUtf(mSiegeCampPos.dim.location().toString());
        data.writeInt(mSiegeCampPos.getX());
        data.writeInt(mSiegeCampPos.getY());
        data.writeInt(mSiegeCampPos.getZ());

        data.writeByte(mPossibleAttacks.size());

        for (SiegeCampAttackInfo info : mPossibleAttacks) {
            data.writeBoolean(info.canAttack);
            writeUUID(data, info.mFactionUUID);
            writeUTF(data, info.mFactionName);
            data.writeByte(info.mOffset.getX());
            data.writeByte(info.mOffset.getZ());
            data.writeInt(info.mFactionColour);
            data.writeShort(info.mWarforgeVein != null ? info.mWarforgeVein.getId() : -1);
            writeUTF(data, info.claimType.serializedName);

            byte oreQualOrd = 0;
            if (info.mOreQuality != null) { oreQualOrd = (byte) info.mOreQuality.ordinal(); }
            data.writeByte(oreQualOrd);

            data.writeBoolean(info.conquered);
        }

        data.writeByte(momentum);
        data.writeInt(color);
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(data.readUtf()));
        int x = data.readInt();
        int y = data.readInt();
        int z = data.readInt();
        mSiegeCampPos = new DimBlockPos(dim, x, y, z);

        int numAttacks = data.readByte();
        mPossibleAttacks.clear();
        for (int i = 0; i < numAttacks; i++) {
            SiegeCampAttackInfo info = new SiegeCampAttackInfo();

            info.canAttack = data.readBoolean();
            info.mFactionUUID = readUUID(data);
            info.mFactionName = readUTF(data);
            int dx = data.readByte();
            int dz = data.readByte();
            info.mOffset = new Vec3i(dx, 0, dz);
            info.mFactionColour = data.readInt();

            short possibleVein = data.readShort();
            info.mWarforgeVein = possibleVein < 0 ? null : ClientProxy.VEIN_ENTRIES.get(possibleVein);
            info.claimType = Faction.ClaimType.fromSerialized(readUTF(data));
            info.mOreQuality = Quality.values()[data.readByte()];
            info.conquered = data.readBoolean();

            mPossibleAttacks.add(info);
        }
        momentum = data.readByte();
        color = data.readInt();

    }

    @Override
    public void handleServerSide(ServerPlayer playerEntity) {
        WarForgeMod.LOGGER.error("Received a siege info packet server side");
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
        lastReceived = this;
        SiegeCampGuiFactory.INSTANCE.openClient(this);
    }
}

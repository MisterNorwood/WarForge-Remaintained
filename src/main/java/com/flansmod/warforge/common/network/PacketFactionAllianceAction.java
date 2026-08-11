package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.FactionMemberManagerGuiData;
import com.flansmod.warforge.server.Faction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class PacketFactionAllianceAction extends PacketBase {
    public static final CustomPacketPayload.Type<PacketFactionAllianceAction> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetfactionallianceaction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketFactionAllianceAction> STREAM_CODEC =
        StreamCodec.ofMember(PacketFactionAllianceAction::encodeInto, buf -> { PacketFactionAllianceAction p = new PacketFactionAllianceAction(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public enum Action {
        INVITE,
        ACCEPT,
        DECLINE,
        BREAK,
        TOGGLE_ALLY_BUILD
    }

    public Action action = Action.INVITE;
    public UUID target = Faction.nullUuid;
    public FactionMemberManagerGuiData.Page page = FactionMemberManagerGuiData.Page.ALLIANCES;

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        data.writeByte(action.ordinal());
        writeUUID(data, target);
        data.writeByte(page.ordinal());
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        action = Action.values()[data.readByte()];
        target = readUUID(data);
        page = FactionMemberManagerGuiData.Page.values()[data.readByte()];
    }

    @Override
    public void handleServerSide(ServerPlayer playerEntity) {
        switch (action) {
            case INVITE -> WarForgeMod.FACTIONS.requestInviteAlly(playerEntity, target);
            case ACCEPT -> WarForgeMod.FACTIONS.requestAcceptAlliance(playerEntity, target);
            case DECLINE -> WarForgeMod.FACTIONS.requestDeclineAlliance(playerEntity, target);
            case BREAK -> WarForgeMod.FACTIONS.requestBreakAlliance(playerEntity, target);
            case TOGGLE_ALLY_BUILD -> WarForgeMod.FACTIONS.requestToggleAllyInteraction(playerEntity);
        }
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
    }
}

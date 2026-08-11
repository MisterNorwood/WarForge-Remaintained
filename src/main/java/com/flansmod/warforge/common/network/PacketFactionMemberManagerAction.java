package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.FactionMemberManagerGuiData;
import com.flansmod.warforge.server.Faction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class PacketFactionMemberManagerAction extends PacketBase {
    public static final CustomPacketPayload.Type<PacketFactionMemberManagerAction> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetfactionmembermanageraction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketFactionMemberManagerAction> STREAM_CODEC =
        StreamCodec.ofMember(PacketFactionMemberManagerAction::encodeInto, buf -> { PacketFactionMemberManagerAction p = new PacketFactionMemberManagerAction(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public enum Action {
        PROMOTE,
        DEMOTE,
        KICK_OR_LEAVE,
        TRANSFER_LEADER,
        INVITE,
        ACCEPT_INVITE
    }

    public Action action = Action.INVITE;
    public UUID target = Faction.nullUuid;
    public FactionMemberManagerGuiData.Page page = FactionMemberManagerGuiData.Page.MEMBERS;

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
            case PROMOTE -> {
                ServerPlayer promoteTarget = WarForgeMod.MC_SERVER.getPlayerList().getPlayer(target);
                if (promoteTarget == null) {
                    playerEntity.sendSystemMessage(Component.literal("That player must be online to be promoted"));
                } else {
                    WarForgeMod.FACTIONS.requestPromote(playerEntity, promoteTarget);
                }
            }
            case DEMOTE -> {
                ServerPlayer demoteTarget = WarForgeMod.MC_SERVER.getPlayerList().getPlayer(target);
                if (demoteTarget == null) {
                    playerEntity.sendSystemMessage(Component.literal("That player must be online to be demoted"));
                } else {
                    WarForgeMod.FACTIONS.requestDemote(playerEntity, demoteTarget);
                }
            }
            case KICK_OR_LEAVE -> {
                Faction faction = WarForgeMod.FACTIONS.getFactionOfPlayer(playerEntity.getUUID());
                if (faction != null) {
                    WarForgeMod.FACTIONS.requestRemovePlayerFromFaction(playerEntity.createCommandSourceStack(), faction.uuid, target);
                }
            }
            case TRANSFER_LEADER -> {
                Faction faction = WarForgeMod.FACTIONS.getFactionOfPlayer(playerEntity.getUUID());
                if (faction != null) {
                    WarForgeMod.FACTIONS.RequestTransferLeadership(playerEntity, faction.uuid, target);
                }
            }
            case INVITE -> WarForgeMod.FACTIONS.requestInvitePlayerToMyFaction(playerEntity, target);
            case ACCEPT_INVITE -> WarForgeMod.FACTIONS.RequestAcceptInvite(playerEntity, target);
        }
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
    }
}

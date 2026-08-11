package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.server.Faction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class PacketFactionInsuranceAction extends PacketBase {
    public static final CustomPacketPayload.Type<PacketFactionInsuranceAction> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetfactioninsuranceaction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketFactionInsuranceAction> STREAM_CODEC =
        StreamCodec.ofMember(PacketFactionInsuranceAction::encodeInto, buf -> { PacketFactionInsuranceAction p = new PacketFactionInsuranceAction(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public int slot = -1;

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        data.writeInt(slot);
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        slot = data.readInt();
    }

    @Override
    public void handleServerSide(ServerPlayer playerEntity) {
        Faction faction = WarForgeMod.FACTIONS.getFactionOfPlayer(playerEntity.getUUID());
        if (faction == null) {
            return;
        }
        if (!WarForgeMod.isOp(playerEntity) && !faction.isPlayerRoleInFaction(playerEntity.getUUID(), Faction.Role.LEADER)) {
            playerEntity.sendSystemMessage(Component.literal("Only the faction leader can void insurance items"));
            return;
        }
        if (slot < 0 || slot >= faction.getInsuranceSlotCount()) {
            return;
        }
        ItemStack existing = faction.getInsuranceStack(slot);
        if (!existing.isEmpty()) {
            faction.setInsuranceStack(slot, ItemStack.EMPTY);
        }
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
    }
}

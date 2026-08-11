package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.server.Faction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PacketRequestInsurance extends PacketBase {

    public static final CustomPacketPayload.Type<PacketRequestInsurance> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetrequestinsurance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRequestInsurance> STREAM_CODEC =
        StreamCodec.ofMember(PacketRequestInsurance::encodeInto, buf -> { PacketRequestInsurance p = new PacketRequestInsurance(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public UUID factionId = Faction.nullUuid;

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        writeUUID(data, factionId);
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        factionId = readUUID(data);
    }

    @Override
    public void handleServerSide(ServerPlayer player) {
        UUID effectiveId = factionId;
        if (effectiveId.equals(Faction.nullUuid)) {
            Faction playerFaction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUUID());
            effectiveId = playerFaction == null ? Faction.nullUuid : playerFaction.uuid;
        }

        Faction faction = WarForgeMod.FACTIONS.getFaction(effectiveId);
        PacketInsuranceData response = new PacketInsuranceData();

        if (faction == null) {
            WarForgeMod.NETWORK.sendTo(response, player);
            return;
        }

        response.hasFaction = true;
        response.factionId = faction.uuid;
        response.factionName = faction.name;
        response.factionColor = faction.colour;
        response.canWithdraw = WarForgeMod.isOp(player)
                || faction.isPlayerRoleInFaction(player.getUUID(), Faction.Role.LEADER);

        int slotCount = faction.getInsuranceSlotCount();
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            stacks.add(faction.getInsuranceStack(i));
        }
        response.stacks = stacks;

        WarForgeMod.NETWORK.sendTo(response, player);
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
        WarForgeMod.LOGGER.error("Received PacketRequestInsurance on client side");
    }
}

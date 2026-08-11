package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Tags.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class WFNetwork {
    private WFNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        reg(r, PacketCreateFaction.TYPE, PacketCreateFaction.STREAM_CODEC);
        reg(r, PacketOpenCreateFaction.TYPE, PacketOpenCreateFaction.STREAM_CODEC);
        reg(r, PacketOpenFlagSelect.TYPE, PacketOpenFlagSelect.STREAM_CODEC);
        reg(r, PacketOpenRecolour.TYPE, PacketOpenRecolour.STREAM_CODEC);
        reg(r, PacketRequestFactionInfo.TYPE, PacketRequestFactionInfo.STREAM_CODEC);
        reg(r, PacketFactionInfo.TYPE, PacketFactionInfo.STREAM_CODEC);
        reg(r, PacketSiegeCampInfo.TYPE, PacketSiegeCampInfo.STREAM_CODEC);
        reg(r, PacketSiegeCampProgressUpdate.TYPE, PacketSiegeCampProgressUpdate.STREAM_CODEC);
        reg(r, PacketStartSiege.TYPE, PacketStartSiege.STREAM_CODEC);
        reg(r, PacketLeaderboardInfo.TYPE, PacketLeaderboardInfo.STREAM_CODEC);
        reg(r, PacketRequestLeaderboardInfo.TYPE, PacketRequestLeaderboardInfo.STREAM_CODEC);
        reg(r, PacketDisbandFaction.TYPE, PacketDisbandFaction.STREAM_CODEC);
        reg(r, PacketRemoveClaim.TYPE, PacketRemoveClaim.STREAM_CODEC);
        reg(r, PacketTimeUpdates.TYPE, PacketTimeUpdates.STREAM_CODEC);
        reg(r, PacketSetFactionColour.TYPE, PacketSetFactionColour.STREAM_CODEC);
        reg(r, PacketClientNotification.TYPE, PacketClientNotification.STREAM_CODEC);
        reg(r, PacketMoveCitadel.TYPE, PacketMoveCitadel.STREAM_CODEC);
        reg(r, PacketCitadelUpgradeRequirement.TYPE, PacketCitadelUpgradeRequirement.STREAM_CODEC);
        reg(r, PacketRequestUpgradeUI.TYPE, PacketRequestUpgradeUI.STREAM_CODEC);
        reg(r, PacketUpgradeUI.TYPE, PacketUpgradeUI.STREAM_CODEC);
        reg(r, PacketRequestUpgrade.TYPE, PacketRequestUpgrade.STREAM_CODEC);
        reg(r, PacketChooseFactionFlag.TYPE, PacketChooseFactionFlag.STREAM_CODEC);
        reg(r, PacketFlagManifest.TYPE, PacketFlagManifest.STREAM_CODEC);
        reg(r, PacketFlagChunk.TYPE, PacketFlagChunk.STREAM_CODEC);
        reg(r, PacketEffect.TYPE, PacketEffect.STREAM_CODEC);
        reg(r, PacketNamePlateChange.TYPE, PacketNamePlateChange.STREAM_CODEC);
        reg(r, PacketRequestNamePlate.TYPE, PacketRequestNamePlate.STREAM_CODEC);
        reg(r, PacketChunkPosVeinID.TYPE, PacketChunkPosVeinID.STREAM_CODEC);
        reg(r, PacketVeinEntries.TYPE, PacketVeinEntries.STREAM_CODEC);
        reg(r, PacketSyncConfig.TYPE, PacketSyncConfig.STREAM_CODEC);
        reg(r, PacketRequestClaimChunks.TYPE, PacketRequestClaimChunks.STREAM_CODEC);
        reg(r, PacketClaimChunksData.TYPE, PacketClaimChunksData.STREAM_CODEC);
        reg(r, PacketClaimChunkAction.TYPE, PacketClaimChunkAction.STREAM_CODEC);
        reg(r, PacketFactionMemberManagerAction.TYPE, PacketFactionMemberManagerAction.STREAM_CODEC);
        reg(r, PacketRequestMemberData.TYPE, PacketRequestMemberData.STREAM_CODEC);
        reg(r, PacketMemberData.TYPE, PacketMemberData.STREAM_CODEC);
        reg(r, PacketFactionInsuranceAction.TYPE, PacketFactionInsuranceAction.STREAM_CODEC);
        reg(r, PacketRequestInsurance.TYPE, PacketRequestInsurance.STREAM_CODEC);
        reg(r, PacketInsuranceData.TYPE, PacketInsuranceData.STREAM_CODEC);
        reg(r, PacketJourneyMapClaims.TYPE, PacketJourneyMapClaims.STREAM_CODEC);
        reg(r, PacketJourneyMapVeins.TYPE, PacketJourneyMapVeins.STREAM_CODEC);
        reg(r, PacketDeclareSiege.TYPE, PacketDeclareSiege.STREAM_CODEC);
        reg(r, PacketRequestTerrainColors.TYPE, PacketRequestTerrainColors.STREAM_CODEC);
        reg(r, PacketTerrainColors.TYPE, PacketTerrainColors.STREAM_CODEC);
        reg(r, PacketFactionAllianceAction.TYPE, PacketFactionAllianceAction.STREAM_CODEC);
        reg(r, PacketEstablishFob.TYPE, PacketEstablishFob.STREAM_CODEC);
        reg(r, PacketRequestFobWarp.TYPE, PacketRequestFobWarp.STREAM_CODEC);
        reg(r, PacketFobManagerAction.TYPE, PacketFobManagerAction.STREAM_CODEC);
        reg(r, PacketRequestOperations.TYPE, PacketRequestOperations.STREAM_CODEC);
        reg(r, PacketOperationsData.TYPE, PacketOperationsData.STREAM_CODEC);
    }

    private static <T extends PacketBase> void reg(PayloadRegistrar r, CustomPacketPayload.Type<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        r.playBidirectional(type, codec, WFNetwork::handle);
    }

    private static void handle(PacketBase payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow() == PacketFlow.SERVERBOUND) {
                payload.handleServerSide((ServerPlayer) context.player());
            } else {
                payload.handleClientSide(context.player());
            }
        });
    }
}

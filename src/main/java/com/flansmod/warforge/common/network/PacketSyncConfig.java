package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.client.ClientProxy;
import com.flansmod.warforge.client.JourneyMapClaimCache;
import com.flansmod.warforge.client.JourneyMapVeinCache;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class PacketSyncConfig extends PacketBase {
    public static final CustomPacketPayload.Type<PacketSyncConfig> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetsyncconfig"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSyncConfig> STREAM_CODEC =
        StreamCodec.ofMember(PacketSyncConfig::encodeInto, buf -> { PacketSyncConfig p = new PacketSyncConfig(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static final int MAX_TOML_LENGTH = 1 << 20;

    public String configToml = "";
    public short megachunkLength;
    public int maxMomentum;

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        data.writeUtf(configToml, MAX_TOML_LENGTH);
        data.writeShort(megachunkLength);
        data.writeVarInt(maxMomentum);
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        configToml = data.readUtf(MAX_TOML_LENGTH);
        megachunkLength = data.readShort();
        maxMomentum = data.readVarInt();
    }

    @Override
    public void handleServerSide(ServerPlayer playerEntity) {
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
        try {
            WarForgeConfig.applySyncedConfig(configToml);
        } catch (Exception e) {
            WarForgeMod.LOGGER.error("Failed to apply synced config from server", e);
            return;
        }

        ClientProxy.megachunkLength = megachunkLength;
        WarForgeConfig.SIEGE_MOMENTUM_MAX = (byte) maxMomentum;
        ClientProxy.VEIN_ENTRIES.clear();

        JourneyMapClaimCache.applyClear();
        JourneyMapVeinCache.applyClear();

        WarForgeMod.LOGGER.info("Applied synced WarForge config from server (momentum="
                + WarForgeConfig.SIEGE_MOMENTUM_TIME + ")");
    }
}

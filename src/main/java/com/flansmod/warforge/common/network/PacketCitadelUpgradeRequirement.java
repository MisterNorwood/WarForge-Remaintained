package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.server.ItemMatcher;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

public class PacketCitadelUpgradeRequirement extends PacketBase {

    public static final CustomPacketPayload.Type<PacketCitadelUpgradeRequirement> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetcitadelupgraderequirement"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketCitadelUpgradeRequirement> STREAM_CODEC =
        StreamCodec.ofMember(PacketCitadelUpgradeRequirement::encodeInto, buf -> { PacketCitadelUpgradeRequirement p = new PacketCitadelUpgradeRequirement(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public int level;
    public HashMap<ItemMatcher, Integer> requirements;
    public int limit;
    public int insuranceSlots;
    public int loadedChunks;

    public PacketCitadelUpgradeRequirement(int level, HashMap<ItemMatcher, Integer> requirements, int limit, int insuranceSlots, int loadedChunks) {
        this.level = level;
        this.requirements = requirements;
        this.limit = limit;
        this.insuranceSlots = insuranceSlots;
        this.loadedChunks = loadedChunks;
    }

    public PacketCitadelUpgradeRequirement() {
    }

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        data.writeInt(level);
        data.writeInt(limit);
        data.writeInt(insuranceSlots);
        data.writeInt(loadedChunks);
        data.writeVarInt(requirements.size());
        for (Map.Entry<ItemMatcher, Integer> entry : requirements.entrySet()) {
            entry.getKey().write(data);
            data.writeVarInt(entry.getValue());
        }
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        level = data.readInt();
        limit = data.readInt();
        insuranceSlots = data.readInt();
        loadedChunks = data.readInt();
        requirements = new HashMap<>();

        int count = data.readVarInt();
        for (int i = 0; i < count; i++) {
            ItemMatcher matcher = ItemMatcher.read(data);
            int amount = data.readVarInt();
            if (matcher != null) {
                requirements.put(matcher, amount);
            }
        }
    }

    @Override
    public void handleServerSide(ServerPlayer player) {
        WarForgeMod.LOGGER.error("Received level requirement info on server");
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
        WarForgeMod.UPGRADE_HANDLER.setLevelAndLimits(level, requirements, limit, insuranceSlots, loadedChunks);
    }

}

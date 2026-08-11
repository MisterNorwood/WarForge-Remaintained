package com.flansmod.warforge.common.network;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.client.JourneyMapVeinCache;
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

/**
 * Server -> client vein updates for the optional JourneyMap overlay.
 * <p>
 * Carries only chunk position + the compressed vein id/quality short (the same encoding the existing
 * vein HUD uses); the client resolves the icon from its already-synced vein definitions. The server
 * decides what each client may receive based on {@code JOURNEYMAP_VEIN_MODE}, so a client cannot learn
 * about veins it is not entitled to see.
 */
public class PacketJourneyMapVeins extends PacketBase {
    public static final CustomPacketPayload.Type<PacketJourneyMapVeins> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "packetjourneymapveins"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketJourneyMapVeins> STREAM_CODEC =
        StreamCodec.ofMember(PacketJourneyMapVeins::encodeInto, buf -> { PacketJourneyMapVeins p = new PacketJourneyMapVeins(); p.decodeInto(buf); return p; });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final class Set {
        public final ResourceKey<Level> dim;
        public final int x, z;
        public final short veinInfo;
        public Set(ResourceKey<Level> dim, int x, int z, short veinInfo) { this.dim = dim; this.x = x; this.z = z; this.veinInfo = veinInfo; }
    }

    public static final class Remove {
        public final ResourceKey<Level> dim;
        public final int x, z;
        public Remove(ResourceKey<Level> dim, int x, int z) { this.dim = dim; this.x = x; this.z = z; }
    }

    /** When true the client wipes its vein cache before applying this packet. */
    public boolean clear = false;
    public final List<Set> sets = new ArrayList<>();
    public final List<Remove> removes = new ArrayList<>();

    public void addSet(ResourceKey<Level> dim, int x, int z, short veinInfo) {
        sets.add(new Set(dim, x, z, veinInfo));
    }

    public void addRemove(ResourceKey<Level> dim, int x, int z) {
        removes.add(new Remove(dim, x, z));
    }

    public boolean isEmpty() {
        return !clear && sets.isEmpty() && removes.isEmpty();
    }

    @Override
    public void encodeInto(FriendlyByteBuf data) {
        data.writeBoolean(clear);
        data.writeInt(sets.size());
        for (Set s : sets) {
            data.writeUtf(s.dim.location().toString());
            data.writeInt(s.x);
            data.writeInt(s.z);
            data.writeShort(s.veinInfo);
        }
        data.writeInt(removes.size());
        for (Remove r : removes) {
            data.writeUtf(r.dim.location().toString());
            data.writeInt(r.x);
            data.writeInt(r.z);
        }
    }

    @Override
    public void decodeInto(FriendlyByteBuf data) {
        clear = data.readBoolean();
        int setCount = data.readInt();
        for (int i = 0; i < setCount; i++) {
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(data.readUtf()));
            sets.add(new Set(dim, data.readInt(), data.readInt(), data.readShort()));
        }
        int removeCount = data.readInt();
        for (int i = 0; i < removeCount; i++) {
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(data.readUtf()));
            removes.add(new Remove(dim, data.readInt(), data.readInt()));
        }
    }

    @Override
    public void handleServerSide(ServerPlayer playerEntity) {
        // client-bound only
    }

    @Override
    public void handleClientSide(Player clientPlayer) {
        if (clear) {
            JourneyMapVeinCache.applyClear();
        }
        for (Set s : sets) {
            JourneyMapVeinCache.set(s.dim, s.x, s.z, s.veinInfo);
        }
        for (Remove r : removes) {
            JourneyMapVeinCache.remove(r.dim, r.x, r.z);
        }
    }
}

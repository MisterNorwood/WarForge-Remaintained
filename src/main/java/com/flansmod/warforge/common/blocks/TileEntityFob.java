package com.flansmod.warforge.common.blocks;

import com.flansmod.warforge.common.Content;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.fob.Fob;
import com.flansmod.warforge.server.fob.FobPresence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class TileEntityFob extends BlockEntity {
    public UUID ownerFaction = new UUID(0, 0);
    public UUID placer = new UUID(0, 0);
    public String name = "";
    public int tickets = 0;
    public int maxTickets = 0;
    public String factionFlagId = "";
    public float poleLength = TileEntityClaim.DEFAULT_POLE_LENGTH;
    private DimChunkPos cachedChunkPos;

    public TileEntityFob(BlockPos pos, BlockState state) {
        super(Content.TE_FOB.get(), pos, state);
    }

    public float getPoleLength() {
        return poleLength;
    }

    public void setPoleLength(float length) {
        poleLength = length;
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            setChanged();
        }
    }

    public void tick() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (ownerFaction.equals(Faction.nullUuid)) {
            return;
        }

        if (cachedChunkPos == null) cachedChunkPos = new DimChunkPos(level.dimension(), getBlockPos());
        Fob fob = WarForgeMod.FOBS.getFobAt(cachedChunkPos);
        if (fob == null) {
            return;
        }

        boolean changed = false;
        if (tickets != fob.tickets || maxTickets != fob.maxTickets) {
            tickets = fob.tickets;
            maxTickets = fob.maxTickets;
            changed = true;
        }

        Faction owner = WarForgeMod.FACTIONS.getFaction(ownerFaction);
        String ownerFlag = owner == null ? "" : owner.flagId;
        if (!factionFlagId.equals(ownerFlag)) {
            factionFlagId = ownerFlag;
            changed = true;
        }

        if (changed) {
            setChanged();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }

        int threshold = WarForgeMod.FOBS.getSiegeHoldThresholdTicks(fob);
        if (threshold <= 0) {
            if (fob.enemyHoldTicks > 0) {
                fob.enemyHoldTicks--;
            }
            return;
        }

        boolean enemyHeld = FobPresence.enemyHeld(fob);

        if (enemyHeld && !FobPresence.ownerOrAllyAbove(fob)) {
            fob.enemyHoldTicks++;
            if (fob.enemyHoldTicks >= threshold) {
                WarForgeMod.FOBS.destroyFob(fob);
            }
        } else if (fob.enemyHoldTicks > 0) {
            fob.enemyHoldTicks--;
        }
    }

    @Override
    public void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        nbt.putUUID("ownerFaction", ownerFaction);
        nbt.putUUID("placer", placer);
        nbt.putString("name", name);
        nbt.putInt("tickets", tickets);
        nbt.putInt("maxTickets", maxTickets);
        nbt.putString("flagId", factionFlagId);
        nbt.putFloat("poleLength", poleLength);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        ownerFaction = nbt.hasUUID("ownerFaction") ? nbt.getUUID("ownerFaction") : Faction.nullUuid;
        placer = nbt.hasUUID("placer") ? nbt.getUUID("placer") : Faction.nullUuid;
        name = nbt.getString("name");
        tickets = nbt.getInt("tickets");
        maxTickets = nbt.getInt("maxTickets");
        factionFlagId = nbt.getString("flagId");
        poleLength = nbt.contains("poleLength") ? nbt.getFloat("poleLength") : TileEntityClaim.DEFAULT_POLE_LENGTH;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tags = super.getUpdateTag(registries);
        tags.putUUID("ownerFaction", ownerFaction);
        tags.putString("name", name);
        tags.putInt("tickets", tickets);
        tags.putInt("maxTickets", maxTickets);
        tags.putString("flagId", factionFlagId);
        tags.putFloat("poleLength", poleLength);
        return tags;
    }

    @Override
    public void handleUpdateTag(CompoundTag tags, HolderLookup.Provider registries) {
        ownerFaction = tags.hasUUID("ownerFaction") ? tags.getUUID("ownerFaction") : Faction.nullUuid;
        name = tags.getString("name");
        tickets = tags.getInt("tickets");
        maxTickets = tags.getInt("maxTickets");
        factionFlagId = tags.getString("flagId");
        poleLength = tags.contains("poleLength") ? tags.getFloat("poleLength") : TileEntityClaim.DEFAULT_POLE_LENGTH;
    }
}

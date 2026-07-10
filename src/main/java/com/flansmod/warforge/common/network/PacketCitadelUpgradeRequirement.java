package com.flansmod.warforge.common.network;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.Tags;
import com.flansmod.warforge.server.StackComparable;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;

import java.util.HashMap;

public class PacketCitadelUpgradeRequirement extends PacketBase {

    public int level;
    public HashMap<StackComparable, Integer> requirements;
    public int limit;
    public int insuranceSlots;
    public int loadedChunks;

    public PacketCitadelUpgradeRequirement(int level, HashMap<StackComparable, Integer> requirements, int limit, int insuranceSlots, int loadedChunks) {
        this.level = level;
        this.requirements = requirements;
        this.limit = limit;
        this.insuranceSlots = insuranceSlots;
        this.loadedChunks = loadedChunks;
    }


    @SuppressWarnings("unused") //Used in reflection
    public PacketCitadelUpgradeRequirement() {
    }

    @Override
    public void encodeInto(ChannelHandlerContext ctx, ByteBuf data) {
        data.writeInt(level);
        data.writeInt(limit);
        data.writeInt(insuranceSlots);
        data.writeInt(loadedChunks);
        for (StackComparable stack : requirements.keySet()) {
            writeUTF(data, stack.writeToNBT().toString());
            data.writeInt(requirements.get(stack));
        }

    }

    @Override
    public void decodeInto(ChannelHandlerContext ctx, ByteBuf data) {
        level = data.readInt();
        limit = data.readInt();
        insuranceSlots = data.readInt();
        loadedChunks = data.readInt();
        requirements = new HashMap<>();

        while (data.isReadable()) {
            try {
                String snbt = readUTF(data);
                NBTTagCompound tag = JsonToNBT.getTagFromJson(snbt);
                StackComparable stack = StackComparable.readFromNBT(tag);
                int amount = data.readInt();
                requirements.put(stack, amount);
            } catch (NBTException e) {
                WarForgeMod.LOGGER.error("Error on decoding CitadelUpgradeRequirementPacket");
            }
        }

    }

    @Override
    public void handleServerSide(EntityPlayerMP playerEntity) {
        WarForgeMod.LOGGER.error("Received level requirement info on server");
    }

    @Override
    public void handleClientSide(EntityPlayer clientPlayer) {
        WarForgeMod.UPGRADE_HANDLER.setLevelAndLimits(level, requirements, limit, insuranceSlots, loadedChunks);
    }

}

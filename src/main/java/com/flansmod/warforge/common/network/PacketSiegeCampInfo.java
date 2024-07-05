package com.flansmod.warforge.common.network;

import java.util.ArrayList;
import java.util.List;

import com.flansmod.warforge.client.GuiSiegeCamp;
import com.flansmod.warforge.common.DimBlockPos;
import com.flansmod.warforge.common.WarForgeMod;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class PacketSiegeCampInfo extends PacketBase
{
	public DimBlockPos mSiegeCampPos;
	public List<SiegeCampAttackInfo> mPossibleAttacks = new ArrayList<SiegeCampAttackInfo>();
	
	
	@Override
	public void encodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
		data.writeInt(mSiegeCampPos.mDim);
		data.writeInt(mSiegeCampPos.getX());
		data.writeInt(mSiegeCampPos.getY());
		data.writeInt(mSiegeCampPos.getZ());
		
		data.writeByte(mPossibleAttacks.size());
		
		for(int i = 0; i < mPossibleAttacks.size(); i++)
		{
			SiegeCampAttackInfo info = mPossibleAttacks.get(i);
			writeUUID(data, info.mFactionUUID);
			writeUTF(data, info.mFactionName);
			data.writeByte(info.mDirection.ordinal());
			data.writeInt(info.mFactionColour);
		}
	}

	@Override
	public void decodeInto(ChannelHandlerContext ctx, ByteBuf data) 
	{
		int dim = data.readInt();
		int x = data.readInt();
		int y = data.readInt();
		int z = data.readInt();
		mSiegeCampPos = new DimBlockPos(dim, x, y, z);
		
		int numAttacks = data.readByte();		
		mPossibleAttacks.clear();
		for(int i = 0; i < numAttacks; i++)
		{
			SiegeCampAttackInfo info = new SiegeCampAttackInfo();
			
			info.mFactionUUID = readUUID(data);
			info.mFactionName = readUTF(data);
			info.mDirection = EnumFacing.values()[data.readByte()];
			info.mFactionColour = data.readInt();
			
			mPossibleAttacks.add(info);
		}
		
	}

	@Override
	public void handleServerSide(EntityPlayerMP playerEntity) 
	{
		WarForgeMod.LOGGER.error("Received a siege info packet server side");
	}

	@Override
	public void handleClientSide(EntityPlayer clientPlayer) 
	{
		ShowClientGUI();
	}
	
	@SideOnly(Side.CLIENT)
	private void ShowClientGUI()
	{
		Minecraft.getMinecraft().displayGuiScreen(new GuiSiegeCamp(mSiegeCampPos, mPossibleAttacks));
	}
	
}

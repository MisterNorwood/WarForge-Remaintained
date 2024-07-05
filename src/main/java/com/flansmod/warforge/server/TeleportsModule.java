package com.flansmod.warforge.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.flansmod.warforge.common.DimBlockPos;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

public class TeleportsModule 
{
	private class PendingTeleport
	{
		public EntityPlayer player;
		public int ticksRemaining;
		public BlockPos pos;
		
		public DimBlockPos target;
	}
	
	
	private List<PendingTeleport> mPendingTPs = new ArrayList<PendingTeleport>();
	
	public void RequestSpawn(EntityPlayer player)
	{
		if(!WarForgeConfig.ENABLE_SPAWN_COMMAND)
		{
			player.sendMessage(new TextComponentString("/f spawn is disabled on this server"));
			return;
		}
		
		if(!WarForgeConfig.ALLOW_SPAWN_BETWEEN_DIMENSIONS && player.dimension != 0)
		{
			player.sendMessage(new TextComponentString("You need to be in the overworld"));
			return;
		}
		
		DimBlockPos target = new DimBlockPos(0, WarForgeMod.MC_SERVER.getWorld(0).getSpawnPoint());
		PendingTeleport tp = new PendingTeleport();
		tp.player = player;
		tp.pos = player.getPosition();
		tp.target = target;
		tp.ticksRemaining = WarForgeConfig.NUM_TICKS_FOR_WARP_COMMANDS;
		mPendingTPs.add(tp);
		
		player.sendMessage(new TextComponentString("Teleport started. Stand still."));
		
	}
	
	public void RequestFHome(EntityPlayer player)
	{
		if(!WarForgeConfig.ENABLE_F_HOME_COMMAND)
		{
			player.sendMessage(new TextComponentString("/f home is disabled on this server"));
			return;
		}
		
		Faction faction = WarForgeMod.FACTIONS.GetFactionOfPlayer(player.getUniqueID());
		if(faction == null)
		{
			player.sendMessage(new TextComponentString("You are not in a faction"));
			return;
		}
		
		DimBlockPos target = faction.mCitadelPos;
		if(!WarForgeConfig.ALLOW_F_HOME_BETWEEN_DIMENSIONS && target.mDim != player.dimension)
		{
			player.sendMessage(new TextComponentString("You need to be in the same dimension as your citadel"));
			return;
		}
		
		PendingTeleport tp = new PendingTeleport();
		tp.player = player;
		tp.pos = player.getPosition();
		tp.target = target;
		tp.ticksRemaining = WarForgeConfig.NUM_TICKS_FOR_WARP_COMMANDS;
		mPendingTPs.add(tp);
		
		player.sendMessage(new TextComponentString("Teleport started. Stand still."));
	}
	
	public void Update()
	{
		for(int i = mPendingTPs.size() - 1; i >= 0; i--)
		{
			PendingTeleport tp = mPendingTPs.get(i);
			if(!tp.player.getPosition().equals(tp.pos))
			{
				tp.player.sendMessage(new TextComponentString("Teleport cancelled."));
				mPendingTPs.remove(i);
			}
			
			if(tp.ticksRemaining % 20 == 0)
			{
				tp.player.sendMessage(new TextComponentString("Teleporting in " + (tp.ticksRemaining / 20)));
			}
			
			tp.ticksRemaining--;

			if(tp.ticksRemaining == 0)
			{
				if(tp.target.mDim != tp.player.dimension)
					tp.player = (EntityPlayer)tp.player.changeDimension(tp.target.mDim);
				
				((EntityPlayerMP)tp.player).connection.setPlayerLocation(tp.target.getX() + 0.5d, tp.target.getY() + 1.5d, tp.target.getZ() + 0.5d, 0, 0);
				tp.player.sendMessage(new TextComponentString("Teleport complete."));
				mPendingTPs.remove(i);
			}
		}
	}
}

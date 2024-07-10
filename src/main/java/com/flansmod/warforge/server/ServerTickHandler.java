package com.flansmod.warforge.server;

import java.util.HashMap;

import com.flansmod.warforge.common.WarForgeMod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent;

public class ServerTickHandler 
{	
	@SubscribeEvent
	public void OnTick(ServerTickEvent tick) {
		WarForgeMod.INSTANCE.UpdateServer();
		WarForgeMod.INSTANCE.NETWORK.handleServerPackets();
		WarForgeMod.PROTECTIONS.UpdateServer();
		WarForgeMod.TELEPORTS.Update();
		WarForgeMod.proxy.TickServer();
		WarForgeMod.FACTIONS.Update();

		// WarForgeMod.LOGGER.info("Current tick: " + WarForgeMod.ServerTick);
	}
}

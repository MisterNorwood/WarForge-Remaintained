package com.flansmod.warforge.server;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.Tags;

import com.flansmod.warforge.common.network.SyncQueueHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;

public class ServerTickHandler
{
	@SubscribeEvent
	public void OnTick(ServerTickEvent tick) {
		// for some reason, ticks may occur with 0ms between them. A tick timer is more useful for distinguishing whether an update has occurred
		WarForgeMod.INSTANCE.updateServer();
		WarForgeMod.NETWORK.handleServerPackets();
		WarForgeMod.PROTECTIONS.UpdateServer();
		WarForgeMod.TELEPORTS.update();
		WarForgeMod.FOBS.getWarpQueue().update();
		WarForgeMod.FOBS.processCleanupQueue();
		WarForgeMod.proxy.TickServer();
		WarForgeMod.FACTIONS.update();
		WarForgeMod.JOURNEYMAP_SYNC.tick();
		WarForgeMod.JOURNEYMAP_VEIN_SYNC.tick();
		SyncQueueHandler.sync(tick);


		// WarForgeMod.LOGGER.info("Current tick: " + WarForgeMod.ServerTick);
	}
}

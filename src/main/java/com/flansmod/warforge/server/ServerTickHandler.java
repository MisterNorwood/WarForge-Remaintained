package com.flansmod.warforge.server;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.Tags;

import com.flansmod.warforge.common.network.SyncQueueHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class ServerTickHandler
{
	@SubscribeEvent
	public void OnTick(ServerTickEvent.Post tick) {
		WarForgeMod.gameTimeMs += 50L;
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

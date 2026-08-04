package com.flansmod.warforge.server;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class TeleportsModule
{
	private class PendingTeleport
	{
		public Player player;
		public int ticksRemaining;
		public BlockPos pos;

		public DimBlockPos target;
	}

	private class TpaRequest
	{
		public UUID requester;
		public UUID target;
		public int ticksRemaining;
	}


	private List<PendingTeleport> mPendingTPs = new ArrayList<PendingTeleport>();
	private List<TpaRequest> mTpaRequests = new ArrayList<TpaRequest>();

	public void requestSpawn(Player player)
	{
		if(!WarForgeConfig.ENABLE_SPAWN_COMMAND)
		{
			player.sendSystemMessage(Component.literal("/f spawn is disabled on this server"));
			return;
		}

		if(!WarForgeConfig.ALLOW_SPAWN_BETWEEN_DIMENSIONS && player.level().dimension() != Level.OVERWORLD)
		{
			player.sendSystemMessage(Component.literal("You need to be in the overworld"));
			return;
		}

		ServerLevel overworld = WarForgeMod.MC_SERVER.overworld();
		DimBlockPos target = new DimBlockPos(Level.OVERWORLD, overworld.getSharedSpawnPos());
		PendingTeleport tp = new PendingTeleport();
		tp.player = player;
		tp.pos = player.blockPosition();
		tp.target = target;
		tp.ticksRemaining = WarForgeConfig.NUM_TICKS_FOR_WARP_COMMANDS;
		mPendingTPs.add(tp);

		player.sendSystemMessage(Component.literal("Teleport started. Stand still."));

	}

	public void RequestFHome(Player player)
	{
		if(!WarForgeConfig.ENABLE_F_HOME_COMMAND)
		{
			player.sendSystemMessage(Component.literal("/f home is disabled on this server"));
			return;
		}

		Faction faction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUUID());
		if(faction == null)
		{
			player.sendSystemMessage(Component.literal("You are not in a faction"));
			return;
		}

		DimBlockPos target = faction.citadelPos;
		if(!WarForgeConfig.ALLOW_F_HOME_BETWEEN_DIMENSIONS && target.dim != player.level().dimension())
		{
			player.sendSystemMessage(Component.literal("You need to be in the same dimension as your citadel"));
			return;
		}

		PendingTeleport tp = new PendingTeleport();
		tp.player = player;
		tp.pos = player.blockPosition();
		tp.target = target;
		tp.ticksRemaining = WarForgeConfig.NUM_TICKS_FOR_WARP_COMMANDS;
		mPendingTPs.add(tp);

		player.sendSystemMessage(Component.literal("Teleport started. Stand still."));
	}

	public void requestTpa(Player requester, ServerPlayer target)
	{
		if(!WarForgeConfig.ENABLE_TPA_COMMAND)
		{
			requester.sendSystemMessage(Component.literal("/f tpa is disabled on this server"));
			return;
		}

		if(requester.getUUID().equals(target.getUUID()))
		{
			requester.sendSystemMessage(Component.literal("You cannot send a teleport request to yourself"));
			return;
		}

		for(int i = mTpaRequests.size() - 1; i >= 0; i--)
		{
			TpaRequest req = mTpaRequests.get(i);
			if(req.requester.equals(requester.getUUID()) && req.target.equals(target.getUUID()))
			{
				mTpaRequests.remove(i);
			}
		}

		TpaRequest req = new TpaRequest();
		req.requester = requester.getUUID();
		req.target = target.getUUID();
		req.ticksRemaining = WarForgeConfig.TPA_REQUEST_TIMEOUT_SECONDS * 20;
		mTpaRequests.add(req);

		requester.sendSystemMessage(Component.literal("Teleport request sent to " + target.getName().getString()));
		target.sendSystemMessage(Component.literal(requester.getName().getString() + " wants to teleport to you. Use /f tpaccept or /f tpdeny"));
	}

	public void acceptTpa(ServerPlayer target, UUID requesterFilter)
	{
		if(!WarForgeConfig.ENABLE_TPA_COMMAND)
		{
			target.sendSystemMessage(Component.literal("/f tpa is disabled on this server"));
			return;
		}

		TpaRequest chosen = findRequest(target.getUUID(), requesterFilter);
		if(chosen == null)
		{
			target.sendSystemMessage(Component.literal("You have no pending teleport requests"));
			return;
		}
		mTpaRequests.remove(chosen);

		ServerPlayer requester = WarForgeMod.MC_SERVER.getPlayerList().getPlayer(chosen.requester);
		if(requester == null)
		{
			target.sendSystemMessage(Component.literal("That player is no longer online"));
			return;
		}

		PendingTeleport tp = new PendingTeleport();
		tp.player = requester;
		tp.pos = requester.blockPosition();
		tp.target = new DimBlockPos(target);
		tp.ticksRemaining = WarForgeConfig.NUM_TICKS_FOR_WARP_COMMANDS;
		mPendingTPs.add(tp);

		target.sendSystemMessage(Component.literal("Accepted teleport request from " + requester.getName().getString()));
		requester.sendSystemMessage(Component.literal(target.getName().getString() + " accepted your request. Teleport started. Stand still."));
	}

	public void denyTpa(ServerPlayer target, UUID requesterFilter)
	{
		if(!WarForgeConfig.ENABLE_TPA_COMMAND)
		{
			target.sendSystemMessage(Component.literal("/f tpa is disabled on this server"));
			return;
		}

		TpaRequest chosen = findRequest(target.getUUID(), requesterFilter);
		if(chosen == null)
		{
			target.sendSystemMessage(Component.literal("You have no pending teleport requests"));
			return;
		}
		mTpaRequests.remove(chosen);

		ServerPlayer requester = WarForgeMod.MC_SERVER.getPlayerList().getPlayer(chosen.requester);
		if(requester != null)
		{
			requester.sendSystemMessage(Component.literal(target.getName().getString() + " denied your teleport request"));
		}
		target.sendSystemMessage(Component.literal("Denied teleport request"));
	}

	private TpaRequest findRequest(UUID targetId, UUID requesterFilter)
	{
		for(int i = mTpaRequests.size() - 1; i >= 0; i--)
		{
			TpaRequest req = mTpaRequests.get(i);
			if(req.target.equals(targetId) && (requesterFilter == null || req.requester.equals(requesterFilter)))
			{
				return req;
			}
		}
		return null;
	}

	public void update()
	{
		for(int i = mPendingTPs.size() - 1; i >= 0; i--)
		{
			PendingTeleport tp = mPendingTPs.get(i);
			if(!tp.player.blockPosition().equals(tp.pos))
			{
				tp.player.sendSystemMessage(Component.literal("Teleport cancelled."));
				mPendingTPs.remove(i);
				continue;
			}

			if(tp.ticksRemaining % 20 == 0)
			{
				tp.player.sendSystemMessage(Component.literal("Teleporting in " + (tp.ticksRemaining / 20)));
			}

			tp.ticksRemaining--;

			if(tp.ticksRemaining == 0)
			{
				TeleportUtil.teleportPlayer( (ServerPlayer)tp.player, tp.target.dim, tp.target.toRegularPos());
				mPendingTPs.remove(i);
			}
		}

		for(int i = mTpaRequests.size() - 1; i >= 0; i--)
		{
			TpaRequest req = mTpaRequests.get(i);
			req.ticksRemaining--;
			if(req.ticksRemaining <= 0)
			{
				mTpaRequests.remove(i);
				ServerPlayer requester = WarForgeMod.MC_SERVER.getPlayerList().getPlayer(req.requester);
				if(requester != null)
				{
					requester.sendSystemMessage(Component.literal("Your teleport request expired"));
				}
			}
		}
	}
}

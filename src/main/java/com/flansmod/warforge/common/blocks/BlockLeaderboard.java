package com.flansmod.warforge.common.blocks;

import java.util.UUID;

import javax.annotation.Nullable;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketLeaderboardInfo;
import com.flansmod.warforge.server.Leaderboard.FactionStat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;

public class BlockLeaderboard extends Block implements EntityBlock
{
	public final FactionStat stat;

	public BlockLeaderboard(FactionStat stat)
	{
		super(BlockBehaviour.Properties.of()
				.strength(-1.0F, 3600000.0F)
				.noLootTable()
				.noOcclusion());

		this.stat = stat;
	}

	@Override
	public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

	@Override
	public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit)
	{
		if(player.isShiftKeyDown())
			return InteractionResult.PASS;
		if(!world.isClientSide)
		{
			UUID uuid = player.getUUID();
			PacketLeaderboardInfo packet = new PacketLeaderboardInfo();
			packet.info = WarForgeMod.LEADERBOARD.CreateInfo(0, stat, uuid);
			WarForgeMod.NETWORK.sendTo(packet, (ServerPlayer)player);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.CONSUME;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
	{
		return new TileEntityLeaderboard(pos, state);
	}
}

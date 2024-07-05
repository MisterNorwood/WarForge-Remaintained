package com.flansmod.warforge.common.blocks;

import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;

public class TileEntityReinforcedClaim extends TileEntityBasicClaim
{
	@Override
	public int GetDefenceStrength() { return WarForgeConfig.CLAIM_STRENGTH_REINFORCED; }
	@Override
	public int GetSupportStrength() { return WarForgeConfig.SUPPORT_STRENGTH_REINFORCED; }
	@Override
	public int GetAttackStrength() { return 0; }
}

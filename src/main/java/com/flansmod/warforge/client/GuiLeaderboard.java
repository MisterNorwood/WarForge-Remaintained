package com.flansmod.warforge.client;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.FactionDisplayInfo;
import com.flansmod.warforge.common.network.LeaderboardInfo;
import com.flansmod.warforge.common.network.PacketFactionInfo;
import com.flansmod.warforge.common.network.PacketLeaderboardInfo;
import com.flansmod.warforge.common.network.PacketRequestLeaderboardInfo;
import com.flansmod.warforge.common.network.PlayerDisplayInfo;
import com.flansmod.warforge.server.Leaderboard.FactionStat;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

public class GuiLeaderboard extends GuiScreen
{
	private static final ResourceLocation texture = new ResourceLocation(WarForgeMod.MODID, "gui/leaderboard.png");

	private FactionStat mCurrentStat = FactionStat.TOTAL;
	private int mLookingAtIndex = 0;
	private int xSize, ySize;
	private LeaderboardInfo info;
	
	public GuiLeaderboard()
	{
		info = PacketLeaderboardInfo.sLatestInfo;
		mCurrentStat = info.stat;
    	xSize = 256;
    	ySize = 191;
	}
	
	@Override
	public void initGui()
	{
		super.initGui();
		
		int j = (width - xSize) / 2;
		int k = (height - ySize) / 2;
		
		// Total button - this is the default tab, always accessible
		GuiButton totalButton = new GuiButton(FactionStat.TOTAL.ordinal(), j + 6, k + 18, 58, 20, "Total");
		buttonList.add(totalButton);
		totalButton.enabled = mCurrentStat != FactionStat.TOTAL;
		
		GuiButton notorietyButton = new GuiButton(FactionStat.NOTORIETY.ordinal(), j + 6 + 62, k + 18, 58, 20, "Notoriety");
		buttonList.add(notorietyButton);
		notorietyButton.enabled = mCurrentStat != FactionStat.NOTORIETY;
		
		GuiButton legacyButton = new GuiButton(FactionStat.LEGACY.ordinal(), j + 6 + 124, k + 18, 58, 20, "Legacy");
		buttonList.add(legacyButton);
		legacyButton.enabled = mCurrentStat != FactionStat.LEGACY;
		
		GuiButton wealthButton = new GuiButton(FactionStat.WEALTH.ordinal(), j + 6 + 186, k + 18, 58, 20, "Wealth");
		buttonList.add(wealthButton);
		wealthButton.enabled = mCurrentStat != FactionStat.WEALTH;
		
	}
	
	@Override
	protected void actionPerformed(GuiButton button)
	{
		FactionStat stat = FactionStat.values()[button.id];
		mCurrentStat = stat;
		info = null;
		
		// TODO: Next page buttons
		
		PacketRequestLeaderboardInfo packet = new PacketRequestLeaderboardInfo();
		packet.mFirstIndex = 0;
		packet.mStat = stat;
		WarForgeMod.NETWORK.sendToServer(packet);
	}
	
	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks)
	{
		// Draw background
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		mc.renderEngine.bindTexture(texture);
		int j = (width - xSize) / 2;
		int k = (height - ySize) / 2;
		drawTexturedModalRect(j, k, 0, 0, xSize, ySize);
		
		// Then draw overlay
		super.drawScreen(mouseX, mouseY, partialTicks);
		
		String line = "";
		switch(mCurrentStat)
		{
			case LEGACY: line = "Legacy Leaderboard"; break;
			case NOTORIETY: line = "Notoriety Leaderboard"; break;
			case TOTAL: line = "Top Leaderboard"; break;
			case WEALTH: line = "Wealth Leaderboard"; break;	
		}
		fontRenderer.drawStringWithShadow(line, j + xSize / 2 - fontRenderer.getStringWidth(line) / 2, k + 6, 0xffffff);
		
		line = "";
		switch(mCurrentStat)
		{
			case LEGACY: line = "Factions that have been active longest"; break;
			case NOTORIETY: line = "Factions that are top in PvP and Siegeing"; break;
			case TOTAL: line = "Top Factions, with combined scores"; break;
			case WEALTH: line = "Factions with the most wealth in their citadel"; break;	
		}
		fontRenderer.drawStringWithShadow(line, j + 8, k + 40, 0xf0f0f0);
			
		if(info == null)
		{
			String text = "Waiting for server...";
			fontRenderer.drawStringWithShadow(text, j + xSize / 2 - fontRenderer.getStringWidth(text) / 2, k + 80, 0xa0a0a0);
		}
		else
		{
			if(info.mMyFaction != null)
			{
				int rank = 0;
				switch(mCurrentStat)
				{
					case LEGACY: rank = info.mMyFaction.mLegacyRank; break;
					case NOTORIETY: rank = info.mMyFaction.mNotorietyRank; break;
					case TOTAL: rank = info.mMyFaction.mTotalRank; break;
					case WEALTH: rank = info.mMyFaction.mWealthRank; break;
				}
				RenderFactionInfo(j + 7, k + 55, info.mMyFaction, info.mMyFaction.mLegacyRank );
			}
			
			for(int i = 0; i < LeaderboardInfo.NUM_LEADERBOARD_ENTRIES_PER_PAGE; i++)
			{
				if(info.mFactionInfos[i] != null)
				{
					RenderFactionInfo(j + 7, k + 72 + 12 * i, info.mFactionInfos[i], info.firstIndex + i + 1);
				}
			}
		}
	}
	
	private void RenderFactionInfo(int x, int y, FactionDisplayInfo faction, int oneIndexedRank)
	{
		int stat = 0;
		switch(mCurrentStat)
		{
			case LEGACY: stat = faction.mLegacy; break;
			case NOTORIETY: stat = faction.mNotoriety; break;
			case TOTAL: stat = faction.mLegacy + faction.mNotoriety + faction.mWealth; break;
			case WEALTH: stat = faction.mWealth; break;
		}
				
		if(oneIndexedRank <= 3)
		{
			int colour = 0xffffff;
			switch(oneIndexedRank)
			{
				case 1: colour = 0xFFD700; break;
				case 2: colour = 0xC0C0C0; break;
				case 3: colour = 0xcd7f32; break;
			}
			fontRenderer.drawStringWithShadow(faction.mFactionName, x + 2, y + 1, colour);
			fontRenderer.drawStringWithShadow("" + stat, x + 150, y + 1, colour);
			fontRenderer.drawStringWithShadow("#" + oneIndexedRank, x + 200, y + 1, colour);
		}
		else	
		{
			fontRenderer.drawString(faction.mFactionName, x + 2, y + 1, 0xffffff);
			fontRenderer.drawString("" + stat, x + 150, y + 1, 0xffffff);
			fontRenderer.drawString("#" + oneIndexedRank, x + 200, y + 1, 0xffffff);
		}
	}
	
	@Override
	public boolean doesGuiPauseGame()
	{
		return false;
	}
}

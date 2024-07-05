package com.flansmod.warforge.common.network;

import com.flansmod.warforge.server.Leaderboard.FactionStat;

public class LeaderboardInfo 
{
	public static final int NUM_LEADERBOARD_ENTRIES_PER_PAGE = 10;
	
	public int firstIndex = 0;
	public FactionStat stat = FactionStat.TOTAL;
	public FactionDisplayInfo[] mFactionInfos = new FactionDisplayInfo[NUM_LEADERBOARD_ENTRIES_PER_PAGE];
	public FactionDisplayInfo mMyFaction = null;
}

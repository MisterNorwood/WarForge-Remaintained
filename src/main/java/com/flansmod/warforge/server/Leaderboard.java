package com.flansmod.warforge.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.UUID;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.LeaderboardInfo;

/* Legacy code that errors
import scala.actors.threadpool.Arrays;
*/

public class Leaderboard 
{
	public static class FactionSorterNotoriety implements Comparator<Faction>
	{
		@Override
		public int compare(Faction a, Faction b) { return Integer.compare(b.mNotoriety, a.mNotoriety); }
	}
	
	
	public static class FactionSorterWealth implements Comparator<Faction>
	{
		@Override
		public int compare(Faction a, Faction b) { return Integer.compare(b.mWealth, a.mWealth); }
	}
	
	
	public static class FactionSorterLegacy implements Comparator<Faction>
	{
		@Override
		public int compare(Faction a, Faction b) { return Integer.compare(b.mLegacy, a.mLegacy); }
	}
	
	public static class FactionSorterTotal implements Comparator<Faction>
	{
		@Override
		public int compare(Faction a, Faction b) 
		{ 
			return Integer.compare(b.mLegacy + b.mNotoriety + b.mWealth, a.mLegacy + a.mNotoriety + a.mWealth); 
		}
	}
	
	
	public enum FactionStat 
	{
		NOTORIETY,
		WEALTH,
		LEGACY,
		TOTAL,
	}
	
	public static Comparator<Faction> GetSorter(FactionStat stat)
	{
		switch(stat)
		{
			case NOTORIETY: return notorietySorter;
			case WEALTH: return wealthSorter;
			case LEGACY: return legacySorter;
			case TOTAL: return totalSorter;
		}
		WarForgeMod.LOGGER.error("Unknown sort type");
		return totalSorter;
	}
	
	
	private static FactionSorterNotoriety notorietySorter = new FactionSorterNotoriety();
	private static FactionSorterWealth wealthSorter = new FactionSorterWealth();
	private static FactionSorterLegacy legacySorter = new FactionSorterLegacy();
	private static FactionSorterTotal totalSorter = new FactionSorterTotal();
	private ArrayList<Faction> mFactions = new ArrayList<Faction>();
	
	public void RegisterFaction(Faction faction)
	{
		mFactions.add(faction);
	}
	
	public void UnregisterFaction(Faction faction) 
	{
		mFactions.remove(faction);
	}
	
	public List<Faction> GetSortedList(FactionStat stat, List<Faction> optionalTarget)
	{
		Comparator<Faction> sorter = GetSorter(stat);
		if(optionalTarget != null)
		{
			optionalTarget.addAll(mFactions);
			optionalTarget.sort(sorter);
			return optionalTarget;
		}
		else
		{
			mFactions.sort(sorter);
			return mFactions;
		}
	}
	
	public int GetOneIndexedRankOf(Faction faction, FactionStat stat)
	{
		return GetZeroIndexedRankOf(faction, stat) + 1;
	}
	
	public int GetZeroIndexedRankOf(Faction faction, FactionStat stat)
	{
		int numBetterFactions = 0;
		Comparator<Faction> sorter = GetSorter(stat);
		
		for(Faction other : mFactions)
		{
			if(sorter.compare(faction, other) > 0)
			{
				numBetterFactions++;
			}
		}
		
		return numBetterFactions;
	}
	
	public LeaderboardInfo CreateInfo(int firstIndex, FactionStat stat, UUID playerAsking)
	{
		LeaderboardInfo info = new LeaderboardInfo();
		
		info.firstIndex = firstIndex;
		info.stat = stat;
		if(playerAsking != null && !playerAsking.equals(Faction.NULL))
		{
			Faction faction = WarForgeMod.FACTIONS.GetFactionOfPlayer(playerAsking);
			if(faction != null)
				info.mMyFaction = faction.CreateInfo();
		}
		ArrayList<Faction> sorted = new ArrayList<Faction>(mFactions.size());
		GetSortedList(stat, sorted);
		for(int i = firstIndex; i < firstIndex + LeaderboardInfo.NUM_LEADERBOARD_ENTRIES_PER_PAGE; i++)
		{
			// Only fill in in-range ones, leave rest null, handle on client
			if(0 <= i && i < sorted.size())
			{
				info.mFactionInfos[i - firstIndex] = sorted.get(i).CreateInfo();
			}
		}
		
		return info;
	}
}

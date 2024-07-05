package com.flansmod.warforge.common.network;

import java.util.UUID;

import com.flansmod.warforge.server.Faction;

public class PlayerDisplayInfo
{
	public String mPlayerName = "";
	public UUID mPlayerUUID = Faction.NULL;
	public Faction.Role mRole = Faction.Role.MEMBER;
}
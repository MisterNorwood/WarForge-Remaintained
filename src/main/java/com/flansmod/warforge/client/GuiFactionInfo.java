package com.flansmod.warforge.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.flansmod.warforge.common.CommonProxy;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.FactionDisplayInfo;
import com.flansmod.warforge.common.network.PacketFactionInfo;
import com.flansmod.warforge.common.network.PacketRequestFactionInfo;
import com.flansmod.warforge.common.network.PlayerDisplayInfo;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.Faction.Role;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.util.ResourceLocation;

public class GuiFactionInfo extends GuiScreen
{
	private static final ResourceLocation texture = new ResourceLocation(WarForgeMod.MODID, "gui/factioninfo.png");
	public static HashMap<String, ResourceLocation> sSkinCache = new HashMap<String, ResourceLocation>();

	private enum EnumTab
	{
		STATS,
		ALLIES,
		SIEGES,
		PLAYER,
	}
	
	// Generic buttons
	private static final int BUTTON_STATS = 0;
	private static final int BUTTON_ALLIES = 1;
	private static final int BUTTON_SIEGES = 2;
	
	// Player buttons
	private static final int BUTTON_PROMOTE = 10;
	private static final int BUTTON_DEMOTE = 11;
	private static final int BUTTON_KICK = 12;
	
	// Player array
	private static final int BUTTON_PLAYER_FIRST = 100;
	
	private EnumTab currentTab = EnumTab.STATS;
	private int xSize, ySize;
	private FactionDisplayInfo info;
	private PlayerDisplayInfo lookingAt = null;
	
	private GuiButton stats, allies, sieges,
						promote, demote, kick;
	
	public GuiFactionInfo()
	{
		info = PacketFactionInfo.sLatestInfo;
    	xSize = 176;
    	ySize = 256;
	}
	
	@Override
	public void initGui()
	{
		super.initGui();
		
		int j = (width - xSize) / 2;
		int k = (height - ySize) / 2;
		
		// Stats button - this is the default tab, always accessible
		stats = new GuiButton(BUTTON_STATS, j + 53, k + 127, 70, 20, "Stats");
		buttonList.add(stats);
		
		// Allies button - publicly available
		allies = new GuiButton(BUTTON_ALLIES, j + 5, k + 127, 46, 20, "Allies");
		buttonList.add(allies);
		allies.enabled = false; // TODO: Not implemented yet
		
		// Sieges button - publicly available
		sieges = new GuiButton(BUTTON_SIEGES, j + 125, k + 127, 46, 20, "Sieges");
		buttonList.add(sieges);
		sieges.enabled = false; // TODO: Not implemented yet
		
		// Player action buttons
		promote = new GuiButton(BUTTON_PROMOTE, j + 108, k + 152, 60, 20, "Promote");
		buttonList.add(promote);
		demote = new GuiButton(BUTTON_DEMOTE, j + 108, k + 174, 60, 20, "Demote");
		buttonList.add(demote);
		kick = new GuiButton(BUTTON_KICK, j + 108, k + 196, 60, 20, "Kick");
		buttonList.add(kick);
		
		// Player selection buttons
		int rowLength = 7;
		int columnHeight = 2;
		int leaderInfoIndex = -1;
		
		for(int y = 0; y < columnHeight; y++)
		{
			for(int x = 0; x < rowLength; x++)
			{
				int index = x + y * rowLength;
				if(leaderInfoIndex != -1)
				{
					index++;
				}
				
				if(index < info.mMembers.size())
				{
					PlayerDisplayInfo playerInfo = info.mMembers.get(index);
					// If this is the leader, skip them and cache to put at top
					if(leaderInfoIndex == -1)
					{
						if(playerInfo.mRole == Faction.Role.LEADER || playerInfo.mPlayerUUID.equals(info.mLeaderID))
						{
							// Cache them
							leaderInfoIndex = index;
							
							// Then render the next one
							if(index + 1 < info.mMembers.size())
							{
								playerInfo = info.mMembers.get(index + 1);
								index++;
							}
							else continue;
						}
					}

					GuiInvisibleButton button = new GuiInvisibleButton(BUTTON_PLAYER_FIRST + index, j + 5 + 24 * x, k + 79 + 24 * y, 22, 22, "");
					buttonList.add(button);
				}
			}
		}
		
		if(leaderInfoIndex != -1)
		{
			GuiInvisibleButton button = new GuiInvisibleButton(BUTTON_PLAYER_FIRST + leaderInfoIndex, j + 34, k + 32, 22, 22, "");
			buttonList.add(button);
		}
		
		SelectTab(EnumTab.STATS, null);
	}
	
	@Override
	protected void actionPerformed(GuiButton button)
	{
		switch(button.id)
		{
			// Tab selections
			case BUTTON_STATS:
			{
				SelectTab(EnumTab.STATS, null);
				break;
			}
			case BUTTON_ALLIES:
			{
				SelectTab(EnumTab.ALLIES, null);
				break;
			}
			case BUTTON_SIEGES:
			{
				SelectTab(EnumTab.ALLIES, null);
				break;
			}
			// Player actions
			case BUTTON_PROMOTE:
			{
				if(lookingAt != null)
				{
					// Request promotion
					Minecraft.getMinecraft().player.sendChatMessage("/f promote " + lookingAt.mPlayerName);
					
					// Re-request updated data
					ClientProxy.RequestFactionInfo(info.mFactionID);
				}
				break;
			}
			case BUTTON_DEMOTE:
			{
				if(lookingAt != null)
				{
					// Request promotion
					Minecraft.getMinecraft().player.sendChatMessage("/f demote " + lookingAt.mPlayerName);
					
					// Re-request updated data
					ClientProxy.RequestFactionInfo(info.mFactionID);
				}
				break;
			}
			case BUTTON_KICK:
			{
				if(lookingAt != null)
				{
					// Request promotion
					Minecraft.getMinecraft().player.sendChatMessage("/f kick " + lookingAt.mPlayerName);
					
					// Re-request updated data
					ClientProxy.RequestFactionInfo(info.mFactionID);
				}
				break;
			}
			
			// Player selections
			default:
			{
				int index = button.id - BUTTON_PLAYER_FIRST;
				if(index >= 0 && index < info.mMembers.size())
				{
					SelectTab(EnumTab.PLAYER, info.mMembers.get(index).mPlayerUUID);
				}
				else
				{
					WarForgeMod.LOGGER.error("Pressed a button with unknown player index");
				}
				break;
			}
		}	
	}
	
	private void SelectTab(EnumTab tab, UUID playerID)
	{
		UUID myID = Minecraft.getMinecraft().player.getUniqueID();
		PlayerDisplayInfo myInfo = info.GetPlayerInfo(myID);
		boolean isMyFaction = myInfo != null;
		
		promote.visible = tab == EnumTab.PLAYER && isMyFaction;
		demote.visible = tab == EnumTab.PLAYER && isMyFaction;
		kick.visible = tab == EnumTab.PLAYER && isMyFaction;
		
		if(tab == EnumTab.PLAYER)
		{
			lookingAt = info.GetPlayerInfo(playerID);
			if(lookingAt != null)
			{
				promote.enabled = isMyFaction && myInfo.mRole == Role.LEADER && lookingAt.mRole == Role.MEMBER;
				demote.enabled = isMyFaction && myInfo.mRole == Role.LEADER && lookingAt.mRole == Role.OFFICER;
				kick.enabled = isMyFaction && lookingAt.mRole.ordinal() < myInfo.mRole.ordinal();
			}
		}
		
		currentTab = tab;
	}
	
	private void SelectPlayer()
	{
		stats.enabled = true;
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
		
		fontRenderer.drawStringWithShadow(info.mFactionName, j + xSize / 2 - fontRenderer.getStringWidth(info.mFactionName) * 0.5f, k + 13, 0xffffff);
		// Some space here for strings
		//fontRenderer.drawStringWithShadow("Notoriety: " + info.mNotoriety, j + 6, k + 57, 0xffffff);
		//fontRenderer.drawStringWithShadow("Wealth: " + info.mNotoriety, j + 2 + xSize / 2, k + 57, 0xffffff);
		//fontRenderer.drawStringWithShadow("Notoriety: " + info.mNotoriety, j + 6, k + 67, 0xffffff);
		//fontRenderer.drawStringWithShadow("Wealth: " + info.mNotoriety, j + 2 + xSize / 2, k + 67, 0xffffff);
			
		// Render user info, and look out for leader info while we there
		int rowLength = 7;
		int columnHeight = 2;
		PlayerDisplayInfo leaderInfo = null; 
		
		for(int y = 0; y < columnHeight; y++)
		{
			for(int x = 0; x < rowLength; x++)
			{
				int index = x + y * rowLength;
				
				if(leaderInfo != null)
				{
					index++;
				}
				
				if(index < info.mMembers.size())
				{
					PlayerDisplayInfo playerInfo = info.mMembers.get(index);
					// If this is the leader, skip them and cache to put at top
					if(leaderInfo == null)
					{
						if(playerInfo.mRole == Faction.Role.LEADER || playerInfo.mPlayerUUID.equals(info.mLeaderID))
						{
							// Cache
							leaderInfo = playerInfo;
							
							// Then render the next one
							if(index + 1 < info.mMembers.size())
								playerInfo = info.mMembers.get(index + 1);
							else continue;
						}
					}
					
					// Bind our texture, render a background
					mc.renderEngine.bindTexture(texture);
					drawTexturedModalRect(j + 5 + 24 * x, k + 79 + 24 * y, playerInfo.mRole == Faction.Role.OFFICER ? 176 : 198, 0, 22, 22);
					
					// Then bind their face and render that
					RenderPlayerFace(j + 8 + 24 * x, k + 82 + 24 * y, playerInfo.mPlayerName);
				}
			}
		}
		
		if(leaderInfo != null)
		{
			fontRenderer.drawStringWithShadow("Leader", j + 56, k + 31, 0xffffff);
			fontRenderer.drawStringWithShadow(leaderInfo.mPlayerName, j + 56, k + 42, 0xffffff);
			RenderPlayerFace(j + 34, k + 32, leaderInfo.mPlayerName);
		}
		
		
		// Lower box
		switch(currentTab)
		{
			case STATS:
			{
				// First column - names
				fontRenderer.drawStringWithShadow("Notoriety:", j + 8, k + 152, 0xffffff);
				fontRenderer.drawStringWithShadow("Wealth:", j + 8, k + 162, 0xffffff);
				fontRenderer.drawStringWithShadow("Legacy:", j + 8, k + 172, 0xffffff);
				fontRenderer.drawStringWithShadow("Total:", j + 8, k + 182, 0xffffff);
				fontRenderer.drawStringWithShadow("Members:", j + 8, k + 192, 0xffffff);
				
				// Second column - numbers
				int column1X = 120;
				fontRenderer.drawStringWithShadow("" + info.mNotoriety, j + column1X, k + 152, 0xffffff);
				fontRenderer.drawStringWithShadow("" + info.mWealth, j + column1X, k + 162, 0xffffff);
				fontRenderer.drawStringWithShadow("" + info.mLegacy, j + column1X, k + 172, 0xffffff);
				fontRenderer.drawStringWithShadow("" + (info.mNotoriety + info.mWealth + info.mLegacy), j + column1X, k + 182, 0xffffff);
				fontRenderer.drawStringWithShadow("" + info.mMembers.size(), j + column1X, k + 192, 0xffffff);
				
				// Third column - server positioning
				int column2X = 150;
				fontRenderer.drawStringWithShadow("#" + info.mNotorietyRank, j + column2X, k + 152, 0xffffff);
				fontRenderer.drawStringWithShadow("#" + info.mWealthRank, j + column2X, k + 162, 0xffffff);
				fontRenderer.drawStringWithShadow("#" + info.mLegacyRank, j + column2X, k + 172, 0xffffff);
				fontRenderer.drawStringWithShadow("#" + info.mTotalRank, j + column2X, k + 182, 0xffffff);
				
				break;
			}
			case ALLIES:
			{
				// TODO:
				break;
			}
			case PLAYER:
			{
				if(lookingAt != null)
				{
					fontRenderer.drawStringWithShadow(lookingAt.mPlayerName, j + 8, k + 152, 0xffffff);
					switch(lookingAt.mRole) 
					{
						case GUEST: fontRenderer.drawStringWithShadow("Guest", j + 8, k + 162, 0xffffff); break;
						case LEADER: fontRenderer.drawStringWithShadow("Leader", j + 8, k + 162, 0xffffff); break;
						case MEMBER: fontRenderer.drawStringWithShadow("Member", j + 8, k + 162, 0xffffff); break;
						case OFFICER: fontRenderer.drawStringWithShadow("Officer", j + 8, k + 162, 0xffffff); break;
						default: fontRenderer.drawStringWithShadow("Unknown", j + 8, k + 162, 0xffffff); break;
					}
				}
				break;
			}
			case SIEGES:
			{
				// TODO:
				break;
			}
		}
		
	}
	
	private void RenderPlayerFace(int x, int y, String username)
	{		
		ResourceLocation skinLocation = GetSkin(username);
        mc.renderEngine.bindTexture(skinLocation);
        drawModalRectWithCustomSizedTexture(x, y, 16, 16, 16, 16, 128, 128);
	}	
		
	public static ResourceLocation GetSkin(String username)
	{
		if(!sSkinCache.containsKey(username))
		{
			// Then bind their face and render that
			ResourceLocation skin = DefaultPlayerSkin.getDefaultSkinLegacy();
			GameProfile profile = TileEntitySkull.updateGameProfile(new GameProfile((UUID)null, username));
	        if (profile != null)
	        {
	            Minecraft minecraft = Minecraft.getMinecraft();
	            Map<Type, MinecraftProfileTexture> map = minecraft.getSkinManager().loadSkinFromCache(profile);
	
	            if (map.containsKey(Type.SKIN))
	            {
	            	skin = minecraft.getSkinManager().loadSkin(map.get(Type.SKIN), Type.SKIN);
	            }
	            else
	            {
	                UUID uuid = EntityPlayer.getUUID(profile);
	                skin = DefaultPlayerSkin.getDefaultSkin(uuid);
	            }
	        }
	        sSkinCache.put(username, skin);
	        return skin;
		}
		
		return sSkinCache.get(username);
	}
	
	@Override
	public boolean doesGuiPauseGame()
	{
		return false;
	}
}

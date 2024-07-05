package com.flansmod.warforge.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.flansmod.warforge.common.DimBlockPos;
import com.flansmod.warforge.common.DimChunkPos;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.IClaim;
import com.flansmod.warforge.common.blocks.TileEntityCitadel;
import com.flansmod.warforge.common.network.SiegeCampProgressInfo;
import com.flansmod.warforge.server.Faction;
import com.google.common.collect.Lists;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.model.ModelBanner;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.renderer.BannerTextures;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntityBannerRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemBanner;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.BannerPattern;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBanner;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;

public class ClientTickHandler 
{
	private Tessellator tess;
	private static final ResourceLocation texture = new ResourceLocation(WarForgeMod.MODID, "world/borders.png");
	private static final ResourceLocation fastTexture = new ResourceLocation(WarForgeMod.MODID, "world/borders_fast.png");
	private static final ResourceLocation overlayTex = new ResourceLocation(WarForgeMod.MODID, "world/overlay.png");
	private static final ResourceLocation siegeprogress = new ResourceLocation(WarForgeMod.MODID, "gui/siegeprogressslim.png");
	private final ModelBanner bannerModel = new ModelBanner();
	private DimChunkPos mStandingInChunk = new DimChunkPos(0,0,0);
	private float mShowNewAreaTicksRemaining = 0;
	private String mAreaMessage = "";
	private int mAreaMessageColour = 0xffffff;
	public static long msOfNextSiegeDay = 0L;
	public static long msOfNextYieldDay = 0L;
	
	public ClientTickHandler()
	{
		tess = Tessellator.getInstance();
	}
	
	@SubscribeEvent
	public void OnTick(ClientTickEvent tick)
	{
		WarForgeMod.INSTANCE.NETWORK.handleClientPackets();
		WarForgeMod.proxy.TickClient();
		ArrayList<DimBlockPos> expired = new ArrayList<DimBlockPos>();
		for(HashMap.Entry<DimBlockPos, SiegeCampProgressInfo> kvp : ClientProxy.sSiegeInfo.entrySet())
		{
			kvp.getValue().ClientTick();
			if(kvp.getValue().Completed())
			{
				expired.add(kvp.getKey());
			}
		}
		
		for(DimBlockPos pos : expired)
		{
			ClientProxy.sSiegeInfo.remove(pos);
		}
		
		if(mShowNewAreaTicksRemaining > 0.0f)
			mShowNewAreaTicksRemaining--;
		
		if(Minecraft.getMinecraft().player != null && Minecraft.getMinecraft().player.ticksExisted % 200 == 0)
			CLAIMS_DIRTY = true;
		
		if(WarForgeConfig.SHOW_NEW_AREA_TIMER > 0.0f)
		{
			EntityPlayerSP player = Minecraft.getMinecraft().player;
			if(player != null)
			{
				DimChunkPos standing = new DimChunkPos(player.dimension, player.getPosition());
				
				if(!standing.equals(mStandingInChunk))
				{
					IClaim preClaim = null;
					IClaim postClaim = null;
					
					// Try and find the claim we left and the claim we entered
					for(TileEntity te : player.world.loadedTileEntityList)
					{
						if(te instanceof IClaim)
						{
							DimChunkPos tePos = ((IClaim) te).GetPos().ToChunkPos();
							if(tePos.equals(mStandingInChunk))
								preClaim = (IClaim)te;
							
							if(tePos.equals(standing))
								postClaim = (IClaim)te;
						}
					}
					
					if(preClaim == null)
					{
						if(postClaim == null)
						{
							// From nowhere to nowhere, nothing doing
						}
						else
						{
							// We've entered a new claim
							mAreaMessage = "Entering " + postClaim.GetDisplayName();		
							mAreaMessageColour = postClaim.GetColour();
							mShowNewAreaTicksRemaining = WarForgeConfig.SHOW_NEW_AREA_TIMER;
						}
					}
					else // We've left somewhere 
					{
						if(postClaim == null) 
						{
							// Gone to nowhere, bye
							mAreaMessage = "Leaving " + preClaim.GetDisplayName();		
							mAreaMessageColour = preClaim.GetColour();
							mShowNewAreaTicksRemaining = WarForgeConfig.SHOW_NEW_AREA_TIMER;
						}
						else
						{
							// We've gone to another place. 
							if(preClaim.GetFaction().equals(postClaim.GetFaction()))
							{
								// If it's the same faction, don't mention it
							}
							else
							{
								// Otherwise, we've switched faction
								mAreaMessage = "Leaving " + preClaim.GetDisplayName() + ", Entering " + postClaim.GetDisplayName();			
								mAreaMessageColour = postClaim.GetColour();
								mShowNewAreaTicksRemaining = WarForgeConfig.SHOW_NEW_AREA_TIMER;
							}
						}
					}
					
					mStandingInChunk = standing;
				}
			}
		}
	}
	
	@SubscribeEvent
	public void OnRenderHUD(RenderGameOverlayEvent event)
	{
		if(event.getType() == ElementType.BOSSHEALTH)
		{
			Minecraft mc = Minecraft.getMinecraft();
			EntityPlayerSP player = Minecraft.getMinecraft().player;
			if(player != null)
			{
				// Timer info
				if(WarForgeConfig.SHOW_YIELD_TIMERS)
				{
					// Anchor point = top left of screen
					int j = 0;
					int k = 0;
					
					long msRemaining = msOfNextSiegeDay - System.currentTimeMillis();
					long s = msRemaining / 1000;
					long m = s / 60;
					long h = m / 60;
					long d = h / 24;
					
					mc.fontRenderer.drawStringWithShadow("Siege Progress: "
					+ (d > 0 ? (d) + " days, " : "")
					+ String.format("%02d", (h % 24))  + ":"
					+ String.format("%02d", (m % 60)) + ":"
					+ String.format("%02d", (s % 60)),
					j + 4,
					k + 4,
					0xffffff);
					
					msRemaining = msOfNextYieldDay - System.currentTimeMillis();
					s = msRemaining / 1000;
					m = s / 60;
					h = m / 60;
					d = h / 24;
					
					mc.fontRenderer.drawStringWithShadow("Next yields: "
					+ (d > 0 ? (d) + " days, " : "")
					+ String.format("%02d", (h % 24))  + ":"
					+ String.format("%02d", (m % 60)) + ":"
					+ String.format("%02d", (s % 60)),
					j + 4,
					k + 14,
					0xffffff);
				}
				
				// Siege camp info
				SiegeCampProgressInfo infoToRender = null;
				double bestDistanceSq = Double.MAX_VALUE;
				
				for(SiegeCampProgressInfo info : ClientProxy.sSiegeInfo.values())
				{
					double distSq = info.mDefendingPos.distanceSq(player.posX, player.posY, player.posZ);
					if(info.mDefendingPos.mDim == player.dimension 
					&& distSq < WarForgeConfig.SIEGE_INFO_RADIUS * WarForgeConfig.SIEGE_INFO_RADIUS)
					{
						if(distSq < bestDistanceSq)
						{
							bestDistanceSq = distSq;
							infoToRender = info;
						}
					}
				}
				
				
				
				// Render siege overlay
				if(infoToRender != null)
				{
					GlStateManager.enableAlpha();
					GlStateManager.enableBlend();
					
	                float attackR = (float)(infoToRender.mAttackingColour >> 16 & 255) / 255.0F;
	                float attackG = (float)(infoToRender.mAttackingColour >> 8 & 255) / 255.0F;
	                float attackB = (float)(infoToRender.mAttackingColour & 255) / 255.0F;
	                float defendR = (float)(infoToRender.mDefendingColour >> 16 & 255) / 255.0F;
	                float defendG = (float)(infoToRender.mDefendingColour >> 8 & 255) / 255.0F;
	                float defendB = (float)(infoToRender.mDefendingColour & 255) / 255.0F;
					
					// Render background, bars etc
					int xSize = 256;
					int ySize = 30;
					
					// Anchor point = top middle of screen
					int j = event.getResolution().getScaledWidth() / 2 - xSize / 2;
					int k = 0;
					
					float scroll = mc.getFrameTimer().getIndex() +  + event.getPartialTicks();
					scroll *= 0.25f;
					scroll = scroll % 10;

					mc.renderEngine.bindTexture(siegeprogress);
					GlStateManager.color(1f, 1f, 1f, 1f);
					drawTexturedModalRect(j, k, 0, 0, xSize, ySize);
					
					float siegeLength = infoToRender.mCompletionPoint + 5;
					float barLengthPx = 224;
					float notchDistance = barLengthPx / siegeLength;
					
					// Draw filled bar
					int firstPx = 0;
					int lastPx = 0;
					boolean isIncreasing = infoToRender.mProgress > infoToRender.mPreviousProgress;
					
					if(infoToRender.mProgress > 0)
					{
						firstPx = (int)(notchDistance * 5);
						lastPx = (int)(notchDistance * (infoToRender.mProgress + 5));
					}
					else
					{
						firstPx = (int)(notchDistance * (5 + infoToRender.mProgress));
						lastPx = (int)(notchDistance * 5);
					}
						
					if(isIncreasing)
					{
						GlStateManager.color(attackR, attackG, attackB, 1.0F);
						drawTexturedModalRect(j + 16 + firstPx, k + 17, 16 + (10 - scroll), 44, lastPx - firstPx, 8);
					}
					else 
					{
						GlStateManager.color(defendR, defendG, defendB, 1.0F);
						drawTexturedModalRect(j + 16 + firstPx, k + 17, 16 + scroll, 54, lastPx - firstPx, 8);
					}
					
					
					
					// Draw shield at -5 (successful defence)
					GlStateManager.color(defendR, defendG, defendB, 1.0F);
					drawTexturedModalRect(j + 4, k + 16, 4, 31, 10, 11);
					
					// Draw sword at +CompletionPoint (successful attack)
					GlStateManager.color(attackR, attackG, attackB, 1.0F);
					drawTexturedModalRect(j + 241, k + 15, 241, 31, 12, 11);
					
					GlStateManager.color(1f, 1f, 1f, 1f);
					// Draw notches at each integer interval
					for(int i = -4; i < infoToRender.mCompletionPoint; i++)
					{
						int x = (int)((i + 5) * notchDistance + 16);
						if(i == 0)
							drawTexturedModalRect(j + x - 2, k + 17, 6, 43, 5, 8);
						else 
							drawTexturedModalRect(j + x - 2, k + 17, 1, 43, 4, 8);
					}
					
					// Draw text
					mc.fontRenderer.drawStringWithShadow(infoToRender.mDefendingName, j + 6, k + 6, infoToRender.mDefendingColour);
					mc.fontRenderer.drawStringWithShadow("VS", j + xSize / 2 - mc.fontRenderer.getStringWidth("VS") / 2, k + 6, 0xffffff);
					mc.fontRenderer.drawStringWithShadow(infoToRender.mAttackingName, j + xSize - 6 - mc.fontRenderer.getStringWidth(infoToRender.mAttackingName), k + 6, infoToRender.mAttackingColour);
					
					String toWin = (infoToRender.mCompletionPoint - infoToRender.mProgress) + " to win";
					String toDefend = (infoToRender.mProgress + 5) + " to defend";
					mc.fontRenderer.drawStringWithShadow(toWin, j + xSize - 8 - mc.fontRenderer.getStringWidth(toWin), k + 32, infoToRender.mAttackingColour);
					mc.fontRenderer.drawStringWithShadow(toDefend, j + 8, k + 32, infoToRender.mAttackingColour);
				}
				
				if(mShowNewAreaTicksRemaining > 0.0f)
				{
					//mShowNewAreaTicksRemaining -= event.getPartialTicks();
					
					// Anchor point = top middle of screen
					int j = event.getResolution().getScaledWidth() / 2;
					int k = 0;
					
					int stringWidth = mc.fontRenderer.getStringWidth(mAreaMessage);
					
					float fadeOut = 2.0f * mShowNewAreaTicksRemaining / WarForgeConfig.SHOW_NEW_AREA_TIMER;
					if(fadeOut > 1.0f)
						fadeOut = 1.0f;
					
					int colour = mAreaMessageColour | ((int)(fadeOut * 255f) << 24);
					
					GlStateManager.enableAlpha();
					GlStateManager.enableBlend();
					GlStateManager.color(1f, 1f, 1f, fadeOut);
					GlStateManager.disableTexture2D();
					drawTexturedModalRect(j - stringWidth / 2 - 50, k + 42, 0, 0, stringWidth + 100, 1);
					drawTexturedModalRect(j - stringWidth / 2 - 25, k + 65, 0, 0, stringWidth + 50, 1);
					GlStateManager.enableTexture2D();
					
					mc.fontRenderer.drawStringWithShadow(mAreaMessage, j - stringWidth / 2, k + 50, colour);
					GlStateManager.disableBlend();
					GlStateManager.disableAlpha();
				}
			}
			
		}
	}
	
	private void drawTexturedModalRect(int x, int y, float u, float v, int w, int h)
	{
		float texScale = 1f / 256f;
		
		tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
		
		tess.getBuffer().pos(x, y + h, -90d)		.tex(u * texScale, (v + h) * texScale).endVertex();
		tess.getBuffer().pos(x + w, y + h, -90d)	.tex((u + w) * texScale, (v + h) * texScale).endVertex();
		tess.getBuffer().pos(x + w, y, -90d)		.tex((u + w) * texScale, (v) * texScale).endVertex();
		tess.getBuffer().pos(x, y, -90d)			.tex(u * texScale, (v) * texScale).endVertex();

		tess.draw();
	}
	
	private class BorderRenderData
	{
		public IClaim claim;
		public int renderList = -1;
	}
	
	public static boolean CLAIMS_DIRTY = false;
	private HashMap<DimChunkPos, BorderRenderData> mRenderData = new HashMap<DimChunkPos, BorderRenderData>();
    private final int renderList = GLAllocation.generateDisplayLists(1);
    
    private void RenderZAlignedSquare(int x, int y, double z, int ori)
    {
		tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
		tess.getBuffer().pos(x + 0, y + 0, z).tex(((ori + 0) / 2) % 2, ((ori + 3) / 2) % 2).endVertex();
		tess.getBuffer().pos(x + 1, y + 0, z).tex(((ori + 1) / 2) % 2, ((ori + 0) / 2) % 2).endVertex();
		tess.getBuffer().pos(x + 1, y + 1, z).tex(((ori + 2) / 2) % 2, ((ori + 1) / 2) % 2).endVertex();
		tess.getBuffer().pos(x + 0, y + 1, z).tex(((ori + 3) / 2) % 2, ((ori + 2) / 2) % 2).endVertex();
		tess.draw();
    }
    
    private void RenderXAlignedSquare(double x, int y, int z, int ori)
    {
		tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
		tess.getBuffer().pos(x, y + 0, z + 0).tex(((ori + 0) / 2) % 2, ((ori + 3) / 2) % 2).endVertex();
		tess.getBuffer().pos(x, y + 0, z + 1).tex(((ori + 1) / 2) % 2, ((ori + 0) / 2) % 2).endVertex();
		tess.getBuffer().pos(x, y + 1, z + 1).tex(((ori + 2) / 2) % 2, ((ori + 1) / 2) % 2).endVertex();
		tess.getBuffer().pos(x, y + 1, z + 0).tex(((ori + 3) / 2) % 2, ((ori + 2) / 2) % 2).endVertex();
		tess.draw();
    }
    
	private void UpdateRenderData()
	{
		World world = Minecraft.getMinecraft().world;
		if(world == null)
			return;
		
		// Update our list from the old one		
		HashMap<DimChunkPos, BorderRenderData> tempData = new HashMap<DimChunkPos, BorderRenderData>();
		
		// Find all our data entries first
		for(TileEntity te : world.loadedTileEntityList)
		{
			if(te instanceof IClaim)
			{
				DimBlockPos blockPos = ((IClaim) te).GetPos();
				DimChunkPos chunkPos = blockPos.ToChunkPos();
			
				if(mRenderData.containsKey(chunkPos))
				{
					tempData.put(chunkPos, mRenderData.get(chunkPos));
				}
				else
				{
					BorderRenderData data = new BorderRenderData();
					data.claim = (IClaim)te;
					tempData.put(chunkPos, data);
				}
			}
		}
		
		mRenderData = tempData;
		
	}
	
	private void UpdateRandomMesh()
	{	
		World world = Minecraft.getMinecraft().world;
		if(world == null || world.rand == null || mRenderData.size() == 0)
			return;
		int index = world.rand.nextInt(mRenderData.size());
				
		// Then construct the mesh for one random entry
		for(HashMap.Entry<DimChunkPos, BorderRenderData> kvp : mRenderData.entrySet())
		{
			if(index > 0)
			{
				index--;
				continue;
			}

			DimChunkPos pos = kvp.getKey();
			BorderRenderData data = kvp.getValue();
			
			data.renderList = GLAllocation.generateDisplayLists(1);
	        GlStateManager.glNewList(data.renderList, 4864);
	        
	        boolean renderNorth = true, renderEast = true, renderWest = true, renderSouth = true;
	        if(mRenderData.containsKey(pos.North()))
	        	renderNorth = !mRenderData.get(pos.North()).claim.GetFaction().equals(data.claim.GetFaction());
	        if(mRenderData.containsKey(pos.East()))
	        	renderEast = !mRenderData.get(pos.East()).claim.GetFaction().equals(data.claim.GetFaction());
	        if(mRenderData.containsKey(pos.South()))
	        	renderSouth = !mRenderData.get(pos.South()).claim.GetFaction().equals(data.claim.GetFaction());
	        if(mRenderData.containsKey(pos.West()))
	        	renderWest = !mRenderData.get(pos.West()).claim.GetFaction().equals(data.claim.GetFaction());

	        // North edge, [0,0] -> [16,0] wall
    		if(renderNorth)
    		{
    			// A smidge of semi-translucent wall from [0,0,0] to [2,256,0] offset by 0.25
    			if(renderWest)
    			{
	    			tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
					tess.getBuffer().pos(0, 0, 0.25d).tex(64f, 0.5f).endVertex();
					tess.getBuffer().pos(2, 0, 0.25d).tex(64f, 0f).endVertex();
					tess.getBuffer().pos(2, 128, 0.25d).tex(0f, 0f).endVertex();
					tess.getBuffer().pos(0, 128, 0.25d).tex(0f, 0.5f).endVertex();
					tess.draw();
    			}
				
				// A smidge of semi-translucent wall from [14,0,0] to [16,256,0] offset by 0.25
    			if(renderEast)
    			{
	    			tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
					tess.getBuffer().pos(16, 0, 0.25d).tex(64f, 0.5f).endVertex();
					tess.getBuffer().pos(14, 0, 0.25d).tex(64f, 0f).endVertex();
					tess.getBuffer().pos(14, 128, 0.25d).tex(0f, 0f).endVertex();
					tess.getBuffer().pos(16, 128, 0.25d).tex(0f, 0.5f).endVertex();
					tess.draw();
    			}
    		}
    		
    		// South edge
    		if(renderSouth)
    		{
    			if(renderWest)
    			{
	    			tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
					tess.getBuffer().pos(0, 0, 16d - 0.25d).tex(64f, 0.5f).endVertex();
					tess.getBuffer().pos(2, 0, 16d - 0.25d).tex(64f, 0f).endVertex();
					tess.getBuffer().pos(2, 128, 16d - 0.25d).tex(0f, 0f).endVertex();
					tess.getBuffer().pos(0, 128, 16d - 0.25d).tex(0f, 0.5f).endVertex();
					tess.draw();
    			}
				
    			if(renderEast)
    			{
	    			tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
					tess.getBuffer().pos(16, 0, 16d - 0.25d).tex(64f, 0.5f).endVertex();
					tess.getBuffer().pos(14, 0, 16d - 0.25d).tex(64f, 0f).endVertex();
					tess.getBuffer().pos(14, 128, 16d - 0.25d).tex(0f, 0f).endVertex();
					tess.getBuffer().pos(16, 128, 16d - 0.25d).tex(0f, 0.5f).endVertex();
					tess.draw();
    			}
    		}
    		
    		// East edge, [0,0] -> [0,16] wall
    		if(renderWest)
    		{
    			if(renderNorth)
    			{
	    			tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
					tess.getBuffer().pos(0.25d, 0, 0).tex(64f, 0.5f).endVertex();
					tess.getBuffer().pos(0.25d, 0, 2).tex(64f, 0f).endVertex();
					tess.getBuffer().pos(0.25d, 128, 2).tex(0f, 0f).endVertex();
					tess.getBuffer().pos(0.25d, 128, 0).tex(0f, 0.5f).endVertex();
					tess.draw();
    			}
				
    			if(renderSouth)
    			{
	    			tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
					tess.getBuffer().pos(0.25d, 0, 16).tex(64f, 0.5f).endVertex();
					tess.getBuffer().pos(0.25d, 0, 14).tex(64f, 0f).endVertex();
					tess.getBuffer().pos(0.25d, 128, 14).tex(0f, 0f).endVertex();
					tess.getBuffer().pos(0.25d, 128, 16).tex(0f, 0.5f).endVertex();
					tess.draw();
    			}
    		}
    		
    		// West edge
    		if(renderEast)
    		{
    			if(renderNorth)
    			{
	    			tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
					tess.getBuffer().pos(16d - 0.25d, 0, 0).tex(64f, 0.5f).endVertex();
					tess.getBuffer().pos(16d - 0.25d, 0, 2).tex(64f, 0f).endVertex();
					tess.getBuffer().pos(16d - 0.25d, 128, 2).tex(0f, 0f).endVertex();
					tess.getBuffer().pos(16d - 0.25d, 128, 0).tex(0f, 0.5f).endVertex();
					tess.draw();
    			}
				
    			if(renderSouth)
    			{
	    			tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
					tess.getBuffer().pos(16d - 0.25d, 0, 16).tex(64f, 0.5f).endVertex();
					tess.getBuffer().pos(16d - 0.25d, 0, 14).tex(64f, 0f).endVertex();
					tess.getBuffer().pos(16d - 0.25d, 128, 14).tex(0f, 0f).endVertex();
					tess.getBuffer().pos(16d - 0.25d, 128, 16).tex(0f, 0.5f).endVertex();
					tess.draw();
    			}
    		}
    		
    			
    		if(renderNorth || renderSouth)
    		{
	    		boolean airX0, airX1, airY0, airY1;
	    		
				// Then render all the block outlines wherever we go in / out of solid blocks
    			for(int x = 0; x < 16; x++)
    			{
    				for(int y = 0; y < 256; y++)
    				{
    					// Render x + 1 edge on north side
       		    		if(x < 15 && renderNorth)
       		    		{
	       					airX0 = world.isAirBlock(new BlockPos(pos.getXStart() + x, y, pos.getZStart()));
	       					airX1 = world.isAirBlock(new BlockPos(pos.getXStart() + x + 1, y, pos.getZStart()));
    			
	       					if(!airX0 && airX1)
	       						RenderZAlignedSquare(x + 1, y + 0, 0.125d, 0);
	       					if(airX0 && !airX1)
	       						RenderZAlignedSquare(x + 0, y + 0, 0.125d, 2);
       		    		}
       					// Render x + 1 edge on south side
       		    		if(x < 15 && renderSouth)
       		    		{
	       					airX0 = world.isAirBlock(new BlockPos(pos.getXStart() + x, y, pos.getZEnd()));
	       					airX1 = world.isAirBlock(new BlockPos(pos.getXStart() + x + 1, y, pos.getZEnd()));
	       					
	       					if(!airX0 && airX1)
	       						RenderZAlignedSquare(x + 1, y + 0, 16d - 0.125d, 0);
	       					if(airX0 && !airX1)
	       						RenderZAlignedSquare(x + 0, y + 0, 16d - 0.125d, 2);
       		    		}
       		    		// Render y + 1 edge on north side
       		    		if(y < 255 && renderNorth)
       		    		{
       		    			airY0 = world.isAirBlock(new BlockPos(pos.getXStart() + x, y, pos.getZStart()));
           					airY1 = world.isAirBlock(new BlockPos(pos.getXStart() + x, y + 1, pos.getZStart()));
	       					if(!airY0 && airY1)
	       						RenderZAlignedSquare(x + 0, y + 1, 0.125d, 3);
	       					if(airY0 && !airY1)
	       						RenderZAlignedSquare(x + 0, y + 0, 0.125d, 1);
       		    		}
       		    		// Render y + 1 edge on south side
       					if(y < 255 && renderSouth)
       					{
       						airY0 = world.isAirBlock(new BlockPos(pos.getXStart() + x, y, pos.getZEnd()));
       						airY1 = world.isAirBlock(new BlockPos(pos.getXStart() + x, y + 1, pos.getZEnd()));
	       					if(!airY0 && airY1)
	       						RenderZAlignedSquare(x + 0, y + 1, 16d - 0.125d, 3);
	       					if(airY0 && !airY1)
	       						RenderZAlignedSquare(x + 0, y + 0, 16d - 0.125d, 1);
       					}
    				}
    			}
	        }
    		
    		if(renderEast || renderWest)
    		{
	    		boolean airZ0, airZ1, airY0, airY1;
	    		
				// Then render all the block outlines wherever we go in / out of solid blocks
    			for(int z = 0; z < 16; z++)
    			{
    				for(int y = 0; y < 256; y++)
    				{
    					// Render z + 1 edge on east side
       		    		if(z < 15 && renderWest)
       		    		{
	       					airZ0 = world.isAirBlock(new BlockPos(pos.getXStart(), y, pos.getZStart() + z));
	       					airZ1 = world.isAirBlock(new BlockPos(pos.getXStart(), y, pos.getZStart() + z + 1));
    			
	       					if(!airZ0 && airZ1)
	       						RenderXAlignedSquare(0.125d, y + 0, z + 1, 0);
	       					if(airZ0 && !airZ1)
	       						RenderXAlignedSquare(0.125d, y + 0, z + 0, 2);
       		    		}
       					// Render z + 1 edge on west side
       		    		if(z < 15 && renderEast)
       		    		{
       		    			airZ0 = world.isAirBlock(new BlockPos(pos.getXEnd(), y, pos.getZStart() + z));
	       					airZ1 = world.isAirBlock(new BlockPos(pos.getXEnd(), y, pos.getZStart() + z + 1));
	       					
	       					if(!airZ0 && airZ1)
	       						RenderXAlignedSquare(16d - 0.125d, y + 0, z + 1, 0);
	       					if(airZ0 && !airZ1)
	       						RenderXAlignedSquare(16d - 0.125d, y + 0, z + 0, 2);
       		    		}
       		    		// Render y + 1 edge on east side
       		    		if(y < 255 && renderWest)
       		    		{
       		    			airY0 = world.isAirBlock(new BlockPos(pos.getXStart(), y, pos.getZStart() + z));
           					airY1 = world.isAirBlock(new BlockPos(pos.getXStart(), y + 1, pos.getZStart() + z));
	       					if(!airY0 && airY1)
	       						RenderXAlignedSquare(0.125d, y + 1, z, 3);
	       					if(airY0 && !airY1)
	       						RenderXAlignedSquare(0.125d, y + 0, z, 1);
       		    		}
       		    		// Render y + 1 edge on west side
       					if(y < 255 && renderEast)
       					{
       		    			airY0 = world.isAirBlock(new BlockPos(pos.getXEnd(), y, pos.getZStart() + z));
           					airY1 = world.isAirBlock(new BlockPos(pos.getXEnd(), y + 1, pos.getZStart() + z));	       					
           					if(!airY0 && airY1)
	       						RenderXAlignedSquare(16d - 0.125d, y + 1, z, 3);
	       					if(airY0 && !airY1)
	       						RenderXAlignedSquare(16d - 0.125d, y + 0, z, 1);
       					}
    				}
    			}
    		}
    		GlStateManager.glEndList();
    		break;
		}	
	}
	
	@SubscribeEvent
	public void OnRenderLast(RenderWorldLastEvent event)
	{
		// Get the player
		EntityPlayer player = Minecraft.getMinecraft().player;
		if(player == null)
			return;
		
		//Get the camera frustrum for clipping
		Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
		double x = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * event.getPartialTicks();
		double y = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * event.getPartialTicks();
		double z = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * event.getPartialTicks();
		
		//Push
		GlStateManager.pushMatrix();
		GlStateManager.pushAttrib();
		{
			//Setup lighting
			Minecraft.getMinecraft().entityRenderer.enableLightmap();
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
			GlStateManager.disableLighting();
			GlStateManager.enableTexture2D();
			GlStateManager.disableCull();
			Minecraft.getMinecraft().entityRenderer.disableLightmap();
			
			if(Minecraft.getMinecraft().isFancyGraphicsEnabled())
			{
				Minecraft.getMinecraft().renderEngine.bindTexture(texture);
				GlStateManager.enableAlpha();
				GlStateManager.enableBlend();
			}
			else
			{
				Minecraft.getMinecraft().renderEngine.bindTexture(fastTexture);
			}
			
			
		
			double skyRenderDistance = 80d;
			double groundRenderDistance = 64d;
			int resolution = 1;
	
			if(CLAIMS_DIRTY)
			{
				UpdateRenderData();
				CLAIMS_DIRTY = false;
			}
			
			// Slower update speed on fast graphics
			if(Minecraft.getMinecraft().isFancyGraphicsEnabled() || player.world.rand.nextInt(5) == 0)
				UpdateRandomMesh();
			
			// Render each chunk we have border data for
			for(HashMap.Entry<DimChunkPos, BorderRenderData> kvp : mRenderData.entrySet())
			{
				DimChunkPos pos = kvp.getKey();
				BorderRenderData data = kvp.getValue();
	
				if(data.renderList >= 0)
				{
					GlStateManager.pushMatrix();
					
					int colour = data.claim.GetColour();
		            float f = (float)(colour >> 16 & 255) / 255.0F;
		            float f1 = (float)(colour >> 8 & 255) / 255.0F;
		            float f2 = (float)(colour & 255) / 255.0F;
					GlStateManager.color(f, f1, f2, 1.0F);
					GlStateManager.translate(pos.x * 16 - x, 0 - y, pos.z * 16 - z);
		            GlStateManager.callList(data.renderList);
		            GlStateManager.popMatrix();
				}
			}
	
			// Player CanPlace? Overlay
			if(player.getHeldItemMainhand().getItem() instanceof ItemBlock)
			{
				boolean shouldRender = false;
				Block holding = ((ItemBlock)player.getHeldItemMainhand().getItem()).getBlock();
				if(holding == WarForgeMod.CONTENT.basicClaimBlock
				|| holding == WarForgeMod.CONTENT.citadelBlock
				|| holding == WarForgeMod.CONTENT.reinforcedClaimBlock)
				{
					shouldRender = true;
				}
				
				// Ray trace player hand to see which chunk they looking at
				DimChunkPos playerPos = new DimChunkPos(player.dimension, player.getPosition());
				RayTraceResult result = player.rayTrace(10.0f, event.getPartialTicks());
				if(result != null && result.typeOfHit == RayTraceResult.Type.BLOCK)
				{
					playerPos = new DimChunkPos(player.dimension, result.getBlockPos());
				}
				
				boolean canPlace = true;
				List<DimChunkPos> siegeablePositions = new ArrayList<DimChunkPos>();
				for(TileEntity te : Minecraft.getMinecraft().world.loadedTileEntityList)
				{
					if(te instanceof IClaim)
					{
						DimBlockPos blockPos = ((IClaim) te).GetPos();
						DimChunkPos chunkPos = blockPos.ToChunkPos();
						
						if(playerPos.x == chunkPos.x && playerPos.z == chunkPos.z)
						{
							canPlace = false;
						}
						if(((IClaim)te).CanBeSieged())	
							siegeablePositions.add(chunkPos);
					}
				}
				if(holding == WarForgeMod.CONTENT.siegeCampBlock)
				{
					shouldRender = true;
					if(canPlace)
					{
						canPlace = false;
						for(EnumFacing facing : EnumFacing.HORIZONTALS)
						{
							if(siegeablePositions.contains(playerPos.Offset(facing, 1)))
							{
								canPlace = true;
							}
						}
					}
				}
				
				if(shouldRender)
				{					
					// Render overlay
					if(canPlace)
						GlStateManager.color(0f, 1f, 0f, 1.0F);
					else
						GlStateManager.color(1f, 0f, 0f, 1.0F);
						
					Minecraft.getMinecraft().renderEngine.bindTexture(overlayTex);
					
					GlStateManager.translate(playerPos.x * 16 - x, 0 - y, playerPos.z * 16 - z);
					for(int i = 0; i < 16; i++)
					{
						for(int k = 0; k < 16; k++)
						{
							BlockPos pos = new BlockPos(playerPos.x * 16 + i, player.posY, playerPos.z * 16 + k);
							
							//pos = player.world.getHeight(pos);
							for(; pos.getY() > 0 && player.world.isAirBlock(pos); pos = pos.down())
							{ 
							}
							
							tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX);
							
							tess.getBuffer().pos(i + 0, pos.getY() + 1.5d, k + 0).tex(0f, 0f).endVertex();
							tess.getBuffer().pos(i + 1, pos.getY() + 1.5d, k + 0).tex(1f, 0f).endVertex();
							tess.getBuffer().pos(i + 1, pos.getY() + 1.5d, k + 1).tex(1f, 1f).endVertex();
							tess.getBuffer().pos(i + 0, pos.getY() + 1.5d, k + 1).tex(0f, 1f).endVertex();
	
							tess.draw();
						}
					}
				}
			}
		
			// Flag rendering
			
			GlStateManager.disableLighting();
			GlStateManager.disableAlpha();
			GlStateManager.enableTexture2D();
			GlStateManager.disableBlend();
			GlStateManager.enableCull();
			GlStateManager.color(1f, 1f, 1f);
			
			for(TileEntity te : Minecraft.getMinecraft().world.loadedTileEntityList)
			{
				if(te instanceof TileEntityCitadel)
				{
					TileEntityCitadel citadel = (TileEntityCitadel)te;
					DimBlockPos blockPos = ((IClaim) te).GetPos();
					
					double distance = Math.sqrt((blockPos.getX() - x)*(blockPos.getX() - x)+(blockPos.getY() - y)*(blockPos.getY() - y)+(blockPos.getZ() - z)*(blockPos.getZ() - z));					
					double groundLevelBlend = (skyRenderDistance - distance) / (skyRenderDistance - groundRenderDistance);
					
					if(groundLevelBlend < 0.0d)
						groundLevelBlend = 0.0d;
					
					if(groundLevelBlend > 1.0d)
						groundLevelBlend = 1.0d;
					
					groundLevelBlend = groundLevelBlend * groundLevelBlend * (3 - 2 * groundLevelBlend);
				
					
					ItemStack bannerStack = citadel.getStackInSlot(TileEntityCitadel.BANNER_SLOT_INDEX);
					if(bannerStack.isEmpty())
					{
						
					}
					else if(mBannerTextures.containsKey(bannerStack))
					{
						Minecraft.getMinecraft().renderEngine.bindTexture(mBannerTextures.get(bannerStack));
					}
					else if(bannerStack.getItem() instanceof ItemBanner)
					{
						ItemBanner banner = (ItemBanner)bannerStack.getItem();
					    
			        	// Start with base colour
						EnumDyeColor baseColour = ItemBanner.getBaseColor(bannerStack);
			        	String patternResourceLocation = "b" + baseColour.getDyeDamage();
			        	List<BannerPattern> patternList = Lists.<BannerPattern>newArrayList();
					    List<EnumDyeColor> colorList = Lists.<EnumDyeColor>newArrayList();
					    
		                patternList.add(BannerPattern.BASE);
		                colorList.add(baseColour);
					    
			        	// Then append patterns
				        if (bannerStack.hasTagCompound() && bannerStack.getTagCompound().hasKey("Patterns", 9))
				        {
				        	NBTTagList patterns = bannerStack.getTagCompound().getTagList("Patterns", 10).copy();
			                if (patterns != null)
			                {
			                    for (int p = 0; p < patterns.tagCount(); p++)
			                    {
			                        NBTTagCompound nbttagcompound = patterns.getCompoundTagAt(p);
			                        BannerPattern bannerpattern = BannerPattern.byHash(nbttagcompound.getString("Pattern"));
	
			                        if (bannerpattern != null)
			                        {
			                            patternList.add(bannerpattern);
			                            int j = nbttagcompound.getInteger("Color");
			                            colorList.add(EnumDyeColor.byDyeDamage(j));
			                            patternResourceLocation = patternResourceLocation + bannerpattern.getHashname() + j;
			                        }
			                    }
			                }
				        }
				        
				        
						ResourceLocation resLoc = BannerTextures.BANNER_DESIGNS.getResourceLocation(patternResourceLocation, patternList, colorList);
						mBannerTextures.put(bannerStack, resLoc);
						Minecraft.getMinecraft().renderEngine.bindTexture(resLoc);
						
						 GlStateManager.pushMatrix();
				            
			            double deltaX = te.getPos().getX() - x;
			            double deltaZ = te.getPos().getZ() - z;
			            
			            float angle = (float)Math.atan2(deltaZ, deltaX) * 180f / (float)Math.PI + 90f;
			            
			            double yPos = te.getPos().getY() + 2d;
			            yPos = 256 + (yPos - 256) * groundLevelBlend;
			            float scale = (float)(1d * groundLevelBlend + 10d * (1d - groundLevelBlend));
			            
			            GlStateManager.translate(0.5d + te.getPos().getX() - x, yPos - y, 0.5d + te.getPos().getZ() - z);
			            GlStateManager.scale(scale, -scale, -scale);
			            GlStateManager.rotate(angle, 0f, 1f, 0f);
			            this.bannerModel.renderBanner();
			            GlStateManager.popMatrix();
					}
				}
			}
		
			//Reset Lighting
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
			GlStateManager.disableLighting();
		}
		GlStateManager.popAttrib();
		GlStateManager.popMatrix();
	}
	
	private HashMap<ItemStack, ResourceLocation> mBannerTextures = new HashMap<ItemStack, ResourceLocation>();
	
	private void VertexAt(DimChunkPos chunkPos, World world, int x, int z, double groundLevelBlend, double playerHeight)
	{
		double topHeight = playerHeight + 128;
		
		double maxHeight = world.getHeight(chunkPos.x * 16 + x, chunkPos.z * 16 + z) + 8;
		if(maxHeight > playerHeight + 16)
			maxHeight = playerHeight + 16;

		double height = topHeight + (maxHeight - topHeight) * groundLevelBlend;
		
		tess.getBuffer().pos(x, height, z).tex(z / 16f, x / 16f).endVertex();
	}
}

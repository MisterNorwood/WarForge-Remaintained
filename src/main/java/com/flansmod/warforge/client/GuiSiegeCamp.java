package com.flansmod.warforge.client;

import java.util.List;
import java.util.UUID;

import com.flansmod.warforge.common.DimBlockPos;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.TileEntitySiegeCamp;
import com.flansmod.warforge.common.network.PacketPlaceFlag;
import com.flansmod.warforge.common.network.PacketStartSiege;
import com.flansmod.warforge.common.network.SiegeCampAttackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

/*
	Controls gui to start raid, not on screen siege progression
 */
public class GuiSiegeCamp extends GuiScreen
{
	private static final ResourceLocation texture = new ResourceLocation(WarForgeMod.MODID, "gui/siegemenu.png");
	private int xSize, ySize;
	private TileEntitySiegeCamp mSiegeCamp;
	private List<SiegeCampAttackInfo> mAttackInfo;
	
	private static final int BUTTON_NORTH = 0;
	private static final int BUTTON_EAST = 1;
	private static final int BUTTON_SOUTH = 2;
	private static final int BUTTON_WEST = 3;
	// private static final int BUTTON_SET_FLAG = 4;
	
	private GuiButton northButton, eastButton, southButton, westButton;
        
	public GuiSiegeCamp(DimBlockPos siegeCampPos, List<SiegeCampAttackInfo> possibleAttacks)
	{
		mSiegeCamp = (TileEntitySiegeCamp)Minecraft.getMinecraft().world.getTileEntity(siegeCampPos.ToRegularPos());
		if(mSiegeCamp == null)
			Minecraft.getMinecraft().displayGuiScreen(null);
		mAttackInfo = possibleAttacks;
		
    	xSize = 141;
    	ySize = 180;
	}

	@Override
	public void initGui()
	{
		super.initGui();
				
		int j = width / 2 - xSize / 2;
		int k = height / 2 - ySize / 2;
		
		if(ClientProxy.sSiegeInfo.containsKey(mSiegeCamp.GetPos()))
		{
			GuiButton setFlagButton = new GuiButton(4, j + 16, k + 86, 104, 20, "Place Flag Here");
			buttonList.add(setFlagButton);
		}
		else
		{	
			// North Button
			northButton = new GuiButton(BUTTON_NORTH, j + 16, k + 86, 104, 20, "Attack North");
			buttonList.add(northButton);
			
			// East Button
			eastButton = new GuiButton(BUTTON_EAST, j + 16, k + 108, 104, 20, "Attack East");
			buttonList.add(eastButton);
			
			// South Button
			southButton = new GuiButton(BUTTON_SOUTH, j + 16, k + 130, 104, 20, "Attack South");
			buttonList.add(southButton);
			
			// West Button
			westButton = new GuiButton(BUTTON_WEST, j + 16, k + 152, 104, 20, "Attack West");
			buttonList.add(westButton);
			
			northButton.enabled = false;
			eastButton.enabled = false;
			southButton.enabled = false;
			westButton.enabled = false;
			
			for(SiegeCampAttackInfo info : mAttackInfo)
			{
				switch(info.mDirection)
				{
					case NORTH: northButton.enabled = true; break;
					case EAST: eastButton.enabled = true; break;
					case SOUTH: southButton.enabled = true; break;
					case WEST: westButton.enabled = true; break;
					default: break;
				}
			}
		}
	}
	
	@Override
	protected void actionPerformed(GuiButton button)
	{
		if(button.id == 4)
		{
			PacketPlaceFlag packet = new PacketPlaceFlag();
			packet.pos = mSiegeCamp.GetPos();
			WarForgeMod.INSTANCE.NETWORK.sendToServer(packet);
			mc.displayGuiScreen(null);
		}
		else
		{
			PacketStartSiege siegePacket = new PacketStartSiege();
				
			siegePacket.mSiegeCampPos = mSiegeCamp.GetPos();
			switch(button.id)
			{
				case BUTTON_NORTH: siegePacket.mDirection = EnumFacing.NORTH; break;
				case BUTTON_EAST: siegePacket.mDirection = EnumFacing.EAST; break;
				case BUTTON_SOUTH: siegePacket.mDirection = EnumFacing.SOUTH; break;
				case BUTTON_WEST: siegePacket.mDirection = EnumFacing.WEST; break;
			}	
			
			WarForgeMod.INSTANCE.NETWORK.sendToServer(siegePacket);
			mc.displayGuiScreen(null);
		}
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
		
		for(SiegeCampAttackInfo info : mAttackInfo)
		{
			float f = (float)(info.mFactionColour >> 16 & 255) / 255.0F;
            float f1 = (float)(info.mFactionColour >> 8 & 255) / 255.0F;
            float f2 = (float)(info.mFactionColour & 255) / 255.0F;
			GlStateManager.color(f, f1, f2, 1.0F);
			
			// Draw an outline in faction colour
			drawTexturedModalRect(j + 54 + info.mDirection.getXOffset() * 22, k + 36 + info.mDirection.getZOffset() * 22, 141, 0, 22, 22);
			
		}
		
		// Then draw overlay
		super.drawScreen(mouseX, mouseY, partialTicks);
		
		//fontRenderer.drawStringWithShadow("Enter Name:",  j + 6, k + 6, 0xffffff);
	}
	
	@Override
	public boolean doesGuiPauseGame()
	{
		return false;
	}
}

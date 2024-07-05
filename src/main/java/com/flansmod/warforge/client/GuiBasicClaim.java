package com.flansmod.warforge.client;

import com.flansmod.warforge.common.CommonProxy;
import com.flansmod.warforge.common.ContainerBasicClaim;
import com.flansmod.warforge.common.ContainerCitadel;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketMoveCitadel;
import com.flansmod.warforge.common.network.PacketPlaceFlag;
import com.flansmod.warforge.common.network.PacketRemoveClaim;
import com.flansmod.warforge.server.Faction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Container;
import net.minecraft.util.ResourceLocation;

public class GuiBasicClaim extends GuiContainer
{
	private static final ResourceLocation texture = new ResourceLocation(WarForgeMod.MODID, "gui/citadelmenu.png");

	private static final int BUTTON_INFO = 0;
	private static final int BUTTON_REMOVE_CLAIM = 1;
	private static final int BUTTON_PLACE_FLAG = 2;
	private static final int BUTTON_MOVE_CITADEL = 3;
	public ContainerBasicClaim claimContainer;
	
	public GuiBasicClaim(Container container) 
	{
		super(container);
		claimContainer = (ContainerBasicClaim)container;
		
		ySize = 182;
	}

	@Override
	public void initGui()
	{
		super.initGui();
						
		//Info Button
		GuiButton infoButton = new GuiButton(BUTTON_INFO, width / 2 - 20, height / 2 - 48, 48, 20, "Info");
		buttonList.add(infoButton);

		GuiButton removeClaimButton = new GuiButton(BUTTON_REMOVE_CLAIM, width / 2 + 32, height / 2 - 48, 48, 20, "Unclaim");
		buttonList.add(removeClaimButton);
		
		GuiButton moveCitadelButton = new GuiButton(BUTTON_MOVE_CITADEL, width / 2 - 20, height / 2 - 26, 100, 20, "Move Citadel");
		buttonList.add(moveCitadelButton);
		
		GuiButton placeFlagButton = new GuiButton(BUTTON_PLACE_FLAG, width / 2 - 20, height / 2 - 70, 100, 20, "Place Flag");
		buttonList.add(placeFlagButton);
		if(claimContainer.claim.GetPlayerFlags().contains(Minecraft.getMinecraft().getSession().getUsername()))
		{
			placeFlagButton.enabled = false;
		}
	}
	
	@Override
	protected void actionPerformed(GuiButton button)
	{
		switch(button.id)
		{
			case BUTTON_INFO:
			{
				ClientProxy.RequestFactionInfo(claimContainer.claim.GetFaction());
				break;
			}
			case BUTTON_REMOVE_CLAIM:
			{
				PacketRemoveClaim packet = new PacketRemoveClaim();
				packet.pos = claimContainer.claim.GetPos();
				WarForgeMod.NETWORK.sendToServer(packet);
				
				Minecraft.getMinecraft().displayGuiScreen(null);
				break;
			}
			case BUTTON_PLACE_FLAG:
			{
				PacketPlaceFlag packet = new PacketPlaceFlag();
				packet.pos = claimContainer.claim.GetPos();
				WarForgeMod.NETWORK.sendToServer(packet);
				
				Minecraft.getMinecraft().displayGuiScreen(null);
				break;
			}
			case BUTTON_MOVE_CITADEL:
			{
				PacketMoveCitadel packet = new PacketMoveCitadel();
				packet.pos = claimContainer.claim.GetPos();
				WarForgeMod.NETWORK.sendToServer(packet);
				
				Minecraft.getMinecraft().displayGuiScreen(null);
				break;
			}
		}	
	}
	
	@Override
	protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY)
	{
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		mc.renderEngine.bindTexture(texture);
		int j = (width - xSize) / 2;
		int k = (height - ySize) / 2;
		drawTexturedModalRect(j, k, 0, 0, xSize, ySize);
		// Paste over the banner slot as we are reusing the citadel screen
		drawTexturedModalRect(j + 151, k + 67, 133, 67, 18, 18);
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int x, int y)
	{
		super.drawGuiContainerForegroundLayer(x, y);
		
		fontRenderer.drawString(claimContainer.claim.GetDisplayName(), 6, 6, 0x404040);

		fontRenderer.drawString("Yields", 6, 20, 0x404040);
		fontRenderer.drawString("Inventory", 8, (ySize - 96) + 2, 0x404040);
	}
	
	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks)
	{
		super.drawScreen(mouseX, mouseY, partialTicks);
		renderHoveredToolTip(mouseX, mouseY);
	}
	
	@Override
	public boolean doesGuiPauseGame()
	{
		return false;
	}
}

package com.flansmod.warforge.client;

import java.awt.Color;
import java.io.IOException;

import org.lwjgl.input.Mouse;

import com.flansmod.warforge.common.DimBlockPos;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.TileEntityCitadel;
import com.flansmod.warforge.common.network.PacketCreateFaction;
import com.flansmod.warforge.common.network.PacketSetFactionColour;
import com.flansmod.warforge.server.Faction;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.FMLClientHandler;

public class GuiCreateFaction extends GuiScreen
{
	private static final ResourceLocation texture = new ResourceLocation(WarForgeMod.MODID, "gui/citadelmenu.png");

	private static final int BUTTON_CREATE = 0;
	private static final int BUTTON_CANCEL = 1;
	private static final int TEXT_FIELD_NAME = 2;
	private static final int BUTTON_RECOLOUR = 3;
	
    protected GuiTextField inputField;
    private GuiButton createButton, cancelButton;
	private TileEntityCitadel citadel;
	
	private int xSize, ySize;
	private float componentBarLength = 68.0f;
	private float[] currentHSB = new float[3];
	private boolean isRecolourGUI = false;
    
    public GuiCreateFaction(TileEntityCitadel tile, boolean isRecolour)
    {
    	citadel = tile;
    	Color.RGBtoHSB((citadel.mColour >> 16) & 0xff, (citadel.mColour >> 8) & 0xff, (citadel.mColour >> 0) & 0xff, currentHSB);
    	xSize = 176;
    	ySize = 56;
    	isRecolourGUI = isRecolour;
    }
    
	@Override
	public void initGui()
	{
		super.initGui();
				
		// Create button
		if(isRecolourGUI)
		{
			GuiButton recolourButton = new GuiButton(BUTTON_RECOLOUR, width / 2 - xSize / 2 + 6, height / 2 - 22, 80, 20, "Set Colour");
			buttonList.add(recolourButton);
			
			// Cancel Button
			cancelButton = new GuiButton(BUTTON_CANCEL, width / 2 - xSize / 2 + 6, height / 2 + 2, 80, 20, "Cancel");
			buttonList.add(cancelButton);
		}
		else
		{
			createButton = new GuiButton(BUTTON_CREATE, width / 2 - xSize / 2 + 6, height / 2 + 2, 40, 20, "Create");
			buttonList.add(createButton);
			
			// Cancel Button
			cancelButton = new GuiButton(BUTTON_CANCEL, width / 2 - xSize / 2 + 50, height / 2 + 2, 40, 20, "Cancel");
			buttonList.add(cancelButton);
		}
		
		if(!isRecolourGUI)
		{
			inputField = new GuiTextField(TEXT_FIELD_NAME, fontRenderer, width / 2 - xSize / 2 + 6, height / 2 - 12, 84, 20);
			inputField.setMaxStringLength(64);
	        inputField.setEnableBackgroundDrawing(false);
	        inputField.setFocused(true);
	        inputField.setText("");
	        inputField.setCanLoseFocus(false);
		}
	}
	
	@Override
	protected void actionPerformed(GuiButton button)
	{
		switch(button.id)
		{
			case BUTTON_CREATE:
			{
				// Send request to server
				PacketCreateFaction packet = new PacketCreateFaction();
				packet.mCitadelPos = new DimBlockPos(citadel.getWorld().provider.getDimension(), citadel.getPos());
				packet.mFactionName = inputField.getText();
				packet.mColour = Color.HSBtoRGB(currentHSB[0], currentHSB[1], currentHSB[2]);
				WarForgeMod.INSTANCE.NETWORK.sendToServer(packet);
				mc.displayGuiScreen(null);
				
				break;
			}
			case BUTTON_RECOLOUR:
			{
				PacketSetFactionColour packet = new PacketSetFactionColour();
				packet.mColour = Color.HSBtoRGB(currentHSB[0], currentHSB[1], currentHSB[2]);
				WarForgeMod.INSTANCE.NETWORK.sendToServer(packet);
				mc.displayGuiScreen(null);
				
				break;
			}
			case BUTTON_CANCEL:
			{
				// Just close this GUI, no further action
				mc.displayGuiScreen(null);
				break;
			}
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
		drawTexturedModalRect(j, k, 0, 182, xSize, ySize);
		
		// Then draw overlay
		super.drawScreen(mouseX, mouseY, partialTicks);
		
		// RGB Slider bars
		GlStateManager.disableTexture2D();
		float scale = 1.0f / 256.0f;
		
		
		//float[] hsb = new float[3];
		//Color.RGBtoHSB((currentColour >> 16) & 0xff, (currentColour >> 8) & 0xff, (currentColour >> 0) & 0xff, hsb);
		
		int currentRgb = Color.HSBtoRGB(currentHSB[0], currentHSB[1], currentHSB[2]);
		float red = scale * ((currentRgb >> 16) & 0xff);
		float green = scale * ((currentRgb >> 8) & 0xff);
		float blue = scale * ((currentRgb >> 0) & 0xff);
		
		GlStateManager.color(red, green, blue);
		drawTexturedModalRect(j + 153, k + 5, 0, 0, 11, 10);
		
		for(int n = 0; n < componentBarLength; n++)
		{
			int rgb = Color.HSBtoRGB((float)n / componentBarLength, currentHSB[1], currentHSB[2]);
			GlStateManager.color(scale * ((rgb >> 16) & 0xff), scale * ((rgb >> 8) & 0xff), scale * ((rgb >> 0) & 0xff));
			//GlStateManager.color((float)n / componentBarLength, green, blue);
			drawTexturedModalRect(j + 103 + n, k + 17, 0, 0, 1, 10);
		}
		for(int n = 0; n < componentBarLength; n++)
		{
			int rgb = Color.HSBtoRGB(currentHSB[0], (float)n / componentBarLength, currentHSB[2]);
			GlStateManager.color(scale * ((rgb >> 16) & 0xff), scale * ((rgb >> 8) & 0xff), scale * ((rgb >> 0) & 0xff));
			//GlStateManager.color(red, (float)n / componentBarLength, blue);
			drawTexturedModalRect(j + 103 + n, k + 29, 0, 0, 1, 10);
		}
		for(int n = 0; n < componentBarLength; n++)
		{
			int rgb = Color.HSBtoRGB(currentHSB[0], currentHSB[1], (float)n / componentBarLength);
			GlStateManager.color(scale * ((rgb >> 16) & 0xff), scale * ((rgb >> 8) & 0xff), scale * ((rgb >> 0) & 0xff));
			//GlStateManager.color(red, green, (float)n / componentBarLength);
			drawTexturedModalRect(j + 103 + n, k + 41, 0, 0, 1, 10);
		}
		GlStateManager.enableTexture2D();
		
		GlStateManager.enableAlpha();
		GlStateManager.color(1.0f, 1.0f, 1.0f);
		// Sliders
		mc.renderEngine.bindTexture(texture);
		drawTexturedModalRect(j + 103 + (int)(currentHSB[0] * componentBarLength), k + 16, 176, 0, 3, 12);
		drawTexturedModalRect(j + 103 + (int)(currentHSB[1] * componentBarLength), k + 28, 176, 0, 3, 12);
		drawTexturedModalRect(j + 103 + (int)(currentHSB[2] * componentBarLength), k + 40, 176, 0, 3, 12);

		
		if(!isRecolourGUI)
		{
			inputField.drawTextBox();
			fontRenderer.drawStringWithShadow("Enter Name:",  j + 6, k + 6, 0xffffff);
		}
		fontRenderer.drawStringWithShadow("Colour:",  j + 106, k + 6, 0xffffff);
	}
	
	@Override
    public void updateScreen()
    {
		if(!isRecolourGUI)
			inputField.updateCursorCounter();
    }
	
	@Override
	public void handleMouseInput() throws IOException
	{
		super.handleMouseInput();
		
		int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
		int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;

		int guiLeft = (this.width - this.xSize) / 2;
        int guiTop = (this.height - this.ySize) / 2;
        
        int xOrigin = 0;
        int yOrigin = 0;
		
		int mouseXInGUI = mouseX - guiLeft;
		int mouseYInGUI = mouseY - guiTop;

		//if(Mouse.getEventButton() == 0)// && Mouse.getEventButtonState())
		if(Mouse.isButtonDown(0))
		{
			// Red bar
			if(mouseXInGUI >= xOrigin + 103 && mouseXInGUI < xOrigin + 103 + componentBarLength 
					&& mouseYInGUI >= yOrigin + 17 && mouseYInGUI < yOrigin + 27)
			{
				currentHSB[0] = (mouseXInGUI - (xOrigin + 103)) / componentBarLength;				
				//int red = (int)(((mouseXInGUI - (xOrigin + 103)) * 0xff) / componentBarLength);
				//currentColour &= 0x00ffff; // Clear red component
				//currentColour |= (red << 16);
			}
			// Green bar
			if(mouseXInGUI >= xOrigin + 103 && mouseXInGUI < xOrigin + 103 + componentBarLength 
					&& mouseYInGUI >= yOrigin + 29 && mouseYInGUI < yOrigin + 39)
			{
				currentHSB[1] = (mouseXInGUI - (xOrigin + 103)) / componentBarLength;				
				
				//int green = (int)(((mouseXInGUI - (xOrigin + 103)) * 0xff) / componentBarLength);
				//currentColour &= 0xff00ff; // Clear green component
				//currentColour |= (green << 8);
			}
			// Blue bar
			if(mouseXInGUI >= xOrigin + 103 && mouseXInGUI < xOrigin + 103 + componentBarLength 
					&& mouseYInGUI >= yOrigin + 41 && mouseYInGUI < yOrigin + 51)
			{
				currentHSB[2] = (mouseXInGUI - (xOrigin + 103)) / componentBarLength;				
				//int blue = (int)(((mouseXInGUI - (xOrigin + 103)) * 0xff) / componentBarLength);
				//currentColour &= 0xffff00; // Clear blue component
				//currentColour |= (blue << 0);
			}
		}
	}
	
	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException
    {
		if(!isRecolourGUI)
			inputField.textboxKeyTyped(typedChar, keyCode);

        if (keyCode != 28 && keyCode != 156)
        {
            if (keyCode == 1)
            {
                actionPerformed(cancelButton);
            }
        }
        else
        {
            actionPerformed(createButton);
        }
    }
	
	@Override
	public boolean doesGuiPauseGame()
	{
		return false;
	}
}

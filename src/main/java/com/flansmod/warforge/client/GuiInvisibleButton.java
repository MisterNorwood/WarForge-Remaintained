package com.flansmod.warforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

public class GuiInvisibleButton extends GuiButton
{   
	public GuiInvisibleButton(int buttonId, int x, int y, String buttonText)
	{
		super(buttonId, x, y, buttonText);
	}

	public GuiInvisibleButton(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText) 
	{
		super(buttonId, x, y, widthIn, heightIn, buttonText);
	}
	
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks)
    {
    	// Don't draw
    }

}

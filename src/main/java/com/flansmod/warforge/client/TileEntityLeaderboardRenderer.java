package com.flansmod.warforge.client;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.TileEntityLeaderboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

public class TileEntityLeaderboardRenderer extends TileEntitySpecialRenderer<TileEntityLeaderboard>
{
	@Override
	public void render(TileEntityLeaderboard te, double x, double y, double z, float partialTicks, int destroyStage, float f)
	{
		GlStateManager.pushMatrix();
        GlStateManager.translate((float)x, (float)y + 1.0F, (float)z + 1.0F);
        GlStateManager.scale(1.0F, -1.0F, -1.0F);
        GlStateManager.translate(0.0F, 0.0F, 0.0F);
        
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        
        float mcScale = 1f / 16f;
        GlStateManager.scale(mcScale, mcScale, mcScale);
        
        // x=3 to x=13
        // y=3 to y=13
        GlStateManager.translate(3, 3, 6.25f);
        int fontArea = 13 - 3;
        
        // Font allowed area is now 0-80 font px
        float fontPxPerMcPx = 8;
        fontArea *= fontPxPerMcPx;
        float textScale = 1f / fontPxPerMcPx;
        GlStateManager.scale(textScale, textScale, textScale);
        
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        
        String type = "Bah";
		if(te.getBlockType() == WarForgeMod.CONTENT.topLeaderboardBlock)
			type = "Top";
		else if(te.getBlockType() == WarForgeMod.CONTENT.wealthLeaderboardBlock)
			type = "Wealth";
		else if(te.getBlockType() == WarForgeMod.CONTENT.notorietyLeaderboardBlock)
			type = "Notoriety";
		else if(te.getBlockType() == WarForgeMod.CONTENT.legacyLeaderboardBlock)
			type = "Legacy";
		
		font.drawStringWithShadow(type, fontArea / 2 - font.getStringWidth(type) / 2, 0, 0xffffff);
		font.drawStringWithShadow("Leaderboard", fontArea / 2 - font.getStringWidth("Leaderboard") / 2, 8, 0xffffff);
				
		for(int i = 0; i < TileEntityLeaderboard.NUM_ENTRIES - 1; i++)
		{
			font.drawString("#" + (i + 1), 0, 20 + 10 * i, 0xffffff);
			font.drawString(te.topNames[i], fontArea - font.getStringWidth(te.topNames[i]), 20 + 10 * i, 0xffffff);
		}
		
		GlStateManager.depthFunc(515);
		GlStateManager.popMatrix();
	}
}

package com.flansmod.warforge.client;

import java.util.List;
import java.util.UUID;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.TileEntityClaim;
import com.flansmod.warforge.common.blocks.TileEntityLeaderboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;

public class TileEntityClaimRenderer extends TileEntitySpecialRenderer<TileEntityClaim>
{
	@Override
	public void render(TileEntityClaim te, double x, double y, double z, float partialTicks, int destroyStage, float f)
	{
		Tessellator tessellator = Tessellator.getInstance();
    	BufferBuilder buff = tessellator.getBuffer();
    	List<String> flags = te.GetPlayerFlags();
    	 
		GlStateManager.pushMatrix();
        GlStateManager.translate((float)x, (float)y + 1.0F, (float)z);
        //GlStateManager.scale(1.0F, 1.0F, 1.0F);
        GlStateManager.translate(0.0F, 0.0F, 0.0F);
        
        float mcScale = 1f;
        GlStateManager.scale(mcScale, mcScale, mcScale);
        
        // x=3 to x=13
        // y=3 to y=13
        //GlStateManager.translate(3, 3, 6.25f);
        //int fontArea = 13 - 3;
        
        // Font allowed area is now 0-80 font px
        //float fontPxPerMcPx = 8;
        //fontArea *= fontPxPerMcPx;
        //float textScale = 1f / fontPxPerMcPx;
        //GlStateManager.scale(textScale, textScale, textScale);
        
        double poleH = flags.size() * 1.2d + 1.2d;
        
        GlStateManager.pushMatrix();
        {
        	GlStateManager.disableTexture2D();
            GlStateManager.enableLighting();
        	GlStateManager.enableCull();
        	GlStateManager.color(0.9f, 0.9f, 0.9f);
        	GlStateManager.translate(0.5F, 0.0F, 0.5F);
        	
        	buff.begin(6, DefaultVertexFormats.POSITION_NORMAL);
	        buff.pos(-0.1, 0, 0.1).normal(0,0,1).endVertex();
	        buff.pos(0.1, 0, 0.1).normal(0,0,1).endVertex();
	        buff.pos(0.1, poleH, 0.1).normal(0,0,1).endVertex();
	        buff.pos(-0.1, poleH, 0.1).normal(0,0,1).endVertex();
	        tessellator.draw();
	       
	        buff.begin(6, DefaultVertexFormats.POSITION_NORMAL);
	        buff.pos(0.1, poleH, -0.1).normal(0,0,-1).endVertex();
	        buff.pos(0.1, 0, -0.1).normal(0,0,-1).endVertex();
	        buff.pos(-0.1, 0, -0.1).normal(0,0,-1).endVertex();
	        buff.pos(-0.1, poleH, -0.1).normal(0,0,-1).endVertex();
	        tessellator.draw();
	        
	        GlStateManager.color(0.85f, 0.85f, 0.85f);
	        
	        buff.begin(6, DefaultVertexFormats.POSITION_NORMAL);
	        buff.pos(0.1, poleH, 0.1).normal(1,0,0).endVertex();
	        buff.pos(0.1, 0, 0.1).normal(1,0,0).endVertex();
	        buff.pos(0.1, 0, -0.1).normal(1,0,0).endVertex();
	        buff.pos(0.1, poleH, -0.1).normal(1,0,0).endVertex();
	        tessellator.draw();
	        
	        buff.begin(6, DefaultVertexFormats.POSITION_NORMAL);
	        buff.pos(-0.1, poleH, 0.1).normal(-1,0,0).endVertex();
	        buff.pos(-0.1, poleH, -0.1).normal(-1,0,0).endVertex();
	        buff.pos(-0.1, 0, -0.1).normal(-1,0,0).endVertex();
	        buff.pos(-0.1, 0, 0.1).normal(-1,0,0).endVertex();
	        tessellator.draw();
	        
        }
        GlStateManager.popMatrix();
        
        // Flag backgrounds
        GlStateManager.pushMatrix();
        {
           

        	GlStateManager.translate(0.5F, 0.0F, 0.5F);
        	
	        float r = (float)(te.mColour >> 16 & 255) / 255.0F;
	        float g = (float)(te.mColour >> 8 & 255) / 255.0F;
	        float b = (float)(te.mColour & 255) / 255.0F;
	        
	        for(int i = 0; i < flags.size(); i++)
	        {
	        	GlStateManager.translate(0.0F, 1.2F, 0.0F);
	        	
	        	 GlStateManager.disableTexture2D();
	         	GlStateManager.enableLighting();
	         	GlStateManager.disableCull();
	        	
		        GlStateManager.color(r, g, b);
		        
		        int numSegments = 10;
		        int numVerts = numSegments + 1;
		        float length = 1.5f;
		        float waveSpeed = 0.1f;
		        float waveSize = 0.5f;
		        
		        float time = waveSpeed * (Minecraft.getMinecraft().player == null ? 0 : Minecraft.getMinecraft().player.ticksExisted + partialTicks);
		        
		        buff.begin(5, DefaultVertexFormats.POSITION_NORMAL);
		        for(int n = 0; n < numVerts; n++)
		        {
		        	float sin = (float)Math.sin(time + n * 0.3f + i * Math.PI * 0.5f);
		        	float cos = (float)Math.cos(time + n * 0.3f + i * Math.PI * 0.5f);
		        	double zOffset = waveSize * sin * n / numSegments;
		        	
		        	buff.pos(length * n / (float)numSegments, 1, zOffset).normal(sin, 0, cos).endVertex();
		        	buff.pos(length * n / (float)numSegments, 0, zOffset).normal(sin, 0, cos).endVertex();
		        }
		        tessellator.draw();
		        
		        // Draw to the middle segments
		        int numFaceSegments = 4;
		        int numFaceVerts = numFaceSegments + 1;
		        float height = length * (float)numFaceSegments / (float)numSegments;
	
		        // Render face
		        GlStateManager.enableTexture2D();
		        GlStateManager.enableCull();
		        GlStateManager.color(1f, 1f, 1f);
		        
	        	ResourceLocation playerFace = GuiFactionInfo.GetSkin(flags.get(i));
	        	bindTexture(playerFace);
		        
	        	// Render front face
		        buff.begin(5, DefaultVertexFormats.POSITION_TEX_NORMAL);
		        for(int m = 0; m < numFaceVerts; m++)
		        {
		        	int n = m + (numSegments - numFaceSegments) / 2;
		        	
		        	float sin = (float)Math.sin(time + n * 0.3f + i * Math.PI * 0.5f);
		        	float cos = (float)Math.cos(time + n * 0.3f + i * Math.PI * 0.5f);
		        	double zOffset = waveSize * sin * n / numSegments + 0.01;
		        	
		        	float u = 0.125f + 0.125f * m / numFaceSegments;
		        	
		        	buff.pos(length * n / (float)numSegments, 0.5d + height / 2d, zOffset).tex( u, 0.125f).normal(sin, 0, cos).endVertex();
		        	buff.pos(length * n / (float)numSegments, 0.5d - height / 2d, zOffset).tex( u, 0.25f).normal(sin, 0, cos).endVertex();
		        }
		        tessellator.draw();
		        
	        	// Render back face
		        buff.begin(5, DefaultVertexFormats.POSITION_TEX_NORMAL);
		        for(int m = numFaceVerts - 1; m >= 0; m--)
		        {
		        	int n = m + (numSegments - numFaceSegments) / 2;
		        	
		        	float sin = (float)Math.sin(time + n * 0.3f + i * Math.PI * 0.5f);
		        	float cos = (float)Math.cos(time + n * 0.3f + i * Math.PI * 0.5f);
		        	double zOffset = waveSize * sin * n / numSegments - 0.01;
		        	
		        	float u = 0.125f + 0.125f * m / numFaceSegments;
		        	
		        	buff.pos(length * n / (float)numSegments, 0.5d + height / 2d, zOffset).tex( u, 0.125f).normal(sin, 0, cos).endVertex();
		        	buff.pos(length * n / (float)numSegments, 0.5d - height / 2d, zOffset).tex( u, 0.25f).normal(sin, 0, cos).endVertex();
		        }
		        tessellator.draw();
			}
        }
        GlStateManager.popMatrix();
        
        
        GlStateManager.popMatrix();
        /*
        for(int i = 0; i < flags.size(); i++)
        {
        	GlStateManager.translate(0.0F, 1.0F, 0.0F);
        	
        	ResourceLocation playerFace = GuiFactionInfo.GetSkin(flags.get(i));
        	bindTexture(playerFace);
        	
    
        	buff.begin(7, DefaultVertexFormats.POSITION_TEX);
        	 
        	
        	buff.pos(0, 1, 0).tex(0.125f, 0.25f).endVertex();
        	buff.pos(1, 1, 0).tex(0.25f, 0.25f).endVertex();
        	buff.pos(1, 0, 0).tex(0.25f, 0.125f).endVertex();
        	buff.pos(0, 0, 0).tex(0.125f, 0.125f).endVertex();
        	
        	tessellator.draw();
        	
        }*/
        
        GlStateManager.enableTexture2D();
		
		//GlStateManager.depthFunc(515);
		//GlStateManager.popMatrix();
	}
}

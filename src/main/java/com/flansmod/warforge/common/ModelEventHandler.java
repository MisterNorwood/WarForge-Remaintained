package com.flansmod.warforge.common;

import com.flansmod.warforge.Tags;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public class ModelEventHandler {

    @SubscribeEvent
    public void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        event.register(new ResourceLocation(Tags.MODID, "block/pole"));
    }
}

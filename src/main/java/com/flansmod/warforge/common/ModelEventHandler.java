package com.flansmod.warforge.common;

import com.flansmod.warforge.Tags;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.bus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public class ModelEventHandler {

    @SubscribeEvent
    public void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "block/pole")));
    }
}

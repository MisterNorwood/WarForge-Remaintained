package com.flansmod.warforge.client;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.client.particle.ParticleStarCircle;
import com.flansmod.warforge.common.Content;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = Tags.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientContent {

    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(Content.STAR_CIRCLE_PARTICLE.get(), ParticleStarCircle.Provider::new);
    }
}

package com.flansmod.warforge.common.effect;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

public class AnimatedEffectHandler {
    public static final List<EffectAnimated<?>> effectQueue = new ArrayList<>();

    public static void add(EffectAnimated<?> effect) {
        effectQueue.add(effect);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onClientTick(ClientTickEvent.Post event) {
        effectQueue.removeIf(effect -> {
            effect.tick();
            return effect.isComplete();
        });
    }

}

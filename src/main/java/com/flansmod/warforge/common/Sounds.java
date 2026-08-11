package com.flansmod.warforge.common;

import com.flansmod.warforge.Tags;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class Sounds {
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, Tags.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SFX_UPGRADE =
            SOUND_EVENTS.register("sfx.upgrade",
                    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Tags.MODID, "sfx.upgrade")));

    public static void register(IEventBus modBus) {
        SOUND_EVENTS.register(modBus);
    }
}

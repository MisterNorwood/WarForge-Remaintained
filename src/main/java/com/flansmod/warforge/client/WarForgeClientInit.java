package com.flansmod.warforge.client;

import com.flansmod.warforge.common.ModelEventHandler;
import com.flansmod.warforge.common.WarForgeMod;
import net.neoforged.bus.api.IEventBus;

public final class WarForgeClientInit {
    private WarForgeClientInit() {
    }

    public static void init(IEventBus modBus) {
        WarForgeMod.NAMETAG_CACHE = new PlayerNametagCache(60_000, 200);
        modBus.register(new ModelEventHandler());
        ClientProxy proxy = (ClientProxy) WarForgeMod.proxy;
        modBus.addListener(proxy::clientSetup);
        modBus.addListener(proxy::registerRenderers);
        modBus.addListener(proxy::registerKeyMappings);
    }
}

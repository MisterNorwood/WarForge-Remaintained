package com.flansmod.warforge.api;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.api.interfaces.IChunkReinforcer;
import com.flansmod.warforge.common.Content;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * WarForge-provided capabilities. Addons (e.g. WFCore) expose these from their tiles so WarForge can
 * query them generically without a compile-time dependency on the addon.
 */
public final class WarForgeCapabilities {

    public static final BlockCapability<IChunkReinforcer, Void> CHUNK_REINFORCER =
            BlockCapability.createVoid(
                    ResourceLocation.fromNamespaceAndPath(Tags.MODID, "chunk_reinforcer"),
                    IChunkReinforcer.class);

    private WarForgeCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                Content.TE_ISLAND_COLLECTOR.get(),
                (be, side) -> be.getPullOnlyView());
    }
}

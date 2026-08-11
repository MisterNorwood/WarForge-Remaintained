package com.flansmod.warforge.client;

import com.flansmod.warforge.Tags;
import com.flansmod.warforge.client.ClientClaimChunkCache;
import com.flansmod.warforge.client.util.FullColorNameplate;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.ClaimManagerGuiFactory;
import com.flansmod.warforge.common.factories.FactionMemberManagerGuiData;
import com.flansmod.warforge.common.factories.FactionMemberManagerGuiFactory;
import com.flansmod.warforge.common.factories.OperationsGuiFactory;
import com.flansmod.warforge.common.network.PacketMoveCitadel;
import com.flansmod.warforge.common.network.PacketRequestFactionInfo;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import static com.flansmod.warforge.client.WarforgeIconButton.WARFORGE_BUTTON_SIZE;

@EventBusSubscriber(modid = Tags.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class WarForgeClientEventHandler {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen) && !(event.getScreen() instanceof CreativeModeInventoryScreen)) {
            return;
        }
        if (Minecraft.getInstance().player == null) {
            return;
        }

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) event.getScreen();
        int x = screen.getGuiLeft() + screen.getXSize() + 4;
        int top = screen.getGuiTop();
        event.addListener(new WarforgeIconButton(x, top + 4, 0, WarForgeClientEventHandler::openClaims));
        event.addListener(new WarforgeIconButton(x, top + 26, WARFORGE_BUTTON_SIZE, WarForgeClientEventHandler::openMembers));
        event.addListener(new WarforgeIconButton(x, top + 48, WARFORGE_BUTTON_SIZE * 2, WarForgeClientEventHandler::openFactionStats));
        event.addListener(new WarforgeIconButton(x, top + 70, WARFORGE_BUTTON_SIZE * 3, WarForgeClientEventHandler::moveCitadel));
        event.addListener(new WarforgeIconButton(x, top + 92, WARFORGE_BUTTON_SIZE * 4, WarForgeClientEventHandler::openOperations));
    }

    private static void openClaims() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        DimChunkPos center = new DimChunkPos(player.level().dimension(), player.blockPosition());
        ClaimManagerGuiFactory.resetSiegeState();
        ClaimManagerGuiFactory.INSTANCE.openClient(center, WarForgeConfig.CLAIM_MANAGER_RADIUS, -1, -1);
    }

    private static void openMembers() {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        FactionMemberManagerGuiFactory.INSTANCE.openClient(FactionMemberManagerGuiData.Page.MEMBERS);
    }

    private static void openFactionStats() {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        PacketRequestFactionInfo packet = new PacketRequestFactionInfo();
        packet.mFactionIDRequest = ClientClaimChunkCache.playerFactionId;
        WarForgeMod.NETWORK.sendToServer(packet);
    }

    private static void openOperations() {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        OperationsGuiFactory.INSTANCE.openClient();
    }

    private static void moveCitadel() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        HitResult hit = player.pick(10.0d, 1.0F, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            PacketMoveCitadel packet = new PacketMoveCitadel();
            packet.pos = new DimBlockPos(player.level().dimension(), blockHit.getBlockPos().relative(blockHit.getDirection()));
            WarForgeMod.NETWORK.sendToServer(packet);
        }
    }

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        // RenderNameTagEvent fires for any named entity (mobs, armor stands, ...); faction nameplates
        // only apply to players, so don't request faction data for non-player names.
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        // Don't draw your own faction tag over yourself (only ever visible in third-person / F5).
        if (event.getEntity() == Minecraft.getInstance().player) {
            return;
        }
        //SOOO minecraft puts this symbol in player nicknames...? the fuck?
        PlayerNametagCache.NamePlateData faction = WarForgeMod.NAMETAG_CACHE.requestIfAbsent(event.getContent().getString().replaceAll("§.", ""));
        if (faction == null) {
            return;
        }
        FullColorNameplate.drawNameplate(Minecraft.getInstance().font, Component.literal(faction.name), event.getEntity(), event.getPoseStack(), event.getMultiBufferSource(), -10, event.getEntity().isDiscrete(), faction.color, faction.darkerColor, event.getPackedLight());
    }
}

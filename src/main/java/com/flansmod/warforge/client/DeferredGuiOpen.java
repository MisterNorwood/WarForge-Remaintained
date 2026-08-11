package com.flansmod.warforge.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public final class DeferredGuiOpen {
    private static Runnable pending;
    private static Runnable parentReopen;
    private static double restoreMouseX;
    private static double restoreMouseY;
    private static boolean restoreMouse;

    private DeferredGuiOpen() {
    }

    /**
     * Opens a top-level menu (no parent to return to). Clears any parent-return action.
     */
    public static void open(Runnable opener) {
        parentReopen = null;
        schedule(opener);
    }

    /**
     * Opens {@code opener} as a child of {@code reopenParent}. Closing the child via its X button will replay
     * {@code reopenParent} instead of returning to the world.
     */
    public static void openChild(Runnable reopenParent, Runnable opener) {
        parentReopen = reopenParent;
        schedule(opener);
    }

    /**
     * Opens {@code opener} as a sibling of the current menu: lateral navigation that keeps the same parent
     * (e.g. Stats -> Members both still return to the citadel on close).
     */
    public static void openSibling(Runnable opener) {
        schedule(opener);
    }

    /**
     * Close handler for sub-GUI X buttons: if this menu was opened as a child, re-open its parent; otherwise
     * close to the world.
     */
    public static void closeOrReturnToParent() {
        Runnable reopenParent = parentReopen;
        parentReopen = null;
        if (reopenParent != null) {
            reopenParent.run();
        } else {
            Minecraft.getInstance().setScreen(null);
        }
    }

    /**
     * Pumped once per client tick. Runs the pending open, if any.
     */
    public static void tick() {
        Runnable opener = pending;
        if (opener == null) {
            return;
        }
        pending = null;
        opener.run();
    }

    /**
     * Called when a screen finishes opening (ScreenEvent.Init.Post). Restores the cursor captured in
     * {@link #schedule} so a deferred reopen does not recentre the mouse — without this, the no-screen
     * gap grabs the mouse and the reopen's {@code MouseHandler.releaseMouse} snaps the cursor to centre.
     */
    public static void onScreenOpened() {
        if (!restoreMouse) {
            return;
        }
        restoreMouse = false;
        Minecraft mc = Minecraft.getInstance();
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), restoreMouseX, restoreMouseY);
    }

    private static void schedule(Runnable opener) {
        Minecraft mc = Minecraft.getInstance();
        // Capture the cursor before closing: setScreen(null) grabs the mouse, so the reopen would otherwise
        // recentre it (see onScreenOpened).
        restoreMouseX = mc.mouseHandler.xpos();
        restoreMouseY = mc.mouseHandler.ypos();
        restoreMouse = true;
        // Close the current screen first so the server returns to the inventory menu before the child opens.
        mc.setScreen(null);
        pending = opener;
    }
}

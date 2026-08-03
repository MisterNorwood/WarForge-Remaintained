package com.flansmod.warforge.common.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayDeque;
import java.util.Deque;

public class SyncQueueHandler {
    public static class SyncTask {
        public final ServerPlayer player;
        public final Runnable runnable;

        public SyncTask(ServerPlayer player, Runnable runnable) {
            this.player = player;
            this.runnable = runnable;
        }
    }

    private static final Deque<SyncTask> syncTasks = new ArrayDeque<>();
    public static final int perTick  = 4;

    public static void enqueue(ServerPlayer player, Runnable task) {
        if (player == null || player.hasDisconnected()) return;
        syncTasks.add(new SyncTask(player, task));
    }

    public static void sync(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !syncTasks.isEmpty()) {
            for (int i = 0; i < perTick && !syncTasks.isEmpty(); i++) {
                SyncTask task = syncTasks.poll();
                if(task == null) continue;
                if (task.player != null && task.player.hasDisconnected()) {
                    syncTasks.removeIf(t -> t.player == task.player);
                } else if (task.runnable != null) {
                    task.runnable.run();
                }
            }
        }
    }
}

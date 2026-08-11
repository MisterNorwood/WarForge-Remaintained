package com.flansmod.warforge.client;

import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.network.SiegeCampProgressInfo;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

public class SiegeUiDebugCommand {

    private static final String[] SCENARIOS = {"attacker", "defender", "even", "blowout", "wide", "abandon", "default"};

    @SubscribeEvent
    public void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("warforgesiegeui");
        root.executes(ctx -> feedback(ctx, "Siege UI debug: /warforgesiegeui <on|off|toggle|animate|scenario|progress|goal|defence|abandon|reset>"));

        root.then(Commands.literal("on").executes(ctx -> setEnabled(ctx, true)));
        root.then(Commands.literal("off").executes(ctx -> setEnabled(ctx, false)));
        root.then(Commands.literal("toggle").executes(ctx -> setEnabled(ctx, !ClientTickHandler.UI_DEBUG)));

        root.then(Commands.literal("animate").executes(ctx -> {
            ClientTickHandler.UI_DEBUG = true;
            ClientTickHandler.getOrCreateDebugSiege();
            ClientTickHandler.animateDebugSiege = !ClientTickHandler.animateDebugSiege;
            return feedback(ctx, "Siege UI animate: " + (ClientTickHandler.animateDebugSiege ? "on" : "off"));
        }));

        root.then(Commands.literal("reset").executes(ctx -> {
            ClientTickHandler.debugSiegeInfo = null;
            ClientTickHandler.animateDebugSiege = false;
            return feedback(ctx, "Siege UI debug reset to defaults");
        }));

        root.then(Commands.literal("scenario")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(SCENARIOS, b))
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            ClientTickHandler.UI_DEBUG = true;
                            ClientTickHandler.animateDebugSiege = false;
                            ClientTickHandler.applyDebugScenario(name);
                            return feedback(ctx, "Siege UI scenario: " + name);
                        })));

        root.then(intOption("progress", (dbg, v) -> {
            dbg.mPreviousProgress = dbg.progress;
            dbg.progress = v;
        }));
        root.then(intOption("goal", (dbg, v) -> dbg.completionPoint = Math.max(1, v)));

        root.then(Commands.literal("defence")
                .then(Commands.argument("value", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            WarForgeConfig.SIEGE_DEFENCE_THRESHOLD = IntegerArgumentType.getInteger(ctx, "value");
                            ClientTickHandler.UI_DEBUG = true;
                            ClientTickHandler.getOrCreateDebugSiege();
                            return feedback(ctx, "Siege defence threshold (client display) = " + WarForgeConfig.SIEGE_DEFENCE_THRESHOLD);
                        })));

        root.then(Commands.literal("abandon")
                .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                        .executes(ctx -> {
                            SiegeCampProgressInfo dbg = ClientTickHandler.getOrCreateDebugSiege();
                            dbg.attackerAbandonSeconds = IntegerArgumentType.getInteger(ctx, "seconds");
                            dbg.attackingFactionId = ClientClaimChunkCache.playerFactionId;
                            ClientTickHandler.UI_DEBUG = true;
                            return feedback(ctx, "Siege abandon seconds = " + dbg.attackerAbandonSeconds);
                        })));

        dispatcher.register(root);
    }

    private interface DebugSetter {
        void apply(SiegeCampProgressInfo dbg, int value);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> intOption(String name, DebugSetter setter) {
        return Commands.literal(name)
                .then(Commands.argument("value", IntegerArgumentType.integer())
                        .executes(ctx -> {
                            int value = IntegerArgumentType.getInteger(ctx, "value");
                            SiegeCampProgressInfo dbg = ClientTickHandler.getOrCreateDebugSiege();
                            setter.apply(dbg, value);
                            ClientTickHandler.UI_DEBUG = true;
                            return feedback(ctx, "Siege UI " + name + " = " + value);
                        }));
    }

    private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        ClientTickHandler.UI_DEBUG = enabled;
        if (enabled) {
            ClientTickHandler.getOrCreateDebugSiege();
        } else {
            ClientTickHandler.animateDebugSiege = false;
        }
        return feedback(ctx, "Siege UI debug: " + (enabled ? "on" : "off"));
    }

    private static int feedback(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return Command.SINGLE_SUCCESS;
    }
}

package com.flansmod.warforge.server;

import com.flansmod.warforge.api.vein.Quality;
import com.flansmod.warforge.api.vein.Vein;
import com.flansmod.warforge.common.ProtectionsModule;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.*;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.common.util.TimeHelper;
import org.apache.commons.lang3.tuple.Pair;
import com.flansmod.warforge.server.Faction.PlayerData;
import com.flansmod.warforge.server.Leaderboard.FactionStat;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class CommandFactions {
    private static boolean isOp(CommandSourceStack src) {
        return src.hasPermission(2);
    }

    private static final SuggestionProvider<CommandSourceStack> FACTION_NAME_SUGGESTIONS = (ctx, builder) -> {
        List<String> names = new ArrayList<>();
        for (Faction faction : WarForgeMod.FACTIONS.getAllFactions()) {
            if (faction != null && faction.name != null && !faction.uuid.equals(Faction.nullUuid)) {
                names.add(faction.name);
            }
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> FLAG_ID_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(WarForgeMod.FLAG_REGISTRY.getAvailableFlagIds(), builder);

    // Registration entrypoint, called from RegisterCommandsEvent.
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(buildTree("faction"));
        dispatcher.register(Commands.literal("f").redirect(root));
        dispatcher.register(Commands.literal("factions").redirect(root));
        dispatcher.register(Commands.literal("war").redirect(root));
        dispatcher.register(Commands.literal("warforge").redirect(root));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTree(String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name);
        root.executes(ctx -> {
            ctx.getSource().sendSuccess(() -> Component.literal("Try /f help"), false);
            return Command.SINGLE_SUCCESS;
        });

        root.then(Commands.literal("help").executes(CommandFactions::doHelp));
        root.then(Commands.literal("create").executes(CommandFactions::doCreate));

        // invite <playerName> [factionName(op)]
        root.then(Commands.literal("invite")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> doInvite(ctx, false))
                        .then(Commands.argument("faction", StringArgumentType.string())
                                .suggests(FACTION_NAME_SUGGESTIONS)
                                .requires(CommandFactions::isOp)
                                .executes(ctx -> doInvite(ctx, true)))));

        // accept [factionName]
        root.then(Commands.literal("accept")
                .executes(ctx -> doAccept(ctx, false))
                .then(Commands.argument("faction", StringArgumentType.string())
                        .suggests(FACTION_NAME_SUGGESTIONS)
                        .executes(ctx -> doAccept(ctx, true))));

        // disband [factionName(op)]
        root.then(Commands.literal("disband")
                .executes(CommandFactions::doDisbandSelf)
                .then(Commands.argument("faction", StringArgumentType.string())
                        .suggests(FACTION_NAME_SUGGESTIONS)
                        .requires(CommandFactions::isOp)
                        .executes(CommandFactions::doDisbandNamed)));

        // expel / remove <playerName>
        root.then(Commands.literal("expel")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> doRemove(ctx, "expel"))));
        root.then(Commands.literal("remove")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> doRemove(ctx, "remove"))));

        root.then(Commands.literal("leave").executes(CommandFactions::doLeave));
        root.then(Commands.literal("exit").executes(CommandFactions::doLeave));

        // setleader <name>
        root.then(Commands.literal("setleader")
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(CommandFactions::doSetLeader)));

        root.then(Commands.literal("time").executes(CommandFactions::doTime));

        // conquered clear | set <time> — admin control over conquered no-man's-land on the current chunk
        root.then(Commands.literal("conquered")
                .requires(CommandFactions::isOp)
                .then(Commands.literal("clear").executes(CommandFactions::doConqueredClear))
                .then(Commands.literal("set")
                        .then(Commands.argument("time", StringArgumentType.word())
                                .executes(CommandFactions::doConqueredSet))));

        // info [factionName]
        root.then(Commands.literal("info")
                .executes(ctx -> doInfo(ctx, false))
                .then(Commands.argument("faction", StringArgumentType.string())
                        .suggests(FACTION_NAME_SUGGESTIONS)
                        .executes(ctx -> doInfo(ctx, true))));

        // list — all factions
        root.then(Commands.literal("list").executes(CommandFactions::doList));

        // Leaderboards
        for (String alias : new String[]{"top"}) {
            root.then(Commands.literal(alias).executes(ctx -> doLeaderboard(ctx, FactionStat.TOTAL, "**Top Leaderboard**")));
        }
        for (String alias : new String[]{"wealth", "wealthtop", "bal", "baltop"}) {
            root.then(Commands.literal(alias).executes(ctx -> doLeaderboard(ctx, FactionStat.WEALTH, "**Top Leaderboard**")));
        }
        for (String alias : new String[]{"notoriety", "notorietytop", "pvp", "pvptop"}) {
            root.then(Commands.literal(alias).executes(ctx -> doLeaderboard(ctx, FactionStat.NOTORIETY, "**Notoriety Leaderboard**")));
        }
        for (String alias : new String[]{"legacy", "legacytop", "playtime", "playtimetop"}) {
            root.then(Commands.literal(alias).executes(ctx -> doLeaderboard(ctx, FactionStat.LEGACY, "**Legacy Leaderboard**")));
        }

        // Op-gated special zone claims (citadel-less SafeZone / WarZone). One command:
        //   /f zone safe | war   -> claim the chunk you stand in for that zone
        //   /f zone remove        -> remove the zone claim in the chunk you stand in
        for (String alias : new String[]{"zone", "zones"}) {
            root.then(Commands.literal(alias).requires(CommandFactions::isOp)
                    .executes(CommandFactions::doZoneUsage)
                    .then(Commands.literal("safe").executes(ctx -> doZoneClaim(ctx, FactionStorage.SAFE_ZONE_ID)))
                    .then(Commands.literal("war").executes(ctx -> doZoneClaim(ctx, FactionStorage.WAR_ZONE_ID)))
                    .then(Commands.literal("remove").executes(CommandFactions::doZoneUnclaim)));
        }

        // Op protection override toggle
        for (String alias : new String[]{"opProtection", "protection", "protectionOverride"}) {
            root.then(Commands.literal(alias).requires(CommandFactions::isOp)
                    .executes(CommandFactions::doProtectionToggle));
        }

        // Sieges (op)
        for (String alias : new String[]{"siege", "sieges"}) {
            root.then(Commands.literal(alias).requires(CommandFactions::isOp)
                    .executes(CommandFactions::doSiegeUsage)
                    .then(Commands.literal("list").executes(CommandFactions::doSiegeList))
                    .then(Commands.literal("l").executes(CommandFactions::doSiegeList))
                    .then(Commands.literal("terminate").then(siegeTerminateArgs()))
                    .then(Commands.literal("t").then(siegeTerminateArgs())));
        }

        root.then(Commands.literal("home").executes(CommandFactions::doHome));
        root.then(Commands.literal("spawn").executes(CommandFactions::doSpawn));

        // promote / demote <player>
        root.then(Commands.literal("promote")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> doPromoteDemote(ctx, true))));
        root.then(Commands.literal("demote")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> doPromoteDemote(ctx, false))));

        // vault redeem
        root.then(Commands.literal("vault")
                .executes(CommandFactions::doVaultUsage)
                .then(Commands.literal("redeem").executes(CommandFactions::doVaultRedeem)));

        // offlineprotection <faction> <enable|disable|status> (op)
        root.then(Commands.literal("offlineprotection").requires(CommandFactions::isOp)
                .executes(CommandFactions::doOfflineProtectionUsage)
                .then(Commands.argument("faction", StringArgumentType.string())
                        .suggests(FACTION_NAME_SUGGESTIONS)
                        .executes(CommandFactions::doOfflineProtectionUsage)
                        .then(Commands.literal("enable").executes(ctx -> doOfflineProtection(ctx, "enable")))
                        .then(Commands.literal("disable").executes(ctx -> doOfflineProtection(ctx, "disable")))
                        .then(Commands.literal("status").executes(ctx -> doOfflineProtection(ctx, "status")))));

        // flag <faction> <set <flagId> | reset> (op)
        root.then(Commands.literal("flag").requires(CommandFactions::isOp)
                .executes(CommandFactions::doFlagUsage)
                .then(Commands.argument("faction", StringArgumentType.string())
                        .suggests(FACTION_NAME_SUGGESTIONS)
                        .executes(CommandFactions::doFlagUsage)
                        .then(Commands.literal("reset").executes(CommandFactions::doFlagReset))
                        .then(Commands.literal("set")
                                .then(Commands.argument("flagId", StringArgumentType.greedyString())
                                        .suggests(FLAG_ID_SUGGESTIONS)
                                        .executes(CommandFactions::doFlagSet)))));

        // rename <old> <new> (op)
        root.then(Commands.literal("rename").requires(CommandFactions::isOp)
                .executes(CommandFactions::doRenameUsage)
                .then(Commands.argument("oldFactionName", StringArgumentType.string())
                        .suggests(FACTION_NAME_SUGGESTIONS)
                        .executes(CommandFactions::doRenameUsage)
                        .then(Commands.argument("newFactionName", StringArgumentType.string())
                                .executes(CommandFactions::doRename))));

        root.then(Commands.literal("clearnotoriety").requires(CommandFactions::isOp)
                .executes(ctx -> { WarForgeMod.FACTIONS.clearNotoriety(); return Command.SINGLE_SUCCESS; }));
        root.then(Commands.literal("clearlegacy").requires(CommandFactions::isOp)
                .executes(ctx -> { WarForgeMod.FACTIONS.clearLegacy(); return Command.SINGLE_SUCCESS; }));

        // chat / msg <message...>
        root.then(Commands.literal("chat")
                .then(Commands.argument("message", StringArgumentType.greedyString()).executes(CommandFactions::doMsg)));
        root.then(Commands.literal("msg")
                .then(Commands.argument("message", StringArgumentType.greedyString()).executes(CommandFactions::doMsg)));

        // tpa / tprequest / tp <player> - request to teleport to another player
        for (String alias : new String[]{"tpa", "tprequest", "tp"}) {
            root.then(Commands.literal(alias)
                    .then(Commands.argument("player", EntityArgument.player())
                            .executes(CommandFactions::doTpaRequest)));
        }
        // tpaccept / tpdeny [player]-- resolve a pending request (newest, or from a specific player)
        root.then(Commands.literal("tpaccept")
                .executes(ctx -> doTpaResolve(ctx, true, false))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> doTpaResolve(ctx, true, true))));
        root.then(Commands.literal("tpdeny")
                .executes(ctx -> doTpaResolve(ctx, false, false))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> doTpaResolve(ctx, false, true))));

        root.then(Commands.literal("borders").executes(CommandFactions::doBorders));

        // debugmsg <title> <subtitle> <colorHex> (op, player)
        root.then(Commands.literal("debugmsg").requires(CommandFactions::isOp)
                .then(Commands.argument("title", StringArgumentType.string())
                        .then(Commands.argument("subtitle", StringArgumentType.string())
                                .then(Commands.argument("color", StringArgumentType.string())
                                        .executes(CommandFactions::doDebugMsg)))));

        // vein (op)
        root.then(buildVeinTree());

        return root;
    }

    private static int doHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("/f invite <playerName>"), false);
        src.sendSuccess(() -> Component.literal("/f accept [factionName]"), false);
        src.sendSuccess(() -> Component.literal("/f disband"), false);
        src.sendSuccess(() -> Component.literal("/f expel <playerName>"), false);
        src.sendSuccess(() -> Component.literal("/f leave"), false);
        src.sendSuccess(() -> Component.literal("/f time"), false);
        src.sendSuccess(() -> Component.literal("/f info <factionName>"), false);
        src.sendSuccess(() -> Component.literal("/f list"), false);
        src.sendSuccess(() -> Component.literal("/f top"), false);
        src.sendSuccess(() -> Component.literal("/f wealth"), false);
        src.sendSuccess(() -> Component.literal("/f legacy"), false);
        src.sendSuccess(() -> Component.literal("/f notoriety"), false);
        src.sendSuccess(() -> Component.literal("/f borders"), false);
        src.sendSuccess(() -> Component.literal("/f tpa <playerName>"), false);
        src.sendSuccess(() -> Component.literal("/f tpaccept | tpdeny [playerName]"), false);
        src.sendSuccess(() -> Component.literal("/f vault redeem"), false);
        if (isOp(src)) {
            src.sendSuccess(() -> Component.literal("/f offlineprotection <faction> <enable|disable|status>"), false);
            src.sendSuccess(() -> Component.literal("/f zone <safe|war|remove>"), false);
            src.sendSuccess(() -> Component.literal("/f rename <oldFactionName> <newFactionName>"), false);
            src.sendSuccess(() -> Component.literal("/f flag <factionName> <set <flagId> | reset>"), false);
            src.sendSuccess(() -> Component.literal("/f vein <info|set <vein> [quality]|clear|reroll> [at <chunkX> <chunkZ> [dim] [radius]]"), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int doCreate(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("Craft a Citadel to create a faction"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int doInvite(CommandContext<CommandSourceStack> ctx, boolean withFaction) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer invitee = EntityArgument.getPlayer(ctx, "player");

        // First, resolve the op version where we can specify the faction
        if (withFaction) {
            String factionName = StringArgumentType.getString(ctx, "faction");
            Faction faction = WarForgeMod.FACTIONS.getFaction(factionName);
            if (faction != null)
                WarForgeMod.FACTIONS.RequestInvitePlayerToFaction(src, faction.uuid, invitee.getUUID());
            else
                src.sendFailure(Component.literal("Could not find faction " + factionName));
            return Command.SINGLE_SUCCESS;
        }

        // Any other case, we assume players can only invite to their own faction
        if (src.getEntity() instanceof Player player) {
            WarForgeMod.FACTIONS.requestInvitePlayerToMyFaction(player, invitee.getUUID());
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int doAccept(CommandContext<CommandSourceStack> ctx, boolean withFaction) {
        CommandSourceStack src = ctx.getSource();
        if (src.getEntity() instanceof Player player) {
            if (withFaction) {
                WarForgeMod.FACTIONS.RequestAcceptInvite(player, StringArgumentType.getString(ctx, "faction"));
            } else {
                WarForgeMod.FACTIONS.RequestAcceptInvite(player);
            }
        } else {
            src.sendFailure(Component.literal("The server can't accept a faction invite"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int doDisbandSelf(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (src.getEntity() instanceof Player player) {
            Faction faction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUUID());
            if (faction != null) {
                WarForgeMod.FACTIONS.requestDisbandFaction(player, faction.uuid);
            } else {
                src.sendFailure(Component.literal("You aren't in a faction"));
            }
        } else {
            src.sendFailure(Component.literal("You aren't in a faction"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int doDisbandNamed(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Faction toDisband = WarForgeMod.FACTIONS.getFaction(StringArgumentType.getString(ctx, "faction"));
        if (toDisband != null) {
            WarForgeMod.FACTIONS.FactionDefeated(toDisband);
        } else {
            src.sendFailure(Component.literal("Could not find that faction"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int doRemove(CommandContext<CommandSourceStack> ctx, String verb) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer toRemove = EntityArgument.getPlayer(ctx, "player");
        UUID toRemoveID = toRemove.getUUID();

        Faction faction = null;
        if (src.getEntity() instanceof Player player) {
            faction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUUID());
        }
        if (faction == null) {
            faction = WarForgeMod.FACTIONS.getFactionOfPlayer(toRemoveID);
        }
        if (faction == null) {
            src.sendFailure(Component.literal("Could not find a faction for that player"));
            return Command.SINGLE_SUCCESS;
        }

        WarForgeMod.FACTIONS.requestRemovePlayerFromFaction(src, faction.uuid, toRemoveID);
        return Command.SINGLE_SUCCESS;
    }

    private static int doLeave(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (src.getEntity() instanceof Player player) {
            Faction faction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUUID());
            if (faction == null) {
                src.sendFailure(Component.literal("You aren't in a faction"));
                return Command.SINGLE_SUCCESS;
            }
            WarForgeMod.FACTIONS.requestRemovePlayerFromFaction(src, faction.uuid, player.getUUID());
        } else {
            src.sendFailure(Component.literal("This command is only for players"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int doSetLeader(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof Player player)) {
            src.sendFailure(Component.literal("This command is only for players"));
            return Command.SINGLE_SUCCESS;
        }
        String name = StringArgumentType.getString(ctx, "name");
        GameProfile profile = src.getServer().getProfileCache().get(name).orElse(null);
        if (profile == null) {
            src.sendFailure(Component.literal("Could not find player " + name));
            return Command.SINGLE_SUCCESS;
        }
        Faction targetFaction = WarForgeMod.FACTIONS.getFactionOfPlayer(profile.getId());
        if (targetFaction == null) {
            src.sendFailure(Component.literal("That player is not in a faction"));
        } else {
            WarForgeMod.FACTIONS.RequestTransferLeadership(player, targetFaction.uuid, profile.getId());
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int doConqueredClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        DimChunkPos chunk = new DimChunkPos(player.level().dimension(), player.blockPosition());
        if (WarForgeMod.FACTIONS.clearConquered(chunk)) {
            ctx.getSource().sendSuccess(() -> Component.literal("Cleared conquered status of chunk [" + chunk.x + ", " + chunk.z + "]"), true);
        } else {
            ctx.getSource().sendFailure(Component.literal("This chunk is not conquered"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int doConqueredSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String timeArg = StringArgumentType.getString(ctx, "time");
        long ms = TimeHelper.parseDurationMs(timeArg);
        if (ms <= 0 || ms > Integer.MAX_VALUE) {
            ctx.getSource().sendFailure(Component.literal("Invalid time '" + timeArg + "'. Use e.g. 30s, 15m, 2h, 1d (bare numbers are seconds)."));
            return 0;
        }
        DimChunkPos chunk = new DimChunkPos(player.level().dimension(), player.blockPosition());
        Faction faction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUUID());
        WarForgeMod.FACTIONS.setConquered(chunk, faction == null ? Faction.nullUuid : faction.uuid, (int) ms);
        final long shownMs = ms;
        ctx.getSource().sendSuccess(() -> Component.literal("Chunk [" + chunk.x + ", " + chunk.z
                + "] is now conquered wilderness, reverting in " + TimeHelper.formatTime(shownMs)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int doTime(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        long ms = WarForgeMod.INSTANCE.getTimeToNextYieldMs();
        long s = ms / 1000;
        long m = s / 60;
        long h = m / 60;
        long d = h / 24;

        final long yd = d, yh = h, ym = m, ys = s;
        src.sendSuccess(() -> Component.literal("Yields will next be awarded in "
                + (yd) + " days, "
                + String.format("%02d", (yh % 24)) + ":"
                + String.format("%02d", (ym % 60)) + ":"
                + String.format("%02d", (ys % 60))), false);

        ms = WarForgeMod.INSTANCE.getTimeToNextSiegeAdvanceMs();
        s = ms / 1000;
        m = s / 60;
        h = m / 60;
        d = h / 24;

        final long sd = d, sh = h, sm = m, ss = s;
        src.sendSuccess(() -> Component.literal("Sieges will progress in "
                + (sd) + " days, "
                + String.format("%02d", (sh % 24)) + ":"
                + String.format("%02d", (sm % 60)) + ":"
                + String.format("%02d", (ss % 60))), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int doInfo(CommandContext<CommandSourceStack> ctx, boolean withFaction) {
        CommandSourceStack src = ctx.getSource();
        if (src.getEntity() instanceof ServerPlayer sender) {
            Faction factionToSend = null;
            if (withFaction) {
                factionToSend = WarForgeMod.FACTIONS.getFaction(StringArgumentType.getString(ctx, "faction"));
            }
            if (factionToSend == null) {
                factionToSend = WarForgeMod.FACTIONS.getFactionOfPlayer(sender.getUUID());
            }

            if (factionToSend == null) {
                src.sendFailure(Component.literal("Could not find that faction"));
            } else {
                Faction senderFaction = WarForgeMod.FACTIONS.getFactionOfPlayer(sender.getUUID());
                if (senderFaction == null || senderFaction.uuid.equals(Faction.nullUuid) || !senderFaction.equals(factionToSend)) {
                    src.sendFailure(Component.literal("Information cannot be provided to non-faction members"));
                } else {
                    PacketFactionInfo packet = new PacketFactionInfo();
                    packet.info = factionToSend.createInfo();
                    WarForgeMod.NETWORK.sendTo(packet, sender);
                }
            }
        } else {
            // Console must specify a faction name.
            Faction factionToSend = withFaction ? WarForgeMod.FACTIONS.getFaction(StringArgumentType.getString(ctx, "faction")) : null;
            if (factionToSend != null) {
                StringBuilder memberList = new StringBuilder("Members: ");
                for (HashMap.Entry<UUID, PlayerData> kvp : factionToSend.members.entrySet()) {
                    GameProfile profile = src.getServer().getProfileCache().get(kvp.getKey()).orElse(null);
                    if (profile != null) {
                        memberList.append(profile.getName()).append(", ");
                    }
                }

                final Faction f = factionToSend;
                final String members = memberList.toString();
                src.sendSuccess(() -> Component.literal("**" + f.name + "**\n"
                        + members + "\n"
                        + "Notoriety: " + f.notoriety + "\n"
                        + "Wealth: " + f.wealth + "\n"
                        + "Legacy: " + f.legacy + "\n"), false);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int doList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        List<Faction> factions = new ArrayList<>();
        for (Faction faction : WarForgeMod.FACTIONS.getAllFactions()) {
            if (faction != null && faction.name != null && !faction.uuid.equals(Faction.nullUuid)) {
                factions.add(faction);
            }
        }
        if (factions.isEmpty()) {
            src.sendSuccess(() -> Component.literal("There are no factions yet"), false);
            return Command.SINGLE_SUCCESS;
        }
        factions.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        src.sendSuccess(() -> Component.literal("Factions (" + factions.size() + "):"), false);
        for (Faction faction : factions) {
            final Faction f = faction;
            src.sendSuccess(() -> Component.literal("§7- §f" + f.name + " §7(" + f.members.size() + ")"), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int doLeaderboard(CommandContext<CommandSourceStack> ctx, FactionStat stat, String consoleHeader) {
        CommandSourceStack src = ctx.getSource();
        if (src.getEntity() instanceof ServerPlayer sender) {
            UUID uuid = sender.getUUID();
            PacketLeaderboardInfo packet = new PacketLeaderboardInfo();
            packet.info = WarForgeMod.LEADERBOARD.CreateInfo(0, stat, uuid);
            WarForgeMod.NETWORK.sendTo(packet, sender);
        } else {
            LeaderboardInfo info = WarForgeMod.LEADERBOARD.CreateInfo(0, stat, Faction.nullUuid);
            StringBuilder result = new StringBuilder(consoleHeader);
            for (int i = 0; i < LeaderboardInfo.NUM_LEADERBOARD_ENTRIES_PER_PAGE; i++) {
                FactionDisplayInfo facInfo = info.factionInfos[i];
                switch (stat) {
                    case TOTAL -> result.append("\n#").append(facInfo.totalRank).append(" | ").append(facInfo.legacy + facInfo.notoriety + facInfo.wealth);
                    case WEALTH -> result.append("\n#").append(facInfo.wealthRank).append(" | ").append(facInfo.wealth);
                    case NOTORIETY -> result.append("\n#").append(facInfo.notorietyRank).append(" | ").append(facInfo.notoriety);
                    case LEGACY -> result.append("\n#").append(facInfo.legacyRank).append(" | ").append(facInfo.legacy);
                }
            }
            final String out = result.toString();
            src.sendSuccess(() -> Component.literal(out), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int doZoneUsage(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendFailure(Component.literal("Usage: /f zone <safe|war|remove>"));
        return Command.SINGLE_SUCCESS;
    }

    private static int doZoneClaim(CommandContext<CommandSourceStack> ctx, UUID zoneID) {
        CommandSourceStack src = ctx.getSource();
        if (src.getEntity() instanceof Player player) {
            DimChunkPos pos = new DimBlockPos(player).toChunkPos();
            WarForgeMod.FACTIONS.requestZoneClaim(player, pos, zoneID);
            refreshClaimViews(src);
        } else {
            src.sendFailure(Component.literal("Use an in-game operator account."));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int doZoneUnclaim(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (src.getEntity() instanceof Player player) {
            DimChunkPos pos = new DimBlockPos(player).toChunkPos();
            WarForgeMod.FACTIONS.requestZoneUnclaim(player, pos);
            refreshClaimViews(src);
        } else {
            src.sendFailure(Component.literal("Use an in-game operator account."));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int doProtectionToggle(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ProtectionsModule.OP_OVERRIDE = !ProtectionsModule.OP_OVERRIDE;
        if (ProtectionsModule.OP_OVERRIDE)
            src.sendSuccess(() -> Component.literal("Admins can now build in protected areas."), false);
        else
            src.sendSuccess(() -> Component.literal("Admins can no longer build in protected areas."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int doSiegeUsage(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendFailure(Component.literal("More arguments required. Possible arguments: list, terminate"));
        return Command.SINGLE_SUCCESS;
    }

    private static int doSiegeList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        FactionStorage STORAGE = WarForgeMod.FACTIONS;
        var sieges = STORAGE.getSieges();
        src.sendSuccess(() -> Component.literal("List of current sieges.\nFormat: <ChunkX>, <ChunkY>, <DimID>; <Attacker>-<Defender>"), false);

        sieges.forEach((dpos, siege) -> {
            src.sendSuccess(() -> Component.literal("§7" + dpos.x + ", " + dpos.z + ", " + dpos.dim + "§r;  "
                    + STORAGE.getFaction(siege.attackingFaction).name + "-" + STORAGE.getFaction(siege.defendingFaction).name), false);
        });
        return Command.SINGLE_SUCCESS;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> siegeTerminateArgs() {
        return Commands.argument("chunkX", IntegerArgumentType.integer())
                .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                        .then(Commands.argument("dim", StringArgumentType.string())
                                .executes(ctx -> doSiegeTerminate(ctx, false))
                                .then(Commands.argument("conclusion", StringArgumentType.string())
                                        .executes(ctx -> doSiegeTerminate(ctx, true)))));
    }

    private static int doSiegeTerminate(CommandContext<CommandSourceStack> ctx, boolean withConclusion) {
        CommandSourceStack src = ctx.getSource();
        int cX = IntegerArgumentType.getInteger(ctx, "chunkX");
        int cZ = IntegerArgumentType.getInteger(ctx, "chunkZ");
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(StringArgumentType.getString(ctx, "dim")));
        DimChunkPos dimPos = new DimChunkPos(dim, cX, cZ);

        FactionStorage.siegeTermination termType;
        if (withConclusion) {
            try {
                termType = FactionStorage.siegeTermination.valueOf(StringArgumentType.getString(ctx, "conclusion"));
            } catch (Exception e) {
                termType = FactionStorage.siegeTermination.NEUTRAL;
            }
        } else {
            termType = FactionStorage.siegeTermination.NEUTRAL;
        }

        FactionStorage STORAGE = WarForgeMod.FACTIONS;
        var sieges = STORAGE.getSieges();
        if (!sieges.containsKey(dimPos)) {
            src.sendFailure(Component.literal("Provided siege does not exist."));
        }
        STORAGE.handleCompletedSiege(dimPos, termType);
        final FactionStorage.siegeTermination tt = termType;
        final DimChunkPos fp = dimPos;
        src.sendSuccess(() -> Component.literal("Terminated siege at: " + fp.x + ", " + fp.z + ", " + fp.dim + " with termination type: " + tt.name()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int doHome(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (src.getEntity() instanceof Player player)
            WarForgeMod.TELEPORTS.RequestFHome(player);
        else
            src.sendFailure(Component.literal("Only valid for players"));
        return Command.SINGLE_SUCCESS;
    }

    private static int doSpawn(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (src.getEntity() instanceof Player player)
            WarForgeMod.TELEPORTS.requestSpawn(player);
        else
            src.sendFailure(Component.literal("Only valid for players"));
        return Command.SINGLE_SUCCESS;
    }

    private static int doTpaRequest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof Player player)) {
            src.sendFailure(Component.literal("Only valid for players"));
            return Command.SINGLE_SUCCESS;
        }
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        WarForgeMod.TELEPORTS.requestTpa(player, target);
        return Command.SINGLE_SUCCESS;
    }

    private static int doTpaResolve(CommandContext<CommandSourceStack> ctx, boolean accept, boolean withPlayer) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("Only valid for players"));
            return Command.SINGLE_SUCCESS;
        }
        UUID filter = withPlayer ? EntityArgument.getPlayer(ctx, "player").getUUID() : null;
        if (accept) {
            WarForgeMod.TELEPORTS.acceptTpa(player, filter);
        } else {
            WarForgeMod.TELEPORTS.denyTpa(player, filter);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int doPromoteDemote(CommandContext<CommandSourceStack> ctx, boolean promote) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof Player player)) {
            src.sendFailure(Component.literal("Only valid for players"));
            return Command.SINGLE_SUCCESS;
        }
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        if (promote) {
            WarForgeMod.FACTIONS.requestPromote(player, target);
        } else {
            WarForgeMod.FACTIONS.requestDemote(player, target);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int doVaultUsage(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayer)) {
            src.sendFailure(Component.literal("Only valid for players"));
        } else {
            src.sendFailure(Component.literal("Usage: /f vault redeem"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int doVaultRedeem(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("Only valid for players"));
        } else {
            WarForgeMod.FACTIONS.requestRedeemInsuranceVault(player);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int doOfflineProtectionUsage(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendFailure(Component.literal("Usage: /f offlineprotection <faction> <enable|disable|status>"));
        return Command.SINGLE_SUCCESS;
    }

    private static int doOfflineProtection(CommandContext<CommandSourceStack> ctx, String action) {
        CommandSourceStack src = ctx.getSource();
        Faction targetFaction = WarForgeMod.FACTIONS.getFaction(StringArgumentType.getString(ctx, "faction"));
        if (targetFaction == null) {
            src.sendFailure(Component.literal("Could not find that faction"));
            return Command.SINGLE_SUCCESS;
        }
        switch (action) {
            case "enable" -> {
                targetFaction.offlineRaidProtectionDisabled = false;
                src.sendSuccess(() -> Component.literal("Enabled offline raid protection for " + targetFaction.name), false);
            }
            case "disable" -> {
                targetFaction.offlineRaidProtectionDisabled = true;
                src.sendSuccess(() -> Component.literal("Disabled offline raid protection for " + targetFaction.name), false);
            }
            case "status" -> src.sendSuccess(() -> Component.literal(
                    "Offline protection for " + targetFaction.name + ": "
                            + (targetFaction.offlineRaidProtectionDisabled ? "DISABLED" : "ENABLED")
                            + ", online=" + targetFaction.onlinePlayerCount
                            + ", protectedNow=" + WarForgeMod.FACTIONS.isOfflineRaidProtected(targetFaction)
            ), false);
            default -> src.sendFailure(Component.literal("Usage: /f offlineprotection <faction> <enable|disable|status>"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int doFlagUsage(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendFailure(Component.literal("Usage: /f flag <factionName> <set <flagId> | reset>"));
        return Command.SINGLE_SUCCESS;
    }

    private static int doFlagReset(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Faction faction = WarForgeMod.FACTIONS.getFaction(StringArgumentType.getString(ctx, "faction"));
        if (faction == null) {
            src.sendFailure(Component.literal("Could not find that faction"));
            return Command.SINGLE_SUCCESS;
        }
        WarForgeMod.FACTIONS.adminSetFactionFlag(src, faction.uuid, "");
        return Command.SINGLE_SUCCESS;
    }

    private static int doFlagSet(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Faction faction = WarForgeMod.FACTIONS.getFaction(StringArgumentType.getString(ctx, "faction"));
        if (faction == null) {
            src.sendFailure(Component.literal("Could not find that faction"));
            return Command.SINGLE_SUCCESS;
        }
        WarForgeMod.FACTIONS.adminSetFactionFlag(src, faction.uuid, StringArgumentType.getString(ctx, "flagId").trim());
        return Command.SINGLE_SUCCESS;
    }

    private static int doRenameUsage(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendFailure(Component.literal("Usage: /f rename <oldFactionName> <newFactionName>"));
        return Command.SINGLE_SUCCESS;
    }

    private static int doRename(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Faction targetFaction = WarForgeMod.FACTIONS.getFaction(StringArgumentType.getString(ctx, "oldFactionName"));
        if (targetFaction == null) {
            src.sendFailure(Component.literal("Could not find that faction"));
            return Command.SINGLE_SUCCESS;
        }
        WarForgeMod.FACTIONS.requestRenameFaction(src, targetFaction.uuid, StringArgumentType.getString(ctx, "newFactionName"));
        return Command.SINGLE_SUCCESS;
    }

    private static int doMsg(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (src.getEntity() instanceof Player player) {
            Faction faction = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUUID());
            if (faction != null) {
                String msg = "§a[" + src.getTextName() + " > Faction]§f " + StringArgumentType.getString(ctx, "message");
                faction.messageAll(Component.literal(msg));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int doBorders(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (src.getEntity() instanceof Player) {
            WarForgeMod.showBorders = !WarForgeMod.showBorders;
            src.sendSuccess(() -> Component.literal("Borders Toggled"), false);
        } else
            src.sendFailure(Component.literal("Only valid for players"));
        return Command.SINGLE_SUCCESS;
    }

    private static int doDebugMsg(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayer player))
            return Command.SINGLE_SUCCESS;

        WarForgeMod.FACTIONS.sendNotificationToPlayer(player, "warforge_notification_debug",
                StringArgumentType.getString(ctx, "title"), StringArgumentType.getString(ctx, "subtitle"),
                hexToArgb(StringArgumentType.getString(ctx, "color")), 5000, player.getUUID());
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------------------------------------
    // Vein subcommand
    // ------------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> buildVeinTree() {
        LiteralArgumentBuilder<CommandSourceStack> vein = Commands.literal("vein").requires(CommandFactions::isOp);
        vein.executes(ctx -> { veinUsage(ctx.getSource()); return Command.SINGLE_SUCCESS; });

        vein.then(veinAtCapable(Commands.literal("info"), (ctx, dim, baseX, baseZ, radius) -> doVeinInfo(ctx.getSource(), dim, baseX, baseZ)));
        vein.then(veinAtCapable(Commands.literal("clear"), (ctx, dim, baseX, baseZ, radius) -> doVeinClear(ctx.getSource(), dim, baseX, baseZ, radius)));
        vein.then(veinAtCapable(Commands.literal("reroll"), (ctx, dim, baseX, baseZ, radius) -> doVeinReroll(ctx.getSource(), dim, baseX, baseZ, radius)));
        vein.then(veinAtCapable(Commands.literal("seed"), (ctx, dim, baseX, baseZ, radius) -> doVeinReroll(ctx.getSource(), dim, baseX, baseZ, radius)));

        // set <vein> [quality] [at ...]
        vein.then(Commands.literal("set")
                .then(veinAtCapable(Commands.argument("vein", StringArgumentType.string()),
                        (ctx, dim, baseX, baseZ, radius) -> doVeinSet(ctx, dim, baseX, baseZ, radius, false))
                        .then(veinAtCapable(Commands.argument("quality", StringArgumentType.string()),
                                (ctx, dim, baseX, baseZ, radius) -> doVeinSet(ctx, dim, baseX, baseZ, radius, true)))));

        return vein;
    }

    @FunctionalInterface
    private interface VeinAction {
        int run(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dim, int baseX, int baseZ, int radius);
    }

    // Attaches the optional "at <chunkX> <chunkZ> [dim] [radius]" tail (plus a no-tail executor that
    // derives the location from the sending player) to a vein subcommand node.
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T veinAtCapable(T node, VeinAction action) {
        node.executes(ctx -> runVeinFromPlayer(ctx, action));
        node.then(Commands.literal("at")
                .then(Commands.argument("atX", IntegerArgumentType.integer())
                        .then(Commands.argument("atZ", IntegerArgumentType.integer())
                                .executes(ctx -> runVeinAt(ctx, action, false, false))
                                .then(Commands.argument("atDim", StringArgumentType.string())
                                        .executes(ctx -> runVeinAt(ctx, action, true, false))
                                        .then(Commands.argument("atRadius", IntegerArgumentType.integer())
                                                .executes(ctx -> runVeinAt(ctx, action, true, true)))))));
        return node;
    }

    private static boolean veinReady(CommandSourceStack src) {
        if (WarForgeMod.VEIN_HANDLER == null || !WarForgeMod.VEIN_HANDLER.hasFinishedInit) {
            src.sendFailure(Component.literal("The vein system is not initialized yet"));
            return false;
        }
        return true;
    }

    private static int runVeinFromPlayer(CommandContext<CommandSourceStack> ctx, VeinAction action) {
        CommandSourceStack src = ctx.getSource();
        if (!veinReady(src)) return Command.SINGLE_SUCCESS;
        if (!(src.getEntity() instanceof Player player)) {
            src.sendFailure(Component.literal("From the console you must specify 'at <chunkX> <chunkZ> [dim] [radius]'"));
            return Command.SINGLE_SUCCESS;
        }
        int baseX = player.chunkPosition().x;
        int baseZ = player.chunkPosition().z;
        ResourceKey<Level> dim = player.level().dimension();
        return action.run(ctx, dim, baseX, baseZ, 0);
    }

    private static int runVeinAt(CommandContext<CommandSourceStack> ctx, VeinAction action, boolean hasDim, boolean hasRadius) {
        CommandSourceStack src = ctx.getSource();
        if (!veinReady(src)) return Command.SINGLE_SUCCESS;
        int baseX = IntegerArgumentType.getInteger(ctx, "atX");
        int baseZ = IntegerArgumentType.getInteger(ctx, "atZ");
        ResourceKey<Level> dim = hasDim
                ? ResourceKey.create(Registries.DIMENSION, new ResourceLocation(StringArgumentType.getString(ctx, "atDim")))
                : senderDim(src);
        int radius = hasRadius ? IntegerArgumentType.getInteger(ctx, "atRadius") : 0;
        radius = Math.max(0, Math.min(radius, 16));
        return action.run(ctx, dim, baseX, baseZ, radius);
    }

    private static void veinUsage(CommandSourceStack src) {
        src.sendFailure(Component.literal("/f vein <info|set <vein> [quality]|clear|reroll> [at <chunkX> <chunkZ> [dim] [radius]]"));
    }

    private static int doVeinInfo(CommandSourceStack src, ResourceKey<Level> dim, int baseX, int baseZ) {
        Pair<Vein, Quality> info = WarForgeMod.VEIN_HANDLER.peekStoredVein(dim, baseX, baseZ);
        String location = "chunk (" + baseX + ", " + baseZ + ") in dim " + dim.location();
        if (info == null) {
            src.sendSuccess(() -> Component.literal(location + ": no stored vein (rolls from the seed on first access)"), false);
        } else if (info.getLeft() == null) {
            src.sendSuccess(() -> Component.literal(location + ": cleared (no vein)"), false);
        } else {
            src.sendSuccess(() -> Component.literal(location + ": " + info.getLeft().translationKey + " [" + info.getRight() + "]"), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int doVeinSet(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dim, int baseX, int baseZ, int radius, boolean hasQuality) {
        CommandSourceStack src = ctx.getSource();
        Vein vein = WarForgeMod.VEIN_HANDLER.findVein(StringArgumentType.getString(ctx, "vein"));
        if (vein == null) {
            src.sendFailure(Component.literal("Unknown vein '" + StringArgumentType.getString(ctx, "vein") + "'"));
            return Command.SINGLE_SUCCESS;
        }
        int quality = Quality.FAIR.ordinal();
        if (hasQuality) {
            String qualityArg = StringArgumentType.getString(ctx, "quality");
            try {
                quality = Quality.valueOf(qualityArg.toUpperCase(Locale.ROOT)).ordinal();
            } catch (IllegalArgumentException e) {
                src.sendFailure(Component.literal("Unknown quality '" + qualityArg + "' (POOR, FAIR, RICH)"));
                return Command.SINGLE_SUCCESS;
            }
        }
        int count = 0;
        for (int x = baseX - radius; x <= baseX + radius; ++x) {
            for (int z = baseZ - radius; z <= baseZ + radius; ++z) {
                WarForgeMod.VEIN_HANDLER.setVeinOverride(dim, x, z, vein.getId(), quality);
                WarForgeMod.JOURNEYMAP_VEIN_SYNC.onVeinChanged(dim, x, z);
                ++count;
            }
        }
        final int c = count;
        final int q = quality;
        src.sendSuccess(() -> Component.literal("Set " + c + " chunk(s) to vein " + vein.translationKey + " [" + Quality.getQuality(q) + "]"), false);
        refreshClaimViews(src);
        return Command.SINGLE_SUCCESS;
    }

    private static int doVeinClear(CommandSourceStack src, ResourceKey<Level> dim, int baseX, int baseZ, int radius) {
        int count = 0;
        for (int x = baseX - radius; x <= baseX + radius; ++x) {
            for (int z = baseZ - radius; z <= baseZ + radius; ++z) {
                WarForgeMod.VEIN_HANDLER.clearVeinAt(dim, x, z);
                WarForgeMod.JOURNEYMAP_VEIN_SYNC.onVeinChanged(dim, x, z);
                ++count;
            }
        }
        final int c = count;
        src.sendSuccess(() -> Component.literal("Cleared the vein in " + c + " chunk(s)"), false);
        refreshClaimViews(src);
        return Command.SINGLE_SUCCESS;
    }

    private static int doVeinReroll(CommandSourceStack src, ResourceKey<Level> dim, int baseX, int baseZ, int radius) {
        long seed = src.getServer().overworld().getSeed();
        int count = 0;
        for (int x = baseX - radius; x <= baseX + radius; ++x) {
            for (int z = baseZ - radius; z <= baseZ + radius; ++z) {
                WarForgeMod.VEIN_HANDLER.rerollVeinAt(dim, x, z, seed);
                WarForgeMod.JOURNEYMAP_VEIN_SYNC.onVeinChanged(dim, x, z);
                ++count;
            }
        }
        final int c = count;
        src.sendSuccess(() -> Component.literal("Rerolled the vein in " + c + " chunk(s) from the world seed"), false);
        refreshClaimViews(src);
        return Command.SINGLE_SUCCESS;
    }

    private static ResourceKey<Level> senderDim(CommandSourceStack src) {
        return (src.getEntity() instanceof Player player) ? player.level().dimension() : Level.OVERWORLD;
    }

    private static void refreshClaimViews(CommandSourceStack src) {
        for (ServerPlayer player : src.getServer().getPlayerList().getPlayers()) {
            WarForgeMod.FACTIONS.sendClaimChunks(player, new DimChunkPos(player.level().dimension(), player.blockPosition()), WarForgeConfig.CLAIM_MANAGER_RADIUS);
        }
    }

    public static int hexToArgb(String hex) {
        if (hex == null) return 0xFFFFFFFF;

        String cleanHex = hex.startsWith("#") ? hex.substring(1) : hex;

        try {
            long value = Long.parseLong(cleanHex, 16);

            if (cleanHex.length() <= 6) {
                return (int) (value | 0xFF000000);
            }

            return (int) value;
        } catch (NumberFormatException e) {
            return 0xFFFFFFFF;
        }
    }
}

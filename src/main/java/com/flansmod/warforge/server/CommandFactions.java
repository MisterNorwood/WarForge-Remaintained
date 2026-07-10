package com.flansmod.warforge.server;

import com.flansmod.warforge.api.vein.Quality;
import com.flansmod.warforge.api.vein.Vein;
import com.flansmod.warforge.client.util.WarForgeNotifications;
import com.flansmod.warforge.common.ProtectionsModule;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.network.*;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.common.util.TimeHelper;
import org.apache.commons.lang3.tuple.Pair;
import com.flansmod.warforge.server.Faction.PlayerData;
import com.flansmod.warforge.server.Leaderboard.FactionStat;
import com.mojang.authlib.GameProfile;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class CommandFactions extends CommandBase {
    private static final List<String> ALIASES;
    private static final String[] tabCompletions = new String[]{
            "invite", "accept", "disband", "expel", "leave", "time", "info", "top", "notoriety", "wealth", "legacy",
            "promote", "demote", "msg", "setleader", "borders", "vault",
    };
    private static final String[] tabCompletionsOp = new String[]{
            "invite", "accept", "disband", "expel", "leave", "time", "info", "top", "notoriety", "wealth", "legacy",
            "promote", "demote", "msg", "setleader", "rename", "siege", "borders", "vault",
            "zone", "conquered", "protection", "resetflagcooldowns", "offlineprotection", "debugmsg", "vein"
    };

    static {
        ALIASES = new ArrayList<String>(4);
        ALIASES.add("f");
        ALIASES.add("factions");
        ALIASES.add("war");
        ALIASES.add("warforge");
    }

    @Override
    public String getName() {
        return "faction";
    }

    @Override
    public List<String> getAliases() {
        return ALIASES;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        final boolean isOp = WarForgeMod.isOp(sender);
        final String base = "/f <help|create|invite|accept|disband|expel|remove|leave|exit|setleader|time|info|top|" +
                            "wealth|bal|baltop|notoriety|pvp|pvptop|legacy|playtime|playtimetop|home|spawn|" + "promote|demote|chat|msg|borders|vault>";
        if (!isOp) return base;
        final String opExtras = " | zone|conquered|protection|sieges|clearnotoriety|clearlegacy|resetflagcooldowns|offlineprotection|rename";
        return base + opExtras;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, WarForgeMod.isOp(sender) ? tabCompletionsOp : tabCompletions);
        }

        if (args.length == 2) {
            return switch (args[0]) {
                case "info" -> getListOfStringsMatchingLastWord(args, WarForgeMod.FACTIONS.GetFactionNames());
                case "vault" -> getListOfStringsMatchingLastWord(args, "redeem");
                case "offlineprotection" -> getListOfStringsMatchingLastWord(args, WarForgeMod.FACTIONS.GetFactionNames());
                case "rename" -> getListOfStringsMatchingLastWord(args, WarForgeMod.FACTIONS.GetFactionNames());
                case "invite", "expel", "demote", "promote", "setleader" ->
                        getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
                case "vein" -> getListOfStringsMatchingLastWord(args, "info", "set", "clear", "reroll", "seed");
                default -> getListOfStringsMatchingLastWord(args, new String[0]);
            };
        }

        if (args.length == 3) {
            return switch (args[0]) {
                case "offlineprotection" -> getListOfStringsMatchingLastWord(args, "enable", "disable", "status");
                default -> getListOfStringsMatchingLastWord(args, new String[0]);
            };
        }

        return getListOfStringsMatchingLastWord(args, new String[0]);
    }

    @Override
    //TODO: Holy fucking shit, this needs a total fucking redo
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            sender.sendMessage(new TextComponentString("Try /f help"));
            return;
        }

        Faction faction = null;
        if (sender instanceof EntityPlayer) {
            faction = WarForgeMod.FACTIONS.getFactionOfPlayer(((EntityPlayer) sender).getUniqueID());
        }

        // Argument 0 is subcommand
        switch (args[0].toLowerCase()) {
            case "help": {
                sender.sendMessage(new TextComponentString("/f invite <playerName>"));
                sender.sendMessage(new TextComponentString("/f accept [factionName]"));
                sender.sendMessage(new TextComponentString("/f disband"));
                sender.sendMessage(new TextComponentString("/f expel <playerName>"));
                sender.sendMessage(new TextComponentString("/f leave"));
                sender.sendMessage(new TextComponentString("/f time"));
                sender.sendMessage(new TextComponentString("/f info <factionName>"));
                sender.sendMessage(new TextComponentString("/f top"));
                sender.sendMessage(new TextComponentString("/f wealth"));
                sender.sendMessage(new TextComponentString("/f legacy"));
                sender.sendMessage(new TextComponentString("/f notoriety"));
                sender.sendMessage(new TextComponentString("/f borders"));
                sender.sendMessage(new TextComponentString("/f vault redeem"));
                if (WarForgeMod.isOp(sender)) {
                    sender.sendMessage(new TextComponentString("/f offlineprotection <faction> <enable|disable|status>"));
                }

                if (WarForgeMod.isOp(sender)) {
                    sender.sendMessage(new TextComponentString("/f zone <safe|war|remove>"));
                    sender.sendMessage(new TextComponentString("/f rename <oldFactionName> <newFactionName>"));
                    sender.sendMessage(new TextComponentString("/f vein <info|set <vein> [quality]|clear|reroll> [at <chunkX> <chunkZ> [dim] [radius]]"));
                }

                break;
            }

            case "create": {
                sender.sendMessage(new TextComponentString("Craft a Citadel to create a faction"));
                break;
            }
            case "debugmsg": {
                if (args.length < 3) {
                    sender.sendMessage(new TextComponentString("Invalid arguments, message title and color"));
                    break;
                }
                if(!WarForgeMod.isOp(sender)){
                    sender.sendMessage(new TextComponentString("You must be an operator to use this"));
                    break;
                }
                if(!(sender instanceof EntityPlayerMP))
                    break;

                WarForgeMod.FACTIONS.sendNotificationToPlayer( (EntityPlayerMP) sender, "warforge_notification_debug", args[0], args[1], hexToArgb(args[2]), 5000, sender.getCommandSenderEntity().getPersistentID());


                break;
            }
            case "vein": {
                if (!WarForgeMod.isOp(sender)) {
                    sender.sendMessage(new TextComponentString("You must be an operator to use this"));
                    break;
                }
                handleVeinCommand(server, sender, args);
                break;
            }
            case "invite": {
                // Argument 1 is the player to invite
                if (args.length < 2) {
                    sender.sendMessage(new TextComponentString("Invalid arguments, please specify player name"));
                    break;
                }

                EntityPlayer invitee = server.getPlayerList().getPlayerByUsername(args[1]);
                if (invitee == null) {
                    sender.sendMessage(new TextComponentString("Could not find player " + args[1]));
                    break;
                }

                // First, resolve the op version where we can specify the faction
                if (args.length >= 3 && WarForgeMod.isOp(sender)) {
                    faction = WarForgeMod.FACTIONS.getFaction(args[2]);
                    if (faction != null)
                        WarForgeMod.FACTIONS.RequestInvitePlayerToFaction(sender, faction.uuid, invitee.getUniqueID());
                    else
                        sender.sendMessage(new TextComponentString("Could not find faction " + args[2]));

                    break;
                }

                // Any other case, we assume players can only invite to their own faction
                if (sender instanceof EntityPlayer) {
                    WarForgeMod.FACTIONS.requestInvitePlayerToMyFaction((EntityPlayer) sender, invitee.getUniqueID());
                }

                break;
            }
            case "accept": {
                if (sender instanceof EntityPlayer) {
                    if (args.length >= 2) {
                        WarForgeMod.FACTIONS.RequestAcceptInvite((EntityPlayer) sender, args[1]);
                    } else {
                        WarForgeMod.FACTIONS.RequestAcceptInvite((EntityPlayer) sender);
                    }
                } else {
                    sender.sendMessage(new TextComponentString("The server can't accept a faction invite"));
                }
                break;
            }
            case "disband": {
                if (WarForgeMod.isOp(sender) && args.length == 2) {
                    Faction toDisband = WarForgeMod.FACTIONS.getFaction(args[1]);
                    if (toDisband != null) {
                        WarForgeMod.FACTIONS.FactionDefeated(toDisband);
                    } else {
                        sender.sendMessage(new TextComponentString("Could not find that faction"));
                    }
                } else {
                    if (sender instanceof EntityPlayer && faction != null) {
                        WarForgeMod.FACTIONS.requestDisbandFaction((EntityPlayer) sender, faction.uuid);
                    } else {
                        sender.sendMessage(new TextComponentString("You aren't in a faction"));
                    }
                }
                break;
            }
            case "expel":
            case "remove": {
                if (args.length >= 2) {
                    EntityPlayer toRemove = server.getPlayerList().getPlayerByUsername(args[1]);
                    if (toRemove != null) {
                        UUID toRemoveID = toRemove.getUniqueID();

                        if (faction == null) {
                            faction = WarForgeMod.FACTIONS.getFactionOfPlayer(toRemoveID);
                        }

                        WarForgeMod.FACTIONS.requestRemovePlayerFromFaction(sender, faction.uuid, toRemoveID);
                    } else {
                        sender.sendMessage(new TextComponentString("Could not find player " + args[1]));
                    }
                } else {
                    sender.sendMessage(new TextComponentString("Correct usage is /f " + args[0] + " <username>"));
                }
                break;
            }
            case "leave":
            case "exit": {
                if (sender instanceof EntityPlayer) {
                    WarForgeMod.FACTIONS.requestRemovePlayerFromFaction(sender, faction.uuid, ((EntityPlayer) sender).getUniqueID());
                } else {
                    sender.sendMessage(new TextComponentString("This command is only for players"));
                }
                break;
            }
            case "setleader": {
                if (sender instanceof EntityPlayer) {
                    if (args.length < 2) {
                        sender.sendMessage(new TextComponentString("Please specify the new leader /f setleader <name>"));
                    } else {
                        GameProfile profile = WarForgeMod.MC_SERVER.getPlayerProfileCache().getGameProfileForUsername(args[1]);
                        if (profile == null) {
                            sender.sendMessage(new TextComponentString("Could not find player " + args[1]));
                        } else {
                            Faction targetFaction = WarForgeMod.FACTIONS.getFactionOfPlayer(profile.getId());
                            if (targetFaction == null) {
                                sender.sendMessage(new TextComponentString("That player is not in a faction"));
                            } else {
                                WarForgeMod.FACTIONS.RequestTransferLeadership((EntityPlayer) sender, targetFaction.uuid, profile.getId());
                            }
                        }
                    }
                } else {
                    sender.sendMessage(new TextComponentString("This command is only for players"));
                }
                break;
            }
            case "time": {

                long day = WarForgeMod.numberOfSiegeDaysTicked;
                //long flagCoolown = WarForgeMod.FACTIONS.getPlayerCooldown(sender.getCommandSenderEntity().getUniqueID());
                long ms = WarForgeMod.INSTANCE.getTimeToNextYieldMs();
                long s = ms / 1000;
                long m = s / 60;
                long h = m / 60;
                long d = h / 24;

                sender.sendMessage(new TextComponentString("Yields will next be awarded in "
                        + (d) + " days, "
                        + String.format("%02d", (h % 24)) + ":"
                        + String.format("%02d", (m % 60)) + ":"
                        + String.format("%02d", (s % 60))));

                ms = WarForgeMod.INSTANCE.getTimeToNextSiegeAdvanceMs();
                s = ms / 1000;
                m = s / 60;
                h = m / 60;
                d = h / 24;

                sender.sendMessage(new TextComponentString("Sieges will progress in "
                        + (d) + " days, "
                        + String.format("%02d", (h % 24)) + ":"
                        + String.format("%02d", (m % 60)) + ":"
                        + String.format("%02d", (s % 60))));

                //ms = flagCoolown;
//                s = ms / 1000;
//                m = s / 60;
//                h = m / 60;
//                d = h / 24;
//
//                sender.sendMessage(new TextComponentString("You can move flag in "
//                        + String.format("%02d", (h % 24)) + ":"
//                        + String.format("%02d", (m % 60)) + ":"
//                        + String.format("%02d", (s % 60))));
                break;
            }

            // Faction info
            case "info": {
                if (sender instanceof EntityPlayerMP) {
                    Faction factionToSend = null;
                    if (args.length >= 2) {
                        factionToSend = WarForgeMod.FACTIONS.getFaction(args[1]);
                    }
                    if (factionToSend == null) {
                        factionToSend = WarForgeMod.FACTIONS.getFactionOfPlayer(((EntityPlayerMP) sender).getUniqueID());
                    }

                    if (factionToSend == null) {
                        sender.sendMessage(new TextComponentString("Could not find that faction"));
                    } else {
                        Faction senderFaction = WarForgeMod.FACTIONS.getFactionOfPlayer(((EntityPlayerMP) sender).getUniqueID());
                        if (senderFaction == null || senderFaction.uuid.equals(Faction.nullUuid) || !senderFaction.equals(factionToSend)) {
                            sender.sendMessage(new TextComponentString("Information cannot be provided to non-faction members"));
                        } else {
                            PacketFactionInfo packet = new PacketFactionInfo();
                            packet.info = factionToSend.createInfo();
                            WarForgeMod.NETWORK.sendTo(packet, (EntityPlayerMP) sender);
                        }
                    }
                } else {
                    Faction factionToSend = WarForgeMod.FACTIONS.getFaction(args[1]);
                    if (factionToSend != null) {
                        StringBuilder memberList = new StringBuilder("Members: ");
                        for (HashMap.Entry<UUID, PlayerData> kvp : factionToSend.members.entrySet()) {
                            GameProfile profile = WarForgeMod.MC_SERVER.getPlayerProfileCache().getProfileByUUID(kvp.getKey());
                            if (profile != null) {
                                memberList.append(profile.getName()).append(", ");
                            }
                        }

                        sender.sendMessage(new TextComponentString("**" + factionToSend.name + "**\n"
                                + memberList + "\n"
                                + "Notoriety: " + factionToSend.notoriety + "\n"
                                + "Wealth: " + factionToSend.wealth + "\n"
                                + "Legacy: " + factionToSend.legacy + "\n"));
                    }
                }
                break;
            }

            // Leaderboards
            case "top": {
                if (sender instanceof EntityPlayerMP) {
                    UUID uuid = ((EntityPlayerMP) sender).getUniqueID();
                    PacketLeaderboardInfo packet = new PacketLeaderboardInfo();
                    packet.info = WarForgeMod.LEADERBOARD.CreateInfo(0, FactionStat.TOTAL, uuid);
                    WarForgeMod.NETWORK.sendTo(packet, (EntityPlayerMP) sender);
                } else {
                    LeaderboardInfo info = WarForgeMod.LEADERBOARD.CreateInfo(0, FactionStat.TOTAL, Faction.nullUuid);
                    StringBuilder result = new StringBuilder("**Top Leaderboard**");
                    for (int i = 0; i < LeaderboardInfo.NUM_LEADERBOARD_ENTRIES_PER_PAGE; i++) {
                        FactionDisplayInfo facInfo = info.factionInfos[i];
                        result.append("\n#").append(facInfo.totalRank).append(" | ").append(facInfo.legacy + facInfo.notoriety + facInfo.wealth);
                    }
                    sender.sendMessage(new TextComponentString(result.toString()));
                }
                break;
            }
            case "wealth":
            case "wealthtop":
            case "bal":
            case "baltop": {
                if (sender instanceof EntityPlayerMP) {
                    UUID uuid = ((EntityPlayerMP) sender).getUniqueID();
                    PacketLeaderboardInfo packet = new PacketLeaderboardInfo();
                    packet.info = WarForgeMod.LEADERBOARD.CreateInfo(0, FactionStat.WEALTH, uuid);
                    WarForgeMod.NETWORK.sendTo(packet, (EntityPlayerMP) sender);
                } else {
                    LeaderboardInfo info = WarForgeMod.LEADERBOARD.CreateInfo(0, FactionStat.WEALTH, Faction.nullUuid);
                    String result = "**Top Leaderboard**";
                    for (int i = 0; i < LeaderboardInfo.NUM_LEADERBOARD_ENTRIES_PER_PAGE; i++) {
                        FactionDisplayInfo facInfo = info.factionInfos[i];
                        result = result + "\n#" + facInfo.wealthRank + " | " + facInfo.wealth;
                    }
                    sender.sendMessage(new TextComponentString(result));
                }
                break;
            }
            case "notoriety":
            case "notorietytop":
            case "pvp":
            case "pvptop": {
                if (sender instanceof EntityPlayerMP) {
                    UUID uuid = ((EntityPlayerMP) sender).getUniqueID();
                    PacketLeaderboardInfo packet = new PacketLeaderboardInfo();
                    packet.info = WarForgeMod.LEADERBOARD.CreateInfo(0, FactionStat.NOTORIETY, uuid);
                    WarForgeMod.NETWORK.sendTo(packet, (EntityPlayerMP) sender);
                } else {
                    LeaderboardInfo info = WarForgeMod.LEADERBOARD.CreateInfo(0, FactionStat.NOTORIETY, Faction.nullUuid);
                    String result = "**Notoriety Leaderboard**";
                    for (int i = 0; i < LeaderboardInfo.NUM_LEADERBOARD_ENTRIES_PER_PAGE; i++) {
                        FactionDisplayInfo facInfo = info.factionInfos[i];
                        result = result + "\n#" + facInfo.notorietyRank + " | " + facInfo.notoriety;
                    }
                    sender.sendMessage(new TextComponentString(result));
                }
                break;
            }
            case "legacy":
            case "legacytop":
            case "playtime":
            case "playtimetop": {
                if (sender instanceof EntityPlayerMP) {
                    UUID uuid = ((EntityPlayerMP) sender).getUniqueID();
                    PacketLeaderboardInfo packet = new PacketLeaderboardInfo();
                    packet.info = WarForgeMod.LEADERBOARD.CreateInfo(0, FactionStat.LEGACY, uuid);
                    WarForgeMod.NETWORK.sendTo(packet, (EntityPlayerMP) sender);
                } else {
                    LeaderboardInfo info = WarForgeMod.LEADERBOARD.CreateInfo(0, FactionStat.LEGACY, Faction.nullUuid);
                    String result = "**Legacy Leaderboard**";
                    for (int i = 0; i < LeaderboardInfo.NUM_LEADERBOARD_ENTRIES_PER_PAGE; i++) {
                        FactionDisplayInfo facInfo = info.factionInfos[i];
                        result = result + "\n#" + facInfo.legacyRank + " | " + facInfo.legacy;
                    }
                    sender.sendMessage(new TextComponentString(result));
                }
                break;
            }
            case "zone": {
                if (!WarForgeMod.isOp(sender)) {
                    sender.sendMessage(new TextComponentString("You are not op."));
                    break;
                }
                if (args.length < 2) {
                    sender.sendMessage(new TextComponentString("Usage: /f zone <safe|war|remove>"));
                    break;
                }
                if (!(sender instanceof EntityPlayer)) {
                    sender.sendMessage(new TextComponentString("Use an in-game operator account."));
                    break;
                }
                EntityPlayer zonePlayer = (EntityPlayer) sender;
                DimChunkPos zonePos = new DimBlockPos(zonePlayer).toChunkPos();
                switch (args[1].toLowerCase()) {
                    case "safe":
                        WarForgeMod.FACTIONS.requestZoneClaim(zonePlayer, zonePos, FactionStorage.SAFE_ZONE_ID);
                        refreshClaimViews(server);
                        break;
                    case "war":
                        WarForgeMod.FACTIONS.requestZoneClaim(zonePlayer, zonePos, FactionStorage.WAR_ZONE_ID);
                        refreshClaimViews(server);
                        break;
                    case "remove":
                        WarForgeMod.FACTIONS.requestZoneUnclaim(zonePlayer, zonePos);
                        refreshClaimViews(server);
                        break;
                    default:
                        sender.sendMessage(new TextComponentString("Usage: /f zone <safe|war|remove>"));
                }
                break;
            }
            case "conquered": {
                if (!WarForgeMod.isOp(sender)) {
                    sender.sendMessage(new TextComponentString("You are not op."));
                    break;
                }
                if (!(sender instanceof EntityPlayer)) {
                    sender.sendMessage(new TextComponentString("Use an in-game operator account."));
                    break;
                }
                if (args.length < 2) {
                    sender.sendMessage(new TextComponentString("Usage: /f conquered <clear|set <time>>"));
                    break;
                }
                EntityPlayer conqueredPlayer = (EntityPlayer) sender;
                DimChunkPos conqueredChunk = new DimBlockPos(conqueredPlayer.dimension, conqueredPlayer.getPosition()).toChunkPos();
                switch (args[1].toLowerCase()) {
                    case "clear": {
                        if (WarForgeMod.FACTIONS.clearConquered(conqueredChunk)) {
                            sender.sendMessage(new TextComponentString("Cleared conquered status of chunk [" + conqueredChunk.x + ", " + conqueredChunk.z + "]"));
                        } else {
                            sender.sendMessage(new TextComponentString("This chunk is not conquered"));
                        }
                        break;
                    }
                    case "set": {
                        if (args.length < 3) {
                            sender.sendMessage(new TextComponentString("Usage: /f conquered set <time> (e.g. 30s, 15m, 2h, 1d)"));
                            break;
                        }
                        String timeArg = args[2];
                        long ms = TimeHelper.parseDurationMs(timeArg);
                        if (ms <= 0 || ms > Integer.MAX_VALUE) {
                            sender.sendMessage(new TextComponentString("Invalid time '" + timeArg + "'. Use e.g. 30s, 15m, 2h, 1d (bare numbers are seconds)."));
                            break;
                        }
                        Faction factionOfSender = WarForgeMod.FACTIONS.getFactionOfPlayer(conqueredPlayer.getUniqueID());
                        WarForgeMod.FACTIONS.setConquered(conqueredChunk, factionOfSender == null ? Faction.nullUuid : factionOfSender.uuid, (int) ms);
                        sender.sendMessage(new TextComponentString("Chunk [" + conqueredChunk.x + ", " + conqueredChunk.z
                                + "] is now conquered wilderness, reverting in " + TimeHelper.formatTime(ms)));
                        break;
                    }
                    default:
                        sender.sendMessage(new TextComponentString("Usage: /f conquered <clear|set <time>>"));
                }
                break;
            }
            case "opProtection":
            case "protection":
            case "protectionOverride": {
                if (WarForgeMod.isOp(sender)) {
                    ProtectionsModule.OP_OVERRIDE = !ProtectionsModule.OP_OVERRIDE;
                    if (ProtectionsModule.OP_OVERRIDE)
                        sender.sendMessage(new TextComponentString("Admins can now build in protected areas."));
                    else
                        sender.sendMessage(new TextComponentString("Admins can no longer build in protected areas."));
                } else {
                    sender.sendMessage(new TextComponentString("You are not op."));
                }
                break;
            }
            case "siege":
            case "sieges": {
                if (!WarForgeMod.isOp(sender)) {
                    sender.sendMessage(new TextComponentString("You are not op."));
                    break;
                }
                if (args.length < 2) {
                    sender.sendMessage(new TextComponentString("More arguments required. Possible arguments: list, terminate"));
                    break;
                }
                switch (args[1]) {
                    case "list", "l" -> {
                        var STORAGE = WarForgeMod.FACTIONS;
                        var sieges = STORAGE.getSieges();
                        sender.sendMessage(new TextComponentString("List of current sieges.\nFormat: <ChunkX>, <ChunkY>, <DimID>; <Attacker>-<Defender>"));


                        sieges.forEach((dpos, siege) -> {
                            TextComponentString line = new TextComponentString("§7" + dpos.x + ", " + dpos.z + ", " + dpos.dim + "§r;  " + STORAGE.getFaction(siege.attackingFaction).name + "-" + STORAGE.getFaction(siege.defendingFaction).name);
                            sender.sendMessage(line);
                        });
                    }
                    case "terminate", "t" -> {
                        if (args.length < 4) {
                            sender.sendMessage(new TextComponentString("Not enough arguments. Required: ChunkX, ChunkZ, Dim, <Conclusion type>"));
                            sender.sendMessage(new TextComponentString("Conclusion types WIN, LOSS, NEUTRAL (counting from attacker perspective) Default: NEUTRAL"));
                        }
                        DimChunkPos dimPos;
                        try {
                            int cX = Integer.parseInt(args[2]);
                            int cZ = Integer.parseInt(args[3]);
                            int dim = Integer.parseInt(args[4]);

                            dimPos = new DimChunkPos(dim, cX, cZ);
                        } catch (NumberFormatException e) {
                            sender.sendMessage(new TextComponentString("Incorrect arguments. Required: ChunkX, ChunkZ, Dim, <Conclusion type>"));
                            sender.sendMessage(new TextComponentString("Conclusion types WIN, LOSS, NEUTRAL (counting from attacker perspective) Default: NEUTRAL"));
                            break;
                        }
                        FactionStorage.siegeTermination termType;
                        try {
                            termType = FactionStorage.siegeTermination.valueOf(args[5]);
                        } catch (Exception e) {
                            termType = FactionStorage.siegeTermination.NEUTRAL;
                        }

                        var STORAGE = WarForgeMod.FACTIONS;
                        var sieges = STORAGE.getSieges();
                        if (!sieges.keySet().contains(dimPos)) {
                            sender.sendMessage(new TextComponentString("Provided siege does not exist."));
                        }
                        STORAGE.handleCompletedSiege(dimPos, termType);
                        sender.sendMessage(new TextComponentString("Terminated siege at: " + dimPos.x + ", " + dimPos.z + ", " + dimPos.dim + " with termination type: " + termType.name()));
                    }
                }

                break;
            }
            case "home": {
                if (sender instanceof EntityPlayer)
                    WarForgeMod.TELEPORTS.RequestFHome((EntityPlayer) sender);
                else
                    sender.sendMessage(new TextComponentString("Only valid for players"));
                break;
            }
            case "spawn": {
                if (sender instanceof EntityPlayer)
                    WarForgeMod.TELEPORTS.requestSpawn((EntityPlayer) sender);
                else
                    sender.sendMessage(new TextComponentString("Only valid for players"));
                break;
            }
            case "promote": {
                if (sender instanceof EntityPlayer) {
                    if (args.length == 2) {
                        EntityPlayerMP target = WarForgeMod.MC_SERVER.getPlayerList().getPlayerByUsername(args[1]);
                        if (target == null) {
                            sender.sendMessage(new TextComponentString("Could not find " + args[1]));
                        } else {
                            WarForgeMod.FACTIONS.requestPromote((EntityPlayer) sender, target);
                        }
                    } else {
                        sender.sendMessage(new TextComponentString("Please specify the player you want to promote"));
                    }
                } else
                    sender.sendMessage(new TextComponentString("Only valid for players"));

                break;
            }
            case "demote": {
                if (sender instanceof EntityPlayer) {
                    if (args.length == 2) {
                        EntityPlayerMP target = WarForgeMod.MC_SERVER.getPlayerList().getPlayerByUsername(args[1]);
                        if (target == null) {
                            sender.sendMessage(new TextComponentString("Could not find " + args[1]));
                        } else {
                            WarForgeMod.FACTIONS.requestDemote((EntityPlayer) sender, target);
                        }
                    } else {
                        sender.sendMessage(new TextComponentString("Please specify the player you want to promote"));
                    }
                } else
                    sender.sendMessage(new TextComponentString("Only valid for players"));

                break;
            }
            case "vault": {
                if (!(sender instanceof EntityPlayerMP)) {
                    sender.sendMessage(new TextComponentString("Only valid for players"));
                } else if (args.length == 2 && "redeem".equalsIgnoreCase(args[1])) {
                    WarForgeMod.FACTIONS.requestRedeemInsuranceVault((EntityPlayerMP) sender);
                } else {
                    sender.sendMessage(new TextComponentString("Usage: /f vault redeem"));
                }
                break;
            }
            case "offlineprotection": {
                if (!WarForgeMod.isOp(sender)) {
                    sender.sendMessage(new TextComponentString("Operator permissions required"));
                    break;
                }
                if (args.length < 3) {
                    sender.sendMessage(new TextComponentString("Usage: /f offlineprotection <faction> <enable|disable|status>"));
                    break;
                }
                Faction targetFaction = WarForgeMod.FACTIONS.getFaction(args[1]);
                if (targetFaction == null) {
                    sender.sendMessage(new TextComponentString("Could not find that faction"));
                    break;
                }
                switch (args[2].toLowerCase()) {
                    case "enable" -> {
                        targetFaction.offlineRaidProtectionDisabled = false;
                        sender.sendMessage(new TextComponentString("Enabled offline raid protection for " + targetFaction.name));
                    }
                    case "disable" -> {
                        targetFaction.offlineRaidProtectionDisabled = true;
                        sender.sendMessage(new TextComponentString("Disabled offline raid protection for " + targetFaction.name));
                    }
                    case "status" -> sender.sendMessage(new TextComponentString(
                            "Offline protection for " + targetFaction.name + ": "
                                    + (targetFaction.offlineRaidProtectionDisabled ? "DISABLED" : "ENABLED")
                                    + ", online=" + targetFaction.onlinePlayerCount
                                    + ", protectedNow=" + WarForgeMod.FACTIONS.isOfflineRaidProtected(targetFaction)
                    ));
                    default -> sender.sendMessage(new TextComponentString("Usage: /f offlineprotection <faction> <enable|disable|status>"));
                }
                break;
            }
            case "rename": {
                if (!WarForgeMod.isOp(sender)) {
                    sender.sendMessage(new TextComponentString("Operator permissions required"));
                    break;
                }
                if (args.length < 3) {
                    sender.sendMessage(new TextComponentString("Usage: /f rename <oldFactionName> <newFactionName>"));
                    break;
                }
                Faction targetFaction = WarForgeMod.FACTIONS.getFaction(args[1]);
                if (targetFaction == null) {
                    sender.sendMessage(new TextComponentString("Could not find that faction"));
                    break;
                }
                WarForgeMod.FACTIONS.requestRenameFaction(sender, targetFaction.uuid, args[2]);
                break;
            }
            case "clearnotoriety": {
                if (WarForgeMod.isOp(sender)) {
                    WarForgeMod.FACTIONS.clearNotoriety();
                }
                break;
            }
            case "clearlegacy": {
                if (WarForgeMod.isOp(sender)) {
                    WarForgeMod.FACTIONS.clearLegacy();
                }
                break;
            }
            case "chat":
            case "msg": {
                if (sender instanceof EntityPlayer) {
                    if (faction != null) {
                        StringBuilder msg = new StringBuilder("§a[" + sender.getName() + " > Faction]§f");
                        for (int i = 1; i < args.length; i++)
                            msg.append(" ").append(args[i]);
                        faction.messageAll(new TextComponentString(msg.toString()));
                    }
                }
                break;
            }

            case "resetflagcooldowns": {
                if (WarForgeMod.isOp(sender)) {
                    WarForgeMod.FACTIONS.opResetFlagCooldowns();
                }
                break;
            }
            case "tpa":
            case "tpaccept":
            case "tp":
            case "tprequest": {
                sender.sendMessage(new TextComponentString("Try brewing a potion of Teleportation / Telereception"));
                break;
            }

            case "borders": {
                if (sender instanceof EntityPlayer) {
                    WarForgeMod.showBorders = !WarForgeMod.showBorders;
                    sender.sendMessage(new TextComponentString("Borders Toggled"));
                } else
                    sender.sendMessage(new TextComponentString("Only valid for players"));
                break;
            }


            default: {
                break;
            }
        }

    }


    private void handleVeinCommand(MinecraftServer server, ICommandSender sender, String[] args) {
        if (WarForgeMod.VEIN_HANDLER == null || !WarForgeMod.VEIN_HANDLER.hasFinishedInit) {
            sender.sendMessage(new TextComponentString("The vein system is not initialized yet"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString("/f vein <info|set <vein> [quality]|clear|reroll> [at <chunkX> <chunkZ> [dim] [radius]]"));
            return;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);

        int atIndex = -1;
        for (int i = 2; i < args.length; ++i) {
            if (args[i].equalsIgnoreCase("at")) { atIndex = i; break; }
        }

        int baseX;
        int baseZ;
        int dim;
        int radius = 0;
        if (atIndex >= 0) {
            try {
                baseX = Integer.parseInt(args[atIndex + 1]);
                baseZ = Integer.parseInt(args[atIndex + 2]);
                dim = (args.length > atIndex + 3) ? Integer.parseInt(args[atIndex + 3]) : senderDim(sender);
                if (args.length > atIndex + 4) { radius = Integer.parseInt(args[atIndex + 4]); }
            } catch (Exception e) {
                sender.sendMessage(new TextComponentString("Usage: ... at <chunkX> <chunkZ> [dim] [radius]"));
                return;
            }
        } else if (sender instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) sender;
            baseX = player.chunkCoordX;
            baseZ = player.chunkCoordZ;
            dim = player.dimension;
        } else {
            sender.sendMessage(new TextComponentString("From the console you must specify 'at <chunkX> <chunkZ> [dim] [radius]'"));
            return;
        }

        radius = Math.max(0, Math.min(radius, 16));
        long seed = server.getWorld(0).getSeed();

        switch (sub) {
            case "info": {
                Pair<Vein, Quality> info = WarForgeMod.VEIN_HANDLER.peekStoredVein(dim, baseX, baseZ);
                String location = "chunk (" + baseX + ", " + baseZ + ") in dim " + dim;
                if (info == null) {
                    sender.sendMessage(new TextComponentString(location + ": no stored vein (rolls from the seed on first access)"));
                } else if (info.getLeft() == null) {
                    sender.sendMessage(new TextComponentString(location + ": cleared (no vein)"));
                } else {
                    sender.sendMessage(new TextComponentString(location + ": " + info.getLeft().translationKey + " [" + info.getRight() + "]"));
                }
                break;
            }
            case "set": {
                if (args.length < 3 || args[2].equalsIgnoreCase("at")) {
                    sender.sendMessage(new TextComponentString("/f vein set <veinKeyOrId> [quality]"));
                    return;
                }
                Vein vein = WarForgeMod.VEIN_HANDLER.findVein(args[2]);
                if (vein == null) {
                    sender.sendMessage(new TextComponentString("Unknown vein '" + args[2] + "'"));
                    return;
                }
                int quality = Quality.FAIR.ordinal();
                if (args.length > 3 && !args[3].equalsIgnoreCase("at")) {
                    try {
                        quality = Quality.valueOf(args[3].toUpperCase(Locale.ROOT)).ordinal();
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(new TextComponentString("Unknown quality '" + args[3] + "' (POOR, FAIR, RICH)"));
                        return;
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
                sender.sendMessage(new TextComponentString("Set " + count + " chunk(s) to vein " + vein.translationKey + " [" + Quality.getQuality(quality) + "]"));
                refreshClaimViews(server);
                break;
            }
            case "clear": {
                int count = 0;
                for (int x = baseX - radius; x <= baseX + radius; ++x) {
                    for (int z = baseZ - radius; z <= baseZ + radius; ++z) {
                        WarForgeMod.VEIN_HANDLER.clearVeinAt(dim, x, z);
                        WarForgeMod.JOURNEYMAP_VEIN_SYNC.onVeinChanged(dim, x, z);
                        ++count;
                    }
                }
                sender.sendMessage(new TextComponentString("Cleared the vein in " + count + " chunk(s)"));
                refreshClaimViews(server);
                break;
            }
            case "reroll":
            case "seed": {
                int count = 0;
                for (int x = baseX - radius; x <= baseX + radius; ++x) {
                    for (int z = baseZ - radius; z <= baseZ + radius; ++z) {
                        WarForgeMod.VEIN_HANDLER.rerollVeinAt(dim, x, z, seed);
                        WarForgeMod.JOURNEYMAP_VEIN_SYNC.onVeinChanged(dim, x, z);
                        ++count;
                    }
                }
                sender.sendMessage(new TextComponentString("Rerolled the vein in " + count + " chunk(s) from the world seed"));
                refreshClaimViews(server);
                break;
            }
            default:
                sender.sendMessage(new TextComponentString("Unknown vein subcommand '" + sub + "'"));
        }
    }

    private int senderDim(ICommandSender sender) {
        return (sender instanceof EntityPlayer) ? ((EntityPlayer) sender).dimension : 0;
    }

    private void refreshClaimViews(MinecraftServer server) {
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            WarForgeMod.FACTIONS.sendClaimChunks(player, new DimChunkPos(player.dimension, player.getPosition()), WarForgeConfig.CLAIM_MANAGER_RADIUS);
        }
    }

    public int hexToArgb(String hex) {
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

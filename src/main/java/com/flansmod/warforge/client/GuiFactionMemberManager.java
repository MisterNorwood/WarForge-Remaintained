package com.flansmod.warforge.client;

import brachy.modularui.api.GuiAxis;
import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.drawable.GuiDraw;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.utils.Alignment;
import brachy.modularui.widget.Widget;
import brachy.modularui.widgets.ListWidget;
import brachy.modularui.widgets.ScrollingTextWidget;
import brachy.modularui.widgets.layout.Flow;
import com.flansmod.warforge.client.util.PlayerFaceDrawable;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.FactionMemberManagerGuiData;
import com.flansmod.warforge.common.factories.FactionMemberManagerGuiFactory;
import com.flansmod.warforge.common.factories.FactionStatsGuiFactory;
import com.flansmod.warforge.common.network.PacketFactionAllianceAction;
import com.flansmod.warforge.common.network.PacketFactionMemberManagerAction;
import com.flansmod.warforge.common.network.PacketRequestFobWarp;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.server.Faction;
import net.minecraft.ChatFormatting;

public final class GuiFactionMemberManager {
    private static final int WIDTH = 372;
    private static final int HEIGHT = 268;
    private static final int CONTENT_LEFT = 12;
    private static final int HEADER_Y = 12;
    private static final int TAB_Y = 48;
    private static final int LIST_Y = 78;

    private GuiFactionMemberManager() {
    }

    public static ModularPanel buildPanel(FactionMemberManagerGuiData data) {
        int sectionWidth = WIDTH - CONTENT_LEFT * 2;
        int listSectionHeight = HEIGHT - LIST_Y - 12;

        ModularPanel panel = ModularPanel.defaultPanel("faction_member_manager")
                .width(WIDTH)
                .height(HEIGHT)
                .topRel(0.40f);

        Flow tabSection = ModularGuiStyle.section(sectionWidth, 24).name("faction_member_tabs_section").pos(CONTENT_LEFT, TAB_Y);
        Flow listSection = ModularGuiStyle.section(sectionWidth, listSectionHeight).name("faction_member_list_section").pos(CONTENT_LEFT, LIST_Y);

        panel.child(new IDrawable.DrawableWidget(ModularGuiStyle.headerBackdrop()).size(WIDTH, 40));
        panel.child(tabSection);
        panel.child(listSection);
        panel.child(new IDrawable.DrawableWidget(ModularGuiStyle.colorStripe(data.hasFaction ? data.factionColor : 0x4A4A4A)).size(6, HEIGHT));
        panel.child(ModularGuiStyle.subPanelCloseButton(WIDTH));

        panel.child(Text.str("Faction Members").asWidget()
                .pos(CONTENT_LEFT, HEADER_Y)
                .style(ChatFormatting.BOLD)
                .color(ModularGuiStyle.TEXT_PRIMARY)
                .shadow(true)
                .scale(1.15f));
        if (data.hasFaction) {
            int viewerRoleColor = switch (data.viewerRole) {
                case LEADER -> 0xFFD54A;
                case OFFICER -> 0x55E3FF;
                default -> 0xFFFFFF;
            };
            Flow headerRow = new Flow(GuiAxis.X);
            headerRow.name("faction_member_header_row");
            headerRow.pos(CONTENT_LEFT, HEADER_Y + 15);
            headerRow.height(12);
            headerRow.child(Text.str(data.factionName).asWidget().style(ChatFormatting.BOLD).color(data.factionColor));
            headerRow.child(Text.str(" | ").asWidget().color(ModularGuiStyle.TEXT_SECONDARY));
            headerRow.child(Text.str("Role: " + formatRole(data.viewerRole)).asWidget().color(viewerRoleColor));
            panel.child(headerRow);
        } else {
            panel.child(Text.str("You are not currently in a faction").asWidget()
                    .pos(CONTENT_LEFT, HEADER_Y + 15)
                    .color(ModularGuiStyle.TEXT_SECONDARY));
        }

        if (data.hasFaction) {
            panel.child(ModularGuiStyle.actionButton("Stats", 58, () -> FactionStatsGuiFactory.INSTANCE.openClientSibling(data.factionId))
                    .pos(WIDTH - 80, 11));
        }

        Flow tabRow = new Flow(GuiAxis.X);
        tabRow.name("faction_member_tab_row");
        tabRow.width(sectionWidth - 10);
        tabRow.height(18);
        tabRow.child(tabButton("Members", data.page == FactionMemberManagerGuiData.Page.MEMBERS, FactionMemberManagerGuiData.Page.MEMBERS));
        Widget<?> invitesTab = tabButton("Invites", data.page == FactionMemberManagerGuiData.Page.INVITES, FactionMemberManagerGuiData.Page.INVITES);
        invitesTab.margin(8, 0);
        tabRow.child(invitesTab);
        if (data.hasFaction) {
            Widget<?> alliancesTab = tabButton("Alliances", data.page == FactionMemberManagerGuiData.Page.ALLIANCES, FactionMemberManagerGuiData.Page.ALLIANCES);
            tabRow.child(alliancesTab);
            Widget<?> fobsTab = tabButton("FOBs", data.page == FactionMemberManagerGuiData.Page.FOBS, FactionMemberManagerGuiData.Page.FOBS);
            fobsTab.margin(8, 0);
            tabRow.child(fobsTab);
        }
        tabSection.child(tabRow);

        if (!data.hasFaction && data.page != FactionMemberManagerGuiData.Page.INVITES) {
            listSection.child(Text.str("Join or create a faction before using the member console.").asWidget()
                    .margin(0, 6)
                    .color(0xD5D9DE));
            return panel;
        }

        String sectionTitle = switch (data.page) {
            case MEMBERS -> "Roster";
            case ALLIANCES -> "Alliances";
            case FOBS -> "FOBs";
            default -> data.hasFaction ? "Invite Console" : "Pending Invites";
        };
        String sectionDescription = switch (data.page) {
            case MEMBERS -> "Faces, rank, presence, and direct faction actions.";
            case ALLIANCES -> "Ally with factions to stop sieges between you. Toggle whether allies may use your land.";
            case FOBS ->
                    "Warp to a forward operating base while your faction is under siege. Tickets regenerate each siege cycle.";
            default -> data.hasFaction
                    ? "Invite online unaffiliated players into the faction."
                    : "Accept one of your outstanding faction invites.";
        };

        listSection.child(Text.str(sectionTitle).color(ModularGuiStyle.TEXT_MUTED).asWidget()
                .margin(0, 0, 0, 4)
                .style(ChatFormatting.BOLD));
        listSection.child(Text.str(sectionDescription)
                .asWidget()
                .margin(0, 0, 0, 6)
                .color(ModularGuiStyle.TEXT_MUTED));

        boolean alliancePage = data.page == FactionMemberManagerGuiData.Page.ALLIANCES;
        boolean fobPage = data.page == FactionMemberManagerGuiData.Page.FOBS;
        if (alliancePage) {
            listSection.child(ModularGuiStyle.actionButton(
                            "Ally Access: " + (data.allowAllyInteraction ? "ENABLED" : "DISABLED"),
                            160, data.canManageAlliances,
                            () -> sendAlliance(PacketFactionAllianceAction.Action.TOGGLE_ALLY_BUILD, data.factionId, data.page))
                    .margin(0, 0, 0, 5));
        }

        ListWidget<IWidget, ?> list = new ListWidget<>()
                .name(data.page == FactionMemberManagerGuiData.Page.MEMBERS ? "faction_member_roster_list"
                        : alliancePage ? "faction_alliance_list"
                          : fobPage ? "faction_fob_list" : "faction_member_invite_list")
                .scrollDirection(GuiAxis.Y)
                .background(ModularGuiStyle.insetBackdrop())
                .width(sectionWidth - 10)
                .height(listSectionHeight - 46 - (alliancePage ? 22 : 0));

        if (data.page == FactionMemberManagerGuiData.Page.MEMBERS) {
            if (data.members.isEmpty()) {
                list.addChild(Text.str("No faction members found.").asWidget().pos(6, 6), 0);
            } else {
                int index = 0;
                for (FactionMemberManagerGuiData.MemberEntry member : data.members) {
                    list.addChild(createMemberRow(member, data.page), index++);
                }
            }
        } else if (alliancePage) {
            if (data.alliances.isEmpty()) {
                list.addChild(Text.str("No factions available to ally with yet.").asWidget().pos(6, 6), 0);
            } else {
                int index = 0;
                byte lastKind = -1;
                for (FactionMemberManagerGuiData.AllianceEntry entry : data.alliances) {
                    if (entry.kind != lastKind) {
                        list.addChild(allianceHeaderRow(entry.kind), index++);
                        lastKind = entry.kind;
                    }
                    list.addChild(createAllianceRow(entry, data.canManageAlliances, data.page), index++);
                }
            }
        } else if (fobPage) {
            if (data.fobs.isEmpty()) {
                list.addChild(Text.str("No FOBs established yet.").asWidget().pos(6, 6), 0);
            } else {
                int index = 0;
                for (FactionMemberManagerGuiData.FobEntry fob : data.fobs) {
                    list.addChild(createFobRow(fob), index++);
                }
            }
        } else {
            if (!data.hasFaction) {
                if (data.inviteCandidates.isEmpty()) {
                    list.addChild(Text.str("You have no open faction invites.").asWidget().pos(6, 6), 0);
                } else {
                    int index = 0;
                    for (FactionMemberManagerGuiData.InviteEntry invite : data.inviteCandidates) {
                        list.addChild(createIncomingInviteRow(invite, data.page), index++);
                    }
                }
            } else if (!data.canInvitePlayers) {
                list.addChild(Text.str("Officer or leader rank is required to send invites.").asWidget().pos(6, 6), 0);
            } else if (data.inviteCandidates.isEmpty()) {
                list.addChild(Text.str("No online players are currently eligible for invite.").asWidget().pos(6, 6), 0);
            } else {
                int index = 0;
                for (FactionMemberManagerGuiData.InviteEntry invite : data.inviteCandidates) {
                    list.addChild(createInviteRow(invite, data.page), index++);
                }
            }
        }

        listSection.child(list);
        return panel;
    }

    private static IWidget createMemberRow(FactionMemberManagerGuiData.MemberEntry member, FactionMemberManagerGuiData.Page page) {
        Flow row = new Flow(GuiAxis.X);
        row.name(ModularGuiStyle.debugName("member_row", member.username));
        row.width(WIDTH - 44);
        row.height(24);
        row.mainAxisAlignment(Alignment.MainAxis.START);
        row.padding(3, 3);
        row.margin(0, 0, 0, 2);
        row.background(ModularGuiStyle.insetBackdrop(0xFF232A30));

        String status = member.online ? "Online" : "Offline";
        int rankColor = switch (member.role) {
            case LEADER -> 0xFFD54A;
            case OFFICER -> 0x55E3FF;
            default -> 0xFFFFFF;
        };
        row.child(new IDrawable.DrawableWidget(new PlayerFaceDrawable(member.playerId)).size(18, 18));
        row.child(new ScrollingTextWidget(Text.str(member.username))
                .margin(5, 0)
                .width(96)
                .color(rankColor)
                .tooltip(tooltip -> tooltip.addLine(member.username)));
        row.child(Text.str(formatRole(member.role)).asWidget().width(52).color(rankColor));
        row.child(Text.str(status).color(member.online ? ModularGuiStyle.TEXT_SUCCESS : 0xAAAAAA).asWidget().width(44));

        if (member.canTransferLeadership) {
            row.child(actionButton("Lead", 36, true, PacketFactionMemberManagerAction.Action.TRANSFER_LEADER, member.playerId, page));
        }
        if (member.canPromote) {
            row.child(actionButton("Promote", 54, true, PacketFactionMemberManagerAction.Action.PROMOTE, member.playerId, page));
        }
        if (member.canDemote) {
            row.child(actionButton("Demote", 52, true, PacketFactionMemberManagerAction.Action.DEMOTE, member.playerId, page));
        }
        if (member.canKickOrLeave) {
            row.child(actionButton(member.self ? "Leave" : "Kick", 42, true, PacketFactionMemberManagerAction.Action.KICK_OR_LEAVE, member.playerId, page));
        }

        if (!member.canTransferLeadership && !member.canPromote && !member.canDemote && !member.canKickOrLeave) {
            row.child(Text.str("-").asWidget().width(24));
        }

        return row;
    }

    private static IWidget createInviteRow(FactionMemberManagerGuiData.InviteEntry invite, FactionMemberManagerGuiData.Page page) {
        Flow row = new Flow(GuiAxis.X);
        row.name(ModularGuiStyle.debugName("invite_row", invite.username));
        row.width(WIDTH - 44);
        row.height(24);
        row.mainAxisAlignment(Alignment.MainAxis.START);
        row.padding(3, 3);
        row.margin(0, 0, 0, 2);
        row.background(ModularGuiStyle.insetBackdrop(0xFF232A30));

        row.child(new IDrawable.DrawableWidget(new PlayerFaceDrawable(invite.playerId)).size(18, 18));
        row.child(new ScrollingTextWidget(Text.str(invite.username))
                .margin(5, 0)
                .width(178)
                .tooltip(tooltip -> tooltip.addLine(invite.username)));
        row.child(Text.str(invite.invited ? "Pending" : "Available").color(invite.invited ? 0xFFAA00 : ModularGuiStyle.TEXT_SUCCESS).asWidget().width(64));
        row.child(actionButton(invite.invited ? "Invited" : "Invite", 54, invite.canInvite, PacketFactionMemberManagerAction.Action.INVITE, invite.playerId, page));
        return row;
    }

    private static IWidget createIncomingInviteRow(FactionMemberManagerGuiData.InviteEntry invite, FactionMemberManagerGuiData.Page page) {
        Flow row = new Flow(GuiAxis.X);
        row.name(ModularGuiStyle.debugName("incoming_invite_row", invite.username));
        row.width(WIDTH - 44);
        row.height(24);
        row.mainAxisAlignment(Alignment.MainAxis.START);
        row.padding(3, 3);
        row.margin(0, 0, 0, 2);
        row.background(ModularGuiStyle.insetBackdrop(0xFF232A30));

        if (!invite.inviterId.equals(Faction.nullUuid)) {
            row.child(new IDrawable.DrawableWidget(new PlayerFaceDrawable(invite.inviterId)).size(18, 18));
        } else {
            row.child(Text.str("?").asWidget().width(18).color(ModularGuiStyle.TEXT_MUTED));
        }
        row.child(new ScrollingTextWidget(Text.str(invite.username))
                .margin(5, 0)
                .width(108)
                .color(invite.factionColor)
                .tooltip(tooltip -> tooltip.addLine(invite.username)));
        row.child(new ScrollingTextWidget(Text.str(invite.inviterName.isEmpty() ? "Faction invite" : "From: " + invite.inviterName))
                .width(116)
                .color(ModularGuiStyle.TEXT_SECONDARY)
                .tooltip(tooltip -> {
                    if (!invite.inviterName.isEmpty()) {
                        tooltip.addLine("Invited by " + invite.inviterName);
                    }
                }));
        row.child(actionButton("Join", 44, invite.canAccept, PacketFactionMemberManagerAction.Action.ACCEPT_INVITE, invite.factionId, page));
        return row;
    }

    private static IWidget actionButton(String label, int width, boolean enabled, PacketFactionMemberManagerAction.Action action, java.util.UUID target, FactionMemberManagerGuiData.Page page) {
        return ModularGuiStyle.actionButton(label, width, enabled, () -> {
            PacketFactionMemberManagerAction packet = new PacketFactionMemberManagerAction();
            packet.action = action;
            packet.target = target;
            packet.page = page;
            WarForgeMod.NETWORK.sendToServer(packet);
        });
    }

    private static IWidget allianceHeaderRow(byte kind) {
        String label = switch (kind) {
            case FactionMemberManagerGuiData.AllianceEntry.KIND_ALLY -> "Current Allies";
            case FactionMemberManagerGuiData.AllianceEntry.KIND_PENDING -> "Incoming Requests";
            default -> "Invite a Faction";
        };
        return Text.str(label).asWidget().color(ModularGuiStyle.TEXT_MUTED).margin(2, 0, 4, 2);
    }

    private static IWidget createAllianceRow(FactionMemberManagerGuiData.AllianceEntry entry, boolean canManage, FactionMemberManagerGuiData.Page page) {
        Flow row = new Flow(GuiAxis.X);
        row.name(ModularGuiStyle.debugName("alliance_row", entry.factionName));
        row.width(WIDTH - 44);
        row.height(24);
        row.mainAxisAlignment(Alignment.MainAxis.START);
        row.padding(3, 3);
        row.margin(0, 0, 0, 2);
        row.background(ModularGuiStyle.insetBackdrop(0xFF232A30));

        row.child(new IDrawable.DrawableWidget(swatch(entry.factionColor)).size(18, 18));
        row.child(new ScrollingTextWidget(Text.str(entry.factionName))
                .margin(5, 0)
                .width(128)
                .color(entry.factionColor)
                .tooltip(tooltip -> tooltip.addLine(entry.factionName)));

        if (entry.kind == FactionMemberManagerGuiData.AllianceEntry.KIND_PENDING) {
            row.child(Text.str("Requested").color(0xFFAA00).asWidget().width(58));
            row.child(allianceButton("Accept", 50, canManage, PacketFactionAllianceAction.Action.ACCEPT, entry.factionId, page));
            Widget<?> decline = allianceButton("Decline", 54, canManage, PacketFactionAllianceAction.Action.DECLINE, entry.factionId, page);
            decline.margin(4, 0);
            row.child(decline);
        } else {
            row.child(Text.str(entry.onlineCount + " online").color(entry.onlineCount > 0 ? ModularGuiStyle.TEXT_SUCCESS : 0xAAAAAA).asWidget().width(58));
            if (entry.kind == FactionMemberManagerGuiData.AllianceEntry.KIND_ALLY) {
                if (canManage) {
                    row.child(ModularGuiStyle.dangerButton("Break", 52, () -> sendAlliance(PacketFactionAllianceAction.Action.BREAK, entry.factionId, page)));
                } else {
                    row.child(Text.str("-").asWidget().width(52).color(ModularGuiStyle.TEXT_MUTED));
                }
            } else {
                row.child(allianceButton("Invite", 52, canManage, PacketFactionAllianceAction.Action.INVITE, entry.factionId, page));
            }
        }
        return row;
    }

    private static IWidget createFobRow(FactionMemberManagerGuiData.FobEntry fob) {
        Flow row = new Flow(GuiAxis.X);
        row.name(ModularGuiStyle.debugName("fob_row", fob.name));
        row.width(WIDTH - 44);
        row.height(24);
        row.mainAxisAlignment(Alignment.MainAxis.START);
        row.padding(3, 3);
        row.margin(0, 0, 0, 2);
        row.background(ModularGuiStyle.insetBackdrop(0xFF232A30));

        row.child(new ScrollingTextWidget(Text.str(fob.name.isEmpty() ? "FOB" : fob.name))
                .margin(5, 0)
                .width(150)
                .tooltip(tooltip -> tooltip.addLine(fob.pos.toFancyString())));
        row.child(Text.str(fob.tickets + " / " + fob.maxTickets)
                .color(fob.tickets > 0 ? ModularGuiStyle.TEXT_SUCCESS : 0xFFAA00)
                .asWidget()
                .width(58));
        row.child(fobWarpButton("Warp", 52, fob.canWarp, fob.pos));
        return row;
    }

    private static Widget<?> fobWarpButton(String label, int width, boolean enabled, DimBlockPos pos) {
        return ModularGuiStyle.actionButton(label, width, enabled, () -> {
            PacketRequestFobWarp packet = new PacketRequestFobWarp();
            packet.mPos = pos;
            WarForgeMod.NETWORK.sendToServer(packet);
        });
    }

    private static Widget<?> allianceButton(String label, int width, boolean enabled, PacketFactionAllianceAction.Action action, java.util.UUID target, FactionMemberManagerGuiData.Page page) {
        return ModularGuiStyle.actionButton(label, width, enabled, () -> sendAlliance(action, target, page));
    }

    private static void sendAlliance(PacketFactionAllianceAction.Action action, java.util.UUID target, FactionMemberManagerGuiData.Page page) {
        PacketFactionAllianceAction packet = new PacketFactionAllianceAction();
        packet.action = action;
        packet.target = target;
        packet.page = page;
        WarForgeMod.NETWORK.sendToServer(packet);
    }

    private static IDrawable swatch(int color) {
        return (context, x, y, w, h, theme) ->
                GuiDraw.drawRect(context.getGraphics(), x, y, w, h, 0xFF000000 | (color & 0x00FFFFFF));
    }

    private static Widget<?> tabButton(String label, boolean selected, FactionMemberManagerGuiData.Page page) {
        return ModularGuiStyle.tabButton(label, 74, selected, () -> FactionMemberManagerGuiFactory.INSTANCE.openClientSibling(page));
    }

    private static String formatRole(Faction.Role role) {
        return switch (role) {
            case LEADER -> "Leader";
            case OFFICER -> "Officer";
            default -> "Member";
        };
    }
}

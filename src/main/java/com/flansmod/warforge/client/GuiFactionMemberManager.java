package com.flansmod.warforge.client;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.ScrollingTextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.flansmod.warforge.client.util.PlayerFaceDrawable;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.FactionMemberManagerGuiData;
import com.flansmod.warforge.common.factories.FactionMemberManagerGuiFactory;
import com.flansmod.warforge.common.factories.FactionStatsGuiFactory;
import com.flansmod.warforge.common.network.PacketFactionAllianceAction;
import com.flansmod.warforge.common.network.PacketFactionMemberManagerAction;
import com.flansmod.warforge.server.Faction;
import net.minecraft.util.text.TextFormatting;

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
        panel.child(ModularGuiStyle.panelCloseButton(WIDTH));

        panel.child(IKey.str("Faction Members").asWidget()
                .pos(CONTENT_LEFT, HEADER_Y)
                .style(TextFormatting.BOLD)
                .color(ModularGuiStyle.TEXT_PRIMARY)
                .shadow(true)
                .scale(1.15f));
        if (data.hasFaction) {
            int viewerRoleColor = switch (data.viewerRole) {
                case LEADER -> 0xFFD54A;
                case OFFICER -> 0x55E3FF;
                default -> 0xFFFFFF;
            };
            var headerRow = new Flow(GuiAxis.X);
            headerRow.name("faction_member_header_row");
            headerRow.pos(CONTENT_LEFT, HEADER_Y + 15);
            headerRow.height(12);
            headerRow.child(IKey.str(data.factionName).asWidget().style(TextFormatting.BOLD).color(data.factionColor));
            headerRow.child(IKey.str(" | ").asWidget().color(ModularGuiStyle.TEXT_SECONDARY));
            headerRow.child(IKey.str("Role: " + formatRole(data.viewerRole)).asWidget().color(viewerRoleColor));
            panel.child(headerRow);
        } else {
            panel.child(IKey.str("You are not currently in a faction").asWidget()
                    .pos(CONTENT_LEFT, HEADER_Y + 15)
                    .color(ModularGuiStyle.TEXT_SECONDARY));
        }

        if (data.hasFaction) {
            panel.child(ModularGuiStyle.actionButton("Stats", 58, () -> FactionStatsGuiFactory.INSTANCE.openClient(data.factionId))
                    .pos(WIDTH - 80, 11));
        }

        var tabRow = new Flow(GuiAxis.X);
        tabRow.name("faction_member_tab_row");
        tabRow.width(sectionWidth - 10);
        tabRow.height(18);
        tabRow.child(tabButton("Members", data.page == FactionMemberManagerGuiData.Page.MEMBERS, FactionMemberManagerGuiData.Page.MEMBERS));
        Widget invitesTab = tabButton("Invites", data.page == FactionMemberManagerGuiData.Page.INVITES, FactionMemberManagerGuiData.Page.INVITES);
        invitesTab.margin(8, 0);
        tabRow.child(invitesTab);
        if (data.hasFaction) {
            Widget alliancesTab = tabButton("Alliances", data.page == FactionMemberManagerGuiData.Page.ALLIANCES, FactionMemberManagerGuiData.Page.ALLIANCES);
            tabRow.child(alliancesTab);
        }
        tabSection.child(tabRow);

        if (!data.hasFaction && data.page != FactionMemberManagerGuiData.Page.INVITES) {
            listSection.child(IKey.str("Join or create a faction before using the member console.").asWidget()
                    .margin(0, 6)
                    .color(0xD5D9DE));
            return panel;
        }

        String sectionTitle = switch (data.page) {
            case MEMBERS -> "Roster";
            case ALLIANCES -> "Alliances";
            default -> data.hasFaction ? "Invite Console" : "Pending Invites";
        };
        String sectionDescription = switch (data.page) {
            case MEMBERS -> "Faces, rank, presence, and direct faction actions.";
            case ALLIANCES -> "Ally with factions to stop sieges between you. Toggle whether allies may use your land.";
            default -> data.hasFaction
                    ? "Invite online unaffiliated players into the faction."
                    : "Accept one of your outstanding faction invites.";
        };

        listSection.child(IKey.str(sectionTitle).color(ModularGuiStyle.TEXT_MUTED).asWidget()
                .margin(0, 0, 0, 4)
                .style(TextFormatting.BOLD));
        listSection.child(IKey.str(sectionDescription)
                .asWidget()
                .margin(0, 0, 0, 6)
                .color(ModularGuiStyle.TEXT_MUTED));

        boolean alliancePage = data.page == FactionMemberManagerGuiData.Page.ALLIANCES;
        if (alliancePage) {
            listSection.child(ModularGuiStyle.actionButton(
                            "Ally Access: " + (data.allowAllyInteraction ? "ENABLED" : "DISABLED"),
                            160, data.canManageAlliances,
                            () -> sendAlliance(PacketFactionAllianceAction.Action.TOGGLE_ALLY_BUILD, data.factionId, data.page))
                    .margin(0, 0, 0, 5));
        }

        ListWidget list = new ListWidget<>()
                .name(data.page == FactionMemberManagerGuiData.Page.MEMBERS ? "faction_member_roster_list"
                        : alliancePage ? "faction_alliance_list" : "faction_member_invite_list")
                .scrollDirection(GuiAxis.Y)
                .background(ModularGuiStyle.insetBackdrop())
                .width(sectionWidth - 10)
                .height(listSectionHeight - 46 - (alliancePage ? 22 : 0));

        if (data.page == FactionMemberManagerGuiData.Page.MEMBERS) {
            if (data.members.isEmpty()) {
                list.addChild(IKey.str("No faction members found.").asWidget().pos(6, 6), 0);
            } else {
                int index = 0;
                for (FactionMemberManagerGuiData.MemberEntry member : data.members) {
                    list.addChild(createMemberRow(member, data.page), index++);
                }
            }
        } else if (alliancePage) {
            if (data.alliances.isEmpty()) {
                list.addChild(IKey.str("No factions available to ally with yet.").asWidget().pos(6, 6), 0);
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
        } else {
            if (!data.hasFaction) {
                if (data.inviteCandidates.isEmpty()) {
                    list.addChild(IKey.str("You have no open faction invites.").asWidget().pos(6, 6), 0);
                } else {
                    int index = 0;
                    for (FactionMemberManagerGuiData.InviteEntry invite : data.inviteCandidates) {
                        list.addChild(createIncomingInviteRow(invite, data.page), index++);
                    }
                }
            } else if (!data.canInvitePlayers) {
                list.addChild(IKey.str("Officer or leader rank is required to send invites.").asWidget().pos(6, 6), 0);
            } else if (data.inviteCandidates.isEmpty()) {
                list.addChild(IKey.str("No online players are currently eligible for invite.").asWidget().pos(6, 6), 0);
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

    private static Widget createMemberRow(FactionMemberManagerGuiData.MemberEntry member, FactionMemberManagerGuiData.Page page) {
        var row = new Flow(GuiAxis.X);
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
        row.child(new IDrawable.DrawableWidget(new PlayerFaceDrawable(member.playerId)).size(20, 20));
        row.child(new ScrollingTextWidget(IKey.str(member.username))
                .margin(5, 0)
                .width(96)
                .color(rankColor)
                .tooltip(tooltip -> tooltip.addLine(member.username)));
        row.child(IKey.str(formatRole(member.role)).asWidget().width(52).color(rankColor));
        row.child(IKey.str(status).color(member.online ? ModularGuiStyle.TEXT_SUCCESS : 0xAAAAAA).asWidget().width(44));

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
            row.child(IKey.str("-").asWidget().width(24));
        }

        return row;
    }

    private static Widget createInviteRow(FactionMemberManagerGuiData.InviteEntry invite, FactionMemberManagerGuiData.Page page) {
        Flow row = new Flow(GuiAxis.X);
        row.name(ModularGuiStyle.debugName("invite_row", invite.username));
        row.width(WIDTH - 44);
        row.height(24);
        row.mainAxisAlignment(Alignment.MainAxis.START);
        row.padding(3, 3);
        row.margin(0, 0, 0, 2);
        row.background(ModularGuiStyle.insetBackdrop(0xFF232A30));

        row.child(new IDrawable.DrawableWidget(new PlayerFaceDrawable(invite.playerId)).size(20, 20));
        row.child(new ScrollingTextWidget(IKey.str(invite.username))
                .margin(5, 0)
                .width(178)
                .tooltip(tooltip -> tooltip.addLine(invite.username)));
        row.child(IKey.str(invite.invited ? "Pending" : "Available").color(invite.invited ? 0xFFAA00 : ModularGuiStyle.TEXT_SUCCESS).asWidget().width(64));
        row.child(actionButton(invite.invited ? "Invited" : "Invite", 54, invite.canInvite, PacketFactionMemberManagerAction.Action.INVITE, invite.playerId, page));
        return row;
    }

    private static Widget createIncomingInviteRow(FactionMemberManagerGuiData.InviteEntry invite, FactionMemberManagerGuiData.Page page) {
        Flow row = new Flow(GuiAxis.X);
        row.name(ModularGuiStyle.debugName("incoming_invite_row", invite.username));
        row.width(WIDTH - 44);
        row.height(24);
        row.mainAxisAlignment(Alignment.MainAxis.START);
        row.padding(3, 3);
        row.margin(0, 0, 0, 2);
        row.background(ModularGuiStyle.insetBackdrop(0xFF232A30));

        if (!invite.inviterId.equals(Faction.nullUuid)) {
            row.child(new IDrawable.DrawableWidget(new PlayerFaceDrawable(invite.inviterId)).size(20, 20));
        } else {
            row.child(IKey.str("?").asWidget().width(18).color(ModularGuiStyle.TEXT_MUTED));
        }
        row.child(new ScrollingTextWidget(IKey.str(invite.username))
                .margin(5, 0)
                .width(108)
                .color(invite.factionColor)
                .tooltip(tooltip -> tooltip.addLine(invite.username)));
        row.child(new ScrollingTextWidget(IKey.str(invite.inviterName.isEmpty() ? "Faction invite" : "From: " + invite.inviterName))
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

    private static Widget actionButton(String label, int width, boolean enabled, PacketFactionMemberManagerAction.Action action, java.util.UUID target, FactionMemberManagerGuiData.Page page) {
        return ModularGuiStyle.actionButton(label, width, enabled, () -> {
            PacketFactionMemberManagerAction packet = new PacketFactionMemberManagerAction();
            packet.action = action;
            packet.target = target;
            packet.page = page;
            WarForgeMod.NETWORK.sendToServer(packet);
        });
    }

    private static Widget allianceHeaderRow(byte kind) {
        String label = switch (kind) {
            case FactionMemberManagerGuiData.AllianceEntry.KIND_ALLY -> "Current Allies";
            case FactionMemberManagerGuiData.AllianceEntry.KIND_PENDING -> "Incoming Requests";
            default -> "Invite a Faction";
        };
        return IKey.str(label).asWidget().color(ModularGuiStyle.TEXT_MUTED).margin(2, 0, 4, 2);
    }

    private static Widget createAllianceRow(FactionMemberManagerGuiData.AllianceEntry entry, boolean canManage, FactionMemberManagerGuiData.Page page) {
        Flow row = new Flow(GuiAxis.X);
        row.name(ModularGuiStyle.debugName("alliance_row", entry.factionName));
        row.width(WIDTH - 44);
        row.height(24);
        row.mainAxisAlignment(Alignment.MainAxis.START);
        row.padding(3, 3);
        row.margin(0, 0, 0, 2);
        row.background(ModularGuiStyle.insetBackdrop(0xFF232A30));

        row.child(new IDrawable.DrawableWidget(swatch(entry.factionColor)).size(18, 18));
        row.child(new ScrollingTextWidget(IKey.str(entry.factionName))
                .margin(5, 0)
                .width(128)
                .color(entry.factionColor)
                .tooltip(tooltip -> tooltip.addLine(entry.factionName)));

        if (entry.kind == FactionMemberManagerGuiData.AllianceEntry.KIND_PENDING) {
            row.child(IKey.str("Requested").color(0xFFAA00).asWidget().width(58));
            row.child(allianceButton("Accept", 50, canManage, PacketFactionAllianceAction.Action.ACCEPT, entry.factionId, page));
            Widget decline = allianceButton("Decline", 54, canManage, PacketFactionAllianceAction.Action.DECLINE, entry.factionId, page);
            decline.margin(4, 0);
            row.child(decline);
        } else {
            row.child(IKey.str(entry.onlineCount + " online").color(entry.onlineCount > 0 ? ModularGuiStyle.TEXT_SUCCESS : 0xAAAAAA).asWidget().width(58));
            if (entry.kind == FactionMemberManagerGuiData.AllianceEntry.KIND_ALLY) {
                if (canManage) {
                    row.child(ModularGuiStyle.dangerButton("Break", 52, () -> sendAlliance(PacketFactionAllianceAction.Action.BREAK, entry.factionId, page)));
                } else {
                    row.child(IKey.str("-").asWidget().width(52).color(ModularGuiStyle.TEXT_MUTED));
                }
            } else {
                row.child(allianceButton("Invite", 52, canManage, PacketFactionAllianceAction.Action.INVITE, entry.factionId, page));
            }
        }
        return row;
    }

    private static Widget allianceButton(String label, int width, boolean enabled, PacketFactionAllianceAction.Action action, java.util.UUID target, FactionMemberManagerGuiData.Page page) {
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
        return (context, x, y, w, h, theme) -> net.minecraft.client.gui.Gui.drawRect(x, y, x + w, y + h, 0xFF000000 | (color & 0x00FFFFFF));
    }

    private static Widget tabButton(String label, boolean selected, FactionMemberManagerGuiData.Page page) {
        return ModularGuiStyle.tabButton(label, 74, selected, () -> FactionMemberManagerGuiFactory.INSTANCE.openClient(page));
    }

    private static String formatRole(Faction.Role role) {
        return switch (role) {
            case LEADER -> "Leader";
            case OFFICER -> "Officer";
            default -> "Member";
        };
    }
}

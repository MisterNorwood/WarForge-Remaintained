package com.flansmod.warforge.client.ui;

import com.flansmod.warforge.api.modularui.WarForgeUiTheme;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.FactionMemberManagerGuiData;
import com.flansmod.warforge.common.network.PacketFactionAllianceAction;
import com.flansmod.warforge.common.network.PacketFactionMemberManagerAction;
import com.flansmod.warforge.common.network.PacketMemberData;
import com.flansmod.warforge.common.network.PacketRequestMemberData;
import com.flansmod.warforge.server.Faction;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class MemberManagerScreen {
    private static final int WIDTH = 380;

    private MemberManagerScreen() {
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        PacketMemberData data = PacketMemberData.latest;
        if (data == null) {
            return;
        }

        UIElement root = new UIElement();
        int stripeColor = data.hasFaction ? (data.factionColor & 0xFFFFFF) : 0x4A4A4A;
        UIElement body = WarForgeUiTheme.frame(root, WIDTH, stripeColor);
        Button close = WarForgeUiTheme.closeButton();
        close.setOnClick(event -> Minecraft.getInstance().setScreen(null));

        if (data.hasFaction) {
            int roleColor = switch (data.viewerRole) {
                case LEADER -> 0xFFD54A;
                case OFFICER -> 0x55E3FF;
                default -> 0xFFFFFF;
            };
            body.addChild(WarForgeUiTheme.header("Faction Members", data.factionName + " | Role: " + formatRole(data.viewerRole), roleColor, close));
        } else {
            body.addChild(WarForgeUiTheme.header("Faction Members", close));
            UIElement noFactionRow = WarForgeUiTheme.row(4);
            noFactionRow.addChild(WarForgeUiTheme.text("You are not currently in a faction", WarForgeUiTheme.TEXT_SECONDARY));
            body.addChild(noFactionRow);
        }

        UIElement tabSection = WarForgeUiTheme.section();
        UIElement tabRow = WarForgeUiTheme.row(6);
        Button membersTab = new Button().setText(data.page == FactionMemberManagerGuiData.Page.MEMBERS ? "[Members]" : "Members");
        WarForgeUiTheme.styleButton(membersTab, 74);
        membersTab.setOnClick(event -> {
            PacketRequestMemberData request = new PacketRequestMemberData();
            request.page = FactionMemberManagerGuiData.Page.MEMBERS;
            WarForgeMod.NETWORK.sendToServer(request);
        });
        tabRow.addChild(membersTab);

        Button invitesTab = new Button().setText(data.page == FactionMemberManagerGuiData.Page.INVITES ? "[Invites]" : "Invites");
        WarForgeUiTheme.styleButton(invitesTab, 74);
        invitesTab.setOnClick(event -> {
            PacketRequestMemberData request = new PacketRequestMemberData();
            request.page = FactionMemberManagerGuiData.Page.INVITES;
            WarForgeMod.NETWORK.sendToServer(request);
        });
        tabRow.addChild(invitesTab);

        if (data.hasFaction) {
            Button alliancesTab = new Button().setText(data.page == FactionMemberManagerGuiData.Page.ALLIANCES ? "[Alliances]" : "Alliances");
            WarForgeUiTheme.styleButton(alliancesTab, 80);
            alliancesTab.setOnClick(event -> {
                PacketRequestMemberData request = new PacketRequestMemberData();
                request.page = FactionMemberManagerGuiData.Page.ALLIANCES;
                WarForgeMod.NETWORK.sendToServer(request);
            });
            tabRow.addChild(alliancesTab);
        }
        tabSection.addChild(tabRow);
        body.addChild(tabSection);

        if (!data.hasFaction && data.page != FactionMemberManagerGuiData.Page.INVITES) {
            UIElement infoSection = WarForgeUiTheme.section();
            infoSection.addChild(WarForgeUiTheme.text("Join or create a faction before using the member console.", WarForgeUiTheme.TEXT_SECONDARY));
            body.addChild(infoSection);
            mc.setScreen(new ModularUIScreen(ModularUI.of(UI.of(root), mc.player), Component.literal("Members")));
            return;
        }

        String sectionTitle = switch (data.page) {
            case MEMBERS -> "Roster";
            case ALLIANCES -> "Alliances";
            default -> data.hasFaction ? "Invite Console" : "Pending Invites";
        };
        String sectionDescription = switch (data.page) {
            case MEMBERS -> "Faces, rank, presence, and direct faction actions.";
            case ALLIANCES -> "Ally with factions to stop sieges between you.";
            default -> data.hasFaction
                    ? "Invite online unaffiliated players into the faction."
                    : "Accept one of your outstanding faction invites.";
        };

        UIElement listSection = WarForgeUiTheme.section();
        listSection.addChild(WarForgeUiTheme.boldText(sectionTitle, WarForgeUiTheme.TEXT_MUTED));
        listSection.addChild(WarForgeUiTheme.text(sectionDescription, WarForgeUiTheme.TEXT_MUTED));

        if (data.page == FactionMemberManagerGuiData.Page.ALLIANCES) {
            String allyAccessLabel = "Ally Access: " + (data.allowAllyInteraction ? "ENABLED" : "DISABLED");
            Button allyToggle = new Button().setText(allyAccessLabel);
            WarForgeUiTheme.styleButton(allyToggle, 160);
            if (data.canManageAlliances) {
                allyToggle.setOnClick(event -> sendAlliance(PacketFactionAllianceAction.Action.TOGGLE_ALLY_BUILD, data.factionId, data.page));
            }
            listSection.addChild(allyToggle);
        }

        UIElement list = new UIElement();
        list.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapRow(3));

        if (data.page == FactionMemberManagerGuiData.Page.MEMBERS) {
            if (data.members.isEmpty()) {
                list.addChild(WarForgeUiTheme.text("No faction members found.", WarForgeUiTheme.TEXT_MUTED));
            } else {
                for (FactionMemberManagerGuiData.MemberEntry member : data.members) {
                    list.addChild(memberRow(member, data.page));
                }
            }
        } else if (data.page == FactionMemberManagerGuiData.Page.ALLIANCES) {
            if (data.alliances.isEmpty()) {
                list.addChild(WarForgeUiTheme.text("No factions available to ally with yet.", WarForgeUiTheme.TEXT_MUTED));
            } else {
                byte lastKind = -1;
                for (FactionMemberManagerGuiData.AllianceEntry entry : data.alliances) {
                    if (entry.kind != lastKind) {
                        list.addChild(allianceHeader(entry.kind));
                        lastKind = entry.kind;
                    }
                    list.addChild(allianceRow(entry, data.canManageAlliances, data.page));
                }
            }
        } else {
            if (!data.hasFaction) {
                if (data.inviteCandidates.isEmpty()) {
                    list.addChild(WarForgeUiTheme.text("You have no open faction invites.", WarForgeUiTheme.TEXT_MUTED));
                } else {
                    for (FactionMemberManagerGuiData.InviteEntry invite : data.inviteCandidates) {
                        list.addChild(incomingInviteRow(invite, data.page));
                    }
                }
            } else if (!data.canInvitePlayers) {
                list.addChild(WarForgeUiTheme.text("Officer or leader rank is required to send invites.", WarForgeUiTheme.TEXT_MUTED));
            } else if (data.inviteCandidates.isEmpty()) {
                list.addChild(WarForgeUiTheme.text("No online players are currently eligible for invite.", WarForgeUiTheme.TEXT_MUTED));
            } else {
                for (FactionMemberManagerGuiData.InviteEntry invite : data.inviteCandidates) {
                    list.addChild(inviteRow(invite, data.page));
                }
            }
        }

        listSection.addChild(list);
        body.addChild(listSection);

        mc.setScreen(new ModularUIScreen(ModularUI.of(UI.of(root), mc.player), Component.literal("Members")));
    }

    private static UIElement memberRow(FactionMemberManagerGuiData.MemberEntry member, FactionMemberManagerGuiData.Page page) {
        UIElement row = WarForgeUiTheme.row(6);

        int rankColor = switch (member.role) {
            case LEADER -> 0xFFD54A;
            case OFFICER -> 0x55E3FF;
            default -> 0xFFFFFF;
        };

        com.flansmod.warforge.client.util.PlayerFaceElement memberFace = new com.flansmod.warforge.client.util.PlayerFaceElement(member.playerId);
        memberFace.layout(l -> l.width(18).height(18));
        row.addChild(memberFace);

        row.addChild(WarForgeUiTheme.text(member.username, rankColor).layout(l -> l.width(100)));
        row.addChild(WarForgeUiTheme.text(formatRole(member.role), rankColor).layout(l -> l.width(56)));
        int statusColor = member.online ? WarForgeUiTheme.TEXT_SUCCESS : 0xAAAAAA;
        row.addChild(WarForgeUiTheme.text(member.online ? "Online" : "Offline", statusColor).layout(l -> l.width(48)));

        if (member.canTransferLeadership) {
            row.addChild(memberActionButton("Lead", 36, PacketFactionMemberManagerAction.Action.TRANSFER_LEADER, member.playerId, page));
        }
        if (member.canPromote) {
            row.addChild(memberActionButton("Promote", 54, PacketFactionMemberManagerAction.Action.PROMOTE, member.playerId, page));
        }
        if (member.canDemote) {
            row.addChild(memberActionButton("Demote", 52, PacketFactionMemberManagerAction.Action.DEMOTE, member.playerId, page));
        }
        if (member.canKickOrLeave) {
            row.addChild(memberActionButton(member.self ? "Leave" : "Kick", 42, PacketFactionMemberManagerAction.Action.KICK_OR_LEAVE, member.playerId, page));
        }

        return row;
    }

    private static UIElement inviteRow(FactionMemberManagerGuiData.InviteEntry invite, FactionMemberManagerGuiData.Page page) {
        UIElement row = WarForgeUiTheme.row(6);

        com.flansmod.warforge.client.util.PlayerFaceElement inviteFace = new com.flansmod.warforge.client.util.PlayerFaceElement(invite.playerId);
        inviteFace.layout(l -> l.width(18).height(18));
        row.addChild(inviteFace);

        row.addChild(WarForgeUiTheme.text(invite.username, WarForgeUiTheme.TEXT_PRIMARY).layout(l -> l.width(180)));
        int statusColor = invite.invited ? 0xFFAA00 : WarForgeUiTheme.TEXT_SUCCESS;
        row.addChild(WarForgeUiTheme.text(invite.invited ? "Pending" : "Available", statusColor).layout(l -> l.width(64)));

        Button inviteBtn = new Button().setText(invite.invited ? "Invited" : "Invite");
        WarForgeUiTheme.styleButton(inviteBtn, 54);
        if (invite.canInvite) {
            inviteBtn.setOnClick(event -> {
                PacketFactionMemberManagerAction packet = new PacketFactionMemberManagerAction();
                packet.action = PacketFactionMemberManagerAction.Action.INVITE;
                packet.target = invite.playerId;
                packet.page = page;
                WarForgeMod.NETWORK.sendToServer(packet);
            });
        }
        row.addChild(inviteBtn);

        return row;
    }

    private static UIElement incomingInviteRow(FactionMemberManagerGuiData.InviteEntry invite, FactionMemberManagerGuiData.Page page) {
        UIElement row = WarForgeUiTheme.row(6);

        com.flansmod.warforge.client.util.PlayerFaceElement incomingFace = new com.flansmod.warforge.client.util.PlayerFaceElement(invite.inviterId);
        incomingFace.layout(l -> l.width(18).height(18));
        row.addChild(incomingFace);

        row.addChild(WarForgeUiTheme.text(invite.username, invite.factionColor & 0xFFFFFF).layout(l -> l.width(120)));

        String fromText = invite.inviterName.isEmpty() ? "Faction invite" : "From: " + invite.inviterName;
        row.addChild(WarForgeUiTheme.text(fromText, WarForgeUiTheme.TEXT_SECONDARY).layout(l -> l.width(120)));

        Button joinBtn = new Button().setText("Join");
        WarForgeUiTheme.styleButton(joinBtn, 44);
        if (invite.canAccept) {
            UUID factionId = invite.factionId;
            joinBtn.setOnClick(event -> {
                PacketFactionMemberManagerAction packet = new PacketFactionMemberManagerAction();
                packet.action = PacketFactionMemberManagerAction.Action.ACCEPT_INVITE;
                packet.target = factionId;
                packet.page = page;
                WarForgeMod.NETWORK.sendToServer(packet);
            });
        }
        row.addChild(joinBtn);

        return row;
    }

    private static UIElement allianceHeader(byte kind) {
        String label = switch (kind) {
            case FactionMemberManagerGuiData.AllianceEntry.KIND_ALLY -> "Current Allies";
            case FactionMemberManagerGuiData.AllianceEntry.KIND_PENDING -> "Incoming Requests";
            default -> "Invite a Faction";
        };
        UIElement el = new UIElement();
        el.addChild(WarForgeUiTheme.text(label, WarForgeUiTheme.TEXT_MUTED));
        return el;
    }

    private static UIElement allianceRow(FactionMemberManagerGuiData.AllianceEntry entry, boolean canManage, FactionMemberManagerGuiData.Page page) {
        UIElement row = WarForgeUiTheme.row(6);

        row.addChild(WarForgeUiTheme.text(entry.factionName, entry.factionColor & 0xFFFFFF).layout(l -> l.width(140)));

        if (entry.kind == FactionMemberManagerGuiData.AllianceEntry.KIND_PENDING) {
            row.addChild(WarForgeUiTheme.text("Requested", 0xFFAA00).layout(l -> l.width(64)));
            if (canManage) {
                row.addChild(allianceActionButton("Accept", 50, PacketFactionAllianceAction.Action.ACCEPT, entry.factionId, page));
                row.addChild(allianceActionButton("Decline", 54, PacketFactionAllianceAction.Action.DECLINE, entry.factionId, page));
            }
        } else {
            int onlineColor = entry.onlineCount > 0 ? WarForgeUiTheme.TEXT_SUCCESS : 0xAAAAAA;
            row.addChild(WarForgeUiTheme.text(entry.onlineCount + " online", onlineColor).layout(l -> l.width(64)));
            if (entry.kind == FactionMemberManagerGuiData.AllianceEntry.KIND_ALLY) {
                if (canManage) {
                    Button breakBtn = new Button().setText("Break");
                    WarForgeUiTheme.styleButton(breakBtn, 52, WarForgeUiTheme.DANGER_FILL);
                    breakBtn.setOnClick(event -> sendAlliance(PacketFactionAllianceAction.Action.BREAK, entry.factionId, page));
                    row.addChild(breakBtn);
                }
            } else {
                if (canManage) {
                    row.addChild(allianceActionButton("Invite", 52, PacketFactionAllianceAction.Action.INVITE, entry.factionId, page));
                }
            }
        }

        return row;
    }

    private static Button memberActionButton(String label, int width, PacketFactionMemberManagerAction.Action action, UUID target, FactionMemberManagerGuiData.Page page) {
        Button btn = new Button().setText(label);
        WarForgeUiTheme.styleButton(btn, width);
        btn.setOnClick(event -> {
            PacketFactionMemberManagerAction packet = new PacketFactionMemberManagerAction();
            packet.action = action;
            packet.target = target;
            packet.page = page;
            WarForgeMod.NETWORK.sendToServer(packet);
        });
        return btn;
    }

    private static Button allianceActionButton(String label, int width, PacketFactionAllianceAction.Action action, UUID target, FactionMemberManagerGuiData.Page page) {
        Button btn = new Button().setText(label);
        WarForgeUiTheme.styleButton(btn, width);
        btn.setOnClick(event -> sendAlliance(action, target, page));
        return btn;
    }

    private static void sendAlliance(PacketFactionAllianceAction.Action action, UUID target, FactionMemberManagerGuiData.Page page) {
        PacketFactionAllianceAction packet = new PacketFactionAllianceAction();
        packet.action = action;
        packet.target = target;
        packet.page = page;
        WarForgeMod.NETWORK.sendToServer(packet);
    }

    private static String formatRole(Faction.Role role) {
        return switch (role) {
            case LEADER -> "Leader";
            case OFFICER -> "Officer";
            default -> "Member";
        };
    }
}

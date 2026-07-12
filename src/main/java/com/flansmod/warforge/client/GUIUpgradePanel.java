package com.flansmod.warforge.client;

import brachy.modularui.ModularUI;
import brachy.modularui.api.GuiAxis;
import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.drawable.ItemDrawable;
import brachy.modularui.drawable.UITexture;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.utils.Alignment;
import brachy.modularui.widget.Widget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ListWidget;
import brachy.modularui.widgets.layout.Flow;
import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.factories.FactionUpgradeGuiData;
import com.flansmod.warforge.common.network.PacketRequestUpgrade;
import com.flansmod.warforge.server.ItemMatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.flansmod.warforge.common.WarForgeMod.NETWORK;
import static com.flansmod.warforge.common.WarForgeMod.UPGRADE_HANDLER;

public class GUIUpgradePanel {
    public static final int WIDTH = 398;
    public static final int HEIGHT = 350;
    private static final int CONTENT_LEFT = 12;
    private static final int HEADER_Y = 12;
    private static final int BODY_Y = 54;
    private static final int ACTIONS_Y = HEIGHT - 40;
    private static final int BODY_SECTION_HEIGHT = ACTIONS_Y - BODY_Y - 10;

    public static ModularScreen createGui(UUID factionID, String factionName, int level, int color, boolean outrankingOfficer) {
        FactionUpgradeGuiData data = new FactionUpgradeGuiData(Minecraft.getInstance().player, factionID);
        data.factionId = factionID;
        data.factionName = factionName;
        data.level = level;
        data.color = color;
        data.outrankingOfficer = outrankingOfficer;
        return new ModularScreen(Tags.MODID, buildPanel(data));
    }

    public static ModularPanel buildPanel(FactionUpgradeGuiData data) {
        UUID factionID = data.factionId;
        String factionName = data.factionName;
        int level = data.level;
        int color = data.color;
        boolean outrankingOfficer = data.outrankingOfficer;
        int contentWidth = WIDTH - CONTENT_LEFT * 2 - 10;
        int listWidth = contentWidth;
        int progressionPanelHeight = 54;
        int listHeight = BODY_SECTION_HEIGHT - progressionPanelHeight - 25;
        ListWidget list = new ListWidget<>()
                .name("upgrade_requirement_list")
                .scrollDirection(GuiAxis.Y)
                .background(ModularGuiStyle.insetBackdrop())
                .marginBottom(5)
                .widthRel(0.98f);

        if (UPGRADE_HANDLER.getRequirementsFor(level + 1) == null) {
            return createNoMoreLevelsPanel();
        }

        // Sorted by quantity
        Map<ItemMatcher, Integer> requirements = UPGRADE_HANDLER.getRequirementsFor(level + 1)
                .entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getValue))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Player player = Minecraft.getInstance().player;
        AtomicBoolean requirementPassed = new AtomicBoolean(true);
        List<ItemStack> inventory = player.getInventory().items;

        List<ItemMatcher.MatchResult> results = requirements.entrySet().stream()
                .map(entry -> {
                    ItemMatcher sc = entry.getKey();
                    int required = entry.getValue();

                    int count = inventory.stream()
                            .filter(stack -> !stack.isEmpty() && sc.matches(stack))
                            .mapToInt(ItemStack::getCount)
                            .sum();

                    if (count < required)
                        requirementPassed.set(false);

                    return new ItemMatcher.MatchResult(sc, count, required);
                })
                .collect(Collectors.toList());

        AtomicInteger index = new AtomicInteger(0);
        results.forEach((comparableResult) -> {
            // resolve a representative stack for the requirement (item tags resolve to their first entry)
            ItemStack stack = comparableResult.matcher().toStack();
            if (stack.isEmpty()) {
                WarForgeMod.LOGGER.error("Malformed item requirement: \n" + comparableResult + "\nSkipping...");
                return;
            }

            String displayName;
            if (comparableResult.matcher().isTag()) {
                displayName = humanizeOreDictName(comparableResult.matcher().id());
            } else {
                displayName = stack.getHoverName().getString();
            }

            list.addChild(createRequirementRow(stack, displayName, comparableResult.required(), comparableResult.has(), listWidth - 10), index.getAndIncrement());
        });

        ModularPanel panel = ModularPanel.defaultPanel("citadel_upgrade_panel")
                .width(WIDTH)
                .height(HEIGHT)
                .topRel(0.5f);

        Flow bodySection = ModularGuiStyle.section(WIDTH - CONTENT_LEFT * 2, BODY_SECTION_HEIGHT).name("upgrade_body_section").pos(CONTENT_LEFT, BODY_Y);
        Flow actionSection = ModularGuiStyle.section(WIDTH - CONTENT_LEFT * 2, 28).name("upgrade_action_section").pos(CONTENT_LEFT, ACTIONS_Y);

        panel.child(new IDrawable.DrawableWidget(ModularGuiStyle.headerBackdrop()).name("upgrade_header_backdrop").size(WIDTH, 40));
        panel.child(bodySection);
        panel.child(actionSection);
        panel.child(new IDrawable.DrawableWidget(ModularGuiStyle.colorStripe(color)).name("upgrade_color_stripe").size(6, HEIGHT));
        panel.child(ModularGuiStyle.subPanelCloseButton(WIDTH));

        Widget<?> prefix = Text.str("Citadel Upgrade")
                .asWidget()
                .name("upgrade_title")
                .pos(CONTENT_LEFT, HEADER_Y)
                .color(ModularGuiStyle.TEXT_PRIMARY)
                .shadow(true)
                .style(ChatFormatting.BOLD)
                .scale(1.15f);

        Widget<?> factionNamePlate = Text.str(factionName)
                .asWidget()
                .name("upgrade_faction_name")
                .color(color)
                .shadow(true)
                .pos(CONTENT_LEFT, HEADER_Y + 15)
                .style(ChatFormatting.BOLD)
                .scale(1.0f);

        panel.child(prefix);
        panel.child(factionNamePlate);
        bodySection.child(Text.str("Requirements to advance from level " + level + " to level " + (level + 1)).asWidget()
                .name("upgrade_requirement_prompt")
                .margin(0, 0, 0, 6)
                .color(ModularGuiStyle.TEXT_MUTED));

        int closeButtonWidth = 94;
        int upgradeButtonWidth = 104;
        ButtonWidget<?> closeButton = ModularGuiStyle.actionButton("Close", closeButtonWidth, () -> panel.closeIfOpen());
        closeButton.name("upgrade_close_button");

        boolean canUpgrade = requirementPassed.get() && outrankingOfficer;
        ButtonWidget<?> upgradeButton = ModularGuiStyle.actionButton("Upgrade", upgradeButtonWidth, canUpgrade, () -> {
                    PacketRequestUpgrade packet = new PacketRequestUpgrade();
                    packet.factionID = factionID;
                    NETWORK.sendToServer(packet);
                    panel.closeIfOpen();
                });
        upgradeButton.name("upgrade_confirm_button");

        if (!outrankingOfficer || !requirementPassed.get()) {
            upgradeButton.tooltip(richTooltip -> {
                richTooltip.addLine(Text.str("You don't meet following requirements:").style(ChatFormatting.BOLD, ChatFormatting.RED));
                if (!outrankingOfficer) richTooltip.addLine(Text.str("- You need to be officer of or higher!"));
                if (!requirementPassed.get()) richTooltip.addLine(Text.str("- You don't have the materials required!"));
            });
        }

        UITexture arrow = UITexture.builder()
                .location(ModularUI.MOD_ID, "gui/widgets/progress_bar_arrow")
                .imageSize(20, 40)
                .subAreaXYWH(0, 20, 20, 20)
                .build();

        list.width(listWidth);
        list.height(listHeight);
        bodySection.child(list);
        Widget<?> outcomePanel = createUpgradeOutcomePanel(contentWidth, progressionPanelHeight, level, arrow);
        outcomePanel.margin(2, 2, 2, 0);
        bodySection.child(outcomePanel);

        var actionRow = new Flow(GuiAxis.X);
        actionRow.name("upgrade_action_row");
        actionRow.width(contentWidth);
        actionRow.height(18);
        actionRow.child(closeButton);
        upgradeButton.margin(contentWidth - closeButtonWidth - upgradeButtonWidth, 0);
        actionRow.child(upgradeButton);
        actionSection.child(actionRow);

        return panel;
    }

    private static Widget<?> createUpgradeOutcomePanel(int width, int height, int level, UITexture arrow) {
        Flow outcomePanel = new Flow(GuiAxis.Y);
        outcomePanel.name("upgrade_outcome_panel");
        outcomePanel.height(height);
        outcomePanel.padding(0);
        outcomePanel.coverChildrenWidth();

        outcomePanel.child(Text.str("Upgrade Outcome").asWidget()
                .name("upgrade_outcome_title")
                .color(ModularGuiStyle.TEXT_PRIMARY)
                .style(ChatFormatting.BOLD)
                .margin(0, 0, 0, 1));
        outcomePanel.child(Text.str("Level " + level + " -> " + (level + 1)).asWidget()
                .name("upgrade_outcome_level_text")
                .color(ModularGuiStyle.TEXT_WARNING)
                .shadow(true)
                .style(ChatFormatting.BOLD)
                .margin(0, 0, 0, 2));

        var deltaRow = new Flow(GuiAxis.X);
        deltaRow.name("upgrade_delta_row");
        deltaRow.height(20);
        deltaRow.coverChildrenWidth();
        deltaRow.marginTop(2);

        Widget<?> claimCard = createUpgradeDeltaCard(
                "Claims",
                String.valueOf(UPGRADE_HANDLER.getClaimLimitForLevel(level)),
                String.valueOf(UPGRADE_HANDLER.getClaimLimitForLevel(level + 1)),
                0xFF5555,
                0x55FF55,
                arrow
        );
        Widget<?> insuranceCard = createUpgradeDeltaCard(
                "Insurance",
                String.valueOf(UPGRADE_HANDLER.getInsuranceSlotsForLevel(level)),
                String.valueOf(UPGRADE_HANDLER.getInsuranceSlotsForLevel(level + 1)),
                0x6CB6FF,
                0x8BFFB0,
                arrow
        );
        claimCard.marginRight(1);
        insuranceCard.marginLeft(1);

        deltaRow.child(claimCard);
        deltaRow.child(insuranceCard);
        outcomePanel.child(deltaRow);

        return outcomePanel;
    }

    private static Widget<?> createUpgradeDeltaCard(String label, String previous, String next, int previousColor, int nextColor, UITexture arrow) {
        Flow card = new Flow(GuiAxis.Y);
        card.name(ModularGuiStyle.debugName("upgrade_delta_card", label));
        card.padding(0);
        card.coverChildren();

        card.child(Text.str(label).asWidget()
                .name(ModularGuiStyle.debugName("upgrade_delta_label", label))
                .color(ModularGuiStyle.TEXT_SECONDARY));

        var valueRow = new Flow(GuiAxis.X);
        valueRow.name(ModularGuiStyle.debugName("upgrade_delta_values", label));
        valueRow.height(10);
        valueRow.coverChildrenWidth();
        valueRow.child(Text.str(previous).asWidget()
                .name(ModularGuiStyle.debugName("upgrade_delta_previous", label))
                .color(previousColor)
                .shadow(true)
                .scale(1.15f));
        valueRow.child(new IDrawable.DrawableWidget(arrow)
                .name(ModularGuiStyle.debugName("upgrade_delta_arrow", label))
                .height(8)
                .width(10)
                .margin(1, 0));
        valueRow.child(Text.str(next).asWidget()
                .name(ModularGuiStyle.debugName("upgrade_delta_next", label))
                .color(nextColor)
                .shadow(true)
                .style(ChatFormatting.BOLD)
                .scale(1.15f));
        card.child(valueRow);

        return card;
    }

    private static ModularPanel createNoMoreLevelsPanel() {
        ModularPanel panel = ModularPanel.defaultPanel("no_more_levels_panel", 220, 76);
        panel
                .child(new IDrawable.DrawableWidget(ModularGuiStyle.headerBackdrop()).name("upgrade_max_level_backdrop").size(220, 76))
                .child(new IDrawable.DrawableWidget(ModularGuiStyle.colorStripe(0xFF9F7A34)).name("upgrade_max_level_stripe").size(6, 76))
                .child(Text.str("Your citadel is at it's max level!").asWidget()
                        .name("upgrade_max_level_text")
                        .pos(16, 16)
                        .shadow(true)
                        .style(ChatFormatting.WHITE)
                        .height(16)
                )
                .child(ModularGuiStyle.actionButton("Close", 96, () -> panel.closeIfOpen())
                        .pos(16, 42)
                );
        return panel;
    }

    public static String humanizeOreDictName(String oredict) {
        String name = oredict.startsWith("any") ? oredict.substring(3) : oredict;

        List<String> words = new ArrayList<>();
        Matcher m = Pattern.compile("([A-Z]?[a-z]+|[A-Z]+(?![a-z]))").matcher(name);
        while (m.find()) {
            words.add(m.group());
        }

        if (words.isEmpty()) {
            return "Any " + oredict;
        }

        Set<String> types = new HashSet<>(Arrays.asList(
                "ingot", "dust", "plate", "nugget", "ore", "gem", "block", "gear",
                "rod", "wire", "bolt", "screw", "foil", "tiny", "small", "cell",
                "dye", "plastic", "circuit"
        ));

        List<String> capitalized = new ArrayList<>();
        for (String word : words) {
            if (word.length() == 1) {
                capitalized.add(word.toUpperCase());
            } else {
                capitalized.add(Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase());
            }
        }

        String first = words.get(0).toLowerCase();
        if (types.contains(first)) {
            String typeWord = capitalized.remove(0);
            capitalized.add(typeWord);
        }

        return "Any " + String.join(" ", capitalized);
    }

    private static Flow createRequirementRow(ItemStack stack, String displayName, int count, int has, int rowWidth) {
        UITexture check = UITexture.builder()
                .location(ModularUI.MOD_ID, "gui/widgets/toggle_config")
                .imageSize(14, 28)
                .subAreaXYWH(2, 16, 10, 10)
                .build();
        int nameLeft = 27;
        int countLeft = rowWidth - 78;
        int nameWidth = Math.max(110, countLeft - nameLeft - 8);
        return new Flow(GuiAxis.X)
                .name(ModularGuiStyle.debugName("requirement_row", displayName))
                .width(rowWidth)
                .child(new IDrawable.DrawableWidget(new ItemDrawable(stack))
                        .name(ModularGuiStyle.debugName("requirement_icon", displayName))
                        .size(20, 20)
                        .posRel(Alignment.CenterLeft)
                )
                .child(Text.str(displayName).asWidget()
                        .name(ModularGuiStyle.debugName("requirement_name", displayName))
                        .textAlign(Alignment.CenterLeft)
                        .left(nameLeft)
                        .bottomRel(0.5f)
                        .width(nameWidth)
                        .color(0xFFFFFF)
                        .shadow(true)
                )
                .child(Text.str(has + "/" + count).asWidget()
                        .name(ModularGuiStyle.debugName("requirement_count", displayName))
                        .textAlign(Alignment.CenterLeft)
                        .left(countLeft)
                        .color(has >= count ? 0x55FF55 : 0xFF5555)
                        .shadow(true)
                        .bottomRel(0.5f)
                        .scale(1.5f)
                )
                .child(new IDrawable.DrawableWidget(check)
                        .name(ModularGuiStyle.debugName("requirement_check", displayName))
                        .posRel(Alignment.CenterRight)
                        .setEnabledIf(x -> has >= count)
                        .size(20, 20)
                        .right(5)
                )
                .paddingLeft(5)
                .paddingRight(5)
                .margin(2, 2)
                .height(30)
                .background(ModularGuiStyle.insetBackdrop())
                ;
    }
}

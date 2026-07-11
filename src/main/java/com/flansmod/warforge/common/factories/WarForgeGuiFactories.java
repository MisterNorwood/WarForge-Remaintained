package com.flansmod.warforge.common.factories;

public final class WarForgeGuiFactories {
    private WarForgeGuiFactories() {
    }

    public static void init() {
        BasicClaimGuiFactory.init();
        CitadelGuiFactory.init();
        ClaimManagerGuiFactory.init();
        FactionFlagSelectGuiFactory.init();
        FactionInsuranceGuiFactory.init();
        FactionMemberManagerGuiFactory.init();
        FactionStatsGuiFactory.init();
        FactionUpgradeGuiFactory.init();
        FobGuiFactory.init();
        SiegeCampGuiFactory.init();
    }
}

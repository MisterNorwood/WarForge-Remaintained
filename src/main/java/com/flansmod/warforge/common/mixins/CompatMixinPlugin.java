package com.flansmod.warforge.common.mixins;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Gates the soft-dependency mixins in mixins.warforge.compat.json. Each maps to the modid that must be
// present for it to apply; getMixins() only registers the ones whose mod is installed, so absent mods cost
// nothing and produce no missing-target warnings. Add a new SBW-addon fix by writing the mixin and adding
// one entry here. LoadingModList is already populated when Mixin prepares configs.
public class CompatMixinPlugin implements IMixinConfigPlugin {

    // mixin class (simple name, relative to the config package) -> required modid
    private static final Map<String, String> GATED_MIXINS = Map.of(
            "SbwExplosionClaimMixin", "superbwarfare",
            "AshVehicleExplosionClaimMixin", "ashvehicle"
    );

    private static boolean modPresent(String modId) {
        return LoadingModList.get() != null && LoadingModList.get().getModFileById(modId) != null;
    }

    @Override
    public List<String> getMixins() {
        List<String> mixins = new ArrayList<>();
        for (Map.Entry<String, String> entry : GATED_MIXINS.entrySet()) {
            if (modPresent(entry.getValue())) {
                mixins.add(entry.getKey());
            }
        }
        return mixins;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        String modId = GATED_MIXINS.get(simpleName);
        return modId == null || modPresent(modId);
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}

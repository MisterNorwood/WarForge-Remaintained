package com.flansmod.warforge.common.mixins;

import com.flansmod.warforge.common.ExplosionProtection;
import com.flansmod.warforge.server.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Ash Vehicles (an SBW addon) missiles/bombs punch direct Level.destroyBlock craters that fire no event
// and so bypass claims, exactly like SBW. Re-route them through the same claim check. Most use the 2-arg
// destroyBlock; the GBU-57 bunker buster uses the 3-arg (Entity) overload, so both are guarded. Targets
// are strings so this compiles without Ash Vehicles present; the whole mixin is only registered when the
// mod is loaded (see CompatMixinPlugin).
// Ash Vehicles renamed its package Aru.Aru.* (older CF builds) -> aru.aru.* (newer "div" builds). Both
// casings are listed so this protects claims regardless of which build the pack ships; the missing casing
// is skipped harmlessly (require=0 + CompatMixinPlugin gates the whole mixin on the mod being present).
@Mixin(remap = false, targets = {
        "aru.aru.ashvehicle.entity.projectile.Agm114Entity",
        "aru.aru.ashvehicle.entity.projectile.Agm158Entity",
        "aru.aru.ashvehicle.entity.projectile.Aim120Entity",
        "aru.aru.ashvehicle.entity.projectile.Aim54Entity",
        "aru.aru.ashvehicle.entity.projectile.Aim9Entity",
        "aru.aru.ashvehicle.entity.projectile.Gbu57Entity",
        "aru.aru.ashvehicle.entity.projectile.R60Entity",
        "Aru.Aru.ashvehicle.entity.projectile.Agm114Entity",
        "Aru.Aru.ashvehicle.entity.projectile.Agm158Entity",
        "Aru.Aru.ashvehicle.entity.projectile.Aim120Entity",
        "Aru.Aru.ashvehicle.entity.projectile.Aim54Entity",
        "Aru.Aru.ashvehicle.entity.projectile.Aim9Entity",
        "Aru.Aru.ashvehicle.entity.projectile.Gbu57Entity",
        "Aru.Aru.ashvehicle.entity.projectile.R60Entity",
})
@SuppressWarnings("unused")
public class AshVehicleExplosionClaimMixin {

    // require = 0: each redirect only matches the subset of targets using that overload (e.g. only Gbu57
    // uses the 3-arg form), so the other targets legitimately yield zero injection points.
    @Redirect(
            method = "*",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z",
                    remap = true),
            require = 0)
    private boolean warforge$guardDestroy(Level level, BlockPos pos, boolean dropBlock) {
        if (ExplosionProtection.isProtected(level, Faction.nullUuid, pos)) {
            return false;
        }
        return level.destroyBlock(pos, dropBlock);
    }

    @Redirect(
            method = "*",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z",
                    remap = true),
            require = 0)
    private boolean warforge$guardDestroyWithEntity(Level level, BlockPos pos, boolean dropBlock, Entity breakingEntity) {
        if (ExplosionProtection.isProtected(level, Faction.nullUuid, pos)) {
            return false;
        }
        return level.destroyBlock(pos, dropBlock, breakingEntity);
    }
}

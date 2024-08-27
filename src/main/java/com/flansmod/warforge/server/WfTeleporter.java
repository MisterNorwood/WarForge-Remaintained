package com.flansmod.warforge.server;

import net.minecraft.entity.Entity;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

public class WfTeleporter extends Teleporter {
    public WfTeleporter(WorldServer world) {
        super(world);
    }

    @Override
    public void placeInPortal(Entity entity, float yaw) {
    }

    @Override
    public boolean placeInExistingPortal(Entity entity, float yaw) {
        return false;
    }

    @Override
    public boolean makePortal(Entity entity) {
        return false; // No new portal creation
    }

    @Override
    public void removeStalePortalLocations(long worldTime) {
    }
}



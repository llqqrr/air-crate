package com.llqqrr.aircrate.content.creature;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Converts saw destruction of a creature parcel into ordinary no-player creature death loot. */
public final class CreatureParcelSawService {
    private CreatureParcelSawService() { }

    public static boolean killContents(ServerLevel level, Vec3 position, net.minecraft.world.item.ItemStack parcel) {
        CreatureRecord record = CreatureParcelItem.record(parcel);
        if (record == null) return false;
        Entity entity = CreatureReleaseService.create(level, record);
        if (!(entity instanceof LivingEntity living)) return false;
        living.moveTo(position);
        if (!level.addFreshEntity(living)) return false;
        living.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
        if (living.isAlive()) living.kill();
        return true;
    }
}

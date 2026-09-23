package com.llqqrr.aircrate.content.creature;

import com.llqqrr.aircrate.registry.ACDataComponents;
import com.llqqrr.aircrate.registry.ACItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Safe entity release with a record-item fallback when a world position is unavailable. */
public final class CreatureReleaseService {
    private static final int RELEASE_RADIUS = 6;

    private CreatureReleaseService() {
    }

    public static void releaseOrDrop(ServerLevel level, BlockPos origin, List<CreatureRecord> records) {
        for (CreatureRecord record : records) {
            Entity entity = createForRelease(level, origin, record);
            BlockPos target = entity == null ? null : findSafePosition(level, origin, entity);
            if (target != null) {
                entity.moveTo(Vec3.atBottomCenterOf(target));
                if (level.addFreshEntity(entity)) continue;
            }
            ItemStack sealed = new ItemStack(ACItems.SEALED_CREATURE.get());
            sealed.set(ACDataComponents.CREATURE_RECORD.get(), record.nextRevision());
            Containers.dropItemStack(level, origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, sealed);
        }
    }

    public static boolean release(ServerLevel level, BlockPos origin, CreatureRecord record) {
        Entity entity = createForRelease(level, origin, record);
        BlockPos target = entity == null ? null : findSafePosition(level, origin, entity);
        if (target == null) return false;
        entity.moveTo(Vec3.atBottomCenterOf(target));
        return level.addFreshEntity(entity);
    }

    public static Entity create(ServerLevel level, CreatureRecord record) {
        EntityType<?> type = EntityType.byString(record.entityType()).orElse(null);
        Entity entity = type == null ? null : type.create(level);
        return restore(record, entity);
    }

    private static Entity createForRelease(ServerLevel level, BlockPos origin, CreatureRecord record) {
        EntityType<?> type = EntityType.byString(record.entityType()).orElse(null);
        Entity entity = type == null ? null : type.create(level, ignored -> { }, origin,
                MobSpawnType.COMMAND, false, false);
        return restore(record, entity);
    }

    private static Entity restore(CreatureRecord record, Entity entity) {
        if (entity == null) return null;
        CompoundTag data = CreatureNbt.parse(record);
        if (!data.isEmpty()) {
            for (String key : List.of("UUID", "Pos", "Motion", "Rotation", "FallDistance", "Fire")) {
                data.remove(key);
            }
            entity.load(data);
            entity.setUUID(java.util.UUID.randomUUID());
        } else {
            restoreLegacy(record, entity);
        }
        return entity;
    }

    private static BlockPos findSafePosition(ServerLevel level, BlockPos origin, Entity entity) {
        for (int distance = 0; distance <= RELEASE_RADIUS; distance++) {
            for (int x = -distance; x <= distance; x++) {
                for (int z = -distance; z <= distance; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != distance) continue;
                    for (int y = -1; y <= 2; y++) {
                        BlockPos candidate = origin.offset(x, y, z);
                        if (!level.getBlockState(candidate).isAir() || !level.getBlockState(candidate.above()).isAir()) continue;
                        entity.moveTo(Vec3.atBottomCenterOf(candidate));
                        if (level.noCollision(entity)) return candidate;
                    }
                }
            }
        }
        return null;
    }

    private static void restoreLegacy(CreatureRecord record, Entity entity) {
        if (entity instanceof LivingEntity living) {
            living.setHealth(Math.min(living.getMaxHealth(), record.healthMilli() / 1000.0F));
        }
        if (entity instanceof AgeableMob ageable && record.juvenile()) {
            ageable.setAge((int) Math.max(-24000L, Math.min(-1L, record.ageTicks())));
        }
    }
}

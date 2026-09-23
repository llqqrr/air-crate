package com.llqqrr.aircrate.content.creature;

import com.llqqrr.aircrate.content.block.AviationCrateBlockEntity;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.llqqrr.aircrate.registry.ACDataComponents;
import com.llqqrr.aircrate.registry.ACItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Converts a live entity into a full-fidelity record only after the destination accepts it. */
public final class CreatureCaptureService {
    private CreatureCaptureService() {
    }

    public static boolean capture(AviationCrateBlockEntity controller, Entity entity) {
        if (!(entity instanceof LivingEntity living) || !isSupported(living)) return false;
        CreatureRecord record = recordFromEntity(living);
        net.minecraft.server.level.ServerLevel level =
                controller.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel
                        ? serverLevel : null;
        CreatureCaptureJournal journal = level == null ? null : CreatureCaptureJournal.get(level);
        if (journal != null) journal.prepare(record, level.dimension(), controller.getBlockPos());
        if (!controller.addRecord(record)) {
            if (journal != null) journal.complete(record.recordId());
            return false;
        }
        if (journal != null) journal.markDestinationCommitted(record.recordId());
        entity.discard();
        if (journal != null) {
            journal.markSourceTombstoned(record.recordId());
            journal.complete(record.recordId());
        }
        return true;
    }

    public static boolean isSupported(LivingEntity entity) {
        return !(entity instanceof net.minecraft.world.entity.player.Player)
                && !(entity instanceof net.minecraft.world.entity.decoration.ArmorStand)
                && !(entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon)
                && !(entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss)
                && (entity instanceof net.minecraft.world.entity.npc.AbstractVillager
                    || entity.getType().getCategory() != net.minecraft.world.entity.MobCategory.MISC)
                && !entity.isPassenger()
                && entity.getPassengers().isEmpty()
                && entity.isAlive();
    }

    /** Client-safe type probe used by filter screens and server packet validation. */
    public static boolean isSupportedType(EntityType<?> type, Level level) {
        if (type == null) return false;
        if (level != null) {
            Entity probe = type.create(level);
            return probe instanceof LivingEntity living && isSupported(living);
        }
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        return id.equals("minecraft:villager") || id.equals("minecraft:wandering_trader");
    }

    public static boolean matchesFilter(LivingEntity entity, ItemStack filter) {
        if (!filter.has(ACDataComponents.CREATURE_RECORD)) return false;
        CreatureRecord wanted = filter.get(ACDataComponents.CREATURE_RECORD);
        return wanted != null && wanted.entityType().equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }

    public static ItemStack asRecordItem(LivingEntity entity) {
        if (!isSupported(entity) || entity.getBbHeight() > 2.0D) return ItemStack.EMPTY;
        ItemStack record = new ItemStack(ACItems.SEALED_CREATURE.get());
        record.set(ACDataComponents.CREATURE_RECORD.get(), recordFromEntity(entity));
        return record;
    }

    public static ItemStack asParcel(LivingEntity entity) {
        if (!isSupported(entity)) return ItemStack.EMPTY;
        return CreatureParcelItem.containing(recordFromEntity(entity));
    }

    public static CreatureRecord recordFromEntity(LivingEntity entity) {
        String entityType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        long age = entity instanceof AgeableMob ageable ? ageable.getAge() : 0L;
        net.minecraft.nbt.CompoundTag data = entity.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        // Identity and position must be regenerated, while every other entity field remains authoritative.
        for (String key : java.util.List.of("UUID", "Pos")) {
            data.remove(key);
        }
        boolean sheared = entity instanceof net.minecraft.world.entity.animal.Sheep sheep && sheep.isSheared();
        return new CreatureRecord(java.util.UUID.randomUUID(), entityType, entityType, age < 0L, age,
                Math.round(Math.max(1.0F, entity.getHealth()) * 1000.0F), 0L,
                TradeOfferSnapshotExtractor.extract(entity), data.toString(),
                Math.max(1, Math.round(entity.getBbWidth() * 1000.0F)),
                Math.max(1, Math.round(entity.getBbHeight() * 1000.0F)),
                0L, sheared, sheared ? entity.level().getGameTime() : 0L);
    }
}

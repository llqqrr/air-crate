package com.llqqrr.aircrate.content.creature;

import com.llqqrr.aircrate.content.block.AviationCrateBlockEntity;

/** Advances bounded record state (age, wool regrowth) without ever creating a live entity in the crate. */
public final class CreatureGrowthProcessor {
    private static final long WOOL_RECOVERY_TICKS = 6000L;

    private CreatureGrowthProcessor() {
    }

    public static void tick(AviationCrateBlockEntity crate, long ticks) {
        long gameTime = crate.getLevel() == null ? 0L : crate.getLevel().getGameTime();
        for (CreatureRecord record : crate.records()) {
            CreatureRecord updated = record;
            if (updated.entityType().equals("minecraft:sheep") && CreatureNbt.sheared(updated)
                    && gameTime - updated.lastShearedGameTime()
                        >= (updated.juvenile() ? WOOL_RECOVERY_TICKS / 2 : WOOL_RECOVERY_TICKS)) {
                updated = new CreatureRecord(updated.recordId(), updated.entityType(), updated.profileId(),
                        updated.juvenile(), updated.ageTicks(), updated.healthMilli(), updated.revision() + 1,
                        updated.trades(), CreatureNbt.withSheared(updated, false), updated.widthMilli(),
                        updated.heightMilli(), updated.lastMilkGameTime(), false, 0L);
            }
            if (updated.juvenile() && updated.ageTicks() < 0L) {
                long age = Math.min(0L, updated.ageTicks() + ticks);
                updated = new CreatureRecord(updated.recordId(), updated.entityType(), updated.profileId(),
                        age < 0L, age, updated.healthMilli(), updated.revision() + 1, updated.trades(),
                        updateAge(updated, age), updated.widthMilli(), updated.heightMilli(),
                        updated.lastMilkGameTime(), updated.sheared(), updated.lastShearedGameTime());
            }
            if (updated != record) crate.replaceRecord(updated);
        }
    }

    private static String updateAge(CreatureRecord record, long age) {
        net.minecraft.nbt.CompoundTag data = CreatureNbt.parse(record);
        if (!data.isEmpty()) data.putInt("Age", (int) Math.clamp(age, Integer.MIN_VALUE, Integer.MAX_VALUE));
        return data.toString();
    }
}

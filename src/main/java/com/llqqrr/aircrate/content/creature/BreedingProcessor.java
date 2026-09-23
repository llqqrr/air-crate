package com.llqqrr.aircrate.content.creature;

import com.llqqrr.aircrate.content.block.AviationCrateBlockEntity;
import com.llqqrr.aircrate.registry.ACItemTags;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.UUID;

/** Performs one bounded, inventory-backed breeding transaction. */
public final class BreedingProcessor {
    private BreedingProcessor() {
    }

    public static void tick(AviationCrateBlockEntity crate) {
        List<CreatureRecord> adults = crate.records().stream()
                .filter(record -> !record.juvenile())
                .toList();
        for (int first = 0; first < adults.size(); first++) {
            CreatureRecord parentA = adults.get(first);
            for (int second = first + 1; second < adults.size(); second++) {
                CreatureRecord parentB = adults.get(second);
                if (!parentA.profileId().equals(parentB.profileId())
                        || !parentA.entityType().equals(parentB.entityType())) continue;
                if (!hasFeed(crate.feedInventory(), 2)) return;
                CreatureRecord child = createChild(crate, parentA);
                if (!crate.addRecord(child)) return;
                consumeFeed(crate.feedInventory(), 2);
                return;
            }
        }
    }

    private static CreatureRecord createChild(AviationCrateBlockEntity crate, CreatureRecord parent) {
        if (crate.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            net.minecraft.world.entity.Entity entity = CreatureReleaseService.create(level, parent);
            if (entity instanceof net.minecraft.world.entity.AgeableMob ageable) {
                ageable.setAge(-24_000);
                CreatureRecord captured = CreatureCaptureService.recordFromEntity(ageable);
                return new CreatureRecord(captured.recordId(), captured.entityType(), parent.profileId(), true,
                        -24_000L, captured.healthMilli(), 0L, captured.trades(), captured.entityData(),
                        captured.widthMilli(), captured.heightMilli(),
                        captured.lastMilkGameTime(), captured.sheared(), captured.lastShearedGameTime());
            }
        }
        net.minecraft.nbt.CompoundTag data = CreatureNbt.parse(parent);
        if (!data.isEmpty()) data.putInt("Age", -24_000);
        return new CreatureRecord(UUID.randomUUID(), parent.entityType(), parent.profileId(), true, -24_000L,
                parent.healthMilli(), 0L, List.of(), data.toString(), parent.widthMilli(), parent.heightMilli());
    }

    private static boolean hasFeed(ItemStackHandler feed, int amount) {
        int available = 0;
        for (int slot = 0; slot < feed.getSlots(); slot++) {
            if (feed.getStackInSlot(slot).is(ACItemTags.BREEDING_FEED)) {
                available += feed.getStackInSlot(slot).getCount();
                if (available >= amount) return true;
            }
        }
        return false;
    }

    private static void consumeFeed(ItemStackHandler feed, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < feed.getSlots() && remaining > 0; slot++) {
            if (!feed.getStackInSlot(slot).is(ACItemTags.BREEDING_FEED)) continue;
            remaining -= feed.extractItem(slot, remaining, false).getCount();
        }
    }
}

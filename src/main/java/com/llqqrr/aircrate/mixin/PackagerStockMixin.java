package com.llqqrr.aircrate.mixin;

import com.llqqrr.aircrate.content.block.AviationCrateBlockEntity;
import com.llqqrr.aircrate.content.creature.CreatureParcelItem;
import com.llqqrr.aircrate.content.creature.CreatureRecord;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;
import java.util.Map;

/** Adds adjacent aviation-crate creature totals to Create stock views without exposing them to packager extraction. */
@Mixin(value = PackagerBlockEntity.class, remap = false)
abstract class PackagerStockMixin {
    @Inject(method = "getAvailableItems", at = @At("RETURN"), cancellable = true)
    private void aircrate$includeCreatureNames(CallbackInfoReturnable<InventorySummary> cir) {
        PackagerBlockEntity self = (PackagerBlockEntity) (Object) this;
        if (self.getLevel() == null) return;
        AviationCrateBlockEntity crate = null;
        for (Direction direction : Direction.values()) {
            if (self.getLevel().getBlockEntity(self.getBlockPos().relative(direction))
                    instanceof AviationCrateBlockEntity found) {
                crate = found.controller();
                break;
            }
        }
        if (crate == null || crate.records().isEmpty()) return;
        Map<String, java.util.List<CreatureRecord>> groups = new LinkedHashMap<>();
        for (CreatureRecord record : crate.records()) groups.computeIfAbsent(record.entityType(), ignored -> new java.util.ArrayList<>()).add(record);
        InventorySummary expanded = cir.getReturnValue().copy();
        for (java.util.List<CreatureRecord> records : groups.values()) {
            expanded.add(CreatureParcelItem.containing(records.getFirst()), records.size());
        }
        cir.setReturnValue(expanded);
    }
}

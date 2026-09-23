package com.llqqrr.aircrate.content.structure;

import com.llqqrr.aircrate.content.creature.CreatureRecord;

import java.util.List;

/**
 * A crate may retain records beyond normal packing capacity when every retained
 * species can individually fit. That state is intentionally overfull and paused.
 */
public record CapacityResult(List<CreatureRecord> placed, List<CreatureRecord> retainedOverfull,
                             List<CreatureRecord> cannotEverFit) {
    public boolean isOverfull() {
        return !retainedOverfull.isEmpty();
    }

    public boolean allowsNormalInteraction() {
        return !isOverfull() && cannotEverFit.isEmpty();
    }
}

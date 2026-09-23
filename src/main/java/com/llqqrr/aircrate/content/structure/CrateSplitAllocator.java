package com.llqqrr.aircrate.content.structure;

import com.llqqrr.aircrate.content.creature.CreatureProfiles;
import com.llqqrr.aircrate.content.creature.CreatureRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Assigns only among structures produced by one destruction transaction. Records
 * that no resulting structure can individually hold are entity-ejection candidates.
 */
public final class CrateSplitAllocator {
    private CrateSplitAllocator() {
    }

    public static SplitAllocation allocate(List<CrateSize> structures, List<CreatureRecord> records) {
        List<List<CreatureRecord>> assigned = new ArrayList<>();
        for (int i = 0; i < structures.size(); i++) assigned.add(new ArrayList<>());

        List<CreatureRecord> ordered = new ArrayList<>(records);
        ordered.sort(Comparator.comparingInt((CreatureRecord record) -> CreatureProfiles.resolve(record).volume())
                .reversed().thenComparing(record -> record.recordId().toString()));

        List<CreatureRecord> entities = new ArrayList<>();
        List<CreatureRecord> items = new ArrayList<>();
        for (CreatureRecord record : ordered) {
            int best = selectTarget(structures, assigned, record);
            if (best < 0) {
                // The caller attempts a safe entity release first; a failed release becomes an item drop.
                entities.add(record);
            } else {
                assigned.get(best).add(record);
            }
        }
        return new SplitAllocation(assigned.stream().map(List::copyOf).toList(), List.copyOf(entities), List.copyOf(items));
    }

    private static int selectTarget(List<CrateSize> structures, List<List<CreatureRecord>> assigned,
                                    CreatureRecord record) {
        int selected = -1;
        int selectedOverfull = Integer.MAX_VALUE;
        int selectedVolume = Integer.MIN_VALUE;
        for (int i = 0; i < structures.size(); i++) {
            CrateSize size = structures.get(i);
            if (!CreatureCapacitySolver.canIndividuallyFit(size, record)) continue;
            List<CreatureRecord> candidate = new ArrayList<>(assigned.get(i));
            candidate.add(record);
            int overfull = CreatureCapacitySolver.solve(size, candidate).retainedOverfull().size();
            if (overfull < selectedOverfull || overfull == selectedOverfull && size.volume() > selectedVolume) {
                selected = i;
                selectedOverfull = overfull;
                selectedVolume = size.volume();
            }
        }
        return selected;
    }
}

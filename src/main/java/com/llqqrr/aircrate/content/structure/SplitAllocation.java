package com.llqqrr.aircrate.content.structure;

import com.llqqrr.aircrate.content.creature.CreatureRecord;

import java.util.List;

public record SplitAllocation(List<List<CreatureRecord>> recordsByStructure,
                              List<CreatureRecord> ejectedAsEntities,
                              List<CreatureRecord> ejectedAsItems) {
}

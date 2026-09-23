package com.llqqrr.aircrate.content.structure;

import com.llqqrr.aircrate.content.creature.CreatureRecord;
import com.llqqrr.aircrate.content.villager.TradingStationRules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatureCapacitySolverTest {
    private static CreatureRecord cow() {
        return CreatureRecord.create("minecraft:cow", "minecraft:cow", false);
    }

    @Test
    void aCowFitsTheRequestedOneByTwoByTwoEnvelope() {
        CapacityResult result = CreatureCapacitySolver.solve(new CrateSize(1, 2, 2), List.of(cow()));
        assertEquals(1, result.placed().size());
        assertTrue(result.retainedOverfull().isEmpty());
    }

    @Test
    void aThirdCowIsRetainedAsOverfullInTwoByTwoByTwo() {
        CapacityResult result = CreatureCapacitySolver.solve(new CrateSize(2, 2, 2), List.of(cow(), cow(), cow()));
        assertEquals(2, result.placed().size());
        assertEquals(1, result.retainedOverfull().size());
        assertTrue(result.cannotEverFit().isEmpty());
    }

    @Test
    void aCowIsReleasedWhenNoSplitResultCanIndividuallyHoldIt() {
        SplitAllocation allocation = CrateSplitAllocator.allocate(List.of(new CrateSize(1, 1, 2)), List.of(cow()));
        assertEquals(1, allocation.ejectedAsEntities().size());
    }

    @Test
    void everyTradingVillagerNeedsItsOwnThreeByThreeByThreeStation() {
        CrateSize twoStations = new CrateSize(3, 3, 6);
        assertTrue(TradingStationRules.canTrade(twoStations, 2, 2, 2, 2));
        assertTrue(!TradingStationRules.canTrade(twoStations, 3, 3, 3, 3));
    }
}

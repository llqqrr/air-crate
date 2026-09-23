package com.llqqrr.aircrate.content.villager;

import com.llqqrr.aircrate.content.structure.CrateSize;

/** A trading villager owns one virtual 3x3x3 station and its three fixtures. */
public final class TradingStationRules {
    public static final int STATION_WIDTH = 3;
    public static final int STATION_HEIGHT = 3;
    public static final int STATION_LENGTH = 3;

    private TradingStationRules() {
    }

    public static int stationCapacity(CrateSize size) {
        return (size.width() / STATION_WIDTH) * (size.height() / STATION_HEIGHT)
                * (size.length() / STATION_LENGTH);
    }

    public static boolean canTrade(CrateSize size, int villagerCount, int bedCount,
                                   int workstationCount, int displayStandCount) {
        int stations = stationCapacity(size);
        return villagerCount > 0 && villagerCount <= stations && bedCount >= villagerCount
                && workstationCount >= villagerCount && displayStandCount >= villagerCount;
    }
}

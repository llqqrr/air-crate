package com.llqqrr.aircrate.content.structure;

import com.llqqrr.aircrate.content.creature.CreatureProfile;
import com.llqqrr.aircrate.content.creature.CreatureProfiles;
import com.llqqrr.aircrate.content.creature.CreatureRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic whole-cell packing. Juveniles reserve their adult profile. */
public final class CreatureCapacitySolver {
    private CreatureCapacitySolver() {
    }

    public static boolean canIndividuallyFit(CrateSize size, CreatureRecord record) {
        return CreatureProfiles.resolve(record).footprints().stream().anyMatch(footprint ->
                footprint.width() <= size.width() && footprint.height() <= size.height()
                        && footprint.length() <= size.length());
    }

    public static CapacityResult solve(CrateSize size, List<CreatureRecord> records) {
        boolean[] occupied = new boolean[size.volume()];
        List<CreatureRecord> ordered = new ArrayList<>(records);
        ordered.sort(Comparator.comparingInt((CreatureRecord record) -> CreatureProfiles.resolve(record).volume())
                .reversed().thenComparing(record -> record.recordId().toString()));

        List<CreatureRecord> placed = new ArrayList<>();
        List<CreatureRecord> overfull = new ArrayList<>();
        List<CreatureRecord> cannotFit = new ArrayList<>();
        for (CreatureRecord record : ordered) {
            if (!canIndividuallyFit(size, record)) {
                cannotFit.add(record);
            } else if (place(occupied, size, CreatureProfiles.resolve(record)) != null) {
                placed.add(record);
            } else {
                // The record is safe on its own but not with the current population.
                overfull.add(record);
            }
        }
        return new CapacityResult(List.copyOf(placed), List.copyOf(overfull), List.copyOf(cannotFit));
    }

    /** A creature's packed position: the minimum corner of its footprint, in cells. */
    public record Placement(CreatureRecord record, int x, int y, int z,
                            int width, int height, int length) {
    }

    /** Deterministic packing positions for every record that fits, in largest-first order. */
    public static List<Placement> layout(CrateSize size, List<CreatureRecord> records) {
        boolean[] occupied = new boolean[size.volume()];
        List<CreatureRecord> ordered = new ArrayList<>(records);
        ordered.sort(Comparator.comparingInt((CreatureRecord record) -> CreatureProfiles.resolve(record).volume())
                .reversed().thenComparing(record -> record.recordId().toString()));
        List<Placement> placements = new ArrayList<>();
        for (CreatureRecord record : ordered) {
            if (!canIndividuallyFit(size, record)) continue;
            CreatureProfile profile = CreatureProfiles.resolve(record);
            int[] spot = place(occupied, size, profile);
            if (spot == null) continue;
            CreatureProfile.Footprint footprint = profile.footprints().get(spot[3]);
            placements.add(new Placement(record, spot[0], spot[1], spot[2],
                    footprint.width(), footprint.height(), footprint.length()));
        }
        return List.copyOf(placements);
    }

    /** @return {x, y, z, footprintIndex} of the chosen spot, or null when nothing fits. */
    private static int[] place(boolean[] occupied, CrateSize size, CreatureProfile profile) {
        for (int f = 0; f < profile.footprints().size(); f++) {
            CreatureProfile.Footprint footprint = profile.footprints().get(f);
            for (int y = 0; y <= size.height() - footprint.height(); y++) {
                for (int z = 0; z <= size.length() - footprint.length(); z++) {
                    for (int x = 0; x <= size.width() - footprint.width(); x++) {
                        if (isFree(occupied, size, x, y, z, footprint)) {
                            fill(occupied, size, x, y, z, footprint, true);
                            return new int[]{x, y, z, f};
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isFree(boolean[] occupied, CrateSize size, int x, int y, int z,
                                  CreatureProfile.Footprint footprint) {
        for (int dy = 0; dy < footprint.height(); dy++) {
            for (int dz = 0; dz < footprint.length(); dz++) {
                for (int dx = 0; dx < footprint.width(); dx++) {
                    if (occupied[index(size, x + dx, y + dy, z + dz)]) return false;
                }
            }
        }
        return true;
    }

    private static void fill(boolean[] occupied, CrateSize size, int x, int y, int z,
                             CreatureProfile.Footprint footprint, boolean value) {
        for (int dy = 0; dy < footprint.height(); dy++) {
            for (int dz = 0; dz < footprint.length(); dz++) {
                for (int dx = 0; dx < footprint.width(); dx++) {
                    occupied[index(size, x + dx, y + dy, z + dz)] = value;
                }
            }
        }
    }

    private static int index(CrateSize size, int x, int y, int z) {
        return x + size.width() * (z + size.length() * y);
    }
}

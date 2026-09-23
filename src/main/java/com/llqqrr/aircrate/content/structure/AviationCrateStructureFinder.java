package com.llqqrr.aircrate.content.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Deterministically decomposes a supplied set of crate blocks into the largest
 * legal axis-aligned boxes. The caller supplies the old structure's surviving
 * members, so a destroyed bridge cannot hide disconnected remaining pieces.
 *
 * Orientation rule: a structure never mixes blocks with different placement
 * facings, and its logical length axis always follows the placement-facing
 * axis (the vent/panel ends sit on that axis). The geometry no longer picks
 * the long side as the length, so extending a crate sideways never flips it.
 */
public final class AviationCrateStructureFinder {
    private static final Comparator<BlockPos> POS_ORDER = Comparator.<BlockPos>comparingInt(pos -> pos.getX())
            .thenComparingInt(pos -> pos.getY()).thenComparingInt(pos -> pos.getZ());

    private AviationCrateStructureFinder() {
    }

    public static List<CratePartition> partition(Set<BlockPos> survivingMembers,
                                                 Function<BlockPos, Direction> facingOf) {
        Map<Direction, Set<BlockPos>> byFacing = new EnumMap<>(Direction.class);
        for (BlockPos pos : survivingMembers) {
            Direction facing = facingOf.apply(pos);
            byFacing.computeIfAbsent(facing == null ? Direction.NORTH : facing, ignored -> new HashSet<>()).add(pos);
        }
        List<CratePartition> result = new ArrayList<>();
        for (Map.Entry<Direction, Set<BlockPos>> group : byFacing.entrySet()) {
            result.addAll(partitionFacingGroup(group.getValue(), group.getKey().getAxis()));
        }
        return List.copyOf(result);
    }

    private static List<CratePartition> partitionFacingGroup(Set<BlockPos> group, Direction.Axis facingAxis) {
        Set<BlockPos> remaining = new HashSet<>(group);
        List<CratePartition> result = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Candidate best = bestCandidate(remaining, facingAxis);
            if (best == null) break;
            result.add(best.toPartition());
            remaining.removeAll(best.members);
        }
        return result;
    }

    private static Candidate bestCandidate(Set<BlockPos> remaining, Direction.Axis facingAxis) {
        Candidate best = null;
        for (BlockPos min : remaining.stream().sorted(POS_ORDER).toList()) {
            best = choose(best, enumerateAt(min, remaining, facingAxis));
        }
        return best;
    }

    private static Candidate enumerateAt(BlockPos min, Set<BlockPos> remaining, Direction.Axis facingAxis) {
        Candidate best = null;
        boolean alongX = facingAxis == Direction.Axis.X;
        int maxX = alongX ? CrateSize.MAX_LENGTH : CrateSize.MAX_WIDTH;
        int maxZ = alongX ? CrateSize.MAX_WIDTH : CrateSize.MAX_LENGTH;
        for (int x = 1; x <= maxX; x++) {
            for (int y = 1; y <= CrateSize.MAX_HEIGHT; y++) {
                for (int z = 1; z <= maxZ; z++) {
                    Set<BlockPos> members = new HashSet<>(x * y * z);
                    for (int dx = 0; dx < x; dx++) for (int dy = 0; dy < y; dy++) for (int dz = 0; dz < z; dz++) {
                        members.add(min.offset(dx, dy, dz));
                    }
                    if (!remaining.containsAll(members)) continue;
                    // Logical width is the cross-section axis, logical length the
                    // facing axis — never normalized to the longer side.
                    CrateSize size = alongX ? new CrateSize(z, y, x) : new CrateSize(x, y, z);
                    best = choose(best, new Candidate(min, size, members));
                }
            }
        }
        return best;
    }

    private static Candidate choose(Candidate current, Candidate candidate) {
        if (candidate == null) return current;
        if (current == null) return candidate;
        int volumeCompare = Integer.compare(candidate.size.volume(), current.size.volume());
        if (volumeCompare != 0) return volumeCompare > 0 ? candidate : current;
        int surfaceCompare = Integer.compare(surfaceArea(candidate.size), surfaceArea(current.size));
        if (surfaceCompare != 0) return surfaceCompare < 0 ? candidate : current;
        return POS_ORDER.compare(candidate.controller, current.controller) < 0 ? candidate : current;
    }

    private static int surfaceArea(CrateSize size) {
        return 2 * (size.width() * size.height() + size.width() * size.length()
                + size.height() * size.length());
    }

    private record Candidate(BlockPos controller, CrateSize size, Set<BlockPos> members) {
        CratePartition toPartition() { return new CratePartition(controller, size, members); }
    }
}

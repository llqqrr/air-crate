package com.llqqrr.aircrate.content.structure;

import net.minecraft.core.BlockPos;

import java.util.Set;

/** One legal rectangular result from a destruction/rebuild transaction. */
public record CratePartition(BlockPos controller, CrateSize size, Set<BlockPos> members) {
    public CratePartition {
        members = Set.copyOf(members);
        if (!members.contains(controller)) throw new IllegalArgumentException("Controller must be a member");
    }
}

package com.llqqrr.aircrate.compat;

import net.minecraft.core.BlockPos;

import java.util.Set;
import java.util.UUID;

/** Optional aviation-mod bridge. Implementations must atomically include all members. */
public interface AeronauticsBridge {
    boolean canAssembleWholeStructure(UUID structureId, Set<BlockPos> members);

    void onAssembled(UUID structureId, long shipId, Set<BlockPos> localMembers);

    void onDisassembled(UUID structureId, Set<BlockPos> worldMembers);
}

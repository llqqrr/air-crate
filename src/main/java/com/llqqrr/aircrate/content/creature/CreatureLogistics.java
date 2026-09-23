package com.llqqrr.aircrate.content.creature;

import com.llqqrr.aircrate.AirCrate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Central classification for surfaces on which a creature item is allowed to physically exist. */
public final class CreatureLogistics {
    private CreatureLogistics() { }

    public static boolean isLogisticsSurface(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof com.llqqrr.aircrate.content.block.AviationCrateHatchBlock) {
            return true;
        }
        if (!net.neoforged.fml.ModList.get().isLoaded("create")) return false;
        return com.llqqrr.aircrate.compat.create.CreateCreatureLogistics.isSurface(level, pos);
    }

    public static boolean isAllowedCarrier(BlockEntity blockEntity) {
        if (blockEntity instanceof com.llqqrr.aircrate.content.block.AviationCrateHatchBlockEntity) return true;
        if (!net.neoforged.fml.ModList.get().isLoaded("create")) return false;
        return com.llqqrr.aircrate.compat.create.CreateCreatureLogistics.isCarrier(blockEntity);
    }
}

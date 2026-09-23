package com.llqqrr.aircrate.compat.create;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Isolated Create type checks so the base mod remains loadable when Create is absent. */
public final class CreateCreatureLogistics {
    private CreateCreatureLogistics() { }

    public static boolean isSurface(Level level, BlockPos pos) {
        return isCarrier(level.getBlockEntity(pos));
    }

    public static boolean isCarrier(BlockEntity blockEntity) {
        return blockEntity instanceof BeltBlockEntity
                || blockEntity instanceof DepotBlockEntity
                || blockEntity instanceof EjectorBlockEntity
                || blockEntity instanceof ChuteBlockEntity;
    }
}

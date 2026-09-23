package com.llqqrr.aircrate.content.block;

import com.llqqrr.aircrate.registry.ACBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Locates the controller behind the tool-interaction hatch. It is not an inventory endpoint. */
public final class CrateInteractionHatchBlockEntity extends BlockEntity {
    public CrateInteractionHatchBlockEntity(BlockPos pos, BlockState state) {
        super(ACBlockEntities.CRATE_INTERACTION_HATCH.get(), pos, state);
    }

    public AviationCrateBlockEntity controller() {
        if (level == null) return null;
        BlockPos cratePos = worldPosition.relative(getBlockState()
                .getValue(CrateInteractionHatchBlock.FACING).getOpposite());
        return level.getBlockEntity(cratePos) instanceof AviationCrateBlockEntity crate ? crate.controller() : null;
    }
}

package com.llqqrr.aircrate.content.block;

import com.llqqrr.aircrate.registry.ACBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.Containers;

/** Create-logistics attachment that turns nearby pushed entities into record items. */
public final class CreatureBeltFixtureBlock extends Block implements EntityBlock {
    public CreatureBeltFixtureBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return isCreateBelt(context.getLevel().getBlockState(context.getClickedPos().below()))
                ? super.getStateForPlacement(context) : null;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreatureBeltFixtureBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ACBlockEntities.CREATURE_BELT_FIXTURE.get()) return null;
        return (tickerLevel, tickerPos, tickerState, blockEntity) ->
                CreatureBeltFixtureBlockEntity.tickServer(tickerLevel, tickerPos, tickerState,
                        (CreatureBeltFixtureBlockEntity) blockEntity);
    }

    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.core.Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == net.minecraft.core.Direction.DOWN && !isCreateBelt(neighborState)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()
                && level.getBlockEntity(pos) instanceof CreatureBeltFixtureBlockEntity fixture) {
            for (int slot = 0; slot < fixture.outputInventory().getSlots(); slot++) {
                var stack = fixture.outputInventory().extractItem(slot, Integer.MAX_VALUE, false);
                if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static boolean isCreateBelt(BlockState state) {
        return state.getBlock().getClass().getName().equals("com.simibubi.create.content.kinetics.belt.BeltBlock");
    }
}

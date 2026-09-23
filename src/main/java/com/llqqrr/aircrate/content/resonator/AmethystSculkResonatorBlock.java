package com.llqqrr.aircrate.content.resonator;

import com.llqqrr.aircrate.registry.ACBlockEntities;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

public final class AmethystSculkResonatorBlock extends HorizontalKineticBlock implements EntityBlock, IWrenchable {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public AmethystSculkResonatorBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(ACTIVE) ? 10 : 0));
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
    }

    @Override public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis();
    }

    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == getRotationAxis(state);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AmethystSculkResonatorBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (type != ACBlockEntities.AMETHYST_SCULK_RESONATOR.get()) return null;
        return (tickerLevel, tickerPos, tickerState, blockEntity) ->
                AmethystSculkResonatorBlockEntity.tick(tickerLevel, tickerPos,
                        (AmethystSculkResonatorBlockEntity) blockEntity);
    }
}

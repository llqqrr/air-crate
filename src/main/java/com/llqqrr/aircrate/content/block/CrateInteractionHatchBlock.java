package com.llqqrr.aircrate.content.block;

import com.llqqrr.aircrate.registry.ACBlockEntities;
import com.llqqrr.aircrate.content.creature.AviationCrateToolActions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** A dedicated Create logistics port mounted on an exterior crate face. */
public final class CrateInteractionHatchBlock extends Block implements EntityBlock {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    public CrateInteractionHatchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        Direction facing = context.getClickedFace().getAxis().isHorizontal()
                ? context.getClickedFace() : context.getHorizontalDirection().getOpposite();
        BlockPos cratePos = context.getClickedPos().relative(facing.getOpposite());
        if (!context.getLevel().isClientSide
                && !(context.getLevel().getBlockEntity(cratePos) instanceof AviationCrateBlockEntity)) return null;
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public CrateInteractionHatchBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrateInteractionHatchBlockEntity(pos, state);
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && isDeployer(player)
                && level.getBlockEntity(pos) instanceof CrateInteractionHatchBlockEntity hatch
                && hatch.controller() != null && player.getMainHandItem().getItem() instanceof AxeItem) {
            AviationCrateToolActions.slaughter(hatch.controller(), player.getMainHandItem(), player);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!isDeployer(player)
                || !(level.getBlockEntity(pos) instanceof CrateInteractionHatchBlockEntity hatch)
                || hatch.controller() == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (stack.is(Items.BUCKET) && !level.isClientSide
                && AviationCrateToolActions.milk(hatch.controller(), level)) {
            stack.shrink(1);
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(Items.MILK_BUCKET));
            return ItemInteractionResult.CONSUME;
        }
        if (stack.getItem() instanceof ShearsItem
                && !level.isClientSide
                && AviationCrateToolActions.shear(hatch.controller(), stack, player)) {
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return isDeployer(player) ? 0.0F : super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite()
                && !(level.getBlockEntity(pos.relative(direction)) instanceof AviationCrateBlockEntity)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> Block.box(2, 2, 12, 14, 14, 16);
            case SOUTH -> Block.box(2, 2, 0, 14, 14, 4);
            case WEST -> Block.box(12, 2, 2, 16, 14, 14);
            case EAST -> Block.box(0, 2, 2, 4, 14, 14);
            default -> Block.box(2, 2, 2, 14, 14, 14);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    private static boolean isDeployer(Player player) {
        return player.getClass().getName().equals("com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer");
    }
}

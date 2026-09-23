package com.llqqrr.aircrate.content.block;

import com.llqqrr.aircrate.compat.CarryOnCreatureBridge;
import com.llqqrr.aircrate.content.creature.CreatureCaptureService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Stackable logistics hatch attached to one exterior face of a valid crate structure. */
public final class AviationCrateHatchBlock extends Block implements EntityBlock {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            BlockStateProperties.HORIZONTAL_FACING;
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty OPEN = BlockStateProperties.OPEN;

    public AviationCrateHatchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false));
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AviationCrateHatchBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T>
    getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (type != com.llqqrr.aircrate.registry.ACBlockEntities.AVIATION_CRATE_HATCH.get()) return null;
        return (tickerLevel, tickerPos, tickerState, blockEntity) -> {
            AviationCrateHatchBlockEntity hatch = (AviationCrateHatchBlockEntity) blockEntity;
            hatch.tick();
            if (!tickerLevel.isClientSide) {
                AviationCrateHatchBlockEntity.tickServer(tickerLevel, tickerPos, tickerState, hatch);
            }
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace().getAxis().isHorizontal()
                ? context.getClickedFace() : context.getHorizontalDirection().getOpposite();
        BlockPos crate = context.getClickedPos().relative(facing.getOpposite());
        if (!context.getLevel().isClientSide
                && !(context.getLevel().getBlockEntity(crate) instanceof AviationCrateBlockEntity)) return null;
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        BlockPos lower = groupBottom(level, pos, state);
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockState lowerState = level.getBlockState(lower);
        if (!isValidHatch(level, lower, lowerState)) {
            player.displayClientMessage(Component.translatable("message.aircrate.invalid_hatch"), true);
            return InteractionResult.FAIL;
        }
        if (lowerState.getBlock() == com.llqqrr.aircrate.AirCrate.BRASS_CRATE_HATCH.get()
                && player.isShiftKeyDown()
                && level.getBlockEntity(lower) instanceof AviationCrateHatchBlockEntity hatch) {
            ItemStack filter = hatch.filterInventory().getStackInSlot(0);
                if (!filter.isEmpty()) {
                    hatch.filterInventory().setStackInSlot(0, ItemStack.EMPTY);
                    return InteractionResult.CONSUME;
                }
        }
        return InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos lower = groupBottom(level, pos, state);
        if (level.getBlockState(lower).getBlock() == com.llqqrr.aircrate.AirCrate.BRASS_CRATE_HATCH.get()
                && stack.is(com.llqqrr.aircrate.registry.ACItems.CREATURE_INTAKE_FAN.get())
                && player.isShiftKeyDown()
                && stack.has(com.llqqrr.aircrate.registry.ACDataComponents.CREATURE_RECORD)
                && level.getBlockEntity(lower) instanceof AviationCrateHatchBlockEntity hatch) {
            if (!level.isClientSide) {
                ItemStack ghost = new ItemStack(com.llqqrr.aircrate.registry.ACItems.SEALED_CREATURE.get());
                ghost.set(com.llqqrr.aircrate.registry.ACDataComponents.CREATURE_RECORD.get(),
                        stack.get(com.llqqrr.aircrate.registry.ACDataComponents.CREATURE_RECORD));
                hatch.filterInventory().setStackInSlot(0, ghost);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.getBlockState(lower).getBlock() == com.llqqrr.aircrate.AirCrate.BRASS_CRATE_HATCH.get()
                && stack.has(com.llqqrr.aircrate.registry.ACDataComponents.CREATURE_RECORD)
                && !stack.is(com.llqqrr.aircrate.registry.ACItems.CREATURE_INTAKE_FAN.get())
                && level.getBlockEntity(lower) instanceof AviationCrateHatchBlockEntity hatch) {
            if (!level.isClientSide) {
                hatch.filterInventory().setStackInSlot(0, stack.copyWithCount(1));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> Block.box(0, 0, 13, 16, 16, 16);
            case SOUTH -> Block.box(0, 0, 0, 16, 16, 3);
            case WEST -> Block.box(13, 0, 0, 16, 16, 16);
            case EAST -> Block.box(0, 0, 0, 3, 16, 16);
            default -> Shapes.block();
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    public static boolean isValidHatch(LevelAccessor level, BlockPos lower, BlockState state) {
        if (!(state.getBlock() instanceof AviationCrateHatchBlock)) return false;
        Direction facing = state.getValue(FACING);
        if (!(level.getBlockEntity(lower.relative(facing.getOpposite()))
                instanceof AviationCrateBlockEntity crate)) return false;
        return crate.controller().size().height() >= contiguousHeight(level, lower, state);
    }

    public static AviationCrateBlockEntity controller(LevelAccessor level, BlockPos lower, Direction facing) {
        BlockPos cratePos = lower.relative(facing.getOpposite());
        return level.getBlockEntity(cratePos) instanceof AviationCrateBlockEntity crate ? crate.controller() : null;
    }

    public static void setOpen(Level level, BlockPos lower, BlockState lowerState, boolean open) {
        for (int offset = 0; offset < contiguousHeight(level, lower, lowerState); offset++) {
            BlockPos partPos = lower.above(offset);
            BlockState part = level.getBlockState(partPos);
            level.setBlock(partPos, part.setValue(OPEN, open), 10);
        }
    }

    public static boolean tryCaptureAtHatch(Player player, BlockPos lower, Direction facing,
                                            net.minecraft.world.entity.LivingEntity creature) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        BlockState state = level.getBlockState(lower);
        if (!isValidHatch(level, lower, state)) return false;
        if (!canPass(level, lower, CreatureCaptureService.recordFromEntity(creature))) return false;
        AviationCrateBlockEntity controller = controller(level, lower, facing);
        return controller != null && CreatureCaptureService.capture(controller, creature);
    }

    public static boolean tryInsertRecord(Level level, BlockPos pos,
                                          com.llqqrr.aircrate.content.creature.CreatureRecord record) {
        BlockState clicked = level.getBlockState(pos);
        if (!(clicked.getBlock() instanceof AviationCrateHatchBlock)) return false;
        BlockPos lower = groupBottom(level, pos, clicked);
        BlockState state = level.getBlockState(lower);
        if (!isValidHatch(level, lower, state) || !canPass(level, lower, record)) return false;
        AviationCrateBlockEntity controller = controller(level, lower, state.getValue(FACING));
        return controller != null && controller.addRecord(record);
    }

    public static boolean canPass(LevelAccessor level, BlockPos lower,
                                  com.llqqrr.aircrate.content.creature.CreatureRecord record) {
        BlockState state = level.getBlockState(lower);
        int openingHeight = contiguousHeight(level, lower, state);
        if (record.heightMilli() > openingHeight * 1000) return false;
        if (state.getBlock() == com.llqqrr.aircrate.AirCrate.BRASS_CRATE_HATCH.get()
                && level.getBlockEntity(lower) instanceof AviationCrateHatchBlockEntity hatch) {
            ItemStack filter = hatch.filterInventory().getStackInSlot(0);
            if (!filter.isEmpty()) {
                var wanted = filter.get(com.llqqrr.aircrate.registry.ACDataComponents.CREATURE_RECORD);
                if (wanted == null || !wanted.entityType().equals(record.entityType())) return false;
            }
        }
        return true;
    }

    private static int contiguousHeight(LevelAccessor level, BlockPos lower, BlockState state) {
        int height = 0;
        for (int offset = 0; offset < 32; offset++) {
            BlockState part = level.getBlockState(lower.above(offset));
            if (part.getBlock() != state.getBlock() || part.getValue(FACING) != state.getValue(FACING)) break;
            height++;
        }
        return height;
    }

    public static int openingHeight(LevelAccessor level, BlockPos lower) {
        return contiguousHeight(level, lower, level.getBlockState(lower));
    }

    public static BlockPos groupBottom(LevelAccessor level, BlockPos pos, BlockState state) {
        BlockPos lower = pos;
        while (lower.getY() > level.getMinBuildHeight()) {
            BlockState below = level.getBlockState(lower.below());
            if (below.getBlock() != state.getBlock() || below.getValue(FACING) != state.getValue(FACING)) break;
            lower = lower.below();
        }
        return lower;
    }
}

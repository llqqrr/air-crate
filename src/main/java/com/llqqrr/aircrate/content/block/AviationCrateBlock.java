package com.llqqrr.aircrate.content.block;

import com.llqqrr.aircrate.content.creature.CreatureReleaseService;
import com.llqqrr.aircrate.content.structure.AviationCrateWorldManager;
import com.llqqrr.aircrate.registry.ACItemTags;
import com.llqqrr.aircrate.content.villager.TradingProcessor;
import com.llqqrr.aircrate.AirCrate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Containers;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.world.item.context.UseOnContext;

/** First-version crate shell. It deliberately has no container UI. */
public final class AviationCrateBlock extends Block implements EntityBlock, IWrenchable {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty OUTPUT_FACING =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty OUTPUT_OPEN =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN;
    /** Which way the placing player faced; decides the vent end of square-footprint structures. */
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty PLACEMENT_FACING =
            net.minecraft.world.level.block.state.properties.DirectionProperty.create("placement",
                    net.minecraft.core.Direction.Plane.HORIZONTAL);

    public AviationCrateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OUTPUT_FACING, net.minecraft.core.Direction.NORTH)
                .setValue(PLACEMENT_FACING, net.minecraft.core.Direction.NORTH)
                .setValue(OUTPUT_OPEN, false));
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        // Like Create's vault: the cross-section faces the player who placed the crate.
        net.minecraft.core.Direction towardPlayer = context.getHorizontalDirection().getOpposite();
        return defaultBlockState()
                .setValue(PLACEMENT_FACING, towardPlayer)
                .setValue(OUTPUT_FACING, towardPlayer)
                .setValue(OUTPUT_OPEN, false);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AviationCrateBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                    BlockEntityType<T> type) {
        if (level.isClientSide || type != com.llqqrr.aircrate.registry.ACBlockEntities.AVIATION_CRATE.get()) return null;
        return (tickerLevel, tickerPos, tickerState, blockEntity) ->
                AviationCrateBlockEntity.tickServer(tickerLevel, tickerPos, tickerState,
                        (AviationCrateBlockEntity) blockEntity);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && oldState.getBlock() != state.getBlock()
                && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            AviationCrateWorldManager.rebuildAt(serverLevel, pos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!isControlArea(pos, hit)) return InteractionResult.PASS;
        if (level.isClientSide && level.getBlockEntity(pos) instanceof AviationCrateBlockEntity crate) {
            AviationCrateBlockEntity controller = crate.controller();
            com.llqqrr.aircrate.client.AirCrateClient.openModeScreen(
                    controller.getBlockPos(), controller.breedingEnabled());
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof AviationCrateBlockEntity crate) {
            AviationCrateBlockEntity controller = crate.controller();
            if (player.distanceToSqr(pos.getCenter()) > 64.0D || !player.mayBuild()) return InteractionResult.FAIL;
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AviationCrateBlockEntity crate)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        AviationCrateBlockEntity controller = crate.controller();
        if (stack.is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH)) {
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            controller.cycleCrateMode();
            com.simibubi.create.AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos);
            player.displayClientMessage(Component.translatable(
                    "aircrate.aviation_crate.mode." + controller.crateMode().getSerializedName()), true);
            return ItemInteractionResult.CONSUME;
        }
        // Let BlockItem placement run before the crate's empty-hand control surface.
        if (stack.is(AirCrate.ANDESITE_CRATE_HATCH.get().asItem())
                || stack.is(AirCrate.BRASS_CRATE_HATCH.get().asItem())
                || stack.is(AirCrate.CRATE_INTERACTION_HATCH.get().asItem())) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (com.llqqrr.aircrate.content.creature.CreatureParcelItem.isCreatureParcel(stack)) {
            com.llqqrr.aircrate.content.creature.CreatureRecord record =
                    com.llqqrr.aircrate.content.creature.CreatureParcelItem.record(stack);
            if (record == null) return ItemInteractionResult.FAIL;
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            if (!controller.addRecord(record)) return ItemInteractionResult.FAIL;
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.has(com.llqqrr.aircrate.registry.ACDataComponents.CREATURE_RECORD)) {
            return ItemInteractionResult.FAIL;
        }
        int fixtureSlot = TradingProcessor.fixtureSlot(stack);
        if (fixtureSlot >= 0) {
            ItemStack remainder = controller.fixtureInventory().insertItem(fixtureSlot, stack.copy(), false);
            int inserted = stack.getCount() - remainder.getCount();
            if (inserted > 0 && !level.isClientSide) stack.shrink(inserted);
            return inserted > 0 ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                    : ItemInteractionResult.FAIL;
        }
        if (stack.is(ACItemTags.FEED) || stack.is(ACItemTags.BREEDING_FEED)) {
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(controller.feedInventory(), stack.copy(), false);
            int inserted = stack.getCount() - remainder.getCount();
            if (inserted > 0 && !level.isClientSide) {
                stack.shrink(inserted);
            }
            return inserted > 0 ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                    : ItemInteractionResult.FAIL;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (!context.getLevel().isClientSide
                && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof AviationCrateBlockEntity crate) {
            AviationCrateBlockEntity controller = crate.controller();
            controller.cycleCrateMode();
            if (context.getPlayer() != null) context.getPlayer().displayClientMessage(Component.translatable(
                    "aircrate.aviation_crate.mode." + controller.crateMode().getSerializedName()), true);
        }
        IWrenchable.playRotateSound(context.getLevel(), context.getClickedPos());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    /** The Create-style control surface is the small centered panel on each crate face. */
    private static boolean isControlArea(BlockPos pos, BlockHitResult hit) {
        net.minecraft.world.phys.Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        double horizontal = switch (hit.getDirection().getAxis()) {
            case X -> local.z;
            case Y -> local.x;
            case Z -> local.x;
        };
        double vertical = hit.getDirection().getAxis() == net.minecraft.core.Direction.Axis.Y ? local.z : local.y;
        return horizontal >= 0.30D && horizontal <= 0.70D
                && vertical >= 0.55D && vertical <= 0.95D;
    }


    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()
                && level.getBlockEntity(pos) instanceof AviationCrateBlockEntity crate) {
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                AviationCrateWorldManager.beforeRemoval(serverLevel, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        // Wall faces depend on neighbouring crate blocks, so refresh the baked quads.
        if (level.isClientSide && level.getBlockEntity(pos) instanceof AviationCrateBlockEntity crate) {
            crate.requestModelDataUpdate();
        }
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OUTPUT_FACING, OUTPUT_OPEN, PLACEMENT_FACING);
    }
}

package com.llqqrr.aircrate.content.frogport;

import com.llqqrr.aircrate.content.creature.CreatureFilterItem;
import com.llqqrr.aircrate.registry.ACBlockEntities;
import com.llqqrr.aircrate.registry.ACItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

public final class CreatureFrogportBlock extends Block implements EntityBlock, IWrenchable {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public CreatureFrogportBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(ACTIVE) ? 8 : 0));
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreatureFrogportBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level.getBlockEntity(pos) instanceof CreatureFrogportBlockEntity frogport)) return;
        CreatureAreaSelection selection = stack.get(com.llqqrr.aircrate.registry.ACDataComponents.FROGPORT_SELECTION.get());
        if (selection != null && selection.complete() && selection.validSize() && selection.inRangeOf(pos)) frogport.setSelection(selection);
        else if (!level.isClientSide && placer instanceof Player player) {
            player.displayClientMessage(Component.translatable("message.aircrate.selection_too_far"), true);
        }
        stack.remove(com.llqqrr.aircrate.registry.ACDataComponents.FROGPORT_SELECTION.get());
        if (placer != null) {
            var delta = VecHelper.getCenterOf(pos).subtract(placer.position());
            frogport.passiveYaw = Mth.wrapDegrees((float) (Mth.atan2(delta.x, delta.z) * 57.2957763671875D));
            frogport.passiveYaw = Math.round(frogport.passiveYaw / 11.25F) * 11.25F;
            frogport.notifyUpdate();
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CreatureFrogportBlockEntity frogport)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.is(ACItems.CREATURE_FILTER.get())) {
            if (!level.isClientSide) {
                ItemStack old = frogport.filter();
                if (!old.isEmpty()) net.neoforged.neoforge.items.ItemHandlerHelper.giveItemToPlayer(player, old);
                frogport.setFilter(stack.copyWithCount(1));
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (com.llqqrr.aircrate.content.creature.CreatureParcelItem.isCreatureParcel(stack)
                && frogport.insertParcel(stack, level.isClientSide).isEmpty()) {
            if (!level.isClientSide && !player.getAbilities().instabuild) stack.shrink(1);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CreatureFrogportBlockEntity frogport)) return InteractionResult.PASS;
        if (level.isClientSide) {
            com.llqqrr.aircrate.client.AirCrateClient.openFrogportScreen(pos, frogport.mode(),
                    frogport.selection(), frogport.filter());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (!context.getLevel().isClientSide
                && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof CreatureFrogportBlockEntity frogport) {
            frogport.toggleMode();
            if (context.getPlayer() != null) context.getPlayer().displayClientMessage(
                    Component.translatable(frogport.mode() == CreatureFrogportBlockEntity.Mode.CAPTURE
                            ? "message.aircrate.frogport_capture" : "message.aircrate.frogport_release"), true);
        }
        IWrenchable.playRotateSound(context.getLevel(), context.getClickedPos());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (type != ACBlockEntities.CREATURE_FROGPORT.get()) return null;
        return (tickerLevel, tickerPos, tickerState, blockEntity) ->
                CreatureFrogportBlockEntity.tickServer(tickerLevel, tickerPos, tickerState,
                        (CreatureFrogportBlockEntity) blockEntity);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }
}

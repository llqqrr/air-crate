package com.llqqrr.aircrate.content.packager;

import com.llqqrr.aircrate.content.creature.CreatureParcelItem;
import com.llqqrr.aircrate.registry.ACBlockEntities;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Create's native packager shell, backed by creature-aware crate transfer logic. */
public final class CreaturePackagerBlock extends PackagerBlock {
    public CreaturePackagerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        ItemInteractionResult result = super.useItemOn(stack, state, level, pos, player, hand, hit);
        // Create swallows a failed unwrap silently; tell the player why nothing happened.
        if (!level.isClientSide && PackageItem.isPackage(stack)
                && level.getBlockEntity(pos) instanceof CreaturePackagerBlockEntity packager
                && packager.heldBox.isEmpty() && packager.animationTicks <= 0
                && !packager.unwrapBox(stack.copy(), true)) {
            if (CreatureParcelItem.record(stack) == null) {
                player.displayClientMessage(Component.translatable("message.aircrate.packager_not_creature_parcel"), true);
            } else if (packager.targetCrate() == null) {
                player.displayClientMessage(Component.translatable("message.aircrate.packager_no_crate"), true);
            } else {
                player.displayClientMessage(Component.translatable("message.aircrate.packager_crate_rejects"), true);
            }
        }
        return result;
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        // Snap onto an adjacent aviation crate like Create's packager snaps onto an
        // inventory: the packager's back (opposite of FACING) must touch the crate.
        net.minecraft.core.Direction crateDir = null;
        if (context.getLevel().getBlockEntity(context.getClickedPos())
                instanceof com.llqqrr.aircrate.content.block.AviationCrateBlockEntity) {
            crateDir = context.getClickedFace().getOpposite();
        } else {
            BlockPos placementPos = context.getClickedPos().relative(context.getClickedFace());
            for (net.minecraft.core.Direction direction : context.getNearestLookingDirections()) {
                if (context.getLevel().getBlockEntity(placementPos.relative(direction))
                        instanceof com.llqqrr.aircrate.content.block.AviationCrateBlockEntity) {
                    crateDir = direction;
                    break;
                }
            }
        }
        if (crateDir != null) state = state.setValue(FACING, crateDir.getOpposite());
        return state;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Class<PackagerBlockEntity> getBlockEntityClass() {
        return (Class) CreaturePackagerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PackagerBlockEntity> getBlockEntityType() {
        return ACBlockEntities.CREATURE_PACKAGER.get();
    }
}

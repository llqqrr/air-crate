package com.llqqrr.aircrate.content.creature;

import com.llqqrr.aircrate.registry.ACDataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.item.ItemEntity;
import java.util.List;

/** A transport-safe creature record; it is not a generic NBT/entity container. */
public final class SealedCreatureItem extends Item {
    public SealedCreatureItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        CreatureRecord record = stack.get(ACDataComponents.CREATURE_RECORD);
        return record == null ? super.getName(stack) : CreatureRecordDisplay.name(record);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (!stack.has(ACDataComponents.CREATURE_RECORD)) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        CreatureRecord record = stack.get(ACDataComponents.CREATURE_RECORD);
        if (record == null || !(context.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
            return InteractionResult.FAIL;
        }
        if (!CreatureLogistics.isLogisticsSurface(context.getLevel(), context.getClickedPos())) {
            return InteractionResult.FAIL;
        }
        if (!CreatureReleaseService.release(level, context.getClickedPos().relative(context.getClickedFace()), record)) {
            return InteractionResult.FAIL;
        }
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level().isClientSide || entity.tickCount < 2) return false;
        net.minecraft.core.BlockPos pos = entity.blockPosition();
        if (CreatureLogistics.isLogisticsSurface(entity.level(), pos)
                || CreatureLogistics.isLogisticsSurface(entity.level(), pos.below())) return false;
        CreatureRecord record = stack.get(ACDataComponents.CREATURE_RECORD);
        if (record == null || !(entity.level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        if (CreatureReleaseService.release(level, pos, record)) {
            entity.discard();
            return true;
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CreatureRecord record = stack.get(ACDataComponents.CREATURE_RECORD);
        if (record != null) tooltip.add(CreatureRecordDisplay.name(record).copy()
                .withStyle(net.minecraft.ChatFormatting.GREEN));
    }
}

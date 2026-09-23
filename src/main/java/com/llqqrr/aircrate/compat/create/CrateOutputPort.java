package com.llqqrr.aircrate.compat.create;

import com.llqqrr.aircrate.content.block.AviationCrateBlockEntity;
import com.llqqrr.aircrate.content.block.AviationCrateBlock;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** Uses Create's basin eligibility rule to eject products into belts or depots beside any crate member. */
public final class CrateOutputPort {
    private CrateOutputPort() {
    }

    public static void tryOutput(ServerLevel level, AviationCrateBlockEntity member,
                                 AviationCrateBlockEntity controller) {
        boolean foundPort = false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!BasinBlock.canOutputTo(level, member.getBlockPos(), direction)) continue;
            foundPort = true;
            setPortState(level, member, direction, true);
            // BasinBlock.canOutputTo validates the air space beside the source and
            // the belt/depot directly below that space.
            var target = member.getBlockPos().relative(direction).below();
            DirectBeltInputBehaviour belt = BlockEntityBehaviour.get(level, target, DirectBeltInputBehaviour.TYPE);
            for (int slot = 0; slot < controller.outputInventory().getSlots(); slot++) {
                ItemStack stack = controller.outputInventory().getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                ItemStack remainder;
                if (belt != null) {
                    remainder = belt.handleInsertion(stack.copy(), direction, false);
                } else {
                    var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, target, direction);
                    if (handler == null) continue;
                    remainder = ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
                }
                int moved = stack.getCount() - remainder.getCount();
                if (moved > 0) controller.outputInventory().extractItem(slot, moved, false);
                return;
            }
        }
        if (!foundPort) setPortState(level, member, Direction.NORTH, false);
    }

    private static void setPortState(ServerLevel level, AviationCrateBlockEntity member,
                                     Direction direction, boolean open) {
        var state = member.getBlockState();
        if (!(state.getBlock() instanceof AviationCrateBlock)
                || state.getValue(AviationCrateBlock.OUTPUT_OPEN) == open
                && (!open || state.getValue(AviationCrateBlock.OUTPUT_FACING) == direction)) return;
        level.setBlock(member.getBlockPos(), state.setValue(AviationCrateBlock.OUTPUT_OPEN, open)
                .setValue(AviationCrateBlock.OUTPUT_FACING, direction), 3);
    }
}

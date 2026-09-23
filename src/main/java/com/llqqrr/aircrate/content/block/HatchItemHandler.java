package com.llqqrr.aircrate.content.block;

import com.llqqrr.aircrate.content.creature.CreatureRecord;
import com.llqqrr.aircrate.registry.ACDataComponents;
import com.llqqrr.aircrate.registry.ACItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** Ordinary item logistics plus one virtual, height-gated creature channel. */
final class HatchItemHandler implements IItemHandler {
    private final AviationCrateHatchBlockEntity hatch;

    HatchItemHandler(AviationCrateHatchBlockEntity hatch) { this.hatch = hatch; }

    private BlockPos lower() {
        return hatch.getLevel() == null ? hatch.getBlockPos()
                : AviationCrateHatchBlock.groupBottom(hatch.getLevel(), hatch.getBlockPos(), hatch.getBlockState());
    }

    private AviationCrateBlockEntity controller() {
        if (hatch.getLevel() == null) return null;
        BlockPos lower = lower();
        var state = hatch.getLevel().getBlockState(lower);
        return AviationCrateHatchBlock.controller(hatch.getLevel(), lower,
                state.getValue(AviationCrateHatchBlock.FACING));
    }

    private IItemHandler delegate() {
        AviationCrateBlockEntity controller = controller();
        return controller == null ? null : controller.automationInventory();
    }

    private boolean ready(CreatureRecord record) {
        if (hatch.getLevel() == null) return false;
        BlockPos lower = lower();
        var state = hatch.getLevel().getBlockState(lower);
        return AviationCrateHatchBlock.isValidHatch(hatch.getLevel(), lower, state)
                && AviationCrateHatchBlock.canPass(hatch.getLevel(), lower, record);
    }

    private CreatureRecord outputRecord() {
        AviationCrateBlockEntity controller = controller();
        if (controller == null) return null;
        for (CreatureRecord record : controller.records()) if (ready(record)) return record;
        return null;
    }

    private int creatureSlot() {
        IItemHandler target = delegate();
        return target == null ? 0 : target.getSlots();
    }

    @Override public int getSlots() { return creatureSlot() + 1; }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot == creatureSlot()) {
            CreatureRecord record = outputRecord();
            if (record == null) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(ACItems.SEALED_CREATURE.get());
            stack.set(ACDataComponents.CREATURE_RECORD.get(), record);
            return stack;
        }
        IItemHandler target = delegate();
        return target == null || slot < 0 || slot >= target.getSlots() ? ItemStack.EMPTY : target.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        // Creature parcels are not a hatch input; the creature packager owns unpacking.
        if (com.llqqrr.aircrate.content.creature.CreatureParcelItem.isCreatureParcel(stack)) return stack;
        CreatureRecord record = stack.get(ACDataComponents.CREATURE_RECORD);
        if (record != null) {
            AviationCrateBlockEntity controller = controller();
            if (controller == null || !ready(record) || !controller.canAcceptRecordFromHatch(record)) return stack;
            if (!simulate && !controller.addRecord(record)) return stack;
            ItemStack remainder = stack.copy();
            remainder.shrink(1);
            return remainder;
        }
        IItemHandler target = delegate();
        return target == null || slot < 0 || slot >= target.getSlots() ? stack : target.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot == creatureSlot() && amount > 0) {
            CreatureRecord record = outputRecord();
            AviationCrateBlockEntity controller = controller();
            if (record == null || controller == null) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(ACItems.SEALED_CREATURE.get());
            stack.set(ACDataComponents.CREATURE_RECORD.get(), record);
            if (!simulate) controller.removeRecord(record.recordId());
            return stack;
        }
        IItemHandler target = delegate();
        return target == null || slot < 0 || slot >= target.getSlots()
                ? ItemStack.EMPTY : target.extractItem(slot, amount, simulate);
    }

    @Override public int getSlotLimit(int slot) { return slot == creatureSlot() ? 1 : 64; }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (com.llqqrr.aircrate.content.creature.CreatureParcelItem.isCreatureParcel(stack)) return false;
        CreatureRecord record = stack.get(ACDataComponents.CREATURE_RECORD);
        if (record != null) return ready(record);
        IItemHandler target = delegate();
        return target != null && slot >= 0 && slot < target.getSlots() && target.isItemValid(slot, stack);
    }
}

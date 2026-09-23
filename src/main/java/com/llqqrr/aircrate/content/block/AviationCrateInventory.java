package com.llqqrr.aircrate.content.block;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Every stored item is extractable; only the feed half accepts ordinary automation inserts. */
final class AviationCrateInventory implements IItemHandler {
    private final ItemStackHandler feed;
    private final ItemStackHandler output;

    AviationCrateInventory(ItemStackHandler feed, ItemStackHandler output) {
        this.feed = feed;
        this.output = output;
    }

    @Override public int getSlots() { return feed.getSlots() + output.getSlots(); }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot < feed.getSlots() ? feed.getStackInSlot(slot) : output.getStackInSlot(slot - feed.getSlots());
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        // Creature parcels enter a crate exclusively through a creature packager.
        if (com.llqqrr.aircrate.content.creature.CreatureParcelItem.isCreatureParcel(stack)) return stack;
        if (slot >= feed.getSlots()) return stack;
        if (stack.has(com.llqqrr.aircrate.registry.ACDataComponents.CREATURE_RECORD)) return stack;
        return feed.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return slot < feed.getSlots() ? feed.extractItem(slot, amount, simulate)
                : output.extractItem(slot - feed.getSlots(), amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot < feed.getSlots() ? feed.getSlotLimit(slot) : output.getSlotLimit(slot - feed.getSlots());
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return !com.llqqrr.aircrate.content.creature.CreatureParcelItem.isCreatureParcel(stack)
                && !stack.has(com.llqqrr.aircrate.registry.ACDataComponents.CREATURE_RECORD)
                && slot < feed.getSlots() && feed.isItemValid(slot, stack);
    }
}

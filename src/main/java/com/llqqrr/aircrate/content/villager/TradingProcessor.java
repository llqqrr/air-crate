package com.llqqrr.aircrate.content.villager;

import com.llqqrr.aircrate.content.block.AviationCrateBlockEntity;
import com.llqqrr.aircrate.content.creature.CreatureRecord;
import com.llqqrr.aircrate.registry.ACItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

/** Inventory-only trade execution modelled after a depot: inputs are consumed, products enter output. */
public final class TradingProcessor {
    private TradingProcessor() { }

    public static void tick(AviationCrateBlockEntity crate) {
        installFixtures(crate);
        int villagers = (int) crate.records().stream().filter(record -> !record.trades().isEmpty()).count();
        int beds = countFixture(crate, 0, 0);
        int workstations = countFixture(crate, 1, 1);
        int displays = countFixture(crate, 2, 2);
        if (!TradingStationRules.canTrade(crate.size(), villagers, beds, workstations, displays)) return;
        for (CreatureRecord record : crate.records()) {
            for (int offerIndex = 0; offerIndex < record.trades().size(); offerIndex++) {
                TradeOfferSnapshot offer = record.trades().get(offerIndex);
                if (offer.exhausted() || !canPay(crate, offer) || !canOutput(crate, offer.result())) continue;
                consume(crate, offer.costA());
                consume(crate, offer.costB());
                ItemHandlerHelper.insertItemStacked(crate.outputInventory(), offer.result().copy(), false);
                List<TradeOfferSnapshot> updatedTrades = new ArrayList<>(record.trades());
                updatedTrades.set(offerIndex, offer.usedOnce());
                crate.replaceRecord(new CreatureRecord(record.recordId(), record.entityType(), record.profileId(),
                        record.juvenile(), record.ageTicks(), record.healthMilli(), record.revision() + 1, updatedTrades,
                        updateOfferNbt(crate, record, offerIndex), record.widthMilli(), record.heightMilli(),
                        record.lastMilkGameTime(), record.sheared(), record.lastShearedGameTime()));
                return;
            }
        }
    }

    private static String updateOfferNbt(AviationCrateBlockEntity crate, CreatureRecord record, int offerIndex) {
        if (crate.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            net.minecraft.world.entity.Entity entity = com.llqqrr.aircrate.content.creature.CreatureReleaseService
                    .create(level, record);
            if (entity instanceof net.minecraft.world.entity.npc.AbstractVillager villager
                    && offerIndex >= 0 && offerIndex < villager.getOffers().size()) {
                villager.getOffers().get(offerIndex).increaseUses();
                return villager.saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString();
            }
        }
        return record.entityData();
    }

    private static void installFixtures(AviationCrateBlockEntity crate) {
        for (int input = 0; input < crate.feedInventory().getSlots(); input++) {
            ItemStack stack = crate.feedInventory().getStackInSlot(input);
            int fixtureSlot = fixtureSlot(stack);
            if (fixtureSlot < 0) continue;
            ItemStack remainder = crate.fixtureInventory().insertItem(fixtureSlot, stack.copy(), false);
            int moved = stack.getCount() - remainder.getCount();
            if (moved > 0) crate.feedInventory().extractItem(input, moved, false);
        }
    }

    public static int fixtureSlot(ItemStack stack) {
        if (stack.is(ACItems.TRADING_BED_FIXTURE.get())) return 0;
        if (stack.is(ACItems.TRADING_WORKSTATION_FIXTURE.get())) return 1;
        if (stack.is(ACItems.TRADING_DISPLAY_FIXTURE.get())) return 2;
        if (stack.getItem() instanceof BedItem) return 0;
        if (stack.is(Blocks.LECTERN.asItem())) return 2;
        if (isWorkstation(stack)) return 1;
        return -1;
    }

    private static int countFixture(AviationCrateBlockEntity crate, int slot, int kind) {
        ItemStack stack = crate.fixtureInventory().getStackInSlot(slot);
        return fixtureSlot(stack) == kind ? stack.getCount() : 0;
    }

    private static boolean isWorkstation(ItemStack stack) {
        return stack.is(Blocks.SMITHING_TABLE.asItem()) || stack.is(Blocks.FLETCHING_TABLE.asItem())
                || stack.is(Blocks.CARTOGRAPHY_TABLE.asItem()) || stack.is(Blocks.COMPOSTER.asItem())
                || stack.is(Blocks.BARREL.asItem()) || stack.is(Blocks.BLAST_FURNACE.asItem())
                || stack.is(Blocks.BREWING_STAND.asItem()) || stack.is(Blocks.CAULDRON.asItem())
                || stack.is(Blocks.LOOM.asItem()) || stack.is(Blocks.STONECUTTER.asItem())
                || stack.is(Blocks.GRINDSTONE.asItem()) || stack.is(Blocks.SMOKER.asItem());
    }

    private static boolean canPay(AviationCrateBlockEntity crate, TradeOfferSnapshot offer) {
        if (ItemStack.isSameItemSameComponents(offer.costA(), offer.costB())) {
            return available(crate, offer.costA()) >= offer.costA().getCount() + offer.costB().getCount();
        }
        return available(crate, offer.costA()) >= offer.costA().getCount()
                && available(crate, offer.costB()) >= offer.costB().getCount();
    }

    private static int available(AviationCrateBlockEntity crate, ItemStack wanted) {
        if (wanted.isEmpty()) return 0;
        int available = 0;
        for (int slot = 0; slot < crate.feedInventory().getSlots(); slot++) {
            ItemStack stack = crate.feedInventory().getStackInSlot(slot);
            if (ItemStack.isSameItemSameComponents(stack, wanted)) available += stack.getCount();
        }
        return available;
    }

    private static boolean canOutput(AviationCrateBlockEntity crate, ItemStack result) {
        return ItemHandlerHelper.insertItemStacked(crate.outputInventory(), result.copy(), true).isEmpty();
    }

    private static void consume(AviationCrateBlockEntity crate, ItemStack wanted) {
        int remaining = wanted.getCount();
        if (wanted.isEmpty()) return;
        for (int slot = 0; slot < crate.feedInventory().getSlots() && remaining > 0; slot++) {
            ItemStack stack = crate.feedInventory().getStackInSlot(slot);
            if (!ItemStack.isSameItemSameComponents(stack, wanted)) continue;
            int removed = crate.feedInventory().extractItem(slot, remaining, false).getCount();
            remaining -= removed;
        }
    }
}

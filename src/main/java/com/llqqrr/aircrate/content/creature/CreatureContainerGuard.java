package com.llqqrr.aircrate.content.creature;

import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/** Keeps transported creature records out of player inventories. */
public final class CreatureContainerGuard {
    private CreatureContainerGuard() { }

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CreatureContainerGuard::onPickup);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CreatureContainerGuard::onPlayerTick);
    }

    private static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (isCreatureParcel(event.getItemEntity().getItem()) && hasParcel(event.getPlayer())) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide && player.tickCount % 5 == 0) dropExcessParcels(player);
        if (!hasParcel(player)) return;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 0,
                true, false, false));
    }

    private static void dropExcessParcels(Player player) {
        boolean kept = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!isCreatureParcel(stack)) continue;
            if (!kept) {
                kept = true;
                continue;
            }
            ItemStack excess = stack.copy();
            player.getInventory().setItem(slot, ItemStack.EMPTY);
            player.drop(excess, false);
        }
    }

    public static boolean isCreatureParcel(ItemStack stack) {
        return CreatureParcelItem.isCreatureParcel(stack);
    }

    /** A hopper may only stage a parcel when it feeds a release-mode creature frogport. */
    public static boolean isCreatureLogisticsHopper(Object container) {
        if (!(container instanceof HopperBlockEntity hopper) || hopper.getLevel() == null) return false;
        var facing = hopper.getBlockState().getValue(HopperBlock.FACING);
        return hopper.getLevel().getBlockEntity(hopper.getBlockPos().relative(facing))
                instanceof com.llqqrr.aircrate.content.frogport.CreatureFrogportBlockEntity frogport
                && frogport.mode() == com.llqqrr.aircrate.content.frogport.CreatureFrogportBlockEntity.Mode.RELEASE;
    }

    public static boolean hasParcel(Player player) {
        for (ItemStack stack : player.getInventory().items) if (isCreatureParcel(stack)) return true;
        for (ItemStack stack : player.getInventory().offhand) if (isCreatureParcel(stack)) return true;
        for (ItemStack stack : player.getInventory().armor) if (isCreatureParcel(stack)) return true;
        return false;
    }
}

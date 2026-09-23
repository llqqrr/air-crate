package com.llqqrr.aircrate.mixin;

import com.llqqrr.aircrate.content.creature.CreatureContainerGuard;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents GUI insertion into chests, drawers, vaults and other slot-backed inventories. */
@Mixin(Slot.class)
abstract class ContainerSlotMixin {
    @Shadow public final net.minecraft.world.Container container;

    protected ContainerSlotMixin(net.minecraft.world.Container container) {
        this.container = container;
    }

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void aircrate$rejectCreatureRecord(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (CreatureContainerGuard.isCreatureParcel(stack)
                && !(container instanceof net.minecraft.world.entity.player.Inventory)
                && !CreatureContainerGuard.isCreatureLogisticsHopper(container)) {
            cir.setReturnValue(false);
        }
    }
}

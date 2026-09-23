package com.llqqrr.aircrate.mixin;

import com.llqqrr.aircrate.content.creature.CreatureContainerGuard;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rejects universal creature records from generic mod inventories. Create logistics use dedicated handlers. */
@Mixin(value = ItemStackHandler.class, remap = false)
abstract class ItemStackHandlerMixin {
    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true)
    private void aircrate$rejectCreatureRecord(int slot, ItemStack stack, boolean simulate,
                                               CallbackInfoReturnable<ItemStack> cir) {
        if (!CreatureContainerGuard.isCreatureParcel(stack)) return;
        String handler = ((Object) this).getClass().getName().toLowerCase(java.util.Locale.ROOT);
        boolean createLogisticsSurface = handler.startsWith("com.simibubi.create.")
                && (handler.contains("belt") || handler.contains("depot") || handler.contains("chute")
                || handler.contains("funnel") || handler.contains("packageport") || handler.contains("frogport")
                || handler.contains("packager"));
        if (!createLogisticsSurface) cir.setReturnValue(stack);
    }
}

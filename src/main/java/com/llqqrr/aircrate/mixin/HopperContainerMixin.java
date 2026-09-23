package com.llqqrr.aircrate.mixin;

import com.llqqrr.aircrate.content.creature.CreatureContainerGuard;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents vanilla hopper-style movement into every ordinary Container implementation. */
@Mixin(HopperBlockEntity.class)
abstract class HopperContainerMixin {
    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private static void aircrate$rejectCreatureRecord(@Nullable Container source, Container destination,
                                                       ItemStack stack, @Nullable Direction direction,
                                                       CallbackInfoReturnable<ItemStack> cir) {
        if (CreatureContainerGuard.isCreatureParcel(stack)
                && !CreatureContainerGuard.isCreatureLogisticsHopper(destination)) {
            cir.setReturnValue(stack);
        }
    }
}

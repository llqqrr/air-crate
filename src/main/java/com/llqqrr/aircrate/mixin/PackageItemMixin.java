package com.llqqrr.aircrate.mixin;

import com.llqqrr.aircrate.content.creature.CreatureParcelItem;
import com.simibubi.create.content.logistics.box.PackageItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A creature parcel can be thrown or transported, but cannot be unpacked as an empty item package. */
@Mixin(value = PackageItem.class, remap = false)
abstract class PackageItemMixin {
    @Inject(method = "open", at = @At("HEAD"), cancellable = true)
    private void aircrate$keepCreatureContained(Level level, Player player, InteractionHand hand,
                                                 CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if (CreatureParcelItem.isCreatureParcel(stack)) {
            cir.setReturnValue(InteractionResultHolder.fail(stack));
        }
    }
}

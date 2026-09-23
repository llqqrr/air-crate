package com.llqqrr.aircrate.mixin;

import com.llqqrr.aircrate.content.creature.CreatureParcelSawService;
import com.llqqrr.aircrate.content.creature.CreatureParcelItem;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Creature parcels are slaughtered instead of unpacked into an empty package inventory. */
@Mixin(value = SawBlockEntity.class, remap = false)
abstract class SawBlockEntityMixin {
    @Shadow public ProcessingInventory inventory;

    @Inject(method = "applyRecipe", at = @At("HEAD"), cancellable = true)
    private void aircrate$slaughterCreatureParcel(CallbackInfo ci) {
        ItemStack input = inventory.getStackInSlot(0);
        SawBlockEntity self = (SawBlockEntity) (Object) this;
        if (!CreatureParcelItem.isCreatureParcel(input) || !(self.getLevel() instanceof ServerLevel level)) return;
        if (!CreatureParcelSawService.killContents(level, self.getBlockPos().getCenter().add(0, .5, 0), input)) return;
        inventory.setStackInSlot(0, ItemStack.EMPTY);
        self.setChanged();
        ci.cancel();
    }
}

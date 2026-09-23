package com.llqqrr.aircrate.mixin;

import com.llqqrr.aircrate.content.creature.CreatureParcelItem;
import com.simibubi.create.content.logistics.box.PackageEntity;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets creature parcels reach PackageRenderer even when Flywheel visualizes entities. */
@Mixin(value = VisualizationHelper.class, remap = false)
abstract class VisualizationHelperMixin {
    @Inject(method = "skipVanillaRender(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void aircrate$renderCreatureParcelVanilla(Entity entity,
                                                               CallbackInfoReturnable<Boolean> callback) {
        if (entity instanceof PackageEntity packageEntity
                && CreatureParcelItem.isCreatureParcel(packageEntity.box)) {
            callback.setReturnValue(false);
        }
    }
}

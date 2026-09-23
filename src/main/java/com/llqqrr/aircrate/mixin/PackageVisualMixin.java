package com.llqqrr.aircrate.mixin;

import com.llqqrr.aircrate.content.creature.CreatureParcelItem;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageVisual;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets PackageRendererMixin own creature parcel rendering in Flywheel mode. */
@Mixin(value = PackageVisual.class, remap = false)
abstract class PackageVisualMixin {
    @Shadow @Final protected PackageEntity entity;
    @Shadow public TransformedInstance instance;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void aircrate$hideCreatureParcelShell(VisualizationContext context, PackageEntity entity,
                                                   float partialTick, CallbackInfo callback) {
        if (CreatureParcelItem.isCreatureParcel(entity.box)) instance.setVisible(false);
    }

    @Inject(method = "beginFrame", at = @At("HEAD"))
    private void aircrate$stabilizePackageVisual(dev.engine_room.flywheel.api.visual.DynamicVisual.Context context,
                                                   CallbackInfo callback) {
        if (CreatureParcelItem.isCreatureParcel(entity.box)) {
            // Create/Flywheel may recreate visibility state while the visual is retained.
            // Reassert this every frame so the vanilla renderer remains the sole owner of
            // the creature parcel shell.
            instance.setVisible(false);
            return;
        }
        // Keep the normal package path neutral as well. This prevents a stale instance
        // tint/overlay from being carried across Iris' batched Flywheel draws.
        instance.setVisible(true);
        instance.colorRgb(0xFFFFFF);
        instance.overlay(0);
    }
}

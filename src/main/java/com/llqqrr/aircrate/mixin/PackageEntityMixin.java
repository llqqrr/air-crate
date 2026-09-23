package com.llqqrr.aircrate.mixin;

import com.llqqrr.aircrate.content.creature.CreatureContainerGuard;
import com.llqqrr.aircrate.content.creature.CreatureParcelItem;
import com.llqqrr.aircrate.content.creature.CreatureRecord;
import com.llqqrr.aircrate.content.creature.CreatureReleaseService;
import com.simibubi.create.content.logistics.box.PackageEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;

/** Applies the one-parcel carry limit to Create's custom package entity pickup path. */
@Mixin(value = PackageEntity.class, remap = false)
abstract class PackageEntityMixin {
    @Shadow public ItemStack box;

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void aircrate$limitCreatureParcelPickup(Player player, InteractionHand hand,
                                                     CallbackInfoReturnable<InteractionResult> cir) {
        if (CreatureParcelItem.isCreatureParcel(box) && CreatureContainerGuard.hasParcel(player)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void aircrate$sawKillsContainedCreature(DamageSource source, float amount,
                                                     CallbackInfoReturnable<Boolean> cir) {
        CreatureRecord record = CreatureParcelItem.record(box);
        if (record == null) return;
        boolean saw = source.typeHolder().unwrapKey()
                .map(key -> key.location().getNamespace().equals("create")
                        && key.location().getPath().equals("saw"))
                .orElse(false);
        PackageEntity self = (PackageEntity) (Object) this;
        if (!(self.level() instanceof ServerLevel level)) return;
        boolean handled = saw
                ? com.llqqrr.aircrate.content.creature.CreatureParcelSawService
                        .killContents(level, self.position(), box)
                : CreatureReleaseService.release(level, self.blockPosition(), record);
        if (!handled) return;
        self.discard();
        cir.setReturnValue(true);
    }
}

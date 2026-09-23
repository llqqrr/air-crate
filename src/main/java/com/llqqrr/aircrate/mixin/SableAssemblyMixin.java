package com.llqqrr.aircrate.mixin;

import com.llqqrr.aircrate.content.structure.AviationCrateWorldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;
import java.util.Map;

/** Makes an Aeronautics/Sable assembly fail rather than separating one logical crate. */
@Mixin(targets = "dev.ryanhcode.sable.api.SubLevelAssemblyHelper")
abstract class SableAssemblyMixin {
    @Inject(method = "assembleBlocks", at = @At("HEAD"), cancellable = true)
    private static void aircrate$requireWholeCrates(ServerLevel level, BlockPos anchor, Iterable<BlockPos> members,
                                                    @Coerce Object bounds,
                                                    CallbackInfoReturnable<Object> callback) {
        if (!AviationCrateWorldManager.containsOnlyWholeStructures(level, members)) callback.setReturnValue(null);
    }

    @Inject(method = "moveBlocks", at = @At("TAIL"))
    private static void aircrate$rebuildMovedCrates(ServerLevel level, @Coerce Object transform,
                                                    Iterable<BlockPos> members, CallbackInfo callback) {
        Map<java.util.UUID, BlockPos> structureSeeds = new LinkedHashMap<>();
        for (BlockPos member : members) {
            BlockPos moved = applyPos(transform, member);
            if (moved != null && level.getBlockEntity(moved)
                    instanceof com.llqqrr.aircrate.content.block.AviationCrateBlockEntity crate) {
                structureSeeds.putIfAbsent(crate.structureId(), moved.immutable());
            }
        }
        structureSeeds.values().forEach(seed -> AviationCrateWorldManager.rebuildAt(level, seed));
    }

    private static BlockPos applyPos(Object transform, BlockPos original) {
        try {
            Object result = transform.getClass().getMethod("apply", BlockPos.class).invoke(transform, original);
            return result instanceof BlockPos pos ? pos : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}

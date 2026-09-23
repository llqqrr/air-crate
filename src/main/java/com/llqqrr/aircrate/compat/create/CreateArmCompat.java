package com.llqqrr.aircrate.compat.create;

import com.llqqrr.aircrate.AirCrate;
import com.llqqrr.aircrate.content.block.AviationCrateBlock;
import com.llqqrr.aircrate.content.block.AviationCrateHatchBlock;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Create-only registration. The class is never loaded when Create is absent. */
public final class CreateArmCompat {
    private static final ResourceLocation ARM_POINT_ID = ResourceLocation.fromNamespaceAndPath(AirCrate.MOD_ID,
            "aviation_crate");

    private CreateArmCompat() { }

    public static void register(IEventBus modBus) {
        modBus.addListener(CreateArmCompat::registerArmPoint);
    }

    private static void registerArmPoint(RegisterEvent event) {
        if (!event.getRegistryKey().equals(CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE.key())) return;
        if (CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE.getOptional(ARM_POINT_ID).isPresent()) return;
        event.register(CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE.key(), ARM_POINT_ID,
                AviationCratePointType::new);
    }

    private static final class AviationCratePointType extends ArmInteractionPointType {
        @Override
        public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
            return state.getBlock() instanceof AviationCrateBlock
                    || state.getBlock() instanceof AviationCrateHatchBlock;
        }

        @Override
        public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
            return new AviationCratePoint(this, level, pos, state);
        }
    }

    private static final class AviationCratePoint extends ArmInteractionPoint {
        private AviationCratePoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
            super(type, level, pos, state);
        }

        @Override
        protected Vec3 getInteractionPositionVector() {
            return Vec3.atLowerCornerOf(pos).add(0.5D, 0.78D, 0.5D);
        }
    }
}

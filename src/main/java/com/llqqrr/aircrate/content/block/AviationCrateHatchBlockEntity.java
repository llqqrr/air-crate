package com.llqqrr.aircrate.content.block;

import com.llqqrr.aircrate.content.creature.CreatureCaptureService;
import com.llqqrr.aircrate.registry.ACBlockEntities;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/** Logistics endpoint and automatic intake state for one hatch half. */
public final class AviationCrateHatchBlockEntity extends SmartBlockEntity {
    private final ItemStackHandler filter = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler automation = new HatchItemHandler(this);

    public AviationCrateHatchBlockEntity(BlockPos pos, BlockState state) {
        super(ACBlockEntities.AVIATION_CRATE_HATCH.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new DirectBeltInputBehaviour(this)
                .setInsertionHandler((transported, side, simulate) ->
                        ItemHandlerHelper.insertItemStacked(automation, transported.stack.copy(), simulate)));
    }

    public ItemStackHandler filterInventory() {
        return filter;
    }

    public IItemHandler automationInventory() {
        return automation;
    }

    public boolean isBrass() {
        return getBlockState().getBlock() == com.llqqrr.aircrate.AirCrate.BRASS_CRATE_HATCH.get();
    }

    public static void tickServer(Level level, BlockPos pos, BlockState state,
                                   AviationCrateHatchBlockEntity hatch) {
        if (level.isClientSide) return;
        // Old builds toggled this legacy state on right-click, which selected a
        // non-colliding visual variant. Normalize it on the next server tick.
        if (state.getValue(AviationCrateHatchBlock.OPEN)) {
            level.setBlock(pos, state.setValue(AviationCrateHatchBlock.OPEN, false), 3);
            state = level.getBlockState(pos);
        }
        if (level.getGameTime() % 5L != 0L) return;
        BlockPos lower = AviationCrateHatchBlock.groupBottom(level, pos, state);
        if (!lower.equals(pos)) return;
        var controller = AviationCrateHatchBlock.controller(level, pos, state.getValue(AviationCrateHatchBlock.FACING));
        if (controller == null) return;
        BlockPos outside = pos.relative(state.getValue(AviationCrateHatchBlock.FACING));
        AABB intake = new AABB(outside).inflate(0.48D, 0.05D, 0.48D)
                .expandTowards(0.0D, AviationCrateHatchBlock.openingHeight(level, pos), 0.0D);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, intake,
                candidate -> CreatureCaptureService.isSupported(candidate)
                        && (!hatch.isBrass() || hatch.filter.getStackInSlot(0).isEmpty()
                        || CreatureCaptureService.matchesFilter(candidate, hatch.filter.getStackInSlot(0))))) {
            if (AviationCrateHatchBlock.canPass(level, pos, CreatureCaptureService.recordFromEntity(entity))
                    && CreatureCaptureService.capture(controller, entity)) break;
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Filter", filter.serializeNBT(registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        filter.deserializeNBT(registries, tag.getCompound("Filter"));
    }
}

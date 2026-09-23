package com.llqqrr.aircrate.content.block;

import com.llqqrr.aircrate.content.creature.CreatureCaptureService;
import com.llqqrr.aircrate.registry.ACBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Output buffer for the belt fixture. Create can extract it through ItemHandler. */
public final class CreatureBeltFixtureBlockEntity extends BlockEntity {
    private final ItemStackHandler output = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public CreatureBeltFixtureBlockEntity(BlockPos pos, BlockState state) {
        super(ACBlockEntities.CREATURE_BELT_FIXTURE.get(), pos, state);
    }

    public ItemStackHandler outputInventory() {
        return output;
    }

    public static void tickServer(Level level, BlockPos pos, BlockState state,
                                  CreatureBeltFixtureBlockEntity fixture) {
        if (level.isClientSide || level.getGameTime() % 5L != 0L || !fixture.output.getStackInSlot(0).isEmpty()) return;
        AABB intake = new AABB(pos).inflate(0.75D, 0.25D, 0.75D).expandTowards(0.0D, 1.25D, 0.0D);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, intake,
                candidate -> candidate.getBbHeight() <= 2.0D && CreatureCaptureService.isSupported(candidate))) {
            ItemStack record = CreatureCaptureService.asRecordItem(entity);
            if (record.isEmpty() || !fixture.output.insertItem(0, record, true).isEmpty()) continue;
            fixture.output.insertItem(0, record, false);
            entity.discard();
            break;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Output", output.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        output.deserializeNBT(registries, tag.getCompound("Output"));
    }
}

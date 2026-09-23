package com.llqqrr.aircrate.compat.create;

import com.llqqrr.aircrate.AirCrate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/** Persistent set of Create belt controllers carrying the creature fixture upgrade. */
public final class CreatureBeltFixtureData extends SavedData {
    private static final String FILE_NAME = AirCrate.MOD_ID + "_belt_fixtures";
    private final Set<BlockPos> controllers = new HashSet<>();

    public static CreatureBeltFixtureData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(CreatureBeltFixtureData::new, CreatureBeltFixtureData::load), FILE_NAME);
    }

    public boolean install(BlockPos controller) {
        boolean added = controllers.add(controller.immutable());
        if (added) setDirty();
        return added;
    }

    public Set<BlockPos> controllers() { return Set.copyOf(controllers); }

    public void remove(BlockPos controller) {
        if (controllers.remove(controller)) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (BlockPos pos : controllers) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", pos.asLong());
            list.add(entry);
        }
        tag.put("Controllers", list);
        return tag;
    }

    private static CreatureBeltFixtureData load(CompoundTag tag, HolderLookup.Provider registries) {
        CreatureBeltFixtureData data = new CreatureBeltFixtureData();
        ListTag list = tag.getList("Controllers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) data.controllers.add(BlockPos.of(list.getCompound(i).getLong("Pos")));
        return data;
    }
}

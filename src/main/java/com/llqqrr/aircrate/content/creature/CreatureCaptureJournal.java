package com.llqqrr.aircrate.content.creature;

import com.llqqrr.aircrate.AirCrate;
import com.llqqrr.aircrate.content.block.AviationCrateBlockEntity;
import com.llqqrr.aircrate.registry.ACDataComponents;
import com.llqqrr.aircrate.registry.ACItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Write-ahead capture journal: PREPARE -> DESTINATION_COMMITTED -> SOURCE_TOMBSTONED -> COMPLETE. */
public final class CreatureCaptureJournal extends SavedData {
    private static final String FILE_NAME = AirCrate.MOD_ID + "_capture_journal";

    private enum State { PREPARE, DESTINATION_COMMITTED, SOURCE_TOMBSTONED }

    private record Entry(State state, ResourceKey<Level> dimension, BlockPos controllerPos, CreatureRecord record) {
    }

    private final Map<UUID, Entry> entries = new HashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CreatureCaptureJournal::onServerStarted);
    }

    public static CreatureCaptureJournal get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(CreatureCaptureJournal::new, CreatureCaptureJournal::load), FILE_NAME);
    }

    public void prepare(CreatureRecord record, ResourceKey<Level> dimension, BlockPos controllerPos) {
        entries.put(record.recordId(),
                new Entry(State.PREPARE, dimension, controllerPos.immutable(), record));
        setDirty();
    }

    public void markDestinationCommitted(UUID recordId) {
        advance(recordId, State.DESTINATION_COMMITTED);
    }

    public void markSourceTombstoned(UUID recordId) {
        advance(recordId, State.SOURCE_TOMBSTONED);
    }

    public void complete(UUID recordId) {
        if (entries.remove(recordId) != null) setDirty();
    }

    private void advance(UUID recordId, State state) {
        Entry entry = entries.get(recordId);
        if (entry == null) return;
        entries.put(recordId, new Entry(state, entry.dimension(), entry.controllerPos(), entry.record()));
        setDirty();
    }

    private static void onServerStarted(ServerStartedEvent event) {
        replay(event.getServer());
    }

    /** Committed captures keep their record in the crate; stale preparations are dropped. */
    private static void replay(MinecraftServer server) {
        CreatureCaptureJournal journal = get(server.overworld());
        if (journal.entries.isEmpty()) return;
        for (Map.Entry<UUID, Entry> stored : Map.copyOf(journal.entries).entrySet()) {
            UUID recordId = stored.getKey();
            Entry entry = stored.getValue();
            if (entry.state() == State.PREPARE) {
                journal.complete(recordId);
                continue;
            }
            ServerLevel level = server.getLevel(entry.dimension());
            if (level == null) continue;
            AviationCrateBlockEntity crate = level.getBlockEntity(entry.controllerPos())
                    instanceof AviationCrateBlockEntity member ? member.controller() : null;
            boolean kept = crate != null
                    && crate.records().stream().anyMatch(record -> record.recordId().equals(recordId));
            if (!kept && (crate == null || !crate.addRecord(entry.record()))) {
                ItemStack sealed = new ItemStack(ACItems.SEALED_CREATURE.get());
                sealed.set(ACDataComponents.CREATURE_RECORD.get(), entry.record().nextRevision());
                Containers.dropItemStack(level, entry.controllerPos().getX() + 0.5,
                        entry.controllerPos().getY() + 0.5, entry.controllerPos().getZ() + 0.5, sealed);
            }
            journal.complete(recordId);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Entry> stored : entries.entrySet()) {
            Entry entry = stored.getValue();
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("Id", stored.getKey());
            entryTag.putString("State", entry.state().name());
            entryTag.putString("Dimension", entry.dimension().location().toString());
            entryTag.putLong("Pos", entry.controllerPos().asLong());
            CreatureRecord.codec().encodeStart(ops, entry.record()).result()
                    .ifPresent(encoded -> entryTag.put("Record", encoded));
            list.add(entryTag);
        }
        tag.put("Entries", list);
        return tag;
    }

    private static CreatureCaptureJournal load(CompoundTag tag, HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        CreatureCaptureJournal journal = new CreatureCaptureJournal();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            if (!entryTag.hasUUID("Id")) continue;
            State state;
            try {
                state = State.valueOf(entryTag.getString("State"));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            CreatureRecord record = entryTag.contains("Record")
                    ? CreatureRecord.codec().parse(ops, entryTag.get("Record")).result().orElse(null) : null;
            if (record == null) continue;
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(entryTag.getString("Dimension")));
            journal.entries.put(entryTag.getUUID("Id"),
                    new Entry(state, dimension, BlockPos.of(entryTag.getLong("Pos")), record));
        }
        return journal;
    }
}

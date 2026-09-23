package com.llqqrr.aircrate.content.structure;

import com.llqqrr.aircrate.content.block.AviationCrateBlockEntity;
import com.llqqrr.aircrate.content.creature.CreatureRecord;
import com.llqqrr.aircrate.content.creature.CreatureReleaseService;
import com.llqqrr.aircrate.registry.ACItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Connects the pure partition/allocation rules to loaded world block entities. */
public final class AviationCrateWorldManager {
    private static final int SCAN_LIMIT = 2048;

    private AviationCrateWorldManager() {
    }

    public static void rebuildAt(ServerLevel level, BlockPos seed) {
        Set<BlockPos> members = collectConnected(level, seed, null);
        if (members.isEmpty()) return;
        List<CratePartition> partitions = AviationCrateStructureFinder.partition(members,
                pos -> placementFacing(level, pos));
        Map<UUID, Set<BlockPos>> oldStructures = new HashMap<>();
        for (BlockPos pos : members) {
            if (level.getBlockEntity(pos) instanceof AviationCrateBlockEntity crate) {
                oldStructures.computeIfAbsent(crate.structureId(), id -> new HashSet<>()).add(pos);
            }
        }
        List<CratePartition> changed = new ArrayList<>();
        Set<BlockPos> changedMembers = new HashSet<>();
        for (CratePartition partition : partitions) {
            UUID oldId = level.getBlockEntity(partition.controller()) instanceof AviationCrateBlockEntity crate
                    ? crate.structureId() : null;
            if (oldId != null && partition.members().equals(oldStructures.get(oldId))) continue;
            changed.add(partition);
            changedMembers.addAll(partition.members());
        }
        if (changed.isEmpty()) return;
        List<CreatureRecord> records = collectRecords(level, changedMembers);
        List<ItemTransfer> items = collectItems(level, changedMembers);
        apply(level, changed, records, items, seed);
    }

    public static void beforeRemoval(ServerLevel level, BlockPos removed) {
        Set<BlockPos> oldMembers = collectConnected(level, removed, null);
        if (oldMembers.isEmpty()) return;
        List<CreatureRecord> records = collectRecords(level, oldMembers);
        List<ItemTransfer> items = collectItems(level, oldMembers);
        oldMembers.remove(removed);
        List<CratePartition> partitions = AviationCrateStructureFinder.partition(oldMembers,
                pos -> placementFacing(level, pos));
        apply(level, partitions, records, items, removed);
    }

    /** Used by the optional Aeronautics bridge to reject a partial logical crate selection. */
    public static boolean containsOnlyWholeStructures(ServerLevel level, Iterable<BlockPos> selectedMembers) {
        Set<BlockPos> selected = new HashSet<>();
        for (BlockPos pos : selectedMembers) selected.add(pos.immutable());
        for (BlockPos pos : selected) {
            if (!(level.getBlockEntity(pos) instanceof AviationCrateBlockEntity)) continue;
            if (!selected.containsAll(collectConnected(level, pos, null))) return false;
        }
        return true;
    }

    private static void apply(ServerLevel level, List<CratePartition> partitions, List<CreatureRecord> records,
                              List<ItemTransfer> items, BlockPos dropPos) {
        List<CrateSize> sizes = partitions.stream().map(CratePartition::size).toList();
        SplitAllocation allocation = CrateSplitAllocator.allocate(sizes, records);
        for (int index = 0; index < partitions.size(); index++) {
            CratePartition partition = partitions.get(index);
            UUID id = UUID.randomUUID();
            int extentX = 1;
            int extentZ = 1;
            for (BlockPos member : partition.members()) {
                extentX = Math.max(extentX, member.getX() - partition.controller().getX() + 1);
                extentZ = Math.max(extentZ, member.getZ() - partition.controller().getZ() + 1);
            }
            for (BlockPos member : partition.members()) {
                if (level.getBlockEntity(member) instanceof AviationCrateBlockEntity crate) {
                    crate.setLogicalStructure(id, partition.controller(), partition.size(), extentX, extentZ);
                    // Structure membership changed; sync and rebuild sections immediately
                    // instead of waiting for the periodic sync poll.
                    level.sendBlockUpdated(member, level.getBlockState(member), level.getBlockState(member), 3);
                }
            }
            if (level.getBlockEntity(partition.controller()) instanceof AviationCrateBlockEntity controller) {
                controller.addAllRecords(allocation.recordsByStructure().get(index));
            }
        }
        distributeItems(level, partitions, items, dropPos);
        CreatureReleaseService.releaseOrDrop(level, dropPos, allocation.ejectedAsEntities());
    }

    private static void distributeItems(ServerLevel level, List<CratePartition> partitions, List<ItemTransfer> items, BlockPos dropPos) {
        if (partitions.isEmpty()) {
            for (ItemTransfer item : items) Containers.dropItemStack(level, dropPos.getX(), dropPos.getY(), dropPos.getZ(), item.stack());
            return;
        }
        List<CratePartition> byVolume = partitions.stream()
                .sorted(Comparator.comparingInt((CratePartition partition) -> partition.size().volume()).reversed())
                .toList();
        for (ItemTransfer transfer : items) {
            ItemStack remainder = transfer.stack();
            CratePartition preferred = null;
            for (CratePartition partition : partitions) {
                if (!partition.members().contains(transfer.originController())) continue;
                preferred = partition;
                break;
            }
            if (preferred != null) remainder = insertInto(level, preferred, transfer, remainder);
            for (CratePartition partition : byVolume) {
                if (remainder.isEmpty()) break;
                if (partition == preferred) continue;
                remainder = insertInto(level, partition, transfer, remainder);
            }
            if (!remainder.isEmpty()) {
                BlockPos dropAt = preferred != null ? transfer.originController() : dropPos;
                Containers.dropItemStack(level, dropAt.getX(), dropAt.getY(), dropAt.getZ(), remainder);
            }
        }
    }

    private static ItemStack insertInto(ServerLevel level, CratePartition partition, ItemTransfer transfer, ItemStack stack) {
        if (level.getBlockEntity(partition.controller()) instanceof AviationCrateBlockEntity controller) {
            return transfer.channel() == ItemChannel.OUTPUT
                    ? ItemHandlerHelper.insertItemStacked(controller.outputInventory(), stack, false)
                    : insertFixtureOrAutomation(controller, stack);
        }
        return stack;
    }

    private static ItemStack insertFixtureOrAutomation(AviationCrateBlockEntity controller, ItemStack stack) {
        if (stack.is(ACItems.TRADING_BED_FIXTURE.get())) {
            return controller.fixtureInventory().insertItem(0, stack, false);
        }
        if (stack.is(ACItems.TRADING_WORKSTATION_FIXTURE.get())) {
            return controller.fixtureInventory().insertItem(1, stack, false);
        }
        if (stack.is(ACItems.TRADING_DISPLAY_FIXTURE.get())) {
            return controller.fixtureInventory().insertItem(2, stack, false);
        }
        return ItemHandlerHelper.insertItemStacked(controller.automationInventory(), stack, false);
    }

    private static List<CreatureRecord> collectRecords(ServerLevel level, Set<BlockPos> members) {
        List<CreatureRecord> records = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (BlockPos pos : members) {
            if (level.getBlockEntity(pos) instanceof AviationCrateBlockEntity crate) {
                for (CreatureRecord record : crate.takeAllRecords()) {
                    if (seen.add(record.recordId())) records.add(record);
                }
            }
        }
        return records;
    }

    private static List<ItemTransfer> collectItems(ServerLevel level, Set<BlockPos> members) {
        List<ItemTransfer> items = new ArrayList<>();
        for (BlockPos pos : members) {
            if (!(level.getBlockEntity(pos) instanceof AviationCrateBlockEntity crate)) continue;
            BlockPos origin = crate.controller().getBlockPos().immutable();
            for (int slot = 0; slot < crate.feedInventory().getSlots(); slot++) {
                ItemStack stack = crate.feedInventory().extractItem(slot, Integer.MAX_VALUE, false);
                if (!stack.isEmpty()) items.add(new ItemTransfer(stack, ItemChannel.INPUT, origin));
            }
            for (int slot = 0; slot < crate.outputInventory().getSlots(); slot++) {
                ItemStack stack = crate.outputInventory().extractItem(slot, Integer.MAX_VALUE, false);
                if (!stack.isEmpty()) items.add(new ItemTransfer(stack, ItemChannel.OUTPUT, origin));
            }
            for (int slot = 0; slot < crate.fixtureInventory().getSlots(); slot++) {
                ItemStack stack = crate.fixtureInventory().extractItem(slot, Integer.MAX_VALUE, false);
                if (!stack.isEmpty()) items.add(new ItemTransfer(stack, ItemChannel.FIXTURE, origin));
            }
        }
        return items;
    }

    private enum ItemChannel { INPUT, OUTPUT, FIXTURE }

    private static Direction placementFacing(ServerLevel level, BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        return state.hasProperty(com.llqqrr.aircrate.content.block.AviationCrateBlock.PLACEMENT_FACING)
                ? state.getValue(com.llqqrr.aircrate.content.block.AviationCrateBlock.PLACEMENT_FACING)
                : Direction.NORTH;
    }

    private record ItemTransfer(ItemStack stack, ItemChannel channel, BlockPos originController) { }

    private static Set<BlockPos> collectConnected(ServerLevel level, BlockPos seed, BlockPos excluded) {
        Set<BlockPos> found = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed);
        while (!queue.isEmpty() && found.size() < SCAN_LIMIT) {
            BlockPos pos = queue.removeFirst();
            if (pos.equals(excluded) || found.contains(pos)
                    || !(level.getBlockEntity(pos) instanceof AviationCrateBlockEntity)) continue;
            found.add(pos.immutable());
            for (Direction direction : Direction.values()) queue.addLast(pos.relative(direction));
        }
        return found;
    }
}

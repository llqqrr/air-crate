package com.llqqrr.aircrate.content.packager;

import com.llqqrr.aircrate.content.block.AviationCrateBlockEntity;
import com.llqqrr.aircrate.content.creature.CreatureParcelItem;
import com.llqqrr.aircrate.content.creature.CreatureRecord;
import com.llqqrr.aircrate.registry.ACBlockEntities;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A native Create packager whose target inventory is the creature list of an aviation crate. */
public final class CreaturePackagerBlockEntity extends PackagerBlockEntity {
    public CreaturePackagerBlockEntity(BlockPos pos, BlockState state) {
        super(ACBlockEntities.CREATURE_PACKAGER.get(), pos, state);
    }

    public ItemStack acceptFromFrogport(ItemStack stack, boolean simulate) {
        return inventory.insertItem(0, stack, simulate);
    }

    public ItemStack takeForFrogport(boolean simulate) {
        return inventory.extractItem(0, 1, simulate);
    }

    @Override
    public boolean unwrapBox(ItemStack box, boolean simulate) {
        CreatureRecord record = CreatureParcelItem.record(box);
        AviationCrateBlockEntity crate = targetCrate();
        if (record == null || crate == null || animationTicks > 0
                || !crate.canAcceptRecordFromHatch(record)) {
            return false;
        }
        if (simulate) return true;
        if (!crate.addRecord(record)) return false;
        previouslyUnwrapped = box.copyWithCount(1);
        animationInward = true;
        animationTicks = CYCLE;
        notifyUpdate();
        return true;
    }

    @Override
    public InventorySummary getAvailableItems() {
        InventorySummary summary = new InventorySummary();
        AviationCrateBlockEntity crate = targetCrate();
        if (crate == null) return summary;

        Map<String, CreatureRecord> representative = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (CreatureRecord record : crate.records()) {
            representative.putIfAbsent(record.entityType(), record);
            counts.merge(record.entityType(), 1, Integer::sum);
        }
        representative.forEach((type, record) -> summary.add(CreatureParcelItem.containing(record), counts.get(type)));
        return summary;
    }

    @Override
    public void attemptToSend(List<PackagingRequest> requests) {
        if (requests == null) {
            packageFirstCreature();
            return;
        }
        if (requests.isEmpty()) return;
        AviationCrateBlockEntity crate = targetCrate();
        if (crate == null) return;

        while (!requests.isEmpty()) {
            PackagingRequest request = requests.getFirst();
            CreatureRecord requested = CreatureParcelItem.record(request.item());
            if (requested == null) {
                requests.removeFirst();
                continue;
            }
            CreatureRecord stored = crate.records().stream()
                    .filter(record -> record.entityType().equals(requested.entityType()))
                    .findFirst().orElse(null);
            if (stored == null) {
                requests.removeFirst();
                continue;
            }

            ItemStack parcel = CreatureParcelItem.containing(stored);
            PackageItem.clearAddress(parcel);
            if (!request.address().isBlank()) PackageItem.addAddress(parcel, request.address());
            if (!crate.removeRecord(stored.recordId())) return;
            int packageIndex = request.packageCounter().getAndIncrement();
            request.subtract(1);
            boolean requestFinished = request.isEmpty();
            if (requestFinished) requests.removeFirst();
            boolean finalPackageAtLink = requestFinished && requests.isEmpty();
            PackageItem.setOrder(parcel, request.orderId(), request.linkIndex(), request.finalLink().booleanValue(),
                    packageIndex, finalPackageAtLink, request.context());
            queueParcel(parcel);
        }
        triggerStockCheck();
        notifyUpdate();
    }

    private void packageFirstCreature() {
        if (!heldBox.isEmpty() || animationTicks != 0 || buttonCooldown > 0) return;
        AviationCrateBlockEntity crate = targetCrate();
        if (crate == null || crate.records().isEmpty()) return;
        CreatureRecord record = crate.records().getFirst();
        ItemStack parcel = CreatureParcelItem.containing(record);
        PackageItem.clearAddress(parcel);
        if (!signBasedAddress.isBlank()) PackageItem.addAddress(parcel, signBasedAddress);
        if (!crate.removeRecord(record.recordId())) return;
        queueParcel(parcel);
        triggerStockCheck();
        notifyUpdate();
    }

    private void queueParcel(ItemStack parcel) {
        if (heldBox.isEmpty() && animationTicks == 0) {
            heldBox = parcel;
            animationInward = false;
            animationTicks = CYCLE;
        } else {
            queuedExitingPackages.add(new BigItemStack(parcel, 1));
        }
    }

    /** Server-side crate lookup used by unwrapping and by interaction diagnostics. */
    public AviationCrateBlockEntity targetCrate() {
        if (level == null) return null;
        Direction facing = getBlockState().getOptionalValue(PackagerBlock.FACING).orElse(Direction.UP);
        if (!(level.getBlockEntity(worldPosition.relative(facing.getOpposite()))
                instanceof AviationCrateBlockEntity member)) {
            return null;
        }
        return member.controller();
    }
}

package com.llqqrr.aircrate.content.frogport;

import com.llqqrr.aircrate.content.creature.CreatureCaptureService;
import com.llqqrr.aircrate.content.creature.CreatureFilterData;
import com.llqqrr.aircrate.content.creature.CreatureFilterItem;
import com.llqqrr.aircrate.content.creature.CreatureParcelItem;
import com.llqqrr.aircrate.content.creature.CreatureRecord;
import com.llqqrr.aircrate.content.creature.CreatureReleaseService;
import com.llqqrr.aircrate.content.packager.CreaturePackagerBlockEntity;
import com.llqqrr.aircrate.content.resonator.AmethystSculkResonatorBlockEntity;
import com.llqqrr.aircrate.registry.ACBlockEntities;
import com.llqqrr.aircrate.registry.ACItems;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportSounds;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Comparator;
import java.util.List;

/** Create frogport animation/state with creature capture and release behavior. */
public final class CreatureFrogportBlockEntity extends FrogportBlockEntity {
    public enum Mode { CAPTURE, RELEASE }

    private Mode mode = Mode.CAPTURE;
    private CreatureAreaSelection selection;
    private ItemStack filter = ItemStack.EMPTY;
    private int workCooldown;
    private boolean releaseAnimation;
    private CreatureRecord pendingRelease;
    private BlockPos pendingReleasePos;
    private boolean clientAnimationPlaying;
    private float clientAnimationProgress;
    private BlockPos boundResonator;
    private int resonatorSearchCooldown;
    private final FrogportSounds creatureSounds = new FrogportSounds();

    private final IItemHandler creatureAutomation = new IItemHandler() {
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? inventory.getStackInSlot(0).copy() : ItemStack.EMPTY;
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || mode != Mode.RELEASE || !CreatureParcelItem.isCreatureParcel(stack)
                    || !filterPermits(CreatureParcelItem.record(stack).entityType())) return stack;
            return insertIntoCreatureSlot(stack, simulate);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0 || mode != Mode.CAPTURE) return ItemStack.EMPTY;
            return inventory.extractItem(0, amount, simulate);
        }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && mode == Mode.RELEASE && CreatureParcelItem.isCreatureParcel(stack);
        }
    };

    public CreatureFrogportBlockEntity(BlockPos pos, BlockState state) {
        super(ACBlockEntities.CREATURE_FROGPORT.get(), pos, state);
    }

    public Mode mode() { return mode; }
    public CreatureAreaSelection selection() { return selection; }
    public ItemStack filter() { return filter.copy(); }
    public IItemHandler automation() { return creatureAutomation; }

    public void setMode(Mode newMode) {
        mode = newMode == null ? Mode.CAPTURE : newMode;
        changed();
    }

    public void toggleMode() {
        setMode(mode == Mode.CAPTURE ? Mode.RELEASE : Mode.CAPTURE);
    }

    public void setSelection(CreatureAreaSelection newSelection) {
        selection = newSelection != null && newSelection.complete() && newSelection.validSize()
                && newSelection.inRangeOf(worldPosition) ? newSelection : null;
        changed();
    }

    public void clearSelection() {
        selection = null;
        changed();
    }

    public void setFilter(ItemStack stack) {
        filter = stack.is(ACItems.CREATURE_FILTER.get()) ? stack.copyWithCount(1) : ItemStack.EMPTY;
        changed();
    }

    public ItemStack insertParcel(ItemStack stack, boolean simulate) {
        return creatureAutomation.insertItem(0, stack, simulate);
    }

    /** The generic container guard cannot identify the owner of Create's SmartInventory. */
    private ItemStack insertIntoCreatureSlot(ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !inventory.getStackInSlot(0).isEmpty()) return stack;
        ItemStack remainder = stack.copyWithCount(stack.getCount() - 1);
        if (!simulate) {
            inventory.setStackInSlot(0, stack.copyWithCount(1));
            notifyUpdate();
        }
        return remainder;
    }

    private boolean filterPermits(String entityType) {
        if (filter.isEmpty()) return true;
        CreatureFilterData data = CreatureFilterItem.data(filter);
        return data.permits(entityType);
    }

    @Override
    public void tick() {
        // FrogportBlockEntity.tick() assumes a normal package-port target and dereferences it
        // during the animation. Creature frogports intentionally have no package-port target,
        // so drive the shared animation fields without entering that logistics code path.
        if (tickCreatureAnimation() && releaseAnimation) finishRelease();
    }

    private boolean tickCreatureAnimation() {
        boolean animationPlaying = isAnimationInProgress();
        if (level != null && level.isClientSide && animationPlaying && !clientAnimationPlaying) {
            creatureSounds.open(level, worldPosition);
            if (currentlyDepositing) creatureSounds.depositPackage(level, worldPosition);
            else creatureSounds.catchPackage(level, worldPosition);
            clientAnimationProgress = 0;
        }
        clientAnimationPlaying = animationPlaying;
        if (anticipationProgress.getValue() == 1)
            anticipationProgress.startWithValue(0);
        manualOpenAnimationProgress.updateChaseTarget(openTracker.openCount > 0 ? 1 : 0);
        anticipationProgress.tickChaser();
        manualOpenAnimationProgress.tickChaser();
        animationProgress.tickChaser();
        float progress = animationProgress.getValue();
        if (level != null && level.isClientSide && currentlyDepositing
                && clientAnimationProgress < .2f && progress >= .2f) {
            BlockPos soundPos = pendingReleasePos == null ? worldPosition : pendingReleasePos;
            level.playLocalSound(soundPos, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS,
                    .25f, 1.2f, false);
        }
        clientAnimationProgress = progress;
        if (progress < 1) return false;
        if (level != null && level.isClientSide && clientAnimationPlaying)
            creatureSounds.close(level, worldPosition);
        clientAnimationPlaying = false;
        clientAnimationProgress = 0;
        anticipationProgress.startWithValue(0);
        animationProgress.startWithValue(0);
        animatedPackage = null;
        currentlyDepositing = false;
        return true;
    }

    private void finishRelease() {
        if (level != null && !level.isClientSide && pendingRelease != null
                && CreatureReleaseService.release((ServerLevel) level,
                pendingReleasePos == null ? worldPosition : pendingReleasePos, pendingRelease)) {
            inventory.extractItem(0, 1, false);
        }
        releaseAnimation = false;
        pendingRelease = null;
        pendingReleasePos = null;
        if (level != null && !level.isClientSide) notifyUpdate();
    }

    public static void tickServer(Level level, BlockPos pos, BlockState state,
                                  CreatureFrogportBlockEntity frogport) {
        frogport.tick();
        if (!(level instanceof ServerLevel serverLevel)) return;
        boolean active = frogport.selection != null && !level.hasNeighborSignal(pos)
                && frogport.operationalResonator(serverLevel) != null;
        if (state.getValue(CreatureFrogportBlock.ACTIVE) != active)
            level.setBlock(pos, state.setValue(CreatureFrogportBlock.ACTIVE, active), 3);
        if (!active || ++frogport.workCooldown < 20) return;
        frogport.workCooldown = 0;
        if (frogport.mode == Mode.CAPTURE) frogport.capture(serverLevel);
        else frogport.release(serverLevel);
    }

    private AmethystSculkResonatorBlockEntity operationalResonator(ServerLevel level) {
        if (boundResonator != null) {
            if (!level.isLoaded(boundResonator)) return null;
            if (level.getBlockEntity(boundResonator) instanceof AmethystSculkResonatorBlockEntity resonator)
                return resonator.operational() ? resonator : null;
            boundResonator = null;
        }
        if (++resonatorSearchCooldown < 20) return null;
        resonatorSearchCooldown = 0;
        AmethystSculkResonatorBlockEntity found =
                AmethystSculkResonatorBlockEntity.findOperational(level, worldPosition);
        boundResonator = found == null ? null : found.getBlockPos();
        return found;
    }

    private void capture(ServerLevel level) {
        if (selection == null || !inventory.getStackInSlot(0).isEmpty()
                || releaseAnimation || animationProgress.getChaseTarget() == 1) return;
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, selection.bounds(),
                entity -> CreatureCaptureService.isSupported(entity)
                        && filterPermits(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .getKey(entity.getType()).toString()));
        LivingEntity target = candidates.stream().min(Comparator.comparingDouble(
                entity -> entity.distanceToSqr(worldPosition.getCenter()))).orElse(null);
        if (target == null) return;
        ItemStack created = CreatureCaptureService.asParcel(target);
        if (created.isEmpty()) return;
        ItemStack remainder = created.copyWithCount(1);
        if (level.getBlockEntity(worldPosition.below()) instanceof CreaturePackagerBlockEntity packager)
            remainder = packager.acceptFromFrogport(remainder, false);
        if (!remainder.isEmpty()) remainder = insertIntoCreatureSlot(remainder, false);
        if (!remainder.isEmpty()) return;
        target.discard();
        startAnimation(created, false);
        notifyUpdate();
        level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.ENTITY_PLACE, worldPosition,
                net.minecraft.world.level.gameevent.GameEvent.Context.of(getBlockState()));
    }

    private void release(ServerLevel level) {
        if (selection == null || releaseAnimation || animationProgress.getChaseTarget() == 1) return;
        if (inventory.getStackInSlot(0).isEmpty()
                && level.getBlockEntity(worldPosition.below()) instanceof CreaturePackagerBlockEntity packager) {
            ItemStack incoming = packager.takeForFrogport(true);
            if (!incoming.isEmpty() && insertIntoCreatureSlot(incoming, true).isEmpty()) {
                ItemStack extracted = packager.takeForFrogport(false);
                ItemStack remainder = insertIntoCreatureSlot(extracted, false);
                if (!remainder.isEmpty()) packager.acceptFromFrogport(remainder, false);
            }
        }
        ItemStack parcel = inventory.getStackInSlot(0);
        CreatureRecord record = CreatureParcelItem.record(parcel);
        if (record == null || !filterPermits(record.entityType())) return;
        BlockPos min = selection.min();
        BlockPos max = selection.max();
        BlockPos releasePos = new BlockPos((min.getX() + max.getX()) / 2, min.getY(),
                (min.getZ() + max.getZ()) / 2);
        if (CreatureReleaseService.create(level, record) == null) return;
        pendingRelease = record;
        pendingReleasePos = releasePos;
        releaseAnimation = true;
        animatedPackage = parcel.copyWithCount(1);
        currentlyDepositing = true;
        animationProgress.startWithValue(0);
        animationProgress.chase(1, .1, Chaser.LINEAR);
        notifyUpdate();
        if (level.isClientSide) {
            creatureSounds.open(level, worldPosition);
            creatureSounds.depositPackage(level, worldPosition);
        }
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString("CreatureMode", mode.name());
        if (selection != null) {
            tag.putLong("SelectionFirst", selection.first().asLong());
            tag.putLong("SelectionSecond", selection.second().asLong());
        }
        if (!filter.isEmpty()) tag.put("CreatureFilter", filter.save(registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        String storedMode = tag.contains("CreatureMode") ? tag.getString("CreatureMode") : tag.getString("Mode");
        try { mode = Mode.valueOf(storedMode); }
        catch (IllegalArgumentException ignored) { mode = Mode.CAPTURE; }
        selection = tag.contains("SelectionFirst") && tag.contains("SelectionSecond")
                ? new CreatureAreaSelection(BlockPos.of(tag.getLong("SelectionFirst")),
                BlockPos.of(tag.getLong("SelectionSecond"))) : null;
        filter = tag.contains("CreatureFilter")
                ? ItemStack.parseOptional(registries, tag.getCompound("CreatureFilter"))
                : tag.contains("Filter") ? ItemStack.parseOptional(registries, tag.getCompound("Filter"))
                : ItemStack.EMPTY;
        if (inventory.getStackInSlot(0).isEmpty() && tag.contains("Parcel")) {
            inventory.setStackInSlot(0, ItemStack.parseOptional(registries, tag.getCompound("Parcel")));
        }
        // FrogportBlockEntity.read() normally defers this package to its own tick(). This
        // frogport uses a target-free animation tick, so initialize the shared animation
        // fields here when the server packet arrives instead.
        if (clientPacket && tag.contains("AnimatedPackage")) {
            animatedPackage = ItemStack.parseOptional(registries, tag.getCompound("AnimatedPackage"));
            currentlyDepositing = tag.getBoolean("Deposit");
            animationProgress.startWithValue(0);
            animationProgress.chase(1, .1, Chaser.LINEAR);
            clientAnimationPlaying = false;
            clientAnimationProgress = 0;
        }
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet,
                                       HolderLookup.Provider registries) {
        if (packet.getTag() != null) read(packet.getTag(), registries, true);
    }
}

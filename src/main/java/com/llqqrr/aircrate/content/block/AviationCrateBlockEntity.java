package com.llqqrr.aircrate.content.block;

import com.llqqrr.aircrate.content.creature.CreatureRecord;
import com.llqqrr.aircrate.content.creature.BreedingProcessor;
import com.llqqrr.aircrate.content.creature.CreatureGrowthProcessor;
import com.llqqrr.aircrate.content.structure.CapacityResult;
import com.llqqrr.aircrate.content.structure.CrateSize;
import com.llqqrr.aircrate.content.structure.CreatureCapacitySolver;
import com.llqqrr.aircrate.content.villager.TradeOfferSnapshot;
import com.llqqrr.aircrate.content.villager.TradingProcessor;
import com.llqqrr.aircrate.registry.ACBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

/** State for a logical crate controller. Member redirection is added by the structure service. */
public final class AviationCrateBlockEntity extends BlockEntity {
    private final ItemStackHandler feed = new ItemStackHandler(9) {
        @Override protected void onContentsChanged(int slot) { changed(); }
    };
    private final ItemStackHandler output = new ItemStackHandler(9) {
        @Override protected void onContentsChanged(int slot) { changed(); }
    };
    private final ItemStackHandler fixtures = new ItemStackHandler(3) {
        @Override protected void onContentsChanged(int slot) { changed(); }
    };
    private final IItemHandler automationInventory = new AviationCrateInventory(feed, output);
    private final List<CreatureRecord> records = new ArrayList<>();

    private UUID structureId = UUID.randomUUID();
    private long structureVersion;
    private CrateSize size = new CrateSize(1, 1, 1);
    // World-axis extents of the structure box; CrateSize is normalized (width<=length)
    // for capacity logic, so rendering must not derive orientation from it.
    private int extentX = 1;
    private int extentZ = 1;
    private BlockPos controllerPos = worldPosition;
    private boolean breedingEnabled;
    private int breedingRate = 50;
    private int tradeRate = 100;
    private int tradeProgress;
    private int breedingProgress;
    private boolean syncPending;
    private CrateMode crateMode = CrateMode.GLASS;

    public AviationCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ACBlockEntities.AVIATION_CRATE.get(), pos, state);
    }

    public CrateMode crateMode() {
        AviationCrateBlockEntity controller = controller();
        return controller == this ? crateMode : controller.crateMode();
    }

    /** Server-side wrench toggle: cycles the wall mode and refreshes every member section. */
    public void cycleCrateMode() {
        crateMode = crateMode.next();
        changed();
        if (level == null || level.isClientSide) return;
        int ex = linkedCount(Direction.EAST) + 1;
        int ey = linkedCount(Direction.UP) + 1;
        int ez = linkedCount(Direction.SOUTH) + 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < ex; dx++) for (int dy = 0; dy < ey; dy++) for (int dz = 0; dz < ez; dz++) {
            cursor.set(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
            BlockState memberState = level.getBlockState(cursor);
            if (memberState.getBlock() instanceof AviationCrateBlock) {
                level.sendBlockUpdated(cursor, memberState, memberState, 3);
            }
        }
    }

    @Override
    public net.neoforged.neoforge.client.model.data.ModelData getModelData() {
        if (level == null) return net.neoforged.neoforge.client.model.data.ModelData.EMPTY;
        return net.neoforged.neoforge.client.model.data.ModelData.builder()
                .with(CrateRenderData.KEY, computeRenderData()).build();
    }

    private CrateRenderData computeRenderData() {
        BlockState state = getBlockState();
        Direction outputDir = state.getValue(AviationCrateBlock.OUTPUT_FACING);
        boolean outputOpen = state.getValue(AviationCrateBlock.OUTPUT_OPEN);
        CrateMode mode = crateMode();
        // The controller sits at the structure's minimum corner; extents are +x/+y/+z.
        AviationCrateBlockEntity controller = controller();
        BlockPos base = controller.worldPosition;
        int width = controller.extentX;
        int height = controller.size.height();
        int length = controller.extentZ;
        int x = worldPosition.getX() - base.getX();
        int y = worldPosition.getY() - base.getY();
        int z = worldPosition.getZ() - base.getZ();
        // The vent end is always the side the placement facing points at; the
        // structure finder now guarantees the facing axis is the logical length
        // axis, so geometry no longer overrides the player's chosen orientation.
        Direction ventDir = controller.getBlockState().getValue(AviationCrateBlock.PLACEMENT_FACING);
        Direction panelDir = ventDir.getOpposite();
        EnumMap<Direction, CrateRenderData.Face> faces = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof AviationCrateBlockEntity neighbor
                    && neighbor.structureId().equals(structureId)) {
                faces.put(direction, CrateRenderData.Face.HIDDEN);
                continue;
            }
            if (outputOpen && direction == outputDir) {
                faces.put(direction, CrateRenderData.Face.OUTPUT);
                continue;
            }
            int u;
            int v;
            int faceWidth;
            int faceHeight;
            switch (direction) {
                case NORTH -> { u = width - 1 - x; v = height - 1 - y; faceWidth = width; faceHeight = height; }
                case SOUTH -> { u = x; v = height - 1 - y; faceWidth = width; faceHeight = height; }
                case WEST -> { u = z; v = height - 1 - y; faceWidth = length; faceHeight = height; }
                case EAST -> { u = length - 1 - z; v = height - 1 - y; faceWidth = length; faceHeight = height; }
                case UP -> { u = x; v = z; faceWidth = width; faceHeight = length; }
                default -> { u = x; v = length - 1 - z; faceWidth = width; faceHeight = length; }
            }
            int mask = (u == 0 ? 1 : 0) | (u == faceWidth - 1 ? 2 : 0)
                    | (v == 0 ? 4 : 0) | (v == faceHeight - 1 ? 8 : 0);
            if (direction == ventDir && u == 0 && v == 0) {
                faces.put(direction, new CrateRenderData.Face(CrateRenderData.Kind.VENT, mask));
            } else if (direction == ventDir || direction == panelDir) {
                faces.put(direction, new CrateRenderData.Face(CrateRenderData.Kind.PANEL, mask));
            } else {
                faces.put(direction, new CrateRenderData.Face(CrateRenderData.Kind.MODE, mask));
            }
        }
        return new CrateRenderData(mode, faces);
    }

    /** Counts consecutive same-structure crate blocks in the given direction. */
    private int linkedCount(Direction direction) {
        int count = 0;
        BlockPos.MutableBlockPos cursor = worldPosition.mutable();
        while (count < CrateSize.MAX_LENGTH + 2) {
            cursor.move(direction);
            if (level.getBlockEntity(cursor) instanceof AviationCrateBlockEntity neighbor
                    && neighbor.structureId().equals(structureId)) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    public IItemHandler automationInventory() {
        if (level != null && !controllerPos.equals(worldPosition) && level.isLoaded(controllerPos)
                && level.getBlockEntity(controllerPos) instanceof AviationCrateBlockEntity controller) {
            return controller.localAutomationInventory();
        }
        return automationInventory;
    }

    IItemHandler localAutomationInventory() {
        return automationInventory;
    }

    public List<CreatureRecord> records() {
        return List.copyOf(records);
    }

    public CrateSize size() {
        return size;
    }

    public UUID structureId() {
        return structureId;
    }

    public long structureVersion() { return structureVersion; }

    public BlockPos controllerPos() {
        return controllerPos;
    }

    public AviationCrateBlockEntity controller() {
        if (level != null && !controllerPos.equals(worldPosition) && level.isLoaded(controllerPos)
                && level.getBlockEntity(controllerPos) instanceof AviationCrateBlockEntity controller) {
            return controller;
        }
        return this;
    }

    public boolean breedingEnabled() {
        return breedingEnabled;
    }

    public void setBreedingEnabled(boolean enabled) {
        breedingEnabled = enabled;
        changed();
    }

    public int breedingRate() { return breedingRate; }
    public int tradeRate() { return tradeRate; }
    public int tradeProgress() { return tradeProgress; }
    public int breedingProgress() { return breedingProgress; }
    public void setBreedingRate(int value) { breedingRate = Math.clamp(value, 0, 100); changed(); }
    public void setTradeRate(int value) { tradeRate = Math.clamp(value, 0, 100); changed(); }
    /** Advances the Create-style rate control clock; returns true when a trade may run. */
    public boolean advanceTradeClock() {
        if (tradeRate <= 0 || records.isEmpty()) return false;
        tradeProgress = Math.min(1000, tradeProgress + tradeRate);
        if (tradeProgress < 1000) {
            changed();
            return false;
        }
        tradeProgress -= 1000;
        changed();
        return true;
    }

    public boolean advanceBreedingClock() {
        if (!breedingEnabled || breedingRate <= 0 || records.isEmpty()) return false;
        breedingProgress = Math.min(1000, breedingProgress + breedingRate);
        if (breedingProgress < 1000) {
            changed();
            return false;
        }
        breedingProgress -= 1000;
        changed();
        return true;
    }

    public void setLogicalStructure(UUID id, BlockPos controller, CrateSize newSize) {
        setLogicalStructure(id, controller, newSize, newSize.width(), newSize.length());
    }

    public void setLogicalStructure(UUID id, BlockPos controller, CrateSize newSize, int worldExtentX,
                                    int worldExtentZ) {
        structureId = id;
        controllerPos = controller.immutable();
        size = newSize;
        extentX = Math.max(1, worldExtentX);
        extentZ = Math.max(1, worldExtentZ);
        structureVersion++;
        changed();
    }

    public CapacityResult capacity() {
        return CreatureCapacitySolver.solve(size, records);
    }

    public boolean addRecord(CreatureRecord record) {
        if (!canAcceptRecord(record)) return false;
        records.add(record);
        changed();
        return true;
    }

    private boolean canAcceptRecord(CreatureRecord record) {
        if (!CreatureCapacitySolver.canIndividuallyFit(size, record)) return false;
        List<CreatureRecord> candidate = new ArrayList<>(records);
        candidate.add(record);
        CapacityResult result = CreatureCapacitySolver.solve(size, candidate);
        return result.retainedOverfull().isEmpty() && result.cannotEverFit().isEmpty();
    }

    public boolean canAcceptRecordFromHatch(CreatureRecord record) {
        return canAcceptRecord(record);
    }

    public boolean removeRecord(UUID recordId) {
        boolean removed = records.removeIf(record -> record.recordId().equals(recordId));
        if (removed) changed();
        return removed;
    }

    public List<CreatureRecord> takeAllRecords() {
        List<CreatureRecord> result = List.copyOf(records);
        records.clear();
        changed();
        return result;
    }

    public void addAllRecords(List<CreatureRecord> added) {
        records.addAll(added);
        changed();
    }

    public void replaceRecord(CreatureRecord updated) {
        for (int index = 0; index < records.size(); index++) {
            if (!records.get(index).recordId().equals(updated.recordId())) continue;
            records.set(index, updated);
            changed();
            return;
        }
    }

    public void clearLocalState() {
        records.clear();
        for (int i = 0; i < feed.getSlots(); i++) feed.setStackInSlot(i, ItemStack.EMPTY);
        for (int i = 0; i < output.getSlots(); i++) output.setStackInSlot(i, ItemStack.EMPTY);
        for (int i = 0; i < fixtures.getSlots(); i++) fixtures.setStackInSlot(i, ItemStack.EMPTY);
        changed();
    }

    public ItemStackHandler feedInventory() {
        return feed;
    }

    public ItemStackHandler outputInventory() {
        return output;
    }

    public ItemStackHandler fixtureInventory() {
        return fixtures;
    }

    public static void tickServer(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                                  AviationCrateBlockEntity crate) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        if (crate.syncPending && serverLevel.getGameTime() % 20L == 0L) {
            crate.syncPending = false;
            level.sendBlockUpdated(pos, state, state, 3);
        }
        if (serverLevel.getGameTime() % 10L != 0L) return;
        AviationCrateBlockEntity controller = crate.controller();
        if (controller == crate) {
            CreatureGrowthProcessor.tick(crate, 10L);
            if (crate.advanceBreedingClock()) BreedingProcessor.tick(crate);
            if (crate.advanceTradeClock()) TradingProcessor.tick(crate);
        }
        if (net.neoforged.fml.ModList.get().isLoaded("create")) {
            com.llqqrr.aircrate.compat.create.CrateOutputPort.tryOutput(serverLevel, crate, controller);
        }
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) syncPending = true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Feed", feed.serializeNBT(registries));
        tag.put("Output", output.serializeNBT(registries));
        tag.put("Fixtures", fixtures.serializeNBT(registries));
        tag.putUUID("StructureId", structureId);
        tag.putLong("ControllerPos", controllerPos.asLong());
        tag.putLong("StructureVersion", structureVersion);
        tag.putInt("Width", size.width());
        tag.putInt("Height", size.height());
        tag.putInt("Length", size.length());
        tag.putInt("ExtentX", extentX);
        tag.putInt("ExtentZ", extentZ);
        tag.putBoolean("Breeding", breedingEnabled);
        tag.putInt("BreedingRate", breedingRate);
        tag.putInt("TradeRate", tradeRate);
        tag.putInt("TradeProgress", tradeProgress);
        tag.putInt("BreedingProgress", breedingProgress);
        tag.putString("CrateMode", crateMode.getSerializedName());
        tag.put("Records", writeRecords(registries));
    }

    private ListTag writeRecords(HolderLookup.Provider registries) {
        ListTag serializedRecords = new ListTag();
        for (CreatureRecord record : records) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", record.recordId());
            entry.putString("EntityType", record.entityType());
            entry.putString("ProfileId", record.profileId());
            entry.putBoolean("Juvenile", record.juvenile());
            entry.putLong("Age", record.ageTicks());
            entry.putInt("Health", record.healthMilli());
            entry.putLong("Revision", record.revision());
            entry.put("EntityData", com.llqqrr.aircrate.content.creature.CreatureNbt.parse(record));
            entry.putLong("LastMilkGameTime", record.lastMilkGameTime());
            entry.putBoolean("ShearedState", record.sheared());
            entry.putLong("LastShearedGameTime", record.lastShearedGameTime());
            entry.putInt("WidthMilli", record.widthMilli());
            entry.putInt("HeightMilli", record.heightMilli());
            ListTag trades = new ListTag();
            for (TradeOfferSnapshot offer : record.trades()) {
                CompoundTag trade = new CompoundTag();
                // A villager's optional second price is commonly empty.
                trade.put("CostA", offer.costA().saveOptional(registries));
                trade.put("CostB", offer.costB().saveOptional(registries));
                trade.put("Result", offer.result().saveOptional(registries));
                trade.putInt("Uses", offer.uses());
                trade.putInt("MaxUses", offer.maxUses());
                trades.add(trade);
            }
            entry.put("Trades", trades);
            serializedRecords.add(entry);
        }
        return serializedRecords;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        feed.deserializeNBT(registries, tag.getCompound("Feed"));
        output.deserializeNBT(registries, tag.getCompound("Output"));
        fixtures.deserializeNBT(registries, tag.getCompound("Fixtures"));
        structureId = tag.hasUUID("StructureId") ? tag.getUUID("StructureId") : UUID.randomUUID();
        controllerPos = tag.contains("ControllerPos", Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("ControllerPos")) : worldPosition;
        structureVersion = Math.max(0L, tag.getLong("StructureVersion"));
        try {
            size = new CrateSize(tag.getInt("Width"), tag.getInt("Height"), tag.getInt("Length"));
        } catch (IllegalArgumentException ignored) {
            size = new CrateSize(1, 1, 1);
        }
        extentX = Math.max(1, tag.contains("ExtentX", Tag.TAG_INT) ? tag.getInt("ExtentX") : size.width());
        extentZ = Math.max(1, tag.contains("ExtentZ", Tag.TAG_INT) ? tag.getInt("ExtentZ") : size.length());
        breedingEnabled = tag.getBoolean("Breeding");
        breedingRate = Math.clamp(tag.getInt("BreedingRate"), 0, 100);
        tradeRate = Math.clamp(tag.getInt("TradeRate"), 0, 100);
        tradeProgress = Math.clamp(tag.getInt("TradeProgress"), 0, 999);
        breedingProgress = Math.clamp(tag.getInt("BreedingProgress"), 0, 999);
        crateMode = CrateMode.byName(tag.getString("CrateMode"));
        records.clear();
        ListTag storedRecords = tag.getList("Records", Tag.TAG_COMPOUND);
        for (int i = 0; i < storedRecords.size(); i++) {
            CompoundTag entry = storedRecords.getCompound(i);
            if (!entry.hasUUID("Id")) continue;
            List<TradeOfferSnapshot> trades = new ArrayList<>();
            ListTag storedTrades = entry.getList("Trades", Tag.TAG_COMPOUND);
            for (int tradeIndex = 0; tradeIndex < storedTrades.size(); tradeIndex++) {
                CompoundTag trade = storedTrades.getCompound(tradeIndex);
                trades.add(new TradeOfferSnapshot(ItemStack.parseOptional(registries, trade.getCompound("CostA")),
                        ItemStack.parseOptional(registries, trade.getCompound("CostB")),
                        ItemStack.parseOptional(registries, trade.getCompound("Result")),
                        trade.getInt("Uses"), trade.getInt("MaxUses")));
            }
            records.add(new CreatureRecord(entry.getUUID("Id"), entry.getString("EntityType"),
                    entry.getString("ProfileId"), entry.getBoolean("Juvenile"), entry.getLong("Age"),
                    entry.getInt("Health"), entry.getLong("Revision"), trades,
                    entry.contains("EntityData", Tag.TAG_COMPOUND) ? entry.getCompound("EntityData").toString() : "{}",
                    entry.contains("WidthMilli", Tag.TAG_INT) ? entry.getInt("WidthMilli") : 600,
                    entry.contains("HeightMilli", Tag.TAG_INT) ? entry.getInt("HeightMilli") : 1800,
                    entry.getLong("LastMilkGameTime"), entry.getBoolean("ShearedState"),
                    entry.getLong("LastShearedGameTime")));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putUUID("StructureId", structureId);
        tag.putLong("ControllerPos", controllerPos.asLong());
        tag.putLong("StructureVersion", structureVersion);
        tag.putInt("Width", size.width());
        tag.putInt("Height", size.height());
        tag.putInt("Length", size.length());
        tag.putInt("ExtentX", extentX);
        tag.putInt("ExtentZ", extentZ);
        tag.putBoolean("Breeding", breedingEnabled);
        tag.putInt("BreedingRate", breedingRate);
        tag.putInt("TradeRate", tradeRate);
        tag.putString("CrateMode", crateMode.getSerializedName());
        // Jade reads the client-side block entity; records must ride the sync tag.
        tag.put("Records", writeRecords(registries));
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) requestModelDataUpdate();
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag == null) return;
        long oldVersion = structureVersion;
        CrateMode oldMode = crateMode;
        loadWithComponents(tag, registries);
        if (level == null || !level.isClientSide) return;
        // Always refresh cached model data: the output-port face comes from the blockstate
        // and its section re-render races this packet otherwise.
        requestModelDataUpdate();
        // Rendering only depends on structure membership and the wall mode; skip
        // the section rebuild for pure inventory/progress syncs.
        if (oldVersion != structureVersion || oldMode != crateMode) {
            com.llqqrr.aircrate.client.AirCrateClient.refreshCrateSection(worldPosition);
        }
    }
}

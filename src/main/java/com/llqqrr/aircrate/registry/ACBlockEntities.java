package com.llqqrr.aircrate.registry;

import com.llqqrr.aircrate.AirCrate;
import com.llqqrr.aircrate.content.block.AviationCrateBlockEntity;
import com.llqqrr.aircrate.content.block.AviationCrateHatchBlockEntity;
import com.llqqrr.aircrate.content.block.CreatureBeltFixtureBlockEntity;
import com.llqqrr.aircrate.content.block.CrateInteractionHatchBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ACBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AirCrate.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AviationCrateBlockEntity>> AVIATION_CRATE =
            BLOCK_ENTITIES.register("aviation_crate", () -> BlockEntityType.Builder.of(
                    AviationCrateBlockEntity::new, AirCrate.AVIATION_CRATE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AviationCrateHatchBlockEntity>> AVIATION_CRATE_HATCH =
            BLOCK_ENTITIES.register("aviation_crate_hatch", () -> BlockEntityType.Builder.of(
                    AviationCrateHatchBlockEntity::new, AirCrate.ANDESITE_CRATE_HATCH.get(),
                    AirCrate.BRASS_CRATE_HATCH.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CreatureBeltFixtureBlockEntity>> CREATURE_BELT_FIXTURE =
            BLOCK_ENTITIES.register("creature_belt_fixture", () -> BlockEntityType.Builder.of(
                    CreatureBeltFixtureBlockEntity::new, AirCrate.CREATURE_BELT_FIXTURE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrateInteractionHatchBlockEntity>> CRATE_INTERACTION_HATCH =
            BLOCK_ENTITIES.register("crate_interaction_hatch", () -> BlockEntityType.Builder.of(
                    CrateInteractionHatchBlockEntity::new, AirCrate.CRATE_INTERACTION_HATCH.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.llqqrr.aircrate.content.frogport.CreatureFrogportBlockEntity>> CREATURE_FROGPORT =
            BLOCK_ENTITIES.register("creature_frogport", () -> BlockEntityType.Builder.of(
                    com.llqqrr.aircrate.content.frogport.CreatureFrogportBlockEntity::new,
                    AirCrate.CREATURE_FROGPORT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.llqqrr.aircrate.content.packager.CreaturePackagerBlockEntity>> CREATURE_PACKAGER =
            BLOCK_ENTITIES.register("creature_packager", () -> BlockEntityType.Builder.of(
                    com.llqqrr.aircrate.content.packager.CreaturePackagerBlockEntity::new,
                    AirCrate.CREATURE_PACKAGER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.llqqrr.aircrate.content.resonator.AmethystSculkResonatorBlockEntity>> AMETHYST_SCULK_RESONATOR =
            BLOCK_ENTITIES.register("amethyst_sculk_resonator", () -> BlockEntityType.Builder.of(
                    com.llqqrr.aircrate.content.resonator.AmethystSculkResonatorBlockEntity::new,
                    AirCrate.AMETHYST_SCULK_RESONATOR.get()).build(null));

    private ACBlockEntities() {
    }

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, AVIATION_CRATE.get(),
                // Exposed on every face so Create packagers (and other automation)
                // see the crate as an ordinary inventory, like Create's own vault.
                (crate, side) -> crate.automationInventory());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, AVIATION_CRATE_HATCH.get(),
                (hatch, side) -> hatch.automationInventory());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CREATURE_BELT_FIXTURE.get(),
                (fixture, side) -> fixture.outputInventory());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CREATURE_FROGPORT.get(),
                (frogport, side) -> frogport.automation());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CREATURE_PACKAGER.get(),
                (packager, side) -> packager.inventory);
    }
}

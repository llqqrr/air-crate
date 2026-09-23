package com.llqqrr.aircrate.registry;

import com.llqqrr.aircrate.AirCrate;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ACItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AirCrate.MOD_ID);

    public static final DeferredHolder<Item, BlockItem> AVIATION_CRATE = ITEMS.register("aviation_crate",
            () -> new BlockItem(AirCrate.AVIATION_CRATE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> ANDESITE_CRATE_HATCH = ITEMS.register("andesite_crate_hatch",
            () -> new BlockItem(AirCrate.ANDESITE_CRATE_HATCH.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> BRASS_CRATE_HATCH = ITEMS.register("brass_crate_hatch",
            () -> new BlockItem(AirCrate.BRASS_CRATE_HATCH.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> CRATE_INTERACTION_HATCH = ITEMS.register("crate_interaction_hatch",
            () -> new BlockItem(AirCrate.CRATE_INTERACTION_HATCH.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> CREATURE_BELT_FIXTURE = ITEMS.register("creature_belt_fixture",
            () -> new com.llqqrr.aircrate.content.block.CreatureBeltFixtureItem(new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<Item, Item> SEALED_CREATURE = ITEMS.register("sealed_creature",
            () -> new com.llqqrr.aircrate.content.creature.SealedCreatureItem(new Item.Properties()));
    public static final DeferredHolder<Item, Item> CREATURE_PARCEL = ITEMS.register("creature_parcel",
            () -> new com.llqqrr.aircrate.content.creature.CreatureParcelItem(new Item.Properties()));
    public static final DeferredHolder<Item, Item> CREATURE_FILTER = ITEMS.register("creature_filter",
            () -> new com.llqqrr.aircrate.content.creature.CreatureFilterItem(new Item.Properties()));
    public static final DeferredHolder<Item, Item> CREATURE_FROGPORT = ITEMS.register("creature_frogport",
            () -> new com.llqqrr.aircrate.content.frogport.CreatureFrogportItem(
                    AirCrate.CREATURE_FROGPORT.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> CREATURE_PACKAGER = ITEMS.register("creature_packager",
            () -> new BlockItem(AirCrate.CREATURE_PACKAGER.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> AMETHYST_SCULK_RESONATOR = ITEMS.register("amethyst_sculk_resonator",
            () -> new BlockItem(AirCrate.AMETHYST_SCULK_RESONATOR.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> MIXED_FEED = ITEMS.register("mixed_feed",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> CREATURE_INTAKE_FAN = ITEMS.register("creature_intake_fan",
            () -> new com.llqqrr.aircrate.content.creature.CreatureIntakeFanItem(
                    new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, Item> TRADING_BED_FIXTURE = ITEMS.register("trading_bed_fixture",
            () -> new Item(new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<Item, Item> TRADING_WORKSTATION_FIXTURE = ITEMS.register("trading_workstation_fixture",
            () -> new Item(new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<Item, Item> TRADING_DISPLAY_FIXTURE = ITEMS.register("trading_display_fixture",
            () -> new Item(new Item.Properties().stacksTo(16)));

    private ACItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}

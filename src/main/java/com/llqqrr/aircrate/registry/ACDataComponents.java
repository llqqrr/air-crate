package com.llqqrr.aircrate.registry;

import com.llqqrr.aircrate.AirCrate;
import com.llqqrr.aircrate.content.creature.CreatureRecord;
import com.llqqrr.aircrate.content.creature.CreatureFilterData;
import com.llqqrr.aircrate.content.frogport.CreatureAreaSelection;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ACDataComponents {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, AirCrate.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CreatureRecord>> CREATURE_RECORD =
            COMPONENTS.register("creature_record", () -> DataComponentType.<CreatureRecord>builder()
                    .persistent(CreatureRecord.codec())
                    .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(CreatureRecord.codec()))
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CreatureFilterData>> CREATURE_FILTER =
            COMPONENTS.register("creature_filter", () -> DataComponentType.<CreatureFilterData>builder()
                    .persistent(CreatureFilterData.codec())
                    .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(CreatureFilterData.codec()))
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CreatureAreaSelection>> FROGPORT_SELECTION =
            COMPONENTS.register("frogport_selection", () -> DataComponentType.<CreatureAreaSelection>builder()
                    .persistent(CreatureAreaSelection.codec())
                    .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(CreatureAreaSelection.codec()))
                    .build());

    /** Compressed air stored in the packing gun, in Create backtank air units. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> AIR =
            COMPONENTS.register("air", () -> DataComponentType.<Integer>builder()
                    .persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(ByteBufCodecs.INT)
                    .build());

    private ACDataComponents() {
    }

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }
}

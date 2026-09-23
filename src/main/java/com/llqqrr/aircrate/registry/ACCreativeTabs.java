package com.llqqrr.aircrate.registry;

import com.llqqrr.aircrate.AirCrate;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Dedicated creative inventory entry for every player-facing Air Crate item. */
public final class ACCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AirCrate.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.aircrate"))
                    .icon(() -> new ItemStack(ACItems.AVIATION_CRATE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ACItems.AVIATION_CRATE.get());
                        output.accept(ACItems.CRATE_INTERACTION_HATCH.get());
                        output.accept(ACItems.CREATURE_INTAKE_FAN.get());
                        output.accept(ACItems.CREATURE_FILTER.get());
                        output.accept(ACItems.CREATURE_FROGPORT.get());
                        output.accept(ACItems.CREATURE_PACKAGER.get());
                        output.accept(ACItems.AMETHYST_SCULK_RESONATOR.get());
                        output.accept(ACItems.MIXED_FEED.get());
                    })
                    .build());

    private ACCreativeTabs() {
    }

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}

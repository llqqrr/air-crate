package com.llqqrr.aircrate.compat.fieldguide;

import com.llqqrr.aircrate.network.AirCrateNetwork;
import com.llqqrr.aircrate.registry.ACItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Reflection keeps the open-source Field Guide integration entirely optional. */
public final class FieldGuideClientCompat {
    private FieldGuideClientCompat() { }

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(FieldGuideClientCompat::onScreenInit);
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!event.getScreen().getClass().getName()
                .equals("com.evandev.fieldguide.client.gui.screens.FieldGuideEntryScreen")) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !holdsFilter(minecraft.player.getMainHandItem(), minecraft.player.getOffhandItem())) return;
        String entityId = entryEntityId(event.getScreen());
        if (entityId == null) return;
        Button button = Button.builder(net.minecraft.network.chat.Component.literal("+"), ignored ->
                        AirCrateNetwork.filterAction(AirCrateNetwork.FilterAction.ADD, entityId))
                .bounds(event.getScreen().width / 2 + 116, event.getScreen().height / 2 + 64, 20, 20).build();
        button.setTooltip(Tooltip.create(net.minecraft.network.chat.Component.translatable("screen.aircrate.add_to_filter")));
        event.addListener(button);
    }

    private static boolean holdsFilter(ItemStack main, ItemStack offhand) {
        return main.is(ACItems.CREATURE_FILTER.get()) || offhand.is(ACItems.CREATURE_FILTER.get());
    }

    private static String entryEntityId(Object screen) {
        try {
            Field entryField = screen.getClass().getDeclaredField("entry");
            entryField.setAccessible(true);
            Object entry = entryField.get(screen);
            if (entry instanceof net.minecraft.world.entity.EntityType<?> type) {
                return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            }
            Method target = entry.getClass().getMethod("targetEntityType");
            Object value = target.invoke(entry);
            return value instanceof ResourceLocation id && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                    ? id.toString() : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}

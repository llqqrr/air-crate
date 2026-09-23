package com.llqqrr.aircrate.content.creature;

import com.llqqrr.aircrate.content.villager.TradeOfferSnapshot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Reads only bounded offer fields; it never serializes villager entity NBT. */
final class TradeOfferSnapshotExtractor {
    private TradeOfferSnapshotExtractor() { }

    static List<TradeOfferSnapshot> extract(Entity entity) {
        if (!(entity instanceof net.minecraft.world.entity.npc.Villager)) return List.of();
        try {
            Object offers = entity.getClass().getMethod("getOffers").invoke(entity);
            List<TradeOfferSnapshot> result = new ArrayList<>();
            if (offers instanceof Iterable<?> iterable) for (Object offer : iterable) {
                ItemStack output = item(invoke(offer, "getResult"));
                if (output.isEmpty()) continue;
                result.add(new TradeOfferSnapshot(item(invoke(offer, "getCostA")), item(invoke(offer, "getCostB")),
                        output, number(invoke(offer, "getUses"), 0), number(invoke(offer, "getMaxUses"), 1)));
                if (result.size() >= 32) break;
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return List.of();
        }
    }

    private static Object invoke(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name);
        return method.invoke(target);
    }

    private static ItemStack item(Object value) {
        if (value instanceof ItemStack stack) return stack.copy();
        if (value instanceof java.util.Optional<?> optional) return optional.map(TradeOfferSnapshotExtractor::item).orElse(ItemStack.EMPTY);
        if (value != null) try { return item(value.getClass().getMethod("itemStack").invoke(value)); }
        catch (ReflectiveOperationException ignored) { }
        return ItemStack.EMPTY;
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }
}

package com.llqqrr.aircrate.content.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

/** Bounded, transport-friendly part of a villager offer used by the crate processor. */
public record TradeOfferSnapshot(ItemStack costA, ItemStack costB, ItemStack result, int uses, int maxUses) {
    public TradeOfferSnapshot {
        costA = costA == null ? ItemStack.EMPTY : costA.copy();
        costB = costB == null ? ItemStack.EMPTY : costB.copy();
        result = result == null ? ItemStack.EMPTY : result.copy();
        uses = Math.max(0, uses);
        maxUses = Math.max(1, maxUses);
    }

    public boolean exhausted() { return uses >= maxUses; }

    public TradeOfferSnapshot usedOnce() {
        return new TradeOfferSnapshot(costA, costB, result, Math.min(maxUses, uses + 1), maxUses);
    }

    public static Codec<TradeOfferSnapshot> codec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                ItemStack.CODEC.optionalFieldOf("cost_a", ItemStack.EMPTY).forGetter(TradeOfferSnapshot::costA),
                ItemStack.CODEC.optionalFieldOf("cost_b", ItemStack.EMPTY).forGetter(TradeOfferSnapshot::costB),
                ItemStack.CODEC.fieldOf("result").forGetter(TradeOfferSnapshot::result),
                Codec.INT.optionalFieldOf("uses", 0).forGetter(TradeOfferSnapshot::uses),
                Codec.INT.optionalFieldOf("max_uses", 1).forGetter(TradeOfferSnapshot::maxUses)
        ).apply(instance, TradeOfferSnapshot::new));
    }
}

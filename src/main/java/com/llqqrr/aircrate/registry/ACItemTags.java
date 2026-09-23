package com.llqqrr.aircrate.registry;

import com.llqqrr.aircrate.AirCrate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ACItemTags {
    public static final TagKey<Item> FEED = itemTag("feed");
    public static final TagKey<Item> BREEDING_FEED = itemTag("breeding_feed");

    private ACItemTags() {
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(AirCrate.MOD_ID, path));
    }
}

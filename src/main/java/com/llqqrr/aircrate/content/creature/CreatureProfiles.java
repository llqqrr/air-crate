package com.llqqrr.aircrate.content.creature;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The registry facade keeps the first version deterministic. Data-pack loading can
 * replace entries later without changing the capacity and split algorithms.
 */
public final class CreatureProfiles {
    private static final Map<String, CreatureProfile> PROFILES = new ConcurrentHashMap<>();

    static {
        register(new CreatureProfile("minecraft:cow", 1, 2, 2, true, 50));
        register(new CreatureProfile("minecraft:sheep", 1, 2, 2, true, 50));
        register(new CreatureProfile("minecraft:pig", 1, 1, 2, true, 50));
        register(new CreatureProfile("minecraft:chicken", 1, 1, 1, true, 50));
        register(new CreatureProfile("minecraft:villager", 1, 2, 1, true, 100));
    }

    private CreatureProfiles() {
    }

    public static void register(CreatureProfile profile) {
        PROFILES.put(profile.id(), profile);
    }

    public static CreatureProfile resolve(CreatureRecord record) {
        return PROFILES.getOrDefault(record.profileId(),
                new CreatureProfile(record.profileId(), 1, 2, 1, true, 100));
    }
}

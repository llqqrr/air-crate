package com.llqqrr.aircrate.content.creature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.UUID;
import com.llqqrr.aircrate.content.villager.TradeOfferSnapshot;

/** A server-authored creature snapshot. Entity data is authoritative; summary fields drive crate simulation. */
public record CreatureRecord(UUID recordId, String entityType, String profileId, boolean juvenile,
                             long ageTicks, int healthMilli, long revision, List<TradeOfferSnapshot> trades,
                             String entityData, int widthMilli, int heightMilli,
                             long lastMilkGameTime, boolean sheared, long lastShearedGameTime) {
    public static Codec<CreatureRecord> codec() {
        return CodecHolder.CODEC;
    }

    public CreatureRecord {
        entityType = entityType == null || entityType.isBlank() ? "minecraft:pig" : entityType;
        profileId = profileId == null || profileId.isBlank() ? entityType : profileId;
        healthMilli = Math.clamp(healthMilli, 1, 1_000_000);
        revision = Math.max(0L, revision);
        trades = trades == null ? List.of() : List.copyOf(trades);
        entityData = entityData == null || entityData.isBlank() ? "{}" : entityData;
        widthMilli = Math.clamp(widthMilli, 1, 100_000);
        heightMilli = Math.clamp(heightMilli, 1, 100_000);
        lastMilkGameTime = Math.max(0L, lastMilkGameTime);
        lastShearedGameTime = Math.max(0L, lastShearedGameTime);
    }

    public CreatureRecord(UUID recordId, String entityType, String profileId, boolean juvenile,
                          long ageTicks, int healthMilli, long revision) {
        this(recordId, entityType, profileId, juvenile, ageTicks, healthMilli, revision, List.of(),
                "{}", 600, 1800, 0L, false, 0L);
    }

    public CreatureRecord(UUID recordId, String entityType, String profileId, boolean juvenile,
                          long ageTicks, int healthMilli, long revision, List<TradeOfferSnapshot> trades) {
        this(recordId, entityType, profileId, juvenile, ageTicks, healthMilli, revision, trades,
                "{}", 600, 1800, 0L, false, 0L);
    }

    public CreatureRecord(UUID recordId, String entityType, String profileId, boolean juvenile,
                          long ageTicks, int healthMilli, long revision, List<TradeOfferSnapshot> trades,
                          String entityData, int widthMilli, int heightMilli) {
        this(recordId, entityType, profileId, juvenile, ageTicks, healthMilli, revision, trades,
                entityData, widthMilli, heightMilli, 0L, false, 0L);
    }

    public static CreatureRecord create(String entityType, String profileId, boolean juvenile) {
        return new CreatureRecord(UUID.randomUUID(), entityType, profileId, juvenile, 0L, 20000, 0L);
    }

    public CreatureRecord nextRevision() {
        return new CreatureRecord(recordId, entityType, profileId, juvenile, ageTicks, healthMilli, revision + 1,
                trades, entityData, widthMilli, heightMilli, lastMilkGameTime, sheared, lastShearedGameTime);
    }

    private static final class CodecHolder {
        private static final Codec<CreatureRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("record_id").forGetter(CreatureRecord::recordId),
                Codec.STRING.fieldOf("entity_type").forGetter(CreatureRecord::entityType),
                Codec.STRING.fieldOf("profile_id").forGetter(CreatureRecord::profileId),
                Codec.BOOL.optionalFieldOf("juvenile", false).forGetter(CreatureRecord::juvenile),
                Codec.LONG.optionalFieldOf("age_ticks", 0L).forGetter(CreatureRecord::ageTicks),
                Codec.INT.optionalFieldOf("health_milli", 20000).forGetter(CreatureRecord::healthMilli),
                Codec.LONG.optionalFieldOf("revision", 0L).forGetter(CreatureRecord::revision),
                TradeOfferSnapshot.codec().listOf().optionalFieldOf("trades", List.of()).forGetter(CreatureRecord::trades),
                Codec.STRING.optionalFieldOf("entity_data", "{}").forGetter(CreatureRecord::entityData),
                Codec.INT.optionalFieldOf("width_milli", 600).forGetter(CreatureRecord::widthMilli),
                Codec.INT.optionalFieldOf("height_milli", 1800).forGetter(CreatureRecord::heightMilli),
                Codec.LONG.optionalFieldOf("last_milk_game_time", 0L).forGetter(CreatureRecord::lastMilkGameTime),
                Codec.BOOL.optionalFieldOf("sheared", false).forGetter(CreatureRecord::sheared),
                Codec.LONG.optionalFieldOf("last_sheared_game_time", 0L).forGetter(CreatureRecord::lastShearedGameTime)
        ).apply(instance, CreatureRecord::new));
    }
}

package com.llqqrr.aircrate.content.creature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashSet;
import java.util.List;

/** Server-safe creature type filter shared by the item and frogport. */
public record CreatureFilterData(boolean whitelist, List<String> entityTypes) {
    public static final CreatureFilterData EMPTY_BLACKLIST = new CreatureFilterData(false, List.of());

    public CreatureFilterData {
        entityTypes = entityTypes == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(entityTypes.stream()
                .filter(id -> id != null && !id.isBlank())
                .limit(1024)
                .toList()));
    }

    public static Codec<CreatureFilterData> codec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("whitelist", false).forGetter(CreatureFilterData::whitelist),
                Codec.STRING.listOf().optionalFieldOf("entity_types", List.of()).forGetter(CreatureFilterData::entityTypes)
        ).apply(instance, CreatureFilterData::new));
    }

    public CreatureFilterData withType(String entityType) {
        if (entityTypes.contains(entityType)) return this;
        LinkedHashSet<String> added = new LinkedHashSet<>(entityTypes);
        added.add(entityType);
        return new CreatureFilterData(whitelist, List.copyOf(added));
    }

    public CreatureFilterData toggleMode() {
        return new CreatureFilterData(!whitelist, entityTypes);
    }

    public boolean permits(String entityType) {
        boolean listed = entityTypes.contains(entityType);
        return whitelist ? listed : !listed;
    }

    public boolean permits(LivingEntity entity) {
        return permits(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(entity.getType()).toString());
    }
}

package com.llqqrr.aircrate.content.creature;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

/** Shared localized display name for full-fidelity records. */
public final class CreatureRecordDisplay {
    private CreatureRecordDisplay() { }

    public static Component name(CreatureRecord record) {
        EntityType<?> type = EntityType.byString(record.entityType()).orElse(null);
        return type == null ? Component.literal(record.entityType()) : type.getDescription();
    }
}

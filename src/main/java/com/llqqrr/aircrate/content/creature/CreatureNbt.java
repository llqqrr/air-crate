package com.llqqrr.aircrate.content.creature;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

/** Minecraft-runtime SNBT conversion kept out of the pure data record used by unit tests. */
public final class CreatureNbt {
    private CreatureNbt() { }

    public static CompoundTag parse(CreatureRecord record) {
        try {
            return TagParser.parseTag(record.entityData());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ignored) {
            return new CompoundTag();
        }
    }

    public static boolean sheared(CreatureRecord record) {
        return record.sheared() || parse(record).getBoolean("Sheared");
    }

    public static String withSheared(CreatureRecord record, boolean sheared) {
        CompoundTag data = parse(record);
        data.putBoolean("Sheared", sheared);
        return data.toString();
    }
}

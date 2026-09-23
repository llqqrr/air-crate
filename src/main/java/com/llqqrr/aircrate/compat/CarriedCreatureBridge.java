package com.llqqrr.aircrate.compat;

import com.llqqrr.aircrate.content.creature.CreatureRecord;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/** Optional bridge for a carry mod; it never exposes another mod's entity NBT. */
public interface CarriedCreatureBridge {
    Optional<CreatureRecord> takeCarriedCreature(Player player);

    boolean restoreCarriedCreature(Player player, CreatureRecord record);
}

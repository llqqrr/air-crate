package com.llqqrr.aircrate.compat;

import com.llqqrr.aircrate.content.block.AviationCrateBlockEntity;
import com.llqqrr.aircrate.content.block.AviationCrateHatchBlock;
import com.llqqrr.aircrate.content.creature.CreatureCaptureService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/** Optional Carry On adapter. Reflection keeps the base mod loadable without Carry On. */
public final class CarryOnCreatureBridge {
    private CarryOnCreatureBridge() {
    }

    public static boolean tryStoreCarriedCreature(Player player, BlockPos lower, Direction facing) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)
                || !ModList.get().isLoaded("carryon")) return false;
        AviationCrateBlockEntity controller = AviationCrateHatchBlock.controller(level, lower, facing);
        if (controller == null) return false;
        try {
            Class<?> manager = Class.forName("tschipp.carryon.common.carry.CarryOnDataManager");
            Object data = manager.getMethod("getCarryData", Player.class).invoke(null, player);
            Method carrying = data.getClass().getMethod("isCarrying");
            if (!Boolean.TRUE.equals(carrying.invoke(data))) return false;
            Entity entity = (Entity) data.getClass().getMethod("getEntity", Level.class).invoke(data, level);
            if (entity == null || !CreatureCaptureService.capture(controller, entity)) return false;
            data.getClass().getMethod("clear").invoke(data);
            manager.getMethod("setCarryData", Player.class, data.getClass()).invoke(null, player, data);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }
}

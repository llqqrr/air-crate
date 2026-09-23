package com.llqqrr.aircrate.compat.create;

import com.llqqrr.aircrate.content.creature.CreatureCaptureService;
import com.llqqrr.aircrate.registry.ACDataComponents;
import com.llqqrr.aircrate.registry.ACItems;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Converts supported living entities standing on an upgraded Create belt into transported record items. */
public final class CreatureBeltFixtureService {
    private CreatureBeltFixtureService() { }

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CreatureBeltFixtureService::onLevelTick);
    }

    public static boolean install(ServerLevel level, BlockPos clicked) {
        if (!(level.getBlockEntity(clicked) instanceof BeltBlockEntity belt)) return false;
        BeltBlockEntity controller = belt.getControllerBE();
        return controller != null && CreatureBeltFixtureData.get(level).install(controller.getBlockPos());
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 5L != 0L) return;
        CreatureBeltFixtureData data = CreatureBeltFixtureData.get(level);
        for (BlockPos controllerPos : data.controllers()) {
            if (!level.isLoaded(controllerPos)) continue;
            if (!(level.getBlockEntity(controllerPos) instanceof BeltBlockEntity controller) || !controller.isController()) {
                data.remove(controllerPos);
                continue;
            }
            convertOne(level, controller);
        }
    }

    private static void convertOne(ServerLevel level, BeltBlockEntity belt) {
        Vec3 chain = Vec3.atLowerCornerOf(belt.getBeltChainDirection());
        Vec3 start = Vec3.atCenterOf(belt.getBlockPos());
        Vec3 end = start.add(chain.scale(Math.max(0, belt.beltLength - 1)));
        AABB beltVolume = new AABB(start, end).inflate(0.75D, 1.25D, 0.75D);
        double chainLengthSquared = Math.max(1.0D, chain.lengthSqr());
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, beltVolume,
                CreatureCaptureService::isSupported)) {
            double along = entity.position().subtract(start).dot(chain) / chainLengthSquared;
            if (along < -0.75D || along > belt.beltLength - 0.25D) continue;
            int segment = Math.clamp((int) Math.floor(along + 0.5D), 0, belt.beltLength - 1);
            if (!belt.getInventory().canInsertAt(segment)) continue;
            ItemStack item = new ItemStack(ACItems.SEALED_CREATURE.get());
            item.set(ACDataComponents.CREATURE_RECORD.get(), CreatureCaptureService.recordFromEntity(entity));
            TransportedItemStack transported = new TransportedItemStack(item);
            transported.beltPosition = (float) Math.clamp(along + 0.5D, 0.5D, belt.beltLength - 0.5D);
            transported.prevBeltPosition = transported.beltPosition;
            transported.insertedAt = segment;
            belt.getInventory().addItem(transported);
            belt.setChanged();
            belt.sendData();
            entity.discard();
            return;
        }
    }
}

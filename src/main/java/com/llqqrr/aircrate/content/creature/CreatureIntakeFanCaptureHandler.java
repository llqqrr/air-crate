package com.llqqrr.aircrate.content.creature;

import com.llqqrr.aircrate.registry.ACItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Runs before entity-specific interactions such as villager trading consume the right click. */
public final class CreatureIntakeFanCaptureHandler {
    private CreatureIntakeFanCaptureHandler() {
    }

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                CreatureIntakeFanCaptureHandler::onEntityInteract);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                CreatureIntakeFanCaptureHandler::onEntityInteractSpecific);
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        handle(event.getItemStack(), event.getEntity(), event.getLevel(), event.getTarget(), () -> {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
        }, event.getHand());
    }

    private static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        handle(event.getItemStack(), event.getEntity(), event.getLevel(), event.getTarget(), () -> {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
        }, event.getHand());
    }

    private static void handle(ItemStack held, Player player, net.minecraft.world.level.Level level,
                               net.minecraft.world.entity.Entity targetEntity, Runnable cancel,
                               net.minecraft.world.InteractionHand hand) {
        if (!held.is(ACItems.CREATURE_INTAKE_FAN.get())
                || !(targetEntity instanceof LivingEntity target)
                || !CreatureCaptureService.isSupported(target)) {
            return;
        }
        cancel.run();
        if (!(level instanceof ServerLevel serverLevel)
                || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }
        CreatureIntakeFanItem.scheduleCapture(serverPlayer, serverLevel, target, hand);
    }
}

package com.llqqrr.aircrate.content.frogport;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative two-corner reselection started from the placed frogport screen. */
public final class CreatureFrogportSelectionHandler {
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private CreatureFrogportSelectionHandler() { }

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CreatureFrogportSelectionHandler::onRightClickBlock);
    }

    public static void begin(ServerPlayer player, BlockPos frogport) {
        PENDING.put(player.getUUID(), new Pending(player.level().dimension(), frogport.immutable(), null));
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.aircrate.reselection_started"), true);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null || pending.dimension != player.level().dimension()) return;
        if (!(player.level().getBlockEntity(pending.frogport) instanceof CreatureFrogportBlockEntity frogport)) {
            PENDING.remove(player.getUUID());
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        BlockPos clicked = event.getPos().immutable();
        if (pending.first == null) {
            PENDING.put(player.getUUID(), new Pending(pending.dimension, pending.frogport, clicked));
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.aircrate.selection_first"), true);
            return;
        }
        CreatureAreaSelection selection = new CreatureAreaSelection(pending.first, clicked);
        if (!selection.validSize()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.aircrate.selection_too_large"), true);
            return;
        }
        if (!selection.inRangeOf(pending.frogport)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.aircrate.selection_too_far"), true);
            return;
        }
        frogport.setSelection(selection);
        PENDING.remove(player.getUUID());
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.aircrate.reselection_complete"), true);
    }

    private record Pending(ResourceKey<Level> dimension, BlockPos frogport, BlockPos first) { }
}

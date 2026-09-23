package com.llqqrr.aircrate.compat.create;

import com.llqqrr.aircrate.content.creature.CreatureContainerGuard;
import com.llqqrr.aircrate.content.creature.CreatureParcelItem;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Empty-hand pickup of creature parcels held by Create logistics. */
public final class CreateCreatureInteractions {
    private CreateCreatureInteractions() { }

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CreateCreatureInteractions::onRightClickBlock);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!CreateCreatureLogistics.isSurface(event.getLevel(), event.getPos())) return;
        ItemStack held = event.getEntity().getItemInHand(event.getHand());
        if (!held.isEmpty() || CreatureContainerGuard.hasParcel(event.getEntity())) return;
        ItemStack parcel = peek(event.getLevel().getBlockEntity(event.getPos()));
        if (!CreatureParcelItem.isCreatureParcel(parcel)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!remove(level.getBlockEntity(event.getPos()))) return;
        ItemStack pickedUp = parcel.copyWithCount(1);
        if (!event.getEntity().getInventory().add(pickedUp)) event.getEntity().drop(pickedUp, false);
    }

    private static ItemStack peek(net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        ItemStack stack = ItemStack.EMPTY;
        if (blockEntity instanceof BeltBlockEntity belt) {
            BeltBlockEntity controller = belt.getControllerBE();
            if (controller != null) {
                for (TransportedItemStack item : controller.getInventory().getTransportedItems()) {
                    if (CreatureParcelItem.isCreatureParcel(item.stack)) { stack = item.stack; break; }
                }
            }
        } else if (blockEntity instanceof DepotBlockEntity depot) {
            stack = depot.getHeldItem();
        } else if (blockEntity instanceof ChuteBlockEntity chute) {
            stack = chute.getItem();
        }
        return stack;
    }

    private static boolean remove(net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        if (blockEntity instanceof BeltBlockEntity belt) {
            BeltBlockEntity controller = belt.getControllerBE();
            if (controller == null) return false;
            boolean changed = controller.getInventory().getTransportedItems()
                    .removeIf(item -> CreatureParcelItem.isCreatureParcel(item.stack));
            if (changed) { controller.setChanged(); controller.sendData(); }
            return changed;
        }
        if (blockEntity instanceof DepotBlockEntity depot
                && CreatureParcelItem.isCreatureParcel(depot.getHeldItem())) {
            depot.setHeldItem(ItemStack.EMPTY);
            depot.setChanged();
            depot.sendData();
            return true;
        }
        if (blockEntity instanceof ChuteBlockEntity chute
                && CreatureParcelItem.isCreatureParcel(chute.getItem())) {
            chute.setItem(ItemStack.EMPTY);
            chute.setChanged();
            chute.sendData();
            return true;
        }
        return false;
    }
}

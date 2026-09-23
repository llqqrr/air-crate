package com.llqqrr.aircrate.content.frogport;

import com.llqqrr.aircrate.registry.ACDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/** Three-click workflow: select corner, select corner, then place the configured frogport. */
public final class CreatureFrogportItem extends BlockItem {
    public CreatureFrogportItem(Block block, Properties properties) {
        super(block, properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        CreatureAreaSelection selection = context.getItemInHand().get(ACDataComponents.FROGPORT_SELECTION.get());
        if (selection == null) {
            if (!context.getLevel().isClientSide) {
                context.getItemInHand().set(ACDataComponents.FROGPORT_SELECTION.get(),
                        new CreatureAreaSelection(context.getClickedPos(), context.getClickedPos(), false));
                if (context.getPlayer() != null) context.getPlayer().displayClientMessage(
                        Component.translatable("message.aircrate.selection_first"), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        if (!selection.complete()) {
            CreatureAreaSelection completed = new CreatureAreaSelection(selection.first(), context.getClickedPos());
            if (!completed.validSize()) {
                if (!context.getLevel().isClientSide && context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(Component.translatable("message.aircrate.selection_too_large"), true);
                }
                return InteractionResult.FAIL;
            }
            if (!context.getLevel().isClientSide) {
                context.getItemInHand().set(ACDataComponents.FROGPORT_SELECTION.get(), completed);
                if (context.getPlayer() != null) context.getPlayer().displayClientMessage(
                        Component.translatable("message.aircrate.selection_complete"), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return super.useOn(context);
    }
}

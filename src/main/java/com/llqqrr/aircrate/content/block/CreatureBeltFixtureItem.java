package com.llqqrr.aircrate.content.block;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** One-use upgrade item that changes a Create belt's persisted operating state. */
public final class CreatureBeltFixtureItem extends Item {
    public CreatureBeltFixtureItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!net.neoforged.fml.ModList.get().isLoaded("create")) return InteractionResult.FAIL;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getLevel() instanceof ServerLevel level)
                || !com.llqqrr.aircrate.compat.create.CreatureBeltFixtureService.install(level, context.getClickedPos())) {
            return InteractionResult.FAIL;
        }
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}

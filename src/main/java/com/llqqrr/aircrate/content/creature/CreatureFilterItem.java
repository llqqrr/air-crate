package com.llqqrr.aircrate.content.creature;

import com.llqqrr.aircrate.registry.ACDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Multi-entry whitelist/blacklist configured by sampling living creatures. */
public final class CreatureFilterItem extends Item {
    public CreatureFilterItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static CreatureFilterData data(ItemStack stack) {
        return stack.getOrDefault(ACDataComponents.CREATURE_FILTER.get(), CreatureFilterData.EMPTY_BLACKLIST);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                   InteractionHand hand) {
        if (!CreatureCaptureService.isSupported(target)) return InteractionResult.PASS;
        if (!player.level().isClientSide) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
            stack.set(ACDataComponents.CREATURE_FILTER.get(), data(stack).withType(id));
            player.displayClientMessage(Component.translatable("message.aircrate.filter_added", target.getName()), true);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            if (level.isClientSide) com.llqqrr.aircrate.client.AirCrateClient.openFilterScreen(data(stack));
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!level.isClientSide) {
            CreatureFilterData toggled = data(stack).toggleMode();
            stack.set(ACDataComponents.CREATURE_FILTER.get(), toggled);
            player.displayClientMessage(Component.translatable(toggled.whitelist()
                    ? "message.aircrate.filter_whitelist" : "message.aircrate.filter_blacklist"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CreatureFilterData data = data(stack);
        tooltip.add(Component.translatable(data.whitelist()
                ? "tooltip.aircrate.filter_whitelist" : "tooltip.aircrate.filter_blacklist")
                .withStyle(data.whitelist() ? ChatFormatting.GREEN : ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.aircrate.filter_count", data.entityTypes().size())
                .withStyle(ChatFormatting.GRAY));
    }
}

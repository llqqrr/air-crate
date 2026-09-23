package com.llqqrr.aircrate.content.creature;

import com.llqqrr.aircrate.registry.ACDataComponents;
import com.llqqrr.aircrate.registry.ACItems;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

/** A Create-compatible package containing exactly one full-fidelity creature record. */
public final class CreatureParcelItem extends PackageItem {
    private static final PackageStyles.PackageStyle STYLE =
            new PackageStyles.PackageStyle("cardboard", 12, 10, .25F, false);

    public CreatureParcelItem(Properties properties) {
        super(properties.stacksTo(1), STYLE);
    }

    public static ItemStack containing(CreatureRecord record) {
        // Keep the parcel a real Air Crate PackageItem so its custom renderer and
        // item identity survive Create logistics, instead of disguising it as cardboard.
        ItemStack parcel = new ItemStack(ACItems.CREATURE_PARCEL.get());
        parcel.set(ACDataComponents.CREATURE_RECORD.get(), record);
        return parcel;
    }

    public static CreatureRecord record(ItemStack stack) {
        return PackageItem.isPackage(stack) ? stack.get(ACDataComponents.CREATURE_RECORD.get()) : null;
    }

    public static boolean isCreatureParcel(ItemStack stack) {
        return record(stack) != null;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.aircrate.creature_parcel");
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos())
                instanceof com.llqqrr.aircrate.content.block.AviationCrateBlockEntity crate)) {
            return InteractionResult.PASS;
        }
        CreatureRecord record = record(context.getItemInHand());
        if (record == null) return InteractionResult.FAIL;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!crate.controller().addRecord(record)) return InteractionResult.FAIL;
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CreatureRecord record = record(stack);
        if (record != null) tooltip.add(Component.translatable("tooltip.aircrate.creature_parcel_contents",
                CreatureRecordDisplay.name(record)).withStyle(ChatFormatting.GREEN));
    }
}

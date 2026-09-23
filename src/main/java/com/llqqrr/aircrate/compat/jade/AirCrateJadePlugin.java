package com.llqqrr.aircrate.compat.jade;

import com.llqqrr.aircrate.content.block.AviationCrateBlock;
import com.llqqrr.aircrate.content.block.AviationCrateBlockEntity;
import com.llqqrr.aircrate.content.creature.CreatureRecord;
import com.llqqrr.aircrate.content.creature.CreatureRecordDisplay;
import com.llqqrr.aircrate.registry.ACDataComponents;
import com.llqqrr.aircrate.registry.ACItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.ITooltip;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Optional Jade overlay: exposes stored creatures as their universal itemized forms. */
@WailaPlugin
public final class AirCrateJadePlugin implements IWailaPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("aircrate", "creatures");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new CreatureListProvider(), AviationCrateBlock.class);
    }

    private static final class CreatureListProvider implements IBlockComponentProvider {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof AviationCrateBlockEntity member)) return;
            AviationCrateBlockEntity crate = member.controller();
            List<CreatureRecord> records = crate.records();
            if (records.isEmpty()) return;
            tooltip.add(Component.translatable("jade.aircrate.creatures", records.size()), UID);
            Map<String, List<CreatureRecord>> grouped = new LinkedHashMap<>();
            for (CreatureRecord record : records) {
                grouped.computeIfAbsent(record.entityType(), ignored -> new ArrayList<>()).add(record);
            }
            int shown = 0;
            for (List<CreatureRecord> group : grouped.values()) {
                if (shown++ >= 12) break;
                CreatureRecord record = group.getFirst();
                ItemStack item = new ItemStack(ACItems.SEALED_CREATURE.get());
                item.set(ACDataComponents.CREATURE_RECORD.get(), record);
                List<IElement> row = new ArrayList<>();
                row.add(IElementHelper.get().smallItem(item));
                row.add(IElementHelper.get().spacer(3, 0));
                row.add(IElementHelper.get().text(CreatureRecordDisplay.name(record).copy()
                        .append(Component.literal(" x " + group.size()))));
                tooltip.add(row);
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}

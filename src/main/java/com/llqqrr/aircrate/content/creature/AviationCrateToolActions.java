package com.llqqrr.aircrate.content.creature;

import com.llqqrr.aircrate.content.block.AviationCrateBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Server-side simulations of Deployer tool actions against an abstract creature record. */
public final class AviationCrateToolActions {
    private static final long MILK_COOLDOWN_TICKS = 6000L;
    private static final Set<String> MILKABLE_TYPES = Set.of("minecraft:cow", "minecraft:mooshroom", "minecraft:goat");
    private static final Item[] WOOL_BY_COLOR = {
            Items.WHITE_WOOL, Items.ORANGE_WOOL, Items.MAGENTA_WOOL, Items.LIGHT_BLUE_WOOL,
            Items.YELLOW_WOOL, Items.LIME_WOOL, Items.PINK_WOOL, Items.GRAY_WOOL,
            Items.LIGHT_GRAY_WOOL, Items.CYAN_WOOL, Items.PURPLE_WOOL, Items.BLUE_WOOL,
            Items.BROWN_WOOL, Items.GREEN_WOOL, Items.RED_WOOL, Items.BLACK_WOOL
    };

    private AviationCrateToolActions() {
    }

    public static boolean slaughter(AviationCrateBlockEntity crate, ItemStack tool, Player actor) {
        for (CreatureRecord record : crate.records()) {
            List<ItemStack> drops = slaughterDrops(crate, record, actor);
            if (drops.isEmpty() || !canStore(crate, drops)) continue;
            crate.removeRecord(record.recordId());
            store(crate, drops);
            tool.hurtAndBreak(1, actor, net.minecraft.world.entity.LivingEntity.getSlotForHand(
                    net.minecraft.world.InteractionHand.MAIN_HAND));
            return true;
        }
        return false;
    }

    public static boolean milk(AviationCrateBlockEntity crate, net.minecraft.world.level.Level level) {
        long gameTime = level.getGameTime();
        for (CreatureRecord record : crate.records()) {
            if (!MILKABLE_TYPES.contains(record.entityType()) || record.juvenile()) continue;
            if (gameTime - record.lastMilkGameTime() < MILK_COOLDOWN_TICKS) continue;
            crate.replaceRecord(new CreatureRecord(record.recordId(), record.entityType(), record.profileId(),
                    record.juvenile(), record.ageTicks(), record.healthMilli(), record.revision() + 1, record.trades(),
                    record.entityData(), record.widthMilli(), record.heightMilli(),
                    gameTime, record.sheared(), record.lastShearedGameTime()));
            return true;
        }
        return false;
    }

    public static boolean shear(AviationCrateBlockEntity crate, ItemStack shears, Player actor) {
        for (CreatureRecord record : crate.records()) {
            if (!record.entityType().equals("minecraft:sheep") || record.juvenile()
                    || CreatureNbt.sheared(record)) continue;
            int amount = 1 + actor.getRandom().nextInt(3);
            ItemStack wool = new ItemStack(woolFor(record), amount);
            if (!canStore(crate, List.of(wool))) return false;
            ItemHandlerHelper.insertItemStacked(crate.outputInventory(), wool, false);
            crate.replaceRecord(new CreatureRecord(record.recordId(), record.entityType(), record.profileId(),
                    record.juvenile(), record.ageTicks(), record.healthMilli(), record.revision() + 1, record.trades(),
                    CreatureNbt.withSheared(record, true), record.widthMilli(), record.heightMilli(),
                    record.lastMilkGameTime(), true, actor.level().getGameTime()));
            shears.hurtAndBreak(1, actor, net.minecraft.world.entity.LivingEntity.getSlotForHand(
                    net.minecraft.world.InteractionHand.MAIN_HAND));
            return true;
        }
        return false;
    }

    private static Item woolFor(CreatureRecord record) {
        net.minecraft.nbt.CompoundTag data = CreatureNbt.parse(record);
        if (!data.contains("Color", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) return Items.WHITE_WOOL;
        return WOOL_BY_COLOR[Math.clamp(data.getInt("Color"), 0, 15)];
    }

    private static boolean canStore(AviationCrateBlockEntity crate, List<ItemStack> drops) {
        ItemStack[] simulated = new ItemStack[crate.outputInventory().getSlots()];
        for (int slot = 0; slot < simulated.length; slot++) simulated[slot] = crate.outputInventory().getStackInSlot(slot).copy();
        for (ItemStack drop : drops) {
            ItemStack remainder = drop.copy();
            for (int slot = 0; slot < simulated.length && !remainder.isEmpty(); slot++) {
                ItemStack current = simulated[slot];
                if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, remainder)) continue;
                int room = Math.min(current.isEmpty() ? remainder.getMaxStackSize() : current.getMaxStackSize(),
                        crate.outputInventory().getSlotLimit(slot)) - current.getCount();
                if (room <= 0) continue;
                int moved = Math.min(room, remainder.getCount());
                if (current.isEmpty()) simulated[slot] = remainder.copyWithCount(moved);
                else current.grow(moved);
                remainder.shrink(moved);
            }
            if (!remainder.isEmpty()) return false;
        }
        return true;
    }

    private static void store(AviationCrateBlockEntity crate, List<ItemStack> drops) {
        for (ItemStack drop : drops) ItemHandlerHelper.insertItemStacked(crate.outputInventory(), drop.copy(), false);
    }

    private static List<ItemStack> slaughterDrops(AviationCrateBlockEntity crate, CreatureRecord record, Player actor) {
        if (crate.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            net.minecraft.world.entity.Entity entity = CreatureReleaseService.create(level, record);
            if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                var source = living.damageSources().playerAttack(actor);
                var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, living)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                                net.minecraft.world.phys.Vec3.atCenterOf(crate.getBlockPos()))
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE, source)
                        .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ATTACKING_ENTITY, actor)
                        .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DIRECT_ATTACKING_ENTITY, actor)
                        .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.LAST_DAMAGE_PLAYER, actor)
                        .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENTITY);
                List<ItemStack> generated = level.getServer().reloadableRegistries()
                        .getLootTable(living.getLootTable()).getRandomItems(params);
                if (!generated.isEmpty()) return List.copyOf(generated);
            }
        }
        return fallbackDrops(record.entityType());
    }

    private static List<ItemStack> fallbackDrops(String type) {
        List<ItemStack> drops = new ArrayList<>();
        switch (type) {
            case "minecraft:cow", "minecraft:mooshroom" -> {
                drops.add(new ItemStack(Items.BEEF, 2));
                drops.add(new ItemStack(Items.LEATHER, 1));
            }
            case "minecraft:sheep" -> {
                drops.add(new ItemStack(Items.MUTTON, 1));
                drops.add(new ItemStack(Items.WHITE_WOOL));
            }
            case "minecraft:pig" -> drops.add(new ItemStack(Items.PORKCHOP, 2));
            case "minecraft:chicken" -> {
                drops.add(new ItemStack(Items.CHICKEN));
                drops.add(new ItemStack(Items.FEATHER, 2));
            }
            case "minecraft:rabbit" -> {
                drops.add(new ItemStack(Items.RABBIT));
                drops.add(new ItemStack(Items.RABBIT_HIDE));
            }
            case "minecraft:goat" -> drops.add(new ItemStack(Items.MUTTON, 2));
            default -> { }
        }
        return drops;
    }
}

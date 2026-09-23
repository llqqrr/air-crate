package com.llqqrr.aircrate.content.creature;

import com.llqqrr.aircrate.registry.ACDataComponents;
import com.llqqrr.aircrate.registry.ACItems;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.equipment.armor.BacktankItem;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * Reusable carrier that converts one live creature directly into a creature parcel,
 * and releases parcelled creatures again. Runs on Create compressed air: one full
 * backtank worth of air = {@value #MAX_CAPTURES} shots, refilled by sipping from a
 * worn backtank (which itself is recharged by placing it powered, as in Create).
 */
public final class CreatureIntakeFanItem extends Item {
    public static final int MAX_CAPTURES = 8;
    /** One shot recharges over this many ticks while a worn backtank has air. */
    private static final int RECHARGE_TICKS_PER_SHOT = 10;

    public CreatureIntakeFanItem(Properties properties) {
        super(properties);
    }

    public static int maxAir() {
        return BacktankUtil.maxAirWithoutEnchants();
    }

    public static int airPerCapture() {
        return maxAir() / MAX_CAPTURES;
    }

    public static int getAir(ItemStack stack) {
        return stack.getOrDefault(ACDataComponents.AIR.get(), 0);
    }

    public static void setAir(ItemStack stack, int air) {
        stack.set(ACDataComponents.AIR.get(), Mth.clamp(air, 0, maxAir()));
    }

    public static float airFraction(ItemStack stack) {
        return Mth.clamp(getAir(stack) / (float) maxAir(), 0, 1);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof LivingEntity living)) return;
        int deficit = maxAir() - getAir(stack);
        if (deficit <= 0) return;
        int wanted = Math.min(deficit, Math.max(1, airPerCapture() / RECHARGE_TICKS_PER_SHOT));
        for (ItemStack backtank : BacktankUtil.getAllWithAir(living)) {
            int move = Math.min(wanted, BacktankUtil.getAir(backtank));
            if (move <= 0) continue;
            BacktankUtil.consumeAir(living, backtank, move);
            setAir(stack, getAir(stack) + move);
            return;
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getAir(stack) < maxAir();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13 * airFraction(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BacktankItem.BAR_COLOR;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                   InteractionHand hand) {
        if (!CreatureCaptureService.isSupported(target)) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.CONSUME;
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(player.level() instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        if (getAir(stack) < airPerCapture()) {
            dryFire(serverLevel, player.blockPosition());
            return InteractionResult.CONSUME;
        }
        scheduleCapture(serverPlayer, serverLevel, target, hand);
        return InteractionResult.CONSUME;
    }

    /**
     * Fires the tongue first and packs the creature only when the tongue snaps back
     * (extend 3 ticks + hold 2 ticks), so the catch reads as the tongue yanking the
     * creature into the gun instead of an instant delete.
     */
    public static void scheduleCapture(ServerPlayer serverPlayer, ServerLevel serverLevel,
                                       LivingEntity target, InteractionHand hand) {
        com.llqqrr.aircrate.network.AirCrateNetwork.gunShot(serverPlayer, false, ItemStack.EMPTY);
        // Frogport sounds follow the capture animation: mouth opens on fire,
        // catch snaps when the tongue retracts, mouth closes as the cycle ends.
        AllSoundEvents.FROGPORT_OPEN.playOnServer(serverLevel, serverPlayer.blockPosition());
        java.util.UUID targetId = target.getUUID();
        serverLevel.getServer().tell(new net.minecraft.server.TickTask(
                serverLevel.getServer().getTickCount() + 5, () -> {
            if (!(serverLevel.getEntity(targetId) instanceof LivingEntity living) || !living.isAlive()) return;
            ItemStack gun = serverPlayer.getItemInHand(hand);
            if (!gun.is(ACItems.CREATURE_INTAKE_FAN.get()) || getAir(gun) < airPerCapture()) return;
            ItemStack parcel = CreatureCaptureService.asParcel(living);
            if (parcel.isEmpty() || CreatureParcelItem.record(parcel) == null) return;
            boolean placed = !CreatureContainerGuard.hasParcel(serverPlayer)
                    && serverPlayer.getInventory().add(parcel);
            if (!placed) {
                net.minecraft.world.entity.item.ItemEntity dropped = serverPlayer.drop(parcel, false);
                placed = dropped != null;
            }
            if (!placed) return;
            setAir(gun, getAir(gun) - airPerCapture());
            living.discard();
            AllSoundEvents.FROGPORT_CATCH.playOnServer(serverLevel, serverPlayer.blockPosition());
        }));
        serverLevel.getServer().tell(new net.minecraft.server.TickTask(
                serverLevel.getServer().getTickCount() + 10, () ->
                AllSoundEvents.FROGPORT_CLOSE.playOnServer(serverLevel, serverPlayer.blockPosition())));
    }

    /** Right-click on nothing: spit the first creature parcel in the inventory back out. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.consume(gun);
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel))
            return InteractionResultHolder.consume(gun);
        if (getAir(gun) < airPerCapture()) {
            dryFire(serverLevel, player.blockPosition());
            return InteractionResultHolder.consume(gun);
        }
        int parcelSlot = findParcelSlot(player);
        if (parcelSlot < 0) {
            player.displayClientMessage(Component.translatable("message.aircrate.gun_no_parcel"), true);
            return InteractionResultHolder.consume(gun);
        }
        ItemStack parcel = player.getInventory().getItem(parcelSlot);
        CreatureRecord record = CreatureParcelItem.record(parcel);
        if (record == null) return InteractionResultHolder.consume(gun);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        BlockPos releasePos = hit.getBlockPos().relative(hit.getDirection());

        // Consume the parcel up front; if the release fails at tongue-retract time, give it back.
        ItemStack shown = parcel.copyWithCount(1);
        parcel.shrink(1);
        com.llqqrr.aircrate.network.AirCrateNetwork.gunShot(serverPlayer, true, shown);
        AllSoundEvents.FROGPORT_OPEN.playOnServer(serverLevel, player.blockPosition());
        serverLevel.getServer().tell(new net.minecraft.server.TickTask(
                serverLevel.getServer().getTickCount() + 5, () -> {
            ItemStack current = serverPlayer.getItemInHand(hand);
            if (!current.is(ACItems.CREATURE_INTAKE_FAN.get()) || getAir(current) < airPerCapture()
                    || !CreatureReleaseService.release(serverLevel, releasePos, record)) {
                giveBack(serverPlayer, shown);
                return;
            }
            setAir(current, getAir(current) - airPerCapture());
            AllSoundEvents.FROGPORT_CATCH.playOnServer(serverLevel, serverPlayer.blockPosition());
        }));
        serverLevel.getServer().tell(new net.minecraft.server.TickTask(
                serverLevel.getServer().getTickCount() + 10, () ->
                AllSoundEvents.FROGPORT_CLOSE.playOnServer(serverLevel, player.blockPosition())));
        return InteractionResultHolder.consume(gun);
    }

    private static int findParcelSlot(Player player) {
        if (CreatureParcelItem.isCreatureParcel(player.getOffhandItem())) return 40; // offhand slot id
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (CreatureParcelItem.isCreatureParcel(player.getInventory().getItem(slot))) return slot;
        }
        return -1;
    }

    private static void giveBack(ServerPlayer player, ItemStack parcel) {
        if (!player.getInventory().add(parcel)) player.drop(parcel, false);
    }

    private static void dryFire(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, .6F, 1.4F);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.aircrate.creature_carrier")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.aircrate.creature_carrier_air",
                getAir(stack) / airPerCapture(), MAX_CAPTURES).withStyle(net.minecraft.ChatFormatting.AQUA));
    }
}

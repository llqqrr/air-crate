package com.llqqrr.aircrate.content.resonator;

import com.llqqrr.aircrate.content.frogport.CreatureFrogportBlockEntity;
import com.llqqrr.aircrate.registry.ACBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class AmethystSculkResonatorBlockEntity extends KineticBlockEntity {
    public static final int RANGE = 15;
    public static final float REQUIRED_SPEED = 256F;
    public static final float IMPACT_PER_FROGPORT = 32F;
    private int boundFrogports;
    private int scanCooldown;

    public AmethystSculkResonatorBlockEntity(BlockPos pos, BlockState state) {
        super(ACBlockEntities.AMETHYST_SCULK_RESONATOR.get(), pos, state);
    }

    public boolean operational() {
        return level != null && Math.abs(getSpeed()) >= REQUIRED_SPEED && !isOverStressed()
                && !level.hasNeighborSignal(worldPosition);
    }

    @Override public float calculateStressApplied() {
        float impact = boundFrogports * IMPACT_PER_FROGPORT;
        lastStressApplied = impact;
        return impact;
    }

    public static void tick(Level level, BlockPos pos, AmethystSculkResonatorBlockEntity resonator) {
        resonator.tick();
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) return;
        boolean active = resonator.operational();
        BlockState state = resonator.getBlockState();
        if (state.hasProperty(AmethystSculkResonatorBlock.ACTIVE)
                && state.getValue(AmethystSculkResonatorBlock.ACTIVE) != active)
            level.setBlock(pos, state.setValue(AmethystSculkResonatorBlock.ACTIVE, active), 3);
        if (++resonator.scanCooldown < 40) return;
        resonator.scanCooldown = 0;
        int previous = resonator.boundFrogports;
        resonator.boundFrogports = resonator.countBoundFrogports(serverLevel);
        if (previous != resonator.boundFrogports && resonator.hasNetwork()) {
            resonator.getOrCreateNetwork().updateStressFor(resonator, resonator.calculateStressApplied());
        }
        if (resonator.operational() && serverLevel.getGameTime() % 80L == 0L) resonator.emitWaves(serverLevel);
    }

    private int countBoundFrogports(ServerLevel level) {
        if (level.hasNeighborSignal(worldPosition) || getSpeed() == 0) return 0;
        int count = 0;
        for (BlockPos candidate : BlockPos.betweenClosed(worldPosition.offset(-RANGE, -RANGE, -RANGE),
                worldPosition.offset(RANGE, RANGE, RANGE))) {
            if (candidate.distSqr(worldPosition) > RANGE * RANGE || !level.isLoaded(candidate)) continue;
            if (!(level.getBlockEntity(candidate) instanceof CreatureFrogportBlockEntity frogport)
                    || frogport.selection() == null || level.hasNeighborSignal(candidate)) continue;
            AmethystSculkResonatorBlockEntity nearest = findNearest(level, candidate, false);
            if (nearest == this) count++;
        }
        return count;
    }

    private void emitWaves(ServerLevel level) {
        level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                net.minecraft.sounds.SoundSource.BLOCKS, .7F, 1.1F);
        for (BlockPos candidate : BlockPos.betweenClosed(worldPosition.offset(-RANGE, -RANGE, -RANGE),
                worldPosition.offset(RANGE, RANGE, RANGE))) {
            if (!level.isLoaded(candidate)) continue;
            if (!(level.getBlockEntity(candidate) instanceof CreatureFrogportBlockEntity)) continue;
            if (findNearest(level, candidate, true) != this) continue;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                    candidate.getX() + .5, candidate.getY() + .7, candidate.getZ() + .5,
                    2, .05, .05, .05, 0.01);
        }
    }

    public static AmethystSculkResonatorBlockEntity findOperational(ServerLevel level, BlockPos frogport) {
        return findNearest(level, frogport, true);
    }

    private static AmethystSculkResonatorBlockEntity findNearest(ServerLevel level, BlockPos origin,
                                                                  boolean requireOperational) {
        AmethystSculkResonatorBlockEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-RANGE, -RANGE, -RANGE),
                origin.offset(RANGE, RANGE, RANGE))) {
            double distance = candidate.distSqr(origin);
            if (distance > RANGE * RANGE || distance >= nearestDistance || !level.isLoaded(candidate)) continue;
            if (!(level.getBlockEntity(candidate) instanceof AmethystSculkResonatorBlockEntity resonator)) continue;
            if (requireOperational ? !resonator.operational()
                    : resonator.getSpeed() == 0 || level.hasNeighborSignal(candidate)) continue;
            nearest = resonator;
            nearestDistance = distance;
        }
        return nearest;
    }
}

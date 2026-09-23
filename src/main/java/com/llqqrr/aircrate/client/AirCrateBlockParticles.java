package com.llqqrr.aircrate.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;

/** Uses the same terrain-particle distribution as vanilla block destruction. */
public final class AirCrateBlockParticles implements IClientBlockExtensions {
    public static final AirCrateBlockParticles INSTANCE = new AirCrateBlockParticles();

    private AirCrateBlockParticles() { }

    @Override
    public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine engine) {
        if (!(level instanceof ClientLevel clientLevel)) return false;
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) return false;

        RandomSource random = RandomSource.create();
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            int countX = Math.max(2, Mth.ceil((maxX - minX) / 0.25D));
            int countY = Math.max(2, Mth.ceil((maxY - minY) / 0.25D));
            int countZ = Math.max(2, Mth.ceil((maxZ - minZ) / 0.25D));
            for (int x = 0; x < countX; x++) {
                for (int y = 0; y < countY; y++) {
                    for (int z = 0; z < countZ; z++) {
                        double px = pos.getX() + minX + (x + 0.5D) / countX * (maxX - minX);
                        double py = pos.getY() + minY + (y + 0.5D) / countY * (maxY - minY);
                        double pz = pos.getZ() + minZ + (z + 0.5D) / countZ * (maxZ - minZ);
                        // A small deterministic-looking jitter keeps large custom boxes from
                        // producing a visibly rigid grid while retaining vanilla density.
                        px += (random.nextDouble() - 0.5D) * 0.08D;
                        py += (random.nextDouble() - 0.5D) * 0.08D;
                        pz += (random.nextDouble() - 0.5D) * 0.08D;
                        engine.add(new TerrainParticle(clientLevel, px, py, pz, 0, 0, 0, state, pos)
                                .updateSprite(state, pos).setPower(0.2F).scale(0.6F));
                    }
                }
            }
        });
        return true;
    }
}

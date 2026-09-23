package com.llqqrr.aircrate.content.frogport;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public record CreatureAreaSelection(BlockPos first, BlockPos second, boolean complete) {
    public CreatureAreaSelection(BlockPos first, BlockPos second) {
        this(first, second, true);
    }

    public static Codec<CreatureAreaSelection> codec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("first").forGetter(CreatureAreaSelection::first),
                BlockPos.CODEC.fieldOf("second").forGetter(CreatureAreaSelection::second),
                Codec.BOOL.optionalFieldOf("complete", true).forGetter(CreatureAreaSelection::complete)
        ).apply(instance, CreatureAreaSelection::new));
    }

    public BlockPos min() {
        return new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
    }

    public BlockPos max() {
        return new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));
    }

    public boolean validSize() {
        BlockPos min = min();
        BlockPos max = max();
        return max.getX() - min.getX() < 5 && max.getY() - min.getY() < 5 && max.getZ() - min.getZ() < 5;
    }

    public boolean inRangeOf(BlockPos frogport) {
        BlockPos min = min();
        BlockPos max = max();
        return Math.max(Math.max(Math.abs(frogport.getX() - min.getX()), Math.abs(frogport.getX() - max.getX())),
                Math.max(Math.max(Math.abs(frogport.getY() - min.getY()), Math.abs(frogport.getY() - max.getY())),
                        Math.max(Math.abs(frogport.getZ() - min.getZ()), Math.abs(frogport.getZ() - max.getZ())))) <= 4;
    }

    public AABB bounds() {
        BlockPos min = min();
        BlockPos max = max();
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }
}

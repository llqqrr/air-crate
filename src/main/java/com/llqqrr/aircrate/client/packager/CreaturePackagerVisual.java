package com.llqqrr.aircrate.client.packager;

import com.llqqrr.aircrate.content.packager.CreaturePackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** Flywheel visual mirroring Create's PackagerVisual with this mod's hatch/tray partials. */
public final class CreaturePackagerVisual extends AbstractBlockEntityVisual<CreaturePackagerBlockEntity>
        implements SimpleDynamicVisual {
    private final TransformedInstance hatch;
    private final TransformedInstance tray;

    private float lastTrayOffset = Float.NaN;
    private PartialModel lastHatchPartial;

    public CreaturePackagerVisual(VisualizationContext ctx, CreaturePackagerBlockEntity blockEntity,
                                  float partialTick) {
        super(ctx, blockEntity, partialTick);

        lastHatchPartial = CreaturePackagerRenderer.getHatchModel(blockEntity);
        hatch = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(lastHatchPartial))
                .createInstance();
        tray = instancerProvider().instancer(InstanceTypes.TRANSFORMED,
                Models.partial(CreaturePackagerRenderer.TRAY)).createInstance();

        Direction facing = blockState.getValue(PackagerBlock.FACING).getOpposite();
        Vec3 lowerCorner = Vec3.atLowerCornerOf(facing.getNormal());
        hatch.setIdentityTransform()
                .translate(getVisualPosition())
                .translate(lowerCorner.scale(.49999f))
                .rotateYCenteredDegrees(AngleHelper.horizontalAngle(facing))
                .rotateXCenteredDegrees(AngleHelper.verticalAngle(facing))
                .setChanged();

        animate(partialTick);
    }

    @Override
    public void beginFrame(Context ctx) {
        animate(ctx.partialTick());
    }

    private void animate(float partialTick) {
        PartialModel hatchPartial = CreaturePackagerRenderer.getHatchModel(blockEntity);
        if (hatchPartial != lastHatchPartial) {
            instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(hatchPartial))
                    .stealInstance(hatch);
            lastHatchPartial = hatchPartial;
        }

        float trayOffset = blockEntity.getTrayOffset(partialTick);
        if (trayOffset != lastTrayOffset) {
            Direction facing = blockState.getValue(PackagerBlock.FACING).getOpposite();
            Vec3 lowerCorner = Vec3.atLowerCornerOf(facing.getNormal());
            tray.setIdentityTransform()
                    .translate(getVisualPosition())
                    .translate(lowerCorner.scale(trayOffset))
                    .rotateYCenteredDegrees(facing.toYRot())
                    .setChanged();
            lastTrayOffset = trayOffset;
        }
    }

    @Override
    public void updateLight(float partialTick) {
        relight(hatch, tray);
    }

    @Override
    protected void _delete() {
        hatch.delete();
        tray.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
    }
}

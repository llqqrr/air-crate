package com.llqqrr.aircrate.client.packager;

import com.llqqrr.aircrate.content.packager.CreaturePackagerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Create packager renderer variant that uses this mod's brass hatch/tray partials. */
public final class CreaturePackagerRenderer extends SmartBlockEntityRenderer<CreaturePackagerBlockEntity> {
    public static final PartialModel HATCH_CLOSED =
            PartialModel.of(ResourceLocation.fromNamespaceAndPath("aircrate", "block/creature_packager/hatch_closed"));
    public static final PartialModel HATCH_OPEN =
            PartialModel.of(ResourceLocation.fromNamespaceAndPath("aircrate", "block/creature_packager/hatch_open"));
    public static final PartialModel TRAY =
            PartialModel.of(ResourceLocation.fromNamespaceAndPath("aircrate", "block/creature_packager/tray"));

    public CreaturePackagerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(CreaturePackagerBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        ItemStack renderedBox = be.getRenderedBox();
        float trayOffset = be.getTrayOffset(partialTicks);
        BlockState blockState = be.getBlockState();
        Direction facing = blockState.getValue(PackagerBlock.FACING).getOpposite();

        if (!VisualizationManager.supportsVisualization(be.getLevel())) {
            SuperByteBuffer sbb = CachedBuffers.partial(getHatchModel(be), blockState);
            sbb.translate(Vec3.atLowerCornerOf(facing.getNormal()).scale(.49999f))
                    .rotateYCenteredDegrees(AngleHelper.horizontalAngle(facing))
                    .rotateXCenteredDegrees(AngleHelper.verticalAngle(facing))
                    .light(light)
                    .renderInto(ms, buffer.getBuffer(RenderType.solid()));

            sbb = CachedBuffers.partial(TRAY, blockState);
            sbb.translate(Vec3.atLowerCornerOf(facing.getNormal()).scale(trayOffset))
                    .rotateYCenteredDegrees(facing.toYRot())
                    .light(light)
                    .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
        }

        if (!renderedBox.isEmpty()) {
            ms.pushPose();
            var msr = TransformStack.of(ms);
            msr.translate(Vec3.atLowerCornerOf(facing.getNormal()).scale(trayOffset))
                    .translate(.5f, .5f, .5f)
                    .rotateYDegrees(facing.toYRot())
                    .translate(0, 2 / 16f, 0)
                    .scale(1.49f, 1.49f, 1.49f);
            Minecraft.getInstance().getItemRenderer().renderStatic(null, renderedBox, ItemDisplayContext.FIXED,
                    false, ms, buffer, be.getLevel(), light, overlay, 0);
            ms.popPose();
        }
    }

    public static PartialModel getHatchModel(PackagerBlockEntity be) {
        return isHatchOpen(be) ? HATCH_OPEN : HATCH_CLOSED;
    }

    public static boolean isHatchOpen(PackagerBlockEntity be) {
        return be.animationTicks > (be.animationInward ? 1 : 5)
                && be.animationTicks < PackagerBlockEntity.CYCLE - (be.animationInward ? 5 : 1);
    }
}

package com.llqqrr.aircrate.client;

import com.llqqrr.aircrate.AirCrate;
import com.llqqrr.aircrate.content.frogport.CreatureAreaSelection;
import com.llqqrr.aircrate.content.frogport.CreatureFrogportBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Create's frogport animation using Air Crate partial models and creature selection targets. */
public final class CreatureFrogportRenderer extends SmartBlockEntityRenderer<CreatureFrogportBlockEntity> {
    private static final PartialModel BODY = partial("creature_frogport_body");
    private static final PartialModel HEAD = partial("creature_frogport_head");
    private static final PartialModel TONGUE = partial("creature_frogport_tongue");
    private static final PartialModel BODY_GLOW = partial("creature_frogport_body_glow");
    private static final PartialModel HEAD_GLOW = partial("creature_frogport_head_glow");

    public CreatureFrogportRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    /** Called during mod construction so Flywheel bakes these models with Create's partials. */
    public static void initPartials() {
        // Class initialization creates the PartialModel registrations.
    }

    private static PartialModel partial(String name) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(AirCrate.MOD_ID, "block/" + name));
    }

    @Override
    protected void renderSafe(CreatureFrogportBlockEntity frogport, float partialTicks, PoseStack pose,
                              MultiBufferSource buffers, int light, int overlay) {
        float yaw = frogport.getYaw();
        float headPitch = 80;
        float tonguePitch = 0;
        float tongueLength = 0;
        float headPitchModifier = 1;
        boolean animating = frogport.isAnimationInProgress();
        boolean depositing = frogport.currentlyDepositing;
        Vec3 targetDelta = selectionDelta(frogport);

        if (targetDelta.lengthSqr() > 0) {
            tonguePitch = (float) Mth.atan2(targetDelta.y,
                    targetDelta.multiply(1, 0, 1).length() + .1875) * Mth.RAD_TO_DEG;
            tongueLength = Math.max((float) targetDelta.length(), 1);
            headPitch = Mth.clamp(tonguePitch * 2, 60, 100);
        }

        if (animating) {
            float progress = frogport.animationProgress.getValue(partialTicks);
            float scale;
            float itemDistance;
            if (depositing) {
                double modifier = Math.max(0, 1 - Math.pow((progress - .25) * 4 - 1, 4));
                itemDistance = (float) Math.max(tongueLength * Math.min(1, (progress - .25) * 3),
                        tongueLength * modifier);
                tongueLength *= (float) Math.max(0,
                        1 - Math.pow((progress * 1.25 - .25) * 4 - 1, 4));
                headPitchModifier = (float) Math.max(0, 1 - Math.pow(progress * 2.5 - 1, 4));
                scale = .25f + progress * .75f;
            } else {
                tongueLength *= (float) Math.pow(Math.max(0, 1 - progress * 1.25), 5);
                headPitchModifier = 1 - (float) Math.min(1,
                        Math.max(0, (Math.pow(progress * 1.5, 2) - .5) * 2));
                scale = (float) Math.max(.5, 1 - progress * 1.25);
                itemDistance = tongueLength;
            }
            renderPackage(frogport, pose, buffers, light, overlay, targetDelta, scale, itemDistance);
        } else {
            tongueLength = 0;
            float anticipation = frogport.anticipationProgress.getValue(partialTicks);
            headPitchModifier = anticipation > 0
                    ? (float) Math.max(0, 1 - Math.pow(anticipation * 2.5 - 1, 4)) : 0;
        }

        headPitch *= headPitchModifier;
        float manualOpen = frogport.manualOpenAnimationProgress.getValue(partialTicks);
        headPitch = Math.max(headPitch, manualOpen * 60);
        tongueLength = Math.max(tongueLength, manualOpen * .25f);

        SuperByteBuffer body = CachedBuffers.partial(BODY, frogport.getBlockState());
        ((SuperByteBuffer) ((SuperByteBuffer) body.center()).rotateYDegrees(yaw)).uncenter();
        render(body, pose, buffers, RenderType.cutoutMipped(), light, overlay);

        SuperByteBuffer head = CachedBuffers.partial(HEAD, frogport.getBlockState());
        ((SuperByteBuffer) ((SuperByteBuffer) head.center()).rotateYDegrees(yaw)).uncenter();
        ((SuperByteBuffer) head.translate(.5f, .625f, .6875f)).rotateXDegrees(headPitch);
        head.translateBack(.5f, .625f, .6875f);
        render(head, pose, buffers, RenderType.cutoutMipped(), light, overlay);

        SuperByteBuffer tongue = CachedBuffers.partial(TONGUE, frogport.getBlockState());
        ((SuperByteBuffer) ((SuperByteBuffer) tongue.center()).rotateYDegrees(yaw)).uncenter();
        ((SuperByteBuffer) tongue.translate(.5f, .625f, .6875f)).rotateXDegrees(tonguePitch);
        tongue.scale(1, 1, tongueLength / .4375f);
        tongue.translateBack(.5f, .625f, .6875f);
        render(tongue, pose, buffers, RenderType.cutoutMipped(), light, overlay);

        if (frogport.getBlockState().getValue(
                com.llqqrr.aircrate.content.frogport.CreatureFrogportBlock.ACTIVE)) {
            SuperByteBuffer bodyGlow = CachedBuffers.partial(BODY_GLOW, frogport.getBlockState());
            ((SuperByteBuffer) ((SuperByteBuffer) bodyGlow.center()).rotateYDegrees(yaw)).uncenter();
            ((SuperByteBuffer) ((SuperByteBuffer) bodyGlow.center()).scale(1.005f)).uncenter();
            render(bodyGlow, pose, buffers, RenderType.cutout(), LightTexture.FULL_BRIGHT, overlay);

            SuperByteBuffer headGlow = CachedBuffers.partial(HEAD_GLOW, frogport.getBlockState());
            ((SuperByteBuffer) ((SuperByteBuffer) headGlow.center()).rotateYDegrees(yaw)).uncenter();
            ((SuperByteBuffer) headGlow.translate(.5f, .625f, .6875f)).rotateXDegrees(headPitch);
            headGlow.translateBack(.5f, .625f, .6875f);
            ((SuperByteBuffer) ((SuperByteBuffer) headGlow.center()).scale(1.005f)).uncenter();
            render(headGlow, pose, buffers, RenderType.cutout(), LightTexture.FULL_BRIGHT, overlay);
        }
    }

    private static Vec3 selectionDelta(CreatureFrogportBlockEntity frogport) {
        CreatureAreaSelection selection = frogport.selection();
        if (selection == null) return Vec3.ZERO;
        BlockPos min = selection.min();
        BlockPos max = selection.max();
        Vec3 target = new Vec3((min.getX() + max.getX() + 1) * .5,
                min.getY() + .5, (min.getZ() + max.getZ() + 1) * .5);
        return target.subtract(Vec3.atCenterOf(frogport.getBlockPos()));
    }

    private static void renderPackage(CreatureFrogportBlockEntity frogport, PoseStack pose,
                                      MultiBufferSource buffers, int light, int overlay, Vec3 delta,
                                      float scale, float itemDistance) {
        if (frogport.animatedPackage == null || scale < .45f) return;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(frogport.animatedPackage.getItem());
        PartialModel boxModel = AllPartialModels.PACKAGES.get(key);
        PartialModel rigModel = AllPartialModels.PACKAGE_RIGGING.get(key);
        if (boxModel == null) return;
        SuperByteBuffer box = CachedBuffers.partial(boxModel, frogport.getBlockState());
        SuperByteBuffer rig = rigModel == null ? null : CachedBuffers.partial(rigModel, frogport.getBlockState());
        SuperByteBuffer[] parts = frogport.currentlyDepositing && rig != null
                ? new SuperByteBuffer[]{box, rig} : new SuperByteBuffer[]{box};
        Vec3 travel = delta.lengthSqr() == 0 ? Vec3.ZERO : delta.normalize().scale(itemDistance);
        if (frogport.currentlyDepositing) travel = travel.subtract(0, .75, 0);
        for (SuperByteBuffer part : parts) {
            part.translate(0, .1875f, 0);
            part.translate(travel);
            ((SuperByteBuffer) part.center()).scale(scale);
            part.uncenter();
            render(part, pose, buffers, RenderType.cutout(), light, overlay);
        }
    }

    private static void render(SuperByteBuffer model, PoseStack pose, MultiBufferSource buffers,
                               RenderType type, int light, int overlay) {
        model.light(light).overlay(overlay).renderInto(pose, buffers.getBuffer(type));
    }
}

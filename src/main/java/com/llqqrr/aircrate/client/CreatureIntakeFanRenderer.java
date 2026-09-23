package com.llqqrr.aircrate.client;

import com.llqqrr.aircrate.registry.ACItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * Renders the packing gun: static body plus animated partials following GPT's
 * capture_cycle design (art/08-frog-catcher-v3): frog jaw opens 72 deg, tongue
 * snaps out, drive gears spin 540 deg, trigger pulls back, tail valve puffs.
 */
public final class CreatureIntakeFanRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ModelResourceLocation BASE_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath("aircrate", "item/creature_intake_fan_base"));
    private static final ModelResourceLocation TONGUE_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath("aircrate", "item/creature_intake_fan_tongue"));
    private static final ModelResourceLocation JAW_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath("aircrate", "item/creature_intake_fan_jaw"));
    private static final ModelResourceLocation GEARS_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath("aircrate", "item/creature_intake_fan_gears"));
    private static final ModelResourceLocation TRIGGER_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath("aircrate", "item/creature_intake_fan_trigger"));
    private static final ModelResourceLocation VALVE_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath("aircrate", "item/creature_intake_fan_valve"));
    private static final ModelResourceLocation GAUGE_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath("aircrate", "item/creature_intake_fan_gauge"));

    /** Tongue travel in model units (blocks), matching the firing variant's -6.72px offset. */
    private static final float TONGUE_TRAVEL = 6.72F / 16.0F;
    private static final int EXTEND_TICKS = 3;
    private static final int HOLD_TICKS = 2;
    private static final int RETRACT_TICKS = 6;
    private static final int TONGUE_TICKS = EXTEND_TICKS + HOLD_TICKS + RETRACT_TICKS;
    /** Gears keep spinning down a few ticks after the tongue is back. */
    private static final int TOTAL_TICKS = 15;

    /** Bone pivots from 08_frog_catcher_animated.bbmodel, converted to block units. */
    private static final float JAW_PIVOT_X = 8 / 16F, JAW_PIVOT_Y = 10.8F / 16F, JAW_PIVOT_Z = 2.3F / 16F;
    private static final float GEAR_PIVOT_X = 4.65F / 16F, GEAR_PIVOT_Y = 6.6F / 16F, GEAR_PIVOT_Z = 5.9F / 16F;
    private static final float TRIGGER_PIVOT_X = 8 / 16F, TRIGGER_PIVOT_Y = 6.1F / 16F, TRIGGER_PIVOT_Z = 8.5F / 16F;
    /** Gauge needle pivot and sweep from the bbmodel pressure_full_to_empty animation. */
    private static final float NEEDLE_PIVOT_X = 3.84F / 16F, NEEDLE_PIVOT_Y = 9.95F / 16F, NEEDLE_PIVOT_Z = 8.15F / 16F;
    private static final float NEEDLE_FULL = -70.0F, NEEDLE_EMPTY = 70.0F;
    private static final float JAW_SHIFT = 1.8F / 16F;
    private static final float VALVE_TRAVEL = 2.5F / 16F;

    private static CreatureIntakeFanRenderer instance;
    private static long captureGameTime = Long.MIN_VALUE;
    /** Parcel carried on the tongue during a release shot; empty for captures. */
    private static ItemStack releaseParcel = ItemStack.EMPTY;

    private CreatureIntakeFanRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static void register(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        instance = new CreatureIntakeFanRenderer();
        event.registerItem(new IClientItemExtensions() {
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return instance; }
        }, ACItems.CREATURE_INTAKE_FAN.get());
    }

    /** Called client-side when the local player fires the packing gun. */
    public static void triggerShot(boolean release, ItemStack parcel) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) captureGameTime = minecraft.level.getGameTime();
        releaseParcel = release ? parcel.copy() : ItemStack.EMPTY;
    }

    private static float elapsed(long gameTime, float partialTick) {
        float t = gameTime - captureGameTime + partialTick;
        return t < 0 || t >= TOTAL_TICKS ? -1 : t;
    }

    private static float tongueProgress(float t) {
        if (t < 0 || t >= TONGUE_TICKS) return 0.0F;
        if (t < EXTEND_TICKS) return smooth(t / EXTEND_TICKS);
        if (t < EXTEND_TICKS + HOLD_TICKS) return 1.0F;
        return 1.0F - smooth((t - EXTEND_TICKS - HOLD_TICKS) / RETRACT_TICKS);
    }

    /** Mouth opens fast, stays open through the catch, closes afterwards. */
    private static float jawOpen(float t) {
        if (t < 1 || t >= 13) return 0.0F;
        if (t < 4) return smooth((t - 1) / 3.0F);
        if (t < 9) return 1.0F;
        return 1.0F - smooth((t - 9) / 4.0F);
    }

    private static float gearAngle(float t) {
        if (t < 2) return 0.0F;
        if (t < 11) return (t - 2) / 9.0F * 360.0F;
        return 360.0F + (t - 11) / 4.0F * 180.0F;
    }

    private static float triggerPull(float t) {
        if (t < 0 || t >= 11) return 0.0F;
        if (t < 2.5F) return smooth(t / 2.5F);
        if (t < 7) return 1.0F;
        return 1.0F - smooth((t - 7) / 4.0F);
    }

    private static float valvePuff(float t) {
        if (t < 9 || t >= 13) return 0.0F;
        if (t < 10.5F) return smooth((t - 9) / 1.5F);
        return 1.0F - smooth((t - 10.5F) / 2.5F);
    }

    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel base = minecraft.getModelManager().getModel(BASE_MODEL);
        BakedModel tongue = minecraft.getModelManager().getModel(TONGUE_MODEL);
        BakedModel jaw = minecraft.getModelManager().getModel(JAW_MODEL);
        BakedModel gears = minecraft.getModelManager().getModel(GEARS_MODEL);
        BakedModel trigger = minecraft.getModelManager().getModel(TRIGGER_MODEL);
        BakedModel valve = minecraft.getModelManager().getModel(VALVE_MODEL);

        boolean leftHand = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        poseStack.pushPose();
        // renderByItem is entered with translate(-0.5,-0.5,-0.5) already applied and no
        // display transform (builtin/entity). Undo it, apply the base model's transform,
        // then redo it — reproducing exactly how the unsplit item model rendered.
        poseStack.translate(0.5D, 0.5D, 0.5D);
        base.getTransforms().getTransform(displayContext).apply(leftHand, poseStack);
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        minecraft.getItemRenderer().renderModelLists(base, stack, packedLight, packedOverlay, poseStack,
                buffer.getBuffer(Sheets.cutoutBlockSheet()));

        long gameTime = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        float t = elapsed(gameTime, partialTick);

        if (t >= 0) {
            float jawAngle = 72.0F * jawOpen(t);
            if (jawAngle != 0.0F) {
                poseStack.pushPose();
                poseStack.translate(JAW_PIVOT_X, JAW_PIVOT_Y, JAW_PIVOT_Z);
                poseStack.mulPose(Axis.XP.rotationDegrees(jawAngle));
                poseStack.translate(-JAW_PIVOT_X, -JAW_PIVOT_Y, -JAW_PIVOT_Z);
                poseStack.translate(0.0D, 0.0D, -JAW_SHIFT * Math.sin(Math.toRadians(jawAngle)));
                renderPart(jaw, stack, poseStack, buffer, packedLight, packedOverlay);
                poseStack.popPose();
            } else {
                renderPart(jaw, stack, poseStack, buffer, packedLight, packedOverlay);
            }

            float spin = gearAngle(t);
            if (spin != 0.0F) {
                poseStack.pushPose();
                poseStack.translate(GEAR_PIVOT_X, GEAR_PIVOT_Y, GEAR_PIVOT_Z);
                poseStack.mulPose(Axis.XP.rotationDegrees(spin));
                poseStack.translate(-GEAR_PIVOT_X, -GEAR_PIVOT_Y, -GEAR_PIVOT_Z);
                renderPart(gears, stack, poseStack, buffer, packedLight, packedOverlay);
                poseStack.popPose();
            } else {
                renderPart(gears, stack, poseStack, buffer, packedLight, packedOverlay);
            }

            float pull = triggerPull(t);
            if (pull != 0.0F) {
                poseStack.pushPose();
                poseStack.translate(TRIGGER_PIVOT_X, TRIGGER_PIVOT_Y, TRIGGER_PIVOT_Z);
                poseStack.mulPose(Axis.XP.rotationDegrees(-16.0F * pull));
                poseStack.translate(-TRIGGER_PIVOT_X, -TRIGGER_PIVOT_Y, -TRIGGER_PIVOT_Z);
                renderPart(trigger, stack, poseStack, buffer, packedLight, packedOverlay);
                poseStack.popPose();
            } else {
                renderPart(trigger, stack, poseStack, buffer, packedLight, packedOverlay);
            }

            float puff = valvePuff(t);
            if (puff != 0.0F) {
                poseStack.pushPose();
                poseStack.translate(0.0D, 0.0D, VALVE_TRAVEL * puff);
                renderPart(valve, stack, poseStack, buffer, packedLight, packedOverlay);
                poseStack.popPose();
            } else {
                renderPart(valve, stack, poseStack, buffer, packedLight, packedOverlay);
            }
        } else {
            renderPart(jaw, stack, poseStack, buffer, packedLight, packedOverlay);
            renderPart(gears, stack, poseStack, buffer, packedLight, packedOverlay);
            renderPart(trigger, stack, poseStack, buffer, packedLight, packedOverlay);
            renderPart(valve, stack, poseStack, buffer, packedLight, packedOverlay);
        }

        // Gauge needle always reflects the tank level, whether or not a shot is playing.
        BakedModel gauge = minecraft.getModelManager().getModel(GAUGE_MODEL);
        float fraction = com.llqqrr.aircrate.content.creature.CreatureIntakeFanItem.airFraction(stack);
        float needle = NEEDLE_FULL + (1.0F - fraction) * (NEEDLE_EMPTY - NEEDLE_FULL);
        poseStack.pushPose();
        poseStack.translate(NEEDLE_PIVOT_X, NEEDLE_PIVOT_Y, NEEDLE_PIVOT_Z);
        poseStack.mulPose(Axis.XP.rotationDegrees(needle));
        poseStack.translate(-NEEDLE_PIVOT_X, -NEEDLE_PIVOT_Y, -NEEDLE_PIVOT_Z);
        renderPart(gauge, stack, poseStack, buffer, packedLight, packedOverlay);
        poseStack.popPose();

        float progress = t < 0 ? 0.0F : tongueProgress(t);
        if (progress > 0.0F) {
            poseStack.translate(0.0D, 0.0D, -TONGUE_TRAVEL * progress);
        }
        renderPart(tongue, stack, poseStack, buffer, packedLight, packedOverlay);

        // Release shots: the parcel rides the fully extended tongue, then the creature
        // pops out of it server-side around t=5.
        if (!releaseParcel.isEmpty() && t >= EXTEND_TICKS && t < EXTEND_TICKS + HOLD_TICKS + 0.5F) {
            poseStack.pushPose();
            poseStack.translate(0.5D, 10.3D / 16.0D, -2.02D / 16.0D + 0.12D);
            poseStack.scale(0.4F, 0.4F, 0.4F);
            minecraft.getItemRenderer().renderStatic(releaseParcel, ItemDisplayContext.GROUND,
                    packedLight, packedOverlay, poseStack, buffer, minecraft.level, 0);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private void renderPart(BakedModel model, ItemStack stack, PoseStack poseStack,
                            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Minecraft.getInstance().getItemRenderer().renderModelLists(model, stack, packedLight, packedOverlay,
                poseStack, buffer.getBuffer(Sheets.cutoutBlockSheet()));
    }
}

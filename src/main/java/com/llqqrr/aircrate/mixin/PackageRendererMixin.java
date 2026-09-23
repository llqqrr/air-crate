package com.llqqrr.aircrate.mixin;

import com.llqqrr.aircrate.client.CreatureEntityRenderer;
import com.llqqrr.aircrate.content.creature.CreatureParcelItem;
import com.llqqrr.aircrate.content.creature.CreatureRecord;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces Create's opaque solid package mesh for creature parcels. */
@Mixin(value = PackageRenderer.class, remap = false)
abstract class PackageRendererMixin {
    // Keep renderer geometry inside the 12 px parcel shell. Entity hit boxes do
    // not include every visual part (pig snouts, ears, hats, etc.).
    private static final float PARCEL_MAX_WIDTH = 0.42F;
    private static final float PARCEL_MAX_HEIGHT = 0.48F;
    private static final float PARCEL_SAFETY = 0.72F;
    private static final ModelResourceLocation BOX_MODEL = ModelResourceLocation.standalone(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aircrate", "item/creature_parcel_box"));

    /**
     * Iris wraps the buffer passed to every entity renderer in its public
     * BufferSourceWrapper.  That wrapper rewrites *all* requested RenderTypes,
     * including block cutout, into the Iris entity pass.  The air-crate shell is
     * an item/block model and must not inherit that pass.  Keep this optional:
     * Iris is not a compile/runtime dependency of the mod.
     */
    private static MultiBufferSource aircrate$unwrapEntityBuffer(MultiBufferSource source) {
        MultiBufferSource current = source;
        for (int depth = 0; depth < 4; depth++) {
            try {
                java.lang.reflect.Method getOriginal = current.getClass().getMethod("getOriginal");
                Object original = getOriginal.invoke(current);
                if (!(original instanceof MultiBufferSource next) || next == current) return current;
                current = next;
            } catch (ReflectiveOperationException | SecurityException ignored) {
                return current;
            }
        }
        return current;
    }

    @Inject(method = "render(Lcom/simibubi/create/content/logistics/box/PackageEntity;FF"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void aircrate$renderCreatureParcel(PackageEntity packageEntity, float entityYaw, float partialTick,
                                                 PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                                 CallbackInfo callback) {
        // PackageEntity extends LivingEntity and Create ticks it through LivingEntity.tick().
        // Do not let a stale damage state leak into Iris/Flywheel's entity render context.
        // This is client-render hygiene only; gameplay damage and the server-side state are
        // untouched because this mixin is client-only.
        packageEntity.hurtTime = 0;
        packageEntity.hurtDuration = 0;
        packageEntity.deathTime = 0;

        ItemStack box = packageEntity.box;
        CreatureRecord record = CreatureParcelItem.record(box);
        if (record == null) return;

        Entity creature = CreatureEntityRenderer.displayEntity(record);
        if (creature != null) {
            poseStack.pushPose();
            float width = Math.max(0.2F, creature.getBbWidth());
            float height = Math.max(0.2F, creature.getBbHeight());
            float scale = PARCEL_SAFETY * Math.min(PARCEL_MAX_WIDTH / width, PARCEL_MAX_HEIGHT / height);
            if (creature instanceof net.minecraft.world.entity.LivingEntity living) {
                living.hurtTime = 0;
                living.hurtDuration = 0;
                living.deathTime = 0;
                living.setYBodyRot(0.0F);
                living.yBodyRotO = 0.0F;
                living.setYHeadRot(0.0F);
                living.yHeadRotO = 0.0F;
                living.setXRot(0.0F);
                living.xRotO = 0.0F;
            }
            // PackageRenderer's origin is the package centre on X/Z and its feet on Y.
            poseStack.translate(0.0D, 0.09D, 0.0D);
            poseStack.scale(scale, scale, scale);
            try {
                Minecraft.getInstance().getEntityRenderDispatcher().render(creature, 0, 0, 0, 0,
                        partialTick, poseStack, buffer, packedLight);
            } catch (RuntimeException failure) {
                com.llqqrr.aircrate.AirCrate.LOGGER.warn("Failed to render dropped creature parcel {}: {}",
                        record.entityType(), failure.toString());
            }
            poseStack.popPose();
        }

        // This is a baked block-style model, not an entity skin. A plain
        // Sheets.cutoutBlockSheet() is not sufficient when Iris is present: Iris
        // wraps the MultiBufferSource at the entity-render entry and rewrites every
        // RenderType requested from that wrapper. Unwrap that optional wrapper before
        // submitting the shell, while the contained creature above still uses the
        // wrapped source so Iris can identify the actual creature entity.
        MultiBufferSource shellBuffer = aircrate$unwrapEntityBuffer(buffer);
        poseStack.pushPose();
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(-(entityYaw + 90.0F))));
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        net.minecraft.client.resources.model.BakedModel model =
                Minecraft.getInstance().getModelManager().getModel(BOX_MODEL);
        Minecraft.getInstance().getItemRenderer().renderModelLists(
                model, ItemStack.EMPTY, packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, shellBuffer.getBuffer(net.minecraft.client.renderer.Sheets.cutoutBlockSheet()));
        poseStack.popPose();
        callback.cancel();
    }
}

package com.llqqrr.aircrate.client;

import com.llqqrr.aircrate.content.creature.CreatureRecord;
import com.llqqrr.aircrate.registry.ACDataComponents;
import com.llqqrr.aircrate.registry.ACItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Renders the original creature model from the universal record item's full entity data. */
public final class CreatureEntityRenderer extends BlockEntityWithoutLevelRenderer {
    private static final int CACHE_LIMIT = 48;
    private static final Map<java.util.UUID, Entity> CACHE = new LinkedHashMap<>(CACHE_LIMIT, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<java.util.UUID, Entity> eldest) {
            return size() > CACHE_LIMIT;
        }
    };
    private static CreatureEntityRenderer instance;
    private static final java.util.Set<String> LOGGED_FAILURES = new java.util.HashSet<>();
    private static final net.minecraft.client.resources.model.ModelResourceLocation BOX_MODEL =
            net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aircrate", "item/creature_parcel_box"));

    private CreatureEntityRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    public static void register(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        instance = new CreatureEntityRenderer(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
        event.registerItem(new IClientItemExtensions() {
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return instance; }
            @Override public boolean shouldBobAsEntity(ItemStack stack) { return false; }
            @Override public boolean shouldSpreadAsEntity(ItemStack stack) { return false; }
        }, ACItems.SEALED_CREATURE.get(), ACItems.CREATURE_PARCEL.get());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource buffer, int packedLight, int packedOverlay) {
        CreatureRecord record = stack.get(ACDataComponents.CREATURE_RECORD);
        Entity entity = displayEntity(record);
        boolean parcel = stack.is(ACItems.CREATURE_PARCEL.get());
        if (displayContext == ItemDisplayContext.GUI && parcel) {
            // builtin/entity models get no display transforms. The pose entering here is
            // GUI_setup * T(-0.5,-0.5,-0.5), so the slot centre corresponds to model-space
            // (0.5, 0.5, 0.5). Rotate/scale around the box centre and land it exactly on
            // the slot centre instead of spinning around the world origin.
            poseStack.translate(0.5D, 0.5D, 0.5D);
            poseStack.mulPose(new org.joml.Quaternionf().rotationXYZ(
                    (float) Math.toRadians(30.0F), (float) Math.toRadians(225.0F), 0.0F));
            poseStack.scale(0.9F, 0.9F, 0.9F);
            poseStack.translate(-0.5D, -0.5D, -0.5D);
            poseStack.translate(0.0D, 0.125D, 0.0D);
        } else if (displayContext == ItemDisplayContext.FIXED && parcel) {
            // The packager's unpack animation renders this item in FIXED context on the
            // tray; at full block size the box (and the creature inside) pokes out of the
            // machine. Shrink around the box centre (the box is 12px tall -> y centre 0.375).
            poseStack.translate(0.5D, 0.375D, 0.5D);
            poseStack.scale(0.65F, 0.65F, 0.65F);
            poseStack.translate(-0.5D, -0.375D, -0.5D);
        }
        if (entity == null) {
            if (record != null && LOGGED_FAILURES.add(record.recordId() + ":null@" + displayContext)) {
                com.llqqrr.aircrate.AirCrate.LOGGER.warn("[parcel-diag] display entity is null for {} in {}",
                        record.entityType(), displayContext);
            }
            renderParcelBox(parcel, poseStack, buffer, packedLight, packedOverlay);
            return;
        }
        // Fit the whole body inside the 12px box with a safety margin, independent of
        // display context, so nothing ever clips through the parcel walls or window.
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            living.setYBodyRot(0.0F);
            living.yBodyRotO = 0.0F;
            living.setYHeadRot(0.0F);
            living.yHeadRotO = 0.0F;
            living.setXRot(0.0F);
            living.xRotO = 0.0F;
        }
        poseStack.pushPose();
        poseStack.translate(0.5D, parcel ? 0.09D : 0.0D, 0.5D);
        float width = Math.max(0.2F, entity.getBbWidth());
        float height = Math.max(0.2F, entity.getBbHeight());
        float scale = parcel
                ? Math.min(0.5F, 0.85F * Math.min(0.5F / width, 0.55F / height))
                : 0.8F / Math.max(width, height);
        poseStack.scale(scale, scale, scale);
        if (record != null && LOGGED_FAILURES.add(record.recordId().toString() + '@' + displayContext)) {
            com.llqqrr.aircrate.AirCrate.LOGGER.info(
                    "[parcel-diag] rendering {} in {}: bb={}x{} scale={} invisible={} alive={} light={} pos={}",
                    record.entityType(), displayContext, width, height, scale, entity.isInvisible(),
                    entity.isAlive(), packedLight, entity.position());
        }
        try {
            Minecraft.getInstance().getEntityRenderDispatcher().render(entity, 0, 0, 0,
                    0, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true), poseStack, buffer, packedLight);
        } catch (RuntimeException renderFailure) {
            // One bad record (broken NBT, modded renderer) must not abort the box or the
            // rest of the GUI; log once per record instead of every frame.
            if (record != null && LOGGED_FAILURES.add(record.recordId() + ":fail")) {
                com.llqqrr.aircrate.AirCrate.LOGGER.warn("Failed to render display entity for {}: {}",
                        record.entityType(), renderFailure.toString());
            }
        }
        poseStack.popPose();
        // Submit the shell after the entity. BufferSource switches flush the entity
        // render type before the block cutout shell, so the shell cannot occlude the
        // creature through the transparent observation window in GROUND/FIXED renders.
        renderParcelBox(parcel, poseStack, buffer, packedLight, packedOverlay);
    }

    private static void renderParcelBox(boolean parcel, PoseStack poseStack, MultiBufferSource buffer,
                                        int packedLight, int packedOverlay) {
        if (!parcel) return;
        poseStack.pushPose();
        net.minecraft.client.resources.model.BakedModel box =
                Minecraft.getInstance().getModelManager().getModel(BOX_MODEL);
        Minecraft.getInstance().getItemRenderer().renderModelLists(box, ItemStack.EMPTY,
                packedLight, packedOverlay, poseStack,
                buffer.getBuffer(net.minecraft.client.renderer.Sheets.cutoutBlockSheet()));
        poseStack.popPose();
    }

    public static Entity displayEntity(CreatureRecord record) {
        Minecraft minecraft = Minecraft.getInstance();
        if (record == null || minecraft.level == null) return null;
        return CACHE.computeIfAbsent(record.recordId(), ignored -> {
            Entity entity = EntityType.byString(record.entityType()).map(type -> type.create(minecraft.level)).orElse(null);
            if (entity == null) return null;
            CompoundTag data = com.llqqrr.aircrate.content.creature.CreatureNbt.parse(record);
            if (!data.isEmpty()) {
                // Strip transient state that ruins a display pose: a creature packed while
                // sleeping would render lying down, sunk into the parcel floor.
                for (String key : java.util.List.of("UUID", "Pos", "Motion", "Rotation", "FallDistance",
                        "Fire", "Pose", "SleepingX", "SleepingY", "SleepingZ", "FallFlying",
                        "DeathTime", "HurtTime")) {
                    data.remove(key);
                }
                try {
                    entity.load(data);
                } catch (RuntimeException loadFailure) {
                    // A damaged record must not take the whole item renderer down with it.
                    com.llqqrr.aircrate.AirCrate.LOGGER.warn("Failed to load display NBT for {}: {}",
                            record.entityType(), loadFailure.toString());
                }
            }
            entity.setUUID(java.util.UUID.randomUUID());
            entity.setYRot(0);
            entity.setXRot(0);
            return entity;
        });
    }
}

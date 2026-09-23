package com.llqqrr.aircrate.client.crate;

import com.llqqrr.aircrate.client.CreatureEntityRenderer;
import com.llqqrr.aircrate.content.block.AviationCrateBlockEntity;
import com.llqqrr.aircrate.content.block.CrateMode;
import com.llqqrr.aircrate.content.structure.CreatureCapacitySolver;
import com.llqqrr.aircrate.content.structure.CrateSize;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shows a few frozen creatures standing on their real capacity cells inside glass/bars
 * crates. The display count grows sub-linearly with interior volume (cap 7), only the
 * heads track the player, and nothing renders in CLOSED mode or past the entity
 * render distance (scaled by the vanilla video settings).
 */
public final class CrateCreatureRenderer extends SafeBlockEntityRenderer<AviationCrateBlockEntity> {
    private static final int DISPLAY_CAP = 7;
    /** Display count = min(CAP, floor(sqrt(volume / VOLUME_STEP))): 27 cells -> 1, 480 -> 7. */
    private static final double VOLUME_STEP = 8.0D;
    private static final double MAX_CULL_DISTANCE = 96.0D;
    private static final float HEAD_YAW_LIMIT = 80.0F;
    private static final float HEAD_PITCH_LIMIT = 30.0F;

    /** Per-record smoothed head yaw, so heads glide instead of snapping. */
    private static final Map<UUID, Float> HEAD_YAW = new HashMap<>();

    public CrateCreatureRenderer(BlockEntityRendererProvider.Context context) {
        super();
    }

    public static int displayCap(CrateSize size) {
        return Math.min(DISPLAY_CAP, (int) Math.floor(Math.sqrt(size.volume() / VOLUME_STEP)));
    }

    @Override
    protected void renderSafe(AviationCrateBlockEntity member, float partialTicks, PoseStack pose,
                              MultiBufferSource buffers, int light, int overlay) {
        AviationCrateBlockEntity controller = member.controller();
        if (controller != member || controller.crateMode() == CrateMode.CLOSED) return;
        List<com.llqqrr.aircrate.content.creature.CreatureRecord> records = controller.records();
        if (records.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;
        CrateSize size = controller.size();
        // Logical coordinates: width = cross-section axis, length = placement-facing
        // axis. World offsets need the facing-aware axis swap or long crates laid
        // along X get their creatures pushed off-centre.
        Direction facing = controller.getBlockState()
                .getValue(com.llqqrr.aircrate.content.block.AviationCrateBlock.PLACEMENT_FACING);
        boolean alongX = facing.getAxis() == Direction.Axis.X;
        int worldExtentX = alongX ? size.length() : size.width();
        int worldExtentZ = alongX ? size.width() : size.length();
        Vec3 centre = Vec3.atCenterOf(member.getBlockPos())
                .add(worldExtentX / 2.0D, size.height() / 2.0D, worldExtentZ / 2.0D);
        double cull = Math.min(MAX_CULL_DISTANCE,
                minecraft.options.getEffectiveRenderDistance() * 16.0D
                        * minecraft.options.entityDistanceScaling().get());
        if (player.getEyePosition().distanceToSqr(centre) > cull * cull) return;

        List<CreatureCapacitySolver.Placement> placements =
                CreatureCapacitySolver.layout(size, records);
        int shown = Math.min(displayCap(size), placements.size());

        // The solver packs from the minimum corner; recentre the displayed group so
        // creatures stand in the middle of the crate instead of huddled in a corner.
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < shown; i++) {
            CreatureCapacitySolver.Placement placement = placements.get(i);
            minX = Math.min(minX, placement.x());
            minZ = Math.min(minZ, placement.z());
            maxX = Math.max(maxX, placement.x() + placement.width());
            maxZ = Math.max(maxZ, placement.z() + placement.length());
        }
        double offsetX = shown > 0 ? (size.width() - (maxX - minX)) / 2.0D - minX : 0.0D;
        double offsetZ = shown > 0 ? (size.length() - (maxZ - minZ)) / 2.0D - minZ : 0.0D;

        for (int i = 0; i < shown; i++) {
            CreatureCapacitySolver.Placement placement = placements.get(i);
            Entity entity = CreatureEntityRenderer.displayEntity(placement.record());
            if (!(entity instanceof LivingEntity living)) continue;

            double px = placement.x() + offsetX + placement.width() / 2.0D;
            double py = placement.y() + 0.07D;
            double pz = placement.z() + offsetZ + placement.length() / 2.0D;
            double worldX = alongX ? pz : px;
            double worldZ = alongX ? px : pz;
            aimHeadAt(living, member.getBlockPos(), worldX, py, worldZ, player);

            // Shrink anything whose model outgrows its capacity cells (villager hats poke
            // through a 2-high ceiling at natural scale).
            float fit = Math.min(placement.height() * 0.92F / Math.max(0.2F, living.getBbHeight()),
                    Math.min(placement.width(), placement.length()) * 0.9F
                            / Math.max(0.2F, living.getBbWidth()));
            float entityScale = Math.min(1.0F, fit);

            int creatureLight = member.getLevel() == null ? light
                    : LevelRenderer.getLightColor(member.getLevel(),
                    member.getBlockPos().offset(alongX ? placement.z() : placement.x(), placement.y() + 1,
                            alongX ? placement.x() : placement.z()));
            pose.pushPose();
            pose.translate(worldX, py, worldZ);
            pose.scale(entityScale, entityScale, entityScale);
            minecraft.getEntityRenderDispatcher().render(living, 0, 0, 0, 0.0F, partialTicks,
                    pose, buffers, creatureLight);
            pose.popPose();
        }
    }

    /** Frozen body, head-only tracking with a soft clamp and per-frame smoothing. */
    private static void aimHeadAt(LivingEntity entity, BlockPos base, double px, double py, double pz,
                                  Player player) {
        double ex = base.getX() + px;
        double ey = base.getY() + py + entity.getEyeHeight() * 0.85D;
        double ez = base.getZ() + pz;
        Vec3 eye = player.getEyePosition();
        double dx = eye.x - ex;
        double dz = eye.z - ez;
        double dy = eye.y - ey;
        float targetYaw = (float) (Math.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        targetYaw = Mth.clamp(Mth.wrapDegrees(targetYaw), -HEAD_YAW_LIMIT, HEAD_YAW_LIMIT);
        float targetPitch = Mth.clamp(
                (float) (-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG),
                -HEAD_PITCH_LIMIT, HEAD_PITCH_LIMIT);
        float current = HEAD_YAW.getOrDefault(entity.getUUID(), targetYaw);
        float smoothed = current + Mth.wrapDegrees(targetYaw - current) * 0.2F;
        if (HEAD_YAW.size() > 128) HEAD_YAW.clear();
        HEAD_YAW.put(entity.getUUID(), smoothed);
        entity.setYBodyRot(0.0F);
        entity.yBodyRotO = 0.0F;
        entity.setYHeadRot(smoothed);
        entity.yHeadRotO = smoothed;
        entity.setXRot(targetPitch);
        entity.xRotO = targetPitch;
    }

    @Override
    public AABB getRenderBoundingBox(AviationCrateBlockEntity member) {
        AviationCrateBlockEntity controller = member.controller();
        CrateSize size = controller.size();
        BlockPos pos = controller.getBlockPos();
        Direction facing = controller.getBlockState()
                .getValue(com.llqqrr.aircrate.content.block.AviationCrateBlock.PLACEMENT_FACING);
        boolean alongX = facing.getAxis() == Direction.Axis.X;
        int extentX = alongX ? size.length() : size.width();
        int extentZ = alongX ? size.width() : size.length();
        return new AABB(Vec3.atLowerCornerOf(pos),
                Vec3.atLowerCornerOf(pos.offset(extentX, size.height(), extentZ))).inflate(1.0D);
    }
}

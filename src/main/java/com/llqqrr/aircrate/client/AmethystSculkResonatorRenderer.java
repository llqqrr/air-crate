package com.llqqrr.aircrate.client;

import com.llqqrr.aircrate.content.resonator.AmethystSculkResonatorBlock;
import com.llqqrr.aircrate.content.resonator.AmethystSculkResonatorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/** Renders the resonator's through-shaft rotor instead of rotating the whole body. */
public final class AmethystSculkResonatorRenderer extends KineticBlockEntityRenderer<AmethystSculkResonatorBlockEntity> {
    public static final PartialModel ROTOR = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath("aircrate", "block/integration_v4/resonator_rotor"));
    public static final PartialModel GLOW = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath("aircrate", "block/integration_v4/resonator_glow"));

    public AmethystSculkResonatorRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(AmethystSculkResonatorBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(ROTOR, state, state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING));
    }

    @Override
    protected void renderSafe(AmethystSculkResonatorBlockEntity be, float partialTicks, PoseStack pose,
                              MultiBufferSource buffers, int light, int overlay) {
        super.renderSafe(be, partialTicks, pose, buffers, light, overlay);
        BlockState state = be.getBlockState();
        if (!state.hasProperty(AmethystSculkResonatorBlock.ACTIVE)
                || !state.getValue(AmethystSculkResonatorBlock.ACTIVE)) return;
        SuperByteBuffer glow = CachedBuffers.partialFacing(GLOW, state,
                state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING));
        // Slight upscale avoids z-fighting with the base block surface.
        ((SuperByteBuffer) ((SuperByteBuffer) glow.center()).scale(1.005f)).uncenter();
        glow.light(LightTexture.FULL_BRIGHT).overlay(overlay)
                .renderInto(pose, buffers.getBuffer(RenderType.cutout()));
    }
}

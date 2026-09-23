package com.llqqrr.aircrate.client;

import com.llqqrr.aircrate.registry.ACDataComponents;
import com.llqqrr.aircrate.registry.ACItems;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/** Third-person Carry On-style visual for a loaded creature intake carrier. */
public final class CarriedCreatureRenderer {
    private CarriedCreatureRenderer() { }

    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        ItemStack stack = event.getEntity().getMainHandItem();
        if (!stack.is(ACItems.CREATURE_INTAKE_FAN.get()) || !stack.has(ACDataComponents.CREATURE_RECORD)) {
            stack = event.getEntity().getOffhandItem();
        }
        if (!stack.is(ACItems.CREATURE_INTAKE_FAN.get())) return;
        Entity creature = CreatureEntityRenderer.displayEntity(stack.get(ACDataComponents.CREATURE_RECORD));
        if (creature == null) return;
        var pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0.0D, -0.75D, -0.55D);
        float largest = Math.max(0.5F, Math.max(creature.getBbWidth(), creature.getBbHeight()));
        float scale = Math.min(0.75F, 1.15F / largest);
        pose.scale(scale, scale, scale);
        Minecraft.getInstance().getEntityRenderDispatcher().render(creature, 0, 0, 0, 0,
                event.getPartialTick(), pose, event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
    }
}

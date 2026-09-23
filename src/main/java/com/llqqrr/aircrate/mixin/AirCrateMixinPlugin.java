package com.llqqrr.aircrate.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Avoids loading Sable descriptors unless Create Aeronautics is actually installed. */
public final class AirCrateMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("PackageRendererMixin") || mixinClassName.endsWith("PackageVisualMixin")
                || mixinClassName.endsWith("VisualizationHelperMixin")) {
            // PackageRenderer and the custom renderer are client-only. Keep this
            // mixin out of dedicated servers, where the client classes do not exist.
            ClassLoader loader = getClass().getClassLoader();
            return ClassResourceProbe.exists(loader, "com.simibubi.create.content.logistics.box.PackageRenderer")
                    && ClassResourceProbe.exists(loader, "com.simibubi.create.content.logistics.box.PackageVisual")
                    && ClassResourceProbe.exists(loader, "dev.engine_room.flywheel.lib.visualization.VisualizationHelper")
                    && ClassResourceProbe.exists(loader, "net.minecraft.client.renderer.entity.EntityRenderer");
        }
        if (!mixinClassName.endsWith("SableAssemblyMixin")) return true;
        ClassLoader loader = getClass().getClassLoader();
        return ClassResourceProbe.exists(loader, "dev.eriksonn.aeronautics.Aeronautics")
                && ClassResourceProbe.exists(loader, "dev.ryanhcode.sable.api.SubLevelAssemblyHelper");
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                                   IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                                    IMixinInfo mixinInfo) { }
}

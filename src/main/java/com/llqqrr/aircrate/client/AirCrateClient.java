package com.llqqrr.aircrate.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only registration keeps the base server free of rendering classes. */
public final class AirCrateClient {
    private static final int HOLD_TICKS = 5;
    private static BlockPos pendingController;
    private static boolean pendingBreeding;
    private static int heldTicks;

    private AirCrateClient() { }

    public static void register(net.neoforged.bus.api.IEventBus modBus) {
        CreatureFrogportRenderer.initPartials();
        NeoForge.EVENT_BUS.addListener(AirCrateClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(CarriedCreatureRenderer::onRenderPlayer);
        modBus.addListener(CreatureEntityRenderer::register);
        modBus.addListener((net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) ->
                event.registerBlock(AirCrateBlockParticles.INSTANCE,
                        com.llqqrr.aircrate.AirCrate.AVIATION_CRATE.get(),
                        com.llqqrr.aircrate.AirCrate.CREATURE_FROGPORT.get(),
                        com.llqqrr.aircrate.AirCrate.CREATURE_PACKAGER.get(),
                        com.llqqrr.aircrate.AirCrate.AMETHYST_SCULK_RESONATOR.get()));
        modBus.addListener(CreatureIntakeFanRenderer::register);
        modBus.addListener(AirCrateClient::registerRenderers);
        modBus.addListener(AirCrateClient::clientSetup);
        modBus.addListener((net.neoforged.neoforge.client.event.ModelEvent.RegisterGeometryLoaders event) ->
                event.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        com.llqqrr.aircrate.AirCrate.MOD_ID, "crate"),
                        new com.llqqrr.aircrate.client.crate.CrateShellModel.Loader()));
        modBus.addListener((net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) -> {
            // Models that are only ever fetched as standalone/partial models must be
            // registered explicitly, otherwise they bake as the missing (purple/black) model.
            for (String path : new String[] {
                    "item/creature_parcel_box",
                    "item/creature_intake_fan_base",
                    "item/creature_intake_fan_tongue",
                    "item/creature_intake_fan_jaw",
                    "item/creature_intake_fan_gears",
                    "item/creature_intake_fan_trigger",
                    "item/creature_intake_fan_valve",
                    "item/creature_intake_fan_gauge",
                    "block/creature_packager/hatch_closed",
                    "block/creature_packager/hatch_open",
                    "block/creature_packager/tray",
                    "block/creature_frogport_body_glow",
                    "block/creature_frogport_head_glow",
                    "block/integration_v4/resonator_glow",
                    "block/integration_v4/resonator_rotor"}) {
                event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                com.llqqrr.aircrate.AirCrate.MOD_ID, path)));
            }
        });
        if (net.neoforged.fml.ModList.get().isLoaded("fieldguide")) {
            com.llqqrr.aircrate.compat.fieldguide.FieldGuideClientCompat.register();
        }
    }

    /** Rebuilds the render section around a crate member after structure or wall-mode changes. */
    public static void refreshCrateSection(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (minecraft.level.getBlockEntity(pos) instanceof com.llqqrr.aircrate.content.block.AviationCrateBlockEntity crate) {
            crate.requestModelDataUpdate();
        }
        net.minecraft.core.SectionPos section = net.minecraft.core.SectionPos.of(pos);
        minecraft.levelRenderer.setSectionDirtyWithNeighbors(section.x(), section.y(), section.z());
    }

    private static void clientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            registerCreatureParcelAnimationModel();
            // Several models rely on transparent texture pixels (packager windows, hatch
            // glass); without a cutout layer they render as sealed opaque faces.
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.llqqrr.aircrate.AirCrate.CREATURE_PACKAGER.get(),
                    net.minecraft.client.renderer.RenderType.cutout());
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.llqqrr.aircrate.AirCrate.CRATE_INTERACTION_HATCH.get(),
                    net.minecraft.client.renderer.RenderType.cutout());
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.llqqrr.aircrate.AirCrate.ANDESITE_CRATE_HATCH.get(),
                    net.minecraft.client.renderer.RenderType.cutout());
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.llqqrr.aircrate.AirCrate.BRASS_CRATE_HATCH.get(),
                    net.minecraft.client.renderer.RenderType.cutout());
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.llqqrr.aircrate.AirCrate.CREATURE_FROGPORT.get(),
                    net.minecraft.client.renderer.RenderType.cutout());
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.llqqrr.aircrate.AirCrate.CREATURE_BELT_FIXTURE.get(),
                    net.minecraft.client.renderer.RenderType.cutout());
            dev.engine_room.flywheel.api.visualization.VisualizerRegistry.setVisualizer(
                    com.llqqrr.aircrate.registry.ACBlockEntities.CREATURE_PACKAGER.get(),
                    dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer
                            .builder(com.llqqrr.aircrate.registry.ACBlockEntities.CREATURE_PACKAGER.get())
                            .factory(com.llqqrr.aircrate.client.packager.CreaturePackagerVisual::new)
                            .neverSkipVanillaRender()
                            .apply());
            dev.engine_room.flywheel.api.visualization.VisualizerRegistry.setVisualizer(
                    com.llqqrr.aircrate.registry.ACBlockEntities.AMETHYST_SCULK_RESONATOR.get(),
                    dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer
                            .builder(com.llqqrr.aircrate.registry.ACBlockEntities.AMETHYST_SCULK_RESONATOR.get())
                            .factory(com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual
                                    .ofZ(AmethystSculkResonatorRenderer.ROTOR))
                            .apply());
        });
    }

    @SuppressWarnings("unchecked")
    private static void registerCreatureParcelAnimationModel() {
        try {
            net.minecraft.resources.ResourceLocation creatureParcel = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(com.llqqrr.aircrate.registry.ACItems.CREATURE_PARCEL.get());
            net.minecraft.resources.ResourceLocation defaultParcel = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(com.simibubi.create.content.logistics.box.PackageStyles.getDefaultBox().getItem());
            Class<?> partials = Class.forName("com.simibubi.create.AllPartialModels");
            Object boxModel = dev.engine_room.flywheel.lib.model.baked.PartialModel.of(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aircrate", "item/creature_parcel_box"));
            java.util.Map<Object, Object> packages = (java.util.Map<Object, Object>) partials
                    .getField("PACKAGES").get(null);
            packages.put(creatureParcel, boxModel);
            java.util.Map<Object, Object> rigging = (java.util.Map<Object, Object>) partials
                    .getField("PACKAGE_RIGGING").get(null);
            Object rope = rigging.get(defaultParcel);
            if (rope != null) rigging.put(creatureParcel, rope);
        } catch (ReflectiveOperationException exception) {
            org.slf4j.LoggerFactory.getLogger("AirCrate").error("Failed to register creature parcel animation", exception);
        }
    }

    private static void registerRenderers(
            net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                com.llqqrr.aircrate.registry.ACBlockEntities.CREATURE_PACKAGER.get(),
                com.llqqrr.aircrate.client.packager.CreaturePackagerRenderer::new);
        event.registerBlockEntityRenderer(
                com.llqqrr.aircrate.registry.ACBlockEntities.CREATURE_FROGPORT.get(),
                CreatureFrogportRenderer::new);
        event.registerBlockEntityRenderer(
                com.llqqrr.aircrate.registry.ACBlockEntities.AMETHYST_SCULK_RESONATOR.get(),
                AmethystSculkResonatorRenderer::new);
        event.registerBlockEntityRenderer(
                com.llqqrr.aircrate.registry.ACBlockEntities.AVIATION_CRATE.get(),
                com.llqqrr.aircrate.client.crate.CrateCreatureRenderer::new);
    }

    /** Starts the same short right-click hold warmup used by Create value controls. */
    public static void openModeScreen(BlockPos controllerPos, boolean breeding) {
        if (controllerPos.equals(pendingController)) return;
        pendingController = controllerPos.immutable();
        pendingBreeding = breeding;
        heldTicks = 0;
    }

    public static void openFrogportScreen(BlockPos pos,
                                          com.llqqrr.aircrate.content.frogport.CreatureFrogportBlockEntity.Mode mode,
                                          com.llqqrr.aircrate.content.frogport.CreatureAreaSelection selection,
                                          net.minecraft.world.item.ItemStack filter) {
        Minecraft.getInstance().setScreen(new CreatureFrogportScreen(pos, mode, selection, filter));
    }

    public static void openFilterScreen(com.llqqrr.aircrate.content.creature.CreatureFilterData data) {
        Minecraft.getInstance().setScreen(new CreatureFilterScreen(data));
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (pendingController == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || !minecraft.options.keyUse.isDown()) {
            cancelPendingInteraction();
            return;
        }
        if (++heldTicks < HOLD_TICKS) return;
        BlockPos controller = pendingController;
        boolean breeding = pendingBreeding;
        cancelPendingInteraction();
        minecraft.setScreen(new AviationCrateModeScreen(controller, breeding));
    }

    private static void cancelPendingInteraction() {
        pendingController = null;
        heldTicks = 0;
    }
}

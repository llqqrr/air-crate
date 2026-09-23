package com.llqqrr.aircrate;

import com.llqqrr.aircrate.content.block.AviationCrateBlock;
import com.llqqrr.aircrate.content.block.AviationCrateHatchBlock;
import com.llqqrr.aircrate.content.block.CrateInteractionHatchBlock;
import com.llqqrr.aircrate.content.block.CreatureBeltFixtureBlock;
import com.llqqrr.aircrate.registry.ACBlockEntities;
import com.llqqrr.aircrate.registry.ACCreativeTabs;
import com.llqqrr.aircrate.registry.ACDataComponents;
import com.llqqrr.aircrate.registry.ACItems;
import com.llqqrr.aircrate.network.AirCrateNetwork;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.fml.ModList;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.function.Supplier;

@Mod(AirCrate.MOD_ID)
public final class AirCrate {
    public static final String MOD_ID = "aircrate";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);

    public static final Supplier<Block> AVIATION_CRATE = BLOCKS.register(
            "aviation_crate", () -> new AviationCrateBlock(
                    BlockBehaviour.Properties.of().strength(2.0F).noOcclusion()));
    public static final Supplier<Block> ANDESITE_CRATE_HATCH = BLOCKS.register(
            "andesite_crate_hatch", () -> new AviationCrateHatchBlock(
                    BlockBehaviour.Properties.of().strength(2.5F).noOcclusion()));
    public static final Supplier<Block> BRASS_CRATE_HATCH = BLOCKS.register(
            "brass_crate_hatch", () -> new AviationCrateHatchBlock(
                    BlockBehaviour.Properties.of().strength(3.5F).noOcclusion()));
    public static final Supplier<Block> CRATE_INTERACTION_HATCH = BLOCKS.register(
            "crate_interaction_hatch", () -> new CrateInteractionHatchBlock(
                    BlockBehaviour.Properties.of().strength(2.5F).noOcclusion()));
    public static final Supplier<Block> CREATURE_BELT_FIXTURE = BLOCKS.register(
            "creature_belt_fixture", () -> new CreatureBeltFixtureBlock(
                    BlockBehaviour.Properties.of().strength(2.0F).noOcclusion()));
    public static final Supplier<Block> CREATURE_FROGPORT = BLOCKS.register(
            "creature_frogport", () -> new com.llqqrr.aircrate.content.frogport.CreatureFrogportBlock(
                    BlockBehaviour.Properties.of().strength(2.5F).noOcclusion()));
    public static final Supplier<Block> CREATURE_PACKAGER = BLOCKS.register(
            "creature_packager", () -> new com.llqqrr.aircrate.content.packager.CreaturePackagerBlock(
                    BlockBehaviour.Properties.of().strength(3.0F).noOcclusion()
                            .isRedstoneConductor((state, level, pos) -> false)));
    public static final Supplier<Block> AMETHYST_SCULK_RESONATOR = BLOCKS.register(
            "amethyst_sculk_resonator", () -> new com.llqqrr.aircrate.content.resonator.AmethystSculkResonatorBlock(
                    BlockBehaviour.Properties.of().strength(3.5F).noOcclusion()));

    public AirCrate(IEventBus modBus) {
        BLOCKS.register(modBus);
        ACItems.register(modBus);
        ACCreativeTabs.register(modBus);
        ACDataComponents.register(modBus);
        ACBlockEntities.register(modBus);
        AirCrateNetwork.register(modBus);
        com.llqqrr.aircrate.content.creature.CreatureContainerGuard.register();
        com.llqqrr.aircrate.content.creature.CreatureCaptureJournal.register();
        com.llqqrr.aircrate.content.creature.CreatureIntakeFanCaptureHandler.register();
        com.llqqrr.aircrate.content.frogport.CreatureFrogportSelectionHandler.register();
        modBus.addListener(this::registerCapabilities);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.llqqrr.aircrate.client.AirCrateClient.register(modBus);
        }
        if (ModList.get().isLoaded("create")) {
            com.llqqrr.aircrate.compat.create.CreateArmCompat.register(modBus);
            com.llqqrr.aircrate.compat.create.CreatureBeltFixtureService.register();
            com.llqqrr.aircrate.compat.create.CreateCreatureInteractions.register();
        }
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        ACBlockEntities.registerCapabilities(event);
    }
}

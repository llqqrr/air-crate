package com.llqqrr.aircrate.client.crate;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.llqqrr.aircrate.content.block.CrateRenderData;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;

/**
 * Dynamic crate shell: per member block and per exposed face, picks the connected-texture
 * part for that cell's edge mask. Parts live in
 * {@code models/block/integration_v4/crate/<kind>_<mask:02>_<direction>.json}; each already
 * contains the inward-facing copy of the panel. The output port reuses the legacy
 * {@code models/block/crate_face/output_<direction>.json} parts.
 */
public final class CrateShellModel implements IDynamicBakedModel {
    private static final String[] WALL_KINDS = {"glass", "bars", "closed", "panel"};
    private static final int[] VENT_MASKS = {5, 7, 13, 15};
    private static final Direction[] VENT_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final ChunkRenderTypeSet CUTOUT = ChunkRenderTypeSet.of(RenderType.cutout());
    private static final ItemTransforms BLOCK_TRANSFORMS = createBlockTransforms();

    private final Map<String, BakedModel> parts;
    private final TextureAtlasSprite particle;

    private CrateShellModel(Map<String, BakedModel> parts, TextureAtlasSprite particle) {
        this.parts = parts;
        this.particle = particle;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData extraData, @Nullable RenderType renderType) {
        if (side != null) return List.of();
        CrateRenderData data = extraData.has(CrateRenderData.KEY) ? extraData.get(CrateRenderData.KEY)
                : CrateRenderData.fallback();
        List<BakedQuad> quads = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            CrateRenderData.Face face = data.faces().get(direction);
            if (face == null || face.kind() == CrateRenderData.Kind.HIDDEN) continue;
            String key = switch (face.kind()) {
                case MODE -> data.mode().getSerializedName() + maskPart(face.mask(), direction);
                case PANEL -> "panel" + maskPart(face.mask(), direction);
                case VENT -> "vent" + maskPart(face.mask(), direction);
                case OUTPUT -> "output_" + direction.getSerializedName();
                default -> null;
            };
            if (key == null) continue;
            BakedModel part = parts.get(key);
            if (part != null) quads.addAll(part.getQuads(state, null, rand, ModelData.EMPTY, renderType));
        }
        return quads;
    }

    private static String maskPart(int mask, Direction direction) {
        return "_" + (mask < 10 ? "0" : "") + mask + "_" + direction.getSerializedName();
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return CUTOUT;
    }

    @Override
    public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
        return List.of(RenderType.cutout());
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return particle;
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        return particle;
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    @Override
    public ItemTransforms getTransforms() {
        return BLOCK_TRANSFORMS;
    }

    /** Same display transforms as {@code minecraft:block/block}, for the item form. */
    private static ItemTransforms createBlockTransforms() {
        ItemTransform thirdPersonLeft = new ItemTransform(new Vector3f(75, 225, 0),
                new Vector3f(0, 2.5f, 0), new Vector3f(0.375f, 0.375f, 0.375f));
        ItemTransform thirdPersonRight = new ItemTransform(new Vector3f(75, 45, 0),
                new Vector3f(0, 2.5f, 0), new Vector3f(0.375f, 0.375f, 0.375f));
        ItemTransform firstPersonLeft = new ItemTransform(new Vector3f(0, 225, 0),
                new Vector3f(0, 0, 0), new Vector3f(0.4f, 0.4f, 0.4f));
        ItemTransform firstPersonRight = new ItemTransform(new Vector3f(0, 45, 0),
                new Vector3f(0, 0, 0), new Vector3f(0.4f, 0.4f, 0.4f));
        ItemTransform head = new ItemTransform(new Vector3f(0, 0, 0),
                new Vector3f(0, 0, 0), new Vector3f(1, 1, 1));
        ItemTransform gui = new ItemTransform(new Vector3f(30, 225, 0),
                new Vector3f(0, 0, 0), new Vector3f(0.625f, 0.625f, 0.625f));
        ItemTransform ground = new ItemTransform(new Vector3f(0, 0, 0),
                new Vector3f(0, 3, 0), new Vector3f(0.25f, 0.25f, 0.25f));
        ItemTransform fixed = new ItemTransform(new Vector3f(0, 180, 0),
                new Vector3f(0, 0, 0), new Vector3f(0.5f, 0.5f, 0.5f));
        return new ItemTransforms(thirdPersonLeft, thirdPersonRight, firstPersonLeft, firstPersonRight,
                head, gui, ground, fixed);
    }

    public static final class Geometry implements IUnbakedGeometry<Geometry> {
        @Override
        public void resolveParents(Function<ResourceLocation, net.minecraft.client.resources.model.UnbakedModel> modelGetter,
                                   IGeometryBakingContext context) {
            // Pull every face part into the dependency graph so their textures get stitched.
            for (String kind : WALL_KINDS) {
                for (int mask = 0; mask < 16; mask++) {
                    for (Direction direction : Direction.values()) {
                        modelGetter.apply(partLocation("integration_v4/crate/",
                                kind + maskName(mask, direction))).resolveParents(modelGetter);
                    }
                }
            }
            for (int mask : VENT_MASKS) {
                for (Direction direction : VENT_DIRECTIONS) {
                    modelGetter.apply(partLocation("integration_v4/crate/",
                            "vent" + maskName(mask, direction))).resolveParents(modelGetter);
                }
            }
            for (Direction direction : Direction.values()) {
                modelGetter.apply(partLocation("crate_face/",
                        "output_" + direction.getSerializedName())).resolveParents(modelGetter);
            }
        }

        private static ResourceLocation partLocation(String folder, String name) {
            return ResourceLocation.fromNamespaceAndPath("aircrate", "block/" + folder + name);
        }

        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                               Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
                               ItemOverrides overrides) {
            Map<String, BakedModel> parts = new HashMap<>();
            for (String kind : WALL_KINDS) {
                for (int mask = 0; mask < 16; mask++) {
                    for (Direction direction : Direction.values()) {
                        bakePart(parts, baker, modelState,
                                kind + maskName(mask, direction), "integration_v4/crate/");
                    }
                }
            }
            for (int mask : VENT_MASKS) {
                for (Direction direction : VENT_DIRECTIONS) {
                    bakePart(parts, baker, modelState,
                            "vent" + maskName(mask, direction), "integration_v4/crate/");
                }
            }
            for (Direction direction : Direction.values()) {
                bakePart(parts, baker, modelState, "output_" + direction.getSerializedName(), "crate_face/");
            }
            Material particleMaterial = new Material(TextureAtlas.LOCATION_BLOCKS,
                    ResourceLocation.fromNamespaceAndPath("create", "block/vault/vault_front_small"));
            return new CrateShellModel(parts, spriteGetter.apply(particleMaterial));
        }

        private static String maskName(int mask, Direction direction) {
            return "_" + (mask < 10 ? "0" : "") + mask + "_" + direction.getSerializedName();
        }

        private static void bakePart(Map<String, BakedModel> parts, ModelBaker baker, ModelState modelState,
                                     String name, String folder) {
            BakedModel baked = baker.bake(
                    ResourceLocation.fromNamespaceAndPath("aircrate", "block/" + folder + name), modelState);
            if (baked != null) parts.put(name, baked);
        }
    }

    public static final class Loader implements IGeometryLoader<Geometry> {
        @Override
        public Geometry read(JsonObject jsonObject, JsonDeserializationContext deserializationContext) {
            return new Geometry();
        }
    }
}

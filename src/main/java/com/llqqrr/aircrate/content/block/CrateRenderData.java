package com.llqqrr.aircrate.content.block;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.EnumMap;

/**
 * Per-face render plan for one crate member block, computed in {@code getModelData()}.
 * Each exposed face carries its connected-texture edge mask: bit 0 = outer left edge,
 * bit 1 = outer right edge, bit 2 = outer top edge, bit 3 = outer bottom edge, as seen
 * from outside the structure.
 */
public record CrateRenderData(CrateMode mode, EnumMap<Direction, Face> faces) {
    public enum Kind {
        /** Shared with another crate block of the same structure: nothing is drawn. */
        HIDDEN,
        /** Ordinary wall panel in the controller's current mode. */
        MODE,
        /** Reinforced end panel (both ends of the primary axis, except the vent cell). */
        PANEL,
        /** The single vent cell at the top-left of the vent end, with the inset fan. */
        VENT,
        /** Active Create-style output port (blockstate OUTPUT_OPEN). */
        OUTPUT
    }

    public record Face(Kind kind, int mask) {
        public static final Face HIDDEN = new Face(Kind.HIDDEN, 0);
        public static final Face OUTPUT = new Face(Kind.OUTPUT, 0);
    }

    public static final ModelProperty<CrateRenderData> KEY = new ModelProperty<>();

    /** Single-block fallback used for items and blocks without structure data. */
    public static CrateRenderData fallback() {
        EnumMap<Direction, Face> faces = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) faces.put(direction, new Face(Kind.MODE, 15));
        faces.put(Direction.SOUTH, new Face(Kind.VENT, 15));
        faces.put(Direction.NORTH, new Face(Kind.PANEL, 15));
        return new CrateRenderData(CrateMode.GLASS, faces);
    }
}

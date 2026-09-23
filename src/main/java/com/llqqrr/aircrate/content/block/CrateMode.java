package com.llqqrr.aircrate.content.block;

import net.minecraft.util.StringRepresentable;

/** Wall panel style of an aviation crate, cycled with a Create wrench. */
public enum CrateMode implements StringRepresentable {
    GLASS("glass"),
    BARS("bars"),
    CLOSED("closed");

    private final String id;

    CrateMode(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public CrateMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static CrateMode byName(String name) {
        for (CrateMode mode : values()) {
            if (mode.id.equals(name)) return mode;
        }
        return GLASS;
    }
}

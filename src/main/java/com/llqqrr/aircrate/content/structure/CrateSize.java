package com.llqqrr.aircrate.content.structure;

/** Horizontal width x vertical height x horizontal length. */
public record CrateSize(int width, int height, int length) {
    public static final int MAX_WIDTH = 5;
    public static final int MAX_HEIGHT = 3;
    public static final int MAX_LENGTH = 32;

    public CrateSize {
        if (width < 1 || width > MAX_WIDTH || height < 1 || height > MAX_HEIGHT
                || length < 1 || length > MAX_LENGTH) {
            throw new IllegalArgumentException("Aviation crate size must be within 1..5 x 1..3 x 1..32");
        }
    }

    public int volume() {
        return width * height * length;
    }
}

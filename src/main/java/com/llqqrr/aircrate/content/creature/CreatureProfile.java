package com.llqqrr.aircrate.content.creature;

import java.util.List;

/** Data-pack registration will replace these defaults; dimensions are in whole block cells. */
public record CreatureProfile(String id, int width, int height, int length,
                              boolean mayRotateHorizontally, int juvenilePercent) {
    public CreatureProfile {
        if (width < 1 || height < 1 || length < 1) {
            throw new IllegalArgumentException("Creature profile dimensions must be positive");
        }
        juvenilePercent = Math.clamp(juvenilePercent, 1, 100);
    }

    public int volume() {
        return width * height * length;
    }

    public List<Footprint> footprints() {
        Footprint primary = new Footprint(width, height, length);
        if (!mayRotateHorizontally || width == length) {
            return List.of(primary);
        }
        return List.of(primary, new Footprint(length, height, width));
    }

    public record Footprint(int width, int height, int length) {
        public int volume() { return width * height * length; }
    }
}

"""Aviation crate face textures: light aviation-aluminum frame + brass corners + big glass.

Palette:
  K dark aluminum edge, A mid aluminum, L light aluminum edge highlight
  B brass mid, G brass light, H brass dark
  g glass base (translucent), G2 glass shine — using digits: 1 glass base, 2 glass shine, 3 glass edge
"""
from texlib import grid_to_image, save, zoom
from PIL import Image
import os

PAL = {
    "K": (58, 66, 74, 255),     # dark aluminum
    "A": (122, 133, 146, 255),  # mid aluminum
    "L": (172, 183, 195, 255),  # light aluminum
    "B": (154, 116, 51, 255),   # brass mid
    "G": (201, 163, 74, 255),   # brass light
    "H": (107, 79, 34, 255),    # brass dark
    "1": (196, 224, 233, 96),   # glass base (translucent)
    "2": (240, 250, 252, 120),  # glass shine
    "3": (150, 185, 198, 120),  # glass edge tint
}

# Side face: beveled aluminum frame, brass corner rivets, big glass pane with shine streaks
SIDE = [
    "KAAAAAAAAAAAAAAK",
    "ALLLLLLLLLLLLLAA"[:16],
    "AL333333333333LA",
    "AL311111111111LA",
    "AL311111221111LA",
    "AL311111122111LA",
    "AL31111111221ULA".replace("U", "1"),
    "AL311221111121LA",
    "AL311122111111LA",
    "AL311112211111LA",
    "AL321111221111LA",
    "AL312111112211LA",
    "AL311111111221LA",
    "AL311111111112LA",
    "AL333333333333LA",
    "KAAAAAAAAAAAAAAK",
]

# with brass corner rivets overlaid (corners 2x2)
def with_corners(grid):
    rows = [list(r) for r in grid]
    for cx, cy in [(0, 0), (14, 0), (0, 14), (14, 14)]:
        rows[cy][cx] = "H"; rows[cy][cx + 1] = "B"
        rows[cy + 1][cx] = "B"; rows[cy + 1][cx + 1] = "G"
    return ["".join(r) for r in rows]


if __name__ == "__main__":
    side = grid_to_image(with_corners(SIDE), PAL)
    save(side, "aviation_crate_side")
    # preview on dark + light background
    TMP = os.path.expandvars(r"%TEMP%")
    for name, bgc in [("dark", (26, 42, 58, 255)), ("lite", (200, 200, 200, 255))]:
        bg = Image.new("RGBA", side.size, bgc)
        bg.alpha_composite(side)
        zoom(bg, 16).save(os.path.join(TMP, f"crate_side_{name}.png"))
    print("ok")

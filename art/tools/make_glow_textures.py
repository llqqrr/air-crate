"""Generate emissive (glow overlay) textures for the frogport veins and resonator sculk ring.

- creature_frogport_port_glow.png: the dark-slate stripe pixels (53,52,61) of the frogport
  atlas become bright cyan sculk-vein pixels; everything else transparent.
- integration_v4/sculk_glow.png: the cyan vein pixels of sculk_active.png boosted to full
  brightness; the dark sculk background becomes transparent.
- transparent.png: fully transparent 16x16 used to blank out a texture slot in glow models.

Idempotent: re-run any time. Run with `python -X utf8`.
"""
import io
import os
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "src" / "main" / "resources" / "assets" / "aircrate" / "textures"

CYAN_BRIGHT = (63, 255, 255, 255)
CYAN_MID = (8, 205, 209, 255)
CYAN_DIM = (12, 150, 160, 255)
VEIN_SOURCE = (53, 52, 61)


def hash2(x, y):
    return (x * 73 + y * 149 + x * y * 11) % 100


def make_frogport_glow():
    src = Image.open(os.path.join(ROOT, "block/creature_frogport_port.png")).convert("RGBA")
    w, h = src.size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    count = 0
    for y in range(h):
        for x in range(w):
            r, g, b, a = src.getpixel((x, y))
            if a == 0 or (r, g, b) != VEIN_SOURCE:
                continue
            v = hash2(x, y)
            out.putpixel((x, y), CYAN_BRIGHT if v < 25 else CYAN_MID if v < 75 else CYAN_DIM)
            count += 1
    out.save(os.path.join(ROOT, "block/creature_frogport_port_glow.png"))
    print("frogport glow pixels:", count)


def make_sculk_glow():
    src = Image.open(os.path.join(ROOT, "block/integration_v4/sculk_active.png")).convert("RGBA")
    w, h = src.size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    count = 0
    for y in range(h):
        for x in range(w):
            r, g, b, a = src.getpixel((x, y))
            if a == 0 or not (g > 55 and b > 65):  # cyan vein pixels only
                continue
            v = hash2(x, y)
            out.putpixel((x, y), CYAN_BRIGHT if v < 35 else CYAN_MID if v < 85 else CYAN_DIM)
            count += 1
    out.save(os.path.join(ROOT, "block/integration_v4/sculk_glow.png"))
    print("sculk glow pixels:", count)


def make_transparent():
    Image.new("RGBA", (16, 16), (0, 0, 0, 0)).save(
        os.path.join(ROOT, "block/integration_v4/transparent.png"))
    print("transparent.png written")


if __name__ == "__main__":
    make_frogport_glow()
    make_sculk_glow()
    make_transparent()

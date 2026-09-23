"""Creature packager: recolor Create's packager to brass at exactly the positions
fluidlogistics recolored to copper (verified pixel-diff mask).

Mask rule per pixel: create != fluid AND fluid pixel is copper-ish (hue 350-40 deg,
sat > 0.12). Replacement: keep Create's value, map hue to brass 45 deg with
Create-brass-like saturation.
"""
from pathlib import Path
import colorsys

from PIL import Image

from texlib import SRC, save, zoom

CREATE_TEX = Path.home() / "AppData/Local/Temp/createtex/assets/create/textures/block"
FLUID_TEX = Path.home() / "AppData/Local/Temp/fluidtex/assets/fluidlogistics/textures/block/fluid_packager"

FILES = [
    "packager_vertical_powered",
    "packager_vertical_unpowered",
    "packager_vertical_linked",
    "packager_horizontal_powered",
    "packager_horizontal_unpowered",
    "packager_horizontal_linked",
    "packager_iris_open",
    "packager_iris_closed",
    "packager_frame",
    "packager_details",
    "packager_particle",
]

BRASS_HUE = 45 / 360.0


def is_copper(pixel):
    r, g, b, a = pixel
    if a == 0:
        return False
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    deg = h * 360
    return (deg >= 350 or deg <= 40) and s > 0.12


def to_brass(pixel):
    r, g, b, a = pixel
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    ns = min(1.0, 0.35 + s * 0.9)
    r2, g2, b2 = colorsys.hsv_to_rgb(BRASS_HUE, ns, v)
    return (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)


def recolor(name):
    create = Image.open(CREATE_TEX / f"{name}.png").convert("RGBA")
    fluid = Image.open(FLUID_TEX / f"{name}.png").convert("RGBA")
    out = create.copy()
    px_c, px_f, px_o = create.load(), fluid.load(), out.load()
    count = 0
    for y in range(create.height):
        for x in range(create.width):
            if px_c[x, y] != px_f[x, y] and is_copper(px_f[x, y]):
                px_o[x, y] = to_brass(px_c[x, y])
                count += 1
    return out, count


if __name__ == "__main__":
    tiles = []
    for name in FILES:
        im, count = recolor(name)
        save(im, f"creature_{name}")
        tiles.append((name, zoom(im, 8)))
        print(f"{name}: {count} px recolored")
    w = max(t.width for _, t in tiles)
    h = sum(t.height + 8 for _, t in tiles)
    sheet = Image.new("RGBA", (w, h), (26, 42, 58, 255))
    y = 0
    for _, t in tiles:
        sheet.paste(t, (0, y), t)
        y += t.height + 8
    sheet.save(SRC.parent / "previews/creature_packager_contact.png")
    print("contact sheet done")

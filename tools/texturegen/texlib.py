"""Shared helpers for aircrate texture generation.

Pixel art is authored as ASCII grids: one character per pixel, mapped through a
palette dict. Layers are composited in order. Output is written as PNG with
nearest-neighbor upscaled copies for preview sheets.
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / "art"
SRC = ART / "src_textures"
PREVIEWS = ART / "previews"


def grid_to_image(grid, palette, scale=1):
    """grid: list[str] or str with newlines. palette: {char: (r,g,b,a)}. '.' is transparent by convention."""
    if isinstance(grid, str):
        grid = [line for line in grid.strip("\n").split("\n")]
    h = len(grid)
    w = max(len(line) for line in grid)
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()
    for y, line in enumerate(grid):
        for x, ch in enumerate(line):
            if ch in (" ", "."):
                continue
            if ch not in palette:
                raise KeyError(f"palette missing {ch!r} at ({x},{y})")
            px[x, y] = palette[ch]
    if scale != 1:
        im = im.resize((w * scale, h * scale), Image.NEAREST)
    return im


def save(im, name, folder=SRC):
    folder.mkdir(parents=True, exist_ok=True)
    out = folder / f"{name}.png"
    im.save(out)
    return out


def hue_recolor(src, hue_of, hue_to, sat_scale=1.0, val_scale=1.0):
    """Recolor pixels whose hue is near hue_of (degrees) toward hue_to, preserving S/V distances."""
    import colorsys

    im = Image.open(src).convert("RGBA")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            hd = abs(h * 360 - hue_of)
            hd = min(hd, 360 - hd)
            if hd <= 45 and s > 0.12:
                dh = ((h * 360 - hue_of + 180) % 360 - 180) / 360.0
                nh = (hue_to / 360.0 + dh * 0.6) % 1.0
                ns = min(1.0, s * sat_scale)
                nv = min(1.0, v * val_scale)
                r2, g2, b2 = colorsys.hsv_to_rgb(nh, ns, nv)
                px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    return im


def zoom(im_or_path, scale=8):
    im = Image.open(im_or_path).convert("RGBA") if isinstance(im_or_path, (str, Path)) else im_or_path
    return im.resize((im.width * scale, im.height * scale), Image.NEAREST)

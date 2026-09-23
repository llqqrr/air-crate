"""Aircrate assets v2: multi-tone ramps, bevels, rivets, noise, per-face designs.

Light direction: top-left. Deterministic noise via seeded random.
Blocks: aviation_crate (side/top/bottom), interaction_hatch (face/panel),
echo_resonator (side/top/bottom), creature_parcel (front/side/top).
Items: mixed_feed, creature_filter, creature_catcher.
"""
import random
from PIL import Image
from texlib import grid_to_image, save, SRC

# ---------------- ramps ----------------
ALU = {"xl": (198, 208, 218, 255), "l": (172, 183, 195, 255), "m": (122, 133, 146, 255),
       "d": (78, 87, 98, 255), "k": (45, 52, 61, 255)}
BRASS = {"y": (232, 196, 104, 255), "g": (201, 163, 74, 255), "b": (154, 116, 51, 255),
         "h": (107, 79, 34, 255), "k": (74, 54, 22, 255)}
GLASS = {"base": (198, 226, 235, 72), "lite": (226, 244, 250, 88), "shine": (244, 252, 254, 150),
         "edge": (148, 186, 200, 120), "deep": (150, 190, 205, 60)}
CARD = {"l": (222, 190, 138, 255), "m": (196, 160, 108, 255), "d": (158, 124, 76, 255),
        "k": (120, 92, 54, 255), "tape": (236, 226, 206, 255), "tape_d": (206, 194, 168, 255)}
ANDESITE = {"xl": (172, 172, 164, 255), "l": (150, 150, 142, 255), "m": (110, 110, 104, 255),
            "d": (78, 78, 73, 255), "k": (46, 50, 54, 255)}
SCULK = {"bg": (14, 38, 44, 255), "m": (20, 84, 92, 255), "v": (29, 180, 190, 255),
         "glow": (92, 225, 230, 255)}
AMETHYST = {"l": (196, 150, 232, 255), "m": (144, 96, 196, 255), "d": (96, 58, 146, 255),
            "k": (64, 36, 104, 255)}

rng = random.Random(20260917)


def noise(im, amount=7, alpha_keep=False):
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            n = rng.randint(-amount, amount)
            px[x, y] = (max(0, min(255, r + n)), max(0, min(255, g + n)), max(0, min(255, b + n)), a)
    return im


def panel(ramp, rivets=True, seam=False):
    """Beveled 16x16 metal plate. ramp: dict with xl,l,m,d,k."""
    im = Image.new("RGBA", (16, 16), ramp["m"])
    px = im.load()
    for i in range(16):
        px[i, 0] = ramp["k"]; px[i, 15] = ramp["k"]
        px[0, i] = ramp["k"]; px[15, i] = ramp["k"]
    for i in range(1, 15):
        px[i, 1] = ramp["l"]; px[1, i] = ramp["l"]       # top-left light bevel
        px[i, 14] = ramp["d"]; px[14, i] = ramp["d"]     # bottom-right dark bevel
    px[1, 1] = ramp["xl"]; px[14, 14] = ramp["d"]
    px[1, 14] = ramp["d"]; px[14, 1] = ramp["l"]
    if rivets:
        for cx, cy in [(3, 3), (12, 3), (3, 12), (12, 12)]:
            px[cx, cy] = ramp["k"]
            px[cx + 1, cy] = ramp["d"]
            px[cx, cy + 1] = ramp["d"]
            px[cx + 1, cy + 1] = ramp["l"]
    if seam:
        for i in range(2, 14):
            px[i, 8] = ramp["d"]
            px[i, 9] = ramp["l"]
    return im


def brass_corners(im, size=2):
    px = im.load()
    for ox, oy in [(0, 0), (16 - size, 0), (0, 16 - size), (16 - size, 16 - size)]:
        for dy in range(size):
            for dx in range(size):
                px[ox + dx, oy + dy] = BRASS["b"]
        px[ox, oy] = BRASS["y"]
        px[ox + size - 1, oy + size - 1] = BRASS["h"]
        px[ox + size - 1, oy] = BRASS["g"]
        px[ox, oy + size - 1] = BRASS["g"]
    return im


def glass_fill(im, x0=2, y0=2, x1=14, y1=14):
    px = im.load()
    for y in range(y0, y1):
        for x in range(x0, x1):
            edge = x in (x0, x1 - 1) or y in (y0, y1 - 1)
            t = (y - y0) / max(1, (y1 - y0 - 1))
            base = GLASS["edge"] if edge else (
                GLASS["lite"] if t < 0.3 else GLASS["base"] if t < 0.7 else GLASS["deep"])
            px[x, y] = base
    # two parallel diagonal shine streaks
    for i in range(5):
        for sx, sy, w in [(4, 9, 2), (8, 12, 1)]:
            pass
    for k in range(6):
        x, y = 4 + k, 10 - k
        if y0 < y < y1 - 1 and x0 < x < x1 - 1:
            px[x, y] = GLASS["shine"]
            if k < 5 and y0 < y - 1:
                px[x + 1, y] = GLASS["shine"]
    for k in range(4):
        x, y = 9 + k, 12 - k
        if y0 < y < y1 - 1 and x0 < x < x1 - 1:
            px[x, y] = GLASS["shine"]
    return im


# ---------------- aviation crate ----------------
def crate_side():
    im = panel(ALU, rivets=False)
    glass_fill(im)
    brass_corners(im)
    return noise(im, 4)


def crate_top():
    im = panel(ALU, rivets=True, seam=True)
    brass_corners(im)
    return noise(im, 5)


def crate_bottom():
    im = panel(ALU, rivets=False)
    px = im.load()
    for y in range(2, 14):
        for x in range(2, 14):
            r, g, b, a = px[x, y]
            px[x, y] = (round(r * 0.72), round(g * 0.72), round(b * 0.72), a)
    brass_corners(im)
    return noise(im, 5)


# ---------------- interaction hatch ----------------
def hatch_face():
    im = panel(ALU, rivets=False)
    px = im.load()
    # brass octagon ring
    ring = [(5, 3), (6, 3), (7, 3), (8, 3), (9, 3), (10, 3),
            (4, 4), (11, 4), (3, 5), (12, 5), (3, 6), (12, 6),
            (3, 7), (12, 7), (3, 8), (12, 8), (3, 9), (12, 9),
            (3, 10), (12, 10), (4, 11), (11, 11),
            (5, 12), (6, 12), (7, 12), (8, 12), (9, 12), (10, 12)]
    for i, (x, y) in enumerate(ring):
        px[x, y] = BRASS["g"] if y < 8 else BRASS["b"]
    for x, y in [(4, 4), (11, 4)]:
        px[x, y] = BRASS["y"]
    for x, y in [(4, 11), (11, 11)]:
        px[x, y] = BRASS["h"]
    # port interior: dark with vertical gradient
    for y in range(4, 12):
        for x in range(4, 12):
            if (x, y) in ring:
                continue
            t = (y - 4) / 7
            v = round(46 - 22 * t)
            px[x, y] = (v, v + 6, v + 12, 255)
    # brush: handle bottom-left to bristles top-right + milk drop
    brush = [(5, 10), (6, 9), (7, 8), (8, 7), (9, 6)]
    for x, y in brush:
        px[x, y] = (130, 96, 58, 255)
    for x, y in [(9, 5), (10, 6), (10, 5)]:
        px[x, y] = (228, 220, 196, 255)
    for x, y in [(5, 6), (5, 7), (6, 6)]:
        px[x, y] = (248, 248, 242, 255)
    px[5, 6] = (255, 255, 252, 255)
    return noise(im, 3)


# ---------------- echo resonator ----------------
def resonator_side():
    im = Image.new("RGBA", (16, 16), ANDESITE["m"])
    px = im.load()
    # casing base rows 11-15
    for y in range(11, 16):
        for x in range(16):
            px[x, y] = ANDESITE["m"]
    for i in range(16):
        px[i, 15] = ANDESITE["k"]; px[i, 11] = ANDESITE["l"]
    for x, y in [(2, 13), (13, 13)]:
        px[x, y] = ANDESITE["k"]; px[x + 1, y + 1] = ANDESITE["l"]
    # sculk band rows 7-10 with veins
    for y in range(7, 11):
        for x in range(16):
            px[x, y] = SCULK["bg"] if (x + y) % 3 else SCULK["m"]
    veins = [(1, 8), (2, 8), (3, 9), (6, 7), (7, 8), (9, 9), (10, 8), (12, 7), (13, 8), (14, 9)]
    for x, y in veins:
        px[x, y] = SCULK["v"]
    px[7, 8] = SCULK["glow"]
    # amethyst cluster rows 0-6: three crystal spikes
    spikes = [
        [(4, 6), (5, 6), (4, 5), (5, 5), (4, 4), (5, 4), (5, 3), (5, 2)],   # left
        [(7, 6), (8, 6), (7, 5), (8, 5), (7, 4), (8, 4), (8, 3), (8, 2), (8, 1), (8, 0)],  # center tall
        [(10, 6), (11, 6), (10, 5), (11, 5), (11, 4), (11, 3)],  # right
    ]
    for spike in spikes:
        for i, (x, y) in enumerate(spike):
            px[x, y] = AMETHYST["d"] if i < 2 else AMETHYST["m"]
    for x, y in [(5, 2), (8, 0), (8, 1), (11, 3), (4, 3)]:
        px[x, y] = AMETHYST["l"]
    px[8, 0] = (232, 208, 250, 255)
    return noise(im, 5)


def resonator_top():
    im = Image.new("RGBA", (16, 16), SCULK["bg"])
    px = im.load()
    for y in range(16):
        for x in range(16):
            if (x + 2 * y) % 4 == 0:
                px[x, y] = SCULK["m"]
    # top-down crystal cluster: center cross + buds
    for x, y in [(7, 7), (8, 7), (7, 8), (8, 8)]:
        px[x, y] = AMETHYST["m"]
    for x, y in [(7, 5), (8, 4), (5, 7), (4, 8), (10, 7), (11, 8), (7, 10), (8, 11)]:
        px[x, y] = AMETHYST["d"]
    for x, y in [(8, 5), (6, 8), (9, 7), (7, 9)]:
        px[x, y] = AMETHYST["l"]
    for x, y in [(7, 6), (8, 6), (6, 7), (9, 8)]:
        px[x, y] = AMETHYST["k"]
    px[7, 7] = (232, 208, 250, 255)
    # ring border
    for i in range(16):
        px[i, 0] = ANDESITE["k"]; px[i, 15] = ANDESITE["k"]
        px[0, i] = ANDESITE["k"]; px[15, i] = ANDESITE["k"]
    return noise(im, 4)


def resonator_bottom():
    return noise(panel(ANDESITE, rivets=True), 5)


# ---------------- creature parcel ----------------
def parcel_side():
    im = Image.new("RGBA", (16, 16), CARD["m"])
    px = im.load()
    for i in range(16):
        px[i, 0] = CARD["k"]; px[i, 15] = CARD["k"]
        px[0, i] = CARD["k"]; px[15, i] = CARD["k"]
        px[i, 1] = CARD["l"]; px[1, i] = CARD["l"]
        px[i, 14] = CARD["d"]; px[14, i] = CARD["d"]
    # vertical tape seam
    for y in range(2, 14):
        px[8, y] = CARD["tape_d"] if y % 3 else CARD["tape"]
    return noise(im, 6)


def parcel_top():
    im = Image.new("RGBA", (16, 16), CARD["m"])
    px = im.load()
    for i in range(16):
        px[i, 0] = CARD["k"]; px[i, 15] = CARD["k"]
        px[0, i] = CARD["k"]; px[15, i] = CARD["k"]
        px[i, 1] = CARD["l"]; px[1, i] = CARD["l"]
        px[i, 14] = CARD["d"]; px[14, i] = CARD["d"]
    for y in range(1, 15):
        px[7, y] = CARD["tape"]; px[8, y] = CARD["tape_d"]
    for x in range(1, 15):
        px[x, 7] = CARD["tape"]; px[x, 8] = CARD["tape_d"]
    return noise(im, 6)


def parcel_front():
    im = Image.new("RGBA", (16, 16), CARD["m"])
    px = im.load()
    for i in range(16):
        px[i, 0] = CARD["k"]; px[i, 15] = CARD["k"]
        px[0, i] = CARD["k"]; px[15, i] = CARD["k"]
    for x in range(1, 15):
        px[x, 1] = CARD["tape"]; px[x, 2] = CARD["tape_d"]
    # glass window 3..12 x 4..13
    for y in range(4, 13):
        for x in range(3, 13):
            edge = x in (3, 12) or y in (4, 12)
            px[x, y] = GLASS["edge"] if edge else GLASS["base"]
    for k in range(5):
        x, y = 5 + k, 10 - k
        px[x, y] = GLASS["shine"]
        px[x + 1, y] = GLASS["shine"]
    for k in range(3):
        px[9 + k, 12 - k] = GLASS["shine"]
    # corner rivets
    for x, y in [(1, 14), (14, 14)]:
        px[x, y] = CARD["k"]
    return noise(im, 4)


# ---------------- items (ASCII grids on richer palettes) ----------------
FEED_PAL = {
    "W": (222, 184, 74, 255), "Y": (243, 216, 118, 255), "D": (176, 136, 46, 255),
    "K": (140, 106, 34, 255), "T": (158, 136, 66, 255), "t": (120, 100, 46, 255),
    "R": (126, 92, 52, 255), "r": (96, 68, 36, 255), "L": (156, 122, 76, 255),
    "A": (198, 52, 40, 255), "a": (232, 110, 88, 255), "w": (248, 200, 160, 255),
    "S": (92, 66, 40, 255), "s": (138, 106, 66, 255),
}

MIXED_FEED = [
    "................",
    "......Y..Y......",
    ".....YWY.WY.....",
    "....KYWYYYWK....",
    "....YWYWWYWY....",
    "...YWYWWWWYWY...",
    "...AWKWWYWKWA...",
    "..AaWWWWYWWWaA..",
    "...DWKWWYWKWD...",
    "....RRRRRRR.....",
    "....RLRRRLRr....",
    "....RRRRRRR.....",
    "..S..TTTTT..S...",
    "..s..TtTtT..s...",
    "......TTT.......",
    "......tTt.......",
]

FILTER_PAL = {
    "Y": (232, 196, 104, 255), "G": (201, 163, 74, 255), "B": (154, 116, 51, 255),
    "H": (107, 79, 34, 255), "N": (44, 56, 68, 255), "n": (30, 40, 50, 255),
    "p": (240, 210, 130, 255), "q": (208, 172, 96, 255),
}

CREATURE_FILTER = [
    "................",
    "..YYYYYYYYYYYY..",
    ".YGGGGGGGGGGGGY.",
    ".YGBBBBBBBBBBBGY",
    ".YGBnnnnnnnnnBGY",
    ".YGBnp.p.p.pnBGY",
    ".YGBnpp.p.ppnBGY",
    ".YGBnnpppppnnBGY",
    ".YGBnpppppppnBGY",
    ".YGBnpqqqqqpnBGY",
    ".YGBnnpqqpnnnBGY",
    ".YGBnnnnnnnnnBGY",
    ".YGBBBBBBBBBBBGY",
    ".YHHHHHHHHHHHHY.",
    "..HHHHHHHHHHHH..",
    "................",
]

CATCHER_PAL = {
    "Y": (232, 196, 104, 255), "G": (201, 163, 74, 255), "B": (154, 116, 51, 255),
    "H": (107, 79, 34, 255), "K": (74, 54, 22, 255),
    "E": (22, 26, 30, 255), "e": (52, 60, 68, 255),
    "L": (172, 183, 195, 255), "D": (78, 87, 98, 255),
    "T": (236, 226, 206, 255),
}

CREATURE_CATCHER = [
    "................",
    "....YYYYYYYYY...",
    "...YGGGGGGGGGY..",
    "...GBBEEEBBBBG..",
    "...GBEEKEEBBBG..",
    "...GBEKKKEBBBG..",
    "...GBEEKEBTBBG..",
    "...GBBEEEBBBBG..",
    "...HBBBBBBBBBH..",
    "...HHYYYYYYYHH..",
    "..HHH..KK.......",
    "..HD..KK........",
    "..HD.KK.........",
    "..HDKK..........",
    "..DK............",
    "................",
]

ITEMS = [
    ("mixed_feed", MIXED_FEED, FEED_PAL),
    ("creature_filter", CREATURE_FILTER, FILTER_PAL),
    ("creature_catcher", CREATURE_CATCHER, CATCHER_PAL),
]

BLOCKS = {
    "aviation_crate_side": crate_side,
    "aviation_crate_top": crate_top,
    "aviation_crate_bottom": crate_bottom,
    "interaction_hatch_face": hatch_face,
    "interaction_hatch_panel": lambda: noise(panel(ALU, rivets=True), 4),
    "echo_resonator_side": resonator_side,
    "echo_resonator_top": resonator_top,
    "echo_resonator_bottom": resonator_bottom,
    "creature_parcel_front": parcel_front,
    "creature_parcel_side": parcel_side,
    "creature_parcel_top": parcel_top,
}

if __name__ == "__main__":
    import os
    TMP = os.path.expandvars(r"%TEMP%")
    tiles = []
    for name, fn in BLOCKS.items():
        im = fn()
        save(im, name)
        tiles.append((name, im))
    for name, grid, pal in ITEMS:
        for row in grid:
            assert len(row) == 16, f"{name}: {len(row)} {row!r}"
        im = grid_to_image(grid, pal)
        save(im, name)
        tiles.append((name, im))
    # quick contact sheet on gray
    W = sum(16 * 8 + 12 for _, _ in tiles)
    sheet = Image.new("RGBA", (W, 16 * 8 + 24), (200, 200, 200, 255))
    x = 6
    for name, im in tiles:
        sheet.alpha_composite(im.resize((128, 128), Image.NEAREST), (x, 12))
        x += 140
    sheet.save(os.path.join(TMP, "v2_contact.png"))
    print("ok", len(tiles), "textures")

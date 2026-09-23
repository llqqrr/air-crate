"""Flat item + machine face textures for aircrate, authored as 16x16 ASCII grids.

Assets: mixed_feed, creature_filter, creature_catcher (handheld packager),
interaction hatch face, echo resonator face, creature parcel face.
"""
import os
from PIL import Image
from texlib import grid_to_image, save, zoom

FEED_PAL = {
    "W": (222, 184, 74, 255), "Y": (243, 216, 118, 255), "D": (176, 136, 46, 255),
    "T": (158, 136, 66, 255), "R": (126, 92, 52, 255), "r": (104, 74, 40, 255),
    "A": (198, 52, 40, 255), "a": (232, 110, 88, 255),
    "S": (92, 66, 40, 255), "s": (138, 106, 66, 255),
}

FILTER_PAL = {
    "H": (107, 79, 34, 255), "B": (154, 116, 51, 255), "G": (201, 163, 74, 255),
    "N": (38, 48, 58, 255), "p": (233, 201, 120, 255),
}

CATCHER_PAL = {
    "H": (107, 79, 34, 255), "B": (154, 116, 51, 255), "G": (201, 163, 74, 255),
    "K": (58, 66, 74, 255), "L": (172, 183, 195, 255),
    "C": (196, 160, 108, 255), "c": (222, 190, 138, 255), "T": (232, 222, 200, 255),
    "E": (26, 30, 34, 255),
}

HATCH_PAL = {
    "K": (58, 66, 74, 255), "A": (122, 133, 146, 255), "L": (172, 183, 195, 255),
    "B": (154, 116, 51, 255), "G": (201, 163, 74, 255), "H": (107, 79, 34, 255),
    "E": (30, 36, 42, 255), "W": (245, 245, 240, 255),
    "b": (130, 96, 58, 255), "f": (222, 214, 190, 255),
}

RESONATOR_PAL = {
    "K": (46, 50, 54, 255), "A": (110, 110, 104, 255), "L": (150, 150, 142, 255),
    "S": (14, 38, 44, 255), "s": (20, 84, 92, 255), "V": (29, 180, 190, 255),
    "P": (122, 74, 168, 255), "p": (168, 120, 210, 255), "D": (80, 44, 120, 255),
}

PARCEL_PAL = {
    "C": (186, 148, 96, 255), "c": (212, 176, 122, 255), "D": (146, 112, 66, 255),
    "T": (232, 222, 200, 255), "t": (208, 196, 170, 255),
    "1": (196, 224, 233, 110), "2": (240, 250, 252, 140), "3": (150, 185, 198, 140),
}

# ---------------- grids (16x16) ----------------

MIXED_FEED = [
    "................",
    "......Y..Y......",
    ".....YWY.WY.....",
    ".....YWWYYY.....",
    "....YWYWWWYW....",
    "...YWYWWWWYWY...",
    "...AWWWWYWWWA...",
    "...aYWWWYWWYa...",
    "....WWWWWWW.....",
    "....RRRRRRR.....",
    "....RrRRRrR.....",
    "....RRRRRRR.....",
    "..S..TTTTT..S...",
    "..s..TTTTT..s...",
    "......TTT.......",
    "................",
]

CREATURE_FILTER = [
    "................",
    "..GGGGGGGGGGGG..",
    ".GBBBBBBBBBBBBBG",
    ".GBNNNNNNNNNNBG.",
    ".GBNp.p..p.pNBG.",
    ".GBNpp..p.ppNBG.",
    ".GBN..pppp..NBG.",
    ".GBN.pppppp.NBG.",
    ".GBN.pppppp.NBG.",
    ".GBN..pppp..NBG.",
    ".GBNNNNNNNNNNBG.",
    ".GBNNNNNNNNNNBG.",
    ".GBBBBBBBBBBBBBG",
    ".GHHHHHHHHHHHHG.",
    "................",
    "................",
]

CREATURE_CATCHER = [
    "................",
    ".....GGGGGGG....",
    "....GBBBBBBBBG..",
    "....GBBEEEBBBG..",
    "....GBBEKEBBG...",
    "....GBBEEEBBG...",
    "....GBBTBBBBG...",
    "....GBBBBBBBG...",
    "....HH..GGGG....",
    "...KH...........",
    "...KH...........",
    "..KH............",
    "..KH............",
    "..KH............",
    "..KK............",
    "................",
]

HATCH_FACE = [
    "KAAAAAAAAAAAAAAK",
    "ALLLLLLLLLLLLLAA",
    "ALAAAAAAAAAAAAAA",
    "ALAAABBBBBBAAALA",
    "ALAABBEEEEEBBALA",
    "ALABEEEEEEEEBALA",
    "ALABEEWWffEEBALA",
    "ALABEEWWffbEBALA",
    "ALABEEWffbbEBALA",
    "ALABEffbbbEEBALA",
    "ALABEbbbEEEEBALA",
    "ALAABBEEEEEBBALA",
    "ALAAABBBBBBAAALA",
    "ALAAAAAAAAAAAAAA",
    "AAAAAAAAAAAAAAAA",
    "KAAAAAAAAAAAAAAK",
]

RESONATOR_FACE = [
    "......p.....p...",
    ".....pPp...pPp..",
    ".....PDp...PDp..",
    "....pPPDp.pPPDp.",
    "....PDDDPpPDDDP.",
    "...pPPDDPPPDDDp.",
    "...PDDDDDDDDDDP.",
    "..SSSVsSVsVsSSS.",
    "..SVsSsVsSVsVsS.",
    "..SsVsSVsSsVsSS.",
    "..SSSVsSVsVsSSS.",
    ".KAAAAAAAAAAAAAK",
    ".LAAAAAAAAAAAAAL",
    "KAAAAAAAAAAAAAAK",
    "KAAKAAAAAAAAKAAK",
    "KKKKKKKKKKKKKKKK",
]

PARCEL_FACE = [
    "DDCCCCCCCCCCCCDD",
    "DCCCCCCCCCCCCCCD",
    "CCTTTTTTTTTTTTCC",
    "CCTTTTTTTTTTTTCC",
    "CCc3333333333cCC",
    "CCc3111111113cCC",
    "CCc3122111113cCC",
    "CCc3112211113cCC",
    "CCc3111221113cCC",
    "CCc3111112213cCC",
    "CCc3111111123cCC",
    "CCc3333333333cCC",
    "DCCcCCCCCCCCCcDD",
    "DCCCCCCCCCCCCCCD",
    "DDCCCCCCCCCCCCDD",
    "................",
]

ASSETS = [
    ("mixed_feed", MIXED_FEED, FEED_PAL),
    ("creature_filter", CREATURE_FILTER, FILTER_PAL),
    ("creature_catcher", CREATURE_CATCHER, CATCHER_PAL),
    ("interaction_hatch_face", HATCH_FACE, HATCH_PAL),
    ("echo_resonator_face", RESONATOR_FACE, RESONATOR_PAL),
    ("creature_parcel_face", PARCEL_FACE, PARCEL_PAL),
]

if __name__ == "__main__":
    TMP = os.path.expandvars(r"%TEMP%")
    sheet_w = 6 * 16 * 10 + 7 * 10
    sheet = Image.new("RGBA", (sheet_w, 16 * 10 + 20), (200, 200, 200, 255))
    x = 10
    for name, grid, pal in ASSETS:
        for row in grid:
            assert len(row) == 16, f"{name}: row len {len(row)}: {row!r}"
        assert len(grid) == 16, f"{name}: {len(grid)} rows"
        im = grid_to_image(grid, pal)
        save(im, name)
        sheet.alpha_composite(zoom(im, 10), (x, 10))
        x += 16 * 10 + 10
    sheet.save(os.path.join(TMP, "flat_items_sheet.png"))
    print("ok")

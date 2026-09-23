"""Engineering preview sheets v2: iso render + six orthographic face views per block,
flat views for items, texture strips, palette, notes. Dark navy cards, Chinese labels."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from texlib import SRC, PREVIEWS

NAVY = (13, 27, 41, 255)
PANEL = (18, 36, 54, 255)
EDGE = (38, 68, 96, 255)
TEXT = (220, 230, 238, 255)
SUB = (140, 165, 185, 255)
TILE_BG = (24, 46, 66, 255)

FONT = ImageFont.truetype(r"C:\Windows\Fonts\msyh.ttc", 28)
FONT_SM = ImageFont.truetype(r"C:\Windows\Fonts\msyh.ttc", 19)

SCALE = 10


def project(p, origin, s):
    x, y, z = p
    return (origin[0] + (x - z) * s, origin[1] + (x + z) * s * 0.46 - y * s)


def blend(pts, u, v):
    p0, p1, p2, p3 = pts
    return ((1 - u) * (1 - v) * p0[0] + u * (1 - v) * p1[0] + u * v * p2[0] + (1 - u) * v * p3[0],
            (1 - u) * (1 - v) * p0[1] + u * (1 - v) * p1[1] + u * v * p2[1] + (1 - u) * v * p3[1])


def shade(c, f):
    return tuple(max(0, min(255, round(ch * f))) for ch in c[:3]) + (c[3],)


def textured_face(canvas, texture, verts, origin, s, bright):
    texture = texture.convert("RGBA").resize((16, 16), Image.NEAREST)
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    proj = [project(v, origin, s) for v in verts]
    for v in range(16):
        for u in range(16):
            c = texture.getpixel((u, v))
            if c[3] < 16:
                continue
            poly = [blend(proj, u / 16, v / 16), blend(proj, (u + 1) / 16, v / 16),
                    blend(proj, (u + 1) / 16, (v + 1) / 16), blend(proj, u / 16, (v + 1) / 16)]
            d.polygon(poly, fill=shade(c, bright))
    canvas.alpha_composite(layer)


def iso_cube(top, left, right):
    s = SCALE
    w, h = int(34 * s), int(34 * s * 0.62 + 16 * s)
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    origin = (w // 2, int(16 * s) + 6)
    textured_face(im, top, [(0, 16, 0), (16, 16, 0), (16, 16, 16), (0, 16, 16)], origin, s, 1.12)
    textured_face(im, left, [(16, 16, 0), (16, 16, 16), (16, 0, 16), (16, 0, 0)], origin, s, 0.88)
    textured_face(im, right, [(16, 16, 16), (0, 16, 16), (0, 0, 16), (16, 0, 16)], origin, s, 0.68)
    bbox = im.getbbox()
    return im.crop(bbox) if bbox else im


def tex(name):
    return Image.open(SRC / f"{name}.png").convert("RGBA")


def z(im, s):
    return im.resize((im.width * s, im.height * s), Image.NEAREST)


def opaque(im, bg=(222, 226, 230, 255)):
    base = Image.new("RGBA", im.size, bg)
    base.alpha_composite(im)
    return base


def crop16(im, x, y):
    return im.crop((x, y, x + 16, y + 16))


def face_tile(face, label, size=110):
    tile = Image.new("RGBA", (size + 16, size + 34), TILE_BG)
    d = ImageDraw.Draw(tile)
    d.rectangle([0, 0, tile.width - 1, tile.height - 1], outline=EDGE)
    tw = size // 16
    im = z(face, tw)
    tile.alpha_composite(im, ((tile.width - im.width) // 2, 8))
    d.text(((tile.width - d.textlength(label, font=FONT_SM)) // 2, tile.height - 24), label,
           font=FONT_SM, fill=SUB)
    return tile


def block_card(title, sub, faces, unique_textures, palette, notes):
    """faces: dict with keys 正面 背面 左侧 右侧 顶部 底部 (PIL 16x16)."""
    W = 1080
    iso = iso_cube(faces["顶部"], faces["左侧"], faces["右侧"])
    labels = ["正面", "背面", "左侧", "右侧", "顶部", "底部"]
    tiles = [face_tile(faces[k], k) for k in labels]
    grid_w = 3 * tiles[0].width
    tex_strip_h = 100
    H = 76 + max(iso.height, 2 * tiles[0].height + 12) + tex_strip_h + 66 + 32 * len(notes) + 26
    im = Image.new("RGBA", (W, H), PANEL)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, W - 1, H - 1], outline=EDGE, width=2)
    d.text((24, 14), title, font=FONT, fill=TEXT)
    d.text((30 + d.textlength(title, font=FONT), 24), sub, font=FONT_SM, fill=SUB)
    # iso left
    iy = 76 + (max(iso.height, 2 * tiles[0].height) - iso.height) // 2
    im.alpha_composite(iso, (30, iy))
    d.text((30, 76 - 2 - 22), "3D 预览", font=FONT_SM, fill=SUB)
    # six-face grid right
    gx = 30 + iso.width + 46
    gy = 76
    for i, t in enumerate(tiles):
        im.alpha_composite(t, (gx + (i % 3) * t.width, gy + (i // 3) * (t.height + 10)))
    # texture strip
    ty = gy + 2 * tiles[0].height + 22
    d.text((30, ty), "材质贴图（16×16）", font=FONT_SM, fill=SUB)
    tx = 240
    for name, t in unique_textures:
        im.alpha_composite(z(t, 5), (tx, ty - 4))
        d.text((tx, ty + 82), name, font=FONT_SM, fill=SUB)
        tx += 96
    # palette + notes
    py = ty + tex_strip_h + 12
    d.text((30, py), "色板", font=FONT_SM, fill=SUB)
    x = 100
    for c in palette:
        d.rectangle([x, py, x + 26, py + 26], fill=c, outline=(0, 0, 0, 255))
        x += 30
    ny = py + 42
    for n in notes:
        d.text((30, ny), "· " + n, font=FONT_SM, fill=SUB)
        ny += 32
    return im


def item_card(title, sub, texture, palette, notes):
    W = 1080
    big = z(texture, 13)
    small = z(texture, 4)
    H = 76 + big.height + 40 + 32 * len(notes) + 26
    im = Image.new("RGBA", (W, H), PANEL)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, W - 1, H - 1], outline=EDGE, width=2)
    d.text((24, 14), title, font=FONT, fill=TEXT)
    d.text((30 + d.textlength(title, font=FONT), 24), sub, font=FONT_SM, fill=SUB)
    im.alpha_composite(big, (40, 84))
    im.alpha_composite(small, (60 + big.width, 100))
    d.text((60 + big.width, 84), "原始 16×16", font=FONT_SM, fill=SUB)
    x = 60 + big.width
    d.text((x, 190), "色板", font=FONT_SM, fill=SUB)
    y = 216
    for c in palette:
        d.rectangle([x, y, x + 26, y + 26], fill=c, outline=(0, 0, 0, 255))
        x += 30
    ny = 76 + big.height + 16
    for n in notes:
        d.text((30, ny), "· " + n, font=FONT_SM, fill=SUB)
        ny += 32
    return im


ALU_PAL = [(198, 208, 218, 255), (122, 133, 146, 255), (45, 52, 61, 255)]
BRASS_PAL = [(232, 196, 104, 255), (154, 116, 51, 255), (107, 79, 34, 255)]
GLASS_PAL = [(198, 226, 235, 200), (244, 252, 254, 220)]


def main():
    cards = []

    crate_side, crate_top, crate_bottom = tex("aviation_crate_side"), tex("aviation_crate_top"), tex("aviation_crate_bottom")
    cards.append(block_card("航空箱  Aviation Crate", "aviation_crate（方块）",
        {"正面": crate_side, "背面": crate_side, "左侧": crate_side, "右侧": crate_side,
         "顶部": crate_top, "底部": crate_bottom},
        [("侧面", crate_side), ("顶部", crate_top), ("底部", crate_bottom)],
        ALU_PAL + BRASS_PAL + GLASS_PAL,
        ["航空铝浅灰蓝边框 + 黄铜角件，与保险库深钢色明确区分",
         "大面积玻璃：多块拼接走 Create 连接纹理，连成整面",
         "扳手右键切玻璃窗/开放（流体储罐语义），开窗变体建模阶段做"]))

    hatch, panel_ = tex("interaction_hatch_face"), tex("interaction_hatch_panel")
    cards.append(block_card("交互舱口  Interaction Hatch", "crate_interaction_hatch（方块）",
        {"正面": hatch, "背面": panel_, "左侧": panel_, "右侧": panel_, "顶部": panel_, "底部": panel_},
        [("正面", hatch), ("其余面", panel_)],
        ALU_PAL + BRASS_PAL + [(245, 245, 240, 255), (130, 96, 58, 255)],
        ["边框与航空箱同族，中央黄铜圆环操作口",
         "口内毛刷 + 奶滴图标，体现'照顾动物'功能",
         "面板件：其余面为铆接铝板"]))

    rs, rt, rb = tex("echo_resonator_side"), tex("echo_resonator_top"), tex("echo_resonator_bottom")
    cards.append(block_card("回响激发器  Echo Resonator", "amethyst_sculk_resonator（拟改名）",
        {"正面": rs, "背面": rs, "左侧": rs, "右侧": rs, "顶部": rt, "底部": rb},
        [("侧面", rs), ("顶部", rt), ("底部", rb)],
        [(110, 110, 104, 255), (14, 38, 44, 255), (29, 180, 190, 255),
         (196, 150, 232, 255), (96, 58, 146, 255)],
        ["安山机壳底座 + 幽匿脉络环带 + 紫晶簇顶",
         "紫晶可发光：蛙港在范围内时常亮（建模阶段做状态切换）",
         "改名回响激发器：仅 lang 显示名，注册 id 不动"]))

    pf, ps, pt = tex("creature_parcel_front"), tex("creature_parcel_side"), tex("creature_parcel_top")
    cards.append(block_card("生物包裹  Creature Parcel", "creature_parcel（物品/渲染）",
        {"正面": pf, "背面": ps, "左侧": ps, "右侧": ps, "顶部": pt, "底部": ps},
        [("正面(玻璃窗)", pf), ("侧面", ps), ("顶部(胶带)", pt)],
        [(222, 190, 138, 255), (158, 124, 76, 255), (236, 226, 206, 255)] + GLASS_PAL,
        ["纸板壳 + 封箱胶带 + 正面大玻璃窗（参考流体包裹模组的流体包裹）",
         "窗内由渲染器画生物本体，手上/背包/掉落物均可见",
         "替换现有'玻璃方块+苔藓垫'临时渲染"]))

    pk_side = opaque(crop16(tex("creature_packager_vertical_unpowered"), 16, 0))
    pk_iris = opaque(crop16(tex("creature_packager_iris_open"), 0, 0))
    pk_frame = opaque(crop16(tex("creature_packager_frame"), 0, 0))
    cards.append(block_card("生物打包机  Creature Packager", "creature_packager（方块）",
        {"正面": pk_iris, "背面": pk_frame, "左侧": pk_side, "右侧": pk_side,
         "顶部": pk_frame, "底部": pk_frame},
        [("正面(虹膜)", pk_iris), ("侧面", pk_side), ("顶/底", pk_frame)],
        BRASS_PAL + [(238, 240, 242, 255), (26, 30, 34, 255)],
        ["Create 打包机像素级同款换色位（diff 流体包裹模组定位）换黄铜",
         "虹膜口/指示灯/连接面回纹保留原版，全套 11 张已生成",
         "动力输入/输出语义与原版打包机一致"]))

    feed = tex("mixed_feed")
    cards.append(item_card("混合饲料  Mixed Feed", "mixed_feed（物品）", feed,
        [(243, 216, 118, 255), (222, 184, 74, 255), (176, 136, 46, 255),
         (198, 52, 40, 255), (126, 92, 52, 255), (92, 66, 40, 255)],
        ["一捆麻绳扎的谷物束：小麦穗主体 + 苹果丁 + 散落种子",
         "配方：小麦 + 苹果 + 任意种子标签物品；合并繁殖谷物/繁殖米"]))

    filt = tex("creature_filter")
    cards.append(item_card("生物过滤器  Creature Filter", "creature_filter（物品）", filt,
        BRASS_PAL + [(44, 56, 68, 255), (240, 210, 130, 255)],
        ["Create 过滤器特征：矩形边框 + 中间内容区",
         "黄铜边框 + 深蓝面板 + 金色爪印，与普通过滤器一眼区分"]))

    catcher = tex("creature_catcher")
    cards.append(item_card("生物收纳器  Creature Catcher", "creature_intake_fan（物品，拟改名）", catcher,
        BRASS_PAL + [(22, 26, 30, 255), (172, 183, 195, 255)],
        ["手持打包机：黄铜机身 + 虹膜封口圆口 + 胶带饰条 + 握柄",
         "右键生物直接打包成生物包裹入包",
         "造型语言参考便携式股票终端的手持设备感"]))

    W = 1080
    H = sum(c.height + 16 for c in cards) + 16
    sheet = Image.new("RGBA", (W, H), NAVY)
    y = 16
    for c in cards:
        sheet.alpha_composite(c, (0, y))
        y += c.height + 16
    out = PREVIEWS / "aircrate_asset_preview_v2.png"
    sheet.save(out)
    print("->", out, sheet.size)


if __name__ == "__main__":
    main()

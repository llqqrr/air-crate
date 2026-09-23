"""Assemble engineering-style preview sheets (dark navy cards) for all new aircrate assets.

Blocks get an isometric cube render (top/left/right faces); items get a flat zoom.
Chinese labels via Microsoft YaHei.
"""
import os
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from texlib import SRC, PREVIEWS

NAVY = (13, 27, 41, 255)
PANEL = (18, 36, 54, 255)
EDGE = (38, 68, 96, 255)
TEXT = (220, 230, 238, 255)
SUB = (140, 165, 185, 255)

FONT = ImageFont.truetype(r"C:\Windows\Fonts\msyh.ttc", 26)
FONT_SM = ImageFont.truetype(r"C:\Windows\Fonts\msyh.ttc", 18)

SCALE = 10


def project(point, origin, scale):
    x, y, z = point
    return (origin[0] + (x - z) * scale, origin[1] + (x + z) * scale * 0.46 - y * scale)


def blend(points, u, v):
    p0, p1, p2, p3 = points
    return ((1 - u) * (1 - v) * p0[0] + u * (1 - v) * p1[0] + u * v * p2[0] + (1 - u) * v * p3[0],
            (1 - u) * (1 - v) * p0[1] + u * (1 - v) * p1[1] + u * v * p2[1] + (1 - u) * v * p3[1])


def shade(c, f):
    return tuple(max(0, min(255, round(ch * f))) for ch in c[:3]) + (c[3],)


def textured_face(canvas, texture, vertices, origin, scale, brightness):
    texture = texture.convert("RGBA").resize((16, 16), Image.NEAREST)
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    proj = [project(v, origin, scale) for v in vertices]
    for v in range(16):
        for u in range(16):
            color = texture.getpixel((u, v))
            if color[3] < 16:
                continue
            poly = [blend(proj, u / 16, v / 16), blend(proj, (u + 1) / 16, v / 16),
                    blend(proj, (u + 1) / 16, (v + 1) / 16), blend(proj, u / 16, (v + 1) / 16)]
            draw.polygon(poly, fill=shade(color, brightness))
    canvas.alpha_composite(layer)


def iso_cube(size, top, left, right):
    """Return RGBA image of a unit cube. Faces: top, left(+x), right(+z)."""
    scale = SCALE
    w = int(32 * scale * 1.1)
    h = int(32 * scale * 0.55 + 16 * scale)
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    origin = (w // 2, int(16 * scale) + 10)
    # cube from (0,0,0) to (16,16,16), y up
    textured_face(im, top, [(0, 16, 0), (16, 16, 0), (16, 16, 16), (0, 16, 16)], origin, scale, 1.12)
    textured_face(im, left, [(16, 16, 0), (16, 16, 16), (16, 0, 16), (16, 0, 0)], origin, scale, 0.88)
    textured_face(im, right, [(16, 16, 16), (0, 16, 16), (0, 0, 16), (16, 0, 16)], origin, scale, 0.68)
    bbox = im.getbbox()
    return im.crop(bbox) if bbox else im


def palette_strip(draw, x, y, colors, sw=28):
    for i, c in enumerate(colors):
        draw.rectangle([x + i * sw, y, x + i * sw + sw - 3, y + sw - 3], fill=c, outline=(0, 0, 0, 255))


def card(title, sub, images, palette, notes):
    """images: list of (label, PIL image) shown left to right."""
    W = 1040
    img_h = max(im.height for _, im in images)
    H = 70 + max(img_h, 260) + 60 + 34 * len(notes) + 30
    im = Image.new("RGBA", (W, H), PANEL)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, W - 1, H - 1], outline=EDGE, width=2)
    d.text((24, 16), title, font=FONT, fill=TEXT)
    d.text((24 + d.textlength(title, font=FONT) + 18, 24), sub, font=FONT_SM, fill=SUB)
    x = 24
    iy = 64
    for label, img in images:
        d.text((x, iy - 4), label, font=FONT_SM, fill=SUB)
        im.alpha_composite(img, (x, iy + 24))
        x += img.width + 30
    py = iy + 24 + img_h + 16
    d.text((24, py), "色板", font=FONT_SM, fill=SUB)
    palette_strip(d, 24, py + 24, palette)
    ny = py + 56
    for note in notes:
        d.text((24, ny), "· " + note, font=FONT_SM, fill=SUB)
        ny += 30
    return im


def tex(name):
    return Image.open(SRC / f"{name}.png").convert("RGBA")


def z(im, s):
    return im.resize((im.width * s, im.height * s), Image.NEAREST)


def crop16(im, x, y):
    return im.crop((x, y, x + 16, y + 16))


BRASS = [(201, 163, 74, 255), (154, 116, 51, 255), (107, 79, 34, 255)]
ALU = [(172, 183, 195, 255), (122, 133, 146, 255), (58, 66, 74, 255)]
GLASS = [(196, 224, 233, 160), (240, 250, 252, 200)]


def main():
    cards = []

    crate_side = tex("aviation_crate_side")
    crate_top = Image.new("RGBA", (16, 16), (122, 133, 146, 255))
    dtop = ImageDraw.Draw(crate_top)
    dtop.rectangle([0, 0, 15, 15], outline=(58, 66, 74, 255))
    dtop.rectangle([1, 1, 14, 14], outline=(172, 183, 195, 255))
    cards.append(card("航空箱  Aviation Crate", "aviation_crate（方块）",
                      [("3D 预览", iso_cube(1, crate_top, crate_side, crate_side)),
                       ("正面材质 16×16", z(crate_side, 12))],
                      ALU + BRASS + GLASS,
                      ["航空铝浅灰蓝边框 + 黄铜角件，和保险库的深钢色区分开",
                       "大面积玻璃：多块拼接时走 Create 连接纹理，连成整面玻璃",
                       "扳手右键切玻璃窗/开放（借流体储罐语义），开窗变体待建模阶段做"]))

    pack_side = crop16(tex("creature_packager_vertical_unpowered"), 16, 0)
    pack_top = crop16(tex("creature_packager_frame"), 0, 0)
    for im in (pack_side, pack_top):
        backing = Image.new("RGBA", im.size, (222, 226, 230, 255))
        backing.alpha_composite(im)
        im.paste(backing, (0, 0))
    cards.append(card("生物打包机  Creature Packager", "creature_packager（方块）",
                      [("3D 预览", iso_cube(1, pack_top, pack_side, pack_side)),
                       ("侧面材质 16×16", z(pack_side, 12))],
                      BRASS + [(238, 240, 242, 255), (26, 30, 34, 255)],
                      ["Create 打包机同款换色位（与流体包裹模组换铜色相同的位置）换黄铜色",
                       "虹膜口/指示灯/连接面回纹等机械细节保留原版",
                       "全套 11 张材质已生成（水平/垂直/联动/虹膜开合等）"]))

    hatch = tex("interaction_hatch_face")
    cards.append(card("交互舱口  Interaction Hatch", "crate_interaction_hatch（方块）",
                      [("3D 预览", iso_cube(1, crate_top, hatch, hatch)),
                       ("正面材质 16×16", z(hatch, 12))],
                      ALU + BRASS + [(245, 245, 240, 255), (130, 96, 58, 255)],
                      ["边框与航空箱同族（铝框），中央圆形操作口",
                       "口内毛刷 + 奶滴图标：一眼看出'照顾动物'",
                       "贴合航空箱外墙安装的薄板件"]))

    reso = tex("echo_resonator_face")
    cards.append(card("回响激发器  Echo Resonator", "amethyst_sculk_resonator（方块，拟改名）",
                      [("3D 预览", iso_cube(1, crate_top, reso, reso)),
                       ("侧面材质 16×16", z(reso, 12))],
                      [(110, 110, 104, 255), (14, 38, 44, 255), (29, 180, 190, 255),
                       (168, 120, 210, 255), (80, 44, 120, 255)],
                      ["安山机壳底座（承接应力）+ 幽匿脉络环带 + 紫晶簇顶",
                       "紫晶簇可做发光态：蛙港在范围内时常亮（建模阶段做状态切换）",
                       "改名'回响激发器'：lang 调整，注册 id 不变更兼容存档"]))

    parcel = tex("creature_parcel_face")
    cards.append(card("生物包裹  Creature Parcel", "creature_parcel（物品/渲染）",
                      [("3D 预览", iso_cube(1, parcel, parcel, parcel)),
                       ("正面材质 16×16", z(parcel, 12))],
                      [(186, 148, 96, 255), (232, 222, 200, 255)] + GLASS,
                      ["纸板壳 + 封箱胶带 + 正面大玻璃窗（参考流体包裹的稀有流体包裹）",
                       "窗内由渲染器画生物本体：手上/背包/掉落物全都可见",
                       "替换现有'玻璃方块+苔藓垫'的临时渲染"]))

    feed = tex("mixed_feed")
    cards.append(card("混合饲料  Mixed Feed", "mixed_feed（物品，合并繁殖谷物/繁殖米）",
                      [("物品材质 16×16", z(feed, 14))],
                      [(243, 216, 118, 255), (222, 184, 74, 255), (198, 52, 40, 255),
                       (126, 92, 52, 255), (92, 66, 40, 255)],
                      ["一捆麻绳扎的谷物束：小麦穗为主体，夹苹果丁和种子",
                       "配方：小麦 + 苹果 + 任意种子标签物品"]))

    filt = tex("creature_filter")
    cards.append(card("生物过滤器  Creature Filter", "creature_filter（物品）",
                      [("物品材质 16×16", z(filt, 14))],
                      BRASS + [(38, 48, 58, 255), (233, 201, 120, 255)],
                      ["保留 Create 过滤器特征：矩形边框 + 中间内容区",
                       "黄铜边框 + 深蓝面板 + 金色爪印，与普通过滤器区分"]))

    catcher = tex("creature_catcher")
    cards.append(card("生物收纳器  Creature Catcher", "creature_intake_fan（物品，拟改名）",
                      [("物品材质 16×16", z(catcher, 14))],
                      BRASS + ALU[:1] + [(26, 30, 34, 255)],
                      ["手持打包机：黄铜机身 + 正面虹膜封口圆口 + 握柄",
                       "右键生物直接打包成生物包裹放入背包",
                       "造型语言参考便携式股票终端的手持设备感"]))

    W = 1040
    H = sum(c.height + 14 for c in cards) + 14
    sheet = Image.new("RGBA", (W, H), NAVY)
    y = 14
    for c in cards:
        sheet.alpha_composite(c, (0, y))
        y += c.height + 14
    out = PREVIEWS / "aircrate_asset_preview_v1.png"
    sheet.save(out)
    print("->", out)


if __name__ == "__main__":
    main()

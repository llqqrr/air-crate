# 气动捕获枪概念概览图（侧视示意，供 GPT 重做收纳器模型参考）
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

S = 6          # 像素放大倍数
GW, GH = 120, 48   # 枪的像素画布
W, H = 1340, 820
BG = (24, 30, 40, 255)
PANEL = (34, 42, 56, 255)
INK = (16, 10, 6, 255)
TXT = (232, 226, 210, 255)
SUB = (170, 176, 190, 255)
ACC = (224, 172, 84, 255)

FROG_HI = (208, 160, 80, 255); FROG = (192, 144, 80, 255)
FROG_DK = (160, 128, 64, 255); FROG_SH = (138, 106, 52, 255)
BRASS_HI = (176, 128, 74, 255); BRASS = (144, 96, 64, 255)
BRASS_DK = (112, 64, 48, 255); BRASS_SH = (64, 32, 16, 255)
COP_HI = (208, 112, 80, 255); COP = (176, 96, 64, 255)
COP_DK = (144, 80, 48, 255); BAND = (64, 48, 32, 255)
AND = (96, 74, 42, 255); AND_HI = (122, 95, 56, 255); AND_DK = (58, 42, 20, 255)
GRAY = (112, 112, 112, 255)
FACE = (232, 224, 200, 255); RED = (192, 48, 48, 255)
PINK = (200, 96, 96, 255)

gun = Image.new('RGBA', (GW, GH), (0, 0, 0, 0))
g = ImageDraw.Draw(gun)

def px(x, y, w, h, c):
    g.rectangle([x, y, x + w - 1, y + h - 1], fill=c)

# ---------- 铜背罐（枪身上方，延伸至尾部） ----------
px(8, 6, 58, 10, COP)
px(8, 6, 58, 2, COP_HI)          # 顶部高光
px(8, 13, 58, 3, COP_DK)         # 底部阴影
for bx in (22, 46):              # 深色箍带
    px(bx, 5, 3, 12, BAND)
    px(bx, 5, 3, 1, COP_HI)
px(8, 6, 3, 10, COP_DK)          # 尾端盖
px(6, 7, 2, 8, BAND)
# 尾部泄压阀
px(9, 2, 4, 4, COP_HI); px(10, 1, 2, 1, BAND); px(9, 5, 4, 1, INK)
# 罐体与枪身的连接管
px(58, 16, 5, 4, COP_DK); px(58, 16, 5, 1, COP_HI)

# ---------- 黄铜机壳枪身 ----------
px(32, 18, 60, 14, BRASS)
px(32, 18, 60, 2, BRASS_HI)
px(32, 30, 60, 2, BRASS_SH)
for rx in range(34, 92, 8):      # 铆钉
    gun.putpixel((rx, 19), BRASS_SH); gun.putpixel((rx, 30), BRASS_DK)
for sx in (52, 74):              # 面板接缝
    px(sx, 18, 1, 14, BRASS_DK); px(sx + 1, 18, 1, 14, BRASS_HI)

# ---------- 压力表（枪身侧面） ----------
cx, cy = 62, 25
g.ellipse([cx - 5, cy - 5, cx + 5, cy + 5], fill=BRASS_SH)
g.ellipse([cx - 4, cy - 4, cx + 4, cy + 4], fill=FACE)
g.pieslice([cx - 4, cy - 4, cx + 4, cy + 4], 300, 360, fill=RED)  # 红区
g.line([cx, cy, cx + 2, cy - 3], fill=RED, width=1)               # 指针
gun.putpixel((cx, cy), INK)

# ---------- 排气口（枪身下方靠后） ----------
px(42, 32, 5, 3, BRASS_SH); px(43, 35, 3, 1, INK)
gun.putpixel((44, 37), (200, 200, 205, 160)); gun.putpixel((46, 39), (200, 200, 205, 110))

# ---------- 握把 + 扳机 ----------
g.polygon([(30, 32), (36, 32), (30, 46), (22, 46)], fill=AND)
g.polygon([(30, 32), (33, 32), (27, 46), (23, 46)], fill=AND_HI)
px(22, 44, 8, 2, AND_DK)         # 握把底盖
px(36, 33, 4, 5, BRASS_HI)       # 扳机
px(36, 33, 1, 5, BRASS_SH)
gun.putpixel((37, 35), INK)

# ---------- 蛙港头（枪口） ----------
hx = 94
px(hx + 1, 12, 20, 22, FROG)                 # 头主体
px(hx, 15, 22, 16, FROG)
px(hx + 1, 12, 20, 3, FROG_HI)               # 头顶高光
px(hx, 15, 3, 3, FROG_HI)
px(hx, 31, 22, 3, FROG_SH)                   # 下颌阴影
px(hx, 15, 2, 19, FROG_DK)                   # 后与枪身衔接
# 蛙眼（圆顶小鼓包，近侧完整 + 远侧露一点）
px(hx + 7, 9, 5, 4, FROG_HI); px(hx + 8, 8, 3, 1, FROG_HI)
px(hx + 7, 12, 5, 1, INK)
px(hx + 9, 10, 2, 2, INK); gun.putpixel((hx + 9, 10), FACE)  # 眼珠高光
px(hx + 2, 10, 3, 3, FROG_DK); gun.putpixel((hx + 3, 11), INK)
# 嘴部：待机微张
px(hx + 8, 26, 14, 2, INK)
px(hx + 12, 27, 8, 1, PINK)                  # 舌尖一点
px(hx + 8, 25, 14, 1, FROG_DK)
# 颈部黄铜环
px(hx - 3, 14, 3, 18, BRASS_DK)
for ry in range(15, 31, 5):
    gun.putpixel((hx - 2, ry), BRASS_HI)

# ---------- 组装画布 ----------
img = Image.new('RGBA', (W, H), BG)
d = ImageDraw.Draw(img)
font = 'C:/Windows/Fonts/msyh.ttc'
f_t = ImageFont.truetype(font, 40)
f_h = ImageFont.truetype(font, 24)
f_s = ImageFont.truetype(font, 19)

d.text((40, 30), '生物收纳器 · 气动捕获枪 —— 概念概览（侧视）', font=f_t, fill=TXT)
d.text((42, 84), '蛙港头 × 铜背罐 × 黄铜/安山机壳 ｜ 比例参照：Create 土豆加农炮 ｜ 手工器械感，不做导轨/瞄具', font=f_s, fill=SUB)

GX, GY = 320, 300
scaled = gun.resize((GW * S, GH * S), Image.NEAREST)
img.paste(scaled, (GX, GY), scaled)

def callout(tx, ty, px_, py_, lines, anchor='left'):
    d.line([tx, ty, px_, py_], fill=ACC, width=2)
    gun.putpixel if False else None
    d.ellipse([px_ - 4, py_ - 4, px_ + 4, py_ + 4], outline=ACC, width=2)
    for i, (t, c) in enumerate(lines):
        d.text((tx + (10 if anchor == 'left' else -10), ty + (12 if ty < py_ else -30) + i * 26 - (26 * (len(lines) - 1) if ty > py_ else 0)),
               t, font=f_h if i == 0 else f_s, fill=c, anchor='lm' if anchor == 'left' else 'rm')

def G(u, v):  # 枪内像素坐标 -> 画布坐标
    return GX + u * S + S // 2, GY + v * S + S // 2

callout(60, 150, *G(11, 2), [('尾部泄压阀', ACC), ('气罐末端的小凸起', SUB)])
callout(330, 130, *G(36, 5), [('铜背罐气源', ACC), ('Create 压缩背罐，枪身延伸至尾部；深色箍带', SUB)])
callout(790, 110, *G(103, 8), [('蛙眼保留', ACC), ('生物感来源，勿删', SUB)])
callout(950, 200, *G(114, 27), [('蛙港头 = 捕获口', ACC), ('舌头从此弹射；嘴部待机微张，发射大张', SUB)])
callout(1180, 560, *G(64, 25), [('压力表', ACC), ('真功能：指针显示剩余气压，低压进红区', SUB)], anchor='right')
callout(760, 660, *G(45, 34), [('排气口', ACC), ('发射后喷一股蒸汽粒子', SUB)])
callout(280, 680, *G(28, 44), [('握把 + 黄铜扳机', ACC), ('安山机壳纹理，向后下倾斜，勿垂直', SUB)])
callout(90, 470, *G(36, 31), [('黄铜机壳枪身', ACC), ('铆钉 + 面板接缝，黄铜色阶', SUB)])

# 源材质参考条
sy = 740
d.text((42, sy - 6), '源材质：', font=f_s, fill=SUB)
import os, glob
refdir = glob.glob(os.path.expanduser('~/AppData/Local/Temp/gunref'))[0]
refs = [('creature_frogport_port.png', '蛙港(本模组)'), ('copper_backtank.png', '铜背罐'),
        ('brass_casing.png', '黄铜机壳'), ('andesite_casing.png', '安山机壳')]
x = 130
for fn, name in refs:
    p = os.path.join(refdir, fn)
    if os.path.exists(p):
        t = Image.open(p).convert('RGBA').resize((64, 64), Image.NEAREST)
        img.paste(t, (x, sy - 32), t)
        d.rectangle([x, sy - 32, x + 64, sy + 32], outline=SUB)
        d.text((x + 32, sy + 42), name, font=f_s, fill=SUB, anchor='mm')
        x += 190

out = Path(__file__).resolve().parents[2] / 'art' / 'previews' / 'catcher_gun_concept.png'
out.parent.mkdir(parents=True, exist_ok=True)
img.convert('RGB').save(out)
print('saved', out)

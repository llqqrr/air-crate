"""Assemble existing reference textures; does not generate new concept art."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import zipfile, io, json, colorsys

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[2]
FLUID = Path.home() / 'AppData/Local/Temp/fluidtex/assets/fluidlogistics/textures'
FONT = 'C:/Windows/Fonts/msyh.ttc'
def font(s): return ImageFont.truetype(FONT, s)
def tile(board, im, x, y, label, note=''):
    d = ImageDraw.Draw(board)
    d.rounded_rectangle((x,y,x+355,y+335),12,fill='#112B38',outline='#294956')
    d.text((x+16,y+12),label,font=font(19),fill='#DFE8EB')
    d.text((x+16,y+43),f'{im.width} × {im.height}  ·  {note}',font=font(13),fill='#99B1BA')
    k = min(270//im.width, 240//im.height)
    v=im.convert('RGBA').resize((im.width*k, im.height*k), Image.Resampling.NEAREST)
    px=x+(355-v.width)//2; py=y+78+(240-v.height)//2
    for a in range(px,px+v.width,12):
        for b in range(py,py+v.height,12):
            d.rectangle((a,b,min(a+11,px+v.width-1),min(b+11,py+v.height-1)),fill=('#243945' if ((a-px)//12+(b-py)//12)%2 else '#1A303D'))
    board.paste(v,(px,py),v)

with zipfile.ZipFile(ROOT/'libs/create-1.21.1-6.0.10.jar') as z:
    def read(n): return Image.open(io.BytesIO(z.read('assets/create/textures/'+n))).convert('RGBA')
    entries=[('黄铜机壳','block/brass_casing.png'),('安山机壳','block/andesite_casing.png'),('保险库侧面','block/vault/vault_side_small.png'),('流体储罐玻璃','block/fluid_tank_window.png'),('过滤器','item/filter.png'),('股票终端纹理参考','block/stock_ticker.png'),('纸板包裹 UV','item/package/cardboard.png')]
    refs=Image.new('RGB',(1536,850),'#081D28'); d=ImageDraw.Draw(refs)
    d.text((32,20),'AIR CRATE / 原始材质参考索引',font=font(30),fill='#E8D4A5')
    d.text((32,66),'Create 6.0.10 本地 JAR + 既有 Fluid Logistics 提取材质；仅供设计对照',font=font(17),fill='#9DB3BC')
    manifest=[]
    for i,(label,path) in enumerate(entries):
        im=read(path); tile(refs,im,32+(i%4)*372,112+(i//4)*356,label,'Create')
        manifest.append({'label':label,'source':'libs/create-1.21.1-6.0.10.jar!assets/create/textures/'+path,'size':im.size})
    im=Image.open(FLUID/'item/rare_fluid_package.png').convert('RGBA')
    tile(refs,im,32+3*372,112+356,'流体包裹 UV','Fluid Logistics')
    manifest.append({'label':'流体包裹','source':str(FLUID/'item/rare_fluid_package.png'),'size':im.size,'version':'原提取目录未记录版本'})
    refs.save(OUT/'reference-materials.png')
    sheet=Image.new('RGB',(1536,1260),'#081D28'); d=ImageDraw.Draw(sheet)
    d.text((32,20),'生物打包机 / 换色位置核对',font=font(30),fill='#E8D4A5')
    d.text((32,66),'左：Create 原图　中：Fluid Logistics 原图　右：铜色差异掩膜（浅黄）　｜ 不代表新设计成品',font=font(17),fill='#9DB3BC')
    stats=[]
    for i,name in enumerate(['packager_frame','packager_horizontal_unpowered','packager_iris_closed']):
        a=read('block/'+name+'.png'); b=Image.open(FLUID/'block/fluid_packager'/f'{name}.png').convert('RGBA')
        assert a.size==b.size
        mask=Image.new('RGBA',a.size,(0,0,0,0)); count=0
        for y in range(a.height):
            for x in range(a.width):
                p=b.getpixel((x,y)); h,s,v=colorsys.rgb_to_hsv(*(c/255 for c in p[:3]))
                if a.getpixel((x,y))!=p and p[3] and (h*360>=350 or h*360<=40) and s>.12:
                    mask.putpixel((x,y),(255,235,140,255)); count+=1
        stats.append({'texture':name,'size':a.size,'copper_difference_pixels':count})
        for j,(im,label) in enumerate([(a,'Create'),(b,'Fluid Logistics'),(mask,f'换色掩膜 · {count} px')]):
            tile(sheet,im,32+j*496,112+i*370,label,name.replace('packager_',''))
    sheet.save(OUT/'reference-packager-mask.png')
    (OUT/'reference-manifest.json').write_text(json.dumps({'sources':manifest,'packager_diff':stats},ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(stats,ensure_ascii=False))

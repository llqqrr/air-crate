"""Reproducible integration assets; leaves src/main/resources untouched."""
from pathlib import Path
import copy
import hashlib
import json
import shutil
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from model_tools import *

ROOT=Path(__file__).resolve().parent
OLD=ROOT.parent/'engineering-script-v2'
REF=ROOT/'references'
ASSETS=ROOT/'resources/assets/aircrate'
TEX=ASSETS/'textures/block/integration_v4'
MOD=ASSETS/'models/block/integration_v4'
for p in (REF,TEX,MOD,ROOT/'blockbench',ROOT/'preview',ROOT/'checks'):p.mkdir(parents=True,exist_ok=True)
LEFT,RIGHT,TOP,BOTTOM=1,2,4,8
NORTH_ROTATION={'north':0,'south':180,'west':90,'east':270}

def source(ns,path):
    p=REF/ns/(path+'.png')
    if not p.exists():
        original=OLD/'references'/ns/'textures'/(path+'.png')
        if ns=='createdeco':original=OLD/'references'/ns/(Path(path).name+'.png')
        p.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(original,p)
    return p

def texture_path(ref):
    ns,path=ref.split(':')
    if ns=='aircrate':return ASSETS/'textures'/(path+'.png')
    return source(ns,path)

def save_texture(name, image):
    image.save(TEX/(name+'.png'))
    return 'aircrate:block/integration_v4/'+name

def readtex(ns,path):return Image.open(source(ns,path)).convert('RGBA')

def edge_mask(u,v,w,h):
    return (LEFT if u==0 else 0)|(RIGHT if u==w-1 else 0)|(TOP if v==0 else 0)|(BOTTOM if v==h-1 else 0)

def prepare_textures():
    side=readtex('create','block/vault/vault_side_small')
    front=readtex('create','block/vault/vault_front_small')
    large=readtex('create','block/vault/vault_front_large').crop((16,0,64,48))
    closed=readtex('create','block/vault/vault_side_large').crop((32,16,48,32))
    # Copy just unframed steel. Make opposite boundaries pixel-identical.
    a=np.array(closed);a[-1]=a[0];a[:,-1]=a[:,0];closed=Image.fromarray(a)
    raw=readtex('createdeco','industrial_iron_bars')
    # Keep upstream post shading; extend the posts through the tile boundary.
    bars=Image.new('RGBA',(16,16))
    for y in range(16):bars.paste(raw.crop((0,8,16,9)),(0,y))
    for y in (7,8):bars.paste(raw.crop((0,3+y-7,16,4+y-7)),(0,y))
    # Put the repeat boundary in a gap between posts, not through a post.
    arr=np.array(bars)
    if not np.array_equal(arr[:,0],arr[:,-1]):arr[:,-1]=arr[:,0]
    bars=Image.fromarray(arr)
    save_texture('glass_inner',Image.new('RGBA',(16,16)))
    save_texture('bars_inner',bars);save_texture('closed_inner',closed)
    for mask in range(16):
        frame=Image.new('RGBA',(16,16))
        if mask&LEFT:frame.paste(front.crop((0,0,2,16)),(0,0))
        if mask&RIGHT:frame.paste(front.crop((14,0,16,16)),(14,0))
        if mask&TOP:frame.paste(side.crop((0,0,16,2)),(0,0))
        if mask&BOTTOM:frame.paste(side.crop((0,14,16,16)),(0,14))
        save_texture(f'frame_{mask:02}',frame)
        for kind,inner in [('glass',Image.new('RGBA',(16,16))),('bars',bars),('closed',closed)]:
            im=inner.copy();im.alpha_composite(frame);save_texture(f'{kind}_{mask:02}',im)
        # Nine-slice for normal widths; retain both opposite borders for 1-block spans.
        def coord(i, low, high):
            if low and high:return i if i<8 else i+32
            return i+(0 if low else 32 if high else 16)
        panel=Image.new('RGBA',(16,16))
        for y in range(16):
            for x in range(16):panel.putpixel((x,y),large.getpixel((coord(x,mask&LEFT,mask&RIGHT),coord(y,mask&TOP,mask&BOTTOM))))
        if mask==15:panel=front.copy()
        save_texture(f'panel_{mask:02}',panel)
        hole=panel.copy();hole.paste((0,0,0,0),(3,3,13,13));save_texture(f'vent_panel_{mask:02}',hole)
    # Casing side with a complete outer frame and 1:1 texels, 12 pixels high.
    casing=readtex('create','block/andesite_casing');body=Image.new('RGBA',(16,16))
    body.paste(casing.crop((0,0,16,10)),(0,0));body.paste(casing.crop((0,14,16,16)),(0,10))
    save_texture('resonator_casing_side',body)
    bearing=body.copy();bearing.paste((0,0,0,0),(6,2,10,6))
    # y=8 mechanical axis -> v=4 on a 12-high body. Frame around the 4x4 opening.
    metal=readtex('create','block/gearbox')
    for y in range(1,7):
        for x in range(5,11):
            if y in (1,6) or x in (5,10):bearing.putpixel((x,y),metal.getpixel((x,y+4)))
    save_texture('resonator_casing_bearing',bearing)
    sc=readtex('minecraft','block/sculk_sensor_side');active=np.array(sc)
    sel=(active[:,:,1]>active[:,:,0]*1.4)&(active[:,:,2]>active[:,:,0]*1.3)&(active[:,:,1]>45)
    active[sel,:3]=np.minimum(active[sel,:3].astype(int)*1.35+8,255).astype('uint8')
    save_texture('sculk_active',Image.fromarray(active))

def wall(kind, mask, direction='north'):
    ref=f'aircrate:block/integration_v4/{kind}_{mask:02}'
    # The backing copy has opposite UV handedness, with a .04 model-unit inset.
    a=[0,0,0];b=[16,16,16]
    axis={'north':2,'south':2,'east':0,'west':0,'up':1,'down':1}[direction]
    negative=direction in ('north','west','down')
    if negative:b[axis]=.04
    else:a[axis]=15.96
    back={'north':'south','south':'north','east':'west','west':'east','up':'down','down':'up'}[direction]
    back_uv=[16,0,0,16] if axis!=1 else [0,16,16,0]
    e=cube(kind,a,b,{direction:'#wall',back:'#wall'},{back:back_uv})
    return model([e],{'wall':ref,'particle':'create:block/vault/vault_front_small'})

def fan_parts():
    src=OLD/'references/create/models/block/encased_fan/item.json'
    dest=REF/'encased_fan_item.json';shutil.copyfile(src,dest)
    d=json.loads(src.read_text(encoding='utf-8'))
    for e in d['elements']:
        for key in ('from','to'):e[key]=[round(v*.625+o,6) for v,o in zip(e[key],(3,3,-.5))]
        if 'rotation' in e:e['rotation']['origin']=[v*.625+o for v,o in zip(e['rotation']['origin'],(3,3,-.5))]
    # Keep all eight native elements and original mirrored/rotated face UVs.
    d.pop('groups',None);d['render_type']='minecraft:cutout'
    static=copy.deepcopy(d);static['elements']=[e for e in d['elements'] if e['name'] not in ('Shaft','Fan')]
    rotor=copy.deepcopy(d);rotor['elements']=[e for e in d['elements'] if e['name'] in ('Shaft','Fan')]
    return d,static,rotor

def resonator(active=False, include_rotor=False):
    tex={'casing':'aircrate:block/integration_v4/resonator_casing_side',
         'bearing':'aircrate:block/integration_v4/resonator_casing_bearing',
         'cap':'create:block/andesite_casing','ring':'aircrate:block/integration_v4/sculk_active' if active else 'minecraft:block/sculk_sensor_side',
         'top':'minecraft:block/calibrated_sculk_sensor_top','crystal':'minecraft:block/calibrated_sculk_sensor_amethyst',
         'brass':'create:block/brass_casing','axis':'create:block/axis','axis_top':'create:block/axis_top','particle':'create:block/andesite_casing'}
    faces={d:('#bearing' if d in ('north','south') else '#cap' if d in ('up','down') else '#casing') for d in DIRS}
    base=cube('Complete casing frame / shaft centre Y=8',(0,0,0),(16,12,16),faces,{d:[0,0,16,12] for d in DIRS if d not in ('up','down')})
    ring=cube('Sculk ring',(0,12,0),(16,16,16),{d:'#top' if d=='up' else '#ring' for d in DIRS if d!='down'}, {d:[0,8,16,12] for d in DIRS if d!='up'})
    src=OLD/'references/minecraft/models/block/calibrated_sculk_sensor.json'
    shutil.copyfile(src,REF/'calibrated_sculk_sensor.json')
    crystals=copy.deepcopy(json.loads(src.read_text())['elements'][5:])
    for i,e in enumerate(crystals):
        e['name']='Native crystal plane '+str(i)
        for key in ('from','to'):e[key][1]+=8
        e['rotation']['origin'][1]+=8
        for f in e['faces'].values():f['texture']='#crystal'
    elements=[base,ring]+crystals
    for x,z in ((1,1),(12,1),(1,12),(12,12)):
        elements.append(cube('Brass crystal clamp',(x,15,z),(x+3,17,z+3),'#brass',{d:[1,1,4,3] for d in DIRS}))
    if include_rotor:elements+=resonator_rotor()['elements']
    return model(elements,tex)

def resonator_rotor():
    e=cube('Kinetic through shaft',(6,6,-.5),(10,10,16.5),{d:'#axis_top' if d in ('north','south') else '#axis' for d in DIRS},{d:[6,6,10,10] if d in ('north','south') else [6,0,10,16] for d in DIRS})
    return model([e],{'axis':'create:block/axis','axis_top':'create:block/axis_top'})

def parcel():
    """Native outer UVs plus a true window; normal boxes for the inward walls."""
    data=json.loads((OLD/'references/create/models/item/package/cardboard_12x12.json').read_text())
    data.pop('groups',None);data['render_type']='minecraft:cutout'
    data['parent']='minecraft:block/block'
    win=Image.open(OLD/'textures/parcel_window_16.png').convert('RGBA')
    # Minecraft cutout cannot represent the old 95-alpha reflection; keep a clear hole.
    a=np.array(win);a[a[:,:,3]<128]=0;win=Image.fromarray(a)
    data['textures']['window']=save_texture('parcel_window',win)
    shell=data['elements'][0];shell['faces'].pop('north')
    data['elements'].append(cube('Observation window',(2,0,2),(14,12,2.04),{'north':'#window','south':'#window'}, {'north':[2,2,14,14],'south':[14,2,2,14]}))
    # Interior surfaces face inward without reversing element from/to.
    walls=[('east',(2,0,2),(2.04,12,14)),('west',(13.96,0,2),(14,12,14)),('north',(2,0,13.96),(14,12,14)),('down',(2,11.96,2),(14,12,14)),('up',(2,0,2),(14,.04,14))]
    for face,a,b in walls:data['elements'].append(cube('Cardboard interior '+face,a,b,{face:'#0'},{face:[9,12.25,12,15.25]}))
    return data

def generate_models():
    for kind in ('glass','bars','closed','panel'):
        for mask in range(16):
            for direction in DIRS:dump(MOD/'crate'/f'{kind}_{mask:02}_{direction}.json',wall(kind,mask,direction))
    fan,static,rotor=fan_parts()
    for direction,angle in NORTH_ROTATION.items():
        for name,data in [('fan',fan),('fan_static',static),('fan_rotor',rotor)]:dump(MOD/'crate'/f'{name}_{direction}.json',rotate_y(data,angle))
        for mask in (5,7,13,15):
            data=wall('vent_panel',mask)
            data['textures'].update(fan['textures']);data['elements']+=copy.deepcopy(fan['elements'])
            dump(MOD/'crate'/f'vent_{mask:02}_{direction}.json',rotate_y(data,angle))
            # Runtime animation: holed panel + fixed frame; rotor is a separate partial.
            data=wall('vent_panel',mask);data['textures'].update(static['textures']);data['elements']+=copy.deepcopy(static['elements'])
            dump(MOD/'crate'/f'vent_static_{mask:02}_{direction}.json',rotate_y(data,angle))
    for name,data in [('resonator_idle',resonator()),('resonator_active',resonator(True)),('resonator_rotor',resonator_rotor()),('resonator_item',resonator(False,True)),('fan_north',fan),('parcel',parcel())]:
        dump(MOD/(name+'.json'),data);dump(ROOT/'blockbench'/(name+'.bbmodel'),bbmodel(data,name,texture_path))

def face_grid(direction,x,y,z,w,h,l):
    # Face-local coordinates follow the Minecraft FaceInfo UV basis, v down.
    return {
        'north':(w-1-x,h-1-y,w,h),'south':(x,h-1-y,w,h),
        'west':(z,h-1-y,l,h),'east':(l-1-z,h-1-y,l,h),
        'up':(x,z,w,l),'down':(x,l-1-z,w,l),
    }[direction]

def assemble(w,h,l,mode='glass'):
    out=model([],{});serial=0
    for x in range(w):
        for y in range(h):
            for z in range(l):
                for direction,show in [('north',z==0),('south',z==l-1),('west',x==0),('east',x==w-1),('up',y==h-1),('down',y==0)]:
                    if not show:continue
                    u,v,fw,fh=face_grid(direction,x,y,z,w,h,l);mask=edge_mask(u,v,fw,fh)
                    kind='vent' if direction=='north' and u==v==0 else 'panel' if direction in ('north','south') else mode
                    p=MOD/'crate'/f'{kind}_{mask:02}_{direction}.json';d=json.loads(p.read_text(encoding='utf-8'))
                    for e in d['elements']:
                        e=copy.deepcopy(e)
                        for key in ('from','to'):e[key]=[a+b*16 for a,b in zip(e[key],(x,y,z))]
                        if 'rotation' in e:e['rotation']['origin']=[a+b*16 for a,b in zip(e['rotation']['origin'],(x,y,z))]
                        for f in e['faces'].values():
                            ref=resolve(f['texture'],d['textures']);key='t'+str(serial);serial+=1;out['textures'][key]=ref;f['texture']='#'+key
                        out['elements'].append(e)
    return out

def preview():
    import preview_renderer as engine
    font=lambda n:ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',n)
    sheet=Image.new('RGB',(1800,1320),(233,235,231));dr=ImageDraw.Draw(sheet)
    dr.text((45,22),'航空箱 · 接入资源修订 V4',font=font(35),fill='#26383c')
    dr.text((45,75),'实际交付 JSON 的脚本投影 · 原保险库材质 / 透明窗口 / 单一立体风扇',font=font(21),fill='#526065')
    for i,mode in enumerate(('closed','glass','bars')):
        data=assemble(3,3,5,mode);name='crate_3x3x5_'+mode
        # A whole assembled multiblock is review-only; runtime uses the small parts.
        dump(ROOT/'blockbench'/(name+'.bbmodel'),render_mesh(data,name,texture_path))
        path=ROOT/'preview'/(name+'.mesh.json');dump(path,render_mesh(data,name,texture_path))
        im=engine.render(path,(565,470),camera=(1,.7,-1.35));im.save(ROOT/'preview'/(name+'.png'))
        sheet.paste(im,(40+i*585,160),im);dr.text((55+i*585,125),{'closed':'封闭 · 外围框保留','glass':'玻璃 · 内部拼缝去框','bars':'栅栏 · 贯通铁栏杆'}[mode],font=font(23),fill='#26383c')
    data=assemble(3,3,1,'glass');path=ROOT/'preview/end.mesh.json';dump(path,render_mesh(data,'end',texture_path))
    im=engine.render(path,(470,470),camera=(0,0,-1),ortho=True);sheet.paste(im,(40,715),im)
    dr.text((50,650),'3×3 端面：左上角仅一组内凹风扇',font=font(23),fill='#26383c')
    for i,name in enumerate(('fan_north','resonator_item')):
        data=json.loads((MOD/(name+'.json')).read_text(encoding='utf-8'));p=ROOT/'preview'/(name+'.mesh.json');dump(p,render_mesh(data,name,texture_path))
        im=engine.render(p,(470,450),camera=(1,.6,-1.4));im.save(ROOT/'preview'/(name+'.png'));sheet.paste(im,(625+i*570,735),im)
    dr.text((635,650),'保留原框架、格栅、背板、轴与扇叶',font=font(23),fill='#26383c')
    dr.text((1200,650),'回响激发器：完整边框 + 对穿轴孔',font=font(23),fill='#26383c')
    dr.text((55,1230),'窗口 alpha=0；铁栏杆原像素平铺；标准零件 JSON 与 Java 格式 Blockbench 工程随包附带。',font=font(22),fill='#526065')
    dr.text((55,1265),'这是模型资源预览，尚未替换游戏资源；结构选面、动力与动画由接入代码驱动。',font=font(21),fill='#526065')
    sheet.save(ROOT/'00_review.png')
    # Tiling proof, normal and one-block-wide edge cases.
    board=Image.new('RGB',(1500,1250),(220,224,221));dr=ImageDraw.Draw(board)
    dr.text((30,15),'连接材质验证 · 橙色细线仅表示方块边界',font=font(28),fill='#26383c')
    for row,(kind,w,h) in enumerate([('glass',5,3),('bars',5,3),('closed',5,3),('panel',5,3)]):
        for col,(ww,hh) in enumerate([(w,h),(1,3),(3,1)]):
            im=Image.new('RGBA',(ww*16,hh*16))
            for u in range(ww):
                for v in range(hh):im.alpha_composite(Image.open(TEX/f'{kind}_{edge_mask(u,v,ww,hh):02}.png'),(u*16,v*16))
            im=im.resize((ww*16*5,hh*16*5),Image.Resampling.NEAREST)
            x=35+col*470;y=80+row*290;board.paste(im,(x,y),im);dr.text((x,y-26),f'{kind} {ww}×{hh}',font=font(17),fill='#26383c')
            for u in range(1,ww):dr.line((x+u*80,y,x+u*80,y+hh*80),fill='#9d7548',width=1)
            for v in range(1,hh):dr.line((x,y+v*80,x+ww*80,y+v*80),fill='#9d7548',width=1)
    board.save(ROOT/'01_tiling.png')
    data=parcel();path=ROOT/'preview/parcel.mesh.json';dump(path,render_mesh(data,'parcel',texture_path))
    board=Image.new('RGB',(1320,640),(233,235,231));dr=ImageDraw.Draw(board)
    for i,(title,cam) in enumerate([('前窗与内壁',(-1,.65,-1.5)),('原版底面 UV · 270°',(0,-1,0)),('原版顶面 UV · 180°',(0,1,0))]):
        im=engine.render(path,(410,500),camera=cam,ortho=i>0)
        board.paste(im,(15+i*440,80),im);dr.text((25+i*440,30),title,font=font(23),fill='#26383c')
    dr.text((30,585),'包裹补充模型 · 保留原版五面 UV 和已认可的大窗造型',font=font(23),fill='#526065')
    board.save(ROOT/'02_parcel_uv.png')

def main():
    prepare_textures();generate_models();preview()
    if not (REF/'CreateDeco-LICENSE').exists():shutil.copyfile(OLD/'references/createdeco/LICENSE',REF/'CreateDeco-LICENSE')
    files=[]
    for p in sorted((ROOT/'resources').rglob('*')):
        if p.is_file():files.append({'path':p.relative_to(ROOT).as_posix(),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
    dump(ROOT/'ASSET_MANIFEST.json',{'namespace':'aircrate','runtime_prefix':'block/integration_v4','source':'Create 6.0.10 + original engineering-script-v2 references; CreateDeco commit 996780bed4549d7b1d0dec65f06d5129e69330ba','mask_bits':{'left':1,'right':2,'top':4,'bottom':8},'files':files})
    print(f'Wrote {len(files)} integration resources; runtime source tree untouched.')

if __name__=='__main__':main()

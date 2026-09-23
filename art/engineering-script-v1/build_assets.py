"""Deterministic 16px textures -> editable Blockbench meshes -> software renders.
No generative image service. Run with Python, Pillow and NumPy.
"""
from pathlib import Path
import base64, colorsys, io, json, math, uuid, zipfile
import numpy as np
from PIL import Image, ImageDraw, ImageFont

OUT=Path(__file__).resolve().parent
ROOT=OUT.parents[2]
TEX=OUT/'textures'; MOD=OUT/'models'; REN=OUT/'renders'; SHEET=OUT/'sheets'
for p in [TEX,MOD,REN,SHEET]: p.mkdir(exist_ok=True)
C={'ink':'#293941','a0':'#4E6372','a1':'#7D94A3','a2':'#AEBEC8','a3':'#D8E1E4',
   'b0':'#724731','b1':'#9E6947','b2':'#CEA05A','b3':'#F7CB6C','b4':'#FFEB8C',
   'paper0':'#806449','paper1':'#AE8658','paper2':'#CDA775','paper3':'#E0BD8C',
   'tape':'#EEE0BD','red0':'#743B37','red':'#A84F43','red1':'#D27B63',
   'ivory':'#DED9C6','white':'#F3EDDB','sculk':'#132F38','teal0':'#245257',
   'teal':'#42817F','cyan':'#83CEC5','purple0':'#443359','purple1':'#705289',
   'purple2':'#A27CBE','purple3':'#D5B4E5','rope0':'#644B31','rope1':'#AD8A52',
   'wheat0':'#977034','wheat1':'#CEA54F','wheat2':'#E7C66D','wheat3':'#F5DEA0'}
def color(v):
    if isinstance(v,tuple):return v
    v=C.get(v,v).lstrip('#');return tuple(int(v[i:i+2],16) for i in (0,2,4))+(255,)
def new(fill=None):return Image.new('RGBA',(16,16),color(fill) if fill else (0,0,0,0))
def rect(im,box,c):ImageDraw.Draw(im).rectangle(box,fill=color(c))
def line(im,pts,c,w=1):ImageDraw.Draw(im).line(pts,fill=color(c),width=w)
def px(im,x,y,c):im.putpixel((x,y),color(c))
def save(im,name):im.save(TEX/(name+'.png'));return name
def load(name):return Image.open(TEX/(name+'.png')).convert('RGBA')
def panel(base='a2',rim=True):
    im=new(base)
    if rim:
        rect(im,(0,0,15,15),'ink');rect(im,(1,1,14,14),'a1');rect(im,(1,1,14,1),'a3');rect(im,(1,1,1,14),'a2');rect(im,(2,2,13,13),base)
        line(im,[(2,13),(13,13),(13,2)],'a0')
        for x,y in [(3,3),(12,3),(3,12),(12,12)]:
            px(im,x,y,'a0');px(im,x,y-1,'a3')
    for x,y in [(5,4),(6,4),(10,8),(11,8),(4,10)]:px(im,x,y,'a1')
    return im
def brass_rivet(im,x,y):
    rect(im,(x,y,x+1,y+1),'b1');px(im,x,y,'b4');px(im,x+1,y,'b3')
def alu_frame(glass=True):
    im=new();rect(im,(0,0,15,15),'ink');rect(im,(1,1,14,14),'a2');rect(im,(2,2,13,13),(0,0,0,0))
    line(im,[(1,14),(14,14),(14,1)],'a0');line(im,[(1,13),(1,1),(13,1)],'a3')
    for x,y in [(0,0),(14,0),(0,14),(14,14)]:brass_rivet(im,x,y)
    if glass:
        rect(im,(2,2,13,13),(141,191,200,18))
        line(im,[(3,5),(5,3)],(200,230,232,135));line(im,[(3,8),(8,3)],(200,230,232,65))
        line(im,[(11,12),(12,11)],(200,230,232,95))
    return im
def iris():
    # Stepped octagonal aperture, overlapping spiral shutter seams.
    im=new();d=ImageDraw.Draw(im)
    octa=[(5,1),(10,1),(14,5),(14,10),(10,14),(5,14),(1,10),(1,5)]
    d.polygon(octa,fill=color('b0'));d.polygon([(5,2),(10,2),(13,5),(13,10),(10,13),(5,13),(2,10),(2,5)],fill=color('b3'))
    d.polygon([(5,3),(10,3),(12,5),(12,10),(10,12),(5,12),(3,10),(3,5)],fill='#343940')
    d.polygon([(5,4),(10,4),(11,5),(7,7),(4,7),(4,5)],fill='#62666B')
    d.polygon([(11,5),(12,6),(12,10),(10,11),(9,8),(9,6)],fill='#4E535A')
    d.polygon([(10,11),(9,12),(5,12),(4,10),(7,9),(9,9)],fill='#41454B')
    d.polygon([(4,10),(3,9),(3,6),(5,6),(7,7),(6,9)],fill='#54585E')
    rect(im,(7,7,8,8),'ink');px(im,5,2,'b4');return im

def make_textures():
    for n,c in C.items():save(new(c),'solid_'+n)
    save(panel(),'alu_panel');save(panel('a1'),'alu_bottom')
    top=panel();line(top,[(5,5),(10,5)],'a3');line(top,[(5,6),(10,6)],'a1');save(top,'crate_top')
    save(alu_frame(),'crate_window');save(alu_frame(False),'crate_open')
    # Four-edge connectivity mask: N=1 E=2 S=4 W=8. Suppress shared borders.
    for mask in range(16):
        im=alu_frame()
        if mask&1:rect(im,(0,0,15,1),(141,191,200,18))
        if mask&2:rect(im,(14,0,15,15),(141,191,200,18))
        if mask&4:rect(im,(0,14,15,15),(141,191,200,18))
        if mask&8:rect(im,(0,0,1,15),(141,191,200,18))
        # Keep only external frame edges; inner tiles get no repeated highlights.
        if mask==15:im=new((141,191,200,18))
        save(im,f'crate_ct_{mask:02d}')
    h=panel();h.alpha_composite(iris());rect(h,(5,5,10,10),'ink')
    line(h,[(5,7),(7,5)],'rope1');line(h,[(5,8),(7,6)],'wheat2')
    ImageDraw.Draw(h).polygon([(10,6),(8,9),(9,10),(11,9)],fill=color('white'))
    save(h,'hatch_front');save(iris(),'iris_brass')
    ande=panel('a1');rect(ande,(4,4,11,11),'rope0');rect(ande,(5,4,6,11),'rope1');line(ande,[(4,7),(11,7)],'paper0');save(ande,'andesite_base')
    shaft=ande.copy();rect(shaft,(5,5,10,10),'ink');rect(shaft,(6,6,9,9),'a2');line(shaft,[(6,6),(9,9)],'a0');save(shaft,'shaft_socket')
    for active in [False,True]:
        sc=new('sculk')
        for pts in [[(0,5),(3,5),(3,3),(6,3),(6,7),(9,7),(9,4),(12,4),(12,5),(15,5)],[(0,12),(2,12),(2,10),(5,10),(5,13),(8,13),(8,10),(12,10),(12,12),(15,12)]]:
            line(sc,pts,'teal' if active else 'teal0')
        for x,y in [(3,5),(6,3),(9,7),(12,10),(5,13)]:px(sc,x,y,'cyan' if active else 'teal')
        line(sc,[(0,0),(15,0)],'a1');line(sc,[(0,15),(15,15)],'ink');save(sc,'sculk_active' if active else 'sculk_idle')
        cr=new('purple1');rect(cr,(3,0,5,15),'purple2');rect(cr,(6,0,7,15),'purple3' if active else 'purple2');rect(cr,(12,0,15,15),'purple0');line(cr,[(2,3),(3,2),(4,2)],'purple3');save(cr,'crystal_active' if active else 'crystal_idle')
    side=new('paper2');rect(side,(0,0,15,0),'paper3');rect(side,(0,15,15,15),'paper0');rect(side,(0,0,0,15),'paper1');rect(side,(15,0,15,15),'paper1')
    for x,y in [(3,3),(4,3),(10,6),(11,6),(5,12),(11,13)]:px(side,x,y,'paper1')
    rect(side,(7,0,8,15),'tape');line(side,[(8,1),(8,14)],'paper3');save(side,'parcel_side')
    back=side.copy()
    for x in [3,5,10,12]:rect(back,(x,3,x,4),'paper0')
    save(back,'parcel_back');pt=side.copy();rect(pt,(0,7,15,8),'tape');line(pt,[(1,6),(6,6)],'paper0');line(pt,[(9,9),(14,9)],'paper1');save(pt,'parcel_top')
    pb=side.copy();line(pb,[(1,1),(7,7),(1,14)],'paper1');line(pb,[(14,1),(9,7),(14,14)],'paper1');save(pb,'parcel_bottom')
    front=side.copy();rect(front,(1,2,14,14),'paper0');rect(front,(2,3,13,13),'a2');rect(front,(3,4,12,12),(141,191,200,16));line(front,[(3,6),(5,4)],(200,230,232,120));save(front,'parcel_front')
    # Read original textures directly and keep exact Fluid Logistics recolor masks.
    fluid=OUT/'references/fluidlogistics'
    stats=[]
    for name in ['frame','details','horizontal_unpowered','horizontal_powered','horizontal_linked','vertical_unpowered','vertical_powered','vertical_linked','iris_closed','iris_open','particle']:
        fn='packager_'+name+'.png';a=Image.open(OUT/'references/create'/fn).convert('RGBA');b=Image.open(fluid/fn).convert('RGBA');o=a.copy();mask=new() if a.size==(16,16) else Image.new('RGBA',a.size)
        count=0
        for y in range(a.height):
            for x in range(a.width):
                p=b.getpixel((x,y));h,s,v=colorsys.rgb_to_hsv(*(k/255 for k in p[:3]))
                if a.getpixel((x,y))!=p and p[3] and (h*360>=350 or h*360<=40) and s>.12:
                    value=max(a.getpixel((x,y))[:3])/255
                    ramp=['b0','b1','b2','b3','b4'];q=color(ramp[min(4,max(0,int((value-.16)*6)))])
                    o.putpixel((x,y),q[:3]+(p[3],));mask.putpixel((x,y),(255,255,255,255));count+=1
        save(o,'packager_'+name);save(mask,'mask_packager_'+name);stats.append({'file':fn,'size':a.size,'mask_pixels':count,'outside_mask_unchanged':all(o.getpixel((x,y))==a.getpixel((x,y)) for y in range(a.height) for x in range(a.width) if mask.getpixel((x,y))[3]==0)})
    (OUT/'packager-mask-audit.json').write_text(json.dumps(stats,indent=2),encoding='utf-8')
    for state in ['unpowered','powered']:
        atlas=load('packager_horizontal_'+state)
        ps=new('ivory');ps.alpha_composite(atlas.crop((0,0,16,16)))
        # Only interior body details are new. Reference frame remains at exact pixels.
        for x in [5,7,9]:rect(ps,(x,5,x,7),'a1')
        rect(ps,(11,10,12,11),'ink');px(ps,11,10,'cyan' if state=='powered' else 'red0')
        save(ps,'packager_side_'+state)
        pf=new('ivory');pf.alpha_composite(load('packager_iris_closed'));save(pf,'packager_front_'+state)
    pkback=new('ivory');pkback.alpha_composite(load('packager_frame').crop((0,0,16,16)))
    line(pkback,[(4,10),(4,4),(11,4),(11,11),(6,11),(6,6),(9,6),(9,9)],'a0');px(pkback,4,11,'a2');save(pkback,'packager_back')
    pkt=new('ivory');pkt.alpha_composite(load('packager_frame').crop((0,0,16,16)));save(pkt,'packager_top')
    # Flat item sprites. Every shape is drawn on the 16px source canvas.
    feed=new();d=ImageDraw.Draw(feed)
    for pts in [[(4,2),(6,3),(7,11),(5,14),(3,11),(2,5)],[(8,0),(10,3),(9,12),(7,14),(6,10),(6,4)],[(12,2),(14,5),(12,12),(10,14),(9,9),(10,4)]]:
        d.polygon(pts,fill=color('wheat0'))
    for x,y in [(4,3),(4,5),(5,7),(8,2),(8,4),(8,6),(12,4),(11,6),(11,8)]:
        rect(feed,(x,y,x+1,y+1),'wheat2');px(feed,x,y,'wheat3')
    for x,y in [(2,8),(11,10)]:
        rect(feed,(x,y,x+2,y+2),'red0');rect(feed,(x,y,x+1,y+1),'red');px(feed,x,y,'red1')
    for x,y in [(6,6),(10,3),(4,12)]:px(feed,x,y,'rope0')
    line(feed,[(3,10),(6,11),(11,10)],'rope0',2);line(feed,[(3,9),(6,10),(12,9)],'rope1');rect(feed,(7,9,8,11),'tape');line(feed,[(7,12),(6,14)],'rope1');line(feed,[(8,12),(10,13)],'rope0');save(feed,'mixed_feed')
    fil=new();rect(fil,(3,1,12,14),'b0');rect(fil,(3,1,12,1),'b4');rect(fil,(3,2,3,13),'b3');rect(fil,(12,2,12,13),'b1');rect(fil,(4,2,11,13),'b2');rect(fil,(5,3,10,12),'ivory');rect(fil,(5,12,10,12),'paper3')
    for x,y in [(5,5),(7,4),(9,5)]:rect(fil,(x,y,x,y+1),'ink')
    ImageDraw.Draw(fil).polygon([(7,7),(8,7),(10,9),(9,10),(6,10),(5,9)],fill=color('ink'));save(fil,'creature_filter')
    catch=new();d=ImageDraw.Draw(catch);d.polygon([(4,2),(12,2),(14,4),(14,10),(11,12),(8,11),(7,14),(4,15),(3,14),(5,9),(2,7),(2,4)],fill=color('ink'))
    rect(catch,(4,3,12,9),'b2');rect(catch,(5,2,11,2),'b4');rect(catch,(3,4,3,8),'b3');rect(catch,(13,4,13,9),'b1');rect(catch,(4,10,10,10),'b0');line(catch,[(6,10),(4,14)],'rope0',2);px(catch,5,12,'a1')
    # Miniature spiral with a six-pixel aperture, not a plus sign.
    rect(catch,(6,4,11,9),'ink');line(catch,[(6,8),(6,4),(11,4),(11,9),(8,9),(8,6),(9,6)],'a1');line(catch,[(7,5),(10,5)],'a2');rect(catch,(3,8,4,9),'red');px(catch,12,4,'cyan');save(catch,'creature_catcher')

DIRS=['north','south','west','east','up','down']
def verts(a,b):
    x,y,z=a;X,Y,Z=b
    return {'north':[(x,Y,z),(X,Y,z),(X,y,z),(x,y,z)],'south':[(X,Y,Z),(x,Y,Z),(x,y,Z),(X,y,Z)],
      'west':[(x,Y,Z),(x,Y,z),(x,y,z),(x,y,Z)],'east':[(X,Y,z),(X,Y,Z),(X,y,Z),(X,y,z)],
      'up':[(x,Y,Z),(X,Y,Z),(X,Y,z),(x,Y,z)],'down':[(x,y,z),(X,y,z),(X,y,Z),(x,y,Z)]}

class Model:
    def __init__(self,name):self.name=name;self.elements=[];self.textures=[];self.names=[];self.groups={}
    def tex(self,name):
        if name in self.names:return self.names.index(name)
        im=load(name);i=len(self.names);self.names.append(name)
        self.textures.append({'name':name+'.png','id':str(i),'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,name)),'mode':'bitmap','saved':True,'particle':False,'width':im.width,'height':im.height,'uv_width':im.width,'uv_height':im.height,'relative_path':'../textures/'+name+'.png','source':'data:image/png;base64,'+base64.b64encode((TEX/(name+'.png')).read_bytes()).decode()});return i
    def mesh(self,name,polys,group='模型'):
        vs={};faces={}
        for j,(points,texture,uv) in enumerate(polys):
            ids=[]
            for k,p in enumerate(points):
                key=f'v{j}_{k}';vs[key]=list(p);ids.append(key)
            if uv is None:
                im=load(texture);uv=[(0,0),(im.width,0),(im.width,im.height),(0,im.height)][:len(points)]
            faces['f'+str(j)]={'vertices':ids,'uv':{k:list(v) for k,v in zip(ids,uv)},'texture':self.tex(texture)}
        uid=str(uuid.uuid5(uuid.NAMESPACE_URL,self.name+'/'+name+'/'+str(len(self.elements))))
        self.elements.append({'name':name,'type':'mesh','origin':[0,0,0],'rotation':[0,0,0],'vertices':vs,'faces':faces,'uuid':uid,'visibility':True,'export':True});self.groups.setdefault(group,[]).append(uid)
    def box(self,name,a,b,textures,group='模型',uv=None):
        if isinstance(textures,str):textures={d:textures for d in DIRS}
        self.mesh(name,[(points,textures[d],uv.get(d) if uv else None) for d,points in verts(a,b).items() if d in textures],group)
    def save(self):
        outliner=[{'name':k,'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,self.name+'/'+k)),'origin':[0,0,0],'children':v,'export':True,'isOpen':False,'visibility':True} for k,v in self.groups.items()]
        d={'meta':{'format_version':'5.0','model_format':'free','box_uv':False},'name':self.name,'model_identifier':self.name,'resolution':{'width':16,'height':16},'elements':self.elements,'outliner':outliner,'textures':self.textures}
        p=MOD/(self.name+'.bbmodel');p.write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding='utf-8');return p

def crate_model(name='01_aviation_crate',opened=False,w=16,h=16,l=16):
    m=Model(name)
    m.box('铆接顶板',(0,h-1,0),(w,h,l),{'up':'crate_top','down':'alu_bottom','north':'alu_panel','south':'alu_panel','west':'alu_panel','east':'alu_panel'},'航空铝外框')
    m.box('铆接底板',(0,0,0),(w,1,l),'alu_bottom','航空铝外框')
    for x in [0,w-1]:
        for z in [0,l-1]:
            m.box('铝立柱',(x,1,z),(x+1,h-1,z+1),'solid_a2','航空铝外框')
            for y in [0,h-2]:m.box('黄铜护角',(max(0,x-0.15),y,max(0,z-0.15)),(min(w,x+1.15),y+2,min(l,z+1.15)),'solid_b2','黄铜角件')
    glass=new((141,191,200,18));line(glass,[(2,5),(5,2)],(200,230,232,110));line(glass,[(2,8),(8,2)],(200,230,232,50));save(glass,'glass_clear')
    if not opened:
        m.box('前后玻璃',(1,1,.35),(w-1,h-1,l-.35),{'north':'glass_clear','south':'glass_clear'},'可移除玻璃')
        m.box('侧向玻璃',(.35,1,1),(w-.35,h-1,l-1),{'west':'glass_clear','east':'glass_clear'},'可移除玻璃')
    elif w==16:
        m.box('保留侧后玻璃',(.35,1,.35),(w-.35,h-1,l-.35),{'south':'glass_clear','east':'glass_clear','west':'glass_clear'},'可移除玻璃')
    return m

def crystal(m,x,z,w,h,state,tilt=0):
    y=8; pts=[(x-w,y,z-w),(x+w,y,z-w),(x+w,y,z+w),(x-w,y,z+w)]
    shoulder=[(a+tilt,y+h-2,c) for a,b,c in pts];tip=(x+tilt,y+h,z)
    tex='crystal_'+state;polys=[]
    for i in range(4):
        j=(i+1)%4;polys.append(([shoulder[i],shoulder[j],pts[j],pts[i]],tex,[(0,0),(8,0),(8,16),(0,16)]));polys.append(([tip,shoulder[j],shoulder[i]],tex,[(4,0),(8,4),(0,4)]))
    m.mesh('紫水晶簇',polys,'紫水晶')

def resonator(state):
    m=Model('03_echo_resonator_'+state)
    m.box('安山机壳底座',(0,0,0),(16,5,16),{d:('shaft_socket' if d in ['north','south'] else 'andesite_base') for d in DIRS},'底座')
    m.box('轴承',(5,1,-1),(11,4,1),'shaft_socket','底座')
    m.box('幽匿脉络环',(1,5,1),(15,8,15),'sculk_'+state,'幽匿环带')
    for x,z,w,h,t in [(7,8,2.1,8,0),(11,10,1.5,5,1),(4,5,1.4,4,-.6),(10,4,1.3,6,.5),(4,11,1.2,3,-.4)]:crystal(m,x,z,w,h,state,t)
    return m

def parcel(with_creature=False):
    m=Model('05_creature_parcel'+('_display' if with_creature else ''))
    m.box('纸板背板',(1,1,14),(15,15,15),{'south':'parcel_back','north':'parcel_side','up':'parcel_top','down':'parcel_bottom','east':'parcel_side','west':'parcel_side'},'包裹外壳')
    m.box('纸板左壁',(1,1,1),(2,15,14),'parcel_side','包裹外壳');m.box('纸板右壁',(14,1,1),(15,15,14),'parcel_side','包裹外壳')
    m.box('纸板顶盖',(1,14,1),(15,15,14),'parcel_top','包裹外壳');m.box('纸板底部',(1,1,1),(15,2,14),'parcel_bottom','包裹外壳')
    m.box('开窗正面',(1,1,.9),(15,15,1),{'north':'parcel_front','south':'parcel_front'},'透明观察窗')
    if with_creature:
        m.box('示意羊身体',(4,3,5),(12,9,12),'solid_white','示意生物_不烘焙进贴图')
        m.box('示意羊头',(5,5,2),(10,10,6),'solid_ivory','示意生物_不烘焙进贴图')
        for x in [5,9]:m.box('眼睛',(x,8,1.9),(x+.8,8.8,2),'solid_ink','示意生物_不烘焙进贴图')
        m.box('鼻口',(6,5.5,1.85),(9,6.5,2),'solid_paper0','示意生物_不烘焙进贴图')
        for x in [4,10]:m.box('示意羊腿',(x,2,8),(x+2,4,10),'solid_rope0','示意生物_不烘焙进贴图')
    return m

def packager(state):
    m=Model('04_creature_packager_'+state)
    # Six canonical 16px faces plus inset front iris; source atlases stay separate.
    faces={'north':'packager_front_'+state,'south':'packager_back','east':'packager_side_'+state,'west':'packager_side_'+state,'up':'packager_top','down':'alu_bottom'}
    m.box('主体',(0,0,0),(16,16,16),faces,'机体')
    # Recessed iris; cover original flat iris with exactly the same sampled pixels.
    irisname='packager_iris_closed';m.box('虹膜封口',(1,1,-.35),(15,15,.15),{'north':irisname,'east':'solid_b1','west':'solid_b2','up':'solid_b3','down':'solid_b0'},'虹膜',{'north':[(1,1),(15,1),(15,15),(1,15)]})
    # Separate rear collar makes the connection motif read as a mechanical joint.
    for a,b in [((4,4,16),(12,5,16.6)),((4,11,16),(12,12,16.6)),((4,5,16),(5,11,16.6)),((11,5,16),(12,11,16.6))]:m.box('后接圈',a,b,'solid_a0','连接面')
    return m

def sprite_model(name,tex):
    m=Model(name);im=load(tex)
    # Per opaque texel boxes form a thin sprite, keeping the silhouette exact.
    for y in range(16):
        for x in range(16):
            if im.getpixel((x,y))[3]:
                uv={d:[(x,y),(x+1,y),(x+1,y+1),(x,y+1)] for d in DIRS}
                m.box(f'像素_{x}_{y}',(x,15-y,7.5),(x+1,16-y,8.5),tex,'平面像素挤出',uv)
    return m

def make_models():
    models=[crate_model(),crate_model('01_aviation_crate_open',True),crate_model('01_aviation_crate_connected',False,48,32,16)]
    hatch=Model('02_interaction_hatch');hatch.box('铆接铝板',(0,0,0),(16,16,16),{d:('hatch_front' if d=='north' else 'alu_panel') for d in DIRS},'舱口')
    # Raised octagonal port rim follows pixel aperture silhouette.
    for a,b in [((5,14,-.6),(11,15,0)),((5,1,-.6),(11,2,0)),((1,5,-.6),(2,11,0)),((14,5,-.6),(15,11,0))]:hatch.box('操作口凸缘',a,b,'solid_b2','舱口')
    models += [hatch,resonator('idle'),resonator('active'),packager('unpowered'),packager('powered'),parcel(),parcel(True),sprite_model('06_mixed_feed','mixed_feed'),sprite_model('07_creature_filter','creature_filter'),sprite_model('08_creature_catcher','creature_catcher')]
    return [m.save() for m in models]

def make_showcase():
    """One optional Blockbench file to inspect all eight designs together."""
    m=Model('00_all_assets_showcase')
    for i,(_,cn,_,name,_,_) in enumerate(ASSETS):
        d=json.loads((MOD/(name+'.bbmodel')).read_text(encoding='utf-8'));offset=np.array([(i%4)*24,0,(i//4)*26])
        for e in d['elements']:
            polys=[]
            for f in e['faces'].values():
                pts=[(np.array(e['vertices'][v])+offset).tolist() for v in f['vertices']]
                tex=Path(d['textures'][f['texture']]['name']).stem
                polys.append((pts,tex,[f['uv'][v] for v in f['vertices']]))
            m.mesh(e['name'],polys,cn)
    m.save()

def read_model(path):
    d=json.loads(path.read_text(encoding='utf-8'));textures=[np.asarray(Image.open(io.BytesIO(base64.b64decode(t['source'].split(',')[1]))).convert('RGBA')) for t in d['textures']];faces=[]
    for e in d['elements']:
        for f in e['faces'].values():
            points=np.array([e['vertices'][k] for k in f['vertices']],float);uv=np.array([f['uv'][k] for k in f['vertices']],float)
            faces.append((points,uv,textures[f['texture']]))
    return d,faces

def render(path,size=(520,520),camera=(1,.72,-1),ortho=False):
    """Nearest-texel triangle rasterizer; opaque z-buffer, then translucent pass."""
    d,faces=read_model(path);W,H=size;out=np.zeros((H,W,4),dtype=float);zb=np.full((H,W),-np.inf)
    view=np.array(camera,float);view/=np.linalg.norm(view);right=np.cross(view,[0,1,0])
    if np.linalg.norm(right)<.01:right=np.array([1.,0,0])
    right/=np.linalg.norm(right);up=np.cross(right,view)
    allp=np.concatenate([p for p,u,t in faces]);center=(allp.min(0)+allp.max(0))/2
    basis=np.array([right,-up,view]).T;ps=(allp-center)@basis
    scale=min((W-30)/max(1,np.ptp(ps[:,0])),(H-30)/max(1,np.ptp(ps[:,1])))
    light=np.array([-.45,.8,-.65]);light/=np.linalg.norm(light)
    triangles=[]
    for p,u,t in faces:
        n=np.cross(p[1]-p[0],p[2]-p[0]);n/=max(np.linalg.norm(n),1e-9)
        # Meshes are rendered double-sided, matching transparent glass intent.
        if np.dot(n,view)<0:n=-n
        bright=1 if ortho else min(1.10,max(.60,.77+.28*np.dot(n,light)))
        q=(p-center)@basis;q[:,:2]*=scale;q[:,:2]+=np.array([W/2,H/2])
        for i in range(1,len(p)-1):triangles.append((q[[0,i,i+1]],u[[0,i,i+1]],t,bright))
    def drawtri(q,uv,t,bright,translucent):
        lo=np.maximum(np.floor(q[:,:2].min(0)).astype(int),[0,0]);hi=np.minimum(np.ceil(q[:,:2].max(0)).astype(int),[W-1,H-1])
        if np.any(hi<lo):return
        xx,yy=np.meshgrid(np.arange(lo[0],hi[0]+1)+.5,np.arange(lo[1],hi[1]+1)+.5)
        a,b,c=q;den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(den)<1e-8:return
        w0=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den;w1=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;w2=1-w0-w1
        depth=w0*a[2]+w1*b[2]+w2*c[2]
        uu=np.clip(np.floor(w0*uv[0,0]+w1*uv[1,0]+w2*uv[2,0]).astype(int),0,t.shape[1]-1)
        vv=np.clip(np.floor(w0*uv[0,1]+w1*uv[1,1]+w2*uv[2,1]).astype(int),0,t.shape[0]-1)
        pix=t[vv,uu].astype(float)/255;pix[:,:,:3]*=bright;pix[:,:,:3]=np.clip(pix[:,:,:3],0,1)
        sy=slice(lo[1],hi[1]+1);sx=slice(lo[0],hi[0]+1);z=zb[sy,sx];dst=out[sy,sx]
        mask=(w0>=-1e-7)&(w1>=-1e-7)&(w2>=-1e-7)&(depth>z+1e-6)
        if translucent:
            mask&=(pix[:,:,3]>0)&(pix[:,:,3]<1);alpha=pix[:,:,3:4];res=np.empty_like(dst);res[:,:,:3]=pix[:,:,:3]*alpha+dst[:,:,:3]*(1-alpha);res[:,:,3]=pix[:,:,3]+dst[:,:,3]*(1-pix[:,:,3]);dst[mask]=res[mask]
        else:
            mask&=pix[:,:,3]==1;dst[mask]=pix[mask];z[mask]=depth[mask]
    for tri in triangles:drawtri(*tri,False)
    for tri in sorted(triangles,key=lambda v:v[0][:,2].mean()):drawtri(*tri,True)
    alpha=out[:,:,3:4];out[:,:,:3]=np.divide(out[:,:,:3],alpha,out=np.zeros_like(out[:,:,:3]),where=alpha>0)
    return Image.fromarray(np.clip(out*255,0,255).astype('uint8'))

ASSETS=[
 ('01','航空箱','AVIATION CRATE','01_aviation_crate',['crate_window','crate_window','crate_window','crate_window','crate_top','alu_bottom'],['铝框与黄铜护角 · 四侧通透玻璃','开窗移除前方玻璃，保留结构边框','连接示例：3×2 外墙连续，无内框遮挡']),
 ('02','交互舱口','INTERACTION HATCH','02_interaction_hatch',['hatch_front','alu_panel','alu_panel','alu_panel','alu_panel','alu_panel'],['同族铝板 · 黄铜操作口 · 轻度凸缘','毛刷与奶滴标识：护理 / 剪毛 / 挤奶','正面朝向机械手，背部连接航空箱']),
 ('03','回响激发器','ECHO RESONATOR','03_echo_resonator_idle',['shaft_socket','shaft_socket','andesite_base','andesite_base','crystal_idle','alu_bottom'],['安山底座 + 幽匿环带 + 独立水晶簇','激活仅切换色阶，保留像素边缘','顶部为立体晶体，贴图分配见纹理区']),
 ('04','生物打包机','CREATURE PACKAGER','04_creature_packager_unpowered',['packager_front_unpowered','packager_back','packager_side_unpowered','packager_side_unpowered','packager_top','alu_bottom'],['按流体打包机换色掩膜替换为黄铜','保留虹膜、象牙白板、红带与连接纹','本轮为工程概念模型，展示基础两态']),
 ('05','生物包裹','CREATURE PARCEL','05_creature_parcel_display',['parcel_front','parcel_back','parcel_side','parcel_side','parcel_top','parcel_bottom'],['纸板封箱胶带 · 前方大观察窗','示意羊独立建模，未画入窗口贴图','同一模型用于手持 / 掉落外观设计']),
 ('06','混合饲料','MIXED FEED','06_mixed_feed',['mixed_feed'],['麻绳束腰 · 金黄麦穗 · 苹果与种子','真实 16×16 透明 PNG；无碗无容器','薄片挤出模型与背包图标来自同一源稿']),
 ('07','生物过滤器','CREATURE FILTER','07_creature_filter',['creature_filter'],['长方形黄铜边框 · 浅纸芯 · 深色爪印','保留 Create 过滤卡片的识别特征','真实 16×16 透明 PNG 与薄片模型']),
 ('08','生物收纳器','CREATURE CATCHER','08_creature_catcher',['creature_catcher'],['黄铜便携机身 · 小型虹膜口 · 握柄','红饰带与青色指示灯呼应生物打包机','真实 16×16 透明 PNG 与薄片模型'])]
FONT='C:/Windows/Fonts/msyh.ttc'
def font(s):return ImageFont.truetype(FONT,s)
BG='#091D29';PANEL='#102B39';EDGE='#2C4854';WHITE='#E5E9E7';MUTED='#9EB1B9';GOLD='#E2BC70'
def text(im,xy,s,size=22,fill=WHITE):ImageDraw.Draw(im).text(xy,s,font=font(size),fill=fill)
def panelbox(im,box,title):
    d=ImageDraw.Draw(im);d.rounded_rectangle(box,12,fill=PANEL,outline=EDGE,width=2);text(im,(box[0]+22,box[1]+14),title,23)
def paste_center(im,v,box):
    x,y,w,h=box
    if v.width>w or v.height>h:v=v.resize((int(v.width*min(w/v.width,h/v.height)),int(v.height*min(w/v.width,h/v.height))),Image.Resampling.NEAREST)
    im.alpha_composite(v,(int(x+(w-v.width)/2),int(y+(h-v.height)/2)))
def checker(im,x,y,w,h,k=12):
    d=ImageDraw.Draw(im)
    for a in range(0,w,k):
        for b in range(0,h,k):d.rectangle((x+a,y+b,x+min(w-1,a+k-1),y+min(h-1,b+k-1)),fill=('#29404A' if (a//k+b//k)%2 else '#213640'))
def texthumb(im,name,x,y,k=8,grid=False):
    v=load(name);w=v.width*k;h=v.height*k;checker(im,x,y,w,h,k);im.alpha_composite(v.resize((w,h),Image.Resampling.NEAREST),(x,y))
    if grid:
        d=ImageDraw.Draw(im)
        for a in range(0,w+1,k):d.line((x+a,y,x+a,y+h),fill='#36505A')
        for b in range(0,h+1,k):d.line((x,y+b,x+w,y+b),fill='#36505A')

def make_sheets():
    files=[];cams=[(0,0,-1),(0,0,1),(-1,0,0),(1,0,0),(0,1,0),(0,-1,0)];labels=['正面','背面','左侧','右侧','顶部','底部']
    for num,cn,en,name,texs,notes in ASSETS:
        print('Rendering',name,flush=True);p=MOD/(name+'.bbmodel');im=Image.new('RGBA',(1800,1200),BG)
        text(im,(38,24),num,48,GOLD);text(im,(130,26),cn,38);text(im,(132,77),en+'  /  DESIGN STUDY 01',18,MUTED)
        text(im,(1320,35),'AIR CRATE · CREATE ADDON',20,GOLD);text(im,(1320,68),'脚本像素绘制 / Blockbench 模型',17,MUTED)
        panelbox(im,(28,126,840,714),'模型预览  /  SAME-SOURCE GEOMETRY')
        hero=render(p,(730,510),camera=(1,.65,-1));hero.save(REN/(name+'_iso.png'));paste_center(im,hero,(65,180,738,510))
        if len(texs)==6:
            panelbox(im,(860,126,1772,714),'六面正投影  /  ORTHOGRAPHIC')
            for i,(cam,label) in enumerate(zip(cams,labels)):
                x=882+(i%3)*290;y=180+(i//3)*256
                v=render(p,(236,210),cam,True);v.save(REN/(name+'_'+DIRS[i]+'.png'));paste_center(im,v,(x,y,270,205));text(im,(x+106,y+215),label,19,MUTED)
        else:
            panelbox(im,(860,126,1772,714),'像素原稿  /  16 × 16 RGBA')
            texthumb(im,texs[0],904,215,24,True)
            text(im,(1330,218),'无抗锯齿 · 最近邻放大',20,MUTED)
            texthumb(im,texs[0],1370,300,8)
            texthumb(im,texs[0],1426,475,1)
            text(im,(1340,518),'上：8×　下：原始大小',18,MUTED)
        panelbox(im,(28,734,1180,1020),'16 × 16 材质源稿  /  TEXTURE SOURCES')
        if len(texs)==6:
            for i,t in enumerate(texs):
                x=60+i*183;texthumb(im,t,x,798,8);text(im,(x+42,940),labels[i],18,MUTED)
            if num=='03':text(im,(54,981),'注：顶部为晶体分面纹理；幽匿环带与发光变体另存为独立贴图。',16,MUTED)
            elif num=='04':text(im,(54,981),'另附完整 32×32 原布局图集及精确换色掩膜；此处为六面设计组合稿。',16,MUTED)
            elif num=='01':text(im,(54,981),'另附 16 种邻接掩码贴图；单格窗框样片与实体铝框为两种分层表达。',16,MUTED)
        else:
            texthumb(im,texs[0],62,800,9);text(im,(256,817),'一像素一色块，透明边界真实保留。',24)
            text(im,(256,865),'.bbmodel 内嵌同一 PNG，可逐像素修改。',22,MUTED)
            text(im,(256,913),'工程图薄片预览不改变物品原稿分辨率。',22,MUTED)
        panelbox(im,(1200,734,1772,1020),'状态 / 结构细节')
        variants={'01':('01_aviation_crate_open','01_aviation_crate_connected','前窗开放','3×2 连接结构'),
         '03':('03_echo_resonator_idle','03_echo_resonator_active','未激活','激活'),
         '04':('04_creature_packager_unpowered','04_creature_packager_powered','未充能','充能'),
         '05':('05_creature_parcel','05_creature_parcel_display','空窗源模型','独立示意生物')}
        if num in variants:
            a,b,la,lb=variants[num]
            for j,(n,lab) in enumerate([(a,la),(b,lb)]):
                v=render(MOD/(n+'.bbmodel'),(235,170));v.save(REN/(n+'_detail.png'));paste_center(im,v,(1217+j*270,790,265,171));text(im,(1260+j*270,975),lab,18,MUTED)
        elif num=='02':
            texthumb(im,'hatch_front',1230,797,10);text(im,(1424,822),'毛刷 / 奶滴',24,GOLD);text(im,(1424,870),'护理操作口',22);text(im,(1424,915),'铝框 + 黄铜圈',20,MUTED)
        else:
            text(im,(1232,806),notes[0].split(' · ')[0],27,GOLD)
            for k,phrase in enumerate(notes[0].split(' · ')[1:]):text(im,(1232,853+k*36),phrase,22)
            text(im,(1232,960),'轮廓与颜色可在 Blockbench 继续调整',16,MUTED)
        panelbox(im,(28,1040,1772,1170),'统一色板')
        pal=['a0','a1','a2','a3','b0','b1','b2','b3','b4','sculk','cyan','purple2'] if num not in ['05','06'] else ['paper0','paper1','paper2','paper3','tape','rope0','rope1','wheat1','wheat2','red','a2','cyan']
        d=ImageDraw.Draw(im)
        for i,c in enumerate(pal):
            x=56+i*76;d.rectangle((x,1091,x+51,1127),fill=color(c));text(im,(x,1135),C[c].upper(),11,MUTED)
        for i,n in enumerate(notes):text(im,(1010,1070+i*27),n,17,MUTED)
        dest=SHEET/(num+'_'+name[3:]+'.png');im.convert('RGB').save(dest);files.append(dest)
    contact=Image.new('RGB',(1800,2400),BG)
    for i,p in enumerate(files):contact.paste(Image.open(p).resize((900,600),Image.Resampling.LANCZOS),((i%2)*900,(i//2)*600))
    contact.save(OUT/'00_all_assets.png')

def validate():
    results=[]
    for p in MOD.glob('*.bbmodel'):
        d,faces=read_model(p);assert len(d['elements'])>0
        for e in d['elements']:
            for f in e['faces'].values():
                assert 0<=f['texture']<len(d['textures']);assert len(f['vertices'])>=3
                t=d['textures'][f['texture']]
                for u,v in f['uv'].values():assert 0<=u<=t['width'] and 0<=v<=t['height'],(p.name,u,v,t['name'])
        results.append({'model':p.name,'elements':len(d['elements']),'textures':len(d['textures']),'valid_uv':True})
    textures=[]
    for p in TEX.glob('*.png'):
        im=Image.open(p);assert im.mode=='RGBA';assert im.size in [(16,16),(32,32)],(p,im.size)
        textures.append({'file':p.name,'size':im.size,'colors':len(im.getcolors(65536)),'alpha_values':sorted(set(im.getchannel('A').get_flattened_data()))})
    report={'models':results,'textures':textures,'sheets':len(list(SHEET.glob('*.png'))),'generation':'deterministic Pillow/NumPy, no image API','renderer':'same embedded Blockbench mesh/UV data, nearest texel sampling'}
    (OUT/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print('Validated',len(results),'models,',len(textures),'textures,',report['sheets'],'sheets')

def make_gallery():
    import html
    cards=[]
    for num,cn,en,name,texs,notes in ASSETS:
        fn=num+'_'+name[3:]+'.png'
        cards.append(f'<article><h2>{num} {cn} <small>{en}</small></h2><a href="sheets/{fn}" target="_blank"><img src="sheets/{fn}" alt="{cn}工程图"></a><p><a href="sheets/{fn}" download>工程图 PNG</a><a href="models/{name}.bbmodel" download>Blockbench 模型</a></p></article>')
    page='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>航空箱 · 像素材质工程图</title><style>
    body{margin:0;background:#091d29;color:#e5e9e7;font:16px/1.7 "Microsoft YaHei",sans-serif}main{max-width:1500px;margin:auto;padding:36px}h1{font-size:34px;margin:0;color:#e2bc70}h2{font-size:23px}small{font-size:13px;color:#9eb1b9;font-weight:400;margin-left:12px}a{color:#e2bc70;margin-right:24px}article{border-top:1px solid #2c4854;margin:36px 0;padding-top:18px}img{display:block;width:100%;height:auto}header p{max-width:950px;color:#afc0c7}nav{padding:18px 0}code{background:#183240;padding:3px 6px}</style><main><header><h1>航空箱 / AIR CRATE</h1><p>脚本逐像素绘制 → 可编辑 Blockbench 模型 → 同源模型工程图。8 项造型确认稿，附透明 PNG、基础状态变体与源脚本。点击工程图查看原尺寸。</p><nav><a href="00_all_assets.png">八项总览</a><a href="models/00_all_assets_showcase.bbmodel" download>八项模型同场景</a><a href="README.md">制作说明</a><a href="validation.json">尺寸与 UV 检查</a></nav></header>'''+''.join(cards)+'</main></html>'
    (OUT/'index.html').write_text(page,encoding='utf-8')

if __name__=='__main__':
    make_textures();make_models();make_showcase();make_sheets();make_gallery();validate()

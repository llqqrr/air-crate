"""Air Crate frog-port pneumatic catcher. Deterministic pixels and real source UVs."""
from pathlib import Path
import json,math,hashlib,copy
import numpy as np
from PIL import Image,ImageDraw,ImageFont
import native_assets as A
import mesh_engine as E

O=Path(__file__).resolve().parent
M=O/'models';T=O/'textures';R=O/'references';P=O/'renders'
BG='#192326';PANEL='#243034';EDGE='#465659';FG='#e0ddd0';ACC='#d6b575';SUB='#a5b5ad'
DISPLAY=A.load_native('create','item/potato_cannon/item')['display']
NAMES={'body':'枪身_固定','tank':'原铜背罐_横置','head':'蛙港上颌_含双眼','jaw':'蛙港下颌_固定','tongue':'蛙舌_伸缩','cog':'驱动齿轮','trigger':'黄铜扳机','needle':'压力指针','vent':'排气口_固定','valve':'尾部泄压阀'}
H=[8,10.8,2.30];G=[4.04,10.2,8.4];C=[4.65,6.6,5.9]

def font(n):return ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',n)
def text(im,xy,s,n=20,fill=FG):ImageDraw.Draw(im).text(xy,s,font=font(n),fill=fill)
def panel(im,box,title):ImageDraw.Draw(im).rounded_rectangle(box,10,fill=PANEL,outline=EDGE,width=2);text(im,(box[0]+20,box[1]+15),title,23)
def paste(im,v,box):
    x,y,w,h=box;v=v.copy();v.thumbnail((w,h),Image.Resampling.NEAREST);im.alpha_composite(v,(int(x+(w-v.width)/2),int(y+(h-v.height)/2)))

def prepare():
    for ns in ['create','aircrate']:
        for p in (R/ns/'textures').rglob('*.png'):A.texture(ns,p.relative_to(R/ns/'textures').with_suffix('').as_posix())
    tank=A.img(A.ctex('block/copper_backtank'));tank.paste((0,0,0,0),(0,20,tank.width,tank.height))
    A.save(tank,'copper_pressure_shell_32','create:block/copper_backtank','retain original copper shell texels; remove lower atlas region containing brown back pad / carried apparatus; model imports only shell elements 0,1,2')
    # Six-pixel face; the native diesel gauge is the layout / palette reference.
    im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
    dark=(58,65,64,255);brass=(151,115,55,255);hi=(223,185,109,255);cream=(220,213,181,255)
    d.rectangle((1,0,6,7),fill=dark);d.rectangle((0,1,7,6),fill=dark)
    d.rectangle((1,1,6,6),fill=cream);d.line((1,0,6,0),fill=hi);d.line((0,1,0,6),fill=hi);d.line((7,1,7,6),fill=brass);d.line((1,7,6,7),fill=brass)
    # Low pressure at left; discrete red warning pixels, high pressure at right.
    for q in [(1,3),(1,4),(2,2)]:im.putpixel(q,(163,60,47,255))
    for q in [(3,1),(5,2),(6,3)]:im.putpixel(q,(70,77,68,255))
    im.putpixel((6,4),(99,130,114,255))
    A.save(im,'pressure_gauge_16','references/diesel/current_gauge.png; Create brass palette','new 6x6 dial with 8x8 bezel on 16x16 canvas; pointer is separate geometry')
    parts=Image.new('RGBA',(16,16));d=ImageDraw.Draw(parts)
    for rect,color in [((0,0,3,3),(53,58,56,255)),((4,0,7,3),(203,185,135,255)),((8,0,11,3),(116,130,128,255)),((12,0,15,3),(193,87,63,255)),((0,4,3,7),(201,210,201,200))]:d.rectangle(rect,fill=color)
    A.save(parts,'mechanism_pixels_16','Create source sampled colors','solid texels for needle, pin, dark nozzle and steam preview')

def transform(m,group,axis,deg,origin):
    r=A.rot(axis,deg);o=np.array(origin)
    for e in m.elements:
        if e['uuid'] in m.groups.get(group,[]):
            e['vertices']={k:((np.array(v)-o)@r.T+o).tolist() for k,v in e['vertices'].items()}

def shift(m,group,delta):
    for e in m.elements:
        if e['uuid'] in m.groups.get(group,[]):e['vertices']={k:(np.array(v)+delta).tolist() for k,v in e['vertices'].items()}

def scale_group(m,group,scale,origin):
    for e in m.elements:
        if e['uuid'] in m.groups.get(group,[]):e['vertices']={k:((np.array(v)-origin)*scale+origin).tolist() for k,v in e['vertices'].items()}

def solid(m,name,a,b,group,pixel=(0,0)):
    m.box(name,a,b,'mechanism_pixels_16',group,{d:A.uvrect(*pixel,pixel[0]+1,pixel[1]+1) for d in E.DIRS})

def cropped(m,name,a,b,tex,group,uv=(0,0)):m.cropbox(name,a,b,A.ctex(tex),group,uv)

def build(name='08_frog_catcher',pressure=1,opened=0,tongue=.1,cog=0,trigger=0,animated=False):
    m=A.Model(name)
    # The current Air Crate material override is resolved through its native parent.
    offset=(4.16,6,-2.98)
    # Native inside-out duplicates are needed by single-sided MC rendering, but
    # would z-fight in a double-sided mesh viewer. Keep each surface only once.
    A.native(m,'block/creature_frogport_body',NAMES['jaw'],ns='aircrate',scale=.48,offset=offset,only=lambda i,e:i in [0,7,8,9])
    A.native(m,'block/creature_frogport_head',NAMES['head'],ns='aircrate',scale=.48,offset=offset,only=lambda i,e:i!=1)
    # The underside and the inner lower-jaw plane meet at y=10 natively.
    # Give only the upper inner plane a tiny clearance at the closed stop.
    for e in m.elements:
        if e['uuid'] in m.groups[NAMES['head']] and e['name'].endswith('_2'):
            e['vertices']={k:[v[0],v[1]+.018,v[2]] for k,v in e['vertices'].items()}
        if e['uuid'] in m.groups[NAMES['head']] and e['name'].endswith('_0'):
            # Clip the native 1px overlapping skirt against the closed jaw stop.
            # Interpolate UVs with the clipped edge instead of stretching the face.
            for f in e['faces'].values():
                for k in f['vertices']:
                    v=e['vertices'][k]
                    if v[1]>=10.82:continue
                    top=next((q for q in f['vertices'] if e['vertices'][q][1]>10.82 and abs(e['vertices'][q][0]-v[0])<1e-6 and abs(e['vertices'][q][2]-v[2])<1e-6),None)
                    if top:
                        a=(10.82-v[1])/(e['vertices'][top][1]-v[1]);f['uv'][k]=((1-a)*np.array(f['uv'][k])+a*np.array(f['uv'][top])).tolist()
                    v[1]=10.82
    m.origins[NAMES['head']]=H
    A.native(m,'block/creature_frogport_tongue',NAMES['tongue'],ns='aircrate',scale=.48,offset=(4.16,6.015,-2.98))
    m.origins[NAMES['tongue']]=H
    # Original backtank shell uniformly scaled and laid on its side, not elongated.
    mat=A.rot('x',90);off=np.array([8,11,8.9])-np.array([8,6,8])@mat.T*.85
    A.native(m,'block/copper_backtank/block',NAMES['tank'],matrix=mat,scale=.85,offset=off,only=lambda i,e:i<3,overrides={'create:block/copper_backtank':'copper_pressure_shell_32'})
    # Brass saddle replaces the removed backpack pad: an actual gun mounting part.
    for z in [4.60,10.90]:cropped(m,'气罐黄铜鞍座',(5.6,7.4,z),(10.4,8.5,z+1.2),'block/brass_casing',NAMES['body'],(0,0))
    # A dark andesite manifold overlaps both parts; there is no daylight seam.
    cropped(m,'蛙港气路连接颈',(5.15,8.0,3.40),(10.85,10.65,4.35),'block/andesite_casing',NAMES['body'],(0,0))
    # Receiver beams leave an actual opening around the spinning gear.
    for x in [5.4,9.5]:cropped(m,'黄铜承力梁',(x,7.9,3.4),(x+1.1,8.8,12.4),'block/brass_casing',NAMES['body'],(0,0))
    cropped(m,'安山前连接座',(5.3,6.8,2.7),(10.7,8.2,4.3),'block/andesite_casing',NAMES['body'],(0,0))
    cropped(m,'机匣后段',(5.4,6.0,8.0),(10.6,8.5,12.7),'block/brass_casing',NAMES['body'],(0,3))
    cropped(m,'齿轮下护轨',(5.4,4.6,4.0),(10.6,5.3,8.2),'block/andesite_casing',NAMES['body'],(0,0))
    # Slim tank cradles repeat a cropped steel edge, never a full casing face.
    for z in [4.60,12.40]:
        for x in [5.15,10.55]:cropped(m,'背罐侧箍',(x,8.15,z),(x+.3,13.55,z+.65),'block/andesite_casing',NAMES['body'],(0,1))
        cropped(m,'背罐上箍',(5.4,13.55,z),(10.6,13.85,z+.65),'block/andesite_casing',NAMES['body'],(0,0))
    # Tank tail is capped in brass with a short release valve (within cannon length).
    cropped(m,'黄铜尾盖',(5.4,8.4,14.1),(10.6,13.6,14.9),'block/brass_casing',NAMES['body'],(0,0))
    cropped(m,'泄压阀阀杆',(7.35,10.35,14.9),(8.65,11.65,16.15),'block/mechanical_press_pole',NAMES['valve'],(10,6))
    cropped(m,'泄压阀阀帽',(6.85,10.0,16.0),(9.15,12.0,16.6),'block/brass_casing',NAMES['valve'],(1,1))
    # The tail cap follows the tank forward as a unit.
    for e in m.elements:
        if e['name'] in ['黄铜尾盖','泄压阀阀杆','泄压阀阀帽']:
            e['vertices']={k:[v[0],v[1],v[2]-.40] for k,v in e['vertices'].items()}
    # Original potato cannon cog, rotated to face sideways in the open receiver.
    mat=A.rot('y',90);off=np.array(C)-np.array([8,8.5,8])@mat.T*.58
    A.native(m,'item/potato_cannon/cog',NAMES['cog'],matrix=mat,scale=.58,offset=off)
    m.origins[NAMES['cog']]=C
    solid(m,'齿轮轴',(4.4,6.3,5.6),(10.5,6.9,6.2),NAMES['body'],(8,0))
    # Backward-raked andesite grip. The brass trigger sits in front of it.
    gp=[8,6.5,11]
    cropped(m,'安山握把',(6.65,1.1,10.0),(9.35,6.5,12.2),'block/andesite_casing','倾斜握把',(0,1))
    cropped(m,'握把底盖',(6.5,.75,9.9),(9.5,1.45,12.35),'block/brass_casing','倾斜握把',(0,0))
    transform(m,'倾斜握把','x',-25,gp)
    cropped(m,'黄铜弧形扳机',(7.55,4.0,8.2),(8.45,6.1,8.85),'block/brass_casing',NAMES['trigger'],(1,1))
    cropped(m,'扳机指托',(7.55,3.85,8.65),(8.45,4.4,9.45),'block/brass_casing',NAMES['trigger'],(2,2))
    m.origins[NAMES['trigger']]=[8,6.1,8.5]
    # Pressure gauge faces the player-side flank. Face is exactly six pixels wide.
    cropped(m,'仪表连接颈',(4.0,9.5,7.7),(4.7,10.9,9.1),'block/mechanical_press_pole',NAMES['body'],(10,6))
    cropped(m,'仪表薄壳',(3.9,8.2,6.4),(4.25,12.2,10.4),'block/brass_casing',NAMES['body'],(0,0))
    m.box('6像素压力表盘',(3.88,8.2,6.4),(3.88,12.2,10.4),{'west':'pressure_gauge_16'},NAMES['body'],{'west':A.uvrect(0,0,8,8)})
    # Needle neutral points up. Positive X angle points toward the red/front side.
    pivot=[3.84,9.95,8.15];m.origins[NAMES['needle']]=pivot
    solid(m,'独立压力指针',(3.82,9.85,8.03),(3.86,11.2,8.27),NAMES['needle'])
    solid(m,'指针轴帽',(3.80,9.7,7.90),(3.88,10.2,8.40),NAMES['body'],(4,0))
    # Discrete exhaust nozzle on the far/rear side, pointing away from the eyes.
    cropped(m,'排气接头',(10.6,7.4,11.5),(12.0,8.6,12.7),'block/brass_casing',NAMES['vent'],(0,0))
    solid(m,'排气黑口',(12.01,7.65,11.75),(12.03,8.35,12.45),NAMES['vent'])
    transform(m,NAMES['head'],'x',opened,H)
    shift(m,NAMES['head'],np.array([0,0,-1.8*math.sin(math.radians(opened))]))
    scale_group(m,NAMES['tongue'],[1,1,tongue],np.array(H))
    transform(m,NAMES['cog'],'x',cog,C)
    transform(m,NAMES['trigger'],'x',trigger,m.origins[NAMES['trigger']])
    if not animated:transform(m,NAMES['needle'],'x',70-140*pressure,pivot)
    if animated:animate(m)
    p=m.save();d=json.loads(p.read_text(encoding='utf-8'));d['display']=DISPLAY
    d['credit']='Air Crate pneumatic frog catcher; source-native frog/backtank/cannon parts; see README.md'
    p.write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding='utf-8')
    return p

def animate(m):
    def animation(name,length,loop,channels):
        out={'uuid':A.uid(name),'name':name,'length':length,'loop':loop,'snapping':20,'override':False,'animators':{}}
        for g,seq in channels.items():
            keys=[]
            for channel,values in seq.items():
                for t,v in values:keys.append({'uuid':A.uid(name+g+channel+str(t)),'channel':channel,'time':t,'data_points':[dict(zip('xyz',map(str,v)))],'interpolation':'linear','color':-1})
            out['animators'][A.uid(m.name+'/'+g)]={'name':g,'type':'bone','keyframes':keys}
        return out
    head=[(0,[0,0,0]),(.12,[0,0,0]),(.23,[72,0,0]),(.43,[72,0,0]),(.65,[0,0,0]),(1.2,[0,0,0])]
    tong=[(0,[1,1,.1]),(.22,[1,1,.1]),(.29,[1,1,4.1]),(.36,[1,1,4.1]),(.53,[1,1,.1]),(1.2,[1,1,.1])]
    # Base tongue is full native size for independent runtime scaling.
    scales=1/.1
    scale_group(m,NAMES['tongue'],[1,1,scales],np.array(H))
    m.animations=[animation('capture_cycle',1.2,'loop',{
        NAMES['head']:{'rotation':head,'position':[(t,[0,0,-1.8*math.sin(math.radians(float(np.interp(t,[a for a,v in head],[v[0] for a,v in head]))))]) for t in [i*.025 for i in range(49)]]},NAMES['tongue']:{'scale':tong},
        NAMES['trigger']:{'rotation':[(0,[0,0,0]),(.12,[-16,0,0]),(.4,[-16,0,0]),(.65,[0,0,0]),(1.2,[0,0,0])]},
        NAMES['cog']:{'rotation':[(0,[0,0,0]),(.12,[0,0,0]),(.53,[360,0,0]),(.75,[540,0,0]),(1.2,[540,0,0])]},
        NAMES['valve']:{'position':[(0,[0,0,0]),(.45,[0,0,0]),(.52,[0,0,.22]),(.67,[0,0,0]),(1.2,[0,0,0])]}}),
        animation('pressure_full_to_empty',1,'hold',{NAMES['needle']:{'rotation':[(0,[-70,0,0]),(1,[70,0,0])]}})]

def bounds(p):
    d=json.loads(p.read_text(encoding='utf-8'));v=np.array([v for e in d['elements'] for v in e['vertices'].values()]);return v.min(0),v.max(0)

def main():
    prepare()
    for name,args in [('08_frog_catcher_idle',{}),('08_frog_catcher_firing',dict(opened=72,tongue=4.1,cog=125,trigger=-16)),('08_frog_catcher_low_pressure',dict(pressure=.1)),('08_frog_catcher_animated',dict(animated=True))]:build(name,**args)
    cannon=A.Model('reference_potato_cannon');A.native(cannon,'item/potato_cannon/item','Create原模型');A.native(cannon,'item/potato_cannon/cog','Create原齿轮');cannon.save()
    frog=A.Model('reference_creature_frogport')
    for k in ['body','head','tongue']:A.native(frog,'block/creature_frogport_'+k,'航空箱原蛙港',ns='aircrate')
    frog.save()
    tank=A.Model('reference_copper_backtank');A.native(tank,'block/copper_backtank/item','Create原气罐');tank.save()
    a,b=bounds(M/'08_frog_catcher_idle.bbmodel');c,d=bounds(M/'reference_potato_cannon.bbmodel')
    assert b[2]-a[2]<=d[2]-c[2]
    print('Length',round(b[2]-a[2],3),'vs native cannon',round(d[2]-c[2],3))
    checks={'idle_bounds':[a.tolist(),b.tolist()],'idle_length':float(b[2]-a[2]),'native_potato_cannon_length':float(d[2]-c[2]),'firstperson_display_equals_native':True,'grip_rake_degrees':25,'dial_face_pixels':6,'capacity_preview':8,'pressure_angle_degrees':'70 - 140 * clamp(air / maxAir, 0, 1)','warning_fraction':.25,'game_integrated':False,'backtank_imported_elements':[0,1,2],'backtank_excluded_pad_elements':[3,4],'back_pad_texture_region_removed':True}
    (O/'validation.json').write_text(json.dumps(checks,indent=2),encoding='utf-8')
    manifest=[]
    for p in T.glob('*.png'):
        item=next((a for a in A.AUD if a['output']==p.name),{})
        manifest.append({**item,'file':p.name,'size':list(Image.open(p).size),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
    (O/'SOURCE_MANIFEST.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    for name in ['idle','firing','low_pressure']:
        E.render(M/f'08_frog_catcher_{name}.bbmodel',(1200,850),(-1,.7,-1.1)).save(P/f'{name}.png')
    print('Models and source-preserving textures built.')

if __name__=='__main__':main()

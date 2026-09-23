"""Create-native art revision. No image generation. All transforms are explicit.
Preserves upstream pixels/UVs where possible; local additions are source-audited.
"""
from pathlib import Path
import base64,copy,hashlib,io,json,math,uuid,colorsys
import numpy as np
from PIL import Image,ImageDraw,ImageFont
import mesh_engine as E

O=Path(__file__).resolve().parent;R=O/'references';T=O/'textures';M=O/'models';S=O/'sheets';P=O/'renders';N=O/'native_models'
for p in [T,M,S,P,N]:p.mkdir(exist_ok=True)
AUD=[]
def uid(s):return str(uuid.uuid5(uuid.NAMESPACE_URL,'aircrate-v2/'+s))
def texid(path):return str(path).replace('\\','/').replace('/','__')
def texture(ns,path):
    name=ns+'__'+texid(path);dest=T/(name+'.png');source=R/ns/'textures'/(path+'.png')
    if not dest.exists():dest.write_bytes(source.read_bytes())
    if not any(a['output']==dest.name for a in AUD):
        AUD.append({'output':dest.name,'source':str(source.relative_to(O)),'operation':'unaltered copy','sha256':hashlib.sha256(dest.read_bytes()).hexdigest()})
    return name
def img(name):return Image.open(T/(name+'.png')).convert('RGBA')
def save(im,name,source,operation):
    im.save(T/(name+'.png'));AUD.append({'output':name+'.png','source':source,'operation':operation});return name
def ctex(s):return texture('create',s)
def mtex(s):return texture('minecraft',s)
def uvrect(u,v,U,V):return [(float(u),float(v)),(float(U),float(v)),(float(U),float(V)),(float(u),float(V))]
def rot(axis,deg):
    t=math.radians(deg);c=math.cos(t);s=math.sin(t)
    return np.array({'x':[[1,0,0],[0,c,-s],[0,s,c]],'y':[[c,0,s],[0,1,0],[-s,0,c]],'z':[[c,-s,0],[s,c,0],[0,0,1]]}[axis],float)
def load_native(ns,path):
    p=R/ns/'models'/(path+'.json');d=json.loads(p.read_text(encoding='utf-8'))
    parent=d.get('parent','')
    if parent and ':' in parent:
        pn,pp=parent.split(':',1)
        if (R/pn/'models'/(pp+'.json')).exists():
            base=load_native(pn,pp);base['textures']={**base.get('textures',{}),**d.get('textures',{})};base.update({k:v for k,v in d.items() if k!='textures'});d=base
    return d
def resolve(ref,table):
    for i in range(20):
        if not ref.startswith('#'):return ref
        ref=table[ref[1:]]
    raise ValueError('cyclic texture')
def native(m,path,group,ns='create',matrix=None,offset=(0,0,0),scale=1,only=None,overrides=None):
    d=load_native(ns,path);count=0
    for i,e in enumerate(d.get('elements',[])):
        if only is not None and not only(i,e):continue
        polys=[]
        for direction,f in e.get('faces',{}).items():
            pts=np.array(E.verts(e['from'],e['to'])[direction],float)
            r=e.get('rotation',{});angle=r.get('angle',0);origin=np.array(r.get('origin',[8,8,8]),float)
            if angle:
                pts-=origin
                if r.get('rescale'):
                    for ax in range(3):
                        if 'xyz'[ax]!=r['axis']:pts[:,ax]/=math.cos(math.radians(angle))
                pts=pts@rot(r['axis'],angle).T+origin
            if matrix is not None:pts=pts@np.asarray(matrix).T
            pts=pts*scale+np.array(offset)
            ref=resolve(f['texture'],d['textures']);rn,rp=ref.split(':',1) if ':' in ref else (ns,ref)
            t=(overrides or {}).get(ref) or texture(rn,rp);im=img(t)
            u,v,U,V=f.get('uv',[0,0,16,16]);uv=uvrect(u*im.width/16,v*im.height/16,U*im.width/16,V*im.height/16);turns=(f.get('rotation',0)//90)%4;uv=uv[turns:]+uv[:turns]
            polys.append((pts.tolist(),t,uv))
        m.mesh(e.get('name',path.split('/')[-1])+f'_{i}',polys,group);count+=1
    return count

class Model(E.Model):
    def __init__(self,name):super().__init__(name);self.origins={};self.animations=[]
    def save(self):
        p=super().save();d=json.loads(p.read_text(encoding='utf-8'))
        for g in d['outliner']:
            g['origin']=self.origins.get(g['name'],[0,0,0]);g['uuid']=uid(self.name+'/'+g['name'])
        d['animations']=self.animations;d['credit']='Create-derived engineering study; see SOURCE_MANIFEST.json';p.write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding='utf-8');return p
    def cropbox(self,name,a,b,texture,group,origin_uv=(0,0)):
        # Texel density 1 pixel / model unit. Never stretch a full face over a strip.
        dx,dy,dz=np.abs(np.array(b)-np.array(a));u,v=origin_uv
        dims={'north':(dx,dy),'south':(dx,dy),'east':(dz,dy),'west':(dz,dy),'up':(dx,dz),'down':(dx,dz)}
        self.box(name,a,b,texture,group,{d:uvrect(u,v,u+w,v+h) for d,(w,h) in dims.items()})

def prepare():
    for p in (R/'create/textures').rglob('*.png'):texture('create',p.relative_to(R/'create/textures').with_suffix('').as_posix())
    for p in (R/'minecraft/textures').rglob('*.png'):texture('minecraft',p.relative_to(R/'minecraft/textures').with_suffix('').as_posix())
    for p in (R/'createdeco').glob('*.png'):save(Image.open(p).convert('RGBA'),'createdeco__'+p.stem,str(p.relative_to(O)),'unaltered copy')
    # Brass ladder sampled from actual Create brass casing, not a new yellow paint.
    brass=img(ctex('block/brass_casing'));ramp=sorted({p[:3] for p in brass.get_flattened_data() if p[0]>p[2]*1.35 and p[0]>110 and p[3]},key=lambda p:sum(p))
    masks=[]
    for p in (R/'fluidlogistics').glob('packager_*.png'):
        src=img(ctex('block/'+p.stem));fluid=Image.open(p).convert('RGBA');out=src.copy();mask=Image.new('RGBA',src.size)
        count=0
        for y in range(src.height):
            for x in range(src.width):
                a=src.getpixel((x,y));b=fluid.getpixel((x,y));h,s,v=colorsys.rgb_to_hsv(*(i/255 for i in b[:3]))
                if a!=b and b[3] and s>.12 and (h*360<40 or h*360>350):
                    val=max(a[:3])/255;rank=min(len(ramp)-1,max(0,round((val-.17)/.70*(len(ramp)-1))))
                    out.putpixel((x,y),ramp[rank]+(a[3],));mask.putpixel((x,y),(255,255,255,255));count+=1
        n=save(out,'brass__'+p.stem,['Create '+p.stem,'Fluid Logistics '+p.stem],'only copper-difference-mask pixels recolored with actual brass casing palette')
        mask.save(T/('mask__'+p.stem+'.png'))
        masks.append({'texture':p.name,'mask_pixels':count,'unchanged_outside_mask':all(out.getpixel((x,y))==src.getpixel((x,y)) for y in range(src.height) for x in range(src.width) if mask.getpixel((x,y))[3]==0)})
    (O/'mask_audit.json').write_text(json.dumps(masks,indent=2))
    # Original filter roll edges and blank parchment remain, only marks change.
    fil=img(ctex('item/filter'));d=ImageDraw.Draw(fil)
    for y,width in [(4,6),(6,4)]:d.line((5,y,5+width,y),fill=(132,112,87,255))
    for x,y in [(6,9),(8,8),(10,9)]:d.rectangle((x,y,x,y+1),fill=(63,68,62,255))
    d.polygon([(8,10),(9,10),(10,12),(7,12),(7,11)],fill=(63,68,62,255))
    save(fil,'creature_list_filter','create:item/filter','content-area edits only; original roll silhouette and side rods unchanged')
    # Mix actual vanilla wheat pixels into a tied grain bundle at native resolution.
    wheat=img(mtex('item/wheat'));feed=Image.new('RGBA',(16,16))
    feed.alpha_composite(wheat,(-2,0));feed.alpha_composite(wheat,(2,1));feed.alpha_composite(wheat,(0,0));d=ImageDraw.Draw(feed)
    apple=img(mtex('item/apple'));red=sorted({p for p in apple.get_flattened_data() if p[0]>p[1]*1.6 and p[0]>80 and p[3]},key=lambda p:sum(p[:3]))
    for x,y in [(2,9),(11,11)]:
        d.rectangle((x,y,x+2,y+2),fill=red[len(red)//3]);d.line((x,y,x+1,y),fill=red[-2]);feed.putpixel((x,y+2),red[0])
    d.line([(3,10),(7,11),(11,10)],fill=(91,68,41,255),width=2);d.line([(3,10),(7,10),(11,9)],fill=(179,149,95,255));d.rectangle((7,10,8,11),fill=(217,186,128,255));d.line([(7,12),(6,14)],fill=(151,118,74,255))
    for x,y in [(5,8),(9,12),(12,7)]:feed.putpixel((x,y),(61,65,34,255))
    save(feed,'mixed_feed','minecraft:item/wheat + apple palette','wheat sprite layered without resampling; rope/apple/seed pixels manually authored')
    # Glass rim based on vault steel, central alpha contains restrained tank highlights.
    base=img(ctex('block/vault/vault_side_small'));g=base.copy();g.paste((0,0,0,0),(0,2,16,14));g.putpixel((2,4),(165,193,195,95));g.putpixel((3,3),(190,217,214,95))
    save(g,'crate_glass_edge','create:block/vault/vault_side_small','only inner panel removed; original top/bottom rails kept')
    center=Image.new('RGBA',(16,16));center.putpixel((2,4),(165,193,195,65));center.putpixel((3,3),(190,217,214,65));save(center,'glass_clear','Create tank glass palette','transparent pane with two restrained reflection pixels')
    # Windowed parcel uses 64px native atlas. No resampling or recoloring cardboard.
    atlas=img(ctex('item/package/cardboard'));front=atlas.crop((36,25,48,37));tile=Image.new('RGBA',(16,16));tile.paste(front,(2,2));d=ImageDraw.Draw(tile)
    d.rectangle((3,4,12,12),fill=(77,84,79,255));d.rectangle((4,5,11,11),fill=(0,0,0,0));d.line((4,4,11,4),fill=(163,166,147,255));d.line((3,5,3,11),fill=(129,141,131,255));d.line((4,7,6,5),fill=(171,205,206,95))
    save(tile,'parcel_window_16','create:item/package/cardboard region [36,25,48,37]','native 12px face centered without resampling; front opening and rim only')
    # Nursing badge is a small label, never the whole port surface.
    badge=Image.new('RGBA',(16,16));d=ImageDraw.Draw(badge);d.rectangle((0,0,7,5),fill=(221,210,179,255));d.line((0,5,7,5),fill=(113,98,77,255));d.polygon([(2,1),(1,3),(2,4),(3,3)],fill=(104,124,132,255));d.line((5,1,6,2),fill=(132,101,61,255));d.line((4,2,5,3),fill=(75,71,58,255));save(badge,'care_badge','new 8x6 symbol on 16px canvas','milk drop and brush; label uses original parchment/metal palette')
    glow=img(mtex('block/sculk_sensor_side'));arr=np.array(glow);sel=(arr[:,:,1]>arr[:,:,0]*1.4)&(arr[:,:,2]>arr[:,:,0]*1.3)&(arr[:,:,1]>45);arr[sel,:3]=np.minimum(arr[sel,:3].astype(int)*1.35+8,255).astype('uint8');save(Image.fromarray(arr),'sculk_active','minecraft:block/sculk_sensor_side','cyan vein pixels only brightened for active concept state')

def packager(state='unpowered',vertical=False):
    name='04_packager_'+('vertical_' if vertical else '')+state;m=Model(name)
    overrides={'create:block/'+p.stem:'brass__'+p.stem for p in (R/'fluidlogistics').glob('*.png')}
    overrides['create:block/packager_horizontal_unpowered']='brass__packager_horizontal_'+state
    native(m,'block/packager/item','Create_packager_original',overrides=overrides)
    if vertical:
        matrix=rot('x',90);center=np.array([8,8,8])
        for e in m.elements:
            for k,v in e['vertices'].items():e['vertices'][k]=((np.array(v)-center)@matrix.T+center).tolist()
    p=m.save()
    # Exact vanilla-model artifact: only texture refs are changed. No invented casing.
    data=load_native('create','block/packager/item');original=copy.deepcopy(data['elements'])
    data['textures']={k:('aircrate_concept:'+overrides[v] if v in overrides else v) for k,v in data['textures'].items()}
    if not vertical:(N/(name+'.json')).write_text(json.dumps(data,indent=2))
    assert data['elements']==original
    return p

def quad(m,name,points,tex,uv,group):m.mesh(name,[(points,tex,uv)],group)
def crate(mode='glass',length=48):
    m=Model('01_crate_'+mode);side=ctex('block/vault/vault_side_small');top=ctex('block/vault/vault_top_small');bottom=ctex('block/vault/vault_bottom_small');front=ctex('block/vault/vault_front_small');bars='createdeco__industrial_iron_bars'
    # Long axis is Z. Ends fixed; exactly one fan assembly on the front end.
    for label,z,direction in [('前端_通风',0,'north'),('后端_封闭',length,'south')]:
        pts=E.verts([0,0,z],[16,16,z])[direction];quad(m,label,pts,front,uvrect(0,0,16,16),'固定端面')
    # Four long faces; every tile is exactly 16 model units and 16 pixels.
    for z in range(0,length,16):
        for direction in ['west','east','up','down']:
            pts=E.verts([0,0,z],[16,16,z+16])[direction]
            if mode=='closed':tex=top if direction=='up' else bottom if direction=='down' else side
            else:
                im=img(side if direction in ['west','east'] else top);im.paste((0,0,0,0),(0,2,16,14))
                if mode=='bars':
                    # Use actual Create Deco pixels inside original vault edge rails.
                    b=img(bars);im.alpha_composite(b.crop((0,2,16,14)),(0,2))
                else:im.alpha_composite(img('glass_clear'))
                tex=save(im,'crate_'+mode+'_'+direction,str(side if direction in ['west','east'] else top),'vault rails retained; transparent center' if mode=='glass' else 'Create Deco original bars inside vault rails')
            # For top/bottom rotate UV to put rails on X boundaries, not cross seams.
            uv=uvrect(0,0,16,16)
            if direction in ['up','down']:uv=uv[1:]+uv[:1]
            quad(m,f'{direction}_{z}',pts,tex,uv,'四个长面_'+mode)
    # Native encased-fan face inset in one end. Remove full end face center behind fan.
    end=img(front);end.paste((0,0,0,0),(3,3,13,13));vent=save(end,'crate_vent_end',front,'center removed for one native fan insert')
    first=m.elements[0];first['faces']['f0']['texture']=m.tex(vent)
    native(m,'block/encased_fan/item','唯一通风口',scale=.625,offset=(3,3,-.5))
    # Small steel fastening strips are actual cropped pixels, not repeated full panels.
    metal='createdeco__industrial_iron_plate_metal'
    for z in [0,length-1]:
        for x in [0,15]:m.cropbox('工业铁角条',(x,0,z),(x+1,16,z+1),metal,'边缘加强',origin_uv=(0,0))
    p=m.save();return p

def hatch():
    m=Model('02_interaction_hatch_patch');vault=ctex('block/vault/vault_front_small');metal='createdeco__industrial_iron_plate_metal'
    # Only 2 units deep, 12 units wide; wall itself is excluded.
    m.cropbox('薄安装背板',(2,2,14),(14,14,16),metal,'贴片底座',(2,2))
    m.box('铆接面板',(2,2,13.75),(14,14,14),{'north':vault},'贴片底座',{'north':uvrect(2,2,14,14)})
    native(m,'block/packager/hatch_closed','护理操作口',scale=.5,offset=(4,5,9.5))
    m.box('奶滴毛刷标签',(6,2.25,13.6),(10,5.25,13.6),{'north':'care_badge'},'护理标识',{'north':uvrect(0,0,8,6)})
    return m.save()

def resonator(active=False):
    m=Model('03_resonator_'+('active' if active else 'idle'));ande=ctex('block/andesite_casing');shaft=ctex('block/mechanical_press_pole')
    m.box('安山机壳底座',(0,0,0),(16,6,16),{d:ande for d in E.DIRS},'机械底座',{d:uvrect(0,10,16,16) if d not in ['up','down'] else uvrect(0,0,16,16) for d in E.DIRS})
    m.cropbox('传动轴座',(5,1,-2),(11,5,1),shaft,'机械底座',(10,6))
    sc='sculk_active' if active else mtex('block/sculk_sensor_side')
    m.box('幽匿环',(0,6,0),(16,10,16),{'north':sc,'south':sc,'west':sc,'east':sc,'up':mtex('block/calibrated_sculk_sensor_top'),'down':ande},'幽匿感应环',{d:uvrect(0,8,16,12) if d not in ['up','down'] else uvrect(0,0,16,16) for d in E.DIRS})
    native(m,'block/calibrated_sculk_sensor','原版紫晶交叉片',ns='minecraft',offset=(0,2,0),only=lambda i,e:i>=5)
    brass=ctex('block/brass_casing')
    for x,z in [(1,1),(12,1),(1,12),(12,12)]:m.cropbox('黄铜晶座压片',(x,9,z),(x+3,11,z+3),brass,'晶座压片',(1,1))
    return m.save()

def parcel(show=False):
    m=Model('05_parcel'+('_display' if show else '_empty'));d=load_native('create','item/package/cardboard_12x12');e=d['elements'][0];atlas=ctex('item/package/cardboard')
    # Preserve 12x12 native box dimensions and atlas UVs; front is the sole redesign.
    native(m,'item/package/cardboard_12x12','Create纸箱主体')
    m.elements[0]['faces'].pop('f0')
    quad(m,'前窗',E.verts([2,0,2],[14,12,2])['north'],'parcel_window_16',uvrect(2,2,14,14),'观察窗')
    if show:
        # Preserve the separate illustrative animal of the accepted previous parcel.
        old=R/'previous_parcel_display.bbmodel';prior=json.loads(old.read_text(encoding='utf-8'));group=next(g for g in prior['outliner'] if g['name']=='示意生物_不烘焙进贴图')
        for el in prior['elements']:
            if el['uuid'] not in group['children']:continue
            polys=[]
            for f in el['faces'].values():
                oldt=prior['textures'][f['texture']];name='display_'+Path(oldt['name']).stem
                if not (T/(name+'.png')).exists():(T/(name+'.png')).write_bytes(base64.b64decode(oldt['source'].split(',')[1]))
                pts=[(np.array(el['vertices'][v])*0.75+np.array([2,-.3,2])).tolist() for v in f['vertices']]
                polys.append((pts,name,[f['uv'][v] for v in f['vertices']]))
            m.mesh(el['name'],polys,'示意生物_非贴图')
    return m.save()

def flat(name,tex):
    m=Model(name);im=img(tex)
    for y in range(16):
        for x in range(16):
            if im.getpixel((x,y))[3]:m.box(f'pixel_{x}_{y}',(x,15-y,7.75),(x+1,16-y,8.25),tex,'原尺寸薄片',{d:uvrect(x,y,x+1,y+1) for d in E.DIRS})
    return m.save()

def catcher(extension=0,opened=False,name=None):
    m=Model(name or ('08_catcher_extended' if extension else '08_catcher_stowed'))
    native(m,'item/extendo_grip/item','握持机构',only=lambda i,e:i<4)
    # Two crossed rod stages, actual native rod geometry and UV; no rectangular gun sprite.
    angle=38+extension*28;span=13*math.sin(math.radians(angle));yhalf=6.5*math.cos(math.radians(angle));basez=5
    for stage in range(2):
        zcenter=basez-(stage+.5)*span
        for sign in [-1,1]:
            group=f'连杆_{stage}_{sign}';mat=rot('x',sign*angle)
            # Source rod centered at (8,5.5,0); rotate so each spans the Z axis.
            offset=np.array([8+(sign*.8),9,zcenter])-np.array([8,5.5,0])@mat.T
            native(m,'item/extendo_grip/thin_long',group,matrix=mat,offset=offset)
            m.origins[group]=[8+sign*.8,9,zcenter]
    headz=basez-2*span-1.5
    # Native packager head, uniformly scaled to wearable size, preserved texture layout.
    ovr={'create:block/'+p.stem:'brass__'+p.stem for p in (R/'fluidlogistics').glob('*.png')}
    if opened:ovr['create:block/packager_iris_closed']='brass__packager_iris_open'
    # Item packager iris faces +Z, so rotate to -Z for the capture direction.
    headmat=rot('y',180);factor=.5;head_offset=np.array([12,5,headz+8])
    native(m,'block/packager/item','捕获头',matrix=headmat,offset=head_offset,scale=factor,overrides=ovr,only=lambda i,e:i!=2)
    m.origins['捕获头']=[8,9,headz+4]
    iris_group='捕获口_开启' if opened else '捕获口_闭合'
    native(m,'block/packager/item',iris_group,matrix=headmat,offset=head_offset,scale=factor,overrides=ovr,only=lambda i,e:i==2)
    m.origins[iris_group]=[8,9,headz-.25]
    if name=='08_catcher_animated':
        ovr['create:block/packager_iris_closed']='brass__packager_iris_open'
        native(m,'block/packager/item','捕获口_开启',matrix=headmat,offset=head_offset+np.array([0,0,.002]),scale=factor,overrides=ovr,only=lambda i,e:i==2)
        m.origins['捕获口_开启']=[8,9,headz-.25]
    # A pair of mechanical gripper fingers frame the iris, using native press metal.
    pole=ctex('block/mechanical_press_pole')
    for sign in [-1,1]:
        g='捕获爪_'+str(sign);x=8+sign*(5.4 if opened else 4.3)
        m.cropbox('夹持翼',(x-.6,6,headz-.8),(x+.6,12,headz+1.2),pole,g,(10,6));m.origins[g]=[x,9,headz+1]
    return m

def attach_animation(m):
    # Explicit Blockbench channels: extension, linkage rotation, capture fingers, return.
    anim={'uuid':uid('capture_cycle'),'name':'capture_cycle','loop':'loop','override':False,'length':1.6,'snapping':20,'animators':{}}
    # Bake the trigonometric extension every 0.05 s to keep rod ends connected.
    times=sorted(set([i/20 for i in range(33)]+[.22,.65,.85,1.2]))
    keys=[(t,float(np.interp(t,[0,.22,.65,.85,1.2,1.6],[0,0,1,1,0,0]))) for t in times]
    initial=38;end=66;s0=13*math.sin(math.radians(initial));s1=13*math.sin(math.radians(end));delta=s1-s0
    for group in m.groups:
        frames=[]
        def key(t,ch,vals):
            frames.append({'channel':ch,'data_points':[dict(zip(['x','y','z'],[str(v) for v in vals]))],'uuid':uid(group+str(t)+ch),'time':t,'color':-1,'interpolation':'linear'})
        for t,a in keys:
            travel=13*math.sin(math.radians(initial+(end-initial)*a))-s0
            if group.startswith('连杆_'):
                _,stage,sign=group.split('_');key(t,'rotation',[int(sign)*28*a,0,0]);key(t,'position',[0,0,-(int(stage)+.5)*travel])
            elif group=='捕获头' or group.startswith('捕获口_'):
                key(t,'position',[0,0,-2*travel])
                if group.startswith('捕获口_'):
                    is_open=.3<=t<.85;visible=is_open if group.endswith('开启') else not is_open
                    key(t,'scale',[1 if visible else 0]*3);frames[-1]['interpolation']='step'
            elif group.startswith('捕获爪_'):
                sign=int(group.split('_')[-1]);spread=1.1*float(np.interp(t,[0,.22,.45,.7,.85,1.6],[0,0,1,1,0,0]));key(t,'position',[sign*spread,0,-2*travel])
        if frames:anim['animators'][uid(m.name+'/'+group)]={'name':group,'type':'bone','keyframes':frames}
    m.animations=[anim]

def build_models():
    paths=[crate(k) for k in ['closed','glass','bars']]+[hatch(),resonator(False),resonator(True)]
    paths += [packager(k) for k in ['unpowered','powered','linked']]+[packager('unpowered',True)]
    paths += [parcel(False),parcel(True),flat('06_mixed_feed','mixed_feed'),flat('07_creature_list_filter','creature_list_filter')]
    m=catcher(name='08_catcher_animated');attach_animation(m);paths.append(m.save());paths.append(catcher(1,True,'08_catcher_extended').save());paths.append(catcher(0,False,'08_catcher_stowed').save())
    return paths

F='C:/Windows/Fonts/msyh.ttc';BG='#182024';PANEL='#202C31';EDGE='#465055';FG='#DDDCD4';SUB='#A3ADA9';ACC='#D5B574'
def font(n):return ImageFont.truetype(F,n)
def text(im,xy,s,size=20,c=FG):ImageDraw.Draw(im).text(xy,s,font=font(size),fill=c)
def box(im,b,title):ImageDraw.Draw(im).rounded_rectangle(b,8,fill=PANEL,outline=EDGE,width=2);text(im,(b[0]+20,b[1]+12),title,21)
def paste(im,v,b):
    x,y,w,h=b;v=v.copy();v.thumbnail((w,h),Image.Resampling.NEAREST);im.alpha_composite(v,(int(x+(w-v.width)/2),int(y+(h-v.height)/2)))
def checker(im,x,y,w,h,k=12):
    d=ImageDraw.Draw(im)
    for a in range(0,w,k):
        for b in range(0,h,k):d.rectangle((x+a,y+b,x+min(w-1,a+k-1),y+min(h-1,b+k-1)),fill=('#354044' if (a//k+b//k)%2 else '#2A3438'))
def thumb(im,name,x,y,maxs=144):
    a=img(name);k=max(1,maxs//max(a.size));v=a.resize((a.width*k,a.height*k),Image.Resampling.NEAREST);checker(im,x,y,v.width,v.height);im.alpha_composite(v,(x,y));return v.size
def render(name,size=(650,500),camera=(1,.65,-1),ortho=False):
    return E.render(M/(name+'.bbmodel'),size,camera,ortho)

ASSETS=[
 ('01','航空箱','AVIATION CRATE','01_crate_glass',[(ctex('block/vault/vault_side_small'),'保险库原侧板'),('crate_glass_west','玻璃模式'),('crate_bars_west','栏杆模式'),('crate_vent_end','端面通风框'),('createdeco__industrial_iron_bars','Deco 原栏杆'),(ctex('block/fan_blades'),'Create 原扇叶')],['长轴 Z：两个固定端面；仅四个长面切换','扳手：封闭 → 玻璃 → 栅栏 → 封闭','整个结构仅前端 1 个通风口，不按方块重复'],[('01_crate_closed','封闭'),('01_crate_glass','玻璃'),('01_crate_bars','栅栏')]),
 ('02','交互舱口','INTERACTION PATCH','02_interaction_hatch_patch',[(ctex('block/vault/vault_front_small'),'保险库面板'),('createdeco__industrial_iron_plate_metal','工业铁板'),(ctex('block/packager_iris_closed'),'原版机械口'),('care_badge','护理标牌')],['贴片尺寸 12×12，安装背板厚度 2 / 16 格','保险库板材 + 原机械口；小标牌提示护理','安装面朝箱壁，操作面朝玩家 / 机械手'],[]),
 ('03','回响激发器','ECHO RESONATOR','03_resonator_idle',[(ctex('block/andesite_casing'),'原安山机壳'),(mtex('block/sculk_sensor_side'),'原幽匿侧面'),(mtex('block/calibrated_sculk_sensor_top'),'原校频顶面'),(mtex('block/calibrated_sculk_sensor_amethyst'),'原版紫晶片'),('sculk_active','激活脉络'),(ctex('block/mechanical_press_pole'),'原传动杆')],['原安山机壳、幽匿和校频水晶分区组合','晶体采用原版交叉面片结构，保留像素轮廓','激活只改变脉络亮度，未添加模糊光晕'],[('03_resonator_idle','未激活'),('03_resonator_active','激活')]),
 ('04','生物打包机','CREATURE PACKAGER','04_packager_unpowered',[('brass__packager_frame','黄铜框图集'),('brass__packager_details','原机构细节'),('brass__packager_horizontal_unpowered','原侧面图集'),('brass__packager_iris_closed','原封口虹膜'),('mask__packager_frame','换色掩膜')],['直接导入 Create 原 item 模型，保留全部机构','只在 Fluid Logistics 对照掩膜内替换黄铜色','充能 / 联动复用原图集；朝下为姿态示意'],[('04_packager_unpowered','基础'),('04_packager_powered','充能'),('04_packager_vertical_unpowered','朝下示意')]),
 ('05','生物包裹','CREATURE PARCEL','05_parcel_display',[('parcel_window_16','前窗 16px 源稿'),(ctex('item/package/cardboard'),'原纸箱 64px 图集'),(ctex('item/package/cardboard_particle'),'原纸板颜色')],['沿用大观察窗思路，壳体回归 Create 原纸箱','保留原 12×12×12 模型比例与背/侧/顶底 UV','示意羊为独立预览层；空窗 PNG 没有动物'],[('05_parcel_empty','空窗结构'),('05_parcel_display','独立生物层')]),
 ('06','混合饲料','MIXED FEED','06_mixed_feed',[('mixed_feed','混合饲料'),(mtex('item/wheat'),'原版小麦'),(mtex('item/apple'),'苹果配色来源')],['原版小麦像素组合，保留穗形和原本明暗','麻绳捆扎；苹果碎块与深色种子点缀','单张 16×16 透明物品 PNG，无碗无容器'],[]),
 ('07','生物过滤器','CREATURE LIST FILTER','07_creature_list_filter',[(ctex('item/filter'),'Create 原列表过滤器'),('creature_list_filter','生物清单变体'),(ctex('item/attribute_filter'),'属性过滤器对照')],['保留原列表过滤器两侧轴杆、纸张及轮廓','只改中央清单内容：短条目 + 生物爪印','没有改成金框卡片；边缘原像素保持一致'],[]),
 ('08','生物收纳器','EXTENDING CREATURE CATCHER','08_catcher_stowed',[(ctex('item/extendo_grip'),'原伸缩机械手'),(ctex('block/mechanical_press_pole'),'原金属传动件'),('brass__packager_iris_open','捕获口开启'),('brass__packager_iris_closed','封口虹膜')],['三维握把与双级剪叉连杆来自 Extendo Grip','前端小型打包头 + 夹持翼，伸出后捕获回缩','附 1.6 秒工作动画与分组；不是平面挤出物品'],[('08_catcher_stowed','收拢'),('08_catcher_extended','伸出 / 张口')])]

def sheets():
    labels=['正面','背面','左侧','右侧','顶部','底部'];cams=[(0,0,-1),(0,0,1),(-1,0,0),(1,0,0),(0,1,0),(0,-1,0)]
    paths=[]
    for num,cn,en,name,tiles,notes,variants in ASSETS:
        print('Sheet',name,flush=True);im=Image.new('RGBA',(1920,1280),BG)
        text(im,(38,22),num,46,ACC);text(im,(130,20),cn,38);text(im,(132,74),en+' / CREATE MATERIAL STUDY 02',17,SUB);text(im,(1460,30),'原材质 · 原比例 · 结构重制',22,ACC);text(im,(1460,72),'SCRIPT + BLOCKBENCH',16,SUB)
        box(im,(28,123,900,730),'模型预览  /  保存模型直接渲染');box(im,(920,123,1892,730),'六面结构视图' if num not in ['06','07'] else '原稿与原版对照')
        cam=(1,.55,1) if num=='04' else (1,.65,-1)
        hero=render(name,(800,525),cam);hero.save(P/(name+'_hero.png'));paste(im,hero,(55,180,817,525))
        if num not in ['06','07']:
            for i,(camera,label) in enumerate(zip(cams,labels)):
                if num=='04' and i<2:camera=tuple(-q for q in camera)
                x=942+(i%3)*312;y=183+(i//3)*254;v=render(name,(272,205),camera,True);v.save(P/(name+'_'+str(i)+'.png'));paste(im,v,(x,y,290,208));text(im,(x+114,y+215),label,18,SUB)
        else:
            ta=tiles[0][0] if num=='06' else tiles[1][0];tb=tiles[1][0] if num=='06' else tiles[0][0]
            for j,(tn,lab) in enumerate([(ta,'生物主题改稿'),(tb,'实际原材质参考')]):
                x=988+j*450;thumb(im,tn,x,224,352);text(im,(x+90,605),lab,21,SUB)
        box(im,(28,750,1275,1047),'源材质与 UV 参考  /  按原像素放大，图集不强行缩成 16px')
        tw=min(195,1190//len(tiles))
        for i,(tn,lab) in enumerate(tiles):
            x=54+i*tw;thumb(im,tn,x,815,144);text(im,(x,970),lab,15,SUB);a=img(tn);text(im,(x,995),str(a.width)+'×'+str(a.height),14,ACC)
        box(im,(1295,750,1892,1047),'工作状态 / 设计关系')
        if variants:
            w=560//len(variants)
            for j,(vn,lab) in enumerate(variants):
                v=render(vn,(w-8,185),cam);paste(im,v,(1310+j*w,800,w,185));text(im,(1327+j*w,1000),lab,17,SUB)
        else:
            for j,note in enumerate(notes):
                # Fit prose naturally into fixed-width annotation panel.
                for k in range(0,len(note),20):text(im,(1318,815+j*65+(k//20)*26),note[k:k+20],18,SUB)
        box(im,(28,1067,1892,1250),'设计约束与原材质色阶')
        # Palette sampled from textures actually used, not an invented palette banner.
        palette=[]
        for tn,lab in tiles[:3]:
            colors=img(tn).getcolors(1000000) or [];colors=sorted(colors,reverse=True)
            palette.extend([c for n,c in colors if c[3]>230][:4])
        for i,c in enumerate(palette[:12]):
            x=57+i*67;ImageDraw.Draw(im).rectangle((x,1130,x+46,1170),fill=c);text(im,(x,1180),'#%02X%02X%02X'%c[:3],10,SUB)
        for i,n in enumerate(notes):text(im,(930,1115+i*35),n,21,SUB)
        p=S/(num+'_'+en.lower().replace(' ','_')+'.png');im.convert('RGB').save(p);paths.append(p)
    contact=Image.new('RGB',(1920,2560),BG)
    for i,p in enumerate(paths):contact.paste(Image.open(p).resize((960,640),Image.Resampling.LANCZOS),((i%2)*960,(i//2)*640))
    contact.save(O/'00_overview.png')

def animation_preview():
    frames=[]
    for a in [0,.15,.35,.6,.85,1,1,1,.85,.6,.35,.15,0,0]:
        m=catcher(a,a>.3,'08_animation_frame');m.save();v=E.render(M/(m.name+'.bbmodel'),(900,650),(1,.45,-1),frame_bounds=E.verts([1,0,-23],[15,16,17])['north']+E.verts([1,0,-23],[15,16,17])['south']);bg=Image.new('RGBA',v.size,BG);bg.alpha_composite(v);text(bg,(28,20),'伸缩结构预演  /  固定相机',24,ACC);frames.append(bg.convert('RGB'))
    frames[0].save(O/'08_catcher_structure_motion.gif',save_all=True,append_images=frames[1:],duration=115,loop=0)
    (M/'08_animation_frame.bbmodel').unlink()

def validate():
    models=[]
    for p in M.glob('*.bbmodel'):
        d,faces=E.read_model(p);assert d['elements'];bad=0
        for e in d['elements']:
            for f in e['faces'].values():
                assert 0<=f['texture']<len(d['textures'])
                assert all(np.isfinite(v).all() for v in [np.array(list(f['uv'].values()))])
        models.append({'file':p.name,'elements':len(d['elements']),'animations':len(d.get('animations',[]))})
    original=img(ctex('item/filter'));edited=img('creature_list_filter')
    edge_unchanged=all(original.getpixel((x,y))==edited.getpixel((x,y)) for y in range(16) for x in range(16) if x<5 or x>11 or y<4 or y>12)
    assert edge_unchanged
    assert all(r['unchanged_outside_mask'] for r in json.loads((O/'mask_audit.json').read_text()))
    for mode in ['closed','glass','bars']:
        d=json.loads((M/('01_crate_'+mode+'.bbmodel')).read_text(encoding='utf-8'));assert len([g for g in d['outliner'] if g['name']=='唯一通风口'])==1
    json.dump({'models':models,'filter_original_edges_unchanged':edge_unchanged,'packager_elements_original':True,'one_vent_per_crate':True,'crates_long_axis':'Z','long_faces':['up','down','west','east'],'fixed_ends':['north','south'],'native_UV_wrap_preserved':True},(O/'validation.json').open('w',encoding='utf-8'),ensure_ascii=False,indent=2)
    audited={a['output']:a for a in AUD}
    for p in T.glob('*.png'):
        a=audited.setdefault(p.name,{'output':p.name,'source':'references/previous_parcel_display.bbmodel' if p.name.startswith('display_') else 'derived packager comparison','operation':'embedded illustration texture' if p.name.startswith('display_') else 'binary recolor mask'})
        a['sha256']=hashlib.sha256(p.read_bytes()).hexdigest();a['dimensions']=list(Image.open(p).size)
    json.dump(list(audited.values()),(O/'SOURCE_MANIFEST.json').open('w',encoding='utf-8'),ensure_ascii=False,indent=2)
    print('Validated',len(models),'models; list-filter silhouette, native packager geometry, recolor masks, single vents')

def gallery():
    cards=[]
    for num,cn,en,name,tiles,notes,variants in ASSETS:
        sheet=num+'_'+en.lower().replace(' ','_')+'.png'
        cards.append(f'<section><h2>{num} {cn}</h2><a href="sheets/{sheet}" target="_blank"><img src="sheets/{sheet}"></a><p><a href="models/{name}.bbmodel" download>下载模型</a> · '+(' <a href="models/08_catcher_animated.bbmodel" download>含工作动画的模型</a> · <a href="08_catcher_work_cycle.gif">动作预览</a>' if num=='08' else '')+'</p></section>')
    s='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>航空箱 · Create 原材质重制</title><style>body{background:#182024;color:#dddcd4;font:18px/1.8 "Microsoft YaHei",sans-serif;max-width:1600px;margin:32px auto;padding:0 24px}h1{color:#d5b574}a{color:#d5b574}img{max-width:100%;display:block}section{margin-top:40px;border-top:1px solid #465055}p{color:#a3ada9}</style><h1>航空箱 · Create 原材质重制 / 02</h1><p>保留原始像素、UV 比例与机械结构。生物蛙港未改动。单击工程图查看完整原图。</p><p><a href="00_overview.png">整套总览</a> · <a href="README.md">制作说明与来源</a> · <a href="SOURCE_MANIFEST.json">逐材质来源</a></p>'''+''.join(cards)
    (O/'index.html').write_text(s,encoding='utf-8')

if __name__=='__main__':
    prepare();build_models();sheets();animation_preview();validate();gallery()

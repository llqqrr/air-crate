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

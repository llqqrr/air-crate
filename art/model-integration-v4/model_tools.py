"""Java model helpers. UV order follows Minecraft 1.21.1 FaceInfo/BlockFaceUV.

Unlike the old mesh bounding-box converter, these helpers never infer cubes
from arbitrary polygons. Native elements, mirrored UVs and rotations survive.
"""
import base64
import copy
import json
import math
import uuid
from pathlib import Path

import numpy as np
from PIL import Image

DIRS = ('north', 'south', 'west', 'east', 'up', 'down')
NORMALS = dict(north=(0,0,-1), south=(0,0,1), west=(-1,0,0), east=(1,0,0), up=(0,1,0), down=(0,-1,0))

def corners(a, b, face):
    x,y,z = a; X,Y,Z = b
    return np.array({
        'north':[(X,Y,z),(X,y,z),(x,y,z),(x,Y,z)],
        'south':[(x,Y,Z),(x,y,Z),(X,y,Z),(X,Y,Z)],
        'west':[(x,Y,z),(x,y,z),(x,y,Z),(x,Y,Z)],
        'east':[(X,Y,Z),(X,y,Z),(X,y,z),(X,Y,z)],
        'up':[(x,Y,z),(x,Y,Z),(X,Y,Z),(X,Y,z)],
        'down':[(x,y,Z),(x,y,z),(X,y,z),(X,y,Z)],
    }[face], dtype=float)

def face_uv(face):
    u,v,U,V=face['uv']; r=face.get('rotation',0)//90
    p=[(u,v),(u,V),(U,V),(U,v)]
    return np.array(p[r:]+p[:r],float)

def rotation(axis, degrees):
    t=math.radians(degrees);c=math.cos(t);s=math.sin(t)
    return np.array({'x':[[1,0,0],[0,c,-s],[0,s,c]],'y':[[c,0,s],[0,1,0],[-s,0,c]],'z':[[c,-s,0],[s,c,0],[0,0,1]]}[axis])

def transformed_points(e, face):
    p=corners(e['from'],e['to'],face)
    if 'rotation' in e:
        r=e['rotation'];o=np.array(r['origin']);p-=o
        if r.get('rescale'):
            for i in range(3):
                if 'xyz'[i]!=r['axis']:p[:,i]/=math.cos(math.radians(r['angle']))
        p=p@rotation(r['axis'],r['angle']).T+o
    return p

def cube(name, a, b, textures, uv=None):
    if isinstance(textures,str): textures={d:textures for d in DIRS}
    return {'name':name,'from':list(a),'to':list(b),'faces':{d:{'texture':t,'uv':list((uv or {}).get(d,[0,0,16,16]))} for d,t in textures.items()}}

def model(elements, textures):
    return {'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':textures,'elements':elements}

def dump(path, data):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def resolve(ref, textures):
    for _ in range(30):
        if not ref.startswith('#'):return ref
        ref=textures[ref[1:]]
    raise ValueError('Cyclic texture reference')

def rotate_y(data, degrees):
    """Exact 90-degree rigid rotation, including face UV correspondence."""
    d=copy.deepcopy(data);r=rotation('y',degrees);c=np.array([8,8,8])
    for e in d['elements']:
        old=copy.deepcopy(e)
        box=np.array([old['from'],old['to']]);box=(box-c)@r.T+c
        e['from']=np.round(box.min(0),6).tolist();e['to']=np.round(box.max(0),6).tolist()
        if 'rotation' in old:
            rr=e['rotation'];rr['origin']=np.round((np.array(rr['origin'])-c)@r.T+c,6).tolist()
            axis=np.eye(3)['xyz'.index(rr['axis'])]@r.T
            k=int(np.argmax(np.abs(axis)));rr['axis']='xyz'[k];rr['angle']*=int(round(axis[k]))
        e['faces']={}
        for direction,face in old['faces'].items():
            n=np.array(NORMALS[direction])@r.T
            dest=next(k for k,v in NORMALS.items() if np.allclose(n,v))
            pts=(corners(old['from'],old['to'],direction)-c)@r.T+c
            target=corners(e['from'],e['to'],dest)
            uv=face_uv(face);wanted=np.array([uv[np.argmin(np.linalg.norm(pts-p,axis=1))] for p in target])
            f=copy.deepcopy(face)
            for angle in (0,90,180,270):
                f['rotation']=angle
                if np.allclose(face_uv(f),wanted):break
            else: raise ValueError('UV rotation cannot be represented')
            if not f['rotation']:f.pop('rotation')
            e['faces'][dest]=f
    return d

def bbmodel(data, name, texture_path):
    """Editable Java Block/Item cubes, NOT free mesh conversion input."""
    refs=list(dict.fromkeys(resolve(f['texture'],data['textures']) for e in data['elements'] for f in e['faces'].values()))
    textures=[]
    for i,ref in enumerate(refs):
        p=texture_path(ref);im=Image.open(p)
        textures.append({'name':ref.split(':')[1]+'.png','namespace':ref.split(':')[0], 'folder':str(Path(ref.split(':')[1]).parent).replace('\\','/'), 'id':str(i),'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,ref)), 'width':im.width,'height':im.height,'uv_width':16,'uv_height':16,'mode':'bitmap','source':'data:image/png;base64,'+base64.b64encode(p.read_bytes()).decode()})
    elements=[]
    for i,e in enumerate(data['elements']):
        el=copy.deepcopy(e);el['type']='cube';el['uuid']=str(uuid.uuid5(uuid.NAMESPACE_URL,name+str(i)))
        r=el.pop('rotation',None);el['origin']=r['origin'] if r else [8,8,8]
        if r:
            el['rotation']=[r['angle'] if ax==r['axis'] else 0 for ax in 'xyz'];el['rescale']=r.get('rescale',False)
        for f in el['faces'].values():f['texture']=refs.index(resolve(f['texture'],data['textures']))
        elements.append(el)
    return {'meta':{'format_version':'4.10','model_format':'java_block','box_uv':False},'name':name,'resolution':{'width':16,'height':16},'elements':elements,'outliner':[e['uuid'] for e in elements],'textures':textures,'display':data.get('display',{}),'credit':'Create / Minecraft derived; see INTEGRATION.md'}

def render_mesh(data, name, texture_path):
    """Mesh only for software preview, sampled from the shipped Java JSON."""
    bb=bbmodel(data,name,texture_path);elements=[]
    refs=list(dict.fromkeys(resolve(f['texture'],data['textures']) for e in data['elements'] for f in e['faces'].values()))
    for i,e in enumerate(data['elements']):
        vertices={};faces={}
        for direction,f in e['faces'].items():
            p=transformed_points(e,direction);uv=face_uv(f)
            ti=refs.index(resolve(f['texture'],data['textures']));t=bb['textures'][ti]
            uv=uv*np.array([t['width']/16,t['height']/16])
            ids=[direction+str(j) for j in range(4)]
            vertices.update({key:v.tolist() for key,v in zip(ids,p)})
            faces[direction]={'vertices':ids,'uv':{key:v.tolist() for key,v in zip(ids,uv)},'texture':ti}
        elements.append({'name':e.get('name',str(i)),'type':'mesh','uuid':bb['elements'][i]['uuid'],'origin':[0,0,0],'rotation':[0,0,0],'vertices':vertices,'faces':faces})
    bb['elements']=elements;bb['meta']['model_format']='free'
    for t in bb['textures']:
        t['uv_width']=t['width'];t['uv_height']=t['height']
    return bb

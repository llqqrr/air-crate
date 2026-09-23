"""Checks exported resources and the selection contract, not a Minecraft playtest."""
import base64
import io
import zipfile
from collections import Counter
from build import *

def main():
    counts=Counter()
    for path in MOD.rglob('*.json'):
        data=json.loads(path.read_text(encoding='utf-8'));counts['java_models']+=1
        assert data['render_type']=='minecraft:cutout',path
        for ref in data['textures'].values():
            if not ref.startswith('#'):assert texture_path(ref).is_file(),(path,ref)
        for e in data['elements']:
            assert all(-16<=v<=32 for v in e['from']+e['to']),(path,e)
            assert all(a<=b for a,b in zip(e['from'],e['to'])),(path,e)
            if 'rotation' in e:
                assert e['rotation']['angle'] in (-45,-22.5,0,22.5,45),(path,e)
            for f in e['faces'].values():
                assert f.get('rotation',0) in (0,90,180,270)
                assert all(0<=v<=16 for v in f['uv'])
                assert texture_path(resolve(f['texture'],data['textures'])).exists()
    for p in TEX.glob('*.png'):
        im=Image.open(p);assert im.size==(16,16),p
        assert set(np.asarray(im)[:,:,3].ravel())<={0,255},p
        counts['pixel_textures']+=1
    for kind in ('bars','closed'):
        a=np.array(Image.open(TEX/(kind+'_inner.png')))
        assert np.array_equal(a[0],a[-1])
        assert np.array_equal(a[:,0],a[:,-1])
    assert not np.array(Image.open(TEX/'glass_inner.png'))[:,:,3].any()
    for mask in range(16):
        frame=np.array(Image.open(TEX/f'frame_{mask:02}.png'))[:,:,3]
        glass=np.array(Image.open(TEX/f'glass_{mask:02}.png'))[:,:,3]
        assert np.array_equal(frame,glass)
        for y in range(16):
            for x in range(16):
                allowed=(mask&LEFT and x<2) or (mask&RIGHT and x>=14) or (mask&TOP and y<2) or (mask&BOTTOM and y>=14)
                if not allowed:assert frame[y,x]==0,(mask,x,y)
    # Full 3x3 end exactly preserves the original 48x48 vault panel pixels.
    assembled=Image.new('RGBA',(48,48))
    for u in range(3):
        for v in range(3):assembled.paste(Image.open(TEX/f'panel_{edge_mask(u,v,3,3):02}.png'),(u*16,v*16))
    native=readtex('create','block/vault/vault_front_large').crop((16,0,64,48))
    assert np.array_equal(assembled,native)
    # All legal sizes, both horizontal main axes. One vent, no interior face.
    model_names={p.name for p in (MOD/'crate').glob('*.json')}
    for w in range(1,6):
        for h in range(1,4):
            for length in range(1,33):
                for axis in ('x','z'):
                    W,L=(length,w) if axis=='x' else (w,length)
                    vent_face='west' if axis=='x' else 'north'
                    end_faces=('west','east') if axis=='x' else ('north','south')
                    vents=0;surface_count=0
                    for x in range(W):
                        for y in range(h):
                            for z in range(L):
                                visible={'north':z==0,'south':z==L-1,'west':x==0,'east':x==W-1,'up':y==h-1,'down':y==0}
                                for direction,show in visible.items():
                                    if not show:continue
                                    surface_count+=1
                                    u,v,fw,fh=face_grid(direction,x,y,z,W,h,L);mask=edge_mask(u,v,fw,fh)
                                    assert 0<=u<fw and 0<=v<fh
                                    vent=direction==vent_face and u==v==0
                                    vents+=vent
                                    kind='vent' if vent else 'panel' if direction in end_faces else 'glass'
                                    assert f'{kind}_{mask:02}_{direction}.json' in model_names
                    assert vents==1,(w,h,length,axis,vents)
                    assert surface_count==2*(W*h+W*L+h*L)
                    counts['structure_size_axis_cases']+=1
    original=json.loads((REF/'encased_fan_item.json').read_text())
    fan=json.loads((MOD/'fan_north.json').read_text())
    assert len(fan['elements'])==len(original['elements'])==8
    for a,b in zip(original['elements'],fan['elements']):
        assert a['faces']==b['faces']
        for key in ('from','to'):assert np.allclose(np.array(a[key])*.625+[3,3,-.5],b[key])
    for angle in (90,180,270):
        # Applying inverse rotation restores every face and its UV mapping.
        roundtrip=rotate_y(rotate_y(fan,angle),-angle)
        for a,b in zip(fan['elements'],roundtrip['elements']):
            assert a['from']==b['from'] and a['to']==b['to']
            for direction,f in a['faces'].items():
                assert np.allclose(face_uv(f),face_uv(b['faces'][direction]))
    for p in (ROOT/'blockbench').glob('*.bbmodel'):
        d=json.loads(p.read_text(encoding='utf-8'));ids={e['uuid'] for e in d['elements']}
        assert set(d['outliner'])==ids
        for t in d['textures']:Image.open(io.BytesIO(base64.b64decode(t['source'].split(',')[1]))).verify()
        counts['blockbench_projects']+=1
    parcel_data=parcel();native=json.loads((OLD/'references/create/models/item/package/cardboard_12x12.json').read_text())
    for face in ('east','west','south','up','down'):assert parcel_data['elements'][0]['faces'][face]==native['elements'][0]['faces'][face]
    # Check the report's atlas-mismatch claim using decoded pixels, not PNG bytes.
    src_bb=json.loads((OLD/'models/05_parcel_empty.bbmodel').read_text(encoding='utf-8'))
    t=next(t for t in src_bb['textures'] if 'cardboard' in t['name'])
    embedded=Image.open(io.BytesIO(base64.b64decode(t['source'].split(',')[1]))).convert('RGBA')
    jar=ROOT.parents[2]/'libs/create-1.21.1-6.0.10.jar'
    with zipfile.ZipFile(jar) as z:runtime=Image.open(io.BytesIO(z.read('assets/create/textures/item/package/cardboard.png'))).convert('RGBA')
    assert np.array_equal(embedded,runtime)
    report={'passed':True,'counts':dict(counts),'checks':['Runtime JSON bounds, UVs, rotations, texture dependencies','16x16 binary alpha; glass fully clear; bars and steel periodic edges','Outer-edge-only mask across 16 variants','3x3 panel pixel-identical to native 48x48 vault panel','All 960 size/axis cases: single top-left vent and no internal surfaces','All eight fan elements and exact original UVs retained','90/180/270-degree rotation UV round trips','Embedded Blockbench assets and outliner references','Parcel five opaque faces identical to native JSON','Parcel source atlas RGBA pixels identical to installed Create 6.0.10'], 'limitations':['No Minecraft client or Blockbench GUI playtest in this revision','Reference assembly/selection checks do not validate the existing Java renderer','Kinetic shafts, partial registration, animation and selection remain runtime integration work']}
    dump(ROOT/'checks/validation.json',report)
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':main()

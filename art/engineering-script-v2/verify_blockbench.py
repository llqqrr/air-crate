"""Load generated projects in the real Blockbench app and capture its animation.
Requires the user's existing Blockbench MCP plugin at localhost:3000/bb-mcp.
No game resources are modified.
"""
import json, hashlib
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from bb_bridge import BB

O=Path(__file__).resolve().parent
P=O/'renders'
B=O/'blockbench_exports';B.mkdir(exist_ok=True)

def data(response):
    if response.get('isError'):raise RuntimeError(response)
    for item in response.get('content',[]):
        if item['type']=='text':return json.loads(item['text'])
    raise RuntimeError('Missing response data')

def main():
    b=BB();checks=[]
    chosen=['01_crate_bars','02_interaction_hatch_patch','04_packager_unpowered','05_parcel_display','07_creature_list_filter']
    files=sorted((O/'models').glob('*.bbmodel'))
    files=sorted(files,key=lambda p:p.stem=='08_catcher_animated')
    for p in files:
        result=data(b.load(p));source=json.loads(p.read_text(encoding='utf-8'))
        assert result['meshes']==len(source['elements'])
        assert result['textures']==len(source['textures'])
        assert result['animations']==len(source.get('animations',[]))
        result['file']=p.name;checks.append(result)
        if p.stem in chosen:
            b.call('risky_eval',{'code':'(()=>{ Modes.options.edit.select(); return true; })()'})
            position=[45,35,55] if p.stem=='04_packager_unpowered' else [48,34,-50]
            target=[8,8,22] if p.stem.startswith('01') else [8,8,8]
            b.call('set_camera_angle',{'position':position,'target':target,'projection':'orthographic'})
            b.shot(P/('blockbench_'+p.stem+'.png'))
        print('Blockbench loaded:',p.name,flush=True)
    data(b.call('risky_eval',{'code':'(()=>{ Animation.all[0].select(); Modes.options.animate.select(); Timeline.setTime(0); Animator.preview(); return {length:Animation.all[0].length,bones:Object.keys(Animation.all[0].animators).length}; })()'}))
    # One fixed camera for all frames. The GIF contains actual Blockbench renders.
    b.call('set_camera_angle',{'position':[40,30,-55],'target':[8,8,-4],'projection':'orthographic'})
    frames=[];signatures=[]
    font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',25)
    for i in range(32):
        t=i*.05
        r=data(b.call('risky_eval',{'code':f'(()=>{{ Timeline.setTime({t}); Animator.preview(); return {{time:Timeline.time}}; }})()'}))
        assert abs(r['time']-t)<.001
        shot=P/'blockbench_animation_frame.png';b.shot(shot)
        im=Image.open(shot).convert('RGBA');signatures.append(hashlib.sha256(im.tobytes()).hexdigest())
        im.thumbnail((1080,580),Image.Resampling.NEAREST)
        canvas=Image.new('RGBA',(1100,660),'#182024');canvas.alpha_composite(im,((1100-im.width)//2,65))
        stage='待机' if t<.22 else '伸出 / 开启' if t<.65 else '捕获 / 闭合' if t<.85 else '回缩' if t<1.2 else '待机'
        ImageDraw.Draw(canvas).text((28,18),f'Blockbench 实播 · {stage}    {t:0.2f}s / 1.60s',font=font,fill='#d5b574')
        frames.append(canvas.convert('RGB'))
        if i in [0,13,17]:canvas.convert('RGB').save(P/f'blockbench_catcher_t{i:02}.png')
    assert len(set(signatures))>12, 'Animation capture did not advance'
    frames[0].save(O/'08_catcher_work_cycle.gif',save_all=True,append_images=frames[1:],duration=50,loop=0)
    shot.unlink()
    formats=data(b.call('list_export_formats',{}));assert any(c['id']=='project' for c in formats['codecs'])
    exported=data(b.call('export_model',{'codec_id':'project','max_content_length':2000000}))
    assert not exported['truncated']
    native=json.loads(exported['content']);assert len(native['animations'])==1
    (B/'08_catcher_animated.bbmodel').write_text(exported['content'],encoding='utf-8')
    b.call('risky_eval',{'code':'(()=>{ Timeline.setTime(0.65); Animator.preview(); return true; })()'})
    report={'models_loaded_in_Blockbench':checks,'native_animation':'capture_cycle','length_seconds':1.6,'captured_frames':32,'distinct_frame_images':len(set(signatures)),'gif_source':'Actual Blockbench renderer; fixed camera; 20 fps','native_codec_export':'blockbench_exports/08_catcher_animated.bbmodel'}
    (O/'BLOCKBENCH_VALIDATION.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print('Actual Blockbench animation captured and exported.',flush=True)

if __name__=='__main__':main()

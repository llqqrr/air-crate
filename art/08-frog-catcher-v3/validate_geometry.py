"""Focused geometry checks for the user's gap and mouth z-fighting feedback."""
from pathlib import Path
import json,math
import numpy as np
from PIL import Image
import build_frog_gun as G
from make_sheets import posed

O=G.O
def run():
    d=json.loads((G.M/'08_frog_catcher_idle.bbmodel').read_text(encoding='utf-8'))
    mapping={eid:g['name'] for g in d['outliner'] for eid in g['children']}
    seen={};duplicates=[]
    for e in d['elements']:
        if '蛙港' not in mapping[e['uuid']]:continue
        for f in e['faces'].values():
            key=tuple(sorted(tuple(round(v,5) for v in e['vertices'][k]) for k in f['vertices']))
            if key in seen:duplicates.append([e['name'],seen[key]])
            seen[key]=e['name']
    assert not duplicates
    lower=next(e for e in d['elements'] if e['name']=='creature_frogport_body_7')
    upper=next(e for e in d['elements'] if e['name']=='creature_frogport_head_2')
    lower_y=max(v[1] for v in lower['vertices'].values());upper_y=min(v[1] for v in upper['vertices'].values())
    assert upper_y-lower_y>0
    tankids=next(g['children'] for g in d['outliner'] if g['name']==G.NAMES['tank']);tankels=[e for e in d['elements'] if e['uuid'] in tankids]
    assert len(tankels)==3
    assert all(e['name'].endswith(('_0','_1','_2')) for e in tankels)
    tank_min_z=min(v[2] for e in tankels for v in e['vertices'].values())
    min_clear=100
    for t in np.linspace(0,1.2,121):
        q=posed(float(t),.875);headids=next(g['children'] for g in q['outliner'] if g['name']==G.NAMES['head'])
        headmax=max(v[2] for e in q['elements'] if e['uuid'] in headids for v in e['vertices'].values())
        min_clear=min(min_clear,tank_min_z-headmax)
    assert min_clear>0, min_clear
    # Tank shell pixels actually referenced remain identical to Create.
    native=Image.open(O/'references/create/textures/block/copper_backtank.png').convert('RGBA');shell=Image.open(O/'textures/copper_pressure_shell_32.png').convert('RGBA')
    assert np.array_equal(np.array(native)[:20],np.array(shell)[:20]);assert np.array(shell)[20:,:,3].max()==0
    for p in G.M.glob('08_*.bbmodel'):
        q=json.loads(p.read_text(encoding='utf-8'));assert q['display']==G.DISPLAY
        for e in q['elements']:
            for f in e['faces'].values():assert 0<=f['texture']<len(q['textures'])
    result={'frog_duplicate_faces':len(duplicates),'closed_inner_plane_clearance':upper_y-lower_y,'head_tank_minimum_z_clearance_over_121_samples':float(min_clear),'tank_body_elements':3,'back_pad_removed':True,'referenced_copper_pixels_unchanged':True,'display_params_equal_potato_cannon':True,'browser_manually_verified':['3D mesh visible','shoot reduces 8 to 7','pressure slider 1/8 rotates needle into red','last shot reaches zero and disables fire','first-person view visible'],'Blockbench_UI_verified':['animated project loads','capture_cycle and pressure_full_to_empty listed'],'game_tested':False}
    (O/'geometry_validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(result,ensure_ascii=True))
if __name__=='__main__':run()

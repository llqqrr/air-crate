from pathlib import Path
import json
import build_frog_gun as G
O=Path(__file__).resolve().parent
def main():
    models={k:json.loads((O/'models'/f).read_text(encoding='utf-8')) for k,f in [('frog','08_frog_catcher_animated.bbmodel'),('cannon','reference_potato_cannon.bbmodel')]}
    s=(O/'viewer/template.html').read_text(encoding='utf-8')
    for key,value in [('THREE_LIBRARY',(O/'viewer/three.min.js').read_text(encoding='utf-8')),('MODEL_DATA',json.dumps(models,ensure_ascii=False)),('CONFIG',json.dumps({'names':G.NAMES,'display':G.DISPLAY},ensure_ascii=False)),('VIEWER_CODE',(O/'viewer/viewer.js').read_text(encoding='utf-8'))]:s=s.replace('/*'+key+'*/',value)
    (O/'viewer/index.html').write_text(s,encoding='utf-8')
    print('Self-contained working 3D prototype saved.')
if __name__=='__main__':main()

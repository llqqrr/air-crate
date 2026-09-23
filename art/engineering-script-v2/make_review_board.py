"""Make a readable design board and exact-model material comparisons."""
from pathlib import Path
import json,base64,copy
from PIL import Image,ImageDraw,ImageFont
import build_v2 as A
import mesh_engine as E

O=Path(__file__).resolve().parent

def main():
    board=Image.new('RGBA',(1920,1140),A.BG)
    A.text(board,(36,22),'航空箱 · Create 原材质重制',37,A.ACC)
    A.text(board,(38,78),'8 项工程预览  /  实际模型、UV 与像素素材  /  2026.09',20,A.SUB)
    for i,(num,cn,en,name,tiles,notes,variants) in enumerate(A.ASSETS):
        x=24+(i%4)*476;y=126+(i//4)*497
        A.box(board,(x,y,x+456,y+473),num+'  '+cn)
        cam=(1,.6,1) if num=='04' else (1,.6,-1)
        v=A.render(name,(430,345),cam)
        A.paste(board,v,(x+12,y+65,432,345))
        labels={'01':'保险库板材 · 单端风机 · 三态','02':'薄型壁挂贴片 · 护理标牌','03':'安山机壳 · 幽匿 · 校频紫晶','04':'原模型 · 局部黄铜换色','05':'原纸箱比例 · 透明观察窗','06':'原版麦穗 · 绳扎配料束','07':'原卷纸轮廓 · 中央爪印','08':'伸缩机械手结构 · 三维工作动画'}
        A.text(board,(x+20,y+427),labels[num],18,A.SUB)
    board.convert('RGB').save(O/'00_design_board.png')

    # The same imported Create mesh receives each source texture set.
    p=O/'models/04_packager_unpowered.bbmodel';data=json.loads(p.read_text(encoding='utf-8'))
    refs=O/'references/comparison_models';refs.mkdir(exist_ok=True)
    variants=[]
    for ns,title in [('create','Create 6.0.10 原材质'),('fluidlogistics','流体包裹 · 铜色参考'),('brass','本版生物打包机 · 黄铜')]:
        d=copy.deepcopy(data);d['name']='comparison_'+ns
        if ns!='brass':
            for t in d['textures']:
                stem=Path(t['name']).stem.removeprefix('brass__')
                source=O/'references/create/textures/block'/(stem+'.png') if ns=='create' else O/'references/fluidlogistics'/(stem+'.png')
                t['source']='data:image/png;base64,'+base64.b64encode(source.read_bytes()).decode()
                t['name']=ns+'_'+stem+'.png';t.pop('relative_path',None)
        f=refs/(ns+'.bbmodel');f.write_text(json.dumps(d,ensure_ascii=False),encoding='utf-8')
        variants.append((f,title))
    im=Image.new('RGBA',(1920,1050),A.BG)
    A.text(im,(36,22),'打包机换色依据 · 同一模型 / 同一 UV / 同一相机',34,A.ACC)
    A.text(im,(38,77),'原模型几何保持一致；只对照流体包裹的铜色替换区域改成 Create 黄铜色阶。',22,A.SUB)
    for i,(p,title) in enumerate(variants):
        x=26+i*632;A.box(im,(x,130,x+607,786),title)
        A.paste(im,E.render(p,(570,570),(1,.65,1)),(x+12,190,581,550))
    A.text(im,(40,815),'换色位置示例：packager_frame.png  /  白色为修改区域',25)
    for j,(n,label) in enumerate([('create__block__packager_frame','Create 原图'),('mask__packager_frame','修改区域'),('brass__packager_frame','黄铜改稿')]):
        A.thumb(im,n,45+j*285,868,128);A.text(im,(190+j*285,898),label,20,A.SUB)
    A.text(im,(1010,869),'掩膜外像素完全相同；保留原红色饰带和机构细节。',23,A.SUB)
    A.text(im,(1010,917),'图中是原 item 模型的空载框架，不额外补画外壳。',23,A.SUB)
    A.text(im,(1010,965),'逐图验证结果见 mask_audit.json。',23,A.SUB)
    im.convert('RGB').save(O/'09_packager_reference_comparison.png')
    gallery=O/'index.html';s=gallery.read_text(encoding='utf-8')
    addition='<section><h2>换色依据对照</h2><a href="09_packager_reference_comparison.png"><img src="09_packager_reference_comparison.png"></a><p>同一原模型对照 Create、流体包裹、本版黄铜三个材质方案。</p></section>'
    if '09_packager_reference_comparison.png' not in s:gallery.write_text(s+addition,encoding='utf-8')
    print('Review board and same-model comparison saved.')

if __name__=='__main__':main()

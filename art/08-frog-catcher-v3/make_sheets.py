from pathlib import Path
import json,copy,math
import numpy as np
from PIL import Image,ImageDraw
import build_frog_gun as G
import native_assets as A
import mesh_engine as E

O=G.O;M=G.M;P=G.P

def render(name,size=(800,600),cam=(-1,.55,-1),ortho=False,frame=None):return E.render(M/(name+'.bbmodel'),size,cam,ortho,frame)

def checker(im,x,y,w,h,k=12):
    d=ImageDraw.Draw(im)
    for a in range(0,w,k):
        for b in range(0,h,k):d.rectangle((x+a,y+b,x+min(w,a+k)-1,y+min(h,b+k)-1),fill='#39474a' if (a//k+b//k)%2 else '#2b3639')

def tile(im,name,x,y,label,size=128):
    v=A.img(name);k=max(1,size//max(v.size));v=v.resize((v.width*k,v.height*k),Image.Resampling.NEAREST);checker(im,x,y,v.width,v.height);im.alpha_composite(v,(x,y));G.text(im,(x,y+size+12),label,18,G.SUB)

def posed(t,pressure):
    d=json.loads((M/'08_frog_catcher_animated.bbmodel').read_text(encoding='utf-8'))
    groups={g['uuid']:g for g in d['outliner']};els={e['uuid']:e for e in d['elements']}
    channels={}
    for anim,at in [(d['animations'][0],t),(d['animations'][1],1-pressure)]:
        for gid,a in anim['animators'].items():
            cs=channels.setdefault(gid,{})
            for ch in ['rotation','position','scale']:
                keys=sorted([k for k in a['keyframes'] if k['channel']==ch],key=lambda k:k['time'])
                if not keys:continue
                cs[ch]=np.array([np.interp(at,[k['time'] for k in keys],[float(k['data_points'][0][xyz]) for k in keys]) for xyz in 'xyz'])
    for gid,ch in channels.items():
        g=groups[gid];org=np.array(g['origin']);r=ch.get('rotation',[0,0,0]);mat=A.rot('z',r[2])@A.rot('y',r[1])@A.rot('x',r[0]);sc=ch.get('scale',[1,1,1]);pos=ch.get('position',[0,0,0])
        for eid in g['children']:
            e=els[eid];e['vertices']={k:(((np.array(v)-org)*sc)@mat.T+org+pos).tolist() for k,v in e['vertices'].items()}
    return d

def run():
    im=Image.new('RGBA',(2000,1460),G.BG)
    G.text(im,(34,24),'08  生物收纳器',42,G.ACC);G.text(im,(38,87),'FROGPORT PNEUMATIC CATCHER  /  工程设计稿 V3',21,G.SUB)
    G.text(im,(1390,32),'蛙港 × 压缩气罐 × 黄铜机构',27,G.FG);G.text(im,(1390,83),'脚本像素制作 · 可编辑 Blockbench 工程',18,G.SUB)
    G.panel(im,(26,132,1050,910),'三维预览  /  闭嘴待机 · 铜罐去背板')
    hero=render('08_frog_catcher_idle',(955,645),(-1,.52,-1));hero.save(P/'hero.png');G.paste(im,hero,(44,206,988,665))
    G.panel(im,(1070,132,1974,910),'六面结构视图')
    for i,(label,cam) in enumerate([('前 / 双眼与闭合嘴',(0,0,-1)),('后 / 端盖与泄压阀',(0,0,1)),('左 / 压力表与齿轮',(-1,0,0)),('右 / 排气口',(1,0,0)),('顶 / 横置气罐',(0,1,0)),('底 / 后倾握把',(0,-1,0))]):
        x=1090+(i%2)*438;y=194+(i//2)*226
        v=render('08_frog_catcher_idle',(412,170),cam,True);v.save(P/f'orthographic_{i}.png');G.paste(im,v,(x,y,416,173));G.text(im,(x+30,y+182),label,19,G.SUB)
    G.panel(im,(26,930,1235,1194),'实际源材质 / 原尺寸图集，不强行缩成一张 16×16')
    tiles=[('aircrate__block__creature_frogport_port','航空箱原蛙港'),('copper_pressure_shell_32','去背板铜罐'),('create__block__brass_casing','原黄铜机壳'),('create__block__andesite_casing','原安山机壳'),('create__item__potato_cannon','原加农炮齿轮'),('pressure_gauge_16','6px 表盘 / 新绘')]
    for i,(name,label) in enumerate(tiles):tile(im,name,46+i*196,994,label)
    G.panel(im,(1255,930,1974,1194),'比例与机构')
    rows=['静止全长 18.32，原土豆加农炮 20 模型单位','握把后倾 25°，沿用原加农炮持物显示参数','铜罐去除背负层；金属箍带 + 黄铜鞍座安装','蛙港与气罐紧接，气路连接颈封住接缝','上颌短行程前移再抬起，给罐体留运动空间']
    for i,s in enumerate(rows):G.text(im,(1275,996+i*34),s,20,G.SUB)
    G.panel(im,(26,1215,1974,1436),'工作顺序与功能预留')
    for i,(title,note) in enumerate([('01 扣动扳机','黄铜扳机后压，侧齿轮转动'),('02 张嘴捕获','双眼随上颌抬起，舌头弹出'),('03 回收与排气','蛙舌收回，侧口喷一小股蒸汽'),('04 余压反馈','独立指针读气量，低压进入红区')]):
        x=50+i*475;G.text(im,(x,1284),title,24,G.ACC);G.text(im,(x,1328),note,20,G.SUB)
    G.text(im,(50,1388),'本轮为工程预览；压力、粒子与捕获逻辑在交互设计稿中演示，尚未写入模组运行代码。',18,G.SUB)
    im.convert('RGB').save(O/'sheets/08_frog_catcher_engineering.png')

    detail=Image.new('RGBA',(2000,1480),G.BG);G.text(detail,(34,24),'08  机构 / 压力反馈 / 原型对照',39,G.ACC)
    G.panel(detail,(26,99,1232,800),'发射状态  /  上颌 72° · 蛙舌弹出')
    G.paste(detail,render('08_frog_catcher_firing',(1150,595),(-1,.55,-1.05)),(40,161,1175,615))
    G.panel(detail,(1252,99,1974,800),'实际压力指针 · 与剩余次数绑定')
    for i,(v,lab) in enumerate([(1,'8 / 8   100%'),(.5,'4 / 8   50%'),(.125,'1 / 8   低压')]):
        d=posed(0,v);d['elements']=[e for e in d['elements'] if e['name'] in ['6像素压力表盘','独立压力指针','指针轴帽','仪表薄壳']]
        p=P/f'gauge_{i}.bbmodel';p.write_text(json.dumps(d,ensure_ascii=False),encoding='utf-8');v=E.render(p,(235,210),(-1,0,0),True);v.save(P/f'gauge_{i}.png');G.paste(detail,v,(1280+i*222,181,204,210));G.text(detail,(1290+i*222,403),lab,21,G.ACC)
    for j,s in enumerate(['表面内容区：6×6 像素，边框 8×8 像素','指针独立旋转；不把角度画死在贴图里','气量 ≤25% 进红区，0% 时锁定试射','8 次容量为预览参数，可按玩法调整','捕获动作与压力动画分离，便于接入代码','侧后排气：约 0.46s 起喷出 5 个小粒子']):G.text(detail,(1280,476+j*46),s,22,G.SUB)
    G.panel(detail,(26,820,1050,1220),'同尺度侧视 · 新枪与原土豆加农炮')
    frame=E.verts([1,0,-3],[15,16,18])['north']+E.verts([1,0,-3],[15,16,18])['south']
    for i,n in enumerate(['08_frog_catcher_idle','reference_potato_cannon']):
        v=render(n,(466,278),(-1,0,0),True,frame);G.paste(detail,v,(48+i*492,890,470,278));G.text(detail,(215+i*492,1175),'新版 18.32' if i==0 else '原版 20.00',20,G.ACC)
    G.panel(detail,(1070,820,1974,1220),'去背负层 · 留铜罐本体')
    G.paste(detail,render('reference_copper_backtank',(325,262),(-1,.6,-1)),(1085,880,355,280))
    # Show only the derived cylinder and straps from the actual gun model.
    d=json.loads((M/'08_frog_catcher_idle.bbmodel').read_text(encoding='utf-8'));ids=next(g['children'] for g in d['outliner'] if g['name']==G.NAMES['tank'])
    d['elements']=[e for e in d['elements'] if e['uuid'] in ids or e['name'].startswith(('气罐黄铜','背罐','黄铜尾盖','泄压阀'))];p=P/'tank_only.bbmodel';p.write_text(json.dumps(d,ensure_ascii=False),encoding='utf-8')
    G.paste(detail,E.render(p,(450,280),(-1,.6,-1)),(1490,880,445,280))
    G.text(detail,(1110,1175),'原背罐（对照）',20,G.SUB);G.text(detail,(1510,1175),'铜壳 + 箍带 / 鞍座 + 尾阀',20,G.SUB)
    G.panel(detail,(26,1240,1974,1456),'素材继承与修改边界')
    for j,s in enumerate(['蛙头：航空箱当前贴图与 Create 原嘴部结构；清理双面重复面与闭合裙边重叠。','气罐：仅保留原铜壳 3 个元素，移除背负衬板及其贴图区域；侧后排气口单独建模。','仪表：参考柴油动力发酵罐的框式表盘，缩成 6px 内容区；低压区和可动指针重新设计。','姿态与工作动画可在 viewer/index.html 交互检查；最终玩家手臂、光照与碰撞需游戏实测。']):G.text(detail,(50,1304+j*34),s,22,G.SUB)
    detail.convert('RGB').save(O/'sheets/08_mechanism_and_pressure.png')
    # Sample the exact saved animation channels (including the moving head hinge).
    frames=[];frame=E.verts([2,0,-12],[14,18,17])['north']+E.verts([2,0,-12],[14,18,17])['south']
    for i in range(30):
        t=i*.04;d=posed(t,.875);p=P/'animation_pose.bbmodel';p.write_text(json.dumps(d,ensure_ascii=False),encoding='utf-8')
        v=E.render(p,(1000,660),(-1,.40,-1.05),frame_bounds=frame);bg=Image.new('RGBA',(1040,740),G.BG);bg.alpha_composite(v,(20,60));G.text(bg,(24,14),f'蛙港捕获枪 / 工作动画    {t:.2f}s',26,G.ACC);G.text(bg,(24,702),'保存的骨骼轨道采样 · 蒸汽粒子请见交互预览',18,G.SUB);frames.append(bg.convert('RGB'))
    frames[0].save(O/'08_capture_cycle.gif',save_all=True,append_images=frames[1:],duration=40,loop=0)
    p.unlink()
    # Replace only 08 in a fresh overview, retaining 01-07 imagery verbatim.
    old=O/'references/previous_design_board.png';board=Image.open(old).convert('RGBA');x=24+3*476;y=126+497
    G.panel(board,(x,y,x+456,y+473),'08  生物收纳器 · 气动枪')
    G.paste(board,hero,(x+12,y+65,432,345));G.text(board,(x+20,y+427),'蛙港嘴 · 铜罐 · 压力表 · 后倾握把',18,G.SUB);board.convert('RGB').save(O/'00_updated_design_board.png')
    print('Two engineering sheets, 30-frame cycle, and updated overview saved.')

if __name__=='__main__':run()

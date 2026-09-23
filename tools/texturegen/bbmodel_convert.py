# bbmodel(free, 轴对齐 mesh) -> Minecraft Java block/item JSON 转换器
# 前提：所有 mesh 无旋转、面均为轴对齐四边形（GPT 工程脚本产物满足）
import base64
import json
import math
import os
import re
import sys

AXES = {
    (0, 0, -1): 'north', (0, 0, 1): 'south',
    (-1, 0, 0): 'west', (1, 0, 0): 'east',
    (0, 1, 0): 'up', (0, -1, 0): 'down',
}
# 每个法向量对应的 uv 平面坐标轴（用于校验），uv 直接用 bbmodel 每顶点 uv min/max


def norm3(v):
    l = math.sqrt(sum(c * c for c in v)) or 1.0
    return tuple(c / l for c in v)


def sub(a, b):
    return tuple(x - y for x, y in zip(a, b))


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def convert(bb_path, out_json, tex_map, tex_out_dir=None, tex_prefix=''):
    """tex_map: bbmodel 纹理名 -> 'namespace:path' 或 None(提取新贴图)。
    返回 (element_count, warnings)"""
    with open(bb_path, encoding='utf-8') as f:
        d = json.load(f)
    textures = d.get('textures', [])
    tex_keys = []
    warnings = []
    for i, t in enumerate(textures):
        name = t.get('name', f'tex{i}')
        target = tex_map.get(name, 'EXTRACT' if tex_out_dir else None)
        if target == 'EXTRACT':
            safe = re.sub(r'[^a-z0-9_]', '_', os.path.splitext(name)[0].lower())
            target = f'aircrate:{tex_prefix}{safe}'
            src = t.get('source', '')
            if src.startswith('data:image/png;base64,'):
                png = base64.b64decode(src.split(',', 1)[1])
                os.makedirs(tex_out_dir, exist_ok=True)
                with open(os.path.join(tex_out_dir, safe + '.png'), 'wb') as out:
                    out.write(png)
            else:
                warnings.append(f'texture {name} has no embedded source')
        elif target is None:
            warnings.append(f'texture {name} unmapped, skipped faces using it')
        tex_keys.append((target, t.get('width', 16), t.get('height', 16)))

    elements = []
    for e in d.get('elements', []):
        if e.get('type') != 'mesh':
            warnings.append(f"element {e.get('name')} is {e.get('type')}, skipped")
            continue
        rot = e.get('rotation', [0, 0, 0])
        if any(abs(a) > 1e-6 for a in rot):
            warnings.append(f"element {e.get('name')} has rotation {rot}, skipped")
            continue
        verts = e['vertices']
        faces = e['faces']
        # 立方体范围
        allv = list(verts.values())
        frm = [min(v[i] for v in allv) for i in range(3)]
        to = [max(v[i] for v in allv) for i in range(3)]
        out_faces = {}
        for fid, face in faces.items():
            fverts = face['vertices']
            pts = [verts[v] for v in fverts]
            n = norm3(cross(sub(pts[1], pts[0]), sub(pts[2], pts[1])))
            axis = max(range(3), key=lambda i: abs(n[i]))
            sign = 1 if n[axis] > 0 else -1
            key = AXES.get(tuple(0 if i != axis else sign for i in range(3)))
            t_idx = face.get('texture')
            if t_idx is None or tex_keys[t_idx][0] is None:
                continue
            target, tw, th = tex_keys[t_idx]
            uvs = [face['uv'][v] for v in fverts]
            u0 = min(u for u, _ in uvs) / tw * 16
            u1 = max(u for u, _ in uvs) / tw * 16
            v0 = min(v for _, v in uvs) / th * 16
            v1 = max(v for _, v in uvs) / th * 16
            if key in out_faces:
                warnings.append(f"element {e.get('name')} duplicate face {key} (coplanar split?)")
            out_faces[key] = {'uv': [round(u0, 3), round(v0, 3), round(u1, 3), round(v1, 3)],
                              'texture': f'#{tex_keys.index(tex_keys[t_idx])}'}
        elements.append({'name': e.get('name', 'cube'),
                         'from': [round(c, 3) for c in frm],
                         'to': [round(c, 3) for c in to],
                         'faces': out_faces})
    model = {
        'parent': 'block/block',
        'textures': {str(i): t[0] for i, t in enumerate(tex_keys) if t[0]},
        'elements': elements,
    }
    disp = d.get('display')
    if disp:
        model['display'] = disp
    with open(out_json, 'w', encoding='utf-8') as f:
        json.dump(model, f, indent=2, ensure_ascii=False)
    return len(elements), warnings


if __name__ == '__main__':
    # 用法: bbmodel_convert.py <bbmodel> <out.json> <texmap.json> [tex_out_dir] [tex_prefix]
    bb, out, tm = sys.argv[1], sys.argv[2], sys.argv[3]
    outdir = sys.argv[4] if len(sys.argv) > 4 else None
    prefix = sys.argv[5] if len(sys.argv) > 5 else ''
    with open(tm, encoding='utf-8') as f:
        tex_map = json.load(f)
    n, w = convert(bb, out, tex_map, outdir, prefix)
    print(f'{os.path.basename(bb)} -> {out}: {n} elements')
    for x in w:
        print('  WARN', x)

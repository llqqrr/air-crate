# 批量转换 GPT bbmodel -> 模组资源
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bbmodel_convert import convert

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
ART_V2 = os.path.join(ROOT, 'art', 'engineering-script-v2', 'models')
ART_V3 = os.path.join(ROOT, 'art', '08-frog-catcher-v3', 'models')
RES = os.path.join(ROOT, 'src', 'main', 'resources')
TEX_BLOCK = os.path.join(RES, 'assets', 'aircrate', 'textures', 'block')
TEX_ITEM = os.path.join(RES, 'assets', 'aircrate', 'textures', 'item')
MODEL_BLOCK = os.path.join(RES, 'assets', 'aircrate', 'models', 'block')
MODEL_ITEM = os.path.join(RES, 'assets', 'aircrate', 'models', 'item')

CREATE = {  # 直接引用 create 命名空间（硬依赖）
    'create__block__andesite_casing.png': 'create:block/andesite_casing',
    'create__block__brass_casing.png': 'create:block/brass_casing',
    'create__block__mechanical_press_pole.png': 'create:block/mechanical_press_pole',
    'create__block__vault__vault_front_small.png': 'create:block/vault/vault_front_small',
    'create__block__vault__vault_side_small.png': 'create:block/vault/vault_side_small',
    'create__block__vault__vault_top_small.png': 'create:block/vault/vault_top_small',
    'create__block__vault__vault_bottom_small.png': 'create:block/vault/vault_bottom_small',
    'create__block__fan_casing.png': 'create:block/fan_casing',
    'create__block__fan_side.png': 'create:block/fan_side',
    'create__block__gearbox.png': 'create:block/gearbox',
    'create__block__axis_top.png': 'create:block/axis_top',
    'create__block__axis.png': 'create:block/axis',
    'create__block__fan_blades.png': 'create:block/fan_blades',
    'create__block__packager_iris_closed.png': 'create:block/packager_iris_closed',
    'create__block__packager_details.png': 'create:block/packager_details',
    'create__block__port2.png': 'create:block/port2',
    'create__item__package__cardboard.png': 'create:item/package/cardboard',
    'create__item__potato_cannon.png': 'create:item/potato_cannon',
    'minecraft__block__sculk_sensor_side.png': 'minecraft:block/sculk_sensor_side',
    'minecraft__block__calibrated_sculk_sensor_top.png': 'minecraft:block/calibrated_sculk_sensor_top',
    'minecraft__block__calibrated_sculk_sensor_amethyst.png': 'minecraft:block/calibrated_sculk_sensor_amethyst',
    'aircrate__block__creature_frogport_port.png': 'aircrate:block/creature_frogport_port',
}
CREATEDECO_EXTRACT = {  # createdeco 非硬依赖，提取进本模组
    'createdeco__industrial_iron_plate_metal.png': 'industrial_iron_plate_metal',
    'createdeco__industrial_iron_bars.png': 'industrial_iron_bars',
    'createdeco__industrial_iron_bars_overlay.png': 'industrial_iron_bars_overlay',
    'createdeco__industrial_iron_bars_post.png': 'industrial_iron_bars_post',
    'createdeco__industrial_iron_bars_top.png': 'industrial_iron_bars_top',
}

JOBS = [
    ('02_interaction_hatch_patch.bbmodel', ART_V2, os.path.join(MODEL_BLOCK, 'crate_interaction_hatch.json'), TEX_BLOCK, 'block/'),
    ('03_resonator_idle.bbmodel', ART_V2, os.path.join(MODEL_BLOCK, 'amethyst_sculk_resonator.json'), TEX_BLOCK, 'block/'),
    ('03_resonator_active.bbmodel', ART_V2, os.path.join(MODEL_BLOCK, 'amethyst_sculk_resonator_active.json'), TEX_BLOCK, 'block/'),
    ('05_parcel_empty.bbmodel', ART_V2, os.path.join(MODEL_ITEM, 'creature_parcel_box.json'), TEX_ITEM, 'item/'),
    ('08_frog_catcher_idle.bbmodel', ART_V3, os.path.join(MODEL_ITEM, 'creature_intake_fan.json'), TEX_ITEM, 'item/'),
]


def tex_map_for(bb_path):
    import json as j
    with open(bb_path, encoding='utf-8') as f:
        d = j.load(f)
    m = {}
    for t in d.get('textures', []):
        name = t.get('name', '')
        if name in CREATE:
            m[name] = CREATE[name]
        elif name in CREATEDECO_EXTRACT:
            m[name] = 'aircrate:block/' + CREATEDECO_EXTRACT[name]
            # 标记为提取：占位，随后从 references 拷贝
        else:
            m[name] = 'EXTRACT'  # 新贴图 -> 提取
    return m


if __name__ == '__main__':
    for bb_name, src_dir, out, texdir, prefix in JOBS:
        bb = os.path.join(src_dir, bb_name)
        tm = tex_map_for(bb)
        n, warns = convert(bb, out, tm, texdir, prefix)
        print(f'{bb_name}: {n} elements -> {os.path.basename(out)}')
        for w in warns:
            print('   WARN', w)

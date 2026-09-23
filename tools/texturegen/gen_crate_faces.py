"""Generate per-face submodels for the dynamic aviation crate renderer.

The crate block renders through a custom NeoForge geometry loader
(``aircrate:crate``). At bake time it pulls one-quad submodels from
``models/block/crate_face/<kind>_<facing>[_in]`` and assembles them per block
based on structure role (vent end / plain end / side) and the wall mode
(glass / bars / closed).

Also fills the fully transparent window area of the crate_glass_* textures
with a translucent glass tint (GPT's source art only contains the frame).
"""
import json
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
RES = os.path.join(ROOT, "src", "main", "resources")
MODEL_DIR = os.path.join(RES, "assets", "aircrate", "models", "block", "crate_face")
TEX_DIR = os.path.join(RES, "assets", "aircrate", "textures", "block")


KIND_TEXTURES = {
    "glass": {
        "west": "aircrate:block/crate_glass_west",
        "east": "aircrate:block/crate_glass_east",
        "north": "aircrate:block/crate_glass_west",
        "south": "aircrate:block/crate_glass_east",
        "up": "aircrate:block/crate_glass_up",
        "down": "aircrate:block/crate_glass_down",
    },
    "bars": {
        "west": "aircrate:block/crate_bars_west",
        "east": "aircrate:block/crate_bars_east",
        "north": "aircrate:block/crate_bars_west",
        "south": "aircrate:block/crate_bars_east",
        "up": "aircrate:block/crate_bars_up",
        "down": "aircrate:block/crate_bars_down",
    },
    "closed": {
        "west": "create:block/vault/vault_side_small",
        "east": "create:block/vault/vault_side_small",
        "north": "create:block/vault/vault_side_small",
        "south": "create:block/vault/vault_side_small",
        "up": "create:block/vault/vault_top_small",
        "down": "create:block/vault/vault_bottom_small",
    },
    "vent": {d: "aircrate:block/crate_vent_end" for d in
             ("north", "south", "west", "east", "up", "down")},
    "panel": {d: "create:block/vault/vault_front_small" for d in
              ("north", "south", "west", "east", "up", "down")},
    "output": {d: "aircrate:block/crate_vent_end" for d in
               ("north", "south", "west", "east", "up", "down")},
}
# Inward copies exist for everything except the opaque closed walls.
INWARD_KINDS = ("glass", "bars", "vent", "panel", "output")

DIRECTIONS = ("north", "south", "west", "east", "up", "down")

# Inward slab: 0.03 blocks inside the boundary, face pointing into the block.
INWARD_BOX = {
    "north": ((0, 0, 0), (16, 16, 0.03), "south"),
    "south": ((0, 0, 15.97), (16, 16, 16), "north"),
    "west": ((0, 0, 0), (0.03, 16, 16), "east"),
    "east": ((15.97, 0, 0), (16, 16, 16), "west"),
    "down": ((0, 0, 0), (16, 0.03, 16), "up"),
    "up": ((0, 15.97, 0), (16, 16, 16), "down"),
}

# Fan quad protruding 0.5 out of the face, inset by 2 on the face plane.
FAN_SPAN = {
    "north": ((2, 2, -0.5), (14, 14, 0), "south", "north"),
    "south": ((2, 2, 16), (14, 14, 16.5), "north", "south"),
    "west": ((-0.5, 2, 2), (0, 14, 14), "east", "west"),
    "east": ((16, 2, 2), (16.5, 14, 14), "west", "east"),
    "down": ((2, -0.5, 2), (14, 0, 14), "up", "down"),
    "up": ((2, 16, 2), (14, 16.5, 14), "down", "up"),
}

FAN_TEXTURE = "create:block/fan_blades"


def write_model(name, textures, elements):
    model = {
        "parent": "block/block",
        "textures": textures,
        "elements": elements,
    }
    path = os.path.join(MODEL_DIR, name + ".json")
    with open(path, "w", encoding="utf-8") as out:
        json.dump(model, out, indent=2)


def generate_face_models():
    os.makedirs(MODEL_DIR, exist_ok=True)
    count = 0
    for kind, per_dir in KIND_TEXTURES.items():
        for direction in DIRECTIONS:
            elements = [{
                "from": [0, 0, 0],
                "to": [16, 16, 16],
                "faces": {direction: {"texture": "#t"}},
            }]
            textures = {"t": per_dir[direction]}
            if kind in ("vent", "output"):
                box_from, box_to, front, back = FAN_SPAN[direction]
                elements.append({
                    "from": list(box_from),
                    "to": list(box_to),
                    "faces": {front: {"texture": "#fan"}, back: {"texture": "#fan"}},
                })
                textures["fan"] = FAN_TEXTURE
            write_model(f"{kind}_{direction}", textures, elements)
            count += 1
    for kind in INWARD_KINDS:
        for direction in DIRECTIONS:
            box_from, box_to, face = INWARD_BOX[direction]
            write_model(f"{kind}_{direction}_in", {"t": KIND_TEXTURES[kind][direction]}, [{
                "from": list(box_from),
                "to": list(box_to),
                "faces": {face: {"texture": "#t"}},
            }])
            count += 1
    print(f"wrote {count} face models to {MODEL_DIR}")


if __name__ == "__main__":
    generate_face_models()

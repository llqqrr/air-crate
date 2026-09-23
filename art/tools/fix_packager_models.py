# Regenerates the creature packager block/item models.
#
# IMPORTANT Minecraft UV note: JSON UV coordinates are in 0..16 "texture units"
# where 16 always means the FULL texture, regardless of pixel size. The packager
# textures are GPT's 32x32 brass sheets (four 16x16 quadrants), so the quadrant
# at pixel [0,0,16,16] is addressed by JSON UV [0,0,8,8] -- Create's original
# 16px-style UV values were already correct for these sheets. Doubling them
# (an earlier mistake) made every face sample the whole 32x32 sheet.
#
# Horizontal models: item.json already carries GPT's full geometry with correct
# UVs; if a doubled version is detected, halve non-iris UVs back. block.json and
# the powered/linked variants are derived from it.
# Vertical models: convert GPT's 04_packager_vertical_unpowered.bbmodel mesh
# (axis-aligned cuboid faces), dividing bbmodel pixel UVs by (uv_width/16) and
# swapping texture 0 to the packager_vertical_* sheets like Create does.
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODELS = os.path.join(ROOT, "..", "src", "main", "resources", "assets", "aircrate",
                      "models", "block", "creature_packager")
BBMODELS = os.path.join(ROOT, "engineering-script-v2", "models")

TEX = "aircrate:block/packager/"


def load(name, base=MODELS):
    with io.open(os.path.join(base, name), encoding="utf-8") as fh:
        return json.load(fh)


def save(name, data, base=MODELS):
    with io.open(os.path.join(base, name), "w", encoding="utf-8", newline="\n") as fh:
        json.dump(data, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def halve_uvs(model, skip_texture_refs=("#3",)):
    """Undoes the mistaken 2x UV scaling (iris faces on 16px sheets were never scaled)."""
    for element in model.get("elements", []):
        for face in element.get("faces", {}).values():
            if face.get("texture") in skip_texture_refs:
                continue
            if "uv" in face:
                face["uv"] = [c / 2 for c in face["uv"]]
    return model


def looks_doubled(model):
    """Detects the doubled state: frame side faces would sit at [0,0,16,16]."""
    for element in model.get("elements", []):
        if element.get("name") == "frame":
            face = element.get("faces", {}).get("north", {})
            return face.get("uv") == [0, 0, 16, 16]
    return False


# The horizontal item.json elements lost their names in an earlier conversion,
# so the iris backplate is matched by its geometry instead.
BACKPLATE_BOX = ([1, 1, 15], [15, 15, 18])   # item_2: pokes out of the front
CRADLE_BOX = ([2, 2, 2], [14, 4, 14])        # item_5: interior floor + tray band
CRADLE_SINK = 0.05                            # drops the cradle below tray level


def strip_static_only_elements(model):
    """item_2 (iris backplate) exists so the *item icon* looks complete; in
    placed blocks it pokes out of the front face (the animated BER hatch already
    provides the door on the container side) -- drop it. item_5 (cradle) is the
    opaque interior floor and must stay, but its top face was coplanar with the
    animated tray (y=4) and z-fought it, so the whole element sinks 0.05px: the
    tray covers it at rest, and the grating floor shows while the tray is out."""
    model = json.loads(json.dumps(model))

    def keep(e):
        if e.get("name") == "item_2":
            return False
        if e.get("from") == BACKPLATE_BOX[0] and e.get("to") == BACKPLATE_BOX[1]:
            return False
        return True

    model["elements"] = [e for e in model.get("elements", []) if keep(e)]
    for e in model["elements"]:
        cradle = e.get("name") == "item_5" or (e.get("from") == CRADLE_BOX[0] and e.get("to") == CRADLE_BOX[1])
        if cradle:
            e["from"] = [e["from"][0], e["from"][1] - CRADLE_SINK, e["from"][2]]
            e["to"] = [e["to"][0], e["to"][1] - CRADLE_SINK, e["to"][2]]
    return model


def retexture(source, key, texture):
    model = json.loads(json.dumps(source))
    model["textures"][key] = TEX + texture
    return model


def face_direction(axis, const, lo, hi):
    eps = 0.01
    if axis == "z":
        return "north" if abs(const - lo[2]) < eps else "south"
    if axis == "x":
        return "west" if abs(const - lo[0]) < eps else "east"
    return "down" if abs(const - lo[1]) < eps else "up"


def convert_bbmodel(bbmodel_name, side_texture):
    data = load(bbmodel_name, BBMODELS)
    tex_units = []  # JSON UV units per pixel for each bbmodel texture
    for tex in data["textures"]:
        tex_units.append(16.0 / tex.get("uv_width", 16))
    elements = []
    for el in data["elements"]:
        verts = list(el["vertices"].values())
        lo = [min(v[i] for v in verts) for i in range(3)]
        hi = [max(v[i] for v in verts) for i in range(3)]
        faces = {}
        for face in el["faces"].values():
            if face.get("texture") is None:
                continue
            ids = face.get("vertices") or list(face["uv"].keys())
            pts = [el["vertices"][v] for v in ids]
            xs = {p[0] for p in pts}
            ys = {p[1] for p in pts}
            zs = {p[2] for p in pts}
            if len(xs) == 1:
                axis, const = "x", xs.pop()
            elif len(ys) == 1:
                axis, const = "y", ys.pop()
            else:
                axis, const = "z", zs.pop()
            scale = tex_units[face["texture"]]
            uvs = list(face["uv"].values())
            us = [u[0] * scale for u in uvs]
            vs = [u[1] * scale for u in uvs]

            def clean(values):
                return [int(v) if float(v).is_integer() else round(v, 3) for v in values]

            faces[face_direction(axis, const, lo, hi)] = {
                "uv": clean([min(us), min(vs), max(us), max(vs)]),
                "texture": "#" + str(face["texture"]),
            }
        entry = {"from": lo, "to": hi, "faces": faces}
        if el.get("name"):
            entry["name"] = el["name"]
        elements.append(entry)
    return {
        "parent": "block/block",
        "textures": {
            "0": TEX + side_texture,
            "1": TEX + "packager_frame",
            "2": TEX + "packager_iris_closed",
            "3": TEX + "packager_details",
            "particle": TEX + "packager_particle",
        },
        "elements": elements,
    }


def main():
    item = load("item.json")
    if looks_doubled(item):
        halve_uvs(item)
        save("item.json", item)

    block = strip_static_only_elements(item)
    block.pop("credit", None)
    save("block.json", block)
    save("block_powered.json", retexture(block, "2", "packager_horizontal_powered"))
    save("block_linked.json", retexture(block, "2", "packager_horizontal_linked"))

    vertical = strip_static_only_elements(
        convert_bbmodel("04_packager_vertical_unpowered.bbmodel",
                        "packager_vertical_unpowered"))
    save("block_vertical.json", vertical)
    save("block_vertical_powered.json", retexture(vertical, "0", "packager_vertical_powered"))
    save("block_vertical_linked.json", retexture(vertical, "0", "packager_vertical_linked"))

    # Animated partials rendered by the block entity renderer: same UV restore,
    # iris sheets stay as-is (16px textures, correct UVs already).
    for name in ("hatch_closed.json", "hatch_open.json", "tray.json"):
        partial = load(name)
        halved = False
        for element in partial.get("elements", []):
            for face in element.get("faces", {}).values():
                if face.get("texture") not in ("#3",) and "uv" in face \
                        and max(face["uv"]) > 16:
                    halved = True
        if halved:
            halve_uvs(partial)
            save(name, partial)

    # tray.json was authored with raw pixel UVs against the 32px details sheet
    # (max 14, so the >16 recovery above never triggers): halve once so the
    # tray top samples the grating quadrant instead of a four-quadrant mashup.
    tray = load("tray.json")
    tray_up = tray["elements"][0]["faces"]["up"]["uv"]
    if tray_up == [2, 2, 14, 14]:
        halve_uvs(tray)
        save("tray.json", tray)

    print("packager models regenerated (uv restore + vertical conversion)")

if __name__ == "__main__":
    main()

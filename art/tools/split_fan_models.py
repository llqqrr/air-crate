# Splits creature_intake_fan_base.json into a static base plus animatable partials,
# following the bone groups of GPT's 08_frog_catcher_animated.bbmodel:
#   jaw     = 蛙港上颌_含双眼  (opens 0->72 deg around X at pivot 8,10.8,2.3)
#   gears   = 驱动齿轮         (spins 540 deg around X at pivot 4.65,6.6,5.9)
#   trigger = 黄铜扳机         (pulls -16 deg around X at pivot 8,6.1,8.5)
#   valve   = 尾部泄压阀       (short +z protrusion during the exhaust phase)
#   gauge   = 独立压力指针     (rotates -70deg full .. +70deg empty around X at 3.84,9.95,8.15)
# The tongue partial already exists and is not touched. 指针轴帽 stays in the base (static hub).
# Idempotent: if the base no longer contains the moving elements, nothing changes.
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_MODELS = os.path.join(ROOT, "..", "src", "main", "resources", "assets",
                           "aircrate", "models", "item")
BASE = "creature_intake_fan_base.json"

GROUPS = {
    "creature_intake_fan_jaw.json": {
        "creature_frogport_head_0", "creature_frogport_head_2",
        "creature_frogport_head_3", "creature_frogport_head_4",
    },
    "creature_intake_fan_gears.json": {"Cog_0", "Cog_1", "Cog_2", "Cog_3"},
    "creature_intake_fan_trigger.json": {"黄铜弧形扳机", "扳机指托"},
    "creature_intake_fan_valve.json": {"泄压阀阀杆", "泄压阀阀帽"},
    "creature_intake_fan_gauge.json": {"独立压力指针"},
}


def load(name):
    with io.open(os.path.join(ITEM_MODELS, name), encoding="utf-8") as fh:
        return json.load(fh)


def save(name, data):
    with io.open(os.path.join(ITEM_MODELS, name), "w", encoding="utf-8", newline="\n") as fh:
        json.dump(data, fh, indent=1, ensure_ascii=False)
        fh.write("\n")


def main():
    base = load(BASE)
    moving = set().union(*GROUPS.values())
    present = {e.get("name") for e in base.get("elements", [])}
    if not present & moving:
        print("base already split, nothing to do")
        return

    base_elements = []
    buckets = {name: [] for name in GROUPS}
    for element in base.get("elements", []):
        target = next((g for g, names in GROUPS.items() if element.get("name") in names), None)
        (buckets[target] if target else base_elements).append(element)

    for name, elements in buckets.items():
        if not elements:
            # Already split out of the base in a previous run: never clobber an
            # existing partial with an empty element list.
            continue
        save(name, {
            "parent": "block/block",
            "textures": base["textures"],
            "elements": elements,
        })
    base["elements"] = base_elements
    save(BASE, base)
    print("split done:", {k: len(v) for k, v in buckets.items()},
          "base keeps", len(base_elements))


if __name__ == "__main__":
    main()

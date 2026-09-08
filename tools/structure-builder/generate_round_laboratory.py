#!/usr/bin/env python3
"""Author the reference sprite's capsule laboratory; emit matching SNBT/NBT.

North (-Z) is the entrance, matching the existing laboratory door contract.
The exterior is sealed because rooms are separate structures in this project.
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

from generate_underground_road_modules import ROOT, serialize_structure

sys.path.insert(0, str(ROOT / "tools/content-manager"))
from content_manager import _read_minecraft_structure_root

SIZE = (43, 13, 23)
PROJECT = ROOT / "content-projects/cobbleventure-main"
OUTPUT = PROJECT / "content/structures/placeholder/round_laboratory.nbt"


def build_blocks():
    blocks = {}

    def put(x, y, z, material, **properties):
        blocks[x, y, z] = ("minecraft:" + material, tuple(sorted(properties.items())), None)

    # Low oval capsule: a wide middle with elliptical ends, rather than a
    # tall rectangular hall. The roof rounds in BOTH axes at the end caps.
    footprint = {(x, z) for x in range(1, 42) for z in range(3, 20)
                 if ((x - min(31, max(11, x))) / 10) ** 2 + ((z - 11) / 8) ** 2 <= 1}
    for x in range(SIZE[0]):
        for z in range(SIZE[2]):
            for y in range(1, SIZE[1]):
                put(x, y, z, "air")
    for x, z in sorted(footprint):
        put(x, 0, z, "polished_deepslate")
        radial = ((x - min(31, max(11, x))) / 10) ** 2 + ((z - 11) / 8) ** 2
        roof = 6 + round(3 * math.sqrt(max(0, 1 - radial)))
        for y in range(1, roof + 1):
            material = "end_stone_bricks" if y < 5 else "smooth_sandstone"
            if y == 1:
                material = "gray_concrete"
            if y >= 6 and z in (6, 7, 11, 12, 16, 17):
                material = "light_gray_terracotta"
            put(x, y, z, material)

    # Separate exposed steel hoops. Only the legs and curved beam are steel;
    # they stand proud of the skin with a visible air gap over the roof.
    for x in (9, 10, 15, 16, 26, 27, 32, 33):
        for z in range(2, 21):
            beam = 6 + round(5 * math.sqrt(max(0, 1 - ((z - 11) / 9) ** 2)))
            # Two-block beam depth keeps the stepped arch connected at the
            # steep ends, rather than leaving floating segments above the legs.
            for y in range(beam - 1, beam + 1):
                put(x, y, z, "smooth_quartz")
            put(x, beam + 1, z, "smooth_quartz_slab", type="bottom", waterlogged="false")
            if z in (2, 20):
                put(x, 0, z, "polished_andesite")
                for y in range(1, beam):
                    put(x, y, z, "iron_block")
    # Broad red central spine and its narrow ivory rails.
    for x in range(18, 25):
        for z in range(3, 20):
            roof = 7 + round(3 * math.sqrt(max(0, 1 - ((z - 11) / 8) ** 2)))
            put(x, roof, z, "smooth_quartz" if x in (18, 24) else
                "orange_terracotta" if z % 4 == 0 else "red_terracotta")

    for start in (12, 28):
        for x in range(start, start + 3):
            for y in range(3, 6):
                put(x, y, 3, "gray_concrete")
            put(x, 4, 3, "light_blue_stained_glass")
            put(x, 4, 4, "sea_lantern")
            put(x, 3, 2, "smooth_stone_slab", type="top", waterlogged="false")
    for x in range(18, 25):
        for z in range(0, 4):
            put(x, 0, z, "smooth_stone")
        for y in range(1, 7):
            put(x, y, 3, "smooth_quartz" if x in (18, 24) else "gray_concrete")
    for y in (1, 2):
        put(21, y, 3, "iron_door", facing="south", half="lower" if y == 1 else "upper",
            hinge="left", open="false", powered="false")
    for x in (20, 22):
        for y in (1, 2, 3):
            put(x, y, 3, "light_blue_stained_glass")
    for x in range(20, 23):
        put(x, 4, 2, "sea_lantern")
        put(x, 5, 2, "smooth_quartz_slab", type="bottom", waterlogged="false")
    for x in (25, 26, 27):
        put(x, 0, 0, "smooth_stone")
        put(x, 2, 0, "smooth_quartz")
    put(26, 1, 0, "polished_andesite")
    put(26, 2, 0, "red_concrete")
    return blocks


def generate():
    data = serialize_structure(SIZE, build_blocks())
    OUTPUT.write_bytes(data)
    # This structure uses only compounds, lists, strings and int tags. JSON's
    # quoted keys/strings are valid SNBT and retain all of those tag types.
    OUTPUT.with_suffix(".snbt").write_text(
        json.dumps(_read_minecraft_structure_root(data), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8")
    OUTPUT.with_suffix(".structure.json").write_text(json.dumps({
        "schema_version": 1,
        "structure": OUTPUT.relative_to(PROJECT).as_posix(),
        "anchors": [{"id": "door", "label": "door", "type": "door",
                     "position": [21, 1, 3], "safe_spawn": [21, 1, 2],
                     "door_facing": "south", "safe_side": "north"}],
    }, indent=2) + "\n", encoding="utf-8")
    return OUTPUT


if __name__ == "__main__":
    print(generate())

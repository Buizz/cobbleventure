"""Create an editable Hall of Fame template; never overwrite hand edits by default."""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/mod-builder"))
from starter_gym import _build_structure_nbt

DIRECTORY = ROOT / "content-projects/cobbleventure-main/content/structures/interiors"


def template() -> bytes:
    blocks = {(x, y, z): ("minecraft:air", (), None)
              for x in range(32) for y in range(12) for z in range(32)}

    def fill(x1, y1, z1, x2, y2, z2, name, **properties):
        state = ("minecraft:" + name, tuple(sorted(properties.items())), None)
        for x in range(x1, x2 + 1):
            for y in range(y1, y2 + 1):
                for z in range(z1, z2 + 1):
                    blocks[x, y, z] = state

    fill(0, 0, 0, 31, 0, 31, "polished_deepslate")
    fill(0, 11, 0, 31, 11, 31, "smooth_quartz")
    for y in range(1, 11):
        material = "gold_block" if y in (1, 9) else "quartz_bricks"
        fill(0, y, 0, 31, y, 0, material)
        fill(0, y, 31, 31, y, 31, material)
        fill(0, y, 0, 0, y, 31, material)
        fill(31, y, 0, 31, y, 31, material)
    fill(14, 0, 12, 18, 0, 28, "red_concrete")
    for x in (13, 19):
        fill(x, 0, 12, x, 0, 28, "gold_block")
    # Raised presentation stage, with a wide walkable stair along its front.
    fill(4, 1, 3, 27, 1, 10, "smooth_quartz")
    fill(4, 1, 11, 27, 1, 11, "quartz_stairs", facing="north", half="bottom", shape="straight", waterlogged="false")
    for x in (6, 10, 14, 18, 22, 26):
        fill(x, 2, 6, x, 2, 6, "gold_block")
        fill(x, 3, 6, x, 3, 6, "sea_lantern")
    for x in (3, 28):
        for z in (4, 12, 20, 28):
            fill(x, 1, z, x, 8, z, "quartz_pillar", axis="y")
            fill(x, 9, z, x, 9, z, "sea_lantern")
    for x in (8, 23):
        fill(x, 10, 4, x, 10, 27, "sea_lantern")
    for x in (0, 31):
        fill(x, 3, 8, x, 6, 25, "white_stained_glass")
    # Exit is visibly framed and kept behind the entry landing point.
    fill(13, 1, 29, 13, 5, 29, "gold_block")
    fill(19, 1, 29, 19, 5, 29, "gold_block")
    fill(13, 5, 29, 19, 5, 29, "sea_lantern")
    fill(14, 1, 29, 18, 4, 29, "barrier")
    # Separate next-generation portal; the original exit continues to return to the lobby.
    fill(5, 1, 16, 5, 5, 16, "gold_block")
    fill(11, 1, 16, 11, 5, 16, "gold_block")
    fill(5, 5, 16, 11, 5, 16, "sea_lantern")
    fill(6, 1, 16, 10, 4, 16, "barrier")
    return _build_structure_nbt((32, 12, 32), blocks)


def create(overwrite=False):
    path = DIRECTORY / "hall_of_fame.nbt"
    metadata_path = path.with_suffix(".structure.json")
    if not overwrite and (path.exists() or metadata_path.exists()):
        raise SystemExit("명예의 전당 원본이 이미 있습니다. 수작업 원본을 보존합니다.")
    DIRECTORY.mkdir(parents=True, exist_ok=True)
    path.write_bytes(template())
    metadata = {
        "schema_version": 1,
        "structure": "content/structures/interiors/hall_of_fame.nbt",
        "interior": {"id": "hall_of_fame", "width": 32, "depth": 32, "floor_height": 12, "floors": 1},
        "anchors": [
            {"id": "hall_next_generation", "label": "hall_next_generation", "type": "transition",
             "position": [8, 2, 16], "safe_spawn": [8, 1, 19], "facing": "north"},
            {"id": "hall_entry", "label": "hall_entry", "type": "arrival",
             "position": [16, 1, 24], "safe_spawn": [16, 1, 24], "facing": "north"},
            {"id": "hall_exit", "label": "hall_exit", "type": "transition",
             "position": [16, 2, 29], "safe_spawn": [16, 1, 26], "facing": "south"},
        ],
    }
    metadata_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(path)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--overwrite", action="store_true")
    create(parser.parse_args().overwrite)

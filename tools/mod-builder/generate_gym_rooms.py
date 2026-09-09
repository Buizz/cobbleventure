"""Reproducible three-room gyms: shared lobby/pillar course and typed arenas."""
from __future__ import annotations

import json
from pathlib import Path

from starter_gym import _build_structure_nbt

ROOT = Path(__file__).resolve().parents[2]
CONTENT = ROOT / "content-projects/cobbleventure-main/content"
SIZE = (32, 12, 32)
# Field, accent, terrain. Domain colors intentionally follow Pokemon types.
THEMES = {
    "normal": ("smooth_stone", "white_concrete", "quartz_block"),
    "fire": ("red_sandstone", "orange_concrete", "magma_block"),
    "water": ("blue_concrete", "cyan_concrete", "prismarine"),
    "electric": ("smooth_stone", "yellow_concrete", "yellow_glazed_terracotta"),
    "grass": ("moss_block", "lime_concrete", "azalea_leaves"),
    "ice": ("blue_ice", "light_blue_concrete", "packed_ice"),
    "fighting": ("smooth_sandstone", "red_concrete", "polished_andesite"),
    "poison": ("purple_terracotta", "purple_concrete", "amethyst_block"),
    "ground": ("smooth_sandstone", "brown_concrete", "terracotta"),
    "flying": ("smooth_quartz", "light_blue_concrete", "quartz_pillar"),
    "psychic": ("pink_terracotta", "magenta_concrete", "amethyst_block"),
    "bug": ("moss_block", "green_concrete", "mushroom_stem"),
    "rock": ("smooth_sandstone", "gray_concrete", "andesite"),
    "ghost": ("deepslate_tiles", "purple_concrete", "polished_blackstone"),
    "dragon": ("purpur_block", "blue_concrete", "end_stone_bricks"),
    "dark": ("polished_deepslate", "black_concrete", "polished_blackstone"),
    "steel": ("smooth_stone", "light_gray_concrete", "iron_block"),
    "fairy": ("pink_terracotta", "pink_concrete", "cherry_leaves"),
}


def anchor(label, kind, point, **extra):
    return dict(id=label, label=label, type=kind, position=list(point), **extra)


def room(kind, theme="normal"):
    blocks = {}

    def box(x1, y1, z1, x2, y2, z2, material):
        for x in range(x1, x2 + 1):
            for y in range(y1, y2 + 1):
                for z in range(z1, z2 + 1):
                    properties = (("persistent", "true"),) if material.endswith("leaves") else ()
                    blocks[x, y, z] = ("minecraft:" + material, properties, None)

    box(0, 0, 0, 31, 11, 31, "air")
    box(0, 0, 0, 31, 0, 31, "smooth_stone")
    box(0, 11, 0, 31, 11, 31, "light_gray_concrete")
    for x1, z1, x2, z2 in [(0, 0, 31, 0), (0, 31, 31, 31), (0, 0, 0, 31), (31, 0, 31, 31)]:
        box(x1, 1, z1, x2, 10, z2, "white_concrete")
        box(x1, 2, z1, x2, 2, z2, "polished_andesite")
    for x in (5, 15, 25):
        for z in (5, 15, 25):
            box(x, 10, z, x + 1, 10, z + 1, "sea_lantern")
    anchors = [anchor("door", "door", (15, 1, 0), safe_spawn=[15, 1, 3], door_facing="north", safe_side="south")]
    box(14, 1, 0, 17, 4, 0, "air")
    box(14, 0, 1, 17, 0, 3, "light_blue_concrete")
    if kind != "arena":
        anchors.append(anchor("next", "door", (15, 1, 31), safe_spawn=[15, 1, 28], door_facing="south", safe_side="north"))
        box(14, 1, 31, 17, 4, 31, "air")
    if kind == "lobby":
        anchors.append(anchor("interior_spawn", "interior_spawn", (15, 1, 3), facing="south"))
        for x in range(2, 30):
            for z in range(4, 30):
                box(x, 0, z, x, 0, z, "white_terracotta" if (x + z) % 2 else "light_gray_terracotta")
        box(14, 0, 4, 17, 0, 30, "red_concrete")
        for x in (8, 23):
            box(x - 1, 1, 10, x + 1, 1, 12, "polished_andesite")
            box(x, 2, 11, x, 3, 11, "chiseled_stone_bricks")
            box(x, 4, 11, x, 4, 11, "quartz_block")
        for x in (3, 27):
            box(x, 1, 20, x + 1, 1, 26, "smooth_quartz")
    elif kind == "gimmick":
        box(2, 0, 2, 29, 0, 29, "yellow_terracotta")
        box(14, 0, 2, 17, 0, 29, "smooth_sandstone")
        # One reusable pillar course. Trainers stand beside, never inside, pillars.
        for x in (5, 10, 21, 26):
            for z in (8, 15, 22):
                box(x, 1, z, x + 1, 2, z + 1, "quartz_pillar")
                box(x, 3, z, x + 1, 3, z + 1, "chiseled_quartz_block")
        for i, (x, z) in enumerate([(8, 6), (23, 6), (12, 12), (19, 12), (8, 18), (23, 18), (12, 25), (19, 25)], 1):
            anchors.append(anchor(f"trainer_{i}", "npc_position", (x, 1, z), facing="north"))
            box(x, 0, z, x, 0, z, "orange_concrete")
    else:
        field, accent, terrain = THEMES[theme]
        box(0, 3, 1, 0, 4, 30, accent)
        box(31, 3, 1, 31, 4, 30, accent)
        box(1, 3, 31, 30, 4, 31, accent)
        box(4, 0, 7, 27, 0, 25, "white_concrete")
        box(5, 0, 8, 26, 0, 24, field)
        box(5, 0, 16, 26, 0, 16, "white_concrete")
        # Center ring and clear approach/standing lanes on the arena's long axis.
        for x in range(12, 20):
            for z in range(12, 21):
                if 6 <= (x - 15.5) ** 2 + (z - 16) ** 2 <= 11:
                    box(x, 0, z, x, 0, z, "white_concrete")
        for x, z in [(7, 10), (23, 10), (9, 21), (23, 21)]:
            box(x, 1, z, x + 1, 1, z + 1, terrain)
            if theme in {"rock", "ground", "ice", "dragon"}:
                box(x, 2, z, x, 2, z, terrain)
        if theme == "water":
            for x in (6, 21):
                for z in (9, 19):
                    box(x, 0, z, x + 4, 0, z + 4, "water")
        if theme in {"rock", "ground"}:
            for x, z in [(6, 19), (10, 9), (11, 23), (21, 12), (25, 18)]:
                box(x, 1, z, x, 1, z + 1, terrain)
        # Raised spectator benches along both sides, with broad arena access.
        for x in (1, 29):
            box(x, 1, 8, x + 1, 1, 26, accent)
        box(13, 0, 5, 18, 0, 6, "blue_concrete")
        box(13, 0, 26, 18, 0, 27, "red_concrete")
        anchors.extend([
            anchor("leader", "npc_position", (15, 1, 27), facing="north"),
            anchor("battle_player", "arrival", (15, 1, 6), facing="south"),
            anchor("battle_leader", "arrival", (15, 1, 27), facing="north"),
        ])
    return blocks, anchors


def generate():
    directory = CONTENT / "structures/interiors/gyms"
    for name, kind, theme in [("shared_lobby", "lobby", "normal"), ("shared_gimmick", "gimmick", "normal")] + [(f"arena_{t}", "arena", t) for t in THEMES]:
        blocks, anchors = room(kind, theme)
        (directory / f"{name}.nbt").write_bytes(_build_structure_nbt(SIZE, blocks))
        metadata = dict(schema_version=1, interior=dict(id=name, width=32, depth=32, floor_height=12, floors=1), anchors=anchors)
        (directory / f"{name}.structure.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    path = CONTENT / "catalogs/gyms.json"
    catalog = json.loads(path.read_text(encoding="utf-8"))
    for gym in catalog["gyms"]:
        gym["interior"]["modules"] = [dict(id=key, structure=f"cobbleventure:interiors/gyms/{name}", position=[0, 0, z], rotation="none") for key, name, z in [("lobby", "shared_lobby", 0), ("gimmick", "shared_gimmick", 40), ("arena", f"arena_{gym['theme']}", 80)]]
        entrance = gym["interior"]["connections"][0]
        entrance["to"] = "lobby:door"
        gym["interior"]["connections"] = [entrance] + [dict(**{"from": a, "to": b}, condition_mode="all", conditions=[], locked_dialogue=[], enter_dialogue=[]) for a, b in [("lobby:next", "gimmick:door"), ("gimmick:next", "arena:door")]]
    path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    generate()

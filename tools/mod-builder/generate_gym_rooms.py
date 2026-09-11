"""Derive terrain-only arena variants from the hand-authored rock NBT.

Never regenerate the lobby, gimmick room, rock source, or gym catalog.
Only palette entries used by the five authored terrain materials are replaced;
all other NBT records (including block entities and mod tags) stay byte-identical.
"""
from __future__ import annotations

import copy
import gzip
import json
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTENT = ROOT / "content-projects/cobbleventure-main/content"
DIRECTORY = CONTENT / "structures/interiors/gyms"
sys.path.insert(0, str(ROOT / "tools/content-manager"))
sys.path.insert(0, str(ROOT / "tools/structure-builder"))
from content_manager import _minecraft_structure_tag_spans, _read_minecraft_structure_root
from cave_road_anchor import _list_records
from starter_gym import _block_state_payload

TERRAIN = ("minecraft:packed_mud", "minecraft:dripstone_block", "create:cut_dripstone",
           "minecraft:muddy_mangrove_roots", "minecraft:pointed_dripstone")
# Field surface, outcrop, cut stone, ground detail, outcrop tip.
THEMES = {
    "normal": ("smooth_stone", "stone", "stone_bricks", "andesite", "cobblestone_wall"),
    "fire": ("red_sandstone", "basalt", "polished_basalt", "red_nether_bricks", "blackstone_wall"),
    "water": ("blue_terracotta", "prismarine", "prismarine_bricks", "dark_prismarine", "prismarine_wall"),
    "electric": ("yellow_terracotta", "yellow_concrete", "waxed_cut_copper", "waxed_cut_copper", "lightning_rod"),
    "grass": ("moss_block", "azalea_leaves", "mossy_stone_bricks", "rooted_dirt", "azalea_leaves"),
    "ice": ("packed_ice", "blue_ice", "packed_ice", "snow_block", "blue_ice"),
    "fighting": ("smooth_red_sandstone", "polished_andesite", "cracked_stone_bricks", "coarse_dirt", "andesite_wall"),
    "poison": ("purple_terracotta", "purple_concrete", "purple_glazed_terracotta", "crying_obsidian", "amethyst_cluster"),
    "ground": ("smooth_sandstone", "red_sandstone", "cut_red_sandstone", "terracotta", "sandstone_wall"),
    "flying": ("white_terracotta", "calcite", "smooth_quartz", "light_blue_terracotta", "diorite_wall"),
    "psychic": ("magenta_terracotta", "amethyst_block", "purpur_block", "purple_terracotta", "amethyst_cluster"),
    "bug": ("moss_block", "mushroom_stem", "mossy_cobblestone", "rooted_dirt", "brown_mushroom_block"),
    "ghost": ("gray_terracotta", "crying_obsidian", "chiseled_deepslate", "soul_soil", "deepslate_tile_wall"),
    "dragon": ("end_stone", "obsidian", "end_stone_bricks", "purpur_block", "end_stone_brick_wall"),
    "dark": ("gray_concrete", "polished_blackstone", "cracked_polished_blackstone_bricks", "black_terracotta", "polished_blackstone_wall"),
    "steel": ("light_gray_terracotta", "iron_block", "chiseled_tuff_bricks", "polished_tuff", "iron_bars"),
    "fairy": ("pink_terracotta", "cherry_leaves", "pink_concrete", "moss_block", "pink_stained_glass"),
}


def variant_nbt(source: bytes, theme: str) -> bytes:
    raw = gzip.decompress(source) if source.startswith(b"\x1f\x8b") else source
    root = _read_minecraft_structure_root(raw)
    names = {e["Name"] for e in root["palette"]}
    if not set(TERRAIN) <= names:
        raise ValueError("Rock arena terrain palette changed; review the terrain mapping before generating.")
    _, start, end = _minecraft_structure_tag_spans(raw)["palette"]
    _, records = _list_records(raw[start:end])
    replacements = dict(zip(TERRAIN, THEMES[theme]))
    encoded = []
    for entry, original in records:
        material = replacements.get(entry["Name"])
        if material is None:
            encoded.append(original)
            continue
        properties = ()
        if material.endswith("leaves"):
            properties = (("persistent", "true"), ("distance", "1"), ("waterlogged", "false"))
        elif material in {"amethyst_cluster", "lightning_rod"}:
            properties = (("facing", "up"), ("waterlogged", "false"))
        encoded.append(_block_state_payload("minecraft:" + material, properties))
    palette = bytes([10]) + struct.pack(">i", len(encoded)) + b"".join(encoded)
    result = raw[:start] + palette + raw[end:]
    return gzip.compress(result, mtime=0) if source.startswith(b"\x1f\x8b") else result


def generate():
    source = (DIRECTORY / "arena_rock.nbt").read_bytes()
    metadata = json.loads((DIRECTORY / "arena_rock.structure.json").read_text(encoding="utf-8"))
    for theme in THEMES:
        name = "arena_" + theme
        (DIRECTORY / (name + ".nbt")).write_bytes(variant_nbt(source, theme))
        document = copy.deepcopy(metadata)
        document["interior"]["id"] = name
        document["structure"] = "content/structures/interiors/gyms/" + name + ".nbt"
        (DIRECTORY / (name + ".structure.json")).write_text(
            json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Generated 17 terrain variants; authored rock, lobby, gimmick and catalog preserved.")


if __name__ == "__main__":
    generate()

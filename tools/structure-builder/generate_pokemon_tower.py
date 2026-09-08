#!/usr/bin/env python3
"""Build the sage-green, seven-storey Pokemon Tower exterior (north entry)."""
from __future__ import annotations

import math
from generate_underground_road_modules import ROOT, serialize_structure

SIZE = (32, 66, 32)
OUTPUT = ROOT / 'content-projects/cobbleventure-main/content/structures/placeholder/pokemon_tower.nbt'


def build_blocks():
    blocks = {}

    def put(x, y, z, material):
        blocks[x, y, z] = ('minecraft:' + material, (), None)

    # Clear the full envelope so terrain and trees cannot intersect the facade.
    for x in range(32):
        for z in range(32):
            for y in range(SIZE[1]):
                put(x, y, z, 'air')

    # The reference has a broad SQUARE podium, with the narrower tower set
    # back above it. Do not extend the upper tower's clipped corners downward.
    for x in range(2, 30):
        for z in range(3, 31):
            for y in range(7):
                put(x, y, z, 'smooth_stone' if y in (0, 6) else 'light_gray_concrete')

    # Only the upper tower uses the clipped-corner footprint.
    def inside(x, z, margin=0):
        a, b = abs(x - 15.5), abs(z - 16.5)
        return a <= 12 - margin and b <= 12 - margin and a + b <= 20 - margin * 2

    footprint = {(x, z) for x in range(32) for z in range(32) if inside(x, z)}
    edge = {(x, z) for x, z in footprint
            if any((x + dx, z + dz) not in footprint for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    for x, z in footprint:
        for y in range(7, 50):
            # Upper shell is intentionally inaccessible; dungeon rooms live separately.
            material = 'green_terracotta'
            if (x, z) in edge:
                material = 'end_stone_bricks'
                along = x if z in (5, 28) else z
                if y < 49 and (y - 7) % 6 in (1, 2, 3, 4) and along % 4 in (2, 3):
                    material = 'gray_stained_glass'
            if (y - 7) % 6 == 5 or y == 49:
                material = 'smooth_sandstone'
            put(x, y, z, material)
        # A true hemisphere: roof rise equals its 11-block horizontal radius.
        # The old five-block rise compressed the dome into a shallow cap.
        distance_squared = (x - 15.5) ** 2 + (z - 16.5) ** 2
        put(x, 50, z, 'smooth_sandstone' if (x, z) in edge else 'green_terracotta')
        if distance_squared < 11 ** 2:
            top = 50 + round(math.sqrt(11 ** 2 - distance_squared))
            for y in range(51, top + 1):
                put(x, y, z, 'green_terracotta' if y < top else 'lime_terracotta')
    for x in (15, 16):
        for z in (16, 17):
            for y in range(62, 65):
                put(x, y, z, 'smooth_sandstone')
            put(x, 65, z, 'end_stone_bricks')

    # Entry porch retains the existing proximity anchor and safe return coordinates.
    for x in range(11, 21):
        for z in range(0, 9):
            put(x, 0, z, 'smooth_stone')
    for x in range(12, 20):
        for z in range(1, 8):
            for y in range(1, 6):
                put(x, y, z, 'smooth_sandstone' if x in (12, 19) or y == 5 else 'air')
    for x in range(14, 18):
        for z in range(1, 8):
            put(x, 5, z, 'sea_lantern')
    # A dark recessed back wall prevents access to the decorative upper shell.
    for x in range(13, 19):
        for y in range(1, 5):
            put(x, y, 8, 'polished_deepslate')
    return blocks


def generate():
    OUTPUT.write_bytes(serialize_structure(SIZE, build_blocks()))
    return OUTPUT


if __name__ == '__main__':
    print(generate())

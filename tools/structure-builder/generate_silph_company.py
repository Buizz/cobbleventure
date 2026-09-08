#!/usr/bin/env python3
"""Author Silph Co.'s glass office tower, lavender roof and barrel skylight."""
from __future__ import annotations

import math
from generate_underground_road_modules import ROOT, serialize_structure

SIZE = (32, 57, 32)
OUTPUT = ROOT / 'content-projects/cobbleventure-main/content/structures/placeholder/silph_company.nbt'


def build_blocks():
    blocks = {}

    def put(x, y, z, material):
        blocks[x, y, z] = ('minecraft:' + material, (), None)

    for x in range(SIZE[0]):
        for z in range(SIZE[2]):
            for y in range(SIZE[1]):
                put(x, y, z, 'air')

    # Clipped corners, continuous cyan glazing and projecting silver floor bands.
    footprint = {(x, z) for x in range(3, 29) for z in range(5, 29)
                 if not (x in (3, 28) and z in (5, 28))}
    edge = {(x, z) for x, z in footprint if any(
        (x+dx, z+dz) not in footprint for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    for x, z in footprint:
        for y in range(50):
            material = 'cyan_concrete'
            if y < 5:
                material = 'smooth_quartz' if y in (0, 4) else 'light_gray_concrete'
            elif (y - 5) % 5 in (0, 4):
                material = 'smooth_quartz' if (y - 5) % 5 == 4 else 'gray_concrete'
            elif (x, z) in edge:
                along = x if z in (5, 28) else z
                material = 'light_gray_concrete' if along % 4 == 0 else 'light_blue_stained_glass'
            put(x, y, z, material)
        for y in range(9, 50, 5):
            if (x, z) in edge:
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if (x+dx, z+dz) not in footprint:
                        put(x+dx, y, z+dz, 'smooth_quartz')
        # Lavender standing-seam roof, split by a raised central glass canopy.
        put(x, 50, z, 'smooth_quartz' if (x, z) in edge else 'purpur_block')
        if (x, z) not in edge:
            put(x, 51, z, 'purpur_pillar' if x % 3 == 0 else 'purpur_block')

    # North-south barrel skylight with pale ribs and luminous cyan glass.
    for x in range(12, 20):
        roof_y = 52 + round(4 * math.sqrt(max(0, 1 - ((x - 15.5) / 4) ** 2)))
        for z in range(5, 29):
            for y in range(51, roof_y + 1):
                material = 'light_blue_stained_glass'
                if y < roof_y and z not in (5, 28) and x not in (12, 19):
                    material = 'sea_lantern' if y == 51 else 'air'
                if y == roof_y and (z - 5) % 5 == 0:
                    material = 'smooth_quartz'
                put(x, y, z, material)

    # Walkable glazed lobby aligned to the existing dungeon trigger/return.
    for x in range(11, 21):
        for z in range(0, 9):
            put(x, 0, z, 'smooth_stone')
            if z >= 1:
                for y in range(1, 5):
                    put(x, y, z, 'light_blue_stained_glass' if x in (11, 20) else 'air')
                put(x, 5, z, 'smooth_quartz')
    for x in range(12, 20):
        for y in range(1, 5):
            put(x, y, 9, 'gray_concrete')
    for x in range(13, 19):
        for z in range(2, 8):
            put(x, 5, z, 'sea_lantern')
    # A raised, dark lintel recalls the sprite's sign panel without invented text.
    for x in range(13, 19):
        put(x, 6, 1, 'gray_concrete')
    return blocks


def generate():
    OUTPUT.write_bytes(serialize_structure(SIZE, build_blocks()))
    return OUTPUT


if __name__ == '__main__':
    print(generate())

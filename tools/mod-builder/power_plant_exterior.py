"""Compact reference-inspired facade; the playable dungeon is a separate asset."""
from starter_gym import _build_structure_nbt

SIZE = (27, 15, 23)
ENTRY = (13, 1, 3)
SAFE_SPAWN = (13, 1, 0)


def power_plant_exterior_layout():
    blocks = {}

    def put(x, y, z, name, **properties):
        blocks[x, y, z] = ('minecraft:' + name, tuple(sorted(properties.items())), None)

    def fill(x1, y1, z1, x2, y2, z2, name, **properties):
        for x in range(x1, x2 + 1):
            for y in range(y1, y2 + 1):
                for z in range(z1, z2 + 1):
                    put(x, y, z, name, **properties)

    fill(0, 0, 0, 26, 14, 22, 'air')
    fill(0, 0, 0, 26, 0, 22, 'smooth_stone')
    # Pale industrial plinth. Clipped corners soften the rectangular silhouette.
    footprint = {(x, z) for x in range(2, 25) for z in range(3, 22)
                 if (x, z) not in {(2, 3), (24, 3), (2, 21), (24, 21)}}
    edge = {(x, z) for x, z in footprint
            if any((x + dx, z + dz) not in footprint
                   for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    for x, z in footprint:
        for y in range(1, 8):
            put(x, y, z, 'light_gray_concrete' if (x, z) in edge else 'gray_concrete')
        put(x, 1, z, 'polished_andesite')
        put(x, 7, z, 'smooth_quartz')
        put(x, 8, z, 'yellow_concrete' if (x, z) in edge else 'red_terracotta')
        if (x, z) not in edge:
            put(x, 9, z, 'red_terracotta')
    # Fine ribs across the muted red roof, under four equally spaced vent housings.
    for x in range(4, 24, 2):
        fill(x, 9, 5, x, 9, 19, 'brick_slab', type='bottom', waterlogged='false')
    for x in (4, 9, 14, 19):
        fill(x, 10, 11, x + 3, 10, 17, 'smooth_stone')
        fill(x, 11, 12, x + 3, 11, 16, 'light_gray_concrete')
        fill(x + 1, 12, 13, x + 2, 13, 15, 'smooth_quartz')
        fill(x, 14, 12, x + 3, 14, 16, 'smooth_quartz_slab', type='bottom', waterlogged='false')
        fill(x + 1, 14, 13, x + 2, 14, 15, 'polished_deepslate_slab', type='bottom', waterlogged='false')
        fill(x + 1, 11, 11, x + 2, 11, 11, 'polished_andesite')
        fill(x + 1, 12, 12, x + 2, 12, 12, 'red_terracotta')
    # Blue glazed front, white mullions, paired louvered service wings.
    fill(8, 2, 3, 18, 6, 3, 'light_blue_stained_glass')
    fill(8, 2, 4, 18, 6, 4, 'light_blue_concrete')
    for x in (7, 11, 15, 19):
        fill(x, 1, 2, x, 6, 2, 'smooth_quartz')
    fill(7, 4, 2, 19, 4, 2, 'smooth_quartz_slab', type='top', waterlogged='false')
    fill(7, 7, 2, 19, 7, 2, 'smooth_quartz')
    for x in (4, 21):
        fill(x, 2, 2, x + 1, 6, 2, 'light_blue_concrete')
        for y in (2, 3, 5, 6):
            fill(x, y, 1, x + 1, y, 1, 'smooth_stone_slab', type='top', waterlogged='false')
    # Recessed entrance is a shallow vestibule, with a supported safe return porch.
    fill(12, 1, 1, 14, 3, 5, 'air')
    fill(12, 1, 6, 14, 3, 6, 'polished_deepslate')
    fill(12, 4, 4, 14, 4, 5, 'sea_lantern')
    # Side service panels and low red/white transformer frames echo the reference.
    for x in (2, 24):
        for z in (7, 12, 17):
            fill(x, 3, z, x, 5, z + 1, 'light_blue_stained_glass')
    for x in (0, 26):
        for z in (6, 16):
            fill(x, 1, z, x, 4, z, 'red_concrete')
            put(x, 2, z, 'iron_bars', north='true', south='true', east='false', west='false', waterlogged='false')
            put(x, 4, z, 'smooth_quartz_slab', type='bottom', waterlogged='false')
            put(x, 5, z, 'lightning_rod', facing='up', waterlogged='false', powered='false')
    return SIZE, blocks


def build_power_plant_exterior_nbt():
    return _build_structure_nbt(*power_plant_exterior_layout())

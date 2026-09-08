#!/usr/bin/env python3
"""Build a shared lobby and five independently routed research rooms."""
import json
from generate_round_laboratory import PROJECT, serialize_structure, _read_minecraft_structure_root

ROOMS = {
    'tm': ('기술머신 제작실', 'yellow', 'tm_researcher'),
    'z_move': ('Z기술 연구실', 'purple', 'z_move_researcher'),
    'mega': ('메가진화 연구실', 'cyan', 'mega_researcher'),
    'dynamax': ('다이맥스 연구실(선택)', 'red', 'dynamax_researcher'),
    'stats': ('노력치·개체값 조절실', 'lime', 'ev_researcher'),
}
OUTPUT_DIR = PROJECT / 'content/structures/interiors'


def build_space(key):
    lobby = key == 'lobby'
    size = (64, 8, 16) if lobby else (24, 8, 24)
    w, h, d = size
    blocks, anchors = {}, []

    def put(x, y, z, material, **props):
        blocks[x, y, z] = (material if ':' in material else 'minecraft:' + material,
                            tuple(sorted(props.items())), None)

    def box(x1, y1, z1, x2, y2, z2, material):
        for x in range(x1, x2 + 1):
            for y in range(y1, y2 + 1):
                for z in range(z1, z2 + 1):
                    put(x, y, z, material)

    def npc(label, x, z):
        anchors.append(dict(id=label, label=label, type='npc_position',
                            position=[x, 1, z], facing='south'))

    def door(label, x, z, safe_z):
        facing = 'south' if safe_z > z else 'north'
        for y in (1, 2):
            put(x, y, z, 'iron_door', facing=facing,
                half='lower' if y == 1 else 'upper', hinge='left', open='false', powered='false')
        anchors.append(dict(id=label, label=label, type='door', position=[x, 1, z],
                            safe_spawn=[x, 1, safe_z], door_facing=facing, safe_side=facing))

    # Same lab tile and cream wall palette as the existing fossil laboratory.
    box(0, 0, 0, w - 1, h - 1, d - 1, 'air')
    box(0, 0, 0, w - 1, 0, d - 1, 'cobblefurnies:lab_floor')
    for y in range(1, h):
        material = 'smooth_quartz' if y in (1, h - 1) else 'cobbleventure_theme_blocks:house_cream_base_wall'
        box(0, y, 0, w - 1, y, 0, material)
        box(0, y, d - 1, w - 1, y, d - 1, material)
        box(0, y, 0, 0, y, d - 1, material)
        box(w - 1, y, 0, w - 1, y, d - 1, material)
    box(1, h - 1, 1, w - 2, h - 1, d - 2, 'smooth_quartz')
    for x in range(4, w - 2, 6):
        for z in range(4, d - 2, 6):
            put(x, h - 1, z, 'sea_lantern')
    door('door', 6 if lobby else 12, d - 1, d - 2)

    def plant(x, z):
        put(x, 1, z, 'quartz_block')
        put(x, 2, z, 'azalea_leaves', persistent='true', distance='1', waterlogged='false')

    if lobby:
        box(3, 1, 5, 9, 1, 5, 'smooth_quartz')
        put(4, 2, 5, 'black_concrete')
        npc('receptionist', 6, 3)
        box(3, 0, 11, 9, 0, 13, 'red_concrete')
        for index, (room, (_, color, _)) in enumerate(ROOMS.items()):
            x = 16 + index * 10
            door(room, x, 0, 1)
            box(x - 1, 3, 0, x + 1, 3, 0, color + '_concrete')
            box(x - 2, 0, 1, x + 2, 0, 2, color + '_concrete')
            box(x - 2, 1, 12, x + 2, 1, 12, 'smooth_quartz')
            plant(x + 4, 12)
        plant(2, 2)
        plant(11, 2)
    else:
        _, color, researcher = ROOMS[key]
        box(9, 0, 19, 15, 0, 22, color + '_concrete')
        # Rear cabinets, blue observation windows, central workbench and stools.
        box(3, 1, 2, 20, 1, 2, 'smooth_quartz')
        for x in (4, 8, 15, 19):
            put(x, 2, 2, 'black_concrete')
            put(x, 3, 2, 'light_blue_stained_glass')
        box(4, 3, 0, 8, 4, 0, 'light_blue_stained_glass')
        box(15, 3, 0, 19, 4, 0, 'light_blue_stained_glass')
        box(9, 1, 10, 15, 1, 12, 'smooth_quartz')
        box(10, 2, 11, 14, 2, 11, color + '_carpet')
        for x in (8, 16):
            for z in (10, 12):
                put(x, 1, z, 'smooth_quartz_slab', type='bottom', waterlogged='false')
        npc(researcher, 11, 8)
        if key == 'stats':
            npc('iv_researcher', 15, 8)
        plant(2, 20)
        plant(21, 20)
        box(2, 1, 5, 2, 3, 8, 'bookshelf')
        # Distinct research props are decorative; services will be assigned later.
        if key == 'tm':
            box(18, 1, 6, 20, 1, 8, 'crafting_table')
        elif key in ('z_move', 'mega'):
            box(18, 1, 6, 20, 1, 8, 'quartz_block')
            put(19, 2, 7, 'amethyst_block' if key == 'z_move' else 'sea_lantern')
            put(19, 3, 7, 'purple_stained_glass' if key == 'z_move' else 'cyan_stained_glass')
        elif key == 'dynamax':
            box(17, 1, 5, 21, 1, 9, 'red_concrete')
            box(18, 2, 6, 20, 2, 8, 'pink_stained_glass')
        else:
            box(18, 1, 6, 20, 1, 8, 'iron_block')
            put(19, 2, 7, 'sea_lantern')
    return size, blocks, anchors


def generate():
    for key in ('lobby', *ROOMS):
        size, blocks, anchors = build_space(key)
        path = OUTPUT_DIR / f'round_laboratory_{key}.nbt'
        data = serialize_structure(size, blocks)
        path.write_bytes(data)
        path.with_suffix('.snbt').write_text(json.dumps(
            _read_minecraft_structure_root(data), ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        path.with_suffix('.structure.json').write_text(json.dumps(dict(
            schema_version=1, structure=path.relative_to(PROJECT).as_posix(),
            interior=dict(id=f'round_laboratory_{key}', width=size[0], depth=size[2],
                          floor_height=size[1], floors=1), anchors=anchors),
            ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    generate()

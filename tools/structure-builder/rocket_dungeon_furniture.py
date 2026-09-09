"""Rocket Hideout rooms, with complete multi-block machines and clear main aisles."""

NS = 'cobbleventure_theme_blocks:'
AIR = ('minecraft:air', (), None)


def state(name, **properties):
    return name, tuple(sorted(properties.items())), None


def machine_parts(number, x, z, facing='north'):
    """Offsets match AbstractRocketMachineBlock / MachineThreeBlock."""
    if number in (1, 2):
        return {(x, 1+h, z): state(NS+f'rocket_base_machine_{number}', facing=facing, height=str(h))
                for h in range(2)}
    offsets = [(w, d, h) for h in range(2) for d in range(2) for w in range(2)]
    offsets += [(w, d, h) for h in range(3) for d in range(2) for w in range(2, 4)]
    right, back = {'north': ((1, 0), (0, 1)), 'east': ((0, 1), (-1, 0)),
                   'south': ((-1, 0), (0, -1)), 'west': ((0, -1), (1, 0))}[facing]
    return {(x+w*right[0]+d*back[0], 1+h, z+w*right[1]+d*back[1]): state(NS+'rocket_base_machine_3', facing=facing, part=str(i))
            for i, (w, d, h) in enumerate(offsets)}


def decorate_rocket(blocks, name, shape):
    width, _, depth = shape.size
    markers = [(p[0], p[2]) for _, _, p, _ in shape.markers]

    def place(parts):
        # Validate the whole object before writing: NBT placement does not call
        # setPlacedBy, so every machine part must be explicitly serialized.
        for (x, y, z) in parts:
            if x in range(5, 11) or z in range(5, 11):
                return False
            if any(abs(x-px) <= 1 and abs(z-pz) <= 1 for px, pz in markers):
                return False
            if blocks.get((x, y, z)) != AIR:
                return False
            if y == 1 and blocks.get((x, 0, z), AIR) == AIR:
                return False
        blocks.update(parts)
        return True

    def table(x, z, length=2):
        parts = {(x+dx, 1, z): state('minecraft:smooth_quartz') for dx in range(length)}
        parts[(x, 2, z)] = state('minecraft:black_stained_glass_pane', north='false', south='false', east='true', west='true', waterlogged='false')
        for dx in range(1, length):
            parts[(x+dx, 2, z)] = state('minecraft:white_carpet')
        place(parts)
        place({(x+length-1, 1, z-1): state('minecraft:quartz_stairs', facing='south', half='bottom', shape='straight', waterlogged='false')})

    # Wall-side consoles leave the six-wide cross and encounter anchors free.
    for x, z, number in ((3, 12, 1), (12, 3, 2)):
        place(machine_parts(number, x, z))
    if name == 'treasure':
        for x in (3, 11):
            for z in (3, 12):
                place({(x, 1, z): state('minecraft:stripped_oak_wood', axis='y'),
                       (x, 2, z): state('minecraft:yellow_terracotta')})
    elif name in {'start', 'support', 'exit', 'dead_end'}:
        place({(12, 1, 12): state('minecraft:quartz_stairs', facing='north', half='bottom', shape='straight', waterlogged='false')})
    else:
        table(11, 12)
    if depth >= 32:
        # B3-style machinery room with an open service aisle in front of each bank.
        for z in (17, 25):
            place(machine_parts(3, 11, z+3, facing='west'))
            for number in (1, 2):
                place(machine_parts(number, 3, z+number-1))
        table(11, 21)
    if width >= 32:
        # Low blue partitions recall B2's maze without implementing spin tiles.
        # Gaps on alternate ends connect the side aisles back to the main cross.
        for x in (18, 23, 27):
            for z in range(12, 23):
                if (x == 23 and z >= 20) or (x != 23 and z <= 14):
                    continue
                place({(x, 1, z): state(NS+'rocket_base_blue_band')})
        for x, z in ((20, 13), (25, 21), (16, 23)):
            blocks[(x, 0, z)] = state(NS+'rocket_base_yellow_light_panel', facing='north')
        table(19, 26, 4)
        for x in (25, 27):
            place({(x, 1, 27): state('minecraft:stripped_oak_wood', axis='y'),
                   (x, 2, 27): state('minecraft:yellow_terracotta')})
        place(machine_parts(3, 22, 3))
    for x, z in ((3, 3), (width-4, depth-4)):
        place({(x, 1, z): state('minecraft:smooth_quartz'),
               (x, 2, z): state('minecraft:potted_bamboo')})

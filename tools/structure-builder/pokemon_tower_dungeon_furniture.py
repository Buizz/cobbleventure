"""Memorial rooms and walkable, three-wide grave mazes for Pokemon Tower."""

NS = 'cobbleventure_theme_blocks:'
AIR = ('minecraft:air', (), None)


def decorate_tower(blocks, name, shape):
    width, _, depth = shape.size
    large = depth >= 32
    markers = [(p[0], p[2]) for _, _, p, _ in shape.markers]

    def grave(x, z, facing='north'):
        if not large and (x in range(5, 11) or z in range(5, 11)):
            return
        # Keep the first standard-grid connector lane clear at every doorway.
        if (x in range(5, 11) and (z <= 4 or z >= depth-3)) or (z in range(5, 11) and (x <= 4 or x >= width-3)):
            return
        if any(abs(x-px) <= 1 and abs(z-pz) <= 1 for px, pz in markers):
            return
        if blocks.get((x, 1, z)) != AIR or blocks.get((x, 2, z)) != AIR:
            return
        blocks[(x, 1, z)] = (NS+'pokemon_tower_grave', (('facing', facing),), None)

    if large:
        # Rows touch the side walls. Alternating three-block gaps produce a
        # simple serpentine walk, rather than a straight bypass around the maze.
        for row, z in enumerate(range(14, depth-4, 4)):
            for x in range(3, width-3):
                if (row % 2 == 0 and x >= width-6) or (row % 2 == 1 and x <= 5):
                    continue
                grave(x, z, 'south' if row % 2 else 'north')
        for x in range(12, width-4, 2):
            grave(x, 3, 'south')
        for x, z in ((3, 3), (3, 11), (width-4, 11)):
            grave(x, z)
    elif name not in {'start', 'exit'}:
        # Paired memorial rows frame the battle area and final-floor approach.
        for x in (shape.room_margin, width-shape.room_margin-1):
            for z in range(shape.room_margin, depth-shape.room_margin, 2):
                grave(x, z, 'east' if x < width//2 else 'west')
        if name == 'boss':
            for x in (3, 4, 11, 12):
                grave(x, 3, 'south')
                grave(x, 12, 'north')

    if name == 'support':
        # A luminous floor marks the existing healing anchor; its function and
        # air space remain owned by the dungeon definition.
        for x in range(6, 11):
            for z in range(6, 11):
                material = 'minecraft:sea_lantern' if x in (6, 10) or z in (6, 10) else 'minecraft:light_blue_concrete'
                blocks[(x, 0, z)] = (material, (), None)

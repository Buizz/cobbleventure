import json
import sys
import unittest
from collections import deque
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import generate_dungeon_piece_skins as gen
sys.path.insert(0, str(gen.ROOT / 'tools/content-manager'))
from content_manager import _read_minecraft_structure_root


class PokemonTowerDungeonTests(unittest.TestCase):
    def test_tower_palette_and_walkable_grave_maze(self):
        content = gen.ROOT / gen.PROJECT / 'content'
        ns = 'cobbleventure_theme_blocks:'
        for name, shape in gen.SHAPES.items():
            with self.subTest(piece=name):
                data = (content / f'structures/dungeon_pieces/pokemon_tower/{name}.nbt').read_bytes()
                self.assertEqual(data, gen._build_nbt(name, shape, gen.SKINS['pokemon_tower']))
                nbt = _read_minecraft_structure_root(data)
                blocks = {tuple(b['pos']): nbt['palette'][b['state']] for b in nbt['blocks']}
                names = {b['Name'] for b in blocks.values()}
                for suffix in ('green_mosaic', 'purple_plinth', 'purple_pillar', 'purple_cornice'):
                    self.assertIn(ns+'pokemon_tower_'+suffix, names)
                for pos, block in blocks.items():
                    self.assertTrue(all(0 <= p < size for p, size in zip(pos, shape.size)))
                    if block['Name'] == ns+'pokemon_tower_grave':
                        self.assertEqual(1, pos[1])
                        self.assertIn(block['Properties']['facing'], ('north', 'south', 'east', 'west'))
                def air(p):
                    return blocks.get(p, {}).get('Name') == 'minecraft:air'
                walkable = {p for p in blocks if air(p) and air((p[0], p[1]+1, p[2]))
                            and (p[0], p[1]-1, p[2]) in blocks and not air((p[0], p[1]-1, p[2]))}
                definition = json.loads((content / f'dungeon_pieces/pokemon_tower/{name}.json').read_text())
                targets = [tuple(c['position']) for c in definition['connectors']+definition['markers']]
                self.assertTrue(set(targets) <= walkable)
                queue = deque([targets[0]])
                visited = set(queue)
                while queue:
                    x, y, z = queue.popleft()
                    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        # Grave rooms must work without jumping over the graves.
                        for dy in ((-1, 0, 1) if name.startswith('stairs_') else (0,)):
                            p = x+dx, y+dy, z+dz
                            if p in walkable and p not in visited:
                                visited.add(p)
                                queue.append(p)
                self.assertTrue(set(targets) <= visited)
                if name.startswith('empty_chamber'):
                    self.assertTrue({p for p in walkable if p[1] == 1} <= visited)
                    width = shape.size[0]
                    for row, z in enumerate((14, 18, 22, 26)):
                        gaps = [x for x in range(3, width-3) if air((x, 1, z))]
                        self.assertEqual(list(range(width-6, width-3)) if row % 2 == 0 else [3, 4, 5], gaps)

    def test_tower_dungeon_selects_large_tower_chambers(self):
        content = gen.ROOT / gen.PROJECT / 'content'
        dungeon = json.loads((content / 'dungeons/generation_1/rocket_pokemon_tower.json').read_text(encoding='utf-8'))
        self.assertEqual('cobbleventure:dungeon_pool/pokemon_tower_test', dungeon['terrain']['piece_pool'])
        self.assertIn('cobbleventure:dungeon_piece/pokemon_tower/empty_chamber_2x2', dungeon['spatial_layout']['chamber_pieces'])


if __name__ == '__main__':
    unittest.main()

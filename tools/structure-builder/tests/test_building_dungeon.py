import json
import sys
import unittest
from collections import deque
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import generate_dungeon_piece_skins as gen
sys.path.insert(0, str(gen.ROOT / 'tools/content-manager'))
from content_manager import _read_minecraft_structure_root


class BuildingDungeonTests(unittest.TestCase):
    def test_saved_pieces_preserve_contract_and_walkable_connections(self):
        content = gen.ROOT / gen.PROJECT / 'content'
        for name, shape in gen.SHAPES.items():
            with self.subTest(piece=name):
                data = (content / f'structures/dungeon_pieces/building/{name}.nbt').read_bytes()
                self.assertEqual(data, gen._build_nbt(name, shape, gen.SKINS['building']))
                definition = json.loads((content / f'dungeon_pieces/building/{name}.json').read_text())
                original = json.loads((content / f'dungeon_pieces/rocket/{name}.json').read_text())
                for key in ('size', 'connectors', 'markers', 'role'):
                    self.assertEqual(original[key], definition[key])
                nbt = _read_minecraft_structure_root(data)
                blocks = {tuple(b['pos']): nbt['palette'][b['state']]['Name'] for b in nbt['blocks']}
                self.assertIn('cobbleventure_theme_blocks:building_lower_band', blocks.values())
                self.assertIn('cobbleventure_theme_blocks:building_upper_band', blocks.values())
                air = lambda p: blocks.get(p) == 'minecraft:air'
                walkable = {p for p in blocks if air(p) and air((p[0], p[1]+1, p[2]))
                            and blocks.get((p[0], p[1]-1, p[2])) not in (None, 'minecraft:air')}
                targets = [tuple(c['position']) for c in definition['connectors'] + definition['markers']]
                self.assertTrue(set(targets) <= walkable)
                visited = {targets[0]}
                queue = deque(visited)
                while queue:
                    x, y, z = queue.popleft()
                    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        for dy in (-1, 0, 1):
                            p = x+dx, y+dy, z+dz
                            if p in walkable and p not in visited:
                                visited.add(p)
                                queue.append(p)
                self.assertTrue(set(targets) <= visited)
                if name in ('room', 'empty_chamber_1x2', 'empty_chamber_2x2'):
                    self.assertIn('minecraft:black_stained_glass_pane', blocks.values())
                if name == 'empty_chamber_2x2':
                    self.assertIn('minecraft:bookshelf', blocks.values())

    def test_silph_selects_only_building_chambers(self):
        content = gen.ROOT / gen.PROJECT / 'content'
        dungeon = json.loads((content / 'dungeons/generation_1/rocket_silph_company.json').read_text(encoding='utf-8'))
        self.assertEqual('cobbleventure:dungeon_pool/building_test', dungeon['terrain']['piece_pool'])
        self.assertTrue(all('/building/' in p for p in dungeon['spatial_layout']['chamber_pieces']))


if __name__ == '__main__':
    unittest.main()

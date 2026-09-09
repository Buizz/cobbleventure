import json
import sys
import unittest
from collections import deque
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import generate_dungeon_piece_skins as gen
from rocket_dungeon_furniture import machine_parts, NS
sys.path.insert(0, str(gen.ROOT / 'tools/content-manager'))
from content_manager import _read_minecraft_structure_root


class RocketDungeonTests(unittest.TestCase):
    def test_saved_rooms_have_complete_machines_and_connected_anchors(self):
        content = gen.ROOT / gen.PROJECT / 'content'
        resources = gen.ROOT / 'projects/cobbleventure-theme-blocks/src/main/resources/assets/cobbleventure_theme_blocks/blockstates'
        for name, shape in gen.SHAPES.items():
            with self.subTest(piece=name):
                data = (content / f'structures/dungeon_pieces/rocket/{name}.nbt').read_bytes()
                self.assertEqual(data, gen._build_nbt(name, shape, gen.SKINS['rocket']))
                nbt = _read_minecraft_structure_root(data)
                blocks = {tuple(b['pos']): nbt['palette'][b['state']] for b in nbt['blocks']}
                for pos, block in blocks.items():
                    self.assertTrue(all(0 <= p < size for p, size in zip(pos, shape.size)))
                    if not block['Name'].startswith(NS):
                        continue
                    blockstate = json.loads((resources / (block['Name'].split(':')[1]+'.json')).read_text())
                    props = block.get('Properties', {})
                    if 'variants' in blockstate:
                        self.assertTrue(any(dict(pair.split('=') for pair in key.split(',') if pair) == props for key in blockstate['variants']), block)
                    if 'rocket_base_machine_' in block['Name']:
                        number = int(block['Name'][-1])
                        if props.get('height', props.get('part')) == '0':
                            for expected_pos, expected in machine_parts(number, pos[0], pos[2], props['facing']).items():
                                actual = blocks[expected_pos]
                                self.assertEqual(expected[0], actual['Name'])
                                self.assertEqual(dict(expected[1]), actual['Properties'])
                def air(p):
                    return blocks.get(p, {}).get('Name') == 'minecraft:air'
                walkable = {p for p in blocks if air(p) and air((p[0], p[1]+1, p[2]))
                            and (p[0], p[1]-1, p[2]) in blocks and not air((p[0], p[1]-1, p[2]))}
                definition = gen._definition(name, shape, 'rocket')
                targets = [tuple(c['position']) for c in definition['connectors']+definition['markers']]
                self.assertTrue(set(targets) <= walkable)
                queue = deque([targets[0]])
                visited = set(queue)
                while queue:
                    x, y, z = queue.popleft()
                    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        for dy in (-1, 0, 1):
                            p = x+dx, y+dy, z+dz
                            if p in walkable and p not in visited:
                                queue.append(p)
                                visited.add(p)
                self.assertTrue(set(targets) <= visited)
                names = {b['Name'] for b in blocks.values()}
                self.assertIn(NS+'rocket_base_olive_vent', names)
                self.assertIn(NS+'rocket_base_blue_wall', names)
                if name.startswith('empty_chamber'):
                    self.assertTrue({NS+f'rocket_base_machine_{i}' for i in (1, 2, 3)} <= names)
                    # Every floor-level aisle tile must remain reachable, including
                    # the side maze and the spaces around machine banks.
                    floor_tiles = {p for p in walkable if p[1] == 1}
                    self.assertTrue(floor_tiles <= visited)


if __name__ == '__main__':
    unittest.main()

import json
import sys
import unittest
from pathlib import Path
from collections import deque

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'tools/mod-builder'))
sys.path.insert(0, str(ROOT / 'tools/content-manager'))
from content_manager import _read_minecraft_structure_root
from power_plant_exterior import build_power_plant_exterior_nbt
from starter_gym import build_power_plant_dungeon_nbt

CONTENT = ROOT / 'content-projects/cobbleventure-main/content'


def read(relative):
    return json.loads((CONTENT / relative).read_text(encoding='utf-8'))


class PowerPlantTests(unittest.TestCase):
    def test_independent_assets_and_saved_geometry(self):
        dungeon = read('dungeons/generation_1/rocket_power_plant.json')
        self.assertEqual('cobbleventure:dungeons/power_plant_interior', dungeon['terrain']['template'])
        for path, builder, expected in (
            ('placeholder/power_plant', build_power_plant_exterior_nbt, [27, 15, 23]),
            ('dungeons/power_plant_interior', build_power_plant_dungeon_nbt, [48, 10, 48]),
        ):
            with self.subTest(path=path):
                data = (CONTENT / f'structures/{path}.nbt').read_bytes()
                self.assertEqual(builder(), data)
                nbt = _read_minecraft_structure_root(data)
                self.assertEqual(expected, nbt['size'])
                self.assertTrue(all(all(0 <= p < n for p, n in zip(b['pos'], expected)) for b in nbt['blocks']))
                metadata = read(f'structures/{path}.structure.json')
                self.assertEqual(f'content/structures/{path}.nbt', metadata['structure'])

    def test_world_entry_and_return_are_clear_and_connected(self):
        nbt = _read_minecraft_structure_root(build_power_plant_exterior_nbt())
        blocks = {tuple(b['pos']): nbt['palette'][b['state']]['Name'] for b in nbt['blocks']}
        metadata = read('structures/placeholder/power_plant.structure.json')
        self.assertEqual(1, len(metadata['anchors']))
        entry = metadata['anchors'][0]
        self.assertEqual('dungeon_entry', entry['id'])
        for x, y, z in (entry['position'], entry['safe_spawn']):
            self.assertNotEqual('minecraft:air', blocks[x, y - 1, z])
            self.assertEqual('minecraft:air', blocks[x, y, z])
            self.assertEqual('minecraft:air', blocks[x, y + 1, z])
        for z in range(6):
            self.assertEqual('minecraft:air', blocks[13, 1, z])
            self.assertEqual('minecraft:air', blocks[13, 2, z])
        world = read('worlds/generation_1.json')
        self.assertIn('cobbleventure:placeholder/power_plant', json.dumps(world))

    def test_every_interior_marker_is_reachable_and_gate_blocks_boss(self):
        nbt = _read_minecraft_structure_root(build_power_plant_dungeon_nbt())
        blocks = {tuple(b['pos']): nbt['palette'][b['state']]['Name'] for b in nbt['blocks']}
        metadata = read('structures/dungeons/power_plant_interior.structure.json')
        anchors = metadata['anchors']
        walkable = {(x, z) for x in range(48) for z in range(48)
                    if blocks[x, 0, z] != 'minecraft:air'
                    and blocks[x, 1, z] == blocks[x, 2, z] == 'minecraft:air'}

        def reachable(allowed):
            visited = {(24, 4)}
            pending = deque(visited)
            while pending:
                x, z = pending.popleft()
                for p in ((x-1, z), (x+1, z), (x, z-1), (x, z+1)):
                    if p in allowed and p not in visited:
                        visited.add(p)
                        pending.append(p)
            return visited

        opened = reachable(walkable)
        for anchor in anchors:
            x, y, z = anchor['position']
            self.assertEqual(1, y)
            self.assertTrue((x, z) in opened, anchor['id'])
        closed = reachable(walkable - {(x, 33) for x in range(21, 27)})
        self.assertNotIn((24, 40), closed)
        for p in ((14, 16), (24, 22), (34, 27)):
            self.assertIn(p, closed)


if __name__ == '__main__':
    unittest.main()

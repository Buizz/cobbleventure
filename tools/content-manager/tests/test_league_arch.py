import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'tools/content-manager'))
import content_manager as cm

STRUCTURES = ROOT / 'content-projects/cobbleventure-main/content/structures'


class LeagueArchTests(unittest.TestCase):
    def test_modules_are_sparse_and_keep_the_road_clear(self):
        counts = []
        for name in ('league_arch', 'league_arch_2', 'league_arch_3'):
            n = cm._read_minecraft_structure_root((STRUCTURES / 'road_decorations' / (name+'.nbt')).read_bytes())
            counts.append(len(n['blocks']))
            self.assertEqual([20, 12], n['size'][:2])
            for b in n['blocks']:
                self.assertNotIn(n['palette'][b['state']]['Name'], ('minecraft:air', 'minecraft:dirt_path'))
                x, y, z = b['pos']
                self.assertFalse(7 <= x <= 12 and y <= 2, (name, b))
        self.assertEqual(1218, sum(counts))

    def test_building_is_cropped_but_door_and_return_remain(self):
        path = STRUCTURES / 'league/indigo_plateau.nbt'
        n = cm._read_minecraft_structure_root(path.read_bytes())
        self.assertEqual([60, 72, 45], n['size'])
        cells = {tuple(b['pos']): n['palette'][b['state']]['Name'] for b in n['blocks']}
        marker = json.loads(path.with_suffix('.structure.json').read_text())['anchors'][0]
        self.assertEqual('door', marker['id'])
        self.assertEqual('create:framed_glass_door', cells[tuple(marker['position'])])
        x,y,z = marker['safe_spawn']
        self.assertEqual('minecraft:air',cells[x,y,z])
        self.assertEqual('minecraft:air',cells[x,y+1,z])
        self.assertNotEqual('minecraft:air',cells[x,y-1,z])

    def test_route_authoring_accepts_league_arch_without_changing_encounters(self):
        route = cm._route_template('arch_test', '리그 아치길')
        route['route_type'] = 'league_arch'
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'arch_test.json'
            path.write_text(json.dumps(route), encoding='utf-8')
            _, issues = cm.validate_route_file(path)
        self.assertFalse([i for i in issues if i.severity == 'error'], issues)


if __name__ == '__main__':
    unittest.main()

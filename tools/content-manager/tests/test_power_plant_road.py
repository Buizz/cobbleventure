import json
from pathlib import Path
import unittest

CONTENT = Path(__file__).resolve().parents[3] / 'content-projects/cobbleventure-main/content'


class PowerPlantRoadTests(unittest.TestCase):
    def test_access_joins_existing_route_and_keeps_its_encounter_preset(self):
        world = json.loads((CONTENT / 'worlds/generation_1.json').read_text(encoding='utf-8'))
        access = next(c for c in world['connections'] if c['id'] == 'power_plant_access')
        route = next(c for c in world['connections'] if c['id'] == 'route_custom_16')
        plant = next(o for o in world['objects'] if o['id'] == access['to'])
        self.assertIn(access['cells'][0], route['cells'])
        self.assertEqual(plant['anchor'], access['cells'][-1])
        self.assertEqual(route['route_preset'], access['route_preset'])
        self.assertEqual('center', plant['properties']['placement_anchor'])
        self.assertTrue(plant['properties']['suppress_natural_spawns'])
        metadata = json.loads((CONTENT / 'structures/placeholder/power_plant.structure.json').read_text(encoding='utf-8'))
        self.assertTrue(any(a['id'] == 'dungeon_entry' and 'safe_spawn' in a for a in metadata['anchors']))


if __name__ == '__main__':
    unittest.main()

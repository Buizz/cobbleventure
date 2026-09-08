import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import generate_round_laboratory as lab
import content_manager as cm


class RoundLaboratoryTests(unittest.TestCase):
    def test_authored_formats_match_and_regeneration_is_deterministic(self):
        data = lab.OUTPUT.read_bytes()
        self.assertEqual(data, lab.serialize_structure(lab.SIZE, lab.build_blocks()))
        self.assertEqual(cm._read_minecraft_structure_root(data),
                         json.loads(lab.OUTPUT.with_suffix('.snbt').read_text()))

    def test_editor_registration_and_accessible_entry(self):
        resource = 'cobbleventure:placeholder/round_laboratory'
        viewer = cm.load_structure_viewer_catalog(lab.PROJECT, full_catalog={})
        self.assertIn(resource, viewer)
        payload = cm.building_settings_payload(lab.PROJECT)['structures'][resource]
        settings = payload['settings']
        self.assertTrue(settings['town_placement']['enabled'])
        self.assertEqual('round_laboratory', settings['town_placement']['id'])
        route = settings['door_routes']['exterior:door']
        interior = next(x for x in settings['interiors'] if x['key'] == route['space'])
        interior_path = cm.managed_structure_files(lab.PROJECT)[interior['structure']]
        doors = cm._structure_named_anchors(interior_path, {'door'})
        self.assertIn(route['door'], [door['label'] for door in doors])
        blocks = lab.build_blocks()
        self.assertEqual('minecraft:iron_door', blocks[21, 1, 3][0])
        for y in (1, 2):
            self.assertEqual('minecraft:air', blocks.get((21, y, 2), ('minecraft:air',))[0])
        self.assertEqual('minecraft:smooth_stone', blocks[21, 0, 2][0])
        for x, y, z in blocks:
            self.assertTrue(0 <= x < lab.SIZE[0] and 0 <= y < lab.SIZE[1] and 0 <= z < lab.SIZE[2])


if __name__ == '__main__':
    unittest.main()

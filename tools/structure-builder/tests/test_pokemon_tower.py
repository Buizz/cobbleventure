import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import generate_pokemon_tower as tower
sys.path.insert(0, str(tower.ROOT / 'tools/content-manager'))
from content_manager import _read_minecraft_structure_root


class PokemonTowerTests(unittest.TestCase):
    def test_square_podium_projects_beyond_upper_tower(self):
        blocks = tower.build_blocks()
        for x in range(2, 30):
            for z in range(3, 31):
                self.assertEqual('minecraft:smooth_stone', blocks[x, 6, z][0])
        for x, z in ((2, 3), (29, 3), (2, 30), (29, 30), (2, 16), (29, 16)):
            self.assertEqual('minecraft:light_gray_concrete', blocks[x, 3, z][0])
            self.assertEqual('minecraft:air', blocks[x, 7, z][0])
        self.assertNotEqual('minecraft:air', blocks[15, 7, 5][0])

    def test_saved_nbt_matches_generator_and_fits_envelope(self):
        data = tower.OUTPUT.read_bytes()
        self.assertEqual(data, tower.serialize_structure(tower.SIZE, tower.build_blocks()))
        root = _read_minecraft_structure_root(data)
        self.assertEqual([32, 66, 32], root['size'])
        for block in root['blocks']:
            self.assertTrue(all(0 <= p < n for p, n in zip(block['pos'], root['size'])))
            self.assertLess(block['state'], len(root['palette']))

    def test_entry_and_return_have_supported_two_block_clearance(self):
        blocks = tower.build_blocks()
        metadata = json.loads(tower.OUTPUT.with_suffix('.structure.json').read_text())
        anchor = metadata['anchors'][0]
        self.assertEqual('cobbleventure:entrance/pokemon_tower', anchor['entrance_id'])
        for x, y, z in (anchor['position'], anchor['safe_spawn']):
            self.assertNotEqual('minecraft:air', blocks[x, y - 1, z][0])
            for dy in (0, 1):
                self.assertEqual('minecraft:air', blocks[x, y + dy, z][0])
        for z in range(8):
            for y in (1, 2):
                self.assertEqual('minecraft:air', blocks[15, y, z][0])

    def test_dedicated_entry_art_is_packaged(self):
        content = tower.ROOT / 'content-projects/cobbleventure-main/content'
        dungeon = json.loads((content / 'dungeons/generation_1/rocket_pokemon_tower.json').read_text(encoding='utf-8'))
        texture = dungeon['entry_ui']['background_texture']
        self.assertEqual('cobbleventure_bootstrap:textures/gui/dungeons/pokemon_tower.png', texture)
        namespace, path = texture.split(':')
        asset = tower.ROOT / 'projects/cobbleventure-world-bootstrap/src/main/resources/assets' / namespace / path
        self.assertTrue(asset.read_bytes().startswith(b'\x89PNG\r\n\x1a\n'))


if __name__ == '__main__':
    unittest.main()

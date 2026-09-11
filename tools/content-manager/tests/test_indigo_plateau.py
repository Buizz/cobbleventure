"""The authored exterior must provide a walkable, single-portal approach."""
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'tools/content-manager'))
from content_manager import _read_minecraft_structure_root

CONTENT = ROOT / 'content-projects/cobbleventure-main/content'
RESOURCE = 'cobbleventure:league/indigo_plateau'


class IndigoPlateauTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = CONTENT / 'structures/league/indigo_plateau.nbt'
        cls.nbt = _read_minecraft_structure_root(path.read_bytes())
        cls.blocks = {tuple(b['pos']): cls.nbt['palette'][b['state']]['Name'] for b in cls.nbt['blocks']}
        cls.metadata = json.loads(path.with_suffix('.structure.json').read_text(encoding='utf-8'))

    def test_single_door_has_connected_barrier_and_safe_return(self):
        self.assertEqual([130, 72, 162], self.nbt['size'])
        self.assertEqual(1, len(self.metadata['anchors']))
        door = self.metadata['anchors'][0]
        self.assertEqual(('door', 'transition'), (door['id'], door['type']))
        self.assertEqual('minecraft:barrier', self.blocks[tuple(door['position'])])
        x, y, z = door['safe_spawn']
        self.assertNotEqual('minecraft:air', self.blocks[x, y-1, z])
        for head in (y, y+1):
            self.assertEqual('minecraft:air', self.blocks[x, head, z])
        self.assertGreater(z-door['position'][2], 3)
        self.assertEqual(120, sum(name == 'minecraft:barrier' for name in self.blocks.values()))

    def test_garden_approach_is_walkable_between_side_gates(self):
        steps = tuple(range(138,123,-2))+tuple(range(112,97,-2))+tuple(range(86,71,-2))
        self.assertEqual(24, len(steps))
        for z in range(58, 162):
            base = sum(z < step for step in steps)
            with self.subTest(z=z):
                for x in ((60,65,69) if z < 68 else (52,65,77)):
                    self.assertIn(self.blocks[x, base, z], ('minecraft:smooth_quartz','minecraft:smooth_sandstone'))
                    self.assertEqual('minecraft:quartz_stairs' if z in steps else 'minecraft:air', self.blocks[x, base+1, z])
                    self.assertEqual('minecraft:air', self.blocks[x, base+2, z])
                    self.assertEqual('minecraft:air', self.blocks[x, base+3, z])
        self.assertFalse(self.nbt.get('entities'))
        self.assertFalse(any('command_block' in name for name in self.blocks.values()))

    def test_side_screens_leave_the_central_skyline_open(self):
        for z,base in ((94,16),(146,0)):
            for x in range(53,77):
                for y in range(base+4,base+28):
                    self.assertEqual('minecraft:air', self.blocks[x,y,z])
        # The human scale figure belongs only to the 3D example, not the game.
        self.assertEqual('minecraft:air',self.blocks[68,1,150])

    def test_world_uses_new_exterior_and_only_lobby_is_linked(self):
        world = json.loads((CONTENT/'worlds/generation_1.json').read_text(encoding='utf-8'))
        self.assertEqual(RESOURCE, next(o for o in world['objects'] if o['id']=='indigo_plateau')['resource'])
        settings = json.loads((CONTENT/'catalogs/building-settings.json').read_text(encoding='utf-8'))['buildings']
        self.assertEqual({'exterior:door': {'space':'lobby','door':'entry'}}, settings[RESOURCE]['door_routes'])
        self.assertEqual(1, len(settings[RESOURCE]['interiors']))
        self.assertFalse(settings['cobbleventure:league/kanto_league']['interiors'])
        self.assertTrue((CONTENT/'structures/league/kanto_league.nbt').is_file())

    def test_rear_and_side_windows_and_retaining_terraces_are_authored(self):
        for pos in ((27,35,40),(101,35,40),(44,35,22)):
            self.assertEqual('minecraft:cyan_stained_glass',self.blocks[pos])
        # Each nested retaining wall has a planted terrace above solid stone.
        for x,z,height in ((8,60,8),(16,60,16),(24,70,24),(60,8,8)):
            self.assertEqual('minecraft:stone_bricks',self.blocks[x,height-1,z])
            self.assertEqual('minecraft:grass_block',self.blocks[x,height,z])


if __name__ == '__main__':
    unittest.main()

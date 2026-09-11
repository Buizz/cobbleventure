import json
import sys
import unittest
from collections import deque
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import generate_round_laboratory_interiors as lab
import content_manager as cm


class RoundLaboratoryInteriorTests(unittest.TestCase):
    def test_generated_assets_and_walkable_anchors(self):
        for key in ('lobby', *lab.ROOMS):
            with self.subTest(space=key):
                size, blocks, anchors = lab.build_space(key)
                path = lab.OUTPUT_DIR / f'round_laboratory_{key}.nbt'
                self.assertEqual(path.read_bytes(), lab.serialize_structure(size, blocks))
                self.assertEqual(cm._read_minecraft_structure_root(path.read_bytes()),
                                 json.loads(path.with_suffix('.snbt').read_text(encoding='utf-8')))
                self.assertEqual(anchors, json.loads(path.with_suffix('.structure.json').read_text(
                    encoding='utf-8'))['anchors'])
                walkable = {(x, z) for x in range(size[0]) for z in range(size[2])
                            if all(blocks[x, y, z][0] == 'minecraft:air' for y in (1, 2))
                            and blocks[x, 0, z][0] != 'minecraft:air'}
                entry = anchors[0]['safe_spawn']
                reached = {(entry[0], entry[2])}
                pending = deque(reached)
                while pending:
                    x, z = pending.popleft()
                    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        p = (x + dx, z + dz)
                        if p in walkable and p not in reached:
                            reached.add(p)
                            pending.append(p)
                for a in anchors:
                    x, y, z = a.get('safe_spawn', a['position'])
                    self.assertIn((x, z), walkable, a['label'])
                    self.assertIn((x, z), reached, a['label'])
                    if a['type'] == 'door':
                        dx, dy, dz = a['position']
                        self.assertEqual('minecraft:iron_door', blocks[dx, dy, dz][0])
                        self.assertEqual(1, abs(x - dx) + abs(z - dz))
                self.assertTrue(all(0 <= p[i] < size[i] for p in blocks for i in range(3)))

    def test_routes_resolve_and_all_rooms_have_researcher_slots(self):
        settings = json.loads((lab.PROJECT / 'content/catalogs/building-settings.json').read_text(
            encoding='utf-8'))['buildings']['cobbleventure:placeholder/round_laboratory']
        files = cm.managed_structure_files(lab.PROJECT)
        spaces = {'exterior': lab.PROJECT / 'content/structures/placeholder/round_laboratory.nbt'}
        spaces.update({i['key']: files[i['structure']] for i in settings['interiors']})
        self.assertEqual(7, len(spaces))
        self.assertEqual(6, len(settings['door_routes']))
        for source, target in settings['door_routes'].items():
            space, anchor = source.split(':')
            for s, a in ((space, anchor), (target['space'], target['door'])):
                self.assertIn(a, [v['label'] for v in cm._structure_named_anchors(spaces[s], {'door'})])
        for key, (_, _, researcher) in lab.ROOMS.items():
            self.assertEqual(key, settings['door_routes']['room_1:' + key]['space'])
            labels = [a['label'] for a in cm._structure_npc_labels(spaces[key])]
            self.assertIn(researcher, labels)
        self.assertIn('iv_researcher', [a['label'] for a in cm._structure_npc_labels(spaces['stats'])])
        for anchor in settings['fixed_npcs']:
            space, label = anchor.split(':', 1)
            self.assertIn(label, [a['label'] for a in cm._structure_npc_labels(spaces[space])])


if __name__ == '__main__':
    unittest.main()

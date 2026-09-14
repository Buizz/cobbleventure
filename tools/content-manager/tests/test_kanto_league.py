import copy
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'tools/content-manager'))
sys.path.insert(0, str(ROOT / 'tools/mod-builder'))
import content_manager as cm
import league_facilities as leagues
from cves import parse, compile_program, load_project_catalog

PROJECT = ROOT / 'content-projects/cobbleventure-main'
CONTENT = PROJECT / 'content'
OWNER = 'cobbleventure:league/indigo_plateau'


class KantoLeagueTests(unittest.TestCase):
    def test_lorelei_tera_target_reaches_generated_rct_profile(self):
        lorelei = leagues.generated_encounters(PROJECT)[0]['battle']
        exported = cm.export_rct_trainer({
            'id': lorelei['battle']['trainer_id'],
            'name': lorelei['name'],
            'battle': lorelei['battle'],
        })

        self.assertEqual('mamoswine', exported['ai']['data']['teraTarget'])

    def test_generated_trainers_emit_the_identity_used_by_battle_positioning(self):
        from generate_easy_npc_presets import npc_identity_tag_fragment
        encounters = leagues.generated_encounters(PROJECT)
        self.assertEqual(5, len(encounters))
        for encounter in encounters:
            identity = 'cobbleventure_npc/' + encounter['npc']['id'].replace(':', '/')
            self.assertIn(json.dumps(identity), npc_identity_tag_fragment(encounter['npc']))

    @classmethod
    def setUpClass(cls):
        cls.catalog = leagues.read(PROJECT / leagues.CATALOG)
        cls.league = cls.catalog['leagues'][0]
        cls.available = leagues.options(PROJECT)
        cls.authored = leagues.read(CONTENT / 'catalogs/building-settings.json')
        cls.runtime = leagues.compile_settings(cls.authored, cls.catalog, PROJECT)['buildings'][OWNER]
        cls.rooms = {}
        for room in [cls.league['lobby'], *cls.league['stages'], cls.league['hall']]:
            path = CONTENT / 'structures' / (room['structure'].split(':')[1] + '.nbt')
            nbt = cm._read_minecraft_structure_root(path.read_bytes())
            anchors = {a['id']: a for a in leagues.read(path.with_suffix('.structure.json'))['anchors']}
            blocks = {tuple(b['pos']): nbt['palette'][b['state']]['Name'] for b in nbt['blocks']}
            cls.rooms[room['structure']] = (nbt, anchors, blocks)

    def test_next_generation_uses_guide_and_an_enabled_house_start(self):
        runtime = self.runtime['runtime_league']
        self.assertEqual((1, 2, 'travel_test'),
                         (runtime['generation'], runtime['next_generation'], runtime['generation_travel_mode']))
        hall = runtime['rooms'][-1]
        self.assertEqual('hall_exit', hall['entry'])
        self.assertNotIn('advance', hall)
        _, anchors, blocks = self.rooms[hall['structure']]
        self.assertEqual('npc_position', anchors[hall['advance_npc']]['type'])
        self.assertEqual('cobbleventure:npc/hall_guide', self.runtime['fixed_npcs']['league_kanto_hall:npc'])
        self.assertNotIn('hall_entry', anchors)
        self.assertNotIn('hall_next_generation', anchors)
        start = next(g for g in leagues.read(CONTENT / 'catalogs/starter-settings.json')['generations'] if g['generation'] == 2)
        self.assertTrue(start['enabled'])
        self.assertEqual(('slot', 'room_1', 'start'),
                         (start['spawn']['mode'], start['spawn']['space'], start['spawn']['npc_slot']))
        world = leagues.read(CONTENT / 'worlds/generation_2.json')
        self.assertEqual(1, len(world['settlements']))
        town = leagues.read(CONTENT / 'settlements/generation_2/johto_starter_town.json')
        self.assertEqual(start['town'], town['id'])
        self.assertEqual('cobbleventure:generation_2', town['dimension'])
        self.assertEqual([start['spawn']['building']], [f['id'] for f in town['structure_profile']['facility_placements']])
        import build_data_mod
        layout = build_data_mod._compile_town_layout(town, ROOT)
        self.assertEqual([start['spawn']['building']], list(layout['facilities']))
        self.assertFalse(layout['houses'])
        self.assertFalse(layout['decorations'])
        dimension = leagues.read(ROOT / 'content-projects/cobbleventure-main/content/resources/cobbleventure-world-bootstrap/data/cobbleventure/dimension/generation_2.json')
        self.assertEqual('minecraft:flat', dimension['generator']['type'])
        self.assertEqual(69, sum(layer['height'] for layer in dimension['generator']['settings']['layers']))

    def test_next_generation_rejects_same_missing_or_unconfigured_targets(self):
        for value in (1, 999, '2'):
            data = copy.deepcopy(self.catalog)
            data['leagues'][0]['next_generation'] = value
            with self.subTest(value=value), self.assertRaises(ValueError):
                leagues.validate(data, self.available)
        for field, value in [('advance_npc', 'missing'), ('advance_npc', self.league['hall']['exit']), ('advance', self.league['hall']['exit'])]:
            data = copy.deepcopy(self.catalog)
            data['leagues'][0]['hall'][field] = value
            with self.assertRaises(ValueError):
                leagues.validate(data, self.available)
        available = copy.deepcopy(self.available)
        available['starter_generations'] = [1]
        with self.assertRaises(ValueError):
            leagues.validate(self.catalog, available)

    def test_hall_guide_requires_choice_and_compiles_travel_command(self):
        source = (CONTENT / 'events/cobbleventure/facilities/hall_guide.cves').read_text(encoding='utf-8')
        compiled = compile_program(parse(source), 'cobbleventure:event_script/facilities/hall_guide', load_project_catalog(PROJECT))
        self.assertIn('cobbleventure_league next_generation', json.dumps(compiled))
        self.assertIn('choice', source)
        self.assertIn('아직 이곳에 머무른다', source)
        self.assertIn('그대로 가지고 이동', source)
        hall = self.league['hall']
        _, anchors, blocks = self.rooms[hall['structure']]
        x, y, z = anchors[hall['entry']]['safe_spawn']
        self.assertEqual('minecraft:air', blocks.get((x, y, z), 'minecraft:air'))
        self.assertEqual('minecraft:air', blocks.get((x, y + 1, z), 'minecraft:air'))
        self.assertNotEqual('minecraft:air', blocks.get((x, y - 1, z), 'minecraft:air'))

    def test_single_exterior_door_generates_return_without_a_return_marker(self):
        authored = copy.deepcopy(self.authored)
        building = authored['buildings'][OWNER]
        building['door_routes'] = {'exterior:door': {'space': 'lobby', 'door': 'entry'}}
        compiled = leagues.compile_settings(authored, self.catalog)['buildings'][OWNER]
        self.assertEqual({'space': 'exterior', 'door': 'door'}, compiled['door_routes']['lobby:door'])
        self.assertEqual({'exterior:door'}, set(building['door_routes']))
        building['door_routes']['exterior:other_door'] = {'space': 'lobby', 'door': 'entry'}
        with self.assertRaisesRegex(ValueError, '입구 연결 하나'):
            leagues.compile_settings(authored, self.catalog)

    def test_lobby_challenge_transition_is_also_the_return_destination(self):
        lobby = self.league['lobby']
        self.assertEqual(('entry', 'door', 'entry'), (lobby['entry'], lobby['leave'], lobby['exit']))
        _, anchors, blocks = self.rooms[lobby['structure']]
        self.assertNotIn('leave', anchors)
        self.assertEqual('door', anchors['door']['type'])
        self.assertEqual('transition', anchors['entry']['type'])
        x, y, z = anchors['entry']['safe_spawn']
        self.assertEqual('minecraft:air', blocks.get((x, y, z), 'minecraft:air'))
        self.assertEqual('minecraft:air', blocks.get((x, y + 1, z), 'minecraft:air'))
        self.assertNotIn(blocks.get((x, y - 1, z), 'minecraft:air'), ('minecraft:air', 'minecraft:water', 'minecraft:lava'))
        routes = self.runtime['door_routes']
        self.assertEqual('lobby', routes['exterior:door']['space'])
        self.assertIn(routes['exterior:door']['door'], {'entry', 'door'})
        self.assertEqual({'space': 'exterior', 'door': 'door'}, routes['lobby:door'])
        self.assertEqual({'space': 'league_kanto_elite_1', 'door': 'entry'}, routes['lobby:entry'])

    def test_lobby_places_fly_instructor_and_three_league_residents_on_safe_markers(self):
        lobby = self.league['lobby']
        _, anchors, blocks = self.rooms[lobby['structure']]
        assignments = {
            'fly_instructor': 'cobbleventure:npc/rewards/field_move_fly_instructor',
            'league_attendant': 'cobbleventure:npc/league/indigo_plateau_attendant',
            'league_challenger': 'cobbleventure:npc/league/indigo_plateau_challenger',
            'league_veteran': 'cobbleventure:npc/league/indigo_plateau_veteran',
        }
        self.assertEqual(assignments, {key: lobby['fixed_npcs'][key] for key in assignments})
        for marker, npc in assignments.items():
            with self.subTest(marker=marker, npc=npc):
                anchor = anchors[marker]
                self.assertEqual('npc_position', anchor['type'])
                x, y, z = anchor['position']
                self.assertEqual('minecraft:air', blocks.get((x, y, z), 'minecraft:air'))
                self.assertEqual('minecraft:air', blocks.get((x, y + 1, z), 'minecraft:air'))
                self.assertNotEqual('minecraft:air', blocks.get((x, y - 1, z), 'minecraft:air'))
                self.assertEqual(npc, self.runtime['fixed_npcs'][f'lobby:{marker}'])

        world = leagues.read(CONTENT / 'worlds/generation_1.json')
        self.assertFalse(any(item.get('id') == 'indigo_plateau_fly_guide' for item in world['objects']))

    def test_external_authoring_connects_only_lobby_and_compilation_is_pure(self):
        exterior = self.authored['buildings'][OWNER]
        self.assertEqual([{'key': 'lobby', 'structure': self.league['lobby']['structure']}], exterior['interiors'])
        self.assertEqual({'exterior:door'}, set(exterior['door_routes']))
        self.assertFalse(exterior['fixed_npcs'])
        self.assertNotIn('runtime_league', exterior)
        self.assertEqual(7, len(self.runtime['interiors']))
        self.assertEqual(14, len(self.runtime['door_routes']))
        leagues.validate(self.catalog, self.available)

    def test_rooms_are_command_free_and_markers_are_safe(self):
        for resource, (nbt, anchors, blocks) in self.rooms.items():
            with self.subTest(room=resource):
                self.assertFalse(nbt.get('entities'))
                self.assertFalse(any('command_block' in name for name in blocks.values()))
                self.assertLessEqual(nbt['size'][0], 64)
                self.assertLessEqual(nbt['size'][2], 64)
                for anchor in anchors.values():
                    x, y, z = anchor['position']
                    self.assertTrue(all(0 <= v < size for v, size in zip([x, y, z], nbt['size'])))
                    if anchor['type'] == 'transition':
                        self.assertEqual('minecraft:barrier', blocks[x, y, z])
                    if anchor['type'] == 'arrival' or anchor['id'] == 'opponent':
                        x, y, z = anchor.get('safe_spawn', anchor['position'])
                        self.assertEqual('minecraft:air', blocks[x, y, z], anchor)
                        self.assertEqual('minecraft:air', blocks[x, y + 1, z], anchor)
                        self.assertNotIn(blocks[x, y - 1, z], ('minecraft:air', 'minecraft:water', 'minecraft:lava'))

    def test_route_chain_uses_configured_destinations_and_hall_then_lobby(self):
        rooms = self.runtime['runtime_league']['rooms']
        self.assertEqual([-1, 0, 1, 2, 3, 4, 5], [r['stage'] for r in rooms])
        self.assertEqual(8, len(self.runtime['runtime_league']['conditions']))
        for i, room in enumerate(rooms):
            target = rooms[(i + 1) % len(rooms)]
            route = self.runtime['door_routes'][f'{room["key"]}:{room["exit"]}']
            self.assertEqual({'space': target['key'], 'door': target['entry']}, route)
            self.assertEqual('transition', self.rooms[target['structure']][1][target['entry']]['type'])
            self.assertEqual(64, len(room['revision']))
        self.assertEqual('cobbleventure:interiors/hall_of_fame', rooms[-1]['structure'])

    def test_every_stage_uses_generic_npc_and_battle_position_markers(self):
        for room in self.runtime['runtime_league']['rooms'][1:-1]:
            self.assertEqual('opponent', room['npc_anchor'])
            self.assertEqual(room['npc'], self.runtime['fixed_npcs'][f'{room["key"]}:opponent'])
            self.assertNotIn(f'{room["key"]}:opponent_battle_player', self.runtime['fixed_npcs'])
        self.assertEqual('chansey level=30 female', self.runtime['fixed_pokemon']['lobby:chansey'])

    def test_every_stage_entry_is_a_portal_back_to_lobby_without_advancing(self):
        for room in self.runtime['runtime_league']['rooms'][1:-1]:
            _, anchors, blocks = self.rooms[room['structure']]
            self.assertEqual('transition', anchors['entry']['type'])
            self.assertEqual([31, 3, 58], anchors['entry']['position'])
            for x in range(30, 33):
                for y in range(2, 5):
                    self.assertEqual('minecraft:barrier', blocks[x, y, 58])
            self.assertEqual({'space': 'lobby', 'door': 'entry'}, self.runtime['door_routes'][room['key'] + ':entry'])
            x, y, z = anchors['entry']['safe_spawn']
            self.assertIn(blocks[x, y, z], ('minecraft:air', 'minecraft:light'))
            self.assertEqual('minecraft:air', blocks[x, y + 1, z])
            self.assertNotIn(blocks[x, y - 1, z], ('minecraft:air', 'minecraft:barrier', 'minecraft:water', 'minecraft:lava'))

    def test_events_can_replay_without_permanent_flags_blocking_a_new_run(self):
        catalog = load_project_catalog(PROJECT)
        for encounter in leagues.generated_encounters(PROJECT):
            source = encounter['script']
            compiled = compile_program(parse(source), encounter['script_id'], catalog)
            self.assertNotIn('page when', source)
            self.assertIn(encounter['flag'], json.dumps(compiled))
            self.assertIn(encounter['battle']['id'], json.dumps(compiled))
            for directory, suffix in [('source', '.json'), ('battles', '.json'), ('events/cobbleventure', '.cves')]:
                self.assertFalse((CONTENT / directory / (encounter['path'] + suffix)).exists())

    def test_inline_trainer_changes_generate_npc_team_and_dialogue_together(self):
        data = copy.deepcopy(self.catalog)
        trainer = data['leagues'][0]['stages'][0]['trainer']
        trainer['name']['ko_kr'] = '테스트 트레이너'
        trainer['dialogue']['challenge'] = '첫 줄 "대사"\n둘째 줄'
        trainer['battle']['team'][0]['level'] = 42
        leagues.validate(data, self.available)
        generated = leagues.generated_encounters(data=data)[0]
        self.assertEqual('테스트 트레이너', generated['npc']['name']['ko_kr'])
        self.assertEqual(42, generated['battle']['battle']['team'][0]['level'])
        self.assertEqual('cobbleventure:npc/league/kanto_elite_1', generated['npc']['id'])
        compiled = compile_program(parse(generated['script']), generated['script_id'], load_project_catalog(PROJECT))
        self.assertIn('둘째 줄', json.dumps(compiled, ensure_ascii=False))
        trainer['battle']['team'] = []
        with self.assertRaises(ValueError): leagues.validate(data, self.available)
        trainer['battle']['team'] = [{'species': 'cobblemon:pikachu', 'level': 101}]
        with self.assertRaises(ValueError): leagues.validate(data, self.available)

    def test_project_emits_generated_league_scripts_and_bindings(self):
        from cves.project import compile_project
        build = compile_project(PROJECT)
        scripts = {a.relative_path.as_posix(): a.document for a in build.scripts}
        bindings = {a.relative_path.as_posix(): a.document for a in build.bindings}
        for encounter in leagues.generated_encounters(PROJECT):
            self.assertIn('cobbleventure/event_script/' + encounter['path'] + '.json', scripts)
            self.assertEqual(encounter['binding'], bindings['cobbleventure/npc_event_binding/' + encounter['path'] + '.json'])

    def test_both_progress_modes_and_invalid_mode(self):
        data = copy.deepcopy(self.catalog)
        data['leagues'][0]['mode'] = 'relay'
        leagues.validate(data, self.available)
        self.assertEqual('relay', leagues.compile_settings(self.authored, data)['buildings'][OWNER]['runtime_league']['mode'])
        data['leagues'][0]['mode'] = 'typo'
        with self.assertRaises(ValueError): leagues.validate(data, self.available)

    def test_reject_missing_marker_duplicate_room_and_nonbattle_npc(self):
        for field, value in [('entry', 'missing'), ('structure', self.league['lobby']['structure']), ('npc', 'cobbleventure:npc/pokemon_center_nurse')]:
            data = copy.deepcopy(self.catalog)
            data['leagues'][0]['stages'][0][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                leagues.validate(data, self.available)

    def test_reject_manual_stage_links_and_duplicate_placement_ownership(self):
        settings = copy.deepcopy(self.authored)
        settings['buildings'][OWNER]['interiors'].append({'key': 'cheat', 'structure': self.league['hall']['structure']})
        with self.assertRaises(ValueError): leagues.compile_settings(settings, self.catalog)
        settings = copy.deepcopy(self.authored)
        settings['buildings'][OWNER]['fixed_npcs']['lobby:nurse'] = 'cobbleventure:npc/pokemon_center_nurse'
        with self.assertRaises(ValueError): leagues.compile_settings(settings, self.catalog)

    def test_missing_return_uses_the_existing_external_entrance(self):
        settings = copy.deepcopy(self.authored)
        settings['buildings'][OWNER]['door_routes'].pop('lobby:door', None)
        compiled = leagues.compile_settings(settings, self.catalog)['buildings'][OWNER]
        self.assertEqual({'space': 'exterior', 'door': 'door'}, compiled['door_routes']['lobby:door'])

    def test_graph_roundtrip_keeps_only_lobby_connections(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            gym = leagues.read(CONTENT / 'catalogs/gyms.json')['gyms'][0]
            resources = [OWNER, self.league['lobby']['structure'], gym['exterior']['structure']]
            resources += [module['structure'] for module in gym['interior']['modules']]
            for resource in resources:
                for suffix in ['.nbt', '.structure.json']:
                    relative = Path('structures') / (resource.split(':')[1] + suffix)
                    target = root / 'content' / relative
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(CONTENT / relative, target)
            path = root / 'content/catalogs/building-settings.json'
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps({'schema_version': 1, 'buildings': {OWNER: self.authored['buildings'][OWNER]}}), encoding='utf-8')
            (path.parent / 'gyms.json').write_text(json.dumps({'schema_version': 1, 'gyms': [gym], 'leagues': []}), encoding='utf-8')
            graphs = cm.space_connections_payload(root)['graphs']
            graph = next(g for g in graphs if g['owner'] == OWNER)
            self.assertEqual(2, len(graph['nodes']))
            # The editor saves every graph, including gym transition anchors.
            self.assertTrue(any(g['kind'] == 'gym' for g in graphs))
            issues = cm.save_space_connections(root, {'schema_version': 1, 'graphs': graphs})
            self.assertFalse([i for i in issues if i.level == 'error'], issues)
            saved = leagues.read(path)['buildings'][OWNER]
            self.assertEqual({'exterior:door'}, set(saved['door_routes']))
            self.assertEqual(self.authored['buildings'][OWNER].get('terrain_preparation', 'none'),
                             saved['terrain_preparation'])

    def test_external_lobby_door_preserves_challenge_entry_and_automatic_return(self):
        settings = copy.deepcopy(self.authored)
        building = settings['buildings'][OWNER]
        building['door_routes'] = {'exterior:door': {'space': 'lobby', 'door': self.league['lobby']['leave']}}
        compiled = leagues.compile_settings(settings, self.catalog)['buildings'][OWNER]
        self.assertEqual(self.league['lobby']['leave'], compiled['door_routes']['exterior:door']['door'])
        self.assertEqual({'space': 'exterior', 'door': 'door'}, compiled['door_routes']['lobby:' + self.league['lobby']['leave']])
        self.assertEqual(self.league['lobby']['entry'], compiled['runtime_league']['rooms'][0]['entry'])
        self.assertEqual('league_kanto_elite_1', compiled['door_routes']['lobby:' + self.league['lobby']['exit']]['space'])


if __name__ == '__main__':
    unittest.main()

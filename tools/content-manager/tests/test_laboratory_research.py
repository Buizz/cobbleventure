import copy
import json
import sys
import unittest
import threading
import urllib.request
import urllib.error
from http.server import ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'tools/content-manager'))
import laboratory_research as research
from cves.parser import parse
from cves.semantic import validate


class LaboratoryResearchTests(unittest.TestCase):
    def setUp(self):
        self.content = ROOT / 'content-projects/cobbleventure-main/content'
        self.data = json.loads((self.content / 'catalogs/laboratory-research.json').read_text(encoding='utf-8'))

    def test_config_and_exclusive_z_conditions(self):
        research.validate(self.data)
        rows = {r['item']: r for r in self.data['z_crystals']}
        self.assertEqual('Volt Tackle', rows['mega_showdown:pikanium_z']['move'])
        self.assertEqual(['Pikachu'], rows['mega_showdown:pikanium_z']['users'])
        self.assertEqual('', rows['mega_showdown:normalium_z']['move'])
        self.assertEqual(18, sum(not r['move'] for r in rows.values()))

    def test_invalid_prices_and_incomplete_conditions_rejected(self):
        for price in [-1, True, 1.5, 1000001]:
            data = copy.deepcopy(self.data)
            data['costs']['iv_point'] = price
            with self.assertRaises(ValueError): research.validate(data)
        data = copy.deepcopy(self.data)
        data['z_crystals'][0]['users'] = ['Pikachu']
        data['z_crystals'][0]['move'] = ''
        with self.assertRaises(ValueError): research.validate(data)
        data = copy.deepcopy(self.data)
        data['z_crystals'].append(data['z_crystals'][0])
        with self.assertRaises(ValueError): research.validate(data)

    def test_npcs_and_scripts_are_connected_to_rooms(self):
        settings = json.loads((self.content / 'catalogs/building-settings.json').read_text(encoding='utf-8'))
        assignments = settings['buildings']['cobbleventure:placeholder/round_laboratory']['fixed_npcs']
        for kind in ('z_move', 'mega', 'dynamax', 'stats'):
            key = 'research_' + kind
            npc = json.loads((self.content / f'source/facilities/{key}.json').read_text(encoding='utf-8'))
            script = (self.content / f'events/cobbleventure/facilities/{key}.cves').read_text(encoding='utf-8')
            self.assertEqual((), validate(parse(script, key + '.cves')))
            self.assertIn('open_research', script)
            self.assertIn(npc['id'], assignments.values())
            self.assertFalse(npc['placement_profile']['automatic_town_placement'])
        self.assertEqual((), validate(parse('event interact { page default { open_research } }', 'research.cves')))
        self.assertTrue(validate(parse('event interact { page default { open_research 1 } }', 'research.cves')))

    def test_editor_serves_settings_and_rejects_invalid_save_without_writing(self):
        import content_manager
        before = (self.content / 'catalogs/laboratory-research.json').read_bytes()
        server = ThreadingHTTPServer(('127.0.0.1', 0), content_manager.create_handler(ROOT))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            url = f'http://127.0.0.1:{server.server_port}'
            for path in ('/research.html', '/research.js', '/api/laboratory-research'):
                with urllib.request.urlopen(url + path) as response:
                    self.assertEqual(200, response.status)
            request = urllib.request.Request(url + '/api/laboratory-research', data=b'{}',
                headers={'Content-Type': 'application/json'}, method='PUT')
            with self.assertRaises(urllib.error.HTTPError) as error:
                urllib.request.urlopen(request)
            self.assertEqual(400, error.exception.code)
            self.assertEqual(before, (self.content / 'catalogs/laboratory-research.json').read_bytes())
        finally:
            server.shutdown(); server.server_close(); thread.join()


if __name__ == '__main__': unittest.main()

import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools/content-manager"))
import content_manager
from cves.parser import parse
from cves.semantic import validate
from cves.diagnostics import CvesSyntaxError


class FossilLaboratoryTests(unittest.TestCase):
    def test_three_indoor_npcs_have_valid_scripts_and_researcher_can_reach_machine(self):
        content = ROOT / "content-projects/cobbleventure-main/content"
        settings = json.loads((content / "catalogs/building-settings.json").read_text(encoding="utf-8"))
        building = settings["buildings"]["cobbleventure:placeholder/fossil_laboratory"]
        assignments = building["fixed_npcs"]
        self.assertEqual({"room_1:npc1", "room_1:npc2", "room_1:npc3"}, set(assignments))
        for anchor, identifier in assignments.items():
            key = identifier.rsplit("/", 1)[1]
            npc = json.loads((content / f"source/facilities/{key}.json").read_text(encoding="utf-8"))
            script = (content / f"events/cobbleventure/facilities/{key}.cves").read_text(encoding="utf-8")
            self.assertEqual(identifier, npc["id"])
            self.assertEqual((), validate(parse(script, f"{key}.cves")))
            self.assertEqual(key == "fossil_researcher", "open_fossil" in script)
            self.assertEqual("stationary", npc["npc"]["behavior"]["movement"])
        sidecar = json.loads((content / "structures/interiors/fossil_laboratory.structure.json").read_text())
        position = next(a["position"] for a in sidecar["anchors"] if a["id"] == "npc3")
        nbt = content_manager._read_minecraft_structure_root(
            (content / "structures/interiors/fossil_laboratory.nbt").read_bytes())
        tanks = [b for b in nbt["blocks"] if nbt["palette"][b["state"]]["Name"] == "cobblemon:restoration_tank"]
        self.assertTrue(any(abs(b["pos"][0] - position[0]) <= 12 and abs(b["pos"][2] - position[2]) <= 12 for b in tanks))

    def test_open_fossil_rejects_arguments_and_await(self):
        self.assertEqual((), validate(parse('event interact { page default { open_fossil } }', 'fossil.cves')))
        self.assertTrue(validate(parse('event interact { page default { open_fossil 1 } }', 'fossil.cves')))
        with self.assertRaises(CvesSyntaxError):
            parse('event interact { page default { await open_fossil } }', 'fossil.cves')

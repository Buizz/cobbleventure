import json
import sys
import unittest
from collections import deque
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools/mod-builder"))
sys.path.insert(0, str(ROOT / "tools/content-manager"))
import generate_gym_rooms as rooms
from content_manager import _minecraft_structure_parts


class GymRoomTests(unittest.TestCase):
    def test_all_catalog_gyms_use_three_connected_rooms_and_separate_staff(self):
        catalog = json.loads((rooms.CONTENT / "catalogs/gyms.json").read_text(encoding="utf-8"))
        for gym in catalog["gyms"]:
            with self.subTest(gym=gym["id"]):
                modules = gym["interior"]["modules"]
                self.assertEqual(["lobby", "gimmick", "arena"], [m["id"] for m in modules])
                self.assertTrue(modules[2]["structure"].endswith("arena_" + gym["theme"]))
                self.assertEqual([("exterior:door", "lobby:door"), ("lobby:next", "gimmick:door"), ("gimmick:next", "arena:door")],
                                 [(c["from"], c["to"]) for c in gym["interior"]["connections"]])
                labels = {}
                bounds = []
                for module in modules:
                    name = module["structure"].split(":")[1]
                    metadata = json.loads((rooms.CONTENT / "structures" / (name + ".structure.json")).read_text(encoding="utf-8"))
                    for a in metadata["anchors"]:
                        if a["type"] == "npc_position":
                            self.assertNotIn(a["label"], labels)
                            labels[a["label"]] = module["id"]
                    bounds.append((module["position"][2], module["position"][2] + metadata["interior"]["depth"]))
                self.assertEqual("arena", labels[gym["staff"]["leader"]["anchor"]])
                for trainer in gym["staff"]["trainers"]:
                    self.assertEqual("gimmick", labels[trainer["anchor"]])
                self.assertTrue(all(a[1] <= b[0] for a, b in zip(bounds, bounds[1:])))

    def test_variants_preserve_every_record_outside_terrain_palette(self):
        import gzip
        from content_manager import _read_minecraft_structure_root, _minecraft_structure_tag_spans
        source = (rooms.DIRECTORY / "arena_rock.nbt").read_bytes()
        base = _read_minecraft_structure_root(source)
        raw = gzip.decompress(source)
        _, start, end = _minecraft_structure_tag_spans(raw)["palette"]
        original_metadata = json.loads((rooms.DIRECTORY / "arena_rock.structure.json").read_text(encoding="utf-8"))
        terrain_count = sum(base["palette"][b["state"]]["Name"] in rooms.TERRAIN for b in base["blocks"])
        self.assertGreater(terrain_count, 0)
        for theme in rooms.THEMES:
            with self.subTest(theme=theme):
                path = rooms.DIRECTORY / f"arena_{theme}.nbt"
                variant = _read_minecraft_structure_root(path.read_bytes())
                changed = gzip.decompress(path.read_bytes())
                _, a, b = _minecraft_structure_tag_spans(changed)["palette"]
                self.assertEqual(raw[:start] + raw[end:], changed[:a] + changed[b:])
                for before, after in zip(base["palette"], variant["palette"]):
                    if before["Name"] not in rooms.TERRAIN:
                        self.assertEqual(before, after)
                    else:
                        self.assertNotEqual(before["Name"], after["Name"])
                metadata = json.loads(path.with_suffix(".structure.json").read_text(encoding="utf-8"))
                self.assertEqual(original_metadata["anchors"], metadata["anchors"])
                self.assertEqual(path.read_bytes(), rooms.variant_nbt(source, theme))

    def test_authored_transition_and_battle_markers_have_physical_support(self):
        from content_manager import _read_minecraft_structure_root
        for name in ["arena_rock"] + ["arena_" + t for t in rooms.THEMES]:
            with self.subTest(room=name):
                path = rooms.DIRECTORY / (name + ".nbt")
                root = _read_minecraft_structure_root(path.read_bytes())
                blocks = {tuple(b["pos"]): root["palette"][b["state"]]["Name"] for b in root["blocks"]}
                anchors = json.loads(path.with_suffix(".structure.json").read_text(encoding="utf-8"))["anchors"]
                for anchor in anchors:
                    if anchor["type"] == "transition":
                        self.assertEqual("minecraft:barrier", blocks[tuple(anchor["position"])])
                    x, y, z = anchor.get("safe_spawn", anchor["position"])
                    self.assertNotIn(blocks[x, y - 1, z], {"minecraft:air", "minecraft:barrier"})
                    self.assertEqual("minecraft:air", blocks[x, y, z])
                    self.assertEqual("minecraft:air", blocks[x, y + 1, z])


if __name__ == "__main__":
    unittest.main()

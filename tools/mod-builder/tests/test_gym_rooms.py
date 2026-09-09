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

    def test_exported_rooms_have_walkable_connected_doors_npcs_and_battle_spots(self):
        names = ["shared_lobby", "shared_gimmick"] + ["arena_" + t for t in rooms.THEMES]
        for name in names:
            with self.subTest(room=name):
                path = rooms.CONTENT / "structures/interiors/gyms" / (name + ".nbt")
                size, palette, entries = _minecraft_structure_parts(path.read_bytes())
                self.assertEqual((32, 12, 32), tuple(size))
                blocks = {tuple(e["pos"]): palette[e["state"]] for e in entries}
                metadata = json.loads(path.with_suffix(".structure.json").read_text(encoding="utf-8"))
                walkable = {(x, z) for x in range(32) for z in range(32)
                            if blocks[x, 0, z] not in {"minecraft:water", "minecraft:air", "minecraft:magma_block"}
                            and all(blocks[x, y, z] == "minecraft:air" for y in (1, 2))}
                visited = {(15, 3)}
                queue = deque(visited)
                while queue:
                    x, z = queue.popleft()
                    for p in ((x - 1, z), (x + 1, z), (x, z - 1), (x, z + 1)):
                        if p in walkable and p not in visited:
                            visited.add(p)
                            queue.append(p)
                for a in metadata["anchors"]:
                    for position in [a["position"]] + ([a["safe_spawn"]] if "safe_spawn" in a else []):
                        x, y, z = position
                        self.assertEqual(1, y)
                        self.assertIn((x, z), visited, a["label"])
                if name.startswith("arena_"):
                    self.assertEqual(["leader"], [a["label"] for a in metadata["anchors"] if a["type"] == "npc_position"])
                    self.assertEqual({"battle_player", "battle_leader"}, {a["label"] for a in metadata["anchors"] if a["type"] == "arrival"})


if __name__ == "__main__":
    unittest.main()

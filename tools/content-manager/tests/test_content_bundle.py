import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build_content_bundle as bundle


class ContentBundleTests(unittest.TestCase):
    def test_rejects_non_object_json_in_cobblemon_species_payload(self):
        with tempfile.TemporaryDirectory() as temporary:
            bundle_root = Path(temporary)
            invalid = bundle_root / "data/cobblemon/species/.internal-manifest.json"
            invalid.parent.mkdir(parents=True)
            invalid.write_text("[]\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Cobblemon species JSON은 객체여야 합니다"):
                bundle.validate_cobblemon_species_payload(bundle_root)

    def test_indexes_preserve_legacy_objectives_and_settlement_names(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            content, output = root / "content", root / "output"
            bundle.write_json(content / "source/trainer.json", {
                "id": "test:npc", "name": {"ko_kr": "트레이너"},
                "events": [{"commands": [{"type": "grant_field_move", "move": "surf"}]}],
                "condition": {"type": "has_item", "item": "minecraft:apple", "count": 2},
            })
            bundle.write_json(content / "settlements/generation_1/town.json", {
                "id": "test:town", "display_name": {"en-US": "Town", "ko_kr": "마을"},
                "npc_placement": {"trainer_slots": [{"members": [{"npc_profile": "test:npc"}]}]},
            })
            bundle.generate_indexes(content, output)
            index = json.loads((output / "data/cobbleventure_player_menu/map/field-move-npcs.json").read_text(encoding="utf-8"))
            self.assertEqual(index["settlements"]["test:town"], [{"name": "트레이너", "moves": ["surf"]}])
            names = json.loads((output / "assets/cobbleventure_adventure/event-resource-names.json").read_text(encoding="utf-8"))
            self.assertEqual(names["resources"]["test:town"]["en_us"], "Town")
            conditions = json.loads((output / "data/cobbleventure_player_menu/bag/bag-conditions.json").read_text(encoding="utf-8"))
            objective = "cvi_" + hashlib.sha1(b"minecraft:apple\x002").hexdigest()[:12]
            self.assertEqual(conditions["conditions"][objective], {"type": "item", "item": "minecraft:apple", "count": 2})

    def test_publish_removes_deleted_content_and_protects_unowned_directories(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            target, stage = root / "installed", root / "stage"
            bundle.write_json(target / "content-manifest.json", {})
            bundle.write_json(target / "deleted-trainer.json", {})
            bundle.write_json(stage / "content-manifest.json", {})
            bundle.write_json(stage / "new-trainer.json", {})
            bundle.publish(stage, target, root)
            self.assertFalse((target / "deleted-trainer.json").exists())
            self.assertTrue((target / "new-trainer.json").is_file())
            unowned = root / "unowned"
            unowned.mkdir()
            with self.assertRaises(ValueError):
                bundle.publish(root / "other-stage", unowned, root)
            with self.assertRaises(ValueError):
                bundle.publish(root / "other-stage", root.parent / "outside", root)

    def test_publish_rolls_back_when_staging_is_missing(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            target = root / "installed"
            bundle.write_json(target / "content-manifest.json", {"original": True})
            with self.assertRaises(OSError):
                bundle.publish(root / "missing", target, root)
            self.assertTrue(json.loads((target / "content-manifest.json").read_text())["original"])


if __name__ == "__main__":
    unittest.main()

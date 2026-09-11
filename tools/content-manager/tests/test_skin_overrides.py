from __future__ import annotations

import base64
import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest
import zlib
import binascii

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import skin_overrides


def png_64() -> bytes:
    def chunk(kind: bytes, payload: bytes) -> bytes:
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", binascii.crc32(kind + payload) & 0xFFFFFFFF)
    rows = b"".join(b"\0" + bytes((20, 40, 60, 255)) * 64 for _ in range(64))
    return (
        skin_overrides.PNG_SIGNATURE
        + chunk(b"IHDR", struct.pack(">IIBBBBB", 64, 64, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(rows))
        + chunk(b"IEND", b"")
    )


class SkinOverrideTests(unittest.TestCase):
    def test_save_catalog_apply_and_remove(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            project = root / "content-projects" / "test"
            catalog = project / "content" / "catalogs" / "trainer-skin-sources.json"
            catalog.parent.mkdir(parents=True)
            catalog.write_text(json.dumps({"skins": [{
                "resource": "cobbleventure:trainer_skin/tester",
                "title": "Tester", "target_model": "slim",
            }]}), encoding="utf-8")
            roster = catalog.with_name("trainer-roster.json")
            roster.write_text(json.dumps({"league_characters": [{
                "id": "cobbleventure:character/brock", "role": "gym_leader",
                "generation": 1, "display_name": {"ko_kr": "웅", "en_us": "Brock"},
                "appearance": {"resource": "rctmod:trainers/single/kanto_brock"},
            }]}), encoding="utf-8")
            data = png_64()
            decoded = skin_overrides.decode_data_url(
                "data:image/png;base64," + base64.b64encode(data).decode("ascii")
            )
            saved = skin_overrides.save_override(
                root, "cobbleventure:trainer_skin/tester", decoded
            )
            self.assertTrue(saved.is_file())
            payload = skin_overrides.catalog_payload(root, project)
            self.assertEqual(2, len(payload["items"]))
            self.assertTrue(next(item for item in payload["items"] if item["resource"].endswith("/tester"))["override"])
            bundle = root / "bundle"
            self.assertEqual(
                ["cobbleventure:trainer_skin/tester"],
                skin_overrides.apply_to_bundle(root, bundle),
            )
            self.assertEqual(
                data,
                (bundle / "assets/cobbleventure/textures/entity/trainer/tester.png").read_bytes(),
            )
            skin_overrides.remove_override(root, "cobbleventure:trainer_skin/tester")
            self.assertFalse(saved.exists())

    def test_rct_gym_skin_uses_its_resource_pack_path(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            data = png_64()
            resource = "rctmod:trainers/single/kanto_brock"
            skin_overrides.save_override(root, resource, data)
            bundle = root / "bundle"
            self.assertEqual([resource], skin_overrides.apply_to_bundle(root, bundle))
            self.assertEqual(
                data,
                (bundle / "assets/rctmod/textures/trainers/single/kanto_brock.png").read_bytes(),
            )

    def test_rejects_wrong_size_and_path_traversal(self) -> None:
        bad = bytearray(png_64())
        bad[16:20] = struct.pack(">I", 32)
        with self.assertRaisesRegex(ValueError, "64×64"):
            skin_overrides.validate_png(bytes(bad))
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaises(ValueError):
                skin_overrides.override_path(
                    Path(temporary), "cobbleventure:trainer_skin/../../outside"
                )


if __name__ == "__main__":
    unittest.main()

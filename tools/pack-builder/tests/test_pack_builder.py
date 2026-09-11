from __future__ import annotations

import importlib.util
import io
import json
import struct
import sys
import tempfile
import unittest
import zipfile
import zlib
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "pack_builder.py"
SPEC = importlib.util.spec_from_file_location("pack_builder", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
pack_builder = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = pack_builder
SPEC.loader.exec_module(pack_builder)


class PackBuilderTests(unittest.TestCase):
    def test_managed_artifact_names_and_versions_are_independent(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            profile_path = self._fixture(root)
            profile = json.loads((root / profile_path).read_text())
            profile["managed_artifact_versions"] = True
            (root / profile_path).write_text(json.dumps(profile))
            pack_builder.artifact_versions.save(root, {"jar_version": "2.0.0", "content_version": "3.0.0"})
            content = root / "pack/overrides/smoke/config/cobbleventure/content"
            content.mkdir(parents=True)
            (content / "content-manifest.json").write_text('{"version":"3.0.0"}')
            full = pack_builder.build_pack(root, profile_path)
            mods = pack_builder.build_pack(root, profile_path, mods_only=True)
            self.assertEqual("cobbleventure-full-jar-2.0.0-content-3.0.0.zip", full.name)
            self.assertEqual("cobbleventure-mods-2.0.0.zip", mods.name)
            before = mods.read_bytes()
            pack_builder.artifact_versions.save(root, {"jar_version": "2.0.0", "content_version": "4.0.0"})
            self.assertEqual(before, pack_builder.build_pack(root, profile_path, mods_only=True).read_bytes())
            with self.assertRaisesRegex(pack_builder.PackError, "콘텐츠"):
                pack_builder.build_pack(root, profile_path)
            self.assertTrue(full.is_file())

    def test_mods_only_pack_excludes_content_and_uses_a_separate_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            profile_path = self._fixture(root)
            overrides = root / "pack/overrides/smoke"
            (overrides / "mods").mkdir()
            (overrides / "mods/engine.jar").write_bytes(b"engine")
            (overrides / "mods/notes.txt").write_text("not a mod")
            content = overrides / "config/cobbleventure/content"
            content.mkdir(parents=True)
            (content / "content-manifest.json").write_text("{}")
            full = pack_builder.build_pack(root, profile_path)
            full_before = full.read_bytes()
            mods = pack_builder.build_pack(root, profile_path, mods_only=True)
            self.assertNotEqual(full, mods)
            self.assertEqual(full_before, full.read_bytes())
            with zipfile.ZipFile(full) as archive:
                self.assertIn("overrides/config/cobbleventure/content/content-manifest.json", archive.namelist())
            with zipfile.ZipFile(mods) as archive:
                names = archive.namelist()
                self.assertIn("overrides/mods/engine.jar", names)
                self.assertNotIn("overrides/mods/notes.txt", names)
                self.assertFalse(any(name.startswith("overrides/config/") for name in names))

    def test_mods_only_does_not_require_local_content_assets(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            profile_path = self._fixture(root)
            profile = json.loads((root / profile_path).read_text())
            profile["local_resourcepacks"] = [{"source": "local-assets/missing.zip", "target": "missing.zip", "pack_format": 34}]
            (root / profile_path).write_text(json.dumps(profile))
            self.assertTrue(pack_builder.build_pack(root, profile_path, mods_only=True).is_file())

    def _write_png(self, path: Path, width: int = 400, height: int = 400) -> None:
        def chunk(name: bytes, data: bytes) -> bytes:
            payload = name + data
            return struct.pack(">I", len(data)) + payload + struct.pack(">I", zlib.crc32(payload))

        rows = b"".join(b"\x00" + b"\x20\x80\xc0" * width for _ in range(height))
        png = b"\x89PNG\r\n\x1a\n"
        png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(rows))
        png += chunk(b"IEND", b"")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(png)

    def _fixture(self, root: Path) -> Path:
        overrides = root / "pack" / "overrides" / "smoke" / "config"
        overrides.mkdir(parents=True)
        (overrides / "marker.txt").write_text("smoke\n", encoding="utf-8")
        self._write_png(root / "pack" / "assets" / "icon.png")
        profile_path = root / "pack" / "profiles" / "smoke.json"
        profile_path.parent.mkdir(parents=True)
        profile = {
            "schema_version": 1,
            "profile_id": "smoke",
            "name": "Smoke",
            "version": "0.0.1",
            "author": "Test",
            "purpose": "test-fixture",
            "production_ready": False,
            "notice": "Test only",
            "icon": "pack/assets/icon.png",
            "minecraft": {
                "version": "1.21.1",
                "mod_loader": {
                    "id": "neoforge-21.1.248",
                    "primary": True,
                },
            },
            "files": [],
            "overrides_directory": "pack/overrides/smoke",
            "output": "dist/smoke.zip",
        }
        profile_path.write_text(json.dumps(profile), encoding="utf-8")
        return profile_path.relative_to(root)

    def _write_resource_pack(self, path: Path, pack_format: int = 4) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("pack.mcmeta", json.dumps({
                "pack": {"pack_format": pack_format, "description": "Test font"}
            }))
            archive.writestr("assets/minecraft/font/default.json", "{}")

    def _write_dependency_lock(self, root: Path) -> None:
        path = root / "pack" / "dependencies.lock.json"
        path.write_text(json.dumps({
            "schema_version": 1,
            "minecraft": {
                "version": "1.21.1",
                "loader": {"type": "neoforge", "version": "21.1.248"},
            },
            "mods": [
                {"id": "server_mod", "display_name": "Server Mod", "version": "1.0",
                 "side": "server", "enabled": True,
                 "curseforge": {"project_id": 10, "file_id": 11}},
                {"id": "common_mod", "display_name": "Common Mod", "version": "2.0",
                 "side": "both", "enabled": True,
                 "curseforge": {"project_id": 20, "file_id": 21}},
                {"id": "client_mod", "display_name": "Client Mod", "version": "3.0",
                 "side": "client", "enabled": True,
                 "curseforge": {"project_id": 30, "file_id": 31}},
            ],
        }), encoding="utf-8")

    def test_builds_and_validates_minimal_curseforge_zip(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            profile_path = self._fixture(root)
            output = pack_builder.build_pack(root, profile_path)
            manifest = pack_builder.validate_pack(
                output,
                root=root,
                profile_path=profile_path,
            )
            with zipfile.ZipFile(output) as archive:
                names = archive.namelist()
                pack_info = json.loads(
                    archive.read("overrides/cobbleventure-pack-info.json")
                )
                root_icon = archive.read("icon.png")
                override_icon = archive.read("overrides/icon.png")
            self.assertEqual("minecraftModpack", manifest["manifestType"])
            self.assertEqual("icon.png", manifest["image"])
            self.assertEqual([], manifest["files"])
            self.assertIn("manifest.json", names)
            self.assertIn("icon.png", names)
            self.assertIn("overrides/", names)
            self.assertIn("overrides/icon.png", names)
            self.assertIn("overrides/config/marker.txt", names)
            self.assertEqual(root_icon, override_icon)
            self.assertEqual("test-fixture", pack_info["purpose"])
            self.assertFalse(pack_info["production_ready"])
            self.assertTrue(output.with_name(output.name + ".sha256").is_file())

    def test_rejects_output_outside_dist(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            profile_path = self._fixture(root)
            absolute_profile = root / profile_path
            profile = json.loads(absolute_profile.read_text(encoding="utf-8"))
            profile["output"] = "smoke.zip"
            absolute_profile.write_text(json.dumps(profile), encoding="utf-8")
            with self.assertRaises(pack_builder.PackError):
                pack_builder.load_profile(root, profile_path)

    def test_rejects_small_or_non_square_icon(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            profile_path = self._fixture(root)
            self._write_png(root / "pack" / "assets" / "icon.png", 399, 400)
            with self.assertRaises(pack_builder.PackError):
                pack_builder.load_profile(root, profile_path)

    def test_embeds_local_resource_pack_for_paxi_and_updates_pack_format(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            profile_path = self._fixture(root)
            resource_pack = root / "local-assets" / "BaskinRobbins.zip"
            self._write_resource_pack(resource_pack)
            absolute_profile = root / profile_path
            profile = json.loads(absolute_profile.read_text(encoding="utf-8"))
            profile["local_resourcepacks"] = [{
                "source": "local-assets/BaskinRobbins.zip",
                "target": "BaskinRobbins.zip",
                "pack_format": 34,
            }]
            absolute_profile.write_text(json.dumps(profile), encoding="utf-8")

            output = pack_builder.build_pack(root, profile_path)
            with zipfile.ZipFile(output) as modpack:
                embedded = modpack.read(
                    "overrides/config/paxi/resourcepacks/BaskinRobbins.zip"
                )
            with zipfile.ZipFile(io.BytesIO(embedded)) as resource:
                metadata = json.loads(resource.read("pack.mcmeta"))
                self.assertIn("assets/minecraft/font/default.json", resource.namelist())
            self.assertEqual(34, metadata["pack"]["pack_format"])

    def test_builds_neoforge_server_zip_with_only_server_files(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            profile_path = self._fixture(root)
            self._write_dependency_lock(root)
            overrides = root / "pack" / "overrides" / "smoke"
            (overrides / "mods").mkdir()
            (overrides / "mods" / "custom.jar").write_bytes(b"custom-server-mod")
            (overrides / "config" / "server.toml").write_text("enabled=true\n", encoding="utf-8")
            (overrides / "config" / "iris.properties").write_text("client=true\n", encoding="utf-8")
            resourcepacks = overrides / "config" / "paxi" / "resourcepacks"
            resourcepacks.mkdir(parents=True)
            (resourcepacks / "client.zip").write_bytes(b"client-resource-pack")

            output = pack_builder.build_server_pack(root, profile_path)
            manifest = pack_builder.validate_server_pack(
                output, root=root, profile_path=profile_path,
            )
            with zipfile.ZipFile(output) as archive:
                names = set(archive.namelist())
                eula = archive.read("eula.txt").decode("ascii")
                setup = archive.read("setup-server.ps1").decode("utf-8-sig")

            self.assertEqual(["common_mod", "server_mod"], sorted(
                item["id"] for item in manifest["external_mods"]
            ))
            self.assertEqual(["custom.jar"], manifest["vendored_mods"])
            self.assertIn("mods/custom.jar", names)
            self.assertIn("config/server.toml", names)
            self.assertNotIn("config/iris.properties", names)
            self.assertNotIn("config/paxi/resourcepacks/client.zip", names)
            self.assertIn("server.properties", names)
            self.assertIn("eula=false", eula)
            self.assertIn("api.curseforge.com", setup)
            self.assertTrue(output.with_name(output.name + ".sha256").is_file())

    def test_uses_profile_specific_lock_and_replaces_base_mods_directory(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            profile_path = self._fixture(root)
            profile_file = root / profile_path
            profile = json.loads(profile_file.read_text(encoding="utf-8"))
            profile["dependency_lock"] = "pack/dependencies-1.8.lock.json"
            profile["mods_directory"] = "pack/overrides/development-1.8/mods"
            profile_file.write_text(json.dumps(profile), encoding="utf-8")

            base_mods = root / "pack" / "overrides" / "smoke" / "mods"
            base_mods.mkdir()
            (base_mods / "stable.jar").write_bytes(b"stable")
            target_mods = root / "pack" / "overrides" / "development-1.8" / "mods"
            target_mods.mkdir(parents=True)
            (target_mods / "next.jar").write_bytes(b"next")
            self._write_dependency_lock(root)
            (root / "pack" / "dependencies.lock.json").replace(
                root / "pack" / "dependencies-1.8.lock.json"
            )

            client_output = pack_builder.build_pack(root, profile_path)
            with zipfile.ZipFile(client_output) as archive:
                names = set(archive.namelist())
            self.assertIn("overrides/mods/next.jar", names)
            self.assertNotIn("overrides/mods/stable.jar", names)

            server_output = pack_builder.build_server_pack(root, profile_path)
            manifest = pack_builder.validate_server_pack(
                server_output, root=root, profile_path=profile_path,
            )
            self.assertEqual(["next.jar"], manifest["vendored_mods"])

if __name__ == "__main__":
    unittest.main()

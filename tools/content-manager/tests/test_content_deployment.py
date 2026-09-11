import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import content_deployment as deployment
import content_manager


class ContentDeploymentTests(unittest.TestCase):
    def fixture(self, root):
        instance = root / "instance"
        (instance / "mods").mkdir(parents=True)
        (instance / "config").mkdir()
        with zipfile.ZipFile(instance / "mods/cobbleventure-content-runtime-test.jar", "w") as archive:
            archive.writestr("dev/buizz/cobbleventure/content/ContentPacks.class", b"engine")
        deployment.save_instance(root, str(instance))
        self.archive(root)
        return instance

    def archive(self, root, extra=None, tamper=False):
        files = {"pack.mcmeta": b'{"pack":{"pack_format":48}}', "data/test/trainer.json": b'{"level":43}'}
        hashes = {name: hashlib.sha256(data).hexdigest() for name, data in files.items()}
        manifest = {"schema_version": 1, "engine_contract": 2, "project": "test", "language": "ko_kr",
            "files": hashes, "sha256": hashlib.sha256(json.dumps(hashes, sort_keys=True).encode()).hexdigest()}
        path = root / "dist/cobbleventure-content.zip"
        path.parent.mkdir(exist_ok=True)
        with zipfile.ZipFile(path, "w") as archive:
            for name, data in files.items():
                archive.writestr("config/cobbleventure/content/" + name, b"tampered" if tamper else data)
            archive.writestr("config/cobbleventure/content/content-manifest.json", json.dumps(manifest))
            archive.writestr("config/easy_npc/skin/humanoid/new.png", b"new skin")
            if extra:
                archive.writestr(extra, b"outside")

    def test_replaces_owned_content_and_keeps_jars_saves_and_other_settings(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            instance = self.fixture(root)
            content = instance / deployment.CONTENT
            content.mkdir(parents=True)
            (content / "content-manifest.json").write_text("{}")
            (content / "deleted-trainer.json").write_text("old")
            (instance / "saves").mkdir()
            (instance / "saves/world.dat").write_bytes(b"save")
            (instance / "config/other.json").write_bytes(b"settings")
            jar = next((instance / "mods").iterdir())
            before = jar.read_bytes()
            result = deployment.install(root, "test")
            self.assertEqual(str(instance), result["instance_path"])
            self.assertFalse((content / "deleted-trainer.json").exists())
            self.assertEqual(b'{"level":43}', (content / "data/test/trainer.json").read_bytes())
            self.assertEqual(before, jar.read_bytes())
            self.assertEqual(b"save", (instance / "saves/world.dat").read_bytes())
            self.assertEqual(b"settings", (instance / "config/other.json").read_bytes())

    def test_invalid_archive_never_changes_instance(self):
        for extra, tamper in [("../escape", False), ("overrides/mods/engine.jar", False),
                              ("config/cobbleventure/content/../outside", False), (None, True)]:
            with self.subTest(extra=extra, tamper=tamper), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                instance = self.fixture(root)
                self.archive(root, extra, tamper)
                with self.assertRaises(ValueError):
                    deployment.install(root, "test")
                self.assertFalse((instance / deployment.CONTENT).exists())

    def test_wrong_project_and_legacy_engine_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            instance = self.fixture(root)
            with self.assertRaisesRegex(ValueError, "프로젝트"):
                deployment.install(root, "other")
            next((instance / "mods").iterdir()).unlink()
            with self.assertRaisesRegex(ValueError, "엔진"):
                deployment.install(root, "test")

    def test_failed_skin_install_restores_previous_content(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            instance = self.fixture(root)
            deployment.install(root, "test")
            old = instance / deployment.CONTENT / "data/test/trainer.json"
            old.write_bytes(b"previous content")
            rename = Path.rename

            def fail_incoming_skin(path, target):
                if "incoming" in path.parts and path.name == "new.png":
                    raise OSError("simulated locked file")
                return rename(path, target)

            with mock.patch.object(Path, "rename", fail_incoming_skin):
                with self.assertRaises(OSError):
                    deployment.install(root, "test")
            self.assertEqual(b"previous content", old.read_bytes())
            self.assertEqual(b"new skin", (instance / "config/easy_npc/skin/humanoid/new.png").read_bytes())

    def test_setting_sections_survive_each_others_updates(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            instance = self.fixture(root)
            content_manager._save_structure_builder_settings(root, str(instance), str(instance))
            self.assertEqual(str(instance), deployment.saved_instance(root))
            deployment.save_instance(root, str(instance))
            self.assertEqual(str(instance), content_manager._load_structure_builder_settings(root)["live_instance_path"])

    def test_mods_only_build_does_not_sync_content(self):
        with mock.patch.object(content_manager, "sync_local_music_catalog") as sync, \
             mock.patch.object(content_manager.subprocess, "run", return_value=mock.Mock(returncode=0, stdout=b"ok", stderr=b"")) as run:
            result = content_manager._run_build(Path.cwd(), Path.cwd(), "mods-pack")
        self.assertTrue(result["success"])
        sync.assert_not_called()
        self.assertEqual("mods-pack", run.call_args.args[0][-2])


if __name__ == "__main__":
    unittest.main()

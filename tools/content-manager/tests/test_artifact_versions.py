from pathlib import Path
import json
import os
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build_content_bundle
from tools import artifact_versions


class ArtifactVersionsTests(unittest.TestCase):
    def test_versions_persist_independently_and_preserve_older_artifacts(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            values = artifact_versions.save(root, {"jar_version": "2.3.4-rc.1", "content_version": "1.0.0"})
            archive = artifact_versions.content_archive(root, values)
            archive.parent.mkdir()
            archive.write_bytes(b"previous release")
            updated = artifact_versions.save(root, {**values, "content_version": "1.1.0"})
            self.assertEqual(updated, artifact_versions.load(root))
            self.assertEqual("cobbleventure-mods-2.3.4-rc.1.zip", artifact_versions.names(updated)["mods"])
            self.assertNotEqual(archive, artifact_versions.content_archive(root, updated))
            self.assertEqual(b"previous release", archive.read_bytes())

    def test_invalid_versions_do_not_change_saved_settings(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            values = artifact_versions.save(root, artifact_versions.DEFAULTS)
            before = (root / artifact_versions.CONFIG).read_bytes()
            for value in ("../1.0.0", "1.0", "1.0.0&calc", "01.0.0", "", None, "1.0.0/evil", "1.0.0\n"):
                with self.subTest(value=value), self.assertRaises(ValueError):
                    artifact_versions.save(root, {**values, "content_version": value})
                self.assertEqual(before, (root / artifact_versions.CONFIG).read_bytes())

    def test_build_environment_is_a_snapshot_not_a_persistent_setting(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            artifact_versions.save(root, artifact_versions.DEFAULTS)
            with mock.patch.dict(os.environ, {"COBBLEVENTURE_JAR_VERSION": "2.0.0", "COBBLEVENTURE_CONTENT_VERSION": "3.0.0"}):
                self.assertEqual("2.0.0", artifact_versions.load(root)["jar_version"])
                self.assertEqual("1.0.0", artifact_versions.load(root, environment=False)["jar_version"])

    def test_content_manifest_tracks_only_its_own_version(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "project.json").write_text('{"id":"test"}')
            bundle = root / "bundle"
            bundle.mkdir()
            with mock.patch.dict(os.environ, {"COBBLEVENTURE_JAR_VERSION": "1.0.0"}):
                build_content_bundle.write_manifest(bundle, root, "ko_kr", "2.0.0")
            before = (bundle / "content-manifest.json").read_bytes()
            with mock.patch.dict(os.environ, {"COBBLEVENTURE_JAR_VERSION": "9.0.0"}):
                build_content_bundle.write_manifest(bundle, root, "ko_kr", "2.0.0")
            self.assertEqual(before, (bundle / "content-manifest.json").read_bytes())
            self.assertEqual("2.0.0", json.loads(before)["version"])

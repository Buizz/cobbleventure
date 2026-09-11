import json
import unittest
from pathlib import Path

CONTENT = Path(__file__).resolve().parents[3] / "content-projects/cobbleventure-main/content"


class GymMusicCoverageTests(unittest.TestCase):
    def test_every_gym_room_has_a_registered_interior_track(self):
        def catalog(name):
            return json.loads((CONTENT / "catalogs" / name).read_text(encoding="utf-8"))

        settings = catalog("building-settings.json")["buildings"]
        tracks = {track["id"] for track in catalog("music-tracks.json")["tracks"]}
        for gym in catalog("gyms.json")["gyms"]:
            for module in gym["interior"]["modules"]:
                with self.subTest(gym=gym["id"], room=module["id"]):
                    track = settings.get(module["structure"], {}).get("music_track")
                    self.assertEqual("facility.gym", track)
                    self.assertIn(track, tracks)


if __name__ == "__main__":
    unittest.main()

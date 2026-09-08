"""Regression: extended panels must not repeat the original end decorations."""
import copy
import importlib.util
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location('raise_furniture', ROOT / 'raise-furniture.py')
furniture = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(furniture)


class PanelUvTests(unittest.TestCase):
    def test_extended_panels_cover_each_uv_interval_once_top_to_bottom(self):
        for folder, stem in furniture.MODELS.items():
            source = furniture.BACKUP / furniture.WORKSHOP / folder / (stem + '.json')
            original = json.loads(source.read_text())
            elements = (furniture.display_case(original) if stem == 'double_display_case'
                        else furniture.bookshelf(original, stem.startswith('green')))
            for index, element in enumerate(elements):
                with self.subTest(model=stem, element=index):
                    slices = furniture.preserve_panel_borders([copy.deepcopy(element)])
                    if '_source_height' not in element:
                        self.assertEqual([element], slices)
                        continue
                    self.assertEqual(3, len(slices))
                    self.assertEqual(element['from'], slices[0]['from'])
                    self.assertEqual(element['to'], slices[-1]['to'])
                    for lower, upper in zip(slices, slices[1:]):
                        self.assertEqual(lower['to'][1], upper['from'][1])
                        self.assertNotIn('up', lower['faces'])
                        self.assertNotIn('down', upper['faces'])
                    for face in ('north', 'south', 'east', 'west'):
                        if face not in element['faces']:
                            continue
                        original_uv = element['faces'][face]['uv']
                        rows = [part['faces'][face]['uv'] for part in reversed(slices)]
                        self.assertAlmostEqual(original_uv[1], rows[0][1])
                        self.assertAlmostEqual(original_uv[3], rows[-1][3])
                        for top, bottom in zip(rows, rows[1:]):
                            self.assertAlmostEqual(top[3], bottom[1])
                        for row in rows:
                            self.assertEqual(original_uv[0::2], row[0::2])


if __name__ == '__main__':
    unittest.main()

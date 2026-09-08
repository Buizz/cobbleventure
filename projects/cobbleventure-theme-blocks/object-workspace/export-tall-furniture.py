"""Center 48-unit editor models on the middle block for Java's -16..32 bounds."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
MODELS = ('02_double_display_case/double_display_case',
          '08_white_connecting_bookshelf/white_connecting_bookshelf',
          '13_green_connecting_bookshelf/green_connecting_bookshelf')
PREFIX = Path('assets/cobbleventure_theme_blocks/models/block/workshop')


def main():
    for name in MODELS:
        relative = PREFIX / (name + '.json')
        model = json.loads((ROOT / relative).read_text(encoding='utf-8'))
        model.pop('groups', None)
        for element in model['elements']:
            assert element.get('rotation', {}).get('angle', 0) == 0
            element.pop('rotation', None)
            for corner in ('from', 'to'):
                element[corner][1] -= 16
                assert all(-16 <= v <= 32 for v in element[corner]), (name, element)
        output = ROOT.parent / 'build/generated/tall-furniture' / relative
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(model, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()

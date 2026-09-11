"""Re-author furniture from immutable backups: preserve props and shelf thickness."""
import copy
import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent
BACKUP = ROOT / 'recovery/furniture-before-height-48-20260908'
MODELS = {
    '02_double_display_case': 'double_display_case',
    '08_white_connecting_bookshelf': 'white_connecting_bookshelf',
    '13_green_connecting_bookshelf': 'green_connecting_bookshelf',
}
WORKSHOP = Path('assets/cobbleventure_theme_blocks/models/block/workshop')


def move(element, bottom, top=None):
    result = copy.deepcopy(element)
    delta = bottom - element['from'][1]
    result['from'][1] = bottom
    result['to'][1] = element['to'][1] + delta if top is None else top
    if top is not None and top - bottom != element['to'][1] - element['from'][1]:
        result['_source_height'] = element['to'][1] - element['from'][1]
    if 'rotation' in result:
        result['rotation']['origin'][1] += delta
    return result


def preserve_panel_borders(elements):
    """Keep end trims once; extend only the continuous panel between them.

    Vertical face V runs from the TOP of a cuboid to its BOTTOM. Repeating
    the whole UV rectangle repeats painted bevels, producing false seams.
    Three slices retain a one-model-unit trim at each end, with one continuous
    middle UV interval. Books, props and unchanged shelves never enter here.
    """
    result = []
    for element in elements:
        source_height = element.pop('_source_height', None)
        if source_height is None:
            result.append(element)
            continue
        bottom, top = element['from'][1], element['to'][1]
        trim = min(1, source_height / 3, (top - bottom) / 3)
        fraction = trim / source_height
        slices = ((bottom, bottom + trim, 1 - fraction, 1),
                  (bottom + trim, top - trim, fraction, 1 - fraction),
                  (top - trim, top, 0, fraction))
        for y, end, v_start, v_end in slices:
            part = copy.deepcopy(element)
            part['from'][1], part['to'][1] = y, end
            for face in ('north', 'south', 'east', 'west'):
                if face in part['faces']:
                    uv = part['faces'][face]['uv']
                    first, last = uv[1], uv[3]
                    uv[1] = first + (last - first) * v_start
                    uv[3] = first + (last - first) * v_end
            if y != bottom:
                part['faces'].pop('down', None)
            if end != top:
                part['faces'].pop('up', None)
            result.append(part)
    return result


def bookshelf(original, green):
    old = original['elements']
    # Preserve a one-unit shelf and the original five/six-unit book silhouettes.
    base = 12 if green else 13
    top = 46 if green else 47
    result = [move(old[0], 0, 48), move(old[7], 0, 48),
              move(old[1], top), move(old[5], 1, base - 1),
              move(old[6], 1, top)]
    original_bases = (10, 17, 24) if green else (11, 18, 25)
    for tier, source in enumerate((0, 1, 2, 1, 0)):
        floor = base + tier * 7
        result.append(move(old[4], floor - 1))
        for element in old[8:]:
            if element['from'][1] == original_bases[source]:
                result.append(move(element, floor))
    return result


def display_case(original):
    old = original['elements']
    result = []
    for i in (0, 1, 34, 36, 37):
        result.append(move(old[i], 3, 39))
    result.extend([move(old[2], 41), move(old[3], 0), move(old[33], 39),
                   move(old[35], 1), move(old[38], 1, 41)])
    for tier, source_floor in enumerate((5, 15, 5)):
        floor = 5 + tier * 13
        for i in (31, 32):
            result.append(move(old[i], floor - 1))
        # The source has two coincident pairs: retain their visible geometry once.
        seen = set()
        for element in old[4:29]:
            if element['from'][1] != source_floor:
                continue
            key = json.dumps(element, sort_keys=True)
            if key not in seen:
                result.append(move(element, floor))
                seen.add(key)
    return result


def main():
    # Copy entire authored folders, plus every external texture, before editing.
    if not BACKUP.exists():
        BACKUP.mkdir(parents=True)
        for folder, stem in MODELS.items():
            shutil.copytree(ROOT / WORKSHOP / folder, BACKUP / WORKSHOP / folder)
            doc = json.loads((ROOT / WORKSHOP / folder / (stem + '.json')).read_text())
            for texture in set(doc['textures'].values()):
                namespace, path = texture.split(':')
                relative = Path('assets') / namespace / 'textures' / (path + '.png')
                target = BACKUP / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(ROOT / relative, target)
        hashes = {p.relative_to(BACKUP).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                  for p in BACKUP.rglob('*') if p.is_file()}
        (BACKUP / 'sha256.json').write_text(json.dumps(hashes, indent=2) + '\n')
    hashes = json.loads((BACKUP / 'sha256.json').read_text())
    for name, digest in hashes.items():
        assert hashlib.sha256((BACKUP / name).read_bytes()).hexdigest() == digest, name
    for folder, stem in MODELS.items():
        relative = WORKSHOP / folder / (stem + '.json')
        doc = json.loads((BACKUP / relative).read_text())
        doc['elements'] = display_case(doc) if stem == 'double_display_case' else bookshelf(doc, stem.startswith('green'))
        doc['elements'] = preserve_panel_borders(doc['elements'])
        # Original element-index groups no longer describe the new tier arrangement.
        doc['groups'] = [{'name': stem + '_height_48', 'origin': [16, 0, 8],
                          'color': 0, 'children': list(range(len(doc['elements'])))}]
        (ROOT / relative).write_text(json.dumps(doc, ensure_ascii=False, indent='\t') + '\n', encoding='utf-8')
        print(stem, len(doc['elements']), 'elements; height 48')


if __name__ == '__main__':
    main()

"""Extract editable league rooms from the original tower, preserving block NBT.

This is a one-time migration: existing room files are never overwritten.
"""
from __future__ import annotations

import gzip
import json
import struct
from pathlib import Path

from cave_road_anchor import _int_tag, _list_records, _named
from content_manager import _minecraft_structure_tag_spans, _read_minecraft_structure_root

ROOT = Path(__file__).resolve().parents[2]
CONTENT = ROOT / 'content-projects/cobbleventure-main/content'


def ints(values):
    return bytes([3]) + struct.pack('>i', len(values)) + struct.pack('>' + 'i' * len(values), *values)


def split():
    source = CONTENT / 'structures/league/kanto_league.nbt'
    raw = gzip.decompress(source.read_bytes())
    root = _read_minecraft_structure_root(raw)
    spans = _minecraft_structure_tag_spans(raw)
    _, start, end = spans['blocks']
    _, records = _list_records(raw[start:end])
    anchors = json.loads(source.with_suffix('.structure.json').read_text(encoding='utf-8'))['anchors']
    palette = root['palette']
    air = next(i for i, p in enumerate(palette) if p['Name'] == 'minecraft:air')
    barrier = next(i for i, p in enumerate(palette) if p['Name'] == 'minecraft:barrier')
    floor = next(i for i, p in enumerate(palette) if p['Name'] in ('minecraft:stone', 'minecraft:smooth_quartz', 'minecraft:quartz_block'))
    for slot, ymin, ymax in [('lobby', 0, 26), ('elite_1', 27, 54), ('elite_2', 55, 82),
                             ('elite_3', 83, 110), ('elite_4', 111, 134), ('champion', 135, 169)]:
        path = CONTENT / f'structures/interiors/leagues/kanto_{slot}.nbt'
        if path.exists() or path.with_suffix('.structure.json').exists():
            raise ValueError(f'수작업 파일을 덮어쓰지 않습니다: {path}')
        blocks = {}
        for block, encoded in records:
            x, y, z = block['pos']
            if not (5 <= x <= 67 and ymin <= y <= ymax and 8 <= z <= 70):
                continue
            pos = [x - 5, y - ymin, z - 8]
            # Replace only the pos payload, retaining every mod-specific block tag.
            _, ps, pe = _minecraft_structure_tag_spans(b'\x0a\x00\x00' + encoded)['pos']
            blocks[tuple(pos)] = encoded[:ps - 3] + ints(pos) + encoded[pe - 3:]
        height = ymax - ymin + 1

        def put(pos, state):
            blocks[tuple(pos)] = _int_tag('state', state) + _named(9, 'pos', ints(pos)) + b'\x00'

        # Cropping is deliberately enclosed so an editable room cannot open onto the void.
        for x in range(63):
            for z in range(63):
                put([x, 0, z], floor)
                put([x, height - 1, z], barrier)
        for y in range(1, height - 1):
            for n in range(63):
                for pos in ([0, y, n], [62, y, n], [n, y, 0], [n, y, 62]):
                    put(pos, barrier)
        mapping = ({'lobby_return': 'entry', 'lobby_to_elite': 'exit',
                    'lobby_nurse': 'nurse', 'lobby_chansey': 'chansey', 'lobby_shop': 'shop'}
                   if slot == 'lobby' else {slot: 'opponent', slot + '_battle_player': 'opponent_battle_player',
                                            slot + '_entry': 'entry', slot + '_exit': 'exit'})
        selected = []
        for original in anchors:
            if original['id'] not in mapping:
                continue
            anchor = dict(original, id=mapping[original['id']], label=mapping[original['id']])
            for field in ('position', 'safe_spawn'):
                if field in anchor:
                    x, y, z = anchor[field]
                    anchor[field] = [x - 5, y - ymin, z - 8]
            selected.append(anchor)
        if slot == 'lobby':
            # Return portal beside the arrival landing, separate from the challenge portal.
            for x in range(24, 27):
                for y in range(1, 4):
                    put([x, y, 43], barrier)
            for x in range(24, 28):
                for z in range(44, 48):
                    put([x, 0, z], floor)
                    put([x, 1, z], air)
                    put([x, 2, z], air)
            selected.append({'id': 'leave', 'label': 'leave', 'type': 'transition',
                             'position': [25, 2, 43], 'safe_spawn': [25, 1, 46], 'facing': 'north'})
        # The champion's original north portal touches the cropped wall: keep its landing clear.
        for anchor in selected:
            if anchor['type'] == 'arrival':
                x, y, z = anchor.get('safe_spawn', anchor['position'])
                put([x, y - 1, z], floor)
                put([x, y, z], air)
                put([x, y + 1, z], air)
        replacements = {'blocks': bytes([10]) + struct.pack('>i', len(blocks)) + b''.join(blocks.values()),
                        'size': ints([63, height, 63])}
        output = raw
        for name, (_, s, e) in sorted(spans.items(), key=lambda item: item[1][1], reverse=True):
            if name in replacements:
                output = output[:s] + replacements[name] + output[e:]
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(gzip.compress(output, mtime=0))
        metadata = {'schema_version': 1, 'structure': 'content/' + path.relative_to(CONTENT).as_posix(),
                    'interior': {'id': 'kanto_' + slot, 'width': 63, 'depth': 63, 'floor_height': height, 'floors': 1},
                    'anchors': selected}
        path.with_suffix('.structure.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print(path.relative_to(ROOT))


if __name__ == '__main__':
    split()

"""Match the authored elite-one entry portal, preserving all other NBT payloads."""
import datetime
import gzip
import io
import json
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/content-manager'))
from content_manager import _read_minecraft_structure_root, _read_nbt_payload, _minecraft_structure_tag_spans


def convert():
    directory = ROOT / 'content-projects/cobbleventure-main/content/structures/interiors/leagues'
    backup = ROOT / 'backups/league-entries' / datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
    backup.mkdir(parents=True)
    portal = {(x, y, 58) for x in range(30, 33) for y in range(2, 5)}
    for name in ('kanto_elite_2', 'kanto_elite_3', 'kanto_elite_4', 'kanto_champion'):
        path = directory / (name + '.nbt')
        original = path.read_bytes()
        sidecar = path.with_suffix('.structure.json')
        metadata = json.loads(sidecar.read_text(encoding='utf-8'))
        nbt = _read_minecraft_structure_root(original)
        barrier = next(i for i, state in enumerate(nbt['palette']) if state['Name'] == 'minecraft:barrier')
        raw = gzip.decompress(original)
        _, start, end = _minecraft_structure_tag_spans(raw)['blocks']
        payload = raw[start:end]
        stream = io.BytesIO(payload[5:])
        records, found = [], set()
        for _ in range(struct.unpack('>i', payload[1:5])[0]):
            at = stream.tell()
            value = _read_nbt_payload(stream, 10)
            encoded = payload[5 + at:5 + stream.tell()]
            pos = tuple(value['pos'])
            if pos in portal:
                assert 'nbt' not in value, 'Do not replace block entities'
                assert nbt['palette'][value['state']]['Name'] in ('minecraft:air', 'minecraft:light', 'minecraft:barrier'), pos
                wrapper = b'\x0a\x00\x00' + encoded
                _, a, b = _minecraft_structure_tag_spans(wrapper)['state']
                encoded = (wrapper[:a] + struct.pack('>i', barrier) + wrapper[b:])[3:]
                found.add(pos)
            records.append(encoded)
        assert found == portal
        result = gzip.compress(raw[:start] + b'\x0a' + struct.pack('>i', len(records)) + b''.join(records) + raw[end:], mtime=0)
        after = _read_minecraft_structure_root(result)
        assert after['palette'] == nbt['palette']
        assert after.get('entities') == nbt.get('entities')
        for before, changed in zip(nbt['blocks'], after['blocks']):
            assert tuple(before['pos']) in portal or before == changed
        entry = next(a for a in metadata['anchors'] if a['id'] == 'entry')
        entry.update(type='transition', position=[31, 3, 58], safe_spawn=[31, 2, 56], facing='south')
        (backup / path.name).write_bytes(original)
        (backup / sidecar.name).write_bytes(sidecar.read_bytes())
        path.write_bytes(result)
        sidecar.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print(name + ': 9 portal blocks; other blocks preserved')
    print('Backup:', backup)


if __name__ == '__main__':
    convert()

"""Remove legacy league commands without rewriting unrelated NBT records."""
from __future__ import annotations

import gzip
import json
import struct
from pathlib import Path

from cave_road_anchor import _int_tag, _list_records, _named
from content_manager import _minecraft_structure_tag_spans, _read_minecraft_structure_root

ROOT = Path(__file__).resolve().parents[2]
LEAGUE = ROOT / "content-projects/cobbleventure-main/content/structures/league/kanto_league.nbt"


def prepare(path: Path = LEAGUE) -> None:
    raw = gzip.decompress(path.read_bytes())
    root = _read_minecraft_structure_root(raw)
    air = next(i for i, state in enumerate(root["palette"]) if state["Name"] == "minecraft:air")
    barrier = next(i for i, state in enumerate(root["palette"]) if state["Name"] == "minecraft:barrier")
    _, start, end = _minecraft_structure_tag_spans(raw)["blocks"]
    count, records = _list_records(raw[start:end])
    changed = 0
    result = []
    for block, encoded in records:
        name = root["palette"][block["state"]]["Name"]
        state = air if "command_block" in name else None
        # Complete the authored 3x3 exit barrier; the central block was left open.
        if block["pos"] == [36, 124, 66]:
            state = barrier
        if state is not None and (state != block["state"] or "nbt" in block):
            encoded = (_int_tag("state", state) + _named(9, "pos",
                bytes([3]) + struct.pack(">i", 3) + struct.pack(">iii", *block["pos"])) + b"\x00")
            changed += 1
        result.append(encoded)
    if changed:
        payload = bytes([10]) + struct.pack(">i", count) + b"".join(result)
        path.write_bytes(gzip.compress(raw[:start] + payload + raw[end:], mtime=0))
    metadata_path = path.with_suffix(".structure.json")
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    for anchor in metadata["anchors"]:
        label = anchor["id"]
        if label == "lobby_return" or label.endswith("_entry"):
            anchor["type"] = "arrival"
        if label == "elite_4_exit":
            anchor.update(type="transition", position=[36, 124, 66], safe_spawn=[36, 123, 63])
    metadata_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"League prepared: {changed} blocks updated")


if __name__ == "__main__":
    prepare()

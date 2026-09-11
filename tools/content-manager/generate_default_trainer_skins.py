#!/usr/bin/env python3
"""Generate repository-safe default skins for every community override slot."""
from __future__ import annotations

import json
from pathlib import Path
import shutil


ROOT = Path(__file__).resolve().parents[2]
PROJECT = ROOT / "content-projects" / "cobbleventure-main"
CATALOG = PROJECT / "content" / "catalogs" / "trainer-skin-sources.json"
ROSTER = PROJECT / "content" / "catalogs" / "trainer-roster.json"
RESOURCE_ROOT = PROJECT / "content" / "resources" / "cobbleventure-world-bootstrap" / "assets"
PLACEHOLDER = RESOURCE_ROOT / "cobbleventure" / "textures" / "entity" / "trainer" / "unimplemented.png"


def main() -> None:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    roster = json.loads(ROSTER.read_text(encoding="utf-8"))
    resources = {
        entry.get("resource", "") for entry in catalog.get("skins", [])
        if isinstance(entry, dict)
    }
    resources.update(
        character.get("appearance", {}).get("resource", "")
        for character in roster.get("league_characters", [])
        if isinstance(character, dict) and character.get("role") == "gym_leader"
        and character.get("appearance", {}).get("source") == "custom"
    )
    generated = 0
    for resource in sorted(resources):
        if ":trainer_skin/" not in resource:
            continue
        namespace, slug = resource.split(":trainer_skin/", 1)
        target = RESOURCE_ROOT / namespace / "textures" / "entity" / "trainer" / f"{slug}.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(PLACEHOLDER, target)
        generated += 1
    print(f"generated {generated} default trainer skins")


if __name__ == "__main__":
    main()

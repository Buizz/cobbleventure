from __future__ import annotations

import argparse
from pathlib import Path

from starter_gym import build_power_plant_dungeon_nbt
from power_plant_exterior import build_power_plant_exterior_nbt


DEFAULT_OUTPUT = Path(
    "content-projects/cobbleventure-main/content/structures/dungeons/power_plant_interior.nbt"
)
EXTERIOR_OUTPUT = DEFAULT_OUTPUT.parent.parent / "placeholder/power_plant.nbt"


def main() -> None:
    parser = argparse.ArgumentParser(description="발전소 외관 또는 독립 던전 내관 NBT를 생성합니다.")
    parser.add_argument("--target", choices=("interior", "exterior"), default="interior")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    output = args.output or (DEFAULT_OUTPUT if args.target == "interior" else EXTERIOR_OUTPUT)
    builder = build_power_plant_dungeon_nbt if args.target == "interior" else build_power_plant_exterior_nbt
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(builder())
    print(output.resolve())


if __name__ == "__main__":
    main()

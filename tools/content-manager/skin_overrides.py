"""Local-only trainer skin overrides shared by the studio and content build."""
from __future__ import annotations

import base64
import binascii
import json
from pathlib import Path
import re
import shutil
import struct
from typing import Any


PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
TRAINER_RESOURCE = re.compile(
    r"^([a-z0-9_.-]+):trainer_skin/([a-z0-9_./-]+)$"
)
RCT_RESOURCE = re.compile(
    r"^rctmod:trainers/(single|group)/([a-z0-9_./-]+)$"
)
MAX_SKIN_BYTES = 2 * 1024 * 1024


def override_root(repository_root: Path) -> Path:
    return (repository_root / "local-assets" / "skins" / "overrides").resolve()


def resource_parts(resource: str) -> tuple[str, str]:
    match = TRAINER_RESOURCE.fullmatch(resource)
    if match:
        return match.group(1), match.group(2)
    rct_match = RCT_RESOURCE.fullmatch(resource)
    if rct_match:
        return "rctmod", f"trainers/{rct_match.group(1)}/{rct_match.group(2)}"
    raise ValueError("올바른 트레이너 스킨 리소스 ID가 아닙니다.")


def override_path(repository_root: Path, resource: str) -> Path:
    namespace, slug = resource_parts(resource)
    root = override_root(repository_root)
    target = (root / namespace / f"{slug}.png").resolve()
    if not target.is_relative_to(root):
        raise ValueError("스킨 경로가 로컬 오버라이드 폴더를 벗어났습니다.")
    return target


def validate_png(data: bytes) -> tuple[int, int]:
    if len(data) > MAX_SKIN_BYTES or not data.startswith(PNG_SIGNATURE):
        raise ValueError("2MB 이하 PNG 파일만 사용할 수 있습니다.")
    if len(data) < 24 or data[12:16] != b"IHDR":
        raise ValueError("PNG 헤더를 읽을 수 없습니다.")
    width, height = struct.unpack(">II", data[16:24])
    if (width, height) != (64, 64):
        raise ValueError("트레이너 스킨은 64×64 PNG여야 합니다.")
    return width, height


def decode_data_url(value: Any) -> bytes:
    if not isinstance(value, str):
        raise ValueError("PNG 데이터가 필요합니다.")
    encoded = value.split(",", 1)[1] if value.startswith("data:image/png;base64,") else value
    try:
        data = base64.b64decode(encoded, validate=True)
    except (ValueError, binascii.Error) as error:
        raise ValueError("PNG 데이터를 읽을 수 없습니다.") from error
    validate_png(data)
    return data


def save_override(repository_root: Path, resource: str, data: bytes) -> Path:
    validate_png(data)
    target = override_path(repository_root, resource)
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(".png.tmp")
    temporary.write_bytes(data)
    temporary.replace(target)
    return target


def remove_override(repository_root: Path, resource: str) -> Path:
    target = override_path(repository_root, resource)
    if not target.is_file():
        raise FileNotFoundError("로컬 스킨 덮어쓰기가 없습니다.")
    target.unlink()
    root = override_root(repository_root)
    parent = target.parent
    while parent != root and parent.is_dir() and not any(parent.iterdir()):
        parent.rmdir()
        parent = parent.parent
    return target


def catalog_payload(repository_root: Path, project_root: Path) -> dict[str, Any]:
    catalog_path = project_root / "content" / "catalogs" / "trainer-skin-sources.json"
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    items_by_resource: dict[str, dict[str, Any]] = {}
    for entry in catalog.get("skins", []):
        if not isinstance(entry, dict) or not isinstance(entry.get("resource"), str):
            continue
        namespace, slug = resource_parts(entry["resource"])
        local = override_path(repository_root, entry["resource"])
        default = (
            project_root / "content" / "resources" / "cobbleventure-world-bootstrap"
            / "assets" / namespace / "textures" / "entity" / "trainer" / f"{slug}.png"
        )
        items_by_resource[entry["resource"]] = {
            "resource": entry["resource"],
            "title": entry.get("title") or slug.replace("_", " "),
            "creator": entry.get("creator", ""),
            "source_url": entry.get("source_url", ""),
            "target_model": entry.get("target_model", "classic"),
            "override": local.is_file(),
            "default": default.is_file(),
            "category": "community",
        }
    roster_path = project_root / "content" / "catalogs" / "trainer-roster.json"
    roster = json.loads(roster_path.read_text(encoding="utf-8"))
    for character in roster.get("league_characters", []):
        if not isinstance(character, dict) or character.get("role") != "gym_leader":
            continue
        appearance = character.get("appearance")
        if not isinstance(appearance, dict) or not isinstance(appearance.get("resource"), str):
            continue
        resource = appearance["resource"]
        namespace, slug = resource_parts(resource)
        local = override_path(repository_root, resource)
        names = character.get("display_name") if isinstance(character.get("display_name"), dict) else {}
        existing = items_by_resource.get(resource, {})
        if RCT_RESOURCE.fullmatch(resource):
            group, rct_slug = RCT_RESOURCE.fullmatch(resource).groups()
            default = (
                project_root / "content" / "resources" / "cobbleventure-world-bootstrap"
                / "assets" / "rctmod" / "textures" / "trainers" / group / f"{rct_slug}.png"
            )
        else:
            default = (
                project_root / "content" / "resources" / "cobbleventure-world-bootstrap"
                / "assets" / namespace / "textures" / "entity" / "trainer" / f"{slug}.png"
            )
        items_by_resource[resource] = {
            **existing,
            "resource": resource,
            "title": names.get("ko_kr") or names.get("en_us") or existing.get("title") or slug.replace("_", " "),
            "subtitle": names.get("en_us", ""),
            "generation": character.get("generation"),
            "target_model": existing.get("target_model") or character.get("body", {}).get("arm_model", "classic"),
            "override": local.is_file(),
            "default": default.is_file(),
            "category": "gym_leader",
        }
    items = sorted(
        items_by_resource.values(),
        key=lambda item: (item.get("category") != "gym_leader", item.get("generation") or 99, item["title"]),
    )
    return {
        "folder": str(override_root(repository_root)),
        "ignored_by_git": True,
        "items": items,
    }


def apply_to_bundle(repository_root: Path, bundle: Path) -> list[str]:
    root = override_root(repository_root)
    if not root.is_dir():
        return []
    applied: list[str] = []
    for source in sorted(root.rglob("*.png")):
        validate_png(source.read_bytes())
        relative = source.relative_to(root)
        if len(relative.parts) < 2:
            continue
        namespace = relative.parts[0]
        slug_path = Path(*relative.parts[1:])
        slug = slug_path.parent / slug_path.stem
        if namespace == "rctmod" and len(slug.parts) >= 3 and slug.parts[0] == "trainers" and slug.parts[1] in {"single", "group"}:
            group = slug.parts[1]
            name = Path(*slug.parts[2:])
            target = bundle / "assets" / "rctmod" / "textures" / "trainers" / group / name.parent / f"{name.name}.png"
            resource = f"rctmod:trainers/{group}/{name.as_posix()}"
        else:
            target = bundle / "assets" / namespace / "textures" / "entity" / "trainer" / slug.parent / f"{slug.name}.png"
            resource = f"{namespace}:trainer_skin/{slug.as_posix()}"
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        applied.append(resource)
    return applied

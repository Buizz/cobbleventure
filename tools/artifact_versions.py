"""Shared artifact release versions for the web, content compiler and pack builder."""
from __future__ import annotations

import json
import os
from pathlib import Path
import re
import uuid

CONFIG = Path("pack/artifact-versions.json")
VERSION = re.compile(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?")
DEFAULTS = {"jar_version": "1.0.0", "content_version": "1.0.0"}


def validate(value: object, label: str) -> str:
    if not isinstance(value, str) or len(value) > 64 or not VERSION.fullmatch(value):
        raise ValueError(f"{label} 버전은 1.0.0 또는 1.0.0-beta.1 형식으로 입력해 주세요.")
    return value


def load(root: Path, *, environment: bool = True) -> dict[str, str]:
    path = root / CONFIG
    document = json.loads(path.read_text(encoding="utf-8")) if path.is_file() else DEFAULTS
    if not isinstance(document, dict) or document.get("schema_version", 1) != 1:
        raise ValueError("지원하지 않는 산출물 버전 설정입니다.")
    versions = {}
    for key, label in (("jar_version", "JAR"), ("content_version", "콘텐츠")):
        override = os.environ.get("COBBLEVENTURE_" + key.upper()) if environment else None
        versions[key] = validate(override if override is not None else document.get(key), label)
    return versions


def save(root: Path, payload: object) -> dict[str, str]:
    if not isinstance(payload, dict):
        raise ValueError("JAR와 콘텐츠 버전을 입력해 주세요.")
    values = {key: validate(payload.get(key), label)
        for key, label in (("jar_version", "JAR"), ("content_version", "콘텐츠"))}
    path = root / CONFIG
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.is_file() and load(root, environment=False) == values:
        return values
    temporary = path.with_name(f".{path.name}.{uuid.uuid4().hex}.tmp")
    try:
        temporary.write_text(json.dumps({"schema_version": 1, **values}, indent=2) + "\n", encoding="utf-8")
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)
    return values


def content_archive(root: Path, versions: dict[str, str] | None = None) -> Path:
    return root / "dist" / f"cobbleventure-content-{(versions or load(root))['content_version']}.zip"


def names(versions: dict[str, str]) -> dict[str, str]:
    jar, content = versions["jar_version"], versions["content_version"]
    return {"full": f"cobbleventure-full-jar-{jar}-content-{content}.zip",
        "mods": f"cobbleventure-mods-{jar}.zip", "content": f"cobbleventure-content-{content}.zip",
        "jars": f"jars/{jar}/"}

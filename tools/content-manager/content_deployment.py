"""Deploy a validated content archive without replacing engine JARs or player saves."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import tempfile
import threading
import uuid
import zipfile
import sys
from contextlib import contextmanager
sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from tools import artifact_versions

SETTINGS = Path("tools/content-manager/settings.local.json")
CONTENT = Path("config/cobbleventure/content")
RECEIPT = Path("config/cobbleventure/content-install.json")
SKINS = "config/easy_npc/skin/"
LEGACY_PACKS = (
    Path("config/paxi/resourcepacks/Cobbleventure-Music.zip"),
    Path("config/paxi/resourcepacks/Cobbleventure-Pokemon-Paintings.zip"),
    Path("config/paxi/datapacks/zzz-cobbleventure-spawns.zip"),
)
SETTINGS_LOCK = threading.RLock()


@contextmanager
def deployment_stage(instance: Path):
    stage = Path(tempfile.mkdtemp(prefix=".cobbleventure-content-", dir=instance)).resolve()
    stage.relative_to(instance)
    try:
        yield stage
    except BaseException:
        # Keep recovery material if deployment or rollback cannot finish.
        raise
    else:
        shutil.rmtree(stage)


def update_settings(root: Path, section: str, value: dict) -> None:
    with SETTINGS_LOCK:
        path = root / SETTINGS
        document = json.loads(path.read_text(encoding="utf-8")) if path.is_file() else {"schema_version": 1}
        document[section] = value
        path.parent.mkdir(parents=True, exist_ok=True)
        temporary = path.with_name(f".{path.name}.{uuid.uuid4().hex}.tmp")
        try:
            temporary.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            os.replace(temporary, path)
        finally:
            temporary.unlink(missing_ok=True)


def saved_instance(root: Path) -> str:
    path = root / SETTINGS
    if not path.is_file():
        return ""
    value = json.loads(path.read_text(encoding="utf-8")).get("content_deployment", {}).get("instance_path", "")
    if not isinstance(value, str):
        raise ValueError("게임 인스턴스 경로 설정이 올바르지 않습니다.")
    return value


def resolve_instance(value: str) -> Path:
    path = Path(os.path.expandvars(value.strip())).expanduser()
    if not value.strip() or not path.is_absolute():
        raise ValueError("게임 인스턴스의 절대 경로를 지정해 주세요.")
    path = path.resolve()
    if not path.is_dir() or not (path / "mods").is_dir() or not (
        (path / "minecraftinstance.json").is_file() or (path / "config").is_dir()
    ):
        raise ValueError("mods와 인스턴스 설정이 있는 Minecraft 프로필 폴더를 선택해 주세요.")
    return path


def save_instance(root: Path, value: str) -> str:
    path = str(resolve_instance(value))
    update_settings(root, "content_deployment", {"instance_path": path})
    return path


def candidates() -> list[str]:
    roots = [Path.home() / "curseforge/minecraft/Instances",
             Path.home() / "Documents/Curse/Minecraft/Instances"]
    if os.environ.get("APPDATA"):
        roots.append(Path(os.environ["APPDATA"]) / "CurseForge/Minecraft/Instances")
    found = set()
    for root in roots:
        if not root.is_dir():
            continue
        for path in root.iterdir():
            if path.is_dir() and (path / "mods").is_dir():
                found.add(str(path.resolve()))
    return sorted(found, key=str.casefold)


def inspect_archive(path: Path, project_id: str) -> tuple[dict, dict[str, bytes]]:
    if not path.is_file():
        raise ValueError("빌드한 콘텐츠가 없습니다. 먼저 콘텐츠 빌드를 실행해 주세요.")
    files = {}
    seen = set()
    prefix = CONTENT.as_posix() + "/"
    with zipfile.ZipFile(path) as archive:
        if sum(info.file_size for info in archive.infolist()) > 512 * 1024 * 1024:
            raise ValueError("콘텐츠 ZIP의 압축 해제 크기가 너무 큽니다.")
        for info in archive.infolist():
            if info.is_dir():
                continue
            name = info.filename
            parts = PurePosixPath(name).parts
            if ("\\" in name or ":" in name or name.startswith("/") or ".." in parts
                or name.casefold() in seen or str(PurePosixPath(name)) != name
                or (info.external_attr >> 16) & 0o170000 == 0o120000):
                raise ValueError(f"허용되지 않는 ZIP 경로입니다: {name}")
            if not (name.startswith(prefix) or name.startswith(SKINS)):
                raise ValueError(f"콘텐츠 교체 범위를 벗어난 파일입니다: {name}")
            seen.add(name.casefold())
            files[name] = archive.read(info)
    try:
        manifest = json.loads(files[prefix + "content-manifest.json"])
        if manifest["schema_version"] != 1 or manifest["engine_contract"] != 2:
            raise ValueError("지원하지 않는 콘텐츠 계약 버전입니다.")
        if manifest["project"] != project_id:
            raise ValueError("현재 프로젝트와 빌드한 콘텐츠가 다릅니다. 콘텐츠를 다시 빌드해 주세요.")
        expected = manifest["files"]
        actual = {name[len(prefix):]: hashlib.sha256(data).hexdigest()
            for name, data in files.items() if name.startswith(prefix)
            and name != prefix + "content-manifest.json"}
        if expected != actual or "pack.mcmeta" not in actual:
            raise ValueError("콘텐츠 파일 목록 또는 체크섬이 일치하지 않습니다.")
        digest = hashlib.sha256(json.dumps(actual, sort_keys=True).encode()).hexdigest()
        if digest != manifest["sha256"]:
            raise ValueError("콘텐츠 번들 체크섬이 일치하지 않습니다.")
    except (KeyError, TypeError, json.JSONDecodeError) as error:
        raise ValueError("콘텐츠 manifest가 없거나 손상됐습니다.") from error
    return manifest, files


def checked_path(instance: Path, relative: Path) -> Path:
    path = instance / relative
    path.resolve().relative_to(instance)
    current = path
    while current != instance:
        if current.is_symlink() or (hasattr(current, "is_junction") and current.is_junction()):
            raise ValueError(f"콘텐츠 교체 경로에 링크를 사용할 수 없습니다: {current}")
        current = current.parent
    return path


def install(root: Path, project_id: str) -> dict:
    instance = resolve_instance(saved_instance(root))
    versions = artifact_versions.load(root, environment=False)
    manifest, files = inspect_archive(artifact_versions.content_archive(root, versions), project_id)
    if manifest.get("version") != versions["content_version"]:
        raise ValueError("선택한 콘텐츠 버전과 ZIP 내부 버전이 다릅니다. 콘텐츠를 다시 빌드해 주세요.")
    loader = "dev/buizz/cobbleventure/content/ContentPacks.class"
    supported = False
    for jar in (instance / "mods").glob("cobbleventure-content-runtime-*.jar"):
        with zipfile.ZipFile(jar) as archive:
            supported |= loader in archive.namelist()
    if not supported:
        raise ValueError("이 인스턴스에는 외부 콘텐츠를 지원하는 엔진이 없습니다. 전체 빌드로 만든 CurseForge 설치팩을 먼저 설치해 주세요.")
    destination = checked_path(instance, CONTENT)
    if destination.exists() and not (destination / "content-manifest.json").is_file():
        raise ValueError("기존 콘텐츠 폴더의 소유 정보를 확인할 수 없어 교체하지 않았습니다.")
    receipt_path = checked_path(instance, RECEIPT)
    previous = json.loads(receipt_path.read_text(encoding="utf-8")) if receipt_path.is_file() else {}
    old_skins = previous.get("managed_skins", [])
    if not isinstance(old_skins, list) or any(not isinstance(name, str) or not name.startswith(SKINS)
            or ".." in PurePosixPath(name).parts or "\\" in name or ":" in name for name in old_skins):
        raise ValueError("이전 콘텐츠 설치 기록의 스킨 경로가 올바르지 않습니다.")
    skins = sorted(name for name in files if name.startswith(SKINS))
    changes = [CONTENT, *(Path(name) for name in sorted(set(skins) | set(old_skins))),
        *LEGACY_PACKS, RECEIPT]
    for relative in changes:
        checked_path(instance, relative)
    # Staging is on the same volume. Every move is confined to the selected instance.
    with deployment_stage(instance) as stage:
        incoming, backup = stage / "incoming", stage / "backup"
        for name, data in files.items():
            target = incoming / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        record = incoming / RECEIPT
        record.parent.mkdir(parents=True, exist_ok=True)
        record.write_text(json.dumps({"schema_version": 1, "sha256": manifest["sha256"], "version": manifest["version"],
            "project": project_id, "managed_skins": skins}, indent=2) + "\n", encoding="utf-8")
        moved, installed = [], []
        try:
            for relative in changes:
                target = checked_path(instance, relative)
                if target.exists():
                    saved = backup / relative
                    saved.parent.mkdir(parents=True, exist_ok=True)
                    target.rename(saved)
                    moved.append(relative)
                source = incoming / relative
                if source.exists():
                    target.parent.mkdir(parents=True, exist_ok=True)
                    source.rename(target)
                    installed.append(relative)
        except BaseException as error:
            recovery_errors = []
            for relative in reversed(installed):
                try:
                    target = checked_path(instance, relative)
                    source = incoming / relative
                    source.parent.mkdir(parents=True, exist_ok=True)
                    target.rename(source)
                except OSError as recovery_error:
                    recovery_errors.append(str(recovery_error))
            for relative in reversed(moved):
                try:
                    target = checked_path(instance, relative)
                    if target.exists():
                        raise OSError(f"복구 대상이 사용 중입니다: {target}")
                    (backup / relative).rename(target)
                except OSError as recovery_error:
                    recovery_errors.append(str(recovery_error))
            if recovery_errors:
                raise RuntimeError(f"교체 실패 후 자동 복구를 완료하지 못했습니다. 원본 보관 경로: {backup}") from error
            raise
    return {"instance_path": str(instance), "sha256": manifest["sha256"], "version": manifest["version"],
        "files": len(files), "language": manifest.get("language"), "project": project_id}


def status(root: Path) -> dict:
    found = candidates()
    saved = saved_instance(root)
    preferred = [value for value in found if Path(value).name in {"Cobbleventure", "Cobbleventure 1.8 Development Test Pack"}]
    suggested = preferred[0] if len(preferred) == 1 else ""
    versions = artifact_versions.load(root, environment=False)
    path = artifact_versions.content_archive(root, versions)
    return {"instance_path": saved, "suggested_instance": suggested, "candidates": found,
        "versions": versions, "artifact_names": artifact_versions.names(versions),
        "bundle_version": versions["content_version"], "bundle_exists": path.is_file(), "bundle_path": str(path),
        "bundle_modified": path.stat().st_mtime if path.is_file() else None}

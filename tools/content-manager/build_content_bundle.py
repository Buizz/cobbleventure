"""Build installable game content without invoking Gradle or modifying engine JARs."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import sys
import subprocess
import tempfile
import zipfile
import engine_content_contract
import skin_overrides
sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from tools import artifact_versions

MODULES = ("world-bootstrap", "player-menu", "adventure", "casino", "pokefinder", "experience", "theme-blocks")


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def copy_tree(source: Path, target: Path) -> None:
    if source.is_dir():
        shutil.copytree(source, target, dirs_exist_ok=True)


def compile_theme_resources(project: Path, working: Path, bundle: Path) -> None:
    workspace = working / "object-workspace"
    shutil.copytree(project / "object-workspace", workspace,
        ignore=shutil.ignore_patterns("__pycache__", "recovery", "reference-images", "reports"))
    for script in ("sync-editable-bbmodels.py", "export-tall-furniture.py"):
        subprocess.run([sys.executable, str(workspace / script)], cwd=working, check=True,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    assets = workspace / "assets"
    tall = {"02_double_display_case/double_display_case.json", "08_white_connecting_bookshelf/white_connecting_bookshelf.json",
        "13_green_connecting_bookshelf/green_connecting_bookshelf.json"}
    for source in sorted(assets.rglob("*")):
        if not source.is_file(): continue
        relative = source.relative_to(assets).as_posix()
        model = "/models/block/workshop/" in relative and source.suffix == ".json"
        texture = "/textures/block/" in relative and source.suffix == ".png"
        if not (model or texture): continue
        if source.name.endswith(("_complete.json", ".pack-report.json")): continue
        if "/textures/block/windows/" in relative or relative.endswith("/textures/block/research_device.png"): continue
        if any(relative.endswith("/" + name) for name in tall): continue
        target = bundle / "assets" / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
    copy_tree(working / "build/generated/tall-furniture/assets", bundle / "assets")


def write_manifest(bundle: Path, project: Path, language: str, version: str) -> None:
    write_json(bundle / "pack.mcmeta", {"pack": {"pack_format": 48,
        "supported_formats": {"min_inclusive": 34, "max_inclusive": 48},
        "description": "Cobbleventure external game content"}})
    entries = {p.relative_to(bundle).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
        for p in sorted(bundle.rglob("*")) if p.is_file() and p.name != "content-manifest.json"}
    digest = hashlib.sha256(json.dumps(entries, sort_keys=True).encode()).hexdigest()
    write_json(bundle / "content-manifest.json", {"schema_version": 1, "engine_contract": 2,
        "project": json.loads((project / "project.json").read_text(encoding="utf-8"))["id"],
        "version": version, "language": language, "sha256": digest, "files": entries})


def validate_cobblemon_species_payload(bundle: Path) -> None:
    """Reject non-species JSON before Cobblemon encounters it at runtime."""
    species_root = bundle / "data/cobblemon/species"
    for path in sorted(species_root.rglob("*.json")):
        try:
            document = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            raise ValueError(f"Cobblemon species JSON을 읽을 수 없습니다: {path}: {error}") from error
        if not isinstance(document, dict):
            relative = path.relative_to(bundle).as_posix()
            raise ValueError(f"Cobblemon species JSON은 객체여야 합니다: {relative}")


def build_authoring_content(root: Path, project: Path, profile: str) -> Path:
    engine_content_contract.validate(project)
    version = artifact_versions.load(root)["content_version"]
    staging = root / "staging"
    staging.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="authoring-content-", dir=staging) as temporary:
        working = Path(temporary)
        bundle = working / "bundle"
        resources = project / "content/resources"
        for module in ("theme-blocks", profile):
            for category in ("assets", "data"):
                copy_tree(resources / f"cobbleventure-{module}" / category, bundle / category)
        # Editor modules implement these blocks under the game namespace.
        bootstrap = resources / "cobbleventure-world-bootstrap/assets/cobbleventure_bootstrap"
        for category in ("blockstates", "models/block", "models/item", "lang"):
            for source in (bootstrap / category).glob("*.json"):
                if category != "lang" and source.stem not in {"excavation_marker", "strength_boulder", "strength_plate", "rock_smash_rock"}: continue
                target = bundle / "assets/cobbleventure_bootstrap" / category / source.name
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, target)
        if profile == "structure-builder":
            copy_tree(root / "staging/structure-builder-resources", bundle)
        write_json(bundle / "data/cobbleventure/catalogs/theme-blocks.json",
            json.loads((project / "content/catalogs/theme-blocks.json").read_text(encoding="utf-8")))
        compile_theme_resources(project, working, bundle)
        write_manifest(bundle, project, "ko_kr", version)
        destination = root / "pack/overrides" / profile / "config/cobbleventure/content"
        publish(bundle, destination, root)
        return destination


def project_resources(content: Path, output: Path, language: str) -> None:
    """The resource projections formerly owned by five Gradle processResources tasks."""
    for category in ("caves", "forests", "underground_roads", "dungeons", "dungeon_pieces", "dungeon_plans"):
        for source in sorted((content / category).rglob("*.json")):
            target = output / "data/cobbleventure" / category / source.relative_to(content / category)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
    mappings = {
        "campaign.json": "data/cobbleventure/catalogs/campaign.json",
        "theme-blocks.json": "data/cobbleventure/catalogs/theme-blocks.json",
        "pokefinder-icons.json": "data/cobbleventure/pokefinder_icons.json",
        "economy.json": "data/cobbleventure/economy/catalog.json",
        "biome-profiles.json": "data/cobbleventure/catalogs/biome-profiles.json",
        "pokemon-habitats.json": "data/cobbleventure/catalogs/pokemon-habitats.json",
        "starter-settings.json": "data/cobbleventure/catalogs/starter-settings.json",
        "gacha-machines.json": "data/cobbleventure_casino/gacha/machines.json",
        "laboratory-research.json": "data/cobbleventure_adventure/research/settings.json",
        "important-items.json": "data/cobbleventure_player_menu/progression/important-items.json",
    }
    for name in ("league-progression", "badges", "gyms", "trainer-roster"):
        mappings[name + ".json"] = f"data/cobbleventure_player_menu/league/{name}.json"
    for source, destination in mappings.items():
        target = output / destination
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(content / "catalogs" / source, target)
    map_root = output / "data/cobbleventure_player_menu/map"
    for category in ("worlds", "settlements", "caves", "forests", "routes"):
        pattern = "generation_*.json" if category == "worlds" else "generation_*/*.json"
        for source in sorted((content / category).glob(pattern)):
            target = map_root / ("" if category == "worlds" else category) / source.relative_to(content / category)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
    for name in ("biome-profiles.json", "pokemon-habitats.json"):
        target = map_root / "catalogs" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(content / "catalogs" / name, target)
    copy_tree(content / "source/trainers/gym_leaders", output / "data/cobbleventure_player_menu/league/npcs")
    export_language = output / "data/cobbleventure/economy/export-language.txt"
    export_language.parent.mkdir(parents=True, exist_ok=True)
    export_language.write_text(language + "\n", encoding="utf-8")
    generate_indexes(content, output)
    for source in (content / "catalogs/battle-ai").glob("*.json"):
        target = output / "data/cobbleventure/catalogs/battle-ai" / source.name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)


def compile_supplemental_resources(root: Path, project: Path, working: Path, bundle: Path) -> None:
    music = import_tool("music_catalog", root / "tools/music-catalog/music_catalog.py")
    catalog = music.load_catalog(project / "content/catalogs/music-tracks.json")
    selected = music.select_used_tracks(catalog, project)
    music.stage_resource_pack(selected, root / catalog["source"]["local_directory"], working / "music", root)
    copy_tree(working / "music/assets", bundle / "assets")
    spawns = import_tool("build_custom_spawns", root / "tools/cobblemon-custom-spawns/build_custom_spawns.py")
    documents, _ = spawns.build_documents(spawns.read_sheet_rows(root / spawns.WORKBOOK))
    for name, document in documents.items():
        write_json(bundle / "data/cobblemon/spawn_pool_world" / name, document)
    paintings = import_tool("build_painting_pack", root / "tools/painting-pack/build_painting_pack.py")
    paintings.GENERATED_DIR = working / "paintings"
    paintings.PACK_DIR = paintings.GENERATED_DIR / "pack"
    paintings.TEXTURE_DIR = paintings.PACK_DIR / "assets/minecraft/textures/painting"
    paintings.OUTPUT_ZIP = working / "paintings.zip"
    paintings.main()
    copy_tree(paintings.PACK_DIR / "assets", bundle / "assets")


def generate_indexes(content: Path, output: Path) -> None:
    profiles = {}
    for source in sorted((content / "source").rglob("*.json")):
        npc = json.loads(source.read_text(encoding="utf-8"))
        moves = list(dict.fromkeys(command["move"] for event in npc.get("events", [])
            for command in event.get("commands", [])
            if command.get("type") == "grant_field_move" and command.get("move")))
        if npc.get("id") and moves:
            profiles[npc["id"]] = {"name": npc.get("npc", {}).get("display_name", {}).get("ko_kr")
                or npc.get("name", {}).get("ko_kr") or npc["id"], "moves": moves}
    by_settlement, names = {}, {}
    for source in sorted((content / "settlements").glob("generation_*/*.json")):
        settlement = json.loads(source.read_text(encoding="utf-8"))
        identifier = settlement["id"]
        if identifier in names:
            raise ValueError(f"Duplicate settlement: {identifier}")
        names[identifier] = {key.lower().replace("-", "_"): value
            for key, value in settlement["display_name"].items() if isinstance(value, str) and value.strip()}
        if not names[identifier]:
            raise ValueError(f"Missing settlement name: {identifier}")
        ids = dict.fromkeys(member.get("npc_profile")
            for slot in settlement.get("npc_placement", {}).get("trainer_slots", [])
            for member in slot.get("members", []))
        entries = [profiles[key] for key in ids if key in profiles]
        if entries:
            by_settlement[identifier] = entries
    write_json(output / "data/cobbleventure_player_menu/map/field-move-npcs.json", {"settlements": by_settlement})
    write_json(output / "assets/cobbleventure_adventure/event-resource-names.json", {"resources": names})
    conditions = {}

    def visit(value):
        if isinstance(value, dict):
            kind = value.get("type")
            if kind in ("item", "has_item") and isinstance(value.get("item"), str):
                count = max(1, int(value.get("count") or 1))
                key = value["item"] + "\0" + str(count)
                objective = "cvi_" + hashlib.sha1(key.encode()).hexdigest()[:12]
                conditions[objective] = {"type": "item", "item": value["item"], "count": count}
            elif kind in ("variable", "badge", "pokemon", "party_count"):
                # Groovy JsonOutput escapes non-ASCII and serializes without spaces.
                key = json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
                objective = "cvc_" + hashlib.sha1(key.encode()).hexdigest()[:12]
                conditions[objective] = value
            for child in value.values():
                visit(child)
        elif isinstance(value, list):
            for child in value:
                visit(child)

    for source in sorted(content.rglob("*.json")):
        if source.relative_to(content).parts[0] in {"resources", "schemas"}:
            continue
        visit(json.loads(source.read_text(encoding="utf-8")))
    write_json(output / "data/cobbleventure_player_menu/bag/bag-conditions.json", {"conditions": conditions})


def import_tool(name: str, path: Path):
    sys.path.insert(0, str(path.parent))
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


def publish(stage: Path, destination: Path, workspace: Path) -> None:
    """Replace only our owned directory, rolling back if publication fails."""
    destination = destination.resolve()
    destination.relative_to(workspace.resolve())
    backup = destination.with_name(destination.name + ".previous")
    if backup.exists():
        raise ValueError(f"Previous publication needs recovery: {backup}")
    if destination.exists() and not (destination / "content-manifest.json").is_file():
        raise ValueError(f"Refusing to replace an unowned directory: {destination}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        destination.rename(backup)
    try:
        stage.rename(destination)
    except BaseException:
        if backup.exists():
            backup.rename(destination)
        raise
    if backup.exists():
        shutil.rmtree(backup)


def build(root: Path, project: Path, language: str = "ko_kr", *,
          install_root: Path | None = None, archive: Path | None = None) -> Path:
    root, project = root.resolve(), project.resolve()
    versions = artifact_versions.load(root)
    engine_content_contract.validate(project)
    os.environ["COBBLEVENTURE_PROJECT_PATH"] = str(project)
    os.environ["COBBLEVENTURE_EXPORT_LANGUAGE"] = language
    manager = import_tool("content_manager", root / "tools/content-manager/content_manager.py")
    install_root = (install_root or root / "pack/overrides/development-placeholder").resolve()
    archive = (archive or artifact_versions.content_archive(root, versions)).resolve()
    install_root.relative_to(root)
    archive.relative_to(root)
    staging = root / "staging"
    staging.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="content-", dir=staging) as temporary:
        working = Path(temporary)
        bundle = working / "bundle"
        bundle.mkdir()
        manager.generate_content(project, output=working / "generated", dependency_root=root)
        for module in MODULES:
            source_root = project / "content/resources" / f"cobbleventure-{module}"
            for category in ("assets", "data"):
                for source in sorted((source_root / category).rglob("*")):
                    if not source.is_file():
                        continue
                    relative = source.relative_to(source_root)
                    # These NPC representations are regenerated from the selected project.
                    if relative.as_posix().startswith("data/easy_npc/preset/"):
                        continue
                    target = bundle / relative
                    if target.is_file() and target.read_bytes() != source.read_bytes():
                        if category == "data" and "/tags/" in relative.as_posix():
                            previous = json.loads(target.read_text(encoding="utf-8"))
                            current = json.loads(source.read_text(encoding="utf-8"))
                            if current.get("replace", False):
                                write_json(target, current)
                            else:
                                values = previous.get("values", [])
                                values.extend(value for value in current.get("values", []) if value not in values)
                                write_json(target, {**previous, "values": values})
                            continue
                        raise ValueError(f"Conflicting module content: {relative} ({module})")
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(source, target)
        builder = import_tool("build_data_mod", root / "tools/mod-builder/build_data_mod.py")
        builder.OUTPUT = working / "compiled"
        builder.GENERATED_CONTENT_DIR = working / "generated"
        builder.build(root)
        for category in ("assets", "data"):
            copy_tree(builder.OUTPUT / category, bundle / category)
        skin_overrides.apply_to_bundle(root, bundle)
        presets = import_tool("generate_easy_npc_presets", root / "tools/content-manager/generate_easy_npc_presets.py")
        presets.RESOURCE_ROOT = bundle
        presets.PACK_OVERRIDE = working / "overrides"
        presets.generate()
        project_resources(project / "content", bundle, language)
        manager._write_economy_species_overrides(root,
            json.loads((project / "content/catalogs/economy.json").read_text(encoding="utf-8")),
            bundle / "data/cobblemon/species")
        compile_theme_resources(project, working, bundle)
        compile_supplemental_resources(root, project, working, bundle)
        validate_cobblemon_species_payload(bundle)
        write_manifest(bundle, project, language, versions["content_version"])
        archive.parent.mkdir(parents=True, exist_ok=True)
        temporary_archive = working / "content.zip"
        with zipfile.ZipFile(temporary_archive, "w", zipfile.ZIP_DEFLATED) as package:
            for path in sorted(bundle.rglob("*")):
                if path.is_file():
                    info = zipfile.ZipInfo("config/cobbleventure/content/" + path.relative_to(bundle).as_posix())
                    info.compress_type = zipfile.ZIP_DEFLATED
                    package.writestr(info, path.read_bytes())
            for path in sorted(presets.PACK_OVERRIDE.rglob("*")):
                if path.is_file():
                    info = zipfile.ZipInfo(path.relative_to(presets.PACK_OVERRIDE).as_posix())
                    info.compress_type = zipfile.ZIP_DEFLATED
                    package.writestr(info, path.read_bytes())
        publish(bundle, install_root / "config/cobbleventure/content", root)
        copy_tree(presets.PACK_OVERRIDE, install_root)
        os.replace(temporary_archive, archive)
        return archive


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--project", type=Path)
    parser.add_argument("--language", choices=("ko_kr", "en_us"), default="ko_kr")
    parser.add_argument("--install-root", type=Path, help="Installation staging directory inside the repository")
    parser.add_argument("--archive", type=Path, help="Output ZIP inside the repository")
    parser.add_argument("--authoring", choices=("structure-builder", "live-nbt-editor"))
    args = parser.parse_args()
    project = args.project or Path(os.environ.get("COBBLEVENTURE_PROJECT_PATH",
        args.root / "content-projects/cobbleventure-main"))
    if args.authoring:
        print(build_authoring_content(args.root.resolve(), project.resolve(), args.authoring))
    else:
        print(build(args.root, project, args.language, install_root=args.install_root, archive=args.archive))


if __name__ == "__main__":
    main()

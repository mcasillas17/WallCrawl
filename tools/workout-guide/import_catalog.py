#!/usr/bin/env python3
"""Import a pinned Workout Guide checkout into WallCrawl Android assets."""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any


SCHEMA_VERSION = 1
MAX_EXERCISES = 5_000
MAX_FRAMES_PER_EXERCISE = 10
MAX_LIST_ITEMS = 1_000
MAX_STRING_LENGTH = 8_192
MAX_JSON_DEPTH = 12
SAFE_ID_PATTERN = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
SUPPORTED_EXERCISE_TYPES = {
    "assisted_bodyweight",
    "bodyweight_reps",
    "distance_duration",
    "duration",
    "weight_reps",
}
SUPPORTED_MOVEMENT_PATTERNS = {
    "horizontal_push",
    "vertical_push",
    "horizontal_pull",
    "vertical_pull",
    "squat",
    "hinge",
    "lunge",
    "isolation",
    "carry",
    "core",
    "other",
}
SUPPORTED_DIFFICULTIES = {"beginner", "intermediate", "advanced"}
SUPPORTED_MECHANICS = {"compound", "isolation"}
SUPPORTED_PROGRESSION_TYPES = {
    "assistance_reduction",
    "distance",
    "duration",
    "load",
    "repetitions",
    "repetitions_then_load",
}


class CatalogImportError(ValueError):
    """Raised when pinned source or generated catalog validation fails."""


@dataclass(frozen=True)
class ImportSummary:
    exercise_count: int
    frame_count: int
    svg_bytes: int
    changed: bool


def import_catalog(
    source_root: Path,
    output_root: Path,
    config_path: Path,
    overrides_path: Path,
    check_only: bool,
) -> ImportSummary:
    source_root = source_root.resolve()
    output_root = output_root.resolve()
    config = _read_object(config_path, "import config")
    overrides = _read_object(overrides_path, "programming overrides")
    _require_schema_version(config, "import config")
    _require_schema_version(overrides, "programming overrides")

    source_repository = _required_string(config, "sourceRepository", "import config")
    source_commit = _required_string(config, "sourceCommit", "import config")
    if not re.fullmatch(r"[0-9a-f]{40}", source_commit):
        raise CatalogImportError("sourceCommit must be a 40-character lowercase git commit")

    manifest_relative = _safe_relative_path(
        _required_string(config, "manifestPath", "import config"),
        "manifestPath",
    )
    asset_base_relative = _safe_relative_path(
        _required_string(config, "assetBasePath", "import config"),
        "assetBasePath",
    )
    license_files = _required_string_list(config, "licenseFiles", "import config", maximum=20)
    license_relatives = [_safe_relative_path(item, "licenseFiles entry") for item in license_files]
    expected_exercises = _required_int(config, "expectedExerciseCount", "import config", minimum=1, maximum=MAX_EXERCISES)
    expected_frames = _required_int(
        config,
        "expectedFrameCount",
        "import config",
        minimum=1,
        maximum=MAX_EXERCISES * MAX_FRAMES_PER_EXERCISE,
    )
    aliases = _required_object(config, "idAliases", "import config")

    if not source_root.is_dir():
        raise CatalogImportError(f"Workout Guide source directory does not exist: {source_root}")
    _validate_source_git_state(
        source_root=source_root,
        pinned_commit=source_commit,
        imported_paths=[manifest_relative, asset_base_relative, *license_relatives],
    )

    manifest_path = _source_file(source_root, manifest_relative, "manifest")
    manifest = _read_array(manifest_path, "upstream manifest")
    if len(manifest) != expected_exercises:
        raise CatalogImportError(
            f"Expected {expected_exercises} exercises for pinned revision, found {len(manifest)}"
        )
    if len(manifest) > MAX_EXERCISES:
        raise CatalogImportError(f"Upstream manifest exceeds {MAX_EXERCISES} exercises")

    for license_relative in license_relatives:
        _source_file(source_root, license_relative, f"license file {license_relative.as_posix()}")

    programming_by_id = _required_object(overrides, "exercises", "programming overrides")
    normalized_exercises: list[dict[str, Any]] = []
    frame_sources: list[tuple[Path, str]] = []
    source_ids: set[str] = set()
    source_slugs: set[str] = set()
    wallcrawl_ids: set[str] = set()
    used_alias_sources: set[str] = set()

    for position, raw_exercise in enumerate(manifest):
        exercise = _expect_object(raw_exercise, f"exercise[{position}]")
        source_id = _required_string(exercise, "id", f"exercise[{position}]")
        source_slug = _required_string(exercise, "slug", f"exercise[{position}]")
        _validate_safe_id(source_id, f"exercise[{position}].id", allow_exercise_prefix=True)
        _validate_safe_id(source_slug, f"exercise[{position}].slug")
        if source_id in source_ids:
            raise CatalogImportError(f"Duplicate source exercise ID: {source_id}")
        if source_slug in source_slugs:
            raise CatalogImportError(f"Duplicate source exercise slug: {source_slug}")
        source_ids.add(source_id)
        source_slugs.add(source_slug)

        alias_value = aliases.get(source_id)
        if alias_value is None:
            wallcrawl_id = source_slug
            search_aliases: list[str] = []
        else:
            alias = _expect_object(alias_value, f"idAliases.{source_id}")
            wallcrawl_id = _required_string(alias, "wallCrawlId", f"idAliases.{source_id}")
            search_aliases = _required_string_list(
                alias,
                "searchAliases",
                f"idAliases.{source_id}",
                maximum=50,
            )
            used_alias_sources.add(source_id)
        _validate_safe_id(wallcrawl_id, f"WallCrawl ID for {source_id}")
        if wallcrawl_id in wallcrawl_ids:
            raise CatalogImportError(f"Duplicate WallCrawl exercise ID: {wallcrawl_id}")
        wallcrawl_ids.add(wallcrawl_id)

        name = _required_string(exercise, "name", source_id)
        exercise_type = _required_string(exercise, "exerciseType", source_id)
        if exercise_type not in SUPPORTED_EXERCISE_TYPES:
            raise CatalogImportError(f"Unsupported exercise type for {source_id}: {exercise_type}")
        equipment = _required_string(exercise, "equipment", source_id)
        primary_muscle = _required_string(exercise, "primaryMuscle", source_id)
        secondary_muscles = _required_string_list(exercise, "secondaryMuscles", source_id, maximum=50)
        is_stretch = _required_bool(exercise, "isStretch", source_id)
        attribution = _required_attribution(exercise, "attribution", source_id)

        raw_frames = _required_array(exercise, "frames", source_id)
        if len(raw_frames) != 3:
            raise CatalogImportError(f"Exercise {source_id} must contain exactly three frames")
        if len(raw_frames) > MAX_FRAMES_PER_EXERCISE:
            raise CatalogImportError(f"Exercise {source_id} has too many frames")

        normalized_frames: list[dict[str, Any]] = []
        frame_indices: set[int] = set()
        for frame_position, raw_frame in enumerate(raw_frames):
            frame_label = f"{source_id}.frames[{frame_position}]"
            frame = _expect_object(raw_frame, frame_label)
            index = _required_int(frame, "index", frame_label, minimum=1, maximum=3)
            if index in frame_indices:
                raise CatalogImportError(f"Duplicate frame index {index} for {source_id}")
            frame_indices.add(index)
            frame_relative = _safe_relative_path(_required_string(frame, "path", frame_label), f"{frame_label}.path")
            expected_frame_relative = PurePosixPath("assets") / source_slug / f"frame-{index}.svg"
            if frame_relative != expected_frame_relative:
                raise CatalogImportError(
                    f"Unsafe or unexpected frame path for {source_id}: {frame_relative.as_posix()}"
                )
            source_frame = _source_file(
                source_root / Path(asset_base_relative.as_posix()),
                frame_relative,
                f"frame {source_id}/{index}",
                reject_symlink=True,
            )
            if source_frame.suffix.lower() != ".svg":
                raise CatalogImportError(f"Frame {source_id}/{index} is not an SVG")
            frame_format = _required_string(frame, "format", frame_label)
            if frame_format != "svg":
                raise CatalogImportError(f"Frame {source_id}/{index} format must be svg")
            width = _required_int(frame, "width", frame_label, minimum=1, maximum=8_192)
            height = _required_int(frame, "height", frame_label, minimum=1, maximum=8_192)
            frame_attribution = _required_attribution(frame, "attribution", frame_label)
            destination_relative = f"assets/{source_slug}/frame-{index}.svg"
            frame_sources.append((source_frame, destination_relative))
            normalized_frames.append(
                {
                    "index": index,
                    "assetPath": f"workout-guide/{destination_relative}",
                    "widthPx": width,
                    "heightPx": height,
                    "format": frame_format,
                    "attribution": frame_attribution,
                }
            )
        if frame_indices != {1, 2, 3}:
            raise CatalogImportError(f"Exercise {source_id} frame indices must be 1, 2, and 3")

        programming_value = programming_by_id.get(wallcrawl_id)
        programming = None
        if programming_value is not None:
            programming = _normalize_programming(programming_value, wallcrawl_id)

        normalized_exercises.append(
            {
                "id": wallcrawl_id,
                "sourceId": source_id,
                "sourceSlug": source_slug,
                "name": name,
                "searchAliases": sorted(set(search_aliases), key=str.casefold),
                "exerciseType": exercise_type,
                "listedEquipment": [equipment],
                "primaryMuscles": [primary_muscle],
                "secondaryMuscles": secondary_muscles,
                "isStretch": is_stretch,
                "frames": sorted(normalized_frames, key=lambda item: item["index"]),
                "attribution": attribution,
                **({"programming": programming} if programming is not None else {}),
            }
        )

    unknown_aliases = sorted(set(aliases) - used_alias_sources)
    if unknown_aliases:
        raise CatalogImportError(f"ID aliases reference unknown source exercises: {', '.join(unknown_aliases)}")
    unknown_programming = sorted(set(programming_by_id) - wallcrawl_ids)
    if unknown_programming:
        raise CatalogImportError(
            f"Programming override references unknown WalCrawl exercise ID: {unknown_programming[0]}"
        )
    for exercise in normalized_exercises:
        programming = exercise.get("programming")
        if programming is None:
            continue
        for alternative_id in programming["alternativeExerciseIds"]:
            if alternative_id not in wallcrawl_ids:
                raise CatalogImportError(
                    f"Programming alternative for {exercise['id']} references unknown ID: {alternative_id}"
                )
            if alternative_id == exercise["id"]:
                raise CatalogImportError(f"Exercise {exercise['id']} cannot list itself as an alternative")

    if len(frame_sources) != expected_frames:
        raise CatalogImportError(
            f"Expected {expected_frames} frames for pinned revision, found {len(frame_sources)}"
        )
    destination_paths = [destination for _, destination in frame_sources]
    if len(set(destination_paths)) != len(destination_paths):
        raise CatalogImportError("Generated frame paths are not unique")

    normalized_exercises.sort(key=lambda item: item["id"])
    catalog = {
        "schemaVersion": SCHEMA_VERSION,
        "source": {
            "repository": source_repository,
            "commit": source_commit,
            "assetLicense": "CC-BY-SA-4.0",
        },
        "exercises": normalized_exercises,
    }

    output_root.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".workout-guide-import-", dir=output_root.parent) as temporary:
        staged_root = Path(temporary) / output_root.name
        staged_root.mkdir()
        _write_json(staged_root / "catalog.json", catalog)
        shutil.copyfile(manifest_path, staged_root / "upstream-manifest.json")
        for license_relative in license_relatives:
            source_license = _source_file(source_root, license_relative, f"license file {license_relative.as_posix()}")
            shutil.copyfile(source_license, staged_root / license_relative.name)
        for source_frame, destination_relative in frame_sources:
            destination = staged_root / destination_relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source_frame, destination)
        notice = (
            "# Workout Guide catalog\n\n"
            "WallCrawl bundles unmodified Workout Guide SVG exercise illustrations and normalized metadata.\n\n"
            f"Source: {source_repository}\n\n"
            f"Pinned commit: `{source_commit}`\n\n"
            f"Imported exercises: {len(normalized_exercises)}\n\n"
            f"Imported SVG frames: {len(frame_sources)}\n\n"
            "PNG counterparts are intentionally not bundled. See LICENSE, LICENSE-ASSETS, "
            "ATTRIBUTION.md, and upstream-manifest.json in this directory.\n"
        )
        (staged_root / "NOTICE.md").write_text(notice, encoding="utf-8", newline="\n")
        _validate_generated_tree(staged_root, expected_exercises, expected_frames)

        changed = not _trees_equal(staged_root, output_root)
        svg_bytes = sum(path.stat().st_size for path in staged_root.rglob("*.svg"))
        summary = ImportSummary(
            exercise_count=len(normalized_exercises),
            frame_count=len(frame_sources),
            svg_bytes=svg_bytes,
            changed=changed,
        )
        if check_only:
            if changed:
                raise CatalogImportError(f"Generated Workout Guide bundle differs from {output_root}")
            return summary
        if changed:
            _replace_directory(staged_root, output_root)
        return summary


def _normalize_programming(raw_value: Any, exercise_id: str) -> dict[str, Any]:
    raw = _expect_object(raw_value, f"programming.{exercise_id}")
    combinations_raw = _required_array(raw, "requiredEquipmentCombinations", exercise_id)
    if len(combinations_raw) > 20:
        raise CatalogImportError(f"Too many equipment combinations for {exercise_id}")
    combinations: list[list[str]] = []
    for index, combination_value in enumerate(combinations_raw):
        if not isinstance(combination_value, list):
            raise CatalogImportError(f"Equipment combination {index} for {exercise_id} must be an array")
        if len(combination_value) > 20:
            raise CatalogImportError(f"Equipment combination {index} for {exercise_id} is too large")
        combination = [_bounded_string(item, f"equipment for {exercise_id}") for item in combination_value]
        if len(set(combination)) != len(combination):
            raise CatalogImportError(f"Equipment combination {index} for {exercise_id} contains duplicates")
        combinations.append(combination)

    movement_pattern = _required_string(raw, "movementPattern", exercise_id)
    difficulty = _required_string(raw, "difficulty", exercise_id)
    mechanics = _required_string(raw, "mechanics", exercise_id)
    progression_type = _required_string(raw, "progressionType", exercise_id)
    if movement_pattern not in SUPPORTED_MOVEMENT_PATTERNS:
        raise CatalogImportError(f"Unsupported movement pattern for {exercise_id}: {movement_pattern}")
    if difficulty not in SUPPORTED_DIFFICULTIES:
        raise CatalogImportError(f"Unsupported difficulty for {exercise_id}: {difficulty}")
    if mechanics not in SUPPORTED_MECHANICS:
        raise CatalogImportError(f"Unsupported mechanics for {exercise_id}: {mechanics}")
    if progression_type not in SUPPORTED_PROGRESSION_TYPES:
        raise CatalogImportError(f"Unsupported progression type for {exercise_id}: {progression_type}")

    rep_range = _required_object(raw, "recommendedRepRange", exercise_id)
    rep_min = _required_int(rep_range, "min", f"{exercise_id}.recommendedRepRange", minimum=1, maximum=1_000)
    rep_max = _required_int(rep_range, "max", f"{exercise_id}.recommendedRepRange", minimum=1, maximum=1_000)
    if rep_min > rep_max:
        raise CatalogImportError(f"Recommended rep range is reversed for {exercise_id}")
    fatigue_score = _required_int(raw, "fatigueScore", exercise_id, minimum=1, maximum=5)
    alternatives = _required_string_list(raw, "alternativeExerciseIds", exercise_id, maximum=100)
    coaching_summary = _required_string(raw, "coachingSummary", exercise_id)

    return {
        "requiredEquipmentCombinations": combinations,
        "movementPattern": movement_pattern,
        "difficulty": difficulty,
        "mechanics": mechanics,
        "recommendedRepRange": {"min": rep_min, "max": rep_max},
        "fatigueScore": fatigue_score,
        "progressionType": progression_type,
        "alternativeExerciseIds": alternatives,
        "coachingSummary": coaching_summary,
    }


def _validate_source_git_state(source_root: Path, pinned_commit: str, imported_paths: list[PurePosixPath]) -> None:
    try:
        head = _git(source_root, "rev-parse", "HEAD").strip()
    except subprocess.CalledProcessError as error:
        raise CatalogImportError(f"Workout Guide source is not a readable git checkout: {source_root}") from error
    if head != pinned_commit:
        raise CatalogImportError(f"Workout Guide source must be at pinned commit {pinned_commit}; found {head}")
    path_arguments = [path.as_posix() for path in imported_paths]
    status = _git(source_root, "status", "--porcelain", "--untracked-files=no", "--", *path_arguments)
    if status.strip():
        raise CatalogImportError("Workout Guide imported paths contain tracked changes")


def _git(source_root: Path, *arguments: str) -> str:
    result = subprocess.run(
        ["git", *arguments],
        cwd=source_root,
        check=True,
        text=True,
        capture_output=True,
    )
    return result.stdout


def _source_file(
    root: Path,
    relative: PurePosixPath,
    label: str,
    reject_symlink: bool = False,
) -> Path:
    candidate = root / Path(relative.as_posix())
    if reject_symlink and candidate.is_symlink():
        raise CatalogImportError(f"{label} must not be a symlink: {relative.as_posix()}")
    try:
        resolved_root = root.resolve(strict=True)
        resolved_candidate = candidate.resolve(strict=True)
    except FileNotFoundError as error:
        raise CatalogImportError(f"Missing {label}: {relative.as_posix()}") from error
    if not resolved_candidate.is_relative_to(resolved_root):
        raise CatalogImportError(f"Unsafe {label} path escapes its source root: {relative.as_posix()}")
    if not resolved_candidate.is_file():
        raise CatalogImportError(f"Expected regular {label}: {relative.as_posix()}")
    return candidate


def _safe_relative_path(value: str, label: str) -> PurePosixPath:
    if "\\" in value:
        raise CatalogImportError(f"Unsafe {label}: backslashes are not allowed")
    path = PurePosixPath(value)
    if not value or path.is_absolute() or any(part in {"", ".", ".."} for part in path.parts):
        raise CatalogImportError(f"Unsafe {label}: {value}")
    if path.as_posix() != value:
        raise CatalogImportError(f"Unsafe {label}: {value}")
    return path


def _validate_safe_id(value: str, label: str, allow_exercise_prefix: bool = False) -> None:
    checked = value.removeprefix("exercise-") if allow_exercise_prefix else value
    if not checked or not SAFE_ID_PATTERN.fullmatch(checked):
        raise CatalogImportError(f"Unsafe {label}: {value}")


def _required_attribution(container: dict[str, Any], key: str, label: str) -> dict[str, Any]:
    attribution = _required_object(container, key, label)
    _required_string(attribution, "creator", f"{label}.{key}")
    _required_string(attribution, "creatorUrl", f"{label}.{key}")
    _required_string(attribution, "license", f"{label}.{key}")
    _required_string(attribution, "licenseUrl", f"{label}.{key}")
    source = attribution.get("source")
    if source is not None:
        source_object = _expect_object(source, f"{label}.{key}.source")
        for source_key in ("name", "url", "license", "licenseUrl", "changes"):
            _required_string(source_object, source_key, f"{label}.{key}.source")
    return _bounded_json_value(attribution, f"{label}.{key}", depth=0)


def _bounded_json_value(value: Any, label: str, depth: int) -> Any:
    if depth > MAX_JSON_DEPTH:
        raise CatalogImportError(f"{label} exceeds maximum JSON nesting depth")
    if value is None or isinstance(value, bool):
        return value
    if isinstance(value, int) and not isinstance(value, bool):
        return value
    if isinstance(value, float):
        if not value.is_integer():
            raise CatalogImportError(f"{label} contains a non-integral number")
        return int(value)
    if isinstance(value, str):
        return _bounded_string(value, label)
    if isinstance(value, list):
        if len(value) > MAX_LIST_ITEMS:
            raise CatalogImportError(f"{label} contains too many items")
        return [_bounded_json_value(item, label, depth + 1) for item in value]
    if isinstance(value, dict):
        if len(value) > MAX_LIST_ITEMS:
            raise CatalogImportError(f"{label} contains too many fields")
        result: dict[str, Any] = {}
        for key, item in value.items():
            checked_key = _bounded_string(key, f"{label} field name")
            result[checked_key] = _bounded_json_value(item, f"{label}.{checked_key}", depth + 1)
        return result
    raise CatalogImportError(f"{label} contains unsupported JSON value {type(value).__name__}")


def _validate_generated_tree(root: Path, expected_exercises: int, expected_frames: int) -> None:
    catalog = _read_object(root / "catalog.json", "generated catalog")
    exercises = _required_array(catalog, "exercises", "generated catalog")
    if len(exercises) != expected_exercises:
        raise CatalogImportError("Generated exercise count changed during staging")
    svg_files = list(root.rglob("*.svg"))
    if len(svg_files) != expected_frames:
        raise CatalogImportError("Generated SVG count changed during staging")
    if list(root.rglob("*.png")):
        raise CatalogImportError("Generated bundle unexpectedly contains PNG files")
    for path in root.rglob("*"):
        if path.is_symlink():
            raise CatalogImportError(f"Generated bundle contains symlink: {path}")


def _trees_equal(left: Path, right: Path) -> bool:
    if not right.is_dir():
        return False
    left_files = sorted(path.relative_to(left) for path in left.rglob("*") if path.is_file())
    right_files = sorted(path.relative_to(right) for path in right.rglob("*") if path.is_file())
    if left_files != right_files:
        return False
    return all((left / relative).read_bytes() == (right / relative).read_bytes() for relative in left_files)


def _replace_directory(staged_root: Path, output_root: Path) -> None:
    backup = output_root.with_name(f".{output_root.name}.import-backup")
    if backup.exists():
        raise CatalogImportError(f"Refusing import because backup path already exists: {backup}")
    had_output = output_root.exists()
    if had_output:
        os.replace(output_root, backup)
    try:
        os.replace(staged_root, output_root)
    except BaseException:
        if had_output and backup.exists() and not output_root.exists():
            os.replace(backup, output_root)
        raise
    if backup.exists():
        shutil.rmtree(backup)


def _read_object(path: Path, label: str) -> dict[str, Any]:
    value = _read_json(path, label)
    return _expect_object(value, label)


def _read_array(path: Path, label: str) -> list[Any]:
    value = _read_json(path, label)
    if not isinstance(value, list):
        raise CatalogImportError(f"{label} must contain a JSON array")
    return value


def _read_json(path: Path, label: str) -> Any:
    try:
        if path.stat().st_size > 50_000_000:
            raise CatalogImportError(f"{label} exceeds the 50 MB input limit")
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        raise CatalogImportError(f"Missing {label}: {path}") from error
    except UnicodeDecodeError as error:
        raise CatalogImportError(f"{label} is not UTF-8: {path}") from error
    except json.JSONDecodeError as error:
        raise CatalogImportError(f"Malformed JSON in {label}: {error}") from error


def _write_json(path: Path, value: Any) -> None:
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def _expect_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise CatalogImportError(f"{label} must be a JSON object")
    return value


def _required_object(container: dict[str, Any], key: str, label: str) -> dict[str, Any]:
    if key not in container:
        raise CatalogImportError(f"Missing {label}.{key}")
    return _expect_object(container[key], f"{label}.{key}")


def _required_array(container: dict[str, Any], key: str, label: str) -> list[Any]:
    value = container.get(key)
    if not isinstance(value, list):
        raise CatalogImportError(f"{label}.{key} must be an array")
    return value


def _required_string(container: dict[str, Any], key: str, label: str) -> str:
    if key not in container:
        raise CatalogImportError(f"Missing {label}.{key}")
    return _bounded_string(container[key], f"{label}.{key}")


def _bounded_string(value: Any, label: str) -> str:
    if not isinstance(value, str):
        raise CatalogImportError(f"{label} must be a string")
    if not value.strip():
        raise CatalogImportError(f"{label} must not be blank")
    if value != value.strip():
        raise CatalogImportError(f"{label} must not have surrounding whitespace")
    if len(value) > MAX_STRING_LENGTH:
        raise CatalogImportError(f"{label} exceeds {MAX_STRING_LENGTH} characters")
    return value


def _required_string_list(
    container: dict[str, Any],
    key: str,
    label: str,
    maximum: int,
) -> list[str]:
    values = _required_array(container, key, label)
    if len(values) > maximum:
        raise CatalogImportError(f"{label}.{key} contains more than {maximum} values")
    result = [_bounded_string(item, f"{label}.{key}") for item in values]
    if len(set(result)) != len(result):
        raise CatalogImportError(f"{label}.{key} contains duplicate values")
    return result


def _required_int(
    container: dict[str, Any],
    key: str,
    label: str,
    minimum: int,
    maximum: int,
) -> int:
    value = container.get(key)
    if not isinstance(value, int) or isinstance(value, bool):
        raise CatalogImportError(f"{label}.{key} must be an integer")
    if value < minimum or value > maximum:
        raise CatalogImportError(f"{label}.{key} must be between {minimum} and {maximum}")
    return value


def _required_bool(container: dict[str, Any], key: str, label: str) -> bool:
    value = container.get(key)
    if not isinstance(value, bool):
        raise CatalogImportError(f"{label}.{key} must be a boolean")
    return value


def _require_schema_version(container: dict[str, Any], label: str) -> None:
    version = _required_int(container, "schemaVersion", label, minimum=SCHEMA_VERSION, maximum=SCHEMA_VERSION)
    if version != SCHEMA_VERSION:
        raise CatalogImportError(f"Unsupported {label} schema version: {version}")


def _parse_arguments() -> argparse.Namespace:
    repository_root = Path(__file__).resolve().parents[2]
    tool_root = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True, help="Local Workout Guide checkout")
    parser.add_argument(
        "--output",
        type=Path,
        default=repository_root / "app/src/main/assets/workout-guide",
        help="Generated Android asset directory",
    )
    parser.add_argument("--config", type=Path, default=tool_root / "import-config.json")
    parser.add_argument("--overrides", type=Path, default=tool_root / "programming-overrides.json")
    parser.add_argument("--check", action="store_true", help="Verify checked-in output without changing it")
    return parser.parse_args()


def main() -> int:
    arguments = _parse_arguments()
    try:
        summary = import_catalog(
            source_root=arguments.source,
            output_root=arguments.output,
            config_path=arguments.config,
            overrides_path=arguments.overrides,
            check_only=arguments.check,
        )
    except (CatalogImportError, OSError, subprocess.CalledProcessError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    state = "up to date" if arguments.check or not summary.changed else "imported"
    print(
        f"Workout Guide {state}: exercises={summary.exercise_count} "
        f"frames={summary.frame_count} svg_bytes={summary.svg_bytes}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

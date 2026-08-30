#!/usr/bin/env python3
"""Import a pinned Workout Guide checkout into WallCrawl Android assets."""

from __future__ import annotations

import argparse
from collections import Counter
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
MAX_STRING_LENGTH = 256
MAX_DESCRIPTION_LENGTH = 2_000
MAX_URL_LENGTH = 2_048
MAX_RAW_JSON_STRING_LENGTH = 8_192
MAX_JSON_DEPTH = 12
MAX_REVIEWED_PAYLOAD_BYTES = 1_000_000
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
    reviewed_metadata_path: Path,
    review_schema_path: Path,
    review_report_path: Path,
    check_only: bool,
) -> ImportSummary:
    source_root = source_root.resolve()
    output_root = output_root.resolve()
    config = _read_object(config_path, "import config")
    overrides = _read_object(overrides_path, "programming overrides")
    review_schema = _read_object(review_schema_path, "review schema")
    reviewed_document = _read_json(
        reviewed_metadata_path,
        "reviewed metadata",
        maximum_bytes=MAX_REVIEWED_PAYLOAD_BYTES,
    )
    _bounded_json_value(reviewed_document, "reviewed metadata", depth=0)
    _reject_forbidden_reviewed_numeric_fields(reviewed_document, "reviewed metadata")
    _validate_json_schema(
        reviewed_document,
        review_schema,
        review_schema,
        "reviewed metadata",
        depth=0,
    )
    reviewed_root = _expect_object(reviewed_document, "reviewed metadata")
    reviewed_by_id = _required_object(reviewed_root, "exercises", "reviewed metadata")
    _require_schema_version(config, "import config")
    _require_schema_version(overrides, "programming overrides")

    source_repository = _required_string(config, "sourceRepository", "import config")
    if not source_repository.startswith("https://"):
        raise CatalogImportError("sourceRepository must be an HTTPS URL")
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
    catalog_attribution: dict[str, Any] | None = None
    visual_width: int | None = None
    visual_height: int | None = None

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
        exercise_catalog_attribution = {
            key: attribution[key]
            for key in ("creator", "creatorUrl", "license", "licenseUrl")
        }
        if catalog_attribution is None:
            catalog_attribution = exercise_catalog_attribution
        elif exercise_catalog_attribution != catalog_attribution:
            raise CatalogImportError(
                f"Exercise {source_id} uses attribution that differs from the catalog attribution"
            )

        raw_frames = _required_array(exercise, "frames", source_id)
        if len(raw_frames) != 3:
            raise CatalogImportError(f"Exercise {source_id} must contain exactly three frames")
        if len(raw_frames) > MAX_FRAMES_PER_EXERCISE:
            raise CatalogImportError(f"Exercise {source_id} has too many frames")

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
            _required_attribution(frame, "attribution", frame_label)
            if visual_width is None:
                visual_width = width
                visual_height = height
            elif width != visual_width or height != visual_height:
                raise CatalogImportError(
                    f"Frame {source_id}/{index} dimensions differ from the catalog visual specification"
                )
            destination_relative = f"assets/{source_slug}/frame-{index}.svg"
            frame_sources.append((source_frame, destination_relative))
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

    unknown_reviewed = sorted(set(reviewed_by_id) - wallcrawl_ids)
    if unknown_reviewed:
        raise CatalogImportError(
            f"Reviewed metadata references unknown WallCrawl exercise ID: {unknown_reviewed[0]}"
        )
    exercises_by_id = {exercise["id"]: exercise for exercise in normalized_exercises}
    normalized_reviewed = {
        exercise_id: _normalize_reviewed_metadata(
            raw_value,
            exercise_id,
            exercises_by_id[exercise_id],
        )
        for exercise_id, raw_value in reviewed_by_id.items()
    }
    _validate_reviewed_graphs(normalized_reviewed, exercises_by_id)
    for exercise_id, reviewed_metadata in normalized_reviewed.items():
        exercises_by_id[exercise_id]["reviewedMetadata"] = reviewed_metadata

    if len(frame_sources) != expected_frames:
        raise CatalogImportError(
            f"Expected {expected_frames} frames for pinned revision, found {len(frame_sources)}"
        )
    destination_paths = [destination for _, destination in frame_sources]
    if len(set(destination_paths)) != len(destination_paths):
        raise CatalogImportError("Generated frame paths are not unique")

    normalized_exercises.sort(key=lambda item: item["id"])
    if catalog_attribution is None or visual_width is None or visual_height is None:
        raise CatalogImportError("Pinned Workout Guide catalog is missing visual attribution or dimensions")
    catalog = {
        "schemaVersion": SCHEMA_VERSION,
        "source": {
            "repository": source_repository,
            "commit": source_commit,
            "assetLicense": "CC-BY-SA-4.0",
            "attribution": catalog_attribution,
        },
        "visuals": {
            "frameCount": 3,
            "widthPx": visual_width,
            "heightPx": visual_height,
            "format": "svg",
        },
        "exercises": normalized_exercises,
    }
    review_report = _render_review_report(normalized_reviewed)

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

        report_changed = not review_report_path.is_file() or review_report_path.read_text(
            encoding="utf-8"
        ) != review_report
        changed = not _trees_equal(staged_root, output_root) or report_changed
        svg_bytes = sum(path.stat().st_size for path in staged_root.rglob("*.svg"))
        summary = ImportSummary(
            exercise_count=len(normalized_exercises),
            frame_count=len(frame_sources),
            svg_bytes=svg_bytes,
            changed=changed,
        )
        if check_only:
            if changed:
                raise CatalogImportError("Generated Workout Guide bundle or review report differs")
            return summary
        if changed:
            if not _trees_equal(staged_root, output_root):
                _replace_directory(staged_root, output_root)
            review_report_path.parent.mkdir(parents=True, exist_ok=True)
            _atomic_write_text(review_report_path, review_report)
        return summary


def _normalize_programming(raw_value: Any, exercise_id: str) -> dict[str, Any]:
    raw = _expect_object(raw_value, f"programming.{exercise_id}")
    combinations_raw = _required_array(raw, "requiredEquipmentCombinations", exercise_id)
    if not combinations_raw:
        raise CatalogImportError(f"Equipment combinations must be non-empty for {exercise_id}")
    if len(combinations_raw) > 20:
        raise CatalogImportError(f"Too many equipment combinations for {exercise_id}")
    combinations: list[list[str]] = []
    for index, combination_value in enumerate(combinations_raw):
        if not isinstance(combination_value, list):
            raise CatalogImportError(f"Equipment combination {index} for {exercise_id} must be an array")
        if not combination_value:
            raise CatalogImportError(f"Equipment combinations must be non-empty for {exercise_id}")
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
    coaching_summary = _required_string(
        raw,
        "coachingSummary",
        exercise_id,
        maximum=MAX_DESCRIPTION_LENGTH,
    )

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


def _validate_json_schema(
    value: Any,
    schema: dict[str, Any],
    root_schema: dict[str, Any],
    label: str,
    depth: int,
) -> None:
    if depth > MAX_JSON_DEPTH:
        raise CatalogImportError(f"{label} exceeds maximum JSON nesting depth")
    reference = schema.get("$ref")
    if reference is not None:
        if not isinstance(reference, str) or not reference.startswith("#/$defs/"):
            raise CatalogImportError("review schema contains an unsupported reference")
        definition_name = reference.removeprefix("#/$defs/")
        definitions = root_schema.get("$defs")
        if not isinstance(definitions, dict) or definition_name not in definitions:
            raise CatalogImportError("review schema contains an unresolved local reference")
        definition = _expect_object(definitions[definition_name], "review schema definition")
        _validate_json_schema(value, definition, root_schema, label, depth + 1)
        return

    expected_types = schema.get("type")
    if expected_types is not None:
        type_names = [expected_types] if isinstance(expected_types, str) else expected_types
        if not isinstance(type_names, list) or not all(isinstance(item, str) for item in type_names):
            raise CatalogImportError("review schema contains an invalid type declaration")
        if not any(_matches_json_schema_type(value, item) for item in type_names):
            raise CatalogImportError(f"{label} has the wrong JSON type")

    if "const" in schema and value != schema["const"]:
        raise CatalogImportError(f"{label} does not match the required schema constant")
    enum_values = schema.get("enum")
    if enum_values is not None:
        if not isinstance(enum_values, list):
            raise CatalogImportError("review schema contains an invalid enum declaration")
        if value not in enum_values:
            raise CatalogImportError(f"{label} contains an unknown enum value")

    if isinstance(value, str):
        minimum_length = schema.get("minLength")
        maximum_length = schema.get("maxLength")
        if isinstance(minimum_length, int) and len(value) < minimum_length:
            raise CatalogImportError(f"{label} is shorter than the schema minimum")
        if isinstance(maximum_length, int) and len(value) > maximum_length:
            raise CatalogImportError(f"{label} exceeds {maximum_length} characters")
        pattern = schema.get("pattern")
        if pattern is not None:
            if not isinstance(pattern, str):
                raise CatalogImportError("review schema contains an invalid string pattern")
            if re.search(pattern, value) is None:
                raise CatalogImportError(f"{label} does not match the required format")

    if isinstance(value, int) and not isinstance(value, bool):
        minimum = schema.get("minimum")
        maximum = schema.get("maximum")
        if isinstance(minimum, int) and value < minimum:
            raise CatalogImportError(f"{label} is below the schema minimum")
        if isinstance(maximum, int) and value > maximum:
            raise CatalogImportError(f"{label} exceeds the schema maximum")

    if isinstance(value, list):
        minimum_items = schema.get("minItems")
        maximum_items = schema.get("maxItems")
        if isinstance(minimum_items, int) and len(value) < minimum_items:
            raise CatalogImportError(f"{label} has too few items")
        if isinstance(maximum_items, int) and len(value) > maximum_items:
            raise CatalogImportError(f"{label} contains more than {maximum_items} items")
        if schema.get("uniqueItems") is True:
            encoded = [json.dumps(item, ensure_ascii=False, sort_keys=True) for item in value]
            if len(set(encoded)) != len(encoded):
                raise CatalogImportError(f"{label} contains duplicate values")
        item_schema = schema.get("items")
        if item_schema is not None:
            checked_item_schema = _expect_object(item_schema, "review schema array items")
            for index, item in enumerate(value):
                _validate_json_schema(
                    item,
                    checked_item_schema,
                    root_schema,
                    f"{label}[{index}]",
                    depth + 1,
                )

    if isinstance(value, dict):
        minimum_properties = schema.get("minProperties")
        maximum_properties = schema.get("maxProperties")
        if isinstance(minimum_properties, int) and len(value) < minimum_properties:
            raise CatalogImportError(f"{label} has too few fields")
        if isinstance(maximum_properties, int) and len(value) > maximum_properties:
            raise CatalogImportError(f"{label} contains more than {maximum_properties} fields")
        property_names = schema.get("propertyNames")
        if property_names is not None:
            checked_name_schema = _expect_object(property_names, "review schema property names")
            for key in value:
                _validate_json_schema(
                    key,
                    checked_name_schema,
                    root_schema,
                    f"{label} field name",
                    depth + 1,
                )
        properties = schema.get("properties", {})
        if not isinstance(properties, dict):
            raise CatalogImportError("review schema properties must be an object")
        required = schema.get("required", [])
        if not isinstance(required, list) or not all(isinstance(item, str) for item in required):
            raise CatalogImportError("review schema required fields must be an array of strings")
        for required_name in required:
            if required_name not in value:
                raise CatalogImportError(f"Missing {label}.{required_name}")
        additional = schema.get("additionalProperties", True)
        for key, item in value.items():
            property_schema = properties.get(key)
            if property_schema is not None:
                _validate_json_schema(
                    item,
                    _expect_object(property_schema, "review schema property"),
                    root_schema,
                    f"{label}.{key}",
                    depth + 1,
                )
            elif additional is False:
                raise CatalogImportError(
                    f"{label} contains unknown field {_safe_error_field(key)}"
                )
            elif isinstance(additional, dict):
                _validate_json_schema(
                    item,
                    additional,
                    root_schema,
                    f"{label}.{_safe_error_field(key)}",
                    depth + 1,
                )
            elif additional is not True:
                raise CatalogImportError("review schema additionalProperties is invalid")


def _matches_json_schema_type(value: Any, type_name: str) -> bool:
    return {
        "null": value is None,
        "boolean": isinstance(value, bool),
        "integer": isinstance(value, int) and not isinstance(value, bool),
        "number": isinstance(value, (int, float)) and not isinstance(value, bool),
        "string": isinstance(value, str),
        "array": isinstance(value, list),
        "object": isinstance(value, dict),
    }.get(type_name, False)


def _safe_error_field(value: Any) -> str:
    if isinstance(value, str) and len(value) <= 80 and re.fullmatch(r"[A-Za-z0-9_.-]+", value):
        return value
    return "<invalid-field>"


def _normalize_reviewed_metadata(
    raw_value: Any,
    exercise_id: str,
    catalog_exercise: dict[str, Any],
) -> dict[str, Any]:
    raw = _expect_object(raw_value, f"reviewed metadata.{exercise_id}")
    _reject_forbidden_reviewed_numeric_fields(raw, exercise_id)
    review_state = _strict_review_text(raw["reviewState"], f"{exercise_id}.reviewState", 32)
    direct_primary = _strict_review_text(
        raw["directPrimaryMuscle"],
        f"{exercise_id}.directPrimaryMuscle",
        MAX_STRING_LENGTH,
    )
    descriptive_secondary = [
        _strict_review_text(value, f"{exercise_id}.descriptiveSecondaryMuscles", MAX_STRING_LENGTH)
        for value in raw["descriptiveSecondaryMuscles"]
    ]
    if direct_primary in descriptive_secondary:
        raise CatalogImportError(
            f"{exercise_id}.descriptiveSecondaryMuscles duplicates directPrimaryMuscle"
        )
    represented_muscles = _canonical_catalog_muscles(catalog_exercise)
    if direct_primary not in represented_muscles:
        raise CatalogImportError(
            f"{exercise_id}.directPrimaryMuscle is not represented by the catalog exercise"
        )

    prescription_shape = _strict_review_text(
        raw["prescriptionShape"],
        f"{exercise_id}.prescriptionShape",
        64,
    )
    if prescription_shape != catalog_exercise["exerciseType"]:
        raise CatalogImportError(
            f"{exercise_id}.prescriptionShape does not match exerciseType"
        )
    catalog_muscles = set(catalog_exercise["primaryMuscles"] + catalog_exercise["secondaryMuscles"])
    if catalog_exercise["isStretch"]:
        raise CatalogImportError(f"{exercise_id} is a stretch and cannot enter the reviewed cohort")
    if prescription_shape == "distance_duration":
        raise CatalogImportError(
            f"{exercise_id}.prescriptionShape cannot be distance_duration in the reviewed cohort"
        )
    if prescription_shape == "duration" and "Cardio" in catalog_muscles:
        raise CatalogImportError(
            f"{exercise_id} is cardio duration work and cannot enter the reviewed cohort"
        )

    provenance = _expect_object(raw["provenance"], f"{exercise_id}.provenance")
    reviewer_role = provenance["reviewerRole"]
    reviewed_at = provenance["reviewedAtEpochMillis"]
    if reviewer_role is not None:
        _strict_review_text(reviewer_role, f"{exercise_id}.provenance.reviewerRole", 120)
    _strict_review_text(
        provenance["rationaleOrSource"],
        f"{exercise_id}.provenance.rationaleOrSource",
        1_000,
    )
    if review_state == "approved" and (reviewer_role is None or reviewed_at is None):
        raise CatalogImportError(
            f"{exercise_id}.provenance requires reviewerRole and reviewedAtEpochMillis when approved"
        )

    normalized = json.loads(json.dumps(raw, ensure_ascii=False, allow_nan=False))
    for field in ("approvedRegressions", "approvedSubstitutions"):
        seen_targets: set[str] = set()
        for index, link_value in enumerate(normalized[field]):
            link = _expect_object(link_value, f"{exercise_id}.{field}[{index}]")
            target_id = _strict_review_text(
                link["exerciseId"],
                f"{exercise_id}.{field}[{index}].exerciseId",
                128,
            )
            if target_id in seen_targets:
                raise CatalogImportError(f"{exercise_id}.{field} contains duplicate edge {target_id}")
            seen_targets.add(target_id)
            rationale = link.get("rationale")
            if rationale is not None:
                _strict_review_text(
                    rationale,
                    f"{exercise_id}.{field}[{index}].rationale",
                    500,
                )
    return normalized


def _strict_review_text(value: Any, label: str, maximum: int) -> str:
    checked = _bounded_string(value, label, maximum=maximum)
    if any(ord(character) < 32 or ord(character) == 127 for character in checked):
        raise CatalogImportError(f"{label} must not contain control characters")
    return checked


def _canonical_catalog_muscles(catalog_exercise: dict[str, Any]) -> set[str]:
    aliases = {
        "quads": ["Quadriceps"],
        "groin": ["Adductors"],
        "grip": ["Forearms"],
        "abs": ["Core"],
        "abdominals": ["Core"],
        "glutes/hamstrings": ["Glutes", "Hamstrings"],
        "legs": ["Quadriceps", "Hamstrings", "Glutes"],
        "posterior chain": ["Glutes", "Hamstrings", "Lower Back"],
        "full body": ["Chest", "Back", "Quadriceps", "Core"],
    }
    canonical = {
        "Chest", "Shoulders", "Rear Delts", "Triceps", "Back", "Upper Back",
        "Lower Back", "Lats", "Biceps", "Forearms", "Quadriceps", "Hamstrings",
        "Glutes", "Adductors", "Calves", "Hips", "Core", "Cardio", "Mobility",
    }
    result: set[str] = set()
    for raw_name in catalog_exercise["primaryMuscles"] + catalog_exercise["secondaryMuscles"]:
        lowered = raw_name.strip().lower()
        if lowered in aliases:
            result.update(aliases[lowered])
        else:
            result.update(item for item in canonical if item.lower() == lowered)
    return result


def _reject_forbidden_reviewed_numeric_fields(value: Any, exercise_id: str) -> None:
    forbidden_fragments = {
        "jointstress", "injuryrisk", "stimulustofatigue", "sfr", "axialload",
        "fatigue", "bodymassfraction", "supportedmass", "bmi", "romsuperiority",
        "rangeofmotionsuperiority", "secondarymusclecredit",
    }
    if isinstance(value, dict):
        for key, item in value.items():
            normalized_key = re.sub(r"[^a-z0-9]", "", key.lower())
            if isinstance(item, (int, float)) and not isinstance(item, bool):
                if any(fragment in normalized_key for fragment in forbidden_fragments):
                    raise CatalogImportError(
                        f"{exercise_id} reviewed metadata contains forbidden numeric field "
                        f"{_safe_error_field(key)}"
                    )
            _reject_forbidden_reviewed_numeric_fields(item, exercise_id)
    elif isinstance(value, list):
        for item in value:
            _reject_forbidden_reviewed_numeric_fields(item, exercise_id)


def _validate_reviewed_graphs(
    reviewed_by_id: dict[str, dict[str, Any]],
    catalog_by_id: dict[str, dict[str, Any]],
) -> None:
    complexity_order = {"foundational": 0, "standard": 1, "advanced": 2}
    support_order = {"supported": 0, "optional_support": 1, "unsupported": 2}
    regression_graph: dict[str, list[str]] = {}

    for exercise_id in sorted(reviewed_by_id):
        source = reviewed_by_id[exercise_id]
        regression_targets = [link["exerciseId"] for link in source["approvedRegressions"]]
        substitution_targets = [link["exerciseId"] for link in source["approvedSubstitutions"]]
        duplicate_roles = sorted(set(regression_targets) & set(substitution_targets))
        if duplicate_roles:
            raise CatalogImportError(
                f"{exercise_id} repeats graph edge {duplicate_roles[0]} as regression and substitution"
            )
        regression_graph[exercise_id] = regression_targets

        for field, links in (
            ("approvedRegressions", source["approvedRegressions"]),
            ("approvedSubstitutions", source["approvedSubstitutions"]),
        ):
            for link in links:
                target_id = link["exerciseId"]
                if target_id == exercise_id:
                    raise CatalogImportError(f"{exercise_id}.{field} cannot contain a self-edge")
                if target_id not in catalog_by_id:
                    raise CatalogImportError(
                        f"{exercise_id}.{field} references unknown exercise ID {target_id}"
                    )
                if target_id not in reviewed_by_id:
                    raise CatalogImportError(
                        f"{exercise_id}.{field} target {target_id} lacks reviewed metadata"
                    )

        for link in source["approvedRegressions"]:
            target_id = link["exerciseId"]
            target = reviewed_by_id[target_id]
            if source["movementPattern"] != target["movementPattern"]:
                raise CatalogImportError(
                    f"{exercise_id}.approvedRegressions target {target_id} has incompatible movementPattern"
                )
            if not _regression_shapes_compatible(
                source["prescriptionShape"], target["prescriptionShape"]
            ):
                raise CatalogImportError(
                    f"{exercise_id}.approvedRegressions target {target_id} has incompatible prescriptionShape"
                )
            if source["directPrimaryMuscle"] != target["directPrimaryMuscle"]:
                raise CatalogImportError(
                    f"{exercise_id}.approvedRegressions target {target_id} changes directPrimaryMuscle"
                )
            if complexity_order[target["complexity"]] > complexity_order[source["complexity"]]:
                raise CatalogImportError(
                    f"{exercise_id}.approvedRegressions target {target_id} is more complex"
                )
            if support_order[target["supportRequirement"]] > support_order[source["supportRequirement"]]:
                raise CatalogImportError(
                    f"{exercise_id}.approvedRegressions target {target_id} requires less support"
                )
            if not set(target["capabilityRequirements"]).issubset(source["capabilityRequirements"]):
                raise CatalogImportError(
                    f"{exercise_id}.approvedRegressions target {target_id} adds capability requirements"
                )
            if source["progressionFamily"] != target["progressionFamily"] and not link.get("rationale"):
                raise CatalogImportError(
                    f"{exercise_id}.approvedRegressions target {target_id} crosses progressionFamily "
                    "without rationale"
                )

        for link in source["approvedSubstitutions"]:
            target_id = link["exerciseId"]
            target = reviewed_by_id[target_id]
            if source["prescriptionShape"] != target["prescriptionShape"]:
                raise CatalogImportError(
                    f"{exercise_id}.approvedSubstitutions target {target_id} has incompatible "
                    "prescriptionShape"
                )
            changes_role = (
                source["movementPattern"] != target["movementPattern"]
                or source["directPrimaryMuscle"] != target["directPrimaryMuscle"]
            )
            if changes_role and not link.get("rationale"):
                raise CatalogImportError(
                    f"{exercise_id}.approvedSubstitutions target {target_id} changes movement role "
                    "without rationale"
                )
            if not target["equipmentAlternatives"]:
                raise CatalogImportError(
                    f"{exercise_id}.approvedSubstitutions target {target_id} has no satisfiable "
                    "equipment alternative"
                )

    visiting: set[str] = set()
    visited: set[str] = set()

    def visit(exercise_id: str, path: list[str]) -> None:
        if exercise_id in visiting:
            cycle_start = path.index(exercise_id)
            cycle = path[cycle_start:] + [exercise_id]
            raise CatalogImportError(f"Regression cycle detected: {' -> '.join(cycle)}")
        if exercise_id in visited:
            return
        visiting.add(exercise_id)
        for target_id in regression_graph.get(exercise_id, []):
            visit(target_id, [*path, exercise_id])
        visiting.remove(exercise_id)
        visited.add(exercise_id)

    for exercise_id in sorted(regression_graph):
        visit(exercise_id, [])


def _regression_shapes_compatible(source_shape: str, target_shape: str) -> bool:
    return source_shape == target_shape or (
        source_shape == "bodyweight_reps" and target_shape == "assisted_bodyweight"
    )


def _render_review_report(reviewed_by_id: dict[str, dict[str, Any]]) -> str:
    review_states = Counter(value["reviewState"] for value in reviewed_by_id.values())
    review_states.setdefault("approved", 0)
    review_states.setdefault("draft", 0)
    movement_patterns = Counter(value["movementPattern"] for value in reviewed_by_id.values())
    progression_families = Counter(value["progressionFamily"] for value in reviewed_by_id.values())
    capability_requirements: Counter[str] = Counter()
    equipment_families: Counter[str] = Counter()
    for value in reviewed_by_id.values():
        capabilities = value["capabilityRequirements"] or ["none"]
        capability_requirements.update(capabilities)
        equipment_families.update(
            sorted({equipment for combination in value["equipmentAlternatives"] for equipment in combination})
        )

    missing_regressions = sorted(
        exercise_id
        for exercise_id, value in reviewed_by_id.items()
        if _reviewed_requires_regression(value) and not value["approvedRegressions"]
    )
    drafts = sorted(
        exercise_id for exercise_id, value in reviewed_by_id.items()
        if value["reviewState"] == "draft"
    )
    lines = [
        "# Reviewed Exercise Metadata Report",
        "",
        "This file is generated by `tools/workout-guide/import_catalog.py`. Do not edit it by hand.",
        "",
        f"- Cohort count: {len(reviewed_by_id)}",
        "",
    ]
    for title, counts in (
        ("Review state", review_states),
        ("Equipment family", equipment_families),
        ("Movement pattern", movement_patterns),
        ("Progression family", progression_families),
        ("Capability requirement", capability_requirements),
    ):
        lines.extend([f"## Count by {title.lower()}", "", "| Value | Count |", "| --- | ---: |"])
        lines.extend(f"| `{key}` | {counts[key]} |" for key in sorted(counts))
        lines.append("")

    lines.extend(["## Entries lacking a required regression", ""])
    lines.extend(f"- `{exercise_id}`" for exercise_id in missing_regressions)
    if not missing_regressions:
        lines.append("- None")
    lines.extend(
        [
            "",
            "## DRAFT entries awaiting human approval",
            "",
            "For every entry below, a human reviewer must inspect direct/secondary muscles, movement "
            "pattern, complexity, progression family, prescription shape, directed regression and "
            "substitution edges (including exception rationales), capabilities, support, impact, "
            "equipment alternatives, and provenance before deliberately changing `reviewState`.",
            "",
        ]
    )
    lines.extend(f"- `{exercise_id}`" for exercise_id in drafts)
    if not drafts:
        lines.append("- None")
    return "\n".join(lines) + "\n"


def _reviewed_requires_regression(value: dict[str, Any]) -> bool:
    return value["complexity"] == "advanced" or (
        value["complexity"] == "standard"
        and value["supportRequirement"] == "unsupported"
        and bool(value["capabilityRequirements"])
    )


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
    _required_string(attribution, "creatorUrl", f"{label}.{key}", maximum=MAX_URL_LENGTH)
    _required_string(attribution, "license", f"{label}.{key}")
    _required_string(attribution, "licenseUrl", f"{label}.{key}", maximum=MAX_URL_LENGTH)
    source = attribution.get("source")
    if source is not None:
        source_object = _expect_object(source, f"{label}.{key}.source")
        _required_string(source_object, "name", f"{label}.{key}.source")
        _required_string(source_object, "url", f"{label}.{key}.source", maximum=MAX_URL_LENGTH)
        _required_string(source_object, "license", f"{label}.{key}.source")
        _required_string(
            source_object,
            "licenseUrl",
            f"{label}.{key}.source",
            maximum=MAX_URL_LENGTH,
        )
        _required_string(
            source_object,
            "changes",
            f"{label}.{key}.source",
            maximum=MAX_DESCRIPTION_LENGTH,
        )
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
        return _bounded_string(value, label, maximum=MAX_RAW_JSON_STRING_LENGTH)
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


def _read_json(path: Path, label: str, maximum_bytes: int = 50_000_000) -> Any:
    try:
        if path.stat().st_size > maximum_bytes:
            raise CatalogImportError(f"{label} exceeds the {maximum_bytes}-byte input limit")
        return json.loads(
            path.read_text(encoding="utf-8"),
            parse_constant=lambda _value: _reject_non_finite_number(label),
            object_pairs_hook=lambda pairs: _reject_duplicate_json_fields(pairs, label),
        )
    except FileNotFoundError as error:
        raise CatalogImportError(f"Missing {label}: {path}") from error
    except UnicodeDecodeError as error:
        raise CatalogImportError(f"{label} is not UTF-8: {path}") from error
    except json.JSONDecodeError as error:
        raise CatalogImportError(f"Malformed JSON in {label}: {error}") from error


def _reject_non_finite_number(label: str) -> None:
    raise CatalogImportError(f"{label} contains a non-finite number")


def _reject_duplicate_json_fields(
    pairs: list[tuple[str, Any]],
    label: str,
) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise CatalogImportError(
                f"{label} contains duplicate JSON field {_safe_error_field(key)}"
            )
        result[key] = value
    return result


def _write_json(path: Path, value: Any) -> None:
    path.write_text(
        json.dumps(
            value,
            ensure_ascii=False,
            separators=(",", ":"),
            sort_keys=True,
        ) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def _atomic_write_text(path: Path, value: str) -> None:
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    temporary_path = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
            stream.write(value)
        os.replace(temporary_path, path)
    except BaseException:
        temporary_path.unlink(missing_ok=True)
        raise


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


def _required_string(
    container: dict[str, Any],
    key: str,
    label: str,
    maximum: int = MAX_STRING_LENGTH,
) -> str:
    if key not in container:
        raise CatalogImportError(f"Missing {label}.{key}")
    return _bounded_string(container[key], f"{label}.{key}", maximum=maximum)


def _bounded_string(
    value: Any,
    label: str,
    maximum: int = MAX_STRING_LENGTH,
) -> str:
    if not isinstance(value, str):
        raise CatalogImportError(f"{label} must be a string")
    if not value.strip():
        raise CatalogImportError(f"{label} must not be blank")
    if value != value.strip():
        raise CatalogImportError(f"{label} must not have surrounding whitespace")
    if len(value) > maximum:
        raise CatalogImportError(f"{label} exceeds {maximum} characters")
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
    parser.add_argument(
        "--reviewed-metadata",
        type=Path,
        default=tool_root / "reviewed-metadata.json",
    )
    parser.add_argument(
        "--review-schema",
        type=Path,
        default=tool_root / "review-schema.json",
    )
    parser.add_argument(
        "--review-report",
        type=Path,
        default=repository_root / "docs/reviewed-exercise-metadata-review.md",
    )
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
            reviewed_metadata_path=arguments.reviewed_metadata,
            review_schema_path=arguments.review_schema,
            review_report_path=arguments.review_report,
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

#!/usr/bin/env python3
"""Validate the authored catalog, canonical art and the existing translation overlay."""
from __future__ import annotations

from pathlib import Path
import sys

from import_catalog import (
    CatalogImportError, SUPPORTED_EXERCISE_TYPES, _bounded_json_value, _bounded_string, _canonical_catalog_muscles,
    _expect_object, _normalize_programming, _normalize_reviewed_metadata, _read_object,
    _required_array, _required_attribution, _required_bool, _required_int,
    _required_object, _required_string, _required_string_list, _require_schema_version,
    _validate_json_schema, _validate_reviewed_graphs, _validate_safe_id,
)

TOOL_ROOT = Path(__file__).resolve().parent
ASSET_PREFIX = "exercise-illustrations/male/"


def validate_supplement(catalog: dict, upstream: dict, overlay: dict, assets: Path) -> tuple[int, int]:
    """Return local exercise/frame counts; never writes or changes imported content."""
    _bounded_json_value(catalog, "supplement", 0)
    _require_schema_version(catalog, "supplement")
    source = _required_object(catalog, "source", "supplement")
    if source.get("id") != "wallcrawl" or "commit" in source:
        raise CatalogImportError("Supplement must identify wallcrawl, without an upstream commit")
    _required_int(source, "version", "source", 1, 1)
    _required_string(source, "assetLicense", "source")
    attribution = _required_attribution(source, "attribution", "source")
    for url in [source.get("repository"), attribution["creatorUrl"], attribution["licenseUrl"]]:
        if not isinstance(url, str) or not url.startswith("https://"):
            raise CatalogImportError("Supplement source and attribution URLs must use HTTPS")
    visuals = _required_object(catalog, "visuals", "supplement")
    if visuals.get("assetPathPrefix") != ASSET_PREFIX or _required_string(visuals, "format", "visuals").lower() != "svg":
        raise CatalogImportError("Supplement visuals must use canonical male SVG paths")
    _required_int(visuals, "frameCount", "visuals", 3, 3)
    for dimension in ["widthPx", "heightPx"]:
        _required_int(visuals, dimension, "visuals", 1, 8192)
    entries = _required_array(catalog, "exercises", "supplement")
    if len(entries) > 5000:
        raise CatalogImportError("Supplement exceeds 5000 exercises")
    upstream_entries = upstream["exercises"]
    seen = {field: {entry[field] for entry in upstream_entries} for field in ["id", "sourceId", "sourceSlug"]}
    rep_schema = _read_object(TOOL_ROOT / "programming-rep-range-schema.json", "rep schema")
    review_schema = _read_object(TOOL_ROOT / "review-schema.json", "review schema")
    equipment = review_schema["$defs"]["equipment"]["enum"]
    for entry in entries:
        _required_string(_expect_object(entry, "exercise"), "id", "exercise")
    all_entries = {entry["id"]: entry for entry in upstream_entries + entries}
    reviewed = {entry["id"]: entry["reviewedMetadata"] for entry in upstream_entries if "reviewedMetadata" in entry}
    for entry in entries:
        _expect_object(entry, "exercise")
        exercise_id = _required_string(entry, "id", "exercise")
        for field in seen:
            value = _required_string(entry, field, exercise_id)
            _validate_safe_id(value, field)
            if value in seen[field]:
                raise CatalogImportError("Supplement repeats an exercise id or source reference")
            seen[field].add(value)
            if value != exercise_id:
                raise CatalogImportError("Local sourceId and sourceSlug must match id")
        _required_string(entry, "name", exercise_id)
        _required_string_list(entry, "searchAliases", exercise_id, 100)
        primary = _required_string_list(entry, "primaryMuscles", exercise_id, 100)
        secondary = _required_string_list(entry, "secondaryMuscles", exercise_id, 100)
        listed = _required_string_list(entry, "listedEquipment", exercise_id, 100)
        if not primary or any(not _canonical_catalog_muscles({"primaryMuscles": [name], "secondaryMuscles": []}) for name in primary + secondary) or any(name not in equipment for name in listed):
            raise CatalogImportError("Supplement contains missing or unknown canonical vocabulary")
        exercise_type = _required_string(entry, "exerciseType", exercise_id)
        if exercise_type not in SUPPORTED_EXERCISE_TYPES:
            raise CatalogImportError("Supplement contains unknown exercise type")
        _required_bool(entry, "isStretch", exercise_id)
        _required_string(entry, "executionGuidance", exercise_id, maximum=1000)
        if "programming" in entry:
            programming = _normalize_programming(entry["programming"], exercise_id, exercise_type, rep_schema)
            if any(target not in all_entries for target in programming["alternativeExerciseIds"]):
                raise CatalogImportError("Supplement programming references unknown exercise")
        if "reviewedMetadata" in entry:
            metadata = _required_object(entry, "reviewedMetadata", exercise_id)
            provenance = _required_object(metadata, "provenance", exercise_id)
            version = _required_int(provenance, "schemaVersion", "provenance", 2, 3)
            document = {"schemaVersion": version, "exercises": {exercise_id: metadata}}
            _validate_json_schema(document, review_schema, review_schema, "reviewedMetadata", 0)
            reviewed[exercise_id] = _normalize_reviewed_metadata(metadata, exercise_id, entry, version)
        for index in range(1, 4):
            path = assets / ASSET_PREFIX / exercise_id / f"frame-{index}.svg"
            if any(parent.is_symlink() for parent in [path, *path.parents] if parent == assets or assets in parent.parents) or not path.is_file() or path.stat().st_size == 0:
                raise CatalogImportError(f"Missing, empty or symlink canonical art for {exercise_id}")
    _validate_reviewed_graphs(reviewed, all_entries)
    # Coverage belongs to the merged catalog; the upstream pin remains independently checked.
    expected_ids = set(all_entries)
    if set(overlay["exercises"]) != expected_ids:
        raise CatalogImportError("Localization exercise ids must match the merged catalog")
    for entry in all_entries.values():
        for language in overlay["languages"]:
            text = overlay["exercises"][entry["id"]].get(language)
            if not isinstance(text, dict):
                raise CatalogImportError("Missing declared-language exercise localization")
            _required_string(text, "name", "localization", maximum=160)
            aliases = text.get("aliases", [])
            _required_string_list({"aliases": aliases}, "aliases", "localization", 12)
            for alias in aliases:
                _bounded_string(alias, "localized alias", maximum=160)
            if entry.get("executionGuidance") or entry.get("programming", {}).get("coachingSummary"):
                _required_string(text, "coachingSummary", "localization", maximum=1000)
        for category, words in [("muscles", entry["primaryMuscles"] + entry["secondaryMuscles"]), ("equipment", entry["listedEquipment"])]:
            for word in words:
                # Upstream aliases are normalized by its pinned importer/runtime parser.
                if word not in overlay["vocabulary"][category] and entry in entries:
                    raise CatalogImportError("Missing local vocabulary translation")
                for language in overlay["languages"]:
                    if word in overlay["vocabulary"][category]:
                        _required_string(overlay["vocabulary"][category][word], language, "vocabulary", maximum=120)
    return len(entries), len(entries) * 3


def main() -> int:
    assets = TOOL_ROOT.parents[1] / "app/src/main/assets"
    try:
        count, frames = validate_supplement(
            _read_object(assets / "wallcrawl/catalog.json", "supplement"),
            _read_object(assets / "workout-guide/catalog.json", "upstream"),
            _read_object(assets / "localization/exercise-localization.json", "localization"), assets,
        )
        print(f"Validated WallCrawl supplement: {count} exercises / {frames} canonical frames; merged localization covered")
        return 0
    except (CatalogImportError, OSError) as error:
        print(f"error: supplement: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

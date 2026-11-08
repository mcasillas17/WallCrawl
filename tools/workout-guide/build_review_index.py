#!/usr/bin/env python3
"""Build bounded, lossless VIEW-friendly artifacts for the full catalog review."""

import argparse
from collections import Counter
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile
from typing import Any

from import_catalog import (
    CatalogImportError,
    _reject_duplicate_json_fields,
    _reject_non_finite_number,
)


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_LEDGER = ROOT / "docs/research/2026-09-07-full-exercise-catalog-review.json"
DEFAULT_METADATA = Path(__file__).with_name("reviewed-metadata.json")
DEFAULT_BUNDLE = ROOT / "app/src/main/assets/workout-guide"
DEFAULT_OUTPUT = ROOT / "build/review-index"
DEFAULT_MAX_BYTES = 16 * 1024
_SAFE_ID = re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*\Z")
_REFERENCE_KEY = "$exactString"
_INTERN_MIN_BYTES = 120
_INTERN_MIN_OCCURRENCES = 5


def _compact_digest(value: Any) -> str:
    encoded = json.dumps(
        value, ensure_ascii=False, separators=(",", ":"), sort_keys=True
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def _json_pointer_part(value: str) -> str:
    return value.replace("~", "~0").replace("/", "~1")


class _JsonLocations:
    def __init__(self, text: str):
        self.text = text
        self.spans: dict[str, tuple[int, int]] = {}
        end = self._walk(0, "")
        if text[end:].strip():
            raise ValueError("unexpected content after JSON value")

    def _space(self, position: int) -> int:
        while position < len(self.text) and self.text[position] in " \t\r\n":
            position += 1
        return position

    def _walk(self, position: int, pointer: str) -> int:
        position = self._space(position)
        start = position
        if position >= len(self.text):
            raise ValueError("unexpected end of JSON")
        token = self.text[position]
        if token == "{":
            position = self._space(position + 1)
            if position < len(self.text) and self.text[position] == "}":
                position += 1
            else:
                while True:
                    key, key_end = json.JSONDecoder().raw_decode(self.text, position)
                    if not isinstance(key, str):
                        raise ValueError("JSON object key is not a string")
                    position = self._space(key_end)
                    if position >= len(self.text) or self.text[position] != ":":
                        raise ValueError("missing JSON object colon")
                    child = f"{pointer}/{_json_pointer_part(key)}"
                    position = self._walk(position + 1, child)
                    position = self._space(position)
                    if position < len(self.text) and self.text[position] == ",":
                        position = self._space(position + 1)
                        continue
                    if position >= len(self.text) or self.text[position] != "}":
                        raise ValueError("missing JSON object terminator")
                    position += 1
                    break
        elif token == "[":
            position = self._space(position + 1)
            index = 0
            if position < len(self.text) and self.text[position] == "]":
                position += 1
            else:
                while True:
                    position = self._walk(position, f"{pointer}/{index}")
                    index += 1
                    position = self._space(position)
                    if position < len(self.text) and self.text[position] == ",":
                        position = self._space(position + 1)
                        continue
                    if position >= len(self.text) or self.text[position] != "]":
                        raise ValueError("missing JSON array terminator")
                    position += 1
                    break
        else:
            _, position = json.JSONDecoder().raw_decode(self.text, position)
        self.spans[pointer] = (start, position)
        return position

    def lines(self, pointer: str, expected: Any) -> tuple[int, int]:
        if pointer not in self.spans:
            raise ValueError(f"missing canonical JSON pointer {pointer or '/'}")
        start, end = self.spans[pointer]
        if json.loads(self.text[start:end]) != expected:
            raise ValueError(f"canonical JSON pointer {pointer or '/'} resolves incorrectly")
        first = self.text.count("\n", 0, start) + 1
        last = self.text.count("\n", 0, max(start, end - 1)) + 1
        return first, last


def _load(path: Path, label: str) -> tuple[Any, str, bytes, _JsonLocations]:
    path = Path(path)
    if not path.is_file():
        raise ValueError(f"missing {label}: {path}")
    try:
        if path.stat().st_size > 50_000_000:
            raise ValueError(f"{label} exceeds the 50000000-byte input limit")
        raw = path.read_bytes()
        if len(raw) > 50_000_000:
            raise ValueError(f"{label} exceeds the 50000000-byte input limit")
        text = raw.decode("utf-8")
        value = json.loads(
            text,
            parse_constant=lambda _value: _reject_non_finite_number(label),
            object_pairs_hook=lambda pairs: _reject_duplicate_json_fields(pairs, label),
        )
        return value, text, raw, _JsonLocations(text)
    except (CatalogImportError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError(str(error)) from error


def _source_path(path: Path, repository_root: Path) -> str:
    try:
        return path.resolve().relative_to(repository_root.resolve()).as_posix()
    except ValueError as error:
        raise ValueError(f"source is outside repository root: {path}") from error


def _split_text(value: str, maximum_bytes: int) -> list[str]:
    if not value:
        return [""]
    parts: list[str] = []
    current: list[str] = []
    current_bytes = 0
    for character in value:
        size = len(character.encode("utf-8"))
        if size > maximum_bytes:
            raise ValueError("artifact limit is smaller than one UTF-8 character")
        if current and current_bytes + size > maximum_bytes:
            parts.append("".join(current))
            current = []
            current_bytes = 0
        current.append(character)
        current_bytes += size
    if current:
        parts.append("".join(current))
    return parts


def _pretty(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def _repeated_strings(*values: Any) -> list[str]:
    counts: Counter[str] = Counter()

    def visit(value: Any) -> None:
        if isinstance(value, str):
            counts[value] += 1
        elif isinstance(value, dict):
            if set(value) == {_REFERENCE_KEY}:
                raise ValueError(f"canonical data uses reserved review-index key {_REFERENCE_KEY}")
            for child in value.values():
                visit(child)
        elif isinstance(value, list):
            for child in value:
                visit(child)

    for value in values:
        visit(value)
    selected = [
        value for value, count in counts.items()
        if count >= _INTERN_MIN_OCCURRENCES
        and len(value.encode("utf-8")) >= _INTERN_MIN_BYTES
    ]
    return sorted(selected, key=lambda value: (hashlib.sha256(value.encode()).hexdigest(), value))


def _intern_exact_strings(value: Any, references: dict[str, str]) -> Any:
    if isinstance(value, str) and value in references:
        return {_REFERENCE_KEY: references[value]}
    if isinstance(value, dict):
        return {key: _intern_exact_strings(child, references) for key, child in value.items()}
    if isinstance(value, list):
        return [_intern_exact_strings(child, references) for child in value]
    return value


def resolve_exact_references(value: Any, artifacts: dict[str, str]) -> Any:
    """Resolve the aid's explicit repeated-string references to canonical JSON values."""
    if isinstance(value, dict) and set(value) == {_REFERENCE_KEY}:
        index_path = value[_REFERENCE_KEY]
        if not isinstance(index_path, str) or not index_path.startswith("values/"):
            raise ValueError("invalid exact-string reference")
        directory = index_path.removesuffix("/INDEX.md")
        if index_path not in artifacts or directory == index_path:
            raise ValueError(f"missing exact-string index: {index_path}")
        part_prefix = f"{directory}/part-"
        part_paths = sorted(
            path for path in artifacts
            if path.startswith(part_prefix) and path.endswith(".txt")
        )
        if not part_paths:
            raise ValueError(f"missing exact-string parts: {index_path}")
        result = "".join(artifacts[path] for path in part_paths)
        expected = directory.rsplit("/", 1)[-1]
        if hashlib.sha256(result.encode("utf-8")).hexdigest() != expected:
            raise ValueError(f"stale exact-string value: {index_path}")
        return result
    if isinstance(value, dict):
        return {
            key: resolve_exact_references(child, artifacts)
            for key, child in value.items()
        }
    if isinstance(value, list):
        return [resolve_exact_references(child, artifacts) for child in value]
    return value


def _add_parts(
    artifacts: dict[str, str], prefix: str, value: str, maximum_bytes: int
) -> list[str]:
    parts = _split_text(value, maximum_bytes)
    width = max(3, len(str(len(parts))))
    paths = []
    for index, part in enumerate(parts, 1):
        path = f"{prefix}-{index:0{width}d}.txt"
        artifacts[path] = part
        paths.append(path)
    return paths


def _paginate(
    artifacts: dict[str, str],
    prefix: str,
    title: str,
    lines: list[str],
    maximum_bytes: int,
) -> list[str]:
    allowance = maximum_bytes - 256
    if allowance < 128:
        raise ValueError("artifact byte limit must be at least 512")
    pages: list[list[str]] = [[]]
    used = 0
    for line in lines or ["(none)"]:
        encoded = len((line + "\n").encode("utf-8"))
        if encoded > allowance:
            raise ValueError(f"navigation line exceeds artifact limit: {line[:80]}")
        if pages[-1] and used + encoded > allowance:
            pages.append([])
            used = 0
        pages[-1].append(line)
        used += encoded
    paths = []
    for number, page in enumerate(pages, 1):
        path = f"{prefix}-{number:03d}.md"
        artifacts[path] = (
            f"# {title} (page {number}/{len(pages)})\n\n"
            + "\n".join(page)
            + "\n"
        )
        paths.append(path)
    return paths


def _reference(
    path: Path,
    repository_root: Path,
    pointer: str,
    value: Any,
    locations: _JsonLocations,
) -> str:
    first, last = locations.lines(pointer, value)
    line_text = str(first) if first == last else f"{first}-{last}"
    return (
        f"`{_source_path(path, repository_root)}:{line_text}` "
        f"JSON pointer `{pointer or '/'}`"
    )


def _require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ValueError(f"{label} must be a JSON object")
    return value


def _require_array(value: Any, label: str) -> list[Any]:
    if not isinstance(value, list):
        raise ValueError(f"{label} must be a JSON array")
    return value


def _unique_records(records: list[Any], label: str) -> tuple[list[dict[str, Any]], dict[str, dict]]:
    objects = [_require_object(value, f"{label} record") for value in records]
    by_id: dict[str, dict] = {}
    for value in objects:
        exercise_id = value.get("id")
        if not isinstance(exercise_id, str) or not _SAFE_ID.fullmatch(exercise_id):
            raise ValueError(f"{label} has unsafe or missing ID: {exercise_id!r}")
        if exercise_id in by_id:
            raise ValueError(f"duplicate {label} ID: {exercise_id}")
        by_id[exercise_id] = value
    return objects, by_id


def _validate_and_edges(
    ledger_entries: list[dict[str, Any]],
    metadata: dict[str, Any],
    catalog_entries: list[dict[str, Any]],
    upstream_entries: list[dict[str, Any]],
    upstream_raw: bytes,
    ledger: dict[str, Any],
) -> tuple[list[dict[str, Any]], dict[str, list[str]], dict[str, list[str]]]:
    _, ledger_by_id = _unique_records(ledger_entries, "ledger")
    _, catalog_by_id = _unique_records(catalog_entries, "catalog")
    _, upstream_by_id = _unique_records(upstream_entries, "upstream manifest")
    if set(ledger_by_id) != set(catalog_by_id):
        raise ValueError("ledger and catalog exercise ID coverage differs")
    if not all(isinstance(key, str) and _SAFE_ID.fullmatch(key) for key in metadata):
        raise ValueError("reviewed metadata has an unsafe or non-string exercise ID")
    unknown_metadata = set(metadata) - set(ledger_by_id)
    if unknown_metadata:
        raise ValueError(f"reviewed metadata has unknown IDs: {sorted(unknown_metadata)}")
    source_ids = [value.get("sourceId") for value in catalog_entries]
    if len(source_ids) != len(set(source_ids)) or set(source_ids) != set(upstream_by_id):
        raise ValueError("catalog source IDs and upstream manifest ID coverage differ")
    recorded_manifest = ledger.get("source", {}).get("manifestSha256")
    actual_manifest = hashlib.sha256(upstream_raw).hexdigest()
    if recorded_manifest is not None and recorded_manifest != actual_manifest:
        raise ValueError("ledger source manifest SHA-256 is stale")

    for exercise_id, entry in ledger_by_id.items():
        authored = metadata.get(exercise_id)
        recorded = entry.get("metadataSha256")
        if authored is None:
            if recorded is not None:
                raise ValueError(f"{exercise_id}: missing metadata has a recorded digest")
        elif recorded != _compact_digest(authored):
            raise ValueError(f"{exercise_id}: stale metadata digest")

    edges: list[dict[str, Any]] = []
    outgoing = {exercise_id: [] for exercise_id in ledger_by_id}
    incoming = {exercise_id: [] for exercise_id in ledger_by_id}
    for entry_index, entry in enumerate(ledger_entries):
        exercise_id = entry["id"]
        decisions = _require_array(entry.get("graphDecisions"), f"{exercise_id}.graphDecisions")
        identities: set[tuple[Any, Any]] = set()
        emitted: set[tuple[str, str]] = set()
        for decision_index, raw_decision in enumerate(decisions):
            decision = _require_object(raw_decision, f"{exercise_id} graph decision")
            identity = (decision.get("kind"), decision.get("targetId"))
            if identity in identities:
                raise ValueError(f"{exercise_id}: duplicate graph decision {identity}")
            identities.add(identity)
            action = decision.get("action")
            kind, target_id = identity
            if kind not in {"regression", "substitution"}:
                raise ValueError(f"{exercise_id}: invalid graph decision kind {kind!r}")
            if action not in {"add", "retain", "hold", "reject"}:
                raise ValueError(f"{exercise_id}: invalid graph decision action {action!r}")
            if action not in {"add", "retain"}:
                continue
            if target_id not in ledger_by_id or target_id not in metadata:
                raise ValueError(f"{exercise_id}: emitted edge has missing target {target_id!r}")
            edge_id = f"edge-{len(edges) + 1:04d}"
            edge = {
                "id": edge_id,
                "sourceId": exercise_id,
                "targetId": target_id,
                "kind": kind,
                "action": action,
                "decisionPointer": f"/entries/{entry_index}/graphDecisions/{decision_index}",
            }
            edges.append(edge)
            outgoing[exercise_id].append(edge_id)
            incoming[target_id].append(edge_id)
            emitted.add((kind, target_id))
        authored = metadata.get(exercise_id)
        actual = {
            (kind, link["exerciseId"])
            for kind, field in (
                ("regression", "approvedRegressions"),
                ("substitution", "approvedSubstitutions"),
            )
            for link in (authored.get(field, []) if authored else [])
        }
        if actual != emitted:
            raise ValueError(f"{exercise_id}: metadata links and emitted graph edges differ")
    return edges, outgoing, incoming


def validate_artifacts(artifacts: dict[str, str], maximum_bytes: int) -> None:
    if maximum_bytes < 512:
        raise ValueError("artifact byte limit must be at least 512")
    for path, value in artifacts.items():
        candidate = Path(path)
        if candidate.is_absolute() or ".." in candidate.parts or path.endswith("/"):
            raise ValueError(f"unsafe artifact path: {path}")
        size = len(value.encode("utf-8"))
        if size > maximum_bytes:
            raise ValueError(f"artifact {path} exceeds {maximum_bytes} bytes ({size})")


def build_artifacts(
    *,
    ledger_path: Path,
    metadata_path: Path,
    catalog_path: Path,
    upstream_manifest_path: Path,
    source_paths: list[Path] | tuple[Path, ...] = (),
    repository_root: Path = ROOT,
    max_bytes: int = DEFAULT_MAX_BYTES,
) -> dict[str, str]:
    if max_bytes < 512:
        raise ValueError("artifact byte limit must be at least 512")
    repository_root = Path(repository_root)
    loaded = {
        "ledger": _load(Path(ledger_path), "full review ledger"),
        "metadata": _load(Path(metadata_path), "reviewed metadata"),
        "catalog": _load(Path(catalog_path), "catalog"),
        "manifest": _load(Path(upstream_manifest_path), "upstream manifest"),
    }
    ledger = _require_object(loaded["ledger"][0], "full review ledger")
    metadata_root = _require_object(loaded["metadata"][0], "reviewed metadata")
    catalog = _require_object(loaded["catalog"][0], "catalog")
    ledger_entries = _require_array(ledger.get("entries"), "ledger.entries")
    metadata = _require_object(metadata_root.get("exercises"), "metadata.exercises")
    catalog_entries = _require_array(catalog.get("exercises"), "catalog.exercises")
    upstream_entries = _require_array(loaded["manifest"][0], "upstream manifest")
    ledger_entries, ledger_by_id = _unique_records(ledger_entries, "ledger")
    catalog_entries, catalog_by_id = _unique_records(catalog_entries, "catalog")
    upstream_entries, upstream_by_id = _unique_records(upstream_entries, "upstream manifest")
    edges, outgoing, incoming = _validate_and_edges(
        ledger_entries, metadata, catalog_entries, upstream_entries,
        loaded["manifest"][2], ledger,
    )

    catalog_ordinals = {value["id"]: index for index, value in enumerate(catalog_entries)}
    metadata_ordinals = {exercise_id: index for index, exercise_id in enumerate(metadata)}
    upstream_ordinals = {value["id"]: index for index, value in enumerate(upstream_entries)}
    artifacts: dict[str, str] = {}
    repeated_strings = _repeated_strings(ledger, metadata_root, catalog, upstream_entries)
    exact_references: dict[str, str] = {}
    value_index_lines = []
    for value in repeated_strings:
        digest = hashlib.sha256(value.encode("utf-8")).hexdigest()
        directory = f"values/{digest}"
        index_path = f"{directory}/INDEX.md"
        exact_references[value] = index_path
        parts = _add_parts(artifacts, f"{directory}/part", value, max_bytes)
        value_index = (
            "# Exact repeated canonical string\n\n"
            f"- SHA-256: `{digest}`\n"
            f"- UTF-8 bytes: {len(value.encode('utf-8'))}\n"
            "- Concatenate the part files in listed order; substitute the exact result "
            f"for `{_REFERENCE_KEY}` references to this page.\n\n"
            + "\n".join(f"- `{path}`" for path in parts)
            + "\n"
        )
        if len(value_index.encode("utf-8")) > max_bytes:
            part_pages = _paginate(
                artifacts,
                f"{directory}/parts-index",
                f"Exact repeated string {digest}",
                [f"- `{path}`" for path in parts],
                max_bytes,
            )
            value_index = (
                "# Exact repeated canonical string\n\n"
                f"- SHA-256: `{digest}`\n"
                f"- UTF-8 bytes: {len(value.encode('utf-8'))}\n"
                "- Concatenate the parts listed by these bounded indexes in page order; "
                f"substitute the exact result for `{_REFERENCE_KEY}` references.\n\n"
                + "\n".join(f"- `{path}`" for path in part_pages)
                + "\n"
            )
        artifacts[index_path] = value_index
        value_index_lines.append(
            f"- `{digest}` ({len(value.encode('utf-8'))} bytes) → `{index_path}`"
        )
    value_pages = _paginate(
        artifacts, "values/index", "Exact repeated-value index", value_index_lines, max_bytes
    )
    artifacts["values/INDEX.md"] = (
        "# Exact repeated canonical values\n\n"
        f"Strings of at least {_INTERN_MIN_BYTES} UTF-8 bytes occurring at least "
        f"{_INTERN_MIN_OCCURRENCES} times are stored once. Every reference contains the "
        "direct path to its value page; concatenate that page's parts and substitute the "
        "result without interpretation.\n\n"
        + "\n".join(f"- `{path}`" for path in value_pages)
        + "\n"
    )

    global_payload = {
        "ledger": {key: copy.deepcopy(value) for key, value in ledger.items() if key != "entries"},
        "metadata": {
            key: copy.deepcopy(value) for key, value in metadata_root.items() if key != "exercises"
        },
        "catalog": {
            key: copy.deepcopy(value) for key, value in catalog.items() if key != "exercises"
        },
        "upstreamManifest": {"rootType": "array", "length": len(upstream_entries)},
    }
    interned_global = _intern_exact_strings(global_payload, exact_references)
    if resolve_exact_references(interned_global, artifacts) != global_payload:
        raise ValueError("global exact-value reconstruction failed")
    global_parts = _add_parts(
        artifacts, "globals/payload", _pretty(interned_global), max_bytes
    )
    global_index = (
        "# Canonical root/global payload\n\n"
        "Concatenate payload parts in listed order and parse the result as JSON. "
        "The ledger `entries`, metadata/catalog `exercises`, and upstream array records "
        "are reconstructed from the per-exercise payloads.\n\n"
        + "\n".join(f"- `{path}`" for path in global_parts)
        + "\n"
    )
    if len(global_index.encode("utf-8")) > max_bytes:
        global_part_pages = _paginate(
            artifacts,
            "globals/payload-index",
            "Global payload parts",
            [f"- `{path}`" for path in global_parts],
            max_bytes,
        )
        global_index = (
            "# Canonical root/global payload\n\n"
            "Concatenate parts listed by these bounded indexes in page order and parse "
            "the result as JSON. Per-exercise payloads supply record collections.\n\n"
            + "\n".join(f"- `{path}`" for path in global_part_pages)
            + "\n"
        )
    artifacts["globals/INDEX.md"] = global_index

    edge_by_id = {edge["id"]: edge for edge in edges}
    exercise_index_lines = []
    for ledger_ordinal, entry in enumerate(ledger_entries):
        exercise_id = entry["id"]
        catalog_ordinal = catalog_ordinals[exercise_id]
        catalog_entry = catalog_by_id[exercise_id]
        source_id = catalog_entry["sourceId"]
        upstream_ordinal = upstream_ordinals[source_id]
        metadata_value = metadata.get(exercise_id)
        package = {
            "id": exercise_id,
            "ledgerOrdinal": ledger_ordinal,
            "metadataOrdinal": metadata_ordinals.get(exercise_id),
            "catalogOrdinal": catalog_ordinal,
            "upstreamManifestOrdinal": upstream_ordinal,
            "ledgerEntry": entry,
            "reviewedMetadata": metadata_value,
            "catalogExercise": catalog_entry,
            "upstreamManifestEntry": upstream_by_id[source_id],
            "outgoingEdgeIds": outgoing[exercise_id],
            "incomingEdgeIds": incoming[exercise_id],
        }
        prefix = f"exercises/{exercise_id}"
        interned_package = _intern_exact_strings(package, exact_references)
        if resolve_exact_references(interned_package, artifacts) != package:
            raise ValueError(f"{exercise_id}: exact-value reconstruction failed")
        payload_parts = _add_parts(
            artifacts, f"{prefix}/payload", _pretty(interned_package), max_bytes
        )
        adjacency_lines = [
            "## Outgoing actual directed edges",
            *[
                f"- `{edge_id}`: {edge_by_id[edge_id]['kind']} to "
                f"`{edge_by_id[edge_id]['targetId']}`"
                for edge_id in outgoing[exercise_id]
            ],
            "",
            "## Incoming actual directed edges",
            *[
                f"- `{edge_id}`: {edge_by_id[edge_id]['kind']} from "
                f"`{edge_by_id[edge_id]['sourceId']}`"
                for edge_id in incoming[exercise_id]
            ],
        ]
        ledger_ref = _reference(
            Path(ledger_path), repository_root, f"/entries/{ledger_ordinal}", entry,
            loaded["ledger"][3],
        )
        metadata_pointer = f"/exercises/{_json_pointer_part(exercise_id)}"
        metadata_ref = (
            _reference(
                Path(metadata_path), repository_root, metadata_pointer, metadata_value,
                loaded["metadata"][3],
            )
            if metadata_value is not None else "absent (authoritative metadata has no record)"
        )
        catalog_ref = _reference(
            Path(catalog_path), repository_root, f"/exercises/{catalog_ordinal}",
            catalog_entry, loaded["catalog"][3],
        )
        upstream_ref = _reference(
            Path(upstream_manifest_path), repository_root, f"/{upstream_ordinal}",
            upstream_by_id[source_id], loaded["manifest"][3],
        )
        index_intro = (
            f"# Exercise `{exercise_id}`\n\n"
            "The original ledger remains authoritative. Concatenate the payload parts in "
            "listed order and parse as JSON; it preserves the full ledger evidence and aids, "
            "metadata, catalog record, upstream record, all graph decisions, and adjacency IDs.\n\n"
            f"Resolve any `{_REFERENCE_KEY}` object by opening its direct value index path "
            "and substituting the exact concatenated string.\n\n"
            "## Exact canonical references\n\n"
            f"- Ledger source lines: {ledger_ref}\n"
            f"- Metadata source lines: {metadata_ref}\n"
            f"- Catalog source lines: {catalog_ref}\n"
            f"- Upstream source lines: {upstream_ref}\n\n"
        )
        exercise_index = (
            index_intro
            + "## Payload parts\n\n"
            + "\n".join(f"- `{path}`" for path in payload_parts)
            + "\n\n## Graph adjacency\n\n"
            + "\n".join(adjacency_lines)
            + "\n"
        )
        if len(exercise_index.encode("utf-8")) > max_bytes:
            payload_pages = _paginate(
                artifacts,
                f"{prefix}/payload-index",
                f"{exercise_id} payload parts",
                [f"- `{path}`" for path in payload_parts],
                max_bytes,
            )
            adjacency_pages = _paginate(
                artifacts,
                f"{prefix}/adjacency",
                f"{exercise_id} graph adjacency",
                adjacency_lines,
                max_bytes,
            )
            exercise_index = (
                index_intro
                + "## Payload navigation\n\n"
                + "\n".join(f"- `{path}`" for path in payload_pages)
                + "\n\n## Graph navigation\n\n"
                + "\n".join(f"- `{path}`" for path in adjacency_pages)
                + "\n"
            )
        artifacts[f"{prefix}/INDEX.md"] = exercise_index
        exercise_index_lines.append(
            f"- `{exercise_id}` → `exercises/{exercise_id}/INDEX.md`"
        )

    exercise_pages = _paginate(
        artifacts, "exercises/index", "Exercise index", exercise_index_lines, max_bytes
    )
    cohort_dimensions = (
        ("disposition", "Review disposition"),
        ("metadataReviewState", "Metadata review state"),
        ("artworkReferenceStatus", "Artwork reference status"),
        ("automaticStrengthClassification", "Automatic strength classification"),
    )
    cohort_root_lines = []
    for field, title in cohort_dimensions:
        groups: dict[str, list[str]] = {}
        for entry in ledger_entries:
            raw_value = entry.get(field, "<not-recorded>")
            value = raw_value if isinstance(raw_value, str) else json.dumps(
                raw_value, ensure_ascii=False, sort_keys=True
            )
            groups.setdefault(value, []).append(entry["id"])
        dimension_lines = []
        for value in sorted(groups):
            path_value = value if re.fullmatch(r"[a-z0-9_-]+", value) else (
                "value-" + hashlib.sha256(value.encode("utf-8")).hexdigest()[:12]
            )
            cohort_pages = _paginate(
                artifacts,
                f"cohorts/{field}/cohort-{path_value}",
                f"{title}: {value}",
                [
                    f"- `{exercise_id}` → `exercises/{exercise_id}/INDEX.md`"
                    for exercise_id in groups[value]
                ],
                max_bytes,
            )
            dimension_lines.extend(
                f"- `{value}` ({len(groups[value])} exercises) → `{path}`"
                for path in cohort_pages
            )
        dimension_pages = _paginate(
            artifacts,
            f"cohorts/{field}/index",
            f"{title} cohorts",
            dimension_lines,
            max_bytes,
        )
        cohort_root_lines.extend(
            f"- {title} → `{path}`" for path in dimension_pages
        )
    artifacts["cohorts/INDEX.md"] = (
        "# Exercise cohorts\n\n"
        "These bounded pages contain navigation links only. They derive from canonical "
        "ledger fields and do not replace or duplicate per-exercise payloads.\n\n"
        + "\n".join(cohort_root_lines)
        + "\n"
    )
    edge_lines = []
    for edge in edges:
        pointer_parts = edge["decisionPointer"].split("/")
        decision = ledger_entries[int(pointer_parts[2])]["graphDecisions"][
            int(pointer_parts[4])
        ]
        decision_reference = _reference(
            Path(ledger_path), repository_root, edge["decisionPointer"], decision,
            loaded["ledger"][3],
        )
        edge_lines.append(
            f"- {edge['id']}: `{edge['sourceId']}` → `{edge['targetId']}` "
            f"({edge['kind']}, {edge['action']}); decision {decision_reference}; source "
            f"`exercises/{edge['sourceId']}/INDEX.md`; target "
            f"`exercises/{edge['targetId']}/INDEX.md`"
        )
    edge_pages = _paginate(
        artifacts, "edges/index", "Actual directed edge index", edge_lines, max_bytes
    )

    canonical_paths = {
        Path(ledger_path).resolve(), Path(metadata_path).resolve(),
        Path(catalog_path).resolve(), Path(upstream_manifest_path).resolve(),
    }
    context_index_lines = []
    supplemental_indexes = []
    seen_context: set[Path] = set()
    for ordinal, raw_path in enumerate(source_paths, 1):
        path = Path(raw_path)
        if not path.is_absolute():
            path = repository_root / path
        resolved = path.resolve()
        if resolved in canonical_paths:
            raise ValueError(f"canonical JSON is already projected; omit redundant --source {path}")
        if resolved in seen_context:
            raise ValueError(f"duplicate context source: {path}")
        seen_context.add(resolved)
        if not resolved.is_file():
            raise ValueError(f"missing context source: {path}")
        try:
            raw = resolved.read_bytes()
            text = raw.decode("utf-8")
        except UnicodeDecodeError as error:
            raise ValueError(f"context source is not UTF-8: {path}") from error
        source_name = _source_path(resolved, repository_root)
        if resolved.name == "INDEX.md":
            supplemental_indexes.append(source_name)
        directory = f"context/{ordinal:03d}-{hashlib.sha256(source_name.encode()).hexdigest()[:12]}"
        parts = _split_text(text, max_bytes)
        part_lines = []
        line = 1
        for part_number, part in enumerate(parts, 1):
            part_path = f"{directory}/part-{part_number:03d}.txt"
            artifacts[part_path] = part
            newline_count = part.count("\n")
            last_line = line + newline_count - (1 if part.endswith("\n") else 0)
            last_line = max(line, last_line)
            part_lines.append(f"- `{part_path}` source lines {line}-{last_line}")
            line += newline_count
        part_pages = _paginate(
            artifacts, f"{directory}/index", f"Context source {source_name}",
            [
                f"- Canonical source: `{source_name}`",
                f"- SHA-256: `{hashlib.sha256(raw).hexdigest()}`",
                f"- UTF-8 bytes: {len(raw)}",
                "- Concatenate all parts in order for exact reconstruction.",
                *part_lines,
            ],
            max_bytes,
        )
        context_index_lines.extend(
            f"- `{source_name}` page {number}/{len(part_pages)} → `{page}`"
            for number, page in enumerate(part_pages, 1)
        )
    context_pages = _paginate(
        artifacts, "context/index", "Changed code and documentation", context_index_lines,
        max_bytes,
    )
    reading_pages = _paginate(
        artifacts, "reading/index", "Complete exercise payload reading order",
        [
            f"- `{path}`"
            for path in sorted(artifacts)
            if path.startswith("exercises/") and "/payload-" in path and path.endswith(".txt")
        ],
        max_bytes,
    )

    sources = {}
    for label, path in (
        ("ledger", Path(ledger_path)),
        ("metadata", Path(metadata_path)),
        ("catalog", Path(catalog_path)),
        ("upstreamManifest", Path(upstream_manifest_path)),
    ):
        raw = loaded["manifest" if label == "upstreamManifest" else label][2]
        sources[label] = {
            "path": _source_path(path, repository_root),
            "sha256": hashlib.sha256(raw).hexdigest(),
            "bytes": len(raw),
        }
    manifest = {
        "schemaVersion": 1,
        "maximumArtifactBytes": max_bytes,
        "sources": sources,
        "counts": {
            "exercises": len(ledger_entries),
            "reviewedMetadata": len(metadata),
            "graphDecisions": sum(len(entry["graphDecisions"]) for entry in ledger_entries),
            "directedEdges": len(edges),
            "contextSources": len(source_paths),
            "internedExactStrings": len(repeated_strings),
        },
        "navigation": {
            "payloadReadingOrderPages": reading_pages,
            "exerciseIndexPages": exercise_pages,
            "edgeIndexPages": edge_pages,
            "cohortIndex": "cohorts/INDEX.md",
            "globalIndex": "globals/INDEX.md",
            "contextIndexPages": context_pages,
            "repeatedValueIndex": "values/INDEX.md",
        },
    }
    if supplemental_indexes:
        manifest["navigation"]["supplementalRepositoryIndexes"] = supplemental_indexes
    artifacts["MANIFEST.json"] = json.dumps(
        manifest, ensure_ascii=False, separators=(",", ":"), sort_keys=True
    ) + "\n"
    artifacts["INDEX.md"] = (
        "# WallCrawl full-catalog review index\n\n"
        "All files are bounded UTF-8 text. Start with the manifest, then open an exercise "
        "index page. The original evidence ledger remains authoritative; every package links "
        "to its exact source lines and JSON pointer.\n\n"
        "For exhaustive review, read every payload in the bounded reading-order pages; "
        "batch their direct paths with parallel VIEW calls. Per-exercise indexes remain "
        "available for exact canonical line pointers and adjacency without being a "
        "mandatory extra read before each payload. Resolve each exact repeated value once.\n\n"
        "- `MANIFEST.json`\n"
        + "\n".join(f"- `{path}`" for path in reading_pages)
        + "\n"
        "- `globals/INDEX.md`\n"
        "- `values/INDEX.md`\n"
        "- `cohorts/INDEX.md`\n"
        + (
            "\n## Additional supplied navigation (repository-relative paths)\n\n"
            + "\n".join(f"- `{path}`" for path in supplemental_indexes)
            + "\n\n"
            if supplemental_indexes else ""
        )
        + "\n".join(f"- `{path}`" for path in exercise_pages)
        + "\n"
        + "\n".join(f"- `{path}`" for path in edge_pages)
        + "\n"
        + "\n".join(f"- `{path}`" for path in context_pages)
        + "\n"
    )
    validate_artifacts(artifacts, max_bytes)
    return dict(sorted(artifacts.items()))


def _files(root: Path) -> set[str]:
    if not root.is_dir():
        return set()
    return {
        path.relative_to(root).as_posix()
        for path in root.rglob("*")
        if path.is_file()
    }


def check_review_index(output: Path, artifacts: dict[str, str]) -> list[str]:
    output = _validate_output_directory(output, require_owned=False)
    actual = _files(output)
    expected = set(artifacts)
    differences = [f"missing: {path}" for path in sorted(expected - actual)]
    differences.extend(f"extra: {path}" for path in sorted(actual - expected))
    for path in sorted(actual & expected):
        try:
            current = (output / path).read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            differences.append(f"changed: {path}")
            continue
        if current != artifacts[path]:
            differences.append(f"changed: {path}")
    return differences


def _validate_output_directory(output: Path, *, require_owned: bool = True) -> Path:
    output = Path(output)
    if ".." in output.parts:
        raise ValueError("review output must not traverse parent directories")
    output = output if output.is_absolute() else ROOT / output
    build_root = ROOT / "build"
    if output == build_root or not output.is_relative_to(build_root):
        raise ValueError("review output must be a dedicated subdirectory of repository build/")
    current = build_root
    for part in ("", *output.relative_to(build_root).parts):
        current = current / part if part else current
        if current.is_symlink():
            raise ValueError(f"review output must not traverse a symlink: {current}")
        if current.exists() and not current.is_dir():
            raise ValueError(f"review output requires directories: {current}")
    if output.exists() and any(output.iterdir()):
        if require_owned:
            marker = output / "MANIFEST.json"
            if marker.is_symlink() or not marker.is_file():
                raise ValueError(f"review output is not an owned review index: {output}")
            manifest = json.loads(marker.read_text(encoding="utf-8"))
            if not isinstance(manifest, dict) or not {
                "schemaVersion", "maximumArtifactBytes", "sources", "counts", "navigation"
            } <= manifest.keys():
                raise ValueError(f"review output is not an owned review index: {output}")
        if any(path.is_symlink() for path in output.rglob("*")):
            raise ValueError(f"review output contains a symlink: {output}")
    return output


def write_review_index(output: Path, artifacts: dict[str, str]) -> None:
    output = _validate_output_directory(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix=".review-index-", dir=output.parent))
    backup = staging.with_name(staging.name + "-old")
    try:
        for relative, value in artifacts.items():
            target = staging / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(value, encoding="utf-8", newline="\n")
        if output.exists():
            os.replace(output, backup)
        os.replace(staging, output)
    except BaseException:
        if backup.exists() and not output.exists():
            os.replace(backup, output)
        if staging.exists():
            shutil.rmtree(staging)
        raise
    # Publication is complete. Cleanup failure must not roll back to a partially
    # deleted backup or destroy the valid newly published index.
    if backup.exists():
        shutil.rmtree(backup)


def _manifest_sources(path: Path) -> list[Path]:
    value, _, _, _ = _load(path, "source manifest")
    if isinstance(value, dict):
        value = value.get("sources")
    if not isinstance(value, list) or not all(isinstance(item, str) for item in value):
        raise ValueError("source manifest must be a JSON string array or an object with sources")
    return [Path(item) for item in value]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ledger", type=Path, default=DEFAULT_LEDGER)
    parser.add_argument("--metadata", type=Path, default=DEFAULT_METADATA)
    parser.add_argument("--catalog", type=Path, default=DEFAULT_BUNDLE / "catalog.json")
    parser.add_argument(
        "--upstream-manifest", type=Path, default=DEFAULT_BUNDLE / "upstream-manifest.json"
    )
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--source", action="append", type=Path, default=[])
    parser.add_argument("--source-manifest", action="append", type=Path, default=[])
    parser.add_argument("--max-bytes", type=int, default=DEFAULT_MAX_BYTES)
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args()
    try:
        sources = list(arguments.source)
        for source_manifest in arguments.source_manifest:
            sources.extend(_manifest_sources(source_manifest))
        artifacts = build_artifacts(
            ledger_path=arguments.ledger,
            metadata_path=arguments.metadata,
            catalog_path=arguments.catalog,
            upstream_manifest_path=arguments.upstream_manifest,
            source_paths=sources,
            repository_root=ROOT,
            max_bytes=arguments.max_bytes,
        )
        if arguments.check:
            differences = check_review_index(arguments.output, artifacts)
            if differences:
                print("error: review index is stale:", file=sys.stderr)
                for difference in differences[:20]:
                    print(f"  {difference}", file=sys.stderr)
                if len(differences) > 20:
                    print(f"  ... {len(differences) - 20} more", file=sys.stderr)
                return 1
        else:
            write_review_index(arguments.output, artifacts)
    except (ValueError, OSError, KeyError, TypeError) as error:
        print(f"error: review index: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

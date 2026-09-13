#!/usr/bin/env python3
"""Render the human-inspection worksheet from the per-ID evidence ledger."""

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import sys

from import_catalog import (
    CatalogImportError, _atomic_write_text, _read_object,
    _validate_ai_acceptance, _validate_json_schema,
)
from verify_ai_acceptance_audit import AUDIT_PATH, MAX_AUDIT_BYTES, parse_audit


ROOT = Path(__file__).resolve().parents[2]
LEDGER = ROOT / "docs/research/2026-09-07-full-exercise-catalog-review.json"
METADATA = Path(__file__).with_name("reviewed-metadata.json")
OUTPUT = ROOT / "docs/reviewed-exercise-metadata-human-signoff.md"


def _audit_summary(ledger: dict) -> list[str]:
    review = ledger.get("review", {}).get("aiAcceptanceReview", {})
    reference = review.get("auditArtifact")
    if reference is None:
        return []
    if reference["path"] != AUDIT_PATH:
        raise ValueError("Worksheet requires the canonical committed audit path")
    with (ROOT / AUDIT_PATH).open("rb") as stream:
        raw = stream.read(MAX_AUDIT_BYTES + 1)
    audit = parse_audit(raw)
    digest = hashlib.sha256(raw).hexdigest()
    if (
        reference["sha256"] != digest
        or review["reviewerModelId"] != audit["reviewerModelId"]
        or review["reviewedAtEpochMillis"] != audit["reviewedAtEpochMillis"]
    ):
        raise ValueError("Worksheet audit hash, reviewer or original timestamp differs")
    return [
        f"- Canonical AI audit: [complete report, criteria, IDs and reasons]({AUDIT_PATH.removeprefix('docs/')})",
        f"- Audit artifact SHA-256: `{digest}`",
        f"- Recorded AI reviewer: `{audit['reviewerModelId']}`; original decision timestamp: "
        f"`{audit['reviewedAtEpochMillis']}` epoch milliseconds",
        "- Reproduce the partition and proposal bindings offline: "
        "`python3 tools/workout-guide/verify_ai_acceptance_audit.py`",
    ]


def classification_reason(entry: dict) -> str:
    boundary = {
        "supported": "The current automatic-strength type boundary admits this non-stretch recording type without excluded timed-conditioning tags",
        "excluded_stretch": "The current automatic-strength type boundary excludes this isStretch record",
        "excluded_distance_duration": "The current automatic-strength type boundary excludes distance-duration work",
        "excluded_timed_conditioning": "The current automatic-strength type boundary excludes duration work tagged Cardio",
    }[entry["automaticStrengthClassification"]]
    return (
        f"{boundary}; source recording type is {entry['sourceType']}. "
        "This describes type scope only, not metadata completeness, actual equipment/"
        "capability eligibility, content readiness or human approval. Browsing and "
        "manual access remain separate."
    )


def graph_assessments(metadata: dict, entries: list[dict]) -> dict[str, str]:
    """Render current adjacency; semantic rationales remain in graphDecisions."""
    outgoing = {entry["id"]: [] for entry in entries}
    incoming = {entry["id"]: [] for entry in entries}
    for source_id, value in metadata.items():
        for kind, field in (
            ("regression", "approvedRegressions"),
            ("substitution", "approvedSubstitutions"),
        ):
            for edge in value[field]:
                target_id = edge["exerciseId"]
                outgoing[source_id].append(f"{kind} -> {target_id}")
                incoming[target_id].append(f"{source_id} ({kind})")
    result = {}
    for entry in entries:
        exercise_id = entry["id"]
        counts = Counter(decision["action"] for decision in entry["graphDecisions"])
        result[exercise_id] = (
            "Current emitted reviewed-metadata outgoing: " + (", ".join(sorted(outgoing[exercise_id])) or "none") +
            ". Current emitted reviewed-metadata incoming: " + (", ".join(sorted(incoming[exercise_id])) or "none") +
            ". Explicit decisions: " +
            ", ".join(f"{action}={counts[action]}" for action in ("add", "retain", "hold", "reject")) +
            ". Each individual semantic rationale and current endpoint comparison is "
            "preserved in graphDecisions. Held/rejected proposals are not emitted. "
            "Source acceptance and relationship authorization do not accept a pending endpoint. "
            "Runtime independently requires accepted endpoints and all other eligibility gates. "
            "This generated adjacency is not human approval or a claim of equivalent outcomes."
        )
    return result


def render_packet(ledger: dict, metadata: dict) -> str:
    entries = ledger["entries"]
    ids = [entry["id"] for entry in entries]
    if len(ids) != len(set(ids)) or set(metadata) - set(ids):
        raise ValueError("Worksheet requires unique review IDs covering every metadata record")
    schema = _read_object(Path(__file__).with_name("review-schema.json"), "review schema")
    for entry in entries:
        if entry.get("humanSignoff") is not None:
            raise ValueError(f"{entry['id']}: this unsigned packet cannot certify human sign-off")
        value = metadata.get(entry["id"])
        if value is None:
            if entry["metadataSha256"] is not None or entry["disposition"] in {
                "ready_for_human_review", "ai_accepted"
            }:
                raise ValueError(f"{entry['id']}: ready/stale review without authored metadata")
            continue
        if (
            value["reviewState"] not in {"draft", "ai_accepted"}
            or value["provenance"]["reviewerRole"] is not None
            or value["provenance"]["reviewedAtEpochMillis"] is not None
        ):
            raise ValueError(f"{entry['id']}: this unsigned AI packet cannot certify human approval")
        if (entry["disposition"] == "ai_accepted") != (value["reviewState"] == "ai_accepted"):
            raise ValueError(f"{entry['id']}: ledger and metadata acceptance disagree")
        _validate_ai_acceptance(value, entry["id"], value["reviewState"], value["provenance"])
        if value["reviewState"] == "ai_accepted":
            ai = value["aiReviewProvenance"]
            _validate_json_schema(ai, schema["$defs"]["aiReviewProvenance"], schema,
                                  f"{entry['id']}.aiReviewProvenance", depth=0)
            if ai["reviewedContentId"] != entry["id"]:
                raise ValueError(f"{entry['id']}: AI acceptance describes a different proposal")
        digest = hashlib.sha256(json.dumps(
            value, sort_keys=True, separators=(",", ":"), ensure_ascii=False
        ).encode()).hexdigest()
        if digest != entry["metadataSha256"]:
            raise ValueError(f"{entry['id']}: metadata changed after its recorded review")
    counts = Counter(entry["disposition"] for entry in entries)
    states = Counter(value["reviewState"] for value in metadata.values())
    # Derived, never asserted: the moment a reviewer clears an ID this sentence must change
    # with the data rather than become a false claim inside the artifact they sign.
    cleared_ids = sorted(
        exercise_id for exercise_id, value in metadata.items()
        if value["clearedTrainingConstraints"]
    )
    cleared_summary = (
        "No record below lists any."
        if not cleared_ids
        else f"{len(cleared_ids)} of {len(metadata)} records list at least one: "
        + ", ".join(f"`{exercise_id}`" for exercise_id in cleared_ids)
        + "."
    )
    lines = [
        "# Exercise Metadata Human Sign-off",
        "",
        "This unsigned worksheet is generated from the "
        "[full per-exercise evidence ledger](research/2026-09-07-full-exercise-catalog-review.json). "
        "It records AI recommendations for human inspection, **not human approval or clinical validation**.",
        "",
        f"- Catalog entries examined: **{len(entries)}**",
        f"- AI-ready for human inspection: **{counts['ready_for_human_review']}**",
        f"- AI-accepted categorical metadata: **{counts['ai_accepted']}**",
        f"- Pending evidence or policy decisions: **{counts['pending_evidence_or_policy']}**",
        f"- Outside automatic-strength scope: **{counts['outside_automatic_strength_scope']}**",
        f"- Authored reviewed metadata: **{len(metadata)}** "
        f"(AI_ACCEPTED: **{states['ai_accepted']}**, DRAFT: **{states['draft']}**)",
        "- Human-approved metadata: **0**",
        f"- Pinned source: `{ledger['source']['commit']}`",
        *_audit_summary(ledger),
        "",
        "`AI_ACCEPTED` records an owner-authorized AI categorical decision, not human sign-off. "
        "`DRAFT` remains pending and ineligible for reviewed planning. "
        "Automatic-strength classification describes the current importer/planner boundary. "
        "Source acceptance, endpoint acceptance, type scope and human approval are separate "
        "judgments; none establishes suitability for every user.",
        "",
        "## What human sign-off covers",
        "",
        "For each ID, inspect the cited source and illustrations, the exact proposed metadata, "
        "its corrections and limitations, and every directed regression/substitution. The "
        "ledger's `metadataSha256` binds the exact current metadata, while "
        "`auditedProposalSha256` and AI `reviewedContentSha256` identify the exact pre-disposition "
        "proposal inspected by the corpus auditor, not a self-referential hash of the final record. "
        "Changed categorical proposals need renewed inspection. The fields named "
        "`approvedRegressions` and `approvedSubstitutions` preserve directed relationship history: "
        "they remain proposals on DRAFT sources and authorizations on accepted sources, never "
        "acceptance of a pending endpoint. Runtime independently requires relevant accepted "
        "endpoints and all other eligibility conditions before using a link.",
        "",
        "Schema version 3 adds dedicated `aiReviewProvenance` without filling human provenance. "
        "The committed audit preserves the complete original report and its final source-file "
        "mtime as the acceptance timestamp, not the archival copy's mtime or a new per-source "
        "fetch or illustration inspection. AI policy version 2 applies to "
        "accepted records; schema-only refreshes of pending drafts retain their original policy "
        "version and do not imply renewed acceptance.",
        "",
        "Reviewed schema version 2 adds `clearedTrainingConstraints`, so sign-off now also covers "
        "which selected joint sensitivities — shoulder, elbow, wrist, lower back, hip, knee — the "
        f"exercise is explicitly cleared for. {cleared_summary} An empty "
        "list is the fail-closed value: the exercise stays out of automatic planning for a user "
        "who selected that sensitivity, and no clearance is inferred from its name, muscles or "
        "movement pattern. `LOW_IMPACT_ONLY` is deliberately not part of that list because "
        "`impactLevel` already decides it. A clearance is a reviewer's product judgement about a "
        "self-reported label, never a diagnosis or clinical clearance.",
        "",
        "A human decision must identify the reviewed ID and fields, actual reviewer role, actual "
        "review time, rationale and remaining caveats. Only that explicit decision can support "
        "a later authored change to `reviewState=approved` with truthful provenance. A checklist, "
        "model consensus, software check, pull-request approval or merge is not that decision. "
        "No sign-off has been supplied for any row below.",
        "",
        f"All {len(entries)} exercises remain available for browsing and manual workouts. Excluded categories "
        "receive no manufactured strength allocation. Production reviewed planning remains disabled; "
        "metadata acceptance, human approval, equipment/profile availability and rollout are separate gates.",
        "",
        "## Per-ID sign-off register",
        "",
        "The ledger contains the field-group evidence and complete remaining decisions for each ID. "
        "`Not allocated` means no reviewed direct-primary block, not absence of muscle involvement.",
        "",
        "| Exercise ID | AI content disposition | Proposed direct primary | Human decision |",
        "| --- | --- | --- | --- |",
    ]
    for entry in sorted(entries, key=lambda value: value["id"]):
        primary = metadata.get(entry["id"], {}).get("directPrimaryMuscle", "Not allocated")
        decision = "N/A" if entry["automaticStrengthClassification"] != "supported" else "Pending"
        lines.append(f"| `{entry['id']}` | {entry['disposition']} | {primary} | {decision} |")
    lines.extend([
        "", "## Additional evidence and policy decisions", "",
        "Human approval still requires field-by-field human inspection. These rows additionally "
        "have unresolved content or representation decisions; they are not ready recommendations.",
        "",
        "| Exercise ID | Remaining decisions |",
        "| --- | --- |",
    ])
    for entry in sorted(entries, key=lambda value: value["id"]):
        if entry["disposition"] == "pending_evidence_or_policy":
            decisions = " ".join(" ".join(entry["remainingDecisions"]).split()).replace("|", "\\|")
            lines.append(f"| `{entry['id']}` | {decisions} |")
    lines.extend([
        "", "## Artwork reference restrictions", "",
        "**Unsuitable as references for new illustrations until reconciled:** the IDs below "
        "have source identity or setup conflicts. Do not use their current frames as references "
        "for female variants or other replacement illustrations before resolving those conflicts. "
        "The ledger retains each canonical ID, exact pinned source PNG paths, bundled SVG paths, "
        "citations and observations. No images or identities have been changed.",
        "",
        "| Exercise ID | Observed reference conflict |",
        "| --- | --- |",
    ])
    for entry in sorted(entries, key=lambda value: value["id"]):
        if entry["artworkReferenceStatus"] == "do_not_use_until_reconciled":
            reason = " ".join(entry["artworkReferenceReason"].split()).replace("|", "\\|")
            lines.append(f"| `{entry['id']}` | {reason} |")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args()
    try:
        rendered = render_packet(
            _read_object(LEDGER, "full catalog review"),
            _read_object(METADATA, "reviewed metadata")["exercises"],
        )
        if arguments.check:
            if not OUTPUT.is_file() or OUTPUT.read_text() != rendered:
                print("error: human sign-off worksheet differs from authored review", file=sys.stderr)
                return 1
        else:
            _atomic_write_text(OUTPUT, rendered)
    except (CatalogImportError, ValueError, KeyError, OSError) as error:
        print(f"error: review packet: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

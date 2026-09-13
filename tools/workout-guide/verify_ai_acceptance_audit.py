#!/usr/bin/env python3
"""Verify the committed AI decision audit and derive its corpus partition offline.

The archived report is historical evidence, not a new review. Proposal hashes cover
the inspected schema-v2/policy-v1 drafts; current metadata also carries disposition
and schema-v3 provenance. Verification never changes either representation.
"""

from __future__ import annotations

import argparse
from collections import Counter
import copy
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import sys

from import_catalog import (
    CatalogImportError, SAFE_ID_PATTERN, _bounded_json_value, _read_object,
    _reject_duplicate_json_fields, _reject_non_finite_number,
)


ROOT = Path(__file__).resolve().parents[2]
AUDIT_PATH = "docs/research/2026-09-13-ai-acceptance-audit.json"
LEDGER_PATH = "docs/research/2026-09-07-full-exercise-catalog-review.json"
FIXTURE_PATH = "app/src/test/resources/ai-acceptance/partition-v2.json"
PROGRAMMING_PATH = "tools/workout-guide/programming-overrides.json"
MAX_AUDIT_BYTES = 64 * 1024
OUTSIDE_CLASSES = ("excluded_stretch", "excluded_distance_duration", "excluded_timed_conditioning")


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def _canonical_bytes(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode()


def _compact_bytes(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


def _digest(value: object) -> str:
    return hashlib.sha256(_compact_bytes(value)).hexdigest()


def parse_audit(raw: bytes) -> dict:
    _require(len(raw) <= MAX_AUDIT_BYTES, "committed audit exceeds the 65536-byte limit")
    try:
        text = raw.decode("utf-8")
        value = json.loads(
            text,
            object_pairs_hook=lambda pairs: _reject_duplicate_json_fields(pairs, "committed audit"),
            parse_constant=lambda _: _reject_non_finite_number("committed audit"),
        )
        _bounded_json_value(value, "committed audit", depth=0)
    except (UnicodeDecodeError, json.JSONDecodeError, RecursionError) as error:
        raise ValueError("committed audit must be bounded UTF-8 JSON") from error
    _require(isinstance(value, dict), "committed audit must be an object")
    _require(raw == _canonical_bytes(value), "committed audit is not canonical JSON")
    _require(not any(marker in text for marker in (
        "/Users/", "/home/", "/tmp/", "/var/folders/", "file://", "AppData\\"
    )), "committed audit contains a private or absolute filesystem path")
    _require(not re.search(r"[A-Za-z]:\\\\", text),
             "committed audit contains an absolute filesystem path")
    _require(set(value) == {
        "schemaVersion", "reviewerModelId", "reviewedAtEpochMillis", "currentProgrammingSha256",
        "reportCanonicalSha256", "timestampSource", "preservationNote", "report",
    }, "committed audit envelope fields differ")
    _require(value["schemaVersion"] == 2, "unsupported committed audit schema")
    _require(isinstance(value["reviewerModelId"], str) and bool(value["reviewerModelId"].strip()),
             "committed audit requires its actual reviewer model")
    _require(type(value["reviewedAtEpochMillis"]) is int and value["reviewedAtEpochMillis"] > 0,
             "committed audit requires its original decision timestamp")
    _require(_digest(value["report"]) == value["reportCanonicalSha256"],
             "committed audit report digest differs")
    for field in ("ledger", "metadata", "programming", "catalog", "manifest", "schema"):
        source_path = PurePosixPath(value["report"]["sources"][field])
        _require(not source_path.is_absolute() and ".." not in source_path.parts,
                 "committed audit source contains an absolute or escaping filesystem path")
    return value


def _ids(values: list | dict, label: str) -> set[str]:
    _require(isinstance(values, (list, dict)), f"{label} must list canonical IDs")
    _require(all(isinstance(value, str) and SAFE_ID_PATTERN.fullmatch(value) for value in values),
             f"{label} contains an invalid ID")
    result = set(values)
    _require(len(result) == len(values), f"{label} contains duplicate IDs")
    return result


def derive_partition(report: dict, canonical_ids: set[str]) -> dict[str, set[str]]:
    _require(report["reportVersion"] == 1 and report["readOnly"] is True,
             "expected the original read-only audit report")
    _require(report["acceptancePerformed"] is False and
             report["fullAutomaticPrescriptionCertified"] is False,
             "the historical audit cannot become acceptance or prescription certification")
    policy = report["candidatePolicy"]
    for field in (
        "requiresAcceptedRelationshipEndpointsForSourceAcceptance",
        "graphClosureIsConservativeAuditChoice", "graphClosureIsCurrentSchemaRequirement",
    ):
        _require(policy[field] is False, "intrinsic acceptance must not require endpoint acceptance")
    pending = report["pending_withheld"]
    original_drafts = _ids(pending["existingDrafts"], "original pending drafts")
    absent = _ids(pending["existingOmittedMetadata"], "pending unauthored records")
    additional = _ids(pending["additionalAuditHolds"], "additional intrinsic holds")
    outside = set()
    for classification in OUTSIDE_CLASSES:
        group = _ids(report["outside_scope"][classification], classification)
        _require(not outside & group, "outside-scope classifications overlap")
        outside |= group
    groups = (original_drafts, absent, additional, outside)
    _require(sum(map(len, groups)) == len(set().union(*groups)), "audit hold partitions overlap")
    _require(set().union(*groups) <= canonical_ids, "audit hold partition contains an unknown ID")
    for field in ("existingDrafts", "existingOmittedMetadata"):
        _require(all(isinstance(reason, str) and reason.strip() for reason in pending[field].values()),
                 "pending records require their recorded reason")
    for exercise_id, hold in pending["additionalAuditHolds"].items():
        correction = report["corrections"].get(hold["correction"])
        _require(isinstance(correction, dict) and exercise_id in correction.get("ids", []),
                 f"{exercise_id}: intrinsic hold correction is missing")
        _require(bool(correction.get("finding")) and bool(correction.get("requiredDecision")),
                 f"{exercise_id}: intrinsic hold correction lacks its decision criteria")

    # The audit starts with the previous ready cohort, subtracts only intrinsic holds,
    # and expressly does not remove sources merely because an endpoint is pending.
    original_ready = canonical_ids - original_drafts - absent - outside
    accepted = original_ready - additional
    authored_pending = original_drafts | additional
    candidates = report["candidate_accepted"]
    with_programming = _ids(candidates["withExistingProgramming"], "programmed candidates")
    without_programming = _ids(candidates["withoutExistingProgramming"], "categorical-only candidates")
    _require(not with_programming & without_programming, "candidate partition overlaps")
    _require(accepted == with_programming | without_programming,
             "candidate partition does not follow the intrinsic hold criteria")
    restored = _ids(report["corrections"]["C3"]["sourcesRestoredToCandidateCohort"],
                    "former graph-only holds")
    _require(restored <= accepted, "former graph-only holds cannot require endpoint acceptance")
    _require(len(canonical_ids) == report["scope"]["canonicalIds"], "canonical ID count differs")
    _require(len(original_ready) == report["currentDispositionCounts"]["ready_for_human_review"],
             "original ready cohort differs from the recorded criteria")
    _require((len(accepted), len(authored_pending | absent), len(outside)) == (
        policy["candidateCount"], policy["pendingCount"], policy["outsideCount"]
    ), "derived audit partition counts differ")
    return {"aiAccepted": accepted, "pendingWithDraft": authored_pending,
            "pendingWithoutMetadata": absent, "outsideScope": outside}


def _proposal(value: dict) -> dict:
    proposal = copy.deepcopy(value)
    proposal.pop("aiReviewProvenance", None)
    proposal["reviewState"] = "draft"
    proposal["provenance"]["schemaVersion"] = 2
    proposal["provenance"]["policyVersion"] = 1
    return proposal


def reconstruct_audited_ledger(ledger: dict, proposals: dict[str, dict]) -> dict:
    """Reverse only the recorded v3 disposition bookkeeping, preserving inspected evidence.

    The original ledger used ordered, indented UTF-8 JSON. Its current/proposed
    digests and generated adjacency must be rebuilt from the inspected proposals,
    not copied from the historical whole-file hash being checked.
    """
    original = copy.deepcopy(ledger)
    review = original["review"]
    review["metadataSchemaVersion"] = 2
    review["metadataPolicyVersion"] = 1
    review.pop("metadataSchemaVersion3Note")
    review.pop("aiAcceptanceReview")
    entries = original["entries"]
    proposal_hashes = {exercise_id: _digest(value) for exercise_id, value in proposals.items()}
    outgoing = {entry["id"]: [] for entry in entries}
    incoming = {entry["id"]: [] for entry in entries}
    for source_id, value in proposals.items():
        for kind, field in (("regression", "approvedRegressions"), ("substitution", "approvedSubstitutions")):
            for edge in value[field]:
                outgoing[source_id].append(f"{kind} -> {edge['exerciseId']}")
                incoming[edge["exerciseId"]].append(f"{source_id} ({kind})")
    for entry in entries:
        exercise_id = entry["id"]
        history = entry.pop("dispositionHistory", [])
        if history:
            _require(len(history) == 1, f"{exercise_id}: ambiguous audited ledger disposition history")
            entry["disposition"] = history[0]["priorDisposition"]
        entry["metadataReviewState"] = "draft" if exercise_id in proposals else "absent"
        entry["metadataSha256"] = proposal_hashes.get(exercise_id)
        entry.pop("auditedProposalSha256", None)
        hold = entry.pop("auditHold", None)
        if hold is not None:
            _require(entry["limitations"][-1:] == [hold["finding"]] and
                     entry["remainingDecisions"][-1:] == [hold["requiredDecision"]],
                     f"{exercise_id}: audited ledger hold addenda differ")
            entry["limitations"] = entry["limitations"][:-1]
            entry["remainingDecisions"] = entry["remainingDecisions"][:-1]
        for decision in entry["graphDecisions"]:
            decision["sourceMetadataSha256"] = proposal_hashes.get(exercise_id)
            decision["targetMetadataSha256"] = proposal_hashes.get(decision["targetId"])
        counts = Counter(decision["action"] for decision in entry["graphDecisions"])
        # This is the archived generator's representation, not current review prose.
        entry["policyAssessment"]["links"] = (
            "Current emitted DRAFT outgoing: " + (", ".join(sorted(outgoing[exercise_id])) or "none") +
            ". Current emitted DRAFT incoming: " + (", ".join(sorted(incoming[exercise_id])) or "none") +
            ". Explicit decisions: " +
            ", ".join(f"{action}={counts[action]}" for action in ("add", "retain", "hold", "reject")) +
            ". Each individual semantic rationale and current endpoint comparison is "
            "preserved in graphDecisions. Held/rejected proposals are not emitted. "
            "This generated adjacency is not human approval or a claim of equivalent outcomes."
        )
    return original


def verify_audit(
    raw: bytes, ledger: dict, metadata_document: dict, catalog: dict, fixture: dict,
    programming_bytes: bytes,
) -> dict:
    audit = parse_audit(raw)
    artifact_sha = hashlib.sha256(raw).hexdigest()
    reference = {"path": AUDIT_PATH, "sha256": artifact_sha}
    review = ledger["review"]["aiAcceptanceReview"]
    _require(fixture["auditArtifact"] == reference and review["auditArtifact"] == reference,
             "committed audit artifact hash/reference differs from ledger or partition fixture")
    _require("auditSha256" not in fixture and "externalAuditSha256" not in review,
             "unavailable source-audit byte identities must not be asserted")
    programming_sha = hashlib.sha256(programming_bytes).hexdigest()
    _require(programming_sha == audit["currentProgrammingSha256"] ==
             fixture["currentProgrammingSha256"] == review["currentProgrammingSha256"],
             "current programming bytes differ from currentProgrammingSha256")
    model = audit["reviewerModelId"]
    timestamp = audit["reviewedAtEpochMillis"]
    _require(model == fixture["reviewerModelId"] == review["reviewerModelId"],
             "audit reviewer model differs from ledger or partition fixture")
    _require(timestamp == fixture["auditMtimeEpochMillis"] == review["reviewedAtEpochMillis"],
             "original audit timestamp differs from ledger or partition fixture")
    report = audit["report"]
    source_hashes = report["sources"]["sha256"]
    _require(programming_sha == source_hashes["programming"],
             "current programming bytes differ from the audited programming source")
    _require(source_hashes == review["auditedSourceSha256"], "audited source hashes differ")
    _require(source_hashes["metadata"] == fixture["auditedMetadataSha256"] and
             source_hashes["ledger"] == fixture["auditedLedgerSha256"],
             "audited source hashes differ from partition fixture")
    _require(report["sources"]["pinnedUpstreamCommit"] == ledger["source"]["commit"] ==
             catalog["source"]["commit"], "pinned source identity differs")
    catalog_by_id = {entry["id"]: entry for entry in catalog["exercises"]}
    entries = {entry["id"]: entry for entry in ledger["entries"]}
    _require(len(catalog_by_id) == len(catalog["exercises"]) and
             len(entries) == len(ledger["entries"]), "duplicate canonical ID")
    _require(set(entries) == set(catalog_by_id), "ledger/catalog canonical IDs differ")
    partition = derive_partition(report, set(catalog_by_id))
    audit_holds = report["pending_withheld"]["additionalAuditHolds"]
    _require({exercise_id for exercise_id, entry in entries.items() if "auditHold" in entry} ==
             set(audit_holds), "audited ledger intrinsic hold records differ")
    for exercise_id, recorded in audit_holds.items():
        hold = entries[exercise_id]["auditHold"]
        correction = report["corrections"][recorded["correction"]]
        _require(hold["reason"] == recorded["reason"] and
                 hold["correction"] == recorded["correction"] and
                 hold["finding"] == correction["finding"] and
                 hold["requiredDecision"] == correction["requiredDecision"] and
                 hold["auditArtifactPath"] == AUDIT_PATH and "auditSha256" not in hold,
                 f"{exercise_id}: audited ledger hold differs from the committed audit")
    for name, ids in partition.items():
        _require(ids == _ids(fixture[name], f"fixture {name}"), f"derived {name} partition differs from fixture")
    accepted = partition["aiAccepted"]
    drafts = partition["pendingWithDraft"]
    outside = partition["outsideScope"]
    metadata = metadata_document["exercises"]
    _require(set(metadata) == accepted | drafts, "authored proposal partition differs")
    _require(metadata_document["schemaVersion"] == fixture["schemaVersion"],
             "reviewed schema version differs")
    _require(len(metadata) == report["scope"]["authoredProposalsInspected"],
             "audited proposal count differs")
    original_proposals = {exercise_id: _proposal(value) for exercise_id, value in metadata.items()}
    original_document = {"schemaVersion": 2, "exercises": original_proposals}
    original_bytes = (json.dumps(original_document, ensure_ascii=False, indent=2) + "\n").encode()
    metadata_sha = hashlib.sha256(original_bytes).hexdigest()
    _require(metadata_sha == source_hashes["metadata"],
             "audited proposal document differs from the inspected source hash")
    for exercise_id, entry in entries.items():
        disposition = ("ai_accepted" if exercise_id in accepted else
                       "outside_automatic_strength_scope" if exercise_id in outside else
                       "pending_evidence_or_policy")
        _require(entry["disposition"] == disposition, f"{exercise_id}: ledger disposition differs")
        _require(entry["humanSignoff"] is None, f"{exercise_id}: unexpected human sign-off")
        value = metadata.get(exercise_id)
        _require(catalog_by_id[exercise_id].get("reviewedMetadata") == value,
                 f"{exercise_id}: catalog proposal/provenance differs")
        if value is None:
            _require(entry["metadataReviewState"] == "absent" and entry["metadataSha256"] is None,
                     f"{exercise_id}: unauthored record acquired metadata")
            continue
        state = "ai_accepted" if exercise_id in accepted else "draft"
        _require(value["reviewState"] == entry["metadataReviewState"] == state,
                 f"{exercise_id}: metadata disposition differs")
        _require(entry["metadataSha256"] == _digest(value), f"{exercise_id}: current metadata hash differs")
        proposal_sha = _digest(original_proposals[exercise_id])
        _require(entry["auditedProposalSha256"] == proposal_sha,
                 f"{exercise_id}: audited proposal hash differs")
        _require(value["provenance"]["reviewerRole"] is None and
                 value["provenance"]["reviewedAtEpochMillis"] is None and
                 value["clearedTrainingConstraints"] == [], f"{exercise_id}: unexpected approval/clearance")
        if exercise_id in accepted:
            ai = value["aiReviewProvenance"]
            _require(ai["reviewedContentId"] == exercise_id and ai["reviewedContentSha256"] == proposal_sha,
                     f"{exercise_id}: AI reviewed proposal identity/hash differs")
            _require(ai["reviewerModelId"] == model and ai["reviewedAtEpochMillis"] == timestamp,
                     f"{exercise_id}: AI reviewer model or timestamp differs")
            recorded = {evidence["source"] for evidence in entry["evidence"]}
            _require(1 <= len(ai["sourceReferences"]) <= 8 and
                     set(ai["sourceReferences"]) <= recorded, f"{exercise_id}: AI evidence references differ")
        else:
            _require("aiReviewProvenance" not in value, f"{exercise_id}: draft acquired AI acceptance")
        for history in entry.get("dispositionHistory", []):
            _require(history["auditArtifactPath"] == AUDIT_PATH and "auditSha256" not in history and
                     history["reviewedAtEpochMillis"] == timestamp and
                     history["priorMetadataSha256"] == proposal_sha,
                     f"{exercise_id}: disposition history does not resolve to the committed audit")
    original_ledger = reconstruct_audited_ledger(ledger, original_proposals)
    ledger_bytes = (json.dumps(original_ledger, ensure_ascii=False, indent=2) + "\n").encode()
    ledger_sha = hashlib.sha256(ledger_bytes).hexdigest()
    _require(ledger_sha == source_hashes["ledger"],
             "reconstructed audited ledger source hash differs")
    original_catalog = copy.deepcopy(catalog)
    for value in original_catalog["exercises"]:
        if value["id"] in original_proposals:
            value["reviewedMetadata"] = original_proposals[value["id"]]
    catalog_sha = hashlib.sha256(_compact_bytes(original_catalog) + b"\n").hexdigest()
    _require(catalog_sha == source_hashes["catalog"], "audited catalog source hash differs")
    return {
        "auditPath": AUDIT_PATH, "auditSha256": artifact_sha,
        "reviewerModelId": model, "reviewedAtEpochMillis": timestamp,
        "counts": {"ai_accepted": len(accepted), "pending_evidence_or_policy": len(drafts) +
                   len(partition["pendingWithoutMetadata"]), "outside_automatic_strength_scope": len(outside)},
        "acceptedIds": sorted(accepted),
        "pendingIds": sorted(drafts | partition["pendingWithoutMetadata"]),
        "outsideIds": sorted(outside), "auditedProposalsVerified": len(original_proposals),
        "verifiedSourceSha256": {
            "auditedLedger": ledger_sha, "auditedMetadata": metadata_sha,
            "auditedCatalog": catalog_sha, "currentProgramming": programming_sha,
        },
    }


def verify_repository(root: Path = ROOT) -> dict:
    with (root / AUDIT_PATH).open("rb") as stream:
        raw = stream.read(MAX_AUDIT_BYTES + 1)
    return verify_audit(
        raw, _read_object(root / LEDGER_PATH, "evidence ledger"),
        _read_object(root / "tools/workout-guide/reviewed-metadata.json", "reviewed metadata"),
        _read_object(root / "app/src/main/assets/workout-guide/catalog.json", "catalog"),
        _read_object(root / FIXTURE_PATH, "partition fixture"),
        (root / PROGRAMMING_PATH).read_bytes(),
    )


def main() -> int:
    argparse.ArgumentParser(description=__doc__).parse_args()
    try:
        result = verify_repository()
    except (CatalogImportError, ValueError, KeyError, TypeError, OSError) as error:
        print(f"error: AI acceptance audit: {error}", file=sys.stderr)
        return 1
    print(json.dumps(result, ensure_ascii=False, sort_keys=True, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

"""Pin the owner-authorized audit partition, without manufacturing human approval."""

import copy
import hashlib
import json
from collections import Counter
from pathlib import Path
import unittest
from urllib.parse import urlsplit

from import_catalog import _read_object, _validate_json_schema


ROOT = Path(__file__).resolve().parents[2]
FIXTURE = ROOT / "app/src/test/resources/ai-acceptance/partition-v2.json"
LEDGER = ROOT / "docs/research/2026-09-07-full-exercise-catalog-review.json"
METADATA = ROOT / "tools/workout-guide/reviewed-metadata.json"
BUNDLE = ROOT / "app/src/main/assets/workout-guide/catalog.json"
GRAPH_ONLY_SOURCES = {
    "archer-push-up", "dumbbell-bent-over-row", "hindu-push-up",
    "hip-adduction-machine", "pistol-squat", "push-up", "seated-row",
}
PENDING_ENDPOINT_EDGES = {
    ("dumbbell-bent-over-row", "approvedRegressions", "machine-row"),
    ("hip-adduction-machine", "approvedSubstitutions", "cable-standing-hip-adduction"),
    ("pistol-squat", "approvedRegressions", "assisted-pistol-squat"),
    ("push-up", "approvedRegressions", "knee-push-up"),
    ("seated-row", "approvedSubstitutions", "machine-row"),
}
INTRINSIC_HOLDS = {
    "cable-kickback", "cable-standing-hip-abduction",
    "cable-standing-hip-adduction", "fire-hydrant",
}


def digest(value):
    return hashlib.sha256(json.dumps(
        value, sort_keys=True, separators=(",", ":"), ensure_ascii=False
    ).encode()).hexdigest()


def pre_disposition_proposal(value):
    """Undo only the recorded disposition/schema bookkeeping, not reviewed content."""
    proposal = copy.deepcopy(value)
    proposal.pop("aiReviewProvenance", None)
    proposal["reviewState"] = "draft"
    proposal["provenance"]["schemaVersion"] = 2
    proposal["provenance"]["policyVersion"] = 1
    return proposal


class AiAcceptedCorpusTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = _read_object(FIXTURE, "audit partition fixture")
        cls.ledger = _read_object(LEDGER, "evidence ledger")
        cls.entries = {entry["id"]: entry for entry in cls.ledger["entries"]}
        cls.document = _read_object(METADATA, "reviewed metadata")
        cls.metadata = cls.document["exercises"]
        cls.catalog = _read_object(BUNDLE, "bundled catalog")
        cls.accepted = set(cls.fixture["aiAccepted"])
        cls.pending_drafts = set(cls.fixture["pendingWithDraft"])
        cls.pending_absent = set(cls.fixture["pendingWithoutMetadata"])
        cls.outside = set(cls.fixture["outsideScope"])

    def test_partition_is_exact_exhaustive_and_disjoint_over_all_302_ids(self):
        groups = [self.accepted, self.pending_drafts, self.pending_absent, self.outside]
        self.assertEqual([182, 29, 56, 35], list(map(len, groups)))
        all_ids = [exercise_id for group in groups for exercise_id in group]
        self.assertEqual(302, len(set(all_ids)))
        self.assertEqual(set(all_ids), set(self.entries))
        self.assertEqual(set(all_ids), {value["id"] for value in self.catalog["exercises"]})
        self.assertEqual({
            "ai_accepted": 182, "pending_evidence_or_policy": 85,
            "outside_automatic_strength_scope": 35,
        }, Counter(entry["disposition"] for entry in self.entries.values()))
        for exercise_id in all_ids:
            expected = (
                "ai_accepted" if exercise_id in self.accepted else
                "outside_automatic_strength_scope" if exercise_id in self.outside else
                "pending_evidence_or_policy"
            )
            self.assertEqual(expected, self.entries[exercise_id]["disposition"], exercise_id)
        self.assertEqual(self.accepted, {
            exercise_id for exercise_id, value in self.metadata.items()
            if value["reviewState"] == "ai_accepted"
        })

    def test_only_audited_candidates_are_accepted_and_unauthored_blocks_stay_absent(self):
        self.assertEqual(self.accepted | self.pending_drafts, set(self.metadata))
        self.assertEqual({"ai_accepted": 182, "draft": 29},
                         Counter(value["reviewState"] for value in self.metadata.values()))
        for exercise_id in self.pending_drafts:
            value = self.metadata[exercise_id]
            self.assertEqual("draft", value["reviewState"], exercise_id)
            self.assertNotIn("aiReviewProvenance", value, exercise_id)
            self.assertEqual(1, value["provenance"]["policyVersion"], exercise_id)
        for exercise_id in self.pending_absent | self.outside:
            self.assertNotIn(exercise_id, self.metadata)
            self.assertEqual("absent", self.entries[exercise_id]["metadataReviewState"])
            self.assertIsNone(self.entries[exercise_id]["metadataSha256"])

    def test_no_human_signoff_and_no_inferred_constraint_clearances(self):
        self.assertFalse(any(e["humanSignoff"] is not None for e in self.entries.values()))
        self.assertNotIn("approved", {value["reviewState"] for value in self.metadata.values()})
        for exercise_id, value in self.metadata.items():
            with self.subTest(exercise=exercise_id):
                self.assertIsNone(value["provenance"]["reviewerRole"])
                self.assertIsNone(value["provenance"]["reviewedAtEpochMillis"])
                self.assertEqual([], value["clearedTrainingConstraints"])
        self.assertFalse(self.ledger["review"]["productionReviewedEligibilityEnabled"])

    def test_schema_three_applies_to_all_records_without_reaccepting_drafts(self):
        self.assertEqual(3, self.document["schemaVersion"])
        self.assertEqual(3, self.ledger["review"]["metadataSchemaVersion"])
        self.assertEqual(2, self.ledger["review"]["metadataPolicyVersion"])
        for value in self.metadata.values():
            self.assertEqual(3, value["provenance"]["schemaVersion"])
        schema = _read_object(ROOT / "tools/workout-guide/review-schema.json", "schema")
        _validate_json_schema(self.document, schema, schema, "reviewed metadata", depth=0)
        self.assertIn("not renewed inspection",
                      self.ledger["review"]["metadataSchemaVersion3Note"])

    def test_ledger_acceptance_history_names_the_same_audit_and_actual_partition(self):
        review = self.ledger["review"]["aiAcceptanceReview"]
        self.assertEqual(self.fixture["auditSha256"], review["externalAuditSha256"])
        self.assertEqual(self.fixture["auditMtimeEpochMillis"], review["reviewedAtEpochMillis"])
        self.assertEqual("gpt-6-astra", review["reviewerModelId"])
        self.assertEqual(self.fixture["auditedMetadataSha256"],
                         review["auditedSourceSha256"]["metadata"])
        self.assertEqual(self.fixture["auditedLedgerSha256"],
                         review["auditedSourceSha256"]["ledger"])
        self.assertEqual(Counter(e["disposition"] for e in self.entries.values()),
                         review["finalDispositionCounts"])
        self.assertEqual(Counter(v["reviewState"] for v in self.metadata.values()),
                         review["finalMetadataReviewStateCounts"])
        self.assertEqual(0, review["actualHumanApprovals"])
        self.assertEqual([], review["additionalWithheldBeyondAudit"])
        self.assertTrue(review["sourceAndEndpointAcceptanceAreIndependent"])
        self.assertEqual(85, review["currentDirectedEdges"])
        self.assertEqual(PENDING_ENDPOINT_EDGES, {
            (edge["source"], edge["field"], edge["target"])
            for edge in review["authorizedLinksWithPendingEndpoints"]
        })
        for exercise_id in self.accepted | INTRINSIC_HOLDS:
            entry = self.entries[exercise_id]
            history = entry["dispositionHistory"][-1]
            self.assertEqual("ready_for_human_review", history["priorDisposition"])
            self.assertEqual("draft", history["priorMetadataReviewState"])
            self.assertEqual(entry["auditedProposalSha256"], history["priorMetadataSha256"])
            self.assertEqual(entry["disposition"], history["decision"])
            self.assertEqual(self.fixture["auditSha256"], history["auditSha256"])
            self.assertEqual(self.fixture["auditMtimeEpochMillis"], history["reviewedAtEpochMillis"])

    def test_ai_provenance_uses_real_auditor_time_exact_id_and_only_recorded_evidence(self):
        expected_fields = {
            "reviewerModelId", "reviewedAtEpochMillis", "reviewedContentId",
            "reviewedContentSha256", "sourceReferences", "decisionRationale",
            "limitations", "schemaVersion", "policyVersion",
        }
        for exercise_id in sorted(self.accepted):
            with self.subTest(exercise=exercise_id):
                value = self.metadata[exercise_id]
                self.assertIn("aiReviewProvenance", value)
                provenance = value["aiReviewProvenance"]
                self.assertEqual(expected_fields, set(provenance))
                self.assertEqual("gpt-6-astra", provenance["reviewerModelId"])
                self.assertEqual(self.fixture["auditMtimeEpochMillis"],
                                 provenance["reviewedAtEpochMillis"])
                self.assertEqual(exercise_id, provenance["reviewedContentId"])
                self.assertEqual(3, provenance["schemaVersion"])
                self.assertEqual(2, provenance["policyVersion"])
                self.assertEqual(2, value["provenance"]["policyVersion"])
                self.assertEqual(digest(pre_disposition_proposal(value)),
                                 provenance["reviewedContentSha256"])
                self.assertNotEqual(digest(value), provenance["reviewedContentSha256"],
                                    "The audit inspected the pre-disposition proposal.")
                references = provenance["sourceReferences"]
                self.assertGreaterEqual(len(references), 1)
                self.assertLessEqual(len(references), 8)
                self.assertEqual(len(references), len(set(references)))
                recorded = {e["source"] for e in self.entries[exercise_id]["evidence"]}
                self.assertTrue(set(references).issubset(recorded))
                for reference in references:
                    url = urlsplit(reference)
                    self.assertEqual("https", url.scheme)
                    self.assertTrue(url.hostname)
                    self.assertIsNone(url.username)
                    self.assertIsNone(url.password)
                    self.assertFalse(any(character.isspace() for character in reference))
                for field in ("decisionRationale", "limitations"):
                    self.assertGreater(len(provenance[field].strip()), 30)
                    self.assertLessEqual(len(provenance[field]), 1000)
                self.assertIn(exercise_id, provenance["decisionRationale"])
                self.assertIn("not human", provenance["limitations"])
                self.assertIn("no new", provenance["limitations"])

    def test_original_proposals_rationales_and_directed_links_are_unchanged(self):
        reconstructed = {
            "schemaVersion": 2,
            "exercises": {
                exercise_id: pre_disposition_proposal(value)
                for exercise_id, value in self.metadata.items()
            },
        }
        original_bytes = (json.dumps(reconstructed, ensure_ascii=False, indent=2) + "\n").encode()
        self.assertEqual(self.fixture["auditedMetadataSha256"],
                         hashlib.sha256(original_bytes).hexdigest())
        for exercise_id, value in self.metadata.items():
            self.assertEqual(digest(value), self.entries[exercise_id]["metadataSha256"])
            self.assertEqual(digest(pre_disposition_proposal(value)),
                             self.entries[exercise_id]["auditedProposalSha256"])

    def test_original_evidence_authors_corrections_and_graph_history_are_not_rewritten(self):
        keys = (
            "reviewedBy", "illustrationObservation", "illustrationSources", "illustrationInspection",
            "evidence", "corrections", "graphDecisions", "graphReviewedBy",
            "supersededGraphNarrative", "supersededClassificationReason", "humanSignoff",
        )
        preserved = {
            exercise_id: {key: copy.deepcopy(entry[key]) for key in keys if key in entry}
            for exercise_id, entry in self.entries.items()
        }
        for value in preserved.values():
            for decision in value["graphDecisions"]:
                decision.pop("sourceMetadataSha256", None)
                decision.pop("targetMetadataSha256", None)
        self.assertEqual("812148b7892962368c6676bbfd9f2875cf9f8837c8450dc76bafbe31e6fbba7b",
                         digest(preserved))

    def test_seven_former_graph_only_holds_keep_acceptance_and_pending_endpoint_edges(self):
        for exercise_id in GRAPH_ONLY_SOURCES:
            self.assertEqual("ai_accepted", self.metadata[exercise_id]["reviewState"], exercise_id)
        edges = {
            (exercise_id, field, link["exerciseId"])
            for exercise_id, value in self.metadata.items()
            for field in ("approvedRegressions", "approvedSubstitutions")
            for link in value[field]
        }
        self.assertEqual(85, len(edges))
        accepted_edges = {edge for edge in edges if edge[0] in self.accepted}
        self.assertEqual(83, len(accepted_edges))
        self.assertEqual(PENDING_ENDPOINT_EDGES,
                         {edge for edge in accepted_edges if edge[2] not in self.accepted})
        self.assertEqual(78, sum(edge[2] in self.accepted for edge in accepted_edges))
        for source, field, target in PENDING_ENDPOINT_EDGES:
            self.assertEqual("draft", self.metadata[target]["reviewState"])
            self.assertTrue(next(link["rationale"] for link in self.metadata[source][field]
                                 if link["exerciseId"] == target))

    def test_four_intrinsic_holds_remain_pending_with_their_audit_decisions(self):
        for exercise_id in INTRINSIC_HOLDS:
            entry = self.entries[exercise_id]
            self.assertEqual("pending_evidence_or_policy", entry["disposition"])
            self.assertEqual("draft", self.metadata[exercise_id]["reviewState"])
            self.assertNotIn("aiReviewProvenance", self.metadata[exercise_id])
            expected = "C2" if exercise_id == "fire-hydrant" else "C1"
            self.assertEqual(expected, entry["auditHold"]["correction"])
            self.assertTrue(entry["auditHold"]["requiredDecision"])
            self.assertIn(entry["auditHold"]["requiredDecision"], entry["remainingDecisions"])

    def test_generated_catalog_preserves_exact_provenance_and_every_metadata_block(self):
        actual = {
            value["id"]: value["reviewedMetadata"] for value in self.catalog["exercises"]
            if value.get("reviewedMetadata") is not None
        }
        self.assertEqual(self.metadata, actual)
        self.assertEqual(self.accepted, {
            exercise_id for exercise_id, value in actual.items()
            if value["reviewState"] == "ai_accepted"
        })
        self.assertEqual(self.pending_drafts, {
            exercise_id for exercise_id, value in actual.items()
            if value["reviewState"] == "draft"
        })


if __name__ == "__main__":
    unittest.main()

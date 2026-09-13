"""The acceptance decision must be reproducible without a private session artifact."""

import copy
import hashlib
import importlib
import json
from pathlib import Path
import unittest

from import_catalog import _read_object
from render_review_packet import render_packet


ROOT = Path(__file__).resolve().parents[2]
AUDIT_PATH = "docs/research/2026-09-13-ai-acceptance-audit.json"
LEDGER_PATH = "docs/research/2026-09-07-full-exercise-catalog-review.json"
FIXTURE_PATH = "app/src/test/resources/ai-acceptance/partition-v2.json"


def canonical_bytes(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode()


def compact_digest(value):
    return hashlib.sha256(json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode()).hexdigest()


class VerifyAiAcceptanceAuditTest(unittest.TestCase):
    def setUp(self):
        self.assertTrue((ROOT / AUDIT_PATH).is_file(),
                        "The complete decision audit must be committed, not just its external hash.")
        self.raw = (ROOT / AUDIT_PATH).read_bytes()
        self.audit = json.loads(self.raw)
        self.ledger = _read_object(ROOT / LEDGER_PATH, "ledger")
        self.metadata = _read_object(ROOT / "tools/workout-guide/reviewed-metadata.json", "metadata")
        self.catalog = _read_object(ROOT / "app/src/main/assets/workout-guide/catalog.json", "catalog")
        self.fixture = _read_object(ROOT / FIXTURE_PATH, "partition")
        self.verifier = importlib.import_module("verify_ai_acceptance_audit")

    def verify(self):
        return self.verifier.verify_audit(
            self.raw, self.ledger, self.metadata, self.catalog, self.fixture
        )

    def rebind_changed_audit(self):
        self.audit["reportCanonicalSha256"] = compact_digest(self.audit["report"])
        self.raw = canonical_bytes(self.audit)
        reference = {"path": AUDIT_PATH, "sha256": hashlib.sha256(self.raw).hexdigest()}
        self.fixture["auditArtifact"] = reference
        self.ledger["review"]["aiAcceptanceReview"]["auditArtifact"] = reference

    def test_full_original_report_is_canonical_bounded_and_free_of_private_paths(self):
        self.assertLessEqual(len(self.raw), 64 * 1024)
        self.assertEqual(canonical_bytes(self.audit), self.raw)
        self.assertEqual("f7601e48f77f6d03ff0efe2f75dfac6ee5605074b18f1fbe8da47ee16edb3c61",
                         self.audit["sourceAuditSha256"])
        # Pin the entire parsed source report, including prose, corrections and historical limits.
        self.assertEqual("78ea7058c01e779303dd67daffe64774fb2286a9713f2dfffe3c9a11b90f9091",
                         compact_digest(self.audit["report"]))
        self.assertEqual("gpt-6-astra", self.audit["reviewerModelId"])
        self.assertEqual(1789278919309, self.audit["reviewedAtEpochMillis"])
        self.assertIn("mtime", self.audit["timestampSource"])
        for marker in ("/Users/", "/home/", "/tmp/", "/var/folders/", "file://", "AppData\\"):
            self.assertNotIn(marker, self.raw.decode())

    def test_verifier_derives_the_partition_and_binds_all_proposals_without_mutation(self):
        before = copy.deepcopy((self.raw, self.ledger, self.metadata, self.catalog, self.fixture))

        result = self.verify()

        self.assertEqual(result, self.verify())
        self.assertEqual({"ai_accepted": 182, "pending_evidence_or_policy": 85,
                          "outside_automatic_strength_scope": 35}, result["counts"])
        self.assertEqual(sorted(self.fixture["aiAccepted"]), result["acceptedIds"])
        self.assertEqual(sorted(self.fixture["pendingWithDraft"] + self.fixture["pendingWithoutMetadata"]),
                         result["pendingIds"])
        self.assertEqual(sorted(self.fixture["outsideScope"]), result["outsideIds"])
        self.assertEqual(211, result["auditedProposalsVerified"])
        self.assertEqual(hashlib.sha256(self.raw).hexdigest(), result["auditSha256"])
        self.assertEqual(before, (self.raw, self.ledger, self.metadata, self.catalog, self.fixture))

    def test_rejects_stale_artifact_hash(self):
        self.raw += b" "
        with self.assertRaises(ValueError):
            self.verify()

    def test_rejects_graph_closure_policy_even_when_artifact_references_are_rehashed(self):
        self.audit["report"]["candidatePolicy"]["requiresAcceptedRelationshipEndpointsForSourceAcceptance"] = True
        self.rebind_changed_audit()
        with self.assertRaisesRegex(ValueError, "endpoint acceptance"):
            self.verify()

    def test_rejects_candidate_ids_that_do_not_follow_the_intrinsic_hold_criteria(self):
        self.audit["report"]["candidate_accepted"]["withExistingProgramming"].remove("push-up")
        self.rebind_changed_audit()
        with self.assertRaisesRegex(ValueError, "candidate partition"):
            self.verify()

    def test_rejects_intrinsic_holds_without_their_actual_reason_and_correction(self):
        self.audit["report"]["pending_withheld"]["additionalAuditHolds"]["fire-hydrant"]["correction"] = "missing"
        self.rebind_changed_audit()
        with self.assertRaisesRegex(ValueError, "hold correction"):
            self.verify()

    def test_rejects_a_changed_proposal_even_when_catalog_and_current_digests_match(self):
        value = self.metadata["exercises"]["push-up"]
        value["supportRequirement"] = "supported"
        entry = next(entry for entry in self.ledger["entries"] if entry["id"] == "push-up")
        entry["metadataSha256"] = compact_digest(value)
        next(entry for entry in self.catalog["exercises"] if entry["id"] == "push-up")[
            "reviewedMetadata"
        ] = copy.deepcopy(value)
        with self.assertRaisesRegex(ValueError, "audited proposal"):
            self.verify()

    def test_rejects_a_changed_audited_proposal_digest(self):
        entry = next(entry for entry in self.ledger["entries"] if entry["id"] == "push-up")
        entry["auditedProposalSha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "audited proposal"):
            self.verify()

    def test_rejects_inconsistent_model_identity_or_original_decision_timestamp(self):
        for target, field, replacement in (
            ("ledger", "reviewerModelId", "different-model"),
            ("fixture", "auditMtimeEpochMillis", 1789278919310),
            ("metadata", "reviewedAtEpochMillis", 1789278919310),
        ):
            with self.subTest(target=target):
                ledger = copy.deepcopy(self.ledger)
                fixture = copy.deepcopy(self.fixture)
                metadata = copy.deepcopy(self.metadata)
                destination = (
                    ledger["review"]["aiAcceptanceReview"] if target == "ledger" else
                    fixture if target == "fixture" else
                    metadata["exercises"]["push-up"]["aiReviewProvenance"]
                )
                destination[field] = replacement
                with self.assertRaises(ValueError):
                    self.verifier.verify_audit(self.raw, ledger, metadata, self.catalog, fixture)

    def test_rejects_malformed_oversized_and_nonportable_audit_documents(self):
        for raw in (
            b'{"schemaVersion":1,"schemaVersion":1}',
            b'{"value":NaN}',
            b"\xff",
            b" " * (64 * 1024 + 1),
        ):
            with self.subTest(raw_prefix=raw[:30]):
                with self.assertRaises(ValueError):
                    self.verifier.parse_audit(raw)
        self.audit["preservationNote"] += " Copied from /Users/private/source.json."
        with self.assertRaisesRegex(ValueError, "filesystem path"):
            self.verifier.parse_audit(canonical_bytes(self.audit))

    def test_generated_worksheet_links_to_inspectable_committed_decision_evidence(self):
        rendered = render_packet(self.ledger, self.metadata["exercises"])
        self.assertIn("(research/2026-09-13-ai-acceptance-audit.json)", rendered)
        self.assertIn(hashlib.sha256(self.raw).hexdigest(), rendered)
        self.assertIn("1789278919309", rendered)
        self.assertIn("gpt-6-astra", rendered)
        self.assertIn("python3 tools/workout-guide/verify_ai_acceptance_audit.py", rendered)
        self.assertEqual(rendered, (ROOT / "docs/reviewed-exercise-metadata-human-signoff.md").read_text())


if __name__ == "__main__":
    unittest.main()

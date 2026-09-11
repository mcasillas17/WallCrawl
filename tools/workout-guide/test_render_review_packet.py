import copy
import hashlib
import json
import unittest

from render_review_packet import classification_reason, graph_assessments, render_packet


class RenderReviewPacketTest(unittest.TestCase):
    def setUp(self):
        self.metadata = {"example-press": {
            "reviewState": "draft",
            "directPrimaryMuscle": "Chest",
            "clearedTrainingConstraints": [],
            "provenance": {"reviewerRole": None, "reviewedAtEpochMillis": None},
        }}
        digest = hashlib.sha256(json.dumps(
            self.metadata["example-press"], sort_keys=True,
            separators=(",", ":"), ensure_ascii=False,
        ).encode()).hexdigest()
        self.ledger = {
            "source": {"commit": "a" * 40},
            "entries": [
                {"id": "example-press", "disposition": "ready_for_human_review",
                 "metadataSha256": digest, "automaticStrengthClassification": "supported",
                 "remainingDecisions": ["Human inspection of this draft."],
                 "artworkReferenceStatus": "no_identity_conflict_observed",
                 "artworkReferenceReason": "Named press and source illustrations agree."},
                {"id": "example-stretch", "disposition": "outside_automatic_strength_scope",
                 "metadataSha256": None, "automaticStrengthClassification": "excluded_stretch",
                 "remainingDecisions": ["No automatic prescription; manual use remains."],
                 "artworkReferenceStatus": "do_not_use_until_reconciled",
                 "artworkReferenceReason": "Source illustration depicts a different movement."},
            ],
        }

    def test_clearance_summary_is_derived_from_the_metadata_not_asserted(self):
        """The worksheet a human signs must not carry a hardcoded claim about clearances."""
        self.assertIn("No record below lists any.", render_packet(self.ledger, self.metadata))

        cleared = copy.deepcopy(self.metadata)
        cleared["example-press"]["clearedTrainingConstraints"] = ["knee_sensitive"]
        digest = hashlib.sha256(json.dumps(
            cleared["example-press"], sort_keys=True,
            separators=(",", ":"), ensure_ascii=False,
        ).encode()).hexdigest()
        ledger = copy.deepcopy(self.ledger)
        ledger["entries"][0]["metadataSha256"] = digest

        result = render_packet(ledger, cleared)

        self.assertNotIn("No record below lists any.", result)
        self.assertIn("1 of 1 records list at least one: `example-press`.", result)

    def test_packet_keeps_content_readiness_cohort_and_approval_separate(self):
        ledger_before = copy.deepcopy(self.ledger)
        metadata_before = copy.deepcopy(self.metadata)
        result = render_packet(self.ledger, self.metadata)
        self.assertEqual(result, render_packet(self.ledger, self.metadata))
        self.assertIn("Catalog entries examined: **2**", result)
        self.assertIn("AI-ready for human inspection: **1**", result)
        self.assertIn("Outside automatic-strength scope: **1**", result)
        self.assertIn("Human-approved metadata: **0**", result)
        self.assertIn("| `example-press` | ready_for_human_review | Chest | Pending |", result)
        self.assertIn("| `example-stretch` | outside_automatic_strength_scope | Not allocated | N/A |", result)
        self.assertIn("Unsuitable as references for new illustrations until reconciled", result)
        self.assertIn("| `example-stretch` | Source illustration depicts a different movement. |", result)
        self.assertEqual(ledger_before, self.ledger)
        self.assertEqual(metadata_before, self.metadata)

    def test_refuses_stale_review_or_unrecorded_human_approval(self):
        for changed in (
            dict(self.metadata["example-press"], directPrimaryMuscle="Triceps"),
            dict(self.metadata["example-press"], reviewState="approved"),
        ):
            with self.subTest(changed=changed):
                with self.assertRaises(ValueError):
                    render_packet(self.ledger, {"example-press": changed})

    def test_pending_decision_text_cannot_break_table_rows(self):
        self.ledger["entries"][0]["disposition"] = "pending_evidence_or_policy"
        self.ledger["entries"][0]["remainingDecisions"] = ["Choose A | B\nwith real sign-off."]
        result = render_packet(self.ledger, self.metadata)
        self.assertIn("Choose A \\| B with real sign-off.", result)
        self.assertIn("Pending evidence or policy decisions: **1**", result)

    def test_current_adjacency_follows_emitted_edges_not_superseded_prose(self):
        metadata = {
            "source": {"approvedRegressions": [{"exerciseId": "target"}], "approvedSubstitutions": []},
            "target": {"approvedRegressions": [], "approvedSubstitutions": []},
        }
        entries = [
            {"id": "source", "graphDecisions": [{"action": "add"}]},
            {"id": "target", "graphDecisions": []},
        ]
        current = graph_assessments(metadata, entries)
        self.assertIn("regression -> target", current["source"])
        self.assertIn("source (regression)", current["target"])
        metadata["source"]["approvedRegressions"] = []
        entries[0]["graphDecisions"][0]["action"] = "hold"
        updated = graph_assessments(metadata, entries)
        self.assertNotIn("source (regression)", updated["target"])
        self.assertIn("hold=1", updated["source"])

    def test_type_classification_never_implies_a_metadata_block_or_approval(self):
        entry = {"automaticStrengthClassification": "supported", "sourceType": "bodyweight_reps"}
        reason = classification_reason(entry)
        self.assertIn("type boundary", reason)
        self.assertIn("not metadata completeness", reason)
        self.assertNotIn("block is authored", reason)
        entry["metadataReviewState"] = "absent"
        self.assertEqual(reason, classification_reason(entry))


if __name__ == "__main__":
    unittest.main()

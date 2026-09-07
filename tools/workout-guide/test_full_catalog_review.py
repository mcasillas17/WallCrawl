"""Integrity checks for the authored evidence ledger, not content approval."""

import hashlib
import json
from collections import Counter
from pathlib import Path
import unittest
from urllib.parse import urlsplit

from import_catalog import _read_object
from render_review_packet import classification_reason, graph_assessments, render_packet


ROOT = Path(__file__).resolve().parents[2]
LEDGER = ROOT / "docs/research/2026-09-07-full-exercise-catalog-review.json"
BUNDLE = ROOT / "app/src/main/assets/workout-guide"


class FullCatalogReviewTest(unittest.TestCase):
    def setUp(self):
        self.assertTrue(LEDGER.is_file(), "The full per-ID evidence ledger must be authored.")
        self.ledger = _read_object(LEDGER, "full catalog review")
        self.catalog = _read_object(BUNDLE / "catalog.json", "catalog")
        self.reviewed = _read_object(
            ROOT / "tools/workout-guide/reviewed-metadata.json", "reviewed metadata"
        )["exercises"]
        self.entries = self.ledger["entries"]

    def test_published_disposition_and_approval_accounting_is_pinned(self):
        self.assertEqual({
            "ready_for_human_review": 186,
            "pending_evidence_or_policy": 81,
            "outside_automatic_strength_scope": 35,
        }, Counter(entry["disposition"] for entry in self.entries))
        pending = [entry for entry in self.entries
                   if entry["disposition"] == "pending_evidence_or_policy"]
        self.assertEqual({"absent": 56, "draft": 25},
                         Counter(entry["metadataReviewState"] for entry in pending))
        self.assertEqual({
            "do_not_use_until_reconciled": 35,
            "no_identity_conflict_observed": 267,
        }, Counter(entry["artworkReferenceStatus"] for entry in self.entries))
        self.assertEqual({"draft": 211},
                         Counter(value["reviewState"] for value in self.reviewed.values()))
        self.assertEqual(0, sum(entry["humanSignoff"] is not None for entry in self.entries))

    def test_named_image_conflicts_and_the_trap_bar_retraction_remain_explicit(self):
        records = {entry["id"]: entry for entry in self.entries}
        for exercise_id in ("rack-pull", "commando-pull-up",
                            "reverse-hyperextension", "skater-squat"):
            entry = records[exercise_id]
            self.assertEqual("pending_evidence_or_policy", entry["disposition"])
            self.assertEqual("absent", entry["metadataReviewState"])
            self.assertEqual("do_not_use_until_reconciled", entry["artworkReferenceStatus"])
        trap = records["trap-bar-deadlift"]
        self.assertEqual("pending_evidence_or_policy", trap["disposition"])
        self.assertEqual("absent", trap["metadataReviewState"])
        self.assertEqual("no_identity_conflict_observed", trap["artworkReferenceStatus"])

    def test_every_current_id_has_one_individual_review_with_exact_source_facts(self):
        config = _read_object(ROOT / "tools/workout-guide/import-config.json", "config")
        manifest = json.loads((BUNDLE / "upstream-manifest.json").read_text())
        originals = {entry["id"]: entry for entry in manifest}
        catalog_by_id = {entry["id"]: entry for entry in self.catalog["exercises"]}
        ids = [entry["id"] for entry in self.entries]
        self.assertEqual(config["expectedExerciseCount"], len(ids))
        self.assertEqual(len(ids), len(set(ids)), "Duplicate per-ID review")
        self.assertEqual(sorted(catalog_by_id), ids)
        self.assertEqual(1, self.ledger["schemaVersion"])
        self.assertEqual(config["sourceRepository"], self.ledger["source"]["repository"])
        self.assertEqual(config["sourceCommit"], self.ledger["source"]["commit"])
        self.assertEqual(
            hashlib.sha256((BUNDLE / "upstream-manifest.json").read_bytes()).hexdigest(),
            self.ledger["source"]["manifestSha256"],
        )
        for entry in self.entries:
            with self.subTest(exercise=entry["id"]):
                exercise = catalog_by_id[entry["id"]]
                original = originals[exercise["sourceId"]]
                self.assertEqual(exercise["sourceId"], entry["sourceId"])
                self.assertEqual(original["slug"], entry["sourceSlug"])
                self.assertEqual(original["exerciseType"], entry["sourceType"])
                self.assertEqual(original["equipment"], entry["sourceEquipment"])
                self.assertEqual(original["primaryMuscle"], entry["sourcePrimary"])
                self.assertEqual(original["secondaryMuscles"], entry["sourceSecondary"])
                self.assertIs(original["isStretch"], entry["isStretch"])
                self.assertEqual([
                    str(Path(config["assetBasePath"]) / Path(frame["path"]).with_suffix(".png"))
                    for frame in original["frames"]
                ], entry["sourceFramePaths"])
                self.assertEqual([
                    str(Path("app/src/main/assets/workout-guide") / frame["path"])
                    for frame in original["frames"]
                ], entry["bundledFramePaths"])
                for frame in entry["bundledFramePaths"]:
                    self.assertTrue((ROOT / frame).is_file())

    def test_evidence_readiness_and_human_approval_remain_distinct(self):
        for entry in self.entries:
            with self.subTest(exercise=entry["id"]):
                self.assertIn(entry["disposition"], {
                    "ready_for_human_review",
                    "pending_evidence_or_policy",
                    "outside_automatic_strength_scope",
                })
                self.assertTrue(entry["reviewedBy"].startswith("AI: "))
                self.assertIn(entry["confidence"], {"high", "moderate", "low"})
                self.assertTrue(entry["remainingDecisions"])
                self.assertIsNone(entry["humanSignoff"])
                self.assertIn(entry["artworkReferenceStatus"], {
                    "no_identity_conflict_observed", "do_not_use_until_reconciled"
                })
                self.assertGreater(len(entry["artworkReferenceReason"].strip()), 20)
                if entry["artworkReferenceStatus"] == "do_not_use_until_reconciled":
                    self.assertNotEqual("ready_for_human_review", entry["disposition"])
                metadata = self.reviewed.get(entry["id"])
                if metadata is None:
                    self.assertNotEqual("ready_for_human_review", entry["disposition"])
                    self.assertEqual("absent", entry["metadataReviewState"])
                    self.assertIsNone(entry["metadataSha256"])
                    continue
                self.assertEqual("draft", entry["metadataReviewState"])
                self.assertEqual("draft", metadata["reviewState"])
                self.assertIsNone(metadata["provenance"]["reviewerRole"])
                self.assertIsNone(metadata["provenance"]["reviewedAtEpochMillis"])
                self.assertIn("AI", metadata["provenance"]["rationaleOrSource"])
                digest = hashlib.sha256(json.dumps(
                    metadata, sort_keys=True, separators=(",", ":"), ensure_ascii=False
                ).encode()).hexdigest()
                self.assertEqual(digest, entry["metadataSha256"], "Review is stale after metadata edit")

    def test_out_of_cohort_entries_are_reviewed_without_manufactured_strength_metadata(self):
        catalog_by_id = {entry["id"]: entry for entry in self.catalog["exercises"]}
        counts = {}
        for entry in self.entries:
            exercise = catalog_by_id[entry["id"]]
            if exercise["isStretch"]:
                expected = "excluded_stretch"
            elif exercise["exerciseType"] == "distance_duration":
                expected = "excluded_distance_duration"
            elif exercise["exerciseType"] == "duration" and "Cardio" in (
                exercise["primaryMuscles"] + exercise["secondaryMuscles"]
            ):
                expected = "excluded_timed_conditioning"
            else:
                expected = "supported"
            counts[expected] = counts.get(expected, 0) + 1
            with self.subTest(exercise=entry["id"]):
                self.assertEqual(expected, entry["automaticStrengthClassification"])
                self.assertGreater(len(entry["classificationReason"].strip()), 15)
                if expected != "supported":
                    self.assertNotIn(entry["id"], self.reviewed)
                    self.assertEqual("outside_automatic_strength_scope", entry["disposition"])
                    self.assertEqual("NO_STRENGTH_CANDIDATES", entry["legacySingleCandidateOutcome"])
                    self.assertIsNone(entry["legacySingleCandidateSplit"])
                else:
                    self.assertNotEqual("outside_automatic_strength_scope", entry["disposition"])
                    self.assertEqual("SELECTED", entry["legacySingleCandidateOutcome"])
                    self.assertIn(entry["legacySingleCandidateSplit"], {
                        "PUSH", "PULL", "LEGS", "UPPER_BODY", "LOWER_BODY", "FULL_BODY"
                    })
        self.assertEqual({
            "supported": 267,
            "excluded_stretch": 14,
            "excluded_distance_duration": 10,
            "excluded_timed_conditioning": 11,
        }, counts)

    def test_every_review_has_observations_field_group_sources_and_explicit_limits(self):
        observations = []
        for entry in self.entries:
            with self.subTest(exercise=entry["id"]):
                observation = entry["illustrationObservation"].strip()
                self.assertGreater(len(observation), 40)
                observations.append(observation)
                self.assertTrue(entry["corrections"])
                self.assertTrue(entry["limitations"])
                self.assertEqual({
                    "muscles", "movement", "equipmentAndCapabilities", "links"
                }, set(entry["policyAssessment"]))
                for value in entry["policyAssessment"].values():
                    self.assertGreater(len(value.strip()), 20)
                self.assertGreaterEqual(len(entry["evidence"]), 2)
                for evidence in entry["evidence"]:
                    self.assertTrue(evidence["fields"])
                    self.assertGreater(len(evidence["observation"].strip()), 20)
                    self.assertGreater(len(evidence["interpretation"].strip()), 20)
                    self.assertFalse(any(character.isspace() for character in evidence["source"]),
                                     "Each citation must be one navigable URL")
                    source = urlsplit(evidence["source"])
                    self.assertEqual("https", source.scheme)
                    self.assertTrue(source.hostname)
                    self.assertIsNone(source.username)
                    self.assertIsNone(source.password)
        self.assertEqual(len(observations), len(set(observations)),
                         "Per-entry observations must not be anonymous bulk rows")

    def test_generated_signoff_matches_the_metadata_bound_review(self):
        self.assertEqual(
            render_packet(self.ledger, self.reviewed),
            (ROOT / "docs/reviewed-exercise-metadata-human-signoff.md").read_text(),
        )

    def test_current_graph_adjacency_cannot_retain_superseded_endpoints(self):
        expected = graph_assessments(self.reviewed, self.entries)
        for entry in self.entries:
            self.assertEqual(expected[entry["id"]], entry["policyAssessment"]["links"],
                             entry["id"])

    def test_classification_reason_stays_independent_of_metadata_presence(self):
        for entry in self.entries:
            self.assertEqual(classification_reason(entry), entry["classificationReason"], entry["id"])

    def test_incident_image_citations_keep_their_own_exercise_observations(self):
        records = {entry["id"]: entry for entry in self.entries}
        machine = records["machine-row"]
        for exercise_id in ("dumbbell-bent-over-row", "seated-row"):
            evidence = next(item for item in machine["evidence"]
                            if item["source"].endswith("/assets/" + exercise_id))
            self.assertEqual(exercise_id, evidence["citedExerciseId"])
            self.assertEqual(records[exercise_id]["illustrationObservation"], evidence["observation"])
        for item in machine["evidence"]:
            self.assertNotIn("when the pictures show a cable", item["interpretation"])
        movement = records["t-bar-row"]["policyAssessment"]["movement"]
        self.assertIn("unsupported", movement)
        self.assertIn("balance_without_support", movement)
        for decision in records["weighted-crunch"]["graphDecisions"]:
            self.assertNotIn("Bench/Box", decision["rationale"])

    def test_every_emitted_edge_has_a_current_endpoint_bound_review_decision(self):
        records = {entry["id"]: entry for entry in self.entries}
        decision_count = 0
        for entry in self.entries:
            with self.subTest(exercise=entry["id"]):
                self.assertIn("graphDecisions", entry)
                decisions = entry["graphDecisions"]
                decision_count += len(decisions)
                identities = [(decision["kind"], decision["targetId"]) for decision in decisions]
                self.assertEqual(len(identities), len(set(identities)))
                emitted = set()
                for decision in decisions:
                    self.assertIn(decision["action"], {"add", "retain", "hold", "reject"})
                    self.assertIn(decision["kind"], {"regression", "substitution"})
                    self.assertGreater(len(decision["rationale"]), 30)
                    self.assertEqual(entry["metadataSha256"], decision["sourceMetadataSha256"])
                    target = records.get(decision["targetId"])
                    self.assertEqual(target["metadataSha256"] if target else None,
                                     decision["targetMetadataSha256"])
                    source_metadata = self.reviewed.get(entry["id"])
                    target_metadata = self.reviewed.get(decision["targetId"])
                    if source_metadata and target_metadata:
                        source_caps = set(source_metadata["capabilityRequirements"])
                        target_caps = set(target_metadata["capabilityRequirements"])
                        self.assertEqual(sorted(target_caps - source_caps),
                                         decision["comparison"]["targetCapabilitiesAdded"])
                        self.assertEqual(sorted(source_caps - target_caps),
                                         decision["comparison"]["targetCapabilitiesRemoved"])
                    if decision["action"] in {"add", "retain"}:
                        self.assertIsNotNone(target)
                        self.assertIn(decision["targetId"], self.reviewed)
                        emitted.add((decision["kind"], decision["targetId"]))
                        if decision["action"] == "add":
                            self.assertEqual("ready_for_human_review", target["disposition"])
                metadata = self.reviewed.get(entry["id"])
                actual = {
                    (kind, link["exerciseId"])
                    for kind, field in (
                        ("regression", "approvedRegressions"),
                        ("substitution", "approvedSubstitutions"),
                    )
                    for link in (metadata[field] if metadata else [])
                }
                self.assertEqual(actual, emitted)
        self.assertEqual(265, decision_count)


if __name__ == "__main__":
    unittest.main()

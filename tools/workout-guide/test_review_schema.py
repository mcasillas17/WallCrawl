"""The authored schema must reject impossible state/provenance combinations by itself.

The importer and the Android parser reject them too, but a schema that accepts them lets
any other reader of `review-schema.json` treat an AI acceptance as a human approval, or
read an AI contract out of a document authored against the older version.
"""

import json
import unittest
from pathlib import Path

from import_catalog import CatalogImportError, _validate_json_schema


SCHEMA = json.loads(Path(__file__).with_name("review-schema.json").read_text())

AI_PROVENANCE = {
    "reviewerModelId": "test-model-1",
    "reviewedAtEpochMillis": 1_756_000_000_000,
    "reviewedContentId": "barbell-bench-press",
    "reviewedContentSha256": "a" * 64,
    "sourceReferences": ["https://example.test/reference"],
    "decisionRationale": "Alpha AI acceptance for owner-authorized planning only.",
    "limitations": "Not a human review and not clinical clearance.",
    "schemaVersion": 3,
    "policyVersion": 1,
}


def entry(schema_version: int = 2, review_state: str = "draft", **overrides) -> dict:
    value = {
        "reviewState": review_state,
        "directPrimaryMuscle": "Chest",
        "descriptiveSecondaryMuscles": ["Shoulders", "Triceps"],
        "movementPattern": "horizontal_push",
        "complexity": "standard",
        "progressionFamily": "barbell-horizontal-push",
        "prescriptionShape": "weight_reps",
        "approvedRegressions": [],
        "approvedSubstitutions": [],
        "capabilityRequirements": [],
        "supportRequirement": "supported",
        "impactLevel": "none",
        "equipmentAlternatives": [["Barbell", "Bench"]],
        "clearedTrainingConstraints": [],
        "provenance": {
            "reviewerRole": None,
            "rationaleOrSource": "Authored rationale.",
            "reviewedAtEpochMillis": None,
            "schemaVersion": schema_version,
            "policyVersion": 1,
        },
    }
    if review_state == "approved":
        value["provenance"]["reviewerRole"] = "Certified strength coach"
        value["provenance"]["reviewedAtEpochMillis"] = 1_756_000_000_000
    if review_state == "ai_accepted":
        value["aiReviewProvenance"] = json.loads(json.dumps(AI_PROVENANCE))
    for key, override in overrides.items():
        value[key] = override
    return value


def document(schema_version: int = 2, **entry_arguments) -> dict:
    entry_arguments.setdefault("schema_version", schema_version)
    return {
        "schemaVersion": schema_version,
        "exercises": {"barbell-bench-press": entry(**entry_arguments)},
    }


class ReviewSchemaTest(unittest.TestCase):
    def assertSchemaAccepts(self, value: dict) -> None:
        _validate_json_schema(value, SCHEMA, SCHEMA, "reviewed metadata", 0)

    def assertSchemaRejects(self, value: dict, fragment: str) -> None:
        with self.assertRaises(CatalogImportError) as caught:
            _validate_json_schema(value, SCHEMA, SCHEMA, "reviewed metadata", 0)
        self.assertIn(fragment, str(caught.exception))

    def test_accepts_the_authored_v2_draft_contract(self) -> None:
        self.assertSchemaAccepts(document(2))

    def test_accepts_a_v3_draft_that_uses_no_ai_contract(self) -> None:
        self.assertSchemaAccepts(document(3))

    def test_accepts_a_human_approved_entry_in_either_version(self) -> None:
        for schema_version in (2, 3):
            with self.subTest(schemaVersion=schema_version):
                self.assertSchemaAccepts(document(schema_version, review_state="approved"))

    def test_accepts_a_v3_ai_accepted_entry_with_ai_provenance(self) -> None:
        self.assertSchemaAccepts(document(3, review_state="ai_accepted"))

    def test_rejects_ai_accepted_without_ai_provenance(self) -> None:
        value = document(3, review_state="ai_accepted")
        del value["exercises"]["barbell-bench-press"]["aiReviewProvenance"]

        self.assertSchemaRejects(value, "aiReviewProvenance")

    def test_rejects_ai_accepted_claiming_a_human_reviewer(self) -> None:
        value = document(3, review_state="ai_accepted")
        value["exercises"]["barbell-bench-press"]["provenance"]["reviewerRole"] = "Coach"

        self.assertSchemaRejects(value, "reviewerRole")

    def test_rejects_ai_accepted_claiming_a_human_review_time(self) -> None:
        value = document(3, review_state="ai_accepted")
        provenance = value["exercises"]["barbell-bench-press"]["provenance"]
        provenance["reviewedAtEpochMillis"] = 1_756_000_000_000

        self.assertSchemaRejects(value, "reviewedAtEpochMillis")

    def test_rejects_ai_provenance_on_human_reviewed_states(self) -> None:
        for review_state in ("draft", "approved"):
            with self.subTest(reviewState=review_state):
                value = document(3, review_state=review_state)
                value["exercises"]["barbell-bench-press"]["aiReviewProvenance"] = json.loads(
                    json.dumps(AI_PROVENANCE)
                )

                self.assertSchemaRejects(value, "aiReviewProvenance")

    def test_rejects_the_ai_contract_inside_a_v2_document(self) -> None:
        value = document(2, review_state="ai_accepted")
        value["exercises"]["barbell-bench-press"]["provenance"]["schemaVersion"] = 2

        self.assertSchemaRejects(value, "reviewState")

    def test_rejects_ai_provenance_inside_a_v2_document(self) -> None:
        value = document(2)
        value["exercises"]["barbell-bench-press"]["aiReviewProvenance"] = json.loads(
            json.dumps(AI_PROVENANCE)
        )

        self.assertSchemaRejects(value, "aiReviewProvenance")

    def test_rejects_provenance_version_that_disagrees_with_the_document(self) -> None:
        for document_version, provenance_version in ((2, 3), (3, 2)):
            with self.subTest(document=document_version, provenance=provenance_version):
                value = document(document_version)
                provenance = value["exercises"]["barbell-bench-press"]["provenance"]
                provenance["schemaVersion"] = provenance_version

                self.assertSchemaRejects(value, "schemaVersion")

    def test_rejects_ai_accepted_authored_against_the_older_provenance_version(self) -> None:
        value = document(3, review_state="ai_accepted")
        value["exercises"]["barbell-bench-press"]["provenance"]["schemaVersion"] = 2

        self.assertSchemaRejects(value, "schemaVersion")

    def test_rejects_approved_without_human_provenance(self) -> None:
        for field in ("reviewerRole", "reviewedAtEpochMillis"):
            with self.subTest(field=field):
                value = document(3, review_state="approved")
                value["exercises"]["barbell-bench-press"]["provenance"][field] = None

                self.assertSchemaRejects(value, field)

    def test_rejects_draft_carrying_human_provenance(self) -> None:
        for field, filled in (
            ("reviewerRole", "Coach"),
            ("reviewedAtEpochMillis", 1_756_000_000_000),
        ):
            with self.subTest(field=field):
                value = document(3)
                value["exercises"]["barbell-bench-press"]["provenance"][field] = filled

                self.assertSchemaRejects(value, field)


if __name__ == "__main__":
    unittest.main()

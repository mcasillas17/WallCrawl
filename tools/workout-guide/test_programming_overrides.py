"""Checks the legacy and reviewed-planning metadata that ships in the app.

The other suite exercises the importer against synthetic fixtures. This one checks the
real authored data, so a bad edit fails here rather than becoming a stale or unsafe
catalog artifact.
"""
import hashlib
import json
import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
CATALOG = REPO_ROOT / "app/src/main/assets/workout-guide/catalog.json"
OVERRIDES = Path(__file__).with_name("programming-overrides.json")
REVIEWED_METADATA = Path(__file__).with_name("reviewed-metadata.json")

# A bodyweight movement may be progressed by reps alone or by adding load once a weighted
# variant exists, so both are accepted; the loaded and assisted cases have one right answer.
PROGRESSION_BY_TYPE = {
    "weight_reps": {"repetitions_then_load", "load"},
    "bodyweight_reps": {"repetitions", "repetitions_then_load"},
    "assisted_bodyweight": {"assistance_reduction"},
    "duration": {"duration"},
}

# Guards against an edit that silently truncates the file. Raise it when coverage grows.
MINIMUM_REVIEWED = 131


class ProgrammingOverridesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.catalog = json.loads(CATALOG.read_text())
        cls.by_id = {e["id"]: e for e in cls.catalog["exercises"]}
        cls.overrides = json.loads(OVERRIDES.read_text())["exercises"]

    def test_exact_timed_strength_cohort_has_complete_programming(self) -> None:
        expected = {'hollow-body-hold', 'plank', 'superman-hold', 'l-sit-hold', 'copenhagen-plank', 'active-hang', 'mountain-climber', 'cable-pallof-hold', 'wall-sit', 'bear-plank', 'dead-hang', 'flutter-kick', 'crab-walk', 'side-plank'}
        derived = {e["id"] for e in self.by_id.values()
                   if e["exerciseType"] == "duration" and not e["isStretch"]
                   and "Cardio" not in e["primaryMuscles"] + e["secondaryMuscles"]}
        self.assertEqual(expected, derived)
        authored = {key for key in self.overrides
                    if self.by_id[key]["exerciseType"] == "duration"}
        self.assertEqual(expected, authored)
        for exercise_id in expected:
            self.assertIsNone(self.overrides[exercise_id]["recommendedRepRange"])
            self.assertEqual("duration", self.overrides[exercise_id]["progressionType"])
            self.assertTrue(self.overrides[exercise_id]["coachingSummary"].strip())
            self.assertTrue(self.overrides[exercise_id]["alternativeExerciseIds"])

    def test_original_117_rep_programming_records_are_unchanged(self) -> None:
        records = {key: value for key, value in self.overrides.items()
                   if self.by_id[key]["exerciseType"] not in {"duration", "distance_duration"}}
        self.assertEqual(117, len(records))
        encoded = json.dumps(records, sort_keys=True, separators=(",", ":")).encode()
        self.assertEqual("6412738d7f19d78189cdc02c204ed0d67614571ee27bdf0f95cda3c608065e13", hashlib.sha256(encoded).hexdigest())

    def test_catalog_facts_frames_and_licensing_match_pinned_baseline(self) -> None:
        facts = json.loads(CATALOG.read_text())
        for exercise in facts["exercises"]:
            exercise.pop("programming", None)
        encoded = json.dumps(facts, sort_keys=True, separators=(",", ":")).encode()
        self.assertEqual("31d3471591f37a4aa4d3b536e23c68b0f15b7ce61e454d3f57bb4cdc601acbbf", hashlib.sha256(encoded).hexdigest())
        self.assertEqual(302, len(facts["exercises"]))
        self.assertEqual("ba0b709cb20430361b2cb33aaadd20998164a916", facts["source"]["commit"])
        self.assertEqual(906, len(list(CATALOG.parent.rglob("*.svg"))))
        digest = hashlib.sha256()
        for file in sorted(CATALOG.parent.rglob("*")):
            if file.is_file() and file.name != "catalog.json":
                digest.update(file.relative_to(CATALOG.parent).as_posix().encode())
                digest.update(b"\0")
                digest.update(file.read_bytes())
        self.assertEqual("bfbb6bffc84e4b6dd58d83cc20c5d925439d88600719c9d60daa61e9c29a6cf8", digest.hexdigest())

    def test_timed_variants_include_their_required_support_equipment(self) -> None:
        expected = {
            "active-hang": [["Pull-up Bar"]],
            "dead-hang": [["Pull-up Bar"]],
            "cable-pallof-hold": [["Cable"]],
            "copenhagen-plank": [["Bodyweight", "Bench"]],
            "l-sit-hold": [["Bodyweight", "Dip Bars"]],
            "wall-sit": [["Bodyweight", "Wall"]],
        }
        for exercise_id, combinations in expected.items():
            self.assertIn(exercise_id, self.overrides)
            self.assertEqual(combinations, self.overrides[exercise_id]["requiredEquipmentCombinations"])

    def test_every_reviewed_exercise_exists_in_the_catalog(self) -> None:
        unknown = sorted(set(self.overrides) - set(self.by_id))
        self.assertEqual([], unknown)

    def test_every_alternative_exists_and_is_not_self_referential(self) -> None:
        for exercise_id, programming in self.overrides.items():
            for alternative in programming["alternativeExerciseIds"]:
                self.assertIn(alternative, self.by_id, f"{exercise_id} -> {alternative}")
                self.assertNotEqual(exercise_id, alternative)

    def test_no_programmed_exercise_is_a_stretch_or_pure_conditioning(self) -> None:
        offenders = [
            exercise_id for exercise_id in self.overrides
            if self.by_id[exercise_id].get("isStretch")
            or self.by_id[exercise_id]["exerciseType"] == "distance_duration"
            or (self.by_id[exercise_id]["exerciseType"] == "duration"
                and "Cardio" in self.by_id[exercise_id]["primaryMuscles"] + self.by_id[exercise_id]["secondaryMuscles"])
        ]
        self.assertEqual([], sorted(offenders))

    def test_progression_type_agrees_with_the_catalog_exercise_type(self) -> None:
        for exercise_id, programming in self.overrides.items():
            allowed = PROGRESSION_BY_TYPE[self.by_id[exercise_id]["exerciseType"]]
            self.assertIn(programming["progressionType"], allowed, exercise_id)

    def test_rep_ranges_are_ordered_and_plausible(self) -> None:
        for exercise_id, programming in self.overrides.items():
            rep_range = programming["recommendedRepRange"]
            if self.by_id[exercise_id]["exerciseType"] == "duration":
                self.assertIsNone(rep_range, exercise_id)
                continue
            self.assertLessEqual(rep_range["min"], rep_range["max"], exercise_id)
            self.assertGreaterEqual(rep_range["min"], 1, exercise_id)
            self.assertLessEqual(rep_range["max"], 30, exercise_id)

    def test_the_bundled_catalog_reflects_the_reviewed_metadata(self) -> None:
        # Compares content, not just which ids are reviewed: the common form of this
        # mistake is editing an entry and forgetting to re-run the importer, which leaves
        # the id set identical and ships stale data.
        reviewed_in_catalog = {
            e["id"]: e["programming"] for e in self.catalog["exercises"] if e.get("programming")
        }
        self.assertEqual(set(self.overrides), set(reviewed_in_catalog))
        for exercise_id, programming in self.overrides.items():
            self.assertEqual(programming, reviewed_in_catalog[exercise_id], exercise_id)

    def test_every_reviewed_exercise_can_actually_be_selected_by_the_planner(self) -> None:
        # Mirrors FakeWorkoutPlanner.isStrengthWork(). Reviewing an exercise the planner
        # can never choose is wasted work, and the two rules drifting apart is the kind of
        # thing that otherwise only surfaces when someone reads both by hand.
        orphaned = []
        for exercise_id in self.overrides:
            exercise = self.by_id[exercise_id]
            muscles = set(exercise["primaryMuscles"]) | set(exercise["secondaryMuscles"])
            selectable = not (
                exercise.get("isStretch")
                or exercise["exerciseType"] == "distance_duration"
                or (exercise["exerciseType"] == "duration" and "Cardio" in muscles)
            )
            if not selectable:
                orphaned.append(exercise_id)
        self.assertEqual([], sorted(orphaned))

    def test_coverage_has_not_been_truncated(self) -> None:
        self.assertGreaterEqual(len(self.overrides), MINIMUM_REVIEWED)

    def test_every_required_equipment_name_is_one_the_app_offers(self) -> None:
        # Requiring a name the Profile screen never offers would hide the exercise from
        # every user, so the vocabulary is pinned to StandardEquipment.
        source = (REPO_ROOT / "app/src/main/java/wallcrawl/elopenmike/com"
                  "/core/model/Exercise.kt").read_text()
        offered = set(re.findall(r'const val [A-Z_]+ = "([^"]+)"', source))
        self.assertIn("Barbell", offered, "failed to read StandardEquipment")
        for exercise_id, programming in self.overrides.items():
            for combination in programming["requiredEquipmentCombinations"]:
                unknown = set(combination) - offered
                self.assertEqual(set(), unknown, f"{exercise_id}: {sorted(unknown)}")


class ReviewedMetadataTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.catalog = json.loads(CATALOG.read_text())
        cls.by_id = {exercise["id"]: exercise for exercise in cls.catalog["exercises"]}
        cls.reviewed = json.loads(REVIEWED_METADATA.read_text())["exercises"]

    def test_initial_cohort_is_present_and_remains_awaiting_human_review(self) -> None:
        self.assertEqual(37, len(self.reviewed))
        for exercise_id, metadata in self.reviewed.items():
            self.assertEqual("draft", metadata["reviewState"], exercise_id)
            self.assertIsNone(metadata["provenance"]["reviewerRole"], exercise_id)
            self.assertIsNone(metadata["provenance"]["reviewedAtEpochMillis"], exercise_id)

    def test_roundtable_corrections_match_product_capability_semantics(self) -> None:
        expected_capabilities = {
            "assisted-pistol-squat": [],
            "banded-lat-pulldown": [],
            "plank": ["floor_transition"],
            "push-up": ["upper_body_bodyweight_push", "floor_transition"],
            "side-plank": ["floor_transition"],
            "smith-machine-split-squat": [],
            "split-squat": ["balance_without_support"],
        }
        expected_support = {
            "band-pull-apart": "unsupported",
            "banded-lat-pulldown": "supported",
            "banded-pallof-press": "unsupported",
            "banded-row": "unsupported",
            "cable-pallof-hold": "unsupported",
            "cable-pull-through": "unsupported",
        }
        expected_no_impact = {
            "assisted-pistol-squat",
            "banded-squat",
            "barbell-back-squat",
            "barbell-deadlift",
            "bodyweight-squat",
            "cable-pull-through",
            "dumbbell-romanian-deadlift",
            "goblet-squat",
            "kettlebell-romanian-deadlift",
            "leg-press",
            "pistol-squat",
            "smith-machine-romanian-deadlift",
            "smith-machine-split-squat",
            "split-squat",
        }

        for exercise_id, capabilities in expected_capabilities.items():
            self.assertEqual(
                capabilities,
                self.reviewed[exercise_id]["capabilityRequirements"],
                exercise_id,
            )
        for exercise_id, support in expected_support.items():
            self.assertEqual(
                support,
                self.reviewed[exercise_id]["supportRequirement"],
                exercise_id,
            )
        for exercise_id in expected_no_impact:
            self.assertEqual("none", self.reviewed[exercise_id]["impactLevel"], exercise_id)
        self.assertEqual(
            [["Resistance Band", "Chair"]],
            self.reviewed["banded-lat-pulldown"]["equipmentAlternatives"],
        )

    def test_ai_draft_rationale_preserves_provenance_boundary(self) -> None:
        pinned_commit = "ba0b709cb20430361b2cb33aaadd20998164a916"
        for exercise_id, metadata in self.reviewed.items():
            rationale = metadata["provenance"]["rationaleOrSource"]
            expected = (
                f"AI-authored DRAFT for {exercise_id}: pinned Workout Guide "
                f"{pinned_commit} manifest/artwork supports muscles, prescription "
                "shape, and equipment; WallCrawl policy supplies pattern, complexity, "
                "family, capabilities, support, impact, and graph edges. "
                "Human field-by-field review required."
            )
            self.assertEqual(expected, rationale, exercise_id)

    def test_every_authored_id_and_graph_edge_resolves(self) -> None:
        for exercise_id, metadata in self.reviewed.items():
            self.assertIn(exercise_id, self.by_id)
            for field in ("approvedRegressions", "approvedSubstitutions"):
                for edge in metadata[field]:
                    self.assertIn(edge["exerciseId"], self.by_id, f"{exercise_id}.{field}")

    def test_bundled_catalog_exactly_reflects_reviewed_metadata(self) -> None:
        reviewed_in_catalog = {
            exercise["id"]: exercise["reviewedMetadata"]
            for exercise in self.catalog["exercises"]
            if exercise.get("reviewedMetadata") is not None
        }
        self.assertEqual(self.reviewed, reviewed_in_catalog)

    def test_initial_cohort_spans_required_movements_and_equipment(self) -> None:
        movements = {metadata["movementPattern"] for metadata in self.reviewed.values()}
        self.assertTrue(
            {"core", "hinge", "horizontal_pull", "horizontal_push", "lunge", "squat", "vertical_pull"}
            <= movements
        )
        equipment = {
            item
            for metadata in self.reviewed.values()
            for combination in metadata["equipmentAlternatives"]
            for item in combination
        }
        self.assertTrue(
            {
                "Barbell", "Bodyweight", "Cable", "Dumbbell", "Kettlebell",
                "Machine", "Pull-up Bar", "Resistance Band", "Wall"
            } <= equipment
        )

    def test_catalog_count_is_unchanged_and_unreviewed_exercises_remain(self) -> None:
        self.assertEqual(302, len(self.by_id))
        self.assertEqual(302 - len(self.reviewed), sum(
            exercise.get("reviewedMetadata") is None for exercise in self.by_id.values()
        ))


if __name__ == "__main__":
    unittest.main()

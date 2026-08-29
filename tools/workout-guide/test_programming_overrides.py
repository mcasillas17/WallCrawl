"""Checks the reviewed programming metadata that ships in the app.

The other suite exercises the importer against synthetic fixtures. This one checks the
real authored data, so a bad edit to programming-overrides.json fails here rather than
becoming a workout that references an exercise nobody can perform.
"""
import json
import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
CATALOG = REPO_ROOT / "app/src/main/assets/workout-guide/catalog.json"
OVERRIDES = Path(__file__).with_name("programming-overrides.json")

# An exercise measured in distance or held for time is not prescribed with sets and reps,
# so reviewing one would put a treadmill in a hypertrophy slot.
UNPRESCRIBABLE_TYPES = {"distance_duration", "duration"}

# A bodyweight movement may be progressed by reps alone or by adding load once a weighted
# variant exists, so both are accepted; the loaded and assisted cases have one right answer.
PROGRESSION_BY_TYPE = {
    "weight_reps": {"repetitions_then_load", "load"},
    "bodyweight_reps": {"repetitions", "repetitions_then_load"},
    "assisted_bodyweight": {"assistance_reduction"},
}

# Guards against an edit that silently truncates the file. Raise it when coverage grows.
MINIMUM_REVIEWED = 100


class ProgrammingOverridesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.catalog = json.loads(CATALOG.read_text())
        cls.by_id = {e["id"]: e for e in cls.catalog["exercises"]}
        cls.overrides = json.loads(OVERRIDES.read_text())["exercises"]

    def test_every_reviewed_exercise_exists_in_the_catalog(self) -> None:
        unknown = sorted(set(self.overrides) - set(self.by_id))
        self.assertEqual([], unknown)

    def test_every_alternative_exists_and_is_not_self_referential(self) -> None:
        for exercise_id, programming in self.overrides.items():
            for alternative in programming["alternativeExerciseIds"]:
                self.assertIn(alternative, self.by_id, f"{exercise_id} -> {alternative}")
                self.assertNotEqual(exercise_id, alternative)

    def test_no_reviewed_exercise_is_a_stretch_or_measured_in_time(self) -> None:
        offenders = [
            exercise_id for exercise_id in self.overrides
            if self.by_id[exercise_id].get("isStretch")
            or self.by_id[exercise_id]["exerciseType"] in UNPRESCRIBABLE_TYPES
        ]
        self.assertEqual([], sorted(offenders))

    def test_progression_type_agrees_with_the_catalog_exercise_type(self) -> None:
        for exercise_id, programming in self.overrides.items():
            allowed = PROGRESSION_BY_TYPE[self.by_id[exercise_id]["exerciseType"]]
            self.assertIn(programming["progressionType"], allowed, exercise_id)

    def test_rep_ranges_are_ordered_and_plausible(self) -> None:
        for exercise_id, programming in self.overrides.items():
            rep_range = programming["recommendedRepRange"]
            self.assertLessEqual(rep_range["min"], rep_range["max"], exercise_id)
            self.assertGreaterEqual(rep_range["min"], 1, exercise_id)
            self.assertLessEqual(rep_range["max"], 30, exercise_id)

    def test_the_bundled_catalog_reflects_the_reviewed_metadata(self) -> None:
        # Catches an override edit that was never imported into the shipped asset.
        reviewed_in_catalog = {
            e["id"] for e in self.catalog["exercises"] if e.get("programming")
        }
        self.assertEqual(set(self.overrides), reviewed_in_catalog)

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


if __name__ == "__main__":
    unittest.main()

import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).with_name("import_catalog.py")
PARITY_FIXTURES = (
    Path(__file__).resolve().parents[2]
    / "app/src/androidTest/assets/reviewed-validation-fixtures.json"
)


class ImportCatalogTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_directory.cleanup)
        self.root = Path(self.temp_directory.name)
        self.source = self.root / "workout-guide"
        self.output = self.root / "android-assets" / "workout-guide"
        self.config = self.root / "import-config.json"
        self.overrides = self.root / "programming-overrides.json"
        self.reviewed_metadata = self.root / "reviewed-metadata.json"
        self.review_report = self.root / "reviewed-metadata-report.md"
        self.source.mkdir()
        self._write_source_fixture()
        self.source_commit = self._commit_source()
        self._write_config()
        self._write_overrides()
        self._write_reviewed_metadata()

    def test_import_generates_normalized_svg_only_bundle(self) -> None:
        result = self._run_import()

        self.assertEqual(result.returncode, 0, result.stderr)
        catalog = json.loads((self.output / "catalog.json").read_text())
        self.assertEqual(catalog["schemaVersion"], 1)
        self.assertEqual(catalog["source"]["commit"], self.source_commit)
        self.assertEqual(catalog["source"]["attribution"]["creator"], "Bryl Lim")
        self.assertEqual(
            catalog["visuals"],
            {"format": "svg", "frameCount": 3, "heightPx": 512, "widthPx": 512},
        )
        self.assertEqual(len(catalog["exercises"]), 1)

        exercise = catalog["exercises"][0]
        self.assertEqual(exercise["id"], "barbell-bench-press")
        self.assertEqual(exercise["sourceId"], "exercise-bench-press")
        self.assertEqual(exercise["sourceSlug"], "bench-press")
        self.assertEqual(exercise["searchAliases"], ["Barbell Bench Press"])
        self.assertEqual(exercise["listedEquipment"], ["Barbell"])
        self.assertEqual(exercise["primaryMuscles"], ["Chest"])
        self.assertEqual(exercise["programming"]["requiredEquipmentCombinations"], [["Barbell", "Bench"]])
        self.assertEqual(exercise["reviewedMetadata"]["reviewState"], "draft")
        self.assertEqual(exercise["reviewedMetadata"]["directPrimaryMuscle"], "Chest")
        review_report = self.review_report.read_text()
        self.assertIn("barbell-bench-press", review_report)
        self.assertIn("| `approved` | 0 |", review_report)
        self.assertIn("| `draft` | 1 |", review_report)
        self.assertNotIn("frames", exercise)
        self.assertNotIn("attribution", exercise)

        self.assertEqual(
            sorted(path.relative_to(self.output).as_posix() for path in self.output.rglob("*.svg")),
            [
                "assets/bench-press/frame-1.svg",
                "assets/bench-press/frame-2.svg",
                "assets/bench-press/frame-3.svg",
            ],
        )
        self.assertEqual(list(self.output.rglob("*.png")), [])
        self.assertEqual((self.output / "upstream-manifest.json").read_bytes(), self._manifest_path().read_bytes())
        preserved_manifest = json.loads((self.output / "upstream-manifest.json").read_text())
        self.assertEqual(preserved_manifest[0]["attribution"]["source"]["name"], "Everkinetic")
        self.assertEqual(preserved_manifest[0]["frames"][0]["attribution"]["creator"], "Bryl Lim")
        for license_name in ("LICENSE", "LICENSE-ASSETS", "ATTRIBUTION.md"):
            self.assertEqual((self.output / license_name).read_bytes(), (self.source / license_name).read_bytes())
        self.assertIn(self.source_commit, (self.output / "NOTICE.md").read_text())
        self.assertIn("exercises=1 frames=3", result.stdout)

    def test_reimport_is_deterministic_and_check_detects_drift_without_writing(self) -> None:
        first = self._run_import()
        self.assertEqual(first.returncode, 0, first.stderr)
        first_digest = self._tree_digest(self.output)
        first_report = self.review_report.read_bytes()

        second = self._run_import()
        self.assertEqual(second.returncode, 0, second.stderr)
        self.assertEqual(self._tree_digest(self.output), first_digest)
        self.assertEqual(self.review_report.read_bytes(), first_report)

        clean_check = self._run_import(check_only=True)
        self.assertEqual(clean_check.returncode, 0, clean_check.stderr)
        self.assertIn("up to date", clean_check.stdout.lower())

        catalog_path = self.output / "catalog.json"
        catalog_path.write_text(catalog_path.read_text() + "drift\n")
        drifted_digest = self._tree_digest(self.output)
        drift_check = self._run_import(check_only=True)
        self.assertNotEqual(drift_check.returncode, 0)
        self.assertIn("differs", drift_check.stderr.lower())
        self.assertEqual(self._tree_digest(self.output), drifted_digest)

        catalog_path.write_text(catalog_path.read_text().removesuffix("drift\n"))
        self.review_report.write_text(self.review_report.read_text() + "drift\n")
        drifted_report = self.review_report.read_bytes()
        report_drift_check = self._run_import(check_only=True)
        self.assertNotEqual(report_drift_check.returncode, 0)
        self.assertIn("review report differs", report_drift_check.stderr.lower())
        self.assertEqual(self.review_report.read_bytes(), drifted_report)

    def test_rejects_source_at_wrong_commit(self) -> None:
        (self.source / "README.md").write_text("new commit\n")
        self._git("add", "README.md")
        self._git("commit", "-m", "advance fixture")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("pinned commit", result.stderr.lower())

    def test_rejects_non_https_source_repository(self) -> None:
        config = json.loads(self.config.read_text())
        config["sourceRepository"] = "http://example.test/workout-guide"
        self.config.write_text(json.dumps(config, indent=2) + "\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("https url", result.stderr.lower())

    def test_rejects_dirty_imported_source_and_preserves_existing_output(self) -> None:
        first = self._run_import()
        self.assertEqual(first.returncode, 0, first.stderr)
        original_digest = self._tree_digest(self.output)
        frame_path = self.source / "packages/workout-guide/assets/bench-press/frame-1.svg"
        frame_path.write_text("<svg>dirty</svg>\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("tracked changes", result.stderr.lower())
        self.assertEqual(self._tree_digest(self.output), original_digest)

    def test_rejects_duplicate_source_ids(self) -> None:
        manifest = json.loads(self._manifest_path().read_text())
        duplicate = dict(manifest[0])
        duplicate["slug"] = "duplicate-bench-press"
        duplicate["frames"] = [dict(frame) for frame in duplicate["frames"]]
        for frame in duplicate["frames"]:
            frame["path"] = frame["path"].replace("bench-press", "duplicate-bench-press")
            duplicate_frame = self.source / "packages/workout-guide" / frame["path"]
            duplicate_frame.parent.mkdir(parents=True, exist_ok=True)
            duplicate_frame.write_text("<svg/>\n")
        manifest.append(duplicate)
        self._manifest_path().write_text(json.dumps(manifest, indent=2) + "\n")
        self._commit_fixture_change("duplicate source id")
        self._write_config(expected_exercises=2, expected_frames=6, source_commit=self._head_commit())

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("duplicate source exercise id", result.stderr.lower())

    def test_rejects_unsafe_frame_path_and_symlink(self) -> None:
        manifest = json.loads(self._manifest_path().read_text())
        manifest[0]["frames"][0]["path"] = "../LICENSE"
        self._manifest_path().write_text(json.dumps(manifest, indent=2) + "\n")
        self._commit_fixture_change("unsafe path")
        self._write_config(source_commit=self._head_commit())

        unsafe_result = self._run_import()

        self.assertNotEqual(unsafe_result.returncode, 0)
        self.assertIn("unsafe", unsafe_result.stderr.lower())

        self._git("reset", "--hard", self.source_commit)
        source_frame = self.source / "packages/workout-guide/assets/bench-press/frame-1.svg"
        source_frame.unlink()
        source_frame.symlink_to(self.source / "LICENSE")
        self._commit_fixture_change("symlink frame")
        self._write_config(source_commit=self._head_commit())

        symlink_result = self._run_import()

        self.assertNotEqual(symlink_result.returncode, 0)
        self.assertIn("symlink", symlink_result.stderr.lower())

    def test_rejects_missing_frame_and_license(self) -> None:
        missing_frame = self.source / "packages/workout-guide/assets/bench-press/frame-3.svg"
        missing_frame.unlink()
        self._commit_fixture_change("missing frame")
        self._write_config(source_commit=self._head_commit())

        frame_result = self._run_import()

        self.assertNotEqual(frame_result.returncode, 0)
        self.assertIn("frame", frame_result.stderr.lower())

        self._git("reset", "--hard", self.source_commit)
        (self.source / "LICENSE-ASSETS").unlink()
        self._commit_fixture_change("missing license")
        self._write_config(source_commit=self._head_commit())

        license_result = self._run_import()

        self.assertNotEqual(license_result.returncode, 0)
        self.assertIn("license", license_result.stderr.lower())

    def test_rejects_programming_override_for_unknown_exercise(self) -> None:
        overrides = json.loads(self.overrides.read_text())
        overrides["exercises"]["unknown-exercise"] = overrides["exercises"]["barbell-bench-press"]
        self.overrides.write_text(json.dumps(overrides, indent=2) + "\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("unknown walcrawl exercise id", result.stderr.lower())

    def test_rejects_regression_cycle(self) -> None:
        self._add_source_exercise(
            source_id="exercise-machine-chest-press",
            slug="machine-chest-press",
            name="Machine Chest Press",
            exercise_type="weight_reps",
            equipment="Machine",
            primary_muscle="Chest",
            secondary_muscles=["Triceps", "Shoulders"],
        )
        self._commit_fixture_change("add graph target")
        self._write_config(expected_exercises=2, expected_frames=6, source_commit=self._head_commit())
        reviewed = json.loads(self.reviewed_metadata.read_text())
        first = reviewed["exercises"]["barbell-bench-press"]
        first["progressionFamily"] = "horizontal-push"
        first["approvedRegressions"] = [{"exerciseId": "machine-chest-press"}]
        second = json.loads(json.dumps(first))
        second["equipmentAlternatives"] = [["Machine"]]
        second["approvedRegressions"] = [{"exerciseId": "barbell-bench-press"}]
        reviewed["exercises"]["machine-chest-press"] = second
        self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("regression cycle", result.stderr.lower())

    def test_rejects_unknown_reviewed_field(self) -> None:
        reviewed = json.loads(self.reviewed_metadata.read_text())
        reviewed["exercises"]["barbell-bench-press"]["fatigueCategory"] = "high"
        self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("unknown field fatigueCategory", result.stderr)

    def test_rejects_duplicate_reviewed_json_field(self) -> None:
        raw = self.reviewed_metadata.read_text()
        raw = raw.replace(
            '"reviewState": "draft"',
            '"reviewState": "draft", "reviewState": "draft"',
            1,
        )
        self.reviewed_metadata.write_text(raw)

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("duplicate JSON field reviewState", result.stderr)

    def test_rejects_missing_reviewed_field(self) -> None:
        reviewed = json.loads(self.reviewed_metadata.read_text())
        del reviewed["exercises"]["barbell-bench-press"]["impactLevel"]
        self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("impactLevel", result.stderr)

    def test_rejects_unknown_reviewed_enum(self) -> None:
        reviewed = json.loads(self.reviewed_metadata.read_text())
        reviewed["exercises"]["barbell-bench-press"]["complexity"] = "elite"
        self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("unknown enum", result.stderr.lower())

    def test_python_and_android_reject_the_same_reviewed_contract_fixtures(self) -> None:
        fixtures = json.loads(PARITY_FIXTURES.read_text())
        for case in fixtures["invalidCases"]:
            with self.subTest(case=case["name"]):
                self._write_reviewed_metadata()
                reviewed = json.loads(self.reviewed_metadata.read_text())
                metadata = json.loads(json.dumps(fixtures["baseReviewedMetadata"]))
                self._apply_fixture_operation(metadata, case)
                reviewed["exercises"]["barbell-bench-press"] = metadata
                self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

                result = self._run_import()

                self.assertNotEqual(result.returncode, 0, case["name"])
                self.assertIn(case["errorFragment"].lower(), result.stderr.lower())

    def test_rejects_approved_entry_without_human_provenance(self) -> None:
        reviewed = json.loads(self.reviewed_metadata.read_text())
        reviewed["exercises"]["barbell-bench-press"]["reviewState"] = "approved"
        self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("requires reviewerRole and reviewedAtEpochMillis", result.stderr)

    def test_rejects_review_timestamp_outside_android_parser_bound(self) -> None:
        reviewed = json.loads(self.reviewed_metadata.read_text())
        provenance = reviewed["exercises"]["barbell-bench-press"]["provenance"]
        provenance["reviewedAtEpochMillis"] = 253_402_300_800_000
        self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("reviewedAtEpochMillis", result.stderr)

    def test_rejects_decimal_notation_for_reviewed_integer_fields(self) -> None:
        for old, new, field in (
            ('"schemaVersion": 1', '"schemaVersion": 1.0', "schemaVersion"),
            ('"policyVersion": 1', '"policyVersion": 1.0', "policyVersion"),
            (
                '"reviewedAtEpochMillis": null',
                '"reviewedAtEpochMillis": 1.0',
                "reviewedAtEpochMillis",
            ),
        ):
            with self.subTest(field=field):
                self._write_reviewed_metadata()
                self.reviewed_metadata.write_text(
                    self.reviewed_metadata.read_text().replace(old, new, 1)
                )

                result = self._run_import()

                self.assertNotEqual(result.returncode, 0)
                self.assertIn(field, result.stderr)

    def test_rejects_primary_muscle_not_represented_by_catalog(self) -> None:
        reviewed = json.loads(self.reviewed_metadata.read_text())
        reviewed["exercises"]["barbell-bench-press"]["directPrimaryMuscle"] = "Quadriceps"
        self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("directPrimaryMuscle", result.stderr)
        self.assertIn("not represented", result.stderr)

    def test_rejects_secondary_muscle_equal_to_direct_primary(self) -> None:
        reviewed = json.loads(self.reviewed_metadata.read_text())
        reviewed["exercises"]["barbell-bench-press"]["descriptiveSecondaryMuscles"] = ["Chest"]
        self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("duplicates directPrimaryMuscle", result.stderr)

    def test_rejects_reviewed_prescription_shape_mismatch(self) -> None:
        reviewed = json.loads(self.reviewed_metadata.read_text())
        reviewed["exercises"]["barbell-bench-press"]["prescriptionShape"] = "duration"
        self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("prescriptionShape does not match exerciseType", result.stderr)

    def test_rejects_unknown_reviewed_equipment_and_capability(self) -> None:
        for field, value in (
            ("equipmentAlternatives", [["Quantum Rack"]]),
            ("capabilityRequirements", ["levitation"]),
        ):
            with self.subTest(field=field):
                self._write_reviewed_metadata()
                reviewed = json.loads(self.reviewed_metadata.read_text())
                reviewed["exercises"]["barbell-bench-press"][field] = value
                self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

                result = self._run_import()

                self.assertNotEqual(result.returncode, 0)
                self.assertIn("unknown enum", result.stderr.lower())

    def test_rejects_forbidden_numeric_pseudoscience_field(self) -> None:
        reviewed = json.loads(self.reviewed_metadata.read_text())
        reviewed["exercises"]["barbell-bench-press"]["joint_stress_score"] = 2
        self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("forbidden numeric field", result.stderr.lower())
        self.assertNotIn("Initial draft for later human review", result.stderr)

    def test_rejects_non_finite_reviewed_number(self) -> None:
        raw = self.reviewed_metadata.read_text()
        raw = raw.replace('"policyVersion": 1', '"policyVersion": NaN')
        self.reviewed_metadata.write_text(raw)

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("non-finite", result.stderr.lower())

    def test_rejects_oversized_reviewed_string_and_array(self) -> None:
        for field, value in (
            ("progressionFamily", "x" * 65),
            ("descriptiveSecondaryMuscles", ["Shoulders"] * 17),
        ):
            with self.subTest(field=field):
                self._write_reviewed_metadata()
                reviewed = json.loads(self.reviewed_metadata.read_text())
                reviewed["exercises"]["barbell-bench-press"][field] = value
                self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

                result = self._run_import()

                self.assertNotEqual(result.returncode, 0)
                self.assertTrue(
                    "characters" in result.stderr.lower()
                    or "items" in result.stderr.lower()
                )

    def test_rejects_cardio_duration_from_reviewed_cohort(self) -> None:
        reviewed = self._prepare_reviewed_graph_target(
            edge_field="approvedSubstitutions",
            target_exercise_type="duration",
            target_primary_muscle="Core",
            target_secondary_muscles=["cardio"],
        )
        reviewed["exercises"]["barbell-bench-press"]["approvedSubstitutions"] = []
        reviewed["exercises"]["machine-chest-press"].update(
            {
                "directPrimaryMuscle": "Core",
                "descriptiveSecondaryMuscles": [],
                "movementPattern": "core",
            }
        )
        self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("cardio duration work", result.stderr.lower())

    def test_rejects_stretch_from_reviewed_cohort(self) -> None:
        self._add_source_exercise(
            source_id="exercise-chest-stretch",
            slug="chest-stretch",
            name="Chest Stretch",
            exercise_type="bodyweight_reps",
            equipment="Bodyweight",
            primary_muscle="Chest",
            secondary_muscles=[],
            is_stretch=True,
        )
        self._commit_fixture_change("add stretch")
        self._write_config(expected_exercises=2, expected_frames=6, source_commit=self._head_commit())
        reviewed = json.loads(self.reviewed_metadata.read_text())
        stretch = json.loads(json.dumps(reviewed["exercises"]["barbell-bench-press"]))
        stretch.update(
            {
                "prescriptionShape": "bodyweight_reps",
                "equipmentAlternatives": [["Bodyweight"]],
                "approvedRegressions": [],
                "approvedSubstitutions": [],
            }
        )
        reviewed["exercises"]["chest-stretch"] = stretch
        self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("is a stretch", result.stderr.lower())

    def test_rejects_excessive_reviewed_depth_and_payload_size(self) -> None:
        reviewed = json.loads(self.reviewed_metadata.read_text())
        nested: object = "value"
        for _ in range(20):
            nested = [nested]
        reviewed["exercises"]["barbell-bench-press"]["provenance"]["reviewerRole"] = nested
        self.reviewed_metadata.write_text(json.dumps(reviewed) + "\n")

        depth_result = self._run_import()

        self.assertNotEqual(depth_result.returncode, 0)
        self.assertIn("nesting depth", depth_result.stderr.lower())

        self._write_reviewed_metadata()
        self.reviewed_metadata.write_text(
            self.reviewed_metadata.read_text() + (" " * 1_000_001)
        )
        payload_result = self._run_import()

        self.assertNotEqual(payload_result.returncode, 0)
        self.assertIn("1000000-byte input limit", payload_result.stderr.lower())

    def test_rejects_more_reviewed_entries_than_schema_bound(self) -> None:
        reviewed = json.loads(self.reviewed_metadata.read_text())
        base = reviewed["exercises"]["barbell-bench-press"]
        reviewed["exercises"] = {
            f"sample-{index}": json.loads(json.dumps(base))
            for index in range(501)
        }
        self.reviewed_metadata.write_text(json.dumps(reviewed) + "\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("more than 500 fields", result.stderr.lower())

    def test_rejects_duplicate_graph_edge_and_self_edge(self) -> None:
        for links, message in (
            ([{"exerciseId": "barbell-bench-press"}], "self-edge"),
            (
                [
                    {"exerciseId": "missing-target"},
                    {"exerciseId": "missing-target", "rationale": "Duplicate."},
                ],
                "duplicate",
            ),
        ):
            with self.subTest(message=message):
                self._write_reviewed_metadata()
                reviewed = json.loads(self.reviewed_metadata.read_text())
                reviewed["exercises"]["barbell-bench-press"]["approvedRegressions"] = links
                self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

                result = self._run_import()

                self.assertNotEqual(result.returncode, 0)
                self.assertIn(message, result.stderr.lower())

    def test_rejects_regression_target_without_reviewed_metadata(self) -> None:
        self._add_source_exercise(
            source_id="exercise-machine-chest-press",
            slug="machine-chest-press",
            name="Machine Chest Press",
            exercise_type="weight_reps",
            equipment="Machine",
            primary_muscle="Chest",
            secondary_muscles=["Triceps", "Shoulders"],
        )
        self._commit_fixture_change("add unreviewed graph target")
        self._write_config(expected_exercises=2, expected_frames=6, source_commit=self._head_commit())
        reviewed = json.loads(self.reviewed_metadata.read_text())
        reviewed["exercises"]["barbell-bench-press"]["approvedRegressions"] = [
            {"exerciseId": "machine-chest-press"}
        ]
        self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("lacks reviewed metadata", result.stderr.lower())

    def test_rejects_regression_that_is_categorically_harder(self) -> None:
        valid_reviewed = self._prepare_reviewed_graph_target()
        for field, target_value, message in (
            ("complexity", "advanced", "more complex"),
            ("supportRequirement", "unsupported", "requires less support"),
            ("capabilityRequirements", ["impact"], "adds capability requirements"),
        ):
            with self.subTest(field=field):
                reviewed = json.loads(json.dumps(valid_reviewed))
                reviewed["exercises"]["machine-chest-press"][field] = target_value
                self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

                result = self._run_import()

                self.assertNotEqual(result.returncode, 0)
                self.assertIn(message, result.stderr.lower())

    def test_rejects_cross_family_regression_without_bounded_rationale(self) -> None:
        reviewed = self._prepare_reviewed_graph_target()
        reviewed["exercises"]["machine-chest-press"]["progressionFamily"] = "machine-horizontal-push"
        self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("crosses progressionFamily without rationale", result.stderr)

    def test_rejects_regression_with_incompatible_movement_pattern(self) -> None:
        reviewed = self._prepare_reviewed_graph_target()
        reviewed["exercises"]["machine-chest-press"]["movementPattern"] = "vertical_push"
        self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("incompatible movementPattern", result.stderr)

    def test_rejects_substitution_with_incompatible_prescription_shape(self) -> None:
        reviewed = self._prepare_reviewed_graph_target(
            edge_field="approvedSubstitutions",
            target_exercise_type="bodyweight_reps",
        )
        self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("incompatible prescriptionShape", result.stderr)

    def test_rejects_substitution_role_change_without_rationale(self) -> None:
        reviewed = self._prepare_reviewed_graph_target(edge_field="approvedSubstitutions")
        target = reviewed["exercises"]["machine-chest-press"]
        target["directPrimaryMuscle"] = "Shoulders"
        target["descriptiveSecondaryMuscles"] = ["Chest", "Triceps"]
        self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("without rationale", result.stderr)

    def test_accepts_directed_substitution_without_reverse_edge(self) -> None:
        reviewed = self._prepare_reviewed_graph_target(edge_field="approvedSubstitutions")
        self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

        result = self._run_import()

        self.assertEqual(result.returncode, 0, result.stderr)

    def test_rejects_programming_the_android_parser_cannot_read(self) -> None:
        overrides = json.loads(self.overrides.read_text())
        programming = overrides["exercises"]["barbell-bench-press"]

        for combinations in ([], [[]]):
            with self.subTest(combinations=combinations):
                programming["requiredEquipmentCombinations"] = combinations
                self.overrides.write_text(json.dumps(overrides, indent=2) + "\n")

                result = self._run_import()

                self.assertNotEqual(result.returncode, 0)
                self.assertIn("equipment combinations must be non-empty", result.stderr.lower())

        programming["requiredEquipmentCombinations"] = [["Barbell", "Bench"]]
        programming["coachingSummary"] = "x" * 2_001
        self.overrides.write_text(json.dumps(overrides, indent=2) + "\n")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("exceeds 2000 characters", result.stderr.lower())

    def test_rejects_catalog_string_the_android_parser_cannot_read(self) -> None:
        manifest = json.loads(self._manifest_path().read_text())
        manifest[0]["name"] = "x" * 257
        self._manifest_path().write_text(json.dumps(manifest, indent=2) + "\n")
        self._commit_fixture_change("oversized exercise name")
        self._write_config(source_commit=self._head_commit())

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("exceeds 256 characters", result.stderr.lower())

    def _write_source_fixture(self) -> None:
        attribution = {
            "creator": "Bryl Lim",
            "creatorUrl": "https://bryllim.com",
            "license": "CC BY-SA 4.0",
            "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
            "source": {
                "name": "Everkinetic",
                "url": "https://example.test/source.svg",
                "license": "CC BY-SA 4.0",
                "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
                "changes": "Vector traced.",
            },
        }
        frames = []
        for index in range(1, 4):
            relative_path = f"assets/bench-press/frame-{index}.svg"
            frame_path = self.source / "packages/workout-guide" / relative_path
            frame_path.parent.mkdir(parents=True, exist_ok=True)
            frame_path.write_text(f"<svg data-frame=\"{index}\"/>\n")
            (frame_path.with_suffix(".png")).write_bytes(b"png-not-imported")
            frames.append(
                {
                    "index": index,
                    "path": relative_path,
                    "width": 512,
                    "height": 512,
                    "format": "svg",
                    "attribution": attribution,
                }
            )
        manifest = [
            {
                "id": "exercise-bench-press",
                "slug": "bench-press",
                "name": "Bench Press",
                "exerciseType": "weight_reps",
                "equipment": "Barbell",
                "primaryMuscle": "Chest",
                "secondaryMuscles": ["Triceps", "Shoulders"],
                "isStretch": False,
                "frames": frames,
                "attribution": attribution,
            }
        ]
        self._manifest_path().parent.mkdir(parents=True, exist_ok=True)
        self._manifest_path().write_text(json.dumps(manifest, indent=2) + "\n")
        (self.source / "LICENSE").write_text("MIT fixture\n")
        (self.source / "LICENSE-ASSETS").write_text("CC BY-SA fixture\n")
        (self.source / "ATTRIBUTION.md").write_text("Fixture attribution\n")

    def _add_source_exercise(
        self,
        *,
        source_id: str,
        slug: str,
        name: str,
        exercise_type: str,
        equipment: str,
        primary_muscle: str,
        secondary_muscles: list[str],
        is_stretch: bool = False,
    ) -> None:
        manifest = json.loads(self._manifest_path().read_text())
        attribution = manifest[0]["attribution"]
        frames = []
        for index in range(1, 4):
            relative_path = f"assets/{slug}/frame-{index}.svg"
            frame_path = self.source / "packages/workout-guide" / relative_path
            frame_path.parent.mkdir(parents=True, exist_ok=True)
            frame_path.write_text(f"<svg data-frame=\"{index}\"/>\n")
            frames.append(
                {
                    "index": index,
                    "path": relative_path,
                    "width": 512,
                    "height": 512,
                    "format": "svg",
                    "attribution": attribution,
                }
            )
        manifest.append(
            {
                "id": source_id,
                "slug": slug,
                "name": name,
                "exerciseType": exercise_type,
                "equipment": equipment,
                "primaryMuscle": primary_muscle,
                "secondaryMuscles": secondary_muscles,
                "isStretch": is_stretch,
                "frames": frames,
                "attribution": attribution,
            }
        )
        self._manifest_path().write_text(json.dumps(manifest, indent=2) + "\n")

    def _prepare_reviewed_graph_target(
        self,
        edge_field: str = "approvedRegressions",
        target_exercise_type: str = "weight_reps",
        target_primary_muscle: str = "Chest",
        target_secondary_muscles: list[str] | None = None,
    ) -> dict:
        target_secondary_muscles = target_secondary_muscles or ["Triceps", "Shoulders"]
        self._add_source_exercise(
            source_id="exercise-machine-chest-press",
            slug="machine-chest-press",
            name="Machine Chest Press",
            exercise_type=target_exercise_type,
            equipment="Machine",
            primary_muscle=target_primary_muscle,
            secondary_muscles=target_secondary_muscles,
        )
        self._commit_fixture_change("add reviewed graph target")
        self._write_config(expected_exercises=2, expected_frames=6, source_commit=self._head_commit())
        reviewed = json.loads(self.reviewed_metadata.read_text())
        source = reviewed["exercises"]["barbell-bench-press"]
        source["progressionFamily"] = "horizontal-push"
        source[edge_field] = [{"exerciseId": "machine-chest-press"}]
        target = json.loads(json.dumps(source))
        target[edge_field] = []
        target["complexity"] = "foundational"
        target["equipmentAlternatives"] = [["Machine"]]
        target["prescriptionShape"] = target_exercise_type
        reviewed["exercises"]["machine-chest-press"] = target
        return reviewed

    def _write_config(
        self,
        expected_exercises: int = 1,
        expected_frames: int = 3,
        source_commit: str | None = None,
    ) -> None:
        config = {
            "schemaVersion": 1,
            "sourceRepository": "https://github.com/bryllim/workout-guide",
            "sourceCommit": source_commit or self.source_commit,
            "manifestPath": "packages/workout-guide/manifest.json",
            "assetBasePath": "packages/workout-guide",
            "licenseFiles": ["LICENSE", "LICENSE-ASSETS", "ATTRIBUTION.md"],
            "expectedExerciseCount": expected_exercises,
            "expectedFrameCount": expected_frames,
            "idAliases": {
                "exercise-bench-press": {
                    "wallCrawlId": "barbell-bench-press",
                    "searchAliases": ["Barbell Bench Press"],
                }
            },
        }
        self.config.write_text(json.dumps(config, indent=2) + "\n")

    def _write_overrides(self) -> None:
        overrides = {
            "schemaVersion": 1,
            "exercises": {
                "barbell-bench-press": {
                    "requiredEquipmentCombinations": [["Barbell", "Bench"]],
                    "movementPattern": "horizontal_push",
                    "difficulty": "intermediate",
                    "mechanics": "compound",
                    "recommendedRepRange": {"min": 5, "max": 8},
                    "fatigueScore": 4,
                    "progressionType": "repetitions_then_load",
                    "alternativeExerciseIds": [],
                    "coachingSummary": "Foundational horizontal press.",
                }
            },
        }
        self.overrides.write_text(json.dumps(overrides, indent=2) + "\n")

    def _write_reviewed_metadata(self) -> None:
        reviewed = {
            "schemaVersion": 1,
            "exercises": {
                "barbell-bench-press": {
                    "reviewState": "draft",
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
                    "provenance": {
                        "reviewerRole": None,
                        "rationaleOrSource": "Initial draft for later human review.",
                        "reviewedAtEpochMillis": None,
                        "schemaVersion": 1,
                        "policyVersion": 1,
                    },
                }
            },
        }
        self.reviewed_metadata.write_text(json.dumps(reviewed, indent=2) + "\n")

    @staticmethod
    def _apply_fixture_operation(metadata: dict, case: dict) -> None:
        path = case["path"]
        target = metadata
        for component in path[:-1]:
            target = target[component]
        if case["operation"] == "remove":
            del target[path[-1]]
        elif case["operation"] == "set":
            target[path[-1]] = case["value"]
        else:
            raise AssertionError(f"Unknown fixture operation: {case['operation']}")

    def _commit_source(self) -> str:
        self._git("init", "-q")
        self._git("config", "user.email", "fixture@example.test")
        self._git("config", "user.name", "Fixture")
        self._git("add", ".")
        self._git("commit", "-q", "-m", "fixture")
        return self._head_commit()

    def _commit_fixture_change(self, message: str) -> None:
        self._git("add", "-A")
        self._git("commit", "-q", "-m", message)

    def _head_commit(self) -> str:
        return self._git("rev-parse", "HEAD").stdout.strip()

    def _git(self, *arguments: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["git", *arguments],
            cwd=self.source,
            check=True,
            text=True,
            capture_output=True,
        )

    def _run_import(self, check_only: bool = False) -> subprocess.CompletedProcess[str]:
        command = [
            sys.executable,
            str(SCRIPT_PATH),
            "--source",
            str(self.source),
            "--output",
            str(self.output),
            "--config",
            str(self.config),
            "--overrides",
            str(self.overrides),
            "--reviewed-metadata",
            str(self.reviewed_metadata),
            "--review-report",
            str(self.review_report),
        ]
        if check_only:
            command.append("--check")
        return subprocess.run(command, text=True, capture_output=True)

    def _manifest_path(self) -> Path:
        return self.source / "packages/workout-guide/manifest.json"

    @staticmethod
    def _tree_digest(root: Path) -> str:
        digest = hashlib.sha256()
        for path in sorted(item for item in root.rglob("*") if item.is_file()):
            digest.update(path.relative_to(root).as_posix().encode())
            digest.update(b"\0")
            digest.update(path.read_bytes())
            digest.update(b"\0")
        return digest.hexdigest()


if __name__ == "__main__":
    unittest.main()

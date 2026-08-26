import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).with_name("import_catalog.py")


class ImportCatalogTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_directory.cleanup)
        self.root = Path(self.temp_directory.name)
        self.source = self.root / "workout-guide"
        self.output = self.root / "android-assets" / "workout-guide"
        self.config = self.root / "import-config.json"
        self.overrides = self.root / "programming-overrides.json"
        self.source.mkdir()
        self._write_source_fixture()
        self.source_commit = self._commit_source()
        self._write_config()
        self._write_overrides()

    def test_import_generates_normalized_svg_only_bundle(self) -> None:
        result = self._run_import()

        self.assertEqual(result.returncode, 0, result.stderr)
        catalog = json.loads((self.output / "catalog.json").read_text())
        self.assertEqual(catalog["schemaVersion"], 1)
        self.assertEqual(catalog["source"]["commit"], self.source_commit)
        self.assertEqual(len(catalog["exercises"]), 1)

        exercise = catalog["exercises"][0]
        self.assertEqual(exercise["id"], "barbell-bench-press")
        self.assertEqual(exercise["sourceId"], "exercise-bench-press")
        self.assertEqual(exercise["sourceSlug"], "bench-press")
        self.assertEqual(exercise["searchAliases"], ["Barbell Bench Press"])
        self.assertEqual(exercise["listedEquipment"], ["Barbell"])
        self.assertEqual(exercise["primaryMuscles"], ["Chest"])
        self.assertEqual(exercise["programming"]["requiredEquipmentCombinations"], [["Barbell", "Bench"]])
        self.assertEqual(
            [frame["assetPath"] for frame in exercise["frames"]],
            [
                "workout-guide/assets/bench-press/frame-1.svg",
                "workout-guide/assets/bench-press/frame-2.svg",
                "workout-guide/assets/bench-press/frame-3.svg",
            ],
        )
        self.assertEqual(exercise["frames"][0]["attribution"]["creator"], "Bryl Lim")
        self.assertEqual(exercise["attribution"]["source"]["name"], "Everkinetic")

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
        for license_name in ("LICENSE", "LICENSE-ASSETS", "ATTRIBUTION.md"):
            self.assertEqual((self.output / license_name).read_bytes(), (self.source / license_name).read_bytes())
        self.assertIn(self.source_commit, (self.output / "NOTICE.md").read_text())
        self.assertIn("exercises=1 frames=3", result.stdout)

    def test_reimport_is_deterministic_and_check_detects_drift_without_writing(self) -> None:
        first = self._run_import()
        self.assertEqual(first.returncode, 0, first.stderr)
        first_digest = self._tree_digest(self.output)

        second = self._run_import()
        self.assertEqual(second.returncode, 0, second.stderr)
        self.assertEqual(self._tree_digest(self.output), first_digest)

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

    def test_rejects_source_at_wrong_commit(self) -> None:
        (self.source / "README.md").write_text("new commit\n")
        self._git("add", "README.md")
        self._git("commit", "-m", "advance fixture")

        result = self._run_import()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("pinned commit", result.stderr.lower())

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

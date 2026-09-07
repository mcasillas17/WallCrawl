import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import subprocess
import sys
from unittest.mock import patch

from build_review_index import (
    _load,
    _validate_output_directory,
    build_artifacts,
    check_review_index,
    resolve_exact_references,
    validate_artifacts,
    write_review_index,
)


ROOT = Path(__file__).resolve().parents[2]
CANONICAL = {
    "ledger": ROOT / "docs/research/2026-09-07-full-exercise-catalog-review.json",
    "metadata": ROOT / "tools/workout-guide/reviewed-metadata.json",
    "catalog": ROOT / "app/src/main/assets/workout-guide/catalog.json",
    "manifest": ROOT / "app/src/main/assets/workout-guide/upstream-manifest.json",
}


def _digest(value):
    return hashlib.sha256(json.dumps(
        value, ensure_ascii=False, separators=(",", ":"), sort_keys=True
    ).encode()).hexdigest()


class BuildReviewIndexTest(unittest.TestCase):
    def setUp(self):
        (ROOT / "build").mkdir(exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(
            prefix="test-review-index-", dir=ROOT / "build"
        )
        self.base = Path(self.temporary.name)
        self.metadata_values = {
            "alpha": {
                "reviewState": "draft",
                "approvedRegressions": [{"exerciseId": "beta"}],
                "approvedSubstitutions": [],
                "notes": "large-value-" + ("évidence " * 900),
            },
            "beta": {
                "reviewState": "draft",
                "approvedRegressions": [],
                "approvedSubstitutions": [],
            },
        }
        self.shared_boilerplate = (
            "This exact long review constraint is intentionally repeated so reviewers "
            "can open one bounded value page instead of rereading boilerplate."
        )
        self.metadata_values["alpha"]["sharedConstraint"] = self.shared_boilerplate
        self.metadata_values["beta"]["sharedConstraint"] = self.shared_boilerplate
        self.entries = [
            {
                "id": "alpha",
                "disposition": "ready_for_human_review",
                "sourceId": "source-alpha",
                "metadataSha256": _digest(self.metadata_values["alpha"]),
                "graphDecisions": [{
                    "kind": "regression",
                    "targetId": "beta",
                    "action": "add",
                    "rationale": "The complete authored rationale remains visible.",
                    "sourceMetadataSha256": _digest(self.metadata_values["alpha"]),
                    "targetMetadataSha256": _digest(self.metadata_values["beta"]),
                }],
                "evidence": [{
                    "source": "https://example.test/alpha",
                    "aids": ["frame-a"],
                    "constraint": self.shared_boilerplate,
                }],
            },
            {
                "id": "beta",
                "disposition": "pending_evidence_or_policy",
                "sourceId": "source-beta",
                "metadataSha256": _digest(self.metadata_values["beta"]),
                "graphDecisions": [],
                "evidence": [{
                    "source": "https://example.test/beta",
                    "aids": ["frame-b"],
                    "constraint": self.shared_boilerplate,
                }],
            },
        ]
        self.catalog = {
            "schemaVersion": 1,
            "visuals": {"format": "svg"},
            "exercises": [
                {"id": "alpha", "sourceId": "source-alpha", "name": "Alpha"},
                {"id": "beta", "sourceId": "source-beta", "name": "Beta"},
            ],
        }
        self.upstream = [
            {"id": "source-alpha", "slug": "alpha-source", "frames": [{"path": "a.svg"}]},
            {"id": "source-beta", "slug": "beta-source", "frames": [{"path": "b.svg"}]},
        ]
        self.paths = {
            "ledger": self.base / "ledger.json",
            "metadata": self.base / "metadata.json",
            "catalog": self.base / "catalog.json",
            "manifest": self.base / "upstream.json",
        }
        manifest_text = json.dumps(self.upstream, ensure_ascii=False, indent=2) + "\n"
        self.ledger = {
            "schemaVersion": 1,
            "source": {"manifestSha256": hashlib.sha256(manifest_text.encode()).hexdigest()},
            "review": {
                "method": "full",
                "aids": ["source frames", "citations"],
                "sharedConstraint": self.shared_boilerplate,
            },
            "entries": self.entries,
        }
        values = {
            "ledger": self.ledger,
            "metadata": {"schemaVersion": 1, "exercises": self.metadata_values},
            "catalog": self.catalog,
        }
        for label, value in values.items():
            self.paths[label].write_text(
                json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
            )
        self.paths["manifest"].write_text(manifest_text, encoding="utf-8")
        self.context = self.base / "changed.py"
        self.context.write_text(
            "".join(f"line_{number:03d} = '{'x' * 90}'\n" for number in range(100)),
            encoding="utf-8",
        )

    def tearDown(self):
        self.temporary.cleanup()

    def _build(self, **changes):
        arguments = dict(
            ledger_path=self.paths["ledger"],
            metadata_path=self.paths["metadata"],
            catalog_path=self.paths["catalog"],
            upstream_manifest_path=self.paths["manifest"],
            source_paths=[self.context],
            repository_root=self.base,
            max_bytes=1024,
        )
        arguments.update(changes)
        return build_artifacts(**arguments)

    def test_build_is_bounded_deterministic_and_lossless(self):
        artifacts = self._build()
        self.assertEqual(artifacts, self._build())
        validate_artifacts(artifacts, 1024)
        self.assertTrue(all(len(value.encode()) <= 1024 for value in artifacts.values()))
        self.assertIn("INDEX.md", artifacts)
        self.assertIn("MANIFEST.json", artifacts)
        self.assertIn("reading/index-001.md", artifacts)
        self.assertIn("- `cohorts/INDEX.md`", artifacts["INDEX.md"])

        manifest = json.loads(artifacts["MANIFEST.json"])
        self.assertEqual(2, manifest["counts"]["exercises"])
        self.assertEqual(1, manifest["counts"]["directedEdges"])
        self.assertEqual(1, manifest["counts"]["graphDecisions"])
        reading_order = "\n".join(
            artifacts[path] for path in manifest["navigation"]["payloadReadingOrderPages"]
        )
        for path in artifacts:
            if path.startswith("exercises/") and "/payload-" in path and path.endswith(".txt"):
                self.assertEqual(1, reading_order.count(f"`{path}`"))
        self.assertEqual(
            hashlib.sha256(self.paths["ledger"].read_bytes()).hexdigest(),
            manifest["sources"]["ledger"]["sha256"],
        )

        packages = {}
        for exercise_id in ("alpha", "beta"):
            prefix = f"exercises/{exercise_id}/payload-"
            payload = "".join(
                artifacts[path] for path in sorted(artifacts)
                if path.startswith(prefix) and path.endswith(".txt")
            )
            packages[exercise_id] = resolve_exact_references(json.loads(payload), artifacts)
        self.assertEqual(self.entries[0], packages["alpha"]["ledgerEntry"])
        self.assertEqual(self.metadata_values["alpha"], packages["alpha"]["reviewedMetadata"])
        self.assertEqual(self.catalog["exercises"][0], packages["alpha"]["catalogExercise"])
        self.assertEqual(self.upstream[0], packages["alpha"]["upstreamManifestEntry"])
        self.assertEqual(["edge-0001"], packages["alpha"]["outgoingEdgeIds"])
        self.assertEqual(["edge-0001"], packages["beta"]["incomingEdgeIds"])
        self.assertEqual(["frame-a"], packages["alpha"]["ledgerEntry"]["evidence"][0]["aids"])
        payload_text = "".join(
            value for path, value in artifacts.items()
            if path.startswith("exercises/") and "/payload-" in path and path.endswith(".txt")
        )
        value_text = "".join(
            value for path, value in artifacts.items()
            if path.startswith("values/") and "/part-" in path
        )
        self.assertNotIn(self.shared_boilerplate, payload_text)
        self.assertEqual(1, value_text.count(self.shared_boilerplate))

        global_payload = resolve_exact_references(json.loads("".join(
            artifacts[path] for path in sorted(artifacts)
            if path.startswith("globals/payload-") and path.endswith(".txt")
        )), artifacts)
        reconstructed_ledger = copy.deepcopy(global_payload["ledger"])
        reconstructed_ledger["entries"] = [
            packages[exercise_id]["ledgerEntry"] for exercise_id in ("alpha", "beta")
        ]
        self.assertEqual(self.ledger, reconstructed_ledger)
        reconstructed_catalog = copy.deepcopy(global_payload["catalog"])
        reconstructed_catalog["exercises"] = [
            packages[exercise_id]["catalogExercise"] for exercise_id in ("alpha", "beta")
        ]
        self.assertEqual(self.catalog, reconstructed_catalog)
        reconstructed_metadata = copy.deepcopy(global_payload["metadata"])
        reconstructed_metadata["exercises"] = {
            exercise_id: packages[exercise_id]["reviewedMetadata"]
            for exercise_id in ("alpha", "beta")
        }
        self.assertEqual(
            {"schemaVersion": 1, "exercises": self.metadata_values},
            reconstructed_metadata,
        )
        self.assertEqual(
            self.context.read_text(),
            "".join(
                artifacts[path] for path in sorted(artifacts)
                if path.startswith("context/") and "/part-" in path
            ),
        )
        context_source_indexes = sorted(
            path for path in artifacts
            if path.startswith("context/001-") and "/index-" in path
        )
        self.assertGreater(len(context_source_indexes), 1)
        for path in context_source_indexes:
            self.assertIn(f"`{path}`", artifacts["context/index-001.md"])

        alpha_index = artifacts["exercises/alpha/INDEX.md"]
        self.assertIn("`/entries/0`", alpha_index)
        self.assertIn("`/exercises/alpha`", alpha_index)
        self.assertIn("source lines", alpha_index)
        cohort_index = artifacts["cohorts/INDEX.md"]
        self.assertIn("cohorts/disposition/index-001.md", cohort_index)
        disposition_pages = "\n".join(
            value for path, value in artifacts.items()
            if path.startswith("cohorts/disposition/") and "cohort-" in path
        )
        self.assertEqual(1, disposition_pages.count("exercises/alpha/INDEX.md"))
        self.assertEqual(1, disposition_pages.count("exercises/beta/INDEX.md"))
        edge_text = "\n".join(
            value for path, value in artifacts.items() if path.startswith("edges/index-")
        )
        self.assertEqual(1, edge_text.count("edge-0001"))
        self.assertIn("alpha", edge_text)
        self.assertIn("beta", edge_text)
        self.assertIn("ledger.json:", edge_text)
        self.assertIn("`/entries/0/graphDecisions/0`", edge_text)

    def test_check_detects_missing_changed_and_extra_artifacts(self):
        artifacts = self._build()
        output = self.base / "output"
        write_review_index(output, artifacts)
        self.assertEqual([], check_review_index(output, artifacts))
        (output / "INDEX.md").unlink()
        self.assertIn("missing: INDEX.md", check_review_index(output, artifacts))
        write_review_index(output, artifacts)
        (output / "INDEX.md").write_text("changed\n")
        self.assertIn("changed: INDEX.md", check_review_index(output, artifacts))
        write_review_index(output, artifacts)
        (output / "extra.txt").write_text("extra\n")
        self.assertIn("extra: extra.txt", check_review_index(output, artifacts))

    def test_supplied_navigation_indexes_are_linked_from_the_entry_point(self):
        navigation = self.base / "INDEX.md"
        navigation.write_text("# Source illustrations\n\n- image-alpha.png\n")
        artifacts = self._build(source_paths=[self.context, navigation], max_bytes=2048)
        manifest = json.loads(artifacts["MANIFEST.json"])
        self.assertEqual(["INDEX.md"],
                         manifest["navigation"]["supplementalRepositoryIndexes"])
        self.assertIn("Additional supplied navigation (repository-relative paths)",
                      artifacts["INDEX.md"])
        self.assertIn("- `INDEX.md`", artifacts["INDEX.md"])

    def test_cli_generation_and_check_share_relative_output_base_from_non_root(self):
        output = self.base / "relative-output"
        command = [
            sys.executable, str(ROOT / "tools/workout-guide/build_review_index.py"),
            "--ledger", str(self.paths["ledger"]),
            "--metadata", str(self.paths["metadata"]),
            "--catalog", str(self.paths["catalog"]),
            "--upstream-manifest", str(self.paths["manifest"]),
            "--output", str(output.relative_to(ROOT)),
        ]
        generated = subprocess.run(command, cwd=self.base, capture_output=True, text=True)
        self.assertEqual(0, generated.returncode, generated.stderr)
        self.assertTrue((output / "INDEX.md").is_file())
        checked = subprocess.run(command + ["--check"], cwd=self.base,
                                 capture_output=True, text=True)
        self.assertEqual(0, checked.returncode, checked.stderr)

    def test_output_is_confined_to_owned_build_subdirectories(self):
        for output in (ROOT, ROOT / "build", ROOT / "docs", self.paths["ledger"]):
            with self.subTest(output=output):
                with self.assertRaises(ValueError):
                    _validate_output_directory(output)
        unowned = self.base / "unowned"
        unowned.mkdir()
        (unowned / "keep.txt").write_text("unrelated data")
        with self.assertRaisesRegex(ValueError, "not an owned"):
            _validate_output_directory(unowned)
        link = self.base / "link"
        link.symlink_to(unowned, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "symlink"):
            _validate_output_directory(link / "child")
        self.assertEqual("unrelated data", (unowned / "keep.txt").read_text())

    def test_failed_backup_cleanup_does_not_delete_the_published_index(self):
        artifacts = self._build()
        output = self.base / "output"
        write_review_index(output, artifacts)
        replacement = dict(artifacts, **{"INDEX.md": "new index\n"})
        with patch(
            "build_review_index.shutil.rmtree", side_effect=OSError("cleanup failed")
        ) as cleanup:
            with self.assertRaisesRegex(OSError, "cleanup failed"):
                write_review_index(output, replacement)
        self.assertEqual(1, cleanup.call_count)
        self.assertNotEqual(output, cleanup.call_args.args[0])
        self.assertEqual("new index\n", (output / "INDEX.md").read_text())

    def test_each_source_is_parsed_from_the_same_bytes_that_are_hashed_and_located(self):
        with patch.object(
            Path, "read_text", side_effect=AssertionError("source was read a second time")
        ):
            value, text, raw, locations = _load(self.paths["ledger"], "ledger")
        self.assertEqual(self.ledger, value)
        self.assertEqual(raw.decode("utf-8"), text)
        self.assertEqual((1, len(text.splitlines())), locations.lines("", value))

    def test_rejects_stale_missing_duplicate_and_oversized_inputs_or_outputs(self):
        original_ledger = self.paths["ledger"].read_text()
        original_manifest = self.paths["manifest"].read_text()
        stale = copy.deepcopy(self.ledger)
        stale["entries"][0]["metadataSha256"] = "0" * 64
        self.paths["ledger"].write_text(json.dumps(stale), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "stale metadata"):
            self._build()

        self.paths["ledger"].write_text(original_ledger, encoding="utf-8")
        duplicate = copy.deepcopy(self.ledger)
        duplicate["entries"].append(copy.deepcopy(duplicate["entries"][0]))
        self.paths["ledger"].write_text(json.dumps(duplicate), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "duplicate ledger ID"):
            self._build()

        self.paths["ledger"].write_text(original_ledger, encoding="utf-8")
        self.paths["manifest"].unlink()
        with self.assertRaisesRegex(ValueError, "missing upstream manifest"):
            self._build()
        self.paths["manifest"].write_text(original_manifest, encoding="utf-8")

        with self.assertRaisesRegex(ValueError, "exceeds"):
            validate_artifacts({"too-large.txt": "x" * 1025}, 1024)

    def test_canonical_aid_reconstructs_every_material_payload(self):
        if not all(path.is_file() for path in CANONICAL.values()):
            self.skipTest("canonical full review inputs are not present")
        artifacts = build_artifacts(
            ledger_path=CANONICAL["ledger"],
            metadata_path=CANONICAL["metadata"],
            catalog_path=CANONICAL["catalog"],
            upstream_manifest_path=CANONICAL["manifest"],
            repository_root=ROOT,
        )
        validate_artifacts(artifacts, 16 * 1024)
        ledger = json.loads(CANONICAL["ledger"].read_text())
        metadata = json.loads(CANONICAL["metadata"].read_text())
        catalog = json.loads(CANONICAL["catalog"].read_text())
        upstream = json.loads(CANONICAL["manifest"].read_text())
        packages = []
        for entry in ledger["entries"]:
            prefix = f"exercises/{entry['id']}/payload-"
            packages.append(resolve_exact_references(json.loads("".join(
                artifacts[path] for path in sorted(artifacts)
                if path.startswith(prefix) and path.endswith(".txt")
            )), artifacts))
        global_payload = resolve_exact_references(json.loads("".join(
            artifacts[path] for path in sorted(artifacts)
            if path.startswith("globals/payload-") and path.endswith(".txt")
        )), artifacts)
        rebuilt_ledger = copy.deepcopy(global_payload["ledger"])
        rebuilt_ledger["entries"] = [value["ledgerEntry"] for value in packages]
        rebuilt_catalog = copy.deepcopy(global_payload["catalog"])
        rebuilt_catalog["exercises"] = [
            value["catalogExercise"]
            for value in sorted(packages, key=lambda item: item["catalogOrdinal"])
        ]
        rebuilt_metadata = copy.deepcopy(global_payload["metadata"])
        rebuilt_metadata["exercises"] = {
            value["id"]: value["reviewedMetadata"]
            for value in sorted(
                (item for item in packages if item["metadataOrdinal"] is not None),
                key=lambda item: item["metadataOrdinal"],
            )
        }
        rebuilt_upstream = [
            value["upstreamManifestEntry"]
            for value in sorted(packages, key=lambda item: item["upstreamManifestOrdinal"])
        ]
        self.assertEqual(ledger, rebuilt_ledger)
        self.assertEqual(metadata, rebuilt_metadata)
        self.assertEqual(catalog, rebuilt_catalog)
        self.assertEqual(upstream, rebuilt_upstream)
        disposition_pages = "\n".join(
            value for path, value in artifacts.items()
            if path.startswith("cohorts/disposition/") and "cohort-" in path
        )
        self.assertEqual(
            sorted(entry["id"] for entry in ledger["entries"]),
            sorted(
                line.split("`")[1]
                for line in disposition_pages.splitlines()
                if line.startswith("- `")
            ),
        )


if __name__ == "__main__":
    unittest.main()

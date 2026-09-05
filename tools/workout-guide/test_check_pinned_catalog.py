"""Exercise the CI checkout path with real local Git/importer fixtures, without network."""

import contextlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import unittest
from unittest.mock import patch

import check_pinned_catalog as checker
import test_import_catalog as fixtures


class CheckPinnedCatalogTest(unittest.TestCase):
    def setUp(self) -> None:
        self.fixture = fixtures.ImportCatalogTest()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.root = self.fixture.root.resolve() / "wallcrawl"
        self.tool = self.root / "tools/workout-guide"
        self.tool.mkdir(parents=True)
        for path in Path(__file__).parent.glob("*.py"):
            shutil.copy2(path, self.tool / path.name)
        for name in ("review-schema.json", "programming-rep-range-schema.json"):
            shutil.copy2(Path(__file__).with_name(name), self.tool / name)
        for path in (self.fixture.config, self.fixture.overrides, self.fixture.reviewed_metadata):
            shutil.copy2(path, self.tool / path.name)
        self.config = self.tool / "import-config.json"
        self.original_config = self.config.read_bytes()
        result = self.fixture._run_import()
        self.assertEqual(0, result.returncode, result.stderr)
        self.bundle = self.root / "app/src/main/assets/workout-guide"
        shutil.copytree(self.fixture.output, self.bundle)
        self.report = self.root / "docs/reviewed-exercise-metadata-review.md"
        self.report.parent.mkdir()
        shutil.copy2(self.fixture.review_report, self.report)
        self.run_process = subprocess.run
        self.checkouts = []
        self.import_started = False

    def run_local(self, command, **kwargs):
        # Only replace the network transport; init, fetch, checkout, status, and
        # the importer execute normally. Production never permits file transport.
        if command[0] == "git":
            checkout = Path(kwargs["cwd"])
            self.checkouts.append(checkout)
            if "fetch" in command:
                self.assertIn(self.fixture.source_commit, command)
                self.assertIn("--depth=1", command)
                command = list(command)
                command[command.index("https://github.com/bryllim/workout-guide")] = str(self.fixture.source)
                kwargs["env"] = {**kwargs["env"], "GIT_ALLOW_PROTOCOL": "file"}
        else:
            self.import_started = True
            self.assertEqual(
                [str(self.tool / "import_catalog.py"), "--source", str(self.checkouts[-1]), "--check"],
                command[1:],
            )
        return self.run_process(command, **kwargs)

    def run_check(self, runner=None):
        stderr = io.StringIO()
        with patch.object(checker.subprocess, "run", side_effect=runner or self.run_local):
            with contextlib.redirect_stderr(stderr):
                result = checker.main(self.root)
        for checkout in self.checkouts:
            self.assertFalse(checkout.exists(), "temporary checkout was not cleaned up")
        return result, stderr.getvalue()

    def snapshot(self):
        return {
            str(path.relative_to(self.root)): (path.read_bytes(), path.stat().st_mtime_ns)
            for path in [*self.bundle.rglob("*"), self.report] if path.is_file()
        }

    def test_checks_configured_commit_and_preserves_all_output_on_success(self):
        # Advance the upstream branch: fetching its default HEAD must fail parity.
        (self.fixture.source / "later.txt").write_text("not part of the pinned commit")
        self.fixture._commit_fixture_change("advance upstream")
        before = self.snapshot()
        result, error = self.run_check()
        self.assertEqual(0, result, error)
        self.assertTrue(self.import_started)
        self.assertEqual(before, self.snapshot())

    def test_catalog_report_and_asset_drift_fail_without_rewriting(self):
        for path in (self.bundle / "catalog.json", self.report, next(self.bundle.rglob("*.svg"))):
            with self.subTest(output=path.name):
                original = path.read_bytes()
                path.write_bytes(original + b"\ndeliberate drift\n")
                before = self.snapshot()
                result, _ = self.run_check()
                self.assertNotEqual(0, result)
                self.assertEqual(before, self.snapshot())
                path.write_bytes(original)

    def test_rejects_missing_malformed_or_ambiguous_config_before_git(self):
        cases = [None, b"", b"{", b"[]", b"null", b"\xff", b'{"schemaVersion":1,"schemaVersion":1}']
        config = json.loads(self.original_config)
        for field in config:
            missing = dict(config)
            del missing[field]
            cases.append(json.dumps(missing).encode())
        for value in cases:
            with self.subTest(config=value):
                if value is None:
                    self.config.unlink(missing_ok=True)
                else:
                    self.config.write_bytes(value)
                result, error = self.run_check(lambda *a, **k: self.fail("Git ran before config validation"))
                self.assertNotEqual(0, result)
                self.assertIn("config", error.lower())

    def test_rejects_unsafe_repository_commit_and_schema_without_echoing_values(self):
        cases = {
            "sourceRepository": [None, 123, "", "https://user:do-not-log@github.com/owner/repo",
                "http://github.com/owner/repo", "https://example.test/owner/repo",
                "https://github.com/owner/repo?do-not-log", "https://github.com/owner/repo#ref",
                "https://github.com/owner/../repo", "https://github.com/owner/repo\n",
                "https://github.com/owner/$(do-not-log)", "ext::do-not-log", "/tmp/repo"],
            "sourceCommit": [None, 1, "", "main", "a" * 39, "A" * 40, "a" * 40 + "\n", "--upload-pack=do-not-log"],
            "schemaVersion": [None, True, 2],
        }
        for field, values in cases.items():
            for value in values:
                with self.subTest(field=field, value=value):
                    config = json.loads(self.original_config)
                    config[field] = value
                    self.config.write_text(json.dumps(config))
                    result, error = self.run_check(lambda *a, **k: self.fail("Git ran with invalid config"))
                    self.assertNotEqual(0, result)
                    self.assertNotIn("do-not-log", error)

    def test_rejects_invalid_paths_counts_and_aliases_before_git(self):
        cases = {
            "manifestPath": [None, 1, "", "../manifest.json", "/manifest.json", "data//manifest.json"],
            "assetBasePath": [None, "", "../assets", "assets\\frames", "assets\nframes"],
            "licenseFiles": [None, "LICENSE", [None], ["../LICENSE"], ["LICENSE", "LICENSE"]],
            "expectedExerciseCount": [None, True, 0, 5001, 1.5],
            "expectedFrameCount": [None, True, 0, 50001, "3"],
            "idAliases": [None, [], {"unsafe/do-not-log": {}},
                {"exercise-bench-press": None}, {"exercise-bench-press": {}},
                {"exercise-bench-press": {"wallCrawlId": "bad/id", "searchAliases": []}},
                {"exercise-bench-press": {"wallCrawlId": "bench-press"}},
                {"exercise-bench-press": {"wallCrawlId": "bench-press", "searchAliases": [None]}},
                {"exercise-bench-press": {"wallCrawlId": "bench-press", "searchAliases": ["Bench", "Bench"]}}],
        }
        for field, values in cases.items():
            for value in values:
                with self.subTest(field=field, value=value):
                    config = json.loads(self.original_config)
                    config[field] = value
                    self.config.write_text(json.dumps(config))
                    result, error = self.run_check(lambda *a, **k: self.fail("Git ran with invalid config"))
                    self.assertNotEqual(0, result)
                    self.assertNotIn("do-not-log", error)

    def test_git_failure_timeout_and_missing_executable_fail_and_cleanup(self):
        for stage in ("init", "fetch", "checkout"):
            for failure in ("exit", "timeout", "missing"):
                with self.subTest(stage=stage, failure=failure):
                    def run(command, **kwargs):
                        if command[0] == "git" and stage in command:
                            self.checkouts.append(Path(kwargs["cwd"]))
                            if failure == "timeout":
                                raise subprocess.TimeoutExpired(command, 180, stderr="do-not-log")
                            if failure == "missing":
                                raise FileNotFoundError("do-not-log")
                            raise subprocess.CalledProcessError(128, command, stderr="do-not-log")
                        return self.run_local(command, **kwargs)
                    result, error = self.run_check(run)
                    self.assertNotEqual(0, result)
                    self.assertIn(stage, error)
                    self.assertNotIn("do-not-log", error)
                    self.assertFalse(self.import_started)

    def test_wrong_checkout_commit_and_dirty_checkout_fail_before_import(self):
        for state in ("wrong commit", "dirty"):
            with self.subTest(state=state):
                def run(command, **kwargs):
                    result = self.run_local(command, **kwargs)
                    if state == "wrong commit" and "rev-parse" in command:
                        return subprocess.CompletedProcess(command, 0, "0" * 40 + "\n", "")
                    if state == "dirty" and "checkout" in command:
                        (Path(kwargs["cwd"]) / "unexpected.txt").write_text("dirty")
                    return result
                result, _ = self.run_check(run)
                self.assertNotEqual(0, result)
                self.assertFalse(self.import_started)

    def test_importer_exit_timeout_and_start_failure_propagate_and_cleanup(self):
        for failure in ("exit", "timeout", "missing"):
            with self.subTest(failure=failure):
                def run(command, **kwargs):
                    if command[0] != "git":
                        if failure == "timeout":
                            raise subprocess.TimeoutExpired(command, 180)
                        if failure == "missing":
                            raise FileNotFoundError("missing interpreter")
                        return subprocess.CompletedProcess(command, 7)
                    return self.run_local(command, **kwargs)
                result, _ = self.run_check(run)
                self.assertEqual(7 if failure == "exit" else 1, result)

    def test_parent_git_configuration_hooks_and_secrets_are_not_forwarded(self):
        marker = self.fixture.root / "hook-ran"
        template = self.fixture.root / "template"
        (template / "hooks").mkdir(parents=True)
        hook = template / "hooks/post-checkout"
        hook.write_text(f'#!/bin/sh\ntouch "{marker}"\n')
        hook.chmod(0o755)
        def run(command, **kwargs):
            environment = kwargs["env"]
            self.assertNotIn("GH_TOKEN", environment)
            self.assertNotIn("GIT_CONFIG_COUNT", environment)
            self.assertNotIn("GIT_TEMPLATE_DIR", environment)
            self.assertNotIn("GIT_DIR", environment)
            self.assertEqual("0", environment["GIT_TERMINAL_PROMPT"])
            self.assertEqual("https", environment["GIT_ALLOW_PROTOCOL"])
            return self.run_local(command, **kwargs)
        with patch.dict(os.environ, {"GH_TOKEN": "do-not-forward", "GIT_TEMPLATE_DIR": str(template),
                "GIT_DIR": "wrong-repo", "GIT_CONFIG_COUNT": "1", "GIT_CONFIG_KEY_0": "credential.helper",
                "GIT_CONFIG_VALUE_0": "!false"}):
            result, error = self.run_check(run)
        self.assertEqual(0, result, error)
        self.assertFalse(marker.exists())


if __name__ == "__main__":
    unittest.main()

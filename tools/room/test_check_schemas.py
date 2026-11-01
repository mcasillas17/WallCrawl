import subprocess
import tempfile
import unittest
from pathlib import Path

from check_schemas import check_generated, repository_schema


class SchemaDriftTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)
        source = self.root / "app/src/main/java/wallcrawl/elopenmike/com/core/database/WallCrawlDatabase.kt"
        source.parent.mkdir(parents=True)
        source.write_text("const val WALLCRAWL_SCHEMA_VERSION = 16\n")
        self.relative = Path("wallcrawl.elopenmike.com.core.database.WallCrawlDatabase/16.json")
        self.committed = self.root / "app/schemas" / self.relative
        self.committed.parent.mkdir(parents=True)
        self.committed.write_text('{"database":{"version":16,"identityHash":"fixture"}}\n')
        self.git("add", ".")
        self.generated = self.root / "generated"
        (self.generated / self.relative).parent.mkdir(parents=True)
        (self.generated / self.relative).write_bytes(self.committed.read_bytes())

    def git(self, *args):
        subprocess.run(["git", "-C", str(self.root), *args], check=True, capture_output=True)

    def test_accepts_matching_current_export_with_historical_artifacts(self):
        older = self.committed.with_name("15.json")
        older.write_text("historical fixture for this guard test only")
        self.git("add", ".")
        expected = repository_schema(self.root)
        self.assertEqual(expected, self.relative)
        check_generated(self.root, self.generated, expected)

    def test_rejects_changed_generated_bytes(self):
        (self.generated / self.relative).write_text("changed")
        with self.assertRaisesRegex(ValueError, "differs"):
            check_generated(self.root, self.generated, self.relative)

    def test_rejects_missing_output_even_when_committed_export_exists(self):
        (self.generated / self.relative).unlink()
        with self.assertRaisesRegex(ValueError, "Generated schema files"):
            check_generated(self.root, self.generated, self.relative)

    def test_rejects_unexpected_generated_version(self):
        (self.generated / self.relative).with_name("17.json").write_text("unexpected")
        with self.assertRaisesRegex(ValueError, "Generated schema files"):
            check_generated(self.root, self.generated, self.relative)

    def test_rejects_missing_current_artifact(self):
        self.committed.unlink()
        with self.assertRaisesRegex(ValueError, "missing"):
            repository_schema(self.root)

    def test_rejects_missing_historical_tracked_artifact(self):
        older = self.committed.with_name("15.json")
        older.write_text("historical fixture for this guard test only")
        self.git("add", ".")
        older.unlink()
        with self.assertRaisesRegex(ValueError, "missing"):
            repository_schema(self.root)

    def test_rejects_untracked_current_artifact(self):
        self.git("rm", "--cached", str(self.committed))
        with self.assertRaisesRegex(ValueError, "tracked"):
            repository_schema(self.root)

    def test_rejects_untracked_version_even_if_git_ignores_it(self):
        (self.root / ".gitignore").write_text("17.json\n")
        self.committed.with_name("17.json").write_text("unexpected")
        with self.assertRaisesRegex(ValueError, "Untracked"):
            repository_schema(self.root)

    def test_new_database_version_requires_its_own_tracked_artifact(self):
        source = self.root / "app/src/main/java/wallcrawl/elopenmike/com/core/database/WallCrawlDatabase.kt"
        source.write_text("const val WALLCRAWL_SCHEMA_VERSION = 17\n")
        with self.assertRaisesRegex(ValueError, "17.json"):
            repository_schema(self.root)


if __name__ == "__main__":
    unittest.main()

"""Regenerate Room's current schema without touching the repository's exports."""

import re
import subprocess
import sys
import tempfile
from pathlib import Path


def repository_schema(root: Path) -> Path:
    database = root / "app/src/main/java/wallcrawl/elopenmike/com/core/database/WallCrawlDatabase.kt"
    versions = re.findall(r"^const val WALLCRAWL_SCHEMA_VERSION = (\d+)$", database.read_text(), re.M)
    if len(versions) != 1:
        raise ValueError("Cannot determine WALLCRAWL_SCHEMA_VERSION")
    expected = Path(f"wallcrawl.elopenmike.com.core.database.WallCrawlDatabase/{versions[0]}.json")
    tracked = {
        Path(name).relative_to("app/schemas")
        for name in subprocess.check_output(
            ["git", "ls-files", "-z", "--", "app/schemas"], cwd=root
        ).decode().split("\0")
        if name.endswith(".json")
    }
    if expected not in tracked:
        raise ValueError(f"Expected export is not tracked: app/schemas/{expected}; generate and git add it")
    present = {path.relative_to(root / "app/schemas") for path in (root / "app/schemas").rglob("*.json")}
    if missing := tracked - present:
        raise ValueError(f"Tracked schema artifacts are missing: {sorted(map(str, missing))}")
    if extra := present - tracked:
        raise ValueError(f"Untracked schema artifacts: {sorted(map(str, extra))}")
    return expected


def check_generated(root: Path, generated: Path, expected: Path) -> None:
    files = {path.relative_to(generated) for path in generated.rglob("*.json")}
    if files != {expected}:
        raise ValueError(f"Generated schema files {sorted(map(str, files))}; expected only {expected}")
    if (generated / expected).read_bytes() != (root / "app/schemas" / expected).read_bytes():
        raise ValueError(f"Generated schema differs from app/schemas/{expected}; regenerate and review the diff")


def main() -> None:
    root = Path(__file__).resolve().parents[2]
    expected = repository_schema(root)
    # Empty output + forced non-incremental processing prevents an old export proving itself.
    with tempfile.TemporaryDirectory(prefix="wallcrawl-room-") as directory:
        subprocess.run(
            [
                str(root / "gradlew"), ":app:kspDebugKotlin",
                f"-Pwallcrawl.roomSchemaDirectory={directory}",
                "-Pksp.incremental=false", "--rerun-tasks", "--no-build-cache", "--no-daemon",
            ],
            cwd=root, check=True,
        )
        check_generated(root, Path(directory), expected)
    print(f"Room schema matches tracked artifact: app/schemas/{expected}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        sys.exit(f"Room schema check failed: {error}")

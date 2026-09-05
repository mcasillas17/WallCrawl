#!/usr/bin/env python3
"""Check committed catalog output against a temporary checkout of the configured pin."""

from __future__ import annotations

import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

from import_catalog import CatalogImportError, _read_object, _require_schema_version


class CatalogCheckError(ValueError):
    """Configuration, checkout, or process failure in the pinned-source check."""


def check_pinned_catalog(repository_root: Path) -> int:
    tool_root = repository_root / "tools/workout-guide"
    config = _read_object(tool_root / "import-config.json", "import config")
    _require_schema_version(config, "import config")
    repository = config.get("sourceRepository")
    commit = config.get("sourceCommit")
    # Only public GitHub HTTPS repository paths are supported. No credentials,
    # ports, query strings, fragments, shell syntax, or alternate Git transports.
    if not isinstance(repository, str) or not re.fullmatch(
        r"https://github\.com/[A-Za-z0-9][A-Za-z0-9_-]{0,99}/[A-Za-z0-9][A-Za-z0-9._-]{0,99}",
        repository,
    ):
        raise CatalogCheckError("import config sourceRepository must be a public GitHub HTTPS repository URL")
    if not isinstance(commit, str) or not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise CatalogCheckError("import config sourceCommit must be a 40-character lowercase Git commit")

    with tempfile.TemporaryDirectory(prefix="wallcrawl-pinned-catalog-") as temporary:
        root = Path(temporary)
        source = root / "source"
        source.mkdir()
        # Do not inherit credentials, URL rewrites, filters, hooks, Git tracing,
        # askpass commands, or environment-injected Git/Python configuration.
        environment = {
            "PATH": os.environ.get("PATH", os.defpath),
            "HOME": temporary,
            "TMPDIR": temporary,
            "LC_ALL": "C",
            "GIT_CONFIG_NOSYSTEM": "1",
            "GIT_CONFIG_GLOBAL": os.devnull,
            "GIT_TERMINAL_PROMPT": "0",
            "GIT_ALLOW_PROTOCOL": "https",
        }

        def git(stage: str, *arguments: str) -> str:
            result = run(
                stage, ["git", *arguments], cwd=source, env=environment,
                capture_output=True, text=True, check=True,
            )
            return result.stdout

        git("git init", "init", "--quiet", "--template=")
        git("git fetch", "-c", "http.followRedirects=false", "fetch", "--quiet", "--no-tags",
            "--depth=1", repository, commit)
        git("git checkout", "checkout", "--quiet", "--detach", commit)
        if git("verify source commit", "rev-parse", "HEAD").strip() != commit:
            raise CatalogCheckError("Source checkout does not match import config sourceCommit")
        if git("verify source cleanliness", "status", "--porcelain", "--untracked-files=all").strip():
            raise CatalogCheckError("Source checkout is not clean")

        print(f"Checking pinned Workout Guide source: {repository} @ {commit}", flush=True)
        return run(
            "importer --check", [sys.executable, str(tool_root / "import_catalog.py"),
                "--source", str(source), "--check"],
            cwd=repository_root, env=environment,
        ).returncode


def run(stage: str, command: list[str], **kwargs) -> subprocess.CompletedProcess:
    try:
        return subprocess.run(command, timeout=180, **kwargs)
    except subprocess.CalledProcessError as error:
        # Raw Git diagnostics can contain remote-controlled content. Name the
        # failing operation and exit code without echoing its output or command.
        raise CatalogCheckError(f"{stage} failed (exit {error.returncode})") from error
    except subprocess.TimeoutExpired as error:
        raise CatalogCheckError(f"{stage} timed out after 180 seconds") from error
    except OSError as error:
        raise CatalogCheckError(f"{stage} could not start or access required files") from error


def main(repository_root: Path | None = None) -> int:
    try:
        return check_pinned_catalog((repository_root or Path(__file__).resolve().parents[2]).resolve())
    except (CatalogCheckError, CatalogImportError, OSError) as error:
        print(f"error: pinned catalog check: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

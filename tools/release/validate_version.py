#!/usr/bin/env python3

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path

MAX_VERSION_CODE = 2_100_000_000
RELEASE_TAG_PATTERN = re.compile(r"v[0-9A-Za-z][0-9A-Za-z._-]*")


class VersionValidationError(ValueError):
    pass


@dataclass(frozen=True)
class ReleaseVersion:
    code: int
    name: str


def derive_release_version(tag: str, run_number: str) -> ReleaseVersion:
    if len(tag) > 128:
        raise VersionValidationError("Release tag exceeds 128 characters")
    if RELEASE_TAG_PATTERN.fullmatch(tag) is None:
        raise VersionValidationError(f"Unsupported release tag: {tag}")
    if re.fullmatch(r"[1-9][0-9]*", run_number) is None:
        raise VersionValidationError("Workflow run number must be a positive integer")

    code = int(run_number)
    if code > MAX_VERSION_CODE:
        raise VersionValidationError(
            f"Workflow run number exceeds Android's maximum versionCode "
            f"{MAX_VERSION_CODE}"
        )

    return ReleaseVersion(code=code, name=tag[1:])


def _read_apk_version(metadata_path: Path) -> ReleaseVersion:
    try:
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    except OSError as error:
        raise VersionValidationError(
            f"Unable to read APK metadata from {metadata_path}: {error}"
        ) from error
    except json.JSONDecodeError as error:
        raise VersionValidationError(
            f"Invalid APK metadata in {metadata_path}: {error}"
        ) from error

    elements = metadata.get("elements") if isinstance(metadata, dict) else None
    if not isinstance(elements, list) or len(elements) != 1:
        raise VersionValidationError(
            "APK metadata must contain exactly one output element"
        )

    element = elements[0]
    if not isinstance(element, dict):
        raise VersionValidationError("APK output metadata must be an object")

    code = element.get("versionCode")
    name = element.get("versionName")
    if not isinstance(code, int) or isinstance(code, bool):
        raise VersionValidationError("APK versionCode must be an integer")
    if not isinstance(name, str) or not name:
        raise VersionValidationError("APK versionName must be a non-empty string")

    return ReleaseVersion(code=code, name=name)


def validate_release_version(
    tag: str,
    run_number: str,
    metadata_path: Path,
) -> ReleaseVersion:
    derived = derive_release_version(tag=tag, run_number=run_number)

    apk = _read_apk_version(metadata_path)
    if apk.code != derived.code:
        raise VersionValidationError(
            f"APK versionCode {apk.code} does not match derived "
            f"versionCode {derived.code}"
        )
    if apk.name != derived.name:
        raise VersionValidationError(
            f"APK versionName {apk.name} does not match derived "
            f"versionName {derived.name}"
        )

    return derived


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Derive and validate WallCrawl Android release metadata."
    )
    parser.add_argument("--tag", required=True)
    parser.add_argument("--run-number", required=True)
    parser.add_argument("--metadata", type=Path)
    args = parser.parse_args()

    try:
        if args.metadata is None:
            version = derive_release_version(
                tag=args.tag,
                run_number=args.run_number,
            )
        else:
            version = validate_release_version(
                tag=args.tag,
                run_number=args.run_number,
                metadata_path=args.metadata,
            )
    except VersionValidationError as error:
        print(error, file=sys.stderr)
        return 1

    if args.metadata is None:
        print(f"version_code={version.code}")
        print(f"version_name={version.name}")
    else:
        print(f"Validated versionCode={version.code} versionName={version.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

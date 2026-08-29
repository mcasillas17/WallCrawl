import json
import tempfile
import unittest
from pathlib import Path

from validate_version import (
    VersionValidationError,
    derive_release_version,
    validate_release_version,
)


class ValidateReleaseVersionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.root = Path(self.temp_dir.name)
        self.metadata_path = self.root / "output-metadata.json"

    def write_metadata(
        self,
        version_code: int = 4,
        version_name: str = "1.0.0-alpha.4",
    ) -> None:
        self.metadata_path.write_text(
            json.dumps(
                {
                    "elements": [
                        {
                            "versionCode": version_code,
                            "versionName": version_name,
                            "outputFile": "app-debug.apk",
                        }
                    ]
                }
            ),
            encoding="utf-8",
        )

    def test_derives_release_version_from_tag_and_workflow_run(self) -> None:
        version = derive_release_version(
            tag="v1.0.0-alpha.4",
            run_number="4",
        )

        self.assertEqual(version.code, 4)
        self.assertEqual(version.name, "1.0.0-alpha.4")

    def test_accepts_matching_generated_apk_metadata(self) -> None:
        self.write_metadata()

        version = validate_release_version(
            tag="v1.0.0-alpha.4",
            run_number="4",
            metadata_path=self.metadata_path,
        )

        self.assertEqual(version.code, 4)
        self.assertEqual(version.name, "1.0.0-alpha.4")

    def test_rejects_unsupported_release_tag(self) -> None:
        with self.assertRaisesRegex(
            VersionValidationError,
            "Unsupported release tag: 1.0.0-alpha.4",
        ):
            derive_release_version(
                tag="1.0.0-alpha.4",
                run_number="4",
            )

    def test_rejects_release_tag_too_long_for_asset_name(self) -> None:
        tag = f"v{'a' * 128}"

        with self.assertRaisesRegex(
            VersionValidationError,
            "Release tag exceeds 128 characters",
        ):
            derive_release_version(
                tag=tag,
                run_number="4",
            )

    def test_rejects_apk_metadata_that_does_not_match_derived_version(self) -> None:
        self.write_metadata(version_code=3)

        with self.assertRaisesRegex(
            VersionValidationError,
            "APK versionCode 3 does not match derived versionCode 4",
        ):
            validate_release_version(
                tag="v1.0.0-alpha.4",
                run_number="4",
                metadata_path=self.metadata_path,
            )

    def test_rejects_non_positive_workflow_run_number(self) -> None:
        with self.assertRaisesRegex(
            VersionValidationError,
            "Workflow run number must be a positive integer",
        ):
            derive_release_version(
                tag="v1.0.0-alpha.4",
                run_number="0",
            )


if __name__ == "__main__":
    unittest.main()

"""Conspicuously fictional catalog fixtures; no clinical or exercise approval."""
import copy
import json
from pathlib import Path
import tempfile
import unittest

from validate_catalog_supplement import validate_supplement
from import_catalog import CatalogImportError

ROOT = Path(__file__).resolve().parents[2]


class SupplementTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.assets = Path(self.temporary.name)
        self.catalog = json.loads((ROOT / "app/src/androidTest/assets/wallcrawl/fictional-catalog.json").read_text())
        self.upstream = json.loads((ROOT / "app/src/main/assets/workout-guide/catalog.json").read_text())
        self.overlay = json.loads((ROOT / "app/src/main/assets/localization/exercise-localization.json").read_text())
        self.overlay["exercises"] = {entry["id"]: self.overlay["exercises"][entry["id"]] for entry in self.upstream["exercises"]}
        self.overlay["exercises"]["fictional-catalog-movement"] = {"es": {"name": "Movimiento FICTICIO", "coachingSummary": "Texto FICTICIO; sin aprobación clínica."}}
        for index in range(1, 4):
            path = self.assets / f"exercise-illustrations/male/fictional-catalog-movement/frame-{index}.svg"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('<svg xmlns="http://www.w3.org/2000/svg" width="320" height="320"><text>FICTIONAL TEST FIXTURE ONLY</text></svg>')

    def validate(self):
        return validate_supplement(self.catalog, self.upstream, self.overlay, self.assets)

    def test_valid_complete_fictional_fixture(self):
        self.assertEqual(self.validate(), (1, 3))

    def test_condition_anatomy_and_support_requirements_are_canonical(self):
        entry = self.catalog["exercises"][0]
        entry["primaryMuscles"] = ["Neck"]
        entry["secondaryMuscles"] = ["Foot Intrinsics"]
        entry["listedEquipment"] = ["Bodyweight", "Padded Plinth", "Calf Support"]
        self.assertEqual(self.validate(), (1, 3))

    def test_empty_production_supplement(self):
        self.catalog["exercises"] = []
        self.overlay["exercises"].pop("fictional-catalog-movement")
        self.assertEqual(self.validate(), (0, 0))

    def test_source_and_visual_contracts(self):
        for field, value in [("id", "workout-guide"), ("version", 2), ("version", True), ("commit", "a" * 40)]:
            with self.subTest(field=field, value=value):
                original = copy.deepcopy(self.catalog)
                self.catalog["source"][field] = value
                with self.assertRaises(CatalogImportError): self.validate()
                self.catalog = original
        self.catalog["visuals"]["assetPathPrefix"] = "../workout-guide/"
        with self.assertRaises(CatalogImportError): self.validate()

    def test_cross_source_and_local_duplicates(self):
        entry = self.catalog["exercises"][0]
        for field in ["id", "sourceId", "sourceSlug"]:
            with self.subTest(field=field):
                original = entry[field]
                entry[field] = self.upstream["exercises"][0][field]
                with self.assertRaises(CatalogImportError): self.validate()
                entry[field] = original
        self.catalog["exercises"].append(copy.deepcopy(entry))
        with self.assertRaises(CatalogImportError): self.validate()

    def test_traversal_and_unknown_vocabulary(self):
        entry = self.catalog["exercises"][0]
        for field, value in [("sourceSlug", "../escape"), ("primaryMuscles", ["Unknown"]), ("secondaryMuscles", ["Unknown"]), ("listedEquipment", ["Unknown"])]:
            with self.subTest(field=field):
                original = entry[field]
                entry[field] = value
                with self.assertRaises(CatalogImportError): self.validate()
                entry[field] = original

    def test_translated_unknown_equipment_is_still_rejected(self):
        self.overlay["vocabulary"]["equipment"]["Fictional unknown equipment"] = {"es": "Objeto ficticio"}
        self.catalog["exercises"][0]["listedEquipment"] = ["Fictional unknown equipment"]
        with self.assertRaises(CatalogImportError): self.validate()

    def test_absent_empty_and_symlink_art(self):
        path = self.assets / "exercise-illustrations/male/fictional-catalog-movement/frame-3.svg"
        path.unlink()
        with self.assertRaises(CatalogImportError): self.validate()
        path.write_text("")
        with self.assertRaises(CatalogImportError): self.validate()
        path.unlink()
        path.symlink_to(path.with_name("frame-1.svg"))
        with self.assertRaises(CatalogImportError): self.validate()

    def test_localization_names_summaries_alias_limits_all_languages(self):
        local = self.overlay["exercises"]["fictional-catalog-movement"]["es"]
        for field, value in [("name", ""), ("name", "x" * 161), ("coachingSummary", ""), ("coachingSummary", "x" * 1001), ("aliases", ["x"] * 13)]:
            with self.subTest(field=field):
                original = copy.deepcopy(local)
                local[field] = value
                with self.assertRaises(CatalogImportError): self.validate()
                local.clear(); local.update(original)
        self.overlay["languages"].append("fr")
        with self.assertRaises(CatalogImportError): self.validate()

    def test_neutral_guidance_required_without_strength_programming(self):
        entry = self.catalog["exercises"][0]
        entry.pop("executionGuidance")
        with self.assertRaises(CatalogImportError): self.validate()


if __name__ == "__main__": unittest.main()

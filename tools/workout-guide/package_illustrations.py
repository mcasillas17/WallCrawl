#!/usr/bin/env python3
"""Copy hash-verified selected SVGs into the app; never run image generation or change pilot art."""
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
PILOT = ROOT / "art/pilots/female-exercises"
DESTINATION = ROOT / "app/src/main/assets/exercise-illustrations"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def collect():
    catalog_path = ROOT / "app/src/main/assets/workout-guide/catalog.json"
    catalog = json.loads(catalog_path.read_text())
    index = {"schemaVersion": 1, "exercises": {e["id"]: {} for e in catalog["exercises"]}}
    provenance = {
        "schemaVersion": 1,
        "source": catalog["source"],
        "license": "CC BY-SA 4.0",
        "changes": "Native Codex Imagegen reference edits; female and male demonstrators, documented anatomy/equipment/continuity corrections, white alpha-normalized linework traced using Sharp/Potrace/SVGO. Original assets remain unchanged.",
        "qualifiedMovementReview": False,
        "playbackPolicy": "Play all three selected frames, including unresolved continuity drafts, per user instruction. Corrections are deferred to the next artwork iteration.",
        "frames": [],
    }
    male_review = json.loads((PILOT / "male-catalog/previews/sequence-playback-manifest.json").read_text())
    male_status = {s["exerciseId"]: s["sequenceStatus"] for s in male_review["sequences"]}
    files = {}
    for variant, folder in [("female", "catalog"), ("male", "male-catalog")]:
        ledger = json.loads((PILOT / folder / "provenance.json").read_text())
        assert ledger["sourceCommit"] == catalog["source"]["commit"], "Artwork source pin differs"
        frames = {(f["exerciseId"], f["frameNumber"]): f for f in ledger["frames"]}
        assert len(frames) == len(catalog["exercises"]) * 3, "Duplicate or missing ledger frames"
        for exercise in catalog["exercises"]:
            paths = []
            for number in range(1, 4):
                frame = frames[(exercise["id"], number)]
                assert frame["status"] in {"svg-produced-ai-reviewed", "user-approved-reused"}, frame["stem"]
                trace = frame.get("pilotProcessing", {}).get("trace", {})
                source_path = frame.get("svg") or trace["svg"]
                expected = frame.get("svgSha256") or frame.get("artifactValidation", {}).get("svgSha256") or trace["svgSha256"]
                data = (PILOT / folder / source_path).read_bytes()
                assert digest(data) == expected, f"Selected SVG hash mismatch: {frame['stem']}"
                svg = ET.fromstring(data)
                assert svg.attrib.get("viewBox") == "0 0 512 512", frame["stem"]
                assert all(e.tag.rsplit("}", 1)[-1] in {"svg", "path", "g"} for e in svg.iter()), frame["stem"]
                assert any(e.tag.endswith("path") and e.attrib.get("d") for e in svg.iter()), frame["stem"]
                path = f"{variant}/{exercise['id']}/frame-{number}.svg"
                files[path] = data
                paths.append(f"exercise-illustrations/{path}")
                provenance["frames"].append({
                    "exerciseId": exercise["id"], "variant": variant, "frameNumber": number,
                    "path": path, "sha256": expected, "source": frame["source"],
                    "pilotLedger": f"art/pilots/female-exercises/{folder}/provenance.json",
                    "pilotStem": frame["stem"], "pilotSvg": source_path,
                    "reviewStatus": frame["status"],
                    "sequenceReviewStatus": male_status.get(exercise["id"], "unreviewed") if variant == "male" else "full-animation-review-not-completed",
                    "changeNotice": frame.get("sourceCorrectionNotice") or frame.get("aiSvgVisualReview") or "Reused user-approved female pilot frame.",
                })
            index["exercises"][exercise["id"]][variant] = paths
    files["index.json"] = (json.dumps(index, indent=2, sort_keys=True) + "\n").encode()
    files["provenance.json"] = (json.dumps(provenance, indent=2, sort_keys=True) + "\n").encode()
    for name in ["ATTRIBUTION.md", "LICENSE-ASSETS"]:
        files[name] = (ROOT / "app/src/main/assets/workout-guide" / name).read_bytes()
    files["NOTICE.md"] = (
        "# WallCrawl exercise illustration adaptations\n\n"
        "Original pose-artwork reference: [Everkinetic](https://github.com/everkinetic/data). "
        "Additional exercises and animation frames were contributed by [Bryl Lim](https://bryllim.com). "
        "WallCrawl adapted that combined source catalog; not every frame is an Everkinetic original.\n\n"
        "Original attribution, per-exercise source URLs and prior change notices are preserved in ATTRIBUTION.md and provenance.json.\n\n"
        "## Source history\n\n"
        "The source catalog is Workout Guide by Bryl Lim, revision "
        + catalog["source"]["commit"] + ": " + catalog["source"]["repository"] + "/tree/" + catalog["source"]["commit"] + "\n\n"
        "## License and changes\n\n"
        "These adaptations are licensed under CC BY-SA 4.0: https://creativecommons.org/licenses/by-sa/4.0/ . "
        "The app code's MIT license does not apply to these assets. No endorsement is implied.\n\n"
        "Changes (2026): Native Codex Imagegen reference editing created adult female and male demonstrators in a matching white line style. "
        "Documented anatomy, equipment and perspective corrections were applied. Raster linework was converted to alpha, normalized, "
        "and traced using Sharp, Potrace and SVGO. Pinned originals were preserved. Selected SVGs are copied byte-for-byte; "
        "masters and full generation records are retained in the local production workspace at art/pilots/female-exercises, "
        "outside the integration commit and app distribution.\n\n"
        "The three-frame sequences are current artwork drafts. Some animation continuity issues remain for a later iteration. "
        "AI visual review is not qualified movement-expert approval.\n"
    ).encode()
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify packaged assets without writing")
    args = parser.parse_args()
    files = collect()  # Complete validation before the first destination write.
    for relative, data in files.items():
        path = DESTINATION / relative
        if args.check:
            assert path.is_file() and path.read_bytes() == data, f"Outdated or missing packaged asset: {relative}"
        elif not path.exists() or path.read_bytes() != data:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    expected = {DESTINATION / p for p in files}
    assert set(DESTINATION.rglob("*.svg")) == {p for p in expected if p.suffix == ".svg"}, "Unexpected stale SVGs"
    print(json.dumps({"svgCount": sum(p.endswith('.svg') for p in files), "svgBytes": sum(len(data) for p, data in files.items() if p.endswith('.svg')), "mode": "verified" if args.check else "packaged"}))


if __name__ == "__main__":
    main()

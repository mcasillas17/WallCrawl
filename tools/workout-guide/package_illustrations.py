#!/usr/bin/env python3
"""Copy hash-verified selected SVGs into the app; never run image generation or change pilot art."""
import argparse
import hashlib
import json
import math
import re
import struct
import zlib
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
        "playbackPolicy": "Play all three selected frames per user instruction. Sequence review status and remaining visual limitations are recorded per exercise.",
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
        "The three-frame sequences have the review status recorded in provenance.json. AI-reviewed sequences may retain minor contour, alignment or phase-spacing variations. "
        "AI visual review is not qualified movement-expert approval.\n"
    ).encode()
    return files


# New authoring lineage is asset-root relative; historical collect() keeps its old contract.
SUPPLEMENT_PROVENANCE = "wallcrawl/illustration-provenance.json"
MAX_FILE_BYTES = 16 * 1024 * 1024
# Imported metadata is immutable; its provenance pins all 1812 historical SVG digests.
LEGACY_METADATA_SHA256 = {
    "provenance.json": "75630fa5b28e0769d14b018b929e4f058307f3a1241a182bb95e1e85b00638e8",
    "NOTICE.md": "33a13bea751c983a5ca61800569a1901895ed931460ddbe2d248fb7523519e1f",
    "ATTRIBUTION.md": "0b0918808512e571072a976c4518b345f829425ab395acac6c9dbc3ec522a96b",
    "LICENSE-ASSETS": "b25de3faf3d7f163f842cb0e2c069da652705f738faa4c6232d45ed36fc29eb3",
}
SAFE_ID = re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*")
SHA256 = re.compile(r"[0-9a-f]{64}")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def safe_path(root: Path, relative: str) -> Path:
    require(isinstance(relative, str) and 0 < len(relative) <= 240, "Invalid bounded asset path")
    require(not relative.startswith("/") and "\\" not in relative and all(
        part not in {"", ".", ".."} and re.fullmatch(r"[A-Za-z0-9_.-]+", part)
        for part in relative.split("/")), "Unsafe asset path")
    path = root / relative
    require(not root.is_symlink() and all(not part.is_symlink() for part in [path, *path.parents] if part == root or root in part.parents), "Symlink inside owned root")
    require(path.resolve().is_relative_to(root.resolve()), "Path escapes owned root")
    return path


def read_bytes(path: Path) -> bytes:
    require(path.is_file() and 0 < path.stat().st_size <= MAX_FILE_BYTES, f"Missing, empty or oversized file: {path}")
    return path.read_bytes()


def read_json(data: bytes) -> dict:
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, "Duplicate JSON key")
            result[key] = value
        return result
    value = json.loads(data, object_pairs_hook=unique)
    require(isinstance(value, dict), "Expected JSON object")
    return value


def json_bytes(value) -> bytes:
    return (json.dumps(value, indent=2, sort_keys=True) + "\n").encode()


NUMBER = r"[-+]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[eE][-+]?[0-9]+)?"


def validate_path_geometry(value):
    tokens = re.findall(r"[MmZzLlHhVvCcSsQqTtAa]|" + NUMBER, value)
    require(tokens and tokens[0] in {"M", "m"} and not re.sub(r"[MmZzLlHhVvCcSsQqTtAa]|" + NUMBER + r"|[\s,]", "", value), "Invalid SVG path tokens")
    arity = {"m": 2, "l": 2, "h": 1, "v": 1, "c": 6, "s": 4, "q": 4, "t": 2, "a": 7, "z": 0}
    index, drawable = 0, False
    while index < len(tokens):
        command = tokens[index].lower()
        require(command in arity, "Missing SVG path command")
        index += 1
        start = index
        while index < len(tokens) and tokens[index].lower() not in arity:
            require(math.isfinite(float(tokens[index])), "Nonfinite SVG coordinate")
            index += 1
        count = index - start
        size = arity[command]
        require(count == 0 if size == 0 else count >= size and count % size == 0, "Incomplete SVG path command")
        if command == "a":
            for offset in range(start, index, 7):
                require(float(tokens[offset]) >= 0 and float(tokens[offset + 1]) >= 0 and tokens[offset + 3] in {"0", "1"} and tokens[offset + 4] in {"0", "1"}, "Invalid SVG arc")
        drawable |= command not in {"m", "z"} or command == "m" and count > 2
    require(drawable, "SVG path has no drawing commands")


def validate_transform(value):
    require(not re.sub(r"(?:matrix|translate|scale|rotate|skewX|skewY)\s*\([^()]+\)|[\s,]", "", value), "Invalid SVG transform")
    for name, arguments in re.findall(r"([A-Za-z]+)\s*\(([^()]+)\)", value):
        require(not re.sub(NUMBER + r"|[\s,]", "", arguments), "Invalid transform number")
        numbers = re.findall(NUMBER, arguments)
        require(len(numbers) in {"matrix": {6}, "translate": {1, 2}, "scale": {1, 2}, "rotate": {1, 3}, "skewX": {1}, "skewY": {1}}[name] and all(math.isfinite(float(number)) for number in numbers), "Invalid transform arguments")


def validate_svg(data: bytes) -> None:
    require(len(data) <= 4 * 1024 * 1024 and b"<!" not in data, "Oversized SVG or forbidden declaration")
    try:
        svg = ET.fromstring(data)
    except ET.ParseError as error:
        raise ValueError("Malformed SVG") from error
    require(svg.tag == "{http://www.w3.org/2000/svg}svg" and svg.attrib.get("viewBox") == "0 0 512 512", "SVG must have a 512x512 viewBox")
    allowed = {"svg": {"width", "height", "viewBox"}, "g": {"transform"}, "path": {"fill", "fill-rule", "d", "transform"}}
    paths = 0
    for element in svg.iter():
        tag = element.tag.removeprefix("{http://www.w3.org/2000/svg}")
        require(element.tag.startswith("{http://www.w3.org/2000/svg}") and tag in allowed and set(element.attrib) <= allowed[tag], "SVG must contain only safe path geometry")
        require(not (element.text or "").strip() and not (element.tail or "").strip(), "Unexpected SVG text")
        if "transform" in element.attrib:
            validate_transform(element.attrib["transform"])
        if tag == "svg":
            require(element is svg, "Nested SVG is forbidden")
            require(all(element.attrib.get(key, "512") == "512" for key in ["width", "height"]), "Invalid SVG dimensions")
        if tag == "path":
            require(element.attrib.get("fill", "").lower() in {"#fff", "#ffffff", "white"}, "Paths must be white")
            require(element.attrib.get("fill-rule", "nonzero") in {"nonzero", "evenodd"}, "Invalid path fill rule")
            geometry = element.attrib.get("d", "")
            validate_path_geometry(geometry)
            paths += 1
    require(paths > 0, "SVG has no paths")


def validate_png(data):
    require(data.startswith(b"\x89PNG\r\n\x1a\n"), "Master must be PNG")
    position, chunks, compressed = 8, [], bytearray()
    dimensions = None
    while position < len(data):
        require(position + 12 <= len(data) and len(chunks) < 4096, "Truncated or oversized PNG chunks")
        size = int.from_bytes(data[position:position + 4], "big")
        kind = data[position + 4:position + 8]
        end = position + 8 + size
        require(end + 4 <= len(data), "Truncated PNG chunk")
        payload = data[position + 8:end]
        require(zlib.crc32(kind + payload) == int.from_bytes(data[end:end + 4], "big"), "PNG chunk CRC mismatch")
        if not chunks:
            require(kind == b"IHDR" and size == 13, "Missing PNG header")
            width, height, depth, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", payload)
            require(0 < width <= 8192 and 0 < height <= 8192 and width * height <= 16777216 and compression == filtering == 0, "Invalid bounded PNG header")
            require(color == 6 and depth == 8 and interlace == 0, "Retained PNG masters must use noninterlaced RGBA8")
            dimensions = width, height
        require(kind not in {b"acTL", b"fcTL", b"fdAT"}, "Animated PNG is forbidden")
        require(kind != b"IHDR" or not chunks, "Duplicate PNG header")
        if kind == b"IDAT": compressed.extend(payload)
        chunks.append(kind)
        position = end + 4
        if kind == b"IEND":
            require(size == 0 and position == len(data), "Trailing PNG content")
            break
    require(dimensions and chunks[-1] == b"IEND" and compressed, "Incomplete PNG")
    width, height = dimensions
    scanline_bytes = 1 + width * 4
    expected = scanline_bytes * height
    try:
        decoder = zlib.decompressobj()
        raw = decoder.decompress(compressed, expected + 1)
        require(len(raw) == expected and decoder.eof and not decoder.unused_data, "Invalid PNG scanline length or deflate stream")
        require(all(raw[offset] <= 4 for offset in range(0, expected, scanline_bytes)), "Invalid PNG scanline filter")
    except zlib.error as error:
        raise ValueError("Malformed PNG image data") from error


def verified_file(root, descriptor):
    require(isinstance(descriptor, dict) and set(descriptor) == {"path", "sha256"}, "Invalid file descriptor")
    expected = descriptor["sha256"]
    require(isinstance(expected, str) and bool(SHA256.fullmatch(expected)), "Invalid SHA-256")
    data = read_bytes(safe_path(root, descriptor["path"]))
    require(digest(data) == expected, f"Lineage digest mismatch: {descriptor['path']}")
    return data


def validate_source(source):
    require(isinstance(source, dict) and source.get("id") == "wallcrawl" and source.get("version") == 1 and "commit" not in source, "Expected distinct WallCrawl source lineage")
    require(isinstance(source.get("repository"), str) and source["repository"].startswith("https://"), "Missing source repository")
    require(bool(source.get("assetLicense")) and isinstance(source.get("attribution"), dict), "Missing authored attribution/license")


def collect_supplement(ledger_path: Path) -> dict[str, bytes]:
    """Validate retained source/master/trace lineage; return only asset-root-relative app files."""
    ledger_path = Path(ledger_path)
    ledger = read_json(read_bytes(ledger_path))
    require(ledger.get("schemaVersion") == 1, "Unknown supplement art schema")
    validate_source(ledger.get("source"))
    frames = ledger.get("frames")
    require(isinstance(frames, list) and len(frames) <= 30000, "Invalid bounded frame ledger")
    files, entries, identities = {}, [], set()
    for frame in frames:
        require(isinstance(frame, dict), "Invalid frame")
        exercise_id, variant, number = frame.get("exerciseId"), frame.get("variant"), frame.get("frameNumber")
        require(isinstance(exercise_id, str) and len(exercise_id) <= 100 and SAFE_ID.fullmatch(exercise_id), "Unsafe exercise ID")
        require(variant in {"male", "female"} and type(number) is int and 1 <= number <= 3, "Invalid variant or slot")
        identity = (exercise_id, variant, number)
        require(identity not in identities, "Duplicate frame slot")
        identities.add(identity)
        inputs = {key: verified_file(ledger_path.parent, frame.get(key)) for key in ["source", "master", "derived", "trace", "generation"]}
        validate_png(inputs["master"])
        validate_svg(inputs["derived"])
        trace, generation = read_json(inputs["trace"]), read_json(inputs["generation"])
        require(trace.get("schemaVersion") == 1 and trace.get("inputSha256") == frame["master"]["sha256"] and trace.get("outputSha256") == frame["derived"]["sha256"], "Trace does not bind master and derived SVG")
        versions, options = trace.get("toolVersions"), trace.get("options")
        require(isinstance(versions, dict) and all(isinstance(versions.get(key), str) and versions[key] for key in ["sharp", "potrace", "svgo", "node"]) and isinstance(options, dict) and options, "Missing effective trace versions/options")
        require(generation.get("tool") == "native-imagegen" and isinstance(generation.get("prompt"), str) and generation["prompt"].strip() and generation.get("outputSha256") == frame["master"]["sha256"], "Generation record must bind the retained master")
        asset_path = f"exercise-illustrations/{variant}/{exercise_id}/frame-{number}.svg"
        files[asset_path] = inputs["derived"]
        entries.append({"exerciseId": exercise_id, "variant": variant, "frameNumber": number, "path": asset_path, "sha256": frame["derived"]["sha256"],
                        "sourceSha256": frame["source"]["sha256"], "masterSha256": frame["master"]["sha256"], "generationRecordSha256": frame["generation"]["sha256"],
                        "traceRecordSha256": frame["trace"]["sha256"], "toolVersions": versions, "options": options})
    for exercise_id in {identity[0] for identity in identities}:
        require({(variant, number) for current, variant, number in identities if current == exercise_id} == {(variant, number) for variant in ["male", "female"] for number in range(1, 4)}, "Both variants need all three slots")
    files[SUPPLEMENT_PROVENANCE] = json_bytes({"schemaVersion": 1, "source": ledger["source"], "frames": sorted(entries, key=lambda frame: (frame["exerciseId"], frame["variant"], frame["frameNumber"]))})
    return files


def bundled_files(destination):
    files = {}
    require(destination.is_dir() and not destination.is_symlink(), "Missing or symlink illustration root")
    for path in destination.rglob("*"):
        require(not path.is_symlink(), "Symlink packaged asset")
        if path.is_file():
            relative = "exercise-illustrations/" + path.relative_to(destination).as_posix()
            files[relative] = read_bytes(safe_path(destination.parent, relative))
    path = safe_path(destination.parent, SUPPLEMENT_PROVENANCE)
    files[SUPPLEMENT_PROVENANCE] = read_bytes(path)
    return files


def validate_bundle(files, assets):
    for name, sha256 in LEGACY_METADATA_SHA256.items():
        require(digest(files.get("exercise-illustrations/" + name, b"")) == sha256, f"Changed historical metadata: {name}")
    upstream = read_json(read_bytes(safe_path(assets, "workout-guide/catalog.json")))
    supplement = read_json(read_bytes(safe_path(assets, "wallcrawl/catalog.json")))
    old_ids = {entry["id"] for entry in upstream["exercises"]}
    new_ids = {entry["id"] for entry in supplement["exercises"]}
    require(not old_ids & new_ids and len(new_ids) == len(supplement["exercises"]), "Duplicate supplement art IDs")
    index = read_json(files["exercise-illustrations/index.json"])
    require(index.get("schemaVersion") == 1 and set(index["exercises"]) == old_ids | new_ids, "Artwork index must cover exactly the merged catalog")
    legacy = read_json(files["exercise-illustrations/provenance.json"])
    local = read_json(files[SUPPLEMENT_PROVENANCE])
    require(legacy.get("schemaVersion") == 1 and legacy.get("source") == upstream["source"], "Historical artwork source mismatch")
    require(local.get("schemaVersion") == 1 and local.get("source") == supplement["source"], "Supplement artwork source mismatch")
    validate_source(local["source"])
    expected = {"exercise-illustrations/" + name for name in ["index.json", "provenance.json", "NOTICE.md", "LICENSE-ASSETS", "ATTRIBUTION.md"]} | {SUPPLEMENT_PROVENANCE}
    for provenance, ids, prefix in [(legacy, old_ids, "exercise-illustrations/"), (local, new_ids, "")]:
        identities = set()
        for frame in provenance["frames"]:
            identity = (frame["exerciseId"], frame["variant"], frame["frameNumber"])
            require(identity not in identities, "Duplicate packaged provenance slot")
            identities.add(identity)
            exercise_id, variant, number = identity
            require(exercise_id in ids and variant in {"male", "female"} and type(number) is int and 1 <= number <= 3, "Unknown packaged provenance slot")
            path = f"exercise-illustrations/{variant}/{exercise_id}/frame-{number}.svg"
            require(prefix + frame["path"] == path, "Provenance path mismatch")
            data = files.get(path)
            require(data is not None and digest(data) == frame["sha256"], f"Packaged SVG digest mismatch: {path}")
            validate_svg(data)
            expected.add(path)
            if not prefix:
                require(all(isinstance(frame.get(key), str) and SHA256.fullmatch(frame[key]) for key in ["sourceSha256", "masterSha256", "generationRecordSha256", "traceRecordSha256"]), "Invalid compact lineage digest")
                require(isinstance(frame.get("toolVersions"), dict) and isinstance(frame.get("options"), dict), "Missing compact processing metadata")
        require(identities == {(exercise_id, variant, number) for exercise_id in ids for variant in ["male", "female"] for number in range(1, 4)}, "Incomplete packaged variants")
    for exercise_id, variants in index["exercises"].items():
        require(set(variants) == {"male", "female"}, "Missing indexed variant")
        for variant, paths in variants.items():
            require(paths == [f"exercise-illustrations/{variant}/{exercise_id}/frame-{number}.svg" for number in range(1, 4)], "Invalid indexed sequence")
    require(set(files) == expected, "Unexpected stale or missing packaged files")


def validate_bundled_illustrations(destination: Path) -> None:
    """Clone-safe integrity check against shipped provenance/index; no historical workspace needed."""
    destination = Path(destination)
    try:
        validate_bundle(bundled_files(destination), destination.parent)
    except (KeyError, TypeError) as error:
        raise ValueError("Malformed bundled artwork metadata") from error


def package_supplement(ledger_path: Path, destination: Path, check=False) -> None:
    files = collect_supplement(ledger_path)
    current = bundled_files(destination)
    index = read_json(current["exercise-illustrations/index.json"])
    legacy_ids = {frame["exerciseId"] for frame in read_json(current["exercise-illustrations/provenance.json"])["frames"]}
    provenance = read_json(files[SUPPLEMENT_PROVENANCE])
    ids = {frame["exerciseId"] for frame in provenance["frames"]}
    require(not ids & legacy_ids, "Supplement cannot overwrite historical art")
    # Complete ledgers replace only the local index entries; stale former assets fail validation.
    index["exercises"] = {exercise_id: variants for exercise_id, variants in index["exercises"].items() if exercise_id in legacy_ids}
    for exercise_id in ids:
        index["exercises"][exercise_id] = {variant: [f"exercise-illustrations/{variant}/{exercise_id}/frame-{number}.svg" for number in range(1, 4)] for variant in ["male", "female"]}
    files["exercise-illustrations/index.json"] = json_bytes(index)
    validate_bundle(current | files, destination.parent)  # Validate every input/output before writes.
    targets = [(safe_path(destination.parent, relative), data) for relative, data in files.items()]
    for path, data in targets:
        if check:
            require(path.is_file() and path.read_bytes() == data, f"Outdated or missing packaged asset: {path}")
    if not check:
        for path, data in targets:
            if not path.exists() or path.read_bytes() != data:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--check", action="store_true", help="Verify outputs without writing; old workspace unless a supplement ledger is given")
    modes.add_argument("--check-bundled", action="store_true", help="Check shipped bytes/index/provenance without historic generation ledgers")
    parser.add_argument("--supplement-ledger", type=Path, help="Package complete retained WallCrawl artwork lineage additively")
    args = parser.parse_args()
    if args.check_bundled:
        if args.supplement_ledger:
            parser.error("--check-bundled cannot take --supplement-ledger")
        validate_bundled_illustrations(DESTINATION)
    elif args.supplement_ledger:
        package_supplement(args.supplement_ledger, DESTINATION, args.check)
    else:
        files = collect()  # Old full-workspace mode remains distinct.
        expected = {DESTINATION / relative for relative in files}
        require(set(DESTINATION.rglob("*.svg")) <= {path for path in expected if path.suffix == ".svg"}, "Unexpected stale SVGs")
        for relative, data in files.items():
            path = safe_path(DESTINATION, relative)
            if args.check:
                require(path.is_file() and path.read_bytes() == data, f"Outdated or missing packaged asset: {relative}")
        if not args.check:
            for relative, data in files.items():
                path = safe_path(DESTINATION, relative)
                if not path.exists() or path.read_bytes() != data:
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(data)
    print(json.dumps({"mode": "verified" if args.check or args.check_bundled else "packaged"}))


if __name__ == "__main__":
    main()

# Condition artwork authoring (Task6A)

This isolated package is offline authoring tooling, never an Android dependency. It does not generate artwork, approve movement/clinical content, authenticate a generator/reviewer, or establish asset rights.

```sh
npm ci --prefix tools/workout-guide/authoring --ignore-scripts
node tools/workout-guide/trace_condition_art.mjs --self-test
node tools/workout-guide/test_trace_condition_art.mjs
node tools/workout-guide/trace_condition_art.mjs /absolute/retained/master.png /absolute/existing-directory/new-frame.svg
```

Use a fresh SVG filename: existing SVG/trace-record files and symlinks are rejected. The PNG is read without modification. Sharp decodes bounded PNG bytes and derives a separate binary mask from alpha≥128 at source resolution (upscaling only when the longest side is below 512). Potrace traces those samples before its paths are scaled and centered inside the transparent 512×512 viewBox. The mask is bounded to 16,777,216 pixels; source dimensions/resize/path scale/offset are recorded per input. RGB is ignored; low-alpha pixels are excluded. Potrace receives a bounded raw Jimp bitmap, never a URL, file path or encoded input. SVGO optimizes the white path geometry. The adjacent `<output>.trace.json` records actual installed versions, input/output digests and exact effective options. Alpha thresholding can lose faint contours; inspect the real composited result and movement sequence before selecting art.

The self-check uses an artificial hollow square and alpha noise, never a substitute exercise pose. It checks determinism, mask/trace overlap, transparent holes, low-alpha noise, RGB independence and unchanged input PNG bytes. The separate boundary checks exercise thin artificial 1254×1254 curves at a 320 pixel render, nonsquare centering/aspect, malformed/empty/opaque PNGs, unsafe output paths, overwrite rejection and real file/sidecar output. The thin-curve test catches detail loss from rasterizing at 512 before tracing.

Sharp 0.35.5, Potrace 2.1.8 and SVGO 4.1.0 are pinned in package.json/package-lock.json. Sharp's native library versions and Node version are also recorded because cross-platform output is not assumed byte-identical. Installed API references: [Sharp output](https://sharp.pixelplumbing.com/api-output/), [Sharp channels](https://sharp.pixelplumbing.com/api-channel/), [Potrace](https://github.com/tooolbox/node-potrace), [SVGO Node API](https://svgo.dev/docs/usage/).

`npm audit` currently reports GHSA-x565-32qp-m3vf through Potrace→Jimp→@jimp/custom→@jimp/core→phin. The advisory concerns sensitive HTTP headers across redirects; the tracer passes only a raw bitmap into this dependency and does not call its URL/encoded input route. The vulnerable dependency remains installed. No package is published, and an incompatible downgrade suggested by npm is not applied.

## Retained ledger and additive packaging

For a three-position sequence drawn as one transparent PNG, retain the original strip and
extract its three cells into an empty directory with the pinned Sharp installation:

```sh
node tools/workout-guide/split_condition_strip.mjs native.png path/to/empty/masters
```

Each cell spans the full native height. Its horizontal bounds are the integer thirds of
the original width, so camera and scale are preserved across playback. The generation
record names its `exerciseId` and `variant`, binds `outputSha256` to the original strip
and adds `nativeStrip`, a path/hash descriptor relative to the retained ledger. All three
cells in one variant must come from that same strip. During a retained-ledger check the packager
repeats the extraction and verifies the exact PNG for each frame number. The bundled
check continues to verify the app assets from a clone.

Keep masters, inspected references, actual prompts/generation records and trace sidecars in the retained artwork workspace. Every descriptor below has a SHA-256 and a safe path relative to the ledger's parent; no absolute paths, `..`, backslashes or symlinks. A combined ledger must live above all its referenced files. Reused held poses can reference the same retained files in multiple frame slots.

```json
{
  "schemaVersion": 1,
  "source": "COPY THE EXACT source OBJECT FROM app/src/main/assets/wallcrawl/catalog.json",
  "frames": [
    {
      "exerciseId": "a-settled-local-id",
      "variant": "male",
      "frameNumber": 1,
      "source": {"path": "references/source.png", "sha256": "64 lowercase hex characters"},
      "master": {"path": "male/masters/frame-1.png", "sha256": "..."},
      "derived": {"path": "male/derived/frame-1.svg", "sha256": "..."},
      "trace": {"path": "male/derived/frame-1.svg.trace.json", "sha256": "..."},
      "generation": {"path": "male/generation/frame-1.json", "sha256": "..."}
    }
  ]
}
```

`source` is the distinct authored WallCrawl source object, never a borrowed upstream commit. Provide all six slots per ID (male/female × 1/2/3), and all shipped supplemental IDs in a complete ledger. A generation record has `tool: "native-imagegen"`, the actual `prompt`, and `outputSha256` matching the native generator output. A single-pose master has those same bytes. A strip uses the verified cell extraction above. Preserve any further available factual references/tool output identifiers in that local record; do not invent unavailable metadata. Source/master/derived/generation/trace bytes are all hash-checked. Packaged PNG masters must be noninterlaced RGBA8 (8-bit channels, color type 6, interlace 0); other sample layouts/bit depths/interlace are rejected. Validation checks PNG structure/CRC, exact decoded scanline length, each row filter 0–4, and the bounded complete deflate stream. SVG namespace/dimensions/path geometry are checked; Sharp performs full raster decoding during tracing.

```sh
python3 tools/workout-guide/package_illustrations.py --supplement-ledger /absolute/retained/provenance.json
python3 tools/workout-guide/package_illustrations.py --supplement-ledger /absolute/retained/provenance.json --check
python3 tools/workout-guide/package_illustrations.py --check-bundled
python3 -m unittest discover -s tools/workout-guide -p test_package_illustrations.py
```

Promote the actual catalog/art together only after the separate Task6B review. Packaging validates the entire overlay before writing any outputs, adds app-ready SVG bytes and index entries, and writes compact lineage to `app/src/main/assets/wallcrawl/illustration-provenance.json`. It preserves historical SVGs/provenance/notice/licenses exactly, rejects stale/unexpected files, and never copies masters or generation records into the app. The compact file has in-app paths, source/master/derived/generation/trace digests, tool versions and options; the full local records remain necessary to re-check source/master lineage. Check mode writes nothing. An interrupted filesystem write is detected by the next check; packaging is not a filesystem transaction.

`--check-bundled` verifies actual shipped SVGs against their index/provenance and pinned historical metadata without the unavailable historic generation ledgers. Old invocation without a supplement ledger, including old `--check`, still uses full historical workspace `collect()` and requires those retained ledgers. No command reconstructs old history.

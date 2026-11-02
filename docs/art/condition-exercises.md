# Four neutral condition-exercise illustrations

The current batch adds four catalog IDs, each matching the same ID in the
historical research request inventory: `supine-wall-supported-piriformis-stretch`,
`pht-single-leg-calf-supported-isometric-hamstring-bridge`,
`seated-hand-resisted-cervical-rotation-isometric`, and
`seated-short-foot-arch-hold-no-toe-flexion`. They are catalog entries for local
art and neutral execution text. The research inventory and its draft therapeutic
mappings are not a supported-condition list. No condition is released until a
qualified reviewer settles the combined evidence, restrictions, dose, and mapping.

The source-readiness register contains 19 pose briefs, not 19 promised catalog
exercises. The other 15 remain research-only or unsettled for this batch. In
particular, chair generation was blocked; the plantar-fascia draft has incorrect
hand roles; the supported chin-nod pose remains unresolved; and heel-raise contact
drifted. The remaining 11 have no selected generated sequence. Those attempts and
reasons are retained under `art/condition-exercises/` and the SDD art reports;
none is represented as completed art or as an equivalent substitute.

## Retention and reproduction

The ignored `art/condition-exercises/` directory holds the complete native PNG
masters, exact generation prompts, source-reference copies, initial and edited
histories, browser comparisons, source-resolution SVG derivatives and trace
sidecars. `packaging-ledger.json` there is the 24-slot complete input ledger. Its
source object is identical to `app/src/main/assets/wallcrawl/catalog.json`'s
source object. Each frame descriptor records relative source/master/derived/trace/
generation paths and SHA-256 values. The retained references are privately
inspected documents or images, not shipped artwork or generation inputs.

For each of the four folders, the retained `male/masters/frame-{1,2,3}.png` and
`female/masters/frame-{1,2,3}.png` are actual 1254-square native PNG copies. The
three PNGs of each variant have identical bytes because the selected exercise is a
still hold. The one reviewed source-resolution derivative per variant is reused
for three app-ready SVG files. The 1→2→3→2 player therefore shows no invented
movement or alternating-side prescription. Male app frames are the canonical
fallback. Fine contours are sharper and some faint detail is lost after alpha
thresholding; static art cannot establish isometric effort, intrinsic-foot muscle
activation, or every hidden contact.

Run from the repo root, with the retained art directory present:

```sh
python3 tools/workout-guide/package_illustrations.py --supplement-ledger art/condition-exercises/packaging-ledger.json --check
python3 tools/workout-guide/package_illustrations.py --check-bundled
```

To regenerate a selected unique SVG from a retained PNG, use the reviewed pinned
Task6A authoring installation (`cd tools/workout-guide/authoring && npm ci`;
see its `README.md`) and run
`node tools/workout-guide/trace_condition_art.mjs <master.png> <derived.svg>`.
The sidecar records the actual versions/options; regenerating to a fresh path
avoids overwriting retained selected bytes. `--check-bundled` works in a clone
without the ignored workspace and checks the shipped index, art and compact
provenance. The notice in `app/src/main/assets/wallcrawl/NOTICE.md` supplies
source titles, publication years/DOIs or primary location, changes, licenses and
non-endorsement. The existing MIT code license is distinct from CC BY-SA art.

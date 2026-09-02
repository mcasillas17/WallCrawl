# Timed-hold programming metadata design

## Outcome and scope

Make `ExerciseProgrammingMetadata.recommendedRepRange` nullable without weakening
rep-based programming. Extend the legacy programming cohort from 117 to 131 entries.
This is the README timed-hold milestone, not approval of the separate reviewed cohort.

## Derived cohort

Starting at origin/main `d8fc5a4`, enumerate the bundled catalog and apply
`FakeWorkoutPlanner.isStrengthWork`: duration, not a stretch, no Cardio in primary
or secondary muscles. Then prove each candidate can actually fill a split by running
the real planner with that single candidate. This yields exactly 14 IDs:

- active-hang
- bear-plank
- cable-pallof-hold
- copenhagen-plank
- crab-walk
- dead-hang
- flutter-kick
- hollow-body-hold
- l-sit-hold
- mountain-climber
- plank
- side-plank
- superman-hold
- wall-sit

The roadmap's “timed holds” includes three moving timed drills: crab-walk,
flutter-kick, and mountain-climber. Names do not override bundled type or planner
classification. Stretch, Cardio-tagged duration, and distance/duration work remain
excluded from automatic strength slots. No classifier change is needed.

## Contract

| Catalog type | Missing range | Explicit null | Object range |
| --- | --- | --- | --- |
| weight_reps, bodyweight_reps, assisted_bodyweight | reject | reject | require integer 1 <= min <= max <= 1000 |
| duration, distance_duration | accept | accept | reject |

The programming block itself stays optional. For timed programming the importer
always emits `recommendedRepRange: null`; authored new entries use the same canonical
representation. Accepting omission preserves a natural optional input form, while
normalization ensures deterministic output. Other scalar/array values are rejected.
Existing rep records retain their objects and serialized content.

Validate at all boundaries: the authored rep-range schema selected by catalog type,
Python normalization, the Android streaming parser after resolving type (independent
of JSON field order), the `Exercise` constructor including `copy`, and the JVM persona
catalog projection. `RepRange` already checks positive ordering; programming validation
also caps its maximum at 1000 to match both import formats. Error messages name the
field/type without embedding records or hostile values. Numeric strings, fractional
values, booleans, missing endpoints, and out-of-range numbers are invalid.

Keep the schema small: named JSON Schema definitions for rep-based and timed
programming, selected from the already validated exercise type. Reuse the importer's
existing schema validator; no new dependency or general schema engine.

## Programming and evidence boundary

Read source metadata/artwork using Workout Guide commit
`ba0b709cb20430361b2cb33aaadd20998164a916`. Never modify the supplied upstream checkout.
Use a disposable clone for clean pinned regeneration. Preserve all 302 exercises,
906 SVG frames, source provenance, attribution, licenses, and reviewed-metadata report.

Every new entry supplies equipment alternatives, pattern, difficulty, mechanics,
nullable range, fatigue, duration progression label, alternatives, and coaching.
Equipment includes support depicted/needed by the authored variant (Bench for
Copenhagen plank, Dip Bars for L-sit, Wall for wall sit). Alternative IDs are catalog
navigation suggestions, not implemented substitutions or claims of equivalence.

Difficulty/mechanics/fatigue are legacy product ranking labels, not validated
physiological measurements. Coaching describes setup and movement only; no medical,
injury-prevention, optimality, or unsupported biomechanical claims. The existing science
evidence review supplies claim boundaries, not exercise-specific efficacy evidence.
All new content is AI-authored; no human reviewer or approval is fabricated.

## Behavior and alternatives considered

Retaining invented rep objects would misrepresent prescriptions. Removing all range
validation would weaken rep work. Nullable metadata with exercise-type validation
preserves existing behavior and enables timed metadata with the smallest API change.

The prescription factory continues selecting by exercise type: ordinary timed work
uses 3 sets, 45-second duration and 45-second rest; stretches retain 1/30/15, and
distance/duration retains 1/600/0. No repetitions-to-seconds conversion, new load,
progression algorithm, deload, substitution, Health/Wear, LLM, dependency, analytics,
or networking is introduced. Metadata can change ranking, coaching, and equipment
filtering for newly authored timed work. Existing rep metadata and per-exercise
prescriptions stay unchanged. Persona outputs remain deterministic.

The 37 reviewed entries remain DRAFT, zero APPROVED, human provenance absent, and
production reviewed eligibility remains false. Room and persistence schemas do not change.

## Verification and publication

Capture expected RED before implementation, then GREEN. Use shared JSON fixtures for
Python and actual Android parser parity; test the JVM projection and domain separately.
Pin the cohort with an exact set and prove real planner selection/exclusion. Pin the
117 rep records and exercise prescriptions against the baseline. Test authored/generated
parity, deterministic import and drift detection, duration-only targets, persona replay,
no invented load, source/frame/license invariants and equipment requirements.

Run Python catalog/release suites, pinned regeneration/check, focused Kotlin tests,
full Gradle test/lint/assemble with rerun tasks, and full connected Android tests with
CI-style API 36 Google APIs and animations disabled (host-native ARM64 on this Mac).
Record any environmental difference. Scan diff/secret/debug/path/residue and clean state.

After commits, exactly two independent code-review agents inspect the complete diff
on the same SHA: GPT-5.6 Luna for correctness/integration and GPT-5.6 Terra for trust,
provenance/security/determinism/performance/maintainability/science. Fix valid findings,
validate and commit, repeat both until no findings on one SHA. Fetch origin/main before
publishing; merge advances without rewriting and repeat validation/review on integrated
SHA. Push, create a non-draft PR, and verify local/remote/PR heads. Every commit includes
the user-specified Co-authored-by and Copilot-Session trailers.

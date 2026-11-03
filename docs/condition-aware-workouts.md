# Condition-aware workouts

**Released conditions: 0.** The production `conditions/support.json` is `withheld-v1`
with an empty records array. The picker exposes only released records that the
current engine can represent, so the research inventory is never a selectable
condition list. No qualified clinical review has been supplied.

The [research inventory](research/conditions/summary.md) covers 50 conditions and
187 distinct publications, guidelines and notices, represented by 196 source
records (118 full-text and 78 abstract-only/partial-access records). Research was
accessed on 2026-11-01 America/Los_Angeles / 2026-11-02 UTC, with scoped execution
rechecks on November 2. This is a targeted review. The [review packet](research/conditions/review.md)
records the narrower independent-access limits, unresolved protocols and all 50
conditions awaiting qualified review. Source verification, ordinary strength
metadata acceptance, art selection and clinical authorization are separate gates.

## Implemented behavior

Released support must bind exact source, exercise-content, artwork, execution,
context-schema and response-policy identities. Changed sources or poses do not
inherit old approval. Required context stays unknown until explicitly answered.
Conditions intersect each other's restrictions and the user's equipment, movement
capabilities and independent joint limits. Incompatible or stale facts produce a
typed refusal; the planner never relaxes a restriction to fill the session.

`ORDINARY` and `SUPPORTIVE` prescriptions are stored separately. Support retains
its exact reviewed dose, rest and schedule; it cannot satisfy strength slots,
receive ordinary strength-set credit or inherit generic timed progression.
Preview, logging and history show frozen instructions and recorded answers.
Historical interpretation does not consult today's support catalog.

Generated, manual-template and program starts check current facts and recheck
inside the insertion transaction. Generated starts rebuild all consumed ordinary
history, weekly credit, scheduling, capability and progression inputs under the
shared write gate; an intervening ordinary completion can invalidate a preview
without changing its condition-history digest. Successful starts retain the
original frozen recommendation context and receipt.

The manual editor offers an explicit action to adopt currently due supportive
prescriptions and their purpose. It shows the doses and instructions first,
rechecks current support on the action, and changes only the draft until Save.
Matching draft exercises are replaced only by that explicit action. Changed or
withdrawn support, unknown context and pending reassessment cannot fabricate a
dose. Start still validates the saved concrete prescription against current
policy; editing an adopted dose does not bypass that validation. A dose that
cannot round-trip exactly through the existing manual units and measurement
precision is refused without partial adoption. Some loaded prescriptions therefore
require another template unit or the separately validated generated-workout path;
the editor never silently rounds the reviewed dose.

Pain-stop outcomes atomically write pending
reassessment events. Removing a condition, cancelling a workout or deleting its
source set does not clear those events. Reassessment requires the exact current
event, required fresh reviewed answers and a current valid policy, then updates
selection/revision and clears only that event in one transaction. There is no
medical-clearance or recovery-score claim.

No-condition profiles retain their existing planning path. English/Spanish UI,
measurement validation, user-confirmed loads and offline operation remain in use.
Condition selections, frozen context and pending events are sensitive local data;
see [privacy and explicit exports](privacy.md).

## Browsing conditions and exercises

**Exercises → Condition reference** opens a searchable offline library of 50 conditions in seven regions. Each detail shows the publications, the research team's access level and expandable study notes. English and Spanish names, search and controls are available. Publication titles retain their original language and detailed extraction notes are in English. Opening a source uses the system browser. Where the cited source describes an illustrated catalog movement, tap its localized name to open the exercise detail.

The catalog contains 319 exercises, including 17 neutral WallCrawl additions with both illustration variants. Each has explicit execution guidance and PNG masters. The source-specific exercise links identify the movement studied. The condition picker and deterministic planner use the separate clinical release records, currently empty. [Exercise sources, PNG masters and remaining artwork gaps](art/condition-exercises.md)

## Integration scenarios

The existing planner corpus has 23 personas: its 14 historical inputs are
unchanged, plus nine fictional condition scenarios. All new support/reviewer
identities say FICTIONAL and use `example.invalid`; no fixture is release content.
Successes compare exact ordinary and supportive prescriptions, raw/displayed
validation, source-rule membership, frozen context/receipt digest, determinism
and purpose-separated progression/accounting. Failures compare typed reason and
condition/exercise IDs and emit no dose.

| Scenario | Expected result and dose | Existing executable seam |
| --- | --- | --- |
| No selected condition | `NotApplicable`; ordinary prescriptions and old typed failures unchanged | Historical corpus; generation-context builder |
| Fictional released condition | `Ready`, raw `Valid`; ordinary 2 × 8 reps, 30 s rest, RIR 1–3; supportive 2 × 20 s, 17 s rest | `condition-released` |
| Two compatible conditions | Same exact dose, one supportive instance, both source-rule references | `condition-compatible` |
| Incompatible dose | `CONDITION_REQUIREMENTS_UNSATISFIED` / `conflicting-support`; no prescription | `condition-incompatible` |
| Missing context | Same typed failure / `missing-context`; no prescription | `condition-missing-context` |
| Withdrawn record | Same typed failure / `unsupported-condition`; no prescription | `condition-withdrawn` |
| Insufficient duration | Same typed failure / `insufficient-duration`; fictional support alone needs 3,634 s against 2,100 s budget; no trimmed dose | `condition-duration` |
| Missing equipment | Same typed failure / `support-restricted`; no prescription | `condition-equipment` |
| Pain stop, including removed selection | Same typed failure / `pending-reassessment`; no new dose | Two pain-stop corpus personas; actual writes/idempotence in `ConditionWorkoutLifecycleTest` |
| Stale preview | Typed start refusal, zero partial insertion; valid start preserves original context/receipt | `ConditionRecommendationIdentityTest`, `ConditionRecommendationValidationTest`, `ConditionStartPersistenceTest`, `ConditionGeneratedFreshnessTest` |
| Manual template | Explicit editor adoption → save → current concrete-dose check; exact purpose/context persisted without automatic receipt; invalid membership/dose refused | `ConditionTemplateEditorTest`, `ConditionStartPersistenceTest`, `WorkoutTemplateSessionTest`, `WorkoutRepositoryTest` |
| Program | Actual program slot/intent/receipt and condition dose retained; refused start reserves nothing | `ProgramPersistenceTest`, `ConditionStartPersistenceTest`, multi-week program suites |

The parser asset fixtures intentionally use their own fictional 1 × 20 s supportive
dose, 0 s rest and 2/week, 48-hour schedule. The corpus and condition-start Room
fixtures use 2 × 20 s, 17 s rest and 2/week, 24 hours. Neither is clinical guidance.
Transactional cases run through the existing repositories/DAOs, not a second
simulated lifecycle.

## Reproducible verification

From a clone, run:

```sh
python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v
python3 tools/workout-guide/validate_condition_research.py --require-complete
python3 tools/workout-guide/validate_catalog_supplement.py
python3 tools/workout-guide/check_pinned_catalog.py
python3 tools/workout-guide/package_illustrations.py --check-bundled
./gradlew testDebugUnitTest lint assembleDebug --no-daemon
```

The CI device matrix remains full API 36 and 37.0 suites plus the API 26 app
package boundary suite. Instrumentation must produce nonzero XML test counts;
a wrapper exit 0 alone is insufficient. New corpus resources use the existing
test resource inputs. Production support is verified from target-app assets and
the actual APK, avoiding the test-APK `conditions/support.json` name collision.

With the retained art workspace, additionally run
`python3 tools/workout-guide/package_illustrations.py --supplement-ledger art/condition-exercises/packaging-ledger.json --check`.
The final integration report and original logs/XML/captures/APK baseline remain
in `.superpowers/sdd/2026-11-01-condition-aware-workouts/`; they are retained local
evidence, not files promised to a fresh clone. Clinical release remains outstanding.

The November 2 integration recheck verified 174 local research links, 50 dossier
anchors and all 60 hashes in the [frozen review manifest](research/conditions/review.md#frozen-candidate-record-manifest).
It rechecked local source records, not access to every primary publication.
The pre-expansion integration-fix debug APK is 67,541,234 bytes (SHA-256
`014d110d44f55ed0edcc0811994f5ae70dcf3080cfbdb6c0a2af5c1686faf7ae`), versus
the 66,360,669-byte Task 6B baseline: +1,180,565 bytes in this **incremental debug
comparison**. This is not clean-build growth or a comparison with a pre-feature
app. Compressed asset payloads grew by 305,437 bytes; all 1,812 historical SVG
payloads and old attribution files match that baseline. All 2,759 asset payloads
also match the retained pre-fix Task 16 APK. The fix adds 14,716 compressed ZIP
payload bytes versus Task 16 while physical APK size stays equal; container
layout/compression effects are not attributed to feature code. Against Task 6B,
the physical delta exceeds summed compressed-payload growth by 633,566 bytes,
which remains separately recorded and unassigned in `final-fix-artifacts/apk-proof.json`.
The original Task 16 APK and proof remain unchanged.

Task 16's **pre-fix baseline** executed 206 Python tests and 1,471 JVM tests;
full instrumentation executed 719 cases on each of API 36 and API 37, and the
API 26 app boundary executed 40. All reported zero failures, errors and skips.
The separate 11-case start-dose rerun is included in the 719, not additional
unique coverage.

The final integration fix executed all **1,471 JVM tests** and **131 affected
API 37 tests**, with zero failures, errors and skips. The affected device checks
cover the actual manual editor/save/start path, ordinary and program freshness
interleavings with real history/ledger inputs, condition controls, existing editor
behavior, and production/program/template persistence. The full device matrix and
unchanged Python/research/art suites were not repeated. Debug assembly, `lint`
and explicit variant lint checks succeeded; retained lint reports and exact
commands distinguish the current verification from the earlier full matrix.
Warnings remain a bounded maintenance concern, not a claim of pristine output.

## Reference and catalog expansion checks

The expanded debug APK contains 319 exercises and 50 searchable condition references. Eleven condition pages link to the 17 illustrated supplement exercises through the publications that describe them. Seven shared body-region graphics identify the location on each reference page.

The expansion passed 212 Python tests, 1,473 JVM tests and 62 focused Android API 37 tests. Device checks covered catalog parsing, Spanish names and guidance, condition reference navigation and publication links, artwork selection, and artwork binding. Debug and release lint reported zero errors. The packaged APK retained all 1,836 prior variant SVGs byte-for-byte and includes 78 new SVG frames. The versioned source-art directory contains 102 PNG frame slots for the 17 local catalog entries.

Verification used an emulator. The supine chin nod and supine dumbbell scapular reach still need usable paired artwork. Condition-specific workout rules await qualified clinical review.

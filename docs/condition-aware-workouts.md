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
inside the insertion transaction. Pain-stop outcomes atomically write pending
reassessment events. Removing a condition, cancelling a workout or deleting its
source set does not clear those events. Reassessment requires the exact current
event, required fresh reviewed answers and a current valid policy, then updates
selection/revision and clears only that event in one transaction. There is no
medical-clearance or recovery-score claim.

No-condition profiles retain their existing planning path. English/Spanish UI,
measurement validation, user-confirmed loads and offline operation remain in use.
Condition selections, frozen context and pending events are sensitive local data;
see [privacy and explicit exports](privacy.md).

## Neutral catalog and retained art

Four neutral duration entries extend the pinned 302-entry upstream catalog to
306 browsable exercises; they do not release a condition or automatically become
strength candidates:

- `supine-wall-supported-piriformis-stretch`
- `pht-single-leg-calf-supported-isometric-hamstring-bridge`
- `seated-hand-resisted-cervical-rotation-isometric`
- `seated-short-foot-arch-hold-no-toe-flexion`

[Artwork provenance and retention](art/condition-exercises.md) records eight
unique native PNG masters and eight SVG derivatives, packaged as 24 held slots.
The actual PNGs remain under `art/condition-exercises/<id>/{male,female}/masters/`;
`packaging-ledger.json` binds sources, prompts, masters, derivatives and trace
records. This retained workspace is not included in an ordinary clone. Fifteen
other pose candidates remain research-only/unsettled; they are not completed art.
The historical 302-exercise/906-original-frame pin, 1,812 adapted SVGs and earlier
AI acceptance audit remain separate and unchanged.

Each selected pose is a still hold: the three frames per variant have identical
bytes. Real app playback/capture coverage verifies both variants of all four IDs.
Spanish detail verification uses the production language controller, activity
recreation and a readable/clickable Close action. Static art cannot certify hidden
piriformis hand contact, PHT ankle/box detail, neck isometric effort or short-foot
activation/overlapping toes. PNG-to-SVG tracing loses some faint detail. These are
rendering/source checks, not qualified movement review.

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
| Stale preview | Typed start refusal, zero partial insertion; valid start preserves original context/receipt | `ConditionRecommendationIdentityTest`, `ConditionRecommendationValidationTest`, `ConditionStartPersistenceTest` |
| Manual template | Concrete converted dose checked; exact purpose/context persisted without automatic receipt; invalid membership/dose refused | `ConditionStartPersistenceTest`, `WorkoutTemplateSessionTest`, `WorkoutRepositoryTest` |
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
The retained final debug APK is 67,541,234 bytes, versus the 66,360,669-byte
Task 6B baseline: +1,180,565 bytes in this **incremental debug comparison**.
This is not clean-build growth or a comparison with a pre-feature app. Compressed
asset payloads grew by 305,437 bytes; all 1,812 historical SVG payloads and old
attribution files match the baseline. The retained report separately records
ZIP payload sizes and the unexplained physical/container difference.

Final November 2 checks executed 206 Python tests and 1,471 JVM tests; full
instrumentation executed 719 cases on each of API 36 and API 37, and the API 26
app boundary executed 40. All reported zero failures, errors and skips. The final
API 37 start-dose assertion update also passed its 11-case class separately;
those cases are included in the 719-case suite, not additional unique cases.
Lint and debug assembly succeeded; lint retained 0 errors, 32 warnings and 3 hints,
with existing dependency, API-style and resource warnings triaged in the report.

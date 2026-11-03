# Room schema artifacts

Room's compiler exports the current annotated `WallCrawlDatabase` to
`app/schemas/wallcrawl.elopenmike.com.core.database.WallCrawlDatabase/16.json`.
This is build evidence containing table definitions, fields, affinities, nullability,
declared SQL defaults, primary keys, indexes, foreign keys, and Room's identity hash.
It contains no user records and is not a backup or restore format.

Database version **16** and archive format **6** (reads **1–6**) are separate contracts.
Enabling export changes neither. It does not add a migration, destructive fallback,
runtime table/index/default change, or data rewrite.

## Generate and check

Use JDK 17 and the Android SDK used by the normal build. From the repository root:

```sh
# A fresh, non-cached compiler export into app/schemas/.
./gradlew :app:clean :app:kspDebugKotlin -Pksp.incremental=false --no-build-cache --no-daemon

# Regenerate without cleaning unrelated build outputs.
./gradlew :app:kspDebugKotlin -Pksp.incremental=false --rerun-tasks --no-build-cache --no-daemon

# Stage a newly generated version before checking (existing versions are already tracked).
git add app/schemas
python3 -m unittest discover -s tools/room -p 'test_*.py' -v
python3 tools/room/check_schemas.py
```

Do not edit the JSON or its hashes manually. Debug and release use the same database
annotations and export directory. `RoomSchemaArgProvider` declares the directory as a
relative, tracked Gradle input and supplies `room.schemaLocation` through KSP. No Room,
KSP, AGP, Kotlin, or Gradle version is changed for this feature.

The guard verifies that the current version's artifact is tracked, every tracked schema
still exists, and no untracked JSON (even an ignored file) has appeared. It then invokes
KSP with `--rerun-tasks --no-build-cache -Pksp.incremental=false` and redirects export
to a new empty temporary directory with `-Pwallcrawl.roomSchemaDirectory=...`.
Only the expected current-version file may be generated, and its bytes must match the
repository artifact. Compiler failures, disabled export, missing output, and changed
output fail the command. The temporary directory is removed even on failure; repository
exports are not overwritten. This check is a separate CI/release step, so an up-to-date
or restored application build cannot skip it. It compares against the checked-out files
and Git index; on CI those are the committed checkout.

The temporary-directory override is for validation, not a second schema history. The
current project uses explicit migrations; if auto-migrations are introduced later, their
historical compiler inputs must also be provided to isolated generation and tested.

## Historical provenance boundary

The first compiler export committed by this workflow is **16**, generated with Room
**2.8.5** through KSP **2.3.11**, using the annotated database from base commit
`da8b24b5137a56a55fe95f7ca2565c8f72840927` with export enabled. The baseline runtime
implementation (export disabled) and the exported build's generated implementation have
the same identity hash, `e3009d24e9a582759daeadbb84576655`.

Repository history contains no earlier committed Room exports. This task does not
recompile old revisions with unverified historical toolchains or label reconstructed SQL
as compiler output. The historical baseline remains:

| Starting schema | Evidence used by instrumentation |
| --- | --- |
| 1–11 | `LegacyDatabaseFixtures`' existing SQL definitions; `MigrationChainToCurrentTest` opens each through `ALL_MIGRATIONS` |
| 12–15 | Existing suites construct schema 11, run the actual migrations to the chosen start, then open through the remaining migrations |
| Current (16) | Authentic compiler JSON; `ExportedSchemaTest` validates it through Room and validates migrations from every older supported start against it |

`MigrationChainToCurrentTest` was previously named `MigrationChainTo12Test`. Its new
method is `everyLegacyFixtureSchemaMigratesToCurrentVersion`; its 1–11 starting fixtures
and all original assertions remain. The individual migration suites, including
`Migration12To13Test` through `Migration15To16Test`, remain separately selectable.
The latest suite additionally compares all stored deload, enrollment, association, and
recommendation fields before/after 15→16 and checks foreign-key integrity.

### Existing SQL default differences

Two historical migration defaults predate schema export:

| Column | Fresh annotated schema / export | Historical upgrade / SQL-fixture default |
| --- | --- | --- |
| `user_profiles.revision` | No SQL default | `0` (introduced by 2→3) |
| `workout_sessions.weightUnit` | No SQL default | `'LBS'` (introduced by 1→2) |

Kotlin constructor defaults are not SQL defaults. A database's physical defaults depend
on its creation/upgrade path; the legacy fixture chains retain both defaults above.
Room permits these extra historical defaults when the entity declares none. This slice preserves that existing distinction;
it does not modify entities or migration SQL to make the definitions appear identical.
`ExportedSchemaTest` checks every column's actual SQL default, explicitly accounting for
these two differences. It creates a database from the export's table/index SQL, omitting
identity setup so actual Room opening must validate affinities, nullability, primary keys,
indexes and foreign keys. The same Room validation runs after every legacy migration
chain; table names, defaults, identity and foreign-key integrity are also checked against
the export. Both paths therefore validate against the actual annotated database.
Any normalization would be a separately scoped database-semantics change.

## Instrumentation and packaging

```sh
# On a task-owned emulator selected with ANDROID_SERIAL:
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=wallcrawl.elopenmike.com.core.database \
  --no-daemon
```

Keep the existing data assertions: historical workout units and loads, null legacy
template units, program membership, recommendation provenance, deload choices, and
archive export/restore. Schema validation supplements these assertions; it cannot prove
data retention by itself. Full CI still runs API 36 and 37, with the existing API 26
app-package smoke leg and report-presence gate.

Only `androidTest` exposes `app/schemas` as assets. Application source sets do not include
it. `ExportedSchemaTest` checks that the schema
directory is absent from target-app assets. Neither the production APK nor user-owned
archives gain schema files or legacy SQL fixtures.

The test uses Android SQLite and `org.json`, already available in instrumentation.
An initial attempt to use `MigrationTestHelper` from Room 2.8.5 failed on the installed
toolchain with `AbstractMethodError` in `FieldBundle`'s generated serializer, before schema
validation. No dependency override or upgrade is added to work around it; the test-only
helper dependency was removed. The generated SQL is consumed directly and Room still
performs the structural validation. This is not a replacement migration implementation.

## A future migration

1. Make the intentional database change, choose the next database version, implement and
   register its migration in `ALL_MIGRATIONS`. Change the archive version only if the
   separate archive contract requires it.
2. Preserve every earlier export and legacy fixture. For versions 16 onward, use the
   authentic exported start schema (with `MigrationTestHelper` if qualified for that
   toolchain); add meaningful before/after
   records and assertions, including foreign-key checks. Do not extend the old SQL
   fixture range as a substitute for compiler exports.
3. Generate the new version with the commands above. Review its tables, defaults,
   indexes, foreign keys and identity changes alongside the migration. Never copy an
   old JSON to a new version number or hand-author an identity hash.
4. Stage the new artifact and run the drift guard, focused migrations, unit/lint/build,
   and the existing instrumentation gates. Update coverage/provenance documentation.
   An unexplained same-version schema diff requires investigation, not automatic acceptance.

## Primary references

- [Room 2 compiler options and Gradle integration](https://developer.android.com/jetpack/androidx/releases/room#compiler-options)
- [Room 2.8.5 release record](https://developer.android.com/jetpack/androidx/releases/room#2.8.5)
- [Directory-aware KSP schema argument provider](https://developer.android.com/training/data-storage/room/migrating-db-versions#export-schemas)
- [KSP processor configuration](https://kotlinlang.org/docs/ksp-quickstart.html)

The general migration guide now shows Room 3 examples. This project retains the Room 2
coordinates and APIs; the directory-aware `room.schemaLocation` KSP option is documented
for Room 2. The helper's published 2.8.5 sources and its device failure informed the
test-only SQL approach above.

## Validation record — 2026-11-01

Fresh generation (`:app:clean :app:kspDebugKotlin`) and forced repeated generation
produced identical JSON: SHA-256
`dba66c1c0be77afc5f1e9ac112eaee249d636b46ea43ea5f5f3b6c7a20a5ff5a`.
All 11 generated Room Kotlin files in both debug and release were byte-identical to
the export-disabled baseline. `WallCrawlDatabase_Impl.kt` retained SHA-256
`83e85c92c349f138688b1e5587d3119b55141ff600638733e0fa95c60d3d0d1a`.

The actual guard, including real compiler execution where applicable, was exercised
with these isolated mutations. Every mutation was restored before subsequent validation:

| Probe | Result |
| --- | --- |
| Append a newline to the repository export | Exit 1: generated schema differs |
| Remove the tracked current export | Exit 1: tracked artifact missing |
| Add an untracked `17.json` | Exit 1: untracked schema artifact |
| Disable export while leaving the old JSON present | Gradle succeeds; guard exits 1 because no schema was generated |
| Restore the original source and artifact | Exit 0: byte-for-byte match |
| Normal KSP invocation reports `UP-TO-DATE`, then run guard | Guard executes KSP and passes |
| Remove only task-owned generated KSP output, restore it `FROM-CACHE`, then run guard | Guard executes KSP without cache and passes |

The nine Python guard tests additionally cover ignored version files, absent generated
output, unexpected generated output, missing historical artifacts, and a version bump
without its new tracked export. The pinned catalog check, 156 importer tests and six
release-tool tests passed. `./gradlew test lint assembleDebug assembleDebugAndroidTest
assembleRelease --no-daemon` passed with 1,322 JVM tests and zero lint errors (30 existing
warnings and three hints). APK inspection found the exact JSON only in the test APK;
debug and unsigned release application APKs contained neither schema JSON nor the legacy
fixture class.

Task-owned ARM64 Google APIs emulators completed the full suite: **577 tests on API 36**
and **577 on API 37**, with zero failures, errors or skips. Both included all **201**
database-package tests, including the legacy chain, every later migration suite and the
two exported-schema tests. The focused `ExportedSchemaTest` also passed independently on
API 37. Full API 37 used `ANDROID_SERIAL=<device> ./gradlew connectedDebugAndroidTest
--no-daemon`; API 36 used the same built APKs and runner directly:

```sh
adb -s "$ANDROID_SERIAL" install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$ANDROID_SERIAL" shell am instrument -w -r -e debug false \
  wallcrawl.elopenmike.com.test/androidx.test.runner.AndroidJUnitRunner
```

The API 36 runner reported `OK (577 tests)`; the API 37 XML report recorded all 577
cases with no failures/errors/skips, in addition to Gradle's successful exit. API 26's
existing app-package smoke configuration is preserved and was not repeated locally for
this slice. Signing, shrinking, dependency remediation and release publication remain
outside this work.

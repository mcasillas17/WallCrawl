# Room schema artifacts implementation plan

> **For agentic workers:** Use `superpowers:executing-plans` for this task.

**Goal:** Deliver the schema-export and migration-artifact slice of Roadmap Package 19.

**Architecture:** Use the installed Room compiler through KSP to export schema 16 to
`app/schemas/`. Preserve the SQL fixtures and data assertions for historical migrations;
supplement them with validation against the authentic current export. CI generates into
an empty temporary directory and compares it with tracked repository artifacts.

**Tech stack:** Room 2.8.5, KSP 2.3.11, AGP 9.4.0, Gradle 9.7.1, JDK 17, Python standard library.

**Spec:** User's Package 19 schema-artifact request; `ROADMAP.md`, Package 19, items 3–4.

## Constraints

- Database version 16 and archive format 6 (reads 1–6) stay unchanged.
- No entity, converter, DAO, migration SQL, identity hash, or destructive-fallback change.
- Historical SQL fixtures are not historical compiler exports.
- No dependency upgrades; coordinate tooling files with the dependency owner.
- Preserve API 26 smoke and API 36/37 full instrumentation matrices and UI test semantics.
- Use task-owned emulator state. Deliver a non-draft PR; do not merge or release.

## Review focus

- A missing, modified, or untracked export must fail even after a cached build.
- Disabled export must fail even if an old committed JSON remains present.
- Real Room opening must preserve historical data and validate schema structure.
- Schema JSON must appear only in the test APK, never in the application APK or backups.
- A future version must add an authentic export without erasing older exports or fixture coverage.

## Completed implementation

- [x] Capture unmodified generated Room implementation and schema identity from a fresh build.
- [x] Configure a directory-aware KSP argument provider in `app/build.gradle.kts`, enable
  `exportSchema`, generate `app/schemas/.../16.json` twice, and compare runtime code to baseline.
- [x] Write guard regression tests using temporary Git repositories: matching output,
  mismatched/missing output, missing tracked artifact, untracked versions, and a version bump.
  Implement the smallest Python guard and wire it into CI and release validation.
- [x] Supplement migration instrumentation with current exported-schema validation;
  update the historical chain's name and all selectors, preserving its assertions.
  Retain late migration tests and exercise foreign-key/data-retention checks.
- [x] Run guard sensitivity experiments, deterministic fresh and incremental generation,
  unit/lint/build, focused migrations, full API 36/37 instrumentation, and APK packaging checks.
- [x] Document commands, provenance, future migrations, and validation evidence; mark only
  Package 19 items 3–4 complete. Results are in [the schema record](../../room-schemas.md).

## Delivery gates

The PR records the required independent implementation and final full-panel reviews,
their snapshot/configuration digests, and validation evidence. Delivery requires a
normal commit/push and a non-draft PR; no merge or release is authorized.

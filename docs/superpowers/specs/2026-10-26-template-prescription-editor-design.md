# Package 15: template prescription editing and draft recovery — design

## Status and authority

Base: freshly fetched `origin/main` `f7dec8b2a` (2026-10-25). Authority is ROADMAP
Package 15 plus the current custom-workout, architecture, localization, privacy,
progression/deload and immutable-history contracts.

Two decisions were made by the user in this task's chat on 2026-10-26. They are the only
user-approved choices here:

1. **Merge order.** Codex's Package 12 (multi-week programs) merges first and keeps
   Room 15 / archive 5. This package is built and tested against a *provisional*
   Room 15 / archive 5 on today's main and **must be renumbered to Room 16 / archive 6**,
   then rebased and re-verified, once Package 12 merges. *Done:* Package 12 merged
   (#93); the template unit is now migration 15→16 and archive format 6. Where this
   spec says provisional 14→15 or format 5 below, read 15→16 and format 6.
2. **Start unit.** A template started in a profile whose unit differs from the
   template's converts loads into the profile unit; the template keeps its own values.

Every other choice below is an agent proposal, recorded here rather than presented as
approved. It reuses the editor work from an unmerged September branch
(`template-prescription-editor`, never pushed), ported onto current main, and extends it
where that branch fell short: it displayed the profile unit beside a unitless stored
number, which this design replaces.

## Problem

- `ExercisePrescription` validates in its constructor, so half-typed text has nowhere
  to live; only set count was editable.
- Nothing survived process recreation.
- **Templates had no unit.** `workout_templates` stored raw load numbers;
  `startWorkoutFromTemplate` stamped the session with the profile's *current* unit, so a
  profile switch from lb to kg silently turned a 100 lb target into 100 kg.

## Editor contract

### Fields by type

| Type | Fields |
| --- | --- |
| `WEIGHT_REPS` | sets, rep min/max, optional external load |
| `BODYWEIGHT_REPS` | sets, rep min/max |
| `ASSISTED_BODYWEIGHT` | sets, rep min/max, optional assistance |
| `DURATION` | sets, required duration |
| `DISTANCE_DURATION` | sets, distance and/or duration (at least one) |

Every type also has rest seconds, an optional effort target (min/max RIR), and notes.
Template name and notes, ordering, removal, full-catalog search and equipment warnings
are unchanged.

### Draft representation

`TemplateExerciseDraft` (feature/templates) holds every measurement as raw text plus a
stable `rowId` (UUID) that is independent of catalog id and list position. Two rows of
the same exercise are therefore independent through edit, reorder and removal; the
editing sheet, its errors and dirty state are all keyed by `rowId`.

`validate()` returns `Valid(PlannedExercise)` or `Invalid(fieldErrors, rowMessage)`.
It parses with `LocaleFormatting.parseIntInput` / `parseDecimalInput` (no fallback
parser: `"2,5"` and `"2.5"` are 2.5, `"1.234,5"`, `"2."` and `"12a"` are errors), checks
the domain's own bounds (`ExercisePrescription`, `EffortTarget`, `WorkoutTemplate`
constants) and only then constructs `ExercisePrescription`. Load, assistance and distance
accept at most two decimals, the precision the field can show; more is a field error
rather than a value later shown and re-saved rounded, and a unit switch is refused while
a field holds one. A stored value with more is seeded rounded (a positive one too small
to show becomes 0.01, never 0): such values are
legitimate (the previous editor saved unit-converted starting loads unrounded) and
refusing them would make an untouched row unsaveable. The editor states that it
rounded them, and saving writes the rounded value. Blank optional load,
assistance, distance and duration stay `null`; `"0"` load or assistance is an explicit
zero. Nothing is clamped; the only stepper (sets) moves within the domain bound.

### Rest and effort provenance

- Rest seconds shown from an untouched row keep their loaded `restClass` /
  `restTargetSource` pair verbatim — displaying a product default does not make it a
  preference.
- A rest value the user changes (and does not change back) is saved through
  `ExercisePrescription.withUserRestPreference`, i.e. `USER_PREFERENCE`. The existing
  `UserRestPreference` contract requires a class, so the class is the **nearest entry in
  the product rest table** (`SHORT` 60 s, `MODERATE` 90 s, `LONG` 180 s), ties going to
  the shorter class. This is a bucketing of the user's own number onto the product's own
  table, not a physiological recommendation; the exact seconds are what is prescribed.
  `0` seconds is valid (domain minimum) and buckets to `SHORT`.
- The effort target is a *planned* RIR range, distinct from logged RPE/RIR. Both blank
  means none; one blank is a field error. Nothing synthesizes feedback, and progression
  and deload policies are unchanged.

## Unit contract

### Storage

- `WorkoutTemplate.weightUnit: WeightUnit?` is the unit of **every** `targetWeight` and
  `targetAssistanceWeight` in that template (a template is single-unit, like a session).
- Room: nullable `weightUnit TEXT` on `workout_templates`, added by an additive
  migration (provisional 14→15). Existing rows become `NULL`. The entity holds it as
  text and the template mapper parses it strictly, so an unrecognised value is a read
  error rather than the shared enum converter's pound fallback.
- `NULL` means **legacy, unit unknown**. No migration or reader assigns a unit to it.
- Repository writes require a non-null unit whenever any load or assistance is present.
  A template with no loads may still be written with a unit (the editor always supplies
  one).
- Domain bound: a template's loads must stay representable after conversion into either
  unit, so a `KG` template's loads are capped at `MAX_WEIGHT` lb expressed in kg
  (4 535.92 kg); `LBS` keeps `MAX_WEIGHT`. Exposed as `WorkoutTemplate.maxLoad(unit)`,
  the bound the editor validates against.

### Archive

- Provisional format 5 writes `"weightUnit"` on a template only when it is non-null and
  reads formats 1–5. Formats 1–4 never carry it; a 1–4 document containing it is
  rejected; reading 1–4 gives `null` (legacy). Writing a format ≤ 4 with a non-null
  template unit is refused, which keeps earlier formats' canonical bytes and checksums.

### Editor

- The draft carries its own unit, saved with the draft. A new template takes the
  profile unit once, at first load; an existing template takes its stored unit.
- A **profile unit change while a draft is open never reinterprets it**: the profile is
  read once and the draft's unit is part of the draft.
- **Explicit conversion.** A "Loads in kg | lb" control converts every load and
  assistance value in the draft (rows and the open sheet), rounding half-up to
  `MeasurementPrecision.EDITABLE_FRACTION_DIGITS` (2) by `convertWeightForEditing`, the
  same helper start uses. Blank stays blank, and a positive value never converts to 0: one
  too small to show (0.01 lb is 0.0045 kg) becomes the smallest step, 0.01, since an
  explicit zero is a different statement. Conversion is refused, with a message, while
  any load/assistance text is unparseable, because malformed text cannot be converted,
  and when a converted value would exceed the target unit's bound.
- **Round trips are exact.** Each converted field remembers the text it held in the
  other unit (saved with the draft); switching back restores it instead of re-converting
  rounded text, so 135 lb → 61.23 kg → 135 lb rather than 134.99. The memory is used only
  while the field still holds what that switch produced (compared as a number), so
  retyping the same value keeps it and a different value is converted instead.
- The kilogram bound is stated, not only enforced: load and assistance are optional fields,
  so their resting hint says they may be left empty, and an out-of-range value's error
  states 0–4535.92.
- A newly added exercise's factory default load is in the profile unit; it is converted
  into the draft unit if they differ.
- **Legacy (null) with loads:** the editor shows the numbers without a unit and asks
  which unit they were entered in. Choosing one *labels* them (no conversion) and makes
  the draft dirty; saving is refused until it is chosen. A unit whose bound an existing number exceeds is
  refused with a message stating that limit (only kilograms can refuse; the pound bound
  is the domain's own).
  **Legacy without loads:** nothing to misinterpret, so the draft adopts the profile unit.

### Start

- Session unit stays the profile unit. A template in another unit has each load and
  assistance converted with `convertWeightForEditing`: rounded half-up to 2 decimals, and a
  positive load too small to show becomes 0.01 rather than 0, the same rule as the unit
  switch. The template is not modified.
- A legacy template with loads cannot start a new workout: the list explains that its
  load unit must be confirmed in the editor first. While a workout is already in
  progress, Start resumes it, as for any template, since nothing is copied. A legacy
  template with no loads starts normally.
- Manual starts keep `CUSTOM_TEMPLATE` origin, `sourceTemplateId`, no recommendation
  record, no program membership and no deload consumption.
- Completed and active sessions are never rewritten; the migration touches only
  `workout_templates`.

## Draft recovery contract

Mechanism: the editor's `SavedStateHandle` (as `OnboardingViewModel` uses), i.e. the
navigation entry's `onSaveInstanceState`. No database table, file or archive scope.

| Event | Outcome |
| --- | --- |
| Configuration change (rotation, language, theme, font scale) | Draft restored exactly, including raw invalid text, unit and the open sheet |
| System-initiated process death while the task is retained | Same |
| Back / toolbar back with a dirty draft | Confirmation; cancel keeps everything; discard clears the draft |
| Back with an open picker or untouched sheet | Closes that surface first; does not leave |
| Back, swipe or scrim tap on a sheet holding typing | Asks; keep editing keeps it, discard drops only the sheet's copy; the question is drawn above the sheet and disappears with it |
| Successful save | Draft cleared after the write commits, then one navigation |
| Failed save | Every edit stays, draft stays recoverable, retry is one tap |
| Force-stop, swipe away from Recents, uninstall, clear data, reboot | **Not recovered** |

Saved keys: has-draft flag, name, notes, draft unit (null while a legacy unit is unconfirmed), the stable
new-template id, ordered encoded rows, the open sheet's row, and a rows-dropped flag.
Each row encodes its fields joined by `U+001F` (stripped from input). Catalog, search
index, artwork, profile and repositories are never serialized; exercises resolve from
`exerciseId` after the catalog loads.

- **Size.** Bounded by domain limits: 50 rows × 1 000-char notes, 2 000-char template
  notes, 120-char name, a 200-char exercise id on every row, UUID row ids and a recorded
  rest provenance per row. Numeric fields are capped at 11 characters, one past what
  the parser accepts, so an over-long entry stays visibly over-long and is refused on
  its field instead of being cut into a shorter valid number; a too-precise decimal is
  reported as too precise whatever its length. The exercise id is a deliberate
  over-estimate: editor rows come from the catalog (ids up to 36 characters today,
  128 allowed by the catalog parser) or from a recovered draft, which can keep an id a
  later catalog dropped; 200 is the archive's id limit. A template whose stored id the
  catalog no longer holds does not open as rows at all, it fails to load. An
  instrumented test parcels the
  saved state at those limits with supplementary-plane characters and asserts it stays
  under a 256 KiB budget (a quarter of the 1 MiB binder transaction); valid templates are
  never truncated and limits are not lowered.
- **Late loading.** The draft is restored synchronously before the load starts; the
  load then fills only reference data (catalog, equipment, profile), never name, notes,
  rows or a restored unit. A recovered draft with no unit and no load or assistance text
  takes the profile unit, the same rule a fresh load and an edit apply; with nothing
  weighed, nothing is reinterpreted.
- **Missing template.** If an edited template no longer exists, the draft stays visible
  and saving is refused (`template_error_missing`); it is never turned into a new
  template. Save also re-checks existence, under the local-data write gate that both
  delete-all and single-template deletion take, so a deletion between load and save
  cannot be undone by the write.
- **Missing or retyped catalog exercise.** The row is flagged and save is refused;
  nothing is substituted.
- **Corrupt saved state.** Undecodable rows, rows repeating one identity (only the first
  is kept), an undecodable open sheet or one that does not match its row, and an unknown
  unit mark the draft incomplete, and save is refused; discard is the way out.
- **New vs existing.** Separate navigation entries, separate handles.
- **Delete-all / restore.** Both run from Profile; the editor hides the bottom bar, so
  it cannot be on the back stack beneath them, and their completion
  (`restartAtOnboarding` / `openTodayAfterRestore`) replaces the back stack. The
  save-time existence check is defence in depth for edits.

## Save and back behavior

- While a save is in flight, every edit is ignored and inputs are disabled; the write
  holds the exact snapshot on screen. Toolbar back is disabled; system back is consumed.
- A new template's id is generated once and stored in the draft, so a retry or a
  re-save after recreation overwrites the same row rather than creating a duplicate.
  `isSaving` is set synchronously and stays set after a successful write, so neither a
  double tap nor a tap after success starts a second write. Success is a state
  (`isSaved`) the screen leaves on, not a callback handed to the write, so a rotation
  during the write cannot strand the editor behind the previous composition's
  navigator. Leaving (arrow, confirmed discard or saved) pops once. No editor text limit
  (name, notes, exercise notes, search, numeric fields) cuts a surrogate pair in half.
- Repository validation remains authoritative; cancellation propagates; load, save and
  restore failures are visible, localized and retryable where retry can succeed.

## Out of scope

Substitutions (Package 10), program integration (Package 12), analytics, any model
feature (Package 17 is retired), accounts, sync, telemetry, dependency or SDK changes.
`DefaultExercisePrescriptionFactory`, eligibility and planner policy are unchanged.

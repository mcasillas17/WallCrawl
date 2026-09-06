# Localization

WallCrawl ships two languages: English and neutral Latin American Spanish. Both are
complete and offline. This document is the reference for how the two are kept complete —
where each kind of text lives, what is deliberately never translated, and what to do when
adding a string, an exercise, or a language.

## The rule everything else follows

**Language is presentation. It never reaches a decision.**

Exercise selection, ordering, prescriptions, loads, rest targets, eligibility outcomes,
policy reasons, stored measurements, and the export archive are identical in every
language. A translated label is only ever read on its way to a screen or into the search
index; it is never a key, a filter value, or an input to the planner.

`PlannerLocaleInvarianceTest` enforces this by planning the same profile under `en-US`,
`es-MX`, `es-ES` and `tr-TR` and requiring byte-identical selections, prescriptions and
failure reasons. `LocalDataArchiveLocaleTest` does the same for the archive, down to the
checksum.

## Where text lives

There are three homes, chosen by what the text *is*:

| Kind of text | Home | Key |
| --- | --- | --- |
| Interface chrome, errors, accessibility labels, counted quantities | `res/values/strings.xml` and `res/values-es/strings.xml` | resource name |
| Exercise names, coaching summaries, muscle and equipment words | `assets/localization/exercise-localization.json` | catalog exercise id, canonical English vocabulary word |
| Names, notes, and text a user typed | Nowhere — stored as written | — |

Domain enums carry no display text. `core/ui/localization/DomainLabels.kt` maps each enum
entry to a `@StringRes` through an exhaustive `when`, so adding a case to the domain fails
the build there rather than shipping an untranslated screen.

### Why exercises are not in `strings.xml`

The bundled catalog is a byte-for-byte mirror of a pinned upstream commit, verified in CI
by `tools/workout-guide/check_pinned_catalog.py`. Translations must not touch it. They also
must not become keys. So they live in a separate overlay keyed by the catalog's own stable
identifiers, loaded and validated by `ExerciseLocalizationParser`, and read through
`ExerciseVocabulary` on the way to a screen. The catalog's `Exercise` objects keep their
canonical English `name`, `primaryMuscles`, and `listedEquipment` throughout — those are
what eligibility, muscle matching, equipment filtering, and dose accounting read.

## The overlay format

```json
{
  "schemaVersion": 1,
  "languages": ["es"],
  "vocabulary": {
    "muscles":   { "Chest": { "es": "Pecho" } },
    "equipment": { "Barbell": { "es": "Barra" } }
  },
  "exercises": {
    "barbell-back-squat": {
      "es": {
        "name": "Sentadilla con barra",
        "aliases": ["Back squat"],
        "coachingSummary": "Sentadilla con barra centrada en cuádriceps y glúteos."
      }
    }
  }
}
```

- `name` is required for every entry; `aliases` and `coachingSummary` are optional.
- Vocabulary keys are the canonical English words from `StandardMuscles` and
  `StandardEquipment`, after `MuscleVocabulary` canonicalization.
- A language that appears in the document but not in `languages` is rejected, so an
  overlay cannot carry translations no reader can reach.
- Duplicate keys, blank values, unknown fields, unsafe ids, and oversized documents are
  rejected at parse.

An overlay that fails to load is **not** fatal: every screen falls back to the catalog's
English. That is a degraded interface rather than a broken one — which is exactly why the
completeness checks below run in CI, since a silent fallback is otherwise invisible.

## Search

Search matches across **every shipped language at once**, regardless of the reader's
current language. "squat" and "sentadilla" resolve the same catalog ids, in either
interface language. Matching is accent-insensitive in both directions: "bíceps" and
"biceps" are the same query.

Filter chips are the exception that proves the rule — they *display* a translated label and
*pass* the canonical English key. `LocalizedExerciseSearchTest` pins both halves, including
that a translated value does not filter.

## Numbers, dates, and input

Everything numeric goes through `core/ui/format/LocaleFormatting.kt`.

- **Display** is locale-aware: the reader's decimal mark and digit grouping.
- **Editable fields never render grouping**, because a grouping mark in a locale that
  groups with `.` cannot be read back without guessing.
- **Parsing accepts both `.` and `,`** as the decimal mark, but only one separator per
  value. `2,5` and `2.5` are both 2.5; `1.234,5` and `2.` are refused rather than guessed
  at. A decimal comma is never read as a different magnitude.
- **A half-typed value is not a value.** Set logging submits on every keystroke, so
  `47.` — what a reader types on the way to `47.5` — is held back rather than submitted
  as a null. Only a blank field clears a stored value.
- **Units are independent of language.** Switching to Spanish never converts pounds to
  kilograms or changes a stored value.
- **Dates** use the platform's locale formats, not a pinned English pattern.
- **Machine-readable output is locale-independent.** The archive is JSON: a load is
  written with a decimal point whatever the reader's language writes decimals with.

## What is never translated

- **User-authored text** — profile name, template names and notes, and anything else typed
  into the app. It is stored and shown exactly as written, in either language.
- **Historical free text.** A workout session's name and explanation are written when the
  session starts, in the language on screen at that moment, and are never rewritten
  afterwards. The planner emits a *structured* title and rationale
  (`WorkoutTitleSpec`, `WorkoutRationaleSpec`), the screen renders them for the reader, and
  the rendered text is what gets stored. A session logged in English keeps its English name
  after switching to Spanish, and vice versa; nothing infers meaning from that text by
  string matching, and no missing structured metadata is fabricated for older rows.
- **Licences.** The bundled attribution and licence documents are reproduced in their
  original language and labelled as the authoritative text. Only the headings around them
  are translated.
- **Identifiers.** Enum names, exercise ids, canonical vocabulary, archive field names,
  and the WallCrawl name and creator names.

## Claim boundaries

Translation must not strengthen a claim. WallCrawl describes what it plans — conservative
volume, capped sets — and never promises injury prevention, healing, or a physiological
outcome. `SafetyCopyTest` reads both shipped resource files **and the translation overlay's
names and coaching summaries**, and fails on that vocabulary in either language, and on any
stop reason that reads as a diagnosis rather than as the user's own decision to stop.

## Adding to it

**A new interface string**

1. Add it to `res/values/strings.xml`. Use a plural for anything counted, and a
   parameterised string for anything with a value in it — never assemble a sentence from
   translated fragments.
2. Add the Spanish to `res/values-es/strings.xml`.
3. `StringResourceParityTest` fails if a key, a plural quantity, or a format argument is
   missing or mismatched, and if a Spanish string was left identical to its English source
   without being listed as deliberately identical.

**A new exercise, or a catalog update that adds one**

1. Re-run the importer as usual. Do not hand-edit `catalog.json`.
2. Add an entry to `assets/localization/exercise-localization.json` keyed by the new id.
3. `BundledExerciseLocalizationTest` fails if any catalog exercise has no Spanish name, if
   any shipped coaching summary is untranslated, if the overlay names an exercise the
   catalog does not contain, or if a catalog muscle or equipment word has no Spanish.

**A new language**

1. Add `res/values-<tag>/strings.xml`.
2. Add the tag to `languages` in the overlay and a block per exercise and vocabulary word.
3. Add it to `AppLanguage`, and to the locale lists in `PlannerLocaleInvarianceTest` and
   `LocalDataArchiveLocaleTest`.
4. `locale_config.xml` is generated by AGP from the `values-*` directories, so the system
   per-app language screen picks the new language up with no further change.

## Choosing the language

`AppLanguageController` is the only place the app language is read or written. Both
selectors — the onboarding Welcome step and Profile → App preferences — call it, so they
share one preference by construction rather than by keeping two copies in sync.

Storage belongs to AndroidX: Android 13 and later keep the choice in the system, so the
in-app selector and the system per-app language screen always agree; below that, the
`autoStoreLocales` service declared in the manifest persists it in SharedPreferences, which
the backup rules already exclude. The preference is device-local — it is not in the profile,
not in the archive, and restoring an archive never changes it.

Applying a language recreates the activity. Every screen keeps its in-progress input in a
ViewModel or a `SavedStateHandle`, so an onboarding answer, the current wizard step, and a
template being edited all survive it.

## Glossary

Consistency matters more than any individual word choice. These are the recurring ones:

| English | Spanish |
| --- | --- |
| workout (session) | entrenamiento |
| routine, template | rutina |
| set | serie |
| repetition, rep | repetición, rep |
| exercise | ejercicio |
| rest | descanso |
| assistance | asistencia |
| load | carga |
| effort | esfuerzo |
| goal | objetivo |
| equipment | equipo |
| restore | restaurar |
| export | exportar |

Spanish copy uses second-person singular *tú* throughout, with no voseo and no *vosotros*,
which is what keeps one neutral Latin American register readable across the region.

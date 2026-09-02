# Capability-Aware Personalization Design

## Goal and staged boundary

WallCrawl needs explicit, respectful information about what movements currently
feel comfortable before it can safely add capability-aware exercise eligibility
and ranking. The first milestone therefore ships validated local profile inputs
only. It does not change the current planner.

This milestone is capability-only. Weight, height, BMI, age, body composition,
exercise-demand metadata, capability filtering/ranking, substitutions,
progression, deload behavior, Health Connect, Wear OS, analytics, accounts,
cloud sync, network calls, and LLM integration are out of scope. Optional body
measurements remain deferred and are not required by this roadmap.

## Shipped profile model

The stable persistence identifiers are:

```kotlin
enum class MovementCapabilityType {
    IMPACT,
    FLOOR_TRANSITION,
    UNSUPPORTED_SQUAT,
    UPPER_BODY_BODYWEIGHT_PUSH,
    VERTICAL_PULL_OR_HANG,
    BALANCE_WITHOUT_SUPPORT,
    CONTINUOUS_ACTIVITY
}

enum class CapabilityLevel {
    UNKNOWN,
    COMFORTABLE,
    LIMITED,
    AVOID
}
```

`MovementCapabilities` owns a complete immutable map. Its constructor normalizes
every missing known key to `UNKNOWN`, lookup also falls back to `UNKNOWN`, and a
defensive copy prevents callers from mutating the value later. Absence is never
treated as `COMFORTABLE`. `UserProfile.movementCapabilities` defaults to this
conservative all-`UNKNOWN` value.

The seven user-facing questions are:

1. impact tolerance;
2. floor transitions;
3. unsupported squat;
4. upper-body bodyweight push;
5. vertical pull or hang;
6. balance without support;
7. continuous activity.

The four options are **Comfortable**, **Limited**, **Avoid**, and **Not sure**.
**Not sure** persists as `UNKNOWN`. Labels and descriptions are Android string
resources and never persistence values.

`TrainingConstraint` remains separate. A capability answer describes current
movement comfort; a constraint is an explicit protected-joint or low-impact
preference.

## Onboarding and Profile behavior

Fresh onboarding has an eighth **Movement preferences** step immediately after
Experience & Units. Every capability requires an explicit choice before the
wizard can advance or finish. The draft is a partial enum-keyed map, not a
normalized domain value, so an unanswered question remains distinct from an
explicit **Not sure** answer.

The onboarding ViewModel saves the wizard step, answered keys, and other draft
fields in `SavedStateHandle`. Back/forward navigation, configuration change, and
process recreation therefore retain actual selections without inventing
`UNKNOWN` answers for missing fields. Completion saves the entire profile in one
repository operation.

Training Profile displays all seven persisted values. Edit creates an in-memory
complete draft; Save writes the current profile plus the draft in one revision,
while Cancel or Back discards the draft without persistence. Copy states that
the settings prepare future personalized exercise selection and do not change
current recommendations yet.

Capability questions use resource-backed labels/descriptions, 48dp-minimum
selection controls, deterministic focus order, selected-state semantics, and a
scrollable layout. Language is neutral and non-medical.

## Persistence and migration

Room schema 8 adds one column to `user_profiles`:

```sql
ALTER TABLE user_profiles
ADD COLUMN movementCapabilitiesJson TEXT NOT NULL DEFAULT '{}'
```

The JSON object uses enum names as stable keys and values. Encoding emits only
the seven allowlisted keys and four allowlisted values in deterministic order.
The codec validates a flat string-to-string object and caps input at 4096
characters. Missing keys, malformed JSON, oversized input, unknown keys,
unknown values, duplicate keys, and invalid nesting never become favorable;
decode falls back conservatively to `UNKNOWN`.

Only expected codec-format failures are handled at this boundary. Raw payloads
and complete profiles are absent from logs, exceptions shown to users, test
snapshots, and screenshots.

Migration `7 → 8` leaves existing profile fields and relations unchanged.
Existing users retain `onboardingCompleted`, profile revision, theme, goals,
equipment, constraints, confirmed loads, templates, sessions, sets, and history.
Their `{}` value normalizes to all `UNKNOWN`, and they continue directly into
the app without forced re-onboarding. Every historically supported migration
chain reaches schema 8 without destructive fallback.

## Current data flow and trust boundaries

```text
Compose enum selection
  → Onboarding/Profile typed draft
  → normalized UserProfile
  → repository validation and atomic profile save
  → allowlisted bounded codec
  → Room user_profiles.movementCapabilitiesJson
  → conservative decode and normalization
  → UserProfile
  → resource-backed Compose display
```

Every in-memory boundary uses closed enums. The repository requires a complete
known-key domain map. Persisted JSON is treated as untrusted. Neither raw JSON
nor capability values are interpolated into SQL, shell commands, HTML,
filesystem paths, or logs.

The values remain in the existing local Room database. This change adds no
network, cloud, account, analytics, model, Health Connect, or Wear data flow.
Android backup policy is unchanged: the manifest already has `allowBackup=true`
and no capability-specific rule, so the database remains within the existing
platform app-data backup behavior when device settings permit it. The app has no
current profile export/delete subsystem; consistent data controls remain a
separate roadmap item.

## Planner boundary

`WorkoutGenerationContext` already carries `UserProfile`; no duplicate
capability field or external interface is added. The current exercise filter,
planner, rank ordering, prescription factory, and validator do not consume
movement capabilities. A regression test requires profiles that differ only in
all-Comfortable versus all-Avoid values to produce identical current
recommendations.

The next milestone is human-reviewed exercise-demand metadata followed by
deterministic capability eligibility. Ranking, substitutions, dose changes,
history evidence, progression, and deload behavior remain later work and must
not be enabled piecemeal before the metadata and evaluation gates exist.

## Verification requirements

The shipped milestone covers:

- domain defaults, normalization, immutability, and lookup;
- complete repository round trip and process-style reload;
- malformed, oversized, partial, future-key, and future-value JSON;
- real schema-7 migration, foreign-key checks, and preservation of every
  existing profile/history field;
- every supported historical migration chain through schema 8;
- onboarding completion gating, explicit **Not sure**, navigation retention,
  and saved-state recreation;
- Profile load, edit, cancel, atomic save, and unrelated-field preservation;
- capability control accessibility semantics; and
- planner-output invariance.

## Historical milestone status

> Current status and future priority live in the
> [canonical roadmap](../../../ROADMAP.md). This section records the state when the
> capability-input milestone was designed.

Completed in the capability-input milestone:

- profile domain and conservative defaults;
- bounded local persistence and schema-8 migration;
- fresh-onboarding collection and saved draft state;
- existing-user compatibility;
- Profile display/edit/cancel/save; and
- the no-planner-change regression boundary.

Not completed:

- optional body measurements of any kind;
- reviewed exercise-demand metadata;
- capability eligibility, ranking, substitutions, or explanations;
- history-derived evidence;
- prescription/progression/deload changes; and
- any LLM or external data integration.

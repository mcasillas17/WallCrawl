# Custom Workout Templates and Full-Catalog Planning Design

## Goal

Let a user build, save, edit, delete, and repeatedly start custom workout templates entirely on-device. Manual templates and automated planners may select from the complete 302-exercise Workout Guide catalog. Automatic planning still applies equipment and exclusion constraints; manual selection shows the complete catalog and warns when an exercise appears incompatible with the current equipment profile.

## Product Experience

Today gains a **My Workouts** entry below the current recommendation. Template management is a pushed destination rather than a fifth bottom-navigation tab.

The template library supports:

- create a named template;
- edit its name, notes, exercises, exercise order, and set counts;
- delete a template after confirmation;
- start a template;
- see its exercise and set counts.

The editor uses the bundled offline catalog. Search matches the catalog library's IDs, names, aliases, muscles, and listed equipment. Every one of the 302 exercises is selectable. An exercise whose listed or reviewed equipment is not in the current profile remains selectable but displays a warning.

Starting a template creates a new, immutable workout-session snapshot. Editing or deleting the template afterward never changes an active or completed session. If another session is already active, the existing single-active-session rule wins and the UI offers the existing resume path.

## Domain Model

Templates are separate from generated recommendations and workout history:

```kotlin
data class WorkoutTemplate(
    val id: String,
    val name: String,
    val notes: String,
    val createdAtTimestamp: Long,
    val updatedAtTimestamp: Long,
    val exercises: List<WorkoutTemplateExercise>
)

data class WorkoutTemplateExercise(
    val id: String,
    val templateId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val prescription: ExercisePrescription,
    val notes: String
)
```

`ExercisePrescription` is shared by generated workouts, templates, and session snapshots. It carries `ExerciseType`, set count, rest, and only the targets that make sense for that type:

- `WEIGHT_REPS`: rep range and optional target weight;
- `BODYWEIGHT_REPS`: rep range;
- `ASSISTED_BODYWEIGHT`: rep range and optional assistance weight;
- `DURATION`: target duration in seconds;
- `DISTANCE_DURATION`: at least one of target distance in meters or target duration in seconds.

This removes the current assumption that every exercise is a weight-and-repetition movement. Structural validation is centralized and shared by manual saves and planner output.

Workout sessions record their origin (`PLANNER` or `CUSTOM_TEMPLATE`) and the source template ID when applicable. The template ID is informational, not a foreign key, so session history survives template deletion. Session exercise and set records snapshot the exercise type and type-specific targets/outcomes.

## Full-Catalog Planner Eligibility

The 12 reviewed programming records remain useful enrichment, not an eligibility gate.

`ExerciseFilter` builds the legal candidate space as follows:

1. remove explicitly excluded exercise IDs;
2. apply reviewed equipment combinations when present;
3. otherwise use Workout Guide `listedEquipment` as the minimum known requirement;
4. apply an optional muscle focus;
5. never remove an exercise merely because `programming` is null.

The fake planner must be able to prescribe any allowed exercise. A type-aware prescription factory supplies conservative structural defaults and uses reviewed programming/history when available. It never fabricates an exercise ID. The future local LLM will receive the same allowed IDs and must return the same structured prescription shape.

## Persistence

Room advances from schema version 3 to 4.

New tables:

- `workout_templates` for template metadata;
- `workout_template_exercises` for ordered catalog references and flattened prescriptions, with a cascading foreign key to the template.

Existing session tables gain origin and type-aware target/outcome columns. Where existing non-null repetition columns prevent faithful duration/distance storage, the migration rebuilds the affected table, copies existing values as `WEIGHT_REPS`-compatible records, recreates indexes and foreign keys, and then drops the old table. Migration instrumentation tests must prove existing user/profile/session/set data survives.

`WorkoutTemplateRepository` owns template CRUD and transactionally replaces ordered exercises on save. It validates names, bounds, catalog existence, and prescription/exercise-type agreement before persistence. Flows expose the template list and individual templates.

`WorkoutRepository.startWorkoutFromTemplate` reads the current profile, validates a freshly loaded template against the catalog, snapshots it into the existing active-session tables, and delegates to the same atomic single-active-session transaction used by planner workouts.

## UI and State

New routes:

- `templates` — saved-template library;
- `template/new` — new editor;
- `template/{templateId}` — edit existing template.

The editor ViewModel owns an in-memory draft, loads catalog data asynchronously, and exposes loading, content, saving, and actionable error states. Explicitly saved templates survive process recreation through Room; unsaved draft restoration and leave confirmation are outside this implementation phase.

Exercise ordering uses accessible move-up/move-down controls instead of a gesture-only drag implementation. The first version edits set counts and displays conservative type-specific targets generated from the current profile; detailed target editing is outside this implementation phase. Input is bounded while editing and revalidated on save.

The active workout logger renders and records fields by exercise type. Existing repetition/weight workouts keep their current fast path. Duration and distance workouts gain appropriate target text and completion inputs. Progress volume continues to count weight × reps only, while duration/distance outcomes remain persisted for later analytics.

## Error and Lifecycle Behavior

- Catalog, Room, and validation failures become screen error states; cancellation is always rethrown.
- A failed save leaves the editor draft intact.
- A failed start does not create a partial session.
- Template deletion is confirmed and does not cascade into session history.
- Starting while a session exists returns/resumes that session through the existing repository invariant.
- Missing catalog exercises in an old template block loading or starting with an error; they are never silently substituted.

## Future AI Boundary

No production local model is added in this phase. Templates remain structured data that a future `WorkoutTemplateAdvisor` can consume alongside user profile, equipment, and history. Suggestions must return catalog exercise IDs and structured edits, pass the same validator, and require explicit user acceptance before mutating a saved template.

## Testing and Acceptance

Tests cover:

- type-specific prescription validity and malformed combinations;
- all 302 exercises entering the pre-equipment planner pool;
- reviewed and fallback equipment filtering;
- fake planner generation from unreviewed exercises of every catalog type;
- validator rejection of ID/type/target mismatches;
- Room 3→4 migration with existing history preserved;
- template CRUD, ordering, atomic replacement, deletion, and missing-ID failures;
- frozen template-to-session snapshots;
- editor catalog-search behavior;
- an emulator smoke flow through create, save, start, log, finish, and history;
- type-aware active-set logging.

The phase is successful when a user can create and save a template from any catalog exercises, reopen and edit it, start it, log its type-appropriate work, complete the session, see it in history, and later start the unchanged saved template again.

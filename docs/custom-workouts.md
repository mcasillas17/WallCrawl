# Custom Workouts

Custom workouts are reusable templates saved entirely on the device. They let a
user choose and order exercises without asking the planner to generate a
recommendation. Starting a template still uses the same active-workout logger,
Room persistence, history, and progress pipeline as a planner-generated workout.

## User flow

1. Open **Today** and select **My Workouts**.
2. Create a workout, then add a name and optional notes.
3. Search the bundled catalog and add exercises.
4. Use the move controls to set exercise order and adjust each set count.
5. Save the template locally.
6. Start it from **My Workouts**, log the workout, and finish normally.

Saved templates can be reopened, edited, started again, or deleted after
confirmation. A template must have a name and at least one exercise.

## Exercise availability

Both workout sources use the same 302-exercise catalog, but their selection
rules differ intentionally:

| Workout source | Catalog access | Equipment behavior |
| --- | --- | --- |
| Automatic planner | Any catalog exercise that survives hard constraints | Unavailable equipment and profile exclusions remove candidates before planning |
| Manual template | All 302 catalog exercises | A possible equipment mismatch is shown as a warning, but the user may still select the exercise |

Manual selection does not weaken catalog integrity. A template can only store a
known catalog exercise ID with a prescription matching that exercise's type. If
a saved reference cannot be resolved later, WallCrawl reports the error instead
of substituting a different exercise.

Search matches exercise ID, display name, aliases, primary and secondary
muscles, and listed equipment. The catalog and illustrations are bundled, so
searching and building templates work offline.

## Default prescriptions

When an exercise is added, `DefaultExercisePrescriptionFactory` supplies a
conservative prescription based on its catalog type and the current profile.
Reviewed programming metadata may enrich that default, but it is not required
for an exercise to be usable. The template editor does not currently apply
historical progression when creating its defaults.

The current editor lets the user change exercise order and set count. Detailed
editing of rep ranges, load, assistance, duration, distance, rest, and per-
exercise notes is planned for a later phase. Those values are already represented
in the domain and database models. Nullable effort guidance, rest class/source,
and exact rest seconds now also round-trip through schema 11. The manual editor
does not apply the reviewed automatic policy; a valid explicit rest preference
already present on a template is preserved when the template is saved or started.

## What is stored

Templates and performed sessions are separate records:

```text
Workout template
  └─ ordered exercise IDs + prescriptions
                    │ Start
                    ▼
Workout session snapshot
  └─ copied exercises + generated sets + origin/source metadata
                    │ Log and finish
                    ▼
Completed workout history
```

Starting a template copies its current definition into a new session. The
session does not read through to the template afterward. This means:

- editing a template does not change an active or completed workout;
- deleting a template does not delete workout history;
- a completed workout preserves the targets that were active when it started;
- starting the edited template later uses the new definition.

The session stores `CUSTOM_TEMPLATE` as its origin and retains the template ID
for future attribution. The reference is informational so template deletion is
safe.

## Active workout logging

The logger presents fields appropriate to each exercise type:

- load and repetitions;
- bodyweight repetitions;
- assisted repetitions and assistance weight;
- duration;
- distance and/or duration.

Targets and actual results remain separate. The completed result feeds the same
history and progress calculations used by planner-generated workouts and is
available to future workout-generation context.

Only one workout can be active. If the user tries to start a template while a
session already exists, WallCrawl preserves and resumes the existing session
instead of creating competing workouts.

## Local-first behavior

Templates are stored in the Room database and require no account or network
connection. A saved template survives navigation and process recreation.
Unsaved editor changes currently remain in memory only; leaving the editor or a
process restart can discard them.

The current feature does not include:

- detailed target and rest editing;
- unsaved-draft restoration or leave confirmation;
- exercise supersets or circuits;
- AI analysis or suggestions for a saved template;
- cloud backup or synchronization.

Future AI assistance should operate on this structured template model, select
only catalog exercise IDs, pass the same validation boundaries, and require the
user to accept changes before a saved template is modified.

## Contributor map

The primary implementation areas are:

All locations below are relative to
`app/src/main/java/wallcrawl/elopenmike/com/`.

| Area | Location |
| --- | --- |
| Domain model and prescription rules | `core/model/WorkoutTemplate.kt`, `core/model/ExercisePrescription.kt` |
| Template persistence | `core/database/repository/WorkoutTemplateRepository.kt`, `WorkoutTemplateDao` |
| Session snapshots and set validation | `core/database/repository/WorkoutRepository.kt` |
| Template screens and state | `feature/templates/` |
| Navigation | `app/WallCrawlApp.kt`, `app/WallCrawlNavigation.kt` |
| Room schema and migration | `core/database/WallCrawlDatabase.kt` |

Tests live beside the corresponding JVM source under `app/src/test/` and under
`app/src/androidTest/` for Room and packaged-asset behavior. See
[WallCrawl Architecture](architecture.md) for the complete data flow and
[Build and test](../README.md#build-and-test) for verification commands.

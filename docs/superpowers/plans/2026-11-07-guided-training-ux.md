# Guided training UX implementation plan

> Implement the approved design in independently verifiable Android presentation changes.

**Goal:** Deliver the guided Train, active workout, Progress, Library and settings flows. Make the approved set editor slightly shorter by tightening its spacing.

**Architecture:** Keep existing navigation route identities and storage contracts. Reuse the existing ViewModels, validators, exercise visuals, numeric bounds and localized strings. Maintain separate input drafts until the applicable save action succeeds.

**Tech stack:** Kotlin, Compose Material 3, Android navigation, existing Room stores and Android/JVM tests.

**Spec:** [Guided training UX](../specs/2026-11-05-guided-training-ux-design.md)

## Constraints and review focus

Preserve 48 dp touch targets, EN/ES, light/dark themes, large text, offline operation, explicit planning intent, clinical release gates, original history and existing restore semantics. Check incomplete decimal drafts, repeated confirmations, an active session during a planning failure, return from a nested screen, and an exact keypad with a running rest timer on a short phone.

## Implementation

- [x] Active workout. In ActiveWorkoutScreen, ActiveWorkoutViewModel, SetRow, ExerciseIllustration and WorkoutSummaryScreen, bind one selected set to compact steppers and in-place exact entry. Reuse the existing measurement types and bounds. Verify saved values, pending/rejected writes, swiping, expanded artwork, rest and explicit leave/finish with JVM and device tests.
- [x] Train and Progress. Reorder TodayScreen, TodayDeloadCard, ProgramScreen and ProgressScreen around Start/Resume, an explicit source chooser, preview/options, real weekly activity, history and accessible details. Verify unavailable programs, adjustment decisions and condition cues ahead of Start.
- [x] Library and settings. In ExercisesScreen, ProfileScreen and WorkoutTemplatesScreen, provide an Exercises/My workouts switch, applied filter controls, research access and grouped settings with Back. Preserve current draft editors and data outcomes. Exercise Library, settings and nested return navigation on device.
- [x] Integrate roots in WallCrawlNavigation and WallCrawlApp. Expose three labelled destinations, Profile and settings in each header, and keep local-data reset handling at graph level. Simplify theme, functional backgrounds and first-run language. Update affected navigation tests and documentation.
- [x] Build both debug APKs, run JVM tests and lint, and exercise the changed journeys on an isolated Android emulator. Inspect screenshots, resolve regressions and review the integrated diff against the spec.

## Native verification

- Built and installed the debug app and instrumented test APKs. All 1,485 JVM tests passed.
- Checked the native flows on isolated API 36 and 37 emulators. Coverage included 360 dp layouts, 320 dp Spanish at 1.8× font scale, light and dark themes, motion changes, and the offline condition guide.
- The Room-backed set logger opened the bundled dumbbell bench press demonstration, preserved a 70 lb draft across leave, saved-state restoration and resume, and recorded the edited 9 reps once. Other cases covered all five measurement types, incomplete decimals, rejected writes, rest during swiping, explicit discard and partial finish.
- Checked Train source selection and restrictions, Library and grouped settings, template start locking, analytics and history drill-downs, language recreation, onboarding and local data controls. Reran the affected native tests after correcting stale selectors, locale fixtures, and asynchronous assertions.

[Set logger](../../../art/screenshots/active-workout.png) · [Inline keypad](../../../art/screenshots/active-workout-keypad.png) · [Train](../../../art/screenshots/today-screen.png) · [Progress](../../../art/screenshots/progress-screen.png) · [Library](../../../art/screenshots/exercises-screen.png)

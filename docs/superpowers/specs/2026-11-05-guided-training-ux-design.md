# Guided training UX

Date: 2026-11-05

Status: implemented in the Android app, including the approved compact set editor. See the [implementation and verification notes](../plans/2026-11-07-guided-training-ux.md).

Scope: Android app experience, English and Spanish, dark and light themes

## Intent and decisions

WallCrawl should make the next action obvious. A person opening the app can start or resume a workout, record what they did, and understand their progress without learning how the planner is implemented.

The owner asked for a drastic improvement in simplicity and intuitiveness after the Today screen became dominated by optional controls and explanations. The owner chose the guided experience from the two approaches below. This is an app-wide presentation and interaction project. “Guided” describes the experience; it does not enroll everyone in the existing optional four-week program.

The following are proposed design decisions, made by the designer for approval rather than attributed to the owner: three main destinations; settings in a header action; one expanded set editor; a persistent, clearly identified workout source with a Change action; grouped settings; and visual simplification throughout the existing screens.

## Approaches

| Approach | Benefit | Tradeoff | Decision |
| --- | --- | --- | --- |
| Guided training | One recommended next action and focused set logging. Programs, custom routines, and analysis remain accessible through named actions. | Less-used controls take another tap. Their entry points must remain discoverable. | Selected by the owner. |
| Training dashboard | Workouts, programs, custom routines, and metrics have similar prominence. | More decisions and information on every visit. | Considered and declined in favor of guided training. |

## Goals and boundaries

- At a normal phone width, a ready user sees Start or Resume on opening Train. At large text sizes, scrolling remains possible and nothing overlaps or becomes unreachable.
- During training, the current exercise, relevant execution cues, and the next set's fields have priority over decoration and historical data.
- Each screen has one dominant action. Secondary actions use clear labels and a consistent position.
- All existing tasks remain reachable, including manual routines, program recovery, research, history, exclusions, localization, backup, restore, deletion, and credits.
- Every user-visible proposal is checked with realistic normal, empty, blocked, loading, saving, and failed states.

This project does not change workout prescription algorithms, clinical release status, exercise assets, progression rules, metric formulas, archive formats, or the database schema. It adds no account, network service, new dependency, conversational coach, readiness score, auto-completion, or automatic program enrollment. Presentation may be reorganized without deleting information needed to make a decision.

## Current evidence

The design follows the current Compose implementation and checked-in screenshots. The screenshots outside Today are historical evidence of layout, not proof of every current runtime state. A fresh device walkthrough is an implementation acceptance requirement.

- `app/WallCrawlNavigation.kt` currently has Today, Progress, Exercises, and Profile destinations.
- `feature/today/TodayScreen.kt` now exposes Start early after the recent local fix, but still presents program and adjustment options as separate prominent surfaces.
- `feature/workout/ActiveWorkoutScreen.kt` places a full illustration, exercise information, and optional previous-session section before full editors for every set.
- `core/ui/components/SetRow.kt` already provides measurement-specific inputs, decimal handling, validation, feedback, skip/stop, and previous-value copying. Reuse these behaviors.
- `feature/profile/ProfileScreen.kt` exposes goals, preferences, movement answers, equipment, priorities, recovery, language, theme, data, and credits in one scrolling form.
- `feature/progress/ProgressScreen.kt` places several analytic sections before workout history. Detailed metric semantics already exist and must remain accurate.
- `feature/onboarding/OnboardingUiState.kt` has eight steps and required movement answers. Cosmetic simplification cannot silently turn unanswered items into consent or a capability claim.
- `app/src/main/assets/conditions/support.json` currently contains zero released condition records. Research browsing must not imply that diagnosis-specific generation is enabled.

## Navigation and structure

Bottom navigation has **Train**, **Progress**, and **Library**, always labelled. Retain existing route identifiers `today`, `progress`, and `exercises` so restored navigation does not depend on translated labels. Each root screen has a labelled, accessible Profile and settings icon in its top bar. Profile becomes a detail destination with Back returning to the originating screen, preserving that screen's scroll position and selections.

Root navigation restores each tab's existing state. Full-screen workouts, onboarding, editors, settings details, and history details do not show the bottom bar. System Back and visible Back agree. Existing `profile`, template, history, condition-reference, program, and active-workout destinations remain valid. Do not replace navigation with a second custom router.

| Destination | First view | Secondary destinations |
| --- | --- | --- |
| Train | Next workout or active-session Resume | Workout preview, workout source chooser, My workouts, Program, workout options |
| Progress | Weekly activity and recent workouts | All history, performance trends, muscle and dose details, existing analytics drill-downs |
| Library | Searchable exercises, with Exercises / My workouts switch | Exercise detail, existing routine editor, clearly labelled Condition guide entry |
| Profile and settings | Short rows with current-value summaries | Training preferences, Equipment, Movement and restrictions, App preferences, Your data, Credits |

Library defaults to Exercises. Its two-option switch opens the current exercise list or saved-routine list without routing through Train. Condition guide remains a visible secondary row on the exercise-library view and opens the existing browsable reference. Train also retains a direct My workouts shortcut. Reuse one routine list implementation for both entry paths, preserving its in-flight start handling and return destination.

## Train

The order is a quiet header, a short calendar-week progress summary, the next action, and compact secondary links.

### Ready state

The selected poster design pairs the localized split and emphasis with a still from the actual first exercise. A compact weekly goal sits above it. Show the re-entry label when applicable, focus, estimated duration, exercise count, set count, and relevant adjustment summary. Estimates retain their approximation marker. Show the source explicitly as **Today's workout** or the existing **Program session** and position. A secondary **Change** action opens a chooser for the currently supported source choices, saved routines, and program management. It does not silently switch the planner or enroll a program.

Start workout is the dominant button. View exercises opens a preview with the complete exercise order, targets, warm-up/supportive roles, and existing cues. Back or closing the preview returns to the same recommendation; no regeneration occurs as a side effect.

My workouts and Program are simple secondary rows below the recommendation. When no program exists, label its entry **Explore programs**, and explain the actual four-week offering on the management screen. An active program shows its completion position. The program choice is never a compulsory question on opening Train.

Keep the selected `WorkoutPlanningIntent` and its current SavedStateHandle restoration behavior. Ordinary remains the default when no previous choice exists. Program management and enrollment do not implicitly change this choice. The source displayed, generated prescription, Start label/context, and saved membership must agree. Pending generation disables Start and reports what is happening. A program failure offers retry, recovery, or an explicit switch to Today's workout; it never falls back silently.

### Active workout and adjustment states

An existing session replaces the new-workout hero with **Resume workout**, its recorded title, and actual set progress. No competing Start is enabled. Program read failures or unavailable new recommendations must not obscure Resume.

An accepted one-workout set reduction appears as a short sentence on the workout and an accessible Details action. Details contains the current explanation and cancel action. An offered reduction still shows its effect and target prescription before explicit acceptance. Decline, dismiss, request, cancel, and acceptance retain distinct existing meanings.

Workout options houses regenerate and the existing lighter-workout request where available. It must describe any replacement action and preserve generation/start locking. Do not add per-session duration/equipment overrides that the engine does not currently support.

### Restrictions and failures

Actionable safety or condition instructions remain above the action they qualify. Required pre-start review of condition-specific execution cues cannot be collapsed into the ordinary preview. Unsupported restrictions and reassessment keep their current refusal behavior and provide the existing next action to edit/review the profile. An inability to generate is expressed as a specific explanation and recovery action, not an empty motivational panel.

Non-blocking coverage limitations use concise, accurate summaries with Details for explanation. Actual missing coverage must remain disclosed before Start; shortening wording cannot claim the requested coverage was met.

## Active workout

The screen answers “What do I do now?” in this order:

1. Compact header with workout position, a **Leave workout** action, and secondary **Finish**.
2. Exercise name and prescribed target above a full-width animated demonstration. The inline demonstration grows with the available window height and stays the same size when selecting sets, logging, or showing guidance and rest controls. It retains a 120 dp minimum when the controls need to scroll. A small expand icon sits in the corner of the artwork. Tapping anywhere on the artwork opens a full-screen view of the same demonstration with catalog guidance and a Close action. Give the whole thumbnail a single accessible action named “Expand [exercise name] demonstration.” Playback runs automatically in both sizes and shows a still frame when reduced motion is enabled. Static illustrations and unavailable artwork keep their existing fallback behavior.
3. Any applicable execution/stop cues and errors, visible before logging.
4. A compact rest strip when applicable, using the existing deadline and rest states.
5. A strip of numbered set buttons above one editor. Each button selects its set, indicates logged or skipped status, and exposes the recorded result to accessibility services. Let long lists of sets wrap or scroll accessibly.
6. Swipe left across the exercise page to advance one exercise and right to return. Keep labelled Previous and Next exercise controls for one-handed use, keyboard navigation, and TalkBack. Update and announce the exercise position and name together. Keep vertical scrolling, numeric controls, and Android's system back gesture usable. At the first and last exercise, a swipe beyond the boundary stays on that exercise. Finishing requires the explicit Finish action.

Both navigation methods preserve entered set values, completion and stop states, and the active rest deadline. An exercise change only selects which exercise to view. Next is secondary until the exercise's sets are resolved, and users can move with sets still open.

When a weighted exercise has no known load, the user chooses a starting weight inline and can fine-tune it with the existing steppers. An untouched first set shows a neutral prompt. Logging confirms the weight. A later ordinary work set with no load target can reuse the preceding completed normal set in the same exercise. Edits stay local until Log set. Warm-ups, skipped work, special set types and supportive prescriptions do not supply this default.

When the workout has a target, the weight row starts there. The most recent matching historical set appears beside the editor with its actual load and reps. The user can expand the previous workout for the other sets. Weighted workouts prescribe a rep range and prefill its upper bound.

Show weight and reps in fixed rows with a read-only value between − and +. Tap either button to change the draft once, or hold to repeat until release. Moving off the button cancels the gesture. Load and assistance adjustments default to steps of 2.5 in the active weight unit. Tap their step size to cycle through 1, 0.1, 0.01 and back to 2.5. Distance starts at 50 m and offers the same finer increments. Duration offers 5 or 1 second, and reps change by one. Use the current locale's decimal separator. Keep **Log set** in place as values change. It saves a new result, and **Save changes** commits a correction. Preserve previous-value copying, optional measurements, validation and feedback.

On first entry to an exercise, select its first unresolved set. A logged set shows a check, a skipped set has a distinct mark, and both can be reopened with their numbered buttons. If every set is resolved, keep the set strip and show Next or Finish.

After an explicitly submitted completion or skip is confirmed by persisted state, focus may advance to the next unresolved set in the same exercise. Do not change exercise automatically. A failed write leaves the same editor open with the user's input and an announced error. Disable repeat submissions while that write is pending. Pain-stop exclusion prompts retain priority, explicit acceptance, and undo through settings. Focus advancement waits until any such prompt is resolved.

Key measurement drafts by session and set. Preserve exact values and adjustment sizes when changing sets or exercises, opening the demonstration, rotating, or restoring the same active session. Incomplete and out-of-range values disable the logging action and explain the required correction. Keep stored results intact until a valid edit is confirmed and retain the existing set-update reconciliation contract.

The rest strip has a visible countdown and direct +30 s / Skip rest actions. The existing Dismiss timer action remains in a labelled secondary menu; it is not merged with Skip because the events differ. Expanding instructions or editing another set does not create a second timer or reset the deadline.

**Leave workout** opens an explicit choice: leave and resume later, keep training, or discard with the existing destructive confirmation. Leaving keeps the session active and returns to its originating screen. System Back follows that same choice. Finish preserves the current incomplete-workout confirmation, counts, unsupported-program handling, and program-advancement rules. Finishing or discarding never happens because of a swipe, navigation, or visual completion animation.

## Completion and Progress

The completion screen shows the recorded workout, duration, completed sets, and applicable achievements compactly. It distinguishes logged work from program advancement on a partial finish. It never celebrates an unperformed set or invents a record. **Done** retains the existing return to Progress; **View workout** opens the frozen history detail.

Progress opens with the current week's workout count and goal, a compact consistency summary, and the most recent completed workouts with an obvious **All workouts** link. History is reachable without scrolling through analytical explanations. A new user sees an honest empty state and an action to Train; no fake chart or trend.

Below the overview, **Performance trends** opens the existing range, scope, measure, and drill-down controls; **Muscle details** exposes existing muscle involvement and separately labelled reviewed primary dose. Preserve their distinct accounting, partial-week labels, unavailable comparisons, provenance, and accessible chart alternatives. Do not relabel credited work as physiological readiness or silently combine metrics. The existing Program destination is reached from Train rather than occupying the top of Progress.

All historical names, targets, measurements, units, timestamps, and reasons remain frozen records. UX simplification must not re-run the planner to populate history. Existing pagination and older-session access remain.

## Library and exercise detail

Exercise search is first. A **Filters** action opens muscle and equipment choices, with an applied-filter count and Clear filters. Active filter summaries stay visible after closing the sheet. Closing filters preserves the search and results; no new search backend is needed.

Each exercise row shows the existing illustration, name, and a concise equipment or muscle summary. Detail prioritizes the full illustration, plain-language instructions, and equipment. Supporting muscle classifications and programming metadata move to **More details**. Source citations and condition-reference links remain accessible. Exercise guidance uses the catalog descriptions and their reviewed sources.

My workouts supports the existing create, edit, reorder, start, and delete flows. The routine editor keeps all prescription types, unit confirmation, unsaved-draft recovery, and active-session start restrictions. Use the new shared visual rules without weakening its existing validation.

Condition guide remains explicitly educational. Each reference retains its sources, evidence limitations, linked exercises, and release status. The UI does not offer to activate an unreviewed condition or mark its exercises as automatically safe. Empty supported-condition choices are explained only where they affect a user's action, without advertising a feature that cannot run.

## Profile and settings

Replace the always-expanded form with these rows, each showing a brief summary:

- **Training preferences** — goals, experience, frequency, preferred session length, muscle priorities.
- **Equipment** — existing available equipment and band setup.
- **Movement and restrictions** — movement answers, sensitivity preferences, existing condition/reassessment state, returning after a break, and excluded exercises with Remove actions.
- **App preferences** — units, illustration preference, theme, and language. Language remains a device-only setting; theme, units, and illustration preference keep their existing profile storage and archive semantics.
- **Your data** — export, restore, and deletion with their present scope and confirmations.
- **About and credits** — attribution and licenses.

Details are screens with Back, not successive nested modal sheets. Use existing persistence semantics: instant-save preferences continue to save immediately; draft editors keep explicit Save and Cancel. Back from a draft follows its existing cancel/unsaved-change handling. Required reassessment or legacy condition reports create a compact, actionable notice at the settings overview so regrouping cannot bury a block on training.

Retain the LocalData outcome observer above scrolling/grouped content. Restore/delete completion must still route correctly if the user leaves the visible row while the operation is running. Existing write gates, confirmations, and fresh-start requirements remain.

## First-run setup

Apply the same visual system and plain language to the existing eight-step setup. Keep the current required answers, step order, validation, review step, draft restoration, and restore-from-file entry. Ask the current step's question prominently, then show its controls. Place supplementary explanations beside the relevant choice or under Details, except information necessary to understand a restriction or unavailable recommendation.

Use **Name**, **Your goals**, and **Review your setup** in place of implementation or codename language. Do not promise an automatic routine on the final screen when selected restrictions make generation unavailable. No new branching questionnaire or reduction in required safety answers is part of this project.

## Visual and accessibility rules

- Reuse Jetpack Compose, Material 3, and the existing WallCrawl components. No UI framework or font package is added.
- Preserve the red brand and white exercise contour artwork. Use opaque neutral surfaces, calmer hierarchy, and fewer bordered containers. Remove the decorative web backdrop from functional screens. Keep the wordmark compact.
- Red identifies the primary action or selection, not every section title. Success and errors also use text/icons so color is not their only signal.
- Establish shared semantic typography and theme tokens. Avoid fixed dark text colors in reusable type styles. Screen titles, section labels, body copy, and secondary copy are consistent across all destinations.
- Preserve 48 dp touch targets, visible focus, meaningful TalkBack labels/order, Android back behavior, keyboard insets, and font scaling. “Compact” means less redundant content, not tiny controls.
- All tasks support visible or labelled actions. The expand icon identifies the artwork as interactive. Exercise guidance remains readable with reduced motion enabled.
- Verify English and Spanish, dark and light, 320/360 dp widths, and at least 1.8x font scale. A scrollable large-text layout takes priority over an arbitrary above-the-fold target.

## Implementation reach and order

This is one coordinated project implemented in reviewable slices. Completion means all listed surfaces use the agreed hierarchy and remain functional, not merely a recolored Train screen.

1. Shared theme/type/card/button treatment, navigation labels, profile entry/return behavior, and grouped settings.
2. Train hierarchy, source chooser, preview/options, program entry, and all actionable restriction/error states.
3. Focused set logging, draft retention, compact rest/instructions, leave/finish behavior, and completion screen.
4. Progress hierarchy, Library/routines access and filters, exercise/research detail, and first-run visual/copy pass.
5. End-to-end native verification, screenshot refresh, localized documentation, and independent review.

Affected implementation includes `app/WallCrawlApp.kt`, `app/WallCrawlNavigation.kt`, `core/ui/theme`, shared UI components, and the existing Today, program, workout, Progress/history, exercises/conditions, profile, onboarding, and template presentation code. Presentation ViewModels may gain focused-editor/draft or navigation state where necessary. Repositories, schemas, archive codecs, generation policy, and research data are reused unchanged unless a separate demonstrated defect is reported and scoped.

Update EN/ES string resources together, navigation and Compose UI tests whose contracts change, screenshot fixtures, README navigation descriptions, and relevant accessibility/localization documentation. Do not blindly replace every “Today” in stored workout names or historical artifacts.

## Acceptance and evidence

- A returning user with an accepted set adjustment can see Start on Train at 360 dp default text size, in both languages; applicable blocking cues remain ahead of Start.
- A user with an active session can resume despite errors in new workout generation or program reads.
- Selected source and started session membership agree. Program and ordinary choices, manual templates, and paused/unavailable programs remain distinguishable.
- A user can log each supported measurement type, correct a completed set, skip/stop, record optional feedback, leave/resume, and finish partial/full workouts without data loss.
- A failed save, fast double tap, decimal fragment, rotation, process recreation, open keyboard, and changing the expanded set cannot silently erase or fabricate measurements.
- Verify single taps, repeated adjustments, cancellation, fractional steps, bounds and corrections to logged sets. On compact phones, adjusting measurements keeps Log set accessible during rest.
- The rest countdown survives ordinary UI transitions and uses the existing deadline, including background/resume behavior.
- Exercise navigation works in both directions with swipes and buttons. Verify first/last bounds, vertical scrolling, decimal drafts, completed sets, and the ongoing rest timer across a round trip. Tap the thumbnail or activate it with a keyboard to expand the selected demonstration, and verify a swipe across it only changes exercises. Check automatic playback and the reduced-motion still in both sizes, then dismiss the larger view and return focus to the same thumbnail.
- Recent and older history, exact prior performance, dose details, routine editing, condition citations, exclusions, and all settings/data actions remain reachable.
- Fresh setup, restore, deletion, legacy reports, pending reassessment, zero released conditions, empty history, catalog failures, unsupported program versions, and unavailable recommendations have explicit paths.
- Capture actual native screenshots for Train ready/active/blocked, active set/rest/failure, completion, Progress empty/populated, Library/filter/detail, grouped settings, and onboarding. Include Spanish large text and light theme examples.
- Run focused UI/navigation tests, relevant JVM tests, build and lint after implementation. Use test failures to identify changed user contracts rather than simply removing assertions.

The interactive mockup accompanying this document demonstrates hierarchy with sample data. It is not a working planner, medical restriction implementation, or substitute for the native accessibility and persistence checks above.

## Builder discretion and approval

The builder may choose component boundaries, whether a preview is a sheet or a detail screen, exact spacing and icon variants, and test fixture organization. These choices must preserve the specified behavior, native ergonomics, routes, and data contracts. No unresolved product decision is intentionally delegated to an implicit default.

Approval covers the proposed navigation, interaction, and presentation scope above. Implementation starts after owner approval of this document; the next artifact is the implementation plan.

Builder check on 2026-11-05: a fresh Android/Compose reviewer read this contract and connected code and reported “nothing important left.” This checks planning clarity, not implemented behavior or native accessibility.

Preview verification on 2026-11-05: browser checks exercised invalid numeric input, set logging, rest display, leave/resume, partial finish, library search, and settings return navigation. Train and active-session layouts were inspected visually; Train was captured in light and dark themes and checked for horizontal overflow at 320 and 390 px. These are checks of the sample-data design preview only. Native implementation and its acceptance checks have not started.

On 2026-11-07 the owner requested swipe navigation and direct artwork expansion with an icon, and removed the separate instruction and pause actions. Browser checks exercised touch and keyboard expansion, focus return, swiping across the artwork, horizontal trackpad input, boundary swipes, retained set drafts and rest time, and reduced motion in both sizes. Playback for bench press, one-arm dumbbell row, and lat pulldown matched their bundled Android frames.

On November 8 the owner chose hold-to-repeat for larger adjustments. The revised preview kept the same rows and Log set position at 320, 360 and 390 px. Touch and keyboard checks covered precision down to 0.01 lb, cancellation on release or drag-away, and saved drafts.

### First-use weight choices

On a weighted lift with no known working load, let the lifter choose a weight inline. Show the exercise’s prescribed reps and effort in plain language. When an explicitly comparable exercise was recently logged with the same equipment and load convention, show its actual load, reps and exercise name as context. Keep that comparison separate from the current exercise’s prescription. Return after a break, prior pain and exercise restrictions must influence whether a comparison is appropriate. When the profile includes a dumbbell rack, offer weights that are actually on it in the workout’s unit. Older entries do not specify whether a lifter recorded one dumbbell or both. Display the entry as logged. When the lifter confirms the reference was per dumbbell, offer nearby available weights up to that value. The new lift still starts unselected. The lifter can save, change, or clear the per-dumbbell weights in Equipment. Other equipment and a missing rack still support manual adjustment without a keyboard. Only logging confirms a working weight.

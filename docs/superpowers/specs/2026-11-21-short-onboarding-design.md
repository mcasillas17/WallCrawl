# Short onboarding with a first workout, then personalization — design

Date: 2026-11-21. Status: approved in conversation, awaiting written-spec review.

## Why

Test-user feedback on the first launch:

- "The onboarding is too long." One tester nearly deleted the app on the third setup page and wanted
  to see a sample workout before answering questions.
- "Too text-heavy." Suggested icons or images.
- "Explain the app before the setup form."

Today a new user sees the intro, then an 8-step wizard (Name, Goals, Experience and units, Movement,
Schedule, Equipment, Conditions, Summary) before any workout.

## Goal and success criteria

A new user reaches a real, safe workout in under a minute, answering three questions. The rest of the
profile is asked after they have finished that workout, and never blocks them.

- Fresh install: intro → one setup screen (3 questions) → Today shows a workout.
- The first workout and every workout before personalization is bodyweight only, low impact, and
  beginner level.
- Existing users and restored backups never see the new prompts.

## Scope

Two PRs, both targeting `main` (not stacked):

1. **Welcome screens v2**, implemented exactly as `docs/welcome-screens-v2.md`. It covers the intro
   changes (personalization tagline, icon-led screen 2 with the flow diagram and privacy badges). This
   design does not change it. Its "Get started → onboarding step 1" now lands on the basic setup screen.
2. **Short onboarding and personalization** (the rest of this document).

## Decisions

| # | Decision |
|---|---|
| 1 | Keep the launch animation and intro; ship welcome v2 as specified. |
| 2 | Basic setup is one screen: goal, days per week, session length. |
| 3 | The first workout is generated from a safe starter profile. |
| 4 | Follow-up questions (equipment, experience and movement, conditions) are offered on the workout summary after a completed workout. |
| 5 | "Not now" keeps starter workouts going, with a card on Today and a row in Profile. Nothing is ever blocked. |
| 6 | State is modelled as `onboardingCompleted = true` plus a new `personalizationCompleted` flag. |

### Approaches considered for decision 6

- **Chosen: basic setup counts as onboarding, plus a `personalizationCompleted` flag.** Every existing
  `onboardingCompleted` check (Today, programs, deloads, backup validation) keeps working unchanged.
  Cost: one column, one migration, one optional archive field.
- Rejected: a three-state onboarding enum. About 10 call sites would each need a rule for the middle
  state, with no user-visible benefit.
- Rejected: inferring "not personalized" from the data (bodyweight only + unknown movement). A user who
  really trains with bodyweight only would be prompted forever.

## 1. Basic setup (replaces the 8-step wizard)

One screen at the `ONBOARDING` route, no step counter, no Summary step.

1. **Goal**: icon tiles over the existing `FitnessGoal` values, multi-select (tap toggles), at least one
   required. Saved in tap order, so the first tapped is the primary goal.
2. **Days per week**: chips 2–6, default 3.
3. **Session length**: chips 20 / 30 / 45 / 60 minutes, default 30.

Footer button **"Show my first workout"**: saves the starter profile (below) with
`onboardingCompleted = true` and `personalizationCompleted = false`, then navigates to Today.

The existing "Restore from backup" entry stays on this screen.

### Starter profile written on save

| Field | Value |
|---|---|
| `name` | model default ("Crawler"); set by the optional name field on Personalize step 1 |
| `gender` | `UNSPECIFIED` (automatic artwork), editable in Profile |
| `goals`, `daysPerWeek`, `preferredDurationMinutes` | from the screen |
| `experienceLevel` | `BEGINNER` |
| `preferredUnit` | `LBS` if the device region is US, LR or MM, else `KG` |
| `availableEquipment` | `[BODYWEIGHT]` |
| `trainingConstraints` | `{LOW_IMPACT_ONLY}` |
| `movementCapabilities` | `MovementCapabilities.unknown()` |
| conditions | none |
| `personalizationCompleted` | `false` |

### Removed

The Name, Experience-and-units and Summary steps, their EN and ES strings, and their tests. The
Equipment, Movement and Safety (conditions) step composables are kept and reused by personalization.

## 2. Personalization flow

New route `AppRoutes.PERSONALIZE`. Four steps with a "1 of 4" step bar:

1. **Equipment**: an optional "What should we call you?" field (max 40 characters; blank keeps the
   current name; the "Crawler" placeholder is not prefilled), then the existing Equipment step.
2. **Experience**: the three levels as full-width rows, then "Time off from training?" (one-line hint;
   the guidance line only appears once a break is picked). Never blocks Next.
3. **How movements feel**: the movement-preference questions only, every one answered (Not sure
   counts). Finish with an unanswered question returns here.
4. **Conditions**: the existing Safety step.

- Back on step 1 leaves the flow and saves nothing.
- **Finish** saves all answers at once and sets `personalizationCompleted = true`. It removes the
  starter `LOW_IMPACT_ONLY` unless the user selected it in step 3. Then it returns to Today, and the next
  workout is generated from the full profile.
- **Nothing is saved before Finish.** Leaving midway leaves the starter profile untouched.

Entry points:

- **Workout summary** (`WorkoutSummaryScreen`): after a completed workout, when
  `personalizationCompleted = false`, a card above the existing actions:
  "Make it yours. Tell us your equipment and anything to work around. Your next workout adapts."
  [Personalize] [Not now]. Not now hides it for that summary only.
- **Today card**: see §3.
- **Profile**: a "Personalize your plan" row while the flag is false.

### Shared code

`OnboardingViewModel` (432 lines) holds the draft and validation for equipment, movement and
conditions. Extract only that shared draft and validation so both the setup and personalization view
models use it; do not duplicate it. No other refactoring.

## 3. Today and starter workouts

**Card**, shown at the top of Today when `personalizationCompleted = false` and the user has completed
at least one workout:

> **Personalize your plan**. Your workouts are bodyweight and low impact until you tell us more.
> [Personalize] ✕

- ✕ hides it for 7 days. The dismissal date is a device setting in a SharedPreferences file (like
  `introSeen`): not profile data, not exported, not restored.
- Before the first completed workout there is no card, so Today's first view is just the workout.

**Starter workouts** use the existing planner with the starter profile. No new planner code. While
the flag is false, the workout shows one line above the exercise list:
"New to this? Go easy, and stop if anything hurts."

**Guard test**: for every `FitnessGoal` × {20, 30, 45, 60} minutes, generate a starter workout and
assert every exercise is bodyweight only, low impact, beginner difficulty or lower, and that the session
fits the chosen length. If this cannot be made to pass, the fallback is a fixed starter template. That is
a design change to bring back for review, not a silent swap.

## 4. Data, migration, backup

- `UserProfile.personalizationCompleted: Boolean`, default `true` in the model.
- Room: the next migration after `MIGRATION_18_19` adds the column with `NOT NULL DEFAULT 1`, so every
  existing profile counts as personalized. Update the tracked Room schema.
- Archive codec: an optional `personalizationCompleted` profile field. When it is missing, restore as
  `true`. Exports always write it. Follow the codec's existing convention for the most recent additive
  optional field when deciding whether `ARCHIVE_VERSION` changes.
- Delete all data and restore behave as today. A restored archive carries its own flag.
- Unchanged: every `onboardingCompleted` check, `introSeen`, the splash, and the welcome flow beyond
  what welcome v2 specifies.

## 5. Tests

JVM:

- Starter-workout guard test (§3).
- Basic-setup save writes the starter profile and `personalizationCompleted = false`.
- Unit from region: US → LBS, DE → KG.
- Personalization Finish removes the starter `LOW_IMPACT_ONLY`, and keeps it when the user chose it.
- Leaving personalization before Finish leaves the profile unchanged.
- Migration: an existing row reads `personalizationCompleted = true`.
- Archive: a round trip with the field, and an old archive without it restores as `true`.

Instrumented:

- Fresh install: intro → basic setup → Today shows a workout and no card.
- Complete a workout → summary card → Not now → Today shows the card.
- Personalize → 3 steps → Finish → card gone, Profile row gone.
- ✕ hides the Today card.
- A pre-existing onboarded profile sees no summary card, Today card or Profile row.

Run device tests at 320×640 dp (the CI emulator size) before pushing.

## 6. Strings

New EN and ES strings for the basic setup screen, the summary card, the Today card, the Profile row,
the starter safety line, and the "Show my first workout" button, following `docs/localization.md`.
Delete the strings of the removed steps from both locales.

## Out of scope

- Fixing the exercise animation in which the bar moves instead of the person (separate; exercise still unidentified).
- Reworking the movement-preference or conditions steps themselves.
- Analytics on drop-off (the app has no analytics, by design).

# Accessibility baseline

This is the record for [Package 20](../ROADMAP.md#20-establish-an-accessibility-regression-baseline).
It says what was checked, how, and what was not. It is not a certification. Passing an automated
checker does not prove a flow is usable, and no physical device has been tested.

The audit began on `da8b24b`. While it was under review, #105, #106 and #108 redesigned most screens: the Train
tab, guided workouts with hold-to-repeat set logging, the Library and its condition reference, grouped
settings, and new Progress pages. The baseline was carried onto that design: where an element survived, its fix
was reapplied, and the new condition screens and controls were audited the same way. Fixes whose element the
redesign removed are listed under Limitations. All TalkBack walks below were made on the earlier design.

## Criteria

These are the standards measured against, not claims that each is met everywhere. What was verified is in the audit table, and what was not is under Limitations.

| Area | Criterion |
| --- | --- |
| Text contrast | WCAG 2.2 1.4.3: at least 4.5:1. Disabled controls are exempt |
| Non-text and state | WCAG 1.4.1 and 1.4.11: not by hue alone; selected fill against surface at least 3:1. Default outlines fall short; see Limitations |
| Touch targets | Android guidance: at least 48×48 dp, including Material's minimum interactive padding |
| Large text and narrow screens | Modelled on WCAG 1.4.4 and 1.4.10, which are written for web zoom: 320 dp at font scale 1.8. The sweep measures text and targets on the routes it reaches; partial clipping is not detected, and dialogs and sheets are not measured at 1.8× (see Limitations) |
| Moving content | WCAG 2.2.2 and 2.3.3: the exercise illustration can be paused and starts paused when animations are off; loading spinners were not checked at animator scale 0 |
| Status messages | WCAG 4.1.3: announced without moving focus; countdown ticks are never announced |
| Names, roles, state | WCAG 4.1.2: every control has a name, a role and its selected or expanded state |
| Reading and focus order | WCAG 2.4.3: order matches the screen, and focus returns where it came from after a dialog. Walked with TalkBack only, on the flows the table lists; focus return was recorded on the workout's discard dialog, not elsewhere |
| Keyboard | Fields stay reachable above the on-screen keyboard, and Back hides it first. Existing editor tests and the walk cover the editor; onboarding has no keyboard check. External keyboards were not tested |
| Scrolling and reflow | Modelled on WCAG 1.4.10: at 320 dp and 1.8× content scrolls and bounded panes leave actions reachable. The sweep scrolls each route's main list; the workout pane has its own test |

## How evidence is labelled

- **Automated**: Compose semantics and Google's Accessibility Test Framework (ATF) on API 34+, which run in CI on API 36 and 37, and the JVM
  palette test, which runs in the unit-test job. CI's API 26 leg runs only the `app` package, so component and feature
  tests, including the set-row and pattern tests, do not run there; see
  [android-target-sdk-37.md](android-target-sdk-37.md#ci).
- **TalkBack**: real TalkBack 16.0 on an emulator, with touches injected by a local script through the emulator
  console (the script and its logs are not in the repository), because `adb input` does not reach TalkBack's gesture detector. Spoken text was read from
  TalkBack's *Display speech output*, and focus from its on-screen highlight. `uiautomator` was not
  attached during these checks, because it suppresses TalkBack.
- **Emulator**: settings, screenshots and interaction with TalkBack off.
- **Physical device**: none.

## Reproduced before changing code

Emulator: both target-SDK findings reproduced on `da8b24b`, API 36, at 320 dp and font scale 1.8.

- **Codename text.** Worse than recorded: typed text was near-white on a white field, about 1.1:1.
  `WallCrawlTypography` hard-coded colours, and a text style's colour beats the field's own colour, so
  this affected any text field using the default style.
- **Bottom bar.** Labels broke mid-word.

`AccessibilityBaselineTest` on the unmodified app also reported routes with no heading, along with
undersized controls on Profile. At the base commit only History, the Progress analytics card, the template
editor's unit section and Today's deload card marked a heading. Every other route had none, and Today and
Progress had one only while those cards showed.

## Route and control audit

`AccessibilityBaselineTest` sweeps the routes it reaches: Train, Progress with its Over time and muscle pages,
the Library, the condition reference, Profile and each of its settings pages, Credits, Program, History and its detail, the template list, new
and edit template, an active workout, and a finished workout's summary, plus all eight onboarding steps. It runs them in English light and dark at 1.0×, in Spanish light at 320 dp at 1.0× and
1.15×, and in English and Spanish, light and dark, at 320 dp and 1.8×. Onboarding runs in English light at 1.0× and in all four combinations at large text.
Each route is scanned at the top and down its main list, up to 12 swipes. Besides names, roles, targets and
ATF, the scan reports text squeezed to nothing and any word broken between two letters across lines; a break at a digit or punctuation is not reported. It found many such breaks at
320 dp and 1.8× on Today, Exercises, Profile, onboarding and set logging, on the earlier design; those rows now wrap, or stack through `StackingRow`, which leaves normal text sizes as they were. The exceptions at normal size, besides every touch target in the Target table, which grows at all sizes: onboarding's training days are 48 dp squares spread across the row instead of about 43 dp wide tiles, and onboarding's equipment quick picks and Program's planning rows grew to 48 dp at every size. In the redesign, Progress's two weekly cards always stack inside the activity details, and Profile's choices sit below their headings on each settings page. The sweep also found words broken in the redesign at 320 dp, now fixed: the active workout's session name now has its own line at every size instead of a fixed share of the row with Leave and Finish; Progress's *All workouts* and Train's *Change* move below their heading or source when the two do not fit side by side; the template list's Create moves below the title at large text; Profile's movement summary puts each level below its movement at large text; the Library drops its decorative thumbnails at large text; and Train's workout name uses the title size instead of the headline size at large text, because a headline-size word such as *Acondicionamiento* was wider than the card. The open workout's name does the same. Credits gives each hyphen between letters an invisible break opportunity, because Android kept a long hyphenated compound in the artwork notice together and then split it mid-letter at large text.
Rows stack when the font scale times 412, divided by the screen width in dp, reaches 1.3: from stock scale 1.3 on a
412 dp phone, 1.15 at 360 dp, and about 1.01 at 320 dp, since 412 / 320 is 1.2875. Web
addresses and file paths (anything containing `/`) are exempt, because they have no break point, and so is a single word wider than its own text box when that
box already has the full content width of a 320 dp screen: 288 dp, less 1 dp for rounding. Those two still break. A long word in a narrower box is still
reported. Glyph widths differ by screen density and by platform font, so the same word can fit
on one device and break on another: "PERSONALIZADAS" broke only on API 26, "COMPLETADAS" and "Acondicionamiento" only at
160 dpi. The suites therefore
also ran on an emulator set to 320×640 dp, 160 dpi, the size CI's emulator uses, as well as, class by class earlier in this work, on 1080×2400 at 420 dpi (1080×1920 for API 26); those logs are outside the repository too.

The sweep sees normal content. The *States* column also lists loading, empty, error and dialog states, which
come from each screen's own tests or TalkBack where the row says so; the sweep does not force them. The
*TalkBack* column lists only what was actually walked; *Not walked* means no TalkBack evidence exists.

Two fixes apply to every route and are not repeated in the rows below. Each route now marks its title and its
main section titles as headings. Not every title is marked. Small kicker labels above a heading, such as the
editor's *Edit targets*, the Train card's source label and the open workout's *Workout in progress*, are left as
plain text, and so are some card and section titles, for example:
- the active workout's *Sets* label and Previous session card;
- Profile's muscle names above their priority chips, each of which already names its muscle;
- the Credits notice and error titles;
- onboarding's equipment quick picks;
- the template list's cards and empty state;
- History's Planned and Performed cards.

This list is not exhaustive: the sweep only fails a route with no heading at all, so it does not catch an unmarked
title.

The second shared fix: every spinner shown on its own is the shared `LoadingIndicator`, which is named and
announced. Three spinners sit beside their own text instead: local data's busy row, and the loading rows of Train
and of Progress's analytics card, whose spinners are hidden from screen readers so each row is read once.

| Flow | States checked | Fixes | TalkBack result |
| --- | --- | --- | --- |
| Workout summary | Normal | Workout name is a heading | Not walked |
| Bottom bar | Selected, 320 and 360 and 412 dp at 1.0–2.0× | Train, Progress and Library labels stay whole and are read once; two rows when a word does not fit | Walked on the earlier design only: on the four-tab bar at 1.0× and 1.8×, tab role, selection and one label; spoken position not recorded when the bar uses two rows |
| Onboarding | Normal, validation error, Back between steps | Typed text readable and field labelled *Your name*; goals, equipment, constraints and No constraints are checkboxes; unit, experience, days and movement levels are grouped radios; named days and slider in minutes; errors announced, including condition and reported-condition errors; Back walks steps and keeps answers, and holds during a save or restore, including a restore on the first step; entering a step is announced | Walked on the earlier design only: the first step. Continue announced the next step; system Back announced the first step and kept the typed name. The validation error was spoken without moving focus. The field was then labelled *Codename*; reading back a typed value was not walked. Steps 3–8 were not walked |
| Train | Normal, open workout, loading (loading set in code, not tested) | Headings on the greeting, the open workout and the workout name; status, deload and program messages announced; the loading row is read once; options and detail dialogs use buttons that dim while busy | Walked on the earlier design only: the Today screen this replaced, including its recommendation controls and exercise rows. Train was not walked |
| Active workout | Normal, rest running and finished, leave and discard dialogs | Headings on the session and exercise; rest finish announced, ticks silent; set-save refusals announced, repeated ones too; buttons dim when disabled; rest options state expanded; animation pausable and follows the system animation setting; dialog body scrolls | Walked on the earlier design only: a set completed; Discard and Keep both exercised, and Keep returned focus to Close and kept the set; 0 speech requests in 25 s of countdown, 1 in the 100 s spanning the finish |
| Set logging | Logged and stopped sets, feedback, stop reasons | Each value states what it is and its value, and the value follows every step; steppers name their field and set; felt-manageable answers are a grouped radio pair; the feedback disclosure states expanded or collapsed; buttons dim while saving | Walked on the earlier design only: the typed-field logger this replaced: its Complete toggle announced *checked* and a focused reps field spoke its value. The stepper logger was not walked |
| Template editor | Normal, keyboard, discard dialog | Dialog body scrolls; typed text readable; number fields state their value, range and error, each read once; steppers and picker rows are buttons; reorder, remove and set controls name their exercise; the picker search keeps its name once typed in; exercise names no longer ellipsized; the effort section title is a heading | Walked on the earlier design only: Back hides the keyboard, the next Back asks, the dialog is read in order, and Keep editing retained the draft |
| Template list | Normal, delete dialog (empty state set in code, not tested) | Edit and Delete name their template; a refused Start, including a condition refusal, is announced, and a repeat counts as a new attempt (checked in the view model); a long name in the delete dialog's title scrolls; icons dim while a template starts | Not walked |
| Program | Normal, confirmation, errors | Planning choices are a grouped radio set of 48 dp rows whose unavailable label dims; disabled buttons look disabled; errors announced, including a missing profile | Not walked |
| Progress, Over time and muscles | Normal, no match, error | Section headings; error announced; trend rows are one stop each; trend arrow says Up or Down, because a gain has no sign of its own; trend and volume text pass contrast; names no longer ellipsized | Walked on the earlier design only: filters, measure definitions, chart summary and per-week rows were read without touching the chart; the chart states *The list below has every value*; formulas unchanged. The redesigned pages were not walked |
| History | List, detail, disclosure, read error | Palette fixes; read and missing errors announced | Not walked |
| Library | Normal, filters, detail sheet (empty and error states set in code, not tested) | Load and link errors announced; filter titles are headings; *More details* states expanded or collapsed; frame counter silent; pause control | Walked on the earlier design only: the top of the catalog and one detail sheet: *Bottom Sheet*, then the illustration, Pause and the name |
| Condition reference | List, search, detail, notes, related reading | Headings on the guide, regions, condition and publications; named spinner; load and link errors announced; research notes and related reading state expanded or collapsed | Not walked |
| Condition controls | Profile, onboarding, pending review | Each question's answers are a grouped radio set of 48 dp rows; section titles are headings; refusals and errors announced; button labels take the button's colour, so a disabled button dims and the filled Submit keeps its contrast | Not walked |
| Profile and data | Overview, each settings page, edit, delete dialog | Goals are checkboxes and the last one is unavailable, with a hint saying why; experience, days, priorities and unit are grouped radios; slider speaks minutes; priority names its muscle; errors announced, including condition and reported-condition errors; dialog body scrolls | Walked on the earlier design only: goals, unit and duration slider. The grouped settings were not walked |
| Credits | Normal (loading and error states set in code, not tested) | Load and link errors announced | Not walked |
| Language change | Draft open in the editor | No change needed. Emulator, TalkBack off, on the earlier design: `MyDraft` survived the switch to Spanish, then dark theme, 320 dp and 1.8× | Not walked |

Decorative images deliberately keep a null description, including leading icons.

## Shared patterns

`core/ui/components/AccessibilityPatterns.kt` holds `a11yHeading()`, `a11yAnnounced()`, `a11yExpanded()`, `a11ySearchField()`,
`LoadingIndicator()`, `rememberAnimationsEnabled()`, `StackingRow()`, `isLargeText()`, which sets the stacking threshold. Most choices use Compose's own `selectable` or `toggleable`; the segmented controls and Material's menu items state their role and selection directly.
A refusal that can repeat in the same words is keyed on an attempt count, through `Refusal` in `core/ui/state` for the template list, the workout and the editor's unit choices; onboarding and the editor's Save count attempts in their own state fields, so each repeat is a new live-region node and is announced again. The RIR chips stay checkboxes because a second tap clears the value.
Typography no longer carries colour, so text takes the colour of the surface it is on.

## Measurements

*Before* values were calculated by hand, on the palette at `da8b24b`. #106 then changed the palette; its red, DE2045, also reads under 4.5:1 on cards, at about 4.3:1 on a light card and 3.4:1 on a dark one, so red text still uses `tertiary`. *After* values are palette colours that `ThemeContrastTest` checks against 4.5:1. That test checks colours, not where each is used; call sites were reviewed by reading, and ATF sees those on screen during the sweep, though not dialogs, which it does not open. The editor's discard dialog text is checked only by a pixel test with a 3:1 floor, so it is not held to 4.5:1 by a test.

| Text | Before | After |
| --- | --- | --- |
| Typed field text, light | F1F5F9 on FFFFFF, about 1.1:1 | Theme `onSurface` |
| Red text on a light card | E81A21 on F1F5F9, about 4.2:1 | B91218 (`tertiary`) |
| Red text on a dark card | E81A21 on 131722, about 3.9:1 | FF7A82 (`tertiary`) |
| Blue text on a light card | 0284C7 on F1F5F9, about 3.7:1 | 126494, #106's own blue, about 5.8:1 |
| Success text, light | 10B981 on FFFFFF, about 2.5:1 | 07704F |

| Target | Before | After |
| --- | --- | --- |
| Profile training days | 32×32 dp | 48×48 dp, wraps; label above the choices at every size |
| Onboarding training days | About 43×48 dp at 320 dp | 48×48 dp, spread and wrapping |
| Today regenerate | 32×32 dp | Replaced by #106 with a full-width button in Train's options |
| Profile unit | Height from padding | At least 48×48 dp |
| Muscle priority | 36–38 dp wide | At least 48×48 dp |

## Motion

The illustration loop is the only continuous motion besides Material spinners shown while a screen loads or a
backup runs. Spinners were not checked separately at animator scale 0. The loop follows the composition's
`MotionDurationScale`, the scale Compose's own animations use, which tracks the system animation setting as it
changes; it is also read again on each return to the app. When animations are off, a loop that was playing stops
and returns to its first frame, and a new illustration starts paused. Full-size illustrations with more than one
frame offer Pause and Play, so a reader with animations off can still choose to play one, and a reader's own Pause
keeps the frame where it stopped. Turning animations back on starts the loop again. Compact illustrations and
thumbnails have no control: thumbnails never animate, and compact ones follow the setting. The active workout's
compact demo shares its playback with the expanded demo, so Pause there holds both; elsewhere a compact
illustration has no Pause of its own. A pause holds across the
exercises of a workout (tested on the component), though not when the catalog sheet is reopened, and Play resumes
in the direction it stopped, unless an image failure has since replaced the frames or the screen was recreated,
which return to the first frame. Its frame counter is hidden from screen readers. Battery Saver's effect on the
scale was not tested.

Tests drive the setting both ways: component tests pass the state in, and `ExerciseIllustrationMotionTest` changes
the scale while compact and full-size illustrations are showing. During a scripted TalkBack walk on API 36 with
animator scale 0, on the earlier design, the exercise sheet opened offering *Play animation*, so the loop had not
started.

## Back

Onboarding acts only when Back is committed and shows no gesture preview: automated tests confirm that system Back steps back, a cancelled gesture stays on the step, and a committed one steps back. The template editor's existing tests drive its sheet through
a predictive gesture. TalkBack walks used discrete Back in the template editor and onboarding, in the intended
order. Predictive gestures were not repeated with TalkBack on, and the preview limitation in
[android-target-sdk-37.md](android-target-sdk-37.md#limitations-and-follow-ups) remains open.

## Limitations

- **TalkBack coverage is partial, so this baseline is not complete.** Only the flows marked above were
  walked, on API 36, and every walk was made on the design before #105, #106 and #108, so none covers the current
  Train, set logger, Library, condition screens, Progress pages or grouped settings. Spanish was walked only on Today, the bottom bar and the top of Profile, in dark
  theme at 320 dp and 1.8×. TalkBack's own words stayed English there because the device language was
  English. API 37 has no TalkBack walk.
- TalkBack exists on the API 36 Play and API 37 Google APIs images used here, but not on the API 26
  image, so the API 26 leg has no screen-reader evidence. CI's API 26 leg has no ATF either, because
  ATF needs API 34.
- ATF sees only what is on screen at each scroll position. The sweep opens no dialogs. The exercise detail
  sheet has its own scan; other dialogs and sheets rely on their screens' tests where those exist.
- *Display speech output* can show only the last fragment of an utterance. The word *selected* was often
  not captured, so automated semantics cover selection. Audio was not listened to.
- Long lists are scanned only as far as 12 swipes reach. Exercises is not scanned to its end.
- At 320 dp and 1.8× the bottom bar uses two rows, about a quarter of the screen, and onboarding's
  pinned footer takes about 40%. Content still scrolls, but the space is tight. The sweep reports zero-size text, broken words (except a word wider than a full-width box, as above) and small targets on what it reaches, and tests feed each detector and ATF a known-bad fixture; it does not detect partial clipping or every state.
- Focused field labels use the checked palette token by reading the code. No test measures a focused label: a pixel check passed with the old colour too, so it was removed.
- Test runs used local arm64 emulators at CI's 320×640 dp, 160 dpi, not CI's x86_64 images, and a direct single-process `am instrument` rather than Gradle. On 8 November 2026, after merging #105, #106 and #108, API 37 passed 824 of 824 and API 26's `app` package 68 of 68. The local API 36 emulator failed to boot four times on 7 November (the emulator's hang detector stopped it at start-up; the cause was not found), so API 36 last ran locally on 3 November and passed 634 of 635, the failure being `profileChoicesExposeTheirSelection`. That run predates the later fixes in this change, including the fix to that test, so no local API 36 run covers the final code. When this record was written, the final code had not run on API 36 or through Gradle's connected task; both are pending checks in CI's instrumentation jobs on the pull request. These counts and the TalkBack notes above come from local runs whose logs are outside the repository, so they cannot be reproduced from it alone.
- Four tests have failed intermittently and may again. `withTheKeyboardOpenThePickerSearchAndItsResultStayAboveIt` failed once on API 37 with the search 20 px under the keyboard, then passed ten repeats and two class runs on API 36 and 37; the cause is not known. `everyRouteInDarkTheme` failed once on API 36 with a whole Profile card measuring 0×0, then passed eight repeats; the sweep now skips nodes that are not placed, which is an inferred fix, not a proven one. `deletionWhileDetailIsOpenClearsTheBackStackBeforeRestore` composed off the main thread once under the Compose test clock; it is unchanged by this work. The onboarding gender selector test lost a tap once; closing the keyboard first is an inferred fix, not a proven one.
- When the bottom bar uses two rows, each row is its own Material navigation bar, so a screen reader may give a tab's position within its row rather than among all four. The walk did not record which.
- A dialog or bottom sheet is its own window, so the tests' Compose-level 1.8× does not reach it. In CI the delete dialog, stop-reason dialog and exercise sheet tests measure at the device's font scale. They were run once with the system font scale at 1.8 at 320×640 dp, where the delete dialog's title was 320 dp without its bound.
- Tinted selected rows fall below the 3:1 fill criterion: break duration, Profile goals and onboarding's choice and no-constraints rows. Their border, check mark or text colour carries the selection. The band-setup chips on Profile and onboarding's equipment step fall below it too: their selected fill is the theme's `secondaryContainer`, the same colour as the card they sit on, so only the check mark shows a chip is selected.
- README and docs screenshots, including #106's new ones, were taken without this baseline's fixes, so some colours and large-text layouts differ. They were not recaptured.
- A standing message inside a scrolling list, such as the workout's program read error, the editor's exercise limit or Profile's capability error, is likely to be announced again when it scrolls back into view. Inferred from the code; not walked.
- Onboarding's No constraints row is checked and unavailable while no constraint is selected, since ticking it only clears the chips; it is dimmed to 60% then. Not walked with TalkBack.
- The last selected goal is dimmed to 60% and announced as unavailable; the dimmed text falls below 4.5:1, which WCAG exempts for disabled controls. No test measures the dimming.
- If an illustration frame fails to load, its counter and Pause/Play stay over the placeholder, because a later frame may still load. No test forces this path.
- Disabled button labels dim to Material's 38%, below 4.5:1, which WCAG exempts for disabled controls, with the two exceptions below. This now includes buttons that used to force their label colour and so looked enabled while disabled, such as Program's Start, Pause and Resume and its replace or restart confirmation, Train's deload choices while a choice saves, the template list's and editor's Back, Create, Edit and Delete icons while a template starts or saves, and the label of an unavailable Program or Ordinary planning choice. Two keep a readable disabled label because it can be the only progress message: `WallCrawlPrimaryButton`, and the restore entry, whose label reads as restoring while a restore runs. The primary button's fill fades to 40% when disabled, but the restore entry is a text button with no fill, so it does not look disabled while a restore runs, and sighted users rely on its label to know a tap does nothing; where an outlined button shows one, as deleting all data does, a readable announced status sits beside it.
- A repeated refusal creates a new live-region node. Screen tests assert this for onboarding, the set row, the active workout's banner and the template editor. The template list's repeated Start and Delete are checked only in its view model, where the attempt count rises, and not on screen. None of these repeats was walked with TalkBack.
- Default outlines around unselected controls and fields are about 1.5:1, below WCAG 1.4.11's 3:1, and were
  not changed. Their labels and text meet the text criterion, and selected states differ by more than the
  outline, but this is an open gap.
- Live regions are asserted on the rest timer, the set-save banner, history, onboarding's movement refusal, the
  backup busy status and existing editor errors. On Train, the Library, the condition reference, Progress, Credits, Program and Profile they are set in code but not
  asserted.
- Material's bottom-sheet drag handle is 32 dp wide. *Close details* is the accessible way to close the exercise
  sheet, and the handle was left as Material ships it. It has no role, nor does Material's dismiss target behind the
  sheet. The sheet's structural scan excuses exactly the handle's size and role and the dismiss target's role. ATF's
  touch-target results are not counted anywhere on the sheet; the structural scan measures every target's size
  there instead.
- Fixes the redesign removed the element for, so they no longer apply: the refusal when Complete met a
  half-typed number, and the field errors read once, since the stepper logger has no typed fields and disables
  *Log set* while a value is invalid; the bounded pane holding the save banner and rest bar, since the workout now
  scrolls as one column; Today's regenerate spinner and suggested-workout card; the set number's large-text
  stacking; Progress's 360 dp weekly-card rule, since the cards now always stack; and the *Codename* label, since
  the field is now labelled *Your name*.
- Gaps found in the redesign and not fixed: the set row's inline error under its values is not announced, which
  could be noisy while stepping; each workout summary metric is two stops, label then value; the workout's session
  header does not scroll, so a very long name takes fixed height at large text; Train's detail dialog titles are
  not headings; and Train's source and deload rows set text beside a button without `StackingRow`, which the sweep
  checks for broken words but not for crowding.
- Not tested: physical devices, Voice Access, Switch Access, external keyboards, landscape phones and split-screen windows, where short height rather than narrow width is the constraint, and tablets as a
  separate audit. Font scale 2.0 is measured only for bottom-bar labels; the route sweep uses 1.0, 1.15 and 1.8.

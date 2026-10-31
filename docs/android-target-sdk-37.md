# Android target SDK 37 compatibility — 2026-10-31

This record covers the Issue #45 slice of
[Package 19](../ROADMAP.md#19-complete-android-and-release-maintenance). `targetSdk` moves
from 35 to 37. `compileSdk` stays 37, `minSdk` stays 26, and the application ID stays
`wallcrawl.elopenmike.com`. Permissions, components, the Room schema, the archive format and
the backup rules are unchanged. The target needed no toolchain or library change: AGP 9.4.0,
Gradle 9.7.1, Kotlin 2.4.21, KSP 2.3.11, JDK 17 and Build Tools 36.0.0 come from `main`.

## Decision and baseline

The review started from `ead925f` (`targetSdk` 35). The dependency-health remediation
([#97](https://github.com/mcasillas17/WallCrawl/pull/97), `d790077`) merged during the work,
so the combined state was validated again.

WallCrawl moves straight to 37. Every Android 16 and Android 17 behavior change was reviewed,
and the target-35 and target-37 builds were compared on the same API 37 emulator. One
regression appeared. Android 16's predictive Back caused it, and it is fixed below. No
platform protection is turned off. The manifest has no `enableOnBackInvokedCallback="false"`,
no orientation, resizability or aspect-ratio restriction, and no
`PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`. The theme does not opt out of edge-to-edge,
and backups stay disabled with their exclusions intact. `TargetSdkContractTest` pins target 37
and min 26 and keeps those opt-outs absent, so the next target change has to be deliberate;
`BackupPolicyResourceTest` still guards the backup rules.

## Fixed: an edited exercise sheet stayed shrunk after Back

With predictive Back, Material 3 1.4.0's `ModalBottomSheet` scales the sheet with the gesture.
On commit it calls `hide()` and leaves the scale for the hide animation. While a sheet holds
edits, the template editor vetoes that hide so it can ask *Discard changes to this exercise?*
After **Keep editing**, the sheet stayed at the gesture's reduced size. At target 35, the same
gesture on the same device leaves the sheet full size, with no in-app predictive animation.

`ExercisePrescriptionSheet` now handles Back itself while the sheet has edits
(`BackHandler(enabled = isModified)` inside the sheet), so the question appears without the
shrink. An unedited sheet keeps Material's predictive dismissal. Swipe and scrim dismissal are
unchanged. `TemplateEditorScreenTest.aBackGestureOnAnEditedSheetAsksFirstAndLeavesTheSheetFullSize`
drives the sheet window's Back dispatcher through started, progressed and committed. Before the
fix it failed (field width 879.8 px instead of 996 px); after the fix it passes. Its sibling,
`aBackGestureOnAnUneditedSheetKeepsMaterialsPredictivePreview`, checks that an unedited sheet still
shrinks with the gesture and returns to full size when the gesture is cancelled. It fails if the
handler is enabled for every sheet.

## Applicability

Sources were fetched on 2026-10-31: Android 16 changes for
[apps targeting 16](https://developer.android.com/about/versions/16/behavior-changes-16) and
[all apps](https://developer.android.com/about/versions/16/behavior-changes-all); Android 17
changes for [apps targeting 17](https://developer.android.com/about/versions/17/behavior-changes-17)
and [all apps](https://developer.android.com/about/versions/17/behavior-changes-all); and the
[Android 16 QPR2 release](https://android-developers.googleblog.com/2025/12/android-16-qpr2-is-released.html),
whose minor-SDK changes are additive. Surfaces were read from source and from the merged
manifest. The merged manifest holds `MainActivity`, AppCompat's disabled locale service, the
AndroidX Startup provider, Room's invalidation service and the ProfileInstaller receiver, plus
two debug-only test/tooling activities. The only permission is the signature-level
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; there is no `INTERNET` permission.

### Applies

| Change | Scope | WallCrawl surface | Outcome | Evidence |
| --- | --- | --- | --- | --- |
| [Edge-to-edge opt-out removed](https://developer.android.com/about/versions/16/behavior-changes-16#edge-to-edge) | Target 36 | `enableEdgeToEdge()`, insets consumed at the `NavHost`, IME and navigation-bar padding in onboarding, the editor and sheets | Already edge-to-edge; no change. The opt-out still works on Android 15, so the theme check matters | Contract test; manual bars, keyboard, dialogs and sheets on API 26, 36 and 37 |
| [Predictive Back by default](https://developer.android.com/about/versions/16/behavior-changes-16#predictive-back) | Target 36 on Android 16+ | `BackHandler` in the template list, editor and Profile; `NavHost` pops; four bottom sheets; seven dialogs, one of which (the editor's discard question) serves both the template and the sheet | One fix, above | Contract test; new UI test; manual gestures |
| Large-screen restrictions [ignored](https://developer.android.com/about/versions/16/behavior-changes-16#ignore-orientation), [opt-out removed](https://developer.android.com/about/versions/17/behavior-changes-17#large-screen-ignore-constraints) | Target 36/37, sw ≥ 600 dp | No restriction declared | No change | Contract test; sw800dp portrait and landscape fill the display, not letterboxed |
| [Lock-free `MessageQueue`](https://developer.android.com/about/versions/17/behavior-changes-17#lock-free-messagequeue) | Target 37 | No app reflection. Its [guidance](https://developer.android.com/about/versions/17/changes/messagequeue) requires Espresso 3.7.0+ or Robolectric 4.17+ | Espresso 3.7.0 already resolves; JVM tests do not use Robolectric | Full connected suite at target 37 on API 37 |
| [Safer native DCL](https://developer.android.com/about/versions/17/behavior-changes-17#safer-dcl) | Target 37 | Only `libandroidx.graphics.path.so`, loaded by `System.loadLibrary` from the APK (`extractNativeLibs="false"`) | Not affected; the rule covers `System.load()` | Launch and rendering on API 37 |
| [3-button predictive Back](https://developer.android.com/about/versions/16/behavior-changes-all#three-button-predictive-back) | All apps, Android 16+ | Same Back surfaces | No change | Long-press preview; Back on an edited template asks |
| [IME visibility after rotation](https://developer.android.com/about/versions/17/behavior-changes-all#ime-rotate) | All apps, Android 17 | Editor and onboarding text fields | Draft text survives; the keyboard is not reopened, as the platform now intends | Rotation with the keyboard open |
| [16 KB page compatibility](https://developer.android.com/about/versions/16/behavior-changes-all#16-kb-compatibility-mode) | All apps, Android 16+ | Same library | Already 16 KB aligned | ELF `LOAD` alignment 0x4000; `zipalign -P 16` passes |
| [Automatic themed icons](https://developer.android.com/about/versions/16/behavior-changes-all#themed-app-icons) | All apps, Android 16 QPR2 | Adaptive launcher icon | Already has a monochrome layer | `LauncherIconResourceTest` (API 33+) |
| Expanded dark theme ([QPR2](https://android-developers.googleblog.com/2025/12/android-16-qpr2-is-released.html)) | All apps, user setting | `Theme.WallCrawl` extends `Theme.AppCompat.NoActionBar` | No change. The window theme inherits `isLightTheme=false`, the documented signal not to invert; Compose paints both themes | Theme inheritance (AppCompat 1.8.0 to the framework `Theme`); not exercised on a device |
| [App memory limits](https://developer.android.com/about/versions/17/behavior-changes-all#app-memory-limits) | All apps, Android 17 | Whole app | No change | Not measured separately |

### Not applicable

| Change | Scope | Why it does not apply |
| --- | --- | --- |
| [Elegant text height](https://developer.android.com/about/versions/16/behavior-changes-16#elegant-text-height) | Target 36 | No `TextView`; Compose text; English and Spanish only |
| [Fixed-rate scheduling](https://developer.android.com/about/versions/16/behavior-changes-16#schedule-at-fixed-rate) | Target 36 | No `scheduleAtFixedRate`; the rest timer is a deadline on `SystemClock.elapsedRealtime()` |
| [Health permissions](https://developer.android.com/about/versions/16/behavior-changes-16#health-fitness-permissions), Bluetooth [bond loss](https://developer.android.com/about/versions/16/behavior-changes-16#new-intents-to-handle-bond-loss) and [removal](https://developer.android.com/about/versions/16/behavior-changes-16#bond-removal-api), [RFCOMM reads](https://developer.android.com/about/versions/17/behavior-changes-17#bluetooth-rfcomm-socket-change) | Target 36/37 | No health, sensor or Bluetooth permission or API |
| [MediaStore version](https://developer.android.com/about/versions/16/behavior-changes-16#mediastore-lockdown), [app-owned photos](https://developer.android.com/about/versions/16/behavior-changes-16#owned-photos) | Target 36 | No MediaStore or photo picker; export and restore use the Storage Access Framework |
| [Safer intents](https://developer.android.com/about/versions/16/behavior-changes-16#safer-intents), [intent redirection](https://developer.android.com/about/versions/16/behavior-changes-all#intent-redirect-attacks), [activity security](https://developer.android.com/about/versions/17/behavior-changes-17#activity-security) | Target 36/37, all apps | The only exported activity is the launcher. The app launches only the two document contracts, never an intent from incoming extras, and uses no `PendingIntent` or `IntentSender` |
| [GPU syscall filtering](https://developer.android.com/about/versions/16/behavior-changes-16#gpu-syscall-filtering) | Target 36 | Standard graphics APIs only |
| Local network permission ([16](https://developer.android.com/about/versions/16/behavior-changes-16#local-network-permission), [17](https://developer.android.com/about/versions/17/behavior-changes-17#local-network-protection-permission)), [ECH](https://developer.android.com/about/versions/17/behavior-changes-17#ech-by-default), [certificate transparency](https://developer.android.com/about/versions/17/behavior-changes-17#ct-default), [cleartext deprecation](https://developer.android.com/about/versions/17/behavior-changes-all#uses-clear-traffic-deprecation), [cross-profile loopback](https://developer.android.com/about/versions/17/behavior-changes-all#block-cross-profile-loopback) | Target 36/37, all apps | No `INTERNET` permission or networking |
| JobScheduler [quotas](https://developer.android.com/about/versions/16/behavior-changes-all#job-quota-opt) and [abandoned jobs](https://developer.android.com/about/versions/16/behavior-changes-all#abandoned-job-stop-reason) | All apps | No jobs, WorkManager, alarms, notifications or working services |
| [Ordered broadcast priority](https://developer.android.com/about/versions/16/behavior-changes-all#ordered-broadcast-priority) | All apps | One context-registered, not-exported clock-change receiver without a priority |
| [Disruptive accessibility announcements](https://developer.android.com/about/versions/16/behavior-changes-all#disruptive-a11y) | All apps | No `announceForAccessibility`; Compose live regions |
| [ART internals](https://developer.android.com/about/versions/16/behavior-changes-all#art-changes), [static final fields](https://developer.android.com/about/versions/17/behavior-changes-17#static-final-fields) | All apps, target 37 | No reflection on ART internals or static finals |
| [Widget memory](https://developer.android.com/about/versions/17/behavior-changes-17#memory-limit-widget), [physical-keyboard IME accessibility](https://developer.android.com/about/versions/17/behavior-changes-17#a11y-ime-pk), [password hiding](https://developer.android.com/about/versions/17/behavior-changes-17#hide-pwd-kbd), SMS OTP ([target](https://developer.android.com/about/versions/17/behavior-changes-17#sms-otp-protection), [all](https://developer.android.com/about/versions/17/behavior-changes-all#sms-otp-all-apps)), contacts [PII](https://developer.android.com/about/versions/17/behavior-changes-17#restrict-pii-fields-cp2-data-view) and [SQL](https://developer.android.com/about/versions/17/behavior-changes-17#enforce-strict-sql-checks), [content capture](https://developer.android.com/about/versions/17/behavior-changes-17#deprecate-setcontentcaptureenabled), background audio ([target](https://developer.android.com/about/versions/17/behavior-changes-17#bg-audio), [all](https://developer.android.com/about/versions/17/behavior-changes-all#bg-audio)) | Target 37, all apps | No widgets, `TextView`-based input, password fields, SMS, contacts, content-capture calls or audio |
| [Implicit URI grants](https://developer.android.com/about/versions/17/behavior-changes-all#restrict-implicit-uri-grants), [keystore limits](https://developer.android.com/about/versions/17/behavior-changes-all#per-app-keystore-limits), [touchpad capture](https://developer.android.com/about/versions/17/behavior-changes-all#touchpad-pointer-capture), [virtual device overrides](https://developer.android.com/about/versions/16/behavior-changes-all#virtual-device-owner-overrides), [companion timeouts](https://developer.android.com/about/versions/16/behavior-changes-all#companion-device-timeout) | All apps | No `ACTION_SEND` or camera intents, keystore, pointer capture, virtual devices or companion devices |

## Devices and results

Every run used an emulator; no physical device was used. The Android 17 images
(`system-images;android-37.0;google_apis;*`, revision 8) need emulator 37.2.11 or newer. A
separate SDK root with emulator 37.2.13 ran them on an Apple M4 host, under Hypervisor.framework
acceleration. All three AVDs use English (US), font scale 1.0 and 4 KB pages.

| AVD | Build | Display and navigation | Used for |
| --- | --- | --- | --- |
| Pixel 7, API 37, Google APIs arm64-v8a | Android 17 `CE2A.260420.051.A3` | 1080×2400, 420 dpi; gesture, plus 3-button checks | Full suite at target 35 and 37; manual flows; upgrade |
| Pixel 7, API 36, Google APIs arm64-v8a | Android 16 `BE2A.250530.026.F3` | 1080×2400, 420 dpi; gesture | Full suite (regression baseline); sheet fix |
| Pixel 2, API 26, Google APIs arm64-v8a | Android 8.0 `OSR1.180418.028.A3` | 1080×1920, 420 dpi; 3-button | minSdk smoke; full-suite diagnosis; manual checks |

| Check | Result |
| --- | --- |
| `./gradlew test lint assembleDebug` | 1,283 JVM tests passed. Lint reports 27 warnings and 1 hint; at target 35 it reported 28 warnings and the same hint. Only `OldTargetApi` is gone, and nothing was added |
| Connected suite, target 37, API 37 | 531 of 532 on the final code. The failure, `TemplateEditorScreenTest.aKilogramLoadStatesTheKilogramBoundItIsCheckedAgainst`, then passed 5 of 5 alone and in two full runs of its class (34 of 34). Earlier full runs: 530 of 530, and 530 of 531 (the intermittent `ProgramNavigationTest` case, then 14 of 14 twice) |
| Connected suite, target 37, API 36 | 532 of 532 on the final code. Earlier full runs: 530 of 530 and 531 of 531 |
| Target-35 baseline (`ead925f`), API 36 and 37 | 4 failures under heavy host load; each passed when rerun alone |
| CI's API 26 leg (app package) | 37 of 37, run locally with CI's two-step script; the report check passed |
| Full suite on API 26, target 37 | 516 of 529 passed. The same 12 tests fail at target 35, and the 13th was a timing failure that passes on rerun. The 12 are test-environment limits, listed under Limitations. This run came before the two sheet tests, which also pass on API 26 |
| Python gates (importer, release, acceptance audit, pinned catalog) | Passed |
| APK and merged manifest | Only the target changed from 35 to 37; min 26, package, permissions and components are unchanged; backup rules and locale config are byte-identical. The unsigned release build has the same components, minus two debug-only test/tooling activities |

Manual checks were driven on the API 37 emulator through `adb` input, with animations on, unless
noted:

- **Launch and onboarding.** Fresh launch is edge to edge. The language can change from the
  in-app chip and from system settings; the typed codename survives the recreation.
- **Predictive Back and lifecycle.** Back-to-home and in-app pop previews appear; a cancelled
  gesture keeps state. The rest timer kept counting from its deadline while the app was in the
  background. After a background process kill, the active workout and its logged set came back.
  The rest countdown is in memory by design and was not restored.
- **Workout.** Back leaves an active workout for Today, which offers Resume. Discard and finish
  questions appear, and the completion summary leads to history detail.
- **Template editor.** The keyboard is padded. The first Back hides the keyboard, and the second
  asks before discarding. **Keep editing** keeps the draft. Picker and sheet gestures preview,
  cancel and commit. Drafts survive rotation and background process death.
- **Profile and local data.** Capability-edit Back asks first. Export works, as does cancelling
  it. Delete-all returns to onboarding, and a fresh-start restore returns to Today.
- **Display variations.** Spanish at 320 dp with font scale 1.8. Light and dark themes. With
  3-button navigation, tapping Back on an edited template asks, and a long press shows the
  preview. At an sw800dp override the app fills the window in both orientations.
- **API 36.** Back-to-home preview, and the sheet fix with real gestures.
- **API 26.** Screens render; the per-app language persists across a force stop. The picker opens
  with the keyboard shown: the first Back hides it and the second closes the picker. The sheet's
  Apply stays above the keyboard.
- **In-place upgrade.** The target-35 debug build held data, then the target-37 build replaced it
  with `adb install -r`, signed with the same debug key. The first-install time was unchanged,
  Room stayed at `user_version` 16, and every row was kept. This does not test release signing,
  which is not configured.

## CI

Pull requests, `main` and tag releases each run the instrumentation job three times (in
[`ci.yml`](../.github/workflows/ci.yml) and [`release.yml`](../.github/workflows/release.yml)).
A tag cannot publish unless all three pass.

| Leg | Runs | Why |
| --- | --- | --- |
| API 36 | Full suite | Existing regression baseline |
| API `37.0` | Full suite | The target level. Android 17's SDK packages are versioned `37.0` |
| API 26 | The `wallcrawl.elopenmike.com.app` package | The minSdk boundary, kept short. It covers installed SDK levels, the packaged backup policy, the launcher icon, and real navigation, Back, recreation and restore |

The API 26 leg selects a package because AGP 9.4.0 keeps only the first class of a
comma-separated `class` argument. Each leg also fails when its JUnit report recorded no test.
AGP can log an APK install failure, write no report and still exit 0; that was reproduced
locally. CI uses x86_64 Google APIs images, which run only on GitHub-hosted runners; all local
evidence above comes from arm64 images. The stable-channel emulator that CI installs (37.2.13)
meets the Android 17 image's 37.2.11 minimum.

## Limitations and follow-ups

- No physical device, API 35 (Android 15) or API 27–34 device, tablet or foldable was tested;
  large screens were checked only with an emulator display override.
- On API 26, 12 tests fail at both targets because of the test environment, not the app:
  - Seven Compose `captureToImage` checks of dialogs, which need API 28.
  - Two tests that build 32–35 MB fixture strings, which exceed the AVD's 48 MiB heap
    growth limit.
  - `LauncherIconResourceTest`'s monochrome check, now limited to API 33+, where the API exists.
  - Two keyboard checks. The picker's search field opens the keyboard automatically on API 26,
    and the component test host lacks the app's `adjustResize` window. The real app behaves
    correctly in both cases (manual checks above).
- Still open from earlier releases and unchanged here: the onboarding codename text is hard to
  read in the light theme; bottom-bar labels break mid-word at 320 dp with font scale 1.8; and
  the predictive preview from a full-screen route shows the previous tab without the bottom bar
  until the gesture commits.
- Some device tests fail intermittently under heavy host load:
  - `ProgramNavigationTest.deletionDuringProgramConfirmationClearsProtectedNavigationAndSavedModeBeforeRestore`
    sometimes misses the Today list after a restore. This happens at target 35 too.
  - `TemplateEditorScreenTest.withTheKeyboardOpenTheSheetStillReachesItsFieldErrorAndApply`
    failed `main`'s API 36 run at `d790077`, which targets 35.
  - `TemplateEditorScreenTest.aKilogramLoadStatesTheKilogramBoundItIsCheckedAgainst` failed once
    here on API 37 while the keyboard opened, then passed every rerun.

  All pass in other runs. Three legs make such failures more likely to show up, so stabilizing
  these tests is a follow-up.
- Package 19 still owns Room schema export, the stale schema-10 CI comments, signing and
  shrinking, and the next alpha.

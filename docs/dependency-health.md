# Dependency health — 2026-10-31

Package 19 dependency-health slice, based on `origin/main` at
`ead925f85d846bdba6c1728da1dcdeb85faa1dfd`. This is partial remediation:
three advisory ranges are removed from the candidate's resolved graph. Six
GitHub alerts still require follow-up, including two whose ranges disagree with
upstream backports. No alert was dismissed and no candidate graph was submitted.
Default-branch alert closure requires merging and refreshing its dependency graph.

## Inventory and paths

GitHub reported **9 open alerts: 1 critical, 3 high, 5 medium**. All were attributed
to `settings.gradle.kts`, with no scope classification. The successful
[default-branch submission](https://github.com/mcasillas17/WallCrawl/actions/runs/41179037708)
used the baseline commit above. The exported SBOM, Gradle resolution reports and
plugin class origins agree: these are tooling dependencies, not direct settings
declarations. HttpClient's two versions belong to different classpaths, not stale
duplicate snapshots. The graph plugin leaves scope unset by default; see its
[reporting documentation](https://github.com/gradle/github-dependency-graph-gradle-plugin#configuring-the-scope-of-dependencies).

Path abbreviations below expand as follows. All coordinates are Maven coordinates;
the version on each owning artifact is unchanged by this slice.

- **AGP**: root buildscript `classpath` →
  `com.android.application:com.android.application.gradle.plugin:9.4.0` →
  `com.android.tools.build:gradle:9.4.0`. The app inherits this plugin classloader;
  its own `buildEnvironment` has no separate dependency tree.
- **Lint**: `:app:androidLintTool` → `com.android.tools.lint:lint-gradle:32.4.0`
  → `com.android.tools.lint:lint:32.4.0` → `com.android.tools.lint:lint-api:32.4.0`
  → `com.android.tools.lint:lint-model:32.4.0`. This tool has a separate classloader.
- **BC**: AGP → `com.android.tools:sdk-common:32.4.0`, AGP →
  `com.android.tools.build:builder:9.4.0`, and AGP → builder →
  `com.android.tools.build:apkzlib:9.4.0` → the BC packages below.
  Lint → sdk-common also resolves them. `bcpkix-jdk18on` → `bcutil-jdk18on`
  → `bcprov-jdk18on` are all `1.80.2`; sdk-common/builder/apkzlib also request bcprov.
- **Lang**: AGP or Lint → `com.android.tools:repository:32.4.0` →
  `org.apache.commons:commons-compress:1.27.1` → `org.apache.commons:commons-lang3`.
  `sdk-common:32.4.0` and `sdklib:32.4.0` supply additional compress paths.
- **HTTP**: Lint → `com.android.tools:sdklib:32.4.0` →
  `org.apache.httpcomponents:httpmime:4.5.6` → `org.apache.httpcomponents:httpclient`.
  AGP → `com.android.tools.analytics-library:crash:32.4.0` already selects `4.5.14`,
  overriding AGP's sdklib/httpmime request for `4.5.6` on that classpath only.
- **Kotlin**: root Compose plugin marker →
  `org.jetbrains.kotlin:compose-compiler-gradle-plugin` →
  `org.jetbrains.kotlin:kotlin-gradle-plugin` and `kotlin-gradle-plugins-bom`.
  These all move from `2.4.10` to `2.4.21`. AGP's KGP request `2.2.10` is raised
  by the same graph. KSP's plugin remains `com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.11`.

| Alert / package | Owning path; scope | Resolved before → after | Disposition / evidence / limitation |
| --- | --- | --- | --- |
| [#62, GHSA-9pwp-9qqc-pr26](https://github.com/advisories/GHSA-9pwp-9qqc-pr26), bcprov (critical) | BC; build and lint | `1.80.2 → 1.80.2` | Unresolved. Patched upstream in `1.85`; qualified AGP/BC upgrade needed. |
| [#63, GHSA-qp49-qgx5-5m26](https://github.com/advisories/GHSA-qp49-qgx5-5m26), bcprov (high) | BC; build and lint | `1.80.2 → 1.80.2` | Unresolved. Same owner/compatibility boundary as #62. |
| [#28, GHSA-c3fc-8qff-9hwx](https://github.com/advisories/GHSA-c3fc-8qff-9hwx), bcprov (medium) | BC; build and lint | `1.80.2 → 1.80.2` | Reporting discrepancy: upstream identifies `1.80.2` as a fixed backport; no new fix claimed. GitHub still flags `<1.84`. |
| [#27, GHSA-wg6q-6289-32hp](https://github.com/advisories/GHSA-wg6q-6289-32hp), bcpkix (medium) | BC; build and lint | `1.80.2 → 1.80.2` | Reporting discrepancy: upstream identifies `1.80.2` as fixed; no new fix claimed. GitHub still flags `<1.84`. |
| [#24, GHSA-3677-xxcr-wjqv](https://github.com/advisories/GHSA-3677-xxcr-wjqv), jose4j (high) | AGP → `com.android.tools.build:bundletool:1.18.3` → `org.bitbucket.b_c:jose4j`; build | `0.9.5 → 0.9.5` | Unresolved. Patched library exists (`0.9.6`); no qualified bundletool/AGP replacement in this slice. |
| [#21, GHSA-2363-cqg2-863c](https://github.com/advisories/GHSA-2363-cqg2-863c), JDOM (high) | AGP → `com.android.tools.build.jetifier:jetifier-processor:1.0.0-beta10` → `org.jdom:jdom2`; build | `2.0.6 → 2.0.6` | Unresolved. Patched library exists (`2.0.6.1`); Jetifier parser behavior must also be qualified. Jetifier is not enabled here. |
| [#61, GHSA-j288-q9x7-2f5v](https://github.com/advisories/GHSA-j288-q9x7-2f5v), Commons Lang (medium) | Lang; build and lint | `3.16.0 → 3.18.0` on both | Candidate remediation: constraints on root `classpath` and `androidLintTool`. Resolved graph, plugin class origin, binary API comparison and long-input probe verified. |
| [#60, GHSA-7r82-7xv7-xcpj](https://github.com/advisories/GHSA-7r82-7xv7-xcpj), HttpClient (medium) | HTTP; lint, plus AGP build | lint `4.5.6 → 4.5.14`; AGP `4.5.14 → 4.5.14` | Candidate remediation: constrain only `androidLintTool`; dependencyInsight and generated graph confirm selection. |
| [#56, GHSA-r937-wjx7-w2jp](https://github.com/advisories/GHSA-r937-wjx7-w2jp), KGP (medium) | Kotlin; build plugin/cache | `2.4.10 → 2.4.21` | Candidate remediation: stable Compose/Kotlin plugin update; KSP and Kotlin compilation verified. No beta adopted. |

All seven flagged packages were absent from debug/release compile and runtime,
debug unit-test compile/runtime, and debug Android-test compile/runtime resolution.
The full `:app:dependencies` report and an enumeration of all resolvable project
configurations found flagged packages only in the root plugin classpath and lint
tool configuration. This covers debug/test tooling too, not just release runtime.
KSP's Room processor configurations and the unified test platform did not add any
of these packages. This is resolved dependency evidence, not a claim that arbitrary
shaded copies in every third-party binary were audited.

Gradle's **own distribution** is a different classloader: `9.7.1` includes
HttpClient `4.5.14`, Commons Lang `3.20.0`, and bcprov/bcutil/bcpg `1.85`.
Those do not replace the older AGP libraries: loading representative classes
through the applied Android plugin confirmed the exact plugin versions above.
The submission reports Gradle as `org.gradle:gradle-core:9.7.1`, rather than
enumerating its distribution jars individually.

## Security decisions and remaining work

**BC:** the [name-constraint advisory](https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%908763)
requires certificate-path validation with attacker-controlled email/URI certificate
names; the [ASN.1 advisory](https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%9013506)
involves deeply nested CRLs and lazy parsing. Android tooling consumes certificates,
keystores and dependency artifacts; build-time availability and integrity matter.
The sdk-common source uses BC in keystore/certificate generation. This investigation
does not establish that those specific vulnerable validation paths execute in a
WallCrawl build, and does not establish that they are unreachable.

The upstream [LDAP notice](https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%900636)
and [composite-signature notice](https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%905588)
both explicitly list `1.80.2` as fixed. Published `1.80.2` sources corroborate the
LDAPUtils parsing change and the `atLeastOneChecked` composite-verifier guard.
Probes against the resolved `1.80.2` jars also confirmed filter escaping and rejection
of an empty composite-signature sequence.
These are advisory-range disagreements, not stale dependency submissions. Keep
the observations visible; resolution requires an upstream advisory correction or
a qualified upgrade beyond GitHub's affected ranges, not deleting graph entries.

Google's published [AGP 9.4.1 POM](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.4.1/gradle-9.4.1.pom),
[AGP 9.5.0 POM](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.5.0/gradle-9.5.0.pom)
and [sdk-common 32.5.0 POM](https://dl.google.com/dl/android/maven2/com/android/tools/sdk-common/32.5.0/sdk-common-32.5.0.pom)
still use BC `1.80.2`, bundletool `1.18.3`, Jetifier `1.0.0-beta10` and
commons-compress `1.27.1` (also checked in sdk-common/sdklib/repository `32.4.1`).
Upgrading AGP to either release does not fix these paths.
BC replacement is left open until a stable AGP release incorporates aligned
bcprov/bcpkix/bcutil `1.85+`, or a separate focused compatibility qualification
establishes those replacements across certificate/signature/packaging operations
and lint. Ordinary app tests do not qualify every cryptographic tooling API.

**jose4j:** the advisory requires decompression of hostile compressed JWE tokens.
[Bundletool 1.18.3's transparency code](https://github.com/google/bundletool/blob/1.18.3/src/main/java/com/android/tools/build/bundletool/transparency/CodeTransparencyCryptoUtils.java)
uses JWS parsing/signature verification; its inspected production imports do not
show JWE decryption. This reduces the demonstrated exposure, but is not a complete
reachability proof. The release workflow builds a debug APK and does not invoke
code-transparency commands. Resolution requires an AGP/bundletool release selecting
jose4j `0.9.6+`, or qualification of an override against bundletool's transparency
and hostile-token paths. This PR does not claim the library is safe merely because
the app is offline.

**JDOM:** XXE depends on parser configuration and attacker-controlled XML/POM input.
The published Jetifier source constructs a default `SAXBuilder`; its XML helper
does not explicitly disable external entities. [JDOM 2.0.6.1](https://github.com/hunterhacker/jdom/releases/tag/JDOM-2.0.6.1)
fixes the advisory, but a version-only override would not prove Jetifier's default
parser rejects hostile external entities: a local probe using a task-owned canary
file confirmed that default `SAXBuilder` expands it on both `2.0.6` and `2.0.6.1`.
`android.enableJetifier` is unset
(disabled by AGP's default), and AndroidX is enabled. Keep it disabled; enabling it
requires a separate audit. Resolution needs a supported owner update/removal, or
tested parser hardening plus the patched library. No required library is excluded.

**Remediated inputs:** Commons Lang's issue is recursive class-name lookup on very
long input; no app-runtime call path was found. The bounded Java probe failed with
`StackOverflowError` on `3.16.0` and returned `ClassNotFoundException` on `3.18.0`.
HttpClient's issue is malformed URI authority selecting an unintended host (despite
the advisory's “cross-site scripting” title); SDK/lint tooling may perform network
operations. KGP's issue is unsafe build-cache metadata deserialization, so a poisoned
cache can affect the build host. Existing read-only PR permissions, credential-free
checkout and Gradle Actions cache isolation remain in place; they are not substitutes
for upgrading vulnerable tooling.

## Compatibility and scope

- Kotlin/Compose `2.4.21` is a [stable bug-fix release](https://kotlinlang.org/docs/releases.html),
  in the fixed `2.4.20+` line. Compose's plugin and KGP resolve together through its
  BOM. [AGP's built-in Kotlin documentation](https://developer.android.com/build/releases/agp-9-0-0-release-notes#runtime-dependency-on-the-kotlin-gradle-plugin)
  permits newer KGP versions. KSP `2.3.11` uses [KSP2](https://github.com/google/ksp/blob/2.3.11/docs/ksp2.md),
  rather than KSP1's compiler-plugin version coupling; no KSP upgrade is needed by
  the observed compilation. Room remains `2.8.5`, with no schema change.
  Generated `.kotlin/` compiler session data is ignored, as recommended by Kotlin.
- [Kotlin's fully tested matrix](https://kotlinlang.org/docs/gradle-configure-project.html)
  lists Gradle through `9.7.0` and AGP through `9.3.1` for KGP `2.4.20–2.4.21`,
  and explicitly permits newer releases with possible warnings/feature limitations.
  Retained Gradle `9.7.1`/AGP `9.4.0` are outside that fully tested matrix; local
  validation is evidence for this app, not a claim of broader vendor certification.
- [AGP 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
  requires Gradle `9.6.0+`, JDK 17 and Build Tools `36.0.0`, and supports API 37.
  Those requirements are retained. Local checks use Homebrew JDK `17.0.20.1`,
  macOS arm64, SDK platform 37 and a dedicated API 36 arm64 emulator; CI uses
  Temurin 17 and API 36 x86_64. `targetSdk` stays 35 in this slice; the later move to 37 is
  recorded in the [target SDK 37 record](android-target-sdk-37.md).
- [Commons Lang 3.18.0 release notes](https://github.com/apache/commons-lang/blob/rel/commons-lang-3.18.0/RELEASE-NOTES.txt)
  retain Java 8+ and identify the recursion fix. A japicmp `0.23.1` comparison
  of the published `3.16.0` and `3.18.0` jars found no binary-incompatible public/
  protected API changes. Constraints apply only to root buildscript `classpath`
  and `:app:androidLintTool`, allowing higher versions if owners later request them.
- [HttpClient's 4.5.14 binary compatibility report](https://hc.apache.org/httpcomponents-client-4.5.x/current/httpclient/clirr-report.html)
  reports no incompatibilities against 4.5. The constraint matches the version
  already used by AGP; it does not substitute the incompatible HttpClient 5 API.
- Wrapper URL/version and workflow SHAs remain unchanged. CI/release use
  setup-gradle's default wrapper-JAR validation; dependency-submission has its own
  action defaults. The wrapper currently has no `distributionSha256Sum` pin.
  No checks, permissions, generated-catalog checks or release validation were weakened.

Remove each constraint when its owning plugin selects a patched version on **all**
affected classpaths without the constraint, then repeat dependency and build checks.
Nothing was added to app `implementation` to mask a plugin dependency.

## Validation and reproduction

Baseline `./gradlew test lint assembleDebug --stacktrace --no-daemon` passed:
56 tasks executed. Focused Kotlin compilation passed after its update. Candidate
dependency resolution and local graph generation passed. Final results:

| Command / check | Result |
| --- | --- |
| `python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v` | 156 tests passed |
| `python3 -m unittest discover -s tools/release -p 'test_*.py' -v` | 6 tests passed |
| `python3 tools/workout-guide/verify_ai_acceptance_audit.py` | Passed; no acceptance metadata changed |
| `python3 tools/workout-guide/check_pinned_catalog.py` | Passed: 302 exercises, 906 SVG frames |
| `./gradlew clean test lint assembleDebug assembleRelease bundleRelease --stacktrace --no-daemon --no-build-cache --console=plain` | Successful, 113 tasks executed; 1,283 JVM tests, zero failures/errors/skips; lint zero errors, 28 warnings |
| `./gradlew connectedDebugAndroidTest --stacktrace --no-daemon --console=plain` with `ANDROID_SERIAL` selecting the task-owned API 36 emulator | Successful: 527 tests, zero failures/errors/skips; 32 tasks executed, 40 build prerequisites up-to-date |
| Resolved graphs, plugin class origins, local graph generation, `git diff --check` | Passed |

The clean build recompiled Kotlin/Compose and KSP for debug/release, built the
debug APK and **unsigned** release APK/AAB, and ran the normal test/lint gates.
The connected report includes the migration suites through 15→16 and 145 archive/
backup test cases. Schema 16, archive formats and their assertions were unchanged.
Tests executed rather than being restored from a build cache. Existing compiler/
Gradle deprecation warnings remain; this is not a claim of warning-free tooling,
physical-device certification or a signed-release test.

The Commons Lang probe uses Java 17 with `-Xss256k` and invokes
`ClassUtils.getClass("a.".repeat(10000) + "Missing")` against each published jar;
the old jar overflows while the candidate throws the expected class-not-found
exception. The JDOM probe uses a default `SAXBuilder` with a SYSTEM entity pointing
only at a generated local canary file. No real secrets or network target are used.

Use JDK 17 and the SDK described in README. Commands for inspecting the same paths:

```sh
./gradlew --version
./gradlew buildEnvironment :app:buildEnvironment :app:dependencies --no-daemon
./gradlew :app:dependencyInsight --configuration androidLintTool --dependency httpclient --no-daemon
./gradlew :app:dependencyInsight --configuration androidLintTool --dependency commons-lang3 --no-daemon
./gradlew :app:dependencies --configuration releaseRuntimeClasspath --no-daemon
./gradlew :app:dependencies --configuration debugRuntimeClasspath --no-daemon
./gradlew :app:dependencies --configuration debugUnitTestRuntimeClasspath --no-daemon
./gradlew :app:dependencies --configuration debugAndroidTestRuntimeClasspath --no-daemon
```

The pinned Gradle Actions `6.4.0` graph init script and its graph plugin `1.5.0`
successfully generated a local candidate snapshot using
`ForceDependencyResolutionPlugin_resolveAllDependencies`, with no configuration
exclusions. It contains only KGP `2.4.21`, Commons Lang `3.18.0`, HttpClient `4.5.14`
for those three packages, and still contains all residual packages. The local
snapshot identifies the maintenance branch and is **not submitted**. After merge,
the existing main-push workflow must submit the real merged commit; re-query the
alerts and SBOM then. No default-branch closure is claimed here.

The latest inspected [HttpClient update failure](https://github.com/mcasillas17/WallCrawl/actions/runs/40282187522)
and [bcpkix update failure](https://github.com/mcasillas17/WallCrawl/actions/runs/40282190899)
(2026-10-26) both still report `security_update_dependency_not_found`. These
transitives are not directly declared in the original manifests; a failed automatic
edit does not invalidate the graph. Dependabot remains enabled without ignore rules.

[PR #80](https://github.com/mcasillas17/WallCrawl/pull/80) remains independent:
Navigation Compose `2.10.0 → 2.10.1` is not one of these security remediations.
No existing PR is merged, closed or superseded. Package 14 analytics has no feature
changes here. Its checkout had no pending changes to these build declarations at
inspection. If either PR merges first, update the other normally from main and
rerun affected integration checks against this exact Kotlin/Compose and tooling
contract; do not force-push shared history.

Package 19 remains ongoing: target SDK behavior, schema export, signing/minification,
release posture and publication are separate work. This slice changes no planner,
analytics behavior, program/template implementation, UI resource, Room schema or
archive format.

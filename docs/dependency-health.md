Latest: [2026-11-01 follow-up](#follow-up--2026-11-01). The earlier dated evidence below is retained.

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

## Follow-up — 2026-11-01

This continues merged [PR #97](https://github.com/mcasillas17/WallCrawl/pull/97)
from freshly fetched `origin/main` at `da8b24b5137a56a55fe95f7ca2565c8f72840927`.
The refreshed GitHub inventory has **seven open alerts: one critical, four high,
two medium**. The successful [main graph submission](https://github.com/mcasillas17/WallCrawl/actions/runs/41288175463)
uses that same commit. The SBOM and local baseline resolution agree; this is not
an investigation of an obsolete pre-#97 graph. #97's KGP `2.4.21`, Commons Lang
`3.18.0` and HttpClient `4.5.14` protections remain, including lint's separate
constraints. No alert has been dismissed, and no security setting has changed.

### Current before/after disposition

The AGP, Lint, BC and Kotlin paths defined in the earlier inventory still apply.
AGP remains `9.4.0`, lint `32.4.0`, KSP `2.3.11`, Room `2.8.5`, Gradle `9.7.1`,
Kotlin/Compose `2.4.21`. Since the earlier record, main raised `targetSdk` to 37;
this follow-up retains it and the API 26/36/37 instrumentation matrix.

| Advisory / alert | Actual path and scope | Baseline → candidate | Disposition and qualification |
| --- | --- | --- | --- |
| [GHSA-xxph-c9ww-hj94 / #64](https://github.com/advisories/GHSA-xxph-c9ww-hj94), high | AGP → sdk-common and other Android tools; `androidLintTool` → lint-api/sdk-common; separate plugin/lint classloaders | Guava `33.4.0-jre → 33.7.2-jre` | Scoped constraints; actual AGP class origins, lint artifact origins, bounded deserialization checks, clean build/lint qualification. |
| Same Guava advisory | `ksp` → Room compiler → compiler-processing (also auto-common `1.2.1`); debug/release processor classpaths | `33.2.1-jre → 33.7.2-jre` | Constraint on `ksp` propagates to both `ksp*KotlinProcessorClasspath` and both `_agp_internal_*_kspClasspath` configurations; KSP output compiles. No schema/export change. |
| Same Guava advisory | Truth `1.4.5` → Guava; unit/instrumentation compile, runtime and lint-check classpaths | `33.4.3-android → 33.7.2-android` | Test-only constraints; selected **jar variants**, not just coordinates, verified. No application dependency added. |
| Same Guava advisory, additional distribution copy | Gradle's own `lib/guava-33.4.6-jre.jar`; a separate distribution classloader | `33.4.6-jre → 33.4.6-jre` | **Unresolved.** The latest stable Gradle `9.8.2` distribution also contains this jar. Project constraints cannot replace it. Needs a supported Gradle distribution with the patched library; no distribution-jar surgery. |
| Same Guava advisory, additional shaded copy | KSP `2.3.11` → detached `symbol-processing-aa-embeddable` engine; isolated parent of the Room processor loader, relocated `ksp.com.google.common` classes | Embedded `33.2.0-jre → 33.2.0-jre` | **Unresolved.** Latest stable KSP `2.3.13` bundles the same version. Jar metadata, actual clean-build class-load trace and all five bounded probes confirm the residual. Requires an upstream KSP artifact rebuilt with patched Guava; normal dependency constraints cannot replace relocated classes. |
| [GHSA-9pwp-9qqc-pr26 / #62](https://github.com/advisories/GHSA-9pwp-9qqc-pr26), critical | BC through sdk-common/builder/apkzlib, plus lint's sdk-common | bcprov `1.80.2 → 1.85` | Aligned bcprov/bcpkix/bcutil constraints on root `classpath` and `androidLintTool`. Synthetic certificate-chain validation reproduces the trailing-dot bypass before and rejects it after, with benign chains still accepted. |
| [GHSA-qp49-qgx5-5m26 / #63](https://github.com/advisories/GHSA-qp49-qgx5-5m26), high | Same BC tooling paths | bcprov `1.80.2 → 1.85` | Bounded lazy ASN.1 depth probe changes from accepting 257 nested sequences to controlled rejection; valid CRL signature/encoding checks pass. This is library-path qualification, not proof of an attacker-controlled CRL in this app's build. |
| [GHSA-c3fc-8qff-9hwx / #28](https://github.com/advisories/GHSA-c3fc-8qff-9hwx), medium | Same BC tooling paths | bcprov `1.80.2 → 1.85` | Candidate leaves GitHub's range. **Not a newly delivered LDAP fix:** upstream still identifies `1.80.2` as fixed, and its source/binary escaping check corroborates the backport. |
| [GHSA-wg6q-6289-32hp / #27](https://github.com/advisories/GHSA-wg6q-6289-32hp), medium | Same BC tooling paths | bcpkix `1.80.2 → 1.85` | Candidate leaves GitHub's range. **Not a newly delivered composite-signature fix:** upstream still identifies `1.80.2` as fixed; its empty-composite rejection was rechecked. |
| [GHSA-3677-xxcr-wjqv / #24](https://github.com/advisories/GHSA-3677-xxcr-wjqv), high | AGP → bundletool `1.18.3` → jose4j; root plugin classloader | `0.9.5 → 0.9.6` | Root-classpath constraint. Actual bundletool JWS parse/verify/fingerprint, tampered-signature and malformed-token checks; bounded compressed-JWE checks. |
| [GHSA-2363-cqg2-863c / #21](https://github.com/advisories/GHSA-2363-cqg2-863c), high | AGP → Jetifier processor `1.0.0-beta10` → JDOM; root plugin classloader | `2.0.6 → 2.0.6` | **Unresolved.** Jetifier remains disabled. Its actual XML helper expands a disposable local canary on both JDOM `2.0.6` and `2.0.6.1`; a version-only bump would not qualify parser behavior. |

All resolvable project configurations were enumerated, including debug/release
compile/runtime, test compile/runtime and tooling configurations. Guava is in
test and processor tooling, **not the shipped debug/release app**. BC, jose4j and
JDOM remain absent from app and test compile/runtime classpaths. KSP's compiler
classpath is distinct from the application runtime. KSP's shaded engine copy was
found through actual clean-build class-load tracing; this is not an exhaustive
audit of every shaded third-party library. Gradle's distribution is explicitly
inventoried separately: its BC bcprov/bcutil/bcpg jars are already `1.85`, and its
HttpClient/Commons Lang remain `4.5.14`/`3.20.0`.

### Owner selection and exposure evidence

Google's refreshed [AGP metadata](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml)
lists `9.5.0` as stable. The [9.4.1 POM](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.4.1/gradle-9.4.1.pom),
[9.5.0 POM](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.5.0/gradle-9.5.0.pom)
and [sdk-common 32.5.0 POM](https://dl.google.com/dl/android/maven2/com/android/tools/sdk-common/32.5.0/sdk-common-32.5.0.pom)
still request Guava `33.4.0-jre`, BC `1.80.2`, bundletool `1.18.3` and Jetifier
`1.0.0-beta10`; upgrading AGP alone is not remediation. Published metadata also
still lists [Room 2.8.5](https://dl.google.com/dl/android/maven2/androidx/room/room-compiler/maven-metadata.xml),
[Truth 1.4.5](https://repo.maven.apache.org/maven2/com/google/truth/truth/maven-metadata.xml),
[bundletool 1.18.3](https://dl.google.com/dl/android/maven2/com/android/tools/build/bundletool/maven-metadata.xml)
and [Jetifier beta10](https://dl.google.com/dl/android/maven2/com/android/tools/build/jetifier/jetifier-processor/maven-metadata.xml)
as their latest stable/current releases. The changes therefore use ordinary
minimum-version constraints on the owning configurations, without forces,
exclusions, application runtime additions or dependency-submission filters.

**Guava:** the [vendor advisory](https://github.com/google/guava/security/advisories/GHSA-xxph-c9ww-hj94)
requires attacker-controlled native Java serialization reaching the affected
collections. It is excessive allocation from a declared collection size; a
post-deserialization type cast cannot prevent it. AGP's published
[9.4.0 sources](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.4.0/gradle-9.4.0-sources.jar)
include `MergeJavaResourcesDelegate.loadMergeState`, which reads an
`ObjectInputStream` from the incremental state file before casting. This is a
concrete build-state trust boundary, not a demonstrated remote attack against
WallCrawl. Cache isolation and credential-free checkout remain useful defenses.

The [Guava 33.7.2 release](https://github.com/google/guava/releases/tag/v33.7.2)
provides both variants and describes bounded allocation. Its
[JRE POM](https://repo.maven.apache.org/maven2/com/google/guava/guava/33.7.2-jre/guava-33.7.2-jre.pom)
selects failureaccess `1.0.3`; Gradle resolution retains that transitive dependency
on the updated paths. Disposable probes run in separate `-Xmx64m` JVMs and exercise
CompactHashMap, CompactHashSet, both linked subclasses, and MapMakerInternalMap.
Old jars exhaust that bounded heap; patched jars reject truncated payloads or
complete with bounded allocation. Ordinary one-entry round trips also pass.
No real secrets, network victims or unbounded host-memory stress are involved.

Gradle's [version inventory](https://services.gradle.org/versions/all) and the
[9.8.2 binary distribution](https://services.gradle.org/distributions/gradle-9.8.2-bin.zip)
were checked independently. Its Guava version remains affected; no claim is made
that changing project dependencies repairs Gradle internals. The
[9.8.2 release notes](https://docs.gradle.org/9.8.2/release-notes.html) separately
report daemon/worker deserialization and repository TLS fixes inherited from
9.8.1. Those additional owner issues merit a dedicated wrapper upgrade and
qualification; they are not fixes supplied by these library constraints. The
wrapper remains unchanged here, and no complete Gradle-deserialization
reachability or safety claim is made.

KSP's [2.3.11 task implementation](https://github.com/google/ksp/blob/2.3.11/gradle-plugin/src/main/kotlin/com/google/devtools/ksp/gradle/KspAATask.kt)
resolves its analysis engine through a non-transitive detached configuration and
loads it with a platform-parent URLClassLoader. The Room processor loader is its
child. The engine embeds relocated Guava separately from the ordinary Guava used
by Room/auto-common. The [2.3.11 engine](https://repo.maven.apache.org/maven2/com/google/devtools/ksp/symbol-processing-aa-embeddable/2.3.11/symbol-processing-aa-embeddable-2.3.11.jar)
and latest stable [2.3.13 engine](https://repo.maven.apache.org/maven2/com/google/devtools/ksp/symbol-processing-aa-embeddable/2.3.13/symbol-processing-aa-embeddable-2.3.13.jar)
both identify embedded Guava `33.2.0-jre` in `pom.properties`. Their relocated
versions of all five affected collection classes exhaust the disposable 64 MiB
heap in the same probes. The [2.3.13 release](https://github.com/google/ksp/releases/tag/2.3.13)
therefore supplies no fix for this copy. The build trace confirms that the engine's
relocated `ImmutableList` is actually loaded; it does **not** establish an
attacker-controlled serialization route to each vulnerable collection. KSP does
serialize its own generated configuration between loaders. Reachability from
untrusted build inputs remains unproven; the affected implementation remains
present and is not called harmless. No engine jar is modified or replaced here.

**BC:** this is a focused qualification of aligned
[bcprov](https://repo.maven.apache.org/maven2/org/bouncycastle/bcprov-jdk18on/1.85/bcprov-jdk18on-1.85.pom),
[bcpkix](https://repo.maven.apache.org/maven2/org/bouncycastle/bcpkix-jdk18on/1.85/bcpkix-jdk18on-1.85.pom)
and [bcutil](https://repo.maven.apache.org/maven2/org/bouncycastle/bcutil-jdk18on/1.85/bcutil-jdk18on-1.85.pom),
not a claim of vendor-certified AGP compatibility. The
[1.85 release notes](https://github.com/bcgit/bc-java/blob/r1rv85/docs/releasenotes.html)
include behavioral and API changes. Binary comparison is not universally clean
(for example, BC util's CMP constructor visibility changes); no blanket binary
compatibility is claimed. Bytecode references in the inspected Android tooling
identify sdk-common's `KeystoreHelper` as its direct BC API consumer. Its public
JKS/PKCS12 create/read operations, wrong-password rejection, and internal RSA/EC
certificate generator were exercised through the actual AGP plugin loader.
Independent BC certificate-chain, CRL and keystore checks also run against lint's
resolved isolated classpath. No production signing key is used; fixture keys and
canary files are generated and deleted by the probes.

The [name-constraint notice](https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%908763)
and [lazy-ASN.1 notice](https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%9013506)
still identify `1.85` as fixed. For each email/URI certificate-chain probe,
`allowed.test` succeeds and `blocked.test` fails on both versions;
`blocked.test.` incorrectly succeeds on `1.80.2` and fails on `1.85`.
The [LDAP](https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%900636)
and [composite-signature](https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%905588)
notices still explicitly list the `1.80.2` backports. Their published source and
binary behavior were reverified; moving to `1.85` also removes the advisory-range
disagreement from the candidate graph, without rewriting the historical findings.

**Bundletool/jose4j:** [bundletool's transparency helper](https://github.com/google/bundletool/blob/1.18.3/src/main/java/com/android/tools/build/bundletool/transparency/CodeTransparencyCryptoUtils.java)
uses JWS. The probe calls those real parse/verify/fingerprint methods with a
synthetic RSA certificate, rejects a modified payload and malformed token, and
separately tests authenticated compressed JWE. A 64-byte payload succeeds on both
jose4j versions; 204,801 decompressed bytes are accepted before and rejected after,
matching the default 204,800-byte bound in the published
[0.9.6 source](https://repo.maven.apache.org/maven2/org/bitbucket/b_c/jose4j/0.9.6/jose4j-0.9.6-sources.jar).
This is stronger than an app unit-test pass, but does not assert that a JWE attack
was reachable from this build's normal bundle commands.

**Jetifier/JDOM:** the [published owner source](https://dl.google.com/dl/android/maven2/com/android/tools/build/jetifier/jetifier-processor/1.0.0-beta10/jetifier-processor-1.0.0-beta10-sources.jar)
still constructs a default `SAXBuilder`. The direct `XmlUtils` helper probe uses
only a generated local canary and leaves `android.enableJetifier` unset. Repeating
it with [JDOM 2.0.6.1](https://github.com/hunterhacker/jdom/releases/tag/JDOM-2.0.6.1)
still expands the entity. A supported Jetifier update/removal or qualified parser
hardening plus the patched library is needed. Enabling Jetifier would change this
exposure and requires its own audit. This work neither enables it nor removes a
required dependency.

### Reproduction and integration limits

Run the retained checks with JDK 17 and the SDK configured as in README:

```sh
python3 tools/dependency-health/verify.py /absolute/path/to/task-owned-evidence
./gradlew buildEnvironment :app:dependencies --no-daemon --console=plain
```

Each verification run writes a fresh evidence directory: complete configuration
inventory, dependency edges, Guava artifact variants, plugin class origins,
KSP detached engine paths/embedded metadata, Gradle distribution identity and
bounded probe results. KSP residual probes run in diagnostic mode; their successful
execution is not a remediation assertion. The lint and KSP origins are an
isolated reconstruction from its actual resolved files; the AGP probes run through
the applied plugin's real classloader. The Guava artifact-only view in this local
inspection script does not alter resolution or the submission workflow. The
independent pinned Gradle Actions `6.4.0` / graph plugin `1.5.0` snapshot resolves
all project configurations without exclusions and retains JDOM and both Guava
variants. It reports Gradle as `gradle-core`, not its individual distribution jars.
Neither the refreshed main SBOM nor that candidate snapshot includes KSP's detached
`symbol-processing-aa-embeddable` engine or its relocated Guava. This existing
submission coverage limitation is recorded explicitly, with the engine separately
inventoried by the retained task; no dependency is filtered out or artificially
added to an unrelated configuration to make the graph appear complete.

The local candidate snapshot is **not submitted**. All seven refreshed main
alerts remain open at this pre-merge checkpoint. Only a merge and the existing
main-push submission can establish default-branch closure. The candidate project
graph still includes affected JDOM, while separate Gradle distribution and KSP
shaded Guava exposures remain; this is not an all-clear for build tooling.

The parallel [Room schema PR #101](https://github.com/mcasillas17/WallCrawl/pull/101)
owns its separate argument and asset blocks; this change
only constrains its processor's Guava dependency. No schemas, migrations, archive
formats, planner/program/template/analytics logic, target SDK, signing configuration
or release workflow change here. Coordination established independent Gradle blocks.
Whichever branch merges second must update normally from main and rerun affected
graph/build/instrumentation checks. Neither task is authorized to merge by this
request. Remove a constraint only after every affected owner classpath selects a
fixed compatible version naturally, followed by the same qualification.

### Follow-up validation evidence

The baseline and candidate use macOS arm64, Homebrew OpenJDK `17.0.20.1`, Gradle
`9.7.1`, Android platform 37 and Build Tools `36.0.0`. Instrumentation uses
task-owned ARM64 Google APIs images, emulator `37.2.13`, and explicitly selected
device serials; other tasks' devices are untouched. No Gradle build cache is used
for the clean candidate build or instrumentation invocations.

| Check | Result |
| --- | --- |
| Baseline `test lint assembleDebug` before constraints | Successful; 1,322 JVM tests, zero failures/errors/skips; lint zero errors, 34 warnings and five hints. |
| Candidate `./gradlew clean test lint assembleDebug assembleRelease bundleRelease --stacktrace --no-daemon --no-build-cache --console=plain` | Successful, 113 tasks executed; debug/release KSP and Kotlin/Compose compile; 1,322 JVM tests, zero failures/errors/skips; lint retains the same zero errors, 34 warnings and five hints. |
| `python3 tools/dependency-health/verify.py <task-owned-evidence>` | Successful; actual AGP-loader crypto/owner probes, isolated lint crypto probes, all selected ordinary Guava artifacts fixed, detached KSP inventory and explicit vulnerable shaded-Guava diagnostics. |
| Five bounded Guava probes against each actually resolved old ordinary jar | Fixed-mode assertions fail as expected on `33.2.1-jre`, `33.4.0-jre`, `33.4.3-android` and Gradle's `33.4.6-jre`; both `33.7.2` variants pass. Both KSP engines retain the failure behavior. |
| Python catalog and release tests; acceptance and pinned-catalog checks | 156 catalog tests and six release tests pass; acceptance audit passes; 302 exercises / 906 SVG frames verified. |
| Bundletool `validate`, `dump manifest`, and guarded unsigned universal APK packaging from the release AAB | Successful with the constrained owner classpath and explicit SDK `aapt2`; independent signature inspection confirms unsigned release APK/AAB and final universal APK. |
| API 26 `connectedDebugAndroidTest` with `-Pandroid.testInstrumentationRunnerArguments.package=wallcrawl.elopenmike.com.app` | 40 tests, zero failures/errors/skips. |
| API 36 full `connectedDebugAndroidTest --stacktrace --no-daemon --no-build-cache --console=plain` | 575 tests, zero failures/errors/skips on the full rerun; initial device failure described below. |
| API 37 full command, same arguments as API 36 | 575 tests, zero failures/errors/skips. |
| Resolved configurations, dependency edges, class origins, local graph generation, `git diff --check` | Successful; graph limitations and residual copies are explicitly recorded above. |

The Room-generated database implementation is byte-identical to the unmodified
main baseline checked by the parallel Room task. Both full device reports include
199 database tests, including 19 migration cases through schema 16, alongside the
existing archive/backup suites. The clean build's JVM class-load
trace independently identifies the updated ordinary Guava jars and the remaining
Gradle/KSP copies. This supplements, rather than replaces, dependency reports.
Direct debug/release APK DEX inspection also finds none of the five targeted Guava
collection classes (ordinary or KSP-relocated), or the BC, jose4j and JDOM probe
classes; this is a targeted check, not a complete shaded-library audit.

The first API 36 attempt stopped after 478 reported tests with five failures,
including four popup-focus timeouts. Device window state and a screenshot captured
an Android **System UI isn't responding** dialog holding focus. That failed report
is retained. After restarting only the task-owned device, verifying normal focus
and stopping the idle task emulator, the unchanged full suite passed all 575 tests.
No test, assertion or timeout was weakened. Devices are run sequentially after
that interruption.

Packaging qualification had one disclosed deviation: an initial disposable
bundletool invocation found the existing **development debug keystore** through
its `HOME` fallback despite an empty JVM `user.home`, and automatically signed
that task-local test APK. The generated APK/APKS were deleted. The corrected
invocation removes that fallback environment and asserts that bundletool cannot
discover a debug signing configuration before packaging; it then succeeds
unsigned, and `apksigner verify` rejects the result for missing signatures as
expected. No production/release key was used, no signing configuration changed,
and nothing was published. Ordinary debug instrumentation uses development-signed
test APKs for installation. This evidence does not certify a signed release.

# Privacy and backup

## Policy

WallCrawl operates offline without an account. Profile and movement-capability
answers, preferences, templates, active/completed workouts, logged set feedback,
and the reconstructable weekly-ledger cache are stored locally in Room. The
catalog and exercise artwork are bundled with the app. The current application
has no cloud-sync service, analytics upload, Health Connect or Wear integration,
or production local-model runtime.

**Implicit Android backup is disabled until user-owned export/import is
implemented.** The application sets `android:allowBackup="false"` and explicitly
excludes all documented app-data domains from legacy full backup and modern cloud
backup and device-to-device (D2D) transfer. There is no custom backup agent.
This chooses privacy over automatic recovery; it does not add an export, import,
or delete-all-data control. Those remain [roadmap Package 2](../ROADMAP.md#2-add-user-owned-export-import-and-deletion).

## Storage and configuration

`WallCrawlDatabase.getInstance()` uses the application context and the
credential-protected `databases/wallcrawl.db` location. The database includes the
profile/capabilities, templates, workout history, set outcomes, and derived ledger
cache. Its SQLite journal/WAL/SHM sidecars are in the same database domain.
The policy excludes the entire directory, not a guessed filename or selected
tables.

| Android branch | Manifest attribute | Resource and exclusions |
| --- | --- | --- |
| API 26-30 | `android:fullBackupContent` | `res/xml/backup_rules.xml`: `<full-backup-content>` excludes all nine domains |
| API 31+ (current target is 35) | `android:dataExtractionRules` | `res/xml/data_extraction_rules.xml`: separate `<cloud-backup>` and `<device-transfer>` sections each exclude all nine domains |
| All supported versions | `android:allowBackup="false"` | Opts out of Android backup/restore participation, subject to the OEM D2D caveat below |

Every exclusion uses `path="."`: all files and recursive subdirectories within
that domain. The domains are `root`, `file`, `database`, `sharedpref`, `external`,
`device_root`, `device_file`, `device_database`, and `device_sharedpref`. The
additional domains cover app-private files/preferences, app-specific external
files, and device-protected storage even though current application-owned
persistence is in Room. Android already excludes cache, code-cache, and no-backup
directories. This policy is not a claim about arbitrary shared/public files.

For an app targeting API 31+, Android 12+ uses the modern rules instead of the
legacy configuration. Keeping both covers older supported devices. Neither an
empty rules file nor an absent transfer section means "exclude everything":
the explicit domain exclusions are intentional defense in depth alongside the
manifest opt-out.

```text
WallCrawl screens / local planner and progress
                  |
                  v
       Local Room database + SQLite sidecars
       (profile, templates, history, ledger cache)
                  |
                  X  app configuration excludes implicit app-data copies
                  |
       Android cloud backup / device-to-device transfer

The X describes the configured boundary, not universal OEM enforcement.
Previously retained backups are outside this change's control.
```

## Persistence, upgrades, and recovery

The backup policy changes no local database path, schema, migration, or ordinary
read/write behavior. Saved data still survives app restarts. A compatible
in-place upgrade with the same application ID and signing identity retains that
data through the existing non-destructive Room migrations.

Uninstalling WallCrawl or clearing its app storage removes local app data.
Device loss, failure, or replacement has **no supported recovery path today**:
do not count on Android cloud restore or device migration, and do not uninstall
as an upgrade workaround expecting history to return. Current debug prereleases
can require an uninstall because signing keys differ; see
[release versioning](../README.md#release-versioning).

Explicit local export/import and an in-app delete-all-data flow are not
implemented. Enabling either requires separate archive, validation, transaction,
and deletion semantics; this backup configuration is not an implementation of
those controls.

## Platform limitations and old backups

Android's documentation warns that, for apps targeting Android 12 or higher,
some manufacturers permit device-to-device migration even when `allowBackup`
is false. The modern device-transfer exclusions express WallCrawl's policy to
transports that honor those rules; they are **not a universal guarantee about
every OEM migration tool**, privileged/debug access, rooted devices, or future
platform behavior. The application itself provides no transfer service.

Android 16 QPR2 (API 36.1) also supports opt-in cross-platform transfer.
WallCrawl does not declare `<cross-platform-transfer>`: it has no counterpart
app or the required counterpart-app mapping. Adding any transfer mode requires
a deliberate policy decision and corresponding guard updates.

**This change does not delete backups uploaded by earlier versions, erase a
copy already transferred elsewhere, or guarantee remote erasure.** Android,
the backup provider, and device settings govern previously retained datasets;
WallCrawl has no remote-backup deletion control. Removing local app data is not
proof that an earlier remote copy was removed.

## Verification boundary

`BackupPolicyResourceTest` reads the installed application's flags and target
APK's merged binary manifest, confirms the package identity, follows the actual
resource references, and checks exclusion semantics. It requires all nine
whole-domain exclusions, no includes or conditional attributes, and both modern
sections. It also rejects a custom backup agent or a new transfer section
without a deliberate test/policy update.

The guard has been exercised on API 30 and 36. On each it parses both packaged
XML resources; it does **not** invoke both versions of Android's backup rule
engine. Regression exercises have demonstrated failure with the original
backup-enabled manifest, a filename-only legacy database exclusion, and a
missing modern device-transfer section, followed by success when restored.
Contributor commands are in [Build and test](../README.md#build-and-test).

Supplemental local Backup Manager requests on API 30 and 36 returned the
per-package result `Backup is not allowed` using Android's `LocalTransport`.
That establishes local backup-eligibility rejection, not successful backup,
cloud upload/restore behavior, or XML enforcement by an OEM D2D tool.
Actual cloud restore and physical/OEM transfer have not been exercised here.
Use dedicated test devices with synthetic data for any transport testing; do
not erase/reset a shared device or run destructive restore scripts on real data.

## Android references

- [Auto Backup: opt-out, domains, legacy/modern rules, and cross-platform transfer](https://developer.android.com/identity/data/autobackup)
- [Application manifest: allowBackup and manufacturer caveats](https://developer.android.com/guide/topics/manifest/application-element#allowbackup)
- [Android 12 backup/restore behavior changes](https://developer.android.com/about/versions/12/behavior-changes-12#backup-restore)
- [Testing backup and transfer](https://developer.android.com/identity/data/testingbackup)

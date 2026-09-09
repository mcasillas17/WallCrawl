# Privacy and backup

## Policy

WallCrawl operates offline without an account. Profile and movement-capability
answers, preferences, templates, active/completed workouts, logged set feedback,
and the reconstructable weekly-ledger cache are stored locally in Room. The
catalog and exercise artwork are bundled with the app. The current application
has no cloud-sync service, analytics upload, Health Connect or Wear integration,
or production local-model runtime.

**Implicit Android backup stays disabled.** The application sets
`android:allowBackup="false"` and explicitly excludes all documented app-data
domains from legacy full backup and modern cloud backup and device-to-device
(D2D) transfer. There is no custom backup agent. Recovery is user-driven
instead: WallCrawl writes an archive only when you ask it to, to a file you
choose, and it adds no upload, sync, or background transfer of any kind.

## Export, restore, and deletion

Three controls put the local data in your hands. They live on the **Training
Profile** screen; restore is also offered on the first onboarding step so a fresh
install can use it without first building a profile it would discard.

### What an export contains

One JSON file, written where you choose:

- the profile: codename, goals, experience, units, schedule, equipment,
  constraints, muscle priorities, confirmed starting loads, movement-capability
  answers, theme, and optional self-described gender (plus legacy illustration-preference
  data from the initial integration preview, if present);
- every saved template with its exercises and prescriptions;
- every workout session — completed, cancelled, and one in progress — with its
  planned targets, performed values, unit, effort feedback, timestamps, stop
  reasons, and status.

It also contains, for each session started from an automatic recommendation, the
validation record described below.

It does **not** contain the bundled catalog or artwork, which ship with the app,
or the weekly-ledger cache, which is derived (see
[deriving the ledger](weekly-dose-ledger.md)).

**An export is readable personal information.** Training history, physical
capability answers, and body-adjacent preferences are in clear text. Anyone who
opens the file can read them. Choose the destination deliberately: Android's
document picker can offer cloud-backed providers as well as local storage, and
WallCrawl cannot tell which one you picked. The app itself never uploads
anything.

The archive records a SHA-256 checksum. It detects corruption and accidental
modification. It is **not encryption and not proof of authenticity**: the file is
not protected from being read, and anyone who edits it can recompute the
checksum. Every archive is validated as untrusted input when it is read back.

### What a validated recommendation records

A session started from an automatic recommendation also stores how that recommendation was
decided: the validator, duration-estimator, catalog, review-policy, training-policy,
ledger, and program-state versions, the adaptation state, the ISO week and time zone it was
accounted against, the profile revision, a digest of the generation inputs, ordered reason
codes, and per muscle the completed, proposed, and configured allowance set counts.

It holds **no** name, note, load, repetition count, effort value, body measurement, or free
text, and it does not repeat the plan itself — the session's own exercises already hold
that. The digest is built only from values a training decision can read (profile identity
and revision, completed-workout count, candidate ids, catalog and policy versions, week and
zone); it contains no measurement and is a freshness check, not a security control.

Sessions started from a manual template, and every session recorded before this existed,
simply have no record. Nothing fabricates one.

### Archive compatibility

The archive format has its own version, separate from the Room schema version. This
build **writes version 3** and **reads versions 1, 2, and 3**. The schema version, app
version, creation time, and the bundled catalog commit are recorded as provenance only;
they never decide whether a file can be restored.

Version 2 added the validation record described above. A version 1 file — the only kind
earlier builds ever wrote — restores exactly as it always did and simply carries none.
Each version is read strictly: a file claiming version 1 while using a version 2 field is
refused rather than quietly upgraded.

Version 3 stores optional gender and preserves the first integration preview's legacy
illustration-preference field. Both stay on-device, are included in user-requested
exports, and are removed by Delete all data. Only gender selects artwork; the legacy
field has no effect. No training decision reads either field. Versions 1 and 2 keep
their original checksums and restore with unspecified gender. Version 3 validates
both fields to preserve compatibility with exports from the initial preview.

A file written by a newer WallCrawl is still refused with a message saying so, rather than
partially understood — a future format may attach meaning to fields this build would drop.

Equipment confirmations use canonical strings in the existing profile list, not
new archive fields. Old profiles and supported version 1/2 archives without the
[fixed-anchor selections](band-anchor-equipment.md) remain unconfirmed; restoring
never infers anchors from a band, wall, doorway, chair or former Full gym inventory.
New selections round-trip unchanged. Unknown equipment strings are preserved,
not remapped to broader categories. This does not make older clients understand
new strings or enforce the new anchor eligibility rules; an older client's
profile validation may also reject them. Archive format compatibility is not
forward behavioral compatibility.

Because a validation record cannot be rebuilt from history the way the weekly-ledger cache
can, it is exported and restored with the session it belongs to instead of being dropped.
A record this build cannot read back is left out rather than restored half-understood.

### Restore prerequisites

**Restore needs a fresh start**: no workouts, no templates, and onboarding not
completed. There is no merge and no replace, so restoring can never overwrite
data you still have. The profile row an app creates for itself before onboarding
finishes does not count as your data and is replaced.

The supported recovery route is therefore explicit, and in this order:

1. **Export** to a file you keep.
2. **Delete all local data**, or reinstall.
3. **Restore** from the file.

A workout in progress is part of the archive and comes back as it was; it is
never silently dropped. An archive claiming more than one active workout is
refused.

Everything is checked before anything is written: the format version, the
checksum, every type, enum, numeric bound and length, duplicate or blank
identifiers, references between sessions, exercises and sets, resource limits,
and the same domain rules the app enforces when it saves its own data. The
restore itself runs in one transaction that rechecks eligibility from inside, so
a refused, failed, or cancelled restore leaves the device exactly as it was —
never half a history. Archived measurements and identifiers are restored as
recorded: a session keeps the unit it was logged in, and an exercise the catalog
no longer contains keeps its identifier rather than being remapped.

### What deletion does and does not reach

Deleting all local data asks for explicit confirmation, naming what is lost —
including a workout in progress — and then removes the profile and preferences,
movement answers, templates, every session with its exercises and sets, every
validation record, and the derived weekly-ledger cache. The app returns to
first-run onboarding.

It does **not** touch:

- files you already exported, wherever you saved them;
- copies you or a provider made elsewhere, including anything a cloud-backed
  destination synchronised;
- backups a previous version of Android or an OEM tool may still hold
  (see [platform limitations](#platform-limitations-and-old-backups));
- the bundled exercise catalog and artwork, which are application assets;
- the app language, which is an Android per-app language setting rather than app
  data, so fresh onboarding after deletion opens in the language already chosen.

Deleting local data is not a claim of remote erasure.

## Storage and configuration

`WallCrawlDatabase.getInstance()` uses the application context and the
credential-protected `databases/wallcrawl.db` location. The database includes the
profile/capabilities, templates, workout history, set outcomes, and derived ledger
cache. Its SQLite journal/WAL/SHM sidecars are in the same database domain.
The policy excludes the entire directory, not a guessed filename or selected
tables.

The app language is not stored here. Android 13 and later keep the per-app language in
the system, where it is the same value the system Settings screen shows and is outside the
app's data entirely; below that, AndroidX's `autoStoreLocales` service persists it in the
app's SharedPreferences, which the `sharedpref` and `device_sharedpref` exclusions below
already cover. Either way it is device-local: it is not in the profile, not in Room, and
not in the export archive, so an archive exported under one language restores unchanged
under the other. See [Localization](localization.md).

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

The only path data takes out of, or back into, the app is one you start:

```text
        Training Profile                     first run / after deletion
    ┌────────────────────────┐              ┌────────────────────────┐
    │ Export my data         │              │ Restore from a file    │
    │ Restore from a file    │              └───────────┬────────────┘
    │ Delete all local data  │                          │
    └───┬──────────┬─────────┘                          │
        │          │                                    │
   export│    delete│                             restore│
        v          v                                    v
  ┌───────────┐  ┌──────────────────┐        ┌────────────────────┐
  │ one JSON  │  │ profile, templates│       │ validate every     │
  │ archive,  │  │ sessions, sets,   │       │ field, then one    │
  │ checksum  │  │ ledger cache      │       │ transaction, or    │
  │ + version │  │ all removed       │       │ nothing at all     │
  └─────┬─────┘  └────────┬──────────┘       └─────────┬──────────┘
        │                 │                            │
        v                 v                            v
  document you      fresh onboarding          empty destination only
  chose (may be     (bundled catalog          (no merge, no overwrite)
  cloud-backed)      and artwork stay)

Nothing here runs in the background, and nothing uploads on its own.
```

## Persistence, upgrades, and recovery

The backup policy changes no local database path, schema, migration, or ordinary
read/write behavior. Saved data still survives app restarts. A compatible
in-place upgrade with the same application ID and signing identity retains that
data through the existing non-destructive Room migrations.

Uninstalling WallCrawl or clearing its app storage removes local app data.
**Export first if you want to keep it.** Do not count on Android cloud restore
or device migration: neither is enabled, and an uninstall without an export
loses the history for good. Current debug prereleases can require an uninstall
because signing keys differ; see
[release versioning](../README.md#release-versioning) — exporting before that
uninstall, and restoring afterwards, is the supported way through it.

Recovery after device loss or replacement depends entirely on whether you
exported a file first and kept it somewhere you can still reach. WallCrawl has
no copy of your data and cannot produce one after the fact: an export you never
took cannot be recovered, and neither can one saved only on the device that is
gone. There is still no automatic backup, and enabling one remains a separate
decision.

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

User-owned export, restore, and deletion are covered by Android instrumentation
against a real Room database: the archive contract and every rejection path, an
export/delete/restore round trip whose starting state was written by the app's
own repositories, restore refusal on a non-empty destination, atomic rollback,
ledger-cache clearing, the one-active-session invariant, deletion completeness,
a profile write racing a deletion, and the controls' destructive confirmation and
large-font behaviour. Those tests exercise the code paths; they are not evidence
about any particular document provider's behaviour.

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

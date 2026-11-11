# Plan: settings and key restored on a new phone, then export and import

Status: **Part 1 steps 1–3 merged** (2026-11-11: #138 account store, #139 library store, #140 backup rules and a
Backup page, with Settings split into pages), with mute (#141) and the Data Saver note (#142). **Next: steps 4 and 5,
then release** (Dallin, 2026-11-11: hold the release for them; see "Why steps 4–5 before release"). Part 2 after.
Ideas from Dallin, 2026-11-08; planned 2026-11-09.

### Why steps 4–5 before release
v1.8's backups hold no settings (the old rules excluded `datastore/`), so nothing can be restored until this version
has backed up, about a day after it is installed. From then on, a move to a new Android 13+ phone restores the key, so
setup (the only place that asks for notification permission) is skipped and the checks run with nothing to show, unless
Android restores the permission grant itself (not checked: it needs a second phone). The fixes are small and ride in the
same release and checklist pass.

### Restore test, done (2026-11-09)
On the Pixel 9 Pro XL with Google's backup transport: `bmgr backupnow` (about 5 KB, no database), uninstall,
`adb install` (restored on install). The three DataStore files came back byte for byte in size, with no `databases/` or
`shared_prefs/`. The app started signed in, in the restored Dark theme, with both favourite forums and the muted thread,
and armed its check alarm at the restored interval. Recorded on #140. `tools/phone/` has the scripts used.

### Found on the phone: background data off for the app itself
The Data Saver note showed with Data Saver off. The debug app's uid had `REJECT_METERED_BACKGROUND` (per-app
background mobile data blocked), as did about 50 other apps on the phone. Android reports this the same way as Data
Saver (`RESTRICT_BACKGROUND_STATUS_ENABLED`) and holds the check back the same way, so the note now says "Checks wait
for Wi-Fi" and names both causes. Odd: the app's Mobile data page showed Background data on while the policy was set;
a reinstall (a new uid) cleared it. `adb shell dumpsys netpolicy | grep 'UID=<uid> policy'` shows it.

## Where things live today
| What | Where | Backed up today? |
|---|---|---|
| Settings (`SETTING_FIELDS`), API key, user id/name, `setup_done` | `files/datastore/settings.preferences_pb` (one Preferences DataStore, `SettingsRepository.kt`) | No: `datastore/` is excluded in both rules files, because of the key |
| Check alarm state (`next_at`, `interval_minutes`, `boot_count`) | shared prefs `check_alarm` (`CheckAlarm.kt`) | No (`sharedpref` excluded). Right: it is per boot |
| Favourite forums (starred in Forums, listed in its Favourites section) | `whirl.db`, table `favourite_forums` (id + `added_at`) | **Yes**, with the rest of the database |
| Everything else: forums, threads, watched lists, posts, archive pages and posts, notified, check log, pending watch actions | `whirl.db` (Room v7) | **Yes** |

Two things the ideas did not cover:
- **`whirl.db` is backed up whole**, caches included. Auto Backup has a 25 MB cap per app, and over it the backup is
  skipped entirely (nothing goes up, not even the small files). The archive reader (v1.8) caches whole threads, so a
  heavy reader will cross it, and then even the settings fix below backs up nothing.
- Restoring the cache onto a new phone brings back `pending_watch` (queued watch/unwatch actions, replayed against
  the server long after) and the check log of another phone.

So the plan separates **what the user chose** (backed up, exported) from **what the server said** (a cache: never
backed up or transferred; a new phone fetches it again).

## Part 1: restored on a new phone (next feature release)

### Storage layout after
| File | Holds | Cloud backup | Device transfer |
|---|---|---|---|
| `datastore/settings.preferences_pb` | `SETTING_FIELDS` only (as now, minus the account keys) | Yes (API 31+: only on phones with a screen lock, see step 3) | Yes |
| `datastore/library.preferences_pb` (new) | favourite forum ids; muted thread ids once that ships | as settings | Yes |
| `datastore/account.preferences_pb` (new) | API key, user id, user name, `setup_done` | Only end-to-end encrypted: API 28+ with a screen lock | Yes |
| `whirl.db` (+ `-wal`, `-shm`) | caches | No | No |
| shared prefs `check_alarm` | alarm state | No | No |

`setup_done` moves with the key: if settings come back but the key does not (API 26–27, or a phone with no screen
lock), the app is in setup, and setup already starts from the stored settings (`SetupViewModel.settings`), so the
restored theme and notification choices are shown, not reset. The reverse (key without settings) cannot happen: every
rule that carries the key also carries settings.

### Steps (one PR each, in order; 1–3 ship in one release)
1. **Account store.** A second DataStore, `account`, behind `SettingsRepository`'s unchanged API (`account`,
   `isSetupDone`, `apiKey()`, `signIn`, `signOut`, `setSetupDone`). Both stores are built in a Hilt
   `@Provides @Singleton` module with `PreferenceDataStoreFactory.create` (the `by preferencesDataStore` delegate only
   has a `Context`, and the library store in step 2 needs `ForumDao`). The account store's `DataMigration` reads the
   four keys through the settings store's `DataStore` instance (never open a file twice: HANDOFF testing notes) and
   writes them; `cleanUp()` removes them from settings. `shouldMigrate` is the account store lacking a
   `migrated` marker, so a failed `cleanUp` never re-copies stale values over newer ones. Fix the comment at
   `SettingsRepository.kt:22`.
   - If `migrate` or `cleanUp` throws, every read of the account store throws until the next open retries. The
     `account` and `isSetupDone` flows, and their collectors in `MainActivity` and `NotificationScheduler.start()`,
     must survive a read error (catch, log without the key, retry on the next start) instead of crashing.
   - Tests (Robolectric, temp files as `RepositoryTest` does): an old single store migrates; the key ends up only in
     the account file; a second start does nothing (cancel the first stores' scope before reopening the same file, or
     DataStore throws for two active stores on one file); a migration that throws once is retried and succeeds on the
     next open; sign-out clears only the account file.
2. **Library store for favourites.** Move `favourite_forums` into `library` as a string set of forum ids (`Int` in
   code; entries that do not parse are skipped). `added_at` is never read for ordering (the forum list orders by
   `forums.sort`), so it is dropped. `ForumWithFavourite` and the LEFT JOIN (`Daos.kt:12–27`) go; the forum list
   becomes a `combine` of the forums and the set, about 100 forums, so no cost. Update `ForumSectionsTest` and the
   "Favourites are kept" comment on `clearAccountData` (`WhirlDatabase.kt:67`).
   - Built as a one-off copy inside `LibraryRepository` (one `edit` with a marker, before the first read or change)
     rather than a `DataMigration`, so the store keeps the `preferencesDataStore` delegate and needs no Hilt module. A
     failed copy is logged and the stored favourites shown; the next read tries again. The marker matters: without it, a user who unstars everything would get the old table copied back on every start.
   - The table stays (unused) for one release, then a Room v8 migration drops it, with a `MigrationTest` case. Doing
     both at once would lose favourites if the copy failed part-way.
3. **Backup rules.** Today the files only exclude. Once there is an `<include>`, only what is included is backed up,
   and the restoring app's rules also filter old backups. So the include list is what keeps `whirl.db` (and its
   `-wal`/`-shm`) and shared prefs out; their excludes stay as documentation only. One side effect: a v1.8 backup
   restored onto the new version brings back no favourites (they were in `whirl.db`); they come back once the old phone
   has run the new version and backed up again.
   - `res/xml/backup_rules.xml` (API 26–27, `fullBackupContent`): include the settings and library files only. API
     26–27 ignore `requireFlags`, so the key never goes up there.
   - `res/xml-v28/backup_rules.xml` (API 28–30): as above, plus the account file twice: once with
     `requireFlags="clientSideEncryption"` (cloud, end-to-end encrypted) and once with
     `requireFlags="deviceToDeviceTransfer"` (a phone-to-phone transfer sets only that flag, so the first include
     alone would leave the key behind).
   - `res/xml/data_extraction_rules.xml` (API 31+; targetSdk is 36, so `fullBackupContent` is ignored there):
     `<include>` here takes only `domain` and `path`, and **`requireFlags` is silently dropped**, so copying the v28
     rule would upload the key unencrypted. Instead `<cloud-backup disableIfNoEncryptionCapabilities="true">` with the
     three files included: on a phone with no screen lock nothing goes to the cloud (as today), on every other phone
     settings, favourites and key go up end-to-end encrypted. `<device-transfer>` includes the three files.
   - Unit test in `:app` that parses the three files and asserts: no `requireFlags` in `data_extraction_rules.xml`;
     `disableIfNoEncryptionCapabilities="true"` on its `<cloud-backup>`; the account file only under a flag in the
     v28 file and absent from the base one; settings and library in all; nothing outside the store list. The store
     file names are a `const` list in `:core:data` that the test reads, so a new store cannot go unclassified.
4. **After a restore.**
   - The alarm: `NotificationScheduler.start()` calls `CheckAlarm.ensure` when setup is done and notifications are on,
     and `check_alarm` is not restored, so `ensure` arms it on first start. `setAndAllowWhileIdle` is inexact and needs
     no exact-alarm permission. Verify on the phone.
   - Notification permission (Android 13+) is not restored, and with the key restored setup is skipped, so nothing
     asks for it (today only `SetupScreen` does). If `watchedNotify` is on and `canNotify()` is false on first start,
     show a one-time prompt (or the existing check-history note) instead of checking silently.
   - A key that is rejected (`WhirlpoolError.BadKey`) is **not** signed out automatically: `BadKey` comes from any
     reply with `auth.id == 0` (`WhirlpoolApi.kt:138`), a passing server glitch included, and an automatic sign-out
     would wipe a working key on the original phone too. Show a "Your API key was rejected" banner with "Enter a new
     key" (to the key step, keeping settings and favourites) instead.
   - First check on the new phone: `notified` is empty, so `planAlert` (`AlertPlan.kt:21`) alerts every unread
     watched thread, the same as after a fresh setup. Left as is.
5. **Key field as a password.** Setup's key field gets `ContentType.Password` / autofill hint so password managers
   offer to save and fill it: covers API 26–27, phones without a screen lock, and people with backup off.

### Not used (and why)
Android Keystore encryption (hardware-bound, blocks restore); `EncryptedSharedPreferences` (deprecated, same
problem); Google Block Store (Play services: F-Droid rejects it); Google Drive (OAuth, Drive scope, app verification);
a custom `BackupAgent` (rules files do the job and stay testable).

### Phone test (`docs/device-checklist.md` gets a section)
The rules' semantics (step 3) come from AOSP's `FullBackup.java` and the data extraction rules docs, not from a
device: the phone test is what confirms them. `adb shell bmgr enabled`, `bmgr backupnow au.howesthat.whirl.debug`,
uninstall, reinstall (Auto Backup restores on install; `bmgr restore` otherwise). Check: theme, palette, favourites
back; key back (Pixel 9 has a screen lock); alarm armed (`dumpsys alarm | grep whirl`). `bmgr` can also list what a
backup holds: confirm `whirl.db` is absent. An API 27 emulator image for the "key not restored" path, and an API 29–30
one for the `deviceToDeviceTransfer` include if a second device is not to hand.

## Part 2: export and import (release after)
A **Backup** group at the end of Settings (its own page if the settings split in HANDOFF lands first): **Export**
writes JSON through `CreateDocument("application/json")`, named `whirl-settings-<date>.json`; **Import** reads
through `OpenDocument`. No storage permission.

```json
{"app":"whirl","format":1,"exportedFrom":"1.9.0",
 "settings":{"theme":"DARK","notify_interval":30,…},
 "favouriteForums":[92,100],"mutedThreads":["9244"]}
```
- `settings` keys are the stored names in `SETTING_FIELDS` (already fixed forever), encoded with each field's
  `encode`, so the codec reuses the table: add `SettingField.toJson/fromJson` and every setting is covered with no list
  to keep in step. All settings are written, defaults included, so the file says exactly what will be applied.
- **Never the account file**: no key, no user id (hard rule; a shared file must be safe to post). A test asserts the
  exported JSON has no `api_key` and no string equal to the test key.
- Thread ids are strings; forum ids numbers.
- Import: reject a file whose `app` is not `whirl` or whose `format` is newer than the app knows with a clear message;
  for each setting, `decode` (an unknown enum constant or bad time is skipped and reported, as stored values already
  are); unknown keys ignored (a newer export into an older app); missing keys keep the current value. Favourites and
  mutes are merged (union), never replaced. Then a dialog lists what changed ("Theme: System → Dark", "+3 favourite
  forums") with Apply / Cancel; nothing is written before Apply.
- Apply goes through `SettingsRepository.update` and the library store, so `NotificationScheduler`'s flow re-arms the
  alarm on an interval change with no extra code; test that it does.
- JSON with kotlinx.serialization (`kotlinx-serialization-json` is already in `libs.versions.toml`).
- Tests: round trip of non-default `Settings()` (every field changed, generated from `SETTING_FIELDS` so a new field is
  covered), a file from format 2 with extra keys, bad values, an empty file, not JSON, a 10 MB file (refuse over 1 MB).

## Effect on the mute idea
Mute a watched thread (HANDOFF open ideas) planned a Room `muted_threads` table. If it ships after Part 1 step 2, keep
mutes in the library store instead (a string set), so they are backed up and exported with favourites and need no
schema bump. If it ships first, it moves with the favourites in step 2.

## Decided (Dallin, 2026-11-09)
- The cache is never backed up or transferred, cloud or phone to phone.
- Backup sits at the end of Settings.
- Phones with no screen lock back up nothing (Android 12+; on 9–11 they back up settings and favourites without the
  key). When `KeyguardManager.isDeviceSecure` is false, the Backup group in Settings says so: on 12+ "Android backs
  up Whirl's settings only on phones with a screen lock", on 9–11 "Your API key is backed up only on phones with a
  screen lock". Part 1 adds the group with just this note (and a line on what
  is backed up); Part 2 adds Export and Import to it.
- Import merges favourite forums and mutes with the current ones (union); it never removes any.

## Reviewed
An architect review (2026-11-09) found the `requireFlags` gap on API 31+ and on API 28–30 device transfer, the
include-only semantics, the missing migration markers and wiring, the failure handling of the account migration, and
the risk of signing out on `BadKey`; all are folded in above.

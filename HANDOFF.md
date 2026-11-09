# Handoff: working on Whirl

Whirl is an Android client for the Whirlpool forums, a Kotlin/Compose rewrite of Whirldroid. Read this file, then
`PLAN.md` (what Whirlpool does and what the app is for), then the current plan in `docs/plans/`. How each phase was
built and what it decided is in `docs/history.md`; read it when a "why" is not answered here.

## Where things are
| Path | What it is |
|---|---|
| `PLAN.md` | Target features (from Whirldroid 2.14.12), verified Whirlpool behaviour, architecture, decisions (§5) |
| `docs/plans/` | The plans since v1.3 (`v1.3.md` also covers v1.4; v1.5 had none) |
| `docs/history.md` | How each phase and release was built and what it decided, up to v1.8.0 |
| `fixtures/api/`, `fixtures/html/` | Real API responses and **raw** pages (no key inside); `fixtures/README.md` |
| `tools/capture/` | Headed Playwright captures, the only way past Cloudflare from this machine |
| `docs/device-checklist.md` | The phone pass before each release |
| `docs/play-console.md`, `docs/fdroid/`, `docs/community.md`, `site/` | Google Play steps and answers; F-Droid metadata; Whirlpool post drafts; GitHub Pages |
| `fastlane/metadata/android/en-AU/`, `en-US/` | Store listing; en-US is a copy of en-AU (CI diffs them) |
| `docs/styles.md`, `core/ui/src/main/assets/layouts/`, `palettes/` | Settings › Layout and Colours (`StylesTest` checks contrast) |
| `app/version.properties` | The version; bumped in each release PR, checked against the tag, read by F-Droid |

## Hard rules
1. **Thread IDs are strings** (`3rvvvlwj`). Parsing them as integers is a bug.
2. **Parse the raw fixtures** (`fixtures/html/*.html`), never `fixtures/html/rendered/` (after JavaScript ran). The live
   site serves `span.pages[data-pages]` and inline `var thread_pages = N;`, which JavaScript rewrites.
3. **Whims are gone.** "Private Messages" is forum 1 and opens in the browser.
4. **Skip `#watchbar`** (a hidden `.notebar.marker` in `#replylist`). Moderator notes are `#top_notebar_public`;
   multi-part links `.notebar.parts`.
5. **API quirks:** dates `yyyy-MM-dd'T'HH:mm:ssxx` (`+1000`, no colon); titles HTML-escaped; `WATCHED.UNREAD` is an
   int, `THREADS.UNREAD` a boolean; always pass `watchedmode`.
6. **This machine cannot reach Whirlpool** (Cloudflare blocks curl, desktop OkHttp and headless browsers). Captures go
   through `tools/capture/` (headed Playwright); live checks run on Dallin's phone. Unit tests never touch the network.
7. **The API key** is in `~/.config/whirldroid/apikey` (mode 600): read it in scripts, never print, log or commit it.
   Same for `~/.config/whirldroid/release.jks` and `release-signing.env`.
8. **Releases come only from the tag-driven workflow** (`release.yml`; PLAN.md §6). Never publish a local build. A
   release PR bumps `app/version.properties`, adds `## vX.Y.Z` to CHANGELOG.md and
   `fastlane/metadata/android/en-AU/changelogs/<versionCode>.txt` (≤500 characters), copied to `en-US`. Ask Dallin
   before tagging. `v0.*` tags are GitHub pre-releases, `v1.*` "Latest".
9. **`main` is protected:** branch, PR, green CI on an up-to-date branch, squash merge. Merge only when Dallin says so
   in the session. Tags are pushed from a commit on `main`.
10. **No Whirlpool web session in the app** (no login, cookies, posting or site AJAX) until Whirlpool's moderators
    approve in writing; posting, private forums and PMs open in the browser. **Whirl stays free**: the API terms
    forbid revenue.
11. **Ask Dallin** before changing a decision in PLAN.md §5 (package `au.howesthat.whirl`, MIT licence, the name
    "Whirl") or anything else already decided there.
12. **`reference/`** (git-ignored: Whirldroid 1.8.12 source, 2.14.12 decompiled) has an unknown licence: read it for
    behaviour, never copy from it.

## Commands
- Full check, as CI runs it: `./gradlew :app:lintDebug test assembleDebug` (CI runs the three as parallel jobs, and a
  PR that changes only Markdown, `docs/` or `site/` skips them; the required check `build` sums them up) (2–12 minutes; a `:core:ui` change reruns
  every module's tests). The daemon is pinned to JDK 17 (`gradle/gradle-daemon-jvm.properties`; `~/.jdks`).
- Versions: `gradle/libs.versions.toml` (check Maven before adding a library). Conventions: `build-logic/convention`
  (`whirl.jvm.library`, `whirl.android.library` with one Robolectric SDK in `build-logic/robolectric/`,
  `whirl.android.compose`, `whirl.serialization`, `whirl.hilt`, `whirl.android.feature`). Kotlin warnings are errors.
- Captures: `tools/capture/` (`npm run public`, `npm run actions`; see its README). Store graphics:
  `tools/store-graphics/make_icon.py` is the icon's single source.

## Modules
| Module | What it holds |
|---|---|
| `:core:model` | `ThreadId`, `WhirlpoolError`, list/thread/post models, `PostBody`, `TextQuery`, `WatchAction`, `WhirlpoolSite` |
| `:core:scraper` | Pure JVM parsers for the raw pages (`ThreadListParser`, `ThreadPageParser`, `PostBodyParser`, `UserProfileParser`, `WhirlpoolTime`). `ArchivePageParser` (v1.8: `/archive/` pages, parts, exact page mapping). `SearchResultsParser` and `ForumIndexParser` are kept for later |
| `:core:network` | `WhirlpoolApi`, `WhirlpoolWeb`, `WhirlpoolUrls`; main-safe (bodies read on IO), Cloudflare detection, retries |
| `:core:database` | Room **v6**, schemas in `core/database/schemas/` |
| `:core:data` | Repositories, `SettingsRepository` (DataStore), `Refresher`, `WatchedRepository` + `WatchQueue`, dispatchers |
| `:core:ui` | Theme, styles, `PostBodyView`, find bar, `ListScreenState`, `ListPaneRoute` |
| `:core:notifications` | Watched-thread check (`CheckAlarm` during Doze, WorkManager fallback), `WatchedNotifier`, notification buttons |
| `:feature:*` | `setup`, `settings`, `threadlist` (all thread lists, search), `forums`, `thread`, `news`, `user`; each owns its routes |
| `:app` | Hilt, `MainActivity` (setup until `setupDone`), Navigation 3 (`TopLevelBackStack`, `WhirlApp`), snackbars |

## Notes for the code
- **Routes** are `NavKey`s and must be listed in `RouteSerializers` (`app/navigation/Routes.kt`). Thread lists
  implement `ListPaneRoute` to sit beside a thread in two panes, which need the setting and a window of at least
  600 × 480 dp, wider than tall with "Only in landscape" (`windowDirective` in `WhirlApp.kt`).
- **Dispatchers** are injected (`@IoDispatcher`, `@DefaultDispatcher`). Parsers run in `parseOrDrift` (off Main;
  unexpected exceptions become `WhirlpoolError.Drift`).
- **Room:** a new version needs a migration and a `MigrationTest` case; build once so the schema JSON exists before
  the test can see it.
- **Settings:** one table (`SETTING_FIELDS`, `SettingsRepository.kt`); only values that differ from the default are
  stored. A new setting is a field in `Settings` and a row in the table (a test checks they match).
- **Watched threads and account switches** (v1.7, docs/plans/v1.7.md §11.2):
  - `WatchedRepository` has the lists; `WatchQueue` makes, stores, sends and undoes actions; `WatchedStore` holds
    what both share. Lock order: `sending`, then `mutex`, then the database transaction; every section under
    `mutex` is one transaction.
  - `WatchQueue.switchAccount(change, clear)` is the only way to sign out or switch accounts: it stops the sender
    after the row it is sending, forgets the queue (waiting actions end as `SendResult.Dropped`), runs `change`, then
    bumps `AccountGeneration` and clears the account's data in one transaction.
  - Any new account-scoped fetch stores through `AccountGeneration.fetchAndStore`, so an answer from before a switch
    is dropped and fetched once more for the new account.
  - On a failed send only membership of the lists (and Mark as read's read position) goes back, then both lists are
    refetched unless it was a network, Cloudflare or rate-limit failure. Undo restores the whole snapshot.
    `deleteOrphans()` keeps threads a queued action names. The backstop worker's chain is at most two runs long.
  - Private-forum lists (`ForumThreadsRepository`) are not account-guarded: deferred to v1.8.
- **Tests** (Robolectric for Room, DataStore and Compose): clear view models and join their jobs before
  `Dispatchers.resetMain()`; `advanceUntilIdle` skips `backgroundScope`; never open two DataStores on one file. A bug
  fix's test is shown failing with the fix reverted (PR descriptions of #112–#114 show the form).

## The phone
Dallin's Pixel 9 Pro XL (Android 17) over the Windows adb
(`/mnt/c/Users/dalli/AppData/Local/Android/Sdk/platform-tools/adb.exe`, APK paths through `wslpath -w`); wireless
debugging reconnects by its mDNS name once paired. Debug builds install as `au.howesthat.whirl.debug`. Offline without
dropping adb: `cmd connectivity set-chain3-enabled true` and
`set-package-networking-enabled false au.howesthat.whirl.debug` (turn both back). The phone locks often: ask Dallin to
unlock it rather than changing its settings. It stays one pane in landscape (448 dp tall); two panes are checked in
unit tests. Per-release checks: `docs/device-checklist.md`.

## Open ideas
- **v1.8: the archive reader** (released 2026-11-09; `docs/plans/v1.8.md`, history in `docs/history.md`): still to
  check on the phone are §7's TalkBack, process-death and offline items. Follow-ups: a moderator view of whole-thread
  search (the note in `docs/play-console.md` mentions it), "tap a filtered post to see it in context".
- **Next release: Data Saver note.** With Data Saver on and Whirl not set to unrestricted data, Android blocks the
  background check on mobile data until Wi-Fi returns (debug app, 2026-11-07: a 1h38m gap while out; the check history
  header showed "data saver ON"). Show a note under the notification settings when
  `ConnectivityManager.restrictBackgroundStatus` is `ENABLED`, linking to the app's data usage setting
  (`Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS`).
- **Mute a watched thread** (idea, Dallin 2026-11-08): stays in Watched (and its Unread tab) but never notifies.
  Whirlpool has no such flag, so it is local: a `muted_threads` table (Room schema bump), set from the row and thread
  menus ("Don't notify" / "Notify again") and from the notification itself. Filter in `notifiable()`
  (`core/notifications/.../AlertPlan.kt`), so the alert, the summary's thread and post counts and the per-thread
  notifications all leave muted threads out. Show a bell-off mark on the row. Drop the mute when the thread is unwatched,
  but only after a successful watched fetch, as `notified.keepOnly` does. Small: could ride along with the Data Saver note.
- **Settings and key restored on a new phone** (idea, Dallin 2026-11-08; do before export/import, it covers the common
  case with no user steps). Today Android's backup skips all settings (`res/xml/data_extraction_rules.xml` and
  `backup_rules.xml` exclude `datastore/` and shared prefs) because the API key lives in the settings DataStore.
  - Move the key to its own DataStore file (migrate on upgrade). Back up the settings file everywhere (cloud and
    device transfer). Back up the key file in the cloud only with `requireFlags="clientSideEncryption"` (Android 9+:
    end-to-end encrypted with the screen lock; Google cannot read it), and always in `<device-transfer>` (never
    leaves the phones).
  - minSdk is 26 and API 26–27 ignore `requireFlags` (the key would go up unencrypted): `res/xml/backup_rules.xml`
    excludes the key, `res/xml-v28/backup_rules.xml` includes it with the flag; `data_extraction_rules.xml` (API 31+)
    carries the flag in `<cloud-backup>`.
  - Re-arm the check alarm after a restore (alarms do not survive; `NotificationScheduler` on app start already
    does via `ensure`, check it). A restored key that fails (`Bad API key?`) goes back to the key screen with a message.
  - Mark the key field on setup as a password (`ContentType.Password` / autofill hint) so password managers offer to
    save and fill it: covers people with backup off.
  - Not used: Android Keystore encryption (hardware-bound, so it blocks restore), `EncryptedSharedPreferences`
    (deprecated, same problem), Google Block Store (Play services: F-Droid would reject it), Google Drive (OAuth,
    Drive scope, app verification).
  - Test on the phone with `adb shell bmgr backupnow au.howesthat.whirl.debug`, uninstall, reinstall, and
    `bmgr restore`; unit-test the rules files' contents and the key migration.
- **Export and import settings** (idea, Dallin 2026-11-08): Settings › Export writes a JSON file through the system
  file picker (`CreateDocument`, no storage permission); Import reads one (`OpenDocument`). Contents: every
  `SettingsRepository` setting, favourite forums, muted threads (above). **Never the API key** (HANDOFF hard rules; a
  shared file must be safe to post). Shape: `{"app":"whirl","format":1,"exportedFrom":"<versionName>",
  "settings":{…},"favouriteForums":[…],"mutedThreads":[…]}`, thread ids as strings. Import validates each value, ignores
  unknown keys (a newer export into an older app) and keeps the current value for missing ones; show what changed.
  Re-arm the alarm after import (notification interval). Tests: round trip, a file from a later format, bad values.
- **Post writer** (idea, `docs/plans/post-writer.md`): write a reply with collected quotes, a formatting bar and a live
  preview, then copy it into Whirlpool's reply page. Probably after v1.8.
- **Guest mode** (idea, `docs/plans/guest.md`): browse public pages with no API key. Ask the moderators first.
- Waiting on others: Whirlpool's moderators (gates the community posts, `docs/community.md`, and the F-Droid merge
  request, `docs/fdroid/`); Play's closed test (12+ testers for 14 days, then production; `docs/play-console.md`).
- Housekeeping: targetSdk 37 before August 2027; Google developer verification (2027); re-check reproducibility when
  the build changes (`docs/fdroid/README.md`).
- Small: the poster's name is a tap target under 48 dp; draggable scroll thumb; find scrolls to the post, not the
  match; linked images; an app widget.
- Unknowns:
  - `get=recent` when not empty: assumed to have the `THREADS` shape; the Recent screen must cope with it failing as
    `Drift`.
  - The markup of a deleted post and of a post with an image: when one is found, add its URL to
    `tools/capture/public.json` and run `npm run public` (the parser tolerates a `replyblock` without `.date`).
  - Where whrl.pl short links lead (none seen in a post; the app falls back to the browser).
  - Reply `?action=reply&t=` and edit `?action=edit&e=` are 2.14.12's URLs, never opened logged in (`reply&t=` exists).
    New thread `?action=newthread&f=` is verified (2026-10-21).

# Plan: tidy up before the archive reader (v1.7)

Status: agreed with Dallin 2026-11-05, after a whole-project review (data layer, UI, build/CI) the same day. v1.7 has
**no new features**: it fixes what the review found, removes what is no longer used and makes the next features
cheaper to build. The archive reader (whole-thread reading, search within it, posts by a user) is v1.8 (§8).

Items marked ✔ were checked against the code while writing this plan; the rest come from the review and are confirmed
(or dropped) as each PR is built. Dependencies were brought up to date the same day (OkHttp 5.5.1, KSP 2.3.13,
DataStore 1.3.0, AGP 9.5.0; #76, #105, #75, #107).

---

## 1. Network and parsing off the main thread ✔

### 1.1 What is wrong
`OkHttpClient.fetch` (`core/network/.../WhirlpoolHttp.kt`) awaits `executeAsync()`, which suspends only until the
headers arrive; `classify()` then reads the whole body with the blocking `response.body.string()` on the caller's
dispatcher. `ThreadPageRepository.load` parses the page with jsoup straight after. Callers are `viewModelScope`
coroutines, so both run on **Main**. Nothing in `core/` switches dispatcher (the only `Dispatchers` use is
`flowOn(Default)` for post bodies in `ThreadViewModel.page`). StrictMode does not flag socket reads under TLS, which is
why it has not shown up; a slow connection or a big page (a 2.2 MB archive page in v1.8) will.

### 1.2 Change
- `:core:network`: `fetch` reads the body in `withContext(io)`; every public `WhirlpoolApi`/`WhirlpoolWeb` call is
  main-safe.
- `:core:scraper` stays pure; the repositories call parsers in `withContext(default)`.
- Dispatchers are injected (`@IoDispatcher`, `@DefaultDispatcher` qualifiers in `:core:data`, provided by Hilt) so tests
  keep using `StandardTestDispatcher`.

### 1.3 Tests
A `WhirlpoolWeb` test that calls from a single-threaded "main" dispatcher and asserts the read happened elsewhere;
existing repository tests run unchanged with the test dispatcher injected.

## 2. Post bodies parsed once
- `ThreadPageRepository.page` re-emits on every `thread_pages` invalidation, and `touch()` (each load) and `evict()`
  (each store) cause one, so every page on screen re-reads its posts and `ThreadViewModel.page` re-parses every body.
  Add `distinctUntilChanged` on the page row (`fetchedAt`, `pageCount`) before reading posts.
- ✔ Find on page builds its own `page(page)` flow (`ThreadViewModel.findMatches`), parsing the page a second time.
  Keep one shared flow per page in the ViewModel (a small map of `shareIn(WhileSubscribed)` flows) used by both.
- Tests: a repository test that `touch`/`evict` of another page does not re-emit; a ViewModel test that find and the
  page share one parse (count parses with a fake).

## 3. UI bugs
1. ✔ **Forum list goes back to page 1** after Back from a thread: `ForumScreen.kt` `LaunchedEffect(group) {
   pager.scrollToPage(0) }` also runs when the screen re-enters composition. Reset to page 0 only when the group is
   changed (from the picker's callback). Check on the phone first: forum page 3 → thread → Back.
2. ✔ **Thread events wait behind a snackbar**: `ThreadScreen.kt` collects `navigation` and `showSnackbar` suspends
   inside the collector, so a link tapped while "link failed" shows waits ~4 s. Launch the snackbar.
3. **Spoiler indices depend on composition order** (`PostBodyView.kt`, `SpoilerCounter` mutated during composition). Give
   each spoiler an index in the parsed `PostBody`; `remember` each paragraph's `AnnotatedString` by (block, marks,
   revealed). Add a Robolectric test that taps the second spoiler in a post.
4. ✔ **News and Forums ignore a refresh while loading** (`if (_refreshing.value) return` in `NewsScreen.kt` and
   `ForumsScreen.kt`); `ListViewModel` already does "latest call wins". Fixed by the shared helper in §5.1.
5. **Drawer highlight** is lost on pushed screens (`WhirlApp.kt` `isSelected = { top::class == route }`): highlight the
   current top-level stack.
6. **"Updated N ago" freezes** (`Instant.now()` read in composition): one `rememberNow()` ticking each minute.
7. **Early taps use default settings**: `ListViewModel`, `ThreadViewModel`, `SettingsViewModel` start from
   `Settings()`. Read `settings.first()` where an action depends on a setting, as `perform` already does.

## 4. Data integrity
1. **Watched rows flicker back**: `refresh`, `fetchUnread` and `send` replace a list and then `reapplyPending()` as
   separate writes, and MARK_READ's `applyLocal` is two statements, so Room can emit the in-between state (also to the
   notification sync). Run each mutex section in `db.withTransaction` (through a small `Transactor` so tests can fake
   it).
2. **Account switch during a refresh**: `forgetPending()` bumps `generation` but only `send()` checks it; a `refresh` or
   `fetchUnread` already past its API call stores the old account's list after `clearAccountData()`. Check
   `generation` there too; make `clearAccountData` one `@Transaction` and run it under the watched mutex. Same check for
   Recent.
3. **Rollback after a late failure** restores whole `ThreadEntity` snapshots taken when the action was made (up to 24 h
   earlier). On failure, restore only the watched membership and force-refresh both lists (`refreshQuietly`); keep the
   full snapshot for Undo only.
4. **Backstop worker can be dropped**: `WatchSendScheduler` uses `ExistingWorkPolicy.KEEP` ✔, so a `schedule()` while
   the worker is finishing an empty run is ignored. Use `APPEND_OR_REPLACE`.
5. ✔ **Settings changes can overwrite each other**: `SettingsRepository.update` reads `settings.first()` outside
   `store.edit {}` and writes every key. Do the read-modify-write inside `edit`, and write only keys that differ from
   the default so a changed default reaches existing users (see §5.3).
6. **`threads` only shrinks on sign-out**: call `deleteOrphans()` after list replacements (or once a day from the
   notification check).
- Tests for each: account switch with a refresh in flight; failure-time rollback after a refresh; the Maintenance "all
  still watched" give-up path; two concurrent `update` calls; the scheduler policy (a fake `WorkManager` or
  `WorkManagerTestInitHelper`).

## 5. Simplify

### 5.1 Shared list plumbing
- One refresh/error/find-query helper for `ListViewModel`, `NewsViewModel`, `ForumsViewModel` (fixes §3.4).
- One `ThreadListScaffold(title, subtitle, actions, subBar, findEmptyAction)` for Watched, the simple lists and User;
  split `DrawerLists.kt` (512 lines) into one file per screen.
- Row keys by id, section keys by forum id (`ThreadListUi.kt` uses `"$index-$id"` and `"section-$index"`, which shift
  when find hides a section); a key for `CheckHistoryScreen`'s items.

### 5.2 Thread screen
- ✔ **Remove the in-app post filter.** Since v1.1.2 every filter but "All" opens the browser and nothing builds a
  `ThreadRoute` with `userId`, so `PostFilter` through `ThreadViewModel`, `ThreadPageRepository`, the `filter` key on
  `thread_pages`/`posts`, and the filter subtitle only ever see `All`. Keep the menu entries that open the browser
  (and "Posts by OP"); drop the rest. v1.8 filters the archive's posts in memory, a different design (§8). The
  `filter` column goes in a Room migration (v4 → v5) with a `MigrationTest` case. It is part of both tables' primary
  keys (`thread_pages`, `posts`), so the tables are rebuilt; they are a cache, so the migration may simply recreate
  them empty (pages refetch on open).
- Split `ThreadScreen.kt` (693 lines): post item/header/frame, menu, find bar wiring into their own files.
- Fold `ThreadViewModel`'s start page, page count, title, error and refreshing into one `ThreadUiState`; per-page and
  find flows stay separate. Collect each once instead of in `ThreadPager`, `PageContent` and `PostList`.
- Navigate from posts directly instead of building `https://forums.whirlpool.net.au/...` strings to parse back
  (`ThreadScreen.kt` user/thread actions); News's hard-coded news URL moves to `WhirlpoolUrls`.

### 5.3 Data layer
- Split `WatchedRepository` (447 lines) into the send queue (`perform`, `undo`, `drain`, `send`) and the lists
  (`watched`, `refresh`, `fetchUnread`). `awaitSend` returns a sealed result instead of `== null`, with an injected
  time source instead of `withContext(Dispatchers.Default)`.
- One generic API-list repository for the five in `Repositories.kt` that repeat `Refresher` + `combine(observe,
  fetchedAt)`; one `MemoryCache<K, V>(keep)` for `ScrapedListRepository` and `UserRepository`.
- `Settings`: one table of keys, defaults and fields instead of listing each setting in three places.
- Room 2.8 stores enums: `action`, `state`, `source` become typed columns; comma-joined ids get a `TypeConverter`.
- Precompiled regexes in `Html.kt` and `ThreadPageParser`; `Refresher.locks` bounded; an OkHttp `callTimeout`.

### 5.4 Dead code
`ThreadEntity.pageCount`/`isSticky`/`isClosed` (never written), `ThreadSource.SEARCH`/`POPULAR`, `ApiEnvelope.user`,
`AlertPlan.hasNew`, `isTopLevelRoute` (duplicates `TopLevelBackStack.isTopLevel`), empty `SetupScreen` callbacks, the
12 empty ProGuard files and `consumerProguardFiles`. **Kept for v1.8**: `WhirlpoolUrls.archive`, `SearchResultsParser`,
`ForumIndexParser` (documented as such). `isListPane` becomes a marker interface on the routes, so it cannot disagree
with which entries get list-pane metadata.

## 6. Build and CI
1. ✔ **Android test tasks do not declare `fixtures/` as an input** (`AndroidLibraryConventionPlugin` sets only the
   system property; the JVM plugin declares it), so a changed fixture can return a cached pass. Copy the
   `inputs.dir(...)` line.
2. **Pin the JDK**: local builds use JDK 21, CI and F-Droid 17, and nothing pins it. Install JDK 17 locally and run
   `./gradlew updateDaemonJvm --jvm-version=17`.
3. **Reproducibility check**: a `workflow_dispatch` job building `assembleRelease` unsigned twice on clean runners and
   diffing, plus `apksigcopier` against the last release. Run it before tagging v1.7.0 (AGP 9.5 changed the toolchain).
4. **Lint once**: `checkDependencies = true` in `:app`, CI runs `:app:lintDebug`; androidTest variants off in the
   conventions (no module has androidTest sources).
5. **Conventions**: `compose-material-icons-core` into the compose convention (7 copies), a `whirl.serialization`
   convention (5 copies), the hard-coded `kotlin-test-junit5` into the catalog, one shared `libs` accessor, one
   Robolectric SDK pin (`robolectric.properties`) instead of `@Config(sdk = [35])` in 19 classes.
6. **Workflows**: `permissions: contents: read` in `ci.yml`; `setup-gradle` pinned by SHA in `release.yml`; upload test
   reports always.
7. **Dependabot**: add `tools/capture` (npm, with a lockfile); `tools/spike` either reads the root catalog or says it is
   frozen; a test-libraries group.
8. **Real-clock waits** in `NotificationSchedulerTest`, `ThreadViewModelTest`, `ListViewModelTest` and
   `ThreadRowAccessibilityTest` move to injected clocks/dispatchers.

## 7. Docs
- **HANDOFF.md** down to what is current (~60 lines): hard rules, commands, module map, device notes, open ideas;
  versions by reference to `gradle/libs.versions.toml`. The phase-by-phase history moves to `docs/history.md`. Fix the
  stale two-pane threshold (600 dp since v1.5.1, HANDOFF still says 840).
- Plans move to `docs/plans/` (`v1.3.md`, `v1.6.md`, `v1.7.md`) with a "Shipped in vX" line; PLAN.md §9's phases are
  marked historical and its archive idea points to §8 here.

## 8. Looking ahead: v1.8 archive reader
Decided by Dallin 2026-11-05 (PLAN.md §9 had it as "Idea, undecided"). Its own plan, `docs/plans/v1.8.md`, comes after
v1.7. It must cover:
- **Reading** a thread from `/archive/<id>` (800 posts per request, later pages `-N`), scrolling instead of paging,
  newest archive page first for big threads, earlier pages on "Load earlier".
- **Search within the thread** (find across the downloaded posts, building on v1.6's `TextQuery` and highlighting).
- **Posts by a user** (me, OP, any poster) filtered in the app from the archive's `data-userid`; moderators/reps if a
  fixture shows how `.usergroup` marks them.
- First step: raw fixtures of a multi-page archive through `tools/capture`; size and request limits from PLAN.md §9.
§1 (parsing off Main) and §5.2 (old filter removed) are prerequisites.

## 9. Order and release
One PR each, in this order (each small enough to review on its own):
1. **A: build and CI** (§6.1–6.2, 6.4–6.8). First, so the other PRs are checked by a correct test cache.
2. **B: off the main thread + parse once** (§1, §2).
3. **C: UI bugs** (§3.1–3.3, 3.5–3.7).
4. **D: data integrity** (§4).
5. **E: remove the post filter + thread screen split** (§5.2), with the Room migration.
6. **F: shared list plumbing** (§5.1, fixes §3.4).
7. **G: data-layer cleanup and dead code** (§5.3, §5.4).
8. **H: docs** (§7).
9. **Release v1.7.0**: reproducibility check (§6.3), then the release PR as usual (`app/version.properties`,
   CHANGELOG `## v1.7.0`, `10700.txt` in en-AU and en-US); the changelog says "fixes and speed-ups". Phone pass:
   `docs/device-checklist.md` plus forum Back (§3.1), a spoiler post, settings kept after the DataStore 1.3 upgrade,
   and a queued watch action across sign-out.

## 10. Decisions (Dallin, 2026-11-05)
1. v1.7 is only the tidy-up; new features (archive reader, search within it, posts by a user) are v1.8.
2. The current in-app post filters can go: they are unused, and v1.8 brings filtering back from the archive.

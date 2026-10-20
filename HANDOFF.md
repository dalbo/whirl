# Handoff brief: building the Whirlpool client

Read in this order: this file → `PLAN.md` → `fixtures/README.md` → `tools/capture/README.md`. Then the two reference trees only when a behaviour question is not answered by `PLAN.md`.

## What exists in this directory
| Path | What it is | In git? |
|---|---|---|
| `PLAN.md` | The plan: target features, verified Whirlpool behaviour, architecture, decisions, phases | yes |
| `fixtures/api/*.json`, `*.xml` | Real Whirlpool API responses, captured 2026-10-20 with Dallin's key (user `d@llin`, #216903). No key inside. | yes |
| `fixtures/html/*.html`, `*.js` | **Raw** server HTML of forum, thread, popular, search, user, index and private pages, plus the site script. Parsers are written against these. | yes |
| `fixtures/html/rendered/` | The same pages after JavaScript ran. Reference only; never test parsers on them. | yes |
| `tools/capture/` | Playwright scripts that fetch live pages through a headed Chromium (the only thing that passes Cloudflare from this machine). | yes |
| `reference/legacy-whirldroid/` | Whirldroid 1.8.12 source from Bitbucket (Java, 2016). Behavioural reference only, unknown licence. | no (git-ignored) |
| `reference/whirldroid-2.14.12-decompiled/` | jadx output of the last Play Store build (versionCode 92), app package and resources only. The authoritative spec for features; see PLAN.md §1. | no (git-ignored) |
| `~/.config/whirldroid/apikey` | Dallin's API key, mode 600. Read it in scripts; never print, log, or commit it. | no |

## Hard rules
1. **Thread IDs are strings** (`3rvvvlwj`). Anything that parses them as integers is a bug.
2. **Parse the raw fixtures, not the rendered ones.** The live site serves `span.pages[data-pages]` for page counts and inline `var thread_pages = N;` on thread pages; JavaScript rewrites both.
3. **Whims are gone.** Do not build a Whims feature; "Private Messages" is forum 1 and opens in the browser.
4. **Skip `#watchbar`.** It is a hidden `.notebar.marker` inside `#replylist`. Moderator notes are `#top_notebar_public`; multi-part links are `.notebar.parts`.
5. **API dates** are `yyyy-MM-dd'T'HH:mm:ssxx` (`+1000`, no colon). API titles are HTML-escaped. `WATCHED.UNREAD` is an int, `THREADS.UNREAD` a boolean. Always pass `watchedmode` explicitly.
6. **The dev machine cannot reach Whirlpool** (Cloudflare blocks curl/OkHttp-from-desktop/headless). Live checks run on Dallin's phone; captures run through `tools/capture/`. Unit tests never touch the network.
7. **Releases come only from the tag-driven GitHub Actions workflow** (PLAN.md §6). Never publish a local build.
8. Nothing from `reference/` is copied into the new code; it is read for behaviour only.
9. **`main` is protected**: no direct pushes (admins included), linear history, CI job `build` must pass on an up-to-date branch. Work on a branch, open a PR with `gh pr create`, merge with `gh pr merge --squash --auto`. Release tags (`vX.Y.Z`) are still pushed directly, from a commit on `main`.

## Where Phase 1 left things (2026-10-20)
- **Cloudflare**: OkHttp passes on Dallin's phone with any User-Agent (PLAN.md §2.1); `tools/spike/run.sh` reruns the spike. Mobile data not yet checked.
- **Repo**: https://github.com/dalbo/whirl (public, MIT). CI (`.github/workflows/ci.yml`: `./gradlew lint test assembleDebug`) is green. `release.yml` published signed `v0.0.2`; tag `v0.0.1` failed at the signature check and published nothing. Release keystore: `~/.config/whirldroid/release.jks` + `release-signing.env` (mode 600), uploaded as the four `SIGNING_*` secrets.
- **Toolchain**: Android SDK in `~/Android/Sdk` (platforms 36 and 37, build-tools 36/37), JDK 21 locally, 17 in CI. AGP 9.4 (built-in Kotlin, no `kotlin-android` plugin), Kotlin 2.4.21, Gradle 9.8.2, compileSdk 37 (OkHttp 5.5 requires it), targetSdk 36, minSdk 26. Versions in `gradle/libs.versions.toml`; check Maven before adding a library, versions move fast.
- **Build logic** (`build-logic/convention`): `whirl.jvm.library` (JUnit Jupiter), `whirl.android.library` (JUnit 4 + Robolectric, SDK 35 via `@Config`), `whirl.android.compose`, `whirl.hilt` (KSP + Hilt), `whirl.android.feature` (library + compose + hilt + `:core:ui` + `:core:data`). Kotlin warnings are errors. Every test task gets `-Dwhirl.fixtures=<repo>/fixtures` and a 10-minute timeout.
- **Modules** (128 tests, all green):
  - `:core:model` — `ThreadId` (string value class), `WhirlpoolError` (Challenge, BadKey, RateLimited, Maintenance, Network, NotSignedIn, Drift, Api), list/thread/post/search/profile/API models, `PostBody` block model.
  - `:core:scraper` (pure JVM, 68 tests) — `ThreadListParser`, `ThreadPageParser` (inline page vars, posts, moderator note, parts, moderator-hidden posts), `SearchResultsParser`, `ForumIndexParser`, `UserProfileParser`, `PostBodyParser`, `WhirlpoolTime` (all Whirlpool date forms, Sydney time). `AllFixturesTest` fails if a new fixture has no parser mapped.
  - `:core:network` (pure JVM, 47 tests) — `WhirlpoolApi` (user, forums, news, threads, recent, watched + watchedadd/read/remove; publishes `ALERT`), `WhirlpoolWeb` + `WhirlpoolUrls` (every verified page URL: forum/group/page, thread `?p=-1`, `ux`/`um`/`ur` filters, archive, popular, reply locator, user, both search forms), Cloudflare detection, retry/backoff. No Retrofit.
  - `:core:database` — Room schema **v1** exported to `core/database/schemas/`: forums, favourite_forums, threads (merged from all sources via partial upserts), thread_source (ordered lists per source/key), news, notified, fetch_log. **No posts table yet.**
  - `:core:data` — `SettingsRepository` (DataStore; API key + all §1.2 settings; excluded from backup), `AccountRepository`, `ForumRepository`, `NewsRepository`, `LatestThreadsRepository`, `WatchedRepository`, `Refresher` (2.14.12 max ages, 10 s manual throttle), `Cached<T>(value, updatedAt)`, `NetworkModule` (User-Agent `Whirl/<version> (Android; +https://github.com/dalbo/whirl)`).
  - `:core:ui` — `WhirlTheme`, `errorMessage`, `ErrorState` (Retry + Open in browser), `UpdatedAgo`.
  - `:feature:setup` (wizard, verified live on the phone), `:feature:settings` (every setting, log out).
  - `:app` — Hilt, splash, type-safe Navigation, theme from settings. (Phase 1's temporary `home/HomeScreen.kt` was replaced in Phase 2 task 1.)
- **Devices**: the phone is reached over USB with the Windows adb (`/mnt/c/Users/dalli/AppData/Local/Android/Sdk/platform-tools/adb.exe`, pass APKs with `wslpath -w`). Wireless pairing failed; ask Dallin to plug in. Debug builds install as `au.howesthat.whirl.debug` beside the release app.
- **Fixture corrections found in Phase 1** (now in the docs): `thread_3pxj858k_p2.html` has 3 edited posts, not 8; `forum92_p1.html` has 1 deleted row; `thread_97r5wzn3_p1.html` has no public notebar; forum 71 is "Lifestyle", group 189 "Food"; lists, `<hr>` and moderator-hidden posts do occur.

## Phase 2 tasks (read-only parity → v0.1), suggested order
PLAN.md §1 is the spec and §9 the scope. Open a PR per task; keep CI green.
1. **Navigation shell**: drawer (Industry News, Recent, Watched, Popular, Forums, Settings, About), `homepage` setting picks the start screen, replace the temporary Home. About shows version, repo and issue links.
2. **Thread page cache**: Room migration v1→v2 adding `posts` (PLAN.md §4 columns) with a migration test (`room-testing` is already a test dependency); LRU of the last 50 thread pages; repository that fetches with `WhirlpoolWeb` + `ThreadPageParser`, maps `NotSignedIn` to `ForumRepository.markPrivate`, and raises Drift as "Whirlpool changed its layout".
3. **Thread lists**: one screen for forum/watched/recent/popular/latest/search: forum pages with page picker and swipe, group selector, sticky/closed/moved/deleted styling, Unread/All tabs for watched, grouped-by-forum headers, "x unread", row long-press menu (open in browser, jump to last page, go to forum; watch/mark read/unwatch actions are Phase 3), "Updated N ago", pull to refresh. Unread state only from the API, never from HTML. Forum lists may overlay `THREADS.UNREAD` from `get=threads&forumids=&threadcount=100`.
4. **Forums screen**: sections from `get=forum`, Favourites section (long-press to add/remove), private forums (1, 35, 85, 134 or any learned via NotSignedIn) open in Custom Tabs (`androidx.browser`).
5. **Thread view**: pager over pages from the inline vars, `?p=-1` for the last page, Compose renderer for `PostBody` (quote attribution as a link, `wcrep1`/`wcrep2` colours from PLAN.md §1.5, tap-to-reveal spoilers, lists, image placeholders, in-app links for thread/forum/user/archive and whrl.pl), moderator note header, parts links, hidden-post rendering, post filters (`ux`/`um`/`ur`), long-press (reply/edit in browser, user info, user's posts), quote navigation (same page → scroll; else `WhirlpoolUrls.reply(postId)`), post font size setting, opening-position rules from PLAN.md §1.1.
6. **News, search (list view; relevance view optional), user profile**.
7. **Deep links**: `https://forums.whirlpool.net.au/thread|forum|user|archive/*` (needs `autoVerify` off since we don't own the domain: register as a browsable handler) and the old `whirldroid-thread://` scheme only if cheap.
8. **Release v0.1**: manual device checklist (PLAN.md §8) with Dallin, `## v0.1.0` in CHANGELOG.md, tag `v0.1.0` from `main`.

## Phase 2 progress
One PR per task; this list says what landed and what was decided along the way.
1. **Navigation shell** (done): drawer in `app/navigation/` (`WhirlApp`, `Drawer`, type-safe `Routes`), About screen. Setup is no longer a NavHost destination: `MainActivity` shows `SetupScreen` while `setupDone` is false and `WhirlApp` otherwise, so finishing setup or signing out swaps the tree and the homepage is always the root of the back stack. "Latest activity" (`get=threads`, site-wide) is a drawer entry and a `Homepage` option (new; not in 2.14.12). Screens that can be drawer roots or pushed take a `NavIcon` (`:core:ui`). `openInBrowser` (Custom Tabs, `androidx.browser`) is in `:core:ui`. Drawer screens not built yet show a placeholder.

## Known unknowns to resolve as you go
- Shape of `get=recent` when non-empty (assumed to match `THREADS`; the Recent screen must cope with it failing as Drift).
- Markup of a deleted post and of a post with an image (capture when found: add the URL to `tools/capture/public.json`, run `npm run public`).
- Behaviour on mobile data; whether `/forum/?action=replies&r=<postid>` redirects as expected from OkHttp (check on the phone).
- Exact response of `watchedadd/read/remove` (Phase 3).

## Who to ask
Dallin (owner, Whirlpool user, tests on a phone in Australia). Decisions already taken are in PLAN.md §5 (package `au.howesthat.whirl`, MIT licence, working name "Whirl"); ask before changing them.

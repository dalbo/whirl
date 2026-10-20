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

## First tasks (Phase 1, in order)
1. Device spike: a minimal app with OkHttp, User-Agent `Whirldroid` (then a new string), calling `get=user` and fetching `/forum/92` and `/thread/3pxj858k?p=2`. Install on the phone via the Windows adb (`/mnt/c/Users/dalli/AppData/Local/Android/Sdk/platform-tools/adb.exe`) or wireless debugging. Record status codes and the `cf-mitigated` header. If challenged, see PLAN.md §2.1 fallbacks before writing any more code.
2. Install the Android SDK in WSL (PLAN.md §7), create the multi-module Kotlin/Compose skeleton, CI workflow, and the release workflow; cut `v0.0.1` to prove signing and artefact upload.
3. `:core:scraper` with tests for every raw fixture. Suggested first assertions: `forum92_p1.html` → 48 thread rows, 17 of them with `data-pages` > 1, 1 moved row (`tr.pointer`), 1 deleted row (`tr.deleted`); `forum92_p2.html` → 2 closed; `popular.html` → 36 sections, 183 rows, 2 sticky; `thread_3pxj858k_p2.html` → page 2 of 13, 20 posts, 8 edited, 5 OP, 17 quote attributions; `thread_big_9nznpky9_last.html` → page 2663 of 2663, 9 posts, 1 public notebar; `thread_sticky_3mnkq4v9.html` → 1211 pages, parts notebar with 3 parts; `search_nbn_listview.html` → 100 rows; `search_nbn.html` → 50 relevance results; `thread_private_feedback_3nzn4p83.html` → NotSignedIn; `user_30703.html` → about-table with "Status"; `thread_archive_numeric.html` → 48 posts.
4. `:core:network` + API models against `fixtures/api/`, including the 403 envelope.

## Known unknowns to resolve as you go
- Does OkHttp pass Cloudflare on the phone? (task 1)
- Shape of `get=recent` when non-empty.
- Markup of a deleted post and of a post with an image or list (capture when found: add the URL to `tools/capture/public.json`, run `npm run public`).
- Exact response of `watchedadd/read/remove` commands (exercise once on a throwaway thread in Phase 3).

## Who to ask
Dallin (owner, Whirlpool user, tests on a phone in Australia). Decisions already taken are in PLAN.md §5 (package `au.howesthat.whirl`, MIT licence, working name "Whirl"); ask before changing them.

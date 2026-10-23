# Whirl: a modern Android client for the Whirlpool forums (Whirldroid rewrite)

Version 2 of this plan, 2026-10-20. Supersedes the first draft after an independent critique, live verification against Whirlpool, and decompilation of the last shipped Whirldroid build.

**Working name** "Whirl", package `au.howesthat.whirl`, MIT licence (see Decisions). The original app, Whirldroid by Greg Smith (gregdev), is abandoned: Bitbucket source stops at 1.8.12 (Jan 2016); the Play Store build reached 2.14.12 (Apr 2021). Both are in `reference/` (git-ignored, unknown licence) and are used only as a behavioural spec.

Companion documents: `HANDOFF.md` (brief for the engineer or AI session that builds this), `fixtures/README.md` (what every captured file is), `tools/capture/README.md` (how to re-capture).

---

## 1. Target feature set (what Whirldroid 2.14.12 does)

Everything in this section was read from the decompiled 2.14.12 code and resources (`reference/whirldroid-2.14.12-decompiled/`). Parity with this list is the definition of "equivalent features". Items marked **[drop]** are deliberately not carried forward.

### 1.1 Navigation and screens
- Single activity with a navigation drawer: Industry News, Whims **[drop, see 2.2]**, Recent Threads, Watched Threads, Popular Threads, Forums, then Settings, About, Send Feedback. A `pref_homepage` setting picks the start screen (default Forums).
- First-run **setup wizard**: Intro → API key ("Log in", validated by one combined API call; failure clears the key with a "remember to include dashes" dialog) → Notifications (shows "Logged in as NAME (#ID)", toggles for watched-thread alerts and check interval) → Theme (Light/Dark) → Done.
- **Forums**: sectioned list from the API, with a "Favourites" section (local SQLite; add/remove via overflow or long-press), swipe to refresh, "New thread" opens the browser.
- **Thread list** (one screen for forum, watched, recent, popular, search): swipeable pages (ViewPager, one page per forum page) with a "Page N of M" spinner for public forums; sub-forum **group** spinner ("All groups" + groups); search box scoped to the current forum/group (private forums search in the browser); rows styled for sticky, closed, deleted, moved, unread; "x unread" in the title; row overflow/long-press menu: Watch, Jump to last post, Open in browser, Go to forum (grouped lists), Mark as read and Unwatch (watched); watched and whims have Unread/All tabs; grouped-by-forum headers for watched/recent/popular/search (tap header → forum); "Updated N ago" subtitle; swipe to refresh.
- **Thread view**: swipeable pages of natively rendered posts (TextView + `Html.fromHtml`, no WebView); bottom bar: Reply to OP (browser), Watch, Mark as read and Unwatch (when opened from watched), Jump to last post, Refresh, Open in browser; **post filter** spinner: All posts / Posts by me / Posts by moderators / Posts by reps (site URL params `ux=<userid>`, `um=1`, `ur=1`); post long-press: Reply in browser, View user info, View user's posts (`ux=`), Edit post (own posts, browser `action=edit&e=<postid>`); **quote-link navigation**: a quote attribution (`p.reference a[title=<postid>]`) scrolls to that post if on the page, else locates it via `/forum/?action=replies&r=<postid>` and jumps page or opens the thread at that post; moderator notebar rendered as a list header; snackbar with Undo on watch/unwatch; optional return to list after marking read.
- **Opening rules** (from 2.14.12 `ForumPageFragment.openThread`): watched → open at `LASTPAGE`, scroll to `(LASTREAD % 20) − 1` (0 → 20) unless `pref_loadwatchedattop`; recent → last page unless `pref_loadattop`; public forums → last page if `pref_loadpublicatbottom`; private forums → browser (`?p=-1#bottom` or `&p=N#rM`), also if `pref_loadprivateatbottom`; `pref_openthreadsinbrowser` forces the browser; `pref_watchedautomarkasread` marks watched threads read when opened.
- **News**: API list grouped by weekday, opens `https://whirlpool.net.au/news/go.cfm?article=<id>` in the browser.
- **User info**: scraped profile key/value lines, subtitle "#id".
- **About / Feedback**: version, link to the Whirldroid thread, email, Play review. **[replace with our own repo/issue links]**
- **Deep link** `whirldroid-thread://com.gregdev.whirldroid?threadid=<id>` (handled only on `onNewIntent` **[bug, drop]**). **[new: handle `https://forums.whirlpool.net.au/thread/*`, `/forum/*`, `/user/*`, `/archive/*` too]**

### 1.2 Settings (2.14.12 keys, titles, defaults)
| Tab | Key | Meaning | Default |
|---|---|---|---|
| General | `pref_apikey` | API key (18 chars) | – |
| General | `pref_homepage` | Start screen | `ForumList` |
| General | `pref_theme` | 0 Light / 1 Dark / 2 "Dark at night" | `0` |
| General | `pref_nightmodestart`, `pref_nightmodeend` | HH:mm window for theme 2 | `00:00` |
| General | `pref_postfontsize` | −2/−1/0/0.5/1/2 → 14/16/normal/19/20/22 sp | `0` |
| Notifications | `pref_whimnotify` | **[drop]** | false |
| Notifications | `pref_watchednotify` | Watched-thread alerts | false |
| Notifications | `pref_notifyfreq` | 15/30/60/240/720/1440 min | – |
| Notifications | `pref_notifysound`, `pref_notifyvibrate`, `pref_notifyled` | **[replace with notification channel settings]** | false |
| Threads | `pref_openthreadsinbrowser`, `pref_loadpublicatbottom`, `pref_loadprivateatbottom` | see opening rules | false |
| Recent | `pref_recenthistory` | 1/3/7/14/30/60/120 days | `7` |
| Recent | `pref_loadattop`, `pref_ignoreownrepliesrecent` | | false |
| Watched | `pref_loadwatchedattop`, `pref_ignoreownreplies`, `pref_watchedautomarkasread`, `pref_watchedbacktolist` | | false |
| Whims | `pref_whimautomarkasread` | **[drop]** | true |

The 1.8.12 `pref_hidewatchedunread` was replaced by Unread/All tabs; keep the tabs.

### 1.3 Notifications (2.14.12)
Periodic job (FirebaseJobDispatcher) every `pref_notifyfreq` minutes, re-armed on boot and on settings change. One API call (`get=watched&watchedmode=0`). Notifies only when some unread thread's `LAST_DATE` is newer than the stored last-notify time and (if `pref_ignoreownreplies`) the last poster is not the user. Title "New watched thread reply/replies", text "N unread posts" or "N new replies in M threads", inbox style with thread titles, actions **Mark Read** (`watchedread=<ids>`) and **Unwatch** (`watchedremove=<ids>`), channel `whirldroid_watched_threads`, high importance, tap opens the Watched screen. Cancelled when the unread count is 0. The 2.14.12 receivers crash on the ids extra type **[bug, drop]**.

### 1.4 Caching and refresh (2.14.12)
Serialised lists in private files: news 60 min; recent, popular, watched (all and unread) 10 min; forums 7 days; scraped forum pages never cached. Mark-read/unwatch mutate the cached list locally before the API call. Swipe to refresh everywhere. **[replace with Room, keep the max-age rules]**

### 1.5 Rendering rules worth keeping (2.14.12 `PostsAdapter`)
Quote attribution `p.reference` → coloured bold link to the quoted post; `span.wcrep1` user-quote colour (`#4455aa` light / `#7885c4` dark); `span.wcrep2` other-quote `#9F6E19`; `<li>` → bullets; relative `/wiki/` and `//` links made absolute; every thread-link form rewritten to an in-app link; no image loading; no spoiler handling **[new: handle `span.wcspoil` as tap-to-reveal]**.

---

## 2. Whirlpool as it is today (verified 2026-10-20)

All claims below were checked against live captures in `fixtures/`. Where something was not captured, it says so.

### 2.1 Reachability and Cloudflare
- Every host (`forums.whirlpool.net.au`, `whirlpool.net.au/api/`, `/wiki/`) sits behind a **Cloudflare managed challenge**. From the same residential Superloop IP: curl, Node `fetch`, and headless Chromium get HTTP 403 `cf-mitigated: challenge`; a headed Chromium passes with no cookie. The block is TLS/JS-fingerprint based, not IP based.
- Whirldroid 2.14.12 (Apache HttpClient / jsoup, User-Agent exactly `Whirldroid`, no cookies) **still works on Dallin's phone**, and its last release note says a custom User-Agent was added "to get around Whirlpool blocks". **Verified 2026-10-20 (tools/spike, Dallin's phone, Android SDK 37, home Wi-Fi):** OkHttp 5.5 over HTTP/2 passes with User-Agent `Whirldroid`, `Whirl/0.0.1 (Android; au.howesthat.whirl)` and the default `okhttp/5.5.0`, and HttpURLConnection passes too: 12/12 requests HTTP 200, no `cf-mitigated`, bodies within a few bytes of the raw fixtures. The User-Agent does not matter to Cloudflare. Mobile data and long sessions not yet checked. The fallbacks below are kept for reference only: Fallback designs, in order: match the 2.14.12 client (HttpURLConnection or Apache HttpClient port) → in-app WebView that solves the challenge and exports `cf_clearance` into the OkHttp cookie jar → ask Whirlpool staff in the API thread for an allow-list.
- Rate limits are undocumented; ~40 API calls and ~40 page loads in 30 minutes caused no 509.
- **Whirlpool's terms for apps (read 2026-10-21):** the API SDK wiki (`/wiki/wp_api`) says calls may be rate limited and the app must honour it; the API "must not be used as part of any application or interface that generate revenue" (so Whirl stays free with no ads or paid tiers); apps are not formally endorsed but may be listed on the 3rd Party Clients wiki page; support goes through the API discussion thread. The community rules (`/wiki/wp_rules`, "apps created/developed by users") let an individual Australian developer start a thread to discuss their app and ask for feedback, as long as it invites discussion rather than advertising; contact a mod first if unsure. Nothing found on reading public pages (scraping) by apps: ask the moderators. Whirlpool's privacy policy is `/wiki/wp_privacypolicy`.

### 2.2 The JSON API (`https://whirlpool.net.au/api/?key=K&output=json&get=a+b`)
- **Envelope on every response**: `ALERT`, `AUTH {NAME, ID}`, `DOCS`, `ERROR`, then the requested keys. Bad key → HTTP 403 with JSON `ERROR: "Not authenticated. Bad API key?"` (`api_badkey.json`). Use `AUTH.ID`/`NAME` for the current user instead of parsing the key.
- `get=forum` → 54 forums in 8 sections (`api_forum.json`); 50 are on the public forum index; 4 need a web login: 1 Private Messages, 35 Feedback, 85 In the news, 134 Talk with a mod. Do not hardcode the public list; derive it as "forums whose `/forum/<id>` page is not `Not signed in`" or from the index page.
- `get=threads[&forumids=&threadcount=1..100]` → thread objects `{ID (string slug), TITLE (HTML-escaped: `&amp;`), REPLIES, FORUM_ID, FORUM_NAME, GROUP, FIRST_DATE, FIRST{NAME,ID}, LAST_DATE, LAST{NAME,ID}, SUBSCRIBED, UNREAD (boolean), PARTICIPATED, DRAFT}`. Without `forumids` it returns the 30 most recently active threads site-wide (`api_threads_default.json`), a ready-made "latest activity" feed. Works for private forums 35 (list only).
- `get=watched[&watchedmode=0|1]` → `{ID, TITLE, REPLIES, FORUM_ID, FORUM_NAME, FIRST_DATE, FIRST, LAST_DATE, LAST, UNREAD (integer count), LASTREAD (post number), LASTPAGE}`. Mode 1 = all (19 here), mode 0 = unread only (2), **no mode = something else (4)**: always pass the mode explicitly. Commands `watchedadd=`, `watchedread=`, `watchedremove=` take comma lists. **Verified 2026-10-21** (`fixtures/api/api_cmd_*`, thread `3pxj858k`): each answers HTTP 200 with the bare envelope; combined with `get=watched&watchedmode=1` the returned list already reflects the command (2.14.12 always combined them); a newly watched thread comes back read to the end (`UNREAD 0`, `LASTREAD` = replies + 1); an unknown id gives HTTP 500 with an HTML error page labelled `application/json`.
- `get=recent[&recentdays=N&userid=]` → `[]` for this account at 14 and 120 days and for two other user IDs, so the shape is unverified; assume it matches `THREADS`. Use `get=threads` (site-wide) as the fallback feed.
- `get=news` → 30 items `{ID, TITLE, SOURCE, BLURB, DATE}`. `get=user[&userid=]` → `{ID, NAME}`. `get=contacts` → `[]`. `get=url` → URL templates (stale `%d` placeholders).
- **Whims no longer exist**: `get=whims`, `get=whim&whimid=` and a made-up `get` all return the bare envelope with no error (`api_whims.json`, `api_bogus_get.json`), while real empty lists come back as `[]`. `get=url` points the whim inbox/outbox at `/forum/1` ("Private Messages") and has empty whim read/write/reply URLs. Private messages are now a forum that needs a web login. Drop the Whims feature; show "Private Messages" as a forum entry that opens in the browser.
- **Dates** are `2026-10-19T20:39:50+1000` (no colon in the offset). `OffsetDateTime.parse` rejects this; use `DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxx")`. The instant is correct (the HTML list shows the same post as "Yesterday at 9:39 pm" in AEDT), so parse the offset and display in `Australia/Sydney` or the device zone. Never treat `+1000` as "the site's zone".

### 2.3 HTML pages (raw server responses in `fixtures/html/`; the `rendered/` copies are post-JavaScript and must not be used for parsers)
- **URLs**: threads are `/thread/<slug>` where slug is 8 alphanumerics (`3rvvvlwj`); numeric legacy IDs still resolve (`/thread/1000422`) and `forum-replies.cfm?t=N` redirects to the same page; `/archive/<id>` returns the **whole thread on one page** (48 posts for a 3-page thread; no pagination). Forums are `/forum/<id>?p=N&g=G`. Users `/user/<id>`. Popular `/forum/?action=popular_views`.
- **Forum thread list** (`forum92_p1.html`, `forum7_g179.html`): `table > tbody > tr#tr<slug>.thread[.sticky][.closed][.read]`; moved and deleted threads keep their id but have class `pointer` / `deleted` instead of `thread`, a `td.point` cell reading "moved" / "deleted", and a note in `td.title` ("– moved to Modems/Routers", "« use existing thread"); their `a.title` may point at a different thread. Cells: `td.title > a.title[href=/thread/<slug>]` + `span.pages[data-thread][data-pages=N]` (server-rendered page count; empty span), `td.reps[title=exact]`, `td.reads[title=exact]`, `td.oldest > a[href=/user/N]` then date text after `<br>`, `td.newest.collapse4 > span > a[href=/user/N]` then date text, `td.goend a[href=...?p=-1#bottom]`. Pagination: `select[name=p] option` (last = page count) and `div.footbar ul.pagination li[.current] a`. Groups: `select[name=g] option` (only on forums that have groups; forum 7 yes, 92 no). Breadcrumb: `ul#breadcrumb li a span[itemprop=name]` (schema.org). Date texts are relative or local-time strings: `Yesterday at 9:39 pm`, `Monday at 2:33 pm`, `2025-Nov-1, 12:34 am`, `22h`, `2d`.
- **Popular** (`popular.html`): same table with `tr.section > td.title > a.title[href=/forum/N]` headers and, on rows, `a.group[href=/forum/N?g=G]` before `a.title`. 183 rows, 36 sections, 2 sticky.
- **Search** (relevance results may have no `div.snippet`; `div.detail` can read `Forum » Group` with two links): `/forum/?action=threads_search` now 302s to `/search?q=&f=<forum>&fg=<group>&w=<t|b|tb>&p=N`. Relevance view: `div#searchresults ol.results > li[value=N] > div.title a[href=/archive/<slug>]`, `div.detail` (forum link, first-post age → last-post age), `div.snippet`, `div.url`; 50 per page; no pagination markup (step `p` until empty). **List view** `/forum/?action=search&q=&f=&fg=&w=t&o=list` (`search_nbn_listview.html`) returns the popular-style table, 100 rows per page; 2.14.12 used this. One parser serves popular and search list view. Form selects on the search page: `f`, `fg`, `w`, `p`.
- **Forum index** `/` (`forum_index.html`): `td.title div.title a[href=/forum/N]` plus `td.threads` with the 3 latest threads per forum; 50 forums.
- **Thread page** (`thread_3pxj858k_p2.html`, `thread_big_9nznpky9_last.html`, `thread_sticky_3mnkq4v9.html`):
  - Inline script: `var forum_id = 92; var thread_id = "3pxj858k"; var thread_page = 2; var thread_pages = 13; var is_watching = false; var read_to = 0;` and later `var pages = { total:13, current:2, userarr:[], baseurl:"/thread/3pxj858k", firstdate:"15 months ago", lastdate:"4 days ago" };`. **This is the page count and current page**; `?p=-1` resolves `thread_page` to the real last page. `#top_pagination` contains only `li.first`/`li.last`; the page list is built by JS. No `select[name=p]`.
  - Title: `ul#breadcrumb li` last, or `<title>` minus suffix. Private/unreadable: `<title>Not signed in</title>` and no `#replylist`.
  - Posts: `#replylist > div.replyblock[data-replyid][data-replynum][data-userid][data-uname]` (20 per page). Inside: `.replymeta .userstats .userid` ("User #30703"), `.replycount`, `.username a[href=/user/N] .bu_name`, `.quip`, `.usergroup`, `.replytools-inner .date` (text after the "posted" link: `2008-Jun-24, 1:15 pm`, Sydney local, no zone; desktop and mobile tool bars each carry a `.date` and `.shortcode`; on `/archive/` pages "posted" is plain text, the date is inside `span[itemprop=datePublished][content=<ISO>]` and ends in `AEST`; `.edited` sits inside `.date`, "edited 2025-Jun-23, 11:55 am" or "edited moments later"), `.shortcode` (whrl.pl link), `.replytext.bodytext` with optional `div.op` ("O.P.") and `div.edited` ("this post was edited") badges inside it, `p.reference` quote attribution, `span.wcrep1`/`wcrep2` quote text, `span.wcspoil` spoilers, `a.inter` internal links. Body markup in fixtures: `p br a div span strong b em i ins wbr ul ol li hr`; code and images not yet seen. Archive bodies are wrapped in `span[itemprop=text]`.
  - **Moderator-hidden posts**: `div.replyblock.hidden` holding `.hiddenpostnotice` (user link, plain time in `.replytools`, `.replytext` "This post was hidden by a moderator (reason)"); no `.date`. `/archive/` omits them, so archive post numbers have gaps.
  - **Three kinds of `.notebar`**: `#watchbar.notebar.marker` (hidden "You have read to here", first child of `#replylist`, must be skipped), `#top_notebar_public.notebar.public` (moderator note; JS copies it into `#foot_notebar_public`), `div.notebar.parts` (links to parts 1..N of a multi-part thread). The legacy `.notebar` / `#replylist > div` selectors are wrong now.
  - Not captured: a deleted post (none found; 2.14.12 treats a post without `.date` as deleted, but hidden posts also lack `.date`, so check `.hiddenpostnotice` first), a closed thread's page, a post with an image.
- **User profile** (`user_30703.html`): `#userprofile` → `h2 "Recent activity"` + `div#threads table` (thread-list rows, `?days=N` filters) then `h2 "About this user"` + a table of `td b "Status:"` / value rows. The legacy `#userprofile > div` index-1 parsing is wrong now.
- **Site AJAX (needs a logged-in web session, not the key; not used, see Decisions "Web session in the app")**: POST `/forum/ajax.cfm` with `action=startWatching|stopWatching|markAtPoint&thread_id=&point=<replynum>` (from `fixtures/html/whirlpool_skin_script.js`). "Mark read to here" is therefore unavailable without web login; the API `watchedread` marks the whole thread.

---

## 3. Bugs and designs not to carry forward
From 1.8.12 and 2.14.12 code:
- `int` thread IDs (1.8.12) and the `(t=N)` regex; 2.14.12 already uses strings. Numeric-only parsing anywhere is a bug.
- Null-pointer paths: sticky/closed flags set before the null check in forum scraping; search inserts null rows; notification action receivers read the wrong extra type and crash on Unwatch / Mark whim read.
- "Private forum" handling that calls `finish()` on the only activity; deep link handled only in `onNewIntent`; `?&p=N` URL form.
- Hand-rolled AEST maths (`−10h + device offset + DST`); use `java.time`.
- Uptime-based "already notified" heuristic (1.8.12) and a single last-notify timestamp (2.14.12): store per-thread `(id, LAST_DATE)` of what was notified.
- Watched-list "cheap hack" clearing the list; `REPLIES/20` page maths where `LASTPAGE` or `data-pages` exists.
- Global mutable singleton API with serialised-file cache; three blind retries on any failure including 403.
- Google Analytics / Firebase Analytics / Crashlytics; Easter-egg dev prefs; vestigial Login fragment and RecyclerView stubs.
- Common HTML-entity replacement by string table: use a real unescape (jsoup `Parser.unescapeEntities`) for API titles and whim text.

---

## 4. Architecture

**Stack**: Kotlin 2.x, Jetpack Compose + Material 3 (dynamic colour; light/dark/system; "dark at night" schedule kept as an option), Navigation Compose with type-safe routes, Hilt, Room, DataStore, WorkManager, OkHttp (+ Retrofit for the JSON API, kotlinx.serialization), jsoup, Coil, Custom Tabs. minSdk 26, targetSdk 36 (Play's requirement from August 2026), compileSdk 37 (OkHttp 5.5 requires it), AGP 9.4 with built-in Kotlin, Kotlin 2.4, JDK 17 bytecode (builds on JDK 17 or 21). No Retrofit: the API is a single endpoint with combinable `get=` keys, so `:core:network` is OkHttp + kotlinx.serialization.

**Modules**
- `:core:network` — one `WhirlpoolClient`: API key injection, fixed User-Agent (start with `Whirldroid`'s successor string; make it a build config), cookie jar (empty for now), typed errors, backoff on 5xx/509, Cloudflare detection (`cf-mitigated` header or "Just a moment" body → `WhirlpoolError.Challenge`).
- `:core:scraper` — pure JVM (no Android deps): `ThreadListParser` (forum pages, popular, search list view, user recent activity), `SearchResultsParser` (relevance view), `ThreadPageParser` (inline vars, posts, notebars), `UserProfileParser`, `ForumIndexParser`, `PostBodyParser` (HTML → block model). Every parser returns a result with counts and raises `ParseDrift` when a 200 page yields zero rows where rows are expected. Unit-tested against `fixtures/html/*.html` (raw only).
- `:core:database` — Room: `forums(id, title, section, sort, is_private, is_favourite)`, `threads(id TEXT PK, title, forum_id, replies, page_count, first_*, last_*, unread_count, last_read_post, last_page, subscribed, …)`, `thread_source(source, key, thread_id, position)` (source ∈ forum/watched/recent/popular/search/latest), `thread_pages(thread_id, filter, page, page_count, title, forum_*, moderator_note_html, parts, fetched_at, accessed_at)` + `posts(thread_id, filter, page, reply_id, reply_num, user_*, user_group, quip, post_count, posted_text, posted_at?, edited_text, is_op, hidden_reason, is_deleted, short_url, body_html)` (schema v2; `filter` is "" or the post filter `u<id>`/`m`/`r`; posts cascade with their page), `news`, `favourite_forums`, `notified(thread_id, last_date)`, `fetch_log(key, fetched_at)`.
- `:core:data` — repositories exposing `Flow`s; stale-while-revalidate with the 2.14.12 max ages; manual refresh throttle 10 s; last 50 thread pages kept (LRU).
- `:core:ui` — theme, `PostBody` composable (block model: Paragraph, Quote(attribution, postId), Spoiler, Link, Bullet, Image placeholder), list items, error/empty states with "Retry" and "Open in browser".
- `:feature:setup`, `:feature:forums`, `:feature:threadlist`, `:feature:thread`, `:feature:news`, `:feature:user`, `:feature:settings`, `:feature:search`.
- `:app` — DI, `NavHost`, drawer, deep links, WorkManager worker, notification channels.

**Key flows**
- Thread view loads `/thread/<id>?p=N`, reads the inline vars for page count/current page, parses posts, caches the page; page spinner and swipe use `thread_pages`; "last page" uses `?p=-1`; watched threads open at `LASTPAGE` and scroll to `((LASTREAD − 1) % 20)`.
- Unread state: watched lists use `WATCHED.UNREAD` (int); forum lists may overlay `THREADS.UNREAD` (bool) for the first 100 threads of that forum via `get=threads&forumids=&threadcount=100`; scraped rows are always `.read` because there is no web session, so never derive unread from HTML.
- Private forums (1, 35, 85, 134 or any page returning "Not signed in"): list via `get=threads`, open in Custom Tabs.
- Notifications: WorkManager periodic (≥15 min), `POST_NOTIFICATIONS` runtime permission, one channel for watched threads, actions Mark read / Unwatch via API, de-dup on `(thread_id, LAST_DATE)`.
- Links in posts: `/thread/`, `/archive/`, `/forum/`, `/user/`, `whrl.pl` short codes (resolve by following the redirect) handled in-app; everything else via Custom Tabs.

**Secrets**: API key in Preferences DataStore, excluded from Auto Backup (`EncryptedSharedPreferences` is deprecated). Never logged.

---

## 5. Decisions (defaults the builder should take unless Dallin overrides)
| Topic | Decision |
|---|---|
| App name / package | Working name "Whirl"; package `au.howesthat.whirl` (Dallin's domain howesthat.au). Do **not** reuse "Whirldroid" or `com.gregdev.whirldroid`; the Whirldroid thread (`97q1vrm9`) is still active, so a courtesy post to gregdev is worthwhile. |
| Licence | MIT for the new code (Dallin's choice; no copyleft needed). The Whirldroid sources carry **no licence** (no LICENSE file in the 1.8.12 repo or the 2.14.12 APK; the only Apache header is Google's on `DashboardLayout.java`), so they are all-rights-reserved and stay git-ignored in `reference/`, read for behaviour only. |
| Navigation | Drawer like 2.14.12 + `homepage` setting; no bottom bar; dashboard grid not revived. |
| Offline | Stale-while-revalidate from Room; always render cache with "Updated N ago"; never block on network. |
| Post rendering | jsoup → block model → Compose; not one giant `AnnotatedString`; images as tappable placeholders until a fixture with images exists. |
| Error UX | Typed `WhirlpoolError` (Challenge, BadKey, RateLimited, Maintenance, Network, Parse/Drift, NotSignedIn); API `ALERT`/`ERROR` strings shown verbatim; every scraped screen has Retry and Open in browser. |
| Whims / PMs | Dropped. "Private Messages" is forum 1 → browser. |
| Mark read to here | Not available without web login → "Mark thread read" (API) only. |
| Web session in the app | **None** (Dallin, 2026-10-21): no in-app Whirlpool login, cookies, site AJAX or posting. In the past Whirlpool has not allowed posting from apps, and the app must not get the site offside. Posting, private forums and private messages stay in the user's browser. Revisit only with written approval from Whirlpool's moderators. |
| Release channel | GitHub Releases (signed APK) first; Play internal → production once a developer account exists (new personal accounts need 12 testers for 14 days of closed testing, a privacy policy and the data-safety form); F-Droid after v1.0. |
| Analytics / crash | None by default; optional opt-in ACRA-to-email or a self-hosted Sentry later. |
| Minimum Android | 26 (8.0). |

---

## 6. Source, builds and releases (the public source must always be what ships)
- Public GitHub repo from day one with the licence, `fixtures/`, `tools/capture/`, this plan. `reference/` git-ignored.
- **CI on every push/PR** (GitHub Actions, `ubuntu-latest`, `actions/setup-java` 17, `gradle/actions/setup-gradle`): `./gradlew lint testDebugUnitTest assembleDebug`. Parser tests run against `fixtures/`, so markup drift fails CI first.
- **Releases only from tags**: `release.yml` on `v*` builds signed release APK + AAB with a keystore from GitHub Secrets (`SIGNING_KEYSTORE_B64`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`, `SIGNING_STORE_PASSWORD`), `versionName`/`versionCode` from `app/version.properties` (bumped in the release PR; the tag must match; `v1.2.3` → `10203`), changelog from `CHANGELOG.md`, attaches APK + SHA-256 to a GitHub Release. Developer-machine builds are never published.
- **Play** from the same workflow (`r0adkll/upload-google-play`, signed in keylessly with Workload Identity Federation; Dallin's Cloud organisation forbids service-account keys) to the closed-testing `alpha` track; production once Play grants it. Play App Signing enrolled; CI checks `targetSdk` against the current Play deadline.
- **F-Droid** builds from source itself (strongest proof binary = source): no proprietary deps, `fastlane/metadata/android/en-AU/` listing, reproducible Gradle build.
- Branch protection on `main` (PRs + green CI); Renovate/Dependabot for AGP, Kotlin, Compose.
- Secrets hygiene: no API key in repo or CI; fixtures captured with a throwaway key contain only the capturing user's public name/ID and watched-thread titles.

---

## 7. Development environment (WSL2 on Dallin's machine)
- JDK 21 is installed; no Android SDK. Install command-line tools to `~/Android/Sdk`, then `sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"`, set `ANDROID_HOME`, add `local.properties` (git-ignored).
- Devices: the Windows adb at `/mnt/c/Users/dalli/AppData/Local/Android/Sdk/platform-tools/adb.exe` is callable from WSL for install/logcat over USB; or enable Wireless debugging on the phone and `adb connect` from WSL's own platform-tools. No emulator (WSL2 nested virtualisation is slow); the phone is the test device.
- Network: the dev machine cannot reach Whirlpool except through headed Chromium (`tools/capture/`). All live checks of the app happen on the phone. Unit tests never touch the network.
- The API key is in `~/.config/whirldroid/apikey` (mode 600); scripts read it, nothing prints it.

---

## 8. Testing and drift detection
- Parser unit tests on every raw fixture, asserting row counts, IDs, page counts, notebar kinds, quote structure, date parsing.
- Repository tests with MockWebServer replaying fixtures (envelope handling, 403/509 mapping, Cloudflare page detection).
- Room migration tests from v1 onward.
- A `tools/capture` run per release to refresh fixtures; CI diff of parser counts flags drift. In the app, a 200 page with zero parsed rows shows a "Whirlpool changed its layout" error with "Open in browser" and "Save page for diagnosis".
- Manual device checklist per phase (login, each list source, thread paging, notifications with the phone dozing).

---

## 9. Phases
**Phase 0 — done** (2026-10-20): fixtures, API behaviour, 2.14.12 inventory, this plan.

**Phase 1 — foundation (≈1.5 weeks)**
1. Day 0 device spike: minimal OkHttp app calling `get=user` and fetching `/forum/92` and a thread with the chosen User-Agent; record status/headers. Decide the HTTP client from the result (see 2.1).
2. Repo, licence, Gradle multi-module skeleton, CI, tag-driven signed release of an empty app (proves the pipeline).
3. `:core:network` with typed errors; `:core:scraper` parsers + tests against fixtures; `:core:database`; `:core:data` for forums, latest, watched, news.
4. Setup wizard (API key → `get=user`), settings scaffold, theme.

**Phase 2 — done** (2026-10-21, v0.1.0 on GitHub Releases): read-only parity (HANDOFF.md "Phase 2" lists what was built and decided). Was: **read-only parity (≈2.5 weeks)** → **milestone v0.1 on GitHub Releases**
Drawer + homepage; Forums with favourites and private-forum handling; Thread lists for forum/watched/recent/popular/latest/search with groups, paging, sticky/closed/moved/deleted styling, Unread/All tabs; Thread view with inline-var paging, block renderer, notebars (public/parts), quote navigation, post filters, user's posts; search (list view); News; User profile; deep links; opening-position rules; all read-side settings.

**Phase 3 — done** (2026-10-21, v0.2.0, then v0.2.1 with a link fix): actions and notifications (HANDOFF.md "Phase 3"). Was: **actions and notifications (≈1.5 weeks)** → **v0.2**
Watch/unwatch/mark read with Undo; WorkManager polling, channel, actions, permission prompt, boot re-arm (WorkManager handles it); New thread via Custom Tabs (Reply and Edit landed in Phase 2); night-mode schedule; back-to-list and auto-mark-read options. (Post font size landed in Phase 2.)

**Phase 4 — done** (2026-10-21/22, v1.0.0 and v1.0.1 on GitHub; Play closed test in review; F-Droid and community posts wait for Whirlpool's moderators): HANDOFF.md "Phase 4 tasks" and "Where Phase 4 left things". Was: **polish and release (≈1 week)** → **v1.0**.
Accessibility (TalkBack, font scaling), landscape, Material You, store listing text/screenshots in `fastlane/`, Play internal track, F-Droid submission, post in the Whirlpool API thread and ask to be listed on the third-party clients wiki page.

**Parked until Whirlpool's moderators approve** (see Decisions, "Web session in the app"): any in-app web login, and everything it would unlock (private forums, PMs, mark-read-to-here, native posting). Ask in the API thread or of the moderators before any work on it.

**Backlog** (not in v1): app widget, tablet two-pane, image loading once a fixture exists, and the archive idea below.

**Idea, undecided (Dallin, 2026-10-23): archive reader and in-app post filters.** `/archive/<id>` is the whole thread on one public page (no login; `thread_archive_numeric.html`: 48 posts, about 3.3 KB each, every post with `data-userid`, name and `.usergroup`). Two uses of one download:
- *Archive reader*: read a small thread on one page (scroll instead of paging; offline once cached).
- *Filters in the app*: since v1.1.2, "Posts by me / OP / moderators / reps" and "user's posts" open the browser, because Whirlpool's `ux=`, `um=`, `ur=` views need a login (checked 2026-10-23). From the archive, Whirl could filter by user id itself.

Limits that decide whether it is worth it: size grows with the thread (10 pages ≈ 0.7 MB, 50 pages ≈ 3.3 MB, the 2,290-page Geforce RTX thread ≈ 150 MB), and the long threads where "Posts by OP" matters most are the ones too big to fetch; one archive pull per filter also strains "about the rate a person browses" (§5). So: a page limit (say 50), with the browser beyond it. The archive omits moderator-hidden posts (numbering gaps; harmless for filtering) and has small parser differences (§2.3: dates in `span[itemprop=datePublished]`, bodies in `span[itemprop=text]`). Moderator and rep filters need a fixture with such posts to confirm how `.usergroup` marks them. Not started.

---

## 10. Risks and open questions
- **OkHttp vs Cloudflare** is the one unknown that can change the architecture; it is the first task of Phase 1.
- **`get=recent` shape** unverified (empty for this account). Fallback feed exists.
- **No deleted-post fixture**; parser must tolerate a `replyblock` without `.date`/`.usergroup`.
- **Markup drift** will recur; mitigated by section 8.
- **Name/licence**: contacting gregdev is a courtesy and avoids confusion on Whirlpool; not a blocker.
- **Play account**: closed-testing requirement delays Play availability; GitHub Releases/Obtainium cover early users.
- **Posting** stays in the browser, users logged in there separately; in-app posting has historically been a no-go with Whirlpool and needs moderator approval first (Decisions).

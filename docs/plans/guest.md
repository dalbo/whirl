# Idea: browse as a guest (no API key)

Status: **idea, not scheduled** (Dallin, 2026-11-07). **Before any work, ask Whirlpool's moderators** (see "Gate" below).

## Why
Most of Whirlpool is public, and Whirl already reads most screens from public pages; the API key only serves the
personal parts. A guest mode would let people try Whirl before making an account and getting a key. It would also
help app-store reviewers, and suit people who only read.

## What a guest gets and loses

| Screen | Source today | Guest |
|---|---|---|
| Forums list | API `get=forum` (`ForumRepository.refresh`) | From the forum index `/` with `ForumIndexParser` (written and tested, not used yet; PLAN.md §2.3) |
| Forum thread lists, groups, paging | Scraped `/forum/<id>` | Yes; no unread overlay (that is API `get=threads&forumids=`) |
| Popular, Search | Scraped | Yes |
| Threads (quotes, notes, opening rules) | Scraped `/thread/<id>` | Yes; no Watch / Mark read buttons |
| User profiles | Scraped `/user/<id>` | Yes |
| Archive reader (v1.8) | `/archive/<id>`, public | Yes |
| News | API `get=news` | Unknown: needs a raw fixture of the public news page through `tools/capture` |
| Latest activity | API `get=threads` | No (or Popular instead) |
| Watched, Recent, notifications | API, per account | No |

## Gate: Whirlpool's moderators
The API terms (`/wiki/wp_api`) cover keyed use. Nothing found on apps reading public pages (PLAN.md §2.1). Today every
page Whirl fetches comes from someone with an account and a key; guest mode would add anonymous traffic. Add this
question to the conversation already waiting on the moderators (`docs/community.md`, F-Droid). Ask whether a keyless
read-only mode is acceptable, and whether they want limits (caching, request rate, a User-Agent marker).

## Sketch of the work
- **Setup**: `SettingsRepository.isSetupDone` needs an API key today. Add a "Browse as guest" choice on the setup
  screen (a `guest` setting, or setup done with no key), and "Add API key" in the drawer and Settings for later.
- **Drawer and home page** (`app/.../navigation/Drawer.kt`): hide Watched, Recent and News (unless scraped); the home
  page setting falls back to Forums or Popular.
- **Forums**: when there is no key, `ForumRepository.refresh` reads the index page. Private forums come from
  `KNOWN_PRIVATE` plus anything the index omits.
- **Thread and list actions**: hide Watch, Unwatch, Mark as read and "Jump to last read" for guests; `WatchQueue` is
  never called.
- **Notifications**: settings hidden; `NotificationScheduler` cancels the alarm and the work when there is no key.
- **Errors**: any API call made without a key is a bug. Make it fail loudly in debug builds, not show "Bad API key?".
- **Tests**: setup with no key, drawer contents, forums from the index fixture, and no API request in guest mode
  (MockWebServer).

## Open questions
- Is the news page public, and does its markup suit a parser?
- Does a guest get the same pages as a signed-out browser? The fixtures were captured without a web login, so
  probably yes.

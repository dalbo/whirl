# Community posts (Phase 4 task 7)

Dallin posts these himself, logged in to Whirlpool in the browser, **only after the moderators have answered the note in
`docs/play-console.md`** and in line with what they said. Whirl never posts (no web session, PLAN.md §5). Links found
2026-10-21 on `/wiki/wp_api` (captured with `tools/capture`).

| Where | Link |
|---|---|
| Whirlpool API discussion thread | <https://forums.whirlpool.net.au/forum-replies.cfm?t=1220737> |
| 3rd Party Clients and Extensions wiki page | <https://whirlpool.net.au/wiki/whirlpool_clients_and_extensions> |
| Whirldroid thread (gregdev), in Android › Apps | <https://forums.whirlpool.net.au/thread/97q1vrm9> |

## 1. Reply in the Whirlpool API thread
> I've written Whirl, a free, open-source (MIT) Android reader for Whirlpool that picks up where Whirldroid left off.
> It uses the API with each user's own key for watched threads (with notifications), news, recent/latest activity and
> watch/mark-read/unwatch, and reads public forum and thread pages for display at about the rate a person browses. It
> never logs in to the site: posting, private forums and PMs open in the browser. No ads, analytics or revenue.
>
> Notes from building it, in case they help other API users: dates come as `2026-10-19T20:39:50+1000` (no colon in the
> offset); `get=watched` without `watchedmode` returns a different set from both modes, so pass it explicitly;
> `get=whims` now returns the bare envelope. Source: https://github.com/dalbo/whirl. Feedback welcome here or on
> GitHub issues.

## 2. Ask to be listed on the wiki page
The wiki is edited by staff or trusted members; ask in the API thread (in the post above, add the line below) or of a
moderator, whichever they suggested:
> Could Whirl be added to the 3rd Party Clients and Extensions page? Suggested entry: **Whirl** (Android) – free,
> open-source reader with watched-thread notifications; uses your API key; posting opens in the browser.
> https://github.com/dalbo/whirl (Google Play/F-Droid links to follow).

## 3. Courtesy note to gregdev in the Whirldroid thread
> Hi Greg, thanks for Whirldroid; I used it for years. Since it hasn't been updated since 2021 I've written a new
> client, Whirl (https://github.com/dalbo/whirl, MIT). It's a fresh codebase in Kotlin/Compose with a different name
> and package so it can't be confused with Whirldroid, and Whirldroid's old `whirldroid-thread://` links open in it.
> I used Whirldroid's behaviour as the reference for what the app should do but haven't used any of its code. Happy to
> credit you however you'd like, or to change anything you're not comfortable with.

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

## 4. v1.1 reply in the Whirldroid thread (draft, 2026-10-23; not yet posted)
In `97q1vrm9` (Android › Apps), answering the feedback since the v1.0 post. Plain text. Drop the tester paragraph if the moderators have not answered yet. Update the release line to the newest tag.

> Whirl 1.1 is out (now 1.1.3). Thanks for all the feedback, most of it is in:
>
> - Mark as read: a "Mark as read" button at the end of the last page of a watched thread, hidden if you use "Mark as read when opened". (BadFish)
> - Posts by OP: added to the post filter. The filters (me, OP, mods, reps) open in your browser: Whirlpool only shows those views to signed-in users. (BadFish)
> - Transitions: Settings > Screen animations turns them off, including the back swipe. (BadFish)
> - Favourites were already there: long-press a forum in Forums to add it; favourites show at the top. (BadFish)
> - Watched refresh: once when you open the app or come back to it, not every time you return from a thread. (kudabird)
> - Mark as read when opened now works however you open a watched thread, which fixes threads staying unread on the site. (kudabird)
> - Replying in your browser: Settings > "Open Whirlpool pages in the browser app". (Chris)
> - Looks: Settings > Layout (Classic, or Cards: Jkill's design, thanks Jkill!) and Colours (from your wallpaper, Whirl blue, Whirlpool, Whirldroid, or Jkill's Silver & ember). (crunchynuts) Each is a small file in the app, so new ones are easy if anyone wants to design one.
>
> Not yet: marking read up to a particular post (BRK) needs Whirl to log in to the site, which I'm not doing without the mods' OK. iOS (FERRARIF1): sorry, not planned.
>
> Download: https://github.com/dalbo/whirl/releases/latest (APK). It's also in closed testing on Google Play. Google needs 12+ testers for 14 days before it can go public, so if you're in Australia and want to help, use the same Google account as your phone and:
> 1. Join https://groups.google.com/g/whirl-testers-group
> 2. Opt in at https://play.google.com/apps/testing/au.howesthat.whirl
> 3. Install from https://play.google.com/store/apps/details?id=au.howesthat.whirl
>
> If you already installed the APK from GitHub, uninstall it first. cevad, you're first in line.


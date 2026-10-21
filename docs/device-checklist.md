# Manual device checklist

Run on a real phone before tagging a release (PLAN.md §8). Debug builds install beside the release app as `au.howesthat.whirl.debug`. Note the build, the phone and its Android version, and the network (Wi-Fi or mobile data) with the result.

## v1.0 (polish)
**TalkBack** (Settings → Accessibility → TalkBack; or hold both volume keys if the shortcut is on)
- [ ] Watched, a forum and a thread: every button is read with a name (menu, back, search, refresh, more options, previous/next part).
- [ ] On a thread row, a forum and a post, swipe up then right (or three-finger tap) → Actions lists the long-press menu entries, and choosing one works.
- [ ] Section headers (forum names in Watched/Popular, sections in Forums, days in News, Settings sections) are read as headings; swiping up/down with "Headings" navigation jumps between them.
- [ ] A watched row with unread posts reads "N unread posts".
- [ ] Swiping to the next page of a forum or thread announces "Page N of M"; the Go to page slider reads "Page N of M" as it moves.

**Large text and display size** (Settings → Display → Font size at maximum, Display size at maximum)
- [ ] Watched, Forums, a forum with groups, a thread with a moderator note and parts, News, Settings and About: no text cut off or overlapping; names in post headers stay readable.
- [ ] A watched-thread notification at maximum font size still shows the title and the buttons.

**Colours**
- [ ] Settings → Use wallpaper colours off: Whirl's blue scheme; on: the wallpaper's. Quotes stay blue/brown and readable in light and dark either way.

**Landscape**
- [ ] Rotate on Watched, a forum, a thread, Settings: nothing cut off; the thread's posts sit in a centred column; the back arrow lines up with other screens; swipe pages and pull to refresh still work; rotating keeps the page you were on.

## v0.2 (actions and notifications)
**Watch actions** (use a thread you are happy to watch, e.g. `3pxj858k`)
- [ ] Long-press a thread in a forum: Watch → "Thread watched · Undo"; Undo, then pull to refresh Watched (All): not there. Watch again and let the snackbar go: it appears in Watched (All).
- [ ] Watched: long-press → Unwatch → Undo puts the row back in place; Unwatch without Undo removes it for good (refresh after 15 s).
- [ ] Watched (Unread): Mark as read moves the thread out of Unread; with "back to list after marking read", Mark as read in the thread view returns to Watched.
- [ ] "Mark as read when opened": opening an unread thread from Watched marks it read.
- [ ] Offline (`cmd connectivity set-package-networking-enabled false <pkg>`): an action rolls back with "Could not …".

**Notifications**
- [ ] Settings → turn alerts on: Android asks for permission; refusing leaves them off; "blocked" line asks again or opens the system settings.
- [ ] With unread watched threads, a check posts one notification (title, "N new replies in M threads" or the thread's title, inbox lines, Mark read / Unwatch). Tapping opens Watched.
- [ ] The same replies do not alert again; a new reply does. Nothing unread cancels it.
- [ ] Mark read / Unwatch buttons remove the notification and update Watched.
- [ ] Dozing: app in the background, screen off, `adb shell dumpsys deviceidle force-idle`; the check is deferred and runs in the next maintenance window (`dumpsys deviceidle step`), WorkManager's periodic run survives a reboot.
- [ ] Changing the interval keeps one scheduled job (`dumpsys jobscheduler | grep WatchedCheckWorker`).

**Other**
- [ ] Forum overflow → New thread in browser opens the new-thread form (logged in to Whirlpool in the browser).
- [ ] "Dark at night": set Night starts (Settings → Theme) a minute or two from now; the theme switches while the app is open. The times show in the phone's 12/24-hour format.

## v0.1 (read-only parity)
**Start**
- [ ] Fresh install: setup wizard → API key → "Logged in as NAME (#ID)" → theme → lands on the start screen chosen in Settings (default Forums).
- [ ] Drawer shows Industry news, Recent, Latest activity, Watched, Popular, Forums, Settings, About; About links open in the browser.
- [ ] Change the start screen in Settings, relaunch: the app opens there.

**Lists**
- [ ] Forums: sections load; long-press adds/removes a favourite (Favourites section at the top); a private forum (Feedback, Private Messages) opens in the browser.
- [ ] Forum (e.g. Choosing an ISP): swipe between pages; "Page N of M" picker jumps; group picker on a forum with groups (Desktops, forum 7); sticky/closed/moved/deleted rows look right; search icon searches within the forum.
- [ ] Watched: Unread and All tabs; "N unread" in the title; unread counts.
- [ ] Recent (may be empty), Latest activity, Popular (forum sections) load; tapping a section header opens the forum.
- [ ] Long-press a thread row: jump to last post, open in browser, go to forum.
- [ ] Pull to refresh everywhere; "Updated N ago" shows.

**Thread view**
- [ ] Opens at page 1 from a forum; at the last read post from Watched; at the last page from Recent (unless set to the top).
- [ ] Swipe pages; first/last/go-to page; last page of a very long thread.
- [ ] Quotes: tap an attribution for a post on the same page (scrolls) and on an earlier page (moves there via the reply locator).
- [ ] Links in posts: thread, forum, user, whrl.pl open in the app; other links in the browser; spoilers reveal on tap.
- [ ] Moderator note (thread `9nznpky9`, last page) and parts links (sticky `3mnkq4v9`); hidden posts show the reason.
- [ ] Filters: my posts, moderators, reps; long-press a post → that user's posts, user info, reply in browser.
- [ ] Post text size setting changes the text.
- [ ] Airplane mode: a page opened before still shows, with "Updated N ago"; a new page shows the error with Retry and Open in browser.

**Other**
- [ ] Industry news grouped by day; an article opens in the browser.
- [ ] Search from Forums (site-wide); Next/Previous on a common term.
- [ ] User profile from a post.
- [ ] Deep links: enable under App info → Open by default, then open `https://forums.whirlpool.net.au/thread/3pxj858k` from another app; `adb shell am start -a android.intent.action.VIEW -d <url>` works too.
- [ ] Dark theme and light theme.
- [ ] Repeat a few list and thread loads on mobile data (Cloudflare).

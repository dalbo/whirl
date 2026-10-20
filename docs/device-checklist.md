# Manual device checklist

Run on a real phone before tagging a release (PLAN.md §8). Debug builds install beside the release app as `au.howesthat.whirl.debug`. Note the build, the phone and its Android version, and the network (Wi-Fi or mobile data) with the result.

## v0.1 (read-only parity)
**Start**
- [ ] Fresh install: setup wizard → API key → "Logged in as NAME (#ID)" → theme → lands on the start screen chosen in Settings (default Forums).
- [ ] Drawer shows Industry news, Recent, Latest activity, Watched, Popular, Forums, Settings, About; About links open in the browser.
- [ ] Change the start screen in Settings, relaunch: the app opens there.

**Lists**
- [ ] Forums: sections load; long-press adds/removes a favourite (Favourites section at the top); a private forum (Feedback, Private Messages) opens in the browser.
- [ ] Forum (e.g. Choosing an ISP): swipe between pages; "Page N of M" picker jumps; group picker on a forum with groups (Broadband, forum 7); sticky/closed/moved/deleted rows look right; search icon searches within the forum.
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

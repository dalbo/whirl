# Changelog

Release notes for each tag are taken from the matching `## vX.Y.Z` section below by the release workflow.

## v0.1.0
First usable release: read-only parity with Whirldroid 2.14.12. Watching, marking read, notifications and posting from the app come in v0.2; posting and private forums open in your browser.
- Navigation drawer (Industry news, Recent, Latest activity, Watched, Popular, Forums, Settings, About); the start screen setting picks where the app opens. Latest activity (site-wide, from the API) is new and can be the start screen.
- About screen with the version, source code and issue tracker links.
- Thread lists: Latest activity, Recent (honours the history length and "ignore my own replies"), Watched (Unread/All tabs), Popular, and forum pages with swipeable pages, a page picker and a group selector. Sticky, closed, moved and deleted threads are marked; unread threads (from the API) are bold. Long-press a thread to jump to its last post, open it in the browser or go to its forum. Private forums list through the API and open threads in the browser.
- Forums screen with a Favourites section (long-press a forum to add or remove it). Private forums are marked and open in the browser.
- Thread view: swipe between pages, jump to the first, last or any page; posts are rendered natively with coloured quotes, tap-to-reveal spoilers and lists. Tapping a quote goes to the quoted post, on another page if needed. Links to Whirlpool threads, forums, users and whrl.pl open in the app. Filter by your posts, moderators, reps or one user; long-press a post to reply or edit in the browser, see the poster, or see their posts. The post text size setting applies. Thread pages are cached for reading offline (the last 50 opened).
- Industry news, grouped by day; articles open in the browser.
- Search thread titles across Whirlpool (from Forums) or within a forum or group (from the forum's search button).
- User profiles: about lines and recent activity.
- Whirlpool links (threads, forums, users, archive) can open in Whirl: turn them on under App info → Open by default. Whirldroid's old thread links work too.

## v0.0.2
- Pipeline proof (v0.0.1's run failed at the signature check; nothing was published from it).

## v0.0.1
- Empty app that proves the tag-driven signed release pipeline. Not useful yet.

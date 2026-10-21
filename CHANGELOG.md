# Changelog

Release notes for each tag are taken from the matching `## vX.Y.Z` section below by the release workflow.

## Unreleased
- Report a post to Whirlpool's moderators from its long-press menu (opens Whirlpool's report form in your browser), and report a concern to the developer from About. Whirl's child safety standards are published at https://dalbo.github.io/whirl/child-safety/.

## v1.0.0
First stable release: polish, accessibility and large screens on top of v0.2.
- New icon: a speech bubble with a spiral, also on the splash screen and in notifications, with a themed-icon version for Android 13+.
- Bigger text: a new "Text size" setting (Normal, Large, Larger, Largest) scales all of Whirl's text on top of Android's font size, and posts at the "Normal" post text size are 17 sp again, as in Whirldroid (they were 14 sp, smaller than "Smaller"). The post sizes "Large" and "Larger" were labelled the wrong way round.
- Watched threads refresh every time you open the app or come back to the list (it used to show the saved list for up to 10 minutes until you pulled to refresh).
- Fixed: the moderator note kept a pale orange background with hard-to-read links in some colour schemes; it now uses the theme's colours.
- A thread split into many parts shows one row (previous part, "Part N of M" with a list of every part, next part) instead of a button per part.
- "Dark at night" now has settings for when the night starts and ends (they could not be changed before; 22:00 to 07:00).
- TalkBack: a thread's, forum's or post's long-press menu is also offered under TalkBack's actions; unread badges read as "N unread posts"; section headers are headings; swiping pages announces the new page; the page slider reads "Page N of M".
- Large text: post headers keep the poster's name readable (the user group moved to the line below), and the notification interval choices wrap instead of running off screen.
- Landscape and large screens: posts keep a readable width (centred, at most 640 dp wide) and the thread's top bar no longer doubles the camera-cutout margin.
- "Use wallpaper colours" setting (Android 12+, on by default): turn it off for Whirl's own blue colours.
- Quotes of other posters use a darker brown in light theme and a lighter one in dark theme, so they meet the 4.5:1 contrast guideline (Whirldroid's single brown fell just short on both).
- The status bar icons follow Whirl's theme, so they stay visible when Whirl is dark and the phone is light (and the other way round).

## v0.2.1
- Fixed: opening a Whirlpool thread link from another app did nothing while a thread was already showing in Whirl.
- Whirl no longer declares the foreground-service permission it never used.
- Privacy policy published at https://dalbo.github.io/whirl/privacy/ (no data is collected by the developer; the app talks only to Whirlpool).

## v0.2.0
Actions and notifications.
- Watch, mark as read and unwatch from a thread's long-press menu and from the thread view's menu. Each shows "Undo" for a few seconds; nothing is sent to Whirlpool until the Undo offer closes, and a failure puts things back and says so.
- Watched-thread notifications: checked in the background at the interval you choose (15 minutes to 24 hours), they list your unread watched threads and alert once per new reply. "Mark read" and "Unwatch" act on the listed threads; tapping opens Watched. Honours "ignore my own replies". Turning them on asks for notification permission (Android 13+); a blocked state says so and leads to the system settings. Sound and vibration are set per channel in Android's notification settings.
- "Mark as read when opened" and "back to the list after marking read" (Watched settings) now work.
- "New thread in browser" on forum screens.
- "Dark at night" switches theme at the start and end of the window while the app is open.

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

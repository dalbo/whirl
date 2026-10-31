# Changelog

Release notes for each tag are taken from the matching `## vX.Y.Z` section below by the release workflow.

## Unreleased
- Watched threads: a "New posts" line marks where the unread posts start, like the orange line on Whirlpool, as asked for by a tester. It stays put after the thread is marked read, and also shows when a watched thread is opened from Recent, a link or elsewhere. The page picker (tap the title) gains "First unread post (page N)".

## v1.4.1
- About: "Whirl on Whirlpool" opens Whirl's thread in the app, and "Google Play" opens Whirl's Play Store page, to rate or share it.

## v1.4.0
Tablets and other wide screens, as an experiment.
- Settings › Layout › "Threads beside the list (experimental)": on tablets, unfolded phones and other wide windows (at least 840 dp wide and 480 dp tall), a thread opens next to the list it came from, with its row highlighted. Choosing another thread replaces it; Back steps back through threads you reached by links, then to the list. Watched, Recent, Latest activity, Popular, a forum, search results and a user's page work this way. Off by default; phones, including in landscape, are unchanged.
- Under the hood, the app moved to Navigation 3, which makes the side-by-side layout possible. Nothing else should look or behave differently; if a notification opens Watched while you are deep in another part of the app, Back now returns to where you were.

## v1.3.0
Find in list, asked for by a tester.
- A search icon on Watched, Recent, Latest activity, Popular, a forum's threads, Forums and Industry news filters what the list shows as you type: every word must appear in the thread title or the forum or group name (case and accents ignored). Forums also matches section names; Industry news matches titles, sources and summaries. On a forum it filters the page you are on.
- "Search Whirlpool for …" under the box searches the site for the same words: within the forum on a forum page, site-wide elsewhere. On Forums and a forum page this replaces the old search icon.
- On Watched's Unread tab, when nothing unread matches, "Show N in All" switches to All with the same filter.
- The filter stays while you open a thread and come back; the back arrow or Back clears it.
- With a keyboard: Ctrl+F or / opens it, Esc closes it.

## v1.2.0
Tester feedback from the Whirl and Whirldroid threads.
- Recent threads: pick how far back to look ("Last 7 days") at the top of the screen, now up to 365 days. The setting moved there from Settings.
- Watched-thread notifications follow what you do in the app: a thread read, marked read or unwatched in Whirl leaves the notification straight away, and it goes once nothing is unread. A dismissed notification only comes back for a new reply.
- Settings › Notifications › Notification style: one notification for all watched threads (as before), or one per thread, grouped, each with its own Mark read and Unwatch. Tapping a thread's notification opens that thread; only threads with a new reply make a sound.
- Settings › Notifications › "Keep the notification up until its threads are read": tapping the notification opens Watched without removing it.
- "Open threads in the browser" is now "Open public forum threads in the browser"; private forum threads always open in the browser.
- The Cloudflare block message mentions that it happens more often outside Australia or on a VPN.

## v1.1.3
- With "Screen animations" off, swiping back no longer animates either: the back gesture's own animation (the page shrinking into a card over the previous screen) is turned off too.

## v1.1.2
- "Posts by me", "Posts by OP", "Posts by moderators", "Posts by reps" and a post's "user's posts" open the filtered page in your browser. Whirlpool shows filtered views only to signed-in users, and Whirl does not sign in to the site, so in the app they always showed "This page needs a Whirlpool login".
- Fixed: trying one of those filters marked the thread's forum as private, and from then on every thread in that forum opened in the browser. Forums marked this way are cleared once when you update.

## v1.1.1
- "Mark as read when opened" marks a watched thread read however it is opened, not only from Watched and only when Watched's saved list showed unread posts. Since v1.1.0 that list can be minutes old, and reading in Whirl does not move your read position on Whirlpool, so threads could stay unread on the site.
- The "Mark as read" button at the end of a watched thread is hidden when "Mark as read when opened" is on.

## v1.1.0
Tester feedback from the Whirlpool thread, and a choice of looks.
- Settings › Layout and Colours: the Classic layout or Cards (rounded cards, boxed quotes, tinted unread rows; design by Jkill), with colours from your wallpaper (the default), Whirl blue, Whirlpool (the website's indigo and greys), Whirldroid, or Silver & ember (design by Jkill). These replace "Use wallpaper colours"; if you had it off, you get Whirl blue.
- A "Mark as read" button at the end of the last page of a watched thread, for when auto mark as read is off.
- "Posts by OP" in the post filter, for catching up on long threads.
- Watched refreshes once each time you open the app or come back to it, no longer after every thread (slow on older phones and connections).
- "Screen animations" setting: turn off the fade between screens.
- "Open Whirlpool pages in the browser app": replies, private forums and "Open in browser" open in your browser instead of a tab inside Whirl.
- Layouts and colours are JSON files in the app (`docs/styles.md`), each checked for 4.5:1 text contrast in light and dark.

## v1.0.1
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

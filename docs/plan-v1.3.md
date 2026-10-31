# Plan: find in list (v1.3) and tablet two-pane (v1.4)

Status: agreed with Dallin 2026-10-31, after an architect review. Two features Dallin chose for the next versions: a
tester's request to search watched threads, widened to most lists, and the tablet two-pane layout from the backlog
(PLAN.md §9). Image loading stays in the backlog (Whirlpool has no inline images; loading linked images is a later idea).

Split (agreed): **find in list ships alone as v1.3.0**; two-pane follows as **v1.4.0** after a short Navigation 3 spike
(§2.5). Find in list does not depend on the navigation change. Dallin has no tablet: two-pane is checked on an emulator
and ships **experimental** (§2.2), so the bar for v1.4.0 is that phones are not broken.

---

## 1. Find in list

### 1.1 What it is
A filter over what a list already shows, on the phone, with no network request. Typing narrows the rows to those whose
visible names match. It is not Whirlpool search, which stays as it is (`SearchRoute`, title search in list view).

The action is called **"Find in list"**. On thread lists and Forums, while the query is not blank, a **"Search
Whirlpool for “x”"** chip sits pinned directly under the field (not as the last row, where 20+ matches would hide it),
so one box covers "it's not here, look on the site". `SearchRoute(query = …)` already searches on open
(`SearchScreen.kt:62, 85, 98`).

The keyboard's action key is **Done** (hides the keyboard). It does not start a site search: people press it to dismiss
the keyboard.

### 1.2 Where, and what is matched
| Screen | Matched fields | "Search Whirlpool" chip | Notes |
|---|---|---|---|
| Watched (Unread and All tabs) | thread title, forum title | site-wide | Filters the tab showing. See §1.5 for the Unread → All hint. |
| Recent | title, forum title | site-wide | Within the chosen days. |
| Latest activity | title, forum title | site-wide | |
| Popular | title, forum title (section header) | site-wide | |
| Forum (one forum's thread list) | title, group name | scoped to the forum/group | The current page only; the empty text says "on this page". Private forums: no chip (they hide search today, `ForumScreen.kt:199`). |
| Forums | forum title, section title | site-wide | **Forums holds the app's only site-wide search today** (`WhirlApp.kt:142`); the chip keeps it. |
| News | title, source, blurb | none | Site search is threads only. |

Not in scope: thread pages (posts), the user screen's recent activity, Whirlpool search results, Settings.

A forum or section title match keeps every row under that header; otherwise rows are kept one by one and a header is
dropped when none of its rows match. The "N unread" in the Watched title, the tab badge and notifications ignore the
filter.

### 1.3 Matching rules
`TextQuery` (pure Kotlin, `:core:model`, unit-tested):
- Split the query on whitespace; **every word must appear** somewhere in the row's fields (any order, any field).
- Case-insensitive (`Locale.ROOT`), accents folded, so "cafe" finds "Café".
- Substring, not whole-word: "nbn" finds "NBN", "#nbn", "nbn's". A word made only of punctuation is ignored.
- The haystack is the displayed text. (Thread titles are unescaped by the API mapping and Jsoup; API forum names, group
  names and news fields are not unescaped, `WhirlpoolApi.kt:109, 152-153, 177`; matching what is shown is still right.)
- Blank query = no filter.

**Highlighting** matched words in titles: accent folding by NFD changes string length, so ranges found in the folded
text do not map back. Fold per character with an index map, or ship without highlighting. Optional; decide in the PR.

### 1.4 UI
- A **search icon** in the top bar of each screen above. On Forum and Forums, the existing Whirlpool-search icon becomes
  this one (its job moves to the chip), so the bar never has two magnifiers.
- Tapping it shows Material 3's **search app bar** component from the BOM (not a hand-built `TextField` in a
  `TopAppBar`, which clips at 200 % font): focused, keyboard up, back arrow (close and clear), clear button. Sub-bars
  (Watched tabs, Recent days, forum group and page pickers) stay under it.
- Hardware keyboard: Ctrl+F and `/` open it, Esc closes it. A freshly opened screen has nothing focused, so Compose key
  modifiers would not see the keys: route them from `MainActivity` (`onKeyShortcut`/`dispatchKeyEvent`) to the current
  screen through a small shared signal, and list them in `onProvideKeyboardShortcuts` (Meta+/ help).
- System back closes the bar first, via a `BackHandler` **enabled only while the field has focus or this screen is the
  top of the back stack**, so in two panes (v1.4) Back still reaches the thread.
- TalkBack: the field is labelled "Find in <screen>"; a polite live region reads "N matches", debounced ~500 ms.
- Empty result has its own branch in `ThreadListBody` (today any all-empty list shows the screen's `emptyText` and
  browser button, `ThreadListUi.kt:231-238`): "Nothing here matches “x”" plus the chip.

### 1.5 State
- The query is kept in the ViewModel's **`SavedStateHandle`** (null = bar closed), so it survives rotation, a trip into
  a thread and back, and process death. Closing the bar clears it.
- No special "clear when leaving via the drawer" rule: Navigation keeps drawer destinations' ViewModels and the start
  destination is never popped, so the ViewModel cannot tell. A restored filter is visible in the open bar, which is
  honest.
- Pull to refresh, page swipes and tab changes keep the query.
- **Watched Unread → All hint**: Watched observes only the visible tab (`DrawerLists.kt:369`). While filtering with no
  Unread matches, also collect `repo.watched(false)`: if the All list is cached, offer "Show N in All"; if it was never
  fetched, offer "Look in All" without a count.

### 1.6 Code shape
- `:core:model` `TextQuery`.
- `:core:ui`: the find bar wrapper (search app bar + chip slot), the shortcut signal, the optional `highlight()`.
- `:feature:threadlist`:
  - `ListViewModel` gains `query` (SavedStateHandle-backed) and a pure `filterSections(sections, query)` (unit-tested).
  - Recent, Latest, Popular, Watched combine `query` into their `state`.
  - **Forum has no single `state`**: rows come from per-page `page(n)` flows created in composition
    (`ForumScreen.kt:124-128, 243`) plus `privateState` (`:120`); combine the query into `page(n)`.
  - `ThreadListBody` takes the query (empty branch, highlight).
- `:feature:forums`: `forumSections` is built in composition with a localised "Favourites" title (`ForumsScreen.kt:123`);
  filter in `remember(query, sections)` there (about 100 rows, cheap), with `filterForums` pure and tested.
- `:feature:news`: `filterNews` pure and tested, applied in the ViewModel's `state`.
- Strings: `find_in_list`, `find_matches` (plural), `find_none`, `find_show_in_all`, `find_look_in_all`,
  `search_whirlpool_for`.

### 1.7 Tests
- Unit: `TextQueryTest` (case, accents, words across fields, punctuation-only words, blank); `filterSections`,
  `filterForums`, `filterNews` (header match keeps section, empty sections drop, order kept).
- Compose (Robolectric): bar opens from the icon and from a hardware Ctrl+F key event; Back closes and clears; "N
  matches" live region; filtered-empty text differs from the list's own empty text; Watched "Show N in All" and "Look in
  All"; Forum chip opens `SearchRoute` with the forum scope and is absent on a private forum; Forums chip opens a
  site-wide `SearchRoute`; the bar at 200 % font (like `ThreadRowAccessibilityTest`).
- Device: Watched with a long All list, a Forum page, Forums, News; 200 % font; TalkBack typing; a Bluetooth or USB
  keyboard if one is to hand.

---

## 2. Tablet two-pane

### 2.1 What it is
On a wide window, a thread opens **beside** the list it came from instead of replacing it: the list on the left, the
thread on the right, the open thread's row highlighted. Narrow windows (every phone in portrait) behave as today.

### 2.2 When it is two panes
- Window width **≥ 840 dp and height ≥ 480 dp**. The height rule keeps phones in landscape (≈ 900 × 400 dp) single-pane.
  Unfolded foldables and tablets in landscape get two panes; most 10" tablets in portrait (≈ 800 dp) stay single, with
  `readableWidth` centring as today.
- Material's default directive decides on width alone, so this needs a custom one: from
  `currentWindowAdaptiveInfoV2()` + `calculatePaneScaffoldDirective(…)`, `copy(maxHorizontalPartitions = 1)` when the
  height rule or the setting says one pane. (`currentWindowAdaptiveInfo()` is deprecated and warnings are errors here.)
- **Settings › Layout › "Threads beside the list on wide screens (experimental)"**, **default off** while it is only
  checked on emulators; turn the default on once a real tablet or foldable has been tried. With it off, Nav3 still
  draws one pane, so phones and tablets look as today.
- Phones in landscape stay single pane (agreed). Trying two panes there is a low-priority experiment for later.
- List pane width 400 dp through the adaptive library's preferred pane width metadata; the thread takes the rest, posts
  capped at 640 dp by `readableWidth`.

### 2.3 Which screens
- **List panes**: Watched, Recent, Latest, Popular, Forum, Whirlpool search results, User (recent activity).
- **Detail pane**: Thread.
- Single pane, full width: News, Forums, Settings, About. Forums | Forum | Thread is a possible later step.
- A list with no thread chosen shows a quiet placeholder ("Choose a thread") in the detail pane.

### 2.4 Behaviour
- Tapping a row **replaces** the thread stack beside the list (pop to the list entry, push the thread). On a phone this
  is the same as today.
- Links inside a thread push onto the detail pane; Back returns to the previous thread, then to the placeholder, then as
  today. (Unconfirmed how the strategy draws several stacked detail entries: spike item.)
- "Go to forum" / a user link from a thread pushes that screen; it becomes the list pane with an empty detail.
- **Watched Unread keeps the open row**: with auto mark-read, tapping a row marks it read at once
  (`ListViewModel.kt:81-83`) and the Room-backed Unread list (`WatchedRepository.kt:91-95`) would drop the row beside
  its own thread. Keep the selected thread in the Unread list until another row is chosen or the screen is left.
- **Highlight**: for each list entry, the selected thread is the thread entry directly above it on the stack, compared
  with the row's `openId` (moved/deleted rows, `ThreadItem.kt:43-46`). A thread reached by a link inside that thread
  keeps the original row highlighted (the row you chose is still what the pane came from).
- **Watched refresh**: today `onShown()` runs on resume (`DrawerLists.kt:407-410`). Nav3 lifecycle is per scene, so in
  two panes Watched stays resumed while threads change beside it. Rule: Watched refreshes once per app visit and on
  explicit refresh; reading beside it does not refresh it (the rows update through Room anyway). Test it.
- **Notifications and links**: a watched-thread notification opens as "select Watched, then push the thread", so on a
  tablet it lands beside Watched, not beside whatever list was showing or full width over News. Other deep links push
  as today; spike what the strategy does with a detail entry that has no list entry before it (expected: full width).
- Resizing (rotation, unfolding, split screen, desktop windows) keeps the back stack and only changes how it is drawn.
- Drawer: stays modal on every size. Its edge swipe is enabled when a top-level list is visible, including as the list
  pane beside a thread (today `gesturesEnabled = isTopLevel`, `WhirlApp.kt:77, 119`, is false whenever a thread is on
  top).
- **"Screen animations" off** must cover both scene changes (`NavDisplay` transition specs) and pane changes inside the
  list-detail scene (the adaptive scaffold's pane motion and pane predictive back): pass no-op pane motion too, or the
  v1.1.3 back-gesture fix regresses on tablets.
- **Insets**: each screen's Scaffold pads both sides today (the thread via `contentWindowInsets … Horizontal`,
  `ThreadScreen.kt:137`). In two panes, consume the inner edge's horizontal insets per pane in the scene
  (`consumeWindowInsets`), not in each screen.
- **Accessibility**: `paneTitle` and a traversal group per pane. Turn off the adaptive library's automatic focus move to
  the detail pane for keyboard users if it loses their place in the list (it has an opt-out); TalkBack order list →
  thread.

### 2.5 Architecture: Navigation 3, after a spike
Navigation Compose 2.x draws one entry at a time, and material3-adaptive's `NavigableListDetailPaneScaffold` runs on
its own navigator, not `NavController`; showing a list and a thread side by side on Nav2 means hosting threads inside
each list destination and handing them over on every resize. **Navigation 3** keeps the back stack as a list of keys
and lets `ListDetailSceneStrategy` (`material3-adaptive-navigation3`) draw trailing entries as list and detail panes.

Versions per the review (re-check at the time; the adaptive artifacts are not in the Compose BOM): navigation3 1.2.0,
lifecycle-viewmodel-navigation3 2.11.0 (lifecycle is already 2.11.0), material3 adaptive 1.3.0.

**Spike (1–2 days, throwaway branch)** before PR A, answering:
1. Drawer stacks with ViewModels and scroll position kept across drawer switches (below).
2. Several stacked detail entries; a detail entry with no list entry before it.
3. No-op pane motion and pane predictive back with "Screen animations" off.
4. `@Serializable NavKey` serialization across `:app` and three feature modules for `rememberNavBackStack` and the
   drawer-stack saver (polymorphic serialization is enforced).
5. Whether Robolectric `@Config(qualifiers = …)` drives `currentWindowAdaptiveInfoV2`; if not, `WhirlApp` takes the
   pane directive as a parameter so tests can set it.
If the spike goes badly, the fallback is Nav2 with the thread hosted inside the list destination on wide windows only,
turned into a pushed `ThreadRoute` on a wide → narrow resize.

**PR A: Navigation 3, no visible change.**
- Routes implement `NavKey`; feature modules gain `navigation3-runtime`. `navigation-compose` removed once unused.
- `WhirlApp`: `NavDisplay` with the saveable-state and ViewModel-store entry decorators; `entryProvider { … }`.
- **ViewModel arguments**: `ThreadViewModel`, `ForumViewModel`, `SearchViewModel`, `UserViewModel` read
  `savedState.toRoute<…>()` today; they take the route by Hilt assisted injection
  (`hiltViewModel<VM, VM.Factory>(creationCallback = { it.create(route) })`). `ThreadViewModelTest` changes its three
  `SavedStateHandle` call sites; Forum, Search and User get new ViewModel tests.
- **Drawer back stacks**: keep today's per-destination saved stacks (`WhirlApp.kt:174-181`). A `TopLevelBackStack` (map
  from drawer route to its stack, saved) plus the **hoisted** ViewModel-store and saveable-state providers, so a stack
  switched away from keeps its ViewModels and scroll position (by default Nav3 clears an entry's state when it leaves
  the displayed list). Unit tests for the stack operations; a compose test that switching away and back keeps the scroll
  position.
- Links: the `LaunchedEffect(link)` logic stays (top-level → select; else push); `isTopLevelRoute` and `RoutesTest`'s
  notification case stay.
- Transitions: none when "Screen animations" is off, including predictive back.
- Device check before merge: `docs/device-checklist.md` navigation rows; predictive back with animations on and off;
  process death (background, `adb shell am kill`) restores stacks and a find query; notification and deep links.
- Measure the APK (release builds are not minified; 2.3 MB today) and re-run the F-Droid reproducibility check
  (`docs/fdroid/README.md`).

**PR B: two-pane**, as §2.2–2.4: the custom directive and setting, list/detail metadata and placeholder,
"replace the thread beside the list", highlight from the stack, Watched's kept row, notification routing, drawer
swipe, no-op pane motion, per-pane insets, pane accessibility.

### 2.6 Tests and checks
- Unit: `TopLevelBackStack` ops; "open from list" replaces the detail stack; highlight derivation per list entry.
- Compose (Robolectric, directive passed in): two panes with placeholder; tapping a row shows the thread beside it and
  highlights the row; a second tap replaces it; Back from the thread shows the placeholder; Back with the find bar open
  in the list pane pops the thread; Watched Unread keeps the open row; the landscape-phone size and setting-off stay
  single pane.
- Device: Dallin's phone with `wm size 1800x2400` + `wm density 240` (≈ 1200 × 1600 dp, as in Phase 4), portrait and
  landscape; `wm size reset` with a thread open (the thread stays on top, single pane); animations off at tablet size;
  a notification tap at tablet size; TalkBack pane order; 200 % font.
- Emulator (no tablet on hand): Android Studio's Pixel Tablet and Pixel Fold AVDs, simplest on the Windows side
  (Windows `adb.exe` already reaches the phone and sees emulators the same way). Fold/unfold and
  resizable-window checks happen there.
- The phone is the release gate: PR A changes navigation for everyone, so its full device pass on the phone matters
  more than the tablet checks.

---

## 3. Order, release and docs
1. **Find in list**: `TextQuery` + Watched first, then the other screens (one or two PRs). Release **v1.3.0**.
2. **Nav3 spike** (throwaway), then **PR A** (Navigation 3, no visible change, full device pass).
3. **PR B** (two-pane). Release **v1.4.0**.

Docs: HANDOFF.md (a v1.3 section; for v1.4, navigation is Nav3; Room unchanged at v2), PLAN.md §9 (backlog: two-pane
done; loading linked images added as a later idea), `docs/device-checklist.md` (find-in-list and two-pane rows),
CHANGELOG and `changelogs/<versionCode>.txt` in en-AU and en-US. For v1.4: `sevenInchScreenshots` and
`tenInchScreenshots` in both store-listing locales (CI diffs them).

## 4. Decisions (Dallin, 2026-10-31)
1. Split releases: find in list v1.3.0, two-pane v1.4.0. Two-pane is experimental (setting off by default) and checked
   on emulators; phones must not break.
2. "Search Whirlpool" chip everywhere it applies: thread lists and Forums, forum-scoped on a forum page. (Not on News:
   site search finds threads, not news.)
3. Phones in landscape stay single pane; dual there is a possible later experiment.
4. Keep the drawer's per-destination saved stacks; "drawer resets the stack" only as a fallback if the spike shows it
   is fragile, and then as a CHANGELOG item.

# Plan: Undo that survives, Mark all as read, scrollbar, find in thread (v1.6)

Status: agreed with Dallin 2026-11-03, after two reviews on 2026-11-02 (data/WorkManager, and Compose UI), both checked
against the code. Decisions in §6. These are the requests from testers in the Whirl thread (`9244p8v1`) that v1.5.1 did
not cover, as Dallin listed them in post #34:

| Request | From | Section |
|---|---|---|
| "Mark all as read" on Watched, "with a nice long Undo period" | RayMondo | §2 |
| A longer Undo (RayMondo asked for 5/10/15 s or a confirmation; vbap: "not a fan of 'are you sure' popups") | RayMondo, vbap | §1 |
| A scrollbar: "nothing to show how far up or down you are on a page" | leo27 | §3 |
| Find in a thread | leo27 | §4 |

Dallin's condition for a longer Undo: the action is only sent once Undo can no longer be pressed, so a longer window
must not make it likelier that the action never reaches Whirlpool (app closed, network gone).

---

## 1. Undo that lasts, and sends that survive

### 1.1 How it works today
`WatchedRepository.perform` (`core/data/.../WatchedRepository.kt:152`) applies Watch / Mark as read / Unwatch to the
cached lists at once and keeps a `PendingWatch` in memory, with the rows it changed for rollback. A coroutine in
`@ApplicationScope` sends it when the snackbar goes away (`commit`) or after `UNDO_WINDOW` = 15 s. Undo cancels it and
rolls back; nothing is sent. Whirlpool has no "mark unread" and re-watching marks a thread read to the end, so Undo
cannot be an API call (verified 2026-10-21).

The snackbar is `SnackbarDuration.Short` (about 4 s, `app/.../navigation/WatchSnackbars.kt:47`), and dismissing it
commits, so the real window is about 4 s. If the process dies during the window, the action is lost: the next refresh
brings the thread back. Nothing breaks, but the user thinks it was done.

### 1.2 What changes for the user
- **Snackbar `SnackbarDuration.Long`**: 10 s, or longer if the user set Android's "Time to take action" accessibility
  timeout (material3 1.4.0 passes Long's 10 000 ms through `calculateRecommendedTimeoutMillis`; checked with javap).
  The same for Mark all as read.
- **No setting** for the length and **no confirmation dialog** (vbap's point; Dallin agreed in the thread).
- Actions survive the app closing and wait for the network.

### 1.3 Design: today's flow, plus a stored queue with WorkManager as a safety net
The in-app flow stays as it is (snackbar, `commit`, in-memory `PendingWatch` with its rollback snapshot), so there is
no extra WorkManager delay while the app is open. What is new is a stored copy of each action and one sender that both
the app and a backstop worker use.

**Table `pending_watch`** (database v3 → v4, an `AutoMigration` like the earlier two):

| Column | |
|---|---|
| `id` | autoincrement; the send order |
| `action` | `WATCH`, `MARK_READ`, `UNWATCH` |
| `threadIds` | comma-separated (thread ids are strings without commas) |
| `sendAfter` | epoch ms: now + backstop for actions with Undo, now for the rest |
| `state` | `WAITING`, `SENDING` |
| `attempts`, `createdAt` | for the retry cap and expiry |

**Flow**
1. `perform`, under the repository mutex: insert the row and apply the change locally (as today), keep the snapshot in
   memory, emit `Applied`, and `enqueueUniqueWork("watch-send", KEEP, …)` with `initialDelay` = backstop and
   `NetworkType.CONNECTED`.
2. **Undo**: `DELETE … WHERE id = ? AND state = 'WAITING'`; if a row was deleted, roll back from the snapshot. Zero rows
   means it is already being sent: too late, as today. (Single conditional statements are atomic; no
   `withTransaction` needed. Always take the mutex before any database transaction, never the other way round.)
3. **Commit** (snackbar gone) or the in-app timer: set the row's `sendAfter` to now and call `WatchSender.drain()`
   directly.
4. **`WatchSender.drain()`**, guarded by a process-wide mutex: take rows in id order; **stop at the first row that is
   not due** (strict first in, first out, so a later "send now" row cannot overtake an earlier one still in its Undo
   window); claim it (`UPDATE … SET state = 'SENDING' WHERE id = ? AND state = 'WAITING'`), send as today
   (`updateWatched(...)` with `get=watched&watchedmode=1`), store the returned list, delete the row.
5. **`WatchSendWorker`** (the backstop) runs `drain()`; if rows remain that are not yet due, it waits inside `doWork`
   until they are. **`REPLACE` is never used**: it cancels a running send mid-request, which can send an action twice
   (a second Watch marks the thread read to its end) or out of order. The work is enqueued with `KEEP` at `perform`,
   after a transient failure in the app, and at app start whenever rows exist (covers force-stop, and a database
   restored from cloud backup, which WorkManager's own database is not). It is cancelled only on sign-out.
6. **Backstop delay** about 3 minutes, not 20 s: commit drives the send in normal use, so the backstop only matters
   after process death, and it must outlast a snackbar stretched by the accessibility timeout (up to 2 minutes).
7. **Errors**
   - Transient: `Network`, `RateLimited`, `Challenge` (Cloudflare) → row back to `WAITING`, `attempts + 1`,
     `Result.retry()` with WorkManager's backoff.
   - `Maintenance` is ambiguous: Whirlpool answers an unknown thread id with an HTML 500, which surfaces as
     `Maintenance` after the usual retries (`WhirlpoolApi.kt:86`). For a single id, treat it as transient up to the cap.
     For several ids (Mark all; one thread deleted between fetch and send fails the whole request): refresh the All
     list once and resend only the ids still on it.
   - Permanent (anything else, including a revoked key), or the cap reached (5 attempts or 24 h old): delete the row,
     **roll back from the snapshot if it is still in memory**, else force-refresh both lists, and emit
     `WatchEvent.Failed`.
8. **`reapplyPending`** applies the stored rows (not only the in-memory list), so a refresh after a process restart does
   not bring back a thread whose action is still queued.
9. **Notification check**: `WatchedNotifier.check` plans alerts from the raw list `fetchUnread` returns
   (`WatchedRepository.kt:126-133`), before pending actions are reapplied. It must drop threads with a queued Mark as
   read or Unwatch, or a longer window (or offline) means alerting for a thread the user just marked.
10. **Sign-out and account switch**: `clearAccountData` lives in `:core:database`, which has no WorkManager. In
    `AccountRepository.signIn`/`signOut` (`AccountRepository.kt:26, 33`), under the repository mutex: cancel
    `watch-send`, clear `pending_watch` and the in-memory list (never cleared today). A send already in flight checks
    that the account is unchanged before `store()`, so it cannot write the old account's lists back.
11. A row left `SENDING` by a killed process is reset to `WAITING` when `drain()` starts with no send in progress
    (the process-wide mutex makes that safe).

**Actions without Undo**: auto mark-read on open joins the queue with `sendAfter = now` (and gains the offline retry).
The notification buttons keep their direct send: `WatchedNotifier.act` needs the result to decide whether to clear the
notification. They are a second path outside the queue order, which is acceptable for a single Mark read or Unwatch.

### 1.4 Where the code lives
`WatchSender` and `WatchSendWorker` go in `:core:data` (`:core:notifications` already depends on it). `:core:data`
gains `work-runtime`, `hilt-work`, `ksp(libs.androidx.hilt.compiler)` and `testImplementation(libs.work.testing)`. The
worker factory is already app-wide (`WhirlApplication`). Scheduling goes through a small interface so
`WatchedRepositoryTest` can use a fake.

### 1.5 Tests
- `WatchedRepositoryTest`: Undo before the send deletes the row and restores the lists; Undo after claiming returns
  false; commit sends; a refresh while a row waits keeps the local change; **a new repository over the same database**
  (process death) reapplies and sends the row; first in, first out with a mix of due and not-due rows; commit during a
  send does not send twice; account switch with rows queued.
- Worker (`TestListenableWorkerBuilder` with a small `WorkerFactory`): sends due rows, waits for later ones, network and
  Challenge → retry, permanent → row gone and `Failed`, Mark all with a deleted id resends the rest, stuck `SENDING`
  reset, cap and expiry.
- `WatchedNotifierTest`: no alert for a thread with a queued Mark as read.
- DAO test for the table; migration test 3 → 4.
- Device: Mark as read then swipe the app away → sent within about 3 minutes; force-stop → sent at the next launch;
  reboot with a row queued; airplane mode then network back; three actions in quick succession; the accessibility
  timeout set to 1 minute; Doze. Rows in `docs/device-checklist.md`.

---

## 2. Mark all as read

### 2.1 What it is
On Watched, "Mark all as read" marks every unread watched thread read in one API call
(`watchedread=<ids>&get=watched&watchedmode=1`), with Undo like any other action.

### 2.2 UI
- Watched gets a ⋮ overflow menu with **Mark all as read**, enabled when there are unread threads, on both tabs.
- **`FindBar` gains an `actions` slot** (today it has none, and `ListTopBar` replaces the whole top bar with it while
  finding, `DrawerLists.kt:275-288`, so a menu only in the normal bar would be unreachable while filtering). The same
  slot carries next/previous in §4.
- While Find in list has a query, the item reads **"Mark N shown as read"** and marks only the matching unread threads,
  on either tab.
- Snackbar: "Marked N threads as read", with Undo restoring every row in place.
- Check the Watched bar (Find, Refresh, ⋮, "N unread" subtitle) at 200 % font.
- A "Mark forum as read" on each forum header is left for later.

### 2.3 Which ids
- From the cached **Unread list with `unreadCount > 0`**: in two panes the thread open beside the list is kept on the
  Unread tab after it is marked read (`keepRow`, `DrawerLists.kt:307-326`), so not every row shown is unread. N counts
  these ids.
- With a query: those ids intersected with the rows the filter shows, on either tab.
- **Stale list**: `watchedread` marks a thread read to its end, including replies posted after the list was fetched.
  The list is normally minutes old (Watched refreshes each visit), but offline it can be any age, and a queued send may
  go out later still. Accepted, and said in the changelog ("everything on the Unread list"). Mark as read rows expire
  with the general cap (§1.3).
- **Many threads**: ids go in the GET query string (about 1 KB for 100 ids; `call` builds a GET). If a limit shows up
  on the phone, split into requests of 50 within one row, removing ids as each request succeeds.

### 2.4 Tests
- `WatchedViewModel`: Mark all takes the unread ids only (not a kept row); with a query, only matching ids, on both tabs;
  disabled with nothing unread.
- Repository: one row with all ids; Undo restores them all.
- Compose: the item is reachable while finding; its label with and without a query.

---

## 3. Scrollbar on the post list

### 3.1 What it is (first step: an indicator, not draggable)
A thin thumb at the end edge of the thread's post list that shows where you are on the page, appears while scrolling
and fades about 1.5 s after it stops. **Not draggable in v1.6**: with about 20 posts per page, dragging by item is
coarse and cannot move through a post several screens long, and a drag target at the screen edge would block
swipe-Back right after a fling. Dragging can come later (§3.5).

### 3.2 Position
Compose foundation 1.12.1 has `LazyListState.scrollIndicatorState` but its content size is an estimate from the visible
items, and neither foundation nor material3 1.4.0 draws a scrollbar. Posts range from one line to several screens, so
the estimate jumps. Use instead, from `layoutInfo`:
- `above = first + hiddenFraction(first)` (how much of the first visible item is scrolled past),
- `below = (count − 1 − last) + hiddenFraction(last)`,
- `progress = above / (above + below)`.

It needs no size estimate, moves the right way as items of different heights come in, and is exactly 0 and 1 at the
ends. The thumb has a **fixed length** (about 48 dp); a length from estimated content would change as tall posts scroll
in.

### 3.3 Where and how it draws
- `ScrollThumb(state: LazyListState)` in `:core:ui`, drawn inside the `PullToRefreshBox` above the list in `PostList`.
- At the viewport's end edge, not the edge of the `readableWidth` column; the Scaffold's horizontal insets keep it clear
  of a side navigation bar. RTL: the end edge.
- No pointer input at all, so it can never take a pager swipe or a link tap. Hidden from accessibility
  (`clearAndSetSemantics {}`); TalkBack scrolls lists itself.
- Always on for the post list (the "taller than two screens" gate needs the same estimate and almost always passes).
- Thread lists: not now.

### 3.4 Tests
- Unit: the progress function at the top, middle, bottom, fewer items than fit, and as the visible item count changes.
- Compose: hidden at rest, shown while scrolling, fades.
- Device: phone, two panes, 200 % font, RTL (pseudo-locale).

### 3.5 Later: dragging
If testers ask: invert the formula with `scrollToItem(i, offset)`, or drag with `dispatchRawDelta`; inset the touch
target by `WindowInsets.systemGestures` rather than excluding gestures; take the touch target out of composition while
the thumb is hidden.

---

## 4. Find in thread

### 4.1 What it is (first step: the page you are on)
⋮ › **Find on page** (and Ctrl+F or `/` in a thread) opens a find bar in place of the thread's top bar. Typing
highlights matches in the posts on the current page. The bar shows **"Post 2 of 7"** with previous/next arrows (in
`FindBar`'s new actions slot, §2.2) that scroll to each matching post. Nothing is filtered out: the page keeps every
post, so replies stay in context.

Asked of leo27 whether this is enough or whether they want the whole thread; no answer yet. The **whole-thread** version
would read `/archive/<id>` (800 posts per request; PLAN.md §9 has sizes), which is the archive reader idea. Find works
on a list of `UiPost`, so the archive can plug in later.

### 4.2 Matching
- A post matches when **every word appears in it** (as Find in list), case- and accent-insensitive.
- **The matcher searches exactly the text the highlighter can mark**: paragraph, list item and table cell text, code
  blocks and quote attributions (both rendered outside `annotate` today, so they get an `AnnotatedString` too), and the
  author's name (highlighted in `PostHeader`). Not the date, user group, quip or the moderator note.
- **Spoilers are excluded, revealed or not**: reveal state is UI state the model cannot see, and a highlight box over
  hidden text would show where the word is and how long it is.
- Highlights only in posts that match all words, so every highlighted post is one that next/previous visits.
- `PostText.extract(PostBody)` (pure, `:core:model`) gives the searchable text; tested against Dallin's whirlcode post
  (#37, `fixtures/html/thread_9244p8v1_whirlcode.html`).

### 4.3 Highlighting
- `PostBodyView` takes an optional `highlight: TextQuery?`. Ranges are computed **over the finished string** that
  `annotate` builds, so a word split across bold or link boundaries is still found, and the highlight span is added
  **last** so it wins over inline-code and link styles. Spoiler ranges are skipped (passed down through
  `append(..., hidden)`).
- `TextQuery` gains `ranges(text): List<IntRange>` using a **per-character fold with an index map** back to the
  original (NFD changes length; `plan-v1.3.md` §1.3). `matches` switches to the same per-character fold so the two can
  never disagree (whole-string `lowercase` treats Greek final sigma differently).
- Colour: `tertiaryContainer` background with `onTertiaryContainer` text; the current post's matches stronger.

### 4.4 State and navigation
- `ThreadViewModel` gains a `SavedStateHandle` (Hilt allows it alongside the assisted factory,
  `ThreadViewModel.kt:85-92`) holding the query, so it survives process death.
- The VM exposes the **matching reply ids of the shown page**, computed from `page(settledPage)`. Next/previous set a
  `ScrollTarget(page, replyId = …)`, reusing the existing scroll effect and its header/marker offset
  (`ThreadScreen.kt:287-299`), so there is no third copy of that arithmetic.
- The match index resets when the page or the query changes. **No automatic scroll on a page change** (it would fight
  `jumpTo`, "last post" and the user's own swipe): "next" goes to the first match below the first visible post.
- Scrolling brings the post's top to the screen; a match far down a long post stays below. Known limit for v1.6;
  `BringIntoViewRequester` on the matching paragraph later if it matters.
- The bar keeps **"Page x of y"** under the field, still a polite live region, so swiping pages while finding is
  announced as today (`ThreadScreen.kt:205-215`).
- Back, Esc or the bar's arrow closes it and clears highlights. With nothing on the page: "No matches on this page".
- **Find shortcut in two panes**: `OnFindShortcut` gets an `enabled` flag. The list's listeners (`ListTopBar` and
  `FindBar`'s refocus listener, `Find.kt:131`) pass `LocalSelectedThread.current == null`, so with a thread open the
  thread takes Ctrl+F. Test Back with both find bars open. The shortcut help in `MainActivity` (:122) gains "Find on
  page".

### 4.5 Tests
- Unit: `TextQuery.ranges` (accents, case, several occurrences, Greek sigma), `PostText.extract` on the fixture post.
- Compose (a new harness: no `ThreadScreen` or `PostBodyView` Compose tests exist yet): highlight spans in paragraph,
  list, cell, code and attribution; none inside a hidden spoiler; "Post 1 of N"; next lands on the right post past the
  New posts marker and the note/parts header rows; the shortcut goes to the thread in two panes; the query survives
  recreation.
- Device: Dallin's whirlcode post, a long page, 200 % font, TalkBack reads the count; rows in
  `docs/device-checklist.md`.

---

## 5. Order and release
1. **PR A: stored queue + longer snackbar + notification filter** (§1). The riskiest; it touches every watch action.
2. **PR B: Mark all as read** (§2), on top of A, including the `FindBar` actions slot.
3. **PR C: scroll indicator** (§3), independent.
4. **PR D: find on page** (§4), after B (shares the actions slot).

Release **v1.6.0** with A–C, and D if it is ready and leo27's answer does not change it; otherwise D goes in v1.6.1 or
v1.7 with the whole-thread option. Release PR as usual: `app/version.properties`, CHANGELOG `## v1.6.0`, store
changelogs `10600.txt` in en-AU and en-US. HANDOFF.md gets a v1.6 paragraph.

## 6. Decisions (Dallin, 2026-11-03)
1. Undo is 10 s for everything, Mark all as read included: "10s is already really long".
2. Mark all applies only to what is visible: "Mark N shown as read" while filtering (disabling it while filtering was
   the alternative he offered).
3. The scrollbar is an indicator only in v1.6. Whirldroid's was similar, with some rubber banding by post length: the
   §3.2 formula moves faster through short posts and slower through long ones, which gives the same feel.
4. Find on page is built for v1.6 without waiting for leo27.

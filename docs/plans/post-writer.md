# Plan: the post writer (v1.11)

Status: **planned and reviewed** (2026-11-19); **PRs A and B built** (2026-11-19). Idea from Dallin, 2026-11-08; decisions below from
2026-11-19. v1.10 is settings export and import (`settings-backup.md` Part 2), so this is the next feature release.

Whirlpool's reply box takes raw WhirlCode or a little HTML, with a preview but no formatting buttons. On a phone, a
reply that quotes several people, with some formatting and a link, means copying text between pages and typing markup by
hand. Whirl already has every post's id, author and body, and renders every WhirlCode format, so it can build the text
and hand it to the website.

## 0. Rules and decisions
- **Whirl never posts** (HANDOFF hard rule 10). The reply is copied to the clipboard and Whirlpool's reply page opens
  in the browser, where the user pastes, previews and posts. No web session, so no moderator approval is needed; still
  mention it in the next community post (`docs/community.md`).
- **The draft is WhirlCode**, edited as plain text with a formatting bar and a preview rendered by Whirl's own post
  renderer. Not a WYSIWYG editor: round-tripping styled text to exact WhirlCode is where those break.
- Decided by Dallin, 2026-11-19:
  - **Quote form**: an `@<postid> <name> writes...` line, then each of the poster's paragraphs as a `["…"]` forum
    quote, the form most posts use.
  - **Inner quotes are dropped**: only the poster's own words are quoted. (First decided as "keep everything"; changed
    after the review found that `["…"]` cannot nest, so kept inner quotes read as the poster's own words.)
  - **The whole post** is quoted; the user trims in the editor. Quoting a selection is later (§9).
  - **One draft per thread**, a marker on threads that have one, and a **Drafts** list in the drawer.

## 1. What we know
### 1.1 The syntax (`fixtures/html/wiki_whirlcode.html`, captured 2026-11-19)
`/wiki/whirlcode` is short and complete. Its first table pairs each **inline** code with the HTML the site rendered
for it; the block table gives only prefixes and purposes, with no rendered output.
- **Inline** (wraps text in one paragraph): `[*bold*]`, `[/italic/]`, `[(small)]`, `[-strike-]`, `[#mono#]`,
  `[~serif~]`, `H[\2\]O`, `500m[^2^]`, `["forum quote"]`, `['external quote']`, `` [`alternate quote`] ``,
  `[_spoiler_]`, `[?google search?]`, `[[wiki term]]`, `[[term|label]]`. Escaped with full stops inside the brackets:
  `[.*not bold*.]`. A spaced `-` becomes an en dash, a spaced `--` an em dash.
- **Allowed HTML**: `<strong> <b> <em> <i> <small> <strike> <tt> <sub> <sup>`, and `<a href>` (only `href`) for link
  text. Web and email addresses link themselves; Whirlpool and `whrl.pl` links are internal.
- **Structure, at the start of a line** (a space before the text; at most four codes on a line, one `:`, three `>`,
  three of a list marker): `:` indent, `*`/`**`/`***` bullets, `#`/`##`/`###` numbers, `>`/`>>`/`>>>` reply quotes,
  `$ ` code (rest of line), `$$` fenced code block, `-----` rule, `@id text` jump link (rendered as
  `p.reference a[title=id]` with `text` as its label; "writes..." is typed, it is not added). `=` headings are wiki
  only.
- **Tables**: lines starting `|`; a Markdown-style `| :--- | :---: | ---:` row under the first makes it a header and
  sets alignment. A literal pipe needs `&#124;`.

### 1.2 What it renders as
`PostBodyParser` and `PostBody` already cover everything above: attributions, `span.wcrep1/wcrep2/wcgrey/wcspoil/
wcserif`, nested `blockquote.wcrep1`, nested `ul`/`ol`, `pre`, `hr`, tables with header and alignment.

**The end-to-end oracle for blocks** is Dallin's test post `76888474` in `fixtures/html/thread_9244p8v1_whirlcode.html`:
its source, written in an earlier session and recovered from that session's transcript, is
`fixtures/whirlcode/post_76888474.txt` (from "Formatting test" on; Dallin added a quote and a paragraph above it when
posting, which the test skips). It uses every code, including lists in quotes, a quote in an indent and an inline quote
in a reply quote. If Dallin edited the text before posting, the test shows where.

**Autolinks are shortened** on display, and the wiki does not say so (review, from the fixtures): some long external
URLs show only the host (`https://www.tweaktown.com/news/113561/…` as `www.tweaktown.com`) and internal links show only
their path; `https://example.com` shows in full. The rule is derived from the fixtures in PR A.

### 1.3 Dallin's answers (logged in, 2026-11-19)
- "Reply to this post" (`?action=reply&r=`) opens an **empty** reply box: no prefill, so pasting cannot duplicate.
- The test post has no edit page; its source was recovered instead (§1.2).
- `@id name` renders `name` only: "writes..." must be written.
- The reply box's length limit is unknown. No counter until someone hits it; then add one.

### 1.4 Still to capture, before PR A's edge cases (step 0)
The wiki and the test post show the site's output for well-formed text only. What it does with malformed text (an
unclosed `[*`, codes across a line break, `- ` at the start of a line, five structural codes, `<script>`, `<a
onclick>`, a single newline versus a blank line) is unknown, and tests for it would encode guesses. So: Whirl writes an
edge-case post (`fixtures/whirlcode/edge_cases.txt`); Dallin posts it in the Whirl thread as before; `tools/capture`
saves that page, and its rendered post becomes the oracle. Until then those cases are tested only for safety (no
unescaped HTML), not for matching the site.

## 2. WhirlCode to HTML: `:core:whirlcode` (pure JVM), PR A
`WhirlCode.toHtml(source): String` writes the site's markup, and the preview is then
`PostBodyParser.parse(toHtml(source))` rendered by `PostBodyView`, the same path as every post.
- **Why HTML and not `PostBody` directly**: one oracle. The site's HTML is the reference, the parser is already
  tested against it, and a difference shows as a `PostBody` diff.
- Line pass first (structure: quotes, lists, code, rules, tables, jump links), then an inline pass per paragraph
  (bracket codes, allowed tags, autolinks with the site's shortening, dashes, escapes). Newlines: as the test post and
  the edge-case capture show (§1.4). Unmatched markers stay literal. Anything else that looks like HTML is escaped.
- **Tests:**
  - each wiki inline row, with an expected value written per row (the rendered column has prose notes such as
    "(subscript)", and the `[[term|label]]` row renders literally there, unlike in posts);
  - the test post: `parse(toHtml(post_76888474.txt))` equals the parsed fixture post, after the part Dallin added;
  - the edge-case post once captured (§1.4);
  - **safety on the HTML string**, not on `PostBody` (the parser ignores attributes such as `onclick`, so a `PostBody`
    comparison cannot catch them): no `<script>`, no attribute but `href` and `class`, no `javascript:` link.

*As built (PR A):* `Inline.kt` (bracket codes by recursive descent, at most 32 deep; allowed tags; entities; dashes),
`Links.kt` (the link rules: from a survey of every link in the fixtures, external links keep their text, forum links
show their path, `whrl.pl` drops its scheme, bare `www.` gets `http://`; the "host only" texts were custom labels, not
shortening) and `WhirlCode.kt` (the line pass). Blank lines end a paragraph but close no quote, list or table, as the
test post shows; a single newline is `<br>`. Guesses waiting for §1.4: trailing punctuation outside links, an
unclosed code left as typed, over-limit structural codes as text, one `<pre>` per `$ ` line.

## 3. Post to WhirlCode, for quoting: PR B
`WhirlCode.fromBody(body: PostBody): String`, the reverse, so a quote carries the post's formatting and links.
- A link whose text is its URL, or the site's shortened form of it (§1.2), becomes the bare URL; wiki, Google and
  email links go back to their codes where `PostBody` tells them apart (today the parser drops the link class,
  `PostBodyParser.kt:195`; keep it in `Inline.Link` if needed).
- **Consistency test over every post in every fixture**: `parse(toHtml(fromBody(b))) == b`. This checks the two
  converters against each other, not against the site, so it complements §2's tests rather than replacing them.
  `PostBody` cannot see some things (styling inside a link's text, a quote inside an inline quote, header cells,
  which it makes bold), and old posts hold markup WhirlCode cannot write (`http://http://` links, nesting past three).
  Posts that do not survive are kept as a reviewed golden list with the reason for each, not skipped one by one.
- **The quote** (`Quote.of(post)`): the `@<postid> <name> writes...` line, then each paragraph the poster wrote as
  `["…"]`, one per line of the paragraph (a `["` does not cross a line break). Inner quotes are dropped: an
  attribution and the quoted paragraphs after it, and `wcrep` spans within a paragraph. Lists, tables and code keep
  their structure. A post that is only quotes gives just the attribution line.
- No Quote on hidden or deleted posts.

*As built (PR B):* `FromBody.kt` and `Quote.kt`. Links go back to the form the site autolinks (email, `[?…?]`,
`[[…]]`, forum paths, `whrl.pl`, `www.`, a bare URL whose text is the URL) when nothing follows that the autolink would
swallow, else `<a href>`. Text the converter would act on (`[*`, `*]`, a spaced hyphen, `://`, `@`, `www.`, a line
starting with a structure code) is written as entities. Inherited styles (a `>` quote's colour, a header cell's bold) are
not written again. Of the 2,591 posts in the fixtures, 2,586 round-trip exactly; the five that do not are in
`FromBodyTest.KNOWN` with why (a URL holding `<` and `>`; 2009 markup with whitespace-only lines). Not writable and so
not round-tripped: images, underline, a line break in a list item, code or a rule inside a quote, an attribution
inside a quote, two quotes or lists of the same kind next to each other (a blank line does not separate them).

## 4. Drafts: Room v9, PR C
- `drafts(thread_id TEXT PRIMARY KEY, thread_title TEXT, text TEXT, updated_at INTEGER)`, with a migration test.
  Not tied to the account: signing out keeps drafts (they are the user's writing), and `clearAccountData` does not
  touch them.
- **Merged after v1.10 is tagged**, so v1.10 does not ship schema v9 for a feature it does not have.
- **One owner**: `DraftRepository` (`:core:data`) serialises every write behind a mutex. The writer saves as typed
  (debounced about 500 ms) and on leaving, through `@ApplicationScope` with `NonCancellable` (a save from
  `viewModelScope` is cancelled in `onCleared`). A quote added while the writer is open (two panes) goes through the
  open writer's state, not straight to Room, so the writer's next save cannot overwrite it; with no writer open,
  `appendQuote` writes Room. An empty draft is deleted.
- **Not backed up or exported.** `whirl.db` stays out of the backup rules, but they and `BackupRulesTest` call it "a
  cache, fetched again": update the comments to say drafts are in it too and are deliberately not backed up. Revisit
  if asked.

*As built (PR C):* `DraftEntity`/`DraftDao`, an auto-migration 8 → 9, and `DraftRepository` (`:core:data`): `observe`,
`observeAll` (newest first), `save` (blank deletes), `delete`, `appendQuote` (returns the quote count for the snackbar)
and `attachWriter`, through which an open writer takes quotes for its thread; its callback must not call `save` (the
lock is held), the writer's own save stores the quote. The backup rules' comments and `BackupRulesTest` now say drafts
are in `whirl.db` and deliberately not backed up.

## 5. The writer screen: `:feature:writer`, PR D
- `WriterRoute(threadId)` (in `RouteSerializers`) is a **detail-pane entry**, like `ThreadRoute` (`WhirlApp.kt`
  metadata): in two panes it replaces the thread beside the list, and back returns to the thread. Not a
  `ListPaneRoute`, which means a thread list.
- **Editor**: `TextFieldState` with its own undo and redo; monospace as a toggle in its menu.
- **Formatting bar**, scrolling, above the keyboard: bold, italic, forum quote, reply quote (`>` toggled on the
  selected lines), spoiler, link (dialog with URL and text; writes `<a href="…">text</a>`, or the bare URL when the
  text is empty), bullet list, numbered list, code. "More" opens the rest: small, strike, mono, serif, sub, sup,
  external quote, alternate quote, rule, a 2×2 table. Each wraps the selection, or inserts the pair with the cursor
  between. Every button has a TalkBack label and a 48 dp target.
- **Preview**: Write and Preview tabs; side by side when **the writer's own width** is at least 600 dp
  (`BoxWithConstraints`), so a writer in a pane of 330 dp keeps tabs. Converted off Main, debounced as typed.
- **Copy and reply**: copies the text and opens the reply page in the browser or Custom Tab, per the existing setting
  (`WhirlpoolWeb.replyToThread`, or `replyToPost` for a draft started from one post; reuse these, not the copies in the
  thread view models). A snackbar says "Copied: paste into the reply box", with **Delete draft**. The draft stays until
  deleted, so a failed post loses nothing.

*As built (PR D):* `:feature:writer`. `WriterViewModel` loads the draft (the field is read-only until then), attaches
itself as the thread's writer, saves 500 ms after typing stops, on Copy and reply, and in `onCleared` through the
application scope; the preview converts 250 ms after typing stops, on the default dispatcher. `Formatting` holds the
bar's operations as pure functions on text and selection (`FormattingTest`). The bar uses text labels with TalkBack
descriptions (only the core icon set is in the build); "More codes" lists the rest. Undo and redo are the text field's
own. Copy and reply copies, saves, opens the reply page, and offers Delete draft in the snackbar. Not yet: the
monospace toggle (§5). Nothing opens the writer until PR E1.

## 6. Entry points: PRs E1 and E2
- **E1** (`:feature:thread`): post menu **Quote** (pages and scroll reader) adds `Quote.of(post)` to that thread's
  draft, with a snackbar "Quoted · 3 quotes in your reply" and **Open**. Thread menu **Write a reply** opens the
  writer ("Reply in browser" stays). A "Draft" chip in the thread's top bar opens it.
- **E2** (`:app`, `:feature:threadlist`): **Drawer › Drafts** (a new top-level route in `TopLevelBackStack` and the
  drawer): thread title, first line and age, newest first; tap opens the writer, swipe deletes with Undo. A draft icon
  on thread-list rows (each list's row model gains the flag, from one `observeAll()` of draft thread ids).

## 7. Order (one PR each; `main` stays releasable)
Step 0 (§1.4, Dallin posts the edge cases; can run alongside A) → A converter → B reverse and quotes → C drafts (after
the v1.10 tag) → D writer → E1 → E2. Nothing shows in the app until E1, so A–D merge as they are ready. Then a release
PR (v1.11.0). **Effort**: A and B are most of the release (the review's estimate); the screens reuse what exists.

## 8. Reviewed
An architect review (2026-11-19, read-only, against the code and fixtures) found:
- the wiki covers inline codes only, so the block oracle is the test post and the edge cases need a capture (§1.2,
  §1.4);
- the site's undocumented autolink shortening (§1.2, §3);
- that the round trip checks the converters against each other, not the site, and that `onclick`/`<script>` must be
  asserted on the HTML string (§2, §3);
- that `["…"]` cannot nest, which changed the inner-quote decision (§0, §3);
- that the writer is a detail-pane entry, not a `ListPaneRoute`, with the preview layout keyed to its own width (§5);
- draft saves cancelled in `onCleared` and stale saves over appended quotes (§4);
- the backup comments on `whirl.db`, and schema v9 shipping early (§4);
- PR E split in two, and reuse of the existing reply URLs (§5, §6).

It also answered §1.3's third question from the fixture before Dallin did. All are folded in above.

## 9. Later
- Quoting a selection: make post text selectable, map the selection back to its blocks, then `fromBody` on that part.
- Styling as typed via an `OutputTransformation` (codes dimmed, bold shown bold, quotes indented).
- Quotes from another thread into this draft (today a quote goes to its own thread's draft).
- New thread (`?action=newthread&f=`, verified 2026-10-21) from the same writer, with a title field.
- A length counter, once the reply box's limit is known.

## 10. Device checks (`docs/device-checklist.md` when built)
- Quote three posts on two pages (one with an inner quote, one with a list), trim, add bold and a link; Copy and reply;
  paste on the site. The site's preview matches Whirl's.
- Process death in the writer (`adb shell am kill` with the app in the background): the draft is back.
- Two panes (emulator): quote from the thread while the writer is open; nothing is lost.
- TalkBack across the formatting bar; 200 % font; landscape.

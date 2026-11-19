# Plan: the post writer (v1.11)

Status: **planned** (2026-11-19), not started. Idea from Dallin, 2026-11-08; decisions below from 2026-11-19. v1.10 is
settings export and import (`settings-backup.md` Part 2), so this is the next feature release.

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
  - **Quotes match the site's own form**: an `@<postid> <name> writes...` jump-link line, then the quoted paragraphs
    as `["…"]` forum quotes, so a quote from Whirl looks like everyone else's. (To confirm against what the site's
    "Reply to this post" prefills: §1.3.)
  - **Quote the whole post, inner quotes included.** The user trims in the editor. Quoting a selection is later (§8).
  - **One draft per thread**, a marker on threads that have one, and a **Drafts** list in the drawer.

## 1. What we know
### 1.1 The syntax (`fixtures/html/wiki_whirlcode.html`, captured 2026-11-19)
`/wiki/whirlcode` is short and complete. Its first table pairs each code with the HTML the site rendered for it, so
the fixture doubles as test data.
- **Inline** (wraps text in one paragraph): `[*bold*]`, `[/italic/]`, `[(small)]`, `[-strike-]`, `[#mono#]`,
  `[~serif~]`, `H[\2\]O`, `500m[^2^]`, `["forum quote"]`, `['external quote']`, `` [`alternate quote`] ``,
  `[_spoiler_]`, `[?google search?]`, `[[wiki term]]`, `[[term|label]]`. Escaped with full stops inside the brackets:
  `[.*not bold*.]`. A spaced `-` becomes an en dash, a spaced `--` an em dash.
- **Allowed HTML**: `<strong> <b> <em> <i> <small> <strike> <tt> <sub> <sup>`, and `<a href>` (only `href`) for link
  text. Web and email addresses link themselves; Whirlpool and `whrl.pl` links are internal.
- **Structure, at the start of a line** (a space before the text; at most four codes on a line, one `:`, three `>`,
  three of a list marker): `:` indent, `*`/`**`/`***` bullets, `#`/`##`/`###` numbers, `>`/`>>`/`>>>` reply quotes,
  `$ ` code (rest of line), `$$` fenced code block, `-----` rule, `@id name` jump link to a reply. `=` headings are
  wiki only.
- **Tables**: lines starting `|`; a Markdown-style `| :--- | :---: | ---:` row under the first makes it a header and
  sets alignment. A literal pipe needs `&#124;`.

### 1.2 What it renders as (already parsed)
`PostBodyParser` and `PostBody` already cover everything above: `p.reference a[title=<postid>]` attributions,
`span.wcrep1/wcrep2/wcgrey/wcspoil/wcserif`, nested `blockquote.wcrep1`, nested `ul`/`ol`, `pre`, `hr` and tables
with header and alignment. Dallin's test post `76888474` in `fixtures/html/thread_9244p8v1_whirlcode.html` uses every
code on the wiki page.

### 1.3 Still unknown: Dallin's checks, logged in, in a browser (before PR B)
1. **What "Reply to this post" prefills** (`/forum/?action=reply&r=<postid>`): if it puts a quote of that post in the
   box, pasting Whirl's would duplicate it. Then Copy and reply opens `?action=reply&t=<thread>` instead. Note the
   exact text it inserts: that is the quote form to copy (`@id name writes...` or something else).
2. **The source of test post `76888474`** (its Edit page, `?action=edit&e=76888474`): paste it into
   `fixtures/whirlcode/post_76888474.txt`. It is the end-to-end test of the converter.
3. Does `@76888415 RayMondo` render "RayMondo writes...", or is "writes..." typed?
4. The reply box's length limit, if any (the site's error on a very long post).

## 2. WhirlCode to HTML: `:core:whirlcode` (pure JVM), PR A
`WhirlCode.toHtml(source): String` writes the site's markup, and the preview is then
`PostBodyParser.parse(toHtml(source))` rendered by `PostBodyView`, the same path as every post.
- **Why HTML and not `PostBody` directly:** one oracle. The site's HTML is the reference, the parser is already tested
  against it, and any difference shows as a `PostBody` diff in a test.
- Line pass first (structure: quotes, lists, code, rules, tables, jump links), then an inline pass per paragraph
  (bracket codes, allowed tags, autolinks, dashes, escapes). Unmatched or misnested markers stay as literal text, as on
  the site. Anything else that looks like HTML is escaped.
- **Tests:**
  - every row of the wiki's tables against its rendered column (compared as `PostBody`);
  - the test post: `parse(toHtml(post_76888474.txt)) == parse(fixture post 76888474)`;
  - edge cases: escapes, an unclosed `[*`, codes across a line break, `- ` at the start of a line (bullet or dash),
    `&#124;` in a cell, five structural codes on a line, `<script>` and `<a onclick>` escaped.

## 3. Post to WhirlCode, for quoting: PR B
`WhirlCode.fromBody(body: PostBody): String`, the reverse, so a quote carries the post's formatting and links.
- **Round-trip test over every post in every fixture** (thousands, from the archive pages):
  `parse(toHtml(fromBody(b))) == b`. Known lossy cases (images, which WhirlCode cannot write) are listed and skipped.
- **The quote** (`Quote.of(post)`): the `@<postid> <name> writes...` line (form per §1.3), then each of the poster's
  own paragraphs as `["…"]`. Lists, tables and code keep their structure. Inner quotes (the post's own attributions
  and quotes) are kept as written; wrapping them in a second `["…"]` would not nest. Check on the device that this
  reads clearly; if not, inner quotes could become `>` blocks.
- No Quote on hidden or deleted posts.

## 4. Drafts: Room v9, PR C
- `drafts(thread_id TEXT PRIMARY KEY, thread_title TEXT, text TEXT, updated_at INTEGER)`, with a migration test. Not
  tied to the account: signing out keeps drafts (they are the user's writing), and `clearAccountData` does not touch
  them.
- Saved as typed (debounced about 500 ms, and on leaving the screen), so a draft survives process death. An empty draft
  is deleted.
- **Not backed up or exported**: `whirl.db` stays out of the backup rules. Drafts are short-lived; revisit if asked.
- `DraftRepository` in `:core:data`: `observe(threadId)`, `observeAll()`, `save`, `appendQuote(threadId, title,
  quote)` (adds a blank line between quotes), `delete`.

## 5. The writer screen: `:feature:writer`, PR D
- `WriterRoute(threadId)` (listed in `RouteSerializers`), a `ListPaneRoute` peer of the thread in two panes.
- **Editor**: `TextFieldState`, monospace optional (a toggle in its menu), its own undo and redo (`UndoState`).
- **Formatting bar**, scrolling, above the keyboard: bold, italic, forum quote, reply quote (`>` toggled on the
  selected lines), spoiler, link (dialog with URL and text; writes `<a href="…">text</a>`, or the bare URL when the
  text is empty), bullet list, numbered list, code. "More" opens the rest: small, strike, mono, serif, sub, sup,
  external quote, alternate quote, rule, a 2×2 table. Each wraps the selection, or inserts the pair with the cursor
  between. Every button has a label for TalkBack and a 48 dp target.
- **Preview**: Write and Preview tabs on a phone; side by side when the window is at least 600 dp wide (the two-pane
  threshold). Converted off Main, debounced as typed.
- **Copy and reply**: copies the text and opens the reply page (`replyToThread`, or `replyToPost` if §1.3 shows no
  prefill) in the browser or Custom Tab, per the existing setting. A snackbar says "Copied: paste into the reply box",
  with a **Delete draft** action. The draft stays until deleted, so a failed post loses nothing.
- **Over the length limit** (§1.3): a counter near the limit and a warning before copying.

## 6. Entry points: PR E (`:feature:thread`, `:app`)
- **Post menu › Quote** (pages and scroll reader): appends `Quote.of(post)` to that thread's draft. A snackbar says
  "Quoted · 3 quotes in your reply" with an **Open** action.
- **Thread menu › Write a reply** opens the writer; "Reply in browser" stays.
- **Draft marker**: on the thread screen, a small "Draft" chip in the top bar opens the writer. In thread lists, an
  icon on rows with a draft.
- **Drawer › Drafts**: thread title, first line and age, newest first. Tap opens the writer; swipe deletes with Undo.

## 7. Order (one PR each; `main` stays releasable)
A converter → B reverse and quotes → C drafts → D writer → E entry points. Nothing shows in the app until E, so A–D
can merge as they are ready. Then a release PR (v1.11.0) with CHANGELOG and store text.

## 8. Later
- Quoting a selection: make post text selectable, map the selection back to its blocks, then `fromBody` on that part.
- Styling as typed via an `OutputTransformation` (codes dimmed, bold shown bold, quotes indented).
- Quotes from another thread into this draft (today a quote goes to its own thread's draft).
- New thread (`?action=newthread&f=`, verified 2026-10-21) from the same writer, with a title field.

## 9. Device checks (`docs/device-checklist.md` when built)
- Quote three posts on two pages (one with an inner quote, one with a list), trim, add bold and a link; Copy and reply;
  paste on the site. The site's preview matches Whirl's.
- Process death in the writer (`adb shell am kill` with the app in the background): the draft is back.
- TalkBack across the formatting bar; 200 % font; landscape; the side-by-side preview on a wide window (emulator).

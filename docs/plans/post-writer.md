# Idea: a post writer (compose WhirlCode, paste into the website)

Status: **idea, not scheduled** (Dallin, 2026-11-08). The natural slot is after the v1.8 archive reader.

## Why
Whirlpool's reply box takes raw WhirlCode or HTML, with a live preview but no formatting buttons. On a phone, a reply
that quotes several people from different posts, with some formatting and a link or two, means copying text between
pages and typing markup by hand. Whirl already has every post's id, author and text, and renders every WhirlCode format.
So it can do the hard part: build the text, then hand it to the website.

## Rules it keeps
- **No web session** (PLAN.md §5, HANDOFF hard rules): Whirl never posts. The output is copied to the clipboard and the
  reply page opens in the browser, where the user pastes, previews and posts. This needs no moderator approval, unlike a
  web login. It is still worth mentioning in the next community post.
- **Not a true WYSIWYG.** Compose's rich-text editing is weak, and round-tripping styled text to exact WhirlCode is
  where such editors break. The draft *is* WhirlCode; the app helps write it and shows how it will look.

## What it does
1. **Quote collecting.** Add "Quote" to the post menu (thread view, later the archive reader). Quotes collect across
   posts, pages and threads into the draft for the thread being replied to. Each is inserted in Whirlpool's own quote
   form, with the attribution linking to the post (the `p.reference a[title=<postid>]` the site renders) and the post's
   text, ready to trim. Select part of a post first to quote just that part.
2. **Editor.** A plain text field with a formatting bar: bold, italic, link, quote, spoiler, list, and the rest of
   WhirlCode, each wrapping the selection (or inserting a pair with the cursor between). Link asks for the URL and the
   text separately. Undo.
3. **Live preview** through the app's own pipeline: WhirlCode → HTML (new, local) → `PostBodyParser` → the same post
   renderer as threads. A tab or a split view, depending on screen size.
4. **Copy and reply.** One button copies the WhirlCode and opens `WhirlpoolWeb.replyToThread` (or `replyToPost` when
   started from a post) in the browser. A snackbar says "Copied: paste into the reply box".
5. **Drafts per thread**, saved as typed (Room), surviving process death. A draft indicator on the thread, and a list of
   drafts somewhere (the drawer or Settings). Delete after copying, or keep until the user clears it.

Later: styling the text as it is typed (bold shown bold, codes dimmed, quotes indented) via Compose's
`OutputTransformation` on `TextFieldState`. That gets most of a WYSIWYG's feel while keeping the codes editable.

## Unknowns (first steps)
- **WhirlCode's exact syntax**, especially quotes with attribution and links to posts. We have the *rendered* result
  of every code (`fixtures/html/thread_9244p8v1_whirlcode.html`, Dallin's test post) but not the source. Capture the
  WhirlCode help page (`/wiki/whirlcode` or the "Whirlcode help" link on the reply page) through `tools/capture` into
  `fixtures/`. Ideally Dallin also pastes the source of his test post, so the converter can be tested end to end:
  source → HTML → parsed body equals the fixture's.
- **What the site's reply links prefill.** `replyToPost` (`?action=reply&r=`) may already put a quote of that post in
  the box. If it does, pasting would duplicate it: open `replyToThread` instead, or tell the user. Never opened logged
  in (HANDOFF "Unknowns"); Dallin can check in his browser.
- **Can the reply page take the text in the URL?** Probably not; clipboard either way. Watch the length: a long post on
  the clipboard is fine, in a URL it is not.
- **HTML input.** The site accepts some HTML too. The writer only produces WhirlCode.

## Sketch of the work
- `:core:model`: a `Draft(threadId, text, updatedAt)`; WhirlCode builders for each code (pure, unit-tested).
- `:core:scraper` or a new `:core:whirlcode`: `WhirlCode.toHtml(source)` matching the site's output closely enough
  that `PostBodyParser` reads it the same way. Tests: every code from the help page, and the test post round trip.
- `:core:database`: a `drafts` table (schema bump, migration test).
- `:feature:compose` (new): the editor screen, formatting bar, preview, copy-and-open. The quote action in
  `:feature:thread` adds to the draft and shows a snackbar ("Quoted. 3 quotes in your reply. Open").
- Device checklist: a quote from three posts on two pages; paste into the site and compare its preview with Whirl's.

## Size
About a feature release (v1.6-sized). The renderer, post data and browser hand-off exist; the new parts are the
WhirlCode-to-HTML converter, the editor screen and drafts.

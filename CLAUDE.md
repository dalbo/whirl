# Project notes for AI sessions

Start with HANDOFF.md, then PLAN.md. Hard rules live in HANDOFF.md ("Hard rules"); the most important: thread IDs are strings, parse only raw fixtures (`fixtures/html/*.html`, never `rendered/`), whims no longer exist, skip `#watchbar`, never print the API key (`~/.config/whirldroid/apikey`), the dev machine cannot reach Whirlpool directly (use `tools/capture/`), releases only from the tag-driven GitHub Actions workflow.

`reference/` is git-ignored decompiled/legacy code with unknown licence: read for behaviour, never copy.

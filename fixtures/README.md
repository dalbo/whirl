# Fixtures index (captured 2026-10-20 via tools/capture, headed Chromium, Adelaide)

Raw HTML in `html/` is the server body an HTTP client receives; `html/rendered/` is the post-JavaScript DOM, for reference only. API responses in `api/` carry the capturing user's name/ID (`d@llin`, 216903) and watched list; no key.

## api/
| File | Call | Notes |
|---|---|---|
| `api_badkey.json` | get=badkey | bad key → HTTP 403, JSON ERROR |
| `api_bogus_get.json` | get=bogus_get | unknown get → bare envelope (same as whims) |
| `api_combined.json` | get=combined | get=forum+news+recent+watched, no watchedmode (4 watched) |
| `api_cmd_watchedadd.json`, `api_cmd_watchedread.json`, `api_cmd_watchedremove.json` | watchedadd/watchedread/watchedremove=3pxj858k, no get | bare envelope, HTTP 200 (captured 2026-10-21, `npm run actions`) |
| `api_cmd_after_add.json`, `api_cmd_after_read.json` | get=watched&watchedmode=1 after the add / read | 20 watched; 3pxj858k comes back `UNREAD 0`, `LASTREAD 257` (replies + 1), `LASTPAGE 13` |
| `api_cmd_after_remove.json`, `api_cmd_final.json` | get=watched&watchedmode=1 after the remove | 19 watched, back to the original list |
| `api_cmd_watchedadd_with_get.json`, `api_cmd_watchedremove_with_get.json` | get=watched&watchedmode=1 combined with the command | the list already reflects the command (20, then 19) |
| `api_cmd_bogus_id_500.html` | watchedadd=zzzzzzzz (watchedremove identical) | HTTP 500, HTML "Something broke!" page with `Content-Type: application/json` |
| `api_contacts.json` | get=contacts | [] |
| `api_forum.json` | get=forum | 54 forums, 8 sections |
| `api_news.json` | get=news | 30 items |
| `api_recent_120d.json` | get=recent_120d | [] |
| `api_recent_14d.json` | get=recent_14d | [] |
| `api_recent_user30703.json` | get=recent_user30703 | userid= → [] |
| `api_recent_user47861.json` | get=recent_user47861 | userid= → [] |
| `api_threads_count100.json` | get=threads_count100 | site-wide, 100 threads |
| `api_threads_default.json` | get=threads_default | site-wide, 30 threads |
| `api_threads_forum92.json` | get=threads_forum92 | forumids=92, 15 threads |
| `api_threads_pm_forum1.json` | get=threads_pm_forum1 | forumids=1 → [] |
| `api_threads_private_134.json` | get=threads_private_134 | → [] |
| `api_threads_private_35.json` | get=threads_private_35 | Feedback forum, works via API |
| `api_url.json` | get=url | URL templates (stale %d) |
| `api_user.json` | get=user | AUTH user |
| `api_user_30703.json` | get=user_30703 | userid=30703 |
| `api_watched_mode0.json` | get=watched_mode0 | unread only (2) |
| `api_watched_mode1.json` | get=watched_mode1 | all (19) |
| `api_whim_markread.json` | get=whim_markread | get=whim&whimid=1 → bare envelope |
| `api_whims.json` | get=whims | get=whims → bare envelope (whims removed) |

## html/
| File | URL / content | Notes |
|---|---|---|
| `forum1_pm_html.html` | /forum/1 — Private Messages, "Not signed in" | 14461 bytes |
| `forum35_feedback_html.html` | /forum/35 — "Not signed in" | 14462 bytes |
| `forum7_g179.html` | /forum/7?g=179 — group page, select[name=g] | 68295 bytes |
| `forum92_p1.html` | /forum/92 — list, 50 rows: 48 threads, 1 pointer, 1 deleted, select[name=p], no groups | 66203 bytes |
| `forum92_p2.html` | /forum/92?p=2 — 2 closed | 67558 bytes |
| `forum_index.html` | / — forum index, 50 public forums | 41337 bytes |
| `popular.html` | /forum/?action=popular_views — 36 sections, 183 rows, 2 sticky | 214601 bytes |
| `search_forum92_nbn.html` | legacy threads_search URL, redirected | 72954 bytes |
| `search_forum92_title_only.html` | /search?q=nbn&f=92&w=t | 72971 bytes |
| `search_nbn.html` | /search?q=nbn (after 302) — relevance view, 50 results | 76279 bytes |
| `search_nbn_after_redirect.html` | same page captured via network log | 76279 bytes |
| `search_nbn_listview.html` | /forum/?action=search&q=nbn&w=t&o=list — table, 100 rows (what 2.14.12 parsed) | 136895 bytes |
| `search_nbn_p2.html` | /search?q=nbn&p=2 | 76295 bytes |
| `search_noresults.html` | /search with no hits | 29627 bytes |
| `thread_3pxj858k_p2.html` | ?p=2 — page 2 of 13, 3 edited, 5 OP, 17 quote refs, 1 list | 96365 bytes |
| `thread_97r5wzn3_last.html` | ?p=-1 — last page, 17 posts | 80400 bytes |
| `thread_97r5wzn3_p1.html` | /thread/97r5wzn3 — 20 posts, 8 edited, 8 OP, no public notebar | 91473 bytes |
| `thread_archive_numeric.html` | /archive/1000422 — whole thread, 48 posts, no paging | 161054 bytes |
| `thread_big_9nznpky9_last.html` | page 2663 of 2663, 9 posts, #top_notebar_public | 53299 bytes |
| `thread_big_9pl2kzy9_last.html` | large thread last page | 91811 bytes |
| `thread_big_9w88m1qr_last.html` | 1-page thread (popular row mismatch) | 33828 bytes |
| `thread_big_9w88m1qr_p1.html` | same | 33828 bytes |
| `thread_numeric_1000422.html` | /thread/1000422 — legacy numeric id still resolves | 94400 bytes |
| `thread_old_url_1000422.html` | forum-replies.cfm?t=1000422 — identical after redirect | 94400 bytes |
| `thread_private_feedback_3nzn4p83.html` | private thread — "Not signed in" | 14468 bytes |
| `thread_private_probe_35.html` | /forum/35 — "Not signed in" | 14462 bytes |
| `thread_sticky_3mnkq4v9.html` | sticky, 1211 pages, parts notebar (3 parts) | 90515 bytes |
| `thread_sticky_3q6vxnj9.html` | sticky | 95098 bytes |
| `user_30703.html` | /user/30703 — recent activity + about table | 14971 bytes |
| `whirlpool_skin_script.js` | https://whirlpool.net.au/skin/script.js — contains /forum/ajax.cfm actions, pagination builder | 148586 bytes |

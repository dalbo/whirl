#!/bin/bash
# builds api.json from the key in ~/.config/whirldroid/apikey (never printed)
set -e
K=$(tr -d ' \n\r' < ~/.config/whirldroid/apikey)
B="https://whirlpool.net.au/api/?key=$K&output=json"
cat > "$1" <<JSON
[["api_user","$B&get=user"],
 ["api_url","$B&get=url"],
 ["api_forum","$B&get=forum"],
 ["api_news","$B&get=news"],
 ["api_recent_14d","$B&get=recent&recentdays=14"],
 ["api_recent_120d","$B&get=recent&recentdays=120"],
 ["api_watched_mode1","$B&get=watched&watchedmode=1"],
 ["api_watched_mode0","$B&get=watched&watchedmode=0"],
 ["api_threads_default","$B&get=threads"],
 ["api_threads_forum92","$B&get=threads&forumids=92&threadcount=50"],
 ["api_contacts","$B&get=contacts"],
 ["api_whims","$B&get=whims"],
 ["api_combined","$B&get=forum+news+recent+watched"],
 ["api_badkey","https://whirlpool.net.au/api/?key=000000-000000-000&output=json&get=forum"]]
JSON
echo "api list written to $1 ($(grep -c '^ *\[' "$1") calls)"

#!/bin/bash
# builds api_actions.json: the watched-thread commands (Phase 3 task 1). These CHANGE the key owner's watched list:
# run only with the owner's agreement and a thread they are happy to watch and unwatch (default 3pxj858k, ends unwatched).
# Key read from ~/.config/whirldroid/apikey, never printed.
set -e
T=${2:-3pxj858k}
K=$(tr -d ' \n\r' < ~/.config/whirldroid/apikey)
B="https://whirlpool.net.au/api/?key=$K&output=json"
W="$B&get=watched&watchedmode=1"
cat > "$1" <<JSON
[["api_cmd_watchedadd","$B&watchedadd=$T"],
 ["api_cmd_after_add","$W"],
 ["api_cmd_watchedread","$B&watchedread=$T"],
 ["api_cmd_after_read","$W"],
 ["api_cmd_watchedremove","$B&watchedremove=$T"],
 ["api_cmd_after_remove","$W"],
 ["api_cmd_watchedadd_with_get","$W&watchedadd=$T"],
 ["api_cmd_watchedremove_with_get","$W&watchedremove=$T"],
 ["api_cmd_watchedadd_bogus","$B&watchedadd=zzzzzzzz"],
 ["api_cmd_watchedremove_bogus","$B&watchedremove=zzzzzzzz"],
 ["api_cmd_final","$W"]]
JSON
echo "action list written to $1 ($(grep -c '^ *\[' "$1") calls)"

#!/bin/bash
# menu.sh "<row text>" "<menu item>" [shot-name]
# Long-presses a row, then taps an item in its menu (a pop-up window, so focus is checked only before the press).
. "$(dirname "$0")/env.sh"
in_whirl || exit 1
dump; xy=$(centre "$1")
[ -z "$xy" ] && { echo "Row not found: $1" >&2; exit 1; }
adbs shell input swipe $xy $xy 900; sleep 1.2
dump; item=$(centre "$2")
[ -z "$item" ] && { echo "Menu item not found: $2; the menu has:" >&2; grep -o ' text="[^"]\+"' "$SHOTS/ui.xml" >&2; exit 1; }
adbs shell input tap $item; sleep 1.2
[ -n "$3" ] && shot "$3"
exit 0

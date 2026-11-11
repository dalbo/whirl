#!/bin/bash
# tap.sh "<text or content-desc>" [shot-name]
# Taps the first node labelled exactly so, then optionally screenshots. Refuses unless Whirl has focus, so a call or a
# chat bubble that pops up never gets the tap. Menu labels: "Open menu" (drawer), "Back", "More" (overflow).
. "$(dirname "$0")/env.sh"
in_whirl || exit 1
dump; xy=$(centre "$1")
[ -z "$xy" ] && { echo "Not found: $1" >&2; exit 1; }
adbs shell input tap $xy; sleep 1.5
[ -n "$2" ] && shot "$2"
exit 0

#!/usr/bin/env bash
# Puts VERSION_CODE, already on the alpha track, on the production track (promote.yml). One Play "edit": open it, check,
# change production, then commit it (or, with CHECK_ONLY=true, validate and discard it).
# Env: PLAY_TOKEN (androidpublisher access token), VERSION_CODE, ROLLOUT (1-100), CHECK_ONLY. Argument: release notes.
set -euo pipefail
notes_file=$1
API=https://androidpublisher.googleapis.com/androidpublisher/v3/applications/au.howesthat.whirl

play() { # method path [json body]
  local out status data=()
  out=$(mktemp)
  [ $# -ge 3 ] && data=(--data "$3")
  status=$(curl -sS -o "$out" -w '%{http_code}' -X "$1" "$API$2" -H "Authorization: Bearer $PLAY_TOKEN" \
    -H 'Content-Type: application/json' "${data[@]}")
  if [ "$status" -ge 300 ]; then
    echo "Play API $1 $2 failed ($status): $(jq -r '.error.message // .' "$out")" >&2
    return 1
  fi
  cat "$out"
}

edit=$(play POST /edits '{}' | jq -r .id)
discard() { play DELETE "/edits/$edit" >/dev/null || true; }
trap discard ERR

releases() { jq -r '[.releases[]? | "\(.status) \(.versionCodes // [] | join(",")) \(if .userFraction then "(\(.userFraction * 100 | round)%)" else "" end)"] | join("; ") | if . == "" then "none" else . end'; }

alpha=$(play GET "/edits/$edit/tracks/alpha")
echo "alpha: $(releases <<< "$alpha")"
jq -e --arg c "$VERSION_CODE" '[.releases[]? | .versionCodes[]?] | index($c)' <<< "$alpha" >/dev/null ||
  { echo "Version code $VERSION_CODE is not on the alpha track: tag and release it first" >&2; discard; exit 1; }
production=$(play GET "/edits/$edit/tracks/production")
echo "production before: $(releases <<< "$production")"

if [ "$ROLLOUT" -eq 100 ]; then rollout='{"status":"completed"}'
else rollout=$(jq -n --argjson p "$ROLLOUT" '{status:"inProgress", userFraction:($p / 100)}'); fi
body=$(jq -n --arg c "$VERSION_CODE" --rawfile notes "$notes_file" --argjson r "$rollout" \
  '{track:"production", releases:[{versionCodes:[$c], releaseNotes:[{language:"en-AU", text:$notes}]} + $r]}')
# Each call is its own assignment: a failure inside "$(...)" in an echo's arguments would not stop the script.
production=$(play PUT "/edits/$edit/tracks/production" "$body")
echo "production after: $(releases <<< "$production")"

if [ "$CHECK_ONLY" = true ]; then
  play POST "/edits/$edit:validate" >/dev/null
  discard
  echo "Check only: Play accepted the change; nothing was committed."
else
  play POST "/edits/$edit:commit" >/dev/null
  echo "Committed: version code $VERSION_CODE goes to production at $ROLLOUT% (after Google's review, if any)."
fi

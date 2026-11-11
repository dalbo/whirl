#!/bin/bash
# break-key.sh: turns the debug app's stored API key into one Whirlpool rejects (every digit 0, same length), to see
# the "key not accepted" banner and enter the real key again. Never prints or keeps the key: the file is read through
# a pipe and only the broken copy touches disk. Stops the app first, since DataStore holds the file while it runs.
. "$(dirname "$0")/env.sh"
set -euo pipefail
FILE=files/datastore/account.preferences_pb
TMP=/data/local/tmp/whirl-account.pb
adbs shell am force-stop "$PKG"
adbs exec-out run-as "$PKG" cat "$FILE" | python3 -c '
import re, sys
data = sys.stdin.buffer.read()
# The key is digits in dash-separated groups; zero the digits so the length (and the protobuf around it) stays the same.
broken, n = re.subn(rb"\d+(?:-\d+){2,}", lambda m: re.sub(rb"\d", b"0", m.group(0)), data)
if n != 1: sys.exit(f"expected one key-shaped value, found {n}; nothing changed")
sys.stdout.buffer.write(broken)
' > "$SHOTS/account-broken.pb"
adbs push "$(wslpath -w "$SHOTS/account-broken.pb")" "$TMP" >/dev/null 2>&1
adbs shell chmod 644 "$TMP"
adbs shell run-as "$PKG" cp "$TMP" "$FILE"
adbs shell rm "$TMP"
rm "$SHOTS/account-broken.pb"
echo "Key broken; open Whirl and refresh Watched to see the banner."

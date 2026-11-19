#!/bin/bash
# state.sh: whether the phone is locked, what has focus, and the debug app's stored key names (never values).
. "$(dirname "$0")/env.sh"
adbs shell dumpsys trust | grep -m1 -o 'deviceLocked=[01]'
adbs shell dumpsys window | grep mCurrentFocus
for f in settings account library; do
    names=$(adbs shell run-as "$PKG" cat "files/datastore/$f.preferences_pb" 2>/dev/null |
        python3 -c "import sys,re; print(' '.join(sorted({k.decode() for k in re.findall(rb'[a-z][a-z_]{2,40}', sys.stdin.buffer.read())})))")
    echo "$f: ${names:-(none)}"
done

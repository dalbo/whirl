# Sourced by the other scripts. Override any of these in the environment.
ADB=${ADB:-/mnt/c/Users/dalli/AppData/Local/Android/Sdk/platform-tools/adb.exe}
# Dallin's Pixel over wireless debugging (its mDNS name; `adb.exe mdns services` if it changes).
SERIAL=${SERIAL:-adb-56031FDAS001RX-jxef5b._adb-tls-connect._tcp}
PKG=${PKG:-au.howesthat.whirl.debug}
# Screenshots and UI dumps; build/ is git-ignored.
SHOTS=${SHOTS:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/build/phone}
mkdir -p "$SHOTS"
adbs() { timeout 30 "$ADB" -s "$SERIAL" "$@"; }

# Dumps the screen's UI tree to $SHOTS/ui.xml.
dump() { adbs shell uiautomator dump /sdcard/ui.xml >/dev/null && adbs shell cat /sdcard/ui.xml > "$SHOTS/ui.xml"; }

# Prints "x y", the centre of the first node whose text or content-desc is exactly $1 (from the last dump).
centre() {
    python3 - "$1" "$SHOTS/ui.xml" <<'P'
import sys, re, html
want, path = sys.argv[1], sys.argv[2]
for m in re.finditer(r'<node [^>]*>', open(path).read()):
    n = m.group(0)
    labels = [html.unescape(re.search(a + r'="([^"]*)"', n).group(1)) for a in (' text', 'content-desc')]
    if want in labels:
        a, b, c, d = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups())
        print((a + c) // 2, (b + d) // 2)
        break
P
}

# Fails unless Whirl's own window (not a call, a chat bubble or the lock screen) has focus.
in_whirl() {
    local focus; focus=$(adbs shell dumpsys window | grep mCurrentFocus)
    echo "$focus" | grep -q "$PKG/" && return 0
    echo "Not in Whirl: $focus" >&2; return 1
}

shot() { adbs exec-out screencap -p > "$SHOTS/$1.png" && echo "$SHOTS/$1.png"; }

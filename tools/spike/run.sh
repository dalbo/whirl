#!/usr/bin/env bash
# Install the spike on the connected phone, run it with the API key, and save results.
# Uses $ADB if set, else WSL's platform-tools adb if it sees a device, else the Windows adb.
# The key is read from ~/.config/whirldroid/apikey and passed as an intent extra; it is never printed.
set -euo pipefail
cd "$(dirname "$0")"

WIN_ADB=/mnt/c/Users/dalli/AppData/Local/Android/Sdk/platform-tools/adb.exe
ADB=${ADB:-$WIN_ADB}
APK=app/build/outputs/apk/debug/app-debug.apk
KEYFILE=~/.config/whirldroid/apikey
OUT=results-$(date +%Y%m%d-%H%M%S).txt

[ -f "$APK" ] || ./gradlew -q assembleDebug
APK_ARG=$APK
[[ "$ADB" == *.exe ]] && APK_ARG=$(wslpath -w "$PWD/$APK")

"$ADB" get-state >/dev/null
"$ADB" install -r "$APK_ARG"
"$ADB" logcat -c
"$ADB" shell am force-stop au.howesthat.whirl.spike
if [ -s "$KEYFILE" ]; then
    "$ADB" shell am start -n au.howesthat.whirl.spike/.SpikeActivity --es key "$(tr -d '[:space:]' < "$KEYFILE")" >/dev/null
else
    "$ADB" shell am start -n au.howesthat.whirl.spike/.SpikeActivity >/dev/null
fi

echo "Running; waiting up to 3 minutes for DONE..."
for _ in $(seq 1 90); do
    if "$ADB" logcat -d -s WhirlSpike:I | grep -q 'DONE'; then break; fi
    sleep 2
done
"$ADB" logcat -d -s WhirlSpike:I | sed -E 's/key=[A-Za-z0-9-]{6,}/key=REDACTED/g' | tr -d '\r' > "$OUT"
cat "$OUT"
echo "Saved to tools/spike/$OUT"

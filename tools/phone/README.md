# Driving the phone

Small scripts for checking the debug app on Dallin's phone over adb (HANDOFF "The phone"). Settings for the adb path,
the phone's serial and the package are in `env.sh` and can be overridden from the environment. Screenshots and UI dumps
go to `build/phone/`.

- `tap.sh "<label>" [shot]`: taps a node by its exact text or content description, then screenshots if named.
- `menu.sh "<row>" "<item>" [shot]`: long-presses a row and picks an item from its menu.
- `state.sh`: whether the phone is locked, what has focus, and the debug app's stored key names. It prints names only,
  never values: the account file holds the API key.

Both tap scripts refuse when Whirl's window is not in front. That has saved a tap landing on an incoming call and in
a chat bubble. The phone locks often: ask Dallin to unlock it rather than changing its settings.

The restore test (`docs/device-checklist.md` v1.9) was run this way on 2026-11-09:
`adb shell bmgr backupnow au.howesthat.whirl.debug`, `adb uninstall`, `adb install` (Auto Backup restores on install),
then `state.sh` and the screens.

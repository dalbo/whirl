# Store graphics

The icon is a speech bubble with a spiral (chosen by Dallin, 2026-10-21; drawn here, MIT like the rest of the repo).
`make_icon.py` is its single source: it writes the launcher foreground (also the themed-icon layer) and gradient
background, the status bar icon, and `icon.html`/`feature.html` for the listing images. Edit it, never the outputs.

    python3 tools/store-graphics/make_icon.py
    cd tools/capture && node ../store-graphics/render.mjs "$PWD/../store-graphics"
    for l in en-AU en-US; do cp ../store-graphics/icon.png ../../fastlane/metadata/android/$l/images/icon.png
      cp ../store-graphics/feature.png ../../fastlane/metadata/android/$l/images/featureGraphic.png; done

`icon.png` is 512×512 full-bleed (Play applies its own mask; a window slightly wider than the launcher's keeps the tail
clear of Play's rounded corners); `feature.png`
is 1024×500. Phone screenshots come from a device (`adb exec-out screencap -p`) and are cropped to at most 2:1, Play's limit.

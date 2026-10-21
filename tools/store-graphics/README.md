# Store graphics

Sources for the Play/F-Droid listing images in `fastlane/metadata/android/en-AU/images/`, drawn from the launcher icon's vector
(`app/src/main/res/drawable/ic_launcher_foreground.xml`, background `#3A5A8C`).

    cd tools/capture && node ../store-graphics/render.mjs "$PWD/../store-graphics"
    cp ../store-graphics/icon.png ../../fastlane/metadata/android/en-AU/images/icon.png
    cp ../store-graphics/feature.png ../../fastlane/metadata/android/en-AU/images/featureGraphic.png

`icon.png` is 512×512 full-bleed (Play applies its own mask, so the W keeps the adaptive icon's proportions); `feature.png`
is 1024×500. Phone screenshots come from a device (`adb exec-out screencap -p`) and are cropped to at most 2:1, Play's limit.

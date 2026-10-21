# F-Droid submission

F-Droid builds Whirl from the source at each `vX.Y.Z` tag. Everything it needs is in this repository:
- `app/version.properties` holds `versionName` and `versionCode`; F-Droid reads it at each tag (`UpdateCheckData`) to
  find new releases, and `release.yml` refuses a tag that does not match it. Bump it in the release PR.
- `fastlane/metadata/android/en-US/` is the listing F-Droid shows (its fallback locale); it is a copy of `en-AU` (the
  Play listing) and CI fails if they differ. Changelogs are `changelogs/<versionCode>.txt`, at most 500 characters.
- No proprietary dependencies (no Google Play Services, Firebase or analytics), and `dependenciesInfo` is off, so the
  APK carries no Google-encrypted dependency blob.
- `au.howesthat.whirl.yml` (this folder) is the metadata file for fdroiddata.

## Steps for Dallin (after v1.0.0 is tagged and released on GitHub)
1. Sign in to <https://gitlab.com> (create an account if needed) and fork <https://gitlab.com/fdroid/fdroiddata>.
2. In the fork, create a branch `au.howesthat.whirl` and add `metadata/au.howesthat.whirl.yml` with the contents of
   `docs/fdroid/au.howesthat.whirl.yml` (Web IDE: **+ → New file**). Commit message: `New app: Whirl`.
3. Open a merge request against `fdroid/fdroiddata` `master`, choose the **App inclusion** template, and tick its
   checklist. Paste this into the description:
   > Whirl is a free, open-source (MIT) Android reader for the Whirlpool forums (whirlpool.net.au), rewritten from the
   > abandoned Whirldroid. It uses the user's own Whirlpool API key and reads public pages; no tracking, analytics,
   > ads or proprietary libraries. NonFreeNet is declared because Whirlpool is a proprietary service. I am the
   > developer. Builds are reproducible from the tag (see `Binaries`), so F-Droid can publish the developer-signed
   > APK, which lets users move between the GitHub, F-Droid and Play builds without reinstalling.
4. The MR's pipeline runs `fdroid lint` and a test build. Reply to the reviewers' comments; the agent can prepare any
   change they ask for. Inclusion usually takes days to weeks; after that, every new tag is picked up automatically
   (`AutoUpdateMode: Version`).

## Reproducibility (checked 2026-10-21)
- Two fresh clones of `main` in different directories built bit-identical unsigned release APKs.
- Tag `v0.2.1` built locally (JDK 21) accepted the signature copied from the GitHub release APK (built in CI on
  Temurin 17) with F-Droid's own tool: `apksigcopier copy whirl-v0.2.1.apk app-release-unsigned.apk out.apk` then
  `apksigner verify out.apk`. That is the check F-Droid runs for `Binaries`. Re-run it before each release if the
  build changes (new AGP, R8 rules, baseline profiles).

## Signing
`AllowedAPKSigningKeys` is the SHA-256 of Whirl's release certificate,
`ebf77bd82e548b81d7a2b8913a5e5cba5bea0624898c89707c24969d89d65ae8` (read from the public v0.2.1 APK with
`apksigner verify --print-certs`). With `Binaries`, F-Droid builds the tag itself, compares its unsigned APK with the
GitHub release APK, and publishes the GitHub one only if they match; otherwise the build fails and nothing ships, so
reproducibility must hold for every release. Without `Binaries` F-Droid would sign with its own key, and users could
not update between the F-Droid build and the others.

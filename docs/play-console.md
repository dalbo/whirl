# Google Play: account, listing and first release

The steps only Dallin can do, in order, with the answers to each Play Console form. Decisions already taken
(2026-10-21): a **personal** developer account; listing name **"Whirl: forum reader"**, with Whirlpool named only in the
description; reviewers get a key from a **separate Whirlpool account**; privacy policy at
<https://dalbo.github.io/whirl/privacy/>; v0.* stay pre-releases on GitHub. Nothing in the app uses a Whirlpool web
session (PLAN.md §5).

Listing text and images live in `fastlane/metadata/android/en-AU/` (copy them in by hand for the first release;
the release workflow can upload later ones, see the end).

## 0. Before Play: talk to Whirlpool
Whirlpool's API terms forbid revenue from API apps (Whirl is free, no ads) and say apps are not endorsed but may be
listed on the 3rd Party Clients wiki page; the community rules let an Australian individual developer start a thread
about their app (PLAN.md §2.1). Two things they do not cover, worth a moderator's view before a public listing: an app
that also reads public forum pages, and an app store listing that names Whirlpool. Draft below ("Note to the
moderators").

## 1. Developer account
1. <https://play.google.com/console/signup> with the Google account that will own Whirl → **Yourself** (personal).
2. Pay the US$25 registration fee; verify identity (government ID) and the contact phone number.
3. Developer name shown on the listing (e.g. "Dallin Howes" or "howesthat") and the public **contact email**
   `whirl@howesthat.net.au` (also on the privacy page); a personal account needs no public postal address.
4. New personal accounts must also confirm access to a real Android device through the Play Console app.

This account also covers Google's developer verification, which applies to sideloaded apps (the GitHub APK) worldwide
from 2027; register the package `au.howesthat.whirl` against it when Play prompts.

## 2. Create the app
**Create app**: name `Whirl: forum reader`, default language English (Australia) – en-AU, **App**, **Free** (it can never
become paid: Whirlpool's API terms), accept the declarations.

## 3. App signing (decide before the first upload)
Use the existing release key as Play's app signing key, so the Play build and the GitHub APK carry the same signature
and people can move between them without uninstalling (if Google generated a new key, they could not update each
other). The same `release.jks` then acts as the upload key, which is what the release workflow signs the AAB with.

**Where it is now (2026-10-21):** not a settings page for a new app, but the **App signing** section at the top of the
first release (create a release on a track → **Change app signing key** → the Java keystore export option). Play offers
`pepk.jar` and an encryption public key; from Claude Code's `!` prompt (no terminal) pepk cannot prompt for passwords,
so pass them from the signing file:

    cd /mnt/c/Users/dalli/Downloads && set -a && . ~/.config/whirldroid/release-signing.env && set +a && \
      java -jar pepk.jar --keystore=$HOME/.config/whirldroid/release.jks --alias="$SIGNING_KEY_ALIAS" \
      --keystore-pass="$SIGNING_STORE_PASSWORD" --key-pass="$SIGNING_KEY_PASSWORD" --output=whirl-signing-key.zip \
      --include-cert --rsa-aes-encryption --encryption-key-path=encryption_public_key.pem

Upload the zip, then delete it. Its certificate must be SHA-256 `ebf77bd8…65ae8` (the GitHub releases'; checked
2026-10-21), and App integrity → App signing should show the same fingerprint afterwards.

## 4. Store presence → Main store listing
- App name: `fastlane/metadata/android/en-AU/title.txt`
- Short description: `short_description.txt`; full description: `full_description.txt`
- App icon: `images/icon.png` (512×512); feature graphic: `images/featureGraphic.png` (1024×500)
- Phone screenshots: `images/phoneScreenshots/1.png`–`6.png` in order (thread, forum, forums, popular, news, thread in
  dark theme; 1008×2016, Pixel 9 Pro XL, cropped to Play's 2:1 limit)
- Category: **Social** (Whirlpool is a discussion community); tags: forums, community, broadband
- Contact: email `whirl@howesthat.net.au`, website `https://dalbo.github.io/whirl/`
- Privacy policy: `https://dalbo.github.io/whirl/privacy/`

## 5. Policy → App content
| Form | Answer |
|---|---|
| Privacy policy | `https://dalbo.github.io/whirl/privacy/` |
| Ads | No ads |
| App access | **All or some functionality is restricted.** Instructions: "Whirl reads the Whirlpool forums with a personal API key. On the first screen choose Log in and paste this key: `<key of the review account>`. No password is needed. Whirlpool → Account settings → API key is where users find their own." Give the key of the separate review account here only, never in the repo. |
| Content rating (IARC) | Category: **Social / communication** style app. Violence, sex, language, drugs, gambling: none produced by the app. "Users can interact or exchange content": **No** in the app (posting happens on Whirlpool's website in the browser), but it **displays user-generated content** from a moderated forum; say so where asked. Expect a teen-level rating. |
| Target audience | **18 and over** (keeps Whirl outside the Families policy; Whirlpool is a general-audience adult community). Not appealing to children: yes. |
| News apps | **No** (industry news is one screen, not the app's purpose). |
| Data safety | See below. |
| Government, financial, health, COVID-19 | None / not applicable. |
| Advertising ID | Not used (no `AD_ID` permission). |
| Foreground services | None (no foreground service in the manifest; WorkManager's is removed). |
| Child safety standards (required because the category is Social) | Published standards: `https://dalbo.github.io/whirl/child-safety/`. In-app mechanism: **Yes** (each post's menu → *Report to Whirlpool moderators*, which opens Whirlpool's report form; About → *Report a concern*, which emails the developer). Point of contact: **Dallin Howes**, `whirl@howesthat.net.au`. Needs v1.0.1 or later in the reviewed build. |

### Data safety answers
Play counts any data sent off the device as **collected**, even straight to a third party; sending it to Whirlpool is
not **sharing**, because it is a user-initiated transfer the user expects (Play Console help, "Provide information for
Google Play's Data safety section", read 2026-10-21).
- Does the app collect or share any required user data types? **Yes.**
- Is all collected data encrypted in transit? **Yes** (HTTPS only).
- Do you provide a way to request deletion? **Yes**: log out in Settings or uninstall; the developer holds no data.
- Data types:
  - **Personal info → User IDs**: collected, not shared, **required**, purpose **App functionality** (the API key and the
    Whirlpool account name and number it belongs to).
  - **App activity → App interactions**: collected, not shared, **required**, purpose **App functionality** (watching,
    unwatching and marking threads read are sent to Whirlpool).
  - Nothing else: no location, contacts, device IDs, analytics, crash logs or diagnostics.

## Status
- 2026-10-21: account verified; app created; `v1.0.0` (10000) released to **Internal testing** (testers must be on the
  internal list for the opt-in link to work). Internal testing does not count towards the 14-day requirement: next,
  promote the same build to **Closed testing → Alpha**.

## 6. Closed testing (personal accounts: 12 testers for 14 days)
1. **Testing → Closed testing**: use the built-in track (**Closed testing – Alpha**, API name `alpha`, which the release
   workflow uploads to). Countries: Australia (add others if testers ask).
2. Testers: a Google Group (e.g. `whirl-testers@googlegroups.com`) is easiest; testers join the group, then open the
   opt-in link from the track.
3. **Create release** → upload `whirl-vX.Y.Z.aab` from the GitHub Release (the first upload must be manual), release
   notes from `changelogs/<versionCode>.txt`, roll out to the track.
4. At least **12 testers opted in for 14 consecutive days**; someone who leaves and rejoins restarts their 14 days.
   Recruit 15–20 to be safe, and ask them to actually use it: Google weighs engagement and feedback when you apply.
5. After 14 days: **Dashboard → Apply for production**, answering the questions about the test.

### Tester post (Android › Apps forum, where the Whirldroid thread is; the community rules allow this for Australian individual developers)
> **Whirl: a new open-source Android app for Whirlpool (testers wanted)**
>
> Whirldroid hasn't been updated since 2021, so I've rewritten it for current Android as Whirl: free, open source (MIT),
> no ads or tracking, using your own API key. It does forums, watched threads with notifications, recent/popular,
> native thread reading with quotes and spoilers, news, search and offline reading. Posting and private forums still open
> in your browser.
>
> I'd like to get it onto Google Play, which needs 12+ people in a 14-day closed test. If you'd use it day to day and
> tell me what's broken or missing, join `<group link>` and then opt in at `<opt-in link>`. Source and APKs:
> github.com/dalbo/whirl. Feedback here or on GitHub issues is very welcome.

### Note to the moderators (before the public listing)
> Hi, I'm d@llin (#216903). I've written Whirl, a free, open-source (MIT) Android reader for Whirlpool that replaces
> Whirldroid. It uses the API with each user's own key for watched threads, news and actions, and reads public forum and
> thread pages for display, at about the rate a person browses (no background scraping; notifications use one
> `get=watched` call per interval). It never logs in to the site: posting, private forums and PMs open in the user's
> browser. It's free with no ads, analytics or revenue.
>
> Before I publish it on Google Play I wanted to check: (1) are you comfortable with an app reading public pages this
> way, (2) is "an unofficial client for the Whirlpool forums" in the store description OK, and (3) may I start a
> thread in Android to find testers? I'd also like it listed on the 3rd Party Clients page once it's out.

## 7. Automating later releases (keyless)
Each tag's AAB is uploaded by `release.yml` to the `alpha` track (`PLAY_TRACK`; switch it to `production` after Play
grants production access), with the R8 mapping file and `fastlane/metadata/android/en-AU/changelogs/<versionCode>.txt`
as release notes. A tag without that file fails before anything reaches Play. The workflow signs in to Google Cloud with
GitHub's OIDC token (**Workload Identity Federation**): there is no service-account key anywhere. (Dallin's Cloud
organisation blocks key creation, `iam.managed.disableServiceAccountKeyCreation`, 2026-10-22; keyless is better anyway.)
Without the two repository variables below, the Play steps are skipped.

1. Google Cloud console, project `whirl-release`: **APIs & Services → Library → Google Play Android Developer API →
   Enable**; **IAM & Admin → Service accounts → Create service account** `whirl-github-release`, no roles. (No key.)
2. Play Console → **Users and permissions → Invite new users**: the service account's email, App permissions → Whirl →
   **Release apps to testing tracks** (add production later) → Invite.
3. Open **Cloud Shell** (the `>_` icon at the top of the Cloud console) and paste:

       PROJECT_ID=whirl-release
       SA=whirl-github-release@$PROJECT_ID.iam.gserviceaccount.com
       gcloud config set project $PROJECT_ID
       gcloud services enable iamcredentials.googleapis.com sts.googleapis.com androidpublisher.googleapis.com
       gcloud iam workload-identity-pools create github --location=global --display-name="GitHub Actions"
       gcloud iam workload-identity-pools providers create-oidc whirl --location=global --workload-identity-pool=github \
         --display-name="dalbo/whirl" --issuer-uri="https://token.actions.githubusercontent.com" \
         --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref" \
         --attribute-condition="assertion.repository=='dalbo/whirl' && assertion.ref.startsWith('refs/tags/v')"
       PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')
       gcloud iam service-accounts add-iam-policy-binding $SA --role=roles/iam.workloadIdentityUser \
         --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository/dalbo/whirl"
       echo "PLAY_WIF_PROVIDER=projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/providers/whirl"
       echo "PLAY_SERVICE_ACCOUNT=$SA"

   Only GitHub Actions runs of `dalbo/whirl` on a `v…` tag can obtain a token, and only for this service account.
4. Set the two printed values as repository **variables** (not secrets; neither is sensitive):
   `gh variable set PLAY_WIF_PROVIDER --body "<value>"` and `gh variable set PLAY_SERVICE_ACCOUNT --body "<value>"`.
5. The next tag (a new versionCode; Play refuses one it already has) uploads to Alpha. A failure at "Sign in to Google
   Cloud" is the federation; at "Upload to Google Play" with 403, the Play Console permission; "Google Play Android
   Developer API has not been used in project … or it is disabled", step 1's API (v1.1.0 hit this, 2026-10-23).
6. **Re-running a failed release**: "Re-run failed jobs" on the tag's run. "Publish GitHub Release" skips a release that
   already exists, so the run goes on to Play. (Tags before v1.1.1 lack that skip: delete the GitHub release first, as
   for v1.1.0, whose rebuild had identical checksums.) Keyless upload first worked on v1.1.0.

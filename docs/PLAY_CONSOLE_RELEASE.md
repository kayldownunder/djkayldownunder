# DJ KaylDownUnder — Play Console release notes

## Build and signing

The Android app ID is `com.k.hosken.djkayldownunder`.  The current maintenance release is
version 1.1, using version code 2 and targeting API 37.

1. Create a private upload key (do not use the Android debug key), for example:
   `keytool -genkeypair -v -keystore release-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000`
2. Copy `keystore.properties.example` to `keystore.properties` and replace every value.
   The properties file and keystore are ignored by Git.
3. Build the signed bundle with `gradlew.bat :app:bundleRelease`.
4. Upload `app/build/outputs/bundle/release/app-release.aab` to Play Console. Enrol in
   Play App Signing when prompted, then keep the upload key backed up securely.

**Never regenerate `release-upload.jks` once it's been used for a real upload.** Doing so
silently changes the certificate fingerprint Play expects, and every later bundle signed with
the new file will be rejected on upload until you go through Play Console's "Request upload
key reset" flow (Protected with Play → App signing → Request upload key reset), which needs
manual Google review and is not instant. This happened once already on 2026-09-22 — the
keystore was regenerated after the 1.0 upload, orphaning it, and a reset had to be requested
using the (different again) key that was actually on disk at the time. Back the file up
somewhere outside the repo the moment it's created.

## Required Play Console declarations

The app is a local music player. It has no account system, advertising SDK, analytics SDK,
or server operated by the developer.

* **Countries/regions:** make the app available in **all countries/regions** unless a legal,
  licensing, or support requirement says otherwise. This is the default distribution choice for
  this app and should be repeated for every future application in this developer account.

* **App access:** all features are available without login.
* **Ads:** no ads.
* **Content rating:** complete the Music / Audio questionnaire accurately.
* **Target audience:** choose the actual intended age groups; do not select children unless
  the store listing and app are designed for them.
* **Foreground services:** declare `mediaPlayback` for music playback and `dataSync` only
  for the user-initiated “Fetch song info” operation. Supply the reviewer steps below.
* **Data safety:** the app reads audio selected by the user and stores preferences and cached
  metadata on-device. When the user explicitly starts metadata fetching, a track name is sent
  to MusicBrainz, the Cover Art Archive, Apple iTunes Search, and Deezer to find matching
  metadata/artwork. No data is sent to a developer-controlled server. Recheck each selected
  answer in Play Console against the final build and the linked privacy policy. The app uses the
  Storage Access Framework, so it does not request broad audio-library access.

## Reviewer instructions

1. Open the app and choose a music folder when prompted.
2. Open an album and start a track to verify the persistent media-playback notification.
3. To test the data-sync foreground service, open Settings and choose Fetch Metadata for a
   playlist or Fetch Metadata for All Playlists. The ongoing “Fetching song info” notification
   remains visible until the operation completes.

## Store assets

All in place as of 2026-09-23, in `docs/store-assets/`:

* Privacy policy published and live at `https://kayldownunder.github.io/djkayldownunder/`
  (verified returning HTTP 200) — this is the URL set in Play Console's Store settings.
* App icon: `app-icon-512.png` — the app's actual launcher icon
  (`app/src/main/ic_launcher-playstore.png`), not a placeholder.
* Feature graphic: `feature-graphic.png` — generated to match the launcher icon (same icon
  centred on a blurred/darkened version of itself as the background). Labelled as
  AI-generated in the store listing, along with the app icon.
* Phone screenshots: `phone-1-library.png`, `phone-2-now-playing.png`, `phone-3-playlist.png` —
  real captures from a debug build running on the Pixel_9 emulator, with placeholder/demo
  tracks and generated cover art (not real user music). 1080×1920, meets the 3-screenshot
  minimum (Play requires 2+; 4+ only unlocks promotion eligibility, not required here).

For paste-ready listing text and the remaining closed-test Console workflow, see
`docs/CLOSED_TESTING.md`.

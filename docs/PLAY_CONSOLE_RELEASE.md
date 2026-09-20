# DJ KaylDownUnder — Play Console release notes

## Build and signing

The Android app ID is `com.k.hosken.djkayldownunder`.  Release version 1.0 uses
version code 1 and targets API 37.

1. Create a private upload key (do not use the Android debug key), for example:
   `keytool -genkeypair -v -keystore release-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000`
2. Copy `keystore.properties.example` to `keystore.properties` and replace every value.
   The properties file and keystore are ignored by Git.
3. Build the signed bundle with `gradlew.bat :app:bundleRelease`.
4. Upload `app/build/outputs/bundle/release/app-release.aab` to Play Console. Enrol in
   Play App Signing when prompted, then keep the upload key backed up securely.

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
  answer in Play Console against the final build and the linked privacy policy.

## Reviewer instructions

1. Open the app and choose a music folder when prompted.
2. Open an album and start a track to verify the persistent media-playback notification.
3. To test the data-sync foreground service, open Settings and choose Fetch Metadata for a
   playlist or Fetch Metadata for All Playlists. The ongoing “Fetching song info” notification
   remains visible until the operation completes.

## Store assets still needed

Before submitting, provide a publicly hosted privacy-policy URL, support email, short and full
descriptions, a 512×512 app icon, a 1024×500 feature graphic, and at least two truthful phone
screenshots. Use the draft in `docs/PRIVACY_POLICY.md` as the policy content after publishing it
at a stable public URL.

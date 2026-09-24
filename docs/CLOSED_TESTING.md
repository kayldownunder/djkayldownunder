# Closed testing setup

Use this checklist after the signed `app-release.aab` has been uploaded to the **Closed testing**
track. The package name is `com.k.hosken.djkayldownunder`; the maintenance release is version
1.1 with version code 2.

The store listing is fully set up as of 2026-09-23 (icon, feature graphic, screenshots, text) —
see `docs/PLAY_CONSOLE_RELEASE.md` for what's where. The **Closed testing – Alpha** track has a
draft release with countries/regions (all 177 available) and testers (the existing "Play Store
Email Test List", 48 users — don't create a duplicate list) already saved, and release notes for
1.1 filled in. It is blocked on the app-release.aab upload: the local `release-upload.jks` was
regenerated at some point after the 1.0 upload and no longer matches the certificate Play
expects, so an upload-key reset was requested via Protected with Play → App signing on
2026-09-23 (reason: lost key) and is pending Google's manual review. Once approved, rebuild with
`gradlew.bat :app:bundleRelease` and upload the fresh bundle to the existing draft release —
don't reuse the old orphaned `app-release.aab`, it won't match either key.

## Store listing copy

**App name**

DJ KaylDownUnder

**Short description**

Your music folders, your playlists, your player.

**Full description**

DJ KaylDownUnder is a local music player for the folders you choose.

Browse your music folder by folder, play playlists from your own audio files, and keep listening
with background playback and notification controls. Mark favourites, skip tracks you do not want
in the queue, create custom playlists, and resume where you left off.

When a track has missing details, you can choose to fetch metadata and album artwork. This is
optional and uses public music catalogues; no account is required.

Your music library stays on your device. DJ KaylDownUnder has no ads and no analytics SDK.

## Console checklist

1. In **Testers**, create an email list (for example, `closed-testers`) and add only people who
   have agreed to test. Save the track and copy its opt-in link. *(Already done for Alpha —
   the "Play Store Email Test List" with 48 users is selected; only copy the opt-in link.)*
2. In **Countries/regions**, select the intended test countries. Use all countries/regions if the
   group is distributed globally and there is no legal or support restriction. *(Already done
   for Alpha — all 177 available countries/regions are selected.)*
3. Under **Create new release**, upload the signed bundle, use the release notes below, save, and
   roll out to the closed-test track. *(Blocked on the upload-key reset — see above.)*
4. Send testers the opt-in link and ask them to install from Play, rather than by sideloading an
   APK. They need a Google account included in the tester list.
5. Complete every item in the Play Console dashboard before applying for production access. Keep
   the privacy-policy URL public and stable; the repository's `index.html` is ready to publish as
   that page.

## Release notes

Maintenance release 1.1: fixed Random Skip All playback continuing after the app reconnects to
the media service. Please test choosing a music folder, playback controls, background playback,
playlists, favourites, and optional metadata fetching. Report any crashes, missing tracks, or
incorrect metadata.

## Tester message

Thanks for testing DJ KaylDownUnder. Open the opt-in link below while signed in with this email
address, then install the app from Google Play:

`[paste closed-test opt-in link here]`

Please try selecting a folder containing a few audio files, playing music with the app in the
background, and optionally fetching metadata. Send feedback to kayl.hosken@gmail.com with your
phone model, Android version, and the steps that led to any problem.

## Tester acceptance checks

* Select a music folder and confirm its tracks and subfolders appear.
* Start a track, lock the phone or leave the app, and verify playback and notification controls
  continue to work.
* Pause, skip, favourite, and mark a track skipped; relaunch the app and verify the expected
  state remains.
* Run one optional metadata fetch and confirm the progress notification disappears when complete.
* Revoke notification permission once, if practical, and report whether the app explains the
  resulting playback-control limitation clearly.

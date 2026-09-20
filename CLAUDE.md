# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

"DJ Kayl From Down Under" is a single-module Android music player app (package
`com.k.hosken.djkayldownunder`) built with Jetpack Compose and Media3/ExoPlayer. It plays
audio files directly from a user-chosen folder tree (accessed via Storage Access Framework) rather
than relying on the system's MediaStore, and it enriches tracks with metadata/artwork pulled from
free online sources when local tags are missing.

## Build, lint, and test commands

Windows (this repo is developed on Windows; use `gradlew.bat`, not `./gradlew`):

```
gradlew.bat assembleDebug          # build the debug APK
gradlew.bat installDebug           # build and install on a connected device/emulator
gradlew.bat test                   # run JVM unit tests (app/src/test)
gradlew.bat connectedAndroidTest   # run instrumented tests on a device/emulator (app/src/androidTest)
gradlew.bat lint                   # run Android Lint
```

Run a single JVM test class or method:

```
gradlew.bat test --tests "com.k.hosken.djkayldownunder.SomeTest"
gradlew.bat test --tests "com.k.hosken.djkayldownunder.SomeTest.someMethod"
```

There is currently no CI config and no meaningful test suite beyond the default
`ExampleUnitTest`/`ExampleInstrumentedTest` templates — most logic lives in repositories and
ViewModels that touch `Context`/SAF/network directly, so verifying behavior generally means
building and running the app rather than running unit tests.

## Architecture

**No DI framework, no Room, no Retrofit.** Everything is hand-rolled: plain repository classes
constructed with a `Context`, `SharedPreferences`/flat JSON files for persistence, `HttpURLConnection`
+ `org.json` for network calls, and ViewModels instantiated via the default `viewModel()` /
`AndroidViewModel` factories (no `ViewModelProvider.Factory` classes).

### Navigation & shared state

`MainActivity` just hosts `AppNavHost` (`navigation/AppNavHost.kt`), which is the composition
root for the whole app:
- All ViewModels (`MusicLibraryViewModel`, `PlayerViewModel`, `MetadataViewModel`, `ThemeViewModel`,
  `SkipListViewModel`, `ViewPreferencesViewModel`, `FavoritesViewModel`, `CustomPlaylistViewModel`)
  are created once here with `viewModel()` and threaded down as constructor params to every screen —
  there is no shared ViewModel scoping trick, just passing references through composables.
- `Routes` defines route constants plus `Routes.folderRoute(uri, name)`, which URL-encodes a folder
  `Uri`/name pair into the `folder/{encodedUri}/{encodedName}` pattern used for recursive folder
  browsing (the Library tab navigates into subfolders one level at a time via this route).
- `hideBottomBarRoutes` controls which destinations (Player, Skip Review, Search, Create Playlist)
  render full-screen without the mini-player/bottom nav.

### Music library model (`data/MusicModels.kt`, `data/MusicFolderRepository.kt`)

- A `Playlist` = one folder that directly contains audio files; a `Track` = one audio file. There is
  no album/artist model — the folder *is* the playlist.
- The user picks a root folder once via SAF (`persistRootFolder`/`getSavedRootFolder` in
  `MusicFolderRepository`, backed by `djkayl_prefs` SharedPreferences + a persisted URI permission).
- `MusicFolderRepository` has two distinct traversal modes:
  - `scanPlaylists(rootUri)` — recursively walks the *entire* tree and flattens every
    audio-containing folder into a `Playlist`. Used by Search, Skip Review, and "Fetch Metadata for
    All Playlists" — anywhere the app needs the complete flat list.
  - `listFolderLevel(folderUri)` — lists just one folder's immediate children as
    `FolderBrowseItem.SubFolder` (has subfolders, navigate deeper) or
    `FolderBrowseItem.LeafPlaylist` (audio files directly inside, ready to play). Used by the
    Library tab's level-by-level `FolderBrowserScreen`.
  - Audio detection (`isAudioFile`) checks mime type first, falling back to extension
    (`SUPPORTED_AUDIO_EXTENSIONS` in `MusicModels.kt`) since some providers report generic mime types.

### Playback (`playback/MusicPlaybackService.kt`, `ui/PlayerViewModel.kt`)

- `MusicPlaybackService` is a thin `MediaSessionService` wrapping a single `ExoPlayer` +
  `MediaSession` — it owns no app logic, just lifecycle plumbing for background playback and the
  system notification/lock-screen controls.
- `PlayerViewModel` connects to it via a `MediaController` (built from a `SessionToken`) and is the
  actual source of playback logic:
  - Maintains `fullTrackList` (every track, for display, including skipped ones — shown with a line
    through) separately from the playable `queue` (skipped tracks filtered out via
    `SkipListRepository`).
  - Polls position every 500ms (`observePosition`) and persists resume progress
    (folder + track + position) via `PlaybackStateRepository` roughly every 5s and on pause/clear.
  - `playPlaylist(playlist, forceRestart, startTrackUri)` resumes from the last saved position by
    default; `forceRestart` or an explicit `startTrackUri` (e.g. un-skipping a track) overrides that.

### Metadata enrichment (`ui/MetadataViewModel.kt`, `data/LocalMetadataReader.kt`, `data/OnlineMetadataFetcher.kt`, `data/TrackMetadata.kt`)

- Resolution order per track: local embedded tags (`LocalMetadataReader`, via
  `MediaMetadataRetriever`) first since it's free; only if that yields nothing useful does it fall
  through to `OnlineMetadataFetcher`, which tries MusicBrainz+CoverArtArchive → iTunes Search →
  Deezer, in that order, returning the first usable hit.
  - Note: `OnlineMetadataFetcher.userAgent` contains a placeholder contact email
    (`your-email@example.com`) required by MusicBrainz's API usage policy — this should be a real
    contact address before relying on the MusicBrainz source in production.
- Results are cached indefinitely in `MetadataStore`, a hand-rolled JSON file
  (`track_metadata.json` in app-private storage) keyed by track URI — never re-fetched once present,
  so there's currently no cache invalidation/refresh path.
- Batch fetch (`fetchMetadataForAllPlaylists`) runs strictly sequentially across playlists/tracks on
  purpose, to avoid hammering the free MusicBrainz API.

### Other per-feature repositories

All follow the same pattern — a plain class taking `Context`, backed by either a dedicated
`SharedPreferences` file or a flat JSON file in `filesDir`, no shared base class or interface:
- `FavoritesRepository`, `SkipListRepository` — per-track boolean flags keyed by folder+track URI,
  in their own SharedPreferences files (`djkayl_skip_list`, etc.).
- `CustomPlaylistRepository` — user-built playlists (hand-picked tracks, not folder-based), JSON in
  `custom_playlists.json`.
- `ViewPreferenceRepository`, `ThemeRepository` — simple single-value SharedPreferences settings
  (playlist card size, theme).
- `PlaybackStateRepository` — last played folder/track/position for resume.

When adding a new piece of persisted state, match this pattern rather than introducing Room/DataStore
unless the user asks for it.

### Permissions & manifest

- SAF (`ACTION_OPEN_DOCUMENT_TREE` + persisted URI permissions) is used for library access, not
  `READ_MEDIA_AUDIO`-driven MediaStore queries — the `READ_MEDIA_AUDIO` permission in the manifest is
  currently unused by the SAF-based folder scanning.
- `POST_NOTIFICATIONS` is requested at runtime from `MainActivity` on API 33+, required for the
  playback foreground notification/lock-screen controls to appear.

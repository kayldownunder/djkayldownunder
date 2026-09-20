package com.k.hosken.djkayldownunder.ui

import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.k.hosken.djkayldownunder.data.PlaybackStateRepository
import com.k.hosken.djkayldownunder.data.Playlist
import com.k.hosken.djkayldownunder.data.SkipListRepository
import com.k.hosken.djkayldownunder.data.Track
import com.k.hosken.djkayldownunder.playback.MusicPlaybackService
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "DJKaylResume"

data class PlayerUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    val queue: List<Track> = emptyList(),         // playable queue - skipped tracks excluded
    val fullTrackList: List<Track> = emptyList(), // every track in the folder, for display
    val currentIndex: Int = -1,
    val currentPlaylistFolderUri: String? = null,
    val isShuffleEnabled: Boolean = false,
    val isShuffleAllActive: Boolean = false  // "Random Skip All Albums" mode - see PlayerViewModel
)

@UnstableApi
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private var controller: MediaController? = null
    private var currentQueue: List<Track> = emptyList()
    private var currentFolderUri: String? = null
    private var currentPlaylist: Playlist? = null

    // "Random Skip All Albums" mode state: the full library to pick from, and the actual
    // sequence of tracks played while the mode has been on (not a pre-shuffled queue - each
    // Next/auto-advance picks fresh) so Previous can walk back through real playback history.
    private var shuffleAllPool: List<Playlist> = emptyList()
    private var shuffleHistory: MutableList<Pair<Track, String>> = mutableListOf()
    private var shuffleHistoryPos: Int = -1

    private val playbackStateRepository = PlaybackStateRepository(application)
    private val skipListRepository = SkipListRepository(application)

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            // TEMPORARY DEBUG LOGGING (TAG) - added to chase down a playback lockup where
            // the player stops responding entirely (no audio, taps do nothing) until the
            // phone is rebooted, reported after moving files / updating metadata. Remove
            // once the cause is confirmed from a captured logcat.
            try {
                controller = controllerFuture.get()
                Log.d(TAG, "MediaController connected")
                attachListener()
                syncStateFromController()
            } catch (e: Exception) {
                Log.e(TAG, "MediaController failed to connect - playback will silently do nothing until this succeeds", e)
            }
        }, MoreExecutors.directExecutor())
        observePosition()
    }

    private fun attachListener() {
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "onIsPlayingChanged isPlaying=$isPlaying playbackState=${controller?.playbackState}")
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                if (!isPlaying) saveProgress()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = controller?.currentMediaItemIndex ?: -1
                val track = currentQueue.getOrNull(index) ?: mediaItem?.let { trackFromMediaItem(it) }
                // Each MediaItem's mediaId carries the URI of the folder it came from (set
                // in playPlaylist/playSingleTrack) - re-reading it on every transition
                // keeps the skip-this-song row correct even when the queue spans multiple
                // folders (shuffle-all), not just the folder active when playback started.
                val folderUriStr = mediaItem?.mediaId?.takeIf { it.isNotBlank() }
                Log.d(TAG, "onMediaItemTransition index=$index track=${track?.displayName} reason=$reason")
                currentFolderUri = folderUriStr ?: currentFolderUri
                _uiState.value = _uiState.value.copy(
                    currentTrack = track,
                    currentIndex = index,
                    durationMs = controller?.duration?.coerceAtLeast(0) ?: 0L,
                    currentPlaylistFolderUri = folderUriStr ?: _uiState.value.currentPlaylistFolderUri
                )
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _uiState.value = _uiState.value.copy(isShuffleEnabled = shuffleModeEnabled)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
                Log.d(TAG, "onPlaybackStateChanged state=$stateName currentTrack=${_uiState.value.currentTrack?.displayName}")
                // "Random Skip All Albums": a track ending naturally (queue is always just
                // the one track while this mode is active, so ending means STATE_ENDED, not
                // an automatic transition to a next queued item) picks another random track,
                // same as pressing Next.
                if (playbackState == Player.STATE_ENDED && _uiState.value.isShuffleAllActive) {
                    playRandomTrackFromPool(pushHistory = true)
                }
            }

            // This is the gap most likely behind "playback locks up and won't respond until
            // reboot": if ExoPlayer hits an unrecoverable error (e.g. a track's content Uri
            // became invalid after being moved/renamed), nothing was previously listening
            // for it - isPlaying just silently goes false with no record of why, indistinguishable
            // from a normal pause. Logged with full detail so a captured logcat pinpoints
            // exactly which track/error caused it.
            //
            // While "Random Skip All Albums" is active this is also the most likely cause of
            // the mode appearing to "just stop" after a while: it plays one random track at a
            // time from the *entire* library, so the odds of eventually landing on a file that
            // was since moved/renamed/deleted (and therefore fails to load) climb the longer
            // it runs. Previously that error only got logged and playback stalled there for
            // good, with no track loaded and nothing left to advance it - since the STATE_ENDED
            // handler above only fires for a track that finishes normally, never for one that
            // errors out. Skip to another random track the same way a natural end-of-track
            // does, so one bad file doesn't kill the whole session.
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(
                    TAG,
                    "onPlayerError code=${error.errorCode} (${error.errorCodeName}) " +
                        "currentIndex=${controller?.currentMediaItemIndex} " +
                        "currentTrack=${_uiState.value.currentTrack?.displayName} " +
                        "currentTrackUri=${_uiState.value.currentTrack?.uri} " +
                        "playWhenReady=${controller?.playWhenReady} " +
                        "playbackState=${controller?.playbackState}",
                    error
                )
                if (_uiState.value.isShuffleAllActive) {
                    Log.d(TAG, "onPlayerError during shuffle-all - skipping to another random track")
                    playRandomTrackFromPool(pushHistory = true)
                }
            }
        })
    }

    /** Best-effort Track reconstructed from a MediaItem's own uri/title - see [syncStateFromController]. */
    private fun trackFromMediaItem(item: MediaItem): Track? {
        val uri = item.localConfiguration?.uri ?: return null
        val title = item.mediaMetadata.title?.toString() ?: uri.lastPathSegment.orEmpty()
        return Track(uri = uri, displayName = title, fileName = title, sizeBytes = 0L, mimeType = null)
    }

    /**
     * Rebuilds UI state directly from whatever the MediaController already has loaded, for
     * when this ViewModel (re)connects to a playback session that was started by an earlier,
     * now-gone instance (e.g. the app process was restarted while background playback kept
     * running in MusicPlaybackService). Without this, opening the Now Playing screen via the
     * dock shortcut right after such a restart showed "Nothing playing" and hid the
     * volume/skip/delete row even though a track was audibly playing - this instance's
     * in-memory currentQueue/currentTrack simply hadn't been populated yet, since that
     * normally only happens via an explicit playPlaylist call or a later transition event.
     * Skipped entirely if playPlaylist/toggleShuffleAllAlbums already populated state first.
     */
    private fun syncStateFromController() {
        val c = controller ?: return
        if (_uiState.value.currentTrack != null || c.mediaItemCount == 0) return

        val items = (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }
        val tracks = items.mapNotNull { trackFromMediaItem(it) }
        val index = c.currentMediaItemIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        val folderUriStr = items.getOrNull(index)?.mediaId?.takeIf { it.isNotBlank() }

        currentQueue = tracks
        currentFolderUri = folderUriStr
        _uiState.value = _uiState.value.copy(
            queue = tracks,
            fullTrackList = tracks,
            currentTrack = tracks.getOrNull(index),
            currentIndex = index,
            currentPlaylistFolderUri = folderUriStr,
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.coerceAtLeast(0),
            isShuffleEnabled = c.shuffleModeEnabled
        )
    }

    /**
     * Polls playback position twice a second while actually playing, and saves resume
     * progress roughly every 5s. Skipped entirely while paused/idle - position doesn't
     * change then anyway, and this state feeds MiniPlayerBar which is visible on almost
     * every screen, so emitting unchanged values would recompose it for nothing.
     */
    private fun observePosition() {
        viewModelScope.launch {
            var tick = 0
            while (true) {
                controller?.let { c ->
                    if (c.isPlaying) {
                        _uiState.value = _uiState.value.copy(
                            positionMs = c.currentPosition.coerceAtLeast(0),
                            durationMs = c.duration.coerceAtLeast(0)
                        )
                        tick++
                        if (tick % 10 == 0) saveProgress()
                    }
                }
                delay(500)
            }
        }
    }

    private fun saveProgress() {
        val folderUri = currentFolderUri
        val trackUri = _uiState.value.currentTrack?.uri?.toString()
        if (folderUri == null || trackUri == null) {
            Log.d(TAG, "saveProgress skipped - folderUri=$folderUri trackUri=$trackUri")
            return
        }
        val pos = controller?.currentPosition ?: 0L
        Log.d(TAG, "saveProgress folder=$folderUri track=$trackUri pos=$pos")
        playbackStateRepository.save(folderUri, trackUri, pos)
    }

    /**
     * Loads a playlist and starts playback. The full track list (including skipped tracks)
     * is kept in fullTrackList for display; the actual playable queue excludes skipped
     * tracks. If this playlist was played before, resumes from the last remembered track
     * and position - unless forceRestart is true or startTrackUri overrides it.
     */
    fun playPlaylist(playlist: Playlist, forceRestart: Boolean = false, startTrackUri: String? = null) {
        currentPlaylist = playlist
        val folderUriStr = playlist.folderUri.toString()
        currentFolderUri = folderUriStr

        val effectiveTracks = playlist.tracks.filterNot {
            skipListRepository.isSkipped(folderUriStr, it.uri.toString())
        }
        currentQueue = effectiveTracks

        var startIndex = 0
        var startPositionMs = 0L

        if (startTrackUri != null) {
            val idx = effectiveTracks.indexOfFirst { it.uri.toString() == startTrackUri }
            if (idx >= 0) startIndex = idx
            Log.d(TAG, "playPlaylist explicit startTrackUri=$startTrackUri -> index=$idx")
        } else if (!forceRestart) {
            val resume = playbackStateRepository.load(folderUriStr)
            Log.d(TAG, "playPlaylist folder=$folderUriStr loaded resume=$resume")
            resume?.let {
                val idx = effectiveTracks.indexOfFirst { t -> t.uri.toString() == it.trackUri }
                Log.d(TAG, "resume track match index=$idx (looking for ${it.trackUri})")
                if (idx >= 0) {
                    startIndex = idx
                    startPositionMs = it.positionMs
                }
            }
        }

        val mediaItems = effectiveTracks.map { track ->
            MediaItem.Builder()
                .setUri(track.uri)
                .setMediaId(folderUriStr)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.displayName)
                        .build()
                )
                .build()
        }
        Log.d(TAG, "setMediaItems count=${mediaItems.size} startIndex=$startIndex startPositionMs=$startPositionMs controllerNull=${controller == null}")
        controller?.apply {
            setMediaItems(mediaItems, startIndex, startPositionMs)
            prepare()
            play()
            // A resume position saved from a previous full play-through can land at or
            // past the track's actual duration - ExoPlayer then reaches STATE_ENDED
            // immediately instead of audibly playing anything, and play() alone can't
            // recover from ENDED (it just sets playWhenReady, which has nothing left to
            // play). Catch that on the very next state change and restart from the top.
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    removeListener(this)
                    if (playbackState == Player.STATE_ENDED) {
                        Log.d(TAG, "playPlaylist landed on STATE_ENDED immediately - restarting from top")
                        seekTo(startIndex, 0L)
                        play()
                    }
                }
            })
        }
        _uiState.value = _uiState.value.copy(
            queue = effectiveTracks,
            fullTrackList = playlist.tracks,
            currentIndex = startIndex,
            currentPlaylistFolderUri = folderUriStr
        )
    }

    /**
     * Toggles Media3's built-in shuffle mode, which randomizes only the playback order
     * within whatever queue is currently loaded (e.g. the tracks of the album/folder
     * playing on the Now Playing screen) - skipNext/skipPrevious then walk that shuffled
     * order automatically.
     */
    fun toggleShuffle() {
        val enabled = !(controller?.shuffleModeEnabled ?: false)
        controller?.shuffleModeEnabled = enabled
        _uiState.value = _uiState.value.copy(isShuffleEnabled = enabled)
    }

    /**
     * Toggles "Random Skip All Albums" mode (the shuffle-all shortcut on the Player,
     * Library, and Play Lists screens). Turning it on never interrupts whatever's already
     * loaded - it just switches Next/Previous/end-of-track over to the random-across-every-
     * album behavior below - except when nothing is loaded yet (e.g. the shortcut is
     * tapped before anything has ever played), in which case it bootstraps by picking a
     * random track itself. Turning it off simply stops applying that behavior going
     * forward; whatever's currently playing keeps playing.
     */
    fun toggleShuffleAllAlbums(playlists: List<Playlist>) {
        // TEMPORARY DEBUG LOGGING (TAG) - this is the only place isShuffleAllActive ever
        // changes, so if the shortcut is observed turning itself off without being tapped,
        // this log line (with a stack trace, since the caller isn't otherwise identifiable)
        // is what will show whether toggleShuffleAllAlbums is really being invoked a second
        // time (e.g. a stray/duplicate tap) versus something else being responsible. Remove
        // once the cause is confirmed from a captured logcat.
        Log.d(
            TAG,
            "toggleShuffleAllAlbums called, currently active=${_uiState.value.isShuffleAllActive}, " +
                "poolSize=${playlists.size}",
            Exception("toggleShuffleAllAlbums call site")
        )
        shuffleAllPool = playlists
        val activating = !_uiState.value.isShuffleAllActive
        _uiState.value = _uiState.value.copy(isShuffleAllActive = activating)
        if (!activating) {
            restoreQueueForCurrentTrack()
            return
        }

        val current = _uiState.value.currentTrack
        if (current != null) {
            shuffleHistory = mutableListOf(current to currentFolderUri.orEmpty())
            shuffleHistoryPos = 0
        } else {
            shuffleHistory = mutableListOf()
            shuffleHistoryPos = -1
            playRandomTrackFromPool(pushHistory = true)
        }
    }

    /**
     * Turning "Random Skip All Albums" off needs to undo what [playSingleTrack] did to the
     * controller/state while it was active: every track change while that mode is on loads
     * the controller with just that one track (see playSingleTrack), collapsing the real
     * queue/fullTrackList down to a single item. Left alone, that breaks end-of-track
     * auto-advance, Next/Previous (nothing else queued to seek to), and the swipe-up
     * playlist sheet (only shows the one track) for the rest of playback. Reloads the actual
     * playlist the current track belongs to (from [shuffleAllPool]) around the current
     * position so normal queue navigation resumes. No-op if the current track wasn't loaded
     * via shuffle-all in the first place (currentPlaylist already set - queue is intact).
     */
    private fun restoreQueueForCurrentTrack() {
        if (currentPlaylist != null) return
        val current = _uiState.value.currentTrack ?: return
        val folderUriStr = currentFolderUri ?: return
        val playlist = shuffleAllPool.find { it.folderUri.toString() == folderUriStr } ?: return

        currentPlaylist = playlist
        val effectiveTracks = playlist.tracks.filterNot {
            skipListRepository.isSkipped(folderUriStr, it.uri.toString())
        }
        currentQueue = effectiveTracks
        val startIndex = effectiveTracks.indexOfFirst { it.uri == current.uri }.coerceAtLeast(0)
        val startPositionMs = controller?.currentPosition?.coerceAtLeast(0) ?: 0L

        val mediaItems = effectiveTracks.map { track ->
            MediaItem.Builder()
                .setUri(track.uri)
                .setMediaId(folderUriStr)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.displayName)
                        .build()
                )
                .build()
        }
        controller?.apply {
            setMediaItems(mediaItems, startIndex, startPositionMs)
            prepare()
            play()
        }
        _uiState.value = _uiState.value.copy(
            queue = effectiveTracks,
            fullTrackList = playlist.tracks,
            currentIndex = startIndex,
            currentPlaylistFolderUri = folderUriStr
        )
    }

    /**
     * Picks a random track from every album in [shuffleAllPool] (skipped tracks excluded
     * per their own folder's skip list, same as a normal playPlaylist) and plays it -
     * avoiding an immediate repeat of the current track when there's more than one
     * candidate. Used by Next and by natural end-of-track while shuffle-all is active. When
     * [pushHistory] is true the pick is appended to [shuffleHistory], discarding any
     * "forward" entries past the current position first (mirrors normal back/forward-stack
     * behavior after a Previous).
     */
    private fun playRandomTrackFromPool(pushHistory: Boolean) {
        val pool = shuffleAllPool.flatMap { playlist ->
            val folderUriStr = playlist.folderUri.toString()
            playlist.tracks
                .filterNot { skipListRepository.isSkipped(folderUriStr, it.uri.toString()) }
                .map { it to folderUriStr }
        }
        if (pool.isEmpty()) return

        val currentUri = _uiState.value.currentTrack?.uri
        val candidates = if (pool.size > 1) pool.filterNot { it.first.uri == currentUri } else pool
        val (track, folderUriStr) = candidates.random()

        if (pushHistory) {
            while (shuffleHistory.size > shuffleHistoryPos + 1) shuffleHistory.removeAt(shuffleHistory.lastIndex)
            shuffleHistory.add(track to folderUriStr)
            shuffleHistoryPos = shuffleHistory.lastIndex
        }
        playSingleTrack(track, folderUriStr)
    }

    /** Loads and plays a single track outside of any playlist context - used by shuffle-all. */
    private fun playSingleTrack(track: Track, folderUriStr: String) {
        currentPlaylist = null
        currentFolderUri = folderUriStr
        currentQueue = listOf(track)

        val mediaItem = MediaItem.Builder()
            .setUri(track.uri)
            .setMediaId(folderUriStr)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.displayName)
                    .build()
            )
            .build()
        controller?.apply {
            setMediaItems(listOf(mediaItem), 0, 0L)
            prepare()
            play()
        }
        _uiState.value = _uiState.value.copy(
            queue = listOf(track),
            fullTrackList = listOf(track),
            currentTrack = track,
            currentIndex = 0,
            currentPlaylistFolderUri = folderUriStr
        )
    }

    fun togglePlayPause() {
        controller?.let { c ->
            if (c.isPlaying) {
                c.pause()
            } else {
                // Once playback reaches STATE_ENDED (e.g. the queue played through to the
                // end), play() alone won't restart it - it only sets playWhenReady, and
                // there's nothing left queued up to play. Seek back to the start first.
                if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0L)
                c.play()
            }
        }
    }

    /**
     * While "Random Skip All Albums" is active, picks a fresh random track from every
     * album instead of stepping to the next item in whatever queue happens to be loaded.
     */
    fun skipNext() {
        if (_uiState.value.isShuffleAllActive) {
            playRandomTrackFromPool(pushHistory = true)
        } else {
            controller?.seekToNextMediaItem()
        }
    }

    /**
     * While "Random Skip All Albums" is active, walks backward through the tracks it has
     * actually played (via [shuffleHistory]) so Previous returns to what was just heard,
     * rather than picking another random one; a no-op once there's nothing earlier to go
     * back to. Otherwise defers to the controller's own queue navigation as normal.
     */
    fun skipPrevious() {
        if (_uiState.value.isShuffleAllActive) {
            if (shuffleHistoryPos > 0) {
                shuffleHistoryPos--
                val (track, folderUriStr) = shuffleHistory[shuffleHistoryPos]
                playSingleTrack(track, folderUriStr)
            }
        } else {
            controller?.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)

    /** Jumps straight to a specific track already in the playable queue. */
    fun playQueueIndex(index: Int) {
        controller?.apply {
            seekToDefaultPosition(index)
            play()
        }
    }

    /**
     * Called when tapping a track in the playlist that currently has a line through it
     * (marked to skip). Un-skips it and rebuilds the queue so it plays right away.
     */
    fun unskipAndPlay(trackUri: String) {
        val playlist = currentPlaylist ?: return
        val folderUriStr = playlist.folderUri.toString()
        skipListRepository.setSkipped(folderUriStr, trackUri, false)
        playPlaylist(playlist, forceRestart = false, startTrackUri = trackUri)
    }

    /**
     * Permanently deletes the currently playing file from device storage and removes it from
     * both the playable queue and the displayed track list. Playback continues with whichever
     * track now occupies the deleted track's position in the queue, or stops cleanly if the
     * deleted track was the last one remaining.
     */
    fun deleteCurrentTrack() {
        val trackToDelete = _uiState.value.currentTrack ?: return
        val deletedIndex = currentQueue.indexOfFirst { it.uri == trackToDelete.uri }
        if (deletedIndex < 0) return

        val context = getApplication<Application>()
        DocumentFile.fromSingleUri(context, trackToDelete.uri)?.delete()

        currentQueue = currentQueue.filterNot { it.uri == trackToDelete.uri }
        currentPlaylist = currentPlaylist?.let { playlist ->
            playlist.copy(tracks = playlist.tracks.filterNot { it.uri == trackToDelete.uri })
        }

        controller?.removeMediaItem(deletedIndex)

        val updatedFullTrackList = currentPlaylist?.tracks ?: currentQueue

        if (deletedIndex < currentQueue.size) {
            val nextTrack = currentQueue[deletedIndex]
            controller?.apply {
                seekToDefaultPosition(deletedIndex)
                play()
            }
            _uiState.value = _uiState.value.copy(
                queue = currentQueue,
                fullTrackList = updatedFullTrackList,
                currentTrack = nextTrack,
                currentIndex = deletedIndex
            )
        } else {
            controller?.stop()
            _uiState.value = _uiState.value.copy(
                queue = currentQueue,
                fullTrackList = updatedFullTrackList,
                currentTrack = null,
                isPlaying = false,
                currentIndex = -1,
                positionMs = 0L,
                durationMs = 0L
            )
        }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        controller?.volume = clamped
        _uiState.value = _uiState.value.copy(volume = clamped)
    }

    override fun onCleared() {
        saveProgress()
        controller?.release()
        controller = null
        super.onCleared()
    }
}

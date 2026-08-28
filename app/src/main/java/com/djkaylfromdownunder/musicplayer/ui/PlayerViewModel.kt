package com.djkaylfromdownunder.musicplayer.ui

import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.djkaylfromdownunder.musicplayer.data.PlaybackStateRepository
import com.djkaylfromdownunder.musicplayer.data.Playlist
import com.djkaylfromdownunder.musicplayer.data.SkipListRepository
import com.djkaylfromdownunder.musicplayer.data.Track
import com.djkaylfromdownunder.musicplayer.playback.MusicPlaybackService
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
    val isShuffleEnabled: Boolean = false
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private var controller: MediaController? = null
    private var currentQueue: List<Track> = emptyList()
    private var currentFolderUri: String? = null
    private var currentPlaylist: Playlist? = null

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
                // in playPlaylist/playShuffledAllTracks) - re-reading it on every transition
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
            }

            // This is the gap most likely behind "playback locks up and won't respond until
            // reboot": if ExoPlayer hits an unrecoverable error (e.g. a track's content Uri
            // became invalid after being moved/renamed), nothing was previously listening
            // for it - isPlaying just silently goes false with no record of why, indistinguishable
            // from a normal pause. Logged with full detail so a captured logcat pinpoints
            // exactly which track/error caused it.
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
     * Skipped entirely if playPlaylist/playShuffledAllTracks already populated state first.
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
     * Builds one combined, randomly-ordered queue out of every track in every playlist
     * passed in (i.e. the whole library, across all folders) and starts playing it from
     * the top - used by the Library screen's shuffle-all button. Skipped tracks are
     * excluded per their own folder's skip list, same as a normal playPlaylist. Since this
     * queue doesn't belong to a single folder, per-folder resume/skip-marking state isn't
     * tracked for it (currentPlaylistFolderUri is left null).
     */
    fun playShuffledAllTracks(playlists: List<Playlist>) {
        val combined = playlists.flatMap { playlist ->
            val folderUriStr = playlist.folderUri.toString()
            playlist.tracks
                .filterNot { skipListRepository.isSkipped(folderUriStr, it.uri.toString()) }
                .map { it to folderUriStr }
        }.shuffled()
        if (combined.isEmpty()) return

        val combinedTracks = combined.map { it.first }
        currentPlaylist = null
        currentFolderUri = combined.first().second
        currentQueue = combinedTracks

        val mediaItems = combined.map { (track, folderUriStr) ->
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
            setMediaItems(mediaItems, 0, 0L)
            prepare()
            play()
        }
        _uiState.value = _uiState.value.copy(
            queue = combinedTracks,
            fullTrackList = combinedTracks,
            currentIndex = 0,
            currentPlaylistFolderUri = combined.first().second
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

    fun skipNext() = controller?.seekToNextMediaItem()
    fun skipPrevious() = controller?.seekToPreviousMediaItem()
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

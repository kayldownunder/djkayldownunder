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
    val currentPlaylistFolderUri: String? = null
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
            controller = controllerFuture.get()
            Log.d(TAG, "MediaController connected")
            attachListener()
        }, MoreExecutors.directExecutor())
        observePosition()
    }

    private fun attachListener() {
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                if (!isPlaying) saveProgress()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = controller?.currentMediaItemIndex ?: -1
                val track = currentQueue.getOrNull(index)
                Log.d(TAG, "onMediaItemTransition index=$index track=${track?.displayName}")
                _uiState.value = _uiState.value.copy(
                    currentTrack = track,
                    currentIndex = index,
                    durationMs = controller?.duration?.coerceAtLeast(0) ?: 0L
                )
            }
        })
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
        }
        _uiState.value = _uiState.value.copy(
            queue = effectiveTracks,
            fullTrackList = playlist.tracks,
            currentIndex = startIndex,
            currentPlaylistFolderUri = folderUriStr
        )
    }

    fun togglePlayPause() {
        controller?.let { c -> if (c.isPlaying) c.pause() else c.play() }
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

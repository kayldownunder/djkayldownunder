package com.djkaylfromdownunder.musicplayer.playback

import android.content.Intent
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

// TEMPORARY DEBUG LOGGING TAG - see the matching one in PlayerViewModel. Chasing a
// playback lockup (no audio, taps do nothing until the phone is rebooted) reported after
// moving files / updating metadata. Remove all of it once the cause is confirmed.
private const val TAG = "DJKaylPlaybackSvc"

class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        val player = ExoPlayer.Builder(this).build()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(
                    TAG,
                    "Service-level onPlayerError code=${error.errorCode} (${error.errorCodeName}) " +
                        "currentMediaItem=${player.currentMediaItem?.mediaId} " +
                        "playbackState=${player.playbackState}",
                    error
                )
            }
        })
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        Log.d(TAG, "onGetSession requested by ${controllerInfo.packageName}, returning session=${mediaSession != null}")
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        val shouldStop = player == null || !player.playWhenReady || player.mediaItemCount == 0
        Log.d(TAG, "onTaskRemoved playWhenReady=${player?.playWhenReady} mediaItemCount=${player?.mediaItemCount} shouldStop=$shouldStop")
        if (shouldStop) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
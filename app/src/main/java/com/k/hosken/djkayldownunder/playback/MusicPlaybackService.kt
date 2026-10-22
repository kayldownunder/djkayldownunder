package com.k.hosken.djkayldownunder.playback

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.k.hosken.djkayldownunder.data.AudioNormalizationRepository

// TEMPORARY DEBUG LOGGING TAG - see the matching one in PlayerViewModel. Chasing a
// playback lockup (no audio, taps do nothing until the phone is rebooted) reported after
// moving files / updating metadata. Remove all of it once the cause is confirmed.
private const val TAG = "DJKaylPlaybackSvc"

// A Bluetooth device commonly sends an AVRCP pause command right before it drops its
// connection (going to sleep, powering off, out of range). If that pause happens within this
// window of the device actually disconnecting, treat it as caused by the disconnect - rather
// than a pause the user meant to stick - so playback can resume when the device comes back.
private const val BLUETOOTH_RESUME_WINDOW_MS = 5_000L

private fun isBluetoothOutput(device: AudioDeviceInfo): Boolean {
    if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) return true
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
}

@UnstableApi
class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val normalizationProcessor = AudioNormalizationProcessor()
    private lateinit var normalizationRepository: AudioNormalizationRepository
    private var normalizationListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var audioManager: AudioManager? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var diagnostics: PlaybackDiagnostics? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        normalizationRepository = AudioNormalizationRepository(this)
        normalizationProcessor.enabled = normalizationRepository.isEnabled()
        normalizationListener = normalizationRepository.registerChangeListener { enabled ->
            normalizationProcessor.enabled = enabled
            diagnostics?.log("Volume normalization toggled enabled=$enabled")
        }

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(normalizationProcessor))
                    .build()
            }
        }
        val player = ExoPlayer.Builder(this, renderersFactory)
            .setHandleAudioBecomingNoisy(true)
            // Android 17 "audio hardening" (22 Oct 2026): a new AudioTrack started in the background
            // is muted unless the app holds audio focus ("AudioHardening background playback muted"
            // in logcat). Each track change makes a new AudioTrack, so with the screen off the next
            // song played silently while the player still said "playing". Handling focus fixes it.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .build()

        diagnostics = PlaybackDiagnostics(this, player).also {
            it.start()
            it.log("Volume normalization enabled=${normalizationProcessor.enabled}")
        }

        var lastPauseElapsedRealtime = 0L
        var resumeOnBluetoothReconnect = false
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

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    lastPauseElapsedRealtime = SystemClock.elapsedRealtime()
                }
            }
        })

        val manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager = manager
        val deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                if (removedDevices.any(::isBluetoothOutput) &&
                    !player.isPlaying &&
                    player.mediaItemCount > 0 &&
                    SystemClock.elapsedRealtime() - lastPauseElapsedRealtime < BLUETOOTH_RESUME_WINDOW_MS
                ) {
                    Log.d(TAG, "Paused right as a Bluetooth output disconnected - will resume when it reconnects")
                    resumeOnBluetoothReconnect = true
                }
            }

            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                if (resumeOnBluetoothReconnect && addedDevices.any(::isBluetoothOutput)) {
                    resumeOnBluetoothReconnect = false
                    Log.d(TAG, "Bluetooth output reconnected - resuming playback")
                    player.play()
                }
            }
        }
        manager.registerAudioDeviceCallback(deviceCallback, null)
        audioDeviceCallback = deviceCallback

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        Log.d(TAG, "onGetSession requested by ${controllerInfo.packageName}, returning session=${mediaSession != null}")
        return mediaSession
    }

    // Android 17 gives a foreground service "while-in-use" rights only if it was started while the
    // app was visible (or by a notification tap / media key). Media3 drops out of the foreground
    // when paused, so pressing play later from the lock screen or a Bluetooth button restarted the
    // service from the background without those rights and the audio was muted ("AudioHardening
    // background playback muted ... level: full", 22 Oct 2026). Staying in the foreground while
    // paused keeps the rights from when the user first started playing.
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired || session.player.mediaItemCount > 0)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        val shouldStop = player == null || !player.playWhenReady || player.mediaItemCount == 0
        Log.d(TAG, "onTaskRemoved playWhenReady=${player?.playWhenReady} mediaItemCount=${player?.mediaItemCount} shouldStop=$shouldStop")
        diagnostics?.log("onTaskRemoved (app swiped away) playWhenReady=${player?.playWhenReady} shouldStop=$shouldStop")
        if (shouldStop) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        diagnostics?.stop()
        diagnostics = null
        normalizationListener?.let { normalizationRepository.unregisterChangeListener(it) }
        audioDeviceCallback?.let { audioManager?.unregisterAudioDeviceCallback(it) }
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

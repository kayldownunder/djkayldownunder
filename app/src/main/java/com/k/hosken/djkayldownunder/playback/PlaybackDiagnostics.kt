package com.k.hosken.djkayldownunder.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.math.abs

// TEMPORARY DEBUG LOGGING - added to catch the "scratched CD" audio glitch (playback breaks up
// and repeats a fraction of a second). Writes a rolling log to
//   <external app files dir>/playback_diagnostics.log  (+ playback_diagnostics.1.log, the previous one)
// i.e. /sdcard/Android/data/com.k.hosken.djkayldownunder/files/ on the phone, and mirrors
// every line to logcat under this tag. Remove this file and its wiring in MusicPlaybackService
// once the cause is confirmed.
private const val TAG = "DJKaylAudioDiag"

private const val MAX_LOG_BYTES = 1_000_000L
private const val TICK_MS = 1_000L

// Position should advance in lock-step with the wall clock while playing at 1x. A gap this big
// between the two over one tick means audio stalled or jumped even though ExoPlayer raised no event.
private const val DRIFT_THRESHOLD_MS = 250L

// How late the main-thread tick may run before it's flagged - a starved main thread is a hint that
// the whole process (audio feed thread included) wasn't getting CPU.
private const val LATE_TICK_THRESHOLD_MS = 500L
private const val SNAPSHOT_EVERY_TICKS = 60

/** Appends timestamped lines to a size-capped two-file rolling log, off the caller's thread. */
private class DiagnosticsLogFile(context: Context) {
    private val dir: File = context.getExternalFilesDir(null) ?: context.filesDir
    private val current = File(dir, "playback_diagnostics.log")
    private val previous = File(dir, "playback_diagnostics.1.log")
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "DJKaylDiagLog").apply { isDaemon = true }
    }

    // Only ever touched from the executor thread, so no synchronisation needed.
    private val timestampFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun log(message: String) {
        val nowMs = System.currentTimeMillis()
        Log.d(TAG, message)
        try {
            executor.execute { append(nowMs, message) }
        } catch (_: RejectedExecutionException) {
            // Already closed - late line during service teardown, nothing to do.
        }
    }

    fun close() = executor.shutdown()

    private fun append(timeMs: Long, message: String) {
        runCatching {
            if (current.length() > MAX_LOG_BYTES) {
                previous.delete()
                current.renameTo(previous)
            }
            current.appendText("${timestampFormat.format(Date(timeMs))}  $message\n")
        }
    }
}

/**
 * Watches the service's [ExoPlayer] for anything that could explain audible break-up/repeat:
 * AudioTrack underruns, position jumps, state changes, audio-focus/route changes, file load errors,
 * and a 1s watchdog that compares how far playback position moved against the wall clock.
 * All callbacks run on the player's application looper.
 */
@UnstableApi
class PlaybackDiagnostics(private val context: Context, private val player: ExoPlayer) {

    private val logFile = DiagnosticsLogFile(context)
    private val handler = Handler(player.applicationLooper)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var baselineValid = false
    private var baselineElapsedMs = 0L
    private var baselinePositionMs = 0L
    private var ticksSinceSnapshot = 0

    fun log(message: String) = logFile.log(message)

    fun start() {
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
        logFile.log(
            "==== diagnostics start ==== device=${Build.MANUFACTURER} ${Build.MODEL} " +
                "android=${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT}) app=$versionName " +
                "ignoringBatteryOpt=${powerManager.isIgnoringBatteryOptimizations(context.packageName)}"
        )
        player.addAnalyticsListener(analyticsListener)
        audioManager.registerAudioDeviceCallback(deviceCallback, handler)
        handler.postDelayed(tick, TICK_MS)
        snapshot("start")
    }

    fun stop() {
        handler.removeCallbacks(tick)
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        player.removeAnalyticsListener(analyticsListener)
        logFile.log("==== diagnostics stop (service destroyed) ====")
        logFile.close()
    }

    private val tick = object : Runnable {
        override fun run() {
            sample()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private fun sample() {
        val nowMs = SystemClock.elapsedRealtime()
        if (!player.isPlaying) {
            baselineValid = false
            return
        }
        val positionMs = player.currentPosition
        if (baselineValid) {
            val wallMs = nowMs - baselineElapsedMs
            val advancedMs = positionMs - baselinePositionMs
            val driftMs = advancedMs - wallMs
            if (abs(driftMs) > DRIFT_THRESHOLD_MS) {
                logFile.log(
                    "POSITION ANOMALY position moved ${advancedMs}ms in ${wallMs}ms of wall time " +
                        "(drift ${driftMs}ms) now=$positionMs track=${trackTitle()} ${conditions()}"
                )
            }
            if (wallMs - TICK_MS > LATE_TICK_THRESHOLD_MS) {
                logFile.log("MAIN THREAD STALL watchdog tick ran ${wallMs - TICK_MS}ms late ${conditions()}")
            }
        }
        baselineValid = true
        baselineElapsedMs = nowMs
        baselinePositionMs = positionMs

        if (++ticksSinceSnapshot >= SNAPSHOT_EVERY_TICKS) {
            ticksSinceSnapshot = 0
            snapshot("periodic")
        }
    }

    private fun snapshot(reason: String) {
        val positionMs = player.currentPosition
        val runtime = Runtime.getRuntime()
        val heapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val heapMaxMb = runtime.maxMemory() / (1024 * 1024)
        logFile.log(
            "SNAPSHOT($reason) track=${trackTitle()} pos=$positionMs " +
                "bufferedAhead=${player.bufferedPosition - positionMs}ms state=${stateName(player.playbackState)} " +
                "playing=${player.isPlaying} outputs=${outputSummary()} heap=${heapUsedMb}/${heapMaxMb}MB ${conditions()}"
        )
    }

    private fun trackTitle(): String =
        player.currentMediaItem?.mediaMetadata?.title?.toString() ?: player.currentMediaItem?.mediaId ?: "none"

    /** Device-wide conditions that commonly starve or throttle an audio feed. */
    private fun conditions(): String {
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalName(powerManager.currentThermalStatus)
        } else {
            "n/a"
        }
        return "[screenOn=${powerManager.isInteractive} powerSave=${powerManager.isPowerSaveMode} thermal=$thermal]"
    }

    private fun outputSummary(): String =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).joinToString(",") { describe(it) }

    private fun event(eventTime: AnalyticsListener.EventTime, message: String) {
        logFile.log("[pos=${eventTime.currentPlaybackPositionMs} buffered=${eventTime.totalBufferedDurationMs}] $message")
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onAudioUnderrun(
            eventTime: AnalyticsListener.EventTime,
            bufferSize: Int,
            bufferSizeMs: Long,
            elapsedSinceLastFeedMs: Long
        ) {
            event(
                eventTime,
                "AUDIO UNDERRUN bufferSize=$bufferSize bufferSizeMs=$bufferSizeMs " +
                    "elapsedSinceLastFeedMs=$elapsedSinceLastFeedMs track=${trackTitle()} ${conditions()}"
            )
        }

        override fun onPositionDiscontinuity(
            eventTime: AnalyticsListener.EventTime,
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            baselineValid = false
            event(
                eventTime,
                "POSITION DISCONTINUITY ${discontinuityName(reason)} " +
                    "item ${oldPosition.mediaItemIndex}@${oldPosition.positionMs} -> " +
                    "${newPosition.mediaItemIndex}@${newPosition.positionMs}"
            )
        }

        override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
            baselineValid = false
            event(eventTime, "STATE ${stateName(state)} track=${trackTitle()}")
        }

        override fun onIsPlayingChanged(eventTime: AnalyticsListener.EventTime, isPlaying: Boolean) {
            baselineValid = false
            event(eventTime, "IS_PLAYING $isPlaying")
        }

        override fun onPlayWhenReadyChanged(eventTime: AnalyticsListener.EventTime, playWhenReady: Boolean, reason: Int) {
            baselineValid = false
            event(eventTime, "PLAY_WHEN_READY $playWhenReady reason=${playWhenReadyReasonName(reason)}")
        }

        override fun onPlaybackSuppressionReasonChanged(eventTime: AnalyticsListener.EventTime, playbackSuppressionReason: Int) {
            baselineValid = false
            event(eventTime, "PLAYBACK SUPPRESSION reason=${suppressionName(playbackSuppressionReason)}")
        }

        override fun onMediaItemTransition(eventTime: AnalyticsListener.EventTime, mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            baselineValid = false
            event(
                eventTime,
                "MEDIA ITEM TRANSITION ${transitionName(reason)} -> " +
                    (mediaItem?.mediaMetadata?.title ?: mediaItem?.mediaId ?: "none")
            )
        }

        override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
            event(
                eventTime,
                "AUDIOTRACK INIT sampleRate=${audioTrackConfig.sampleRate} channelConfig=${audioTrackConfig.channelConfig} " +
                    "encoding=${audioTrackConfig.encoding} bufferSize=${audioTrackConfig.bufferSize} " +
                    "offload=${audioTrackConfig.offload} tunneling=${audioTrackConfig.tunneling}"
            )
        }

        override fun onAudioTrackReleased(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
            event(eventTime, "AUDIOTRACK RELEASED")
        }

        override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
            event(eventTime, "AUDIO SESSION ID $audioSessionId")
        }

        override fun onAudioSinkError(eventTime: AnalyticsListener.EventTime, audioSinkError: Exception) {
            event(eventTime, "AUDIO SINK ERROR $audioSinkError ${conditions()}")
        }

        override fun onAudioCodecError(eventTime: AnalyticsListener.EventTime, audioCodecError: Exception) {
            event(eventTime, "AUDIO CODEC ERROR $audioCodecError")
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            event(eventTime, "DECODER $decoderName init took ${initializationDurationMs}ms")
        }

        override fun onAudioDisabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
            event(eventTime, "AUDIO RENDERER DISABLED")
        }

        override fun onLoadError(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
            error: IOException,
            wasCanceled: Boolean
        ) {
            event(eventTime, "LOAD ERROR canceled=$wasCanceled uri=${loadEventInfo.uri} $error")
        }

        override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
            event(eventTime, "PLAYER ERROR ${error.errorCodeName} (${error.errorCode}) $error ${conditions()}")
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            logRouteChange("+", addedDevices)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            logRouteChange("-", removedDevices)
        }

        private fun logRouteChange(sign: String, devices: Array<out AudioDeviceInfo>) {
            val outputs = devices.filter { it.isSink }
            if (outputs.isEmpty()) return
            logFile.log("OUTPUT ROUTE $sign ${outputs.joinToString(",") { describe(it) }} now=${outputSummary()}")
        }
    }

    private fun describe(device: AudioDeviceInfo) = "${deviceTypeName(device.type)}(${device.productName})"

    private fun deviceTypeName(type: Int) = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired-headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired-headphones"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bt-a2dp"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bt-sco"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "usb"
        AudioDeviceInfo.TYPE_HDMI -> "hdmi"
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
            "ble-headset"
        } else {
            "type$type"
        }
    }

    private fun stateName(state: Int) = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN($state)"
    }

    private fun discontinuityName(reason: Int) = when (reason) {
        Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> "AUTO_TRANSITION"
        Player.DISCONTINUITY_REASON_SEEK -> "SEEK"
        Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> "SEEK_ADJUSTMENT"
        Player.DISCONTINUITY_REASON_SKIP -> "SKIP"
        Player.DISCONTINUITY_REASON_REMOVE -> "REMOVE"
        Player.DISCONTINUITY_REASON_INTERNAL -> "INTERNAL"
        else -> "UNKNOWN($reason)"
    }

    private fun playWhenReadyReasonName(reason: Int) = when (reason) {
        Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "USER_REQUEST"
        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "AUDIO_FOCUS_LOSS"
        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "AUDIO_BECOMING_NOISY"
        Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "REMOTE"
        Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "END_OF_MEDIA_ITEM"
        else -> "UNKNOWN($reason)"
    }

    private fun suppressionName(reason: Int) = when (reason) {
        Player.PLAYBACK_SUPPRESSION_REASON_NONE -> "NONE"
        Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS -> "TRANSIENT_AUDIO_FOCUS_LOSS"
        Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT -> "UNSUITABLE_AUDIO_OUTPUT"
        else -> "UNKNOWN($reason)"
    }

    private fun transitionName(reason: Int) = when (reason) {
        Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
        Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
        else -> "UNKNOWN($reason)"
    }

    private fun thermalName(status: Int) = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "none"
        PowerManager.THERMAL_STATUS_LIGHT -> "light"
        PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
        PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
        else -> "unknown($status)"
    }
}

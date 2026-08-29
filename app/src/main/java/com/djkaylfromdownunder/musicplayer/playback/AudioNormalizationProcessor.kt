package com.djkaylfromdownunder.musicplayer.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp

/**
 * Real-time "auto-leveling" so a quiet track and a loud track play back at roughly the same
 * perceived volume, without having to reach for the volume slider between songs. There's no
 * per-track loudness metadata anywhere in this app (no ReplayGain tags, no pre-analysis pass),
 * so this works purely on the live PCM stream as it plays: it tracks a smoothed running RMS
 * level and continuously nudges a gain multiplier toward whatever value would put that RMS at
 * [TARGET_RMS]. The gain move itself is smoothed over roughly half a second (see
 * [gainSmoothingAlpha]) so the adjustment isn't audible as pumping, and it's clamped to
 * [MIN_GAIN]/[MAX_GAIN] so near-silent passages (e.g. a quiet intro, or the gap between
 * tracks) don't get amplified into audible noise.
 *
 * Only handles 16-bit PCM (what this app's ExoPlayer instance outputs by default); any other
 * encoding makes the processor report itself inactive via [onConfigure], so it's a no-op
 * pass-through for that stream rather than something that needs to be guarded elsewhere.
 *
 * [enabled] is flipped live from the Settings toggle (see AudioNormalizationRepository) - the
 * processor stays wired into the audio sink permanently and just bypasses processing when off,
 * so toggling it doesn't require rebuilding the player's audio pipeline.
 */
class AudioNormalizationProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    private var rmsSmoothingAlpha = 0f
    private var gainSmoothingAlpha = 0f
    private var smoothedRms = 0f
    private var gain = 1f

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioFormat.NOT_SET
        }
        rmsSmoothingAlpha = alphaForTimeConstant(RMS_TIME_CONSTANT_SECONDS, inputAudioFormat.sampleRate)
        gainSmoothingAlpha = alphaForTimeConstant(GAIN_TIME_CONSTANT_SECONDS, inputAudioFormat.sampleRate)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled) {
            val outputBuffer = replaceOutputBuffer(remaining)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        // Only whole 16-bit samples are processed; any single trailing byte (a mid-sample
        // buffer split, which shouldn't normally happen but isn't guaranteed against) is left
        // unconsumed for the next call, as the AudioProcessor contract allows.
        val processableBytes = remaining - (remaining % 2)
        val outputBuffer = replaceOutputBuffer(processableBytes)
        var bytesLeft = processableBytes
        while (bytesLeft > 0) {
            val sample = inputBuffer.short
            val normalized = sample / 32768f
            smoothedRms += rmsSmoothingAlpha * (abs(normalized) - smoothedRms)
            val targetGain = if (smoothedRms > MIN_RMS_FOR_ADJUSTMENT) {
                (TARGET_RMS / smoothedRms).coerceIn(MIN_GAIN, MAX_GAIN)
            } else {
                gain
            }
            gain += gainSmoothingAlpha * (targetGain - gain)
            val boosted = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            outputBuffer.putShort(boosted.toShort())
            bytesLeft -= 2
        }
        outputBuffer.flip()
    }

    override fun onFlush() {
        smoothedRms = 0f
        gain = 1f
    }

    private fun alphaForTimeConstant(timeConstantSeconds: Float, sampleRate: Int): Float {
        val tauSamples = timeConstantSeconds * sampleRate
        return 1f - exp(-1f / tauSamples)
    }

    private companion object {
        const val TARGET_RMS = 0.15f
        const val MIN_GAIN = 0.25f
        const val MAX_GAIN = 4f
        const val MIN_RMS_FOR_ADJUSTMENT = 0.0005f
        const val RMS_TIME_CONSTANT_SECONDS = 0.05f
        const val GAIN_TIME_CONSTANT_SECONDS = 0.5f
    }
}

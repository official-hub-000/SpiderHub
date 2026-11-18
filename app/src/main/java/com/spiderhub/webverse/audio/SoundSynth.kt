package com.spiderhub.webverse.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class SoundSynth {
    @Volatile
    var muted: Boolean = false

    private val executor = Executors.newSingleThreadExecutor()
    private val sampleRate = 22050

    fun playWebThwip() = playAsync(0.14f) { t ->
        val env = exp(-t * 24f)
        val freq = 950f - t * 3200f
        val noise = (Random.nextFloat() * 2f - 1f) * 0.25f
        (sin(2.0 * PI * freq.coerceAtLeast(180f) * t).toFloat() * 0.7f + noise) * env
    }

    fun playZipDash() = playAsync(0.18f) { t ->
        val env = exp(-t * 14f)
        val freq = 320f + t * 2400f
        sin(2.0 * PI * freq * t).toFloat() * env * 0.65f
    }

    fun playStrike() = playAsync(0.16f) { t ->
        val env = exp(-t * 22f)
        val bass = sin(2.0 * PI * (180f - t * 600f).coerceAtLeast(55f) * t).toFloat()
        val crunch = (Random.nextFloat() * 2f - 1f) * exp(-t * 35f) * 0.5f
        (bass * 0.8f + crunch) * env
    }

    fun playShardChime() = playAsync(0.28f) { t ->
        val env = exp(-t * 9f)
        val f1 = sin(2.0 * PI * 880.0 * t).toFloat()
        val f2 = sin(2.0 * PI * 1320.0 * t).toFloat() * 0.6f
        (f1 + f2) * 0.45f * env
    }

    fun playDimensionShift() = playAsync(0.38f) { t ->
        val env = sin(PI * (t / 0.38f).coerceIn(0f, 1f)).toFloat()
        val sweep = sin(2.0 * PI * (120.0 + sin(t * 14.0) * 60.0 + t * 320.0) * t).toFloat()
        sweep * env * 0.65f
    }

    private fun playAsync(durationSec: Float, sampleFn: (Float) -> Float) {
        if (muted) return
        executor.execute {
            try {
                val count = (sampleRate * durationSec).toInt().coerceAtLeast(256)
                val pcm = ShortArray(count)
                for (i in 0 until count) {
                    val t = i.toFloat() / sampleRate
                    val v = sampleFn(t).coerceIn(-1f, 1f)
                    pcm[i] = (v * 24000f).toInt().toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep((durationSec * 1000f).toLong() + 40L)
                track.stop()
                track.release()
            } catch (_: Exception) {
            }
        }
    }

    fun release() {
        executor.shutdownNow()
    }
}

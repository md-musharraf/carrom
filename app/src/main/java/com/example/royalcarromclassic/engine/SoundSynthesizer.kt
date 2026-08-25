package com.example.royalcarromclassic.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.*

class SoundSynthesizer {
    private val sampleRate = 22050
    private val scope = CoroutineScope(Dispatchers.Default)
    var isSoundEnabled: Boolean = true

    private fun playTone(
        durationMs: Int,
        sampleGenerator: (sampleIndex: Int, totalSamples: Int) -> Short
    ) {
        if (!isSoundEnabled) return

        scope.launch {
            try {
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    buffer[i] = sampleGenerator(i, numSamples)
                }

                val audioTrack = AudioTrack.Builder()
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
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()

                // Release after playing
                Thread.sleep(durationMs.toLong() + 30)
                audioTrack.stop()
                audioTrack.release()
            } catch (_: Exception) {
                // Ignore audio track generation errors on constrained devices
            }
        }
    }

    /**
     * Wood disc collision clack
     */
    fun playClack(intensity: Float = 0.5f) {
        val baseFreq = 540f + (Math.random().toFloat() * 200f)
        playTone(durationMs = 50) { i, total ->
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / total
            val decay = (1f - progress).pow(3f)
            val freq = baseFreq * (1f - progress * 0.4f)
            val wave = sin(2f * PI.toFloat() * freq * t)
            (wave * decay * intensity * 26000f).toInt().toShort()
        }
    }

    /**
     * Striker flick release thump
     */
    fun playFlick(power: Float = 50f) {
        val pRatio = (power / 100f).coerceIn(0.2f, 1f)
        playTone(durationMs = 80) { i, total ->
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / total
            val decay = (1f - progress).pow(2f)
            val freq = 220f * (1f - progress * 0.7f)
            val wave = sin(2f * PI.toFloat() * freq * t)
            (wave * decay * pRatio * 28000f).toInt().toShort()
        }
    }

    /**
     * Cushion bounce thud
     */
    fun playWall(intensity: Float = 0.5f) {
        playTone(durationMs = 60) { i, total ->
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / total
            val decay = (1f - progress).pow(2.5f)
            val freq = 140f * (1f - progress * 0.6f)
            val wave = sin(2f * PI.toFloat() * freq * t)
            (wave * decay * intensity * 22000f).toInt().toShort()
        }
    }

    /**
     * Pocket drop sound
     */
    fun playPocket() {
        playTone(durationMs = 120) { i, total ->
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / total
            val decay = (1f - progress).pow(1.8f)
            val freq = 360f * (1f - progress * 0.5f)
            val wave = sin(2f * PI.toFloat() * freq * t)
            (wave * decay * 26000f).toInt().toShort()
        }
    }

    /**
     * Victory fanfare
     */
    fun playVictory() {
        val notes = listOf(523.25f, 659.25f, 783.99f, 1046.50f)
        scope.launch {
            for (freq in notes) {
                playTone(durationMs = 160) { i, total ->
                    val t = i.toFloat() / sampleRate
                    val progress = i.toFloat() / total
                    val decay = (1f - progress).pow(1.5f)
                    val wave = sin(2f * PI.toFloat() * freq * t)
                    (wave * decay * 22000f).toInt().toShort()
                }
                Thread.sleep(110)
            }
        }
    }

    /**
     * Coin chime
     */
    fun playCoin() {
        scope.launch {
            playTone(durationMs = 70) { i, total ->
                val t = i.toFloat() / sampleRate
                val decay = 1f - (i.toFloat() / total)
                (sin(2f * PI.toFloat() * 987.77f * t) * decay * 20000f).toInt().toShort()
            }
            Thread.sleep(50)
            playTone(durationMs = 140) { i, total ->
                val t = i.toFloat() / sampleRate
                val decay = 1f - (i.toFloat() / total)
                (sin(2f * PI.toFloat() * 1318.51f * t) * decay * 22000f).toInt().toShort()
            }
        }
    }

    /**
     * Button click / slider tick
     */
    fun playClick() {
        playTone(durationMs = 25) { i, total ->
            val t = i.toFloat() / sampleRate
            val decay = 1f - (i.toFloat() / total)
            (sin(2f * PI.toFloat() * 850f * t) * decay * 14000f).toInt().toShort()
        }
    }
}

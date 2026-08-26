package com.example.royalcarromclassic.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.example.royalcarromclassic.core.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * High-performance, zero-blocking audio synthesizer utilizing Android SoundPool.
 * Generates procedural retro-acoustic waveforms into cached WAV assets at launch,
 * avoiding runtime AudioTrack allocations, thread blocking, or GC pressure.
 */
class SoundSynthesizer(private val context: Context? = null) : AudioEngine {

    override var isSoundEnabled: Boolean = true
    override var isMusicEnabled: Boolean = true

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val sampleRate = 22050
    private var soundPool: SoundPool? = null

    private var clackSoundId = 0
    private var flickSoundId = 0
    private var wallSoundId = 0
    private var pocketSoundId = 0
    private var coinSoundId = 0
    private var clickSoundId = 0
    private var victorySoundId = 0

    private var lastClackTimeNanos = 0L
    private var lastWallTimeNanos = 0L

    init {
        initSoundPool()
    }

    private fun initSoundPool() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(audioAttributes)
                .build()
            soundPool = pool

            if (context != null) {
                scope.launch {
                    loadSynthesizedSounds(context, pool)
                }
            }
        } catch (e: Exception) {
            AppLogger.e("SoundSynthesizer", { "Failed to initialize SoundPool" }, e)
        }
    }

    private fun loadSynthesizedSounds(ctx: Context, pool: SoundPool) {
        try {
            val cacheDir = File(ctx.cacheDir, "audio_synth")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val clackFile = File(cacheDir, "clack.wav").apply {
                if (!exists() || length() == 0L) writeBytes(generateWav(50) { i, total ->
                    val t = i.toFloat() / sampleRate
                    val progress = i.toFloat() / total
                    val decay = (1f - progress).pow(3f)
                    val freq = 620f * (1f - progress * 0.4f)
                    val wave = sin(2f * PI.toFloat() * freq * t)
                    (wave * decay * 28000f).toInt().toShort()
                })
            }
            clackSoundId = pool.load(clackFile.absolutePath, 1)

            val flickFile = File(cacheDir, "flick.wav").apply {
                if (!exists() || length() == 0L) writeBytes(generateWav(80) { i, total ->
                    val t = i.toFloat() / sampleRate
                    val progress = i.toFloat() / total
                    val decay = (1f - progress).pow(2f)
                    val freq = 220f * (1f - progress * 0.7f)
                    val wave = sin(2f * PI.toFloat() * freq * t)
                    (wave * decay * 28000f).toInt().toShort()
                })
            }
            flickSoundId = pool.load(flickFile.absolutePath, 1)

            val wallFile = File(cacheDir, "wall.wav").apply {
                if (!exists() || length() == 0L) writeBytes(generateWav(60) { i, total ->
                    val t = i.toFloat() / sampleRate
                    val progress = i.toFloat() / total
                    val decay = (1f - progress).pow(2.5f)
                    val freq = 140f * (1f - progress * 0.6f)
                    val wave = sin(2f * PI.toFloat() * freq * t)
                    (wave * decay * 24000f).toInt().toShort()
                })
            }
            wallSoundId = pool.load(wallFile.absolutePath, 1)

            val pocketFile = File(cacheDir, "pocket.wav").apply {
                if (!exists() || length() == 0L) writeBytes(generateWav(120) { i, total ->
                    val t = i.toFloat() / sampleRate
                    val progress = i.toFloat() / total
                    val decay = (1f - progress).pow(1.8f)
                    val freq = 360f * (1f - progress * 0.5f)
                    val wave = sin(2f * PI.toFloat() * freq * t)
                    (wave * decay * 26000f).toInt().toShort()
                })
            }
            pocketSoundId = pool.load(pocketFile.absolutePath, 1)

            val coinFile = File(cacheDir, "coin.wav").apply {
                if (!exists() || length() == 0L) writeBytes(generateWav(120) { i, total ->
                    val t = i.toFloat() / sampleRate
                    val progress = i.toFloat() / total
                    val decay = 1f - progress
                    val freq = if (progress < 0.4f) 987.77f else 1318.51f
                    val wave = sin(2f * PI.toFloat() * freq * t)
                    (wave * decay * 22000f).toInt().toShort()
                })
            }
            coinSoundId = pool.load(coinFile.absolutePath, 1)

            val clickFile = File(cacheDir, "click.wav").apply {
                if (!exists() || length() == 0L) writeBytes(generateWav(25) { i, total ->
                    val t = i.toFloat() / sampleRate
                    val decay = 1f - (i.toFloat() / total)
                    val wave = sin(2f * PI.toFloat() * 850f * t)
                    (wave * decay * 14000f).toInt().toShort()
                })
            }
            clickSoundId = pool.load(clickFile.absolutePath, 1)

            val victoryFile = File(cacheDir, "victory.wav").apply {
                if (!exists() || length() == 0L) writeBytes(generateWav(400) { i, total ->
                    val t = i.toFloat() / sampleRate
                    val progress = i.toFloat() / total
                    val noteIdx = (progress * 4).toInt().coerceIn(0, 3)
                    val freq = when (noteIdx) {
                        0 -> 523.25f
                        1 -> 659.25f
                        2 -> 783.99f
                        else -> 1046.50f
                    }
                    val localProgress = (progress * 4f) - noteIdx
                    val decay = (1f - localProgress).pow(1.5f)
                    val wave = sin(2f * PI.toFloat() * freq * t)
                    (wave * decay * 22000f).toInt().toShort()
                })
            }
            victorySoundId = pool.load(victoryFile.absolutePath, 1)
        } catch (e: Exception) {
            AppLogger.e("SoundSynthesizer", { "Error synthesizing audio files" }, e)
        }
    }

    private fun generateWav(durationMs: Int, generator: (Int, Int) -> Short): ByteArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val pcmData = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            pcmData[i] = generator(i, numSamples)
        }

        val totalDataLen = numSamples * 2 + 36
        val totalAudioLen = numSamples * 2
        val channels = 1
        val byteRate = sampleRate * channels * 2

        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalDataLen)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // Subchunk1Size for PCM
        buffer.putShort(1) // AudioFormat PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort((channels * 2).toShort()) // BlockAlign
        buffer.putShort(16) // BitsPerSample
        buffer.put("data".toByteArray())
        buffer.putInt(totalAudioLen)

        val output = ByteArrayOutputStream(44 + numSamples * 2)
        output.write(header)
        val pcmBytes = ByteArray(numSamples * 2)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcmData)
        output.write(pcmBytes)
        return output.toByteArray()
    }

    override fun playClack(intensity: Float) {
        if (!isSoundEnabled) return
        val now = System.nanoTime()
        // Audio rate-limiting debounce (max 1 collision clack per 35ms)
        if (now - lastClackTimeNanos < 35_000_000L) return
        lastClackTimeNanos = now

        val pool = soundPool ?: return
        if (clackSoundId > 0) {
            val volume = intensity.coerceIn(0.15f, 1.0f)
            val pitch = 0.92f + (Math.random().toFloat() * 0.16f) // Organic pitch variation
            pool.play(clackSoundId, volume, volume, 1, 0, pitch)
        }
    }

    override fun playFlick(power: Float) {
        if (!isSoundEnabled) return
        val pool = soundPool ?: return
        if (flickSoundId > 0) {
            val volume = (power / 100f).coerceIn(0.25f, 1.0f)
            pool.play(flickSoundId, volume, volume, 2, 0, 1.0f)
        }
    }

    override fun playWall(intensity: Float) {
        if (!isSoundEnabled) return
        val now = System.nanoTime()
        if (now - lastWallTimeNanos < 40_000_000L) return
        lastWallTimeNanos = now

        val pool = soundPool ?: return
        if (wallSoundId > 0) {
            val volume = intensity.coerceIn(0.2f, 1.0f)
            pool.play(wallSoundId, volume, volume, 1, 0, 1.0f)
        }
    }

    override fun playPocket() {
        if (!isSoundEnabled) return
        val pool = soundPool ?: return
        if (pocketSoundId > 0) {
            pool.play(pocketSoundId, 1.0f, 1.0f, 2, 0, 1.0f)
        }
    }

    override fun playVictory() {
        if (!isSoundEnabled) return
        val pool = soundPool ?: return
        if (victorySoundId > 0) {
            pool.play(victorySoundId, 1.0f, 1.0f, 3, 0, 1.0f)
        }
    }

    override fun playCoin() {
        if (!isSoundEnabled) return
        val pool = soundPool ?: return
        if (coinSoundId > 0) {
            pool.play(coinSoundId, 0.9f, 0.9f, 2, 0, 1.0f)
        }
    }

    override fun playClick() {
        if (!isSoundEnabled) return
        val pool = soundPool ?: return
        if (clickSoundId > 0) {
            pool.play(clickSoundId, 0.6f, 0.6f, 1, 0, 1.0f)
        }
    }

    override fun release() {
        soundPool?.release()
        soundPool = null
    }
}

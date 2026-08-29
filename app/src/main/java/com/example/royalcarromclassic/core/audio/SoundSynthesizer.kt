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
            val cacheDir = File(ctx.cacheDir, "audio_synth_v2")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            // 1. Authentic Carromite Wood Clack (Transient click + 3-stage harmonic body resonance)
            val clackFile = File(cacheDir, "clack.wav").apply {
                if (!exists() || length() == 0L) writeBytes(generateWav(55) { i, total ->
                    val t = i.toFloat() / sampleRate
                    val progress = i.toFloat() / total
                    val fastDecay = (1f - progress).pow(6f)
                    val bodyDecay = (1f - progress).pow(2.8f)

                    // Sharp transient click (2800Hz - 3600Hz)
                    val click = sin(2f * PI.toFloat() * 3200f * t) * fastDecay * 0.45f
                    // Fundamental wood body tone (760Hz)
                    val f0 = sin(2f * PI.toFloat() * 760f * (1f - progress * 0.15f) * t) * bodyDecay * 0.65f
                    // 2nd wood harmonic (1520Hz)
                    val f1 = sin(2f * PI.toFloat() * 1520f * (1f - progress * 0.2f) * t) * bodyDecay * 0.35f
                    // 3rd overtone (2280Hz)
                    val f2 = sin(2f * PI.toFloat() * 2280f * t) * fastDecay * 0.2f

                    val sample = (click + f0 + f1 + f2)
                    (sample.coerceIn(-1f, 1f) * 29000f).toInt().toShort()
                })
            }
            clackSoundId = pool.load(clackFile.absolutePath, 1)

            // 2. Crisp Striker Flick (Transient Snap + Low-end release)
            val flickFile = File(cacheDir, "flick.wav").apply {
                if (!exists() || length() == 0L) writeBytes(generateWav(75) { i, total ->
                    val t = i.toFloat() / sampleRate
                    val progress = i.toFloat() / total
                    val snapDecay = (1f - progress).pow(4.5f)
                    val releaseDecay = (1f - progress).pow(2.0f)

                    val snap = sin(2f * PI.toFloat() * 1850f * (1f - progress * 0.5f) * t) * snapDecay * 0.6f
                    val thud = sin(2f * PI.toFloat() * 260f * (1f - progress * 0.6f) * t) * releaseDecay * 0.5f

                    val sample = snap + thud
                    (sample.coerceIn(-1f, 1f) * 28000f).toInt().toShort()
                })
            }
            flickSoundId = pool.load(flickFile.absolutePath, 1)

            // 3. Deep Wooden Cushion Wall Rebound (Low-end frame thump with acoustic dampening)
            val wallFile = File(cacheDir, "wall.wav").apply {
                if (!exists() || length() == 0L) writeBytes(generateWav(70) { i, total ->
                    val t = i.toFloat() / sampleRate
                    val progress = i.toFloat() / total
                    val decay = (1f - progress).pow(2.4f)
                    val f0 = sin(2f * PI.toFloat() * 145f * (1f - progress * 0.4f) * t) * 0.75f
                    val f1 = sin(2f * PI.toFloat() * 320f * (1f - progress * 0.3f) * t) * 0.35f

                    val sample = (f0 + f1) * decay
                    (sample.coerceIn(-1f, 1f) * 26000f).toInt().toShort()
                })
            }
            wallSoundId = pool.load(wallFile.absolutePath, 1)

            // 4. Soft Hollow Pocket Drop (Velvet pocket pouch landing)
            val pocketFile = File(cacheDir, "pocket.wav").apply {
                if (!exists() || length() == 0L) writeBytes(generateWav(130) { i, total ->
                    val t = i.toFloat() / sampleRate
                    val progress = i.toFloat() / total
                    val decay = (1f - progress).pow(1.9f)
                    val freq = 290f * (1f - progress * 0.45f)
                    val wave = sin(2f * PI.toFloat() * freq * t) * 0.7f + sin(2f * PI.toFloat() * (freq * 0.5f) * t) * 0.4f

                    val sample = wave * decay
                    (sample.coerceIn(-1f, 1f) * 27000f).toInt().toShort()
                })
            }
            pocketSoundId = pool.load(pocketFile.absolutePath, 1)

            // 5. Bright Coin / Gem Chime
            val coinFile = File(cacheDir, "coin.wav").apply {
                if (!exists() || length() == 0L) writeBytes(generateWav(140) { i, total ->
                    val t = i.toFloat() / sampleRate
                    val progress = i.toFloat() / total
                    val decay = 1f - progress
                    val freq = if (progress < 0.35f) 1046.50f else 1567.98f // C6 -> G6 harmonic
                    val wave = sin(2f * PI.toFloat() * freq * t) + 0.3f * sin(2f * PI.toFloat() * (freq * 2f) * t)
                    (wave * decay * 20000f).toInt().toShort()
                })
            }
            coinSoundId = pool.load(coinFile.absolutePath, 1)

            // 6. UI Soft Tap Click
            val clickFile = File(cacheDir, "click.wav").apply {
                if (!exists() || length() == 0L) writeBytes(generateWav(25) { i, total ->
                    val t = i.toFloat() / sampleRate
                    val decay = (1f - (i.toFloat() / total)).pow(2f)
                    val wave = sin(2f * PI.toFloat() * 950f * t)
                    (wave * decay * 16000f).toInt().toShort()
                })
            }
            clickSoundId = pool.load(clickFile.absolutePath, 1)

            // 7. Victory Fanfare Arpeggio
            val victoryFile = File(cacheDir, "victory.wav").apply {
                if (!exists() || length() == 0L) writeBytes(generateWav(450) { i, total ->
                    val t = i.toFloat() / sampleRate
                    val progress = i.toFloat() / total
                    val noteIdx = (progress * 4).toInt().coerceIn(0, 3)
                    val freq = when (noteIdx) {
                        0 -> 523.25f  // C5
                        1 -> 659.25f  // E5
                        2 -> 783.99f  // G5
                        else -> 1046.50f // C6
                    }
                    val localProgress = (progress * 4f) - noteIdx
                    val decay = (1f - localProgress).pow(1.4f)
                    val wave = sin(2f * PI.toFloat() * freq * t) + 0.25f * sin(2f * PI.toFloat() * (freq * 2f) * t)
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

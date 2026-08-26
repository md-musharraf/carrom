package com.example.royalcarromclassic.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.royalcarromclassic.core.logging.AppLogger

/**
 * Resilient, low-latency haptics controller.
 * Conforms to the HapticEngine contract.
 */
class HapticController(context: Context) : HapticEngine {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        AppLogger.w("HapticController", { "Failed to access system vibrator" }, e)
        null
    }

    override var isHapticEnabled: Boolean = true

    override fun vibrateShort(durationMs: Long) {
        if (!isHapticEnabled || vibrator == null || !vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (_: Exception) {
            // Guard against device-specific vibration exceptions
        }
    }

    override fun vibrateStrike() {
        if (!isHapticEnabled || vibrator == null || !vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 25, 20, 35),
                        intArrayOf(0, 200, 0, 255),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(40)
            }
        } catch (_: Exception) {
            vibrateShort(30)
        }
    }

    override fun vibratePocket() {
        if (!isHapticEnabled || vibrator == null || !vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 30, 25, 50),
                        intArrayOf(0, 150, 0, 220),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(60)
            }
        } catch (_: Exception) {
            vibrateShort(50)
        }
    }
}

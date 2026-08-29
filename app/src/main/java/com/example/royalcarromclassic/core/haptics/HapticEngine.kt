package com.example.royalcarromclassic.core.haptics

/**
 * Interface defining the haptic vibration contract for the game.
 * Follows the Dependency Inversion Principle (DIP).
 */
interface HapticEngine {
    var isHapticEnabled: Boolean

    fun vibrateShort(durationMs: Long = 15)
    fun vibrateTick()
    fun vibrateCollision(intensity: Float = 0.5f)
    fun vibrateStrike()
    fun vibratePocket()
}

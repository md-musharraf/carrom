package com.example.royalcarromclassic.core.audio

import com.example.royalcarromclassic.data.PlayStyle

/**
 * Interface defining the audio contract for the game.
 * Follows the Dependency Inversion Principle (DIP).
 */
interface AudioEngine {
    var isSoundEnabled: Boolean
    var isMusicEnabled: Boolean

    fun playClack(intensity: Float = 0.5f)
    fun playFlick(power: Float = 50f)
    fun playWall(intensity: Float = 0.5f)
    fun playPocket()
    fun playVictory()
    fun playCoin()
    fun playClick()
    fun release()

    /** Swaps the sound pack; Normal keeps the classic sounds. */
    fun setStyle(style: PlayStyle) {}
    fun playFoul() = playPocket()
    fun playQueen() = playPocket()
    fun playDefeat() {}
}

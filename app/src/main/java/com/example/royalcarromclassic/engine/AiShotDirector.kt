package com.example.royalcarromclassic.engine

import kotlin.math.abs

/** A striker set-up: baseline placement, aim direction (radians) and power (percent). */
data class ShotAim(
    val baselineOffset: Float,
    val angle: Float,
    val power: Float
)

/**
 * Choreographs the bot's turn as a sequence of eased, time-based phases
 * (consider → slide along the baseline → sweep the aim → draw back → release).
 *
 * Driven by the game loop with real frame times, so the bot moves as smoothly as the display allows.
 */
class AiShotDirector {

    private enum class Phase(val next: Phase?) {
        HOLDING(null),
        DRAWING_BACK(HOLDING),
        AIMING(DRAWING_BACK),
        SLIDING(AIMING),
        THINKING(SLIDING)
    }

    private var phase: Phase? = null
    private var elapsed = 0f
    private var duration = 0f
    private var start = ShotAim(0.5f, 0f, BoardGeometry.MIN_POWER)
    private var target = start

    /** The aim to show this frame. */
    var current: ShotAim = start
        private set

    val isActive: Boolean get() = phase != null

    fun begin(from: ShotAim, to: ShotAim) {
        start = from
        target = to.copy(angle = from.angle + shortestTurn(from.angle, to.angle))
        current = from
        enter(Phase.THINKING)
    }

    fun cancel() {
        phase = null
    }

    /**
     * Advances the choreography by [dtSeconds].
     * @return true exactly once, on the frame the shot should be released.
     */
    fun advance(dtSeconds: Float): Boolean {
        val active = phase ?: return false
        elapsed += dtSeconds
        val t = if (duration <= 0f) 1f else (elapsed / duration).coerceIn(0f, 1f)

        current = when (active) {
            Phase.THINKING, Phase.HOLDING -> current
            Phase.SLIDING -> current.copy(baselineOffset = lerp(start.baselineOffset, target.baselineOffset, easeInOutCubic(t)))
            Phase.AIMING -> current.copy(angle = lerp(start.angle, target.angle, easeInOutCubic(t)))
            Phase.DRAWING_BACK -> current.copy(power = lerp(BoardGeometry.MIN_POWER, target.power, easeOutCubic(t)))
        }

        if (t < 1f) return false
        val next = active.next
        if (next == null) {
            phase = null
            current = target.copy(angle = BoardGeometry.normalizeAngle(target.angle))
            return true
        }
        enter(next)
        return false
    }

    private fun enter(next: Phase) {
        phase = next
        elapsed = 0f
        duration = when (next) {
            Phase.THINKING -> 0.45f
            Phase.SLIDING -> 0.22f + 0.6f * abs(target.baselineOffset - start.baselineOffset)
            Phase.AIMING -> 0.5f
            Phase.DRAWING_BACK -> 0.4f
            Phase.HOLDING -> 0.2f
        }
    }

    companion object {
        /** Signed smallest rotation (radians) that turns [from] into [to]. */
        fun shortestTurn(from: Float, to: Float): Float = BoardGeometry.normalizeAngle(to - from)

        fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        fun easeInOutCubic(t: Float): Float =
            if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f

        fun easeOutCubic(t: Float): Float = 1f - (1f - t).let { it * it * it }
    }
}

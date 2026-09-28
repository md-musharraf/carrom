package com.example.royalcarromclassic.engine

import com.example.royalcarromclassic.data.Piece
import kotlin.math.hypot

/**
 * Fading ring buffer of recent striker positions, drawn as a soft motion trail.
 * Fixed capacity, no allocation after construction.
 */
class MotionTrail(private val capacity: Int = 14, private val lifetimeSeconds: Float = 0.16f) {
    private val xs = FloatArray(capacity)
    private val ys = FloatArray(capacity)
    private val ages = FloatArray(capacity)
    private var head = 0
    var size = 0
        private set

    fun push(x: Float, y: Float) {
        head = (head + 1) % capacity
        xs[head] = x
        ys[head] = y
        ages[head] = 0f
        if (size < capacity) size++
    }

    fun advance(dtSeconds: Float) {
        var alive = 0
        for (i in 0 until size) {
            val idx = indexOf(i)
            ages[idx] += dtSeconds
            if (ages[idx] < lifetimeSeconds) alive = i + 1
        }
        size = alive
    }

    fun clear() {
        size = 0
    }

    /** i = 0 is the newest sample. */
    fun x(i: Int): Float = xs[indexOf(i)]
    fun y(i: Int): Float = ys[indexOf(i)]

    /** 1 for a brand new sample, fading to 0 at the end of its lifetime. */
    fun strength(i: Int): Float = (1f - ages[indexOf(i)] / lifetimeSeconds).coerceIn(0f, 1f)

    private fun indexOf(i: Int): Int = ((head - i) % capacity + capacity) % capacity
}

/**
 * Transient, purely visual board state (particles, striker trail, striker placement bounce).
 * Owned by the ViewModel, advanced by the game loop and drawn by the board canvas.
 */
class BoardEffects(particleCapacity: Int = 96) {
    val particles = ParticleSystem(particleCapacity)
    val trail = MotionTrail()

    /** 0 → 1 while a freshly placed striker settles onto the baseline. */
    var strikerAppear: Float = 1f
        private set

    val isAnimating: Boolean
        get() = particles.hasActiveParticles || strikerAppear < 1f || trail.size > 0

    fun onStrikerPlaced() {
        strikerAppear = 0f
        trail.clear()
    }

    fun onShot() {
        trail.clear()
    }

    fun advance(dtSeconds: Float, striker: Piece?) {
        particles.update(dtSeconds)
        if (strikerAppear < 1f) {
            strikerAppear = (strikerAppear + dtSeconds / STRIKER_APPEAR_SECONDS).coerceAtMost(1f)
        }
        trail.advance(dtSeconds)
        if (striker != null && !striker.isPocketed && hypot(striker.vx, striker.vy) > TRAIL_MIN_SPEED) {
            trail.push(striker.x, striker.y)
        }
    }

    fun clear() {
        particles.clear()
        trail.clear()
        strikerAppear = 1f
    }

    private companion object {
        const val STRIKER_APPEAR_SECONDS = 0.32f
        const val TRAIL_MIN_SPEED = 6f
    }
}

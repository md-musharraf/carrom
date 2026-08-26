package com.example.royalcarromclassic.core.logging

/**
 * Observability helper for tracking game engine metrics,
 * physics frame duration, memory churn, and collision counts.
 */
object PerformanceTracker {
    private var lastFrameTimeNanos = 0L
    private var physicsTickCount = 0L
    private var totalPhysicsDurationNanos = 0L
    private var maxTickDurationNanos = 0L
    private var collisionCount = 0L

    fun recordPhysicsTickStart(): Long {
        return System.nanoTime()
    }

    fun recordPhysicsTickEnd(startNanos: Long) {
        val duration = System.nanoTime() - startNanos
        physicsTickCount++
        totalPhysicsDurationNanos += duration
        if (duration > maxTickDurationNanos) {
            maxTickDurationNanos = duration
        }
    }

    fun recordCollision() {
        collisionCount++
    }

    fun getAverageTickDurationMs(): Double {
        return if (physicsTickCount > 0) {
            (totalPhysicsDurationNanos / physicsTickCount.toDouble()) / 1_000_000.0
        } else {
            0.0
        }
    }

    fun getMaxTickDurationMs(): Double {
        return maxTickDurationNanos / 1_000_000.0
    }

    fun getCollisionCount(): Long = collisionCount

    fun reset() {
        physicsTickCount = 0L
        totalPhysicsDurationNanos = 0L
        maxTickDurationNanos = 0L
        collisionCount = 0L
        lastFrameTimeNanos = 0L
    }
}

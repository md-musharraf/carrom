package com.example.royalcarromclassic.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pre-allocated, zero-garbage particle entity.
 */
class Particle {
    var x: Float = 0f
    var y: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f
    var radius: Float = 2f
    var color: Color = Color(0xFFFBBF24)
    var alpha: Float = 1.0f
    var life: Int = 0
    var maxLife: Int = 20
    var isActive: Boolean = false

    fun reset(
        newX: Float,
        newY: Float,
        newVx: Float,
        newVy: Float,
        newRadius: Float,
        newColor: Color,
        newMaxLife: Int
    ) {
        x = newX
        y = newY
        vx = newVx
        vy = newVy
        radius = newRadius
        color = newColor
        alpha = 1.0f
        life = 0
        maxLife = newMaxLife
        isActive = true
    }
}

/**
 * High-performance, zero-allocation particle system.
 * Uses a fixed circular ring pool of pre-allocated particles,
 * completely eliminating GC pauses, array clones, and memory fragmentation.
 */
class ParticleSystem(private val maxCapacity: Int = 64) {
    private val pool: Array<Particle> = Array(maxCapacity) { Particle() }
    private var nextIndex: Int = 0

    fun spawnImpactSparks(x: Float, y: Float, color: Color = Color(0xFFFBBF24), count: Int = 6) {
        val clampedCount = count.coerceAtMost(maxCapacity)
        for (i in 0 until clampedCount) {
            val angle = Random.nextFloat() * (2f * Math.PI.toFloat())
            val speed = 1.2f + Random.nextFloat() * 3.8f
            val p = pool[nextIndex]
            nextIndex = (nextIndex + 1) % maxCapacity

            p.reset(
                newX = x,
                newY = y,
                newVx = cos(angle) * speed,
                newVy = sin(angle) * speed,
                newRadius = 1.5f + Random.nextFloat() * 2.0f,
                newColor = color,
                newMaxLife = 12 + Random.nextInt(10)
            )
        }
    }

    fun spawnPocketVortex(x: Float, y: Float, color: Color = Color(0xFFF59E0B)) {
        val vortexCount = 10
        for (i in 0 until vortexCount) {
            val angle = (i.toFloat() / vortexCount.toFloat()) * (2f * Math.PI.toFloat())
            val dist = 26f + Random.nextFloat() * 8f
            val p = pool[nextIndex]
            nextIndex = (nextIndex + 1) % maxCapacity

            p.reset(
                newX = x + cos(angle) * dist,
                newY = y + sin(angle) * dist,
                newVx = -cos(angle) * 1.4f + sin(angle) * 0.9f,
                newVy = -sin(angle) * 1.4f - cos(angle) * 0.9f,
                newRadius = 2.0f + Random.nextFloat() * 1.8f,
                newColor = color,
                newMaxLife = 18
            )
        }
    }

    fun update() {
        for (i in 0 until maxCapacity) {
            val p = pool[i]
            if (!p.isActive) continue

            p.life++
            p.x += p.vx
            p.y += p.vy
            p.vx *= 0.93f
            p.vy *= 0.93f
            p.alpha = max(0f, 1f - (p.life.toFloat() / p.maxLife.toFloat()))

            if (p.life >= p.maxLife) {
                p.isActive = false
            }
        }
    }

    fun draw(drawScope: DrawScope) {
        for (i in 0 until maxCapacity) {
            val p = pool[i]
            if (!p.isActive || p.alpha <= 0.01f) continue

            drawScope.drawCircle(
                color = p.color.copy(alpha = p.alpha),
                radius = p.radius,
                center = Offset(p.x, p.y)
            )
        }
    }

    fun clear() {
        for (i in 0 until maxCapacity) {
            pool[i].isActive = false
        }
    }
}

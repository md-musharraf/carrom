package com.example.royalcarromclassic.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pre-allocated, zero-garbage particle entity. Motion is in board units per second.
 */
class Particle {
    @JvmField var x: Float = 0f
    @JvmField var y: Float = 0f
    @JvmField var vx: Float = 0f
    @JvmField var vy: Float = 0f
    @JvmField var radius: Float = 2f
    /** Radius change per second (powder puffs bloom, sparks shrink). */
    @JvmField var growth: Float = 0f
    var color: Color = Color.White
    @JvmField var alpha: Float = 1f
    @JvmField var age: Float = 0f
    @JvmField var life: Float = 0.3f
    @JvmField var isRing: Boolean = false
    @JvmField var isActive: Boolean = false

    fun reset(
        newX: Float,
        newY: Float,
        newVx: Float,
        newVy: Float,
        newRadius: Float,
        newGrowth: Float,
        newColor: Color,
        newLife: Float,
        ring: Boolean = false
    ) {
        x = newX
        y = newY
        vx = newVx
        vy = newVy
        radius = newRadius
        growth = newGrowth
        color = newColor
        alpha = 1f
        age = 0f
        life = newLife
        isRing = ring
        isActive = true
    }
}

/**
 * High-performance, zero-allocation particle system.
 * A fixed ring pool of particles is recycled, so gameplay effects never trigger GC pauses.
 * Lifetimes are in seconds, so effects look identical at any display refresh rate.
 */
class ParticleSystem(private val maxCapacity: Int = 96) {
    private companion object {
        const val TWO_PI = (Math.PI * 2.0).toFloat()
        /** Fraction of velocity kept after one second (strong drag: effects settle quickly). */
        const val DRAG_PER_SECOND = 0.02f
        const val NOMINAL_DT = 1f / 60f
        val POWDER = Color(0xFFF7F1E3)
        val POCKET_GLOW = Color(0xFFE9C46A)
    }

    private val pool: Array<Particle> = Array(maxCapacity) { Particle() }
    private val ringStroke = Stroke(width = 1.6f)
    private var nextIndex: Int = 0
    private var activeCount: Int = 0

    /** True while any particle is still visible. */
    val hasActiveParticles: Boolean get() = activeCount > 0

    private fun obtain(): Particle {
        val p = pool[nextIndex]
        nextIndex = (nextIndex + 1) % maxCapacity
        if (!p.isActive) activeCount++
        return p
    }

    fun spawnImpactSparks(x: Float, y: Float, color: Color = Color(0xFFFBBF24), count: Int = 6) {
        repeat(count.coerceAtMost(maxCapacity)) {
            val angle = Random.nextFloat() * TWO_PI
            val speed = 70f + Random.nextFloat() * 160f
            obtain().reset(
                newX = x,
                newY = y,
                newVx = cos(angle) * speed,
                newVy = sin(angle) * speed,
                newRadius = 1.2f + Random.nextFloat() * 1.4f,
                newGrowth = -2.5f,
                newColor = color,
                newLife = 0.18f + Random.nextFloat() * 0.14f
            )
        }
    }

    /** Soft puff of carrom powder where two discs (or a disc and the frame) meet. */
    fun spawnCollisionDust(x: Float, y: Float, intensity: Float = 0.5f, count: Int = 5) {
        val strength = intensity.coerceIn(0f, 1f)
        repeat(count.coerceAtMost(maxCapacity)) {
            val angle = Random.nextFloat() * TWO_PI
            val speed = (18f + Random.nextFloat() * 55f) * (0.5f + strength)
            obtain().reset(
                newX = x + cos(angle) * 2f,
                newY = y + sin(angle) * 2f,
                newVx = cos(angle) * speed,
                newVy = sin(angle) * speed,
                newRadius = 2.2f + Random.nextFloat() * 2.2f,
                newGrowth = 9f + Random.nextFloat() * 8f,
                newColor = POWDER.copy(alpha = 0.30f + 0.35f * strength),
                newLife = 0.35f + Random.nextFloat() * 0.25f
            )
        }
    }

    /** Expanding rings and a few motes swirling into the net when a disc drops. */
    fun spawnPocketVortex(x: Float, y: Float, color: Color = POCKET_GLOW) {
        obtain().reset(x, y, 0f, 0f, 14f, 70f, color.copy(alpha = 0.75f), 0.42f, ring = true)
        obtain().reset(x, y, 0f, 0f, 8f, 55f, color.copy(alpha = 0.45f), 0.55f, ring = true)
        val motes = 8
        for (i in 0 until motes) {
            val angle = (i.toFloat() / motes) * TWO_PI + Random.nextFloat() * 0.3f
            val dist = 26f + Random.nextFloat() * 8f
            obtain().reset(
                newX = x + cos(angle) * dist,
                newY = y + sin(angle) * dist,
                newVx = -cos(angle) * 90f + sin(angle) * 60f,
                newVy = -sin(angle) * 90f - cos(angle) * 60f,
                newRadius = 1.6f + Random.nextFloat() * 1.4f,
                newGrowth = -2f,
                newColor = color,
                newLife = 0.36f
            )
        }
    }

    fun update(dtSeconds: Float = NOMINAL_DT) {
        if (activeCount == 0) return
        val drag = Math.pow(DRAG_PER_SECOND.toDouble(), dtSeconds.toDouble()).toFloat()
        for (i in 0 until maxCapacity) {
            val p = pool[i]
            if (!p.isActive) continue

            p.age += dtSeconds
            if (p.age >= p.life) {
                p.isActive = false
                activeCount--
                continue
            }
            p.x += p.vx * dtSeconds
            p.y += p.vy * dtSeconds
            p.vx *= drag
            p.vy *= drag
            p.radius = (p.radius + p.growth * dtSeconds).coerceAtLeast(0.2f)
            val remaining = 1f - p.age / p.life
            p.alpha = remaining * remaining
        }
    }

    fun draw(drawScope: DrawScope) {
        if (activeCount == 0) return
        for (i in 0 until maxCapacity) {
            val p = pool[i]
            if (!p.isActive || p.alpha <= 0.01f) continue
            val center = Offset(p.x, p.y)
            if (p.isRing) {
                drawScope.drawCircle(p.color, p.radius, center, alpha = p.alpha, style = ringStroke)
            } else {
                drawScope.drawCircle(p.color, p.radius, center, alpha = p.alpha)
            }
        }
    }

    fun clear() {
        for (i in 0 until maxCapacity) pool[i].isActive = false
        activeCount = 0
    }
}

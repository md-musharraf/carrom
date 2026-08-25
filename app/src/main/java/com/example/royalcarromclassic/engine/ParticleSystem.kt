package com.example.royalcarromclassic.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val color: Color,
    var alpha: Float = 1.0f,
    var life: Int = 0,
    val maxLife: Int = 20
)

class ParticleSystem {
    private val particles = CopyOnWriteArrayList<Particle>()

    fun spawnImpactSparks(x: Float, y: Float, color: Color = Color(0xFFFBBF24), count: Int = 8) {
        for (i in 0 until count) {
            val angle = Random.nextFloat() * (2f * Math.PI.toFloat())
            val speed = 1.5f + Random.nextFloat() * 4.5f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    radius = 1.5f + Random.nextFloat() * 2.5f,
                    color = color,
                    maxLife = 15 + Random.nextInt(12)
                )
            )
        }
    }

    fun spawnPocketVortex(x: Float, y: Float, color: Color = Color(0xFFF59E0B)) {
        for (i in 0 until 12) {
            val angle = (i.toFloat() / 12f) * (2f * Math.PI.toFloat())
            val dist = 30f + Random.nextFloat() * 10f
            particles.add(
                Particle(
                    x = x + cos(angle) * dist,
                    y = y + sin(angle) * dist,
                    vx = -cos(angle) * 1.5f + sin(angle) * 1.0f,
                    vy = -sin(angle) * 1.5f - cos(angle) * 1.0f,
                    radius = 2.0f + Random.nextFloat() * 2f,
                    color = color,
                    maxLife = 22
                )
            )
        }
    }

    fun update() {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.life++
            p.x += p.vx
            p.y += p.vy
            p.vx *= 0.94f
            p.vy *= 0.94f
            p.alpha = max(0f, 1f - (p.life.toFloat() / p.maxLife.toFloat()))

            if (p.life >= p.maxLife) {
                particles.remove(p)
            }
        }
    }

    fun draw(drawScope: DrawScope) {
        for (p in particles) {
            drawScope.drawCircle(
                color = p.color.copy(alpha = p.alpha),
                radius = p.radius,
                center = Offset(p.x, p.y)
            )
        }
    }

    fun clear() {
        particles.clear()
    }
}

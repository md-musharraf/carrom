package com.example.royalcarromclassic.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.royalcarromclassic.data.Vector2D
import com.example.royalcarromclassic.engine.BoardGeometry
import com.example.royalcarromclassic.engine.CarromPhysicsEngine.TrajectoryData
import com.example.royalcarromclassic.theme.CarromPalette
import kotlin.math.hypot

private val GuideIvory = Color(0xFFFFF6E2)
private val ghostStroke = Stroke(width = 1.6f)
private val pocketStroke = Stroke(width = 2.4f)
private val endStroke = Stroke(width = 1.2f)
private const val DOT_SPACING = 13f

/**
 * Draws the classic dotted aim guide: marching dots along the striker's predicted path, a ghost
 * striker at the contact point, and where the struck disc will travel (gold when it drops).
 *
 * @param phase 0..1 animation phase that marches the dots forward.
 * @param pulse 0..1 breathing value for pocket highlights.
 * @param subdued draw only a faint path (used while the bot is aiming).
 */
fun DrawScope.drawAimGuide(preview: TrajectoryData, phase: Float, pulse: Float, subdued: Boolean) {
    val path = preview.strikerPath
    if (path.size < 2) return
    val dotPhase = phase * DOT_SPACING

    if (subdued) {
        drawDottedPath(path, DOT_SPACING, dotPhase, 2.2f, GuideIvory, alpha = 0.45f)
        return
    }

    drawDottedPath(path, DOT_SPACING, dotPhase, 2.6f, GuideIvory, alpha = 0.95f)

    val ghost = preview.targetHitGhostPos
    when {
        ghost != null -> {
            val g = Offset(ghost.x, ghost.y)
            drawCircle(GuideIvory, BoardGeometry.STRIKER_RADIUS, g, alpha = 0.14f)
            drawCircle(GuideIvory, BoardGeometry.STRIKER_RADIUS, g, alpha = 0.85f, style = ghostStroke)

            val willDrop = preview.targetPocketId >= 0
            val targetColor = if (willDrop) CarromPalette.GoldLight else GuideIvory
            drawDottedPath(preview.targetPath, 10f, phase * 10f, 2.4f, targetColor, alpha = if (willDrop) 1f else 0.75f)
            if (willDrop) {
                drawPocketHighlight(preview.targetPocketId, CarromPalette.GoldLight, pulse)
            } else if (preview.targetPath.size >= 2) {
                val end = preview.targetPath.last()
                drawCircle(GuideIvory, BoardGeometry.PUCK_RADIUS, Offset(end.x, end.y), alpha = 0.4f, style = endStroke)
            }
            drawDottedPath(preview.strikerDeflectPath, 10f, phase * 10f, 1.8f, GuideIvory, alpha = 0.4f)
        }

        preview.strikerPocketId >= 0 -> drawPocketHighlight(preview.strikerPocketId, CarromPalette.CrimsonLight, pulse)

        else -> {
            val end = path.last()
            drawCircle(GuideIvory, BoardGeometry.STRIKER_RADIUS, Offset(end.x, end.y), alpha = 0.35f, style = endStroke)
        }
    }
}

private fun DrawScope.drawPocketHighlight(pocketId: Int, color: Color, pulse: Float) {
    val pocket = BoardGeometry.POCKETS.getOrNull(pocketId) ?: return
    val c = Offset(pocket.x, pocket.y)
    drawCircle(color, pocket.radius + 3f + pulse * 5f, c, alpha = 0.25f + 0.35f * (1f - pulse), style = pocketStroke)
    drawCircle(color, pocket.radius, c, alpha = 0.12f)
}

/**
 * Dots evenly spaced along a polyline, offset by [phase] so they appear to flow forward,
 * fading toward the end of the path. Allocation-free.
 */
private fun DrawScope.drawDottedPath(
    points: List<Vector2D>,
    spacing: Float,
    phase: Float,
    radius: Float,
    color: Color,
    alpha: Float
) {
    if (points.size < 2) return
    var total = 0f
    for (i in 0 until points.size - 1) {
        total += hypot(points[i + 1].x - points[i].x, points[i + 1].y - points[i].y)
    }
    if (total < 1f) return

    var distance = phase % spacing
    var segmentStart = 0f
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        val len = hypot(b.x - a.x, b.y - a.y)
        if (len < 0.001f) continue
        while (distance <= segmentStart + len) {
            val t = (distance - segmentStart) / len
            val f = distance / total
            drawCircle(
                color,
                radius * (1f - 0.35f * f),
                Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t),
                alpha = alpha * (1f - 0.7f * f * f)
            )
            distance += spacing
        }
        segmentStart += len
    }
}

/** Elastic slingshot band from the striker to the finger, plus a power gauge ring. */
fun DrawScope.drawPullBand(strikerX: Float, strikerY: Float, strikerRadius: Float, pull: Offset, powerFraction: Float) {
    val tension = tensionColor(powerFraction)
    val center = Offset(strikerX, strikerY)
    drawLine(tension, center, pull, strokeWidth = 3f, cap = StrokeCap.Round, alpha = 0.85f)
    drawCircle(tension, 9f, pull, alpha = 0.3f)
    drawCircle(tension, 5.5f, pull)
    drawCircle(Color.White, 2.2f, pull, alpha = 0.9f)

    val ringRadius = strikerRadius + 11f
    val topLeft = Offset(strikerX - ringRadius, strikerY - ringRadius)
    val ringSize = androidx.compose.ui.geometry.Size(ringRadius * 2f, ringRadius * 2f)
    drawArc(Color.White, -90f, 360f, false, topLeft, ringSize, alpha = 0.15f, style = gaugeStroke)
    drawArc(tension, -90f, 360f * powerFraction, false, topLeft, ringSize, style = gaugeStroke)
}

private val gaugeStroke = Stroke(width = 4f, cap = StrokeCap.Round)

/** Jade → amber → crimson as the pull tightens. */
fun tensionColor(fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return if (f < 0.5f) {
        androidx.compose.ui.graphics.lerp(CarromPalette.Jade, CarromPalette.Amber, f * 2f)
    } else {
        androidx.compose.ui.graphics.lerp(CarromPalette.Amber, CarromPalette.CrimsonLight, (f - 0.5f) * 2f)
    }
}

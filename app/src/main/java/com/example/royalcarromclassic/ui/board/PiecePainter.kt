package com.example.royalcarromclassic.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.example.royalcarromclassic.data.CoinSet
import com.example.royalcarromclassic.data.PieceType
import com.example.royalcarromclassic.data.StrikerConfig
import com.example.royalcarromclassic.engine.BoardGeometry
import com.example.royalcarromclassic.engine.PieceFactory
import com.example.royalcarromclassic.ui.components.starPath

/**
 * Cached brushes for one kind of disc, all centred on the origin so they can be reused at any
 * board position via `translate` — drawing a disc allocates nothing.
 */
class PieceArt(base: Color, edge: Color, val radius: Float) {
    val faceCenter = Offset(-radius * 0.03f, -radius * 0.05f)
    val shadow: Brush = Brush.radialGradient(
        0f to Color.Black.copy(alpha = 0.5f),
        0.55f to Color.Black.copy(alpha = 0.32f),
        1f to Color.Transparent,
        center = Offset.Zero,
        radius = radius * 1.32f
    )
    val side: Brush = Brush.linearGradient(
        listOf(lerp(edge, Color.White, 0.3f), edge, lerp(edge, Color.Black, 0.45f)),
        start = Offset(-radius, -radius),
        end = Offset(radius, radius)
    )
    val face: Brush = Brush.radialGradient(
        0f to lerp(base, Color.White, 0.36f),
        0.55f to base,
        1f to lerp(base, edge, 0.4f),
        center = Offset(-radius * 0.32f, -radius * 0.36f),
        radius = radius * 1.3f
    )
    val specular: Brush = Brush.radialGradient(
        listOf(Color.White.copy(alpha = 0.34f), Color.Transparent),
        center = Offset(-radius * 0.34f, -radius * 0.4f),
        radius = radius * 0.6f
    )
    val grooveDark: Color = lerp(edge, Color.Black, 0.25f)
    val grooveLight: Color = lerp(base, Color.White, 0.55f)
    val grooveStroke = Stroke(width = radius * 0.075f)
    val hairline = Stroke(width = radius * 0.045f)
    val rimHighlight = Stroke(width = radius * 0.08f, cap = StrokeCap.Round)

    companion object {
        fun forType(type: PieceType, radius: Float = BoardGeometry.PUCK_RADIUS): PieceArt {
            val (base, edge) = PieceFactory.colorsForType(type)
            return PieceArt(base, edge, radius)
        }
    }
}

/** Cached looks for the three kinds of carrom men in one [CoinSet], at [radius]. */
class CoinSetArt(val set: CoinSet, val radius: Float = BoardGeometry.PUCK_RADIUS) {
    val white = PieceArt(set.whiteFace, set.whiteEdge, radius)
    val black = PieceArt(set.blackFace, set.blackEdge, radius)
    val queen = PieceArt(set.queenFace, set.queenEdge, radius)

    fun forType(type: PieceType): PieceArt = when (type) {
        PieceType.WHITE -> white
        PieceType.BLACK -> black
        else -> queen
    }
}

/** A striker's cached look, derived from the equipped [StrikerConfig]. */
class StrikerArt(config: StrikerConfig, val radius: Float = BoardGeometry.STRIKER_RADIUS) {
    val body = PieceArt(config.primaryColor, lerp(config.secondaryColor, Color.Black, 0.15f), radius)
    val inlay: Color = config.glowColor
    val inlayDark: Color = lerp(config.secondaryColor, Color.Black, 0.35f)
    val emblem: Path = starPath(0f, 0f, radius * 0.4f, radius * 0.17f, points = 8)
    val emblemFill: Color = lerp(config.secondaryColor, config.glowColor, 0.25f)
    val glow: Color = config.glowColor
    val trail: Color = lerp(config.primaryColor, config.glowColor, 0.4f)
    val inlayStroke = Stroke(width = radius * 0.11f)
    val fineStroke = Stroke(width = radius * 0.04f)
}

/** Draws a classic turned-wood carrom man at ([x], [y]). */
fun DrawScope.drawCarromMan(
    art: PieceArt,
    x: Float,
    y: Float,
    scale: Float = 1f,
    alpha: Float = 1f,
    shadowAlpha: Float = 1f
) {
    val r = art.radius
    translate(x, y) {
        scale(scale, scale, pivot = Offset.Zero) {
            drawDiscBody(art, alpha, shadowAlpha)
            val c = art.faceCenter
            val lightOffset = Offset(c.x + r * 0.035f, c.y + r * 0.045f)
            for (ring in RINGS) {
                drawCircle(art.grooveDark, r * ring, c, alpha = alpha * 0.55f, style = art.grooveStroke)
                drawCircle(art.grooveLight, r * ring, lightOffset, alpha = alpha * 0.3f, style = art.hairline)
            }
            drawCircle(art.grooveDark, r * 0.11f, c, alpha = alpha * 0.6f)
            drawSheen(art, alpha)
        }
    }
}

/** Draws the striker at ([x], [y]) with its inlaid ring and star emblem. */
fun DrawScope.drawStriker(art: StrikerArt, x: Float, y: Float, scale: Float = 1f, alpha: Float = 1f) {
    val r = art.radius
    val body = art.body
    translate(x, y) {
        scale(scale, scale, pivot = Offset.Zero) {
            drawDiscBody(body, alpha, shadowAlpha = 1f)
            val c = body.faceCenter
            drawCircle(art.inlayDark, r * 0.79f, c, alpha = alpha * 0.6f, style = art.fineStroke)
            drawCircle(art.inlay, r * 0.72f, c, alpha = alpha, style = art.inlayStroke)
            drawCircle(art.inlayDark, r * 0.64f, c, alpha = alpha * 0.5f, style = art.fineStroke)
            translate(c.x, c.y) {
                drawPath(art.emblem, art.emblemFill, alpha = alpha * 0.9f)
                drawPath(art.emblem, art.inlayDark, alpha = alpha * 0.6f, style = art.fineStroke)
            }
            drawCircle(art.glow, r * 0.1f, c, alpha = alpha)
            drawCircle(Color.White, r * 0.045f, c + Offset(-r * 0.03f, -r * 0.03f), alpha = alpha * 0.8f)
            drawSheen(body, alpha)
        }
    }
}

private val RINGS = floatArrayOf(0.68f, 0.4f)

/** Contact shadow, bevelled side and turned face shared by all discs. */
private fun DrawScope.drawDiscBody(art: PieceArt, alpha: Float, shadowAlpha: Float) {
    val r = art.radius
    translate(r * 0.12f, r * 0.2f) {
        drawCircle(art.shadow, r * 1.32f, Offset.Zero, alpha = alpha * shadowAlpha)
    }
    drawCircle(art.side, r, Offset.Zero, alpha = alpha)
    drawCircle(art.face, r * 0.9f, art.faceCenter, alpha = alpha)
}

private fun DrawScope.drawSheen(art: PieceArt, alpha: Float) {
    val r = art.radius
    drawCircle(art.specular, r * 0.6f, Offset(-r * 0.34f, -r * 0.4f), alpha = alpha)
    drawArc(
        Color.White, startAngle = 190f, sweepAngle = 80f, useCenter = false,
        topLeft = Offset(-r * 0.93f, -r * 0.93f), size = Size(r * 1.86f, r * 1.86f),
        alpha = alpha * 0.4f, style = art.rimHighlight
    )
}

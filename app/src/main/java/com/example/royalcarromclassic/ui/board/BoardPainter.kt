package com.example.royalcarromclassic.ui.board

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import com.example.royalcarromclassic.data.BoardTheme
import com.example.royalcarromclassic.engine.BoardGeometry
import com.example.royalcarromclassic.ui.components.starPath
import kotlin.random.Random

private const val SIZE = BoardGeometry.BOARD_SIZE
private const val IN_MIN = BoardGeometry.PLAYABLE_MIN
private const val IN_MAX = BoardGeometry.PLAYABLE_MAX
private const val MID = BoardGeometry.CENTER
private const val FRAME_CORNER = 34f
private const val DIAGONAL = 0.70710677f

/**
 * Pre-built geometry and brushes for the static board layer, in board units.
 * Built once per theme; the grain is seeded from the theme id so it never shimmers or shifts.
 */
class BoardArt(val theme: BoardTheme, val pocketScale: Float = 1f) {

    /** One mitred frame plank: its outline, lengthwise wood grain and lighting. */
    class Plank(val shape: Path, val fill: Brush, val darkGrain: Path, val lightGrain: Path, val shade: Float)

    val ink: Color = theme.feltPatternColor
    val red: Color = theme.centerCircleColor

    val outline: Path = Path().apply {
        addRoundRect(RoundRect(0f, 0f, SIZE, SIZE, CornerRadius(FRAME_CORNER, FRAME_CORNER)))
    }

    val planks: List<Plank>
    val surfaceFill: Brush
    val surfaceGrain: Path
    val surfaceBands: Path
    val sheen: Brush
    val vignette: Brush
    val rosette: Path = starPath(MID, MID, 66f, 27f, points = 8)
    val pocketRims: List<Brush>
    val pocketHoles: List<Brush>
    val outerHighlight: Brush = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.22f), Color.Transparent, Color.Black.copy(alpha = 0.25f)),
        start = Offset.Zero,
        end = Offset(SIZE, SIZE)
    )

    // Cached strokes (board units).
    val grainStroke = Stroke(width = 0.9f)
    val bandStroke = Stroke(width = 7f)
    val baselineOuter = Stroke(width = 3f)
    val baselineInner = Stroke(width = 1.4f)
    val inkHairline = Stroke(width = 1.3f)
    val inkRing = Stroke(width = 1.7f)
    val inkBold = Stroke(width = 2.8f)
    val arrowStroke = Stroke(width = 1.8f, cap = StrokeCap.Round)
    val rimStroke = Stroke(width = 4.2f)
    val lipStroke = Stroke(width = 2.4f)

    init {
        val random = Random(theme.id.hashCode())
        val wood = theme.woodColor
        val woodLight = theme.woodInner

        fun plankFill(start: Offset, end: Offset) = Brush.linearGradient(
            0f to lerp(wood, Color.Black, 0.35f),
            0.3f to woodLight,
            0.55f to lerp(woodLight, Color.White, 0.08f),
            0.85f to wood,
            1f to lerp(wood, Color.Black, 0.5f),
            start = start,
            end = end
        )

        // Grain runs along each plank: u is the position along the plank, v across its width.
        fun grain(dark: Boolean, map: (u: Float, v: Float) -> Offset): Path = Path().apply {
            val lines = if (dark) 9 else 5
            repeat(lines) {
                var v = 3f + random.nextFloat() * (IN_MIN - 6f)
                var u = -20f
                val start = map(u, v)
                moveTo(start.x, start.y)
                while (u < SIZE + 20f) {
                    val step = 60f + random.nextFloat() * 70f
                    val nextV = (v + (random.nextFloat() - 0.5f) * 3.2f).coerceIn(2f, IN_MIN - 2f)
                    val c1 = map(u + step * 0.33f, v + (random.nextFloat() - 0.5f) * 2.5f)
                    val c2 = map(u + step * 0.66f, nextV + (random.nextFloat() - 0.5f) * 2.5f)
                    val end = map(u + step, nextV)
                    cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
                    u += step
                    v = nextV
                }
            }
        }

        fun plank(points: List<Offset>, fill: Brush, shade: Float, map: (Float, Float) -> Offset) = Plank(
            shape = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                close()
            },
            fill = fill,
            darkGrain = grain(dark = true, map),
            lightGrain = grain(dark = false, map),
            shade = shade
        )

        planks = listOf(
            plank(
                listOf(Offset(0f, 0f), Offset(SIZE, 0f), Offset(IN_MAX, IN_MIN), Offset(IN_MIN, IN_MIN)),
                plankFill(Offset(0f, 0f), Offset(0f, IN_MIN)), 0f
            ) { u, v -> Offset(u, v) },
            plank(
                listOf(Offset(SIZE, 0f), Offset(SIZE, SIZE), Offset(IN_MAX, IN_MAX), Offset(IN_MAX, IN_MIN)),
                plankFill(Offset(SIZE, 0f), Offset(IN_MAX, 0f)), 0.16f
            ) { u, v -> Offset(SIZE - v, u) },
            plank(
                listOf(Offset(SIZE, SIZE), Offset(0f, SIZE), Offset(IN_MIN, IN_MAX), Offset(IN_MAX, IN_MAX)),
                plankFill(Offset(0f, SIZE), Offset(0f, IN_MAX)), 0.2f
            ) { u, v -> Offset(u, SIZE - v) },
            plank(
                listOf(Offset(0f, SIZE), Offset(0f, 0f), Offset(IN_MIN, IN_MIN), Offset(IN_MIN, IN_MAX)),
                plankFill(Offset(0f, 0f), Offset(IN_MIN, 0f)), 0.06f
            ) { u, v -> Offset(v, u) }
        )

        val felt = theme.feltColor
        surfaceFill = Brush.radialGradient(
            0f to lerp(felt, Color.White, 0.10f),
            0.6f to felt,
            1f to lerp(felt, Color.Black, 0.10f),
            center = Offset(MID * 0.92f, MID * 0.88f),
            radius = SIZE * 0.72f
        )

        // Plywood surface grain: long, gently wandering horizontal fibres plus broad soft bands.
        surfaceGrain = Path().apply {
            repeat(70) {
                var y = IN_MIN + random.nextFloat() * (IN_MAX - IN_MIN)
                var x = IN_MIN
                moveTo(x, y)
                while (x < IN_MAX) {
                    val step = 90f + random.nextFloat() * 110f
                    val nextY = y + (random.nextFloat() - 0.5f) * 5f
                    cubicTo(x + step * 0.35f, y + (random.nextFloat() - 0.5f) * 4f, x + step * 0.7f, nextY, x + step, nextY)
                    x += step
                    y = nextY
                }
            }
        }
        surfaceBands = Path().apply {
            repeat(9) {
                val y = IN_MIN + 20f + random.nextFloat() * (IN_MAX - IN_MIN - 40f)
                moveTo(IN_MIN, y)
                cubicTo(MID * 0.7f, y + 10f * (random.nextFloat() - 0.5f), MID * 1.3f, y + 14f * (random.nextFloat() - 0.5f), IN_MAX, y)
            }
        }

        sheen = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.13f), Color.Transparent, Color.Transparent, Color.White.copy(alpha = 0.04f)),
            start = Offset(IN_MIN, IN_MIN),
            end = Offset(IN_MAX, IN_MAX)
        )
        vignette = Brush.radialGradient(
            0f to Color.Transparent,
            0.62f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.2f),
            center = Offset(MID, MID),
            radius = (IN_MAX - IN_MIN) * 0.72f
        )

        val rim = theme.pocketRimColor
        pocketRims = BoardGeometry.POCKETS.map { p ->
            Brush.sweepGradient(
                listOf(
                    lerp(rim, Color.White, 0.45f), rim, lerp(rim, Color.Black, 0.45f), rim,
                    lerp(rim, Color.White, 0.45f)
                ),
                center = Offset(p.x, p.y)
            )
        }
        pocketHoles = BoardGeometry.POCKETS.map { p ->
            Brush.radialGradient(
                0f to Color.Black,
                0.62f to Color(0xFF070403),
                1f to Color(0xFF2A1B12),
                center = Offset(p.x - 6f, p.y - 6f),
                radius = BoardGeometry.POCKET_RADIUS * pocketScale + 4f
            )
        }
    }
}

/** Paints the complete static board (frame, surface, markings and pockets) in board units. */
fun DrawScope.drawCarromBoard(art: BoardArt) {
    clipPath(art.outline) {
        drawFrame(art)
        drawSurface(art)
        drawMarkings(art)
        drawFrameShadows()
        drawPockets(art)
    }
    drawPath(art.outline, art.outerHighlight, style = Stroke(width = 2.5f))
}

private fun DrawScope.drawFrame(art: BoardArt) {
    for (plank in art.planks) {
        drawPath(plank.shape, plank.fill)
        clipPath(plank.shape) {
            drawPath(plank.darkGrain, Color.Black, alpha = 0.16f, style = art.grainStroke)
            drawPath(plank.lightGrain, Color.White, alpha = 0.07f, style = art.grainStroke)
        }
        if (plank.shade > 0f) drawPath(plank.shape, Color.Black, alpha = plank.shade)
    }
    // Mitre joints: a dark seam with a lit edge beside it.
    val corners = listOf(
        Offset(0f, 0f) to Offset(IN_MIN, IN_MIN),
        Offset(SIZE, 0f) to Offset(IN_MAX, IN_MIN),
        Offset(SIZE, SIZE) to Offset(IN_MAX, IN_MAX),
        Offset(0f, SIZE) to Offset(IN_MIN, IN_MAX)
    )
    for ((outer, inner) in corners) {
        drawLine(Color.Black.copy(alpha = 0.55f), outer, inner, strokeWidth = 1.6f)
        drawLine(Color.White.copy(alpha = 0.10f), outer + Offset(1.2f, 0f), inner + Offset(1.2f, 0f), strokeWidth = 0.8f)
    }
}

private fun DrawScope.drawSurface(art: BoardArt) {
    val topLeft = Offset(IN_MIN, IN_MIN)
    val size = Size(IN_MAX - IN_MIN, IN_MAX - IN_MIN)
    drawRect(art.surfaceFill, topLeft, size)
    clipToRect(topLeft, size) {
        drawPath(art.surfaceBands, lerp(art.theme.feltColor, Color.Black, 0.4f), alpha = 0.035f, style = art.bandStroke)
        drawPath(art.surfaceGrain, lerp(art.theme.feltColor, Color.Black, 0.45f), alpha = 0.07f, style = art.grainStroke)
    }
    drawRect(art.sheen, topLeft, size)
    drawRect(art.vignette, topLeft, size)
}

private inline fun DrawScope.clipToRect(topLeft: Offset, size: Size, block: DrawScope.() -> Unit) =
    clipRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height, block = block)

private fun DrawScope.drawMarkings(art: BoardArt) {
    val center = Offset(MID, MID)

    // Four baselines (double rule with red foul circles) and four corner arrows, drawn once
    // for the bottom-right quadrant and rotated into place.
    for (quarter in 0 until 4) {
        rotate(quarter * 90f, center) {
            drawBaseline(art)
            drawCornerArrow(art)
        }
    }

    // Centre: double ring, red rosette, inner ring and the queen's spot.
    drawCircle(art.ink, 92f, center, alpha = 0.85f, style = art.inkHairline)
    drawCircle(art.ink, 86f, center, alpha = 0.9f, style = art.inkBold)
    drawPath(art.rosette, art.red, alpha = 0.9f)
    drawPath(art.rosette, art.ink, alpha = 0.85f, style = art.inkHairline)
    drawCircle(art.ink, 27f, center, alpha = 0.85f, style = art.inkHairline)
    drawCircle(art.red, BoardGeometry.PUCK_RADIUS, center)
    drawCircle(art.ink, BoardGeometry.PUCK_RADIUS, center, alpha = 0.9f, style = art.inkRing)
}

/** Bottom baseline: an outer bold rule and inner fine rule, tangent to two red foul circles. */
private fun DrawScope.drawBaseline(art: BoardArt) {
    val y = BoardGeometry.BASELINE_BOTTOM_Y
    val r = BoardGeometry.BASELINE_CIRCLE_RADIUS
    val start = BoardGeometry.BASELINE_START_X
    val end = BoardGeometry.BASELINE_END_X
    drawLine(art.ink, Offset(start, y + r), Offset(end, y + r), strokeWidth = art.baselineOuter.width, alpha = 0.9f)
    drawLine(art.ink, Offset(start, y - r), Offset(end, y - r), strokeWidth = art.baselineInner.width, alpha = 0.9f)
    for (x in floatArrayOf(start, end)) {
        drawCircle(art.red, r - 0.5f, Offset(x, y))
        drawCircle(Color.White, r * 0.45f, Offset(x - r * 0.25f, y - r * 0.3f), alpha = 0.08f)
        drawCircle(art.ink, r, Offset(x, y), alpha = 0.9f, style = art.inkRing)
    }
}

/** Bottom-right diagonal: an arrow from the pocket towards the centre ending in a curled hook. */
private fun DrawScope.drawCornerArrow(art: BoardArt) {
    val pocket = BoardGeometry.POCKETS[2]
    fun along(d: Float) = Offset(pocket.x - d * DIAGONAL, pocket.y - d * DIAGONAL)
    val tail = along(58f)
    val tip = along(232f)
    val hookCenter = along(206f)
    val hookRadius = 24f

    drawLine(art.ink, tail, tip, strokeWidth = art.arrowStroke.width, cap = StrokeCap.Round, alpha = 0.85f)
    // Hook opens towards the pocket, so the shaft passes through it to the tip.
    drawArc(
        art.ink, startAngle = 45f + 50f, sweepAngle = 260f, useCenter = false,
        topLeft = Offset(hookCenter.x - hookRadius, hookCenter.y - hookRadius),
        size = Size(hookRadius * 2f, hookRadius * 2f),
        alpha = 0.85f,
        style = art.arrowStroke
    )
    // Arrow head pointing into the pocket: barbs at ±45° back from the tail along the diagonal.
    val barb = 9f
    drawLine(art.ink, tail, Offset(tail.x - barb, tail.y), strokeWidth = art.arrowStroke.width, cap = StrokeCap.Round, alpha = 0.85f)
    drawLine(art.ink, tail, Offset(tail.x, tail.y - barb), strokeWidth = art.arrowStroke.width, cap = StrokeCap.Round, alpha = 0.85f)
}

/** The frame is raised above the surface: it casts a soft shadow inward (light from top-left). */
private fun DrawScope.drawFrameShadows() {
    val span = IN_MAX - IN_MIN
    drawRect(
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.34f), Color.Transparent), IN_MIN, IN_MIN + 16f),
        Offset(IN_MIN, IN_MIN), Size(span, 16f)
    )
    drawRect(
        Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.26f), Color.Transparent), IN_MIN, IN_MIN + 14f),
        Offset(IN_MIN, IN_MIN), Size(14f, span)
    )
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.14f)), IN_MAX - 10f, IN_MAX),
        Offset(IN_MIN, IN_MAX - 10f), Size(span, 10f)
    )
    drawRect(
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.14f)), IN_MAX - 10f, IN_MAX),
        Offset(IN_MAX - 10f, IN_MIN), Size(10f, span)
    )
    // Polished cushion lip.
    drawRect(Color.Black.copy(alpha = 0.55f), Offset(IN_MIN, IN_MIN), Size(span, span), style = Stroke(width = 1.6f))
}

private fun DrawScope.drawPockets(art: BoardArt) {
    val recess = lerp(art.theme.woodColor, Color.Black, 0.55f)
    BoardGeometry.POCKETS.forEachIndexed { i, pocket ->
        val c = Offset(pocket.x, pocket.y)
        // Wide-pocket boards really are cut wider, so the power is visible at a glance.
        val r = pocket.radius * art.pocketScale
        drawCircle(recess, r + 7f, c)
        drawCircle(art.pocketRims[i], r + 3f, c, style = art.rimStroke)
        drawCircle(art.pocketHoles[i], r, c)
        drawCircle(Color.Black, r + 0.8f, c, alpha = 0.6f, style = art.lipStroke)
    }
}

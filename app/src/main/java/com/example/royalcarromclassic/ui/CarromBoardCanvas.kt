package com.example.royalcarromclassic.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.royalcarromclassic.data.*
import com.example.royalcarromclassic.engine.BoardEffects
import com.example.royalcarromclassic.engine.BoardGeometry
import com.example.royalcarromclassic.engine.CarromPhysicsEngine.TrajectoryData
import com.example.royalcarromclassic.engine.LuckyShot
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.board.BoardArt
import com.example.royalcarromclassic.ui.board.PieceArt
import com.example.royalcarromclassic.ui.board.StrikerArt
import com.example.royalcarromclassic.ui.board.drawAimGuide
import com.example.royalcarromclassic.ui.board.drawCarromBoard
import com.example.royalcarromclassic.ui.board.drawCarromMan
import com.example.royalcarromclassic.ui.board.drawPullBand
import com.example.royalcarromclassic.ui.board.drawStriker
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

private const val STRIKER_TOUCH_SLOP = 30f
private const val RAIL_TOUCH_SLOP = 36f
private const val MIN_PULL = 18f
private const val MAX_PULL = 150f

private val ZeroState: State<Float> = object : State<Float> {
    override val value: Float = 0f
}
private val haloStroke = Stroke(width = 2f)

/**
 * The carrom board, rendered in two layers:
 *  - a static layer (frame, surface, markings, pockets) cached in an offscreen layer that is
 *    only re-rasterised when the board theme changes;
 *  - a dynamic layer (discs, striker, guide, effects) redrawn when [frameTick] advances.
 *
 * Touch model: drag the striker sideways (or anywhere on the rail) to place it, pull it
 * backwards like a slingshot and release to shoot, or touch the board elsewhere to aim at a point.
 */
@Composable
fun CarromBoardCanvas(
    pieces: List<Piece>,
    striker: Piece?,
    boardTheme: BoardTheme,
    strikerConfig: StrikerConfig,
    gameState: GameState,
    aimPreview: TrajectoryData?,
    effects: BoardEffects,
    frameTick: State<Long>,
    onPositionChanged: (Float) -> Unit,
    onAimChanged: (angle: Float, power: Float) -> Unit,
    onShoot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val boardArt = remember(boardTheme) { BoardArt(boardTheme) }
    val whiteArt = remember { PieceArt.forType(PieceType.WHITE) }
    val blackArt = remember { PieceArt.forType(PieceType.BLACK) }
    val queenArt = remember { PieceArt.forType(PieceType.QUEEN) }
    val strikerArt = remember(strikerConfig) { StrikerArt(strikerConfig) }
    fun artFor(type: PieceType) = when (type) {
        PieceType.WHITE -> whiteArt
        PieceType.BLACK -> blackArt
        else -> queenArt
    }

    val canAim = gameState.canAim
    val showGuide = aimPreview != null && !gameState.isGameOver

    // The marching guide and the "ready" halo only animate while the player can act.
    val phase: State<Float>
    val pulse: State<Float>
    if (showGuide && canAim) {
        val transition = rememberInfiniteTransition(label = "aimGuide")
        phase = transition.animateFloat(
            0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "guidePhase"
        )
        pulse = transition.animateFloat(
            0f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "guidePulse"
        )
    } else {
        phase = ZeroState
        pulse = ZeroState
    }

    var pullPoint by remember { mutableStateOf<Offset?>(null) }

    val currentStriker by rememberUpdatedState(striker)
    val currentState by rememberUpdatedState(gameState)
    val currentOnPosition by rememberUpdatedState(onPositionChanged)
    val currentOnAim by rememberUpdatedState(onAimChanged)
    val currentOnShoot by rememberUpdatedState(onShoot)

    Box(
        modifier
            .aspectRatio(1f)
            .shadow(18.dp, RoundedCornerShape(percent = 4), clip = false)
            .semantics { contentDescription = "Carrom board" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val state = currentState
                    val s = currentStriker
                    if (!state.canAim || s == null || s.isPocketed) return@awaitEachGesture

                    val scale = size.width / BoardGeometry.BOARD_SIZE
                    fun toBoard(p: Offset) = Offset(p.x / scale, p.y / scale)
                    val start = toBoard(down.position)
                    val baselineY = if (state.isBottomTurn) BoardGeometry.BASELINE_BOTTOM_Y else BoardGeometry.BASELINE_TOP_Y
                    val onStriker = hypot(start.x - s.x, start.y - s.y) < s.radius + STRIKER_TOUCH_SLOP
                    val onRail = abs(start.y - baselineY) < RAIL_TOUCH_SLOP &&
                        start.x in (BoardGeometry.BASELINE_START_X - RAIL_TOUCH_SLOP)..(BoardGeometry.BASELINE_END_X + RAIL_TOUCH_SLOP)

                    fun slideTo(p: Offset) = currentOnPosition(BoardGeometry.baselineFractionAt(p.x))
                    fun aimAt(p: Offset) {
                        if (hypot(p.x - s.x, p.y - s.y) > s.radius) {
                            currentOnAim(atan2(p.y - s.y, p.x - s.x), currentState.strikerPower)
                        }
                    }
                    fun pullTo(p: Offset) {
                        pullPoint = p
                        val dx = p.x - s.x
                        val dy = p.y - s.y
                        val dist = hypot(dx, dy)
                        if (dist >= MIN_PULL) {
                            val fraction = ((dist - MIN_PULL) / (MAX_PULL - MIN_PULL)).coerceIn(0f, 1f)
                            val power = BoardGeometry.MIN_POWER + fraction * (BoardGeometry.MAX_POWER - BoardGeometry.MIN_POWER)
                            currentOnAim(atan2(-dy, -dx), power)
                        }
                    }

                    when {
                        onStriker -> {
                            var slingshot = false
                            val first = awaitTouchSlopOrCancellation(down.id) { change, over ->
                                change.consume()
                                val backward = if (state.isBottomTurn) over.y else -over.y
                                slingshot = backward > abs(over.x) * 0.5f
                            } ?: return@awaitEachGesture

                            if (slingshot) {
                                pullTo(toBoard(first.position))
                                val completed = drag(first.id) { change ->
                                    change.consume()
                                    pullTo(toBoard(change.position))
                                }
                                val released = pullPoint
                                pullPoint = null
                                if (completed && released != null && hypot(released.x - s.x, released.y - s.y) >= MIN_PULL) {
                                    currentOnShoot()
                                }
                            } else {
                                slideTo(toBoard(first.position))
                                drag(first.id) { change ->
                                    change.consume()
                                    slideTo(toBoard(change.position))
                                }
                            }
                        }

                        onRail -> {
                            slideTo(start)
                            drag(down.id) { change ->
                                change.consume()
                                slideTo(toBoard(change.position))
                            }
                        }

                        else -> {
                            aimAt(start)
                            drag(down.id) { change ->
                                change.consume()
                                aimAt(toBoard(change.position))
                            }
                        }
                    }
                }
            }
    ) {
        // Static layer: rasterised once into an offscreen layer and reused every frame.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            inBoardUnits { drawCarromBoard(boardArt) }
        }

        if (gameState.mode == GameMode.LUCKY_SHOT) PrizeRings(Modifier.fillMaxSize())

        // Dynamic layer.
        Canvas(Modifier.fillMaxSize()) {
            frameTick.value // Invalidate this layer whenever the game loop advances.
            inBoardUnits {
                effects.particles.draw(this)

                if (showGuide) {
                    drawAimGuide(aimPreview, phase.value, pulse.value, subdued = !canAim)
                }

                // Discs dropping into pockets sit beneath the discs still rolling.
                for (i in pieces.indices) {
                    val p = pieces[i]
                    if (p.isPocketed && p.pocketProgress > 0f) drawSinking(p) { x, y, scale, alpha ->
                        drawCarromMan(artFor(p.type), x, y, scale, alpha, shadowAlpha = alpha)
                    }
                }
                for (i in pieces.indices) {
                    val p = pieces[i]
                    if (!p.isPocketed) drawCarromMan(artFor(p.type), p.x, p.y)
                }

                if (striker != null) {
                    drawStrikerLayer(striker, strikerArt, effects, halo = canAim && pullPoint == null, pulse = pulse.value)
                    val pull = pullPoint
                    if (pull != null && !striker.isPocketed) {
                        val fraction = (gameState.strikerPower - BoardGeometry.MIN_POWER) /
                            (BoardGeometry.MAX_POWER - BoardGeometry.MIN_POWER)
                        drawPullBand(striker.x, striker.y, striker.radius, pull, fraction)
                    }
                }
            }
        }
    }
}

/** Lucky Shot's target: concentric prize rings that shimmer gently, labelled with their coins. */
@Composable
private fun PrizeRings(modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val shimmer by rememberInfiniteTransition(label = "prizeRings").animateFloat(
        0.75f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ringShimmer"
    )
    val labelStyle = MaterialTheme.typography.labelSmall
    Canvas(modifier) {
        val s = size.width / BoardGeometry.BOARD_SIZE
        val c = Offset(LuckyShot.TARGET.x * s, LuckyShot.TARGET.y * s)
        val rings = LuckyShot.RINGS
        for (i in rings.indices.reversed()) {
            val ring = rings[i]
            val fill = when (i) {
                0 -> CarromPalette.GoldLight
                1 -> CarromPalette.Gold
                2 -> CarromPalette.Crimson
                else -> CarromPalette.Parchment
            }
            drawCircle(fill, ring.radius * s, c, alpha = (if (i == 0) 0.75f else 0.3f) * shimmer)
            drawCircle(CarromPalette.GoldDeep, ring.radius * s, c, alpha = 0.9f, style = Stroke(1.5f * s))
        }
        // Bullseye label in the middle; the others fan out (top, left, right) so they never collide.
        rings.forEachIndexed { i, ring ->
            val inner = if (i == 0) 0f else rings[i - 1].radius
            val mid = (inner + ring.radius) / 2f * s
            val label = measurer.measure("${ring.prize}", labelStyle.copy(color = if (i == 0) CarromPalette.Ink else CarromPalette.Ivory))
            val at = when (i) {
                0 -> c
                1 -> Offset(c.x, c.y - mid)
                2 -> Offset(c.x - mid, c.y)
                else -> Offset(c.x + mid, c.y)
            }
            drawText(label, topLeft = Offset(at.x - label.size.width / 2f, at.y - label.size.height / 2f))
        }
    }
}

private inline fun DrawScope.inBoardUnits(block: DrawScope.() -> Unit) {
    val s = size.width / BoardGeometry.BOARD_SIZE
    scale(s, s, pivot = Offset.Zero, block = block)
}

private fun DrawScope.drawStrikerLayer(striker: Piece, art: StrikerArt, effects: BoardEffects, halo: Boolean, pulse: Float) {
    if (striker.isPocketed) {
        if (striker.pocketProgress > 0f) drawSinking(striker) { x, y, scale, alpha -> drawStriker(art, x, y, scale, alpha) }
        return
    }

    val trail = effects.trail
    for (i in trail.size - 1 downTo 1) {
        val fade = 1f - i.toFloat() / trail.size
        drawCircle(
            art.trail,
            art.radius * (0.55f + 0.4f * fade),
            Offset(trail.x(i), trail.y(i)),
            alpha = 0.22f * fade * trail.strength(i)
        )
    }

    if (halo) {
        drawCircle(
            CarromPalette.GoldLight,
            art.radius + 4f + 9f * pulse,
            Offset(striker.x, striker.y),
            alpha = 0.55f * (1f - pulse),
            style = haloStroke
        )
    }

    val appear = effects.strikerAppear
    val scale = 0.55f + 0.45f * easeOutBack(appear)
    drawStriker(art, striker.x, striker.y, scale = scale, alpha = (appear * 3f).coerceAtMost(1f))
}

/**
 * A pocketed disc slides to the pocket centre, shrinks and darkens as it drops into the net.
 */
private inline fun DrawScope.drawSinking(
    piece: Piece,
    draw: DrawScope.(x: Float, y: Float, scale: Float, alpha: Float) -> Unit
) {
    val pocket = BoardGeometry.POCKETS.getOrNull(piece.pocketId) ?: return
    val t = 1f - piece.pocketProgress
    val slide = 1f - (1f - (t * 1.6f).coerceAtMost(1f)).let { it * it * it }
    val x = piece.x + (pocket.x - piece.x) * slide
    val y = piece.y + (pocket.y - piece.y) * slide
    val scale = 1f - 0.4f * t * t
    draw(x, y, scale, 1f - 0.5f * t * t)
    drawCircle(Color.Black, piece.radius * scale, Offset(x, y), alpha = 0.75f * t)
}

private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val u = t - 1f
    return 1f + c3 * u * u * u + c1 * u * u
}

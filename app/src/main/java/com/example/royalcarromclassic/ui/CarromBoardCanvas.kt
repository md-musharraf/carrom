package com.example.royalcarromclassic.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitDragOrCancellation
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
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
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
import com.example.royalcarromclassic.engine.Powers
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.board.BoardArt
import com.example.royalcarromclassic.ui.board.CoinSetArt
import com.example.royalcarromclassic.ui.board.ShotGesture
import com.example.royalcarromclassic.ui.board.StrikerArt
import com.example.royalcarromclassic.ui.board.drawAimGuide
import com.example.royalcarromclassic.ui.board.drawCarromBoard
import com.example.royalcarromclassic.ui.board.drawCarromMan
import com.example.royalcarromclassic.ui.board.drawPullBand
import com.example.royalcarromclassic.ui.board.drawStriker
import kotlin.math.atan2
import kotlin.math.hypot

/** Pull distances and touch targets are set in dp, so the slingshot feels the same on every screen. */
private val MIN_PULL_DP = 14.dp
private val MAX_PULL_DP = 112.dp
private val FINGERTIP_DP = 30.dp
private val RAIL_SLOP_DP = 20.dp

private val ZeroState: State<Float> = object : State<Float> {
    override val value: Float = 0f
}
private val haloStroke = Stroke(width = 2f)
private val wideRingStroke = Stroke(width = 2.2f)

/**
 * The carrom board, rendered in two layers:
 *  - a static layer (frame, surface, markings, pockets) cached in an offscreen layer that is
 *    only re-rasterised when the board theme changes;
 *  - a dynamic layer (discs, striker, guide, effects) redrawn when [frameTick] advances.
 *
 * Touch model (see [ShotGesture]): drag the striker along its baseline (or touch the rail) to
 * place it, pull it back like a slingshot and release to shoot, or touch the board anywhere to aim
 * at that point. Works from any of the four seats.
 */
@Composable
fun CarromBoardCanvas(
    pieces: List<Piece>,
    striker: Piece?,
    boardTheme: BoardTheme,
    strikerConfig: StrikerConfig,
    coinSet: CoinSet,
    gameState: GameState,
    aimPreview: TrajectoryData?,
    effects: BoardEffects,
    frameTick: State<Long>,
    onPositionChanged: (Float) -> Unit,
    onAimChanged: (angle: Float, power: Float) -> Unit,
    onShoot: () -> Unit,
    modifier: Modifier = Modifier,
    /** How wide the table's pockets are cut (the Wide Pockets board). */
    pocketScale: Float = 1f,
    /** How strongly a pull snaps onto the aim chosen by touching the board. */
    aimMagnetDegrees: Float = 5f
) {
    val boardArt = remember(boardTheme, pocketScale) { BoardArt(boardTheme, pocketScale) }
    val discRadius = pieces.firstOrNull()?.radius ?: BoardGeometry.PUCK_RADIUS
    val coinArt = remember(coinSet, discRadius) { CoinSetArt(coinSet, discRadius) }
    val strikerArt = remember(strikerConfig) { StrikerArt(strikerConfig) }

    val canAim = gameState.canAim
    val showGuide = aimPreview != null && !gameState.isGameOver
    val widePocketRoll = gameState.dice?.face == DiceFace.WIDE_POCKETS && gameState.turnState != TurnState.MOVING

    // The marching guide and the "ready" halo only animate while the player can act.
    val phase: State<Float>
    val pulse: State<Float>
    if ((showGuide && canAim) || widePocketRoll) {
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
    var pullArmed by remember { mutableStateOf(false) }

    val currentStriker by rememberUpdatedState(striker)
    val currentState by rememberUpdatedState(gameState)
    val currentPieces by rememberUpdatedState(pieces)
    val currentMagnet by rememberUpdatedState(aimMagnetDegrees)
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
                    fun dpUnits(dp: Dp) = dp.toPx() / scale
                    val minPull = dpUnits(MIN_PULL_DP)
                    val maxPull = maxOf(dpUnits(MAX_PULL_DP), minPull + 60f)
                    val seat = state.currentSeat
                    val start = toBoard(down.position)
                    val onStriker = ShotGesture.touchesStriker(start.x, start.y, s.x, s.y, s.radius, dpUnits(FINGERTIP_DP))
                    val onDisc = currentPieces.any { !it.isPocketed && hypot(it.x - start.x, it.y - start.y) < it.radius + 4f }
                    val onRail = !onDisc && ShotGesture.touchesRail(start.x, start.y, seat, dpUnits(RAIL_SLOP_DP))
                    // A pull that lines up with an aim chosen by touching the board keeps that aim.
                    val lockedAngle = state.strikerAimAngle.takeIf { state.turnState == TurnState.AIMING }
                    val magnet = currentMagnet * BoardGeometry.DEG_TO_RAD

                    fun slideTo(p: Offset) = currentOnPosition(BoardGeometry.baselineFractionAt(p.x, p.y, seat))
                    fun aimAt(p: Offset) {
                        if (hypot(p.x - s.x, p.y - s.y) > s.radius) {
                            currentOnAim(atan2(p.y - s.y, p.x - s.x), currentState.strikerPower)
                        }
                    }
                    fun pullTo(p: Offset) {
                        pullPoint = p
                        val pull = ShotGesture.pull(s.x, s.y, p.x, p.y, minPull, maxPull, lockedAngle, magnet)
                        pullArmed = pull != null
                        if (pull != null) currentOnAim(pull.angle, pull.power)
                    }
                    suspend fun AwaitPointerEventScope.pullFrom(pointer: PointerId, at: Offset) {
                        pullTo(at)
                        val completed = drag(pointer) { change ->
                            change.consume()
                            pullTo(toBoard(change.position))
                        }
                        val armed = pullArmed
                        pullPoint = null
                        pullArmed = false
                        if (completed && armed) currentOnShoot()
                    }

                    when {
                        onStriker -> {
                            var kind = ShotGesture.Kind.PULL
                            val first = awaitTouchSlopOrCancellation(down.id) { change, over ->
                                change.consume()
                                kind = ShotGesture.classify(over.x, over.y, seat)
                            } ?: return@awaitEachGesture

                            when (kind) {
                                ShotGesture.Kind.PULL -> pullFrom(first.id, toBoard(first.position))
                                ShotGesture.Kind.AIM -> {
                                    aimAt(toBoard(first.position))
                                    drag(first.id) { change ->
                                        change.consume()
                                        aimAt(toBoard(change.position))
                                    }
                                }
                                ShotGesture.Kind.SLIDE -> {
                                    slideTo(toBoard(first.position))
                                    // Slide until the finger lifts; if it drifts back behind the
                                    // baseline part-way, carry on as a pull from there.
                                    while (true) {
                                        val change = awaitDragOrCancellation(first.id) ?: break
                                        if (change.changedToUp()) break
                                        change.consume()
                                        val p = toBoard(change.position)
                                        val now = currentStriker ?: break
                                        if (ShotGesture.escalatesToPull(p.x, p.y, now.x, now.y, seat)) {
                                            pullFrom(change.id, p)
                                            break
                                        }
                                        slideTo(p)
                                    }
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

                if (widePocketRoll) drawWidePockets(pocketScale * Powers.WIDE_POCKET_DICE_SCALE, pulse.value)

                if (showGuide) {
                    drawAimGuide(aimPreview, phase.value, pulse.value, subdued = !canAim)
                }

                // Discs dropping into pockets sit beneath the discs still rolling.
                for (i in pieces.indices) {
                    val p = pieces[i]
                    if (p.isPocketed && p.pocketProgress > 0f) drawSinking(p) { x, y, scale, alpha ->
                        drawCarromMan(coinArt.forType(p.type), x, y, scale, alpha, shadowAlpha = alpha)
                    }
                }
                for (i in pieces.indices) {
                    val p = pieces[i]
                    if (!p.isPocketed) drawCarromMan(coinArt.forType(p.type), p.x, p.y)
                }

                if (striker != null) {
                    drawStrikerLayer(striker, strikerArt, effects, halo = canAim && pullPoint == null, pulse = pulse.value)
                    val pull = pullPoint
                    if (pull != null && !striker.isPocketed) {
                        val fraction = (gameState.strikerPower - BoardGeometry.MIN_POWER) /
                            (BoardGeometry.MAX_POWER - BoardGeometry.MIN_POWER)
                        drawPullBand(striker.x, striker.y, striker.radius, pull, fraction, armed = pullArmed)
                    }
                }
            }
        }
    }
}

/** Glowing rings showing how wide the pockets play after a Wide Pockets roll. */
private fun DrawScope.drawWidePockets(scale: Float, pulse: Float) {
    for (pocket in BoardGeometry.POCKETS) {
        val c = Offset(pocket.x, pocket.y)
        val r = pocket.radius * scale
        drawCircle(CarromPalette.Jade, r, c, alpha = 0.16f)
        drawCircle(CarromPalette.Jade, r + 2f + pulse * 4f, c, alpha = 0.7f * (1f - pulse), style = wideRingStroke)
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

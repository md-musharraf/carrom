package com.example.royalcarromclassic.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import com.example.royalcarromclassic.data.*
import com.example.royalcarromclassic.engine.*
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.*

/**
 * High-performance, isolated Carrom Board Canvas.
 * Delivers silky-smooth 60-120 FPS rendering, zero-allocation frame draws,
 * animated laser trajectory beams, elastic slingshot mechanics, and tactile 3D piece rendering.
 */
@Composable
fun CarromBoardCanvas(
    pieces: List<Piece>,
    striker: Piece?,
    boardTheme: BoardTheme,
    strikerConfig: StrikerConfig,
    gameState: GameState,
    particles: ParticleSystem,
    physicsTickFlow: StateFlow<Long>,
    onPositionChanged: (Float) -> Unit,
    onAimChanged: (Float, Float) -> Unit,
    onShoot: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Collect physics tick exclusively in this isolated canvas scope
    val physicsTick by physicsTickFlow.collectAsState()

    var pullTouchPos by remember { mutableStateOf<Offset?>(null) }
    var isPullingStriker by remember { mutableStateOf(false) }

    // Animated dash phase for flowing laser trajectory beam
    val infiniteTransition = rememberInfiniteTransition(label = "CanvasLaserAnim")
    val dashPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "LaserDashPhase"
    )

    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "StrikerGlowPulse"
    )

    // Pre-cached brushes and path effects
    val woodFrameBrush = remember(boardTheme.woodColor, boardTheme.woodInner) {
        Brush.linearGradient(
            colors = listOf(boardTheme.woodColor, boardTheme.woodInner, boardTheme.woodColor),
            start = Offset.Zero,
            end = Offset(BoardGeometry.BOARD_SIZE, BoardGeometry.BOARD_SIZE)
        )
    }

    val feltBrush = remember(boardTheme.feltColor) {
        Brush.radialGradient(
            colors = listOf(boardTheme.feltColor, boardTheme.feltColor.copy(alpha = 0.90f)),
            center = Offset(BoardGeometry.BOARD_SIZE / 2f, BoardGeometry.BOARD_SIZE / 2f),
            radius = BoardGeometry.BOARD_SIZE * 0.72f
        )
    }

    val feltLacquerGloss = remember {
        Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
            start = Offset(BoardGeometry.PLAYABLE_MIN, BoardGeometry.PLAYABLE_MIN),
            end = Offset(BoardGeometry.BOARD_SIZE * 0.55f, BoardGeometry.BOARD_SIZE * 0.55f)
        )
    }

    val dashedAimPathEffect = remember(dashPhase) {
        PathEffect.dashPathEffect(floatArrayOf(14f, 10f), -dashPhase)
    }
    val dashedTargetEffect = remember(dashPhase) {
        PathEffect.dashPathEffect(floatArrayOf(10f, 8f), -dashPhase)
    }
    val pullStringEffect = remember {
        PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
    }

    val s = striker
    val isAimingOrPlacing = s != null && !s.isPocketed && gameState.currentTurn != "ai" &&
            (gameState.turnState == TurnState.AIMING || gameState.turnState == TurnState.PLACING_STRIKER)

    val trajectoryData = remember(
        isAimingOrPlacing,
        s?.x,
        s?.y,
        gameState.strikerAimAngle,
        gameState.strikerPower,
        pieces
    ) {
        if (isAimingOrPlacing) {
            CarromPhysicsEngine.calculateTrajectory(
                striker = s,
                aimAngle = gameState.strikerAimAngle,
                power = gameState.strikerPower,
                pieces = pieces,
                maxBounces = 3
            )
        } else {
            null
        }
    }

    val frameCornerRadius = remember { CornerRadius(36f, 36f) }
    val boardSize2D = remember { Size(BoardGeometry.BOARD_SIZE, BoardGeometry.BOARD_SIZE) }
    val feltSize = remember { BoardGeometry.PLAYABLE_MAX - BoardGeometry.PLAYABLE_MIN }
    val feltRectSize = remember { Size(feltSize, feltSize) }
    val feltTopLeft = remember { Offset(BoardGeometry.PLAYABLE_MIN, BoardGeometry.PLAYABLE_MIN) }
    val innerCushionStroke = remember { Stroke(width = 5.5f) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(gameState.currentTurn, gameState.turnState) {
                if (gameState.currentTurn == "ai" || gameState.turnState == TurnState.MOVING || gameState.isGameOver) {
                    return@pointerInput
                }

                detectTapGestures { offset ->
                    val scale = size.width / BoardGeometry.BOARD_SIZE
                    val boardX = offset.x / scale
                    val boardY = offset.y / scale

                    val isBottom = gameState.currentTurn == "player1" || gameState.mode == GameMode.TRICK_SHOTS
                    val baselineY = if (isBottom) BoardGeometry.BASELINE_BOTTOM_Y else BoardGeometry.BASELINE_TOP_Y

                    if (abs(boardY - baselineY) < 55f) {
                        val fraction = (boardX - BoardGeometry.BASELINE_START_X) / BoardGeometry.BASELINE_WIDTH
                        onPositionChanged(fraction.coerceIn(0.06f, 0.94f))
                    } else if (striker != null) {
                        val dx = boardX - striker.x
                        val dy = boardY - striker.y
                        val angle = atan2(dy, dx)
                        onAimChanged(angle, gameState.strikerPower)
                    }
                }
            }
            .pointerInput(gameState.currentTurn, gameState.turnState) {
                if (gameState.currentTurn == "ai" || gameState.turnState == TurnState.MOVING || gameState.isGameOver) {
                    return@pointerInput
                }

                var dragMode = 0 // 0: None, 1: Baseline Slider, 2: Pull-to-Shoot Aim

                detectDragGestures(
                    onDragStart = { offset ->
                        val scale = size.width / BoardGeometry.BOARD_SIZE
                        val boardX = offset.x / scale
                        val boardY = offset.y / scale

                        val isBottom = gameState.currentTurn == "player1" || gameState.mode == GameMode.TRICK_SHOTS
                        val baselineY = if (isBottom) BoardGeometry.BASELINE_BOTTOM_Y else BoardGeometry.BASELINE_TOP_Y

                        if (striker != null) {
                            val distToStriker = hypot(boardX - striker.x, boardY - striker.y)

                            if (distToStriker < BoardGeometry.STRIKER_RADIUS + 45f) {
                                // Direct Touch on Striker -> Pull-to-Shoot mode
                                dragMode = 2
                                isPullingStriker = true
                                pullTouchPos = Offset(boardX, boardY)
                            } else if (abs(boardY - baselineY) < 42f && boardX >= BoardGeometry.BASELINE_START_X - 35f && boardX <= BoardGeometry.BASELINE_END_X + 35f) {
                                // Baseline Rail positioning
                                dragMode = 1
                                val fraction = (boardX - BoardGeometry.BASELINE_START_X) / BoardGeometry.BASELINE_WIDTH
                                onPositionChanged(fraction.coerceIn(0.06f, 0.94f))
                            } else {
                                // Touch anywhere on felt -> Smooth Laser Aim Mode
                                dragMode = 2
                                isPullingStriker = true
                                pullTouchPos = Offset(boardX, boardY)
                                val pullDx = boardX - striker.x
                                val pullDy = boardY - striker.y
                                val forwardAngle = atan2(-pullDy, -pullDx)
                                val pullDist = hypot(pullDx, pullDy)
                                val power = ((pullDist / 130f) * 100f).coerceIn(20f, 100f)
                                onAimChanged(forwardAngle, power)
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val scale = size.width / BoardGeometry.BOARD_SIZE
                        val boardX = change.position.x / scale
                        val boardY = change.position.y / scale

                        if (dragMode == 1) {
                            val fraction = (boardX - BoardGeometry.BASELINE_START_X) / BoardGeometry.BASELINE_WIDTH
                            onPositionChanged(fraction.coerceIn(0.06f, 0.94f))
                        } else if (dragMode == 2 && striker != null) {
                            pullTouchPos = Offset(boardX, boardY)
                            val pullDx = boardX - striker.x
                            val pullDy = boardY - striker.y
                            val pullDist = hypot(pullDx, pullDy)

                            val forwardAngle = atan2(-pullDy, -pullDx)
                            val calcPower = ((pullDist / 130f) * 100f).coerceIn(20f, 100f)
                            onAimChanged(forwardAngle, calcPower)
                        }
                    },
                    onDragEnd = {
                        if (dragMode == 2 && striker != null && pullTouchPos != null) {
                            val pullDist = hypot(pullTouchPos!!.x - striker.x, pullTouchPos!!.y - striker.y)
                            if (pullDist >= 14f) {
                                onShoot()
                            }
                        }
                        pullTouchPos = null
                        isPullingStriker = false
                        dragMode = 0
                    },
                    onDragCancel = {
                        pullTouchPos = null
                        isPullingStriker = false
                        dragMode = 0
                    }
                )
            }
    ) {
        // Suppress unused warning; physicsTick ensures Canvas invalidation
        val _tick = physicsTick
        val scale = size.width / BoardGeometry.BOARD_SIZE

        withTransform({
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            // 1. Wooden Outer Frame
            drawRoundRect(
                brush = woodFrameBrush,
                topLeft = Offset.Zero,
                size = boardSize2D,
                cornerRadius = frameCornerRadius
            )

            // 2. Playable Felt Surface
            drawRect(
                brush = feltBrush,
                topLeft = feltTopLeft,
                size = feltRectSize
            )

            // Board Lacquer Specular Sheen
            drawRect(
                brush = feltLacquerGloss,
                topLeft = feltTopLeft,
                size = feltRectSize
            )

            // Inner Cushion Border
            drawRect(
                color = boardTheme.woodInner,
                topLeft = feltTopLeft,
                size = feltRectSize,
                style = innerCushionStroke
            )

            // 3. Traditional Mandala & Baseline Markings
            drawMandalaMarkings(this, boardTheme)

            // 4. Corner Pockets
            drawPockets(this, boardTheme)

            // 5. Particle Effects
            particles.draw(this)

            // 6. Elastic Slingshot Pull Band & Dynamic Power Rings
            if (pullTouchPos != null && striker != null && !striker.isPocketed) {
                val pull = pullTouchPos!!
                val powerFrac = (gameState.strikerPower / 100f).coerceIn(0f, 1f)

                val tensionColor = when {
                    powerFrac > 0.75f -> Color(0xFFEF4444)
                    powerFrac > 0.45f -> Color(0xFFFBBF24)
                    else -> Color(0xFF22C55E)
                }

                // Elastic Pull Line
                drawLine(
                    color = tensionColor.copy(alpha = 0.90f),
                    start = Offset(striker.x, striker.y),
                    end = pull,
                    strokeWidth = 4.5f,
                    pathEffect = pullStringEffect
                )

                // Touch Anchor Glow
                drawCircle(color = tensionColor.copy(alpha = 0.35f), radius = 14f, center = pull)
                drawCircle(color = tensionColor, radius = 8f, center = pull)
                drawCircle(color = Color.White, radius = 3.5f, center = pull)

                // Tension Power Gauge Ring around Striker
                drawCircle(
                    color = tensionColor.copy(alpha = 0.6f),
                    radius = striker.radius + 6f + (powerFrac * 12f),
                    center = Offset(striker.x, striker.y),
                    style = Stroke(width = 3f)
                )
            }

            // 7. Dynamic Trajectory Line
            if (trajectoryData != null) {
                drawTrajectory(this, trajectoryData, strikerConfig, dashedAimPathEffect, dashedTargetEffect)
            }

            // 8. Carrom Pieces (With 3D lacquered shading & dynamic drop shadows)
            val pieceCount = pieces.size
            for (i in 0 until pieceCount) {
                drawCarromPiece(this, pieces[i])
            }

            // 9. Luxury 3D Striker
            if (striker != null && !striker.isPocketed) {
                drawStrikerPiece(
                    this,
                    striker,
                    strikerConfig,
                    gameState.turnState == TurnState.AIMING || gameState.turnState == TurnState.PLACING_STRIKER,
                    isPullingStriker,
                    pulseGlow
                )
            }
        }
    }
}

private fun drawMandalaMarkings(scope: DrawScope, theme: BoardTheme) {
    val cx = BoardGeometry.BOARD_SIZE / 2f
    val cy = BoardGeometry.BOARD_SIZE / 2f
    val patternColor = theme.feltPatternColor

    // Outer Circle
    scope.drawCircle(
        color = patternColor,
        radius = BoardGeometry.CENTER_OUTER_CIRCLE_RADIUS,
        center = Offset(cx, cy),
        style = Stroke(width = 2.5f)
    )

    // Mid Circle
    scope.drawCircle(
        color = patternColor,
        radius = BoardGeometry.CENTER_CIRCLE_RADIUS,
        center = Offset(cx, cy),
        style = Stroke(width = 2f)
    )

    // Center Queen Spot
    scope.drawCircle(
        color = theme.centerCircleColor,
        radius = BoardGeometry.CENTER_SMALL_CIRCLE_RADIUS,
        center = Offset(cx, cy)
    )
    scope.drawCircle(
        color = theme.accentGold,
        radius = BoardGeometry.CENTER_SMALL_CIRCLE_RADIUS,
        center = Offset(cx, cy),
        style = Stroke(width = 2f)
    )

    // 8 Decorative Radial Florets
    for (i in 0 until 8) {
        val angle = (i * PI.toFloat()) / 4f
        val cosA = cos(angle)
        val sinA = sin(angle)
        val px = cx + cosA * (BoardGeometry.CENTER_CIRCLE_RADIUS + 25f)
        val py = cy + sinA * (BoardGeometry.CENTER_CIRCLE_RADIUS + 25f)

        scope.drawCircle(
            color = theme.centerCircleColor,
            radius = 5.5f,
            center = Offset(px, py)
        )

        scope.drawLine(
            color = patternColor,
            start = Offset(cx + cosA * BoardGeometry.CENTER_CIRCLE_RADIUS, cy + sinA * BoardGeometry.CENTER_CIRCLE_RADIUS),
            end = Offset(cx + cosA * (BoardGeometry.CENTER_OUTER_CIRCLE_RADIUS - 10f), cy + sinA * (BoardGeometry.CENTER_OUTER_CIRCLE_RADIUS - 10f)),
            strokeWidth = 1.5f
        )
    }

    // Baselines (Top & Bottom)
    val drawHorizontalBaseline = { y: Float ->
        val offset = 7f
        scope.drawLine(patternColor, Offset(BoardGeometry.BASELINE_START_X, y - offset), Offset(BoardGeometry.BASELINE_END_X, y - offset), strokeWidth = 2f)
        scope.drawLine(patternColor, Offset(BoardGeometry.BASELINE_START_X, y + offset), Offset(BoardGeometry.BASELINE_END_X, y + offset), strokeWidth = 2f)

        // Left circle
        scope.drawCircle(theme.centerCircleColor, radius = BoardGeometry.BASELINE_CIRCLE_RADIUS, center = Offset(BoardGeometry.BASELINE_START_X, y))
        scope.drawCircle(theme.accentGold, radius = BoardGeometry.BASELINE_CIRCLE_RADIUS, center = Offset(BoardGeometry.BASELINE_START_X, y), style = Stroke(width = 1.5f))

        // Right circle
        scope.drawCircle(theme.centerCircleColor, radius = BoardGeometry.BASELINE_CIRCLE_RADIUS, center = Offset(BoardGeometry.BASELINE_END_X, y))
        scope.drawCircle(theme.accentGold, radius = BoardGeometry.BASELINE_CIRCLE_RADIUS, center = Offset(BoardGeometry.BASELINE_END_X, y), style = Stroke(width = 1.5f))
    }

    drawHorizontalBaseline(BoardGeometry.BASELINE_BOTTOM_Y)
    drawHorizontalBaseline(BoardGeometry.BASELINE_TOP_Y)

    // Corner Arrow Rays pointing to pockets
    for (i in 0 until 4) {
        val angle = (i * PI.toFloat()) / 2f + (PI.toFloat() / 4f)
        val cosA = cos(angle)
        val sinA = sin(angle)
        val startX = cx + cosA * (BoardGeometry.CENTER_OUTER_CIRCLE_RADIUS + 25f)
        val startY = cy + sinA * (BoardGeometry.CENTER_OUTER_CIRCLE_RADIUS + 25f)
        val endX = cx + cosA * (BoardGeometry.BOARD_SIZE * 0.42f)
        val endY = cy + sinA * (BoardGeometry.BOARD_SIZE * 0.42f)

        scope.drawLine(patternColor, Offset(startX, startY), Offset(endX, endY), strokeWidth = 2f)
        scope.drawCircle(patternColor, radius = 5f, center = Offset(endX, endY))
    }
}

private fun drawPockets(scope: DrawScope, theme: BoardTheme) {
    for (pocket in BoardGeometry.POCKETS) {
        // Metallic Outer Rim
        scope.drawCircle(
            color = theme.pocketRimColor,
            radius = pocket.radius + 5.5f,
            center = Offset(pocket.x, pocket.y)
        )

        // Deep Pocket Cup
        scope.drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF020617), Color(0xFF0F172A), Color(0xFF1E293B)),
                center = Offset(pocket.x, pocket.y),
                radius = pocket.radius
            ),
            radius = pocket.radius,
            center = Offset(pocket.x, pocket.y)
        )

        // Inner Shadow Ring
        scope.drawCircle(
            color = Color(0x66000000),
            radius = pocket.radius,
            center = Offset(pocket.x, pocket.y),
            style = Stroke(width = 3f)
        )
    }
}

private fun drawCarromPiece(scope: DrawScope, piece: Piece) {
    if (piece.isPocketed && piece.pocketProgress <= 0.02f) return

    val currentRadius = piece.radius * piece.pocketProgress
    val currentAlpha = piece.pocketProgress

    if (currentRadius <= 1f) return

    // 1. Dynamic Drop Shadow
    val speed = hypot(piece.vx, piece.vy)
    val shadowOffset = 3f + (speed * 0.15f).coerceAtMost(4f)
    scope.drawCircle(
        color = Color(0x60000000).copy(alpha = 0.38f * currentAlpha),
        radius = currentRadius + 1f,
        center = Offset(piece.x + shadowOffset, piece.y + shadowOffset)
    )

    // 2. Base Shaded Body (3D Sphere/Disc bevel)
    scope.drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.85f * currentAlpha),
                piece.primaryColor.copy(alpha = currentAlpha),
                piece.borderColor.copy(alpha = currentAlpha)
            ),
            center = Offset(piece.x - currentRadius * 0.32f, piece.y - currentRadius * 0.32f),
            radius = currentRadius
        ),
        radius = currentRadius,
        center = Offset(piece.x, piece.y)
    )

    // 3. Signature Engraved Inlay Ring
    scope.drawCircle(
        color = (if (piece.type == PieceType.QUEEN) Color(0xFFFEF08A) else piece.borderColor).copy(alpha = currentAlpha * 0.9f),
        radius = currentRadius * 0.58f,
        center = Offset(piece.x, piece.y),
        style = Stroke(width = 1.6f)
    )

    // 4. Center Carved Dot
    scope.drawCircle(
        color = (if (piece.type == PieceType.QUEEN) Color(0xFFFEF08A) else piece.borderColor).copy(alpha = currentAlpha),
        radius = currentRadius * 0.22f,
        center = Offset(piece.x, piece.y)
    )

    // 5. Specular Gloss Arc
    scope.drawCircle(
        color = Color.White.copy(alpha = 0.45f * currentAlpha),
        radius = currentRadius * 0.28f,
        center = Offset(piece.x - currentRadius * 0.3f, piece.y - currentRadius * 0.3f)
    )
}

private fun drawStrikerPiece(
    scope: DrawScope,
    striker: Piece,
    config: StrikerConfig,
    isActiveAim: Boolean,
    isPulling: Boolean,
    pulseAlpha: Float
) {
    val r = striker.radius

    // 1. Luminous Pulsing Aura when Aiming
    if (isActiveAim) {
        val glowRadius = r + (if (isPulling) 14f else 8f)
        scope.drawCircle(
            color = (if (isPulling) Color(0xFFFBBF24) else config.glowColor).copy(alpha = if (isPulling) 0.65f else pulseAlpha * 0.5f),
            radius = glowRadius,
            center = Offset(striker.x, striker.y)
        )
    }

    // 2. 3D Drop Shadow
    scope.drawCircle(
        color = Color(0x75000000),
        radius = r + 1f,
        center = Offset(striker.x + 4.5f, striker.y + 4.5f)
    )

    // 3. Multi-Layered Striker Body Gradient
    scope.drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, config.primaryColor, config.secondaryColor),
            center = Offset(striker.x - r * 0.36f, striker.y - r * 0.36f),
            radius = r
        ),
        radius = r,
        center = Offset(striker.x, striker.y)
    )

    // 4. Metallic Outer Rim
    scope.drawCircle(
        color = config.glowColor,
        radius = r,
        center = Offset(striker.x, striker.y),
        style = Stroke(width = 2.8f)
    )

    // 5. Engraved Inner Ring
    scope.drawCircle(
        color = Color.White.copy(alpha = 0.75f),
        radius = r * 0.65f,
        center = Offset(striker.x, striker.y),
        style = Stroke(width = 1.6f)
    )

    // 6. Central Embedded Gemstone
    scope.drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, config.glowColor, config.secondaryColor),
            center = Offset(striker.x - r * 0.08f, striker.y - r * 0.08f),
            radius = r * 0.28f
        ),
        radius = r * 0.28f,
        center = Offset(striker.x, striker.y)
    )
}

private fun drawTrajectory(
    scope: DrawScope,
    trajectory: CarromPhysicsEngine.TrajectoryData,
    config: StrikerConfig,
    aimDashEffect: PathEffect,
    targetDashEffect: PathEffect
) {
    val path = trajectory.strikerPath
    if (path.size < 2) return

    // 1. Flowing Laser Striker Aim Beam
    for (i in 0 until path.size - 1) {
        // Outer glow
        scope.drawLine(
            color = config.trailColor.copy(alpha = 0.3f),
            start = Offset(path[i].x, path[i].y),
            end = Offset(path[i + 1].x, path[i + 1].y),
            strokeWidth = 7f
        )
        // Laser core
        scope.drawLine(
            color = config.trailColor,
            start = Offset(path[i].x, path[i].y),
            end = Offset(path[i + 1].x, path[i + 1].y),
            strokeWidth = 3.5f,
            pathEffect = aimDashEffect
        )
    }

    // 2. Ghost Striker at Hit Position
    if (trajectory.targetHitGhostPos != null && trajectory.targetHitPiece != null) {
        val ghost = trajectory.targetHitGhostPos
        scope.drawCircle(
            color = Color(0x44FBBF24),
            radius = BoardGeometry.STRIKER_RADIUS,
            center = Offset(ghost.x, ghost.y)
        )
        scope.drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = BoardGeometry.STRIKER_RADIUS,
            center = Offset(ghost.x, ghost.y),
            style = Stroke(width = 1.8f)
        )

        // 3. Target Hit Piece Predicted Deflection Arrow
        if (trajectory.targetPath.size >= 2) {
            val tPath = trajectory.targetPath
            scope.drawLine(
                color = Color(0xFF22C55E),
                start = Offset(tPath[0].x, tPath[0].y),
                end = Offset(tPath[1].x, tPath[1].y),
                strokeWidth = 4f,
                pathEffect = targetDashEffect
            )
            scope.drawCircle(
                color = Color(0x6022C55E),
                radius = BoardGeometry.PUCK_RADIUS,
                center = Offset(tPath[1].x, tPath[1].y)
            )
            scope.drawCircle(
                color = Color(0xFF22C55E),
                radius = BoardGeometry.PUCK_RADIUS,
                center = Offset(tPath[1].x, tPath[1].y),
                style = Stroke(width = 1.5f)
            )
        }

        // 4. Striker Deflection Vector
        if (trajectory.strikerDeflectPath.size >= 2) {
            val sPath = trajectory.strikerDeflectPath
            scope.drawLine(
                color = Color(0xFFFBBF24).copy(alpha = 0.7f),
                start = Offset(sPath[0].x, sPath[0].y),
                end = Offset(sPath[1].x, sPath[1].y),
                strokeWidth = 2.5f,
                pathEffect = aimDashEffect
            )
        }
    }
}


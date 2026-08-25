package com.example.royalcarromclassic.ui

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
import kotlin.math.*

@Composable
fun CarromBoardCanvas(
    pieces: List<Piece>,
    striker: Piece?,
    boardTheme: BoardTheme,
    strikerConfig: StrikerConfig,
    gameState: GameState,
    particles: ParticleSystem,
    physicsTick: Long,
    onPositionChanged: (Float) -> Unit,
    onAimChanged: (Float, Float) -> Unit,
    onShoot: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pullTouchPos by remember { mutableStateOf<Offset?>(null) }
    var isPullingStriker by remember { mutableStateOf(false) }

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
                        onPositionChanged(fraction.coerceIn(0f, 1f))
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

                var dragMode = 0 // 0: None, 1: Baseline Slider Drag, 2: Pull & Release Aim

                detectDragGestures(
                    onDragStart = { offset ->
                        val scale = size.width / BoardGeometry.BOARD_SIZE
                        val boardX = offset.x / scale
                        val boardY = offset.y / scale

                        val isBottom = gameState.currentTurn == "player1" || gameState.mode == GameMode.TRICK_SHOTS
                        val baselineY = if (isBottom) BoardGeometry.BASELINE_BOTTOM_Y else BoardGeometry.BASELINE_TOP_Y

                        if (striker != null) {
                            val distToStriker = hypot(boardX - striker.x, boardY - striker.y)

                            if (distToStriker < BoardGeometry.STRIKER_RADIUS + 40f) {
                                // Direct Touch on Striker -> Enter Pull-to-Shoot mode!
                                dragMode = 2
                                isPullingStriker = true
                                pullTouchPos = Offset(boardX, boardY)
                            } else if (abs(boardY - baselineY) < 40f && boardX >= BoardGeometry.BASELINE_START_X - 30f && boardX <= BoardGeometry.BASELINE_END_X + 30f) {
                                // Touch along baseline rail -> Baseline Positioning
                                dragMode = 1
                                val fraction = (boardX - BoardGeometry.BASELINE_START_X) / BoardGeometry.BASELINE_WIDTH
                                onPositionChanged(fraction.coerceIn(0f, 1f))
                            } else {
                                // Touch on board -> Aim & Pull Mode
                                dragMode = 2
                                isPullingStriker = true
                                pullTouchPos = Offset(boardX, boardY)
                                val pullDx = boardX - striker.x
                                val pullDy = boardY - striker.y
                                val forwardAngle = atan2(-pullDy, -pullDx)
                                val pullDist = hypot(pullDx, pullDy)
                                val power = ((pullDist / 140f) * 100f).coerceIn(20f, 100f)
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
                            // Real-time baseline positioning
                            val fraction = (boardX - BoardGeometry.BASELINE_START_X) / BoardGeometry.BASELINE_WIDTH
                            onPositionChanged(fraction.coerceIn(0f, 1f))
                        } else if (dragMode == 2 && striker != null) {
                            // Real-time Slingshot Pull & Aim
                            pullTouchPos = Offset(boardX, boardY)
                            val pullDx = boardX - striker.x
                            val pullDy = boardY - striker.y
                            val pullDist = hypot(pullDx, pullDy)

                            // Pull backward fires forward
                            val forwardAngle = atan2(-pullDy, -pullDx)
                            val calcPower = ((pullDist / 140f) * 100f).coerceIn(20f, 100f)
                            onAimChanged(forwardAngle, calcPower)
                        }
                    },
                    onDragEnd = {
                        if (dragMode == 2 && striker != null && pullTouchPos != null) {
                            val pullDist = hypot(pullTouchPos!!.x - striker.x, pullTouchPos!!.y - striker.y)
                            // Pull distance must be at least 16 pixels to register as a deliberate shot
                            if (pullDist >= 16f) {
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
        val tick = physicsTick
        val scale = size.width / BoardGeometry.BOARD_SIZE

        withTransform({
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            // 1. Draw Wooden Frame
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(boardTheme.woodColor, boardTheme.woodInner, boardTheme.woodColor),
                    start = Offset.Zero,
                    end = Offset(BoardGeometry.BOARD_SIZE, BoardGeometry.BOARD_SIZE)
                ),
                topLeft = Offset.Zero,
                size = Size(BoardGeometry.BOARD_SIZE, BoardGeometry.BOARD_SIZE),
                cornerRadius = CornerRadius(36f, 36f)
            )

            // 2. Draw Felt Surface
            val feltSize = BoardGeometry.PLAYABLE_MAX - BoardGeometry.PLAYABLE_MIN
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(boardTheme.feltColor, boardTheme.feltColor.copy(alpha = 0.85f)),
                    center = Offset(BoardGeometry.BOARD_SIZE / 2f, BoardGeometry.BOARD_SIZE / 2f),
                    radius = BoardGeometry.BOARD_SIZE * 0.7f
                ),
                topLeft = Offset(BoardGeometry.PLAYABLE_MIN, BoardGeometry.PLAYABLE_MIN),
                size = Size(feltSize, feltSize)
            )

            // Inner cushion border
            drawRect(
                color = boardTheme.woodInner,
                topLeft = Offset(BoardGeometry.PLAYABLE_MIN, BoardGeometry.PLAYABLE_MIN),
                size = Size(feltSize, feltSize),
                style = Stroke(width = 5f)
            )

            // 3. Draw Traditional Mandalas & Markings
            drawMandalaMarkings(this, boardTheme)

            // 4. Draw 4 Corner Pockets
            drawPockets(this, boardTheme)

            // 5. Draw Particle effects
            particles.draw(this)

            // 6. Draw Elastic Slingshot Pull Band & Power Gauge (When pulling back)
            if (pullTouchPos != null && striker != null && !striker.isPocketed) {
                val pull = pullTouchPos!!
                val pullDist = hypot(pull.x - striker.x, pull.y - striker.y)
                val powerFrac = (gameState.strikerPower / 100f).coerceIn(0f, 1f)

                val tensionColor = when {
                    powerFrac > 0.75f -> Color(0xFFEF4444)
                    powerFrac > 0.45f -> Color(0xFFFBBF24)
                    else -> Color(0xFF22C55E)
                }

                // Elastic Pull String
                drawLine(
                    color = tensionColor.copy(alpha = 0.85f),
                    start = Offset(striker.x, striker.y),
                    end = pull,
                    strokeWidth = 4.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f)
                )

                // Touch Pull Anchor
                drawCircle(
                    color = tensionColor,
                    radius = 9f,
                    center = pull
                )
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = pull
                )

                // Tension Power Ring around Striker
                drawCircle(
                    color = tensionColor.copy(alpha = 0.5f),
                    radius = striker.radius + 6f + (powerFrac * 10f),
                    center = Offset(striker.x, striker.y),
                    style = Stroke(width = 2.5f)
                )
            }

            // 7. Draw Trajectory Line (if aiming or ready to shoot)
            if (striker != null && !striker.isPocketed && gameState.currentTurn != "ai" &&
                (gameState.turnState == TurnState.AIMING || gameState.turnState == TurnState.PLACING_STRIKER)
            ) {
                val trajectory = CarromPhysicsEngine.calculateTrajectory(
                    striker = striker,
                    aimAngle = gameState.strikerAimAngle,
                    power = gameState.strikerPower,
                    pieces = pieces
                )
                drawTrajectory(this, trajectory, strikerConfig)
            }

            // 8. Draw Carrom Pieces
            for (p in pieces) {
                drawCarromPiece(this, p)
            }

            // 9. Draw Striker
            if (striker != null && !striker.isPocketed) {
                drawStrikerPiece(
                    this,
                    striker,
                    strikerConfig,
                    gameState.turnState == TurnState.AIMING || gameState.turnState == TurnState.PLACING_STRIKER,
                    isPullingStriker
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

    // Center Queen Red Spot
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
        val px = cx + cos(angle) * (BoardGeometry.CENTER_CIRCLE_RADIUS + 25f)
        val py = cy + sin(angle) * (BoardGeometry.CENTER_CIRCLE_RADIUS + 25f)

        scope.drawCircle(
            color = theme.centerCircleColor,
            radius = 5.5f,
            center = Offset(px, py)
        )

        scope.drawLine(
            color = patternColor,
            start = Offset(cx + cos(angle) * BoardGeometry.CENTER_CIRCLE_RADIUS, cy + sin(angle) * BoardGeometry.CENTER_CIRCLE_RADIUS),
            end = Offset(cx + cos(angle) * (BoardGeometry.CENTER_OUTER_CIRCLE_RADIUS - 10f), cy + sin(angle) * (BoardGeometry.CENTER_OUTER_CIRCLE_RADIUS - 10f)),
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
        val startX = cx + cos(angle) * (BoardGeometry.CENTER_OUTER_CIRCLE_RADIUS + 25f)
        val startY = cy + sin(angle) * (BoardGeometry.CENTER_OUTER_CIRCLE_RADIUS + 25f)
        val endX = cx + cos(angle) * (BoardGeometry.BOARD_SIZE * 0.42f)
        val endY = cy + sin(angle) * (BoardGeometry.BOARD_SIZE * 0.42f)

        scope.drawLine(patternColor, Offset(startX, startY), Offset(endX, endY), strokeWidth = 2f)
        scope.drawCircle(patternColor, radius = 5f, center = Offset(endX, endY))
    }
}

private fun drawPockets(scope: DrawScope, theme: BoardTheme) {
    for (pocket in BoardGeometry.POCKETS) {
        // Metallic Brass Rim
        scope.drawCircle(
            color = theme.pocketRimColor,
            radius = pocket.radius + 5f,
            center = Offset(pocket.x, pocket.y)
        )

        // Deep Pocket Cup
        scope.drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF000000), Color(0xFF1E293B)),
                center = Offset(pocket.x, pocket.y),
                radius = pocket.radius
            ),
            radius = pocket.radius,
            center = Offset(pocket.x, pocket.y)
        )
    }
}

private fun drawCarromPiece(scope: DrawScope, piece: Piece) {
    if (piece.isPocketed && piece.pocketProgress <= 0.02f) return

    val currentRadius = piece.radius * piece.pocketProgress
    val currentAlpha = piece.pocketProgress

    if (currentRadius <= 1f) return

    // 3D Shadow
    scope.drawCircle(
        color = Color(0x60000000),
        radius = currentRadius,
        center = Offset(piece.x + 3f, piece.y + 3f)
    )

    // Base Shaded Body
    scope.drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.8f * currentAlpha),
                piece.primaryColor.copy(alpha = currentAlpha),
                piece.borderColor.copy(alpha = currentAlpha)
            ),
            center = Offset(piece.x - currentRadius * 0.3f, piece.y - currentRadius * 0.3f),
            radius = currentRadius
        ),
        radius = currentRadius,
        center = Offset(piece.x, piece.y)
    )

    // Concentric Inlay Ring
    scope.drawCircle(
        color = (if (piece.type == PieceType.QUEEN) Color(0xFFFEF08A) else piece.borderColor).copy(alpha = currentAlpha),
        radius = currentRadius * 0.55f,
        center = Offset(piece.x, piece.y),
        style = Stroke(width = 1.5f)
    )

    // Center Dot
    scope.drawCircle(
        color = (if (piece.type == PieceType.QUEEN) Color(0xFFFEF08A) else piece.borderColor).copy(alpha = currentAlpha),
        radius = currentRadius * 0.2f,
        center = Offset(piece.x, piece.y)
    )
}

private fun drawStrikerPiece(
    scope: DrawScope,
    striker: Piece,
    config: StrikerConfig,
    isActiveAim: Boolean,
    isPulling: Boolean
) {
    val r = striker.radius

    // Halo Glow when aiming
    if (isActiveAim) {
        scope.drawCircle(
            color = (if (isPulling) Color(0xFFFBBF24) else config.glowColor).copy(alpha = if (isPulling) 0.6f else 0.35f),
            radius = r + (if (isPulling) 12f else 7f),
            center = Offset(striker.x, striker.y)
        )
    }

    // Shadow
    scope.drawCircle(
        color = Color(0x70000000),
        radius = r,
        center = Offset(striker.x + 4f, striker.y + 4f)
    )

    // Striker Body Gradient
    scope.drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, config.primaryColor, config.secondaryColor),
            center = Offset(striker.x - r * 0.35f, striker.y - r * 0.35f),
            radius = r
        ),
        radius = r,
        center = Offset(striker.x, striker.y)
    )

    // Metallic Outer Rim
    scope.drawCircle(
        color = config.glowColor,
        radius = r,
        center = Offset(striker.x, striker.y),
        style = Stroke(width = 2.5f)
    )

    // Engraved Inner Ring
    scope.drawCircle(
        color = Color.White.copy(alpha = 0.7f),
        radius = r * 0.65f,
        center = Offset(striker.x, striker.y),
        style = Stroke(width = 1.5f)
    )

    // Center Jewel
    scope.drawCircle(
        color = config.glowColor,
        radius = r * 0.25f,
        center = Offset(striker.x, striker.y)
    )
}

private fun drawTrajectory(
    scope: DrawScope,
    trajectory: CarromPhysicsEngine.TrajectoryData,
    config: StrikerConfig
) {
    val path = trajectory.strikerPath
    if (path.size < 2) return

    val strokeDash = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)

    // 1. Striker Aim Beam
    for (i in 0 until path.size - 1) {
        scope.drawLine(
            color = config.trailColor,
            start = Offset(path[i].x, path[i].y),
            end = Offset(path[i + 1].x, path[i + 1].y),
            strokeWidth = 4f,
            pathEffect = strokeDash
        )
    }

    // 2. Ghost Striker at Hit Position
    if (trajectory.targetHitGhostPos != null && trajectory.targetHitPiece != null) {
        val ghost = trajectory.targetHitGhostPos
        scope.drawCircle(
            color = Color(0x40FBBF24),
            radius = BoardGeometry.STRIKER_RADIUS,
            center = Offset(ghost.x, ghost.y)
        )
        scope.drawCircle(
            color = Color.White.copy(alpha = 0.8f),
            radius = BoardGeometry.STRIKER_RADIUS,
            center = Offset(ghost.x, ghost.y),
            style = Stroke(width = 1.5f)
        )

        // 3. Target Hit Piece Deflected Path
        if (trajectory.targetPath.size >= 2) {
            val tPath = trajectory.targetPath
            scope.drawLine(
                color = Color(0xFF22C55E),
                start = Offset(tPath[0].x, tPath[0].y),
                end = Offset(tPath[1].x, tPath[1].y),
                strokeWidth = 3.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            )
            scope.drawCircle(
                color = Color(0x5022C55E),
                radius = BoardGeometry.PUCK_RADIUS,
                center = Offset(tPath[1].x, tPath[1].y)
            )
        }
    }
}

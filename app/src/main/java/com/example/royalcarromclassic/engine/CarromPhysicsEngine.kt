package com.example.royalcarromclassic.engine

import androidx.compose.ui.graphics.Color
import com.example.royalcarromclassic.core.logging.PerformanceTracker
import com.example.royalcarromclassic.data.Piece
import com.example.royalcarromclassic.data.PieceType
import com.example.royalcarromclassic.data.Vector2D
import kotlin.math.*

object CarromPhysicsEngine {

    data class TrajectoryData(
        val strikerPath: List<Vector2D>,
        val targetHitPiece: Piece?,
        val targetHitGhostPos: Vector2D?,
        val targetPath: List<Vector2D>,
        val strikerDeflectPath: List<Vector2D>
    )

    data class PhysicsStepResult(
        val pocketedPieces: List<Piece>,
        val anyMoving: Boolean
    )

    // Ultra-smooth physical parameters
    const val SUB_STEPS = 10
    const val RESTITUTION_PUCK_PUCK = 0.94f
    const val RESTITUTION_STRIKER_PUCK = 0.92f
    const val RESTITUTION_WALL = 0.88f
    const val FRICTION_BASE = 0.991f // Silky carrom powder glide
    const val LINEAR_DRAG = 0.035f // Linear deceleration
    const val VELOCITY_EPSILON = 0.08f

    fun generateClassicCluster(): List<Piece> {
        val pieces = mutableListOf<Piece>()
        val cx = BoardGeometry.BOARD_SIZE / 2f
        val cy = BoardGeometry.BOARD_SIZE / 2f
        val r = BoardGeometry.PUCK_RADIUS
        val gap = 0.4f

        // 1. Center Queen
        pieces.add(
            Piece(
                id = "queen",
                type = PieceType.QUEEN,
                x = cx,
                y = cy,
                radius = r,
                mass = BoardGeometry.PUCK_MASS,
                primaryColor = Color(0xFFEF4444),
                borderColor = Color(0xFFB91C1C),
                points = 25
            )
        )

        // 2. Inner ring: 6 pieces (3 White, 3 Black alternating)
        val d1 = (2f * r) + gap
        for (i in 0 until 6) {
            val angle = (i * PI.toFloat()) / 3f
            val isWhite = i % 2 == 0
            val type = if (isWhite) PieceType.WHITE else PieceType.BLACK
            pieces.add(
                Piece(
                    id = "inner_${i}_${type.name.lowercase()}",
                    type = type,
                    x = cx + cos(angle) * d1,
                    y = cy + sin(angle) * d1,
                    radius = r,
                    mass = BoardGeometry.PUCK_MASS,
                    primaryColor = if (isWhite) Color(0xFFF8FAFC) else Color(0xFF1E293B),
                    borderColor = if (isWhite) Color(0xFF94A3B8) else Color(0xFF0F172A),
                    points = if (isWhite) 10 else 5
                )
            )
        }

        // 3. Outer ring: 12 pieces in hexagonal geometry (6 corners at 2*d1, 6 mid-edges at sqrt(3)*d1)
        val dCorner = 2f * d1
        val dEdge = sqrt(3.0).toFloat() * d1

        var whiteCount = 0
        var blackCount = 0

        for (i in 0 until 6) {
            // Corner piece (angle = i * 60 deg)
            val cornerAngle = (i * PI.toFloat()) / 3f
            val isCornerWhite = (i % 2 == 1)
            val cornerType = if (isCornerWhite) PieceType.WHITE else PieceType.BLACK
            if (cornerType == PieceType.WHITE) whiteCount++ else blackCount++

            pieces.add(
                Piece(
                    id = "outer_c_${i}_${cornerType.name.lowercase()}",
                    type = cornerType,
                    x = cx + cos(cornerAngle) * dCorner,
                    y = cy + sin(cornerAngle) * dCorner,
                    radius = r,
                    mass = BoardGeometry.PUCK_MASS,
                    primaryColor = if (isCornerWhite) Color(0xFFF8FAFC) else Color(0xFF1E293B),
                    borderColor = if (isCornerWhite) Color(0xFF94A3B8) else Color(0xFF0F172A),
                    points = if (isCornerWhite) 10 else 5
                )
            )

            // Mid-edge piece (angle = i * 60 deg + 30 deg)
            val edgeAngle = cornerAngle + (PI.toFloat() / 6f)
            val isEdgeWhite = (i % 2 == 0)
            val edgeType = if (isEdgeWhite) PieceType.WHITE else PieceType.BLACK
            if (edgeType == PieceType.WHITE) whiteCount++ else blackCount++

            pieces.add(
                Piece(
                    id = "outer_e_${i}_${edgeType.name.lowercase()}",
                    type = edgeType,
                    x = cx + cos(edgeAngle) * dEdge,
                    y = cy + sin(edgeAngle) * dEdge,
                    radius = r,
                    mass = BoardGeometry.PUCK_MASS,
                    primaryColor = if (isEdgeWhite) Color(0xFFF8FAFC) else Color(0xFF1E293B),
                    borderColor = if (isEdgeWhite) Color(0xFF94A3B8) else Color(0xFF0F172A),
                    points = if (isEdgeWhite) 10 else 5
                )
            )
        }

        return pieces
    }

    fun createStriker(fraction: Float = 0.5f, isBottom: Boolean = true): Piece {
        val pos = BoardGeometry.getBaselineStrikerPos(fraction, isBottom)
        return Piece(
            id = "striker",
            type = PieceType.STRIKER,
            x = pos.x,
            y = pos.y,
            radius = BoardGeometry.STRIKER_RADIUS,
            mass = BoardGeometry.STRIKER_MASS,
            primaryColor = Color(0xFFFBBF24),
            borderColor = Color(0xFFB45309),
            points = 0
        )
    }

    /**
     * Executes physics simulation with variable delta-time support.
     * Pre-computes exponential math per frame to ensure zero CPU stalls.
     */
    fun updatePhysics(
        pieces: List<Piece>,
        striker: Piece?,
        dtSeconds: Float = 0.016f,
        onClack: (intensity: Float) -> Unit = {},
        onWall: (intensity: Float) -> Unit = {},
        onPocket: () -> Unit = {}
    ): PhysicsStepResult {
        val startNanos = PerformanceTracker.recordPhysicsTickStart()

        val subStepCount = SUB_STEPS
        val subDt = (dtSeconds * 60f) / subStepCount.toFloat()
        // Precompute friction factor once per frame instead of inside inner loops
        val frictionMult = FRICTION_BASE.pow(subDt)
        val linearDecel = LINEAR_DRAG * subDt

        val activePieces = ArrayList<Piece>(pieces.size + 1)
        if (striker != null && !striker.isPocketed) {
            activePieces.add(striker)
        }
        val pieceCount = pieces.size
        for (i in 0 until pieceCount) {
            val p = pieces[i]
            if (!p.isPocketed) {
                activePieces.add(p)
            } else if (p.pocketProgress > 0f) {
                p.pocketProgress = max(0f, p.pocketProgress - (0.06f * subDt * subStepCount))
            }
        }

        val pocketedThisFrame = ArrayList<Piece>(4)
        var anyMoving = false
        val activeCount = activePieces.size

        for (step in 0 until subStepCount) {
            // 1. Position update, friction deceleration & pocket suction
            for (i in 0 until activeCount) {
                val p = activePieces[i]
                if (p.isPocketed) continue

                p.x += p.vx * subDt
                p.y += p.vy * subDt

                val speed = hypot(p.vx, p.vy)
                if (speed > VELOCITY_EPSILON) {
                    val decel = min(speed, linearDecel)
                    val invSpeed = 1f / speed
                    val normVx = p.vx * invSpeed
                    val normVy = p.vy * invSpeed

                    p.vx = (p.vx * frictionMult) - (normVx * decel)
                    p.vy = (p.vy * frictionMult) - (normVy * decel)
                    anyMoving = true
                } else {
                    p.vx = 0f
                    p.vy = 0f
                }

                // Pocket gravity well
                for (pocket in BoardGeometry.POCKETS) {
                    val dx = pocket.x - p.x
                    val dy = pocket.y - p.y
                    val dist = hypot(dx, dy)

                    if (dist < BoardGeometry.POCKET_SUCTION_RADIUS) {
                        val pull = 0.55f * (1f - dist / BoardGeometry.POCKET_SUCTION_RADIUS) * subDt
                        val normDist = if (dist > 0f) dist else 1f
                        p.vx += (dx / normDist) * pull
                        p.vy += (dy / normDist) * pull

                        val dropRadius = BoardGeometry.POCKET_RADIUS - (if (p.type == PieceType.STRIKER) 5f else 2f)
                        if (dist < dropRadius) {
                            p.isPocketed = true
                            p.vx = 0f
                            p.vy = 0f
                            p.pocketProgress = 1.0f
                            pocketedThisFrame.add(p)
                            onPocket()
                            break
                        }
                    }
                }
            }

            // 2. Wall / Cushion Collisions
            for (i in 0 until activeCount) {
                val p = activePieces[i]
                if (p.isPocketed) continue

                val minBound = BoardGeometry.PLAYABLE_MIN + p.radius
                val maxBound = BoardGeometry.PLAYABLE_MAX - p.radius
                var hitWall = false
                var impactSpeed = 0f

                if (p.x < minBound) {
                    p.x = minBound
                    p.vx = -p.vx * RESTITUTION_WALL
                    impactSpeed = abs(p.vx)
                    hitWall = true
                } else if (p.x > maxBound) {
                    p.x = maxBound
                    p.vx = -p.vx * RESTITUTION_WALL
                    impactSpeed = abs(p.vx)
                    hitWall = true
                }

                if (p.y < minBound) {
                    p.y = minBound
                    p.vy = -p.vy * RESTITUTION_WALL
                    impactSpeed = max(impactSpeed, abs(p.vy))
                    hitWall = true
                } else if (p.y > maxBound) {
                    p.y = maxBound
                    p.vy = -p.vy * RESTITUTION_WALL
                    impactSpeed = max(impactSpeed, abs(p.vy))
                    hitWall = true
                }

                if (hitWall && impactSpeed > 0.6f) {
                    PerformanceTracker.recordCollision()
                    onWall((impactSpeed / 14f).coerceIn(0f, 1f))
                }
            }

            // 3. Circle-Circle Collisions & Positional Separation
            for (i in 0 until activeCount) {
                val p1 = activePieces[i]
                if (p1.isPocketed) continue

                for (j in i + 1 until activeCount) {
                    val p2 = activePieces[j]
                    if (p2.isPocketed) continue

                    val dx = p2.x - p1.x
                    val dy = p2.y - p1.y
                    val dist = hypot(dx, dy)
                    val minDist = p1.radius + p2.radius

                    if (dist < minDist && dist > 0.0001f) {
                        val invDist = 1f / dist
                        val nx = dx * invDist
                        val ny = dy * invDist

                        // Positional correction to prevent disc overlap
                        val overlap = minDist - dist
                        val totalMass = p1.mass + p2.mass
                        val invTotalMass = 1f / totalMass
                        val m1Ratio = p2.mass * invTotalMass
                        val m2Ratio = p1.mass * invTotalMass

                        p1.x -= nx * overlap * m1Ratio
                        p1.y -= ny * overlap * m1Ratio
                        p2.x += nx * overlap * m2Ratio
                        p2.y += ny * overlap * m2Ratio

                        val rvx = p2.vx - p1.vx
                        val rvy = p2.vy - p1.vy
                        val velAlongNormal = rvx * nx + rvy * ny

                        if (velAlongNormal < 0f) {
                            val restitution = if (p1.type == PieceType.STRIKER || p2.type == PieceType.STRIKER) {
                                RESTITUTION_STRIKER_PUCK
                            } else {
                                RESTITUTION_PUCK_PUCK
                            }

                            val impulse = -(1f + restitution) * velAlongNormal / ((1f / p1.mass) + (1f / p2.mass))
                            val invM1 = 1f / p1.mass
                            val invM2 = 1f / p2.mass

                            p1.vx -= (impulse * invM1) * nx
                            p1.vy -= (impulse * invM1) * ny
                            p2.vx += (impulse * invM2) * nx
                            p2.vy += (impulse * invM2) * ny

                            val impulseMag = abs(impulse)
                            if (impulseMag > 0.35f) {
                                PerformanceTracker.recordCollision()
                                onClack((impulseMag / 10f).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
        }

        PerformanceTracker.recordPhysicsTickEnd(startNanos)
        return PhysicsStepResult(pocketedThisFrame, anyMoving)
    }

    fun calculateTrajectory(
        striker: Piece,
        aimAngle: Float,
        power: Float,
        pieces: List<Piece>,
        maxBounces: Int = 2
    ): TrajectoryData {
        val speed = (power / 100f) * 34f
        var simX = striker.x
        var simY = striker.y
        var simVx = cos(aimAngle) * speed
        var simVy = sin(aimAngle) * speed

        val strikerPath = ArrayList<Vector2D>(40)
        strikerPath.add(Vector2D(simX, simY))
        var targetHitPiece: Piece? = null
        var targetHitGhostPos: Vector2D? = null
        val targetPath = ArrayList<Vector2D>(2)
        val strikerDeflectPath = ArrayList<Vector2D>(2)

        val maxSteps = 160
        val dt = 0.5f
        var bounces = 0
        val pieceCount = pieces.size

        for (s in 0 until maxSteps) {
            simX += simVx * dt
            simY += simVy * dt

            // Piece collision check
            for (i in 0 until pieceCount) {
                val p = pieces[i]
                if (p.isPocketed) continue
                val dx = p.x - simX
                val dy = p.y - simY
                val dist = hypot(dx, dy)
                val minDist = striker.radius + p.radius

                if (dist <= minDist) {
                    targetHitPiece = p
                    targetHitGhostPos = Vector2D(simX, simY)
                    strikerPath.add(Vector2D(simX, simY))

                    val normDist = if (dist > 0f) dist else 1f
                    val nx = dx / normDist
                    val ny = dy / normDist
                    val targetSpeed = max(8f, speed * 0.7f)

                    targetPath.add(Vector2D(p.x, p.y))
                    targetPath.add(Vector2D(p.x + nx * targetSpeed * 6.5f, p.y + ny * targetSpeed * 6.5f))

                    val tanX = -ny
                    val tanY = nx
                    val dotTan = simVx * tanX + simVy * tanY
                    strikerDeflectPath.add(Vector2D(simX, simY))
                    strikerDeflectPath.add(Vector2D(simX + tanX * dotTan * 4f, simY + tanY * dotTan * 4f))

                    return TrajectoryData(strikerPath, targetHitPiece, targetHitGhostPos, targetPath, strikerDeflectPath)
                }
            }

            // Wall bounce
            val minBound = BoardGeometry.PLAYABLE_MIN + striker.radius
            val maxBound = BoardGeometry.PLAYABLE_MAX - striker.radius
            var bounced = false

            if (simX < minBound) {
                simX = minBound
                simVx = -simVx
                bounced = true
            } else if (simX > maxBound) {
                simX = maxBound
                simVx = -simVx
                bounced = true
            }

            if (simY < minBound) {
                simY = minBound
                simVy = -simVy
                bounced = true
            } else if (simY > maxBound) {
                simY = maxBound
                simVy = -simVy
                bounced = true
            }

            if (bounced) {
                bounces++
                strikerPath.add(Vector2D(simX, simY))
                if (bounces >= maxBounces) break
            } else if (s % 4 == 0) {
                strikerPath.add(Vector2D(simX, simY))
            }
        }

        strikerPath.add(Vector2D(simX, simY))
        return TrajectoryData(strikerPath, targetHitPiece, targetHitGhostPos, targetPath, strikerDeflectPath)
    }
}

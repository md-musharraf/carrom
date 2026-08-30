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

    // Ultra-smooth physical parameters calibrated to international Carrom boards
    const val SUB_STEPS = 16
    const val RESTITUTION_PUCK_PUCK = 0.95f
    const val RESTITUTION_STRIKER_PUCK = 0.93f
    const val RESTITUTION_WALL = 0.89f
    const val WALL_TANGENTIAL_FRICTION = 0.97f // Dampens tangential sliding against wooden frame
    const val DISC_TANGENTIAL_FRICTION = 0.98f // Slight rotational friction on collision

    // Boric acid carrom powder glide physics
    const val FRICTION_BASE = 0.9935f
    const val LINEAR_DRAG = 0.026f
    const val VELOCITY_EPSILON = 0.06f

    fun generateClassicCluster(): List<Piece> {
        val pieces = ArrayList<Piece>(19)
        val cx = BoardGeometry.BOARD_SIZE / 2f
        val cy = BoardGeometry.BOARD_SIZE / 2f
        val r = BoardGeometry.PUCK_RADIUS
        val gap = 0.35f

        // 1. Center Queen
        pieces.add(PieceFactory.createPiece("queen", PieceType.QUEEN, cx, cy))

        // 2. Inner ring: 6 pieces (3 White, 3 Black alternating)
        val d1 = (2f * r) + gap
        val angleStep6 = BoardGeometry.TWO_PI / 6f
        for (i in 0 until 6) {
            val angle = i * angleStep6
            val isWhite = (i % 2 == 0)
            val type = if (isWhite) PieceType.WHITE else PieceType.BLACK
            pieces.add(
                PieceFactory.createPiece(
                    id = "inner_${i}_${type.name.lowercase()}",
                    type = type,
                    x = cx + cos(angle) * d1,
                    y = cy + sin(angle) * d1
                )
            )
        }

        // 3. Outer ring: 12 pieces in hexagonal geometry (6 corners at 2*d1, 6 mid-edges at sqrt(3)*d1)
        val dCorner = 2f * d1
        val dEdge = sqrt(3.0).toFloat() * d1
        val halfAngleStep = angleStep6 / 2f

        for (i in 0 until 6) {
            // Corner piece (angle = i * 60 deg)
            val cornerAngle = i * angleStep6
            val isCornerWhite = (i % 2 == 1)
            val cornerType = if (isCornerWhite) PieceType.WHITE else PieceType.BLACK

            pieces.add(
                PieceFactory.createPiece(
                    id = "outer_c_${i}_${cornerType.name.lowercase()}",
                    type = cornerType,
                    x = cx + cos(cornerAngle) * dCorner,
                    y = cy + sin(cornerAngle) * dCorner
                )
            )

            // Mid-edge piece (angle = i * 60 deg + 30 deg)
            val edgeAngle = cornerAngle + halfAngleStep
            val isEdgeWhite = (i % 2 == 0)
            val edgeType = if (isEdgeWhite) PieceType.WHITE else PieceType.BLACK

            pieces.add(
                PieceFactory.createPiece(
                    id = "outer_e_${i}_${edgeType.name.lowercase()}",
                    type = edgeType,
                    x = cx + cos(edgeAngle) * dEdge,
                    y = cy + sin(edgeAngle) * dEdge
                )
            )
        }

        return pieces
    }

    fun createStriker(fraction: Float = 0.5f, isBottom: Boolean = true): Piece {
        return PieceFactory.createStriker(fraction, isBottom)
    }

    /**
     * Executes physics simulation with variable delta-time support and multi-stage sub-stepping.
     * Guarantees silky smooth glide, zero tunneling at high velocities, and realistic multi-body contacts.
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
                // Smooth parabolic sink into pocket hole
                p.pocketProgress = max(0f, p.pocketProgress - (0.055f * subDt * (subStepCount / 12f)))
            }
        }

        val pocketedThisFrame = ArrayList<Piece>(4)
        var anyMoving = false
        val activeCount = activePieces.size

        for (step in 0 until subStepCount) {
            // 1. Position update, Powder friction deceleration & Pocket suction
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

                    // Smooth combination of exponential powder glide and linear surface drag
                    p.vx = (p.vx * frictionMult) - (normVx * decel)
                    p.vy = (p.vy * frictionMult) - (normVy * decel)
                    anyMoving = true
                } else {
                    p.vx = 0f
                    p.vy = 0f
                }

                // Pocket gravitational suction well
                for (pocket in BoardGeometry.POCKETS) {
                    val dx = pocket.x - p.x
                    val dy = pocket.y - p.y
                    val dist = hypot(dx, dy)

                    if (dist < BoardGeometry.POCKET_SUCTION_RADIUS) {
                        val suctionRatio = (1f - dist / BoardGeometry.POCKET_SUCTION_RADIUS).coerceIn(0f, 1f)
                        val pullForce = 0.65f * (suctionRatio * suctionRatio) * subDt
                        val normDist = if (dist > 0.001f) dist else 1f
                        p.vx += (dx / normDist) * pullForce
                        p.vy += (dy / normDist) * pullForce

                        val dropThreshold = BoardGeometry.POCKET_RADIUS - (if (p.type == PieceType.STRIKER) 4.5f else 1.5f)
                        if (dist < dropThreshold) {
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

            // 2. Wall / Cushion Collisions with Tangential Damping
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
                    p.vy *= WALL_TANGENTIAL_FRICTION
                    impactSpeed = abs(p.vx)
                    hitWall = true
                } else if (p.x > maxBound) {
                    p.x = maxBound
                    p.vx = -p.vx * RESTITUTION_WALL
                    p.vy *= WALL_TANGENTIAL_FRICTION
                    impactSpeed = abs(p.vx)
                    hitWall = true
                }

                if (p.y < minBound) {
                    p.y = minBound
                    p.vy = -p.vy * RESTITUTION_WALL
                    p.vx *= WALL_TANGENTIAL_FRICTION
                    impactSpeed = max(impactSpeed, abs(p.vy))
                    hitWall = true
                } else if (p.y > maxBound) {
                    p.y = maxBound
                    p.vy = -p.vy * RESTITUTION_WALL
                    p.vx *= WALL_TANGENTIAL_FRICTION
                    impactSpeed = max(impactSpeed, abs(p.vy))
                    hitWall = true
                }

                if (hitWall && impactSpeed > 0.5f) {
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

                        // Positional correction to eliminate overlap cleanly
                        val overlap = minDist - dist
                        val totalMass = p1.mass + p2.mass
                        val invTotalMass = 1f / totalMass
                        val m1Ratio = p2.mass * invTotalMass
                        val m2Ratio = p1.mass * invTotalMass

                        p1.x -= nx * overlap * m1Ratio * 0.95f
                        p1.y -= ny * overlap * m1Ratio * 0.95f
                        p2.x += nx * overlap * m2Ratio * 0.95f
                        p2.y += ny * overlap * m2Ratio * 0.95f

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

                            // Micro tangential friction during contact
                            val tx = -ny
                            val ty = nx
                            val velAlongTangent = rvx * tx + rvy * ty
                            val tangentImpulse = -velAlongTangent * 0.05f / ((1f / p1.mass) + (1f / p2.mass))
                            p1.vx -= (tangentImpulse * invM1) * tx
                            p1.vy -= (tangentImpulse * invM1) * ty
                            p2.vx += (tangentImpulse * invM2) * tx
                            p2.vy += (tangentImpulse * invM2) * ty

                            val impulseMag = abs(impulse)
                            if (impulseMag > 0.3f) {
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
        maxBounces: Int = 3
    ): TrajectoryData {
        val speed = (power / 100f) * 34f
        var simX = striker.x
        var simY = striker.y
        var simVx = cos(aimAngle) * speed
        var simVy = sin(aimAngle) * speed

        val strikerPath = ArrayList<Vector2D>(60)
        strikerPath.add(Vector2D(simX, simY))
        var targetHitPiece: Piece? = null
        var targetHitGhostPos: Vector2D? = null
        val targetPath = ArrayList<Vector2D>(2)
        val strikerDeflectPath = ArrayList<Vector2D>(2)

        val maxSteps = 220
        val dt = 0.4f
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

                    val normDist = if (dist > 0.001f) dist else 1f
                    val nx = dx / normDist
                    val ny = dy / normDist
                    val targetSpeed = max(8f, speed * 0.75f)

                    // Target puck deflect line
                    targetPath.add(Vector2D(p.x, p.y))
                    targetPath.add(Vector2D(p.x + nx * targetSpeed * 7.5f, p.y + ny * targetSpeed * 7.5f))

                    // Striker post-impact tangent deflection line
                    val tanX = -ny
                    val tanY = nx
                    val dotTan = simVx * tanX + simVy * tanY
                    strikerDeflectPath.add(Vector2D(simX, simY))
                    strikerDeflectPath.add(Vector2D(simX + tanX * dotTan * 4.5f, simY + tanY * dotTan * 4.5f))

                    return TrajectoryData(strikerPath, targetHitPiece, targetHitGhostPos, targetPath, strikerDeflectPath)
                }
            }

            // Wall bounce
            val minBound = BoardGeometry.PLAYABLE_MIN + striker.radius
            val maxBound = BoardGeometry.PLAYABLE_MAX - striker.radius
            var bounced = false

            if (simX < minBound) {
                simX = minBound
                simVx = -simVx * RESTITUTION_WALL
                bounced = true
            } else if (simX > maxBound) {
                simX = maxBound
                simVx = -simVx * RESTITUTION_WALL
                bounced = true
            }

            if (simY < minBound) {
                simY = minBound
                simVy = -simVy * RESTITUTION_WALL
                bounced = true
            } else if (simY > maxBound) {
                simY = maxBound
                simVy = -simVy * RESTITUTION_WALL
                bounced = true
            }

            if (bounced) {
                bounces++
                strikerPath.add(Vector2D(simX, simY))
                if (bounces >= maxBounces) break
            } else if (s % 3 == 0) {
                strikerPath.add(Vector2D(simX, simY))
            }
        }

        strikerPath.add(Vector2D(simX, simY))
        return TrajectoryData(strikerPath, targetHitPiece, targetHitGhostPos, targetPath, strikerDeflectPath)
    }
}

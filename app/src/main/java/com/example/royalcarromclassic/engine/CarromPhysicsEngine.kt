package com.example.royalcarromclassic.engine

import com.example.royalcarromclassic.core.logging.PerformanceTracker
import com.example.royalcarromclassic.data.Piece
import com.example.royalcarromclassic.data.PieceType
import com.example.royalcarromclassic.data.Pocket
import com.example.royalcarromclassic.data.Vector2D
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Deterministic carrom physics in board units. Velocities are expressed in board units per
 * 60 Hz frame; every function takes real elapsed seconds so motion is identical at 60, 90 or 120 Hz.
 *
 * Not thread-safe by design: the game loop drives it from the main thread, once per display frame.
 */
object CarromPhysicsEngine {

    data class TrajectoryData(
        val strikerPath: List<Vector2D>,
        val targetHitPiece: Piece?,
        val targetHitGhostPos: Vector2D?,
        val targetPath: List<Vector2D>,
        val strikerDeflectPath: List<Vector2D>,
        /** Pocket the struck disc is predicted to drop into, or -1. */
        val targetPocketId: Int = -1,
        /** Pocket the striker itself is predicted to fall into (a foul), or -1. */
        val strikerPocketId: Int = -1,
    )

    /**
     * How the table plays. Board, coin, striker and dice powers adjust it for a match or a single
     * shot; [STANDARD] is the regulation table (and the one the server simulates online).
     */
    data class PhysicsTuning(
        /** Per-60 Hz-frame glide factor for carrom men (closer to 1 = glides farther). */
        val discFriction: Float = FRICTION_BASE,
        /** Per-60 Hz-frame glide factor for the striker. */
        val strikerFriction: Float = FRICTION_BASE,
        val linearDrag: Float = LINEAR_DRAG,
        val wallRestitution: Float = RESTITUTION_WALL,
        /** Scales how far from a pocket's centre a disc still drops, and its pull radius. */
        val pocketScale: Float = 1f,
        /** Strength of the pull that guides rolling carrom men over the pocket lip. */
        val discPocketPull: Float = POCKET_PULL
    ) {
        companion object {
            val STANDARD = PhysicsTuning()
        }
    }

    /** Contact callback: [intensity] is normalised to 0..1, ([x], [y]) is the contact point. */
    fun interface ContactListener {
        fun onContact(intensity: Float, x: Float, y: Float)
    }

    fun interface PocketListener {
        fun onPocketed(piece: Piece, pocket: Pocket)
    }

    private val NO_CONTACT = ContactListener { _, _, _ -> }
    private val NO_POCKET = PocketListener { _, _ -> }

    // Integration sub-steps per 60 Hz frame (scaled with the real frame time, capped for safety).
    const val SUB_STEPS = 16
    private const val MAX_SUB_STEPS = 64
    private const val SUB_STEP_EPSILON = 1e-3f

    /** Striker launch speed at 100% power, in board units per 60 Hz frame. */
    const val MAX_STRIKE_SPEED = 34f

    // Ultra-smooth physical parameters calibrated to international Carrom boards
    const val RESTITUTION_PUCK_PUCK = 0.95f
    const val RESTITUTION_STRIKER_PUCK = 0.93f
    const val RESTITUTION_WALL = 0.89f
    const val WALL_TANGENTIAL_FRICTION = 0.97f // Dampens tangential sliding against wooden frame
    private const val CONTACT_TANGENTIAL_FRICTION = 0.05f

    // Boric acid carrom powder glide physics
    const val FRICTION_BASE = 0.9935f
    const val LINEAR_DRAG = 0.026f
    const val VELOCITY_EPSILON = 0.06f

    const val POCKET_PULL = 0.65f
    private const val STRIKER_DROP_MARGIN = 4.5f
    private const val PUCK_DROP_MARGIN = 1.5f

    /** Seconds a disc takes to slide from the pocket lip down into the net. */
    const val POCKET_DROP_SECONDS = 0.34f

    /** Base on-screen length of the aim guide before striker upgrades. */
    const val DEFAULT_GUIDE_LENGTH = 560f
    private const val TRAJECTORY_STEP = 0.5f
    private const val TRAJECTORY_MIN_SPEED = 0.25f
    private const val TARGET_GUIDE_LENGTH = 520f
    private const val DEFLECT_GUIDE_LENGTH = 150f
    private const val NO_HIT = Float.MAX_VALUE

    /** The classic 19-disc rosette; [radius] and [mass] vary with the coin set's power offline. */
    fun generateClassicCluster(
        radius: Float = BoardGeometry.PUCK_RADIUS,
        mass: Float = BoardGeometry.PUCK_MASS
    ): List<Piece> {
        val pieces = ArrayList<Piece>(19)
        val cx = BoardGeometry.CENTER
        val cy = BoardGeometry.CENTER
        val r = radius
        val gap = 0.35f
        fun disc(id: String, type: PieceType, x: Float, y: Float) =
            PieceFactory.createPiece(id, type, x, y, radius = radius, mass = mass)

        // 1. Center Queen
        pieces.add(disc("queen", PieceType.QUEEN, cx, cy))

        // 2. Inner ring: 6 pieces (3 White, 3 Black alternating)
        val d1 = (2f * r) + gap
        val angleStep6 = BoardGeometry.TWO_PI / 6f
        for (i in 0 until 6) {
            val angle = i * angleStep6
            val type = if (i % 2 == 0) PieceType.WHITE else PieceType.BLACK
            pieces.add(
                disc(
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
            val cornerAngle = i * angleStep6
            val cornerType = if (i % 2 == 1) PieceType.WHITE else PieceType.BLACK
            pieces.add(
                disc(
                    id = "outer_c_${i}_${cornerType.name.lowercase()}",
                    type = cornerType,
                    x = cx + cos(cornerAngle) * dCorner,
                    y = cy + sin(cornerAngle) * dCorner
                )
            )

            val edgeAngle = cornerAngle + halfAngleStep
            val edgeType = if (i % 2 == 0) PieceType.WHITE else PieceType.BLACK
            pieces.add(
                disc(
                    id = "outer_e_${i}_${edgeType.name.lowercase()}",
                    type = edgeType,
                    x = cx + cos(edgeAngle) * dEdge,
                    y = cy + sin(edgeAngle) * dEdge
                )
            )
        }

        return pieces
    }

    fun createStriker(fraction: Float = 0.5f, isBottom: Boolean = true): Piece =
        PieceFactory.createStriker(fraction, isBottom)

    /**
     * Advances the simulation by [dtSeconds] of real time using enough sub-steps to keep every
     * disc moving less than a fraction of its radius per step (no tunnelling at full power).
     *
     * @return true while any disc on the board is still moving.
     */
    fun updatePhysics(
        pieces: List<Piece>,
        striker: Piece?,
        dtSeconds: Float = 1f / 60f,
        onClack: ContactListener = NO_CONTACT,
        onWall: ContactListener = NO_CONTACT,
        onPocket: PocketListener = NO_POCKET,
        tuning: PhysicsTuning = PhysicsTuning.STANDARD
    ): Boolean {
        val startNanos = PerformanceTracker.recordPhysicsTickStart()

        val frames = dtSeconds.coerceAtLeast(0f) * 60f
        // The epsilon absorbs float noise ((1f / 60f) * 60f is 1.0000001), so a 60 Hz frame is
        // exactly SUB_STEPS steps and two 120 Hz frames integrate identically to one 60 Hz frame.
        val subStepCount = ceil(frames * SUB_STEPS - SUB_STEP_EPSILON).toInt().coerceIn(1, MAX_SUB_STEPS)
        val subDt = frames / subStepCount
        val discFrictionMult = tuning.discFriction.pow(subDt)
        val strikerFrictionMult = tuning.strikerFriction.pow(subDt)
        val linearDecel = tuning.linearDrag * subDt

        val active = ArrayList<Piece>(pieces.size + 1)
        if (striker != null && !striker.isPocketed) active.add(striker)
        for (i in pieces.indices) {
            val p = pieces[i]
            if (!p.isPocketed) active.add(p)
        }

        for (step in 0 until subStepCount) {
            integrate(active, subDt, discFrictionMult, strikerFrictionMult, linearDecel, tuning, onPocket)
            resolveCushions(active, tuning.wallRestitution, onWall)
            resolveContacts(active, onClack)
        }

        var anyMoving = false
        for (i in active.indices) {
            val p = active[i]
            if (!p.isPocketed && (p.vx != 0f || p.vy != 0f)) {
                anyMoving = true
                break
            }
        }

        PerformanceTracker.recordPhysicsTickEnd(startNanos)
        return anyMoving
    }

    /** Powder-glide friction, pocket guidance and pocket drops for one sub-step. */
    private fun integrate(
        active: List<Piece>,
        subDt: Float,
        discFrictionMult: Float,
        strikerFrictionMult: Float,
        linearDecel: Float,
        tuning: PhysicsTuning,
        onPocket: PocketListener
    ) {
        val pockets = BoardGeometry.POCKETS
        val suction = BoardGeometry.POCKET_SUCTION_RADIUS * tuning.pocketScale
        val pocketRadius = BoardGeometry.POCKET_RADIUS * tuning.pocketScale
        for (i in active.indices) {
            val p = active[i]
            if (p.isPocketed) continue

            p.x += p.vx * subDt
            p.y += p.vy * subDt

            val speed = hypot(p.vx, p.vy)
            val isMoving = speed > VELOCITY_EPSILON
            if (isMoving) {
                // Exponential powder glide combined with a gentle linear surface drag.
                val frictionMult = if (p.type == PieceType.STRIKER) strikerFrictionMult else discFrictionMult
                val scale = frictionMult - min(speed, linearDecel) / speed
                p.vx *= scale
                p.vy *= scale
            } else {
                p.vx = 0f
                p.vy = 0f
            }

            val isStriker = p.type == PieceType.STRIKER
            val dropThreshold = pocketRadius - if (isStriker) STRIKER_DROP_MARGIN else PUCK_DROP_MARGIN
            val pocketPull = if (isStriker) POCKET_PULL else tuning.discPocketPull
            for (k in pockets.indices) {
                val pocket = pockets[k]
                val dx = pocket.x - p.x
                val dy = pocket.y - p.y
                val dist = hypot(dx, dy)
                if (dist >= suction) continue

                if (dist < dropThreshold) {
                    p.isPocketed = true
                    p.pocketId = pocket.id
                    p.pocketProgress = 1f
                    p.vx = 0f
                    p.vy = 0f
                    onPocket.onPocketed(p, pocket)
                    break
                }

                // Only rolling discs are guided over the lip, so resting discs never creep.
                if (isMoving && dist > 0.001f) {
                    val ratio = 1f - dist / suction
                    val pull = pocketPull * ratio * ratio * subDt / dist
                    p.vx += dx * pull
                    p.vy += dy * pull
                }
            }
        }
    }

    /** Wall (cushion) rebounds with tangential damping. */
    private fun resolveCushions(active: List<Piece>, restitution: Float, onWall: ContactListener) {
        for (i in active.indices) {
            val p = active[i]
            if (p.isPocketed) continue

            val minBound = BoardGeometry.PLAYABLE_MIN + p.radius
            val maxBound = BoardGeometry.PLAYABLE_MAX - p.radius
            var impactSpeed = 0f

            if (p.x < minBound) {
                p.x = minBound
                p.vx = -p.vx * restitution
                p.vy *= WALL_TANGENTIAL_FRICTION
                impactSpeed = abs(p.vx)
            } else if (p.x > maxBound) {
                p.x = maxBound
                p.vx = -p.vx * restitution
                p.vy *= WALL_TANGENTIAL_FRICTION
                impactSpeed = abs(p.vx)
            }

            if (p.y < minBound) {
                p.y = minBound
                p.vy = -p.vy * restitution
                p.vx *= WALL_TANGENTIAL_FRICTION
                impactSpeed = maxOf(impactSpeed, abs(p.vy))
            } else if (p.y > maxBound) {
                p.y = maxBound
                p.vy = -p.vy * restitution
                p.vx *= WALL_TANGENTIAL_FRICTION
                impactSpeed = maxOf(impactSpeed, abs(p.vy))
            }

            if (impactSpeed > 0.5f) {
                PerformanceTracker.recordCollision()
                onWall.onContact((impactSpeed / 14f).coerceIn(0f, 1f), p.x, p.y)
            }
        }
    }

    /** Disc-disc impulses with positional separation and a touch of contact friction. */
    private fun resolveContacts(active: List<Piece>, onClack: ContactListener) {
        val count = active.size
        for (i in 0 until count) {
            val p1 = active[i]
            if (p1.isPocketed) continue

            for (j in i + 1 until count) {
                val p2 = active[j]
                if (p2.isPocketed) continue

                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                val minDist = p1.radius + p2.radius
                // Cheap rejection before the square root.
                if (abs(dx) >= minDist || abs(dy) >= minDist) continue
                val dist = hypot(dx, dy)
                if (dist >= minDist || dist <= 0.0001f) continue

                val nx = dx / dist
                val ny = dy / dist
                val invM1 = 1f / p1.mass
                val invM2 = 1f / p2.mass
                val invMassSum = 1f / (invM1 + invM2)

                // Positional correction to eliminate overlap cleanly
                val overlap = (minDist - dist) * 0.95f
                val m1Share = invM1 * invMassSum
                val m2Share = invM2 * invMassSum
                p1.x -= nx * overlap * m1Share
                p1.y -= ny * overlap * m1Share
                p2.x += nx * overlap * m2Share
                p2.y += ny * overlap * m2Share

                val rvx = p2.vx - p1.vx
                val rvy = p2.vy - p1.vy
                val velAlongNormal = rvx * nx + rvy * ny
                if (velAlongNormal >= 0f) continue

                val restitution = if (p1.type == PieceType.STRIKER || p2.type == PieceType.STRIKER) {
                    RESTITUTION_STRIKER_PUCK
                } else {
                    RESTITUTION_PUCK_PUCK
                }

                val impulse = -(1f + restitution) * velAlongNormal * invMassSum
                p1.vx -= impulse * invM1 * nx
                p1.vy -= impulse * invM1 * ny
                p2.vx += impulse * invM2 * nx
                p2.vy += impulse * invM2 * ny

                // Micro tangential friction during contact
                val tx = -ny
                val ty = nx
                val tangentImpulse = -(rvx * tx + rvy * ty) * CONTACT_TANGENTIAL_FRICTION * invMassSum
                p1.vx -= tangentImpulse * invM1 * tx
                p1.vy -= tangentImpulse * invM1 * ty
                p2.vx += tangentImpulse * invM2 * tx
                p2.vy += tangentImpulse * invM2 * ty

                val impulseMag = abs(impulse)
                if (impulseMag > 0.3f) {
                    PerformanceTracker.recordCollision()
                    onClack.onContact(
                        (impulseMag / 10f).coerceIn(0f, 1f),
                        p1.x + nx * p1.radius,
                        p1.y + ny * p1.radius
                    )
                }
            }
        }
    }

    /**
     * Advances the drop-into-pocket animation of every pocketed disc.
     * @return true while any disc is still visibly sinking.
     */
    fun advancePocketDrops(pieces: List<Piece>, striker: Piece?, dtSeconds: Float): Boolean {
        val step = dtSeconds / POCKET_DROP_SECONDS
        var sinking = false
        for (i in pieces.indices) {
            if (sinkStep(pieces[i], step)) sinking = true
        }
        if (striker != null && sinkStep(striker, step)) sinking = true
        return sinking
    }

    private fun sinkStep(piece: Piece, step: Float): Boolean {
        if (!piece.isPocketed || piece.pocketProgress <= 0f) return false
        piece.pocketProgress = (piece.pocketProgress - step).coerceAtLeast(0f)
        return piece.pocketProgress > 0f
    }

    /** Stops every disc immediately (used as a safety net for runaway simulations). */
    fun haltAll(pieces: List<Piece>, striker: Piece?) {
        for (i in pieces.indices) {
            pieces[i].vx = 0f
            pieces[i].vy = 0f
        }
        striker?.let {
            it.vx = 0f
            it.vy = 0f
        }
    }

    /**
     * Finds the free spot closest to ([x], [y]) for a disc of [radius], searching outward in rings.
     * Used to re-spot the queen without overlapping discs that now sit on the centre.
     */
    fun findFreeSpot(
        pieces: List<Piece>,
        radius: Float,
        x: Float = BoardGeometry.CENTER,
        y: Float = BoardGeometry.CENTER,
        ignore: Piece? = null
    ): Vector2D {
        if (isSpotFree(pieces, radius, x, y, ignore)) return Vector2D(x, y)
        val minBound = BoardGeometry.PLAYABLE_MIN + radius
        val maxBound = BoardGeometry.PLAYABLE_MAX - radius
        for (ring in 1..16) {
            val dist = ring * radius
            val samples = 8 * ring
            for (i in 0 until samples) {
                val angle = i * BoardGeometry.TWO_PI / samples
                val cx = x + cos(angle) * dist
                val cy = y + sin(angle) * dist
                if (cx < minBound || cx > maxBound || cy < minBound || cy > maxBound) continue
                if (isSpotFree(pieces, radius, cx, cy, ignore)) return Vector2D(cx, cy)
            }
        }
        return Vector2D(x, y)
    }

    private fun isSpotFree(pieces: List<Piece>, radius: Float, x: Float, y: Float, ignore: Piece?): Boolean {
        for (i in pieces.indices) {
            val p = pieces[i]
            if (p === ignore || p.isPocketed) continue
            if (hypot(p.x - x, p.y - y) < radius + p.radius + 0.5f) return false
        }
        return true
    }

    /**
     * Predicts the striker's path for the aim guide, using the same friction model as the live
     * simulation. Stops at the first disc contact (and then predicts where that disc travels and
     * whether it drops), at a pocket, after [maxBounces] cushion rebounds, or after [maxLength].
     */
    fun calculateTrajectory(
        striker: Piece,
        aimAngle: Float,
        power: Float,
        pieces: List<Piece>,
        maxBounces: Int = 2,
        powerMultiplier: Float = 1f,
        maxLength: Float = DEFAULT_GUIDE_LENGTH,
        tuning: PhysicsTuning = PhysicsTuning.STANDARD
    ): TrajectoryData {
        val speed0 = (power / 100f) * MAX_STRIKE_SPEED * powerMultiplier
        var x = striker.x
        var y = striker.y
        var vx = cos(aimAngle) * speed0
        var vy = sin(aimAngle) * speed0
        val r = striker.radius
        val minBound = BoardGeometry.PLAYABLE_MIN + r
        val maxBound = BoardGeometry.PLAYABLE_MAX - r
        val stepFriction = tuning.strikerFriction.pow(TRAJECTORY_STEP)
        val stepDrag = tuning.linearDrag * TRAJECTORY_STEP
        val strikerDrop = BoardGeometry.POCKET_RADIUS * tuning.pocketScale - STRIKER_DROP_MARGIN

        val path = ArrayList<Vector2D>(maxBounces + 3)
        path.add(Vector2D(x, y))
        var travelled = 0f
        var bounces = 0

        while (true) {
            val speed = hypot(vx, vy)
            if (speed < TRAJECTORY_MIN_SPEED) break
            val stepX = vx * TRAJECTORY_STEP
            val stepY = vy * TRAJECTORY_STEP
            val stepLen = speed * TRAJECTORY_STEP

            // Earliest event along this step: disc contact, pocket, cushion or guide length.
            var eventT = NO_HIT
            var hit: Piece? = null
            for (i in pieces.indices) {
                val p = pieces[i]
                if (p.isPocketed || p === striker) continue
                val t = sweepCircle(x, y, stepX, stepY, p.x, p.y, r + p.radius)
                if (t < eventT) {
                    eventT = t
                    hit = p
                }
            }

            var pocketId = -1
            val pockets = BoardGeometry.POCKETS
            for (k in pockets.indices) {
                val t = sweepCircle(x, y, stepX, stepY, pockets[k].x, pockets[k].y, strikerDrop)
                if (t < eventT) {
                    eventT = t
                    pocketId = pockets[k].id
                    hit = null
                }
            }

            val wallT = cushionCrossing(x, y, stepX, stepY, minBound, maxBound)
            val lengthT = (maxLength - travelled) / stepLen

            if (hit != null && eventT <= 1f && eventT <= wallT && eventT <= lengthT) {
                x += stepX * eventT
                y += stepY * eventT
                path.add(Vector2D(x, y))
                return predictContact(striker, hit, x, y, vx, vy, pieces, path, tuning)
            }
            if (pocketId >= 0 && eventT <= 1f && eventT <= wallT && eventT <= lengthT) {
                x += stepX * eventT
                y += stepY * eventT
                path.add(Vector2D(x, y))
                return TrajectoryData(path, null, null, emptyList(), emptyList(), strikerPocketId = pocketId)
            }
            if (lengthT <= 1f && lengthT <= wallT) {
                x += stepX * lengthT
                y += stepY * lengthT
                break
            }
            if (wallT <= 1f) {
                x += stepX * wallT
                y += stepY * wallT
                travelled += stepLen * wallT
                if (x <= minBound + 0.01f || x >= maxBound - 0.01f) {
                    vx = -vx * tuning.wallRestitution
                    vy *= WALL_TANGENTIAL_FRICTION
                }
                if (y <= minBound + 0.01f || y >= maxBound - 0.01f) {
                    vy = -vy * tuning.wallRestitution
                    vx *= WALL_TANGENTIAL_FRICTION
                }
                x = x.coerceIn(minBound, maxBound)
                y = y.coerceIn(minBound, maxBound)
                path.add(Vector2D(x, y))
                bounces++
                if (bounces > maxBounces) {
                    return TrajectoryData(path, null, null, emptyList(), emptyList())
                }
                continue
            }

            x += stepX
            y += stepY
            travelled += stepLen
            val scale = stepFriction - min(speed, stepDrag) / speed
            vx *= scale
            vy *= scale
        }

        path.add(Vector2D(x, y))
        return TrajectoryData(path, null, null, emptyList(), emptyList())
    }

    /** Resolves the predicted striker → disc contact into target and striker follow-up guides. */
    private fun predictContact(
        striker: Piece,
        target: Piece,
        ghostX: Float,
        ghostY: Float,
        vx: Float,
        vy: Float,
        pieces: List<Piece>,
        strikerPath: List<Vector2D>,
        tuning: PhysicsTuning
    ): TrajectoryData {
        var nx = target.x - ghostX
        var ny = target.y - ghostY
        val nLen = hypot(nx, ny).coerceAtLeast(0.0001f)
        nx /= nLen
        ny /= nLen
        val vn = (vx * nx + vy * ny).coerceAtLeast(0f)
        val totalMass = striker.mass + target.mass

        val targetSpeed = (1f + RESTITUTION_STRIKER_PUCK) * striker.mass / totalMass * vn
        val targetEnd = projectDisc(
            target.x, target.y, nx, ny, targetSpeed, target.radius,
            BoardGeometry.POCKET_RADIUS * tuning.pocketScale - PUCK_DROP_MARGIN, pieces, target, striker, TARGET_GUIDE_LENGTH,
            tuning.discFriction, tuning.linearDrag
        )

        // The heavier striker keeps its tangential velocity plus part of the normal component.
        val keep = (striker.mass - RESTITUTION_STRIKER_PUCK * target.mass) / totalMass
        val tvx = vx - vn * nx
        val tvy = vy - vn * ny
        val dvx = tvx + keep * vn * nx
        val dvy = tvy + keep * vn * ny
        val dSpeed = hypot(dvx, dvy)
        val deflectPath = if (dSpeed > TRAJECTORY_MIN_SPEED) {
            val end = projectDisc(
                ghostX, ghostY, dvx / dSpeed, dvy / dSpeed, dSpeed, striker.radius,
                pocketThreshold = 0f, pieces = pieces, ignoreA = target, ignoreB = striker,
                maxLength = DEFLECT_GUIDE_LENGTH, friction = tuning.strikerFriction, drag = tuning.linearDrag
            )
            listOf(Vector2D(ghostX, ghostY), Vector2D(end.x, end.y))
        } else {
            emptyList()
        }

        return TrajectoryData(
            strikerPath = strikerPath,
            targetHitPiece = target,
            targetHitGhostPos = Vector2D(ghostX, ghostY),
            targetPath = listOf(Vector2D(target.x, target.y), Vector2D(targetEnd.x, targetEnd.y)),
            strikerDeflectPath = deflectPath,
            targetPocketId = targetEnd.pocketId,
        )
    }

    private class Projection(val x: Float, val y: Float, val pocketId: Int)

    /**
     * Projects a disc sliding in a straight line from ([x], [y]) until friction stops it or it meets
     * a cushion, another disc, or (when [pocketThreshold] > 0) a pocket.
     */
    private fun projectDisc(
        x: Float,
        y: Float,
        dirX: Float,
        dirY: Float,
        speed: Float,
        radius: Float,
        pocketThreshold: Float,
        pieces: List<Piece>,
        ignoreA: Piece?,
        ignoreB: Piece?,
        maxLength: Float,
        friction: Float = FRICTION_BASE,
        drag: Float = LINEAR_DRAG
    ): Projection {
        val length = min(frictionTravel(speed, friction, drag), maxLength)
        if (length <= 0.5f) return Projection(x, y, -1)
        val dx = dirX * length
        val dy = dirY * length

        var bestT = 1f
        var pocketId = -1
        val minBound = BoardGeometry.PLAYABLE_MIN + radius
        val maxBound = BoardGeometry.PLAYABLE_MAX - radius
        val wallT = cushionCrossing(x, y, dx, dy, minBound, maxBound)
        if (wallT < bestT) bestT = wallT

        for (i in pieces.indices) {
            val p = pieces[i]
            if (p.isPocketed || p === ignoreA || p === ignoreB) continue
            val t = sweepCircle(x, y, dx, dy, p.x, p.y, radius + p.radius)
            if (t < bestT) bestT = t
        }

        if (pocketThreshold > 0f) {
            val pockets = BoardGeometry.POCKETS
            for (k in pockets.indices) {
                val t = sweepCircle(x, y, dx, dy, pockets[k].x, pockets[k].y, pocketThreshold)
                if (t <= bestT) {
                    bestT = t
                    pocketId = pockets[k].id
                }
            }
        }

        if (pocketId >= 0) {
            val pocket = BoardGeometry.POCKETS[pocketId]
            return Projection(pocket.x, pocket.y, pocketId)
        }
        return Projection(x + dx * bestT, y + dy * bestT, -1)
    }

    /** Distance a disc launched at [speed] glides before powder friction stops it. */
    private fun frictionTravel(speed: Float, friction: Float, drag: Float): Float {
        val stepFriction = friction.pow(TRAJECTORY_STEP)
        val stepDrag = drag * TRAJECTORY_STEP
        var s = speed
        var distance = 0f
        while (s > TRAJECTORY_MIN_SPEED && distance < BoardGeometry.BOARD_SIZE * 2f) {
            distance += s * TRAJECTORY_STEP
            s = s * stepFriction - min(s, stepDrag)
        }
        return distance
    }

    /** Fraction of the step (dx, dy) at which a disc centre leaves [min, max] on either axis. */
    private fun cushionCrossing(x: Float, y: Float, dx: Float, dy: Float, min: Float, max: Float): Float {
        var t = NO_HIT
        if (dx < 0f && x + dx < min) t = minOf(t, (min - x) / dx)
        if (dx > 0f && x + dx > max) t = minOf(t, (max - x) / dx)
        if (dy < 0f && y + dy < min) t = minOf(t, (min - y) / dy)
        if (dy > 0f && y + dy > max) t = minOf(t, (max - y) / dy)
        return t.coerceAtLeast(0f)
    }

    /**
     * Earliest fraction t in [0, 1] at which a point moving from (px, py) by (dx, dy) enters the
     * circle of radius [r] around (cx, cy); [NO_HIT] if it never does during this step.
     */
    private fun sweepCircle(px: Float, py: Float, dx: Float, dy: Float, cx: Float, cy: Float, r: Float): Float {
        val fx = px - cx
        val fy = py - cy
        val c = fx * fx + fy * fy - r * r
        val b = 2f * (fx * dx + fy * dy)
        if (c <= 0f) return if (b < 0f) 0f else NO_HIT
        if (b >= 0f) return NO_HIT
        val a = dx * dx + dy * dy
        if (a < 1e-9f) return NO_HIT
        val disc = b * b - 4f * a * c
        if (disc < 0f) return NO_HIT
        val t = (-b - sqrt(disc)) / (2f * a)
        return if (t in 0f..1f) t else NO_HIT
    }
}

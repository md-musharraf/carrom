package com.example.royalcarromclassic.ui.board

import com.example.royalcarromclassic.data.Seat
import com.example.royalcarromclassic.engine.BoardGeometry
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * How touches on the board become striker moves. Pure geometry in board units, so the rules the
 * player feels are unit tested.
 *
 * Why the old gesture sometimes refused to pull: a drag that started on the striker was locked
 * into "slide" unless it began almost straight backwards, so any diagonal pull (every cut shot)
 * slid the striker instead, and the striker's touch target was smaller than a fingertip on most
 * phones. Now:
 *  - the striker's touch target is at least a fingertip wide, whatever the screen size;
 *  - only a drag that runs close to the baseline slides; anything else pulls;
 *  - a slide that turns backwards part-way becomes a pull, so a hesitant start is forgiven;
 *  - a drag forwards, into the board, aims at the finger instead of firing backwards;
 *  - a pull that lines up with the aim you set by touching the board snaps onto it (aim magnet),
 *    so pulling for power never spoils a carefully chosen angle.
 */
object ShotGesture {
    /** A drag slides only when it runs within ~30° of the baseline. */
    const val SLIDE_RATIO = 1.7f

    /** How far behind the baseline a sliding finger must drift before the slide becomes a pull. */
    const val ESCALATE_TO_PULL = 40f

    /** Extra touch radius around the striker, in board units (before the fingertip minimum). */
    const val STRIKER_TOUCH_SLOP = 30f

    enum class Kind { SLIDE, PULL, AIM }

    /** What a drag that started on the striker and moved by ([dx], [dy]) should do. */
    fun classify(dx: Float, dy: Float, seat: Seat): Kind {
        val back = dx * seat.outwardX + dy * seat.outwardY
        val along = if (seat.horizontal) dx else dy
        return when {
            abs(along) > abs(back) * SLIDE_RATIO -> Kind.SLIDE
            back < 0f -> Kind.AIM
            else -> Kind.PULL
        }
    }

    /** True when a sliding finger at ([fx], [fy]) has moved far enough behind the striker to pull instead. */
    fun escalatesToPull(fx: Float, fy: Float, strikerX: Float, strikerY: Float, seat: Seat): Boolean =
        (fx - strikerX) * seat.outwardX + (fy - strikerY) * seat.outwardY > ESCALATE_TO_PULL

    /** True when a touch at ([x], [y]) grabs a striker of [radius] at ([sx], [sy]). */
    fun touchesStriker(x: Float, y: Float, sx: Float, sy: Float, radius: Float, minTouchRadius: Float): Boolean =
        hypot(x - sx, y - sy) < maxOf(radius + STRIKER_TOUCH_SLOP, minTouchRadius)

    /** True when a touch at ([x], [y]) is on [seat]'s baseline rail (within [slop]). */
    fun touchesRail(x: Float, y: Float, seat: Seat, slop: Float): Boolean {
        val line = BoardGeometry.strikerPos(0.5f, seat)
        val across = if (seat.horizontal) abs(y - line.y) else abs(x - line.x)
        val along = if (seat.horizontal) x else y
        return across < slop &&
            along in (BoardGeometry.BASELINE_START_X - slop)..(BoardGeometry.BASELINE_END_X + slop)
    }

    /** The result of pulling the striker back to a point. */
    data class Pull(val angle: Float, val power: Float, val distance: Float)

    /**
     * Aim and power for a slingshot pulled from ([sx], [sy]) to the finger at ([fx], [fy]):
     * the shot flies away from the finger, harder the farther it is pulled (between [minPull] and
     * [maxPull] board units). Null while the pull is too short to fire (releasing there cancels).
     *
     * @param lockedAngle the aim chosen before the pull, or null
     * @param magnetRadians how close (radians) the pull must come to [lockedAngle] to snap onto it
     */
    fun pull(
        sx: Float,
        sy: Float,
        fx: Float,
        fy: Float,
        minPull: Float,
        maxPull: Float,
        lockedAngle: Float?,
        magnetRadians: Float
    ): Pull? {
        val dx = fx - sx
        val dy = fy - sy
        val dist = hypot(dx, dy)
        if (dist < minPull) return null
        val fraction = ((dist - minPull) / (maxPull - minPull).coerceAtLeast(1f)).coerceIn(0f, 1f)
        val power = BoardGeometry.MIN_POWER + fraction * (BoardGeometry.MAX_POWER - BoardGeometry.MIN_POWER)
        val raw = atan2(-dy, -dx)
        val angle = if (lockedAngle != null && abs(BoardGeometry.normalizeAngle(raw - lockedAngle)) <= magnetRadians) lockedAngle else raw
        return Pull(angle, power, dist)
    }
}

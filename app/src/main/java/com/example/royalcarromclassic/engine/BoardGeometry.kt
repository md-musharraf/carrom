package com.example.royalcarromclassic.engine

import com.example.royalcarromclassic.data.Pocket
import com.example.royalcarromclassic.data.Seat
import com.example.royalcarromclassic.data.Vector2D
import kotlin.math.hypot

/**
 * Gameplay geometry in board units (the board is BOARD_SIZE × BOARD_SIZE; the renderer scales it
 * to the screen). Purely decorative dimensions live with the board painter.
 */
object BoardGeometry {
    const val BOARD_SIZE = 800f
    const val BOARD_PADDING = 50f
    const val PLAYABLE_MIN = BOARD_PADDING
    const val PLAYABLE_MAX = BOARD_SIZE - BOARD_PADDING // 750f
    const val CENTER = BOARD_SIZE / 2f

    const val STRIKER_RADIUS = 22f
    const val PUCK_RADIUS = 15.5f
    const val POCKET_RADIUS = 32f
    const val POCKET_SUCTION_RADIUS = 40f

    /** Pre-computed math constants to avoid repeated conversions. */
    const val HALF_PI = (Math.PI / 2.0).toFloat()
    const val PI_F = Math.PI.toFloat()
    const val TWO_PI = (Math.PI * 2.0).toFloat()
    const val DEG_TO_RAD = (Math.PI / 180.0).toFloat()
    const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()

    const val STRIKER_MASS = 3.0f
    const val PUCK_MASS = 1.0f

    // Physics constants are centralized in CarromPhysicsEngine (DRY)

    const val BASELINE_OFFSET_FROM_EDGE = 140f
    const val BASELINE_WIDTH = 420f
    const val BASELINE_START_X = (BOARD_SIZE - BASELINE_WIDTH) / 2f // 190f
    const val BASELINE_END_X = BASELINE_START_X + BASELINE_WIDTH // 610f
    const val BASELINE_CIRCLE_RADIUS = 16f

    const val BASELINE_BOTTOM_Y = BOARD_SIZE - BASELINE_OFFSET_FROM_EDGE // 660f
    const val BASELINE_TOP_Y = BASELINE_OFFSET_FROM_EDGE // 140f
    const val BASELINE_LEFT_X = BASELINE_OFFSET_FROM_EDGE // 140f
    const val BASELINE_RIGHT_X = BOARD_SIZE - BASELINE_OFFSET_FROM_EDGE // 660f

    /** Allowed striker placement along the baseline (fraction of its length). */
    const val MIN_BASELINE_FRACTION = 0.06f
    const val MAX_BASELINE_FRACTION = 0.94f

    /** Allowed shot power range, in percent. */
    const val MIN_POWER = 20f
    const val MAX_POWER = 100f
    const val DEFAULT_POWER = 50f

    val POCKETS = listOf(
        Pocket(id = 0, x = PLAYABLE_MIN + 18f, y = PLAYABLE_MIN + 18f, radius = POCKET_RADIUS, name = "Top-Left"),
        Pocket(id = 1, x = PLAYABLE_MAX - 18f, y = PLAYABLE_MIN + 18f, radius = POCKET_RADIUS, name = "Top-Right"),
        Pocket(id = 2, x = PLAYABLE_MAX - 18f, y = PLAYABLE_MAX - 18f, radius = POCKET_RADIUS, name = "Bottom-Right"),
        Pocket(id = 3, x = PLAYABLE_MIN + 18f, y = PLAYABLE_MAX - 18f, radius = POCKET_RADIUS, name = "Bottom-Left")
    )

    fun getBaselineStrikerPos(fraction: Float, isBottomPlayer: Boolean = true): Vector2D =
        strikerPos(fraction, if (isBottomPlayer) Seat.BOTTOM else Seat.TOP)

    /**
     * The striker's centre at [fraction] along [seat]'s baseline. Fractions run left → right on
     * screen for the bottom and top seats, and top → bottom for the side seats.
     */
    fun strikerPos(fraction: Float, seat: Seat): Vector2D {
        val along = BASELINE_START_X + fraction.coerceIn(0f, 1f) * BASELINE_WIDTH
        return when (seat) {
            Seat.BOTTOM -> Vector2D(along, BASELINE_BOTTOM_Y)
            Seat.TOP -> Vector2D(along, BASELINE_TOP_Y)
            Seat.LEFT -> Vector2D(BASELINE_LEFT_X, along)
            Seat.RIGHT -> Vector2D(BASELINE_RIGHT_X, along)
        }
    }

    /** Converts a board x-coordinate on a horizontal baseline to a clamped placement fraction. */
    fun baselineFractionAt(x: Float): Float =
        ((x - BASELINE_START_X) / BASELINE_WIDTH).coerceIn(MIN_BASELINE_FRACTION, MAX_BASELINE_FRACTION)

    /** The clamped placement fraction on [seat]'s baseline nearest the board point ([x], [y]). */
    fun baselineFractionAt(x: Float, y: Float, seat: Seat): Float = baselineFractionAt(if (seat.horizontal) x else y)

    /** Straight-ahead aim for the player shooting from the given side. */
    fun forwardAngle(isBottomPlayer: Boolean): Float = if (isBottomPlayer) -HALF_PI else HALF_PI

    /** Straight-ahead aim (towards the far side) for [seat]. */
    fun forwardAngle(seat: Seat): Float = when (seat) {
        Seat.BOTTOM -> -HALF_PI
        Seat.TOP -> HALF_PI
        Seat.LEFT -> 0f
        Seat.RIGHT -> PI_F
    }

    fun isOverBaselineCircle(x: Float, y: Float, isBottomPlayer: Boolean = true): Boolean =
        isOverBaselineCircle(x, y, if (isBottomPlayer) Seat.BOTTOM else Seat.TOP)

    /** True when ([x], [y]) sits on one of the two red foul circles that end [seat]'s baseline. */
    fun isOverBaselineCircle(x: Float, y: Float, seat: Seat): Boolean {
        val a = strikerPos(0f, seat)
        val b = strikerPos(1f, seat)
        val limit = BASELINE_CIRCLE_RADIUS + 4f
        return hypot(x - a.x, y - a.y) <= limit || hypot(x - b.x, y - b.y) <= limit
    }

    /**
     * The rotation (radians, about the board centre) that carries [seat]'s baseline onto the top
     * baseline. Lets seat-agnostic logic (the bot) reason as if it always sat at the top.
     */
    fun rotationToTop(seat: Seat): Float = when (seat) {
        Seat.TOP -> 0f
        Seat.BOTTOM -> PI_F
        Seat.LEFT -> HALF_PI
        Seat.RIGHT -> -HALF_PI
    }

    /** Rotates the board point ([x], [y]) by [angle] radians about the centre. */
    fun rotateAboutCenter(x: Float, y: Float, angle: Float): Vector2D {
        if (angle == 0f) return Vector2D(x, y)
        val c = kotlin.math.cos(angle)
        val s = kotlin.math.sin(angle)
        val dx = x - CENTER
        val dy = y - CENTER
        return Vector2D(CENTER + dx * c - dy * s, CENTER + dx * s + dy * c)
    }

    /** Wraps an angle into (-π, π]. */
    fun normalizeAngle(angle: Float): Float {
        var a = angle % TWO_PI
        if (a <= -PI_F) a += TWO_PI
        if (a > PI_F) a -= TWO_PI
        return a
    }
}

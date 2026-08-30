package com.example.royalcarromclassic.engine

import com.example.royalcarromclassic.data.Pocket
import com.example.royalcarromclassic.data.Vector2D
import kotlin.math.hypot

object BoardGeometry {
    const val BOARD_SIZE = 800f
    const val BOARD_PADDING = 50f
    const val PLAYABLE_MIN = BOARD_PADDING
    const val PLAYABLE_MAX = BOARD_SIZE - BOARD_PADDING // 750f

    const val STRIKER_RADIUS = 22f
    const val PUCK_RADIUS = 15.5f
    const val POCKET_RADIUS = 32f
    const val POCKET_SUCTION_RADIUS = 40f

    /** Pre-computed math constants to avoid repeated conversions. */
    const val HALF_PI = (Math.PI / 2.0).toFloat()
    const val TWO_PI = (Math.PI * 2.0).toFloat()
    const val DEG_TO_RAD = (Math.PI / 180.0).toFloat()

    const val CENTER_CIRCLE_RADIUS = 40f
    const val CENTER_SMALL_CIRCLE_RADIUS = 15f
    const val CENTER_OUTER_CIRCLE_RADIUS = 110f

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

    val POCKETS = listOf(
        Pocket(id = 0, x = PLAYABLE_MIN + 18f, y = PLAYABLE_MIN + 18f, radius = POCKET_RADIUS, name = "Top-Left"),
        Pocket(id = 1, x = PLAYABLE_MAX - 18f, y = PLAYABLE_MIN + 18f, radius = POCKET_RADIUS, name = "Top-Right"),
        Pocket(id = 2, x = PLAYABLE_MAX - 18f, y = PLAYABLE_MAX - 18f, radius = POCKET_RADIUS, name = "Bottom-Right"),
        Pocket(id = 3, x = PLAYABLE_MIN + 18f, y = PLAYABLE_MAX - 18f, radius = POCKET_RADIUS, name = "Bottom-Left")
    )

    fun getBaselineStrikerPos(fraction: Float, isBottomPlayer: Boolean = true): Vector2D {
        val clamped = fraction.coerceIn(0f, 1f)
        val x = BASELINE_START_X + clamped * BASELINE_WIDTH
        val y = if (isBottomPlayer) BASELINE_BOTTOM_Y else BASELINE_TOP_Y
        return Vector2D(x, y)
    }

    fun isOverBaselineCircle(x: Float, y: Float, isBottomPlayer: Boolean = true): Boolean {
        val baselineY = if (isBottomPlayer) BASELINE_BOTTOM_Y else BASELINE_TOP_Y
        val distLeft = hypot(x - BASELINE_START_X, y - baselineY)
        val distRight = hypot(x - BASELINE_END_X, y - baselineY)
        return distLeft <= BASELINE_CIRCLE_RADIUS + 4f || distRight <= BASELINE_CIRCLE_RADIUS + 4f
    }
}

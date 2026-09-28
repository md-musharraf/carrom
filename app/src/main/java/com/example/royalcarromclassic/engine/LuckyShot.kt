package com.example.royalcarromclassic.engine

import com.example.royalcarromclassic.data.Piece
import com.example.royalcarromclassic.data.PieceType
import com.example.royalcarromclassic.data.Vector2D
import java.util.TimeZone
import kotlin.math.hypot

/**
 * Lucky Shot, a daily mini-game: flick the striker into the lucky disc and send it into the
 * prize rings on the far half of the board. Where the disc comes to rest decides the prize.
 */
object LuckyShot {
    const val DAILY_ATTEMPTS = 3
    const val COUNTER = "lucky_shot"

    /** Paid when the disc stops outside every ring. */
    const val CONSOLATION = 10

    /** Paid when the disc drops into a pocket instead. */
    const val POCKETED = 25

    const val DISC_ID = "lucky"
    val TARGET = Vector2D(BoardGeometry.CENTER, 230f)
    val DISC_START = Vector2D(BoardGeometry.CENTER, 540f)

    /** A prize ring: a disc whose centre stops within [radius] of [TARGET] wins [prize]. */
    data class Ring(val radius: Float, val prize: Int)

    /** Innermost first. */
    val RINGS = listOf(Ring(16f, 500), Ring(38f, 250), Ring(64f, 100), Ring(92f, 50))

    fun setup(): List<Piece> = listOf(PieceFactory.createPiece(DISC_ID, PieceType.QUEEN, DISC_START.x, DISC_START.y))

    fun prizeFor(disc: Piece): Int {
        if (disc.isPocketed) return POCKETED
        val distance = hypot(disc.x - TARGET.x, disc.y - TARGET.y)
        return RINGS.firstOrNull { distance <= it.radius }?.prize ?: CONSOLATION
    }

    /** Local calendar day number of [nowMillis], so the allowance resets at the player's midnight. */
    fun dayIndex(nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        Math.floorDiv(nowMillis + zone.getOffset(nowMillis), DAY_MILLIS)

    private const val DAY_MILLIS = 86_400_000L
}

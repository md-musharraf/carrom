package com.example.royalcarromclassic.engine

import com.example.royalcarromclassic.data.Piece

/**
 * Eases discs from where the local simulation left them to authoritative positions (online play),
 * so small float differences between devices and the server resolve as a short glide, not a jump.
 */
class PositionGlide(private val durationSeconds: Float = DEFAULT_DURATION) {
    private class Move(val piece: Piece, val fromX: Float, val fromY: Float, val toX: Float, val toY: Float)

    private val moves = ArrayList<Move>()
    private var elapsed = 0f

    val isActive: Boolean get() = moves.isNotEmpty()

    /** Glides [piece] to ([x], [y]); restarts the shared timer so every move lands together. */
    fun add(piece: Piece, x: Float, y: Float) {
        moves.removeAll { it.piece === piece }
        moves += Move(piece, piece.x, piece.y, x, y)
        elapsed = 0f
    }

    fun advance(dtSeconds: Float) {
        if (moves.isEmpty()) return
        elapsed += dtSeconds
        val t = AiShotDirector.easeOutCubic((elapsed / durationSeconds).coerceIn(0f, 1f))
        for (move in moves) {
            move.piece.x = AiShotDirector.lerp(move.fromX, move.toX, t)
            move.piece.y = AiShotDirector.lerp(move.fromY, move.toY, t)
        }
        if (elapsed >= durationSeconds) moves.clear()
    }

    /** Jumps every disc to its destination (e.g. before a new shot starts). */
    fun finish() {
        for (move in moves) {
            move.piece.x = move.toX
            move.piece.y = move.toY
        }
        moves.clear()
    }

    fun cancel() = moves.clear()

    private companion object {
        const val DEFAULT_DURATION = 0.28f
    }
}

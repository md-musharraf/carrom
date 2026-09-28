package com.example.royalcarromclassic.online

import com.example.royalcarromclassic.data.Piece
import com.example.royalcarromclassic.data.PieceType
import com.example.royalcarromclassic.data.PlayerSlot
import com.example.royalcarromclassic.engine.BoardGeometry
import com.example.royalcarromclassic.engine.PieceFactory
import com.example.royalcarromclassic.engine.PositionGlide
import com.example.royalcarromclassic.engine.ShotAim
import kotlin.math.hypot

/**
 * The board as seen from [mySeat]. The server puts seat 0 at the bottom; every device shows its
 * own player at the bottom, so seat 1 sees the board rotated half a turn. The rotation is its own
 * inverse, so the same functions convert server → local and local → server.
 */
class SeatView(val mySeat: Int) {
    private val flipped = mySeat == 1

    fun x(x: Float) = if (flipped) BoardGeometry.BOARD_SIZE - x else x
    fun y(y: Float) = if (flipped) BoardGeometry.BOARD_SIZE - y else y
    fun offset(offset: Float) = if (flipped) 1f - offset else offset
    fun angle(angle: Float) = BoardGeometry.normalizeAngle(if (flipped) angle + BoardGeometry.PI_F else angle)

    fun slotOf(seat: Int): PlayerSlot = if (seat == mySeat) PlayerSlot.PLAYER1 else PlayerSlot.PLAYER2
    fun seatOf(slot: PlayerSlot): Int = if (slot == PlayerSlot.PLAYER1) mySeat else 1 - mySeat

    fun toServer(aim: ShotAim) = ShotInputDto(offset(aim.baselineOffset), angle(aim.angle), aim.power)
    fun toLocal(input: ShotInputDto) = ShotAim(offset(input.offset), angle(input.angle), input.power)

    /** Fresh local discs for [snapshot]; pocketed discs are already gone from the board. */
    fun piecesOf(snapshot: SnapshotDto): List<Piece> = snapshot.pieces.mapNotNull { dto ->
        val type = pieceType(dto.type) ?: return@mapNotNull null
        PieceFactory.createPiece(dto.id, type, x(dto.x), y(dto.y)).apply {
            if (dto.pocketed) {
                isPocketed = true
                pocketProgress = 0f
            }
        }
    }

    /**
     * Brings [local] discs to [snapshot]'s authoritative state: discs the server pocketed drop into
     * the nearest pocket, discs it kept reappear, and small position differences glide into place.
     * Returns false when the two boards hold different discs and must be rebuilt instead.
     */
    fun reconcile(local: List<Piece>, snapshot: SnapshotDto, glide: PositionGlide?): Boolean {
        if (local.size != snapshot.pieces.size) return false
        val byId = local.associateBy { it.id }
        if (snapshot.pieces.any { it.id !in byId }) return false

        for (dto in snapshot.pieces) {
            val piece = byId.getValue(dto.id)
            piece.vx = 0f
            piece.vy = 0f
            val tx = x(dto.x)
            val ty = y(dto.y)
            when {
                dto.pocketed && !piece.isPocketed -> {
                    piece.isPocketed = true
                    piece.pocketProgress = 1f
                    piece.pocketId = nearestPocket(piece.x, piece.y)
                }
                dto.pocketed -> Unit
                piece.isPocketed -> {
                    piece.isPocketed = false
                    piece.pocketProgress = 1f
                    piece.pocketId = -1
                    piece.x = tx
                    piece.y = ty
                }
                hypot(piece.x - tx, piece.y - ty) > SNAP_EPSILON -> {
                    if (glide != null) glide.add(piece, tx, ty) else {
                        piece.x = tx
                        piece.y = ty
                    }
                }
            }
        }
        return true
    }

    companion object {
        private const val SNAP_EPSILON = 0.5f

        fun pieceType(name: String): PieceType? = PieceType.entries.firstOrNull { it.name == name }

        fun nearestPocket(x: Float, y: Float): Int =
            BoardGeometry.POCKETS.minBy { hypot(it.x - x, it.y - y) }.id

        /** Up to two initials for a player's medallion, e.g. "Asha Rao" → "AR". */
        fun monogram(name: String): String {
            val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val letters = when {
                words.size >= 2 -> "${words[0].first()}${words[1].first()}"
                words.size == 1 -> words[0].take(2)
                else -> "?"
            }
            return letters.uppercase()
        }
    }
}

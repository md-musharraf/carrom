package com.example.royalcarromclassic.engine

import androidx.compose.ui.graphics.Color
import com.example.royalcarromclassic.data.Piece
import com.example.royalcarromclassic.data.PieceType
import com.example.royalcarromclassic.data.Seat
import com.example.royalcarromclassic.data.Vector2D

/**
 * Centralized factory for creating game pieces.
 * Eliminates duplicated color/property assignment across the codebase (DRY principle).
 */
object PieceFactory {

    // Canonical piece colours — single source of truth. Classic turned-wood carrom men:
    // natural boxwood whites, ebony blacks and a lacquered crimson queen.
    private val WHITE_PRIMARY = Color(0xFFE9D9B6)
    private val WHITE_BORDER = Color(0xFFA88A5E)
    private val BLACK_PRIMARY = Color(0xFF2B1F19)
    private val BLACK_BORDER = Color(0xFF0E0907)
    private val QUEEN_PRIMARY = Color(0xFFB8232C)
    private val QUEEN_BORDER = Color(0xFF63101A)
    private val STRIKER_PRIMARY = Color(0xFFF5EDDC)
    private val STRIKER_BORDER = Color(0xFFA88A5A)

    /**
     * Returns the canonical (primaryColor, borderColor) pair for a given [PieceType].
     */
    fun colorsForType(type: PieceType): Pair<Color, Color> = when (type) {
        PieceType.WHITE -> WHITE_PRIMARY to WHITE_BORDER
        PieceType.BLACK -> BLACK_PRIMARY to BLACK_BORDER
        PieceType.QUEEN -> QUEEN_PRIMARY to QUEEN_BORDER
        PieceType.STRIKER -> STRIKER_PRIMARY to STRIKER_BORDER
    }

    /**
     * Returns the canonical point value for a given [PieceType].
     */
    fun pointsForType(type: PieceType): Int = when (type) {
        PieceType.WHITE -> 10
        PieceType.BLACK -> 5
        PieceType.QUEEN -> 25
        PieceType.STRIKER -> 0
    }

    /**
     * Creates a game piece with canonical colors and properties.
     * Used by both classic cluster generation and trick shot level loading.
     */
    fun createPiece(
        id: String,
        type: PieceType,
        x: Float,
        y: Float,
        radius: Float = if (type == PieceType.STRIKER) BoardGeometry.STRIKER_RADIUS else BoardGeometry.PUCK_RADIUS,
        mass: Float = if (type == PieceType.STRIKER) BoardGeometry.STRIKER_MASS else BoardGeometry.PUCK_MASS,
        points: Int = pointsForType(type)
    ): Piece {
        val (primary, border) = colorsForType(type)
        return Piece(
            id = id,
            type = type,
            x = x,
            y = y,
            radius = radius,
            mass = mass,
            primaryColor = primary,
            borderColor = border,
            points = points
        )
    }

    /**
     * Creates a striker piece positioned on the baseline.
     */
    fun createStriker(fraction: Float = 0.5f, isBottom: Boolean = true): Piece =
        createStriker(fraction, if (isBottom) Seat.BOTTOM else Seat.TOP)

    /** Creates a striker on [seat]'s baseline; [mass] varies with the striker's weight offline. */
    fun createStriker(fraction: Float, seat: Seat, mass: Float = BoardGeometry.STRIKER_MASS): Piece {
        val pos = BoardGeometry.strikerPos(fraction, seat)
        return createPiece(
            id = "striker",
            type = PieceType.STRIKER,
            x = pos.x,
            y = pos.y,
            mass = mass
        )
    }

    /**
     * Creates pieces from trick shot level definitions.
     * Eliminates the duplicated mapIndexed+color-assignment in CarromViewModel.
     */
    fun createTrickShotPieces(levelId: Int, pieceDefs: List<Pair<PieceType, Vector2D>>): List<Piece> {
        return pieceDefs.mapIndexed { idx, (type, pos) ->
            createPiece(
                id = "trick_${levelId}_$idx",
                type = type,
                x = pos.x,
                y = pos.y
            )
        }
    }
}

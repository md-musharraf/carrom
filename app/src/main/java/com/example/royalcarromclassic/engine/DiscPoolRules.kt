package com.example.royalcarromclassic.engine

import com.example.royalcarromclassic.data.PieceType
import com.example.royalcarromclassic.data.PlayerSlot

/** Everything a Disc Pool shot changes, computed without touching game state. */
data class DiscPoolOutcome(
    val isFoul: Boolean,
    val keepsTurn: Boolean,
    val queen: QueenStatus,
    val returnQueenToCenter: Boolean,
    val queenCoveredNow: Boolean,
    /** Pocketed white and black discs to put back near the centre (penalties and illegal pots). */
    val respotWhite: Int,
    val respotBlack: Int,
    val announcement: String?
)

/**
 * Disc Pool, as played on real boards: each side owns a colour (the first player white, the
 * second black; partners share a colour in doubles) and wins by pocketing all of it.
 *
 * - Pocketing your own colour (or the queen) keeps the turn.
 * - Pocketing the other colour just helps your opponent: those discs stay down.
 * - The queen must be covered: pocket one of your own discs on the same or the next shot, or the
 *   queen returns to the centre.
 * - "Queen first": no colour may be cleared while the queen is still uncovered. A shot that would
 *   pocket either side's last disc before then is a foul and that disc comes back.
 * - Pocketing the striker is a foul: every disc it pocketed comes back, plus one of the shooter's
 *   own discs as a penalty, and the turn passes.
 */
object DiscPoolRules {
    const val DISCS_PER_COLOUR = 9

    fun colourOf(team: Int): PieceType = if (team == 0) PieceType.WHITE else PieceType.BLACK

    fun opposite(colour: PieceType): PieceType = if (colour == PieceType.WHITE) PieceType.BLACK else PieceType.WHITE

    /**
     * @param whitesLeft white discs still on the board after the shot (before any respots)
     * @param blacksLeft black discs still on the board after the shot (before any respots)
     */
    fun evaluate(
        shooter: PlayerSlot,
        colour: PieceType,
        pocketed: List<PieceType>,
        strikerPocketed: Boolean,
        queen: QueenStatus,
        whitesLeft: Int,
        blacksLeft: Int
    ): DiscPoolOutcome {
        val other = opposite(colour)
        val ownDown = pocketed.count { it == colour }
        val otherDown = pocketed.count { it == other }
        val ownLeft = if (colour == PieceType.WHITE) whitesLeft else blacksLeft
        val otherLeft = if (colour == PieceType.WHITE) blacksLeft else whitesLeft
        val queenPocketed = PieceType.QUEEN in pocketed
        val awaitingOwnCover = queen.awaitingCover && queen.pottedBy == shooter

        fun respot(own: Int, theirs: Int) =
            if (colour == PieceType.WHITE) own to theirs else theirs to own

        if (strikerPocketed) {
            val forfeitQueen = queenPocketed || awaitingOwnCover
            // Every disc pocketed on the foul returns, plus one already-pocketed disc of the shooter's.
            val ownPocketedBefore = DISCS_PER_COLOUR - ownLeft - ownDown
            val penalty = if (ownPocketedBefore > 0) 1 else 0
            val (w, b) = respot(ownDown + penalty, otherDown)
            return DiscPoolOutcome(
                isFoul = true,
                keepsTurn = false,
                queen = if (forfeitQueen) QueenStatus() else queen,
                returnQueenToCenter = forfeitQueen,
                queenCoveredNow = false,
                respotWhite = w,
                respotBlack = b,
                announcement = if (penalty > 0) "Foul! Striker pocketed · one disc returns" else "Foul! Striker pocketed"
            )
        }

        var nextQueen = queen
        var returnQueen = false
        var coveredNow = false
        var queenNote: String? = null
        when {
            queenPocketed && ownDown > 0 -> {
                nextQueen = QueenStatus(pottedBy = shooter, covered = true)
                coveredNow = true
                queenNote = "Queen pocketed and covered!"
            }
            queenPocketed -> {
                nextQueen = QueenStatus(pottedBy = shooter, awaitingCover = true)
                queenNote = "Queen pocketed — cover it with your next shot"
            }
            awaitingOwnCover && ownDown > 0 -> {
                nextQueen = queen.copy(awaitingCover = false, covered = true)
                coveredNow = true
                queenNote = "Queen covered!"
            }
            awaitingOwnCover -> {
                nextQueen = QueenStatus()
                returnQueen = true
                queenNote = "Queen not covered — returned to the centre"
            }
        }

        // Queen first: nobody's colour may be cleared while the queen is still uncovered.
        if (!nextQueen.covered) {
            val ownIllegal = if (ownLeft == 0 && ownDown > 0) 1 else 0
            val otherIllegal = if (otherLeft == 0 && otherDown > 0) 1 else 0
            if (ownIllegal + otherIllegal > 0) {
                val (w, b) = respot(ownIllegal, otherIllegal)
                val forfeitQueen = queenPocketed || awaitingOwnCover
                return DiscPoolOutcome(
                    isFoul = true,
                    keepsTurn = false,
                    queen = if (forfeitQueen) QueenStatus() else queen,
                    returnQueenToCenter = forfeitQueen,
                    queenCoveredNow = false,
                    respotWhite = w,
                    respotBlack = b,
                    announcement = "Foul! Cover the queen before the last disc"
                )
            }
        }

        return DiscPoolOutcome(
            isFoul = false,
            keepsTurn = (ownDown > 0 || queenPocketed) && !returnQueen,
            queen = nextQueen,
            returnQueenToCenter = returnQueen,
            queenCoveredNow = coveredNow,
            respotWhite = 0,
            respotBlack = 0,
            announcement = queenNote ?: if (otherDown > 0 && ownDown == 0) "That's their disc — turn passes" else null
        )
    }

    /**
     * The colour that has won once the board settles, or null while play continues. A colour
     * wins when all of it is pocketed (the queen-first rule guarantees the queen is covered by
     * then). If one shot clears both, the shooter's colour wins.
     */
    fun winningColour(whitesLeft: Int, blacksLeft: Int, shooterColour: PieceType): PieceType? = when {
        whitesLeft == 0 && blacksLeft == 0 -> shooterColour
        whitesLeft == 0 -> PieceType.WHITE
        blacksLeft == 0 -> PieceType.BLACK
        else -> null
    }
}

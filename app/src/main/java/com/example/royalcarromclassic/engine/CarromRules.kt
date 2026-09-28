package com.example.royalcarromclassic.engine

import com.example.royalcarromclassic.data.GameMode
import com.example.royalcarromclassic.data.PieceType
import com.example.royalcarromclassic.data.PlayerSlot

/** Where the queen stands in the cover sequence. */
data class QueenStatus(
    val pottedBy: PlayerSlot? = null,
    val awaitingCover: Boolean = false,
    val covered: Boolean = false
)

/** Everything a finished shot changes, computed without touching game state. */
data class ShotOutcome(
    /** Points gained (or lost, when negative) by the shooter. */
    val scoreDelta: Int,
    val isFoul: Boolean,
    val queen: QueenStatus,
    val returnQueenToCenter: Boolean,
    val queenCoveredNow: Boolean,
    val keepsTurn: Boolean,
    val announcement: String?
)

/**
 * Pure scoring and turn rules (points race with queen and cover), kept separate from the
 * ViewModel so they can be unit tested directly.
 */
object CarromRules {
    const val FOUL_PENALTY = 5
    const val QUEEN_COVER_BONUS = 25
    const val FREESTYLE_TARGET = 160
    const val BLITZ_TARGET = 120

    fun evaluateShot(
        shooter: PlayerSlot,
        pocketed: List<PieceType>,
        strikerPocketed: Boolean,
        queen: QueenStatus
    ): ShotOutcome {
        val queenPocketed = PieceType.QUEEN in pocketed
        val coverDiscs = pocketed.count { it == PieceType.WHITE || it == PieceType.BLACK }
        val pieceScore = pocketed.sumOf { if (it == PieceType.QUEEN) 0 else PieceFactory.pointsForType(it) }
        val awaitingOwnCover = queen.awaitingCover && queen.pottedBy == shooter

        if (strikerPocketed) {
            // A foul forfeits the queen: it is re-spotted if it dropped now or was awaiting cover.
            val forfeitQueen = queenPocketed || awaitingOwnCover
            return ShotOutcome(
                scoreDelta = pieceScore - FOUL_PENALTY,
                isFoul = true,
                queen = if (forfeitQueen) QueenStatus() else queen,
                returnQueenToCenter = forfeitQueen,
                queenCoveredNow = false,
                keepsTurn = false,
                announcement = if (forfeitQueen) "Foul! Striker pocketed (−$FOUL_PENALTY) · Queen returned"
                else "Foul! Striker pocketed (−$FOUL_PENALTY)"
            )
        }

        return when {
            queenPocketed && coverDiscs > 0 -> ShotOutcome(
                scoreDelta = pieceScore + QUEEN_COVER_BONUS,
                isFoul = false,
                queen = QueenStatus(pottedBy = shooter, covered = true),
                returnQueenToCenter = false,
                queenCoveredNow = true,
                keepsTurn = true,
                announcement = "Queen pocketed and covered! +$QUEEN_COVER_BONUS"
            )

            queenPocketed -> ShotOutcome(
                scoreDelta = pieceScore,
                isFoul = false,
                queen = QueenStatus(pottedBy = shooter, awaitingCover = true),
                returnQueenToCenter = false,
                queenCoveredNow = false,
                keepsTurn = true,
                announcement = "Queen pocketed — cover it with your next shot"
            )

            awaitingOwnCover && coverDiscs > 0 -> ShotOutcome(
                scoreDelta = pieceScore + QUEEN_COVER_BONUS,
                isFoul = false,
                queen = queen.copy(awaitingCover = false, covered = true),
                returnQueenToCenter = false,
                queenCoveredNow = true,
                keepsTurn = true,
                announcement = "Queen covered! +$QUEEN_COVER_BONUS"
            )

            awaitingOwnCover -> ShotOutcome(
                scoreDelta = pieceScore,
                isFoul = false,
                queen = QueenStatus(),
                returnQueenToCenter = true,
                queenCoveredNow = false,
                keepsTurn = false,
                announcement = "Queen not covered — returned to the centre"
            )

            else -> ShotOutcome(
                scoreDelta = pieceScore,
                isFoul = false,
                queen = queen,
                returnQueenToCenter = false,
                queenCoveredNow = false,
                keepsTurn = pocketed.isNotEmpty(),
                announcement = null
            )
        }
    }

    /** Who shoots next. Solo modes always return to player 1. */
    fun nextShooter(mode: GameMode, shooter: PlayerSlot, keepsTurn: Boolean): PlayerSlot = when {
        isSolo(mode) -> PlayerSlot.PLAYER1
        keepsTurn -> shooter
        else -> shooter.opponent
    }

    /** Modes with nobody sitting opposite. */
    fun isSolo(mode: GameMode): Boolean =
        mode == GameMode.PRACTICE || mode == GameMode.TRICK_SHOTS || mode == GameMode.LUCKY_SHOT

    /** Points that end the match early, or null when only clearing the board ends it. */
    fun targetScore(mode: GameMode): Int? = when (mode) {
        GameMode.FREESTYLE -> FREESTYLE_TARGET
        GameMode.BLITZ -> BLITZ_TARGET
        else -> null
    }

    /** The winner once the match is decided, or null while play continues. Ties go to player 1. */
    fun winnerOrNull(mode: GameMode, discsLeft: Int, player1Score: Int, player2Score: Int): PlayerSlot? {
        val target = targetScore(mode)
        val decided = discsLeft == 0 || (target != null && (player1Score >= target || player2Score >= target))
        if (!decided) return null
        return if (player1Score >= player2Score) PlayerSlot.PLAYER1 else PlayerSlot.PLAYER2
    }
}

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
    const val DICE_TARGET = 200

    fun evaluateShot(
        shooter: PlayerSlot,
        pocketed: List<PieceType>,
        strikerPocketed: Boolean,
        queen: QueenStatus,
        /** Dice Carrom's Double face: points gained this shot are multiplied (penalties are not). */
        scoreMultiplier: Int = 1,
        /** Dice Carrom's Bonus Turn face: the shooter keeps the turn unless they foul. */
        bonusTurn: Boolean = false
    ): ShotOutcome {
        val outcome = evaluatePlainShot(shooter, pocketed, strikerPocketed, queen, scoreMultiplier.coerceAtLeast(1))
        return if (bonusTurn && !outcome.isFoul && !outcome.keepsTurn) {
            outcome.copy(keepsTurn = true, announcement = outcome.announcement ?: "Bonus turn!")
        } else {
            outcome
        }
    }

    private fun evaluatePlainShot(
        shooter: PlayerSlot,
        pocketed: List<PieceType>,
        strikerPocketed: Boolean,
        queen: QueenStatus,
        multiplier: Int
    ): ShotOutcome {
        val queenPocketed = PieceType.QUEEN in pocketed
        val coverDiscs = pocketed.count { it == PieceType.WHITE || it == PieceType.BLACK }
        val pieceScore = multiplier * pocketed.sumOf { if (it == PieceType.QUEEN) 0 else PieceFactory.pointsForType(it) }
        val coverBonus = multiplier * QUEEN_COVER_BONUS
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
                scoreDelta = pieceScore + coverBonus,
                isFoul = false,
                queen = QueenStatus(pottedBy = shooter, covered = true),
                returnQueenToCenter = false,
                queenCoveredNow = true,
                keepsTurn = true,
                announcement = "Queen pocketed and covered! +$coverBonus"
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
                scoreDelta = pieceScore + coverBonus,
                isFoul = false,
                queen = queen.copy(awaitingCover = false, covered = true),
                returnQueenToCenter = false,
                queenCoveredNow = true,
                keepsTurn = true,
                announcement = "Queen covered! +$coverBonus"
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

    /** Who shoots next: play passes to the right around [playerCount] seats. Solo modes stay with player 1. */
    fun nextShooter(mode: GameMode, shooter: PlayerSlot, keepsTurn: Boolean, playerCount: Int = 2): PlayerSlot = when {
        isSolo(mode) -> PlayerSlot.PLAYER1
        keepsTurn -> shooter
        else -> shooter.next(playerCount)
    }

    /** Modes with nobody sitting opposite. */
    fun isSolo(mode: GameMode): Boolean =
        mode == GameMode.PRACTICE || mode == GameMode.TRICK_SHOTS || mode == GameMode.LUCKY_SHOT ||
            mode == GameMode.TIME_ATTACK

    /** Points that end the match early, or null when only clearing the board ends it. */
    fun targetScore(mode: GameMode): Int? = when (mode) {
        GameMode.FREESTYLE -> FREESTYLE_TARGET
        GameMode.BLITZ -> BLITZ_TARGET
        GameMode.DICE -> DICE_TARGET
        else -> null
    }

    /** The winner once the match is decided, or null while play continues. Ties go to player 1. */
    fun winnerOrNull(mode: GameMode, discsLeft: Int, player1Score: Int, player2Score: Int): PlayerSlot? =
        winnerOrNull(mode, discsLeft, listOf(player1Score, player2Score))

    /**
     * The winner among any number of players once the match is decided, or null while play
     * continues. In doubles ([teams] gives each player's team, 0 or 1) partners pool their points
     * and the winner reported is the winning team's top scorer. Ties go to the earlier seat.
     */
    fun winnerOrNull(mode: GameMode, discsLeft: Int, scores: List<Int>, teams: List<Int>? = null): PlayerSlot? {
        if (scores.isEmpty()) return null
        val target = targetScore(mode)
        val totals = if (teams != null) scores.indices.map { i -> scores.indices.filter { teams[it] == teams[i] }.sumOf { scores[it] } } else scores
        val decided = discsLeft == 0 || (target != null && totals.any { it >= target })
        if (!decided) return null
        val best = totals.max()
        val contenders = scores.indices.filter { totals[it] == best }
        val first = contenders.first()
        val winner = if (teams != null) {
            // The earliest seat's team takes a tie; its top scorer (earliest on a tie) is reported.
            contenders.filter { teams[it] == teams[first] }.maxWith(compareBy<Int> { scores[it] }.thenByDescending { it })
        } else {
            first
        }
        return PlayerSlot.of(winner)
    }
}

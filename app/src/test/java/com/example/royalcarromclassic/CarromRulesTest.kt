package com.example.royalcarromclassic

import com.example.royalcarromclassic.data.GameMode
import com.example.royalcarromclassic.data.PieceType.BLACK
import com.example.royalcarromclassic.data.PieceType.QUEEN
import com.example.royalcarromclassic.data.PieceType.WHITE
import com.example.royalcarromclassic.data.PlayerSlot.PLAYER1
import com.example.royalcarromclassic.data.PlayerSlot.PLAYER2
import com.example.royalcarromclassic.engine.CarromRules
import com.example.royalcarromclassic.engine.QueenStatus
import org.junit.Assert.*
import org.junit.Test

class CarromRulesTest {

    @Test
    fun pocketingDiscsScoresAndKeepsTheTurn() {
        val outcome = CarromRules.evaluateShot(PLAYER1, listOf(WHITE, BLACK), strikerPocketed = false, queen = QueenStatus())
        assertEquals(15, outcome.scoreDelta)
        assertTrue(outcome.keepsTurn)
        assertFalse(outcome.isFoul)
    }

    @Test
    fun emptyShotPassesTheTurn() {
        val outcome = CarromRules.evaluateShot(PLAYER1, emptyList(), strikerPocketed = false, queen = QueenStatus())
        assertEquals(0, outcome.scoreDelta)
        assertFalse(outcome.keepsTurn)
        assertNull(outcome.announcement)
    }

    @Test
    fun pocketedStrikerIsAFoulThatEndsTheTurn() {
        val outcome = CarromRules.evaluateShot(PLAYER2, listOf(WHITE), strikerPocketed = true, queen = QueenStatus())
        assertTrue(outcome.isFoul)
        assertEquals(10 - CarromRules.FOUL_PENALTY, outcome.scoreDelta)
        assertFalse(outcome.keepsTurn)
    }

    @Test
    fun queenAloneMustBeCoveredNextShot() {
        val outcome = CarromRules.evaluateShot(PLAYER1, listOf(QUEEN), strikerPocketed = false, queen = QueenStatus())
        assertEquals(QueenStatus(pottedBy = PLAYER1, awaitingCover = true), outcome.queen)
        assertEquals(0, outcome.scoreDelta)
        assertTrue(outcome.keepsTurn)
    }

    @Test
    fun queenAndCoverInTheSameShotAreCreditedTogether() {
        val outcome = CarromRules.evaluateShot(PLAYER1, listOf(QUEEN, WHITE), strikerPocketed = false, queen = QueenStatus())
        assertEquals(10 + CarromRules.QUEEN_COVER_BONUS, outcome.scoreDelta)
        assertTrue(outcome.queen.covered)
        assertTrue(outcome.queenCoveredNow)
    }

    @Test
    fun coverOnTheFollowingShotAwardsTheBonus() {
        val awaiting = QueenStatus(pottedBy = PLAYER1, awaitingCover = true)
        val outcome = CarromRules.evaluateShot(PLAYER1, listOf(BLACK), strikerPocketed = false, queen = awaiting)
        assertEquals(5 + CarromRules.QUEEN_COVER_BONUS, outcome.scoreDelta)
        assertTrue(outcome.queen.covered)
        assertFalse(outcome.queen.awaitingCover)
    }

    @Test
    fun missedCoverReturnsTheQueenAndPassesTheTurn() {
        val awaiting = QueenStatus(pottedBy = PLAYER1, awaitingCover = true)
        val outcome = CarromRules.evaluateShot(PLAYER1, emptyList(), strikerPocketed = false, queen = awaiting)
        assertTrue(outcome.returnQueenToCenter)
        assertEquals(QueenStatus(), outcome.queen)
        assertFalse(outcome.keepsTurn)
    }

    @Test
    fun foulForfeitsAQueenAwaitingCover() {
        val awaiting = QueenStatus(pottedBy = PLAYER1, awaitingCover = true)
        val outcome = CarromRules.evaluateShot(PLAYER1, listOf(WHITE), strikerPocketed = true, queen = awaiting)
        assertTrue(outcome.returnQueenToCenter)
        assertEquals(QueenStatus(), outcome.queen)
    }

    @Test
    fun soloModesAlwaysReturnToPlayerOne() {
        assertEquals(PLAYER1, CarromRules.nextShooter(GameMode.PRACTICE, PLAYER1, keepsTurn = false))
        assertEquals(PLAYER1, CarromRules.nextShooter(GameMode.TRICK_SHOTS, PLAYER1, keepsTurn = false))
        assertEquals(PLAYER2, CarromRules.nextShooter(GameMode.VS_AI, PLAYER1, keepsTurn = false))
        assertEquals(PLAYER2, CarromRules.nextShooter(GameMode.PASS_AND_PLAY, PLAYER2, keepsTurn = true))
    }

    @Test
    fun winnerIsDecidedFromTheFinalScores() {
        assertNull(CarromRules.winnerOrNull(GameMode.CLASSIC, discsLeft = 3, player1Score = 90, player2Score = 40))
        assertEquals(PLAYER2, CarromRules.winnerOrNull(GameMode.CLASSIC, discsLeft = 0, player1Score = 60, player2Score = 85))
        assertEquals(PLAYER1, CarromRules.winnerOrNull(GameMode.CLASSIC, discsLeft = 0, player1Score = 70, player2Score = 70))
        assertEquals(
            PLAYER2,
            CarromRules.winnerOrNull(GameMode.FREESTYLE, discsLeft = 5, player1Score = 100, player2Score = CarromRules.FREESTYLE_TARGET)
        )
    }
}

package com.example.royalcarromclassic

import android.app.Application
import com.example.royalcarromclassic.data.PlayerSlot
import com.example.royalcarromclassic.data.TurnState
import com.example.royalcarromclassic.engine.BoardGeometry
import com.example.royalcarromclassic.ui.CarromViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Drives the real ViewModel frame by frame, exactly as the UI's vsync loop does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameLoopTest {

    private lateinit var viewModel: CarromViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = CarromViewModel(Application(), MockGameRepository(), MockAudioEngine(), MockHapticEngine())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Advances at 60 fps until [until] holds; returns the simulated seconds taken. */
    private fun runFrames(maxSeconds: Float, until: () -> Boolean): Float {
        var t = 0f
        while (t < maxSeconds && !until()) {
            viewModel.advanceFrame(FRAME)
            t += FRAME
        }
        return t
    }

    @Test
    fun frameLoopGoesIdleOnceNothingAnimates() {
        assertTrue("A new turn animates the striker in", viewModel.needsFrames.value)
        val elapsed = runFrames(2f) { !viewModel.needsFrames.value }
        assertFalse(viewModel.needsFrames.value)
        assertTrue("Idle within a second, took $elapsed s", elapsed < 1f)
    }

    @Test
    fun missedShotSettlesAndHandsTheTurnToTheBot() {
        viewModel.setStrikerAim(0f, BoardGeometry.MIN_POWER) // Along the baseline: touches nothing.
        viewModel.executeShot()
        assertEquals(TurnState.MOVING, viewModel.gameState.value.turnState)
        assertTrue(viewModel.needsFrames.value)

        runFrames(15f) { viewModel.gameState.value.turnState != TurnState.MOVING }
        val state = viewModel.gameState.value
        assertEquals(PlayerSlot.PLAYER2, state.currentTurn)
        assertTrue(state.isAiTurn)
        assertEquals(0, state.player1.score)

        val botTime = runFrames(6f) { viewModel.gameState.value.turnState == TurnState.MOVING }
        assertEquals("The bot takes its shot", TurnState.MOVING, viewModel.gameState.value.turnState)
        assertTrue("Bot choreography should take a moment, took $botTime s", botTime in 1f..4f)

        runFrames(15f) { viewModel.gameState.value.turnState != TurnState.MOVING }
        assertNotEquals(TurnState.MOVING, viewModel.gameState.value.turnState)
        assertNoOverlaps()
    }

    @Test
    fun predictedPotDropsTheDiscAndSolvesTheTrickShot() {
        viewModel.startTrickShotLevel(5)
        val striker = viewModel.striker.value!!
        val target = viewModel.pieces.value.single()
        val pocket = BoardGeometry.POCKETS[1]

        // Ghost-ball aim: strike the point behind the disc on the disc → pocket line.
        val dx = pocket.x - target.x
        val dy = pocket.y - target.y
        val len = hypot(dx, dy)
        val gx = target.x - dx / len * (target.radius + striker.radius)
        val gy = target.y - dy / len * (target.radius + striker.radius)
        viewModel.setStrikerAim(atan2(gy - striker.y, gx - striker.x), 85f)

        assertEquals("Aim guide predicts the pot", pocket.id, viewModel.aimPreview.value!!.targetPocketId)

        viewModel.executeShot()
        assertEquals(1, viewModel.gameState.value.trickShot!!.shotsTaken)
        runFrames(15f) { viewModel.gameState.value.isGameOver }

        val state = viewModel.gameState.value
        assertTrue(state.isGameOver)
        assertEquals(PlayerSlot.PLAYER1, state.winner)
        assertEquals(0, viewModel.boardSummary.value.whites)
        assertEquals(pocket.id, target.pocketId)
    }

    @Test
    fun restartReplaysTheCurrentTrickShotLevel() {
        viewModel.startTrickShotLevel(3)
        viewModel.setStrikerAim(0f, BoardGeometry.MIN_POWER)
        viewModel.executeShot()
        runFrames(15f) { viewModel.gameState.value.isGameOver }

        viewModel.restartMatch()
        val state = viewModel.gameState.value
        assertEquals(3, state.trickShot?.levelId)
        assertEquals(0, state.trickShot?.shotsTaken)
        assertFalse(state.isGameOver)
        assertEquals(2, viewModel.pieces.value.size)
    }

    private fun assertNoOverlaps() {
        val live = viewModel.pieces.value.filter { !it.isPocketed }
        for (i in live.indices) for (j in i + 1 until live.size) {
            val a = live[i]
            val b = live[j]
            val gap = hypot(a.x - b.x, a.y - b.y) - (a.radius + b.radius)
            assertTrue("${a.id} overlaps ${b.id} by ${-gap}", gap > -0.5f)
        }
    }

    private companion object {
        const val FRAME = 1f / 60f
    }
}

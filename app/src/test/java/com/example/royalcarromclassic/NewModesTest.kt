package com.example.royalcarromclassic

import android.app.Application
import com.example.royalcarromclassic.data.GameMode
import com.example.royalcarromclassic.data.PlayerSlot
import com.example.royalcarromclassic.data.TurnState
import com.example.royalcarromclassic.engine.BoardGeometry
import com.example.royalcarromclassic.engine.CarromPhysicsEngine
import com.example.royalcarromclassic.engine.CarromRules
import com.example.royalcarromclassic.engine.DailySpin
import com.example.royalcarromclassic.engine.LuckyShot
import com.example.royalcarromclassic.engine.PieceFactory
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
import java.util.TimeZone
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class NewModesTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = MockGameRepository()
    private var now = 1_700_000_000_000L
    private lateinit var viewModel: CarromViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = CarromViewModel(Application(), repository, MockAudioEngine(), MockHapticEngine(), null) { now }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.gameState.value

    private fun runFrames(maxSeconds: Float, until: () -> Boolean) {
        var t = 0f
        while (t < maxSeconds && !until()) {
            viewModel.advanceFrame(FRAME)
            t += FRAME
        }
    }

    /** Where the lucky disc stops after a straight shot at [power]. */
    private fun luckyShotAt(power: Float): Int {
        val pieces = LuckyShot.setup()
        val striker = PieceFactory.createStriker(0.5f, isBottom = true).apply {
            vy = -(power / 100f) * CarromPhysicsEngine.MAX_STRIKE_SPEED
        }
        var t = 0f
        while (t < 15f && CarromPhysicsEngine.updatePhysics(pieces, striker, FRAME)) t += FRAME
        return LuckyShot.prizeFor(pieces.first())
    }

    @Test
    fun everyLuckyShotPrizeCanBeWonWithTheRightPower() {
        val prizes = (40..200).map { luckyShotAt(it / 2f) }.toSet()
        for (ring in LuckyShot.RINGS) assertTrue("No power wins ${ring.prize}: $prizes", ring.prize in prizes)
        assertTrue("A soft shot misses the rings", LuckyShot.CONSOLATION in prizes)
    }

    @Test
    fun luckyShotGivesThreeAttemptsADay() {
        viewModel.startNewGame(GameMode.LUCKY_SHOT)
        assertEquals(GameMode.LUCKY_SHOT, state.mode)
        assertEquals(3, state.luckyShot!!.attemptsLeft)
        val coinsBefore = viewModel.playerStats.value.coins

        repeat(3) { attempt ->
            viewModel.setStrikerAim(-BoardGeometry.HALF_PI, 30f)
            viewModel.executeShot()
            runFrames(15f) { state.turnState != TurnState.MOVING || state.isGameOver }
            assertEquals(2 - attempt, state.luckyShot!!.attemptsLeft)
            if (attempt < 2) {
                dispatcher.scheduler.advanceTimeBy(2_000) // The pause before the next attempt.
                dispatcher.scheduler.runCurrent()
                runFrames(15f) { state.turnState != TurnState.MOVING }
                assertEquals(TurnState.PLACING_STRIKER, state.turnState)
            }
        }
        assertTrue(state.isGameOver)
        val won = state.luckyShot!!.coinsWon
        assertTrue(won >= 3 * LuckyShot.CONSOLATION)
        assertEquals(coinsBefore + won, viewModel.playerStats.value.coins)

        // No more today...
        viewModel.startNewGame(GameMode.VS_AI)
        viewModel.startLuckyShot()
        assertEquals(GameMode.VS_AI, state.mode)
        assertEquals(0, viewModel.luckyShotsLeft())

        // ...but tomorrow brings three more.
        now += 86_400_000L
        assertEquals(LuckyShot.DAILY_ATTEMPTS, viewModel.luckyShotsLeft())
    }

    @Test
    fun blitzPassesTheTurnWhenTheShotClockRunsOut() {
        viewModel.startNewGame(GameMode.BLITZ)
        val clock = state.turnClock!!
        assertEquals(now + 10_000, clock.deadlineMillis)
        assertEquals(PlayerSlot.PLAYER1, state.currentTurn)

        dispatcher.scheduler.advanceTimeBy(10_001)
        dispatcher.scheduler.runCurrent()
        assertEquals(PlayerSlot.PLAYER2, state.currentTurn)
        assertTrue(state.isAiTurn)
        assertNull("The bot plays without a clock", state.turnClock)
    }

    @Test
    fun shootingStopsTheBlitzClock() {
        viewModel.startNewGame(GameMode.BLITZ)
        viewModel.setStrikerAim(0f, BoardGeometry.MIN_POWER)
        viewModel.executeShot()
        assertNull(state.turnClock)
        dispatcher.scheduler.advanceTimeBy(10_001)
        dispatcher.scheduler.runCurrent()
        assertEquals("The expired clock no longer passes the turn", TurnState.MOVING, state.turnState)
    }

    @Test
    fun theWheelSpinsOncePerDay() {
        assertTrue(viewModel.canSpinToday())
        val coins = viewModel.playerStats.value.coins
        val index = viewModel.spinDailyWheel()!!
        assertEquals(coins + DailySpin.PRIZES[index], viewModel.playerStats.value.coins)
        assertFalse(viewModel.canSpinToday())
        assertNull(viewModel.spinDailyWheel())

        now += 86_400_000L
        assertTrue(viewModel.canSpinToday())
    }

    @Test
    fun wheelOddsFavourSmallPrizes() {
        val random = Random(42)
        val draws = List(20_000) { DailySpin.draw(random) }
        val jackpot = DailySpin.PRIZES.indexOf(DailySpin.PRIZES.max())
        val jackpotShare = draws.count { it == jackpot } / 20_000f
        assertTrue("Jackpot share $jackpotShare", jackpotShare in 0.002f..0.02f)
        assertTrue(draws.all { it in DailySpin.PRIZES.indices })
    }

    @Test
    fun blitzAndLuckyShotRules() {
        assertEquals(PlayerSlot.PLAYER1, CarromRules.winnerOrNull(GameMode.BLITZ, 9, CarromRules.BLITZ_TARGET, 50))
        assertNull(CarromRules.winnerOrNull(GameMode.BLITZ, 9, CarromRules.BLITZ_TARGET - 5, 50))
        assertEquals(PlayerSlot.PLAYER1, CarromRules.nextShooter(GameMode.LUCKY_SHOT, PlayerSlot.PLAYER1, keepsTurn = false))
        assertTrue(CarromRules.isSolo(GameMode.LUCKY_SHOT))
        assertFalse(CarromRules.isSolo(GameMode.ONLINE))
    }

    @Test
    fun luckyShotDaysFollowTheLocalCalendar() {
        val india = TimeZone.getTimeZone("Asia/Kolkata")
        // 20:00 UTC is already 01:30 the next day in India.
        val evening = 1_700_000_000_000L - (1_700_000_000_000L % 86_400_000L) + 20 * 3_600_000L
        assertEquals(LuckyShot.dayIndex(evening, TimeZone.getTimeZone("UTC")) + 1, LuckyShot.dayIndex(evening, india))
    }

    private companion object {
        const val FRAME = 1f / 60f
    }
}

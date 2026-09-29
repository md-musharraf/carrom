package com.example.royalcarromclassic

import android.app.Application
import com.example.royalcarromclassic.data.*
import com.example.royalcarromclassic.engine.BoardGeometry
import com.example.royalcarromclassic.engine.CarromPhysicsEngine
import com.example.royalcarromclassic.engine.CarromPhysicsEngine.PhysicsTuning
import com.example.royalcarromclassic.engine.CarromRules
import com.example.royalcarromclassic.engine.DiscPoolRules
import com.example.royalcarromclassic.engine.PieceFactory
import com.example.royalcarromclassic.engine.Powers
import com.example.royalcarromclassic.engine.QueenStatus
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
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class PowersAndModesTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = MockGameRepository()
    private var now = 1_700_000_000_000L
    private lateinit var viewModel: CarromViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = CarromViewModel(Application(), repository, MockAudioEngine(), MockHapticEngine(), null, Random(11)) { now }
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

    private fun elapse(millis: Long) {
        now += millis
        dispatcher.scheduler.advanceTimeBy(millis)
        dispatcher.scheduler.runCurrent()
    }

    /** A soft shot along the baseline that pockets nothing and fouls nothing. */
    private fun missShot() {
        val seat = state.currentSeat
        viewModel.setStrikerAim(BoardGeometry.forwardAngle(seat) + BoardGeometry.HALF_PI, BoardGeometry.MIN_POWER)
        viewModel.executeShot()
        assertEquals(TurnState.MOVING, state.turnState)
        runFrames(15f) { state.turnState != TurnState.MOVING }
    }

    // region Party play

    @Test
    fun fourPlayersTakeTurnsAroundTheTable() {
        val humans = List(4) { SeatSetup("", false) }
        viewModel.startMatch(MatchConfig(GameMode.PASS_AND_PLAY, humans))
        assertEquals(4, state.playerCount)
        assertEquals(listOf(Seat.BOTTOM, Seat.RIGHT, Seat.TOP, Seat.LEFT), state.seats)
        assertEquals("Player 3", state.players[2].name)

        val expected = listOf(PlayerSlot.PLAYER2, PlayerSlot.PLAYER3, PlayerSlot.PLAYER4, PlayerSlot.PLAYER1)
        for (next in expected) {
            missShot()
            assertEquals(next, state.currentTurn)
            val striker = viewModel.striker.value!!
            val spot = BoardGeometry.strikerPos(0.5f, state.currentSeat)
            assertEquals(spot.x, striker.x, 0.01f)
            assertEquals(spot.y, striker.y, 0.01f)
        }
    }

    @Test
    fun aBotCanPlayFromTheSideSeat() {
        val seats = listOf(SeatSetup("Asha", false), SeatSetup("", true), SeatSetup("", true))
        viewModel.startMatch(MatchConfig(GameMode.PASS_AND_PLAY, seats, AIDifficulty.HARD))
        assertEquals("Asha", state.player1.name)
        assertEquals(listOf(Seat.BOTTOM, Seat.RIGHT, Seat.LEFT), state.seats)
        missShot()
        assertEquals(PlayerSlot.PLAYER2, state.currentTurn)
        assertTrue(state.isAiTurn)
        assertFalse(state.canAim)
        runFrames(6f) { state.turnState == TurnState.MOVING }
        assertEquals("The right-seat bot fired", TurnState.MOVING, state.turnState)
        assertTrue("It shot leftwards, into the board", viewModel.striker.value!!.vx < 0f)
    }

    @Test
    fun doublesPartnersPoolTheirPoints() {
        val teams = listOf(0, 1, 0, 1)
        assertEquals(PlayerSlot.PLAYER2, CarromRules.winnerOrNull(GameMode.CLASSIC, 0, listOf(30, 25, 10, 20), teams))
        assertEquals(PlayerSlot.PLAYER1, CarromRules.winnerOrNull(GameMode.CLASSIC, 0, listOf(30, 25, 20, 20), teams))
        assertNull(CarromRules.winnerOrNull(GameMode.CLASSIC, 3, listOf(30, 25, 20, 20), teams))
        viewModel.startMatch(MatchConfig(GameMode.PASS_AND_PLAY, List(4) { SeatSetup("", false) }, doubles = true))
        assertTrue(state.isDoubles)
        assertEquals(listOf(0, 1, 0, 1), state.players.map { it.team })
    }

    @Test
    fun turnsRotateAmongThreeAndLandBackOnTheShooterWhenTheyScore() {
        assertEquals(PlayerSlot.PLAYER3, CarromRules.nextShooter(GameMode.CLASSIC, PlayerSlot.PLAYER2, keepsTurn = false, playerCount = 3))
        assertEquals(PlayerSlot.PLAYER1, CarromRules.nextShooter(GameMode.CLASSIC, PlayerSlot.PLAYER3, keepsTurn = false, playerCount = 3))
        assertEquals(PlayerSlot.PLAYER3, CarromRules.nextShooter(GameMode.CLASSIC, PlayerSlot.PLAYER3, keepsTurn = true, playerCount = 3))
    }

    // endregion

    // region Disc Pool

    private fun discPool(
        colour: PieceType,
        pocketed: List<PieceType>,
        whites: Int,
        blacks: Int,
        striker: Boolean = false,
        queen: QueenStatus = QueenStatus()
    ) = DiscPoolRules.evaluate(PlayerSlot.PLAYER1, colour, pocketed, striker, queen, whites, blacks)

    @Test
    fun discPoolRewardsYourOwnColourOnly() {
        val own = discPool(PieceType.WHITE, listOf(PieceType.WHITE), whites = 8, blacks = 9)
        assertTrue(own.keepsTurn)
        assertFalse(own.isFoul)
        val theirs = discPool(PieceType.WHITE, listOf(PieceType.BLACK), whites = 9, blacks = 8)
        assertFalse(theirs.keepsTurn)
        assertFalse(theirs.isFoul)
        assertEquals(0, theirs.respotBlack)
    }

    @Test
    fun discPoolNeedsTheQueenCoveredBeforeTheLastDisc() {
        val early = discPool(PieceType.WHITE, listOf(PieceType.WHITE), whites = 0, blacks = 5)
        assertTrue(early.isFoul)
        assertEquals(1, early.respotWhite)
        val covered = discPool(PieceType.WHITE, listOf(PieceType.WHITE), whites = 0, blacks = 5, queen = QueenStatus(PlayerSlot.PLAYER2, covered = true))
        assertFalse(covered.isFoul)
        assertEquals(PieceType.WHITE, DiscPoolRules.winningColour(0, 5, PieceType.WHITE))
        // Queen and last disc together: covered on the spot, and that wins.
        val together = discPool(PieceType.BLACK, listOf(PieceType.QUEEN, PieceType.BLACK), whites = 4, blacks = 0)
        assertFalse(together.isFoul)
        assertTrue(together.queenCoveredNow)
    }

    @Test
    fun aDiscPoolStrikerFoulReturnsADisc() {
        val foul = discPool(PieceType.BLACK, listOf(PieceType.BLACK, PieceType.WHITE), whites = 6, blacks = 5, striker = true)
        assertTrue(foul.isFoul)
        assertFalse(foul.keepsTurn)
        assertEquals(2, foul.respotBlack) // The one just pocketed plus a penalty disc.
        assertEquals(1, foul.respotWhite)
    }

    @Test
    fun discPoolAssignsColoursAndCountsPocketedDiscs() {
        viewModel.startNewGame(GameMode.DISC_POOL)
        assertEquals(PieceType.WHITE, state.player1.assignedColor)
        assertEquals(PieceType.BLACK, state.player2.assignedColor)
        assertTrue(state.player2.isBot)
        assertFalse("Two players are not doubles", state.isDoubles)
        // Sink a white by hand and let a shot resolve: player 1's count goes up.
        val white = viewModel.pieces.value.first { it.type == PieceType.WHITE }
        white.isPocketed = true
        white.pocketProgress = 0f
        missShot()
        assertEquals(1, state.player1.score)
        assertEquals(0, state.player2.score)
    }

    // endregion

    // region Dice

    @Test
    fun diceCarromWaitsForARoll() {
        viewModel.startNewGame(GameMode.DICE)
        assertTrue(state.needsRoll)
        assertFalse(state.canAim)
        viewModel.executeShot()
        assertNotEquals("No shot before rolling", TurnState.MOVING, state.turnState)

        viewModel.rollDice()
        assertTrue(state.dice!!.rolling)
        elapse(1_000)
        val face = state.dice!!.face
        assertNotNull(face)
        assertFalse(state.needsRoll)
        assertTrue(state.canAim)
        assertEquals(1, state.dice!!.rollId)
    }

    @Test
    fun theBotRollsItsOwnDieAndShoots() {
        viewModel.startNewGame(GameMode.DICE)
        viewModel.rollDice()
        elapse(1_000)
        // A Bonus Turn keeps the shot with us; roll again until the turn passes.
        var guard = 0
        while (state.currentTurn == PlayerSlot.PLAYER1 && guard++ < 6) {
            if (state.needsRoll) {
                viewModel.rollDice()
                elapse(1_000)
            }
            missShot()
        }
        assertEquals(PlayerSlot.PLAYER2, state.currentTurn)
        assertTrue(state.needsRoll)
        elapse(2_000) // The bot rolls, reads its face, then plans.
        assertNotNull(state.dice!!.face)
        runFrames(6f) { state.turnState == TurnState.MOVING }
        assertEquals(TurnState.MOVING, state.turnState)
    }

    @Test
    fun diceFacesChangeScoringAndTurns() {
        val double = CarromRules.evaluateShot(PlayerSlot.PLAYER1, listOf(PieceType.WHITE), false, QueenStatus(), scoreMultiplier = 2)
        assertEquals(20, double.scoreDelta)
        val golden = CarromRules.evaluateShot(PlayerSlot.PLAYER1, listOf(PieceType.WHITE), false, QueenStatus(), scoreMultiplier = 3)
        assertEquals(30, golden.scoreDelta)
        val foul = CarromRules.evaluateShot(PlayerSlot.PLAYER1, listOf(PieceType.BLACK), true, QueenStatus(), scoreMultiplier = 2)
        assertEquals("Penalties are not doubled", 10 - CarromRules.FOUL_PENALTY, foul.scoreDelta)

        val bonus = CarromRules.evaluateShot(PlayerSlot.PLAYER1, emptyList(), false, QueenStatus(), bonusTurn = true)
        assertTrue(bonus.keepsTurn)
        val bonusFoul = CarromRules.evaluateShot(PlayerSlot.PLAYER1, emptyList(), true, QueenStatus(), bonusTurn = true)
        assertFalse("A foul still passes the turn", bonusFoul.keepsTurn)

        assertEquals(3, Powers.scoreMultiplier(DiceFace.DOUBLE, DicePower.GOLDEN_DOUBLE))
        assertEquals(2, Powers.scoreMultiplier(DiceFace.DOUBLE, DicePower.FAIR))
        assertEquals(1, Powers.scoreMultiplier(DiceFace.POWER_SURGE, DicePower.GOLDEN_DOUBLE))
    }

    @Test
    fun theNoBlanksDieNeverRollsSteadyAndTheFairDieRollsEverything() {
        val random = Random(5)
        val crimson = List(600) { Powers.roll(DicePower.NO_BLANKS, random) }.toSet()
        assertFalse(DiceFace.STEADY in crimson)
        assertEquals(5, crimson.size)
        assertEquals(DiceFace.entries.toSet(), List(600) { Powers.roll(DicePower.FAIR, random) }.toSet())
    }

    // endregion

    // region Powers

    /** How far a lone disc glides from a push on [tuning]. */
    private fun glideDistance(tuning: PhysicsTuning): Float {
        // Gentle enough never to reach a cushion, so the displacement is the whole glide.
        val disc = PieceFactory.createPiece("d", PieceType.WHITE, 400f, 700f).apply { vy = -4.5f }
        var t = 0f
        while (t < 15f && CarromPhysicsEngine.updatePhysics(listOf(disc), null, FRAME, tuning = tuning)) t += FRAME
        assertTrue("stayed clear of the cushion", disc.y > BoardGeometry.PLAYABLE_MIN + disc.radius + 1f)
        return 700f - disc.y
    }

    @Test
    fun boardAndCoinPowersChangeHowDiscsGlide() {
        val standard = glideDistance(PhysicsTuning.STANDARD)
        val velvet = glideDistance(Powers.tableTuning(BoardPower.VELVET_TOUCH, CoinPower.BALANCED))
        val hyper = glideDistance(Powers.tableTuning(BoardPower.HYPER_GLIDE, CoinPower.BALANCED))
        val crystal = glideDistance(Powers.tableTuning(BoardPower.TOURNAMENT, CoinPower.GLIDE))
        assertTrue("velvet $velvet < standard $standard", velvet < standard)
        assertTrue("hyper $hyper > standard $standard", hyper > standard)
        assertTrue("crystal $crystal > standard $standard", crystal > standard)
        assertEquals(PhysicsTuning.STANDARD, Powers.tableTuning(BoardPower.TOURNAMENT, CoinPower.BALANCED))
    }

    @Test
    fun widePocketsSwallowADiscThatWouldSitOnTheLip() {
        val pocket = BoardGeometry.POCKETS[2]
        fun sits(tuning: PhysicsTuning): Boolean {
            val disc = PieceFactory.createPiece("d", PieceType.BLACK, pocket.x - 22.5f, pocket.y - 22.5f) // ~31.8 from the centre
            CarromPhysicsEngine.updatePhysics(listOf(disc), null, FRAME, tuning = tuning)
            return !disc.isPocketed
        }
        assertTrue(sits(PhysicsTuning.STANDARD))
        assertFalse(sits(Powers.tableTuning(BoardPower.WIDE_POCKETS, CoinPower.BALANCED)))
        assertFalse(sits(Powers.shotTuning(PhysicsTuning.STANDARD, StrikerAbility.BALANCED, DiceFace.WIDE_POCKETS)))
    }

    @Test
    fun coinSetsShapeTheRack() {
        repository.setUnlocked("jumbo_carnival", true)
        val vm = CarromViewModel(Application(), repository, MockAudioEngine(), MockHapticEngine(), null, Random(1)) { now }
        vm.selectCoinSet("jumbo_carnival")
        vm.startNewGame(GameMode.VS_AI)
        assertEquals(Powers.discRadius(CoinPower.JUMBO), vm.pieces.value.first().radius, 1e-4f)
        assertTrue(vm.gameState.value.powersActive)

        // Powers off: the regulation rack, whatever is equipped.
        vm.togglePowers()
        assertFalse(repository.getFlag("powers", true))
        vm.startNewGame(GameMode.VS_AI)
        assertFalse(vm.gameState.value.powersActive)
        assertEquals(BoardGeometry.PUCK_RADIUS, vm.pieces.value.first().radius, 1e-4f)
    }

    @Test
    fun lockedItemsCannotBeEquippedAndPurchasesPersist() {
        viewModel.selectCoinSet("golden_mint")
        assertEquals("heritage_boxwood", state.selectedCoinSetId)
        val marble = viewModel.coinSets.value.first { it.id == "marble_royale" }
        val coins = viewModel.playerStats.value.coins
        viewModel.buyCoinSet(marble)
        assertEquals(coins - marble.price, viewModel.playerStats.value.coins)
        assertEquals("marble_royale", state.selectedCoinSetId)
        assertEquals("marble_royale", repository.getSelection("coins", ""))

        val clover = viewModel.dice.value.first { it.id == "clover_die" }
        viewModel.buyDice(clover)
        assertEquals("clover_die", state.selectedDiceId)
        viewModel.startNewGame(GameMode.DICE)
        assertEquals("The Lucky Clover grants a re-roll", 1, state.dice!!.rerollsLeft)
    }

    @Test
    fun strikerPowersOnlyChangeTheStrikersOwnNumbers() {
        val gold = viewModel.strikers.value.first { it.ability == StrikerAbility.HEAVYWEIGHT }
        val ruby = viewModel.strikers.value.first { it.ability == StrikerAbility.FIRE_SHOT }
        assertEquals(gold.weight, Powers.strikerMass(gold), 1e-4f)
        assertEquals(BoardGeometry.STRIKER_MASS, Powers.strikerMass(ruby), 1e-4f)
        assertEquals(ruby.powerMultiplier * Powers.POWER_SURGE, Powers.powerMultiplier(ruby, DiceFace.POWER_SURGE), 1e-4f)
        assertEquals(3, Powers.guideBounces(StrikerAbility.DRAGON_SIGHT, null))
        assertEquals(5, Powers.guideBounces(StrikerAbility.DRAGON_SIGHT, DiceFace.EAGLE_EYE))
        assertEquals(1.5f, Powers.winCoinMultiplier(CoinPower.MIDAS), 1e-4f)
    }

    @Test
    fun onlineAndChallengesAlwaysUseTheStandardTable() {
        repository.setUnlocked("jumbo_carnival", true)
        viewModel.selectCoinSet("jumbo_carnival")
        viewModel.startTrickShotLevel(1)
        assertFalse(state.powersActive)
        viewModel.startLuckyShot()
        assertFalse(state.powersActive)
    }

    // endregion

    // region Time Attack

    @Test
    fun timeAttackEndsAtTheBuzzerAndKeepsTheBest() {
        viewModel.startTimeAttack()
        assertEquals(GameMode.TIME_ATTACK, state.mode)
        assertEquals(1, state.playerCount)
        assertNotNull(state.turnClock)
        missShot()
        assertEquals("Solo: always your shot", PlayerSlot.PLAYER1, state.currentTurn)
        assertFalse(state.isGameOver)

        state.let { assertEquals(now + 90_000, it.timeAttack!!.deadlineMillis) }
        elapse(90_001)
        assertTrue(state.isGameOver)
        assertEquals(state.player1.score, repository.getBest("time_attack"))
    }

    @Test
    fun aTimeAttackFoulCostsFiveSeconds() {
        viewModel.startTimeAttack()
        val deadline = state.timeAttack!!.deadlineMillis
        // Aim the striker straight into the bottom-left pocket.
        val striker = viewModel.striker.value!!
        val pocket = BoardGeometry.POCKETS[3]
        viewModel.setStrikerBaselineOffset(0.06f)
        viewModel.setStrikerAim(kotlin.math.atan2(pocket.y - striker.y, pocket.x - striker.x), 45f)
        assertTrue("The guide predicts the foul", viewModel.aimPreview.value!!.strikerPocketId >= 0)
        viewModel.executeShot()
        runFrames(15f) { state.turnState != TurnState.MOVING }
        assertEquals(1, state.player1.fouls)
        assertEquals(deadline - 5_000, state.timeAttack!!.deadlineMillis)
    }

    // endregion

    private companion object {
        const val FRAME = 1f / 60f
    }
}

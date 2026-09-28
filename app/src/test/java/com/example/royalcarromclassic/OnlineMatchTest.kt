package com.example.royalcarromclassic

import android.app.Application
import com.example.royalcarromclassic.data.GameMode
import com.example.royalcarromclassic.data.PlayerSlot
import com.example.royalcarromclassic.data.TurnState
import com.example.royalcarromclassic.engine.BoardGeometry
import com.example.royalcarromclassic.online.*
import com.example.royalcarromclassic.ui.CarromViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.PI

/** Records what the game sends and lets tests play the server's part. */
class FakeMatchSource(override val playerId: String? = ME) : OnlineMatchSource {
    val flow = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 16)
    override val events = flow
    override val connection = MutableStateFlow(ConnectionState.OFFLINE)
    val shots = mutableListOf<Pair<Int, ShotInputDto>>()
    val aims = mutableListOf<ShotInputDto>()
    val resigned = mutableListOf<String>()
    var shootError: ApiException? = null
    var resumeSnapshot: SnapshotDto? = null

    override suspend fun shoot(matchId: String, turn: Int, input: ShotInputDto) {
        shots += turn to input
        shootError?.let { throw it }
    }

    override fun aim(matchId: String, input: ShotInputDto) {
        aims += input
    }

    override suspend fun emote(matchId: String, emote: String) = Unit

    override suspend fun resign(matchId: String) {
        resigned += matchId
    }

    override suspend fun resume(): SnapshotDto? = resumeSnapshot
}

const val ME = "MEPLAYER"
const val MATCH_ID = "5b0c2a0e-6f7e-4d5a-9a3c-0d9f1e2b3c4d"
const val NOW = 1_700_000_000_000L
private const val FRAME = 1f / 60f

/** A match where the opponent holds seat 0 (bottom on the server) and we hold seat 1. */
fun snapshot(
    turn: Int = 0,
    current: Int = 1,
    pieces: List<PieceDto> = listOf(PieceDto("w1", "WHITE", 400f, 300f, false), PieceDto("b1", "BLACK", 250f, 420f, false)),
    scores: Pair<Int, Int> = 0 to 0,
    phase: String = "playing",
    winner: Int? = null,
    connected: Boolean = true
) = SnapshotDto(
    id = MATCH_ID,
    mode = "classic",
    arena = "bronze",
    ranked = true,
    entryFee = 100,
    seats = listOf(
        SeatDto("OPPONENT", "Asha Rao", rating = 1520, score = scores.first, connected = connected),
        SeatDto(ME, "Me Player", rating = 1490, score = scores.second)
    ),
    pieces = pieces,
    current = current,
    turn = turn,
    deadline = NOW + 20_000,
    serverTime = NOW,
    turnSeconds = 20f,
    phase = phase,
    winner = winner
)

/** Drives the real game ViewModel against a scripted server. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnlineMatchTest {
    private val dispatcher = StandardTestDispatcher()
    private val source = FakeMatchSource()
    private var now = NOW
    private lateinit var viewModel: CarromViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = CarromViewModel(Application(), MockGameRepository(), MockAudioEngine(), MockHapticEngine(), source) { now }
        dispatcher.scheduler.runCurrent() // Subscribe to the event stream.
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun emit(event: RealtimeEvent) {
        assertTrue(source.flow.tryEmit(event))
        dispatcher.scheduler.runCurrent()
    }

    private fun runFrames(maxSeconds: Float, until: () -> Boolean) {
        var t = 0f
        while (t < maxSeconds && !until()) {
            viewModel.advanceFrame(FRAME)
            dispatcher.scheduler.runCurrent()
            t += FRAME
        }
    }

    private fun piece(id: String) = viewModel.pieces.value.first { it.id == id }
    private val state get() = viewModel.gameState.value

    @Test
    fun seatOneSeesTheBoardRotatedWithItselfAtTheBottom() {
        emit(RealtimeEvent.MatchStarted(snapshot(current = 0)))

        assertEquals(GameMode.ONLINE, state.mode)
        assertEquals("Me Player", state.player1.name)
        assertEquals("Asha Rao", state.player2.name)
        assertEquals(400f, piece("w1").x, 0.01f)
        assertEquals(500f, piece("w1").y, 0.01f)
        assertEquals(550f, piece("b1").x, 0.01f)
        assertEquals(380f, piece("b1").y, 0.01f)

        // Seat 0 (the opponent) breaks, from the top of our view.
        assertEquals(PlayerSlot.PLAYER2, state.currentTurn)
        assertTrue(state.isRemoteTurn)
        assertFalse(state.canAim)
        assertEquals(BoardGeometry.BASELINE_TOP_Y, viewModel.striker.value!!.y, 0.01f)
        assertEquals(1520, state.online!!.opponentRating)
        assertEquals(NOW + 20_000, state.turnClock!!.deadlineMillis)
    }

    @Test
    fun ourShotIsSentInServerCoordinatesAndSettlesOnTheServersResult() {
        emit(RealtimeEvent.MatchStarted(snapshot(current = 1)))
        assertTrue(state.canAim)

        viewModel.setStrikerBaselineOffset(0.3f)
        viewModel.setStrikerAim(-BoardGeometry.HALF_PI + 0.1f, 60f)
        viewModel.executeShot()
        dispatcher.scheduler.runCurrent()

        val (turn, input) = source.shots.single()
        assertEquals(0, turn)
        assertEquals(0.7f, input.offset, 1e-4f)
        assertEquals(BoardGeometry.HALF_PI + 0.1f, input.angle, 1e-4f)
        assertEquals(60f, input.power, 1e-4f)

        // Our animation ends before the server answers: the board waits, controls stay locked.
        runFrames(15f) { state.online?.syncing == true }
        assertTrue(state.online!!.syncing)
        assertFalse(state.canAim)

        val settled = snapshot(
            turn = 1,
            current = 1,
            pieces = listOf(PieceDto("w1", "WHITE", 400f, 300f, true), PieceDto("b1", "BLACK", 260f, 420f, false)),
            scores = 0 to 10
        )
        emit(RealtimeEvent.ShotPlayed(ShotEventDto(MATCH_ID, 0, 1, input, 1f, ShotResultDto(listOf("w1"), scoreDelta = 10), settled)))

        assertFalse(state.online!!.syncing)
        assertTrue(piece("w1").isPocketed)
        assertEquals(10, state.player1.score)
        assertEquals(PlayerSlot.PLAYER1, state.currentTurn)
        assertEquals(TurnState.PLACING_STRIKER, state.turnState)
        runFrames(1f) { false }
        assertEquals(540f, piece("b1").x, 0.01f)
        assertEquals(380f, piece("b1").y, 0.01f)
    }

    @Test
    fun aServerResultArrivingMidAnimationIsAppliedWhenTheShotEnds() {
        emit(RealtimeEvent.MatchStarted(snapshot(current = 1)))
        viewModel.setStrikerAim(-BoardGeometry.HALF_PI, 50f)
        viewModel.executeShot()
        dispatcher.scheduler.runCurrent()
        val input = source.shots.single().second

        val settled = snapshot(turn = 1, current = 0, scores = 0 to 0)
        emit(RealtimeEvent.ShotPlayed(ShotEventDto(MATCH_ID, 0, 1, input, 1f, ShotResultDto(), settled)))
        assertEquals("Still animating our shot", TurnState.MOVING, state.turnState)

        runFrames(15f) { state.turnState != TurnState.MOVING }
        assertEquals(PlayerSlot.PLAYER2, state.currentTurn)
        assertFalse(state.online!!.syncing)
    }

    @Test
    fun theOpponentsShotIsReplayedFromTheirInputs() {
        emit(RealtimeEvent.MatchStarted(snapshot(current = 0)))
        val input = ShotInputDto(offset = 0.5f, angle = -BoardGeometry.HALF_PI, power = 70f) // Seat 0 shoots "up" on the server.
        emit(RealtimeEvent.ShotPlayed(ShotEventDto(MATCH_ID, 0, 0, input, 1f, ShotResultDto(), snapshot(turn = 1, current = 1))))

        assertEquals(TurnState.MOVING, state.turnState)
        val striker = viewModel.striker.value!!
        assertEquals(BoardGeometry.BASELINE_TOP_Y, striker.y, 1f)
        assertTrue("From our side the opponent shoots downwards", striker.vy > 0f)

        runFrames(15f) { state.turnState != TurnState.MOVING }
        assertEquals(PlayerSlot.PLAYER1, state.currentTurn)
        assertTrue(state.canAim)
        assertTrue("Replays never send shots", source.shots.isEmpty())
    }

    @Test
    fun aRefusedShotRestoresTheServersBoard() {
        emit(RealtimeEvent.MatchStarted(snapshot(current = 1)))
        source.shootError = ApiException(409, "stale_turn", "That turn has already been played")
        source.resumeSnapshot = snapshot(turn = 1, current = 0)

        viewModel.setStrikerAim(-BoardGeometry.HALF_PI, 100f) // Straight into the white disc.
        viewModel.executeShot()
        dispatcher.scheduler.runCurrent()
        assertEquals("That turn has already been played", state.toastMessage)

        runFrames(15f) { state.turnState != TurnState.MOVING }
        runFrames(1f) { false }
        assertEquals(PlayerSlot.PLAYER2, state.currentTurn)
        assertFalse(piece("w1").isPocketed)
        assertEquals(400f, piece("w1").x, 0.01f)
        assertEquals(500f, piece("w1").y, 0.01f)
    }

    @Test
    fun aTimeoutThatOvertakesOurShotUnlocksTheBoard() {
        emit(RealtimeEvent.MatchStarted(snapshot(current = 1)))
        viewModel.setStrikerAim(-BoardGeometry.HALF_PI, 60f)
        viewModel.executeShot() // Accepted, but the server's result never reaches us.
        dispatcher.scheduler.runCurrent()
        runFrames(15f) { state.online?.syncing == true }
        assertTrue(state.online!!.syncing)

        emit(RealtimeEvent.TurnPassed(TurnEventDto("timeout", timedOutSeat = 1, snapshot = snapshot(turn = 1, current = 0))))
        runFrames(1f) { false }
        assertFalse("No longer waiting for the lost shot", state.online!!.syncing)
        assertEquals(PlayerSlot.PLAYER2, state.currentTurn)
        assertEquals(TurnState.PLACING_STRIKER, state.turnState)
        assertEquals(500f, piece("w1").y, 0.01f)
    }

    @Test
    fun theOpponentsAimIsFollowedSmoothly() {
        emit(RealtimeEvent.MatchStarted(snapshot(current = 0)))
        emit(RealtimeEvent.OpponentAim(AimEventDto(seat = 0, offset = 0.2f, angle = -BoardGeometry.HALF_PI + 0.3f, power = 80f)))

        viewModel.advanceFrame(FRAME)
        val first = state.strikerBaselineOffset
        assertTrue("Eases toward the target, got $first", first > 0.5f && first < 0.8f)

        runFrames(1.5f) { false }
        assertEquals(0.8f, state.strikerBaselineOffset, 1e-3f)
        assertEquals(BoardGeometry.HALF_PI + 0.3f, state.strikerAimAngle, 1e-3f)
        assertEquals(80f, state.strikerPower, 0.5f)
        assertTrue("Aim previews are never sent back", source.aims.isEmpty())
    }

    @Test
    fun ourAimIsRelayedToTheOpponent() {
        emit(RealtimeEvent.MatchStarted(snapshot(current = 1)))
        viewModel.setStrikerAim(-BoardGeometry.HALF_PI + 0.2f, 40f)
        dispatcher.scheduler.advanceUntilIdle()
        val sent = source.aims.last()
        assertEquals(BoardGeometry.HALF_PI + 0.2f, sent.angle, 1e-4f)
        assertEquals(40f, sent.power, 1e-4f)
    }

    @Test
    fun theEndOfTheMatchShowsOurRewardsAndRatingChange() {
        emit(RealtimeEvent.MatchStarted(snapshot(current = 1)))
        val end = EndEventDto(
            snapshot = snapshot(turn = 9, phase = "over", winner = 1, scores = 40 to 90),
            winner = 1,
            reason = "completed",
            rewards = listOf(RewardDto(0, 25, 1520, 1508), RewardDto(200, 100, 1490, 1503, leveledUp = true))
        )
        emit(RealtimeEvent.MatchEnded(end))

        assertTrue(state.isGameOver)
        assertEquals(PlayerSlot.PLAYER1, state.winner)
        assertEquals(90, state.player1.score)
        val result = state.online!!.result!!
        assertEquals(200, result.coins)
        assertEquals(13, result.ratingChange)
        assertNull(state.turnClock)
    }

    @Test
    fun theOpponentLosingConnectionIsShown() {
        emit(RealtimeEvent.MatchStarted(snapshot(current = 1)))
        emit(RealtimeEvent.Presence(PresenceEventDto(seat = 0, connected = false)))
        assertFalse(state.online!!.opponentConnected)
        assertEquals("Asha Rao lost connection", state.toastMessage)
    }

    @Test
    fun startingAnotherGameForfeitsTheLiveMatch() {
        emit(RealtimeEvent.MatchStarted(snapshot(current = 1)))
        assertTrue(viewModel.isInLiveOnlineMatch)
        viewModel.startNewGame(GameMode.VS_AI)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf(MATCH_ID), source.resigned)
        assertEquals(GameMode.VS_AI, state.mode)
        assertNull(state.online)
        assertFalse(viewModel.isInLiveOnlineMatch)
    }

    @Test
    fun reconnectingResumesTheLiveMatch() {
        source.resumeSnapshot = snapshot(turn = 4, current = 1, scores = 15 to 20)
        source.connection.value = ConnectionState.ONLINE
        dispatcher.scheduler.runCurrent()
        assertEquals(GameMode.ONLINE, state.mode)
        assertEquals(20, state.player1.score)
        assertTrue(state.canAim)
    }

    @Test
    fun deviceClockSkewDoesNotAffectTheShotClock() {
        now = NOW + 3_600_000 // This phone's clock is an hour ahead of the server.
        emit(RealtimeEvent.MatchStarted(snapshot(current = 1)))
        assertEquals(now + 20_000, state.turnClock!!.deadlineMillis)
    }

    @Test
    fun theHalfTurnRotationIsItsOwnInverse() {
        val view = SeatView(1)
        val input = ShotInputDto(0.23f, -2.5f, 64f)
        val back = view.toServer(view.toLocal(input))
        assertEquals(input.offset, back.offset, 1e-5f)
        assertEquals(input.angle, back.angle, 1e-5f)
        assertEquals(PlayerSlot.PLAYER1, view.slotOf(1))
        assertEquals(0, view.seatOf(PlayerSlot.PLAYER2))

        val identity = SeatView(0)
        assertEquals(input.angle, identity.toLocal(input).angle, 1e-6f)
        assertEquals(123f, identity.x(123f), 0f)
        assertTrue(kotlin.math.abs(view.angle(PI.toFloat() / 2f) + PI.toFloat() / 2f) < 1e-5f)
    }

    @Test
    fun reconcileRebuildsWhenTheDiscsDiffer() {
        val view = SeatView(0)
        val local = view.piecesOf(snapshot())
        assertFalse(view.reconcile(local, snapshot(pieces = listOf(PieceDto("x", "WHITE", 1f, 1f, false))), null))
        assertTrue(view.reconcile(local, snapshot(), null))
        assertEquals("AR", SeatView.monogram("Asha Rao"))
        assertEquals("ME", SeatView.monogram("me"))
    }
}

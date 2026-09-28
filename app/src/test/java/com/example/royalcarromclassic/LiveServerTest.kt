package com.example.royalcarromclassic

import android.app.Application
import com.example.royalcarromclassic.data.PlayerSlot
import com.example.royalcarromclassic.data.TurnState
import com.example.royalcarromclassic.engine.BoardGeometry
import com.example.royalcarromclassic.online.*
import com.example.royalcarromclassic.ui.CarromViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/**
 * Two devices play a ranked match against a real server, through the real REST client, socket
 * and game ViewModels. Skipped unless CARROM_SERVER_URL points at a running server, e.g.
 * `CARROM_SERVER_URL=http://localhost:8080 ./gradlew :app:testDebugUnitTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class LiveServerTest {
    private val url = System.getenv("CARROM_SERVER_URL")?.takeIf { it.isNotBlank() }
    private val main = newSingleThreadContext("game-main")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val devices = mutableListOf<OnlineServices>()

    /** Every event each device received, reported if a step times out. */
    private val received = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        assumeTrue("Set CARROM_SERVER_URL to run against a live server", url != null)
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        devices.forEach { it.realtime.disconnect() }
        scope.cancel()
        if (url != null) Dispatchers.resetMain()
        main.close()
    }

    private suspend fun device(): OnlineServices {
        val services = OnlineServices(url!!, InMemorySessionStore(), scope)
        devices += services
        services.api.signInAsGuest(country = "IN")
        val playerId = services.client.session.value?.playerId
        scope.launch { services.realtime.events.collect { received += "$playerId ← ${it.toString().take(140)}" } }
        withTimeout(10_000) { services.realtime.connection.first { it == ConnectionState.ONLINE } }
        return services
    }

    private suspend fun gameOn(services: OnlineServices) = withContext(main) {
        CarromViewModel(Application(), MockGameRepository(), MockAudioEngine(), MockHapticEngine(), services.matches)
    }

    /** Runs both games at 60 fps (in real time, so network events interleave) until [done]. */
    private suspend fun play(vararg games: CarromViewModel, seconds: Long = 30, done: () -> Boolean) {
        val finished = withTimeoutOrNull(seconds * 1000) {
            while (!withContext(main) { done() }) {
                withContext(main) { games.forEach { it.advanceFrame(1f / 60f) } }
                delay(4)
            }
        }
        if (finished == null) {
            val tables = games.joinToString("\n") { g ->
                val s = g.gameState.value
                "turn=${s.currentTurn} state=${s.turnState} frames=${g.needsFrames.value} over=${s.isGameOver} " +
                    "online=${s.online} toast=${s.toastMessage}"
            }
            fail("Timed out after $seconds s:\n$tables\nEvents:\n" + received.joinToString("\n"))
        }
    }

    private fun settled(game: CarromViewModel): Boolean {
        val s = game.gameState.value
        return s.turnState != TurnState.MOVING && s.online?.syncing != true && !game.needsFrames.value
    }

    @Test
    fun twoPlayersMeetBecomeFriendsAndPlayARankedMatch() = runBlocking {
        val alice = device()
        val bob = device()
        val aliceId = alice.client.session.value!!.playerId
        val bobId = bob.client.session.value!!.playerId
        assertTrue("Generated player IDs", Regex("[0-9A-HJKMNP-TV-Z]{8}").matches(aliceId))
        assertNotEquals(aliceId, bobId)

        // Friends: Alice adds Bob by Player ID; Bob is told in real time and accepts.
        val notice = async { bob.realtime.events.filterIsInstance<RealtimeEvent.FriendRequest>().first() }
        assertEquals("pending", alice.api.sendFriendRequest(bobId).status)
        assertEquals(aliceId, withTimeout(5_000) { notice.await() }.notice.player.playerId)
        bob.api.acceptFriendRequest(bob.api.friendRequests().single { it.direction == "incoming" }.id)
        assertEquals(listOf(bobId), alice.api.friends().map { it.playerId })

        // Both open the game, then queue for the bronze arena.
        val aliceGame = gameOn(alice)
        val bobGame = gameOn(bob)
        val started = listOf(alice, bob).map { d -> async { d.realtime.events.filterIsInstance<RealtimeEvent.MatchStarted>().first() } }
        assertEquals(100, alice.lobby.joinQueue("classic", "bronze")!!.entryFee)
        bob.lobby.joinQueue("classic", "bronze")
        val (a, b) = withTimeout(15_000) { started.awaitAll() }
        assertEquals(a.snapshot.id, b.snapshot.id)
        play(aliceGame, bobGame, seconds = 5) { aliceGame.isInLiveOnlineMatch && bobGame.isInLiveOnlineMatch }

        // Exactly one of them is to play, and each sees themselves at the bottom.
        val (shooter, watcher) = if (aliceGame.gameState.value.currentTurn == PlayerSlot.PLAYER1) aliceGame to bobGame else bobGame to aliceGame
        assertEquals(PlayerSlot.PLAYER2, watcher.gameState.value.currentTurn)
        assertTrue(shooter.gameState.value.canAim)
        assertFalse(watcher.gameState.value.canAim)

        // Live aim: the watcher sees the shooter's striker move to the mirrored spot.
        withContext(main) { shooter.setStrikerBaselineOffset(0.3f) }
        play(shooter, watcher, seconds = 5) { abs(watcher.gameState.value.strikerBaselineOffset - 0.7f) < 0.01f }

        // The break: both boards play it out and agree with the server, mirrored.
        withContext(main) {
            shooter.setStrikerBaselineOffset(0.5f)
            shooter.setStrikerAim(-BoardGeometry.HALF_PI + 0.01f, 100f)
            shooter.executeShot()
        }
        play(shooter, watcher) { settled(shooter) && settled(watcher) && !shooter.gameState.value.isGameOver }
        assertBoardsMirror(shooter, watcher)
        assertEquals(shooter.gameState.value.player1.score, watcher.gameState.value.player2.score)
        assertEquals(shooter.gameState.value.player2.score, watcher.gameState.value.player1.score)

        // Whoever is up takes the next shot.
        val (next, other) = if (shooter.gameState.value.currentTurn == PlayerSlot.PLAYER1) shooter to watcher else watcher to shooter
        withContext(main) {
            next.setStrikerAim(-BoardGeometry.HALF_PI - 0.4f, 70f)
            next.executeShot()
        }
        play(next, other) { settled(next) && settled(other) }
        assertBoardsMirror(next, other)

        // A quick-chat emote reaches the other table.
        withContext(main) { next.sendEmote("nice_shot") }
        play(next, other, seconds = 5) { other.gameState.value.online?.emote?.emote == "nice_shot" }

        // The player not on turn resigns; both see the result, and the winner takes the pot.
        withContext(main) { other.resignOnlineMatch() }
        play(next, other, seconds = 10) { next.gameState.value.isGameOver && other.gameState.value.isGameOver }
        assertEquals(PlayerSlot.PLAYER1, next.gameState.value.winner)
        assertEquals(PlayerSlot.PLAYER2, other.gameState.value.winner)
        val winnerResult = next.gameState.value.online!!.result!!
        assertEquals("resigned", winnerResult.reason)
        assertEquals(200, winnerResult.coins)
        assertTrue("Ranked: the winner gains rating", winnerResult.ratingChange > 0)
        assertTrue(other.gameState.value.online!!.result!!.ratingChange < 0)

        // The record and the wallets on the server.
        val winner = if (next === aliceGame) alice else bob
        val loser = if (next === aliceGame) bob else alice
        assertEquals(1200 - 100 + 200, winner.api.me().wallet.coins)
        assertEquals(1200 - 100, loser.api.me().wallet.coins)
        val history = winner.api.matchHistory()
        assertEquals("win", history.single().result)
        assertEquals("resigned", history.single().endReason)
        val board = winner.api.leaderboard(Board.FRIENDS)
        assertEquals(2, board.entries.size)
        assertTrue(board.entries.first().isMe)
    }

    /** Every disc sits at the mirrored spot on the other device (seat 0 vs seat 1 view). */
    private fun assertBoardsMirror(one: CarromViewModel, two: CarromViewModel) {
        val theirs = two.pieces.value.associateBy { it.id }
        for (piece in one.pieces.value) {
            val other = theirs.getValue(piece.id)
            assertEquals("${piece.id} pocketed", piece.isPocketed, other.isPocketed)
            if (!piece.isPocketed) {
                assertEquals("${piece.id} x", piece.x, BoardGeometry.BOARD_SIZE - other.x, 0.05f)
                assertEquals("${piece.id} y", piece.y, BoardGeometry.BOARD_SIZE - other.y, 0.05f)
            }
        }
    }
}

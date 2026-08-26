package com.example.royalcarromclassic

import android.app.Application
import com.example.royalcarromclassic.core.audio.AudioEngine
import com.example.royalcarromclassic.core.haptics.HapticEngine
import com.example.royalcarromclassic.data.*
import com.example.royalcarromclassic.ui.CarromViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MockGameRepository : GameRepository {
    private var stats = PlayerStats(coins = 2000, level = 1)
    private val unlockedItems = mutableSetOf("classic_ivory", "classic_teak")
    private var selectedStriker = "classic_ivory"
    private var selectedBoard = "classic_teak"
    private val trickStars = mutableMapOf<Int, Int>()

    override fun getPlayerStats(): PlayerStats = stats
    override fun savePlayerStats(stats: PlayerStats) { this.stats = stats }
    override fun isUnlocked(itemId: String, defaultUnlocked: Boolean): Boolean =
        unlockedItems.contains(itemId) || defaultUnlocked
    override fun setUnlocked(itemId: String, unlocked: Boolean) {
        if (unlocked) unlockedItems.add(itemId) else unlockedItems.remove(itemId)
    }
    override fun getSelectedStriker(): String = selectedStriker
    override fun setSelectedStriker(id: String) { selectedStriker = id }
    override fun getSelectedBoard(): String = selectedBoard
    override fun setSelectedBoard(id: String) { selectedBoard = id }
    override fun getTrickShotStars(levelId: Int): Int = trickStars[levelId] ?: 0
    override fun setTrickShotStars(levelId: Int, stars: Int) { trickStars[levelId] = stars }
}

class MockAudioEngine : AudioEngine {
    override var isSoundEnabled: Boolean = true
    override var isMusicEnabled: Boolean = true
    override fun playClack(intensity: Float) {}
    override fun playFlick(power: Float) {}
    override fun playWall(intensity: Float) {}
    override fun playPocket() {}
    override fun playVictory() {}
    override fun playCoin() {}
    override fun playClick() {}
    override fun release() {}
}

class MockHapticEngine : HapticEngine {
    override var isHapticEnabled: Boolean = true
    override fun vibrateShort(durationMs: Long) {}
    override fun vibrateStrike() {}
    override fun vibratePocket() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class GameLogicAndViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: MockGameRepository
    private lateinit var audio: MockAudioEngine
    private lateinit var haptics: MockHapticEngine
    private lateinit var viewModel: CarromViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = MockGameRepository()
        audio = MockAudioEngine()
        haptics = MockHapticEngine()
        viewModel = CarromViewModel(
            application = Application(),
            repository = repository,
            sound = audio,
            haptic = haptics
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialGameState() {
        val state = viewModel.gameState.value
        assertEquals(GameMode.VS_AI, state.mode)
        assertEquals("player1", state.currentTurn)
        assertEquals(TurnState.PLACING_STRIKER, state.turnState)
        assertFalse(state.isGameOver)
        assertNull(state.winner)
    }

    @Test
    fun testStrikerBaselineClamping() {
        viewModel.setStrikerBaselineOffset(0.0f)
        assertEquals(0.06f, viewModel.gameState.value.strikerBaselineOffset, 0.001f)

        viewModel.setStrikerBaselineOffset(1.0f)
        assertEquals(0.94f, viewModel.gameState.value.strikerBaselineOffset, 0.001f)

        viewModel.setStrikerBaselineOffset(0.5f)
        assertEquals(0.5f, viewModel.gameState.value.strikerBaselineOffset, 0.001f)
    }

    @Test
    fun testAimAngleAndPowerClamping() {
        viewModel.setStrikerAim(-1.57f, 150f)
        assertEquals(100f, viewModel.gameState.value.strikerPower, 0.001f)
        assertEquals(TurnState.AIMING, viewModel.gameState.value.turnState)

        viewModel.setStrikerAim(-1.57f, 5f)
        assertEquals(20f, viewModel.gameState.value.strikerPower, 0.001f)
    }

    @Test
    fun testAwardRewardsAndLevelUp() {
        val initialCoins = viewModel.playerStats.value.coins
        viewModel.awardRewards(coins = 500, xp = 150)

        assertTrue(viewModel.playerStats.value.coins == initialCoins + 500)
        assertTrue("Player should level up with 150 XP", viewModel.playerStats.value.level >= 2)
    }

    @Test
    fun testBuyAndSelectStriker() {
        val strikerToBuy = ShopRepository.STRIKERS.first { !it.isUnlocked }
        val initialCoins = viewModel.playerStats.value.coins

        viewModel.buyStriker(strikerToBuy)

        assertEquals(initialCoins - strikerToBuy.price, viewModel.playerStats.value.coins)
        assertEquals(strikerToBuy.id, viewModel.gameState.value.selectedStrikerId)
        assertTrue(repository.isUnlocked(strikerToBuy.id, false))
    }

    @Test
    fun testToggleSoundAndHaptics() {
        assertTrue(viewModel.gameState.value.soundEnabled)
        viewModel.toggleSound()
        assertFalse(viewModel.gameState.value.soundEnabled)
        assertFalse(audio.isSoundEnabled)

        assertTrue(viewModel.gameState.value.hapticEnabled)
        viewModel.toggleHaptics()
        assertFalse(viewModel.gameState.value.hapticEnabled)
        assertFalse(haptics.isHapticEnabled)
    }
}

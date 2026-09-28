package com.example.royalcarromclassic.ui

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.royalcarromclassic.core.audio.AudioEngine
import com.example.royalcarromclassic.core.audio.SoundSynthesizer
import com.example.royalcarromclassic.core.haptics.HapticController
import com.example.royalcarromclassic.core.haptics.HapticEngine
import com.example.royalcarromclassic.data.*
import com.example.royalcarromclassic.engine.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Game state, turn flow, rewards and the frame-driven game loop.
 *
 * The UI calls [onFrame] once per display frame (vsync) while [needsFrames] is true. Physics,
 * the bot's choreography and all board effects advance there, on the main thread, so the board
 * is always drawn from a consistent state and motion is perfectly paced at any refresh rate.
 */
class CarromViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: GameRepository = PreferencesManager(application),
    val sound: AudioEngine = SoundSynthesizer(application),
    val haptic: HapticEngine = HapticController(application)
) : AndroidViewModel(application) {

    /** Particles, striker trail and placement bounce, drawn by the board canvas. */
    val effects = BoardEffects()

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _pieces = MutableStateFlow<List<Piece>>(emptyList())
    val pieces: StateFlow<List<Piece>> = _pieces.asStateFlow()

    private val _striker = MutableStateFlow<Piece?>(null)
    val striker: StateFlow<Piece?> = _striker.asStateFlow()

    private val _aimPreview = MutableStateFlow<CarromPhysicsEngine.TrajectoryData?>(null)
    val aimPreview: StateFlow<CarromPhysicsEngine.TrajectoryData?> = _aimPreview.asStateFlow()

    private val _boardSummary = MutableStateFlow(BoardSummary())
    val boardSummary: StateFlow<BoardSummary> = _boardSummary.asStateFlow()

    private val _needsFrames = MutableStateFlow(false)

    /** True while anything on the board is animating; the UI runs its frame loop only then. */
    val needsFrames: StateFlow<Boolean> = _needsFrames.asStateFlow()

    private val _frameTick = mutableLongStateOf(0L)

    /**
     * Incremented after every simulated frame. Snapshot state (rather than a flow) so a draw
     * that reads it is invalidated within the same frame the simulation advanced.
     */
    val frameTick: State<Long> get() = _frameTick

    private val _playerStats = MutableStateFlow(repository.getPlayerStats())
    val playerStats: StateFlow<PlayerStats> = _playerStats.asStateFlow()

    private val _strikers = MutableStateFlow(
        ShopRepository.STRIKERS.map { it.copy(isUnlocked = repository.isUnlocked(it.id, it.isUnlocked)) }
    )
    val strikers: StateFlow<List<StrikerConfig>> = _strikers.asStateFlow()

    private val _boards = MutableStateFlow(
        ShopRepository.BOARDS.map { it.copy(isUnlocked = repository.isUnlocked(it.id, it.isUnlocked)) }
    )
    val boards: StateFlow<List<BoardTheme>> = _boards.asStateFlow()

    private val _trickShotLevels = MutableStateFlow(
        TrickShotsManager.LEVELS.map {
            it.copy(
                stars = repository.getTrickShotStars(it.id),
                isUnlocked = it.id == 1 || repository.getTrickShotStars(it.id - 1) > 0
            )
        }
    )
    val trickShotLevels: StateFlow<List<TrickShotLevel>> = _trickShotLevels.asStateFlow()

    private val aiDirector = AiShotDirector()
    private val pocketedThisShot = ArrayList<Piece>(8)
    private var isSimulating = false
    private var simulationSeconds = 0f
    private var lastFrameNanos = 0L
    private var lastBaselineDetent = -1
    private var toastJob: Job? = null
    private var currentTrickLevelId = 1

    init {
        _gameState.update {
            it.copy(
                selectedStrikerId = repository.getSelectedStriker(),
                selectedBoardId = repository.getSelectedBoard()
            )
        }
        startNewGame(GameMode.VS_AI, AIDifficulty.MEDIUM)
    }

    // region Game loop

    /** Called by the UI once per display frame while [needsFrames] is true. */
    fun onFrame(frameTimeNanos: Long) {
        val dt = if (lastFrameNanos == 0L) {
            NOMINAL_FRAME_SECONDS
        } else {
            ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, MAX_FRAME_SECONDS)
        }
        lastFrameNanos = frameTimeNanos
        advanceFrame(dt)
    }

    /** Advances the whole game by [dtSeconds]; exposed for deterministic tests. */
    internal fun advanceFrame(dtSeconds: Float) {
        if (isSimulating) stepSimulation(dtSeconds)
        val sinking = CarromPhysicsEngine.advancePocketDrops(_pieces.value, _striker.value, dtSeconds)
        effects.advance(dtSeconds, _striker.value)
        if (aiDirector.isActive) advanceAi(dtSeconds)
        _frameTick.longValue++
        updateFrameDemand(sinking)
    }

    private fun updateFrameDemand(sinking: Boolean = false) {
        val needed = isSimulating || sinking || aiDirector.isActive || effects.isAnimating
        if (needed && !_needsFrames.value) lastFrameNanos = 0L
        _needsFrames.value = needed
    }

    private fun requestFrames() = updateFrameDemand(sinking = true)

    private fun stepSimulation(dtSeconds: Float) {
        simulationSeconds += dtSeconds
        val striker = _striker.value
        val stillMoving = CarromPhysicsEngine.updatePhysics(
            pieces = _pieces.value,
            striker = striker,
            dtSeconds = dtSeconds,
            onClack = { intensity, x, y ->
                sound.playClack(intensity)
                if (intensity > 0.4f) haptic.vibrateCollision(intensity)
                if (intensity > 0.25f) effects.particles.spawnCollisionDust(x, y, intensity, count = 3 + (intensity * 4).toInt())
            },
            onWall = { intensity, x, y ->
                sound.playWall(intensity)
                if (intensity > 0.5f) haptic.vibrateTick()
                if (intensity > 0.35f) effects.particles.spawnCollisionDust(x, y, intensity * 0.6f, count = 3)
            },
            onPocket = { piece, pocket ->
                pocketedThisShot.add(piece)
                sound.playPocket()
                haptic.vibratePocket()
                effects.particles.spawnPocketVortex(pocket.x, pocket.y)
                if (piece.type != PieceType.STRIKER) refreshBoardSummary()
            }
        )

        if (stillMoving && simulationSeconds > MAX_SIMULATION_SECONDS) {
            CarromPhysicsEngine.haltAll(_pieces.value, striker)
        } else if (stillMoving) {
            return
        }
        isSimulating = false
        finishShot()
    }

    // endregion

    // region Game setup

    fun startNewGame(mode: GameMode, difficulty: AIDifficulty = AIDifficulty.MEDIUM) {
        if (mode == GameMode.TRICK_SHOTS) {
            startTrickShotLevel(currentTrickLevelId)
            return
        }
        resetBoard(CarromPhysicsEngine.generateClassicCluster())
        val opponent = if (mode == GameMode.VS_AI) PlayerData("Bot Master", "AI") else PlayerData("Player 2", "P2")
        _gameState.update {
            it.copy(
                mode = mode,
                aiDifficulty = difficulty,
                isGameOver = false,
                winner = null,
                player1 = PlayerData("Player 1", "P1"),
                player2 = opponent,
                queenPottedBy = null,
                queenNeedsCover = false,
                queenCovered = false,
                trickShot = null,
                toastMessage = null
            )
        }
        startTurn(PlayerSlot.PLAYER1)
    }

    /** Replays the current match (or the current trick shot level) from the start. */
    fun restartMatch() {
        val state = _gameState.value
        if (state.mode == GameMode.TRICK_SHOTS) startTrickShotLevel(currentTrickLevelId)
        else startNewGame(state.mode, state.aiDifficulty)
    }

    /** The next trick shot level, if it exists and is unlocked. */
    fun nextTrickShotLevelId(): Int? =
        _trickShotLevels.value.firstOrNull { it.id == currentTrickLevelId + 1 && it.isUnlocked }?.id

    fun startTrickShotLevel(levelId: Int) {
        val level = _trickShotLevels.value.find { it.id == levelId } ?: return
        currentTrickLevelId = level.id
        resetBoard(PieceFactory.createTrickShotPieces(level.id, level.pieces))

        val offset = BoardGeometry.baselineFractionAt(level.strikerPos.x)
        _gameState.update {
            it.copy(
                mode = GameMode.TRICK_SHOTS,
                isGameOver = false,
                winner = null,
                player1 = PlayerData("Player 1", "P1"),
                player2 = PlayerData("Trick Shot", "#${level.id}"),
                queenPottedBy = null,
                queenNeedsCover = false,
                queenCovered = false,
                trickShot = TrickShotStatus(level.id, level.title, level.hint, shotsTaken = 0, maxShots = level.maxShots),
                toastMessage = null
            )
        }
        startTurn(PlayerSlot.PLAYER1, offset = offset, power = 60f)
        showToast("Level ${level.id}: ${level.title}")
    }

    private fun resetBoard(pieces: List<Piece>) {
        aiDirector.cancel()
        isSimulating = false
        pocketedThisShot.clear()
        effects.clear()
        _pieces.value = pieces
        refreshBoardSummary()
    }

    private fun startTurn(
        slot: PlayerSlot,
        offset: Float = 0.5f,
        power: Float = BoardGeometry.DEFAULT_POWER
    ) {
        val isBottom = slot == PlayerSlot.PLAYER1
        _striker.value = PieceFactory.createStriker(offset, isBottom)
        effects.onStrikerPlaced()
        _gameState.update {
            it.copy(
                currentTurn = slot,
                turnState = TurnState.PLACING_STRIKER,
                strikerBaselineOffset = offset,
                strikerAimAngle = BoardGeometry.forwardAngle(isBottom),
                strikerPower = power
            )
        }
        refreshAimPreview()
        if (_gameState.value.isAiTurn) scheduleAIShot()
        requestFrames()
    }

    // endregion

    // region Striker controls

    fun setStrikerBaselineOffset(fraction: Float, isManualTouch: Boolean = false) {
        val state = _gameState.value
        if (state.isGameOver || state.turnState == TurnState.MOVING) return
        val clamped = fraction.coerceIn(BoardGeometry.MIN_BASELINE_FRACTION, BoardGeometry.MAX_BASELINE_FRACTION)
        val pos = BoardGeometry.getBaselineStrikerPos(clamped, state.isBottomTurn)
        _striker.value?.let {
            it.x = pos.x
            it.y = pos.y
        }
        if (isManualTouch) {
            // Notched-rail feel: one soft tick per detent instead of a continuous buzz.
            val detent = (clamped * BASELINE_DETENTS).toInt()
            if (detent != lastBaselineDetent) haptic.vibrateTick()
            lastBaselineDetent = detent
        }
        _gameState.update { it.copy(strikerBaselineOffset = clamped) }
        refreshAimPreview()
        _frameTick.longValue++
    }

    fun setStrikerAim(angle: Float, power: Float) {
        val state = _gameState.value
        if (state.isGameOver || state.turnState == TurnState.MOVING) return
        _gameState.update {
            it.copy(
                strikerAimAngle = BoardGeometry.normalizeAngle(angle),
                strikerPower = power.coerceIn(BoardGeometry.MIN_POWER, BoardGeometry.MAX_POWER),
                turnState = TurnState.AIMING
            )
        }
        refreshAimPreview()
    }

    fun nudgeAimAngle(deltaDegrees: Float) {
        val state = _gameState.value
        setStrikerAim(state.strikerAimAngle + deltaDegrees * BoardGeometry.DEG_TO_RAD, state.strikerPower)
        haptic.vibrateTick()
    }

    fun nudgePower(delta: Float) {
        val state = _gameState.value
        setStrikerAim(state.strikerAimAngle, state.strikerPower + delta)
        haptic.vibrateTick()
    }

    fun executeShot() {
        val state = _gameState.value
        val striker = _striker.value ?: return
        if (state.isGameOver || state.turnState == TurnState.MOVING || striker.isPocketed) return

        if (BoardGeometry.isOverBaselineCircle(striker.x, striker.y, state.isBottomTurn)) {
            // Auto-nudge off the foul circle to the nearest legal spot.
            val safe = if (striker.x < BoardGeometry.CENTER) 0.12f else 0.88f
            val pos = BoardGeometry.getBaselineStrikerPos(safe, state.isBottomTurn)
            striker.x = pos.x
            striker.y = pos.y
            _gameState.update { it.copy(strikerBaselineOffset = safe) }
        }

        val speed = (state.strikerPower / 100f) * CarromPhysicsEngine.MAX_STRIKE_SPEED * activeStrikerConfig().powerMultiplier
        striker.vx = cos(state.strikerAimAngle) * speed
        striker.vy = sin(state.strikerAimAngle) * speed

        sound.playFlick(state.strikerPower)
        haptic.vibrateStrike()

        _aimPreview.value = null
        effects.onShot()
        pocketedThisShot.clear()
        simulationSeconds = 0f
        isSimulating = true
        _gameState.update {
            it.copy(
                turnState = TurnState.MOVING,
                trickShot = it.trickShot?.let { t -> t.copy(shotsTaken = t.shotsTaken + 1) }
            )
        }
        requestFrames()
    }

    private fun refreshAimPreview() {
        val state = _gameState.value
        val striker = _striker.value
        _aimPreview.value = if (striker != null && !striker.isPocketed && !state.isGameOver &&
            state.turnState != TurnState.MOVING
        ) {
            val config = activeStrikerConfig()
            CarromPhysicsEngine.calculateTrajectory(
                striker = striker,
                aimAngle = state.strikerAimAngle,
                power = state.strikerPower,
                pieces = _pieces.value,
                powerMultiplier = config.powerMultiplier,
                maxLength = CarromPhysicsEngine.DEFAULT_GUIDE_LENGTH * config.aimGuideLength
            )
        } else {
            null
        }
    }

    private fun activeStrikerConfig(): StrikerConfig =
        _strikers.value.find { it.id == _gameState.value.selectedStrikerId } ?: _strikers.value.first()

    // endregion

    // region Turn evaluation

    private fun finishShot() {
        val state = _gameState.value
        val shooter = state.currentTurn
        val strikerPocketed = _striker.value?.isPocketed == true
        val pocketedDiscs = pocketedThisShot.filter { it.type != PieceType.STRIKER }
        pocketedThisShot.clear()

        val outcome = CarromRules.evaluateShot(
            shooter = shooter,
            pocketed = pocketedDiscs.map { it.type },
            strikerPocketed = strikerPocketed,
            queen = QueenStatus(state.queenPottedBy, state.queenNeedsCover, state.queenCovered)
        )

        if (outcome.returnQueenToCenter) respotQueen()
        outcome.announcement?.let(::showToast)
        recordCareerStats(shooter, pocketedDiscs.size, outcome.queenCoveredNow)

        _gameState.update {
            val scored = it.player(shooter).let { p ->
                p.copy(
                    score = (p.score + outcome.scoreDelta).coerceAtLeast(0),
                    fouls = p.fouls + if (outcome.isFoul) 1 else 0
                )
            }
            val withScore = if (shooter == PlayerSlot.PLAYER1) it.copy(player1 = scored) else it.copy(player2 = scored)
            withScore.copy(
                queenPottedBy = outcome.queen.pottedBy,
                queenNeedsCover = outcome.queen.awaitingCover,
                queenCovered = outcome.queen.covered
            )
        }
        refreshBoardSummary()

        val updated = _gameState.value
        if (updated.mode == GameMode.TRICK_SHOTS) {
            checkTrickShotCompletion()
            return
        }

        val winner = CarromRules.winnerOrNull(
            mode = updated.mode,
            discsLeft = _pieces.value.count { !it.isPocketed },
            player1Score = updated.player1.score,
            player2Score = updated.player2.score
        )
        if (winner != null) {
            _gameState.update { it.copy(isGameOver = true, winner = winner) }
            _aimPreview.value = null
            handleGameEnd(winner == PlayerSlot.PLAYER1)
            return
        }

        startTurn(CarromRules.nextShooter(updated.mode, shooter, outcome.keepsTurn))
    }

    private fun respotQueen() {
        val pieces = _pieces.value
        val queen = pieces.find { it.type == PieceType.QUEEN } ?: return
        val spot = CarromPhysicsEngine.findFreeSpot(pieces, queen.radius, ignore = queen)
        queen.isPocketed = false
        queen.pocketProgress = 1f
        queen.pocketId = -1
        queen.x = spot.x
        queen.y = spot.y
        queen.vx = 0f
        queen.vy = 0f
    }

    private fun refreshBoardSummary() {
        var whites = 0
        var blacks = 0
        var queen = false
        for (p in _pieces.value) {
            if (p.isPocketed) continue
            when (p.type) {
                PieceType.WHITE -> whites++
                PieceType.BLACK -> blacks++
                PieceType.QUEEN -> queen = true
                PieceType.STRIKER -> Unit
            }
        }
        _boardSummary.value = BoardSummary(whites, blacks, queen)
    }

    private fun recordCareerStats(shooter: PlayerSlot, pocketed: Int, queenCovered: Boolean) {
        if (shooter != PlayerSlot.PLAYER1 || (pocketed == 0 && !queenCovered)) return
        _playerStats.update {
            it.copy(
                totalPockets = it.totalPockets + pocketed,
                queenCovers = it.queenCovers + if (queenCovered) 1 else 0
            )
        }
    }

    private fun scheduleAIShot() {
        val state = _gameState.value
        val plan = CarromAIEngine.calculateBestShot(
            pieces = _pieces.value,
            aiColor = state.player2.assignedColor,
            queenNeedsCover = state.queenNeedsCover,
            queenPottedByAI = state.queenPottedBy == PlayerSlot.PLAYER2,
            difficulty = state.aiDifficulty
        )
        aiDirector.begin(
            from = ShotAim(state.strikerBaselineOffset, state.strikerAimAngle, state.strikerPower),
            to = ShotAim(plan.baselineFraction, plan.aimAngle, plan.power)
        )
    }

    private fun advanceAi(dtSeconds: Float) {
        val state = _gameState.value
        if (!state.isAiTurn || state.isGameOver || state.turnState == TurnState.MOVING) {
            aiDirector.cancel()
            return
        }
        val release = aiDirector.advance(dtSeconds)
        val aim = aiDirector.current
        if (aim.baselineOffset != state.strikerBaselineOffset) setStrikerBaselineOffset(aim.baselineOffset)
        if (aim.angle != state.strikerAimAngle || aim.power != state.strikerPower) setStrikerAim(aim.angle, aim.power)
        if (release) executeShot()
    }

    private fun checkTrickShotCompletion() {
        val level = _trickShotLevels.value.find { it.id == currentTrickLevelId } ?: return
        val shotsTaken = _gameState.value.trickShot?.shotsTaken ?: 0
        val cleared = _pieces.value.none { !it.isPocketed && (it.type == PieceType.WHITE || it.type == PieceType.QUEEN) }

        when {
            cleared -> {
                val stars = if (shotsTaken <= level.maxShots) 3 else 2
                val firstClear = level.stars == 0
                repository.setTrickShotStars(level.id, stars)
                _trickShotLevels.update { list ->
                    list.map {
                        when (it.id) {
                            level.id -> it.copy(stars = maxOf(it.stars, stars))
                            level.id + 1 -> it.copy(isUnlocked = true)
                            else -> it
                        }
                    }
                }
                if (firstClear) _playerStats.update { it.copy(trickShotsCompleted = it.trickShotsCompleted + 1) }
                awardRewards(coins = TRICK_SHOT_REWARD_COINS, xp = 50)
                sound.playVictory()
                showToast("Trick shot solved! +$TRICK_SHOT_REWARD_COINS coins")
                _aimPreview.value = null
                _gameState.update { it.copy(isGameOver = true, winner = PlayerSlot.PLAYER1) }
            }

            shotsTaken >= level.maxShots -> {
                showToast("Out of shots — try again")
                _aimPreview.value = null
                _gameState.update { it.copy(isGameOver = true, winner = PlayerSlot.PLAYER2) }
            }

            else -> startTurn(PlayerSlot.PLAYER1, offset = _gameState.value.strikerBaselineOffset, power = 60f)
        }
    }

    private fun handleGameEnd(isPlayer1Win: Boolean) {
        _playerStats.update {
            it.copy(
                matchesPlayed = it.matchesPlayed + 1,
                matchesWon = it.matchesWon + if (isPlayer1Win) 1 else 0
            )
        }
        if (isPlayer1Win) {
            sound.playVictory()
            awardRewards(coins = MATCH_WIN_COINS, xp = MATCH_WIN_XP)
        } else {
            repository.savePlayerStats(_playerStats.value)
        }
    }

    // endregion

    // region Rewards, shop & settings

    fun awardRewards(coins: Int, xp: Int) {
        sound.playCoin()
        val current = _playerStats.value
        var newXp = current.xp + xp
        var newLevel = current.level
        var nextLevelXp = current.xpToNextLevel

        while (newXp >= nextLevelXp) {
            newXp -= nextLevelXp
            newLevel++
            nextLevelXp = (nextLevelXp * 1.3f).toInt()
            showToast("Level up! You reached level $newLevel")
        }

        val updated = current.copy(
            coins = current.coins + coins,
            xp = newXp,
            level = newLevel,
            xpToNextLevel = nextLevelXp
        )
        _playerStats.value = updated
        repository.savePlayerStats(updated)
    }

    fun buyStriker(striker: StrikerConfig) {
        if (!spendCoins(striker.price, striker.id, striker.isUnlocked)) return
        _strikers.update { list -> list.map { if (it.id == striker.id) it.copy(isUnlocked = true) else it } }
        selectStriker(striker.id)
        showToast("Unlocked ${striker.name}")
    }

    fun buyBoard(board: BoardTheme) {
        if (!spendCoins(board.price, board.id, board.isUnlocked)) return
        _boards.update { list -> list.map { if (it.id == board.id) it.copy(isUnlocked = true) else it } }
        selectBoard(board.id)
        showToast("Unlocked ${board.name}")
    }

    private fun spendCoins(price: Int, itemId: String, alreadyUnlocked: Boolean): Boolean {
        if (alreadyUnlocked || _playerStats.value.coins < price) return false
        sound.playCoin()
        val newStats = _playerStats.value.copy(coins = _playerStats.value.coins - price)
        _playerStats.value = newStats
        repository.savePlayerStats(newStats)
        repository.setUnlocked(itemId, true)
        return true
    }

    fun selectStriker(id: String) {
        repository.setSelectedStriker(id)
        _gameState.update { it.copy(selectedStrikerId = id) }
        refreshAimPreview()
    }

    fun selectBoard(id: String) {
        repository.setSelectedBoard(id)
        _gameState.update { it.copy(selectedBoardId = id) }
    }

    fun toggleSound() {
        val next = !_gameState.value.soundEnabled
        sound.isSoundEnabled = next
        _gameState.update { it.copy(soundEnabled = next) }
    }

    fun toggleHaptics() {
        val next = !_gameState.value.hapticEnabled
        haptic.isHapticEnabled = next
        _gameState.update { it.copy(hapticEnabled = next) }
    }

    private fun showToast(msg: String) {
        _gameState.update { it.copy(toastMessage = msg) }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(TOAST_MILLIS)
            _gameState.update { if (it.toastMessage == msg) it.copy(toastMessage = null) else it }
        }
    }

    // endregion

    override fun onCleared() {
        super.onCleared()
        aiDirector.cancel()
        sound.release()
    }

    private companion object {
        const val NOMINAL_FRAME_SECONDS = 1f / 60f
        const val MAX_FRAME_SECONDS = 1f / 20f
        const val MAX_SIMULATION_SECONDS = 15f
        const val BASELINE_DETENTS = 24
        const val TOAST_MILLIS = 2600L
        const val MATCH_WIN_COINS = 500
        const val MATCH_WIN_XP = 100
        const val TRICK_SHOT_REWARD_COINS = 300
    }
}

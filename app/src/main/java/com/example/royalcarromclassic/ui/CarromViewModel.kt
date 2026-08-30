package com.example.royalcarromclassic.ui

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.royalcarromclassic.core.audio.AudioEngine
import com.example.royalcarromclassic.core.audio.SoundSynthesizer
import com.example.royalcarromclassic.core.haptics.HapticController
import com.example.royalcarromclassic.core.haptics.HapticEngine
import com.example.royalcarromclassic.core.logging.AppLogger
import com.example.royalcarromclassic.data.*
import com.example.royalcarromclassic.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Main ViewModel for Carrom game state, turn mechanics, physics loop, and rewards.
 * Designed with SOLID principles, loose coupling, and memory efficiency.
 */
class CarromViewModel(
    application: Application,
    private val repository: GameRepository = PreferencesManager(application),
    val sound: AudioEngine = SoundSynthesizer(application),
    val haptic: HapticEngine = HapticController(application)
) : AndroidViewModel(application) {

    val particles = ParticleSystem(maxCapacity = 64)

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _pieces = MutableStateFlow<List<Piece>>(emptyList())
    val pieces: StateFlow<List<Piece>> = _pieces.asStateFlow()

    private val _striker = MutableStateFlow<Piece?>(null)
    val striker: StateFlow<Piece?> = _striker.asStateFlow()

    private val _physicsTick = MutableStateFlow(0L)
    val physicsTick: StateFlow<Long> = _physicsTick.asStateFlow()

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
            val stars = repository.getTrickShotStars(it.id)
            it.copy(stars = stars, isUnlocked = it.id == 1 || repository.getTrickShotStars(it.id - 1) > 0)
        }
    )
    val trickShotLevels: StateFlow<List<TrickShotLevel>> = _trickShotLevels.asStateFlow()

    private var physicsJob: Job? = null
    private var aiJob: Job? = null
    private var trickShotCurrentShots = 0
    private var currentTrickLevelId = 1

    init {
        val savedStriker = repository.getSelectedStriker()
        val savedBoard = repository.getSelectedBoard()
        _gameState.update {
            it.copy(
                selectedStrikerId = savedStriker,
                selectedBoardId = savedBoard
            )
        }
        startNewGame(GameMode.VS_AI, AIDifficulty.MEDIUM)
    }

    fun startNewGame(mode: GameMode, difficulty: AIDifficulty = AIDifficulty.MEDIUM) {
        physicsJob?.cancel()
        aiJob?.cancel()
        particles.clear()

        val initialPieces = when (mode) {
            GameMode.TRICK_SHOTS -> loadTrickLevelPieces(1)
            else -> CarromPhysicsEngine.generateClassicCluster()
        }

        val isBottom = true
        val newStriker = CarromPhysicsEngine.createStriker(0.5f, isBottom)

        _pieces.value = initialPieces
        _striker.value = newStriker

        _gameState.update {
            it.copy(
                mode = mode,
                aiDifficulty = difficulty,
                isGameOver = false,
                winner = null,
                currentTurn = "player1",
                turnState = TurnState.PLACING_STRIKER,
                player1 = PlayerData("Player 1", "👑", score = 0),
                player2 = PlayerData(if (mode == GameMode.VS_AI) "Bot Master" else "Player 2", if (mode == GameMode.VS_AI) "🤖" else "🎯", score = 0),
                queenPottedBy = null,
                queenNeedsCover = false,
                queenCovered = false,
                strikerBaselineOffset = 0.5f,
                strikerAimAngle = -BoardGeometry.HALF_PI,
                strikerPower = 50f
            )
        }
        _physicsTick.value++
    }

    private fun isCurrentTurnBottom(): Boolean {
        val state = _gameState.value
        return state.currentTurn == "player1" || state.mode == GameMode.TRICK_SHOTS
    }

    fun setStrikerBaselineOffset(fraction: Float, isManualTouch: Boolean = false) {
        val clamped = fraction.coerceIn(0.06f, 0.94f)
        val isBottom = isCurrentTurnBottom()
        val pos = BoardGeometry.getBaselineStrikerPos(clamped, isBottom)
        _striker.value?.let {
            it.x = pos.x
            it.y = pos.y
        }
        if (isManualTouch) {
            haptic.vibrateTick()
        }
        _gameState.update { it.copy(strikerBaselineOffset = clamped) }
        _physicsTick.value++
    }

    fun setStrikerAim(angle: Float, power: Float) {
        _gameState.update {
            it.copy(
                strikerAimAngle = angle,
                strikerPower = power.coerceIn(20f, 100f),
                turnState = TurnState.AIMING
            )
        }
        _physicsTick.value++
    }

    fun nudgeAimAngle(deltaDegrees: Float) {
        val rad = deltaDegrees * BoardGeometry.DEG_TO_RAD
        val newAngle = _gameState.value.strikerAimAngle + rad
        setStrikerAim(newAngle, _gameState.value.strikerPower)
        haptic.vibrateTick()
    }

    fun nudgePower(delta: Float) {
        val newPower = (_gameState.value.strikerPower + delta).coerceIn(20f, 100f)
        setStrikerAim(_gameState.value.strikerAimAngle, newPower)
        haptic.vibrateTick()
    }

    fun executeShot() {
        val currentStriker = _striker.value ?: return
        if (_gameState.value.turnState == TurnState.MOVING || _gameState.value.isGameOver) return

        val isBottom = isCurrentTurnBottom()
        val isFoul = BoardGeometry.isOverBaselineCircle(currentStriker.x, currentStriker.y, isBottom)
        if (isFoul) {
            // Auto-nudge to nearest safe position
            val safeFrac = if (currentStriker.x < BoardGeometry.BOARD_SIZE / 2f) 0.12f else 0.88f
            val pos = BoardGeometry.getBaselineStrikerPos(safeFrac, isBottom)
            currentStriker.x = pos.x
            currentStriker.y = pos.y
            _gameState.update { it.copy(strikerBaselineOffset = safeFrac) }
        }

        // Get striker power multiplier
        val activeStrikerConfig = _strikers.value.find { it.id == _gameState.value.selectedStrikerId }
        val mult = activeStrikerConfig?.powerMultiplier ?: 1.0f

        val speed = (_gameState.value.strikerPower / 100f) * 34f * mult
        currentStriker.vx = cos(_gameState.value.strikerAimAngle) * speed
        currentStriker.vy = sin(_gameState.value.strikerAimAngle) * speed

        sound.playFlick(_gameState.value.strikerPower)
        haptic.vibrateStrike()

        _gameState.update { it.copy(turnState = TurnState.MOVING) }

        if (_gameState.value.mode == GameMode.TRICK_SHOTS) {
            trickShotCurrentShots++
        }

        startPhysicsSimulation()
    }

    private fun startPhysicsSimulation() {
        physicsJob?.cancel()
        physicsJob = viewModelScope.launch(Dispatchers.Default) {
            val pocketedThisTurn = ArrayList<Piece>(4)
            var isMoving = true
            var lastTimeNanos = System.nanoTime()
            var sparkFrameCounter = 0

            while (isMoving && isActive) {
                val nowNanos = System.nanoTime()
                val dtSeconds = ((nowNanos - lastTimeNanos) / 1_000_000_000f).coerceIn(0.004f, 0.022f)
                lastTimeNanos = nowNanos

                val currentPieces = _pieces.value
                val currentStriker = _striker.value

                val result = CarromPhysicsEngine.updatePhysics(
                    pieces = currentPieces,
                    striker = currentStriker,
                    dtSeconds = dtSeconds,
                    onClack = { intensity ->
                        sound.playClack(intensity)
                        if (intensity > 0.4f) {
                            haptic.vibrateCollision(intensity)
                        }
                        if (intensity > 0.65f && currentStriker != null && !currentStriker.isPocketed) {
                            particles.spawnImpactSparks(currentStriker.x, currentStriker.y, Color(0xFFFDE68A), count = 3)
                        }
                    },
                    onWall = { intensity ->
                        sound.playWall(intensity)
                        if (intensity > 0.5f) {
                            haptic.vibrateTick()
                        }
                    },
                    onPocket = {
                        sound.playPocket()
                        haptic.vibratePocket()
                    }
                )

                val pocketedFrame = result.pocketedPieces
                if (pocketedFrame.isNotEmpty()) {
                    pocketedThisTurn.addAll(pocketedFrame)
                    val pCount = pocketedFrame.size
                    for (i in 0 until pCount) {
                        val p = pocketedFrame[i]
                        particles.spawnPocketVortex(p.x, p.y)
                    }
                }

                particles.update()

                // Striker velocity trail sparks (rate-limited for performance)
                sparkFrameCounter++
                if (sparkFrameCounter % 3 == 0 && currentStriker != null && !currentStriker.isPocketed) {
                    val sSpeed = hypot(currentStriker.vx, currentStriker.vy)
                    if (sSpeed > 7f) {
                        particles.spawnImpactSparks(currentStriker.x, currentStriker.y, Color(0xFFFDE68A), count = 1)
                    }
                }

                _physicsTick.value++
                isMoving = result.anyMoving

                delay(6) // Smooth 120Hz-ready physical clock tick
            }

            withContext(Dispatchers.Main) {
                evaluateTurnEnd(pocketedThisTurn)
            }
        }
    }

    private fun evaluateTurnEnd(pocketedPieces: List<Piece>) {
        val state = _gameState.value
        val isPlayer1 = state.currentTurn == "player1"
        val strikerPocketed = _striker.value?.isPocketed == true

        var p1ScoreDelta = 0
        var p2ScoreDelta = 0
        var p1FoulDelta = 0
        var p2FoulDelta = 0
        var queenCoveredThisTurn = false
        var repeatTurn = false

        // 1. Striker Pocketed (Foul penalty)
        if (strikerPocketed) {
            if (isPlayer1) {
                p1FoulDelta++
                p1ScoreDelta -= 5
            } else {
                p2FoulDelta++
                p2ScoreDelta -= 5
            }
            showToast("Foul! Striker pocketed (-5 pts)")
        }

        // 2. Score pocketed pieces
        val regularPockets = pocketedPieces.filter { it.type != PieceType.STRIKER }

        for (p in regularPockets) {
            if (p.type == PieceType.QUEEN) {
                _gameState.update {
                    it.copy(
                        queenPottedBy = if (isPlayer1) "player1" else "player2",
                        queenNeedsCover = true
                    )
                }
                showToast("Queen Potted! Pocket a cover disc to claim 25 pts!")
                repeatTurn = true
            } else {
                val pts = if (p.type == PieceType.WHITE) 10 else 5
                if (isPlayer1) p1ScoreDelta += pts else p2ScoreDelta += pts

                // Check Queen Cover
                if (state.queenNeedsCover && state.queenPottedBy == (if (isPlayer1) "player1" else "player2")) {
                    queenCoveredThisTurn = true
                    val queenBonus = 25
                    if (isPlayer1) p1ScoreDelta += queenBonus else p2ScoreDelta += queenBonus
                    _gameState.update { it.copy(queenNeedsCover = false, queenCovered = true) }
                    showToast("Queen Covered! +25 Points Bonus!")
                    repeatTurn = true
                } else {
                    repeatTurn = true
                }
            }
        }

        // If queen was potted previously but NOT covered in this turn, return queen to center
        if (state.queenNeedsCover && regularPockets.none { it.type == PieceType.QUEEN } && !queenCoveredThisTurn) {
            _gameState.update { it.copy(queenNeedsCover = false, queenPottedBy = null) }
            val queen = _pieces.value.find { it.type == PieceType.QUEEN }
            queen?.let {
                it.isPocketed = false
                it.x = BoardGeometry.BOARD_SIZE / 2f
                it.y = BoardGeometry.BOARD_SIZE / 2f
                it.vx = 0f
                it.vy = 0f
                it.pocketProgress = 1f
            }
            showToast("Queen Not Covered! Returned to center.")
        }

        // Apply scores
        _gameState.update {
            it.copy(
                player1 = it.player1.copy(
                    score = (it.player1.score + p1ScoreDelta).coerceAtLeast(0),
                    fouls = it.player1.fouls + p1FoulDelta
                ),
                player2 = it.player2.copy(
                    score = (it.player2.score + p2ScoreDelta).coerceAtLeast(0),
                    fouls = it.player2.fouls + p2FoulDelta
                )
            )
        }

        // Check Trick Shots completion
        if (state.mode == GameMode.TRICK_SHOTS) {
            checkTrickShotCompletion()
            return
        }

        // Check Game Over
        val remainingActivePieces = _pieces.value.filter { !it.isPocketed }
        if (remainingActivePieces.isEmpty() || (state.mode == GameMode.FREESTYLE && (state.player1.score >= 160 || state.player2.score >= 160))) {
            val winner = if (state.player1.score >= state.player2.score) "player1" else "player2"
            _gameState.update {
                it.copy(
                    isGameOver = true,
                    winner = winner
                )
            }
            handleGameWin(winner == "player1")
            return
        }

        // Next Turn setup
        val nextTurn = if (repeatTurn && !strikerPocketed) {
            state.currentTurn
        } else {
            if (isPlayer1) (if (state.mode == GameMode.VS_AI) "ai" else "player2") else "player1"
        }

        val isNextBottom = nextTurn == "player1"
        val nextStriker = CarromPhysicsEngine.createStriker(0.5f, isNextBottom)
        _striker.value = nextStriker

        _gameState.update {
            it.copy(
                currentTurn = nextTurn,
                turnState = TurnState.PLACING_STRIKER,
                strikerBaselineOffset = 0.5f,
                strikerAimAngle = if (isNextBottom) -BoardGeometry.HALF_PI else BoardGeometry.HALF_PI,
                strikerPower = 50f
            )
        }
        _physicsTick.value++

        // If next turn is AI, schedule AI aim and shoot routine
        if (nextTurn == "ai" && state.mode == GameMode.VS_AI) {
            scheduleAIShot()
        }
    }

    private fun scheduleAIShot() {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            delay(350) // Bot initial decision time

            val state = _gameState.value
            val shotPlan = CarromAIEngine.calculateBestShot(
                pieces = _pieces.value,
                aiColor = state.player2.assignedColor,
                queenNeedsCover = state.queenNeedsCover,
                queenPottedByAI = state.queenPottedBy == "player2",
                difficulty = state.aiDifficulty
            )

            // Stage 1: Smooth baseline slider movement with cosine ease
            val startFraction = state.strikerBaselineOffset
            val targetFraction = shotPlan.baselineFraction
            val sliderSteps = 16
            for (i in 1..sliderSteps) {
                val t = i.toFloat() / sliderSteps.toFloat()
                val easeT = (1f - cos(t * kotlin.math.PI.toFloat())) / 2f
                val frac = startFraction + (targetFraction - startFraction) * easeT
                setStrikerBaselineOffset(frac, isManualTouch = false)
                delay(14)
            }

            delay(120)

            // Stage 2: Smooth aim rotation with cubic ease
            val startAngle = _gameState.value.strikerAimAngle
            val targetAngle = shotPlan.aimAngle
            val aimSteps = 16
            for (i in 1..aimSteps) {
                val t = i.toFloat() / aimSteps.toFloat()
                val easeT = t * t * (3f - 2f * t)
                val curAngle = startAngle + (targetAngle - startAngle) * easeT
                _gameState.update {
                    it.copy(
                        strikerAimAngle = curAngle,
                        turnState = TurnState.AIMING
                    )
                }
                _physicsTick.value++
                delay(12)
            }

            // Stage 3: Smooth power pull-back windup
            val targetPower = shotPlan.power
            val powerSteps = 10
            for (i in 1..powerSteps) {
                val t = i.toFloat() / powerSteps.toFloat()
                val curPower = 20f + (targetPower - 20f) * t
                _gameState.update {
                    it.copy(strikerPower = curPower)
                }
                _physicsTick.value++
                delay(12)
            }

            delay(200) // Brief lock-on freeze before striking

            // Stage 4: Execute Bot Strike
            executeShot()
        }
    }

    private fun checkTrickShotCompletion() {
        val remainingPieces = _pieces.value.filter { !it.isPocketed }
        val currentLevel = _trickShotLevels.value.find { it.id == currentTrickLevelId } ?: return

        val isCleared = remainingPieces.none { it.type == PieceType.WHITE || it.type == PieceType.QUEEN }

        if (isCleared) {
            val stars = if (trickShotCurrentShots <= currentLevel.maxShots) 3 else 2
            repository.setTrickShotStars(currentLevel.id, stars)
            _trickShotLevels.update { list ->
                list.map {
                    if (it.id == currentLevel.id) it.copy(stars = stars)
                    else if (it.id == currentLevel.id + 1) it.copy(isUnlocked = true)
                    else it
                }
            }
            awardRewards(coins = 300, xp = 50)
            sound.playVictory()
            showToast("Trick Shot Solved! ⭐⭐⭐ (+300 Coins)")
            _gameState.update { it.copy(isGameOver = true, winner = "player1") }
        } else if (trickShotCurrentShots >= currentLevel.maxShots) {
            showToast("Out of shots! Try again.")
            _gameState.update { it.copy(isGameOver = true, winner = "player2") }
        } else {
            val nextStriker = CarromPhysicsEngine.createStriker(0.5f, true)
            _striker.value = nextStriker
            _gameState.update {
                it.copy(
                    turnState = TurnState.PLACING_STRIKER,
                    strikerBaselineOffset = 0.5f,
                    strikerAimAngle = -BoardGeometry.HALF_PI
                )
            }
            _physicsTick.value++
        }
    }

    fun startTrickShotLevel(levelId: Int) {
        currentTrickLevelId = levelId
        trickShotCurrentShots = 0
        val level = _trickShotLevels.value.find { it.id == levelId } ?: return

        val levelPieces = PieceFactory.createTrickShotPieces(level.id, level.pieces)

        val strikerPosFraction = (level.strikerPos.x - BoardGeometry.BASELINE_START_X) / BoardGeometry.BASELINE_WIDTH
        val newStriker = CarromPhysicsEngine.createStriker(strikerPosFraction, true)

        _pieces.value = levelPieces
        _striker.value = newStriker

        _gameState.update {
            it.copy(
                mode = GameMode.TRICK_SHOTS,
                isGameOver = false,
                winner = null,
                currentTurn = "player1",
                turnState = TurnState.PLACING_STRIKER,
                strikerBaselineOffset = strikerPosFraction,
                strikerAimAngle = -BoardGeometry.HALF_PI,
                strikerPower = 60f
            )
        }
        _physicsTick.value++
    }

    private fun loadTrickLevelPieces(levelId: Int): List<Piece> {
        val level = TrickShotsManager.LEVELS.find { it.id == levelId } ?: TrickShotsManager.LEVELS.first()
        return PieceFactory.createTrickShotPieces(level.id, level.pieces)
    }

    private fun handleGameWin(isPlayer1Win: Boolean) {
        if (isPlayer1Win) {
            sound.playVictory()
            awardRewards(coins = 500, xp = 100)
            _playerStats.update {
                it.copy(
                    matchesPlayed = it.matchesPlayed + 1,
                    matchesWon = it.matchesWon + 1
                )
            }
        } else {
            _playerStats.update {
                it.copy(matchesPlayed = it.matchesPlayed + 1)
            }
        }
        repository.savePlayerStats(_playerStats.value)
    }

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
            showToast("LEVEL UP! Reached Level $newLevel! 🎉")
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
        if (_playerStats.value.coins >= striker.price && !striker.isUnlocked) {
            sound.playCoin()
            val newStats = _playerStats.value.copy(coins = _playerStats.value.coins - striker.price)
            _playerStats.value = newStats
            repository.savePlayerStats(newStats)
            repository.setUnlocked(striker.id, true)

            _strikers.update { list ->
                list.map { if (it.id == striker.id) it.copy(isUnlocked = true) else it }
            }
            selectStriker(striker.id)
            showToast("Unlocked ${striker.name}!")
        }
    }

    fun buyBoard(board: BoardTheme) {
        if (_playerStats.value.coins >= board.price && !board.isUnlocked) {
            sound.playCoin()
            val newStats = _playerStats.value.copy(coins = _playerStats.value.coins - board.price)
            _playerStats.value = newStats
            repository.savePlayerStats(newStats)
            repository.setUnlocked(board.id, true)

            _boards.update { list ->
                list.map { if (it.id == board.id) it.copy(isUnlocked = true) else it }
            }
            selectBoard(board.id)
            showToast("Unlocked ${board.name}!")
        }
    }

    fun selectStriker(id: String) {
        repository.setSelectedStriker(id)
        _gameState.update { it.copy(selectedStrikerId = id) }
        _physicsTick.value++
    }

    fun selectBoard(id: String) {
        repository.setSelectedBoard(id)
        _gameState.update { it.copy(selectedBoardId = id) }
        _physicsTick.value++
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
        viewModelScope.launch {
            delay(2800)
            if (_gameState.value.toastMessage == msg) {
                _gameState.update { it.copy(toastMessage = null) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        physicsJob?.cancel()
        aiJob?.cancel()
        sound.release()
    }
}

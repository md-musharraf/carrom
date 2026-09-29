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
import com.example.royalcarromclassic.online.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Game state, turn flow, rewards and the frame-driven game loop.
 *
 * The UI calls [onFrame] once per display frame (vsync) while [needsFrames] is true. Physics,
 * the bot's choreography and all board effects advance there, on the main thread, so the board
 * is always drawn from a consistent state and motion is perfectly paced at any refresh rate.
 *
 * Online matches are server-authoritative: shots animate locally at once, then the board settles
 * on the server's result. The opponent's shots are replayed from their inputs.
 */
class CarromViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: GameRepository = PreferencesManager(application),
    val sound: AudioEngine = SoundSynthesizer(application),
    val haptic: HapticEngine = HapticController(application),
    private val online: OnlineMatchSource? = (application as? OnlineHost)?.online?.matches,
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis
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

    private val _coinSets = MutableStateFlow(
        ShopRepository.COIN_SETS.map { it.copy(isUnlocked = repository.isUnlocked(it.id, it.isUnlocked)) }
    )
    val coinSets: StateFlow<List<CoinSet>> = _coinSets.asStateFlow()

    private val _dice = MutableStateFlow(
        ShopRepository.DICE.map { it.copy(isUnlocked = repository.isUnlocked(it.id, it.isUnlocked)) }
    )
    val dice: StateFlow<List<DiceSkin>> = _dice.asStateFlow()

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
    private val glide = PositionGlide()
    private val pocketedThisShot = ArrayList<Piece>(8)
    private var isSimulating = false
    private var simulationSeconds = 0f
    private var lastFrameNanos = 0L
    private var lastBaselineDetent = -1
    private var toastJob: Job? = null

    /** A delayed turn action: the Blitz shot clock or the pause between Lucky Shot attempts. */
    private var turnJob: Job? = null
    private var emoteJob: Job? = null
    private var aimRelayJob: Job? = null
    private var lastAimRelayAt = 0L
    private var emoteCounter = 0L
    private var currentTrickLevelId = 1

    /** The online match being played, or null offline. */
    private var session: OnlineSession? = null

    /** How the table plays this match (board and coin powers), fixed when the match starts. */
    private var tableTuning = CarromPhysicsEngine.PhysicsTuning.STANDARD

    /** The table plus the shooter's striker and die, for the shot in play. */
    private var shotTuning = CarromPhysicsEngine.PhysicsTuning.STANDARD

    /** The coin set's power this match (Midas pays out when the match is won). */
    private var matchCoinPower = CoinPower.BALANCED

    /** The last local match set-up, so a rematch plays the same table. */
    private var lastConfig: MatchConfig? = null
    private var diceJob: Job? = null
    private var timeAttackJob: Job? = null

    init {
        _gameState.update {
            it.copy(
                selectedStrikerId = repository.getSelectedStriker(),
                selectedBoardId = repository.getSelectedBoard(),
                selectedCoinSetId = repository.getSelection(SELECTION_COINS, DEFAULT_COIN_SET),
                selectedDiceId = repository.getSelection(SELECTION_DICE, DEFAULT_DICE),
                powersEnabled = repository.getFlag(FLAG_POWERS, true)
            )
        }
        startNewGame(GameMode.VS_AI, AIDifficulty.MEDIUM)

        if (online != null) {
            viewModelScope.launch { online.events.collect { onRealtimeEvent(it) } }
            // (Re)connecting: pick up a live match, e.g. after the app was closed mid-game.
            viewModelScope.launch {
                online.connection.collect { if (it == ConnectionState.ONLINE) resumeOnlineMatch() }
            }
        }
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
        glide.advance(dtSeconds)
        if (aiDirector.isActive) advanceAi(dtSeconds)
        session?.remoteAim?.let { followRemoteAim(it, dtSeconds) }
        _frameTick.longValue++
        updateFrameDemand(sinking)
    }

    private fun updateFrameDemand(sinking: Boolean = false) {
        val needed = isSimulating || sinking || aiDirector.isActive || effects.isAnimating ||
            glide.isActive || session?.remoteAim != null
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
            },
            tuning = shotTuning
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

    /** Starts [mode] with its usual line-up (you against the bot, or two people for Pass & Play). */
    fun startNewGame(mode: GameMode, difficulty: AIDifficulty = AIDifficulty.MEDIUM) {
        startMatch(MatchConfig(mode, defaultSeats(mode), difficulty))
    }

    /** Starts a local match from the set-up sheet: two to four seats, people or bots, optionally doubles. */
    fun startMatch(config: MatchConfig) {
        when (config.mode) {
            GameMode.TRICK_SHOTS -> return startTrickShotLevel(currentTrickLevelId)
            GameMode.LUCKY_SHOT -> return startLuckyShot()
            GameMode.TIME_ATTACK -> return startTimeAttack()
            // Online matches start from the lobby, when the server pairs two players.
            GameMode.ONLINE -> return
            else -> Unit
        }
        val mode = config.mode
        val seats = normalizedSeats(config)
        val doubles = seats.size == 4 && (config.doubles || mode == GameMode.DISC_POOL)
        lastConfig = config.copy(seats = seats, doubles = doubles)

        val powers = beginTable(mode)
        resetBoard(tableCluster(powers))
        var bots = 0
        val players = seats.mapIndexed { i, seat ->
            val team = if (doubles) i % 2 else -1
            PlayerData(
                name = seat.name.trim().ifEmpty { if (seat.isBot) BOT_NAMES[bots % BOT_NAMES.size] else "Player ${i + 1}" },
                monogram = if (seat.isBot) (if (bots++ == 0) "AI" else "B${bots}") else "P${i + 1}",
                isBot = seat.isBot,
                team = team,
                // Disc Pool: seats alternate white and black, so partners (opposite) share a colour.
                assignedColor = if (mode == GameMode.DISC_POOL) DiscPoolRules.colourOf(i % 2) else null
            )
        }
        val die = if (powers) equippedDice().power else DicePower.FAIR
        _gameState.update {
            it.freshMatch(mode).copy(
                aiDifficulty = config.difficulty,
                players = players,
                seats = Seat.layoutFor(players.size),
                powersActive = powers,
                dice = if (mode == GameMode.DICE) DiceStatus(rerollsLeft = Powers.rerollsPerMatch(die)) else null
            )
        }
        startTurn(PlayerSlot.PLAYER1)
        when (mode) {
            GameMode.BLITZ -> showToast("Blitz: first to ${CarromRules.BLITZ_TARGET} · ${BLITZ_SHOT_MILLIS / 1000}s a shot")
            GameMode.DICE -> showToast("Dice Carrom: roll, then shoot · first to ${CarromRules.DICE_TARGET}")
            GameMode.DISC_POOL -> showToast("You play white: pocket all nine, cover the queen first")
            else -> if (doubles) showToast("Doubles: partners sit opposite each other")
        }
    }

    /** The seats [mode] starts with when no set-up sheet was used. */
    private fun defaultSeats(mode: GameMode): List<SeatSetup> = when (mode) {
        GameMode.PASS_AND_PLAY -> listOf(SeatSetup("", false), SeatSetup("", false))
        GameMode.PRACTICE -> listOf(SeatSetup("", false))
        else -> listOf(SeatSetup("", false), SeatSetup(BOT_NAMES[0], true))
    }

    /** Solo modes seat one player; Blitz is always one against the bot; Disc Pool needs 2 or 4. */
    private fun normalizedSeats(config: MatchConfig): List<SeatSetup> {
        val seats = config.seats.take(4).ifEmpty { defaultSeats(config.mode) }
        return when (config.mode) {
            GameMode.PRACTICE -> seats.take(1).map { it.copy(isBot = false) }
            GameMode.VS_AI, GameMode.BLITZ -> listOf(seats.first().copy(isBot = false), seats.getOrNull(1)?.copy(isBot = true) ?: SeatSetup(BOT_NAMES[0], true))
            GameMode.DISC_POOL -> if (seats.size >= 4) seats.take(4) else seats.take(2).let { if (it.size < 2) defaultSeats(GameMode.VS_AI) else it }
            else -> if (seats.size < 2) defaultSeats(config.mode) else seats
        }
    }

    /**
     * Fixes the table's physics for a new match of [mode] and returns whether loadout powers apply.
     * Powers are off online (the server simulates the standard table) and in the calibrated
     * challenges (trick shots, Lucky Shot).
     */
    private fun beginTable(mode: GameMode): Boolean {
        val powers = _gameState.value.powersEnabled && mode in POWER_MODES
        val board = activeBoardTheme()
        val coins = equippedCoinSet()
        tableTuning = if (powers) Powers.tableTuning(board.power, coins.power) else CarromPhysicsEngine.PhysicsTuning.STANDARD
        shotTuning = tableTuning
        matchCoinPower = if (powers) coins.power else CoinPower.BALANCED
        return powers
    }

    private fun tableCluster(powers: Boolean): List<Piece> {
        val coins = if (powers) equippedCoinSet().power else CoinPower.BALANCED
        return CarromPhysicsEngine.generateClassicCluster(Powers.discRadius(coins), Powers.discMass(coins))
    }

    /** Replays the current match (or the current trick shot level) from the start. */
    fun restartMatch() {
        val state = _gameState.value
        when (state.mode) {
            GameMode.TRICK_SHOTS -> startTrickShotLevel(currentTrickLevelId)
            GameMode.LUCKY_SHOT -> startLuckyShot()
            GameMode.TIME_ATTACK -> startTimeAttack()
            GameMode.ONLINE -> Unit
            else -> startMatch(lastConfig?.takeIf { it.mode == state.mode } ?: MatchConfig(state.mode, defaultSeats(state.mode), state.aiDifficulty))
        }
    }

    /** The line-up of the last local match, to pre-fill the set-up sheet. */
    fun lastMatchConfig(): MatchConfig? = lastConfig

    /** The next trick shot level, if it exists and is unlocked. */
    fun nextTrickShotLevelId(): Int? =
        _trickShotLevels.value.firstOrNull { it.id == currentTrickLevelId + 1 && it.isUnlocked }?.id

    fun startTrickShotLevel(levelId: Int) {
        val level = _trickShotLevels.value.find { it.id == levelId } ?: return
        currentTrickLevelId = level.id
        beginTable(GameMode.TRICK_SHOTS)
        resetBoard(PieceFactory.createTrickShotPieces(level.id, level.pieces))

        val offset = BoardGeometry.baselineFractionAt(level.strikerPos.x)
        _gameState.update {
            it.freshMatch(GameMode.TRICK_SHOTS).copy(
                players = listOf(PlayerData("Player 1", "P1"), PlayerData("Trick Shot", "#${level.id}")),
                trickShot = TrickShotStatus(level.id, level.title, level.hint, shotsTaken = 0, maxShots = level.maxShots)
            )
        }
        startTurn(PlayerSlot.PLAYER1, offset = offset, power = 60f)
        showToast("Level ${level.id}: ${level.title}")
    }

    /** Lucky Shot attempts left today. */
    fun luckyShotsLeft(): Int =
        (LuckyShot.DAILY_ATTEMPTS - repository.getDailyCount(LuckyShot.COUNTER, LuckyShot.dayIndex(clock()))).coerceAtLeast(0)

    fun startLuckyShot() {
        val left = luckyShotsLeft()
        if (left == 0) {
            showToast("No Lucky Shots left today. Come back tomorrow!")
            return
        }
        beginTable(GameMode.LUCKY_SHOT)
        resetBoard(LuckyShot.setup())
        _gameState.update {
            it.freshMatch(GameMode.LUCKY_SHOT).copy(
                players = listOf(PlayerData("Player 1", "P1"), PlayerData("Lucky Shot", "LS")),
                luckyShot = LuckyShotStatus(attemptsLeft = left)
            )
        }
        startTurn(PlayerSlot.PLAYER1)
        showToast("Send the lucky disc into the rings!")
    }

    /** Time Attack: [TIME_ATTACK_MILLIS] to pocket as many points as possible. */
    fun startTimeAttack() {
        val powers = beginTable(GameMode.TIME_ATTACK)
        resetBoard(tableCluster(powers))
        lastConfig = null
        val deadline = clock() + TIME_ATTACK_MILLIS
        val seconds = TIME_ATTACK_MILLIS / 1000f
        _gameState.update {
            it.freshMatch(GameMode.TIME_ATTACK).copy(
                players = listOf(PlayerData("Player 1", "P1")),
                seats = Seat.layoutFor(1),
                powersActive = powers,
                timeAttack = TimeAttackStatus(deadline, seconds, best = repository.getBest(BEST_TIME_ATTACK)),
                turnClock = TurnClock(deadline, seconds)
            )
        }
        startTurn(PlayerSlot.PLAYER1)
        armTimeAttackClock()
        showToast("Time Attack: ${TIME_ATTACK_MILLIS / 1000} seconds · go!")
    }

    /** State for a new match of [mode]: scores, queen, challenges and online details reset. */
    private fun GameState.freshMatch(mode: GameMode) = copy(
        mode = mode,
        isGameOver = false,
        winner = null,
        players = listOf(PlayerData("Player 1", "P1"), PlayerData("Player 2", "P2")),
        seats = Seat.layoutFor(2),
        queenPottedBy = null,
        queenNeedsCover = false,
        queenCovered = false,
        trickShot = null,
        luckyShot = null,
        online = null,
        dice = null,
        timeAttack = null,
        turnClock = null,
        powersActive = false,
        shotsPlayed = 0,
        matchCoins = 0,
        toastMessage = null
    )

    private fun resetBoard(pieces: List<Piece>) {
        abandonOnlineMatch()
        aiDirector.cancel()
        glide.cancel()
        turnJob?.cancel()
        diceJob?.cancel()
        timeAttackJob?.cancel()
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
        val state = _gameState.value
        val seat = state.seatOf(slot)
        _striker.value = PieceFactory.createStriker(offset, seat, mass = strikerMassFor(state, slot))
        effects.onStrikerPlaced()
        _gameState.update {
            it.copy(
                currentTurn = slot,
                turnState = TurnState.PLACING_STRIKER,
                strikerBaselineOffset = offset,
                strikerAimAngle = BoardGeometry.forwardAngle(seat),
                strikerPower = power,
                dice = it.dice?.copy(face = null, rolling = false)
            )
        }
        shotTuning = tableTuning
        refreshAimPreview()
        val next = _gameState.value
        if (next.mode == GameMode.BLITZ) startShotClock(slot)
        if (next.isAiTurn) {
            if (next.dice != null) rollDice(forBot = true) else scheduleAIShot()
        }
        requestFrames()
    }

    // endregion

    // region Striker controls

    fun setStrikerBaselineOffset(fraction: Float, isManualTouch: Boolean = false) {
        val state = _gameState.value
        if (state.isGameOver || state.turnState == TurnState.MOVING) return
        val clamped = fraction.coerceIn(BoardGeometry.MIN_BASELINE_FRACTION, BoardGeometry.MAX_BASELINE_FRACTION)
        val pos = BoardGeometry.strikerPos(clamped, state.currentSeat)
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
        relayAim()
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
        relayAim()
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
        if (state.isGameOver || state.turnState == TurnState.MOVING || striker.isPocketed ||
            state.isRemoteTurn || state.online?.syncing == true || state.needsRoll
        ) return
        val s = session
        if (s != null) {
            shotTuning = CarromPhysicsEngine.PhysicsTuning.STANDARD
            launchStriker(activeStrikerConfig().powerMultiplier)
            sendOnlineShot(s)
            return
        }
        val face = state.dice?.face
        shotTuning = Powers.shotTuning(tableTuning, shooterStriker(state).ability.takeIf { state.powersActive } ?: StrikerAbility.BALANCED, face)
        launchStriker(shotPowerMultiplier(state))
    }

    /** Fires the striker with the current aim; shared by local shots and replayed online shots. */
    private fun launchStriker(powerMultiplier: Float) {
        val striker = _striker.value ?: return
        val seat = _gameState.value.currentSeat
        if (BoardGeometry.isOverBaselineCircle(striker.x, striker.y, seat)) {
            // Auto-nudge off the foul circle to the nearest legal spot (the server does the same).
            val safe = if (_gameState.value.strikerBaselineOffset < 0.5f) 0.12f else 0.88f
            val pos = BoardGeometry.strikerPos(safe, seat)
            striker.x = pos.x
            striker.y = pos.y
            _gameState.update { it.copy(strikerBaselineOffset = safe) }
        }
        val state = _gameState.value
        val speed = (state.strikerPower / 100f) * CarromPhysicsEngine.MAX_STRIKE_SPEED * powerMultiplier
        striker.vx = cos(state.strikerAimAngle) * speed
        striker.vy = sin(state.strikerAimAngle) * speed

        sound.playFlick(state.strikerPower)
        haptic.vibrateStrike()

        turnJob?.cancel()
        glide.finish()
        _aimPreview.value = null
        effects.onShot()
        pocketedThisShot.clear()
        simulationSeconds = 0f
        isSimulating = true
        _gameState.update {
            it.copy(
                turnState = TurnState.MOVING,
                turnClock = if (it.mode == GameMode.TIME_ATTACK) it.turnClock else null,
                shotsPlayed = it.shotsPlayed + 1,
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
            if (session != null) {
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
                val config = shooterStriker(state)
                val ability = if (state.powersActive) config.ability else StrikerAbility.BALANCED
                val face = state.dice?.face
                CarromPhysicsEngine.calculateTrajectory(
                    striker = striker,
                    aimAngle = state.strikerAimAngle,
                    power = state.strikerPower,
                    pieces = _pieces.value,
                    maxBounces = Powers.guideBounces(ability, face),
                    powerMultiplier = shotPowerMultiplier(state),
                    maxLength = Powers.guideLength(if (state.powersActive) config else standardStriker(), face),
                    tuning = Powers.shotTuning(tableTuning, ability, face)
                )
            }
        } else {
            null
        }
    }

    /** The striker in play: online, the one equipped on the player's account decides the physics. */
    private fun activeStrikerConfig(): StrikerConfig {
        val id = session?.myStrikerId ?: _gameState.value.selectedStrikerId
        return _strikers.value.find { it.id == id } ?: _strikers.value.first()
    }

    private fun standardStriker(): StrikerConfig = _strikers.value.first()

    /** People shoot with their equipped striker; bots always use the standard one. */
    private fun shooterStriker(state: GameState, slot: PlayerSlot = state.currentTurn): StrikerConfig =
        if (state.player(slot).isBot) standardStriker() else activeStrikerConfig()

    /** Strike power multiplier: the striker's (only with powers on, except its catalogue power) and the die's. */
    private fun shotPowerMultiplier(state: GameState): Float {
        val striker = shooterStriker(state)
        val base = when {
            state.mode == GameMode.TRICK_SHOTS || state.mode == GameMode.LUCKY_SHOT -> standardStriker()
            state.powersActive -> striker
            else -> standardStriker()
        }
        return Powers.powerMultiplier(base, state.dice?.face)
    }

    private fun strikerMassFor(state: GameState, slot: PlayerSlot): Float =
        if (state.powersActive) Powers.strikerMass(shooterStriker(state, slot)) else BoardGeometry.STRIKER_MASS

    private fun equippedCoinSet(): CoinSet =
        _coinSets.value.find { it.id == _gameState.value.selectedCoinSetId } ?: _coinSets.value.first()

    private fun equippedDice(): DiceSkin =
        _dice.value.find { it.id == _gameState.value.selectedDiceId } ?: _dice.value.first()

    private fun activeBoardTheme(): BoardTheme =
        _boards.value.find { it.id == _gameState.value.selectedBoardId } ?: _boards.value.first()

    // endregion

    // region Dice Carrom

    /**
     * Rolls the die for the current shooter. People roll with the die they equipped (its power
     * applies when powers are on); bots roll a fair die, then plan their shot around the face.
     */
    fun rollDice(forBot: Boolean = false) {
        val state = _gameState.value
        val dice = state.dice ?: return
        if (state.isGameOver || dice.rolling || state.turnState == TurnState.MOVING) return
        if (state.isAiTurn != forBot) return
        val reroll = dice.face != null
        if (reroll && (forBot || dice.rerollsLeft <= 0)) return

        val power = if (!forBot && state.powersActive) equippedDice().power else DicePower.FAIR
        _gameState.update {
            it.copy(dice = it.dice?.copy(rolling = true, rerollsLeft = it.dice.rerollsLeft - if (reroll) 1 else 0))
        }
        sound.playClick()
        haptic.vibrateTick()
        diceJob?.cancel()
        diceJob = viewModelScope.launch {
            delay(if (forBot) BOT_ROLL_DELAY_MILLIS + DICE_ROLL_MILLIS else DICE_ROLL_MILLIS)
            val face = Powers.roll(power, random)
            _gameState.update { it.copy(dice = it.dice?.copy(face = face, rolling = false, rollId = it.dice.rollId + 1)) }
            haptic.vibrateStrike()
            val multiplier = Powers.scoreMultiplier(face, power)
            showToast(
                if (face == DiceFace.DOUBLE && multiplier > 2) "Golden Double! Points ×$multiplier"
                else "${face.title}: ${face.summary}"
            )
            refreshAimPreview()
            if (_gameState.value.isAiTurn) {
                delay(BOT_READ_FACE_MILLIS)
                if (_gameState.value.isAiTurn && _gameState.value.turnState != TurnState.MOVING) scheduleAIShot()
            }
        }
    }

    // endregion

    // region Turn evaluation

    private fun finishShot() {
        when (_gameState.value.mode) {
            GameMode.ONLINE -> return finishOnlineShot()
            GameMode.LUCKY_SHOT -> return finishLuckyShot()
            GameMode.TIME_ATTACK -> return finishTimeAttackShot()
            GameMode.DISC_POOL -> return finishDiscPoolShot()
            else -> Unit
        }
        val state = _gameState.value
        val shooter = state.currentTurn
        val strikerPocketed = _striker.value?.isPocketed == true
        val pocketedDiscs = pocketedThisShot.filter { it.type != PieceType.STRIKER }
        pocketedThisShot.clear()

        val face = state.dice?.face
        val diePower = if (!state.player(shooter).isBot && state.powersActive) equippedDice().power else DicePower.FAIR
        val outcome = CarromRules.evaluateShot(
            shooter = shooter,
            pocketed = pocketedDiscs.map { it.type },
            strikerPocketed = strikerPocketed,
            queen = QueenStatus(state.queenPottedBy, state.queenNeedsCover, state.queenCovered),
            scoreMultiplier = Powers.scoreMultiplier(face, diePower),
            bonusTurn = face == DiceFace.BONUS_TURN
        )

        if (outcome.returnQueenToCenter) respotQueen()
        outcome.announcement?.let(::showToast)
        recordCareerStats(shooter, pocketedDiscs.size, outcome.queenCoveredNow)

        _gameState.update {
            val p = it.player(shooter)
            it.withPlayer(
                shooter,
                p.copy(
                    score = (p.score + outcome.scoreDelta).coerceAtLeast(0),
                    fouls = p.fouls + if (outcome.isFoul) 1 else 0
                )
            ).copy(
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
            scores = updated.players.map { it.score },
            teams = if (updated.isDoubles) updated.players.map { it.team } else null
        )
        if (winner != null) return declareWinner(winner)

        startTurn(CarromRules.nextShooter(updated.mode, shooter, outcome.keepsTurn, updated.playerCount))
    }

    private fun declareWinner(winner: PlayerSlot) {
        _gameState.update { it.copy(isGameOver = true, winner = winner, turnClock = null) }
        _aimPreview.value = null
        val state = _gameState.value
        val p1Won = winner == PlayerSlot.PLAYER1 ||
            (state.isDoubles && state.player(winner).team == state.player1.team)
        handleGameEnd(p1Won)
    }

    /** Disc Pool: each side pockets its own colour; see [DiscPoolRules]. */
    private fun finishDiscPoolShot() {
        val state = _gameState.value
        val shooter = state.currentTurn
        val colour = state.player(shooter).assignedColor ?: PieceType.WHITE
        val strikerPocketed = _striker.value?.isPocketed == true
        val pocketedDiscs = pocketedThisShot.filter { it.type != PieceType.STRIKER }
        pocketedThisShot.clear()

        val outcome = DiscPoolRules.evaluate(
            shooter = shooter,
            colour = colour,
            pocketed = pocketedDiscs.map { it.type },
            strikerPocketed = strikerPocketed,
            queen = QueenStatus(state.queenPottedBy, state.queenNeedsCover, state.queenCovered),
            whitesLeft = discsOnBoard(PieceType.WHITE),
            blacksLeft = discsOnBoard(PieceType.BLACK)
        )
        respotDiscs(PieceType.WHITE, outcome.respotWhite, preferred = pocketedDiscs)
        respotDiscs(PieceType.BLACK, outcome.respotBlack, preferred = pocketedDiscs)
        if (outcome.returnQueenToCenter) respotQueen()
        outcome.announcement?.let(::showToast)
        recordCareerStats(shooter, pocketedDiscs.count { it.type == colour }, outcome.queenCoveredNow)

        val whites = discsOnBoard(PieceType.WHITE)
        val blacks = discsOnBoard(PieceType.BLACK)
        _gameState.update { s ->
            s.copy(
                players = s.players.mapIndexed { i, p ->
                    val left = if (p.assignedColor == PieceType.BLACK) blacks else whites
                    p.copy(
                        score = DiscPoolRules.DISCS_PER_COLOUR - left,
                        fouls = p.fouls + if (outcome.isFoul && i == shooter.index) 1 else 0
                    )
                },
                queenPottedBy = outcome.queen.pottedBy,
                queenNeedsCover = outcome.queen.awaitingCover,
                queenCovered = outcome.queen.covered
            )
        }
        refreshBoardSummary()

        val winningColour = DiscPoolRules.winningColour(whites, blacks, colour)
        if (winningColour != null) {
            val players = _gameState.value.players
            val winner = if (colour == winningColour) shooter
            else PlayerSlot.of(players.indexOfFirst { it.assignedColor == winningColour }.coerceAtLeast(0))
            return declareWinner(winner)
        }
        startTurn(CarromRules.nextShooter(state.mode, shooter, outcome.keepsTurn, state.playerCount))
    }

    private fun discsOnBoard(type: PieceType): Int = _pieces.value.count { it.type == type && !it.isPocketed }

    /** Returns [count] pocketed discs of [type] to free spots near the centre, those just pocketed first. */
    private fun respotDiscs(type: PieceType, count: Int, preferred: List<Piece>) {
        if (count <= 0) return
        val pieces = _pieces.value
        val candidates = (preferred.filter { it.type == type } + pieces.filter { it.type == type && it.isPocketed })
            .distinct()
            .filter { it.isPocketed }
        for (disc in candidates.take(count)) {
            val spot = CarromPhysicsEngine.findFreeSpot(pieces, disc.radius, ignore = disc)
            disc.isPocketed = false
            disc.pocketProgress = 1f
            disc.pocketId = -1
            disc.x = spot.x
            disc.y = spot.y
            disc.vx = 0f
            disc.vy = 0f
        }
    }

    /** Time Attack: bank the points, dock time for fouls, end on a clear board or at the buzzer. */
    private fun finishTimeAttackShot() {
        val state = _gameState.value
        val status = state.timeAttack ?: return
        val strikerPocketed = _striker.value?.isPocketed == true
        val pocketedDiscs = pocketedThisShot.filter { it.type != PieceType.STRIKER }
        pocketedThisShot.clear()
        val points = pocketedDiscs.sumOf { if (it.type == PieceType.QUEEN) TIME_ATTACK_QUEEN_POINTS else PieceFactory.pointsForType(it.type) }
        recordCareerStats(PlayerSlot.PLAYER1, pocketedDiscs.size, queenCovered = false)

        var deadline = status.deadlineMillis
        if (strikerPocketed) {
            deadline -= TIME_ATTACK_FOUL_MILLIS
            showToast("Foul! −${TIME_ATTACK_FOUL_MILLIS / 1000}s")
        } else if (points > 0) {
            showToast("+$points")
        }
        val p = state.player1
        _gameState.update {
            it.withPlayer(PlayerSlot.PLAYER1, p.copy(score = p.score + points, fouls = p.fouls + if (strikerPocketed) 1 else 0)).copy(
                timeAttack = status.copy(deadlineMillis = deadline, pocketed = status.pocketed + pocketedDiscs.size),
                turnClock = TurnClock(deadline, status.totalSeconds)
            )
        }
        refreshBoardSummary()

        val cleared = _pieces.value.none { !it.isPocketed }
        val remaining = deadline - clock()
        when {
            cleared -> {
                val bonus = (remaining.coerceAtLeast(0L) / 1000L).toInt() * TIME_ATTACK_BONUS_PER_SECOND
                if (bonus > 0) {
                    _gameState.update { it.withPlayer(PlayerSlot.PLAYER1, it.player1.copy(score = it.player1.score + bonus)) }
                    showToast("Board cleared! Time bonus +$bonus")
                }
                endTimeAttack()
            }
            remaining <= 0L -> endTimeAttack()
            else -> {
                if (strikerPocketed) armTimeAttackClock()
                startTurn(PlayerSlot.PLAYER1, offset = state.strikerBaselineOffset, power = state.strikerPower)
            }
        }
    }

    /** Ends the run when the clock runs out, or right after the shot still rolling at the buzzer. */
    private fun armTimeAttackClock() {
        timeAttackJob?.cancel()
        val deadline = _gameState.value.timeAttack?.deadlineMillis ?: return
        timeAttackJob = viewModelScope.launch {
            delay((deadline - clock()).coerceAtLeast(0L))
            val state = _gameState.value
            if (state.mode != GameMode.TIME_ATTACK || state.isGameOver) return@launch
            if (state.turnState != TurnState.MOVING) endTimeAttack()
        }
    }

    private fun endTimeAttack() {
        timeAttackJob?.cancel()
        val state = _gameState.value
        val status = state.timeAttack ?: return
        val score = state.player1.score
        val record = score > status.best
        if (record) repository.setBest(BEST_TIME_ATTACK, score)
        _aimPreview.value = null
        _gameState.update {
            it.copy(
                isGameOver = true,
                winner = PlayerSlot.PLAYER1,
                turnClock = null,
                timeAttack = status.copy(best = maxOf(score, status.best))
            )
        }
        if (record && score > 0) {
            sound.playVictory()
            showToast("New best: $score!")
        }
        _playerStats.update { it.copy(matchesPlayed = it.matchesPlayed + 1) }
        val coins = score / TIME_ATTACK_POINTS_PER_COIN
        _gameState.update { it.copy(matchCoins = coins) }
        awardRewards(coins = coins, xp = TIME_ATTACK_XP)
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
            aiColor = state.player(state.currentTurn).assignedColor,
            queenNeedsCover = state.queenNeedsCover,
            queenPottedByAI = state.queenPottedBy == state.currentTurn,
            difficulty = state.aiDifficulty,
            seat = state.currentSeat,
            random = random
        )
        // The plan assumes a standard strike; a Power Surge roll would overshoot, so the bot eases off.
        val power = (plan.power / shotPowerMultiplier(state)).coerceIn(BoardGeometry.MIN_POWER, BoardGeometry.MAX_POWER)
        aiDirector.begin(
            from = ShotAim(state.strikerBaselineOffset, state.strikerAimAngle, state.strikerPower),
            to = ShotAim(plan.baselineFraction, plan.aimAngle, power)
        )
        requestFrames()
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

    /** Blitz: the human gets [BLITZ_SHOT_MILLIS] per shot; the bot keeps its own pace. */
    private fun startShotClock(slot: PlayerSlot) {
        turnJob?.cancel()
        if (_gameState.value.player(slot).isBot) {
            _gameState.update { it.copy(turnClock = null) }
            return
        }
        _gameState.update { it.copy(turnClock = TurnClock(clock() + BLITZ_SHOT_MILLIS, BLITZ_SHOT_MILLIS / 1000f)) }
        turnJob = viewModelScope.launch {
            delay(BLITZ_SHOT_MILLIS)
            val state = _gameState.value
            if (state.mode != GameMode.BLITZ || state.isGameOver || state.currentTurn != slot ||
                state.turnState == TurnState.MOVING
            ) return@launch
            haptic.vibrateStrike()
            showToast("Shot clock! The turn passes")
            startTurn(slot.next(state.playerCount))
        }
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
                _gameState.update { it.copy(matchCoins = TRICK_SHOT_REWARD_COINS) }
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

    private fun finishLuckyShot() {
        pocketedThisShot.clear()
        val disc = _pieces.value.firstOrNull { it.id == LuckyShot.DISC_ID } ?: return
        val prize = LuckyShot.prizeFor(disc)
        val day = LuckyShot.dayIndex(clock())
        val used = repository.getDailyCount(LuckyShot.COUNTER, day) + 1
        repository.setDailyCount(LuckyShot.COUNTER, day, used)
        val left = (LuckyShot.DAILY_ATTEMPTS - used).coerceAtLeast(0)

        awardRewards(coins = prize, xp = LUCKY_SHOT_XP)
        if (!disc.isPocketed) effects.particles.spawnPocketVortex(disc.x, disc.y)
        if (prize >= LuckyShot.RINGS[1].prize) sound.playVictory()
        showToast(
            when {
                disc.isPocketed -> "Pocketed! +$prize coins"
                prize >= LuckyShot.RINGS[0].prize -> "Bullseye! +$prize coins"
                prize > LuckyShot.CONSOLATION -> "In the rings! +$prize coins"
                else -> "So close. +$prize coins"
            }
        )
        _gameState.update {
            it.copy(luckyShot = LuckyShotStatus(left, (it.luckyShot?.coinsWon ?: 0) + prize, prize))
        }

        if (left == 0) {
            _aimPreview.value = null
            _gameState.update { it.copy(isGameOver = true, winner = PlayerSlot.PLAYER1) }
            return
        }
        // Let the result sink in, then set up the next attempt.
        turnJob = viewModelScope.launch {
            delay(LUCKY_SHOT_PAUSE_MILLIS)
            if (_gameState.value.mode != GameMode.LUCKY_SHOT) return@launch
            _pieces.value = LuckyShot.setup()
            effects.clear()
            refreshBoardSummary()
            startTurn(PlayerSlot.PLAYER1)
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
            val coins = (MATCH_WIN_COINS * Powers.winCoinMultiplier(matchCoinPower)).toInt()
            _gameState.update { it.copy(matchCoins = coins) }
            awardRewards(coins = coins, xp = MATCH_WIN_XP)
        } else {
            repository.savePlayerStats(_playerStats.value)
        }
    }

    // endregion

    // region Online match

    /** Joins [snapshot]'s match (just paired, or resumed after reconnecting). */
    private fun enterOnlineMatch(snapshot: SnapshotDto) {
        val me = online?.playerId ?: return
        val seat = snapshot.seats.indexOfFirst { it.playerId == me }
        if (seat < 0 || snapshot.seats.size != 2) return
        if (session?.matchId == snapshot.id) return onMatchUpdate(MatchUpdate.Sync(snapshot))

        val view = SeatView(seat)
        tableTuning = CarromPhysicsEngine.PhysicsTuning.STANDARD
        shotTuning = tableTuning
        matchCoinPower = CoinPower.BALANCED
        lastConfig = null
        resetBoard(view.piecesOf(snapshot))
        val mine = snapshot.seats[seat]
        val theirs = snapshot.seats[1 - seat]
        val next = OnlineSession(snapshot.id, view, mine.strikerId)
        session = next
        _gameState.update {
            it.freshMatch(GameMode.ONLINE).copy(
                online = OnlineMatchStatus(
                    matchId = snapshot.id,
                    ranked = snapshot.ranked,
                    arena = snapshot.arena,
                    myRating = mine.rating,
                    opponentRating = theirs.rating,
                    opponentPlayerId = theirs.playerId,
                    opponentConnected = theirs.connected
                )
            )
        }
        applySnapshot(next, snapshot)
        haptic.vibrateStrike()
        showToast(
            when {
                snapshot.turn > 0 -> "Match resumed against ${theirs.name}"
                snapshot.current == seat -> "You break first"
                else -> "${theirs.name} breaks first"
            }
        )
    }

    private fun onRealtimeEvent(event: RealtimeEvent) {
        when (event) {
            is RealtimeEvent.MatchStarted -> enterOnlineMatch(localized(event.snapshot))
            is RealtimeEvent.ShotPlayed ->
                onMatchUpdate(MatchUpdate.Shot(event.shot.copy(snapshot = localized(event.shot.snapshot))))
            is RealtimeEvent.TurnPassed ->
                onMatchUpdate(MatchUpdate.Turn(event.turn.copy(snapshot = localized(event.turn.snapshot))))
            is RealtimeEvent.MatchEnded -> onMatchUpdate(MatchUpdate.End(event.end))
            is RealtimeEvent.OpponentAim -> onOpponentAim(event.aim)
            is RealtimeEvent.Emote -> showEmote(event.emote)
            is RealtimeEvent.Presence -> onPresence(event.presence)
            // Friends, invites and the queue belong to the lobby.
            else -> Unit
        }
    }

    /** Re-expresses the snapshot's deadline on this device's clock, whatever its skew. */
    private fun localized(snapshot: SnapshotDto): SnapshotDto {
        val now = clock()
        return snapshot.copy(deadline = now + (snapshot.deadline - snapshot.serverTime), serverTime = now)
    }

    private fun onMatchUpdate(update: MatchUpdate) {
        val s = session ?: return
        if (s.ended || update.snapshot.id != s.matchId) return
        if (update is MatchUpdate.Shot && update.event.seat == s.view.mySeat && update.event.turn == s.shotInFlight) {
            // The server's verdict on the shot already animating here.
            s.confirmed = update.event
            if (s.awaitingConfirmation) settleOwnShot(s)
            return
        }
        // The server moved past the turn our shot was for without playing it (the clock ran out,
        // or the match ended): stop waiting for a result that will never come.
        val superseded = update is MatchUpdate.End || update.snapshot.turn > s.shotInFlight
        if (s.shotInFlight != NO_TURN && s.confirmed == null && superseded) {
            s.shotInFlight = NO_TURN
            s.awaitingConfirmation = false
        }
        s.updates.addLast(update)
        drainMatchUpdates()
    }

    /** Applies queued server updates in order, pausing while a shot animates or awaits its result. */
    private fun drainMatchUpdates() {
        val s = session ?: return
        while (!isSimulating && !s.awaitingConfirmation && !s.ended) {
            when (val update = s.updates.removeFirstOrNull() ?: return) {
                is MatchUpdate.Shot -> if (update.event.turn >= s.turn) replayShot(s, update.event)
                is MatchUpdate.Turn -> if (update.snapshot.turn > s.turn) {
                    val mine = update.event.timedOutSeat == s.view.mySeat
                    showToast(if (mine) "Time's up! Your turn passed" else "${_gameState.value.player2.name} ran out of time")
                    applySnapshot(s, update.snapshot)
                }
                is MatchUpdate.Sync -> {
                    val stuck = _gameState.value.turnState == TurnState.MOVING
                    if (update.snapshot.turn > s.turn || stuck) applySnapshot(s, update.snapshot) else refreshMatchDetails(s, update.snapshot)
                }
                is MatchUpdate.End -> endOnlineMatch(s, update.event)
            }
        }
    }

    /** Plays a shot the server has already resolved (the opponent's, or ours from another device). */
    private fun replayShot(s: OnlineSession, shot: ShotEventDto) {
        val aim = s.view.toLocal(shot.input)
        val slot = s.view.slotOf(shot.seat)
        val offset = aim.baselineOffset.coerceIn(BoardGeometry.MIN_BASELINE_FRACTION, BoardGeometry.MAX_BASELINE_FRACTION)
        s.remoteAim = null
        s.replaying = shot
        _striker.value = PieceFactory.createStriker(offset, isBottom = slot == PlayerSlot.PLAYER1)
        shotTuning = CarromPhysicsEngine.PhysicsTuning.STANDARD
        _gameState.update {
            it.copy(currentTurn = slot, strikerBaselineOffset = offset, strikerAimAngle = aim.angle, strikerPower = aim.power)
        }
        launchStriker(shot.strikerPower)
    }

    private fun sendOnlineShot(s: OnlineSession) {
        val source = online ?: return
        val state = _gameState.value
        val input = s.view.toServer(ShotAim(state.strikerBaselineOffset, state.strikerAimAngle, state.strikerPower))
        val turn = s.turn
        s.shotInFlight = turn
        s.confirmed = null
        aimRelayJob?.cancel()
        viewModelScope.launch {
            try {
                source.shoot(s.matchId, turn, input)
            } catch (e: ApiException) {
                onShotRejected(s, turn, e)
            }
        }
    }

    private fun onShotRejected(s: OnlineSession, turn: Int, error: ApiException) {
        if (session !== s || s.shotInFlight != turn) return
        s.shotInFlight = NO_TURN
        s.confirmed = null
        showToast(error.message ?: "Shot not accepted")
        requestResync()
        if (s.awaitingConfirmation) {
            s.awaitingConfirmation = false
            drainMatchUpdates()
        }
    }

    private fun finishOnlineShot() {
        pocketedThisShot.clear()
        val s = session ?: return
        val replayed = s.replaying
        when {
            replayed != null -> {
                s.replaying = null
                applyShotResult(s, replayed)
            }
            s.confirmed != null -> return settleOwnShot(s)
            s.shotInFlight != NO_TURN -> {
                s.awaitingConfirmation = true
                _gameState.update { it.copy(online = it.online?.copy(syncing = true)) }
            }
        }
        drainMatchUpdates()
    }

    private fun settleOwnShot(s: OnlineSession) {
        val shot = s.confirmed ?: return
        s.confirmed = null
        s.shotInFlight = NO_TURN
        s.awaitingConfirmation = false
        applyShotResult(s, shot)
        drainMatchUpdates()
    }

    private fun applyShotResult(s: OnlineSession, shot: ShotEventDto) {
        shot.result.announcement?.let(::showToast)
        applySnapshot(s, shot.snapshot)
    }

    /** Moves the board, scores, queen and turn to the server's [snapshot]. */
    private fun applySnapshot(s: OnlineSession, snapshot: SnapshotDto) {
        if (!s.view.reconcile(_pieces.value, snapshot, glide)) {
            glide.cancel()
            _pieces.value = s.view.piecesOf(snapshot)
        }
        s.turn = snapshot.turn
        s.remoteAim = null
        refreshBoardSummary()
        refreshMatchDetails(s, snapshot)
        if (snapshot.phase == PHASE_PLAYING) startTurn(s.view.slotOf(snapshot.current)) else requestFrames()
    }

    /** Scores, queen, presence and shot clock from [snapshot], without touching the discs. */
    private fun refreshMatchDetails(s: OnlineSession, snapshot: SnapshotDto) {
        val mine = snapshot.seats[s.view.mySeat]
        val theirs = snapshot.seats[1 - s.view.mySeat]
        _gameState.update {
            it.copy(
                players = listOf(
                    PlayerData(mine.name, SeatView.monogram(mine.name), mine.score, fouls = mine.fouls),
                    PlayerData(theirs.name, SeatView.monogram(theirs.name), theirs.score, fouls = theirs.fouls)
                ),
                queenPottedBy = snapshot.queen.pottedBy?.let(s.view::slotOf),
                queenNeedsCover = snapshot.queen.awaitingCover,
                queenCovered = snapshot.queen.covered,
                turnClock = if (snapshot.phase == PHASE_PLAYING) TurnClock(snapshot.deadline, snapshot.turnSeconds) else null,
                online = it.online?.copy(syncing = false, opponentConnected = theirs.connected)
            )
        }
    }

    private fun endOnlineMatch(s: OnlineSession, end: EndEventDto) {
        s.ended = true
        s.updates.clear()
        applySnapshot(s, end.snapshot)
        val reward = end.rewards.getOrNull(s.view.mySeat)
        val won = end.winner == s.view.mySeat
        _aimPreview.value = null
        _gameState.update {
            it.copy(
                isGameOver = true,
                winner = s.view.slotOf(end.winner),
                turnClock = null,
                online = it.online?.copy(
                    syncing = false,
                    result = reward?.let { r -> OnlineResult(end.reason, r.coins, r.xp, r.ratingBefore, r.ratingAfter, r.leveledUp) }
                )
            )
        }
        if (won) sound.playVictory()
    }

    /** Our match finished while we were away (e.g. the opponent won on time). */
    private fun endMissedMatch(s: OnlineSession) {
        s.ended = true
        s.updates.clear()
        _aimPreview.value = null
        _gameState.update { it.copy(isGameOver = true, winner = null, turnClock = null, online = it.online?.copy(syncing = false)) }
        showToast("The match ended while you were away")
    }

    private fun resumeOnlineMatch() {
        val source = online ?: return
        viewModelScope.launch {
            val snapshot = try {
                source.resume()
            } catch (e: ApiException) {
                return@launch
            }
            val s = session
            when {
                snapshot != null && snapshot.phase == PHASE_PLAYING -> enterOnlineMatch(localized(snapshot))
                s != null && !s.ended -> endMissedMatch(s)
            }
        }
    }

    /** Asks the server for the full match state, e.g. after it refused a shot. */
    private fun requestResync() {
        val source = online ?: return
        val s = session ?: return
        viewModelScope.launch {
            val snapshot = try {
                source.resume()
            } catch (e: ApiException) {
                return@launch // Reconnecting will resume the match.
            }
            if (session !== s) return@launch
            if (snapshot == null || snapshot.id != s.matchId) endMissedMatch(s) else onMatchUpdate(MatchUpdate.Sync(localized(snapshot)))
        }
    }

    private fun onOpponentAim(aim: AimEventDto) {
        val s = session ?: return
        val state = _gameState.value
        if (s.ended || s.view.slotOf(aim.seat) != PlayerSlot.PLAYER2 || !state.isRemoteTurn ||
            state.turnState == TurnState.MOVING || s.replaying != null
        ) return
        s.remoteAim = s.view.toLocal(ShotInputDto(aim.offset, aim.angle, aim.power))
        requestFrames()
    }

    /** Eases the opponent's striker toward their latest aim, so 20 Hz updates look continuous. */
    private fun followRemoteAim(target: ShotAim, dtSeconds: Float) {
        val s = session ?: return
        val state = _gameState.value
        if (!state.isRemoteTurn || state.turnState == TurnState.MOVING) {
            s.remoteAim = null
            return
        }
        val k = 1f - exp(-dtSeconds * AIM_FOLLOW_RATE)
        val turn = AiShotDirector.shortestTurn(state.strikerAimAngle, target.angle)
        val arrived = abs(state.strikerBaselineOffset - target.baselineOffset) < 0.002f &&
            abs(turn) < 0.003f && abs(state.strikerPower - target.power) < 0.3f
        val aim = if (arrived) {
            s.remoteAim = null
            target
        } else {
            ShotAim(
                AiShotDirector.lerp(state.strikerBaselineOffset, target.baselineOffset, k),
                state.strikerAimAngle + turn * k,
                AiShotDirector.lerp(state.strikerPower, target.power, k)
            )
        }
        setStrikerBaselineOffset(aim.baselineOffset)
        setStrikerAim(aim.angle, aim.power)
    }

    /** Shares our aim with the opponent, at most every [AIM_RELAY_MILLIS] (the last one always goes). */
    private fun relayAim() {
        val s = session ?: return
        val source = online ?: return
        val state = _gameState.value
        if (s.ended || state.mode != GameMode.ONLINE || state.currentTurn != PlayerSlot.PLAYER1 || !state.canAim) return
        if (aimRelayJob?.isActive == true) return // A pending send will carry the latest aim.
        val wait = (lastAimRelayAt + AIM_RELAY_MILLIS - clock()).coerceAtLeast(0L)
        aimRelayJob = viewModelScope.launch {
            if (wait > 0) delay(wait)
            lastAimRelayAt = clock()
            val latest = _gameState.value
            if (session !== s || !latest.canAim) return@launch
            source.aim(s.matchId, s.view.toServer(ShotAim(latest.strikerBaselineOffset, latest.strikerAimAngle, latest.strikerPower)))
        }
    }

    fun sendEmote(emote: String) {
        val s = session ?: return
        val source = online ?: return
        viewModelScope.launch {
            try {
                source.emote(s.matchId, emote)
            } catch (e: ApiException) {
                showToast(e.message ?: "Couldn't send that")
            }
        }
    }

    private fun showEmote(event: EmoteEventDto) {
        val s = session ?: return
        val bubble = EmoteBubble(s.view.slotOf(event.seat), event.emote, ++emoteCounter)
        _gameState.update { it.copy(online = it.online?.copy(emote = bubble)) }
        if (bubble.slot == PlayerSlot.PLAYER2) haptic.vibrateTick()
        emoteJob?.cancel()
        emoteJob = viewModelScope.launch {
            delay(EMOTE_MILLIS)
            _gameState.update { if (it.online?.emote?.id == bubble.id) it.copy(online = it.online?.copy(emote = null)) else it }
        }
    }

    private fun onPresence(presence: PresenceEventDto) {
        val s = session ?: return
        if (s.ended || s.view.slotOf(presence.seat) != PlayerSlot.PLAYER2) return
        val name = _gameState.value.player2.name
        _gameState.update { it.copy(online = it.online?.copy(opponentConnected = presence.connected)) }
        showToast(if (presence.connected) "$name is back" else "$name lost connection")
    }

    /** Concedes the online match in progress. */
    fun resignOnlineMatch() {
        val s = session ?: return
        val source = online ?: return
        if (s.ended) return
        viewModelScope.launch {
            try {
                source.resign(s.matchId)
            } catch (e: ApiException) {
                showToast(e.message ?: "Couldn't resign")
            }
        }
    }

    /** True while an online match is being played (not yet over). */
    val isInLiveOnlineMatch: Boolean get() = session?.ended == false

    /** Starting another game mid-match forfeits it, so the opponent isn't left waiting. */
    private fun abandonOnlineMatch() {
        val s = session ?: return
        session = null
        aimRelayJob?.cancel()
        if (!s.ended) {
            val source = online ?: return
            viewModelScope.launch {
                try {
                    source.resign(s.matchId)
                } catch (_: ApiException) {
                    // The server forfeits it on the turn clock anyway.
                }
            }
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

    /** True until today's free wheel spin has been used. */
    fun canSpinToday(): Boolean = repository.getDailyCount(DailySpin.COUNTER, LuckyShot.dayIndex(clock())) == 0

    /**
     * Uses today's spin: draws the prize, pays it and returns the winning segment, or null when
     * today's spin is gone. Paid up front so closing the app mid-spin never loses the prize.
     */
    fun spinDailyWheel(): Int? {
        if (!canSpinToday()) return null
        repository.setDailyCount(DailySpin.COUNTER, LuckyShot.dayIndex(clock()), 1)
        val index = DailySpin.draw()
        awardRewards(coins = DailySpin.PRIZES[index], xp = 20)
        return index
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

    fun buyCoinSet(set: CoinSet) {
        if (!spendCoins(set.price, set.id, set.isUnlocked)) return
        _coinSets.update { list -> list.map { if (it.id == set.id) it.copy(isUnlocked = true) else it } }
        selectCoinSet(set.id)
        showToast("Unlocked ${set.name}")
    }

    fun buyDice(die: DiceSkin) {
        if (!spendCoins(die.price, die.id, die.isUnlocked)) return
        _dice.update { list -> list.map { if (it.id == die.id) it.copy(isUnlocked = true) else it } }
        selectDice(die.id)
        showToast("Unlocked ${die.name}")
    }

    /** Coin sets change the discs' size and weight, so a new set lands with the next rack. */
    fun selectCoinSet(id: String) {
        if (_coinSets.value.none { it.id == id && it.isUnlocked }) return
        repository.setSelection(SELECTION_COINS, id)
        _gameState.update { it.copy(selectedCoinSetId = id) }
    }

    fun selectDice(id: String) {
        if (_dice.value.none { it.id == id && it.isUnlocked }) return
        repository.setSelection(SELECTION_DICE, id)
        _gameState.update { it.copy(selectedDiceId = id) }
    }

    /** Loadout powers on or off for offline matches; takes effect from the next match. */
    fun togglePowers() {
        val next = !_gameState.value.powersEnabled
        repository.setFlag(FLAG_POWERS, next)
        _gameState.update { it.copy(powersEnabled = next) }
    }

    /** The best Time Attack score so far. */
    fun timeAttackBest(): Int = repository.getBest(BEST_TIME_ATTACK)

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

    fun showToast(msg: String) {
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

    /** Server updates for the match in play, applied strictly in order. */
    private sealed interface MatchUpdate {
        val snapshot: SnapshotDto

        data class Shot(val event: ShotEventDto) : MatchUpdate {
            override val snapshot get() = event.snapshot
        }

        data class Turn(val event: TurnEventDto) : MatchUpdate {
            override val snapshot get() = event.snapshot
        }

        data class End(val event: EndEventDto) : MatchUpdate {
            override val snapshot get() = event.snapshot
        }

        data class Sync(override val snapshot: SnapshotDto) : MatchUpdate
    }

    /** Bookkeeping for one online match; dropped when the player leaves it. */
    private class OnlineSession(val matchId: String, val view: SeatView, val myStrikerId: String) {
        /** The server's turn counter for the turn now being played. */
        var turn = 0

        /** Turn number of our shot while it animates here awaiting the server, else [NO_TURN]. */
        var shotInFlight = NO_TURN

        /** The server's result for [shotInFlight], if it arrived before the local animation ended. */
        var confirmed: ShotEventDto? = null

        /** Our animation finished first; the board waits for [confirmed]. */
        var awaitingConfirmation = false

        /** A server-resolved shot being replayed on the board. */
        var replaying: ShotEventDto? = null
        val updates = ArrayDeque<MatchUpdate>()
        var remoteAim: ShotAim? = null
        var ended = false
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
        const val BLITZ_SHOT_MILLIS = 10_000L
        const val LUCKY_SHOT_XP = 5
        const val LUCKY_SHOT_PAUSE_MILLIS = 1400L
        const val EMOTE_MILLIS = 2800L
        const val AIM_RELAY_MILLIS = 80L
        const val AIM_FOLLOW_RATE = 16f
        const val NO_TURN = -1
        const val PHASE_PLAYING = "playing"
        const val DICE_ROLL_MILLIS = 900L
        const val BOT_ROLL_DELAY_MILLIS = 450L
        const val BOT_READ_FACE_MILLIS = 500L
        const val TIME_ATTACK_MILLIS = 90_000L
        const val TIME_ATTACK_FOUL_MILLIS = 5_000L
        const val TIME_ATTACK_QUEEN_POINTS = 50
        const val TIME_ATTACK_BONUS_PER_SECOND = 2
        const val TIME_ATTACK_POINTS_PER_COIN = 2
        const val TIME_ATTACK_XP = 30
        const val SELECTION_COINS = "coins"
        const val SELECTION_DICE = "dice"
        const val FLAG_POWERS = "powers"
        const val BEST_TIME_ATTACK = "time_attack"
        const val DEFAULT_COIN_SET = "heritage_boxwood"
        const val DEFAULT_DICE = "ivory_die"
        val BOT_NAMES = listOf("Bot Master", "Bot Ace", "Bot Rio", "Bot Zen")

        /** Offline modes where loadout powers apply (when the player has them switched on). */
        val POWER_MODES = setOf(
            GameMode.VS_AI, GameMode.CLASSIC, GameMode.DISC_POOL, GameMode.FREESTYLE, GameMode.PASS_AND_PLAY,
            GameMode.PRACTICE, GameMode.BLITZ, GameMode.DICE, GameMode.TIME_ATTACK
        )
    }
}

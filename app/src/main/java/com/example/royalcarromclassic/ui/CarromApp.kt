package com.example.royalcarromclassic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.royalcarromclassic.data.AIDifficulty
import com.example.royalcarromclassic.data.GameMode
import com.example.royalcarromclassic.ui.online.AccountSheet
import com.example.royalcarromclassic.ui.online.CommunitySheet
import com.example.royalcarromclassic.ui.online.CommunityTab
import com.example.royalcarromclassic.ui.online.ConfirmDialog
import com.example.royalcarromclassic.ui.online.InviteBanner
import com.example.royalcarromclassic.ui.online.OnlineViewModel
import com.example.royalcarromclassic.ui.online.PlayOnlineSheet
import kotlinx.coroutines.flow.collectLatest

/** The two screens: the home page and the table. */
enum class AppScreen { HOME, TABLE }

/** Which sheet or dialog is open over either screen; only one at a time. */
private enum class Overlay { NONE, SETUP, TRICK_SHOTS, WHEEL, LOCKER, RULES, SETTINGS, ACCOUNT, ONLINE, COMMUNITY, RESIGN }

/**
 * The app shell: the home page and the table, the sheets they share, and the game loop. Back on
 * the table returns home (an offline match waits there to be resumed; an online one asks first,
 * because leaving forfeits it).
 */
@Composable
fun CarromApp(
    viewModel: CarromViewModel = viewModel(),
    onlineViewModel: OnlineViewModel = viewModel(),
    platform: PlatformBridge? = null
) {
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val playerStats by viewModel.playerStats.collectAsStateWithLifecycle()
    val strikers by viewModel.strikers.collectAsStateWithLifecycle()
    val boards by viewModel.boards.collectAsStateWithLifecycle()
    val coinSets by viewModel.coinSets.collectAsStateWithLifecycle()
    val dice by viewModel.dice.collectAsStateWithLifecycle()
    val trickShotLevels by viewModel.trickShotLevels.collectAsStateWithLifecycle()
    val online by onlineViewModel.state.collectAsStateWithLifecycle()

    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var setupMode by remember { mutableStateOf(GameMode.PASS_AND_PLAY) }
    val closeOverlay = { overlay = Overlay.NONE }
    // Starting something else mid-way through an online match waits for the player to confirm forfeiting it.
    var pendingLeave by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun leavingMatch(action: () -> Unit) {
        if (viewModel.isInLiveOnlineMatch) pendingLeave = action else action()
    }
    fun play(start: () -> Unit) = leavingMatch {
        start()
        overlay = Overlay.NONE
        screen = AppScreen.TABLE
    }

    // Game loop: advance the simulation once per display frame while the table is showing and
    // something animates. Leaving for the home page pauses a local match mid-shot.
    LaunchedEffect(viewModel, screen) {
        if (screen != AppScreen.TABLE) return@LaunchedEffect
        viewModel.needsFrames.collectLatest { active ->
            if (active) {
                while (true) withFrameNanos(viewModel::onFrame)
            }
        }
    }

    // Lobby notices appear as the table's toast.
    LaunchedEffect(online.message) {
        online.message?.let {
            viewModel.showToast(it)
            onlineViewModel.consumeMessage()
        }
    }

    // A match starting (from the queue, a room or an invite) takes over the screen.
    val matchId = gameState.online?.matchId
    LaunchedEffect(matchId) {
        if (matchId != null) {
            overlay = Overlay.NONE
            screen = AppScreen.TABLE
        }
    }

    BackHandler(enabled = screen == AppScreen.TABLE && overlay == Overlay.NONE) {
        if (viewModel.isInLiveOnlineMatch) overlay = Overlay.RESIGN else screen = AppScreen.HOME
    }

    val boardTheme = boards.find { it.id == gameState.selectedBoardId } ?: boards.first()
    val strikerConfig = strikers.find { it.id == gameState.selectedStrikerId } ?: strikers.first()
    val coinSet = coinSets.find { it.id == gameState.selectedCoinSetId } ?: coinSets.first()
    val diceSkin = dice.find { it.id == gameState.selectedDiceId } ?: dice.first()
    val wheelReady = remember(overlay, playerStats) { viewModel.canSpinToday() }

    val homeActions = remember(viewModel) {
        object : HomeActions {
            override fun quickPlay(difficulty: AIDifficulty) = play { viewModel.startNewGame(GameMode.VS_AI, difficulty) }
            override fun resume() {
                screen = AppScreen.TABLE
            }
            override fun setUpMatch(mode: GameMode) {
                setupMode = mode
                overlay = Overlay.SETUP
            }
            override fun startMode(mode: GameMode, difficulty: AIDifficulty) = play { viewModel.startNewGame(mode, difficulty) }
            override fun openTrickShots() {
                overlay = Overlay.TRICK_SHOTS
            }
            override fun openOnline() {
                overlay = Overlay.ONLINE
            }
            override fun openCommunity() {
                overlay = Overlay.COMMUNITY
            }
            override fun openAccount() {
                overlay = Overlay.ACCOUNT
            }
            override fun openLocker() {
                overlay = Overlay.LOCKER
            }
            override fun openWheel() {
                overlay = Overlay.WHEEL
            }
            override fun openSettings() {
                overlay = Overlay.SETTINGS
            }
            override fun openRules() {
                overlay = Overlay.RULES
            }
            override fun togglePowers() = viewModel.togglePowers()
        }
    }

    Box(Modifier.fillMaxSize()) {
        ClassicBackdrop(Modifier.matchParentSize())

        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.97f)) togetherWith
                    (fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 1.02f))
            },
            label = "screen"
        ) { current ->
            when (current) {
                AppScreen.HOME -> Box(Modifier.fillMaxSize()) {
                    HomeScreen(
                        info = HomeInfo(
                            stats = playerStats,
                            state = gameState,
                            striker = strikerConfig,
                            coinSet = coinSet,
                            die = diceSkin,
                            board = boardTheme,
                            playerName = online.profile?.displayName,
                            signedIn = if (online.available) online.signedIn else null,
                            onlineAvailable = online.available,
                            wheelReady = wheelReady,
                            luckyShotsLeft = remember(overlay, playerStats) { viewModel.luckyShotsLeft() },
                            trickStars = trickShotLevels.sumOf { it.stars },
                            trickStarsMax = trickShotLevels.size * 3,
                            timeAttackBest = remember(playerStats) { viewModel.timeAttackBest() }
                        ),
                        actions = homeActions
                    )
                    ToastBanner(
                        message = gameState.toastMessage,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .safeDrawingPadding()
                            .padding(16.dp)
                    )
                }

                AppScreen.TABLE -> TableScreen(
                    viewModel = viewModel,
                    gameState = gameState,
                    boardTheme = boardTheme,
                    strikerConfig = strikerConfig,
                    coinSet = coinSet,
                    diceSkin = diceSkin,
                    onHome = { if (viewModel.isInLiveOnlineMatch) overlay = Overlay.RESIGN else screen = AppScreen.HOME },
                    onOpenRules = { overlay = Overlay.RULES },
                    onResign = { overlay = Overlay.RESIGN }
                )
            }
        }

        InviteBanner(
            invite = online.invite.takeIf { gameState.online == null || gameState.isGameOver },
            onAccept = { leavingMatch(onlineViewModel::acceptInvite) },
            onDismiss = onlineViewModel::dismissInvite,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(start = 12.dp, end = 12.dp, top = 64.dp)
        )

        when (overlay) {
            Overlay.SETUP -> MatchSetupSheet(
                mode = setupMode,
                initial = viewModel.lastMatchConfig(),
                onStart = { config -> play { viewModel.startMatch(config) } },
                onDismiss = closeOverlay
            )

            Overlay.TRICK_SHOTS -> TrickShotsSheet(
                levels = trickShotLevels,
                onSelectLevel = { level -> play { viewModel.startTrickShotLevel(level) } },
                onDismiss = closeOverlay
            )

            Overlay.WHEEL -> LuckyWheelDialog(
                spinAvailable = viewModel.canSpinToday(),
                onSpin = viewModel::spinDailyWheel,
                onDismiss = closeOverlay
            )

            Overlay.LOCKER -> LockerSheet(
                stats = playerStats,
                strikers = strikers,
                coinSets = coinSets,
                dice = dice,
                boards = boards,
                state = gameState,
                onSelectStriker = viewModel::selectStriker,
                onSelectCoinSet = viewModel::selectCoinSet,
                onSelectDice = viewModel::selectDice,
                onSelectBoard = viewModel::selectBoard,
                onBuyStriker = viewModel::buyStriker,
                onBuyCoinSet = viewModel::buyCoinSet,
                onBuyDice = viewModel::buyDice,
                onBuyBoard = viewModel::buyBoard,
                onTogglePowers = viewModel::togglePowers,
                onDismiss = closeOverlay
            )

            Overlay.RULES -> RulesSheet(onDismiss = closeOverlay)

            Overlay.SETTINGS -> SettingsSheet(
                soundEnabled = gameState.soundEnabled,
                hapticEnabled = gameState.hapticEnabled,
                stats = playerStats,
                onToggleSound = viewModel::toggleSound,
                onToggleHaptics = viewModel::toggleHaptics,
                onOpenRules = { overlay = Overlay.RULES },
                onDismiss = closeOverlay
            )

            Overlay.ACCOUNT -> AccountSheet(
                state = online,
                actions = onlineViewModel,
                socialAuth = platform?.socialAuth,
                onDismiss = closeOverlay
            )

            Overlay.ONLINE -> PlayOnlineSheet(
                state = online,
                actions = onlineViewModel,
                onOpenAccount = { overlay = Overlay.ACCOUNT },
                onShareText = platform?.let { it::shareText },
                onDismiss = closeOverlay
            )

            Overlay.COMMUNITY -> CommunitySheet(
                state = online,
                actions = onlineViewModel,
                initialTab = CommunityTab.FRIENDS,
                onOpenAccount = { overlay = Overlay.ACCOUNT },
                onDismiss = closeOverlay
            )

            Overlay.RESIGN -> ConfirmDialog(
                title = "Resign the match?",
                message = "${gameState.player2.name} wins and takes the pot. Ranked matches count as a loss.",
                confirmLabel = "Resign",
                onConfirm = {
                    viewModel.resignOnlineMatch()
                    screen = AppScreen.HOME
                },
                onDismiss = closeOverlay
            )

            Overlay.NONE -> Unit
        }

        pendingLeave?.let { leave ->
            ConfirmDialog(
                title = "Leave the match?",
                message = "Leaving now forfeits your online match to ${gameState.player2.name}.",
                confirmLabel = "Leave",
                onConfirm = {
                    leave()
                    overlay = Overlay.NONE
                },
                onDismiss = { pendingLeave = null }
            )
        }

        if (screen == AppScreen.TABLE && gameState.isGameOver && overlay == Overlay.NONE && pendingLeave == null) {
            val goHome = {
                if (gameState.mode == GameMode.ONLINE || gameState.mode == GameMode.LUCKY_SHOT) {
                    viewModel.startNewGame(GameMode.VS_AI, gameState.aiDifficulty)
                }
                screen = AppScreen.HOME
            }
            GameOverDialog(
                gameState = gameState,
                nextTrickShotLevel = viewModel.nextTrickShotLevelId(),
                onRematch = when (gameState.mode) {
                    GameMode.ONLINE -> {
                        {
                            viewModel.startNewGame(GameMode.VS_AI, gameState.aiDifficulty)
                            screen = AppScreen.HOME
                            overlay = Overlay.ONLINE
                        }
                    }
                    GameMode.LUCKY_SHOT -> goHome
                    else -> viewModel::restartMatch
                },
                onNextTrickShot = viewModel::startTrickShotLevel,
                onChangeMode = goHome
            )
        }
    }
}

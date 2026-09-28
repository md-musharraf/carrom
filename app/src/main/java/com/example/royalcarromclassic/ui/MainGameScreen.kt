package com.example.royalcarromclassic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.royalcarromclassic.data.EmoteBubble
import com.example.royalcarromclassic.data.GameMode
import com.example.royalcarromclassic.data.GameState
import com.example.royalcarromclassic.data.PlayerSlot
import com.example.royalcarromclassic.data.TurnState
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.components.ChipShape
import com.example.royalcarromclassic.ui.components.Glyph
import com.example.royalcarromclassic.ui.components.GlyphButton
import com.example.royalcarromclassic.ui.components.GlyphIcon
import com.example.royalcarromclassic.ui.components.classicPanel
import com.example.royalcarromclassic.ui.online.AccountSheet
import com.example.royalcarromclassic.ui.online.CommunitySheet
import com.example.royalcarromclassic.ui.online.CommunityTab
import com.example.royalcarromclassic.ui.online.ConfirmDialog
import com.example.royalcarromclassic.ui.online.EMOTES
import com.example.royalcarromclassic.ui.online.InviteBanner
import com.example.royalcarromclassic.ui.online.OnlineViewModel
import com.example.royalcarromclassic.ui.online.PlayOnlineSheet
import com.example.royalcarromclassic.ui.online.emoteText
import kotlinx.coroutines.flow.collectLatest

/** Which overlay (sheet or dialog) is open; only one at a time. */
private enum class Overlay { NONE, MODES, TRICK_SHOTS, WHEEL, SHOP, RULES, SETTINGS, ACCOUNT, ONLINE, COMMUNITY, RESIGN }

/**
 * Main game screen. High-frequency board rendering is isolated in [CarromBoardCanvas]; this
 * composable only recomposes for state the HUD actually shows.
 */
@Composable
fun MainGameScreen(
    viewModel: CarromViewModel = viewModel(),
    onlineViewModel: OnlineViewModel = viewModel(),
    platform: PlatformBridge? = null
) {
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val pieces by viewModel.pieces.collectAsStateWithLifecycle()
    val striker by viewModel.striker.collectAsStateWithLifecycle()
    val aimPreview by viewModel.aimPreview.collectAsStateWithLifecycle()
    val summary by viewModel.boardSummary.collectAsStateWithLifecycle()
    val playerStats by viewModel.playerStats.collectAsStateWithLifecycle()
    val strikers by viewModel.strikers.collectAsStateWithLifecycle()
    val boards by viewModel.boards.collectAsStateWithLifecycle()
    val trickShotLevels by viewModel.trickShotLevels.collectAsStateWithLifecycle()
    val online by onlineViewModel.state.collectAsStateWithLifecycle()

    var overlay by remember { mutableStateOf(Overlay.NONE) }
    val closeOverlay = { overlay = Overlay.NONE }
    var emotesOpen by remember { mutableStateOf(false) }
    // A new game requested mid-match waits for the player to confirm forfeiting it.
    var pendingLeave by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun leavingMatch(action: () -> Unit) {
        if (viewModel.isInLiveOnlineMatch) pendingLeave = action else action()
    }

    // Game loop: advance the simulation once per display frame, only while something animates.
    LaunchedEffect(viewModel) {
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

    // A match starting (from the queue, a room or an invite) takes over the table.
    val matchId = gameState.online?.matchId
    LaunchedEffect(matchId) {
        if (matchId != null) {
            overlay = Overlay.NONE
            emotesOpen = false
        }
    }

    val activeBoardTheme = remember(boards, gameState.selectedBoardId) {
        boards.find { it.id == gameState.selectedBoardId } ?: boards.first()
    }
    val activeStrikerConfig = remember(strikers, gameState.selectedStrikerId) {
        strikers.find { it.id == gameState.selectedStrikerId } ?: strikers.first()
    }
    val wheelReady = remember(overlay, playerStats) { viewModel.canSpinToday() }

    val matchStatus = gameState.online
    val p1Caption = when {
        matchStatus?.syncing == true -> "Syncing"
        gameState.mode == GameMode.PASS_AND_PLAY -> "To play"
        else -> "Your shot"
    }
    val p2Caption = when {
        matchStatus != null && !matchStatus.opponentConnected -> "Reconnecting"
        gameState.turnState == TurnState.MOVING && (gameState.isAiTurn || gameState.isRemoteTurn) -> "Shooting"
        gameState.isAiTurn -> "Thinking"
        gameState.isRemoteTurn -> "Aiming"
        else -> "To play"
    }

    Box(Modifier.fillMaxSize()) {
        ClassicBackdrop(Modifier.matchParentSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopActionBarView(
                stats = playerStats,
                signedIn = if (online.available) online.signedIn else null,
                onOpenAccount = { overlay = Overlay.ACCOUNT },
                onOpenOnline = { overlay = Overlay.ONLINE },
                onOpenModes = { overlay = Overlay.MODES },
                onOpenCommunity = { overlay = Overlay.COMMUNITY },
                onOpenWheel = { overlay = Overlay.WHEEL },
                onOpenShop = { overlay = Overlay.SHOP },
                onOpenSettings = { overlay = Overlay.SETTINGS },
                wheelReady = wheelReady
            )

            BoardStatusStrip(
                summary = summary,
                mode = gameState.mode,
                queenNeedsCover = gameState.queenNeedsCover,
                queenCovered = gameState.queenCovered,
                onOpenModes = { overlay = Overlay.MODES },
                trailing = if (matchStatus != null && !gameState.isGameOver) {
                    {
                        MatchActions(
                            emotesOpen = emotesOpen,
                            onToggleEmotes = { emotesOpen = !emotesOpen },
                            onResign = { overlay = Overlay.RESIGN }
                        )
                    }
                } else {
                    null
                }
            )

            // The table: the opponent's seat above the board, yours below it. The board takes
            // whatever height is left and stays square; the group is centred vertically.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val trickShot = gameState.trickShot
                val luckyShot = gameState.luckyShot
                val p2Active = !gameState.isGameOver && gameState.currentTurn == PlayerSlot.PLAYER2
                when {
                    trickShot != null -> ChallengePlate(trickShot)
                    luckyShot != null -> LuckyShotPlate(luckyShot)
                    gameState.mode == GameMode.PRACTICE -> Unit // Solo: nobody sits opposite.
                    else -> PlayerPlate(
                        player = gameState.player2,
                        accent = CarromPalette.Silver,
                        isActive = p2Active,
                        caption = p2Caption,
                        mirrored = true,
                        clock = gameState.turnClock.takeIf { p2Active },
                        badge = matchStatus?.opponentRating?.toString()
                    )
                }

                Box(Modifier.weight(1f, fill = false), contentAlignment = Alignment.TopCenter) {
                    CarromBoardCanvas(
                        pieces = pieces,
                        striker = striker,
                        boardTheme = activeBoardTheme,
                        strikerConfig = activeStrikerConfig,
                        gameState = gameState,
                        aimPreview = aimPreview,
                        effects = viewModel.effects,
                        frameTick = viewModel.frameTick,
                        onPositionChanged = { viewModel.setStrikerBaselineOffset(it, isManualTouch = true) },
                        onAimChanged = viewModel::setStrikerAim,
                        onShoot = viewModel::executeShot
                    )
                    EmoteBubbleView(matchStatus?.emote, PlayerSlot.PLAYER2, Modifier.align(Alignment.TopEnd).padding(10.dp))
                    EmoteBubbleView(matchStatus?.emote, PlayerSlot.PLAYER1, Modifier.align(Alignment.BottomStart).padding(10.dp))
                    EmotePicker(
                        visible = emotesOpen && matchStatus != null,
                        onPick = {
                            viewModel.sendEmote(it)
                            emotesOpen = false
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(10.dp)
                    )
                    ToastBanner(
                        message = gameState.toastMessage,
                        modifier = Modifier.padding(top = 20.dp, start = 16.dp, end = 16.dp)
                    )
                }

                val p1Active = !gameState.isGameOver && gameState.currentTurn == PlayerSlot.PLAYER1
                PlayerPlate(
                    player = gameState.player1,
                    accent = CarromPalette.Gold,
                    isActive = p1Active,
                    caption = p1Caption,
                    mirrored = false,
                    clock = gameState.turnClock.takeIf { p1Active },
                    badge = matchStatus?.myRating?.toString()
                )
            }

            StrikerControlsView(
                gameState = gameState,
                onPositionChanged = { viewModel.setStrikerBaselineOffset(it, isManualTouch = true) },
                onPowerChanged = { viewModel.setStrikerAim(gameState.strikerAimAngle, it) },
                onNudgeAngle = viewModel::nudgeAimAngle,
                onNudgePower = viewModel::nudgePower,
                onShoot = viewModel::executeShot
            )
        }

        InviteBanner(
            invite = online.invite.takeIf { matchStatus == null || gameState.isGameOver },
            onAccept = { leavingMatch(onlineViewModel::acceptInvite) },
            onDismiss = onlineViewModel::dismissInvite,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(start = 12.dp, end = 12.dp, top = 64.dp)
        )

        when (overlay) {
            Overlay.MODES -> GameModesSheet(
                currentMode = gameState.mode,
                aiDifficulty = gameState.aiDifficulty,
                onSelectMode = { mode, difficulty -> leavingMatch { viewModel.startNewGame(mode, difficulty) } },
                onOpenTrickShots = { overlay = Overlay.TRICK_SHOTS },
                onDismiss = closeOverlay,
                luckyShotsLeft = viewModel.luckyShotsLeft(),
                onOpenOnline = if (online.available) ({ overlay = Overlay.ONLINE }) else null
            )

            Overlay.TRICK_SHOTS -> TrickShotsSheet(
                levels = trickShotLevels,
                onSelectLevel = { level -> leavingMatch { viewModel.startTrickShotLevel(level) } },
                onDismiss = closeOverlay
            )

            Overlay.WHEEL -> LuckyWheelDialog(
                spinAvailable = viewModel.canSpinToday(),
                onSpin = viewModel::spinDailyWheel,
                onDismiss = closeOverlay
            )

            Overlay.SHOP -> ShopBottomSheet(
                stats = playerStats,
                strikers = strikers,
                boards = boards,
                selectedStrikerId = gameState.selectedStrikerId,
                selectedBoardId = gameState.selectedBoardId,
                onSelectStriker = viewModel::selectStriker,
                onSelectBoard = viewModel::selectBoard,
                onBuyStriker = viewModel::buyStriker,
                onBuyBoard = viewModel::buyBoard,
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
                onConfirm = viewModel::resignOnlineMatch,
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

        if (gameState.isGameOver && overlay == Overlay.NONE && pendingLeave == null) {
            val backToTable = { viewModel.startNewGame(GameMode.VS_AI, gameState.aiDifficulty) }
            GameOverDialog(
                gameState = gameState,
                nextTrickShotLevel = viewModel.nextTrickShotLevelId(),
                onRematch = when (gameState.mode) {
                    GameMode.ONLINE -> {
                        {
                            backToTable()
                            overlay = Overlay.ONLINE
                        }
                    }
                    GameMode.LUCKY_SHOT -> backToTable
                    else -> viewModel::restartMatch
                },
                onNextTrickShot = viewModel::startTrickShotLevel,
                onChangeMode = when (gameState.mode) {
                    GameMode.ONLINE -> backToTable
                    GameMode.LUCKY_SHOT -> {
                        {
                            backToTable()
                            overlay = Overlay.MODES
                        }
                    }
                    else -> {
                        {
                            viewModel.restartMatch()
                            overlay = Overlay.MODES
                        }
                    }
                }
            )
        }
    }
}

/** Quick chat and resign, shown in the status strip during an online match. */
@Composable
private fun MatchActions(emotesOpen: Boolean, onToggleEmotes: () -> Unit, onResign: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphButton(
            Glyph.Chat,
            contentDescription = if (emotesOpen) "Close quick chat" else "Quick chat",
            onClick = onToggleEmotes,
            modifier = Modifier.size(30.dp),
            glyphSize = 15.dp,
            tint = if (emotesOpen) CarromPalette.Ivory else CarromPalette.GoldLight
        )
        GlyphButton(Glyph.Flag, contentDescription = "Resign", onClick = onResign, modifier = Modifier.size(30.dp), glyphSize = 14.dp)
    }
}

/** Eight quick-chat phrases in two brass rows. */
@Composable
private fun EmotePicker(visible: Boolean, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.9f),
        exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.95f),
        modifier = modifier
    ) {
        Column(
            Modifier
                .classicPanel(accentAlpha = 0.7f, raised = true)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EMOTES.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (id, text) ->
                        Text(
                            text,
                            style = MaterialTheme.typography.labelMedium,
                            color = CarromPalette.Ivory,
                            maxLines = 1,
                            modifier = Modifier
                                .classicPanel(ChipShape, accentAlpha = 0.35f)
                                .clickable(role = Role.Button) { onPick(id) }
                                .padding(horizontal = 9.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

/** A speech bubble beside [slot]'s side of the board while their emote is showing. */
@Composable
private fun EmoteBubbleView(bubble: EmoteBubble?, slot: PlayerSlot, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = bubble?.takeIf { it.slot == slot },
        transitionSpec = {
            (fadeIn(tween(160)) + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.6f)) togetherWith
                (fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.9f))
        },
        contentKey = { it?.id },
        label = "emoteBubble",
        modifier = modifier
    ) { shown ->
        if (shown != null) {
            Row(
                Modifier
                    .classicPanel(accent = if (slot == PlayerSlot.PLAYER1) CarromPalette.Gold else CarromPalette.Silver, accentAlpha = 0.8f, raised = true)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GlyphIcon(Glyph.Chat, size = 14.dp, tint = CarromPalette.GoldLight)
                Text(emoteText(shown.emote), style = MaterialTheme.typography.titleSmall, color = CarromPalette.Ivory)
            }
        } else {
            Spacer(Modifier.size(1.dp))
        }
    }
}

/**
 * Dark walnut room with a faint brass lattice, drawn once in its own layer so HUD updates
 * never repaint it.
 */
@Composable
private fun ClassicBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier.graphicsLayer()) {
        drawRect(
            Brush.radialGradient(
                listOf(CarromPalette.Mahogany, CarromPalette.Walnut, CarromPalette.Night),
                center = Offset(size.width / 2f, size.height * 0.48f),
                radius = size.maxDimension * 0.65f
            )
        )
        val cell = 34.dp.toPx()
        val lattice = CarromPalette.Gold.copy(alpha = 0.035f)
        var d = -size.height
        while (d < size.width + size.height) {
            drawLine(lattice, Offset(d, 0f), Offset(d + size.height, size.height), 1f)
            drawLine(lattice, Offset(d + size.height, 0f), Offset(d, size.height), 1f)
            d += cell
        }
        drawRect(
            Brush.radialGradient(
                0.55f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.55f),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = size.maxDimension * 0.75f
            )
        )
    }
}

/** Brass-edged announcement ribbon that springs in and fades away. */
@Composable
private fun ToastBanner(message: String?, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = message,
        transitionSpec = {
            (fadeIn(tween(200)) + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.85f) +
                slideInVertically(tween(260)) { -it / 2 }) togetherWith
                (fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.95f) + slideOutVertically(tween(220)) { -it / 3 })
        },
        contentAlignment = Alignment.TopCenter,
        label = "toast",
        modifier = modifier
    ) { text ->
        if (text != null) {
            Row(
                Modifier
                    .classicPanel(accentAlpha = 0.7f, raised = true)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlyphIcon(Glyph.Crown, size = 14.dp, tint = CarromPalette.Gold)
                Text(text, style = MaterialTheme.typography.titleSmall, color = CarromPalette.Ivory, textAlign = TextAlign.Center)
            }
        } else {
            Spacer(Modifier.height(1.dp))
        }
    }
}

/** Display name of whoever won, for dialogs. */
internal fun winnerName(state: GameState): String =
    state.player(state.winner ?: PlayerSlot.PLAYER1).name

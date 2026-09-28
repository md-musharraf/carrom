package com.example.royalcarromclassic.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.royalcarromclassic.data.GameMode
import com.example.royalcarromclassic.data.GameState
import com.example.royalcarromclassic.data.PlayerSlot
import com.example.royalcarromclassic.data.TurnState
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.components.Glyph
import com.example.royalcarromclassic.ui.components.GlyphIcon
import com.example.royalcarromclassic.ui.components.classicPanel
import kotlinx.coroutines.flow.collectLatest

/** Which overlay (sheet or dialog) is open; only one at a time. */
private enum class Overlay { NONE, MODES, TRICK_SHOTS, WHEEL, SHOP, RULES, SETTINGS }

/**
 * Main game screen. High-frequency board rendering is isolated in [CarromBoardCanvas]; this
 * composable only recomposes for state the HUD actually shows.
 */
@Composable
fun MainGameScreen(
    viewModel: CarromViewModel = viewModel()
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

    var overlay by remember { mutableStateOf(Overlay.NONE) }
    val closeOverlay = { overlay = Overlay.NONE }

    // Game loop: advance the simulation once per display frame, only while something animates.
    LaunchedEffect(viewModel) {
        viewModel.needsFrames.collectLatest { active ->
            if (active) {
                while (true) withFrameNanos(viewModel::onFrame)
            }
        }
    }

    val activeBoardTheme = remember(boards, gameState.selectedBoardId) {
        boards.find { it.id == gameState.selectedBoardId } ?: boards.first()
    }
    val activeStrikerConfig = remember(strikers, gameState.selectedStrikerId) {
        strikers.find { it.id == gameState.selectedStrikerId } ?: strikers.first()
    }

    val isBotGame = gameState.mode == GameMode.VS_AI
    val p1Caption = if (gameState.mode == GameMode.PASS_AND_PLAY) "To play" else "Your shot"
    val p2Caption = when {
        !isBotGame -> "To play"
        gameState.turnState == TurnState.MOVING -> "Shooting"
        else -> "Thinking"
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
                onOpenModes = { overlay = Overlay.MODES },
                onOpenTrickShots = { overlay = Overlay.TRICK_SHOTS },
                onOpenWheel = { overlay = Overlay.WHEEL },
                onOpenShop = { overlay = Overlay.SHOP },
                onOpenRules = { overlay = Overlay.RULES },
                onOpenSettings = { overlay = Overlay.SETTINGS }
            )

            BoardStatusStrip(
                summary = summary,
                mode = gameState.mode,
                queenNeedsCover = gameState.queenNeedsCover,
                queenCovered = gameState.queenCovered,
                onOpenModes = { overlay = Overlay.MODES }
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
                when {
                    trickShot != null -> ChallengePlate(trickShot)
                    gameState.mode == GameMode.PRACTICE -> Unit // Solo: nobody sits opposite.
                    else -> PlayerPlate(
                        player = gameState.player2,
                        accent = CarromPalette.Silver,
                        isActive = !gameState.isGameOver && gameState.currentTurn == PlayerSlot.PLAYER2,
                        caption = p2Caption,
                        mirrored = true
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
                    ToastBanner(
                        message = gameState.toastMessage,
                        modifier = Modifier.padding(top = 20.dp, start = 16.dp, end = 16.dp)
                    )
                }

                PlayerPlate(
                    player = gameState.player1,
                    accent = CarromPalette.Gold,
                    isActive = !gameState.isGameOver && gameState.currentTurn == PlayerSlot.PLAYER1,
                    caption = p1Caption,
                    mirrored = false
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

        when (overlay) {
            Overlay.MODES -> GameModesSheet(
                currentMode = gameState.mode,
                aiDifficulty = gameState.aiDifficulty,
                onSelectMode = { mode, difficulty -> viewModel.startNewGame(mode, difficulty) },
                onOpenTrickShots = { overlay = Overlay.TRICK_SHOTS },
                onDismiss = closeOverlay
            )

            Overlay.TRICK_SHOTS -> TrickShotsSheet(
                levels = trickShotLevels,
                onSelectLevel = viewModel::startTrickShotLevel,
                onDismiss = closeOverlay
            )

            Overlay.WHEEL -> LuckyWheelDialog(
                onAwardCoins = { viewModel.awardRewards(coins = it, xp = 20) },
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

            Overlay.NONE -> Unit
        }

        if (gameState.isGameOver && overlay == Overlay.NONE) {
            GameOverDialog(
                gameState = gameState,
                nextTrickShotLevel = viewModel.nextTrickShotLevelId(),
                onRematch = viewModel::restartMatch,
                onNextTrickShot = viewModel::startTrickShotLevel,
                onChangeMode = {
                    viewModel.restartMatch()
                    overlay = Overlay.MODES
                }
            )
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

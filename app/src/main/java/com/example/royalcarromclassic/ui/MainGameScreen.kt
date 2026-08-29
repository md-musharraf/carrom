package com.example.royalcarromclassic.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.royalcarromclassic.data.*

/**
 * Main game screen composable.
 * Optimized to prevent top-level recompositions during physical piece motion.
 */
@Composable
fun MainGameScreen(
    viewModel: CarromViewModel = viewModel()
) {
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val pieces by viewModel.pieces.collectAsStateWithLifecycle()
    val striker by viewModel.striker.collectAsStateWithLifecycle()
    val playerStats by viewModel.playerStats.collectAsStateWithLifecycle()
    val strikers by viewModel.strikers.collectAsStateWithLifecycle()
    val boards by viewModel.boards.collectAsStateWithLifecycle()
    val trickShotLevels by viewModel.trickShotLevels.collectAsStateWithLifecycle()

    var showModesSheet by remember { mutableStateOf(false) }
    var showShopSheet by remember { mutableStateOf(false) }
    var showWheelDialog by remember { mutableStateOf(false) }
    var showTrickShotsSheet by remember { mutableStateOf(false) }
    var showRulesSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val activeBoardTheme = remember(boards, gameState.selectedBoardId) {
        boards.find { it.id == gameState.selectedBoardId } ?: boards.first()
    }
    val activeStrikerConfig = remember(strikers, gameState.selectedStrikerId) {
        strikers.find { it.id == gameState.selectedStrikerId } ?: strikers.first()
    }

    val remainingWhites = remember(pieces) { pieces.count { it.type == PieceType.WHITE && !it.isPocketed } }
    val remainingBlacks = remember(pieces) { pieces.count { it.type == PieceType.BLACK && !it.isPocketed } }
    val isQueenOnBoard = remember(pieces) { pieces.any { it.type == PieceType.QUEEN && !it.isPocketed } }

    Scaffold(
        containerColor = Color(0xFF090D16)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Top Action Navigation Bar
                TopActionBarView(
                    stats = playerStats,
                    soundEnabled = gameState.soundEnabled,
                    onOpenModes = { showModesSheet = true },
                    onOpenTrickShots = { showTrickShotsSheet = true },
                    onOpenWheel = { showWheelDialog = true },
                    onOpenShop = { showShopSheet = true },
                    onOpenRules = { showRulesSheet = true },
                    onOpenSettings = { showSettingsSheet = true }
                )

                // 2. Score Header & Turn Info
                ScoreHeaderView(
                    gameState = gameState,
                    remainingWhites = remainingWhites,
                    remainingBlacks = remainingBlacks,
                    isQueenOnBoard = isQueenOnBoard
                )

                // 3. Central Carrom Board Canvas (Isolated high-frequency render scope)
                CarromBoardCanvas(
                    pieces = pieces,
                    striker = striker,
                    boardTheme = activeBoardTheme,
                    strikerConfig = activeStrikerConfig,
                    gameState = gameState,
                    particles = viewModel.particles,
                    physicsTickFlow = viewModel.physicsTick,
                    onPositionChanged = { viewModel.setStrikerBaselineOffset(it) },
                    onAimChanged = { angle, power -> viewModel.setStrikerAim(angle, power) },
                    onShoot = { viewModel.executeShot() },
                    modifier = Modifier.weight(1f, fill = false)
                )

                // 4. Sleek Baseline Slider & Gesture Status
                StrikerControlsView(
                    gameState = gameState,
                    strikerConfig = activeStrikerConfig,
                    onPositionChanged = { viewModel.setStrikerBaselineOffset(it, isManualTouch = true) },
                    onAimAngleChanged = { viewModel.setStrikerAim(it, gameState.strikerPower) },
                    onPowerChanged = { viewModel.setStrikerAim(gameState.strikerAimAngle, it) },
                    onNudgeAngle = { viewModel.nudgeAimAngle(it) },
                    onNudgePower = { viewModel.nudgePower(it) },
                    onShoot = { viewModel.executeShot() }
                )
            }

            // Toast Notifications Overlay
            AnimatedVisibility(
                visible = gameState.toastMessage != null,
                enter = fadeIn() + slideInVertically { -40 },
                exit = fadeOut() + slideOutVertically { -40 },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 70.dp)
            ) {
                gameState.toastMessage?.let { msg ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xF00F172A))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = msg,
                            color = Color(0xFFFDE68A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Dialogs & Sheets
            GameModesSheet(
                isOpen = showModesSheet,
                currentMode = gameState.mode,
                aiDifficulty = gameState.aiDifficulty,
                onSelectMode = { mode, diff -> viewModel.startNewGame(mode, diff) },
                onDismiss = { showModesSheet = false }
            )

            ShopBottomSheet(
                isOpen = showShopSheet,
                stats = playerStats,
                strikers = strikers,
                boards = boards,
                selectedStrikerId = gameState.selectedStrikerId,
                selectedBoardId = gameState.selectedBoardId,
                onSelectStriker = { viewModel.selectStriker(it) },
                onSelectBoard = { viewModel.selectBoard(it) },
                onBuyStriker = { viewModel.buyStriker(it) },
                onBuyBoard = { viewModel.buyBoard(it) },
                onDismiss = { showShopSheet = false }
            )

            LuckyWheelDialog(
                isOpen = showWheelDialog,
                onAwardCoins = { viewModel.awardRewards(coins = it, xp = 20) },
                onDismiss = { showWheelDialog = false }
            )

            TrickShotsSheet(
                isOpen = showTrickShotsSheet,
                levels = trickShotLevels,
                onSelectLevel = { viewModel.startTrickShotLevel(it) },
                onDismiss = { showTrickShotsSheet = false }
            )

            RulesSheet(
                isOpen = showRulesSheet,
                onDismiss = { showRulesSheet = false }
            )

            SettingsSheet(
                isOpen = showSettingsSheet,
                soundEnabled = gameState.soundEnabled,
                hapticEnabled = gameState.hapticEnabled,
                stats = playerStats,
                onToggleSound = { viewModel.toggleSound() },
                onToggleHaptics = { viewModel.toggleHaptics() },
                onDismiss = { showSettingsSheet = false }
            )

            GameOverDialog(
                gameState = gameState,
                onRematch = { viewModel.startNewGame(gameState.mode, gameState.aiDifficulty) },
                onChangeMode = {
                    viewModel.startNewGame(gameState.mode, gameState.aiDifficulty)
                    showModesSheet = true
                }
            )
        }
    }
}

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
import com.example.royalcarromclassic.data.*
import com.example.royalcarromclassic.engine.DiscPoolRules
import com.example.royalcarromclassic.engine.Powers
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.components.ChipShape
import com.example.royalcarromclassic.ui.components.Glyph
import com.example.royalcarromclassic.ui.components.GlyphButton
import com.example.royalcarromclassic.ui.components.GlyphIcon
import com.example.royalcarromclassic.ui.components.classicPanel
import com.example.royalcarromclassic.ui.online.ConfirmDialog
import com.example.royalcarromclassic.ui.online.EMOTES
import com.example.royalcarromclassic.ui.online.emoteText

/**
 * The table: header, seats, the board as large as the screen allows, the dice tray in Dice Carrom,
 * and the striker controls. High-frequency board rendering is isolated in [CarromBoardCanvas];
 * this composable only recomposes for state the HUD actually shows.
 */
@Composable
fun TableScreen(
    viewModel: CarromViewModel,
    gameState: GameState,
    boardTheme: BoardTheme,
    strikerConfig: StrikerConfig,
    coinSet: CoinSet,
    diceSkin: DiceSkin,
    onHome: () -> Unit,
    onOpenRules: () -> Unit,
    onResign: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pieces by viewModel.pieces.collectAsStateWithLifecycle()
    val striker by viewModel.striker.collectAsStateWithLifecycle()
    val aimPreview by viewModel.aimPreview.collectAsStateWithLifecycle()
    val summary by viewModel.boardSummary.collectAsStateWithLifecycle()

    var emotesOpen by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    val matchStatus = gameState.online
    LaunchedEffect(matchStatus?.matchId) { emotesOpen = false }

    // Powers shown on the table: wide pockets drawn wider, the Precision striker's finer aim.
    val powers = gameState.powersActive
    val pocketScale = if (powers && boardTheme.power == BoardPower.WIDE_POCKETS) Powers.WIDE_POCKET_BOARD_SCALE else 1f
    val humanStriker = powers && !gameState.isAiTurn
    val ability = if (humanStriker) strikerConfig.ability else StrikerAbility.BALANCED

    val p1Caption = when {
        matchStatus?.syncing == true -> "Syncing"
        gameState.mode == GameMode.PASS_AND_PLAY -> "To play"
        else -> "Your shot"
    }
    val p2Caption = when {
        matchStatus != null && !matchStatus.opponentConnected -> "Reconnecting"
        gameState.turnState == TurnState.MOVING && (gameState.isAiTurn || gameState.isRemoteTurn) -> "Shooting"
        gameState.isAiTurn && gameState.needsRoll -> "Rolling"
        gameState.isAiTurn -> "Thinking"
        gameState.isRemoteTurn -> "Aiming"
        else -> "To play"
    }
    val discPool = gameState.mode == GameMode.DISC_POOL

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TableHeader(gameState, summary, onHome = onHome) {
            if (matchStatus != null && !gameState.isGameOver) {
                MatchActions(emotesOpen = emotesOpen, onToggleEmotes = { emotesOpen = !emotesOpen }, onResign = onResign)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(gameState.mode.displayName.uppercase(), style = MaterialTheme.typography.labelMedium, color = CarromPalette.Gold, maxLines = 1)
                    if (matchStatus == null) {
                        GlyphButton(
                            Glyph.Restart, contentDescription = "Restart match",
                            onClick = { if (gameState.shotsPlayed > 0 && !gameState.isGameOver) confirmRestart = true else viewModel.restartMatch() },
                            modifier = Modifier.size(30.dp), glyphSize = 15.dp
                        )
                    }
                    GlyphButton(Glyph.Rules, contentDescription = "Rules", onClick = onOpenRules, modifier = Modifier.size(30.dp), glyphSize = 15.dp)
                }
            }
        }

        // The table: the other seats above the board, the shooter (or you) below it. The board
        // takes whatever height is left and stays square; the group is centred vertically.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val trickShot = gameState.trickShot
            val luckyShot = gameState.luckyShot
            val timeAttack = gameState.timeAttack
            val p2Active = !gameState.isGameOver && gameState.currentTurn == PlayerSlot.PLAYER2
            when {
                trickShot != null -> ChallengePlate(trickShot)
                luckyShot != null -> LuckyShotPlate(luckyShot)
                timeAttack != null -> TimeAttackPlate(timeAttack, gameState.player1.score, running = !gameState.isGameOver)
                gameState.mode == GameMode.PRACTICE -> Unit // Solo: nobody sits opposite.
                gameState.playerCount > 2 -> SeatsRow(gameState)
                else -> PlayerPlate(
                    player = gameState.player2,
                    accent = CarromPalette.Silver,
                    isActive = p2Active,
                    caption = p2Caption,
                    mirrored = true,
                    clock = gameState.turnClock.takeIf { p2Active },
                    badge = matchStatus?.opponentRating?.toString(),
                    discColour = gameState.player2.assignedColor,
                    scoreSuffix = if (discPool) "/${DiscPoolRules.DISCS_PER_COLOUR}" else null
                )
            }

            Box(Modifier.weight(1f, fill = false), contentAlignment = Alignment.TopCenter) {
                CarromBoardCanvas(
                    pieces = pieces,
                    striker = striker,
                    boardTheme = boardTheme,
                    strikerConfig = if (gameState.isAiTurn) viewModel.strikers.value.first() else strikerConfig,
                    coinSet = coinSet,
                    gameState = gameState,
                    aimPreview = aimPreview,
                    effects = viewModel.effects,
                    frameTick = viewModel.frameTick,
                    onPositionChanged = { viewModel.setStrikerBaselineOffset(it, isManualTouch = true) },
                    onAimChanged = viewModel::setStrikerAim,
                    onShoot = viewModel::executeShot,
                    pocketScale = pocketScale,
                    aimMagnetDegrees = Powers.aimMagnetDegrees(ability)
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

            if (gameState.playerCount > 2) {
                // Three or four at the table: the shooter's plate sits by the controls.
                val slot = gameState.currentTurn
                val shooter = gameState.player(slot)
                PlayerPlate(
                    player = shooter,
                    accent = seatAccent(gameState, slot),
                    isActive = !gameState.isGameOver,
                    caption = if (shooter.isBot) p2Caption else "${gameState.currentSeat.name.lowercase()} seat · to play",
                    mirrored = false,
                    clock = gameState.turnClock,
                    discColour = shooter.assignedColor,
                    scoreSuffix = if (discPool) "/${DiscPoolRules.DISCS_PER_COLOUR}" else null
                )
            } else if (gameState.timeAttack == null) {
                val p1Active = !gameState.isGameOver && gameState.currentTurn == PlayerSlot.PLAYER1
                PlayerPlate(
                    player = gameState.player1,
                    accent = CarromPalette.Gold,
                    isActive = p1Active,
                    caption = p1Caption,
                    mirrored = false,
                    clock = gameState.turnClock.takeIf { p1Active },
                    badge = matchStatus?.myRating?.toString(),
                    discColour = gameState.player1.assignedColor,
                    scoreSuffix = if (discPool) "/${DiscPoolRules.DISCS_PER_COLOUR}" else null
                )
            }

            if (gameState.dice != null) DiceTray(gameState, diceSkin, onRoll = { viewModel.rollDice() })
        }

        StrikerControlsView(
            gameState = gameState,
            onPositionChanged = { viewModel.setStrikerBaselineOffset(it, isManualTouch = true) },
            onPowerChanged = { viewModel.setStrikerAim(gameState.strikerAimAngle, it) },
            onNudgeAngle = viewModel::nudgeAimAngle,
            onNudgePower = viewModel::nudgePower,
            onShoot = viewModel::executeShot,
            angleStep = if (ability == StrikerAbility.PRECISION) ANGLE_STEP_DEGREES / 2f else ANGLE_STEP_DEGREES
        )
    }

    if (confirmRestart) {
        ConfirmDialog(
            title = "Restart the match?",
            message = "The scores go back to zero and the discs are racked again.",
            confirmLabel = "Restart",
            onConfirm = viewModel::restartMatch,
            onDismiss = { confirmRestart = false }
        )
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
internal fun ClassicBackdrop(modifier: Modifier = Modifier) {
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
internal fun ToastBanner(message: String?, modifier: Modifier = Modifier) {
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

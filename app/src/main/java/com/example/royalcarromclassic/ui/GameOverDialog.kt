package com.example.royalcarromclassic.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.royalcarromclassic.data.GameMode
import com.example.royalcarromclassic.data.GameState
import com.example.royalcarromclassic.data.PlayerSlot
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.components.*

private data class Verdict(val title: String, val subtitle: String, val triumphant: Boolean, val coins: Int, val xp: Int)

private fun verdictFor(state: GameState): Verdict {
    val p1Won = state.winner == PlayerSlot.PLAYER1
    return when (state.mode) {
        GameMode.TRICK_SHOTS ->
            if (p1Won) Verdict("Solved!", "A masterful trick shot", true, 300, 50)
            else Verdict("Out of Shots", "Study the guide and try again", false, 0, 0)
        GameMode.VS_AI ->
            if (p1Won) Verdict("Victory", "You bested the Bot Master", true, 500, 100)
            else Verdict("Defeat", "The bot takes this one — a rematch?", false, 0, 0)
        GameMode.PRACTICE -> Verdict("Board Cleared", "Practice makes perfect", true, 500, 100)
        else -> Verdict("${winnerName(state)} Wins", "A fine game of carrom", true, if (p1Won) 500 else 0, if (p1Won) 100 else 0)
    }
}

@Composable
fun GameOverDialog(
    gameState: GameState,
    nextTrickShotLevel: Int?,
    onRematch: () -> Unit,
    onNextTrickShot: (Int) -> Unit,
    onChangeMode: () -> Unit
) {
    val verdict = remember(gameState.mode, gameState.winner, gameState.player1.name, gameState.player2.name) { verdictFor(gameState) }

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val emblemScale by animateFloatAsState(
        if (appeared) 1f else 0.2f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow),
        label = "emblemScale"
    )
    val bodyAlpha by animateFloatAsState(if (appeared) 1f else 0f, tween(450, delayMillis = 180), label = "bodyAlpha")
    val bodyOffset by animateFloatAsState(if (appeared) 0f else 24f, tween(450, delayMillis = 180), label = "bodyOffset")

    Dialog(onDismissRequest = {}) {
        Column(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .classicPanel(accent = if (verdict.triumphant) CarromPalette.Gold else CarromPalette.Silver, accentAlpha = 0.8f, raised = true)
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier
                    .size(132.dp)
                    .graphicsLayer {
                        scaleX = emblemScale
                        scaleY = emblemScale
                    },
                contentAlignment = Alignment.Center
            ) {
                if (verdict.triumphant) Sunburst(Modifier.fillMaxSize())
                Medallion(size = 84.dp, accent = if (verdict.triumphant) CarromPalette.Gold else CarromPalette.SilverDeep) {
                    GlyphIcon(Glyph.Trophy, size = 40.dp, tint = CarromPalette.Ink)
                }
            }

            Column(
                Modifier.graphicsLayer {
                    alpha = bodyAlpha
                    translationY = bodyOffset
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        verdict.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = if (verdict.triumphant) CarromPalette.GoldLight else CarromPalette.Silver,
                        textAlign = TextAlign.Center
                    )
                    Text(verdict.subtitle, style = MaterialTheme.typography.bodyMedium, color = CarromPalette.Parchment, textAlign = TextAlign.Center)
                }

                if (gameState.mode != GameMode.TRICK_SHOTS) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .classicPanel(accentAlpha = 0.18f)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FinalScore(gameState.player1.name, gameState.player1.score, gameState.winner == PlayerSlot.PLAYER1, Modifier.weight(1f))
                        Text("—", style = MaterialTheme.typography.titleMedium, color = CarromPalette.Muted)
                        FinalScore(gameState.player2.name, gameState.player2.score, gameState.winner == PlayerSlot.PLAYER2, Modifier.weight(1f))
                    }
                }

                if (verdict.coins > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RewardChip(Glyph.Coin, "+${verdict.coins}", "coins")
                        RewardChip(Glyph.Star, "+${verdict.xp}", "xp")
                    }
                }

                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val solvedWithNext = gameState.mode == GameMode.TRICK_SHOTS && verdict.triumphant && nextTrickShotLevel != null
                    if (solvedWithNext) {
                        ClassicButton("Next challenge", onClick = { onNextTrickShot(nextTrickShotLevel) }, modifier = Modifier.fillMaxWidth())
                        ClassicButton("Replay", onClick = onRematch, style = ButtonStyle.Outline, modifier = Modifier.fillMaxWidth())
                    } else {
                        ClassicButton(
                            if (gameState.mode == GameMode.TRICK_SHOTS && !verdict.triumphant) "Try again" else "Play again",
                            onClick = onRematch,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ClassicButton("Change mode", onClick = onChangeMode, style = ButtonStyle.Outline, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun FinalScore(name: String, score: Int, isWinner: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = MaterialTheme.typography.labelMedium, color = CarromPalette.Parchment, maxLines = 1)
        Text("$score", style = MaterialTheme.typography.headlineMedium, color = if (isWinner) CarromPalette.GoldLight else CarromPalette.Ivory)
    }
}

@Composable
private fun RewardChip(glyph: Glyph, amount: String, label: String) {
    Row(
        Modifier
            .classicPanel(accentAlpha = 0.45f, raised = true)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        GlyphIcon(glyph, size = 16.dp, tint = CarromPalette.GoldLight)
        Text(amount, style = MaterialTheme.typography.titleSmall, color = CarromPalette.GoldLight)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = CarromPalette.Muted)
    }
}

/** Slowly turning golden rays behind a victory medallion. */
@Composable
private fun Sunburst(modifier: Modifier = Modifier) {
    val spin by rememberInfiniteTransition(label = "sunburst").animateFloat(
        0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "sunburstAngle"
    )
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        drawCircle(
            Brush.radialGradient(listOf(CarromPalette.Gold.copy(alpha = 0.35f), Color.Transparent), center = c, radius = r),
            radius = r,
            center = c
        )
        rotate(spin, c) {
            for (i in 0 until 16) {
                rotate(i * 22.5f, c) {
                    drawLine(
                        Brush.verticalGradient(listOf(Color.Transparent, CarromPalette.GoldLight.copy(alpha = 0.55f)), startY = 0f, endY = c.y),
                        Offset(c.x, 0f),
                        Offset(c.x, c.y - r * 0.45f),
                        strokeWidth = if (i % 2 == 0) 5f else 2.5f
                    )
                }
            }
        }
    }
}

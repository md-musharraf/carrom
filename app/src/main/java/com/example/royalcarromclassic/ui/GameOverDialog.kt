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

private data class Verdict(
    val title: String,
    val subtitle: String,
    val triumphant: Boolean,
    val coins: Int,
    val xp: Int,
    val primary: String = "Play again",
    /** Label of the second button, or null to show only the first. */
    val secondary: String? = "Home",
    /** Rating points gained or lost (ranked online matches only). */
    val ratingChange: Int? = null
)

private fun verdictFor(state: GameState): Verdict {
    val p1Won = state.winner == PlayerSlot.PLAYER1 ||
        (state.isDoubles && state.winner != null && state.player(state.winner).team == state.player1.team)
    val coins = state.matchCoins
    val xp = if (coins > 0) 100 else 0
    return when (state.mode) {
        GameMode.TRICK_SHOTS ->
            if (p1Won) Verdict("Solved!", "A masterful trick shot", true, coins, 50)
            else Verdict("Out of Shots", "Study the guide and try again", false, 0, 0, primary = "Try again", secondary = "Home")
        GameMode.VS_AI ->
            if (p1Won) Verdict("Victory", "You bested ${state.player2.name}", true, coins, xp, secondary = "Home")
            else Verdict("Defeat", "The bot takes this one — a rematch?", false, 0, 0, secondary = "Home")
        GameMode.BLITZ ->
            if (p1Won) Verdict("Blitz Champion", "Quick hands and a steady eye", true, coins, xp, secondary = "Home")
            else Verdict("Out-paced", "The bot was quicker this time", false, 0, 0, secondary = "Home")
        GameMode.LUCKY_SHOT -> Verdict(
            "Lucky Shot", "You won ${state.luckyShot?.coinsWon ?: 0} coins today. New shots at midnight.", true,
            coins = state.luckyShot?.coinsWon ?: 0, xp = 0, primary = "Back home", secondary = null
        )
        GameMode.TIME_ATTACK -> {
            val score = state.player1.score
            val best = state.timeAttack?.best ?: score
            Verdict(
                title = if (score > 0 && score >= best) "New Best!" else "Time!",
                subtitle = "You scored $score · best $best",
                triumphant = score > 0 && score >= best,
                coins = coins,
                xp = if (state.isGameOver) 30 else 0,
                primary = "Go again",
                secondary = "Home"
            )
        }
        GameMode.ONLINE -> onlineVerdict(state)
        GameMode.PRACTICE -> Verdict("Board Cleared", "Practice makes perfect", true, coins, xp, secondary = "Home")
        else -> {
            val title = when {
                state.isDoubles -> "Team ${if (state.player(state.winner ?: PlayerSlot.PLAYER1).team == 0) "Gold" else "Silver"} Wins"
                else -> "${winnerName(state)} Wins"
            }
            val subtitle = when (state.mode) {
                GameMode.DISC_POOL -> "Every disc of their colour pocketed"
                GameMode.DICE -> "The dice were kind, the aim was true"
                else -> "A fine game of carrom"
            }
            Verdict(title, subtitle, true, coins, xp, secondary = "Home")
        }
    }
}

private fun onlineVerdict(state: GameState): Verdict {
    val online = state.online
    val result = online?.result
    if (state.winner == null || result == null) {
        return Verdict("Match Over", "The match ended while you were away", false, 0, 0, primary = "Play online", secondary = "Leave")
    }
    val won = state.winner == PlayerSlot.PLAYER1
    val opponent = state.player2.name
    val subtitle = when (result.reason) {
        "resigned" -> if (won) "$opponent resigned" else "You resigned"
        "timeout" -> if (won) "$opponent ran out of time" else "You ran out of time"
        else -> if (won) "You outplayed $opponent" else "$opponent takes this one"
    }
    return Verdict(
        title = if (won) "Victory" else "Defeat",
        subtitle = subtitle,
        triumphant = won,
        coins = result.coins,
        xp = result.xp,
        primary = "Play again",
        secondary = "Leave",
        ratingChange = if (online.ranked) result.ratingChange else null
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameOverDialog(
    gameState: GameState,
    nextTrickShotLevel: Int?,
    onRematch: () -> Unit,
    onNextTrickShot: (Int) -> Unit,
    onChangeMode: () -> Unit
) {
    val verdict = remember(gameState.mode, gameState.winner, gameState.players, gameState.online?.result, gameState.matchCoins, gameState.timeAttack) {
        verdictFor(gameState)
    }

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

                val solo = gameState.mode == GameMode.TRICK_SHOTS || gameState.mode == GameMode.LUCKY_SHOT ||
                    gameState.mode == GameMode.TIME_ATTACK || gameState.mode == GameMode.PRACTICE
                if (!solo) {
                    val suffix = if (gameState.mode == GameMode.DISC_POOL) "/9" else ""
                    FlowRow(
                        Modifier
                            .fillMaxWidth()
                            .classicPanel(accentAlpha = 0.18f)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        gameState.players.forEachIndexed { i, player ->
                            val slot = PlayerSlot.of(i)
                            val won = gameState.winner == slot ||
                                (gameState.isDoubles && gameState.winner != null && gameState.player(gameState.winner).team == player.team)
                            FinalScore(player.name, "${player.score}$suffix", won, Modifier.widthIn(min = 72.dp))
                        }
                    }
                    if (gameState.isDoubles) {
                        val totals = gameState.teamScores
                        Text(
                            "Team Gold ${totals[0]}  ·  Team Silver ${totals[1]}",
                            style = MaterialTheme.typography.titleSmall,
                            color = CarromPalette.Parchment
                        )
                    }
                }

                if (verdict.coins > 0 || verdict.xp > 0 || verdict.ratingChange != null) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (verdict.coins > 0) RewardChip(Glyph.Coin, "+%,d".format(verdict.coins), "coins")
                        if (verdict.xp > 0) RewardChip(Glyph.Star, "+${verdict.xp}", "xp")
                        verdict.ratingChange?.let { change ->
                            RewardChip(Glyph.Chart, if (change >= 0) "+$change" else "$change", "rating")
                        }
                    }
                }

                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val solvedWithNext = gameState.mode == GameMode.TRICK_SHOTS && verdict.triumphant && nextTrickShotLevel != null
                    if (solvedWithNext) {
                        ClassicButton("Next challenge", onClick = { onNextTrickShot(nextTrickShotLevel) }, modifier = Modifier.fillMaxWidth())
                        ClassicButton("Replay", onClick = onRematch, style = ButtonStyle.Outline, modifier = Modifier.fillMaxWidth())
                    } else {
                        ClassicButton(verdict.primary, onClick = onRematch, modifier = Modifier.fillMaxWidth())
                    }
                    verdict.secondary?.let { ClassicButton(it, onClick = onChangeMode, style = ButtonStyle.Outline, modifier = Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

@Composable
private fun FinalScore(name: String, score: String, isWinner: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = MaterialTheme.typography.labelMedium, color = CarromPalette.Parchment, maxLines = 1)
        Text(score, style = MaterialTheme.typography.headlineMedium, color = if (isWinner) CarromPalette.GoldLight else CarromPalette.Ivory)
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

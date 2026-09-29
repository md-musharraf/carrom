package com.example.royalcarromclassic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.royalcarromclassic.data.DiceFace
import com.example.royalcarromclassic.data.DiceSkin
import com.example.royalcarromclassic.data.GameState
import com.example.royalcarromclassic.data.PlayerData
import com.example.royalcarromclassic.data.PlayerSlot
import com.example.royalcarromclassic.data.TimeAttackStatus
import com.example.royalcarromclassic.data.TurnClock
import com.example.royalcarromclassic.data.BoardSummary
import com.example.royalcarromclassic.data.TurnState
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.components.*
import kotlinx.coroutines.delay

/** Each seat's metal: gold, silver, jade and amber (in doubles, one metal per team). */
fun seatAccent(state: GameState, slot: PlayerSlot): Color {
    val team = state.player(slot).team
    val index = if (state.isDoubles && team >= 0) team else slot.index
    return SEAT_ACCENTS[index % SEAT_ACCENTS.size]
}

private val SEAT_ACCENTS = listOf(CarromPalette.Gold, CarromPalette.Silver, CarromPalette.Jade, CarromPalette.Amber)

/**
 * The strip above the table: back to the home page, discs left and the queen's status, and the
 * match's own actions on the right.
 */
@Composable
fun TableHeader(
    state: GameState,
    summary: BoardSummary,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        GlyphButton(Glyph.Home, contentDescription = "Home", onClick = onHome, modifier = Modifier.size(40.dp), glyphSize = 20.dp)
        BoardStatusStrip(
            summary = summary,
            mode = state.mode,
            queenNeedsCover = state.queenNeedsCover,
            queenCovered = state.queenCovered,
            onOpenModes = onHome,
            modifier = Modifier.weight(1f),
            trailing = trailing
        )
    }
}

/** Compact seats for three or four players, the shooter's glowing; team totals in doubles. */
@Composable
fun SeatsRow(state: GameState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.players.forEachIndexed { i, player ->
                val slot = PlayerSlot.of(i)
                SeatChip(
                    player = player,
                    accent = seatAccent(state, slot),
                    active = !state.isGameOver && state.currentTurn == slot,
                    caption = state.seatOf(slot).name.lowercase().replaceFirstChar { it.uppercase() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (state.isDoubles) {
            val totals = state.teamScores
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Tag("Team Gold ${totals[0]}", color = CarromPalette.Gold)
                Spacer(Modifier.width(8.dp))
                Text("vs", style = MaterialTheme.typography.labelSmall, color = CarromPalette.Muted)
                Spacer(Modifier.width(8.dp))
                Tag("Team Silver ${totals[1]}", color = CarromPalette.Silver)
            }
        }
    }
}

@Composable
private fun SeatChip(player: PlayerData, accent: Color, active: Boolean, caption: String, modifier: Modifier = Modifier) {
    val edge by animateColorAsState(if (active) accent else CarromPalette.Seam, tween(300), label = "seatEdge")
    Column(
        modifier
            .classicPanel(accent = accent, accentAlpha = 0.12f, raised = active)
            .border(1.5.dp, edge.copy(alpha = if (active) 0.95f else 0.5f), PanelShape)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Medallion(size = 24.dp, accent = accent) {
                Text(player.monogram, style = MaterialTheme.typography.labelSmall, color = CarromPalette.Ink, maxLines = 1)
            }
            RollingScore(player.score, color = if (active) CarromPalette.GoldLight else CarromPalette.Parchment)
        }
        Text(
            player.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) CarromPalette.Ivory else CarromPalette.Parchment,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            if (active) (if (player.isBot) "THINKING" else "TO PLAY") else caption.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (active) accent else CarromPalette.Muted,
            maxLines = 1
        )
    }
}

/** Stands in for the opponent in Time Attack: the clock, the points banked and the record. */
@Composable
fun TimeAttackPlate(status: TimeAttackStatus, score: Int, running: Boolean, modifier: Modifier = Modifier) {
    var secondsLeft by remember(status.deadlineMillis) { mutableIntStateOf(secondsUntil(status.deadlineMillis)) }
    LaunchedEffect(status.deadlineMillis, running) {
        while (running) {
            secondsLeft = secondsUntil(status.deadlineMillis)
            if (secondsLeft <= 0) break
            delay(250)
        }
    }
    val urgent = secondsLeft <= 10
    Row(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .classicPanel(PanelShape, accent = if (urgent) CarromPalette.CrimsonLight else CarromPalette.GoldLight, accentAlpha = 0.45f)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (running) TurnClockRing(TurnClock(status.deadlineMillis, status.totalSeconds), CarromPalette.Gold, Modifier.size(46.dp))
            Medallion(size = 36.dp, accent = if (urgent) CarromPalette.Crimson else CarromPalette.Gold) {
                GlyphIcon(Glyph.Timer, size = 18.dp, tint = CarromPalette.Ink)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (running) "${secondsLeft}s left" else "Time!",
                style = MaterialTheme.typography.titleMedium,
                color = if (urgent) CarromPalette.CrimsonLight else CarromPalette.Ivory
            )
            Text("Best ${status.best} · ${status.pocketed} pocketed", style = MaterialTheme.typography.bodySmall, color = CarromPalette.Muted, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("SCORE", style = MaterialTheme.typography.labelSmall, color = CarromPalette.Muted)
            RollingScore(score, color = CarromPalette.GoldLight)
        }
    }
}

private fun secondsUntil(deadline: Long): Int =
    (((deadline - System.currentTimeMillis()).coerceAtLeast(0L) + 999L) / 1000L).toInt()

/**
 * Dice Carrom's tray: the big die (tap it to roll), what the face grants, and the roll or re-roll
 * button. Bots roll on their own.
 */
@Composable
fun DiceTray(state: GameState, skin: DiceSkin, onRoll: () -> Unit, modifier: Modifier = Modifier) {
    val dice = state.dice ?: return
    val shooter = state.player(state.currentTurn)
    val humanTurn = !state.isAiTurn && !state.isGameOver
    val canRoll = humanTurn && !dice.rolling && state.turnState != TurnState.MOVING &&
        (dice.face == null || dice.rerollsLeft > 0)
    val face = dice.face
    Row(
        modifier
            .fillMaxWidth()
            .classicPanel(accent = if (face == null && humanTurn) CarromPalette.GoldLight else CarromPalette.Gold, accentAlpha = if (face == null && humanTurn) 0.8f else 0.3f, raised = true)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DieView(
            face = face,
            skin = skin,
            rolling = dice.rolling,
            rollId = dice.rollId,
            size = 64.dp,
            modifier = Modifier.clickable(enabled = canRoll, role = Role.Button, onClickLabel = "Roll the die", onClick = onRoll)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AnimatedContent(
                targetState = Triple(face, dice.rolling, shooter.name),
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "diceFace"
            ) { (f, rolling, name) ->
                Column {
                    Text(
                        when {
                            rolling -> "Rolling…"
                            f != null -> "${f.pips} · ${f.title}"
                            humanTurn -> "$name, roll the die"
                            else -> "$name rolls…"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (f == DiceFace.STEADY || f == null) CarromPalette.Ivory else CarromPalette.GoldLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when {
                            rolling -> "Every face is a power for this shot"
                            f != null -> f.summary
                            else -> "Tap the die. Each face powers up your next shot."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = CarromPalette.Parchment,
                        maxLines = 2
                    )
                }
            }
        }
        when {
            face == null && humanTurn -> ClassicButton("Roll", onClick = onRoll, enabled = canRoll, leading = Glyph.Dice, height = 44.dp, horizontalPadding = 12.dp)
            face != null && humanTurn && dice.rerollsLeft > 0 -> ClassicButton(
                "Re-roll", onClick = onRoll, enabled = canRoll, style = ButtonStyle.Outline, leading = Glyph.Restart, height = 40.dp, horizontalPadding = 10.dp
            )
        }
    }
}

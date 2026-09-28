package com.example.royalcarromclassic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.royalcarromclassic.data.AIDifficulty
import com.example.royalcarromclassic.data.GameMode
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.components.*

private data class GameModeItem(
    val mode: GameMode,
    val title: String,
    val description: String,
    val glyph: Glyph,
    val accent: Color,
    val badge: String
)

private val MODE_ITEMS = listOf(
    GameModeItem(GameMode.VS_AI, "Play the Bot", "A cunning opponent with cuts, bank shots and three levels of skill.", Glyph.Crown, CarromPalette.Gold, "Popular"),
    GameModeItem(GameMode.CLASSIC, "Classic Carrom", "Tournament points: white 10, black 5, queen 25 with cover.", Glyph.Trophy, CarromPalette.Amber, "Tournament"),
    GameModeItem(GameMode.DISC_POOL, "Disc Pool", "A brisk pool-style race to clear the board.", Glyph.Modes, CarromPalette.Jade, "Fast"),
    GameModeItem(GameMode.FREESTYLE, "Freestyle", "Every disc scores. First to 160 points wins.", Glyph.Bolt, CarromPalette.CrimsonLight, "Arcade"),
    GameModeItem(GameMode.PASS_AND_PLAY, "Pass & Play", "Two players, one device, taking turns from opposite sides.", Glyph.Players, CarromPalette.Silver, "2 players"),
    GameModeItem(GameMode.PRACTICE, "Practice", "Unlimited strikes to rehearse angles, rebounds and power.", Glyph.Target, CarromPalette.Parchment, "Training"),
)

private val DIFFICULTIES = listOf(AIDifficulty.EASY to "Rookie", AIDifficulty.MEDIUM to "Pro", AIDifficulty.HARD to "Master")

@Composable
fun GameModesSheet(
    currentMode: GameMode,
    aiDifficulty: AIDifficulty,
    onSelectMode: (GameMode, AIDifficulty) -> Unit,
    onOpenTrickShots: () -> Unit,
    onDismiss: () -> Unit
) {
    // Difficulty is chosen here and applied with the mode, so changing it never restarts a match.
    var difficulty by remember { mutableStateOf(aiDifficulty) }

    ClassicSheet(title = "Game Modes", subtitle = "Choose how you want to play", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("BOT DIFFICULTY", style = MaterialTheme.typography.labelSmall, color = CarromPalette.Muted)
            SegmentedSelector(
                options = DIFFICULTIES.map { it.second },
                selectedIndex = DIFFICULTIES.indexOfFirst { it.first == difficulty }.coerceAtLeast(0),
                onSelect = { difficulty = DIFFICULTIES[it].first }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(MODE_ITEMS, key = { it.mode }) { item ->
                ModeCard(
                    item = item,
                    selected = item.mode == currentMode,
                    onClick = {
                        onSelectMode(item.mode, difficulty)
                        onDismiss()
                    }
                )
            }
            item(key = "trick_shots") {
                ModeCard(
                    item = GameModeItem(
                        GameMode.TRICK_SHOTS, "Trick Shot Challenges",
                        "Ten hand-crafted puzzles: banks, splits and queen rescues.",
                        Glyph.Star, CarromPalette.GoldLight, "Puzzles"
                    ),
                    selected = currentMode == GameMode.TRICK_SHOTS,
                    onClick = onOpenTrickShots
                )
            }
        }
    }
}

@Composable
private fun ModeCard(item: GameModeItem, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .classicPanel(accent = item.accent, accentAlpha = if (selected) 0.5f else 0.2f, raised = selected)
            .then(if (selected) Modifier.border(1.5.dp, CarromPalette.Gold, PanelShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Medallion(size = 42.dp, accent = item.accent) {
            GlyphIcon(item.glyph, size = 20.dp, tint = CarromPalette.Ink)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, color = CarromPalette.Ivory, modifier = Modifier.weight(1f, fill = false))
                Tag(item.badge, color = item.accent)
            }
            Text(item.description, style = MaterialTheme.typography.bodySmall, color = CarromPalette.Parchment, maxLines = 2)
        }
        AnimatedVisibility(selected, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            GlyphIcon(Glyph.Check, size = 18.dp, tint = CarromPalette.Gold)
        }
    }
}

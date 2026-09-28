package com.example.royalcarromclassic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.royalcarromclassic.data.PlayerStats
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.components.*

@Composable
fun SettingsSheet(
    soundEnabled: Boolean,
    hapticEnabled: Boolean,
    stats: PlayerStats,
    onToggleSound: () -> Unit,
    onToggleHaptics: () -> Unit,
    onOpenRules: () -> Unit,
    onDismiss: () -> Unit
) {
    ClassicSheet(title = "Settings", subtitle = "Sound, feel and your career record", onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .classicPanel(accentAlpha = 0.18f)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            ToggleRow(
                glyph = if (soundEnabled) Glyph.SoundOn else Glyph.SoundOff,
                title = "Sound effects",
                subtitle = "Wooden clacks, cushion thumps and pocket drops",
                checked = soundEnabled,
                onToggle = onToggleSound
            )
            GoldDivider(alpha = 0.2f)
            ToggleRow(
                glyph = Glyph.Vibration,
                title = "Haptics",
                subtitle = "Feel every strike, rebound and pocket",
                checked = hapticEnabled,
                onToggle = onToggleHaptics
            )
        }

        OrnamentHeading("Career")
        val winRate = if (stats.matchesPlayed > 0) stats.matchesWon * 100 / stats.matchesPlayed else 0
        Column(
            Modifier
                .fillMaxWidth()
                .classicPanel(accentAlpha = 0.18f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatRow("Level", "${stats.level}", CarromPalette.GoldLight)
            StatRow("Matches played", "${stats.matchesPlayed}")
            StatRow("Matches won", "${stats.matchesWon}", CarromPalette.Jade)
            StatRow("Win rate", "$winRate%", CarromPalette.GoldLight)
            StatRow("Discs pocketed", "${stats.totalPockets}")
            StatRow("Queens covered", "${stats.queenCovers}", CarromPalette.CrimsonLight)
            StatRow("Trick shots solved", "${stats.trickShotsCompleted}")
        }

        ClassicButton(
            text = "Rules of play",
            onClick = onOpenRules,
            style = ButtonStyle.Outline,
            leading = Glyph.Rules,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ToggleRow(glyph: Glyph, title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch, onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlyphIcon(glyph, size = 22.dp, tint = CarromPalette.Gold)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = CarromPalette.Ivory)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CarromPalette.Muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = CarromPalette.GoldLight,
                checkedTrackColor = CarromPalette.GoldDeep,
                checkedBorderColor = CarromPalette.Gold,
                uncheckedThumbColor = CarromPalette.Muted,
                uncheckedTrackColor = CarromPalette.Night,
                uncheckedBorderColor = CarromPalette.Seam
            )
        )
    }
}

package com.example.royalcarromclassic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.royalcarromclassic.data.TrickShotLevel
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.components.*

@Composable
fun TrickShotsSheet(
    levels: List<TrickShotLevel>,
    onSelectLevel: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val solved = levels.count { it.stars > 0 }
    ClassicSheet(
        title = "Trick Shots",
        subtitle = "$solved of ${levels.size} solved · earn 300 coins each",
        onDismiss = onDismiss
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(levels, key = { it.id }) { level ->
                LevelCard(level) {
                    onSelectLevel(level.id)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun LevelCard(level: TrickShotLevel, onClick: () -> Unit) {
    val accent = if (level.stars > 0) CarromPalette.Gold else CarromPalette.Parchment
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (level.isUnlocked) 1f else 0.55f)
            .classicPanel(accent = accent, accentAlpha = if (level.isUnlocked) 0.3f else 0.1f, raised = level.isUnlocked)
            .clickable(enabled = level.isUnlocked, role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Medallion(size = 40.dp, accent = if (level.isUnlocked) accent else CarromPalette.SilverDeep) {
            if (level.isUnlocked) {
                Text("${level.id}", style = MaterialTheme.typography.titleMedium, color = CarromPalette.Ink)
            } else {
                GlyphIcon(Glyph.Lock, size = 16.dp, tint = CarromPalette.Ink)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    level.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = CarromPalette.Ivory,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                StarRating(level.stars)
            }
            Text(level.description, style = MaterialTheme.typography.bodySmall, color = CarromPalette.Parchment, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (level.maxShots == 1) "1 SHOT" else "${level.maxShots} SHOTS",
                style = MaterialTheme.typography.labelSmall,
                color = CarromPalette.Muted
            )
        }
    }
}

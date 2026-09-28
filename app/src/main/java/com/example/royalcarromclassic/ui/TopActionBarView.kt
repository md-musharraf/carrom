package com.example.royalcarromclassic.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.royalcarromclassic.data.PlayerStats
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.components.CoinAmount
import com.example.royalcarromclassic.ui.components.Glyph
import com.example.royalcarromclassic.ui.components.GlyphButton
import com.example.royalcarromclassic.ui.components.Medallion
import com.example.royalcarromclassic.ui.components.classicPanel

@Composable
fun TopActionBarView(
    stats: PlayerStats,
    onOpenModes: () -> Unit,
    onOpenTrickShots: () -> Unit,
    onOpenWheel: () -> Unit,
    onOpenShop: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val xpProgress by animateFloatAsState(
        targetValue = stats.xp.toFloat() / stats.xpToNextLevel.coerceAtLeast(1),
        animationSpec = tween(700),
        label = "xpProgress"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .classicPanel(raised = true)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Medallion(size = 38.dp, accent = CarromPalette.GoldDeep, progress = xpProgress) {
            Text("${stats.level}", style = MaterialTheme.typography.titleMedium, color = CarromPalette.Ivory, maxLines = 1)
        }
        CoinAmount(stats.coins)

        // Chips share the remaining width and shrink gracefully on narrow phones.
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                Triple(Glyph.Modes, "Game modes", onOpenModes),
                Triple(Glyph.Target, "Trick shots", onOpenTrickShots),
                Triple(Glyph.Gift, "Daily spin", onOpenWheel),
                Triple(Glyph.Shop, "Shop", onOpenShop),
                Triple(Glyph.Rules, "Rules", onOpenRules),
                Triple(Glyph.Settings, "Settings", onOpenSettings)
            ).forEach { (glyph, label, action) ->
                GlyphButton(
                    glyph = glyph,
                    contentDescription = label,
                    onClick = action,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = 38.dp)
                        .aspectRatio(1f)
                )
            }
        }
    }
}

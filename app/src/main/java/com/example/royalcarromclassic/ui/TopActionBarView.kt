package com.example.royalcarromclassic.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.royalcarromclassic.data.PlayerStats
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.components.CoinAmount
import com.example.royalcarromclassic.ui.components.Glyph
import com.example.royalcarromclassic.ui.components.GlyphButton
import com.example.royalcarromclassic.ui.components.Medallion
import com.example.royalcarromclassic.ui.components.classicPanel
import com.example.royalcarromclassic.ui.components.rememberPressScale
import com.example.royalcarromclassic.ui.components.scaledBy

/**
 * Level medallion (opens the account; a dot shows the online status), coins, and the menu chips.
 * [signedIn] is null when this build has no online play.
 */
@Composable
fun TopActionBarView(
    stats: PlayerStats,
    signedIn: Boolean?,
    onOpenAccount: () -> Unit,
    onOpenOnline: () -> Unit,
    onOpenModes: () -> Unit,
    onOpenCommunity: () -> Unit,
    onOpenWheel: () -> Unit,
    onOpenShop: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    wheelReady: Boolean = false
) {
    val xpProgress by animateFloatAsState(
        targetValue = stats.xp.toFloat() / stats.xpToNextLevel.coerceAtLeast(1),
        animationSpec = tween(700),
        label = "xpProgress"
    )
    val interaction = remember { MutableInteractionSource() }
    val medallionScale = rememberPressScale(interaction, pressedScale = 0.9f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .classicPanel(raised = true)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .scaledBy(medallionScale)
                .clickable(interaction, indication = null, role = Role.Button, onClickLabel = "Account", onClick = onOpenAccount)
        ) {
            Medallion(size = 38.dp, accent = CarromPalette.GoldDeep, progress = xpProgress) {
                Text("${stats.level}", style = MaterialTheme.typography.titleMedium, color = CarromPalette.Ivory, maxLines = 1)
            }
            if (signedIn != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(if (signedIn) CarromPalette.Jade else CarromPalette.Muted)
                        .border(1.5.dp, CarromPalette.Mahogany, CircleShape)
                )
            }
        }
        CoinAmount(stats.coins)

        // Chips share the remaining width and shrink gracefully on narrow phones.
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                Triple(Glyph.Globe, "Play online", onOpenOnline),
                Triple(Glyph.Modes, "Game modes", onOpenModes),
                Triple(Glyph.Players, "Friends and leaderboards", onOpenCommunity),
                Triple(Glyph.Gift, "Daily spin", onOpenWheel),
                Triple(Glyph.Shop, "Shop", onOpenShop),
                Triple(Glyph.Settings, "Settings", onOpenSettings)
            ).forEach { (glyph, label, action) ->
                Box(
                    Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = 38.dp)
                        .aspectRatio(1f)
                ) {
                    GlyphButton(
                        glyph = glyph,
                        contentDescription = label,
                        onClick = action,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (glyph == Glyph.Gift && wheelReady) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(CarromPalette.CrimsonLight)
                        )
                    }
                }
            }
        }
    }
}

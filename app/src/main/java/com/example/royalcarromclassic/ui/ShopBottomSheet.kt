package com.example.royalcarromclassic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.royalcarromclassic.data.BoardTheme
import com.example.royalcarromclassic.data.PlayerStats
import com.example.royalcarromclassic.data.StrikerConfig
import com.example.royalcarromclassic.engine.BoardGeometry
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.board.BoardArt
import com.example.royalcarromclassic.ui.board.StrikerArt
import com.example.royalcarromclassic.ui.board.drawCarromBoard
import com.example.royalcarromclassic.ui.board.drawStriker
import com.example.royalcarromclassic.ui.components.*
import kotlin.math.roundToInt

private enum class ShopTab(val label: String) { STRIKERS("Strikers"), BOARDS("Boards") }

@Composable
fun ShopBottomSheet(
    stats: PlayerStats,
    strikers: List<StrikerConfig>,
    boards: List<BoardTheme>,
    selectedStrikerId: String,
    selectedBoardId: String,
    onSelectStriker: (String) -> Unit,
    onSelectBoard: (String) -> Unit,
    onBuyStriker: (StrikerConfig) -> Unit,
    onBuyBoard: (BoardTheme) -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableStateOf(ShopTab.STRIKERS) }

    ClassicSheet(
        title = "Royal Armoury",
        subtitle = "Strikers and boards crafted for connoisseurs",
        onDismiss = onDismiss,
        trailing = {
            Box(
                Modifier
                    .classicPanel(accentAlpha = 0.45f, raised = true)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) { CoinAmount(stats.coins) }
        }
    ) {
        SegmentedSelector(
            options = ShopTab.entries.map { it.label },
            selectedIndex = tab.ordinal,
            onSelect = { tab = ShopTab.entries[it] }
        )

        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(tween(260)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(200)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(160)))
            },
            label = "shopTab"
        ) { current ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (current) {
                    ShopTab.STRIKERS -> items(strikers, key = { it.id }) { striker ->
                        ShopItemCard(
                            title = striker.name,
                            description = striker.description,
                            detail = strikerPerks(striker),
                            price = striker.price,
                            unlocked = striker.isUnlocked,
                            equipped = striker.id == selectedStrikerId,
                            affordable = stats.coins >= striker.price,
                            onEquip = { onSelectStriker(striker.id) },
                            onBuy = { onBuyStriker(striker) },
                            preview = { StrikerPreview(striker) }
                        )
                    }

                    ShopTab.BOARDS -> items(boards, key = { it.id }) { board ->
                        ShopItemCard(
                            title = board.name,
                            description = board.description,
                            detail = null,
                            price = board.price,
                            unlocked = board.isUnlocked,
                            equipped = board.id == selectedBoardId,
                            affordable = stats.coins >= board.price,
                            onEquip = { onSelectBoard(board.id) },
                            onBuy = { onBuyBoard(board) },
                            preview = { BoardPreview(board) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopItemCard(
    title: String,
    description: String,
    detail: String?,
    price: Int,
    unlocked: Boolean,
    equipped: Boolean,
    affordable: Boolean,
    onEquip: () -> Unit,
    onBuy: () -> Unit,
    preview: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .classicPanel(accentAlpha = if (equipped) 0.6f else 0.2f, raised = equipped)
            .then(if (equipped) Modifier.border(1.5.dp, CarromPalette.Gold, PanelShape) else Modifier)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { preview() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = CarromPalette.Ivory, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(description, style = MaterialTheme.typography.bodySmall, color = CarromPalette.Parchment, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Text(detail.uppercase(), style = MaterialTheme.typography.labelSmall, color = CarromPalette.Gold, maxLines = 1)
            }
        }
        when {
            equipped -> Tag("Equipped")
            unlocked -> ClassicButton("Equip", onEquip, style = ButtonStyle.Outline, height = 36.dp, horizontalPadding = 12.dp)
            else -> ClassicButton(
                text = "%,d".format(price),
                onClick = onBuy,
                enabled = affordable,
                leading = Glyph.Coin,
                height = 36.dp,
                horizontalPadding = 10.dp
            )
        }
    }
}

/** "Power +15% · Guide +5%", or "Balanced" for the standard striker. */
private fun strikerPerks(striker: StrikerConfig): String {
    val power = ((striker.powerMultiplier - 1f) * 100).roundToInt()
    val guide = ((striker.aimGuideLength - 1f) * 100).roundToInt()
    val perks = listOfNotNull(
        "Power +$power%".takeIf { power > 0 },
        "Guide +$guide%".takeIf { guide > 0 }
    )
    return if (perks.isEmpty()) "Balanced" else perks.joinToString("  ·  ")
}

@Composable
private fun StrikerPreview(config: StrikerConfig) {
    Spacer(
        Modifier
            .size(48.dp)
            .drawWithCache {
                val art = StrikerArt(config, radius = size.minDimension * 0.4f)
                onDrawBehind { drawStriker(art, size.width / 2f, size.height / 2f) }
            }
    )
}

@Composable
private fun BoardPreview(theme: BoardTheme) {
    val art = remember(theme) { BoardArt(theme) }
    Canvas(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(6.dp))
    ) {
        val s = size.width / BoardGeometry.BOARD_SIZE
        scale(s, s, pivot = Offset.Zero) { drawCarromBoard(art) }
    }
}

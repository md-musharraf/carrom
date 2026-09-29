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
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.royalcarromclassic.data.*
import com.example.royalcarromclassic.engine.BoardGeometry
import com.example.royalcarromclassic.engine.Powers
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.board.BoardArt
import com.example.royalcarromclassic.ui.board.CoinSetArt
import com.example.royalcarromclassic.ui.board.drawCarromMan
import com.example.royalcarromclassic.ui.board.StrikerArt
import com.example.royalcarromclassic.ui.board.drawCarromBoard
import com.example.royalcarromclassic.ui.board.drawStriker
import com.example.royalcarromclassic.ui.components.*
import kotlin.math.roundToInt

private enum class LockerTab(val label: String) { STRIKERS("Striker"), COINS("Coins"), DICE("Dice"), BOARDS("Board") }

/**
 * The Locker: buy and equip strikers, coin sets, dice and boards. Every item's power is spelled
 * out on its card, and powers can be switched off for purists.
 */
@Composable
fun LockerSheet(
    stats: PlayerStats,
    strikers: List<StrikerConfig>,
    coinSets: List<CoinSet>,
    dice: List<DiceSkin>,
    boards: List<BoardTheme>,
    state: GameState,
    onSelectStriker: (String) -> Unit,
    onSelectCoinSet: (String) -> Unit,
    onSelectDice: (String) -> Unit,
    onSelectBoard: (String) -> Unit,
    onBuyStriker: (StrikerConfig) -> Unit,
    onBuyCoinSet: (CoinSet) -> Unit,
    onBuyDice: (DiceSkin) -> Unit,
    onBuyBoard: (BoardTheme) -> Unit,
    onTogglePowers: () -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableStateOf(LockerTab.STRIKERS) }

    ClassicSheet(
        title = "The Locker",
        subtitle = "Every piece has a power. Powers apply from your next offline match.",
        onDismiss = onDismiss,
        trailing = {
            Box(
                Modifier
                    .classicPanel(accentAlpha = 0.45f, raised = true)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) { CoinAmount(stats.coins) }
        }
    ) {
        PowersToggle(enabled = state.powersEnabled, onToggle = onTogglePowers)
        SegmentedSelector(
            options = LockerTab.entries.map { it.label },
            selectedIndex = tab.ordinal,
            onSelect = { tab = LockerTab.entries[it] }
        )

        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(tween(260)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(200)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(160)))
            },
            label = "lockerTab"
        ) { current ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (current) {
                    LockerTab.STRIKERS -> items(strikers, key = { it.id }) { striker ->
                        ShopItemCard(
                            title = striker.name,
                            description = striker.description,
                            power = striker.ability.title,
                            powerDetail = strikerPerks(striker),
                            price = striker.price,
                            unlocked = striker.isUnlocked,
                            equipped = striker.id == state.selectedStrikerId,
                            affordable = stats.coins >= striker.price,
                            onEquip = { onSelectStriker(striker.id) },
                            onBuy = { onBuyStriker(striker) },
                            preview = { StrikerPreview(striker) }
                        )
                    }

                    LockerTab.COINS -> items(coinSets, key = { it.id }) { set ->
                        ShopItemCard(
                            title = set.name,
                            description = set.description,
                            power = set.power.title,
                            powerDetail = set.power.summary,
                            price = set.price,
                            unlocked = set.isUnlocked,
                            equipped = set.id == state.selectedCoinSetId,
                            affordable = stats.coins >= set.price,
                            onEquip = { onSelectCoinSet(set.id) },
                            onBuy = { onBuyCoinSet(set) },
                            preview = { CoinSetPreview(set) }
                        )
                    }

                    LockerTab.DICE -> items(dice, key = { it.id }) { die ->
                        ShopItemCard(
                            title = die.name,
                            description = die.description,
                            power = die.power.title,
                            powerDetail = die.power.summary,
                            price = die.price,
                            unlocked = die.isUnlocked,
                            equipped = die.id == state.selectedDiceId,
                            affordable = stats.coins >= die.price,
                            onEquip = { onSelectDice(die.id) },
                            onBuy = { onBuyDice(die) },
                            preview = { DieView(face = DiceFace.BONUS_TURN, skin = die, size = 50.dp) }
                        )
                    }

                    LockerTab.BOARDS -> items(boards, key = { it.id }) { board ->
                        ShopItemCard(
                            title = board.name,
                            description = board.description,
                            power = board.power.title,
                            powerDetail = board.power.summary,
                            price = board.price,
                            unlocked = board.isUnlocked,
                            equipped = board.id == state.selectedBoardId,
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

/** Powers on or off, with what that means. */
@Composable
fun PowersToggle(enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .classicPanel(accent = if (enabled) CarromPalette.Jade else CarromPalette.Seam, accentAlpha = 0.5f)
            .clickable(role = Role.Switch, onClickLabel = "Toggle powers", onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GlyphIcon(Glyph.Bolt, size = 18.dp, tint = if (enabled) CarromPalette.Jade else CarromPalette.Muted)
        Column(Modifier.weight(1f)) {
            Text(if (enabled) "Powers on" else "Powers off", style = MaterialTheme.typography.titleSmall, color = CarromPalette.Ivory)
            Text(
                if (enabled) "Offline matches use your loadout's powers. Online stays standard."
                else "Every offline match plays on the standard table.",
                style = MaterialTheme.typography.bodySmall,
                color = CarromPalette.Muted
            )
        }
        Tag(if (enabled) "On" else "Off", color = if (enabled) CarromPalette.Jade else CarromPalette.Muted)
    }
}

@Composable
private fun ShopItemCard(
    title: String,
    description: String,
    power: String,
    powerDetail: String,
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
        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) { preview() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = CarromPalette.Ivory, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(description, style = MaterialTheme.typography.bodySmall, color = CarromPalette.Parchment, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlyphIcon(Glyph.Bolt, size = 11.dp, tint = CarromPalette.Gold)
                Text(power.uppercase(), style = MaterialTheme.typography.labelSmall, color = CarromPalette.Gold, maxLines = 1)
            }
            Text(powerDetail, style = MaterialTheme.typography.bodySmall, color = CarromPalette.GoldLight.copy(alpha = 0.85f), maxLines = 2)
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

/** The ability in its own words, plus the aim guide bonus the words don't already cover. */
private fun strikerPerks(striker: StrikerConfig): String {
    val guide = ((striker.aimGuideLength - 1f) * 100).roundToInt()
    val mentionsGuide = "guide" in striker.ability.summary.lowercase()
    return if (guide > 0 && !mentionsGuide) "${striker.ability.summary} Guide +$guide%." else striker.ability.summary
}

@Composable
fun StrikerPreview(config: StrikerConfig, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Spacer(
        modifier
            .size(size)
            .drawWithCache {
                val art = StrikerArt(config, radius = this.size.minDimension * 0.4f)
                onDrawBehind { drawStriker(art, this.size.width / 2f, this.size.height / 2f) }
            }
    )
}

/** Three carrom men of the set, fanned out. */
@Composable
fun CoinSetPreview(set: CoinSet, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    Spacer(
        modifier
            .size(size)
            .drawWithCache {
                val r = this.size.minDimension * 0.22f
                val art = CoinSetArt(set, r)
                onDrawBehind {
                    val w = this.size.width
                    val h = this.size.height
                    drawCarromMan(art.black, w * 0.3f, h * 0.36f)
                    drawCarromMan(art.white, w * 0.7f, h * 0.36f)
                    drawCarromMan(art.queen, w * 0.5f, h * 0.68f)
                }
            }
    )
}

@Composable
fun BoardPreview(theme: BoardTheme, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val art = remember(theme) { BoardArt(theme, if (theme.power == BoardPower.WIDE_POCKETS) Powers.WIDE_POCKET_BOARD_SCALE else 1f) }
    Canvas(
        modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
    ) {
        val s = this.size.width / BoardGeometry.BOARD_SIZE
        scale(s, s, pivot = Offset.Zero) { drawCarromBoard(art) }
    }
}

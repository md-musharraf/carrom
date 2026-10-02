package com.example.royalcarromclassic.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.royalcarromclassic.data.*
import com.example.royalcarromclassic.engine.BoardGeometry
import com.example.royalcarromclassic.engine.CarromPhysicsEngine
import com.example.royalcarromclassic.engine.Powers
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.board.BoardArt
import com.example.royalcarromclassic.ui.board.CoinSetArt
import com.example.royalcarromclassic.ui.board.StrikerArt
import com.example.royalcarromclassic.ui.board.drawCarromBoard
import com.example.royalcarromclassic.ui.board.drawCarromMan
import com.example.royalcarromclassic.ui.board.drawStriker
import com.example.royalcarromclassic.ui.components.*

/** Everything the home page can start or open. */
interface HomeActions {
    fun quickPlay(difficulty: AIDifficulty)
    fun resume()
    fun setUpMatch(mode: GameMode)
    fun startMode(mode: GameMode, difficulty: AIDifficulty)
    fun openTrickShots()
    fun openOnline()
    fun openCommunity()
    fun openAccount()
    fun openLocker()
    fun openWheel()
    fun openSettings()
    fun openRules()
    fun togglePowers()
    fun setPlayStyle(style: PlayStyle)
}

/** What the home page shows; plain values so it previews and tests without a ViewModel. */
data class HomeInfo(
    val stats: PlayerStats,
    val state: GameState,
    val striker: StrikerConfig,
    val coinSet: CoinSet,
    val die: DiceSkin,
    val board: BoardTheme,
    val playerName: String?,
    val signedIn: Boolean?,
    val onlineAvailable: Boolean,
    val wheelReady: Boolean,
    val luckyShotsLeft: Int,
    val trickStars: Int,
    val trickStarsMax: Int,
    val timeAttackBest: Int
) {
    /** A local match left mid-way that the player can go back to. */
    val canResume: Boolean
        get() = !state.isGameOver && state.shotsPlayed > 0 && state.mode != GameMode.ONLINE && state.mode != GameMode.LUCKY_SHOT
}

private data class ModeTile(
    val mode: GameMode,
    val title: String,
    val tagline: String,
    val glyph: Glyph,
    val accent: Color,
    val badge: String? = null,
    val enabled: Boolean = true,
    /** Overrides the mode's usual start (online tiles open sheets instead). */
    val action: (() -> Unit)? = null
)

/**
 * The lobby: who you are, one-tap quick play, every mode, your loadout and its powers, and your
 * career. The table itself is a separate screen, so the board gets the whole display.
 */
@Composable
fun HomeScreen(info: HomeInfo, actions: HomeActions, modifier: Modifier = Modifier) {
    var difficulty by rememberSaveableDifficulty(info.state.aiDifficulty)
    var onlineTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HomeTopBar(info, actions)
        HeroCard(info, difficulty, onDifficulty = { difficulty = it }, actions = actions)

        OrnamentHeading("Play style")
        PlayStyleCard(info.state.playStyle, actions::setPlayStyle)

        OrnamentHeading("Game modes")
        SegmentedSelector(
            options = listOf("Offline", "Online"),
            selectedIndex = if (onlineTab) 1 else 0,
            onSelect = { onlineTab = it == 1 }
        )
        if (onlineTab) {
            if (info.onlineAvailable) OnlineCard(info, actions) else OfflineNotice()
            ModeGrid(onlineTiles(info, actions), onPick = { it.action?.invoke() })
        } else {
            ModeGrid(modeTiles(info), onPick = { tile ->
                when (tile.mode) {
                    GameMode.PASS_AND_PLAY, GameMode.DICE, GameMode.DISC_POOL, GameMode.CLASSIC, GameMode.FREESTYLE ->
                        actions.setUpMatch(tile.mode)
                    GameMode.TRICK_SHOTS -> actions.openTrickShots()
                    GameMode.ONLINE -> actions.openOnline()
                    else -> actions.startMode(tile.mode, difficulty)
                }
            })
        }

        OrnamentHeading("Your loadout")
        LoadoutCard(info, actions)

        OrnamentHeading("Career")
        CareerRow(info.stats)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ClassicButton("Rules", onClick = actions::openRules, style = ButtonStyle.Outline, leading = Glyph.Rules, modifier = Modifier.weight(1f))
            ClassicButton("Settings", onClick = actions::openSettings, style = ButtonStyle.Outline, leading = Glyph.Settings, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun rememberSaveableDifficulty(initial: AIDifficulty): MutableState<AIDifficulty> =
    androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initial) }

@Composable
private fun HomeTopBar(info: HomeInfo, actions: HomeActions) {
    val stats = info.stats
    val xp by animateFloatAsState(stats.xp.toFloat() / stats.xpToNextLevel.coerceAtLeast(1), tween(700), label = "homeXp")
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPressScale(interaction, pressedScale = 0.95f)
    Row(
        Modifier
            .fillMaxWidth()
            .classicPanel(raised = true)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier
                .weight(1f)
                .scaledBy(press)
                .clickable(interaction, indication = null, role = Role.Button, onClickLabel = "Account", onClick = actions::openAccount),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box {
                Medallion(size = 46.dp, accent = CarromPalette.GoldDeep, progress = xp) {
                    Text("${stats.level}", style = MaterialTheme.typography.titleLarge, color = CarromPalette.Ivory)
                }
                if (info.signedIn != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (info.signedIn) CarromPalette.Jade else CarromPalette.Muted)
                            .border(1.5.dp, CarromPalette.Mahogany, CircleShape)
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    info.playerName ?: "Welcome, player",
                    style = MaterialTheme.typography.titleMedium,
                    color = CarromPalette.Ivory,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("Level ${stats.level} · ${stats.xp}/${stats.xpToNextLevel} XP", style = MaterialTheme.typography.bodySmall, color = CarromPalette.Muted, maxLines = 1)
            }
        }
        CoinAmount(stats.coins)
        Box {
            GlyphButton(Glyph.Gift, contentDescription = "Daily spin", onClick = actions::openWheel, modifier = Modifier.size(40.dp))
            if (info.wheelReady) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(CarromPalette.CrimsonLight)
                )
            }
        }
    }
}

/** The title plate: a live miniature of your table, quick play against the bot, and resume. */
@Composable
private fun HeroCard(info: HomeInfo, difficulty: AIDifficulty, onDifficulty: (AIDifficulty) -> Unit, actions: HomeActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .classicPanel(accentAlpha = 0.55f, raised = true)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ROYAL", style = MaterialTheme.typography.labelMedium, color = CarromPalette.Gold)
                Text("Carrom", style = MaterialTheme.typography.displayMedium, color = CarromPalette.GoldLight)
                Text(
                    "Flick, cut and cover the queen. Every striker, coin set, die and board has its own power.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CarromPalette.Parchment
                )
            }
            MiniTable(info.board, info.coinSet, info.striker, Modifier.size(118.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("QUICK MATCH VS BOT", style = MaterialTheme.typography.labelSmall, color = CarromPalette.Muted)
            SegmentedSelector(
                options = DIFFICULTIES.map { it.second },
                selectedIndex = DIFFICULTIES.indexOfFirst { it.first == difficulty }.coerceAtLeast(0),
                onSelect = { onDifficulty(DIFFICULTIES[it].first) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ClassicButton("Play", onClick = { actions.quickPlay(difficulty) }, leading = Glyph.Bolt, height = 54.dp, modifier = Modifier.weight(1f))
            if (info.canResume) {
                ClassicButton(
                    "Resume ${info.state.mode.displayName}",
                    onClick = actions::resume,
                    style = ButtonStyle.Outline,
                    height = 54.dp,
                    horizontalPadding = 10.dp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** A slowly turning miniature of the equipped board, coins and striker. */
@Composable
private fun MiniTable(board: BoardTheme, coins: CoinSet, striker: StrikerConfig, modifier: Modifier = Modifier) {
    val boardArt = remember(board) { BoardArt(board, if (board.power == BoardPower.WIDE_POCKETS) Powers.WIDE_POCKET_BOARD_SCALE else 1f) }
    val radius = Powers.discRadius(coins.power)
    val coinArt = remember(coins) { CoinSetArt(coins, radius) }
    val strikerArt = remember(striker) { StrikerArt(striker) }
    val rack = remember(coins) { CarromPhysicsEngine.generateClassicCluster(radius) }
    val spin by rememberInfiniteTransition(label = "miniTable").animateFloat(
        0f, 360f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "rackSpin"
    )
    Canvas(
        modifier
            .shadow(10.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .semantics { contentDescription = "Your table: ${board.name} with ${coins.name}" }
    ) {
        val s = size.width / BoardGeometry.BOARD_SIZE
        scale(s, s, pivot = Offset.Zero) {
            drawCarromBoard(boardArt)
            rotate(spin, Offset(BoardGeometry.CENTER, BoardGeometry.CENTER)) {
                for (p in rack) drawCarromMan(coinArt.forType(p.type), p.x, p.y)
            }
            drawStriker(strikerArt, BoardGeometry.CENTER, BoardGeometry.BASELINE_BOTTOM_Y)
        }
    }
}

private fun modeTiles(info: HomeInfo) = listOf(
    ModeTile(GameMode.PASS_AND_PLAY, "Party", "2–4 players · people or bots", Glyph.Players, CarromPalette.Silver, "2–4P"),
    ModeTile(GameMode.DICE, "Dice Carrom", "Roll a power before every shot", Glyph.Dice, CarromPalette.GoldLight, "New"),
    ModeTile(GameMode.DISC_POOL, "Disc Pool", "Own a colour · clear it first", Glyph.Modes, CarromPalette.Jade),
    ModeTile(GameMode.CLASSIC, "Classic", "White 10 · black 5 · queen 25", Glyph.Trophy, CarromPalette.Amber),
    ModeTile(GameMode.FREESTYLE, "Freestyle", "First to ${com.example.royalcarromclassic.engine.CarromRules.FREESTYLE_TARGET} points", Glyph.Bolt, CarromPalette.CrimsonLight),
    ModeTile(GameMode.BLITZ, "Blitz", "10 seconds a shot vs the bot", Glyph.Bolt, CarromPalette.Amber),
    ModeTile(
        GameMode.TIME_ATTACK, "Time Attack",
        if (info.timeAttackBest > 0) "90 seconds · best ${info.timeAttackBest}" else "Most points in 90 seconds",
        Glyph.Timer, CarromPalette.GoldLight, "New"
    ),
    ModeTile(GameMode.PRACTICE, "Practice", "Free shots, no pressure", Glyph.Target, CarromPalette.Parchment),
    ModeTile(GameMode.TRICK_SHOTS, "Trick Shots", "Puzzles · ★ ${info.trickStars}/${info.trickStarsMax}", Glyph.Star, CarromPalette.GoldLight),
    ModeTile(
        GameMode.LUCKY_SHOT, "Lucky Shot",
        if (info.luckyShotsLeft > 0) "${info.luckyShotsLeft} free shots today" else "Back at midnight",
        Glyph.Target, CarromPalette.GoldLight, "Daily", enabled = info.luckyShotsLeft > 0
    )
)

@Composable
private fun ModeGrid(tiles: List<ModeTile>, onPick: (ModeTile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                row.forEach { tile -> ModeTileCard(tile, onClick = { onPick(tile) }, modifier = Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModeTileCard(tile: ModeTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPressScale(interaction, pressedScale = 0.96f)
    Column(
        modifier
            .scaledBy(press)
            .classicPanel(accent = tile.accent, accentAlpha = 0.35f)
            .clickable(interaction, indication = null, enabled = tile.enabled, role = Role.Button, onClick = onClick)
            .graphicsLayer { alpha = if (tile.enabled) 1f else 0.5f }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Medallion(size = 38.dp, accent = tile.accent) { GlyphIcon(tile.glyph, size = 19.dp, tint = CarromPalette.Ink) }
            Spacer(Modifier.weight(1f))
            if (tile.badge != null) Tag(tile.badge, color = tile.accent)
        }
        Text(tile.title, style = MaterialTheme.typography.titleMedium, color = CarromPalette.Ivory, maxLines = 1)
        Text(tile.tagline, style = MaterialTheme.typography.bodySmall, color = CarromPalette.Parchment, maxLines = 2)
    }
}

/** Kids, Normal or Meme: changes the sounds (and the kids' aim guide) everywhere. */
@Composable
private fun PlayStyleCard(style: PlayStyle, onSelect: (PlayStyle) -> Unit) {
    val styles = PlayStyle.entries
    Column(
        Modifier
            .fillMaxWidth()
            .classicPanel(accentAlpha = 0.3f)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SegmentedSelector(
            options = styles.map { it.title },
            selectedIndex = styles.indexOf(style),
            onSelect = { onSelect(styles[it]) }
        )
        Text(style.blurb, style = MaterialTheme.typography.bodySmall, color = CarromPalette.Parchment)
    }
}

/** Online tiles need the server; without it they open the sheets, which explain what's missing. */
private fun onlineTiles(info: HomeInfo, actions: HomeActions): List<ModeTile> {
    val signedIn = info.signedIn == true
    return listOf(
        ModeTile(GameMode.ONLINE, "Ranked", "Matched by rating · win the pot", Glyph.Trophy, CarromPalette.Jade, "1v1",
            enabled = info.onlineAvailable, action = actions::openOnline),
        ModeTile(GameMode.ONLINE, "Private room", "Share a code, play a friend", Glyph.Globe, CarromPalette.GoldLight,
            enabled = info.onlineAvailable, action = actions::openOnline),
        ModeTile(GameMode.ONLINE, "Friends", "Invites, friends and leaderboards", Glyph.Players, CarromPalette.Silver,
            enabled = info.onlineAvailable, action = actions::openCommunity),
        ModeTile(GameMode.ONLINE, "Account", if (signedIn) "Your profile and sign-in" else "Sign in to play online",
            Glyph.User, CarromPalette.Amber, if (signedIn) null else "Sign in",
            enabled = info.onlineAvailable, action = actions::openAccount)
    )
}

@Composable
private fun OfflineNotice() {
    Text(
        "Online play isn't available right now. Every offline mode still works — no internet needed.",
        style = MaterialTheme.typography.bodySmall,
        color = CarromPalette.Parchment,
        modifier = Modifier
            .fillMaxWidth()
            .classicPanel(accentAlpha = 0.18f)
            .padding(12.dp)
    )
}

@Composable
private fun OnlineCard(info: HomeInfo, actions: HomeActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .classicPanel(accent = CarromPalette.Jade, accentAlpha = 0.5f, raised = true)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Medallion(size = 44.dp, accent = CarromPalette.Jade) { GlyphIcon(Glyph.Globe, size = 22.dp, tint = CarromPalette.Ink) }
        Column(Modifier.weight(1f)) {
            Text("Play Online", style = MaterialTheme.typography.titleMedium, color = CarromPalette.Ivory)
            Text(
                if (info.signedIn == true) "Ranked arenas and private rooms" else "Sign in to play ranked and with friends",
                style = MaterialTheme.typography.bodySmall,
                color = CarromPalette.Parchment
            )
        }
        GlyphButton(Glyph.Players, contentDescription = "Friends and leaderboards", onClick = actions::openCommunity, modifier = Modifier.size(42.dp))
        ClassicButton("Go", onClick = actions::openOnline, height = 42.dp, horizontalPadding = 14.dp)
    }
}

/** The four equipped pieces and their powers; tap to open the Locker. */
@Composable
private fun LoadoutCard(info: HomeInfo, actions: HomeActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .classicPanel(accentAlpha = 0.3f)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LoadoutSlot("Striker", info.striker.ability.title, Modifier.weight(1f)) { StrikerPreview(info.striker, size = 44.dp) }
            LoadoutSlot("Coins", info.coinSet.power.title, Modifier.weight(1f)) { CoinSetPreview(info.coinSet, size = 48.dp) }
            LoadoutSlot("Dice", info.die.power.title, Modifier.weight(1f)) { DieView(DiceFace.BONUS_TURN, info.die, size = 42.dp) }
            LoadoutSlot("Board", info.board.power.title, Modifier.weight(1f)) { BoardPreview(info.board, size = 44.dp) }
        }
        PowersToggle(enabled = info.state.powersEnabled, onToggle = actions::togglePowers)
        ClassicButton("Open the Locker", onClick = actions::openLocker, leading = Glyph.Shop, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LoadoutSlot(label: String, power: String, modifier: Modifier = Modifier, preview: @Composable () -> Unit) {
    Column(
        modifier
            .classicPanel(accentAlpha = 0.18f, raised = true)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) { preview() }
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = CarromPalette.Muted, maxLines = 1)
        Text(power, style = MaterialTheme.typography.labelMedium, color = CarromPalette.GoldLight, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CareerRow(stats: PlayerStats) {
    val winRate = if (stats.matchesPlayed > 0) stats.matchesWon * 100 / stats.matchesPlayed else 0
    Row(
        Modifier
            .fillMaxWidth()
            .classicPanel(accentAlpha = 0.18f)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        CareerStat("${stats.matchesPlayed}", "Played")
        CareerStat("${stats.matchesWon}", "Won")
        CareerStat("$winRate%", "Win rate")
        CareerStat("${stats.totalPockets}", "Pockets")
        CareerStat("${stats.queenCovers}", "Queens")
    }
}

@Composable
private fun CareerStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = CarromPalette.GoldLight, maxLines = 1)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = CarromPalette.Muted, maxLines = 1)
    }
}

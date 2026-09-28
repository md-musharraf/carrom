package com.example.royalcarromclassic.ui.online

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.royalcarromclassic.online.ArenaDto
import com.example.royalcarromclassic.online.DailyStatusDto
import com.example.royalcarromclassic.online.ProfileDto
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.components.*
import kotlinx.coroutines.delay

/** Ranked matchmaking by arena, private rooms with friends, and the online daily reward. */
@Composable
fun PlayOnlineSheet(
    state: OnlineUiState,
    actions: OnlineActions,
    onOpenAccount: () -> Unit,
    onShareText: ((String) -> Unit)?,
    onDismiss: () -> Unit
) {
    val profile = state.profile
    LaunchedEffect(profile?.playerId) { if (profile != null) actions.loadLobby() }

    ClassicSheet(
        title = "Play Online",
        subtitle = profile?.let { "Rating ${it.rating} · Level ${it.level}" } ?: "Ranked matches and private rooms",
        trailing = { ConnectionBadge(state.connection) },
        onDismiss = onDismiss
    ) {
        if (profile == null) {
            EmptyNote("Sign in to play against real opponents, add friends and climb the leaderboards.")
            ClassicButton("Sign in", onClick = onOpenAccount, leading = Glyph.User, modifier = Modifier.fillMaxWidth())
            return@ClassicSheet
        }

        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedContent(
                targetState = state.lobby,
                contentKey = { it::class },
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "lobby"
            ) { lobby ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (lobby) {
                        LobbyState.Idle -> IdleLobby(state, profile, actions)
                        is LobbyState.Searching -> SearchingCard(lobby, state.arenas, busy = state.busy, onCancel = actions::cancelSearch)
                        is LobbyState.Hosting -> HostingCard(lobby, state.friends, busy = state.busy, actions = actions, onShareText = onShareText)
                        LobbyState.Joining -> SearchingCard(null, state.arenas, busy = true, onCancel = {})
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleLobby(state: OnlineUiState, profile: ProfileDto, actions: OnlineActions) {
    var modeIndex by remember { mutableIntStateOf(0) }
    val mode = ONLINE_MODES[modeIndex].first

    state.daily?.let { DailyRewardCard(it, busy = state.busy, onClaim = actions::claimDailyReward) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("MODE", style = MaterialTheme.typography.labelSmall, color = CarromPalette.Muted, modifier = Modifier.weight(1f))
        GlyphIcon(Glyph.Coin, size = 14.dp)
        Spacer(Modifier.width(5.dp))
        Text("%,d".format(profile.wallet.coins), style = MaterialTheme.typography.titleSmall, color = CarromPalette.GoldLight)
    }
    SegmentedSelector(options = ONLINE_MODES.map { it.second }, selectedIndex = modeIndex, onSelect = { modeIndex = it })

    OrnamentHeading("Ranked arenas")
    if (state.arenas.isEmpty()) EmptyNote("Loading arenas…")
    state.arenas.forEach { arena ->
        ArenaCard(arena, enabled = !state.busy && arena.unlocked && arena.affordable, onPlay = { actions.findMatch(mode, arena) })
    }

    OrnamentHeading("Play a friend")
    PrivateRoomCard(busy = state.busy, onCreate = { actions.createRoom(mode) }, onJoin = actions::joinRoom)
}

@Composable
private fun DailyRewardCard(daily: DailyStatusDto, busy: Boolean, onClaim: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .classicPanel(accent = CarromPalette.GoldLight, accentAlpha = if (daily.claimedToday) 0.2f else 0.7f, raised = !daily.claimedToday)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Medallion(size = 40.dp, accent = CarromPalette.Gold) { GlyphIcon(Glyph.Gift, size = 20.dp, tint = CarromPalette.Ink) }
        Column(Modifier.weight(1f)) {
            Text(
                if (daily.claimedToday) "Daily reward collected" else "Daily reward · day ${daily.streak + 1}",
                style = MaterialTheme.typography.titleSmall,
                color = CarromPalette.Ivory
            )
            Text(
                if (daily.claimedToday) "Come back tomorrow to keep your ${daily.streak}-day streak"
                else "+${daily.nextReward.coins} coins" + if (daily.nextReward.gems > 0) " · +${daily.nextReward.gems} gems" else "",
                style = MaterialTheme.typography.bodySmall,
                color = CarromPalette.Muted
            )
        }
        if (!daily.claimedToday) ClassicButton("Claim", onClick = onClaim, enabled = !busy, height = 38.dp, horizontalPadding = 14.dp)
    }
}

@Composable
private fun ArenaCard(arena: ArenaDto, enabled: Boolean, onPlay: () -> Unit) {
    val accent = when (arena.id) {
        "bronze" -> CarromPalette.Amber
        "silver" -> CarromPalette.Silver
        "gold" -> CarromPalette.GoldLight
        else -> CarromPalette.CrimsonLight
    }
    Row(
        Modifier
            .fillMaxWidth()
            .classicPanel(accent = accent, accentAlpha = if (enabled) 0.45f else 0.15f, raised = enabled)
            .clickable(enabled = enabled, role = Role.Button, onClick = onPlay)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Medallion(size = 42.dp, accent = if (arena.unlocked) accent else CarromPalette.Seam) {
            GlyphIcon(if (arena.unlocked) Glyph.Trophy else Glyph.Lock, size = 20.dp, tint = CarromPalette.Ink)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(arena.name, style = MaterialTheme.typography.titleMedium, color = if (enabled) CarromPalette.Ivory else CarromPalette.Muted)
            Text(
                when {
                    !arena.unlocked -> "Unlocks at level ${arena.minLevel}"
                    !arena.affordable -> "Needs ${"%,d".format(arena.entryFee)} coins"
                    else -> "Entry ${"%,d".format(arena.entryFee)} · Win ${"%,d".format(arena.entryFee * 2)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = CarromPalette.Parchment
            )
        }
        if (enabled) ClassicButton("Play", onClick = onPlay, height = 36.dp, horizontalPadding = 14.dp)
    }
}

@Composable
private fun PrivateRoomCard(busy: Boolean, onCreate: () -> Unit, onJoin: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxWidth()
            .classicPanel(accentAlpha = 0.25f)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Create a room and send the code, or join a friend's room. Private games are unranked and free.", style = MaterialTheme.typography.bodySmall, color = CarromPalette.Parchment)
        ClassicButton("Create room", onClick = onCreate, enabled = !busy, leading = Glyph.Players, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ClassicTextField(
                value = code,
                onValueChange = { v -> code = v.filter(Char::isLetterOrDigit).uppercase().take(8) },
                placeholder = "Room code",
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Go,
                onImeAction = { if (code.length >= 4) onJoin(code) },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            )
            ClassicButton("Join", onClick = { onJoin(code) }, enabled = !busy && code.length >= 4, style = ButtonStyle.Outline, height = 50.dp)
        }
    }
}

/** Ticks once a second while shown, for countdowns and elapsed timers. */
@Composable
private fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L - now % 1000L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun SearchingCard(search: LobbyState.Searching?, arenas: List<ArenaDto>, busy: Boolean, onCancel: () -> Unit) {
    val now = rememberNow()
    val arenaName = search?.let { s -> arenas.firstOrNull { it.id == s.arena }?.name ?: s.arena.replaceFirstChar { it.uppercase() } }
    Column(
        Modifier
            .fillMaxWidth()
            .classicPanel(accentAlpha = 0.6f, raised = true)
            .padding(vertical = 22.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SearchRadar(Modifier.size(120.dp))
        Text(if (search == null) "Joining room…" else "Finding an opponent…", style = MaterialTheme.typography.headlineSmall, color = CarromPalette.GoldLight)
        if (search != null) {
            Text(
                "$arenaName · ${ONLINE_MODES.firstOrNull { it.first == search.mode }?.second ?: search.mode} · entry ${"%,d".format(search.entryFee)}",
                style = MaterialTheme.typography.bodySmall,
                color = CarromPalette.Parchment,
                textAlign = TextAlign.Center
            )
            Text(formatSeconds((now - search.sinceMillis) / 1000), style = MaterialTheme.typography.titleLarge, color = CarromPalette.Ivory)
            Text("The search widens the longer you wait.", style = MaterialTheme.typography.bodySmall, color = CarromPalette.Muted)
            ClassicButton("Cancel", onClick = onCancel, enabled = !busy, style = ButtonStyle.Outline, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** A brass sweep turning over concentric rings. */
@Composable
private fun SearchRadar(modifier: Modifier = Modifier) {
    val sweep by rememberInfiniteTransition(label = "radar").animateFloat(
        0f, 360f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "radarSweep"
    )
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        for (i in 1..3) drawCircle(CarromPalette.Gold.copy(alpha = 0.25f), r * i / 3f, c, style = Stroke(1.5f))
        drawArc(
            CarromPalette.GoldLight.copy(alpha = 0.35f), sweep - 50f, 50f, true,
            topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2)
        )
        drawArc(
            CarromPalette.GoldLight, sweep - 2f, 2f, true,
            topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = Stroke(3f, cap = StrokeCap.Round)
        )
        drawCircle(CarromPalette.GoldLight, 5f, c)
    }
}

@Composable
private fun HostingCard(
    room: LobbyState.Hosting,
    friends: List<ProfileDto>,
    busy: Boolean,
    actions: OnlineActions,
    onShareText: ((String) -> Unit)?
) {
    @Suppress("DEPRECATION") // LocalClipboard is suspend-only and newer than the oldest Compose we support.
    val clipboard = LocalClipboardManager.current
    val now = rememberNow()
    val invited = remember { mutableStateListOf<String>() }

    Column(
        Modifier
            .fillMaxWidth()
            .classicPanel(accentAlpha = 0.6f, raised = true)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("YOUR ROOM CODE", style = MaterialTheme.typography.labelMedium, color = CarromPalette.Muted)
        Text(room.code.chunked(3).joinToString(" "), style = MaterialTheme.typography.displaySmall, color = CarromPalette.GoldLight)
        Text(
            "Waiting for your friend · expires in ${formatSeconds((room.expiresAtMillis - now) / 1000)}",
            style = MaterialTheme.typography.bodySmall,
            color = CarromPalette.Parchment,
            textAlign = TextAlign.Center
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClassicButton("Copy", onClick = { clipboard.setText(AnnotatedString(room.code)) }, style = ButtonStyle.Outline, leading = Glyph.Copy, modifier = Modifier.weight(1f))
            if (onShareText != null) {
                ClassicButton(
                    "Share",
                    onClick = { onShareText("Join my Royal Carrom room! Open Play Online → Join and enter ${room.code}") },
                    leading = Glyph.Share,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    val online = friends.sortedByDescending { it.online }
    if (online.isNotEmpty()) {
        OrnamentHeading("Invite friends")
        online.forEach { friend ->
            PlayerRow(friend, showPresence = true, subtitle = if (friend.online) "Online now" else "Offline") {
                val sent = friend.playerId in invited
                ClassicButton(
                    if (sent) "Sent" else "Invite",
                    onClick = {
                        invited += friend.playerId
                        actions.inviteFriend(friend)
                    },
                    enabled = !busy && !sent && friend.online,
                    height = 34.dp,
                    horizontalPadding = 12.dp
                )
            }
        }
    }
    ClassicButton("Close room", onClick = actions::cancelRoom, enabled = !busy, style = ButtonStyle.Outline, modifier = Modifier.fillMaxWidth())
}

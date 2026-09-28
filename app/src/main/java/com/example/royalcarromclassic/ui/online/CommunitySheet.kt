package com.example.royalcarromclassic.ui.online

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.royalcarromclassic.online.Board
import com.example.royalcarromclassic.online.FriendRequestDto
import com.example.royalcarromclassic.online.InviteDto
import com.example.royalcarromclassic.online.LeaderboardEntryDto
import com.example.royalcarromclassic.online.ProfileDto
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.components.*

enum class CommunityTab(val label: String) { FRIENDS("Friends"), LEADERBOARD("Leaderboard") }

/** Friends (add by Player ID, requests, presence) and the leaderboards, in two tabs. */
@Composable
fun CommunitySheet(
    state: OnlineUiState,
    actions: OnlineActions,
    initialTab: CommunityTab,
    onOpenAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableStateOf(initialTab) }
    val profile = state.profile
    LaunchedEffect(profile?.playerId) {
        if (profile != null) {
            actions.loadFriends()
            actions.selectBoard(state.board)
        }
    }

    ClassicSheet(title = "Community", subtitle = profile?.let { "Your Player ID: ${it.displayId}" } ?: "Friends and leaderboards", onDismiss = onDismiss) {
        if (profile == null) {
            EmptyNote("Sign in to add friends and see where you rank.")
            ClassicButton("Sign in", onClick = onOpenAccount, leading = Glyph.User, modifier = Modifier.fillMaxWidth())
            return@ClassicSheet
        }
        SegmentedSelector(
            options = CommunityTab.entries.map { it.label },
            selectedIndex = tab.ordinal,
            onSelect = { tab = CommunityTab.entries[it] }
        )
        when (tab) {
            CommunityTab.FRIENDS -> FriendsTab(state, actions)
            CommunityTab.LEADERBOARD -> LeaderboardTab(state, actions)
        }
    }
}

@Composable
private fun FriendsTab(state: OnlineUiState, actions: OnlineActions) {
    var playerId by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<ProfileDto?>(null) }
    val valid = playerId.count { it.isLetterOrDigit() } == 8
    val add = {
        actions.sendFriendRequest(playerId)
        playerId = ""
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ClassicTextField(
            value = playerId,
            onValueChange = { v -> playerId = v.filter { it.isLetterOrDigit() || it == '-' }.uppercase().take(9) },
            placeholder = "Friend's Player ID",
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Send,
            onImeAction = { if (valid) add() },
            leading = Glyph.Players,
            enabled = !state.busy,
            modifier = Modifier.weight(1f)
        )
        ClassicButton("Add", onClick = add, enabled = !state.busy && valid, height = 50.dp)
    }

    val incoming = state.requests.filter { it.direction == "incoming" }
    val outgoing = state.requests.filter { it.direction != "incoming" }
    val friends = state.friends.sortedWith(compareByDescending<ProfileDto> { it.online }.thenBy { it.displayName.lowercase() })

    LazyColumn(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (incoming.isNotEmpty()) {
            item(key = "incoming") { OrnamentHeading("Requests") }
            items(incoming, key = { "in_" + it.id }) { request -> IncomingRequest(request, state.busy, actions) }
        }
        item(key = "friends") { OrnamentHeading("Friends · ${friends.size}") }
        if (friends.isEmpty()) {
            item(key = "empty") { EmptyNote("No friends yet. Share your Player ID or add a friend's.") }
        }
        items(friends, key = { "f_" + it.playerId }) { friend ->
            PlayerRow(friend, showPresence = true) {
                GlyphButton(Glyph.Close, "Remove ${friend.displayName}", onClick = { removing = friend }, modifier = Modifier.size(34.dp), tint = CarromPalette.Muted, glyphSize = 14.dp)
            }
        }
        if (outgoing.isNotEmpty()) {
            item(key = "outgoing") { OrnamentHeading("Sent") }
            items(outgoing, key = { "out_" + it.id }) { request ->
                PlayerRow(request.player, subtitle = "Waiting for them to accept") { Tag("Pending", color = CarromPalette.Silver) }
            }
        }
    }

    removing?.let { friend ->
        ConfirmDialog(
            title = "Remove friend?",
            message = "${friend.displayName} will be removed from your friends.",
            confirmLabel = "Remove",
            onConfirm = { actions.removeFriend(friend) },
            onDismiss = { removing = null }
        )
    }
}

@Composable
private fun IncomingRequest(request: FriendRequestDto, busy: Boolean, actions: OnlineActions) {
    PlayerRow(request.player, subtitle = "Wants to be friends") {
        GlyphButton(Glyph.Close, "Decline", onClick = { actions.declineFriendRequest(request) }, enabled = !busy, modifier = Modifier.size(34.dp), tint = CarromPalette.Muted, glyphSize = 14.dp)
        GlyphButton(Glyph.Check, "Accept", onClick = { actions.acceptFriendRequest(request) }, enabled = !busy, modifier = Modifier.size(34.dp), tint = CarromPalette.Jade, glyphSize = 16.dp)
    }
}

@Composable
private fun LeaderboardTab(state: OnlineUiState, actions: OnlineActions) {
    SegmentedSelector(
        options = Board.entries.map { it.label },
        selectedIndex = state.board.ordinal,
        onSelect = { actions.selectBoard(Board.entries[it]) }
    )
    val board = state.leaderboard?.takeIf { it.board == state.board.path }
    val scoreLabel = if (state.board == Board.WEEKLY) "wins" else "rating"
    when {
        board == null && state.leaderboardLoading -> EmptyNote("Loading…")
        board == null || board.entries.isEmpty() -> EmptyNote(
            if (state.board == Board.FRIENDS) "Add friends to compare your ratings." else "No ranked players here yet. Be the first!"
        )
        else -> {
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(board.entries, key = { it.playerId }) { entry -> LeaderboardRow(entry, scoreLabel) }
            }
            val me = board.me
            if (me != null && board.entries.none { it.isMe }) {
                GoldDivider(alpha = 0.3f)
                LeaderboardRow(me, scoreLabel)
            }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntryDto, scoreLabel: String) {
    val medal = when (entry.rank) {
        1 -> CarromPalette.GoldLight
        2 -> CarromPalette.Silver
        3 -> Color(0xFFCD8B4E)
        else -> null
    }
    Row(
        Modifier
            .fillMaxWidth()
            .classicPanel(accent = if (entry.isMe) CarromPalette.GoldLight else CarromPalette.Gold, accentAlpha = if (entry.isMe) 0.7f else 0.16f, raised = entry.isMe)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
            if (medal != null) {
                Medallion(size = 28.dp, accent = medal) {
                    Text("${entry.rank}", style = MaterialTheme.typography.labelLarge, color = CarromPalette.Ink)
                }
            } else {
                Text("${entry.rank}", style = MaterialTheme.typography.titleSmall, color = CarromPalette.Parchment)
            }
        }
        PlayerCrest(entry.displayName, size = 32.dp, accent = if (entry.isMe) CarromPalette.GoldLight else CarromPalette.SilverDeep)
        Column(Modifier.weight(1f)) {
            Text(
                if (entry.isMe) "${entry.displayName} (you)" else entry.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = CarromPalette.Ivory,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("Level ${entry.level}" + (entry.country?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = CarromPalette.Muted, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("%,d".format(entry.score), style = MaterialTheme.typography.titleMedium, color = CarromPalette.GoldLight)
            Text(scoreLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = CarromPalette.Muted)
        }
    }
}

/** A friend's invitation to their private room, sliding down over the table. */
@Composable
fun InviteBanner(invite: InviteDto?, onAccept: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = invite != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        val shown = invite ?: return@AnimatedVisibility
        Row(
            Modifier
                .fillMaxWidth()
                .classicPanel(accent = CarromPalette.GoldLight, accentAlpha = 0.8f, raised = true)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PlayerCrest(shown.from.displayName, size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text("${shown.from.displayName} invites you", style = MaterialTheme.typography.titleSmall, color = CarromPalette.Ivory, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${ONLINE_MODES.firstOrNull { it.first == shown.mode }?.second ?: shown.mode} · private room",
                    style = MaterialTheme.typography.bodySmall,
                    color = CarromPalette.Muted
                )
            }
            GlyphButton(Glyph.Close, "Decline invite", onClick = onDismiss, modifier = Modifier.size(36.dp), tint = CarromPalette.Muted, glyphSize = 14.dp)
            ClassicButton("Play", onClick = onAccept, height = 36.dp, horizontalPadding = 14.dp)
        }
    }
}

/** A green or grey presence dot. */
@Composable
fun PresenceDot(online: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (online) CarromPalette.Jade else CarromPalette.Muted)
    )
}

package com.example.royalcarromclassic.ui.online

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.royalcarromclassic.online.MatchSummaryDto
import com.example.royalcarromclassic.online.ProfileDto
import com.example.royalcarromclassic.online.SocialAuthProvider
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.components.*

private val GoogleBlue = Color(0xFF4285F4)
private val FacebookBlue = Color(0xFF1877F2)

/** Sign in (Google, Facebook, phone or guest), or manage the signed-in account. */
@Composable
fun AccountSheet(
    state: OnlineUiState,
    actions: OnlineActions,
    socialAuth: SocialAuthProvider?,
    onDismiss: () -> Unit
) {
    val profile = state.profile
    ClassicSheet(
        title = if (profile == null) "Play Online" else "Your Account",
        subtitle = when {
            profile == null -> "Sign in to challenge players worldwide"
            profile.isGuest -> "Guest account on this device"
            else -> "Signed in with " + listOfNotNull(
                "Google".takeIf { profile.linked.google },
                "Facebook".takeIf { profile.linked.facebook },
                "phone".takeIf { profile.linked.phone != null }
            ).joinToString(" · ").ifEmpty { "your account" }
        },
        trailing = { ConnectionBadge(state.connection) },
        onDismiss = onDismiss
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (profile == null) {
                SignInOptions(state, actions, socialAuth, linking = false)
            } else {
                LaunchedEffect(profile.playerId) { actions.loadHistory() }
                ProfileCard(profile, busy = state.busy, onRename = actions::rename)
                if (profile.isGuest) {
                    OrnamentHeading("Secure your progress")
                    Text(
                        "Guest progress lives on this device only. Link an account to keep it forever and play on any phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CarromPalette.Parchment
                    )
                    SignInOptions(state, actions, socialAuth, linking = true)
                } else {
                    LinkedAccounts(profile)
                }
                MatchHistory(state.history)
                AccountActionsRow(busy = state.busy, onSignOut = actions::signOut, onDelete = actions::deleteAccount)
            }
        }
    }
}

@Composable
private fun SignInOptions(state: OnlineUiState, actions: OnlineActions, socialAuth: SocialAuthProvider?, linking: Boolean) {
    var phoneOpen by remember { mutableStateOf(state.phoneStep != PhoneStep.EnterNumber) }
    val enabled = !state.busy
    val verb = if (linking) "Link" else "Continue with"

    ProviderButton(
        "$verb Google",
        GoogleBlue,
        onClick = { socialAuth?.let(actions::signInWithGoogle) },
        enabled = enabled && socialAuth != null,
        letter = "G"
    )
    ProviderButton(
        "$verb Facebook",
        FacebookBlue,
        onClick = { socialAuth?.let(actions::signInWithFacebook) },
        enabled = enabled && socialAuth != null,
        letter = "f"
    )
    ProviderButton(
        if (linking) "Link phone number" else "Continue with phone",
        CarromPalette.Jade,
        onClick = { phoneOpen = !phoneOpen },
        enabled = enabled,
        glyph = Glyph.Phone
    )
    if (phoneOpen) PhoneSignIn(state, actions)
    if (!linking) {
        ProviderButton("Play as guest", CarromPalette.SilverDeep, onClick = actions::signInAsGuest, enabled = enabled, glyph = Glyph.User)
        Text(
            "Your Player ID is created on first sign-in. Friends add you with it.",
            style = MaterialTheme.typography.bodySmall,
            color = CarromPalette.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    if (state.busy) {
        Text("Please wait…", style = MaterialTheme.typography.labelMedium, color = CarromPalette.Gold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun PhoneSignIn(state: OnlineUiState, actions: OnlineActions) {
    AnimatedContent(targetState = state.phoneStep, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "phoneStep") { step ->
        Column(
            Modifier
                .fillMaxWidth()
                .classicPanel(accentAlpha = 0.2f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (step) {
                PhoneStep.EnterNumber -> {
                    var phone by remember { mutableStateOf("") }
                    Text("We'll text you a 6-digit code.", style = MaterialTheme.typography.bodySmall, color = CarromPalette.Parchment)
                    ClassicTextField(
                        value = phone,
                        onValueChange = { v -> phone = v.filter { it.isDigit() || it == '+' || it == ' ' }.take(20) },
                        placeholder = "Mobile number, e.g. +91 98765 43210",
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Send,
                        onImeAction = { if (phone.isNotBlank()) actions.startPhoneSignIn(phone) },
                        leading = Glyph.Phone,
                        enabled = !state.busy
                    )
                    ClassicButton("Send code", onClick = { actions.startPhoneSignIn(phone) }, enabled = !state.busy && phone.count { it.isDigit() } >= 6, modifier = Modifier.fillMaxWidth())
                }

                is PhoneStep.EnterCode -> {
                    var code by remember { mutableStateOf("") }
                    Text("Enter the code sent to ${step.phone}", style = MaterialTheme.typography.bodySmall, color = CarromPalette.Parchment)
                    ClassicTextField(
                        value = code,
                        onValueChange = { v -> code = v.filter(Char::isDigit).take(step.codeLength) },
                        placeholder = "•".repeat(step.codeLength),
                        keyboardType = KeyboardType.NumberPassword,
                        onImeAction = { if (code.length == step.codeLength) actions.verifyPhone(code) },
                        enabled = !state.busy,
                        centered = true
                    )
                    ClassicButton("Verify", onClick = { actions.verifyPhone(code) }, enabled = !state.busy && code.length == step.codeLength, modifier = Modifier.fillMaxWidth())
                    Text(
                        "Use a different number",
                        style = MaterialTheme.typography.labelMedium,
                        color = CarromPalette.Gold,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clickable(role = Role.Button, onClick = actions::editPhoneNumber)
                            .padding(6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: ProfileDto, busy: Boolean, onRename: (String) -> Unit) {
    @Suppress("DEPRECATION") // LocalClipboard is suspend-only and newer than the oldest Compose we support.
    val clipboard = LocalClipboardManager.current
    var editing by remember { mutableStateOf(false) }
    var draft by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var copied by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .classicPanel(accentAlpha = 0.5f, raised = true)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Medallion(size = 56.dp, accent = CarromPalette.Gold, progress = profile.progress.xp.toFloat() / profile.progress.xpToNext.coerceAtLeast(1)) {
                Text("${profile.level}", style = MaterialTheme.typography.headlineSmall, color = CarromPalette.Ivory)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (editing) {
                    ClassicTextField(
                        value = draft,
                        onValueChange = { draft = it.take(20) },
                        placeholder = "Display name",
                        capitalization = KeyboardCapitalization.Words,
                        onImeAction = {
                            if (draft.isNotBlank()) onRename(draft)
                            editing = false
                        },
                        enabled = !busy
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(profile.displayName, style = MaterialTheme.typography.titleLarge, color = CarromPalette.Ivory, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (profile.isGuest) Tag("Guest", color = CarromPalette.Silver)
                    }
                }
                Row(
                    Modifier.clickable(role = Role.Button, onClickLabel = "Copy player ID") {
                        clipboard.setText(AnnotatedString(profile.displayId))
                        copied = true
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("ID ${profile.displayId}", style = MaterialTheme.typography.labelLarge, color = CarromPalette.Gold)
                    GlyphIcon(Glyph.Copy, size = 13.dp, tint = CarromPalette.Gold)
                    if (copied) Text("Copied", style = MaterialTheme.typography.labelSmall, color = CarromPalette.Jade)
                }
            }
            GlyphButton(
                glyph = if (editing) Glyph.Check else Glyph.Edit,
                contentDescription = if (editing) "Save name" else "Edit name",
                onClick = {
                    if (editing && draft.isNotBlank() && draft != profile.displayName) onRename(draft)
                    editing = !editing
                },
                enabled = !busy,
                modifier = Modifier.size(38.dp)
            )
        }
        GoldDivider(alpha = 0.3f)
        Row(Modifier.fillMaxWidth()) {
            ProfileStat("Rating", "${profile.rating}", Modifier.weight(1f))
            ProfileStat("Wins", "${profile.stats.won}", Modifier.weight(1f))
            ProfileStat("Win rate", "${profile.stats.winRate}%", Modifier.weight(1f))
            ProfileStat("Coins", "%,d".format(profile.wallet.coins), Modifier.weight(1f))
        }
    }
}

@Composable
private fun ProfileStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = CarromPalette.GoldLight, maxLines = 1)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = CarromPalette.Muted, maxLines = 1)
    }
}

@Composable
private fun LinkedAccounts(profile: ProfileDto) {
    OrnamentHeading("Linked accounts")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatRow("Google", if (profile.linked.google) "Linked" else "—", if (profile.linked.google) CarromPalette.Jade else CarromPalette.Muted)
        StatRow("Facebook", if (profile.linked.facebook) "Linked" else "—", if (profile.linked.facebook) CarromPalette.Jade else CarromPalette.Muted)
        StatRow("Phone", profile.linked.phone ?: "—", if (profile.linked.phone != null) CarromPalette.Jade else CarromPalette.Muted)
    }
}

@Composable
private fun MatchHistory(history: List<MatchSummaryDto>) {
    OrnamentHeading("Recent matches")
    if (history.isEmpty()) {
        EmptyNote("No online matches yet. Your results will appear here.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        history.take(8).forEach { match ->
            val won = match.result == "win"
            Row(
                Modifier
                    .fillMaxWidth()
                    .classicPanel(accent = if (won) CarromPalette.Jade else CarromPalette.CrimsonLight, accentAlpha = 0.3f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Tag(if (won) "Won" else "Lost", color = if (won) CarromPalette.Jade else CarromPalette.CrimsonLight)
                Column(Modifier.weight(1f)) {
                    Text("vs ${match.opponent.name}", style = MaterialTheme.typography.titleSmall, color = CarromPalette.Ivory, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${match.myScore} – ${match.opponent.score} · ${match.mode.replaceFirstChar { it.uppercase() }}${if (match.ranked) " · Ranked" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CarromPalette.Muted,
                        maxLines = 1
                    )
                }
                if (match.ranked) {
                    val change = match.ratingChange
                    Text(
                        if (change >= 0) "+$change" else "$change",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (change >= 0) CarromPalette.Jade else CarromPalette.CrimsonLight
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountActionsRow(busy: Boolean, onSignOut: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
        ClassicButton("Sign out", onClick = onSignOut, enabled = !busy, style = ButtonStyle.Outline, modifier = Modifier.weight(1f))
        ClassicButton("Delete account", onClick = { confirmDelete = true }, enabled = !busy, style = ButtonStyle.Crimson, modifier = Modifier.weight(1f), horizontalPadding = 8.dp)
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete account?",
            message = "Your profile, coins, friends and match history will be erased. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false }
        )
    }
}

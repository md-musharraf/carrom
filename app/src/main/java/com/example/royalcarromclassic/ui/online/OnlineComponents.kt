package com.example.royalcarromclassic.ui.online

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.royalcarromclassic.online.ConnectionState
import com.example.royalcarromclassic.online.ProfileDto
import com.example.royalcarromclassic.online.SeatView
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.components.*

/** Quick-chat emotes the server accepts, with what they say. */
val EMOTES = listOf(
    "nice_shot" to "Nice shot!",
    "well_played" to "Well played",
    "good_luck" to "Good luck",
    "thanks" to "Thanks!",
    "wow" to "Wow!",
    "oops" to "Oops!",
    "hurry_up" to "Hurry up…",
    "gg" to "GG"
)

fun emoteText(id: String): String = EMOTES.firstOrNull { it.first == id }?.second ?: id

/** Online modes the server plays, with their labels. */
val ONLINE_MODES = listOf("classic" to "Classic", "freestyle" to "Freestyle")

/** Single-line walnut text field with a brass edge. */
@Composable
fun ClassicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    enabled: Boolean = true,
    leading: Glyph? = null,
    centered: Boolean = false
) {
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(
            color = CarromPalette.Ivory,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start
        ),
        cursorBrush = SolidColor(CarromPalette.GoldLight),
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        modifier = modifier,
        decorationBox = { field ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(shape)
                    .background(CarromPalette.Night.copy(alpha = 0.55f))
                    .border(1.dp, CarromPalette.Gold.copy(alpha = if (enabled) 0.45f else 0.2f), shape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (leading != null) GlyphIcon(leading, size = 18.dp, tint = CarromPalette.Gold)
                Box(Modifier.weight(1f), contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = CarromPalette.Muted, maxLines = 1)
                    }
                    field()
                }
            }
        }
    )
}

/** A sign-in choice: a coloured badge (brand initial or glyph), a label and a brass-edged row. */
@Composable
fun ProviderButton(
    label: String,
    badgeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    letter: String? = null,
    glyph: Glyph? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .classicPanel(accentAlpha = if (enabled) 0.4f else 0.15f, raised = true)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (enabled) badgeColor else CarromPalette.Seam),
            contentAlignment = Alignment.Center
        ) {
            if (letter != null) Text(letter, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
            if (glyph != null) GlyphIcon(glyph, size = 16.dp, tint = Color.White)
        }
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) CarromPalette.Ivory else CarromPalette.Muted,
            modifier = Modifier.weight(1f)
        )
        GlyphIcon(Glyph.ChevronRight, size = 14.dp, tint = CarromPalette.Gold.copy(alpha = if (enabled) 0.8f else 0.3f))
    }
}

/** Crest with the player's initials and, optionally, a presence dot. */
@Composable
fun PlayerCrest(name: String, size: Dp = 38.dp, online: Boolean? = null, accent: Color = CarromPalette.Gold) {
    Box {
        Medallion(size = size, accent = accent) {
            Text(SeatView.monogram(name), style = MaterialTheme.typography.titleSmall, color = CarromPalette.Ink, maxLines = 1)
        }
        if (online != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(if (online) CarromPalette.Jade else CarromPalette.Muted)
                    .border(1.5.dp, CarromPalette.Walnut, CircleShape)
            )
        }
    }
}

/** A player in a list: crest, name, level and rating, then any [trailing] actions. */
@Composable
fun PlayerRow(
    player: ProfileDto,
    modifier: Modifier = Modifier,
    showPresence: Boolean = false,
    subtitle: String = "Level ${player.level} · Rating ${player.rating}",
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier
            .fillMaxWidth()
            .classicPanel(accentAlpha = 0.18f)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PlayerCrest(player.displayName, online = if (showPresence) player.online else null)
        Column(Modifier.weight(1f)) {
            Text(player.displayName, style = MaterialTheme.typography.titleSmall, color = CarromPalette.Ivory, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CarromPalette.Muted, maxLines = 1)
        }
        trailing()
    }
}

/** Small pill showing whether the realtime connection is up. */
@Composable
fun ConnectionBadge(connection: ConnectionState, modifier: Modifier = Modifier) {
    val (label, color) = when (connection) {
        ConnectionState.ONLINE -> "Online" to CarromPalette.Jade
        ConnectionState.CONNECTING -> "Connecting" to CarromPalette.Amber
        ConnectionState.OFFLINE -> "Offline" to CarromPalette.Muted
    }
    val pulse = if (connection == ConnectionState.CONNECTING) {
        rememberInfiniteTransition(label = "connecting").animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dot")
    } else {
        null
    }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(7.dp)
                .graphicsLayer { alpha = pulse?.value ?: 1f }
                .clip(CircleShape)
                .background(color)
        )
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

/** Asks before something that can't be undone. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .classicPanel(accent = if (destructive) CarromPalette.CrimsonLight else CarromPalette.Gold, accentAlpha = 0.7f, raised = true)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = CarromPalette.GoldLight, textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = CarromPalette.Parchment, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ClassicButton("Cancel", onClick = onDismiss, style = ButtonStyle.Outline, modifier = Modifier.weight(1f))
                ClassicButton(
                    confirmLabel,
                    onClick = {
                        onConfirm()
                        onDismiss()
                    },
                    style = if (destructive) ButtonStyle.Crimson else ButtonStyle.Brass,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Placeholder text for an empty list. */
@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = CarromPalette.Muted,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp)
    )
}

/** "12s", "1:05" */
fun formatSeconds(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    return if (s < 60) "${s}s" else "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

package com.example.royalcarromclassic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.royalcarromclassic.data.AIDifficulty
import com.example.royalcarromclassic.data.GameMode
import com.example.royalcarromclassic.data.MatchConfig
import com.example.royalcarromclassic.data.PieceType
import com.example.royalcarromclassic.data.Seat
import com.example.royalcarromclassic.data.SeatSetup
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.components.*

val DIFFICULTIES = listOf(AIDifficulty.EASY to "Rookie", AIDifficulty.MEDIUM to "Pro", AIDifficulty.HARD to "Master")

private const val NAME_LIMIT = 14

/** One-line pitch for each mode that uses the set-up sheet. */
fun modeBlurb(mode: GameMode): String = when (mode) {
    GameMode.PASS_AND_PLAY -> "Two to four players around one phone. Hand it on when your turn ends."
    GameMode.DICE -> "Roll before every shot: each face is a power, from double points to a bonus turn. First to 200."
    GameMode.DISC_POOL -> "Each side owns a colour. Pocket all nine — but cover the queen before your last disc."
    GameMode.CLASSIC -> "White 10, black 5, the covered queen 25. Most points when the board is clear wins."
    GameMode.FREESTYLE -> "Every disc scores. First to 160 points wins."
    else -> ""
}

/**
 * Who's playing: two to four seats, each a person (with a name) or a bot, optionally as doubles
 * where partners sit opposite. Disc Pool is played by two, or four as doubles.
 */
@Composable
fun MatchSetupSheet(
    mode: GameMode,
    initial: MatchConfig?,
    onStart: (MatchConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val counts = if (mode == GameMode.DISC_POOL) listOf(2, 4) else listOf(2, 3, 4)
    val seed = initial?.takeIf { it.mode == mode }
    var count by remember { mutableIntStateOf(seed?.seats?.size?.takeIf { it in counts } ?: 2) }
    val seats = remember {
        mutableStateListOf<SeatSetup>().apply {
            val defaults = List(4) { i ->
                if (i == 0) SeatSetup("", false) else SeatSetup("", isBot = mode != GameMode.PASS_AND_PLAY)
            }
            addAll(defaults.mapIndexed { i, d -> seed?.seats?.getOrNull(i) ?: d })
        }
    }
    var doubles by remember { mutableStateOf(seed?.doubles ?: true) }
    var difficulty by remember { mutableStateOf(seed?.difficulty ?: AIDifficulty.MEDIUM) }
    val anyBot = seats.take(count).any { it.isBot }
    val teams = count == 4 && (doubles || mode == GameMode.DISC_POOL)

    ClassicSheet(title = mode.displayName, subtitle = modeBlurb(mode), onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Label("Players")
            SegmentedSelector(
                options = counts.map { "$it players" },
                selectedIndex = counts.indexOf(count).coerceAtLeast(0),
                onSelect = { count = counts[it] }
            )

            for (i in 0 until count) {
                SeatRow(
                    index = i,
                    seatName = Seat.layoutFor(count)[i].name,
                    seat = seats[i],
                    team = if (teams) i % 2 else -1,
                    colour = if (mode == GameMode.DISC_POOL) (if (i % 2 == 0) PieceType.WHITE else PieceType.BLACK) else null,
                    onChange = { seats[i] = it }
                )
            }

            if (count == 4 && mode != GameMode.DISC_POOL) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .classicPanel(accentAlpha = if (doubles) 0.5f else 0.18f)
                        .clickable(role = Role.Switch) { doubles = !doubles }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlyphIcon(Glyph.Players, size = 18.dp, tint = CarromPalette.Gold)
                    Column(Modifier.weight(1f)) {
                        Text("Doubles", style = MaterialTheme.typography.titleSmall, color = CarromPalette.Ivory)
                        Text("Partners sit opposite and pool their points", style = MaterialTheme.typography.bodySmall, color = CarromPalette.Muted)
                    }
                    Tag(if (doubles) "On" else "Off", color = if (doubles) CarromPalette.Jade else CarromPalette.Muted)
                }
            }

            if (anyBot) {
                Label("Bot skill")
                SegmentedSelector(
                    options = DIFFICULTIES.map { it.second },
                    selectedIndex = DIFFICULTIES.indexOfFirst { it.first == difficulty }.coerceAtLeast(0),
                    onSelect = { difficulty = DIFFICULTIES[it].first }
                )
            }
        }

        ClassicButton(
            text = "Start match",
            leading = Glyph.Bolt,
            onClick = {
                onStart(MatchConfig(mode, seats.take(count).toList(), difficulty, doubles = teams))
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = CarromPalette.Muted)
}

/** One seat: its crest, an editable name, and person/bot. Player 1 is always you. */
@Composable
private fun SeatRow(index: Int, seatName: String, seat: SeatSetup, team: Int, colour: PieceType?, onChange: (SeatSetup) -> Unit) {
    val accent = when {
        team == 0 -> CarromPalette.Gold
        team == 1 -> CarromPalette.Silver
        else -> listOf(CarromPalette.Gold, CarromPalette.Silver, CarromPalette.Jade, CarromPalette.Amber)[index]
    }
    Row(
        Modifier
            .fillMaxWidth()
            .classicPanel(accent = accent, accentAlpha = 0.35f)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Medallion(size = 34.dp, accent = accent) {
            if (seat.isBot) GlyphIcon(Glyph.Crown, size = 16.dp, tint = CarromPalette.Ink)
            else Text("P${index + 1}", style = MaterialTheme.typography.titleSmall, color = CarromPalette.Ink)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "$seatName seat".uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = CarromPalette.Muted
                )
                if (colour != null) MiniDisc(colour, 12.dp)
            }
            if (seat.isBot) {
                Text("Bot", style = MaterialTheme.typography.titleSmall, color = CarromPalette.Parchment)
            } else {
                BasicTextField(
                    value = seat.name,
                    onValueChange = { onChange(seat.copy(name = it.take(NAME_LIMIT))) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleSmall.copy(color = CarromPalette.Ivory),
                    cursorBrush = SolidColor(CarromPalette.Gold),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    decorationBox = { field ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(ChipShape)
                                .background(CarromPalette.Night.copy(alpha = 0.5f))
                                .border(1.dp, CarromPalette.Seam, ChipShape)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            if (seat.name.isEmpty()) {
                                Text(if (index == 0) "Your name" else "Player ${index + 1}", style = MaterialTheme.typography.titleSmall, color = CarromPalette.Muted)
                            }
                            field()
                        }
                    }
                )
            }
        }
        if (index > 0) {
            SegmentedSelector(
                options = listOf("Person", "Bot"),
                selectedIndex = if (seat.isBot) 1 else 0,
                onSelect = { onChange(seat.copy(isBot = it == 1)) },
                modifier = Modifier.width(128.dp)
            )
        }
    }
}


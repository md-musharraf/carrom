package com.example.royalcarromclassic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.royalcarromclassic.data.BoardPower
import com.example.royalcarromclassic.data.CoinPower
import com.example.royalcarromclassic.data.DicePower
import com.example.royalcarromclassic.data.PieceType
import com.example.royalcarromclassic.data.StrikerAbility
import com.example.royalcarromclassic.engine.CarromRules
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.components.*

private sealed interface RuleIcon {
    data class Disc(val type: PieceType) : RuleIcon
    data class Symbol(val glyph: Glyph, val tint: Color) : RuleIcon
}

private data class Rule(val icon: RuleIcon, val title: String, val body: String)

private val SCORING = listOf(
    Rule(RuleIcon.Disc(PieceType.QUEEN), "The Queen · 25", "Pocket the red queen, then cover it by pocketing one of your discs with the same or your very next shot. An uncovered queen returns to the centre."),
    Rule(RuleIcon.Disc(PieceType.WHITE), "White discs · 10", "Each white disc you pocket scores ten points and earns you another shot."),
    Rule(RuleIcon.Disc(PieceType.BLACK), "Black discs · 5", "Each black disc you pocket scores five points and earns you another shot."),
)

private val FOULS = listOf(
    Rule(RuleIcon.Symbol(Glyph.Target, CarromPalette.CrimsonLight), "Pocketing the striker", "Costs ${CarromRules.FOUL_PENALTY} points and ends your turn. A queen pocketed on that shot, or still awaiting cover, goes back to the centre."),
    Rule(RuleIcon.Symbol(Glyph.Lock, CarromPalette.CrimsonLight), "The red base circles", "The striker may not rest on the red circles at either end of your baseline — slide it clear before you shoot."),
)

private val CONTROLS = listOf(
    Rule(RuleIcon.Symbol(Glyph.ChevronRight, CarromPalette.Gold), "Place", "Drag the striker sideways along your baseline, touch the rail, or use the arrows beneath the board."),
    Rule(RuleIcon.Symbol(Glyph.Target, CarromPalette.Gold), "Aim", "Touch anywhere on the board to aim at that point. The dotted guide turns gold when the struck disc is heading into a pocket."),
    Rule(RuleIcon.Symbol(Glyph.Bolt, CarromPalette.Gold), "Shoot", "Pull the striker back in any direction and release — the further you pull, the harder the shot. A pull that lines up with the aim you set keeps it exactly (aim lock). Let go close to the striker to cancel. Or set power below and press Strike."),
)

private val MODES = listOf(
    Rule(RuleIcon.Symbol(Glyph.Players, CarromPalette.Silver), "Party", "Two to four players on one phone; any seat can be a bot. With four you can play doubles: partners sit opposite and pool their points. Play passes to the right."),
    Rule(RuleIcon.Symbol(Glyph.Dice, CarromPalette.GoldLight), "Dice Carrom", "Roll before every shot. 1 Steady · 2 Double Points · 3 Eagle Eye (long guide) · 4 Wide Pockets · 5 Power Surge · 6 Bonus Turn. First to ${CarromRules.DICE_TARGET} points."),
    Rule(RuleIcon.Disc(PieceType.WHITE), "Disc Pool", "The first player owns white, the second black (partners share in doubles). Pocket all nine of yours to win. Pocketing theirs just helps them. The queen must be covered before anyone's last disc, and a striker foul returns a disc."),
    Rule(RuleIcon.Symbol(Glyph.Timer, CarromPalette.GoldLight), "Time Attack", "90 seconds to score as much as you can: white 10, black 5, queen 50. A striker foul costs five seconds; clearing the board banks a time bonus."),
    Rule(RuleIcon.Symbol(Glyph.Globe, CarromPalette.Jade), "Online", "Each turn has a shot clock, shown as a ring around the player's crest; three timeouts in a row forfeit the match. Ranked arenas escrow the entry fee and the winner takes the pot. Powers are off online."),
    Rule(RuleIcon.Symbol(Glyph.Bolt, CarromPalette.Amber), "Blitz", "Race the bot to ${CarromRules.BLITZ_TARGET} points. You have ten seconds a shot — let the clock run out and the turn passes."),
    Rule(RuleIcon.Symbol(Glyph.Target, CarromPalette.GoldLight), "Lucky Shot", "Three free shots a day: knock the lucky disc into the rings. The closer it stops to the centre, the bigger the prize."),
)

private val POWERS = listOf(
    Rule(RuleIcon.Symbol(Glyph.Target, CarromPalette.Gold), "Strikers", "Your own: " + StrikerAbility.entries.joinToString(" · ") { "${it.title} — ${it.summary.lowercase().trimEnd('.')}" } + ". Bots use the standard striker."),
    Rule(RuleIcon.Symbol(Glyph.Coins, CarromPalette.Gold), "Coin sets", "The whole table's discs: " + CoinPower.entries.joinToString(" · ") { "${it.title} — ${it.summary.lowercase().trimEnd('.')}" } + "."),
    Rule(RuleIcon.Symbol(Glyph.Board, CarromPalette.Gold), "Boards", "How the surface plays for everyone: " + BoardPower.entries.joinToString(" · ") { "${it.title} — ${it.summary.lowercase().trimEnd('.')}" } + "."),
    Rule(RuleIcon.Symbol(Glyph.Dice, CarromPalette.Gold), "Dice", "In Dice Carrom: " + DicePower.entries.joinToString(" · ") { "${it.title} — ${it.summary.lowercase().trimEnd('.')}" } + ". Bots roll a fair die."),
    Rule(RuleIcon.Symbol(Glyph.Lock, CarromPalette.Muted), "When powers apply", "Offline matches, from the next match after you change your loadout. Trick shots, Lucky Shot and online play always use the standard table. Switch powers off in the Locker to play pure."),
)

@Composable
fun RulesSheet(onDismiss: () -> Unit) {
    ClassicSheet(title = "Rules of Play", subtitle = "Scoring, controls, every mode and every power", onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RuleSection("Scoring", SCORING)
            RuleSection("Fouls", FOULS)
            RuleSection("Controls", CONTROLS)
            RuleSection("Modes", MODES)
            RuleSection("Powers", POWERS)
        }
    }
}

@Composable
private fun RuleSection(title: String, rules: List<Rule>) {
    OrnamentHeading(title)
    rules.forEach { rule ->
        Row(
            Modifier
                .fillMaxWidth()
                .classicPanel(accentAlpha = 0.16f)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                when (val icon = rule.icon) {
                    is RuleIcon.Disc -> MiniDisc(icon.type, 26.dp)
                    is RuleIcon.Symbol -> GlyphIcon(icon.glyph, size = 20.dp, tint = icon.tint)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(rule.title, style = MaterialTheme.typography.titleSmall, color = CarromPalette.GoldLight)
                Text(rule.body, style = MaterialTheme.typography.bodySmall, color = CarromPalette.Parchment)
            }
        }
    }
}

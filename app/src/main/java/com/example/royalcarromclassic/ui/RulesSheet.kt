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
import com.example.royalcarromclassic.data.PieceType
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
    Rule(RuleIcon.Symbol(Glyph.ChevronRight, CarromPalette.Gold), "Place", "Drag the striker along your baseline, or use the rail and arrows beneath the board."),
    Rule(RuleIcon.Symbol(Glyph.Target, CarromPalette.Gold), "Aim", "Touch anywhere on the board to aim at that point. The dotted guide turns gold when the struck disc is heading into a pocket."),
    Rule(RuleIcon.Symbol(Glyph.Bolt, CarromPalette.Gold), "Shoot", "Pull the striker back like a slingshot and release — the further you pull, the harder the shot. Or set power below and press Strike."),
)

@Composable
fun RulesSheet(onDismiss: () -> Unit) {
    ClassicSheet(title = "Rules of Play", subtitle = "The essentials of classic carrom", onDismiss = onDismiss) {
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

package com.example.royalcarromclassic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("📖 Official Carrom Rules", color = Color(0xFFFDE68A), fontSize = 20.sp, fontWeight = FontWeight.Black)

            val rules = listOf(
                Pair("👑 The Red Queen (25 pts)", "The Queen must be potted and covered by potting another regular piece in the same or immediately next shot. If not covered, the Queen is returned to the center circle."),
                Pair("⚪ White Pieces (10 pts)", "Valued at 10 points each. In Disc Pool mode, one player is assigned Whites and must pocket all whites to win."),
                Pair("⚫ Black Pieces (5 pts)", "Valued at 5 points each. In Disc Pool mode, one player is assigned Blacks and must pocket all blacks to win."),
                Pair("⚠️ Baseline Foul Rules", "The striker MUST NOT touch either of the two red foul circles at the ends of your baseline rail. Touching them causes a foul penalty."),
                Pair("⛔ Striker Pocket Penalty", "If your striker drops into any corner pocket, you incur a foul penalty (-5 pts) and your turn ends."),
                Pair("🎯 Aiming & Direct Cuts", "Drag the striker to position it on your baseline, drag across the board to aim and adjust power, and tap STRIKE to fire.")
            )

            rules.forEach { (title, desc) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF090D16))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(title, color = Color(0xFFFBBF24), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(desc, color = Color(0xFFCBD5E1), fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}

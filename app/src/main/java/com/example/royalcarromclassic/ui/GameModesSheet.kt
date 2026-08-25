package com.example.royalcarromclassic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.royalcarromclassic.data.AIDifficulty
import com.example.royalcarromclassic.data.GameMode

data class GameModeItem(
    val mode: GameMode,
    val title: String,
    val description: String,
    val icon: String,
    val gradient: List<Color>,
    val badge: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameModesSheet(
    isOpen: Boolean,
    currentMode: GameMode,
    aiDifficulty: AIDifficulty,
    onSelectMode: (GameMode, AIDifficulty) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val modeItems = listOf(
        GameModeItem(
            mode = GameMode.VS_AI,
            title = "Play vs Smart AI",
            description = "Challenge smart bot with realistic aiming, cuts, and bank shots.",
            icon = "🤖",
            gradient = listOf(Color(0xFF312E81), Color(0xFF1E1B4B)),
            badge = "Popular"
        ),
        GameModeItem(
            mode = GameMode.CLASSIC,
            title = "Classic Carrom",
            description = "Standard tournament points (White 10, Black 5, Queen 25 with Cover).",
            icon = "🏆",
            gradient = listOf(Color(0xFF78350F), Color(0xFF451A03)),
            badge = "Tournament"
        ),
        GameModeItem(
            mode = GameMode.DISC_POOL,
            title = "Carrom Disc Pool",
            description = "Fast-paced pool rules. Pocket all your assigned color pieces first to win!",
            icon = "⚡",
            gradient = listOf(Color(0xFF064E3B), Color(0xFF022C22)),
            badge = "Fast Action"
        ),
        GameModeItem(
            mode = GameMode.FREESTYLE,
            title = "Freestyle Points Race",
            description = "Every pocketed disc scores points. First player to reach 160 points wins.",
            icon = "✨",
            gradient = listOf(Color(0xFF831843), Color(0xFF500724)),
            badge = "Arcade"
        ),
        GameModeItem(
            mode = GameMode.PASS_AND_PLAY,
            title = "Pass & Play (2 Player)",
            description = "Compete with a friend locally on the same mobile device.",
            icon = "👥",
            gradient = listOf(Color(0xFF0E7490), Color(0xFF164E63)),
            badge = "Local 2P"
        ),
        GameModeItem(
            mode = GameMode.PRACTICE,
            title = "Practice & Sandbox",
            description = "Unlimited free strikes to practice bank shots, angles, and power.",
            icon = "🎯",
            gradient = listOf(Color(0xFF334155), Color(0xFF1E293B)),
            badge = "Training"
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Select Game Mode",
                color = Color(0xFFFDE68A),
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )

            // AI Difficulty Selector
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF090D16))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("🤖 Bot AI Difficulty:", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Pair(AIDifficulty.EASY, "🌱 Rookie"),
                        Pair(AIDifficulty.MEDIUM, "⚡ Pro"),
                        Pair(AIDifficulty.HARD, "👑 Master")
                    ).forEach { (diff, label) ->
                        val isSelected = aiDifficulty == diff
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) Color(0xFFF59E0B) else Color(0xFF1E293B)
                                )
                                .clickable { onSelectMode(currentMode, diff) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            // Modes List
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(modeItems) { item ->
                    val isSelected = currentMode == item.mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brush.horizontalGradient(item.gradient))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color(0xFFFBBF24) else Color(0x33FFFFFF),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable {
                                onSelectMode(item.mode, aiDifficulty)
                                onDismiss()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(item.icon, fontSize = 28.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0x33FBBF24))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(item.badge, color = Color(0xFFFDE68A), fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                            }
                            Text(item.description, color = Color(0xFFCBD5E1), fontSize = 11.sp, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

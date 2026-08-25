package com.example.royalcarromclassic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.royalcarromclassic.data.PlayerStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    isOpen: Boolean,
    soundEnabled: Boolean,
    hapticEnabled: Boolean,
    stats: PlayerStats,
    onToggleSound: () -> Unit,
    onToggleHaptics: () -> Unit,
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
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("⚙️ Game Settings", color = Color(0xFFFDE68A), fontSize = 20.sp, fontWeight = FontWeight.Black)

            // Audio & Haptics Toggles
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF090D16))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Sound Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔊", fontSize = 18.sp)
                        Column {
                            Text("Sound Effects", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Acoustic wood clacks and fanfares", color = Color(0xFF94A3B8), fontSize = 10.sp)
                        }
                    }
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = { onToggleSound() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFFBBF24),
                            checkedTrackColor = Color(0xFFB45309)
                        )
                    )
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                // Haptics Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📳", fontSize = 18.sp)
                        Column {
                            Text("Vibration Haptics", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Tactile strike and pocket rumbles", color = Color(0xFF94A3B8), fontSize = 10.sp)
                        }
                    }
                    Switch(
                        checked = hapticEnabled,
                        onCheckedChange = { onToggleHaptics() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFFBBF24),
                            checkedTrackColor = Color(0xFFB45309)
                        )
                    )
                }
            }

            // Player Career Stats
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF090D16))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("📊 Career Record", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Matches Played:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    Text("${stats.matchesPlayed}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Matches Won:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    Text("${stats.matchesWon}", color = Color(0xFF34D399), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Win Rate:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    val winRate = if (stats.matchesPlayed > 0) ((stats.matchesWon.toFloat() / stats.matchesPlayed) * 100).toInt() else 0
                    Text("$winRate%", color = Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

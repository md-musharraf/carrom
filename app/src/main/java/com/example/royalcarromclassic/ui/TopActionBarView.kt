package com.example.royalcarromclassic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.royalcarromclassic.data.PlayerStats

@Composable
fun TopActionBarView(
    stats: PlayerStats,
    soundEnabled: Boolean,
    onOpenModes: () -> Unit,
    onOpenTrickShots: () -> Unit,
    onOpenWheel: () -> Unit,
    onOpenShop: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xE60F172A))
            .border(1.dp, Color(0x33F59E0B), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Level & Coins
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Level
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFB45309))
                    .clickable { onOpenModes() }
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text("LVL ${stats.level}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }

            // Coins
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF090D16))
                    .border(1.dp, Color(0x33F59E0B), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFBBF24)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$", color = Color(0xFF090D16), fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
                Text("${stats.coins}", color = Color(0xFFFDE68A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Action Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                Pair("🎮", onOpenModes),
                Pair("🎯", onOpenTrickShots),
                Pair("🎁", onOpenWheel),
                Pair("🛍️", onOpenShop),
                Pair("❓", onOpenRules),
                Pair("⚙️", onOpenSettings)
            ).forEach { (icon, action) ->
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(9.dp))
                        .clickable { action() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(icon, fontSize = 13.sp)
                }
            }
        }
    }
}

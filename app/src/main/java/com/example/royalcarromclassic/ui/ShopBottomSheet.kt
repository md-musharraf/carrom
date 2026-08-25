package com.example.royalcarromclassic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.royalcarromclassic.data.BoardTheme
import com.example.royalcarromclassic.data.PlayerStats
import com.example.royalcarromclassic.data.StrikerConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopBottomSheet(
    isOpen: Boolean,
    stats: PlayerStats,
    strikers: List<StrikerConfig>,
    boards: List<BoardTheme>,
    selectedStrikerId: String,
    selectedBoardId: String,
    onSelectStriker: (String) -> Unit,
    onSelectBoard: (String) -> Unit,
    onBuyStriker: (StrikerConfig) -> Unit,
    onBuyBoard: (BoardTheme) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    var activeTab by remember { mutableStateOf("strikers") }

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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Royal Armory",
                    color = Color(0xFFFDE68A),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )

                // Coin Badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF090D16))
                        .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("$", color = Color(0xFFFBBF24), fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text("${stats.coins}", color = Color(0xFFFDE68A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF090D16))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    Pair("strikers", "👑 Strikers (${strikers.size})"),
                    Pair("boards", "🪵 Boards (${boards.size})")
                ).forEach { (tabKey, tabLabel) ->
                    val isSelected = activeTab == tabKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (isSelected) Color(0xFFF59E0B) else Color.Transparent)
                            .clickable { activeTab = tabKey },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabLabel,
                            color = if (isSelected) Color(0xFF0F172A) else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // List
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (activeTab == "strikers") {
                    items(strikers) { s ->
                        val isEquipped = selectedStrikerId == s.id
                        val canAfford = stats.coins >= s.price

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isEquipped) Color(0xFF2C1608) else Color(0xFF090D16))
                                .border(
                                    1.dp,
                                    if (isEquipped) Color(0xFFFBBF24) else Color(0xFF1E293B),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Striker Icon
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(s.primaryColor)
                                    .border(2.dp, s.glowColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(s.glowColor)
                                )
                            }

                            // Info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(s.description, color = Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⚡ Power: +${((s.powerMultiplier - 1f) * 100).toInt()}%", color = Color(0xFFFBBF24), fontSize = 9.sp)
                                    Text("🎯 Aim: +${((s.aimGuideLength - 1f) * 100).toInt()}%", color = Color(0xFF38BDF8), fontSize = 9.sp)
                                }
                            }

                            // Action Button
                            if (s.isUnlocked) {
                                Button(
                                    onClick = { onSelectStriker(s.id) },
                                    enabled = !isEquipped,
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF59E0B),
                                        contentColor = Color(0xFF0F172A),
                                        disabledContainerColor = Color(0xFF1E293B),
                                        disabledContentColor = Color(0xFF64748B)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Text(if (isEquipped) "Equipped" else "Equip", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = { onBuyStriker(s) },
                                    enabled = canAfford,
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF10B981),
                                        contentColor = Color(0xFF0F172A)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("Buy $${s.price}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    items(boards) { b ->
                        val isEquipped = selectedBoardId == b.id
                        val canAfford = stats.coins >= b.price

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isEquipped) Color(0xFF2C1608) else Color(0xFF090D16))
                                .border(
                                    1.dp,
                                    if (isEquipped) Color(0xFFFBBF24) else Color(0xFF1E293B),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Board Swatch
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(b.feltColor)
                                    .border(2.dp, b.woodColor, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(b.centerCircleColor))
                            }

                            // Info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(b.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(b.description, color = Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1)
                            }

                            // Action Button
                            if (b.isUnlocked) {
                                Button(
                                    onClick = { onSelectBoard(b.id) },
                                    enabled = !isEquipped,
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF59E0B),
                                        contentColor = Color(0xFF0F172A),
                                        disabledContainerColor = Color(0xFF1E293B),
                                        disabledContentColor = Color(0xFF64748B)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Text(if (isEquipped) "Equipped" else "Equip", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = { onBuyBoard(b) },
                                    enabled = canAfford,
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF10B981),
                                        contentColor = Color(0xFF0F172A)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("Buy $${b.price}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.royalcarromclassic.data.TrickShotLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrickShotsSheet(
    isOpen: Boolean,
    levels: List<TrickShotLevel>,
    onSelectLevel: (Int) -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🎯 Trick Shot Challenges",
                color = Color(0xFFFDE68A),
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(levels) { lvl ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (lvl.isUnlocked) Color(0xFF090D16) else Color(0x60090D16))
                            .border(
                                1.dp,
                                if (lvl.isUnlocked) Color(0xFF38BDF8) else Color(0xFF1E293B),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable(enabled = lvl.isUnlocked) {
                                onSelectLevel(lvl.id)
                                onDismiss()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Level Number Circle
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (lvl.isUnlocked) Color(0xFF0284C7) else Color(0xFF334155)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (lvl.isUnlocked) "#${lvl.id}" else "🔒",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        // Details
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(lvl.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Row {
                                    for (i in 1..3) {
                                        Text(
                                            text = if (i <= lvl.stars) "⭐" else "☆",
                                            fontSize = 11.sp,
                                            color = Color(0xFFFBBF24)
                                        )
                                    }
                                }
                            }
                            Text(lvl.description, color = Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

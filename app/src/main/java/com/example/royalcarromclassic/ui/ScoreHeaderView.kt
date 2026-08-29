package com.example.royalcarromclassic.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.royalcarromclassic.data.GameState

@Composable
fun ScoreHeaderView(
    gameState: GameState,
    remainingWhites: Int,
    remainingBlacks: Int,
    isQueenOnBoard: Boolean,
    modifier: Modifier = Modifier
) {
    val player1 = gameState.player1
    val player2 = gameState.player2
    val currentTurn = gameState.currentTurn
    val mode = gameState.mode
    val queenNeedsCover = gameState.queenNeedsCover
    val queenCovered = gameState.queenCovered

    // Smooth score counter animations
    val p1ScoreAnim by animateIntAsState(
        targetValue = player1.score,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "P1ScoreAnim"
    )
    val p2ScoreAnim by animateIntAsState(
        targetValue = player2.score,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "P2ScoreAnim"
    )

    val isP1Turn = currentTurn == "player1"
    val isP2Turn = currentTurn == "player2" || currentTurn == "ai"

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Player Score Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Player 1
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isP1Turn) Color(0xFF2C1608) else Color(0xFF0F172A))
                    .border(
                        width = if (isP1Turn) 2.dp else 1.dp,
                        color = if (isP1Turn) Color(0xFFFBBF24) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFFF59E0B), Color(0xFFB45309)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(player1.avatar, fontSize = 17.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(player1.name, color = Color(0xFFF8FAFC), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (isP1Turn) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFF59E0B))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text("TURN", color = Color(0xFF0F172A), fontSize = 8.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("$p1ScoreAnim", color = Color(0xFFFBBF24), fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text("pts", color = Color(0xFFFDE68A), fontSize = 9.sp)
                    }
                }
            }

            // Player 2 / AI
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isP2Turn) Color(0xFF1E1B4B) else Color(0xFF0F172A))
                    .border(
                        width = if (isP2Turn) 2.dp else 1.dp,
                        color = if (isP2Turn) Color(0xFF818CF8) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isP2Turn) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF818CF8))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text("TURN", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Text(player2.name, color = Color(0xFFF8FAFC), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("$p2ScoreAnim", color = Color(0xFF818CF8), fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text("pts", color = Color(0xFFA5B4FC), fontSize = 9.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFF6366F1), Color(0xFF3730A3)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(player2.avatar, fontSize = 17.sp)
                }
            }
        }

        // Center Stats Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xE6090D16))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pieces Remaining
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.White).border(1.dp, Color(0xFF94A3B8), CircleShape))
                    Text("$remainingWhites", color = Color(0xFFF8FAFC), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF1E293B)).border(1.dp, Color(0xFF475569), CircleShape))
                    Text("$remainingBlacks", color = Color(0xFFF8FAFC), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Queen Status
            Text(
                text = when {
                    isQueenOnBoard -> "👑 Queen (25 pts)"
                    queenCovered -> "✨ Queen Covered!"
                    queenNeedsCover -> "⚠️ Cover Needed!"
                    else -> "Queen Claimed"
                },
                color = when {
                    queenCovered -> Color(0xFF34D399)
                    queenNeedsCover -> Color(0xFFFBBF24)
                    else -> Color(0xFFF87171)
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )

            // Mode Name
            Text(
                text = mode.name.replace('_', ' '),
                color = Color(0xFFF59E0B),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}


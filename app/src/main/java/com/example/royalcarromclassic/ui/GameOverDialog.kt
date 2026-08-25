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
import androidx.compose.ui.window.Dialog
import com.example.royalcarromclassic.data.GameState

@Composable
fun GameOverDialog(
    gameState: GameState,
    onRematch: () -> Unit,
    onChangeMode: () -> Unit
) {
    if (!gameState.isGameOver) return

    val isPlayer1Win = gameState.winner == "player1"

    Dialog(onDismissRequest = {}) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF0F172A))
                .border(2.dp, if (isPlayer1Win) Color(0xFFFBBF24) else Color(0xFFEF4444), RoundedCornerShape(28.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Trophy / Result Icon
            Text(
                text = if (isPlayer1Win) "🏆" else "💥",
                fontSize = 52.sp
            )

            // Result Title
            Text(
                text = if (isPlayer1Win) "VICTORY!" else "MATCH COMPLETE",
                color = if (isPlayer1Win) Color(0xFFFBBF24) else Color(0xFFF87171),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )

            Text(
                text = if (isPlayer1Win) "Outstanding match! You mastered the carrom board." else "Good game! Better luck in the next round.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            // Rewards Breakdown
            if (isPlayer1Win) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF090D16))
                        .border(1.dp, Color(0x33FBBF24), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+500", color = Color(0xFFFDE68A), fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Text("Coins Earned", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    }
                    HorizontalDivider(
                        modifier = Modifier
                            .height(28.dp)
                            .width(1.dp),
                        color = Color(0xFF1E293B)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+100", color = Color(0xFF38BDF8), fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Text("Player XP", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    }
                }
            }

            // Final Scores
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF090D16))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${gameState.player1.name}: ${gameState.player1.score} pts", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${gameState.player2.name}: ${gameState.player2.score} pts", color = Color(0xFF818CF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRematch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF59E0B),
                        contentColor = Color(0xFF0F172A)
                    )
                ) {
                    Text("PLAY AGAIN", fontSize = 13.sp, fontWeight = FontWeight.Black)
                }

                OutlinedButton(
                    onClick = onChangeMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFF8FAFC)
                    )
                ) {
                    Text("CHANGE MODE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

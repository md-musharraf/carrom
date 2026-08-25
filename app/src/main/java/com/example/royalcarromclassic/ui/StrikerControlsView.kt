package com.example.royalcarromclassic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.royalcarromclassic.data.GameState
import com.example.royalcarromclassic.data.GameMode
import com.example.royalcarromclassic.data.StrikerConfig
import com.example.royalcarromclassic.data.TurnState
import com.example.royalcarromclassic.engine.BoardGeometry
import kotlin.math.roundToInt

@Composable
fun StrikerControlsView(
    gameState: GameState,
    strikerConfig: StrikerConfig,
    onPositionChanged: (Float) -> Unit,
    onAimAngleChanged: (Float) -> Unit,
    onPowerChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val isBottom = gameState.currentTurn == "player1" || gameState.mode == GameMode.TRICK_SHOTS
    val isAIMoving = gameState.currentTurn == "ai"
    val isMoving = gameState.turnState == TurnState.MOVING || gameState.turnState == TurnState.TURN_EVALUATING

    val strikerPos = BoardGeometry.getBaselineStrikerPos(gameState.strikerBaselineOffset, isBottom)
    val isFoulPosition = BoardGeometry.isOverBaselineCircle(strikerPos.x, strikerPos.y, isBottom)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xE6090D16))
            .border(1.dp, Color(0x33F59E0B), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Sleek Baseline Position Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { onPositionChanged((gameState.strikerBaselineOffset - 0.04f).coerceAtLeast(0f)) },
                enabled = !isAIMoving && !isMoving && gameState.strikerBaselineOffset > 0f,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B))
            ) {
                Text("◀", color = Color(0xFFFDE68A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Slider(
                value = gameState.strikerBaselineOffset,
                onValueChange = onPositionChanged,
                valueRange = 0f..1f,
                enabled = !isAIMoving && !isMoving,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = if (isFoulPosition) Color(0xFFEF4444) else Color(0xFFFBBF24),
                    activeTrackColor = if (isFoulPosition) Color(0xFFEF4444) else Color(0xFFF59E0B),
                    inactiveTrackColor = Color(0xFF334155)
                )
            )

            IconButton(
                onClick = { onPositionChanged((gameState.strikerBaselineOffset + 0.04f).coerceAtMost(1f)) },
                enabled = !isAIMoving && !isMoving && gameState.strikerBaselineOffset < 1f,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B))
            ) {
                Text("▶", color = Color(0xFFFDE68A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 2. Interactive Gesture Status & Power Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF131B2E))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Message
            Text(
                text = when {
                    isAIMoving -> "🤖 Bot Master is Aiming..."
                    isMoving -> "⚡ Pieces in motion..."
                    isFoulPosition -> "⚠️ Striker touching circle (Reposition)"
                    gameState.turnState == TurnState.AIMING -> "🎯 Aiming..."
                    else -> "👆 Pull striker & release to shoot"
                },
                color = when {
                    isFoulPosition -> Color(0xFFEF4444)
                    isAIMoving -> Color(0xFF818CF8)
                    gameState.turnState == TurnState.AIMING -> Color(0xFFFBBF24)
                    else -> Color(0xFF34D399)
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            // Live Power & Angle Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val deg = ((gameState.strikerAimAngle * 180f) / Math.PI.toFloat()).roundToInt()
                Text(
                    text = "$deg°",
                    color = Color(0xFF7DD3FC),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "⚡ ${gameState.strikerPower.roundToInt()}%",
                    color = if (gameState.strikerPower > 80f) Color(0xFFEF4444) else Color(0xFFFBBF24),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

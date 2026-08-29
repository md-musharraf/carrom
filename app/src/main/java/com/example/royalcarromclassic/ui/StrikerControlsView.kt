package com.example.royalcarromclassic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
    onNudgeAngle: (Float) -> Unit = {},
    onNudgePower: (Float) -> Unit = {},
    onShoot: () -> Unit = {},
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
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // 1. Sleek Baseline Position Slider with Micro Stepper buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = { onPositionChanged((gameState.strikerBaselineOffset - 0.03f).coerceAtLeast(0.06f)) },
                enabled = !isAIMoving && !isMoving && gameState.strikerBaselineOffset > 0.06f,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B))
            ) {
                Text("◀", color = Color(0xFFFDE68A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Slider(
                value = gameState.strikerBaselineOffset,
                onValueChange = onPositionChanged,
                valueRange = 0.06f..0.94f,
                enabled = !isAIMoving && !isMoving,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = if (isFoulPosition) Color(0xFFEF4444) else Color(0xFFFBBF24),
                    activeTrackColor = if (isFoulPosition) Color(0xFFEF4444) else Color(0xFFF59E0B),
                    inactiveTrackColor = Color(0xFF334155)
                )
            )

            IconButton(
                onClick = { onPositionChanged((gameState.strikerBaselineOffset + 0.03f).coerceAtMost(0.94f)) },
                enabled = !isAIMoving && !isMoving && gameState.strikerBaselineOffset < 0.94f,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B))
            ) {
                Text("▶", color = Color(0xFFFDE68A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 2. Interactive Status Bar & Fine Tuning Steppers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF131B2E))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Message
            Text(
                text = when {
                    isAIMoving -> "🤖 Bot Master Aiming..."
                    isMoving -> "⚡ In Motion..."
                    isFoulPosition -> "⚠️ Circle Foul! Nudge slider"
                    gameState.turnState == TurnState.AIMING -> "🎯 Aim Set"
                    else -> "👆 Pull & Release or Tap to Shoot"
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

            // Fine Angle & Power Micro-Adjust Steppers
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Angle Stepper
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val deg = ((gameState.strikerAimAngle * 180f) / Math.PI.toFloat()).roundToInt()
                    Text(
                        text = "⟲",
                        color = Color(0xFF7DD3FC),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable(enabled = !isAIMoving && !isMoving) { onNudgeAngle(-1.5f) }
                    )
                    Text(
                        text = "$deg°",
                        color = Color(0xFF7DD3FC),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "⟳",
                        color = Color(0xFF7DD3FC),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable(enabled = !isAIMoving && !isMoving) { onNudgeAngle(1.5f) }
                    )
                }

                // Power Stepper
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "-",
                        color = Color(0xFFFBBF24),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable(enabled = !isAIMoving && !isMoving) { onNudgePower(-5f) }
                    )
                    Text(
                        text = "⚡${gameState.strikerPower.roundToInt()}%",
                        color = if (gameState.strikerPower > 80f) Color(0xFFEF4444) else Color(0xFFFBBF24),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "+",
                        color = Color(0xFFFBBF24),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable(enabled = !isAIMoving && !isMoving) { onNudgePower(5f) }
                    )
                }

                // Quick Strike Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (!isAIMoving && !isMoving && !isFoulPosition)
                                Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706)))
                            else
                                Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))
                        )
                        .clickable(enabled = !isAIMoving && !isMoving && !isFoulPosition) { onShoot() }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "STRIKE",
                        color = if (!isAIMoving && !isMoving && !isFoulPosition) Color(0xFF0F172A) else Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}


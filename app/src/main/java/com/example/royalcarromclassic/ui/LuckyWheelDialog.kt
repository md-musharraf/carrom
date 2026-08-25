package com.example.royalcarromclassic.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun LuckyWheelDialog(
    isOpen: Boolean,
    onAwardCoins: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val scope = rememberCoroutineScope()
    var isSpinning by remember { mutableStateOf(false) }
    var currentRotation by remember { mutableStateOf(0f) }
    var wonPrize by remember { mutableStateOf<String?>(null) }

    val rotationAnim = remember { Animatable(0f) }

    val prizes = listOf(
        Pair(100, Color(0xFFEF4444)),
        Pair(250, Color(0xFFF59E0B)),
        Pair(500, Color(0xFF10B981)),
        Pair(1000, Color(0xFF6366F1)),
        Pair(150, Color(0xFFEC4899)),
        Pair(300, Color(0xFF06B6D4)),
        Pair(750, Color(0xFF8B5CF6)),
        Pair(2000, Color(0xFFEAB308))
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF0F172A))
                .border(2.dp, Color(0xFFF59E0B), RoundedCornerShape(28.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("🎁 Daily Lucky Spin", color = Color(0xFFFDE68A), fontSize = 20.sp, fontWeight = FontWeight.Black)

            // Animated Wheel
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(rotationAnim.value)
                ) {
                    val radius = size.width / 2f
                    val center = Offset(radius, radius)
                    val sweep = 360f / prizes.size

                    prizes.forEachIndexed { index, (_, color) ->
                        drawArc(
                            color = color,
                            startAngle = index * sweep,
                            sweepAngle = sweep,
                            useCenter = true,
                            size = Size(size.width, size.height)
                        )
                    }

                    // Outer Rim
                    drawCircle(color = Color(0xFFFBBF24), radius = radius, style = Stroke(width = 6f))
                }

                // Center Hub
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                        .border(3.dp, Color(0xFFFBBF24), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("SPIN", color = Color(0xFFFDE68A), fontSize = 10.sp, fontWeight = FontWeight.Black)
                }

                // Top Pointer Arrow
                Text("🔻", fontSize = 24.sp, modifier = Modifier.align(Alignment.TopCenter).offset(y = (-14).dp))
            }

            if (wonPrize != null) {
                Text(
                    text = "🎉 You Won $wonPrize!",
                    color = Color(0xFF34D399),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
            }

            // Spin Button
            Button(
                onClick = {
                    if (isSpinning) return@Button
                    isSpinning = true
                    wonPrize = null
                    scope.launch {
                        val winningIndex = Random.nextInt(prizes.size)
                        val fullSpins = 5 + Random.nextInt(4)
                        val sweep = 360f / prizes.size
                        val targetRot = currentRotation + (fullSpins * 360f) + (winningIndex * sweep) + (sweep / 2f)

                        rotationAnim.animateTo(
                            targetValue = targetRot,
                            animationSpec = tween(
                                durationMillis = 3500,
                                easing = FastOutSlowInEasing
                            )
                        )

                        currentRotation = targetRot % 360f
                        val prizeAmount = prizes[prizes.size - 1 - (winningIndex % prizes.size)].first
                        wonPrize = "$$prizeAmount Coins"
                        onAwardCoins(prizeAmount)
                        isSpinning = false
                    }
                },
                enabled = !isSpinning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF59E0B),
                    contentColor = Color(0xFF0F172A),
                    disabledContainerColor = Color(0xFF334155),
                    disabledContentColor = Color(0xFF94A3B8)
                )
            ) {
                Text(if (isSpinning) "Spinning..." else "SPIN FOR FREE", fontSize = 14.sp, fontWeight = FontWeight.Black)
            }

            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
        }
    }
}

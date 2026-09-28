package com.example.royalcarromclassic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.components.*
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private val PRIZES = listOf(100, 250, 500, 1000, 150, 300, 750, 2000)

/**
 * Wheel geometry. Segment i spans [i·sweep, (i+1)·sweep) degrees measured clockwise from
 * 12 o'clock; the wheel turns clockwise by `rotation` degrees and the pointer sits at 12 o'clock.
 */
internal object WheelMath {
    fun sweep(count: Int): Float = 360f / count

    /** Index of the segment under the pointer when the wheel has turned [rotation] degrees. */
    fun segmentAt(rotation: Float, count: Int): Int {
        val underPointer = ((-rotation % 360f) + 360f) % 360f
        return floor(underPointer / sweep(count)).toInt().coerceIn(0, count - 1)
    }

    /**
     * Rotation that spins at least [fullSpins] turns past [current] and stops with segment [index]
     * under the pointer, offset within it by [jitter] (−0.5..0.5 of a segment).
     */
    fun targetRotation(current: Float, index: Int, count: Int, fullSpins: Int, jitter: Float = 0f): Float {
        val landing = (index + 0.5f + jitter.coerceIn(-0.4f, 0.4f)) * sweep(count)
        val desired = ((-landing % 360f) + 360f) % 360f
        val delta = ((desired - current % 360f) % 360f + 360f) % 360f
        return current + fullSpins * 360f + delta
    }
}

@Composable
fun LuckyWheelDialog(
    onAwardCoins: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }
    var spinning by remember { mutableStateOf(false) }
    var prize by remember { mutableStateOf<Int?>(null) }

    Dialog(onDismissRequest = { if (!spinning) onDismiss() }) {
        Column(
            Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .classicPanel(accentAlpha = 0.8f, raised = true)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Daily Lucky Spin", style = MaterialTheme.typography.headlineMedium, color = CarromPalette.GoldLight)
            Text("Turn the royal wheel for bonus coins", style = MaterialTheme.typography.bodySmall, color = CarromPalette.Muted)

            Wheel(rotation = { rotation.value }, spinning = spinning, modifier = Modifier.size(264.dp))

            AnimatedVisibility(
                visible = prize != null,
                enter = fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.6f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlyphIcon(Glyph.Coin, size = 22.dp)
                    Text("You won ${prize ?: 0} coins!", style = MaterialTheme.typography.titleLarge, color = CarromPalette.GoldLight)
                }
            }

            ClassicButton(
                text = when {
                    spinning -> "Spinning…"
                    prize != null -> "Spin again"
                    else -> "Spin the wheel"
                },
                enabled = !spinning,
                onClick = {
                    spinning = true
                    prize = null
                    scope.launch {
                        val index = Random.nextInt(PRIZES.size)
                        val target = WheelMath.targetRotation(
                            current = rotation.value,
                            index = index,
                            count = PRIZES.size,
                            fullSpins = 5 + Random.nextInt(3),
                            jitter = Random.nextFloat() - 0.5f
                        )
                        rotation.animateTo(target, tween(4_200, easing = CubicBezierEasing(0.12f, 0.75f, 0.18f, 1f)))
                        val won = PRIZES[WheelMath.segmentAt(rotation.value, PRIZES.size)]
                        prize = won
                        onAwardCoins(won)
                        spinning = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            ClassicButton("Close", onClick = onDismiss, enabled = !spinning, style = ButtonStyle.Outline, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
/** [rotation] is read only in layer blocks, so spinning never recomposes the wheel. */
private fun Wheel(rotation: () -> Float, spinning: Boolean, modifier: Modifier = Modifier) {
    val count = PRIZES.size
    val sweep = WheelMath.sweep(count)

    Box(modifier, contentAlignment = Alignment.Center) {
        // Rim with brass studs (static).
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f
            drawCircle(Brush.radialGradient(listOf(CarromPalette.MahoganyRaised, CarromPalette.Night), c, r), r, c)
            drawCircle(CarromPalette.Gold, r - 3f, c, style = Stroke(3f))
            for (i in 0 until count * 2) {
                val a = Math.toRadians((i * sweep / 2f - 90f).toDouble())
                val stud = Offset(c.x + (r - 11f) * cos(a).toFloat(), c.y + (r - 11f) * sin(a).toFloat())
                drawCircle(CarromPalette.GoldLight, 4f, stud)
                drawCircle(CarromPalette.GoldDeep, 4f, stud, style = Stroke(1f))
            }
        }

        // The turning wheel: segments and labels rotate together in one layer.
        Box(
            Modifier
                .fillMaxSize()
                .padding(22.dp)
                .graphicsLayer { rotationZ = rotation() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.minDimension / 2f
                for (i in 0 until count) {
                    drawArc(segmentColor(i), -90f + i * sweep, sweep, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2))
                }
                for (i in 0 until count) {
                    val a = Math.toRadians((-90f + i * sweep).toDouble())
                    drawLine(CarromPalette.Gold, c, Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()), 2f)
                }
                drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f)), c, r), r, c)
            }
            for (i in 0 until count) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = (i + 0.5f) * sweep },
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        "${PRIZES[i]}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (segmentColor(i) == SEGMENT_CRIMSON) CarromPalette.Ivory else CarromPalette.Ink,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
            }
        }

        Medallion(size = 54.dp, accent = CarromPalette.Gold) {
            Text("SPIN", style = MaterialTheme.typography.labelMedium, color = CarromPalette.Ink)
        }

        // Pointer flicks as each peg passes underneath it.
        Canvas(
            Modifier
                .align(Alignment.TopCenter)
                .size(width = 26.dp, height = 34.dp)
                .graphicsLayer {
                    val passed = ((rotation() % sweep) + sweep) % sweep / sweep
                    rotationZ = if (spinning) 18f * (1f - passed).pow(4) else 0f
                    transformOrigin = TransformOrigin(0.5f, 0.2f)
                }
        ) {
            val path = Path().apply {
                moveTo(size.width / 2f, size.height)
                lineTo(0f, size.height * 0.25f)
                lineTo(size.width, size.height * 0.25f)
                close()
            }
            drawPath(path, Brush.verticalGradient(listOf(CarromPalette.GoldLight, CarromPalette.GoldDeep)))
            drawPath(path, CarromPalette.Ink, style = Stroke(1.5f))
            drawCircle(CarromPalette.Crimson, size.width * 0.2f, Offset(size.width / 2f, size.height * 0.28f))
        }
    }
}

private val SEGMENT_CRIMSON = Color(0xFF8E1C1C)
private val SEGMENT_CREAM = Color(0xFFF1E4C6)
private val SEGMENT_JACKPOT = Color(0xFFC9A227)

private fun segmentColor(index: Int): Color = when {
    PRIZES[index] == PRIZES.max() -> SEGMENT_JACKPOT
    index % 2 == 0 -> SEGMENT_CRIMSON
    else -> SEGMENT_CREAM
}

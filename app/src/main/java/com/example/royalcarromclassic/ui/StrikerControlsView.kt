package com.example.royalcarromclassic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.royalcarromclassic.data.GameState
import com.example.royalcarromclassic.data.TurnState
import com.example.royalcarromclassic.engine.BoardGeometry
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.board.tensionColor
import com.example.royalcarromclassic.ui.components.ClassicButton
import com.example.royalcarromclassic.ui.components.Glyph
import com.example.royalcarromclassic.ui.components.RepeatingGlyphButton
import com.example.royalcarromclassic.ui.components.classicPanel
import kotlin.math.abs
import kotlin.math.roundToInt

private const val ANGLE_STEP_DEGREES = 0.75f
private const val POWER_STEP = 2f
private const val OFFSET_STEP = 0.01f

/** What the hint line above the controls should say. */
private enum class ControlHint(val text: String) {
    PLACE("Slide the striker, then pull it back to shoot"),
    AIM("Touch the board to aim · pull the striker back to fire"),
    FOUL("On a foul circle — slide the striker clear"),
    MOVING("Discs in motion…"),
    BOT("Bot Master is lining up a shot…"),
    OVER("Match complete")
}

@Composable
fun StrikerControlsView(
    gameState: GameState,
    onPositionChanged: (Float) -> Unit,
    onPowerChanged: (Float) -> Unit,
    onNudgeAngle: (Float) -> Unit,
    onNudgePower: (Float) -> Unit,
    onShoot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBottom = gameState.isBottomTurn
    val enabled = gameState.canAim
    val strikerPos = BoardGeometry.getBaselineStrikerPos(gameState.strikerBaselineOffset, isBottom)
    val isFoulPosition = BoardGeometry.isOverBaselineCircle(strikerPos.x, strikerPos.y, isBottom)

    val hint = when {
        gameState.isGameOver -> ControlHint.OVER
        gameState.isAiTurn -> ControlHint.BOT
        gameState.turnState == TurnState.MOVING -> ControlHint.MOVING
        isFoulPosition -> ControlHint.FOUL
        gameState.turnState == TurnState.AIMING -> ControlHint.AIM
        else -> ControlHint.PLACE
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .classicPanel(raised = true)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedContent(
            targetState = hint,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 2 }) togetherWith
                    (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 2 })
            },
            label = "controlHint",
            modifier = Modifier.fillMaxWidth()
        ) { current ->
            Text(
                text = current.text,
                style = MaterialTheme.typography.bodySmall,
                color = when (current) {
                    ControlHint.FOUL -> CarromPalette.CrimsonLight
                    ControlHint.BOT -> CarromPalette.Silver
                    else -> CarromPalette.Parchment
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Baseline rail and the strike button
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RepeatingGlyphButton(
                Glyph.ChevronLeft, "Move striker left",
                onStep = { onPositionChanged(gameState.strikerBaselineOffset - OFFSET_STEP) },
                enabled = enabled,
                modifier = Modifier.size(32.dp)
            )
            BaselineRail(
                fraction = gameState.strikerBaselineOffset,
                enabled = enabled,
                isFoul = isFoulPosition,
                onFractionChanged = onPositionChanged,
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
            )
            RepeatingGlyphButton(
                Glyph.ChevronRight, "Move striker right",
                onStep = { onPositionChanged(gameState.strikerBaselineOffset + OFFSET_STEP) },
                enabled = enabled,
                modifier = Modifier.size(32.dp)
            )
            ClassicButton(
                text = "Strike",
                onClick = onShoot,
                enabled = enabled && !isFoulPosition,
                height = 40.dp,
                horizontalPadding = 14.dp,
                modifier = Modifier.padding(start = 2.dp)
            )
        }

        // Fine aim and power
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RepeatingGlyphButton(
                Glyph.RotateCcw, "Rotate aim left",
                onStep = { onNudgeAngle(-ANGLE_STEP_DEGREES) },
                enabled = enabled,
                modifier = Modifier.size(32.dp)
            )
            AimReadout(
                angle = gameState.strikerAimAngle,
                isBottom = isBottom,
                modifier = Modifier.width(50.dp)
            )
            RepeatingGlyphButton(
                Glyph.RotateCw, "Rotate aim right",
                onStep = { onNudgeAngle(ANGLE_STEP_DEGREES) },
                enabled = enabled,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(4.dp))
            PowerMeter(
                power = gameState.strikerPower,
                enabled = enabled,
                onPowerChanged = onPowerChanged,
                onNudge = onNudgePower,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Aim as seen by the shooter: "0°" straight ahead, then degrees to their left (L) or right (R). */
@Composable
private fun AimReadout(angle: Float, isBottom: Boolean, modifier: Modifier = Modifier) {
    val deviation = BoardGeometry.normalizeAngle(angle - BoardGeometry.forwardAngle(isBottom)) * BoardGeometry.RAD_TO_DEG
    val degrees = abs(deviation).roundToInt()
    val label = when {
        degrees == 0 -> "0°"
        deviation > 0 -> "$degrees° R"
        else -> "$degrees° L"
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("AIM", style = MaterialTheme.typography.labelSmall, color = CarromPalette.Muted)
        Text(label, style = MaterialTheme.typography.titleSmall, color = CarromPalette.Ivory, maxLines = 1)
    }
}

/**
 * The striker's baseline in miniature: a groove with its two red foul circles and a small
 * striker as the thumb. Tap or drag anywhere along it to place the striker.
 */
@Composable
private fun BaselineRail(
    fraction: Float,
    enabled: Boolean,
    isFoul: Boolean,
    onFractionChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnChange by rememberUpdatedState(onFractionChanged)
    var dragging by remember { mutableStateOf(false) }
    val thumbScale by animateFloatAsState(
        if (dragging) 1.18f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "railThumb"
    )
    val thumbColor = if (isFoul) CarromPalette.CrimsonLight else CarromPalette.Ivory

    Canvas(
        modifier
            .semantics {
                contentDescription = "Striker position"
                stateDescription = "${(fraction * 100).roundToInt()}%"
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val inset = size.height / 2f
                fun fractionAt(x: Float) = ((x - inset) / (size.width - inset * 2f)).coerceIn(0f, 1f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    dragging = true
                    currentOnChange(fractionAt(down.position.x))
                    drag(down.id) { change ->
                        change.consume()
                        currentOnChange(fractionAt(change.position.x))
                    }
                    dragging = false
                }
            }
    ) {
        val h = size.height
        val inset = h / 2f
        val trackLeft = inset
        val trackWidth = size.width - inset * 2f
        val cy = h / 2f
        val grooveHeight = h * 0.34f

        drawRoundRect(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), CarromPalette.Seam)),
            topLeft = Offset(trackLeft - grooveHeight / 2f, cy - grooveHeight / 2f),
            size = Size(trackWidth + grooveHeight, grooveHeight),
            cornerRadius = CornerRadius(grooveHeight / 2f)
        )
        drawLine(CarromPalette.Gold.copy(alpha = 0.35f), Offset(trackLeft, cy - grooveHeight / 2f - 2f), Offset(trackLeft + trackWidth, cy - grooveHeight / 2f - 2f), 1f)
        drawLine(CarromPalette.Gold.copy(alpha = 0.35f), Offset(trackLeft, cy + grooveHeight / 2f + 2f), Offset(trackLeft + trackWidth, cy + grooveHeight / 2f + 2f), 1f)

        // Foul circles at both ends, as on the board.
        val foulRadius = grooveHeight * 0.75f
        for (x in floatArrayOf(trackLeft, trackLeft + trackWidth)) {
            drawCircle(CarromPalette.Crimson, foulRadius, Offset(x, cy))
            drawCircle(Color.Black.copy(alpha = 0.5f), foulRadius, Offset(x, cy), style = Stroke(1f))
        }
        // Centre notch.
        drawLine(CarromPalette.Gold.copy(alpha = 0.5f), Offset(trackLeft + trackWidth / 2f, cy - grooveHeight), Offset(trackLeft + trackWidth / 2f, cy + grooveHeight), 1.5f)

        val thumbX = trackLeft + trackWidth * fraction
        val r = h * 0.36f * thumbScale
        drawCircle(Color.Black.copy(alpha = 0.35f), r, Offset(thumbX + 1.5f, cy + 2.5f))
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White, thumbColor, if (isFoul) CarromPalette.Crimson else CarromPalette.Parchment),
                center = Offset(thumbX - r * 0.3f, cy - r * 0.35f),
                radius = r * 1.3f
            ),
            r,
            Offset(thumbX, cy),
            alpha = if (enabled) 1f else 0.5f
        )
        drawCircle(CarromPalette.Gold, r * 0.7f, Offset(thumbX, cy), style = Stroke(r * 0.14f), alpha = if (enabled) 1f else 0.5f)
    }
}

/** Power gauge (drag or tap to set) flanked by −/+ steppers. */
@Composable
private fun PowerMeter(
    power: Float,
    enabled: Boolean,
    onPowerChanged: (Float) -> Unit,
    onNudge: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnChange by rememberUpdatedState(onPowerChanged)
    val fraction = ((power - BoardGeometry.MIN_POWER) / (BoardGeometry.MAX_POWER - BoardGeometry.MIN_POWER)).coerceIn(0f, 1f)
    val shown by animateFloatAsState(fraction, spring(stiffness = Spring.StiffnessMediumLow), label = "powerFill")

    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RepeatingGlyphButton(Glyph.Minus, "Less power", onStep = { onNudge(-POWER_STEP) }, enabled = enabled, modifier = Modifier.size(32.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("POWER ${power.roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = CarromPalette.Muted, maxLines = 1)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .semantics { contentDescription = "Shot power ${power.roundToInt()} percent" }
                    .pointerInput(enabled) {
                        if (!enabled) return@pointerInput
                        fun powerAt(x: Float) = BoardGeometry.MIN_POWER +
                            (x / size.width).coerceIn(0f, 1f) * (BoardGeometry.MAX_POWER - BoardGeometry.MIN_POWER)
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            currentOnChange(powerAt(down.position.x))
                            drag(down.id) { change ->
                                change.consume()
                                currentOnChange(powerAt(change.position.x))
                            }
                        }
                    }
            ) {
                val corner = CornerRadius(size.height / 2f)
                drawRoundRect(Color.Black.copy(alpha = 0.5f), cornerRadius = corner)
                drawRoundRect(
                    Brush.horizontalGradient(listOf(CarromPalette.Jade, CarromPalette.Amber, CarromPalette.CrimsonLight)),
                    size = Size(size.width * shown.coerceAtLeast(0.04f), size.height),
                    cornerRadius = corner,
                    alpha = if (enabled) 1f else 0.45f
                )
                // Graduation ticks every 20%.
                for (i in 1 until 5) {
                    val x = size.width * i / 5f
                    drawLine(Color.Black.copy(alpha = 0.35f), Offset(x, 2f), Offset(x, size.height - 2f), 1f)
                }
                drawRoundRect(tensionColor(shown).copy(alpha = 0.6f), cornerRadius = corner, style = Stroke(1f))
            }
        }
        RepeatingGlyphButton(Glyph.Plus, "More power", onStep = { onNudge(POWER_STEP) }, enabled = enabled, modifier = Modifier.size(32.dp))
    }
}

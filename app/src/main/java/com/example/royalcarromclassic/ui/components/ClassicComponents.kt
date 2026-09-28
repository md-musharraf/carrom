package com.example.royalcarromclassic.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.royalcarromclassic.theme.CarromPalette
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val PanelShape = RoundedCornerShape(16.dp)
val ChipShape = RoundedCornerShape(11.dp)
private val SheetShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)

/**
 * The house surface: a softly lit mahogany panel edged with a brass hairline.
 * [accent] tints the edge (e.g. the active player's colour); [raised] lifts the fill.
 */
fun Modifier.classicPanel(
    shape: Shape = PanelShape,
    accent: Color = CarromPalette.Gold,
    accentAlpha: Float = 0.28f,
    raised: Boolean = false
): Modifier {
    val top = if (raised) CarromPalette.MahoganyRaised else CarromPalette.Mahogany
    val bottom = if (raised) CarromPalette.Mahogany else CarromPalette.Walnut
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(top, bottom)))
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(accent.copy(alpha = accentAlpha), accent.copy(alpha = accentAlpha * 0.35f))
            ),
            shape = shape
        )
}

/** Springy scale-down-on-press value for [interactionSource]. */
@Composable
fun rememberPressScale(interactionSource: MutableInteractionSource, pressedScale: Float = 0.94f): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
}

/** Applies [scale] in the layer phase, so the animation never recomposes the content. */
fun Modifier.scaledBy(scale: State<Float>): Modifier = graphicsLayer {
    scaleX = scale.value
    scaleY = scale.value
}

enum class ButtonStyle { Brass, Outline, Crimson }

/** Primary call-to-action with a polished brass (or lacquer) face. */
@Composable
fun ClassicButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: ButtonStyle = ButtonStyle.Brass,
    leading: Glyph? = null,
    height: Dp = 48.dp,
    horizontalPadding: Dp = 16.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    val shape = RoundedCornerShape(14.dp)
    val face = when {
        !enabled -> Brush.verticalGradient(listOf(CarromPalette.Seam, CarromPalette.MahoganyRaised))
        style == ButtonStyle.Brass -> Brush.verticalGradient(listOf(CarromPalette.GoldLight, CarromPalette.Gold, CarromPalette.GoldDeep))
        style == ButtonStyle.Crimson -> Brush.verticalGradient(listOf(CarromPalette.CrimsonLight, CarromPalette.Crimson, Color(0xFF7A1D14)))
        else -> Brush.verticalGradient(listOf(CarromPalette.MahoganyRaised, CarromPalette.Mahogany))
    }
    val content = when {
        !enabled -> CarromPalette.Muted
        style == ButtonStyle.Brass -> CarromPalette.Ink
        else -> CarromPalette.Ivory
    }
    val edge = when {
        !enabled -> CarromPalette.Seam
        style == ButtonStyle.Outline -> CarromPalette.Gold.copy(alpha = 0.55f)
        else -> Color.White.copy(alpha = 0.25f)
    }

    Box(
        modifier = modifier
            .height(height)
            .scaledBy(scale)
            .clip(shape)
            .background(face)
            .border(1.dp, edge, shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (leading != null) GlyphIcon(leading, size = 16.dp, tint = content)
            Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
        }
    }
}

/** Square icon chip used in the top bar and steppers. */
@Composable
fun GlyphButton(
    glyph: Glyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = CarromPalette.GoldLight,
    glyphSize: Dp = 18.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, pressedScale = 0.88f)
    Box(
        modifier = modifier
            .scaledBy(scale)
            .classicPanel(ChipShape, accentAlpha = if (enabled) 0.32f else 0.12f, raised = true)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(glyph, size = glyphSize, tint = if (enabled) tint else CarromPalette.Muted.copy(alpha = 0.5f))
    }
}

/**
 * Stepper button that fires once on press and then repeats (accelerating) while held,
 * for comfortable fine-tuning of aim and power.
 */
@Composable
fun RepeatingGlyphButton(
    glyph: Glyph,
    contentDescription: String,
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = CarromPalette.GoldLight
) {
    val currentOnStep by rememberUpdatedState(onStep)
    var pressed by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(if (pressed) 0.86f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "stepperScale")
    Box(
        modifier = modifier
            .scaledBy(scale)
            .classicPanel(ChipShape, accentAlpha = if (enabled) 0.3f else 0.1f, raised = true)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown()
                        pressed = true
                        currentOnStep()
                        val repeater = launch {
                            delay(360)
                            var interval = 90L
                            while (true) {
                                currentOnStep()
                                delay(interval)
                                interval = (interval * 0.85f).toLong().coerceAtLeast(28L)
                            }
                        }
                        try {
                            waitForUpOrCancellation()
                        } finally {
                            repeater.cancel()
                            pressed = false
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(glyph, size = 16.dp, tint = if (enabled) tint else CarromPalette.Muted.copy(alpha = 0.5f))
    }
}

/** Circular engraved medallion (player crests, level badge, dialog emblems). */
@Composable
fun Medallion(
    size: Dp,
    accent: Color,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val r = this.size.minDimension / 2f
                drawCircle(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.95f), lerpColor(accent, Color.Black, 0.55f)),
                        center = Offset(r * 0.7f, r * 0.6f),
                        radius = r * 1.4f
                    ),
                    radius = r
                )
                drawCircle(Color.White.copy(alpha = 0.28f), radius = r - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
                drawCircle(Color.Black.copy(alpha = 0.35f), radius = r * 0.78f, style = Stroke(1.dp.toPx()))
                if (progress != null) {
                    val w = 2.5.dp.toPx()
                    drawArc(
                        Color.Black.copy(alpha = 0.35f), -90f, 360f, false,
                        topLeft = Offset(w / 2, w / 2),
                        size = Size(this.size.width - w, this.size.height - w),
                        style = Stroke(w)
                    )
                    drawArc(
                        CarromPalette.GoldLight, -90f, 360f * progress.coerceIn(0f, 1f), false,
                        topLeft = Offset(w / 2, w / 2),
                        size = Size(this.size.width - w, this.size.height - w),
                        style = Stroke(w, cap = StrokeCap.Round)
                    )
                }
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** A brass hairline that fades out at both ends. */
@Composable
fun GoldDivider(modifier: Modifier = Modifier, alpha: Float = 0.45f) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, CarromPalette.Gold.copy(alpha = alpha), Color.Transparent)
                )
            )
    )
}

/** Ornamental section heading: ◆ TITLE ◆ with fading rules either side. */
@Composable
fun OrnamentHeading(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GoldDivider(Modifier.weight(1f), alpha = 0.35f)
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = CarromPalette.Gold)
        GoldDivider(Modifier.weight(1f), alpha = 0.35f)
    }
}

/**
 * Segmented selector with a brass indicator that glides between options.
 */
@Composable
fun SegmentedSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(ChipShape)
            .background(CarromPalette.Night.copy(alpha = 0.6f))
            .border(1.dp, CarromPalette.Seam, ChipShape)
            .padding(3.dp)
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(
            targetValue = segment * selectedIndex,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "segmentIndicator"
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(9.dp))
                .background(Brush.verticalGradient(listOf(CarromPalette.GoldLight, CarromPalette.Gold, CarromPalette.GoldDeep)))
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val color by animateColorAsState(
                    if (selected) CarromPalette.Ink else CarromPalette.Parchment,
                    tween(220),
                    label = "segmentLabel"
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(role = Role.Tab) { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
                }
            }
        }
    }
}

/** Small uppercase badge, e.g. "POPULAR" or "EQUIPPED". */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, color: Color = CarromPalette.Gold) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

/** Row of 1..[max] stars, filled up to [stars]. */
@Composable
fun StarRating(stars: Int, modifier: Modifier = Modifier, max: Int = 3, size: Dp = 14.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..max) {
            GlyphIcon(
                if (i <= stars) Glyph.Star else Glyph.StarOutline,
                size = size,
                tint = if (i <= stars) CarromPalette.GoldLight else CarromPalette.Muted
            )
        }
    }
}

/** Coin glyph with a count that rolls smoothly to new values. */
@Composable
fun CoinAmount(amount: Int, modifier: Modifier = Modifier) {
    val shown by animateIntAsState(amount, tween(650), label = "coinCount")
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        GlyphIcon(Glyph.Coin, size = 16.dp)
        Text("%,d".format(shown), style = MaterialTheme.typography.titleSmall, color = CarromPalette.GoldLight, maxLines = 1)
    }
}

/**
 * Shared bottom sheet chrome: walnut sheet, brass grab handle and a serif title block.
 * Every sheet in the game uses it so they look and behave as one family.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassicSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = CarromPalette.Walnut,
        contentColor = CarromPalette.Ivory,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 44.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(CarromPalette.Gold.copy(alpha = 0.55f))
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineMedium, color = CarromPalette.GoldLight)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CarromPalette.Muted)
                    }
                }
                trailing?.invoke()
            }
            GoldDivider()
            content()
        }
    }
}

/** A labelled value row for stats tables. */
@Composable
fun StatRow(label: String, value: String, valueColor: Color = CarromPalette.Ivory) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CarromPalette.Parchment, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

fun lerpColor(a: Color, b: Color, t: Float): Color = lerp(a, b, t)

package com.example.royalcarromclassic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.royalcarromclassic.data.DiceFace
import com.example.royalcarromclassic.data.DiceSkin
import kotlinx.coroutines.delay

/**
 * A die drawn in the house style: a bevelled, softly lit cube face with inset pips.
 * While [rolling] it tumbles through random faces; when a new roll lands ([rollId] changes) it
 * bounces into place. [face] null shows a blank face waiting to be rolled.
 */
@Composable
fun DieView(
    face: DiceFace?,
    skin: DiceSkin,
    modifier: Modifier = Modifier,
    rolling: Boolean = false,
    rollId: Int = 0,
    size: Dp = 64.dp
) {
    var tumbling by remember { mutableIntStateOf(1) }
    LaunchedEffect(rolling) {
        var i = 0
        while (rolling) {
            tumbling = 1 + (i * 5 + 3) % 6
            i++
            delay(70)
        }
    }
    val wobble = if (rolling) {
        rememberInfiniteTransition(label = "dieWobble").animateFloat(
            -16f, 16f, infiniteRepeatable(tween(140), RepeatMode.Reverse), label = "dieAngle"
        ).value
    } else {
        0f
    }
    val land = remember { Animatable(1f) }
    LaunchedEffect(rollId) {
        if (rollId > 0) {
            land.snapTo(1.3f)
            land.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val pips = if (rolling) tumbling else face?.pips ?: 0
    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = face?.let { "Die showing ${it.pips}: ${it.title}" } ?: "Die, not rolled" }
    ) {
        val s = land.value
        rotate(wobble) {
            scale(s, s) { drawDie(skin, pips) }
        }
    }
}

/** The die face centred in the draw area, with [pips] 1..6 (0 draws a blank face). */
fun DrawScope.drawDie(skin: DiceSkin, pips: Int) {
    val side = size.minDimension * 0.86f
    val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)
    val corner = CornerRadius(side * 0.2f)
    // Contact shadow, then the body lit from the top-left, then a bevel and a glossy highlight.
    drawRoundRect(Color.Black.copy(alpha = 0.4f), topLeft + Offset(side * 0.05f, side * 0.08f), Size(side, side), corner)
    drawRoundRect(
        Brush.linearGradient(
            listOf(lerp(skin.body, Color.White, 0.3f), skin.body, lerp(skin.body, skin.edge, 0.6f)),
            start = topLeft,
            end = topLeft + Offset(side, side)
        ),
        topLeft, Size(side, side), corner
    )
    drawRoundRect(skin.edge, topLeft, Size(side, side), corner, style = Stroke(side * 0.035f))
    val inset = side * 0.09f
    drawRoundRect(
        Color.White.copy(alpha = 0.18f),
        topLeft + Offset(inset, inset),
        Size(side - inset * 2f, side * 0.34f),
        CornerRadius(side * 0.14f)
    )

    val r = side * 0.085f
    fun pip(u: Float, v: Float) {
        val c = topLeft + Offset(side * u, side * v)
        drawCircle(lerp(skin.pip, Color.Black, 0.3f), r * 1.1f, c + Offset(r * 0.12f, r * 0.18f), alpha = 0.5f)
        drawCircle(skin.pip, r, c)
        drawCircle(Color.White, r * 0.3f, c - Offset(r * 0.3f, r * 0.3f), alpha = 0.25f)
    }
    val lo = 0.27f
    val mid = 0.5f
    val hi = 0.73f
    when (pips) {
        1 -> pip(mid, mid)
        2 -> { pip(lo, lo); pip(hi, hi) }
        3 -> { pip(lo, lo); pip(mid, mid); pip(hi, hi) }
        4 -> { pip(lo, lo); pip(hi, lo); pip(lo, hi); pip(hi, hi) }
        5 -> { pip(lo, lo); pip(hi, lo); pip(mid, mid); pip(lo, hi); pip(hi, hi) }
        6 -> { pip(lo, lo); pip(hi, lo); pip(lo, mid); pip(hi, mid); pip(lo, hi); pip(hi, hi) }
    }
}

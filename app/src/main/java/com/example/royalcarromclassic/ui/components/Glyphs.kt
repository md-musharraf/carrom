package com.example.royalcarromclassic.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.royalcarromclassic.theme.CarromPalette
import kotlin.math.cos
import kotlin.math.sin

/** Hand-drawn vector glyphs so icons look identical (and classic) on every device. */
enum class Glyph {
    Modes, Target, Gift, Shop, Rules, Settings, Coin, Crown, Star, StarOutline, Lock,
    ChevronLeft, ChevronRight, ChevronDown, RotateCcw, RotateCw, Minus, Plus,
    SoundOn, SoundOff, Vibration, Trophy, Bolt, Chart, Check, Players,
    Globe, Flag, Chat, Copy, Share, Phone, Close, User, Edit,
    Home, Dice, Timer, Restart, Coins, Board
}

@Composable
fun GlyphIcon(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = CarromPalette.Ivory,
    contentDescription: String? = null
) {
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Canvas(modifier.size(size).then(semantics)) { drawGlyph(glyph, tint) }
}

/** Draws [glyph] centred in the current draw area. */
fun DrawScope.drawGlyph(glyph: Glyph, tint: Color) {
    val s = minOf(size.width, size.height)
    val ox = (size.width - s) / 2f
    val oy = (size.height - s) / 2f
    fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    val line = Stroke(width = s * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val thin = Stroke(width = s * 0.065f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (glyph) {
        Glyph.Modes -> {
            val r = s * 0.15f
            drawCircle(tint, r, p(0.3f, 0.3f))
            drawCircle(tint, r, p(0.7f, 0.7f))
            drawCircle(tint, r, p(0.7f, 0.3f), style = thin)
            drawCircle(tint, r, p(0.3f, 0.7f), style = thin)
        }

        Glyph.Target -> {
            drawCircle(tint, s * 0.40f, p(0.5f, 0.5f), style = thin)
            drawCircle(tint, s * 0.24f, p(0.5f, 0.5f), style = thin)
            drawCircle(tint, s * 0.08f, p(0.5f, 0.5f))
        }

        Glyph.Gift -> {
            drawRoundRect(tint, p(0.2f, 0.46f), Size(s * 0.6f, s * 0.42f), CornerRadius(s * 0.05f), style = thin)
            drawRoundRect(tint, p(0.13f, 0.32f), Size(s * 0.74f, s * 0.15f), CornerRadius(s * 0.04f), style = thin)
            drawLine(tint, p(0.5f, 0.32f), p(0.5f, 0.88f), s * 0.065f)
            drawCircle(tint, s * 0.085f, p(0.39f, 0.22f), style = thin)
            drawCircle(tint, s * 0.085f, p(0.61f, 0.22f), style = thin)
        }

        Glyph.Shop -> {
            drawRoundRect(tint, p(0.18f, 0.36f), Size(s * 0.64f, s * 0.52f), CornerRadius(s * 0.07f), style = thin)
            drawArc(tint, 180f, 180f, false, p(0.34f, 0.14f), Size(s * 0.32f, s * 0.42f), style = thin)
        }

        Glyph.Rules -> {
            drawRoundRect(tint, p(0.12f, 0.2f), Size(s * 0.38f, s * 0.62f), CornerRadius(s * 0.05f), style = thin)
            drawRoundRect(tint, p(0.5f, 0.2f), Size(s * 0.38f, s * 0.62f), CornerRadius(s * 0.05f), style = thin)
            for (row in 0..2) {
                val y = 0.38f + row * 0.13f
                drawLine(tint, p(0.2f, y), p(0.41f, y), s * 0.05f, cap = StrokeCap.Round)
                drawLine(tint, p(0.59f, y), p(0.8f, y), s * 0.05f, cap = StrokeCap.Round)
            }
        }

        Glyph.Settings -> {
            val c = p(0.5f, 0.5f)
            for (i in 0 until 8) {
                rotate(i * 45f, c) {
                    drawRoundRect(tint, p(0.43f, 0.08f), Size(s * 0.14f, s * 0.2f), CornerRadius(s * 0.03f))
                }
            }
            drawCircle(tint, s * 0.25f, c, style = Stroke(width = s * 0.13f))
        }

        Glyph.Coin -> {
            val c = p(0.5f, 0.5f)
            drawCircle(
                Brush.radialGradient(
                    listOf(CarromPalette.GoldLight, CarromPalette.Gold, CarromPalette.GoldDeep),
                    center = p(0.38f, 0.34f),
                    radius = s * 0.6f
                ),
                s * 0.44f,
                c
            )
            drawCircle(CarromPalette.GoldDeep, s * 0.3f, c, style = Stroke(width = s * 0.06f))
            drawCircle(CarromPalette.GoldLight.copy(alpha = 0.7f), s * 0.44f, c, style = Stroke(width = s * 0.04f))
        }

        Glyph.Crown -> {
            val crown = Path().apply {
                moveTo(ox + 0.14f * s, oy + 0.72f * s)
                lineTo(ox + 0.18f * s, oy + 0.3f * s)
                lineTo(ox + 0.36f * s, oy + 0.5f * s)
                lineTo(ox + 0.5f * s, oy + 0.2f * s)
                lineTo(ox + 0.64f * s, oy + 0.5f * s)
                lineTo(ox + 0.82f * s, oy + 0.3f * s)
                lineTo(ox + 0.86f * s, oy + 0.72f * s)
                close()
            }
            drawPath(crown, tint)
            drawRoundRect(tint, p(0.14f, 0.77f), Size(s * 0.72f, s * 0.09f), CornerRadius(s * 0.03f))
        }

        Glyph.Star, Glyph.StarOutline -> {
            val star = starPath(ox + s / 2f, oy + s * 0.53f, s * 0.46f, s * 0.19f, points = 5)
            if (glyph == Glyph.Star) drawPath(star, tint) else drawPath(star, tint, style = thin)
        }

        Glyph.Lock -> {
            drawArc(tint, 180f, 180f, false, p(0.3f, 0.12f), Size(s * 0.4f, s * 0.44f), style = line)
            drawLine(tint, p(0.3f, 0.34f), p(0.3f, 0.46f), s * 0.09f)
            drawLine(tint, p(0.7f, 0.34f), p(0.7f, 0.46f), s * 0.09f)
            drawRoundRect(tint, p(0.18f, 0.44f), Size(s * 0.64f, s * 0.44f), CornerRadius(s * 0.08f))
        }

        Glyph.ChevronLeft -> chevron(p(0.62f, 0.2f), p(0.34f, 0.5f), p(0.62f, 0.8f), tint, line)
        Glyph.ChevronRight -> chevron(p(0.38f, 0.2f), p(0.66f, 0.5f), p(0.38f, 0.8f), tint, line)
        Glyph.ChevronDown -> chevron(p(0.22f, 0.38f), p(0.5f, 0.66f), p(0.78f, 0.38f), tint, line)

        Glyph.RotateCcw, Glyph.RotateCw -> {
            val clockwise = glyph == Glyph.RotateCw
            val c = p(0.5f, 0.54f)
            val r = s * 0.3f
            val startDeg = if (clockwise) 200f else -20f
            val sweep = if (clockwise) 250f else -250f
            drawArc(tint, startDeg, sweep, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = line)
            val endRad = Math.toRadians((startDeg + sweep).toDouble())
            val tip = Offset(c.x + r * cos(endRad).toFloat(), c.y + r * sin(endRad).toFloat())
            val dir = if (clockwise) 1f else -1f
            // Tangent of motion at the arc end, then two barbs of the arrowhead.
            val tx = (-sin(endRad)).toFloat() * dir
            val ty = cos(endRad).toFloat() * dir
            val barb = s * 0.16f
            drawLine(tint, tip, Offset(tip.x - tx * barb - ty * barb, tip.y - ty * barb + tx * barb), s * 0.09f, cap = StrokeCap.Round)
            drawLine(tint, tip, Offset(tip.x - tx * barb + ty * barb, tip.y - ty * barb - tx * barb), s * 0.09f, cap = StrokeCap.Round)
        }

        Glyph.Minus -> drawLine(tint, p(0.24f, 0.5f), p(0.76f, 0.5f), s * 0.1f, cap = StrokeCap.Round)
        Glyph.Plus -> {
            drawLine(tint, p(0.24f, 0.5f), p(0.76f, 0.5f), s * 0.1f, cap = StrokeCap.Round)
            drawLine(tint, p(0.5f, 0.24f), p(0.5f, 0.76f), s * 0.1f, cap = StrokeCap.Round)
        }

        Glyph.SoundOn, Glyph.SoundOff -> {
            val speaker = Path().apply {
                moveTo(ox + 0.12f * s, oy + 0.39f * s)
                lineTo(ox + 0.28f * s, oy + 0.39f * s)
                lineTo(ox + 0.48f * s, oy + 0.2f * s)
                lineTo(ox + 0.48f * s, oy + 0.8f * s)
                lineTo(ox + 0.28f * s, oy + 0.61f * s)
                lineTo(ox + 0.12f * s, oy + 0.61f * s)
                close()
            }
            drawPath(speaker, tint)
            if (glyph == Glyph.SoundOn) {
                drawArc(tint, -45f, 90f, false, p(0.42f, 0.34f), Size(s * 0.24f, s * 0.32f), style = thin)
                drawArc(tint, -50f, 100f, false, p(0.4f, 0.2f), Size(s * 0.44f, s * 0.6f), style = thin)
            } else {
                drawLine(tint, p(0.62f, 0.36f), p(0.88f, 0.64f), s * 0.08f, cap = StrokeCap.Round)
                drawLine(tint, p(0.88f, 0.36f), p(0.62f, 0.64f), s * 0.08f, cap = StrokeCap.Round)
            }
        }

        Glyph.Vibration -> {
            drawRoundRect(tint, p(0.33f, 0.14f), Size(s * 0.34f, s * 0.72f), CornerRadius(s * 0.07f), style = thin)
            drawLine(tint, p(0.18f, 0.34f), p(0.18f, 0.66f), s * 0.065f, cap = StrokeCap.Round)
            drawLine(tint, p(0.82f, 0.34f), p(0.82f, 0.66f), s * 0.065f, cap = StrokeCap.Round)
            drawLine(tint, p(0.06f, 0.42f), p(0.06f, 0.58f), s * 0.065f, cap = StrokeCap.Round)
            drawLine(tint, p(0.94f, 0.42f), p(0.94f, 0.58f), s * 0.065f, cap = StrokeCap.Round)
        }

        Glyph.Trophy -> {
            val cup = Path().apply {
                moveTo(ox + 0.27f * s, oy + 0.16f * s)
                lineTo(ox + 0.73f * s, oy + 0.16f * s)
                lineTo(ox + 0.71f * s, oy + 0.4f * s)
                cubicTo(ox + 0.69f * s, oy + 0.56f * s, ox + 0.6f * s, oy + 0.62f * s, ox + 0.5f * s, oy + 0.62f * s)
                cubicTo(ox + 0.4f * s, oy + 0.62f * s, ox + 0.31f * s, oy + 0.56f * s, ox + 0.29f * s, oy + 0.4f * s)
                close()
            }
            drawPath(cup, tint)
            drawArc(tint, 90f, 180f, false, p(0.12f, 0.2f), Size(s * 0.24f, s * 0.24f), style = thin)
            drawArc(tint, -90f, 180f, false, p(0.64f, 0.2f), Size(s * 0.24f, s * 0.24f), style = thin)
            drawRect(tint, p(0.46f, 0.6f), Size(s * 0.08f, s * 0.14f))
            drawRoundRect(tint, p(0.3f, 0.74f), Size(s * 0.4f, s * 0.1f), CornerRadius(s * 0.03f))
        }

        Glyph.Bolt -> {
            val bolt = Path().apply {
                moveTo(ox + 0.58f * s, oy + 0.08f * s)
                lineTo(ox + 0.24f * s, oy + 0.56f * s)
                lineTo(ox + 0.47f * s, oy + 0.56f * s)
                lineTo(ox + 0.4f * s, oy + 0.92f * s)
                lineTo(ox + 0.76f * s, oy + 0.42f * s)
                lineTo(ox + 0.53f * s, oy + 0.42f * s)
                close()
            }
            drawPath(bolt, tint)
        }

        Glyph.Chart -> {
            drawRoundRect(tint, p(0.16f, 0.5f), Size(s * 0.16f, s * 0.34f), CornerRadius(s * 0.03f))
            drawRoundRect(tint, p(0.42f, 0.3f), Size(s * 0.16f, s * 0.54f), CornerRadius(s * 0.03f))
            drawRoundRect(tint, p(0.68f, 0.16f), Size(s * 0.16f, s * 0.68f), CornerRadius(s * 0.03f))
        }

        Glyph.Players -> {
            drawCircle(tint, s * 0.12f, p(0.34f, 0.32f))
            drawArc(tint, 180f, 180f, true, p(0.12f, 0.5f), Size(s * 0.44f, s * 0.44f))
            drawCircle(tint, s * 0.12f, p(0.68f, 0.32f), style = thin)
            drawArc(tint, 180f, 180f, false, p(0.46f, 0.5f), Size(s * 0.44f, s * 0.44f), style = thin)
        }

        Glyph.Globe -> {
            val c = p(0.5f, 0.5f)
            drawCircle(tint, s * 0.38f, c, style = thin)
            drawOval(tint, p(0.34f, 0.12f), Size(s * 0.32f, s * 0.76f), style = thin)
            drawLine(tint, p(0.12f, 0.5f), p(0.88f, 0.5f), s * 0.065f, cap = StrokeCap.Round)
            drawArc(tint, 200f, 140f, false, p(0.16f, 0.26f), Size(s * 0.68f, s * 0.4f), style = thin)
            drawArc(tint, 20f, 140f, false, p(0.16f, 0.34f), Size(s * 0.68f, s * 0.4f), style = thin)
        }

        Glyph.Flag -> {
            drawLine(tint, p(0.26f, 0.12f), p(0.26f, 0.9f), s * 0.08f, cap = StrokeCap.Round)
            val cloth = Path().apply {
                moveTo(ox + 0.3f * s, oy + 0.16f * s)
                cubicTo(ox + 0.45f * s, oy + 0.08f * s, ox + 0.6f * s, oy + 0.26f * s, ox + 0.8f * s, oy + 0.16f * s)
                lineTo(ox + 0.8f * s, oy + 0.52f * s)
                cubicTo(ox + 0.6f * s, oy + 0.62f * s, ox + 0.45f * s, oy + 0.44f * s, ox + 0.3f * s, oy + 0.52f * s)
                close()
            }
            drawPath(cloth, tint)
        }

        Glyph.Chat -> {
            drawRoundRect(tint, p(0.12f, 0.16f), Size(s * 0.76f, s * 0.54f), CornerRadius(s * 0.14f), style = thin)
            val tail = Path().apply {
                moveTo(ox + 0.3f * s, oy + 0.68f * s)
                lineTo(ox + 0.26f * s, oy + 0.88f * s)
                lineTo(ox + 0.48f * s, oy + 0.7f * s)
            }
            drawPath(tail, tint, style = thin)
            for (i in 0..2) drawCircle(tint, s * 0.05f, p(0.34f + i * 0.16f, 0.43f))
        }

        Glyph.Copy -> {
            drawRoundRect(tint, p(0.32f, 0.3f), Size(s * 0.5f, s * 0.56f), CornerRadius(s * 0.07f), style = thin)
            val back = Path().apply {
                moveTo(ox + 0.2f * s, oy + 0.66f * s)
                lineTo(ox + 0.2f * s, oy + 0.22f * s)
                cubicTo(ox + 0.2f * s, oy + 0.17f * s, ox + 0.23f * s, oy + 0.14f * s, ox + 0.28f * s, oy + 0.14f * s)
                lineTo(ox + 0.62f * s, oy + 0.14f * s)
            }
            drawPath(back, tint, style = thin)
        }

        Glyph.Share -> {
            val a = p(0.72f, 0.22f)
            val b = p(0.28f, 0.5f)
            val c = p(0.72f, 0.78f)
            drawLine(tint, a, b, s * 0.065f)
            drawLine(tint, b, c, s * 0.065f)
            drawCircle(tint, s * 0.12f, a)
            drawCircle(tint, s * 0.12f, b)
            drawCircle(tint, s * 0.12f, c)
        }

        Glyph.Phone -> {
            drawRoundRect(tint, p(0.3f, 0.1f), Size(s * 0.4f, s * 0.8f), CornerRadius(s * 0.08f), style = thin)
            drawLine(tint, p(0.44f, 0.2f), p(0.56f, 0.2f), s * 0.05f, cap = StrokeCap.Round)
            drawCircle(tint, s * 0.045f, p(0.5f, 0.78f))
        }

        Glyph.Close -> {
            drawLine(tint, p(0.26f, 0.26f), p(0.74f, 0.74f), s * 0.09f, cap = StrokeCap.Round)
            drawLine(tint, p(0.74f, 0.26f), p(0.26f, 0.74f), s * 0.09f, cap = StrokeCap.Round)
        }

        Glyph.Edit -> {
            val pencil = Path().apply {
                moveTo(ox + 0.66f * s, oy + 0.14f * s)
                lineTo(ox + 0.86f * s, oy + 0.34f * s)
                lineTo(ox + 0.38f * s, oy + 0.82f * s)
                lineTo(ox + 0.14f * s, oy + 0.86f * s)
                lineTo(ox + 0.18f * s, oy + 0.62f * s)
                close()
            }
            drawPath(pencil, tint, style = thin)
            drawLine(tint, p(0.56f, 0.24f), p(0.76f, 0.44f), s * 0.065f)
        }

        Glyph.User -> {
            drawCircle(tint, s * 0.17f, p(0.5f, 0.32f))
            drawArc(tint, 180f, 180f, true, p(0.18f, 0.56f), Size(s * 0.64f, s * 0.56f))
        }

        Glyph.Check -> {
            val check = Path().apply {
                moveTo(ox + 0.2f * s, oy + 0.52f * s)
                lineTo(ox + 0.42f * s, oy + 0.72f * s)
                lineTo(ox + 0.8f * s, oy + 0.3f * s)
            }
            drawPath(check, tint, style = line)
        }

        Glyph.Home -> {
            val roof = Path().apply {
                moveTo(ox + 0.14f * s, oy + 0.5f * s)
                lineTo(ox + 0.5f * s, oy + 0.16f * s)
                lineTo(ox + 0.86f * s, oy + 0.5f * s)
            }
            drawPath(roof, tint, style = line)
            drawRoundRect(tint, p(0.26f, 0.46f), Size(s * 0.48f, s * 0.4f), CornerRadius(s * 0.04f), style = thin)
            drawRoundRect(tint, p(0.43f, 0.62f), Size(s * 0.14f, s * 0.24f), CornerRadius(s * 0.03f))
        }

        Glyph.Dice -> {
            drawRoundRect(tint, p(0.16f, 0.16f), Size(s * 0.68f, s * 0.68f), CornerRadius(s * 0.14f), style = thin)
            val pip = s * 0.065f
            drawCircle(tint, pip, p(0.34f, 0.34f))
            drawCircle(tint, pip, p(0.5f, 0.5f))
            drawCircle(tint, pip, p(0.66f, 0.66f))
        }

        Glyph.Timer -> {
            drawCircle(tint, s * 0.34f, p(0.5f, 0.56f), style = thin)
            drawLine(tint, p(0.5f, 0.56f), p(0.5f, 0.36f), s * 0.07f, cap = StrokeCap.Round)
            drawLine(tint, p(0.5f, 0.56f), p(0.64f, 0.64f), s * 0.07f, cap = StrokeCap.Round)
            drawLine(tint, p(0.4f, 0.1f), p(0.6f, 0.1f), s * 0.07f, cap = StrokeCap.Round)
        }

        Glyph.Restart -> {
            drawArc(tint, -60f, 300f, false, p(0.2f, 0.2f), Size(s * 0.6f, s * 0.6f), style = line)
            val head = Path().apply {
                moveTo(ox + 0.62f * s, oy + 0.1f * s)
                lineTo(ox + 0.8f * s, oy + 0.26f * s)
                lineTo(ox + 0.56f * s, oy + 0.34f * s)
                close()
            }
            drawPath(head, tint)
        }

        Glyph.Coins -> {
            drawCircle(tint, s * 0.2f, p(0.32f, 0.62f), style = thin)
            drawCircle(tint, s * 0.2f, p(0.68f, 0.62f), style = thin)
            drawCircle(tint, s * 0.2f, p(0.5f, 0.32f))
        }

        Glyph.Board -> {
            drawRoundRect(tint, p(0.12f, 0.12f), Size(s * 0.76f, s * 0.76f), CornerRadius(s * 0.08f), style = thin)
            val r = s * 0.075f
            drawCircle(tint, r, p(0.25f, 0.25f))
            drawCircle(tint, r, p(0.75f, 0.25f))
            drawCircle(tint, r, p(0.25f, 0.75f))
            drawCircle(tint, r, p(0.75f, 0.75f))
            drawCircle(tint, s * 0.12f, p(0.5f, 0.5f), style = thin)
        }
    }
}

private fun DrawScope.chevron(a: Offset, b: Offset, c: Offset, tint: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
    }
    drawPath(path, tint, style = stroke)
}

/** A regular star polygon centred at ([cx], [cy]) with its first point straight up. */
fun starPath(cx: Float, cy: Float, outer: Float, inner: Float, points: Int): Path = Path().apply {
    val step = Math.PI / points
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outer else inner
        val a = -Math.PI / 2 + i * step
        val x = cx + (r * cos(a)).toFloat()
        val y = cy + (r * sin(a)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

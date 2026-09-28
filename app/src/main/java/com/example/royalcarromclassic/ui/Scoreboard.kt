package com.example.royalcarromclassic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.royalcarromclassic.data.BoardSummary
import com.example.royalcarromclassic.data.GameMode
import com.example.royalcarromclassic.data.PieceType
import com.example.royalcarromclassic.data.PlayerData
import com.example.royalcarromclassic.data.TrickShotStatus
import com.example.royalcarromclassic.theme.CarromPalette
import com.example.royalcarromclassic.ui.board.PieceArt
import com.example.royalcarromclassic.ui.board.drawCarromMan
import com.example.royalcarromclassic.ui.components.Glyph
import com.example.royalcarromclassic.ui.components.GlyphIcon
import com.example.royalcarromclassic.ui.components.Medallion
import com.example.royalcarromclassic.ui.components.PanelShape
import com.example.royalcarromclassic.ui.components.classicPanel

/**
 * A player's seat at the table: engraved crest, name, turn caption and a rolling score.
 * The active seat glows in the player's metal (gold or silver).
 */
@Composable
fun PlayerPlate(
    player: PlayerData,
    accent: Color,
    isActive: Boolean,
    caption: String,
    mirrored: Boolean,
    modifier: Modifier = Modifier
) {
    val edge by animateColorAsState(
        if (isActive) accent else CarromPalette.Seam,
        tween(350),
        label = "plateEdge"
    )
    val glow = if (isActive) {
        rememberInfiniteTransition(label = "activePlate")
            .animateFloat(0.3f, 0.95f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "plateGlow")
    } else {
        null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .classicPanel(PanelShape, accent = accent, accentAlpha = 0.12f, raised = isActive)
            .border(1.5.dp, edge.copy(alpha = if (isActive) 0.9f else 0.5f), PanelShape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val crest: @Composable () -> Unit = {
            Box(contentAlignment = Alignment.Center) {
                if (glow != null) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .graphicsLayer { alpha = glow.value }
                            .clip(CircleShape)
                            .border(2.dp, accent, CircleShape)
                    )
                }
                Medallion(size = 36.dp, accent = accent) {
                    Text(player.monogram, style = MaterialTheme.typography.titleSmall, color = CarromPalette.Ink, maxLines = 1)
                }
            }
        }
        val details: @Composable RowScope.() -> Unit = {
            Column(
                Modifier.weight(1f),
                horizontalAlignment = if (mirrored) Alignment.End else Alignment.Start
            ) {
                Text(
                    player.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isActive) CarromPalette.Ivory else CarromPalette.Parchment,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AnimatedVisibility(
                    visible = isActive,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Text(caption.uppercase(), style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1)
                }
            }
        }
        val score: @Composable () -> Unit = {
            RollingScore(player.score, color = if (isActive) CarromPalette.GoldLight else CarromPalette.Parchment)
        }

        if (mirrored) {
            score()
            details()
            crest()
        } else {
            crest()
            details()
            score()
        }
    }
}

/** Stands in for the opponent during a trick shot: the level, its hint and the shots left. */
@Composable
fun ChallengePlate(status: TrickShotStatus, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .classicPanel(PanelShape, accent = CarromPalette.Silver, accentAlpha = 0.3f)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Medallion(size = 36.dp, accent = CarromPalette.Silver) {
            Text("${status.levelId}", style = MaterialTheme.typography.titleSmall, color = CarromPalette.Ink, maxLines = 1)
        }
        Column(Modifier.weight(1f)) {
            Text(status.title, style = MaterialTheme.typography.titleSmall, color = CarromPalette.Ivory, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status.hint, style = MaterialTheme.typography.bodySmall, color = CarromPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("SHOTS LEFT", style = MaterialTheme.typography.labelSmall, color = CarromPalette.Muted, maxLines = 1)
            RollingScore(status.shotsLeft, color = if (status.shotsLeft > 0) CarromPalette.Silver else CarromPalette.CrimsonLight)
        }
    }
}

/** Serif score that rolls to its new value like a mechanical counter. */
@Composable
private fun RollingScore(score: Int, color: Color) {
    AnimatedContent(
        targetState = score,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(tween(320)) { if (up) it else -it } + fadeIn(tween(320))) togetherWith
                (slideOutVertically(tween(320)) { if (up) -it else it } + fadeOut(tween(200))) using
                SizeTransform(clip = true)
        },
        label = "score"
    ) { value ->
        Text("$value", style = MaterialTheme.typography.headlineLarge, color = color, maxLines = 1)
    }
}

/** Discs left on the board, the queen's status and the current mode (tap to change). */
@Composable
fun BoardStatusStrip(
    summary: BoardSummary,
    mode: GameMode,
    queenNeedsCover: Boolean,
    queenCovered: Boolean,
    onOpenModes: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .classicPanel(accentAlpha = 0.16f)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DiscCount(PieceType.WHITE, summary.whites)
        DiscCount(PieceType.BLACK, summary.blacks)

        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MiniDisc(PieceType.QUEEN, 15.dp, alpha = if (summary.queenOnBoard) 1f else 0.4f)
            val (text, color) = when {
                queenNeedsCover -> "Cover the queen!" to CarromPalette.Amber
                queenCovered -> "Queen covered" to CarromPalette.Jade
                summary.queenOnBoard -> "Queen · 25" to CarromPalette.Parchment
                else -> "No queen" to CarromPalette.Muted
            }
            AnimatedContent(targetState = text to color, label = "queenStatus") { (label, tint) ->
                Text(label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClickLabel = "Change game mode", onClick = onOpenModes)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(mode.displayName.uppercase(), style = MaterialTheme.typography.labelMedium, color = CarromPalette.Gold, maxLines = 1)
            GlyphIcon(Glyph.ChevronDown, size = 12.dp, tint = CarromPalette.Gold)
        }
    }
}

@Composable
private fun DiscCount(type: PieceType, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        MiniDisc(type, 15.dp)
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
            },
            label = "discCount"
        ) { value ->
            Text("$value", style = MaterialTheme.typography.titleSmall, color = CarromPalette.Ivory)
        }
    }
}

/** A carrom man drawn with the same painter as the board, for the HUD and menus. */
@Composable
fun MiniDisc(type: PieceType, size: Dp, modifier: Modifier = Modifier, alpha: Float = 1f) {
    Spacer(
        modifier
            .size(size)
            .drawWithCache {
                val art = PieceArt.forType(type, this.size.minDimension / 2f * 0.84f)
                onDrawBehind {
                    drawCarromMan(art, this.size.width / 2f, this.size.height / 2f, alpha = alpha, shadowAlpha = 0.6f)
                }
            }
    )
}

val GameMode.displayName: String
    get() = when (this) {
        GameMode.VS_AI -> "vs Bot"
        GameMode.CLASSIC -> "Classic"
        GameMode.DISC_POOL -> "Disc Pool"
        GameMode.FREESTYLE -> "Freestyle"
        GameMode.TRICK_SHOTS -> "Trick Shots"
        GameMode.PASS_AND_PLAY -> "Pass & Play"
        GameMode.PRACTICE -> "Practice"
    }

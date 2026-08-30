package com.example.royalcarromclassic.engine

import com.example.royalcarromclassic.data.AIDifficulty
import com.example.royalcarromclassic.data.Piece
import com.example.royalcarromclassic.data.PieceType
import com.example.royalcarromclassic.data.Vector2D
import kotlin.math.*
import kotlin.random.Random

object CarromAIEngine {

    data class AIShotPlan(
        val baselineFraction: Float,
        val aimAngle: Float,
        val power: Float,
        val targetPiece: Piece?,
        val targetPocketId: Int,
        val score: Float
    )

    fun calculateBestShot(
        pieces: List<Piece>,
        aiColor: PieceType?,
        queenNeedsCover: Boolean,
        queenPottedByAI: Boolean,
        difficulty: AIDifficulty
    ): AIShotPlan {
        val activePieces = ArrayList<Piece>(pieces.size)
        val targetCandidates = ArrayList<Piece>(pieces.size)
        val pieceCount = pieces.size

        for (i in 0 until pieceCount) {
            val p = pieces[i]
            if (!p.isPocketed && p.type != PieceType.STRIKER) {
                activePieces.add(p)
                val isCandidate = when {
                    queenNeedsCover && queenPottedByAI -> {
                        if (aiColor != null) p.type == aiColor else (p.type == PieceType.WHITE || p.type == PieceType.BLACK)
                    }
                    p.type == PieceType.QUEEN -> true
                    aiColor != null -> p.type == aiColor
                    else -> (p.type == PieceType.WHITE || p.type == PieceType.BLACK)
                }
                if (isCandidate) {
                    targetCandidates.add(p)
                }
            }
        }

        val effectiveCandidates = if (targetCandidates.isNotEmpty()) targetCandidates else activePieces
        if (effectiveCandidates.isEmpty()) {
            return AIShotPlan(
                baselineFraction = 0.5f,
                aimAngle = BoardGeometry.HALF_PI,
                power = 60f,
                targetPiece = null,
                targetPocketId = 2,
                score = 0f
            )
        }

        var bestShot = AIShotPlan(
            baselineFraction = 0.5f,
            aimAngle = BoardGeometry.HALF_PI,
            power = 70f,
            targetPiece = effectiveCandidates.firstOrNull(),
            targetPocketId = 2,
            score = -99999f
        )

        val sampleCount = if (difficulty == AIDifficulty.HARD) 15 else 8
        val candidateCount = effectiveCandidates.size
        val activeCount = activePieces.size
        val pockets = BoardGeometry.POCKETS
        val pocketCount = pockets.size

        for (s in 0 until sampleCount) {
            val fraction = 0.06f + (s.toFloat() / (sampleCount - 1).toFloat()) * 0.88f
            val strikerPos = BoardGeometry.getBaselineStrikerPos(fraction, isBottomPlayer = false) // AI on Top Baseline

            for (t in 0 until candidateCount) {
                val target = effectiveCandidates[t]

                for (pk in 0 until pocketCount) {
                    val pocket = pockets[pk]
                    val toPocketX = pocket.x - target.x
                    val toPocketY = pocket.y - target.y
                    val distToPocket = hypot(toPocketX, toPocketY)
                    if (distToPocket < 10f) continue

                    val invDistToPocket = 1f / distToPocket
                    val dirToPocketX = toPocketX * invDistToPocket
                    val dirToPocketY = toPocketY * invDistToPocket

                    // Ghost striker impact point behind target piece
                    val impactOffset = target.radius + BoardGeometry.STRIKER_RADIUS
                    val ghostX = target.x - dirToPocketX * impactOffset
                    val ghostY = target.y - dirToPocketY * impactOffset

                    val toGhostX = ghostX - strikerPos.x
                    val toGhostY = ghostY - strikerPos.y
                    val distStrikerToGhost = hypot(toGhostX, toGhostY)

                    if (distStrikerToGhost < 5f || toGhostY < 10f) continue

                    val invDistStrikerToGhost = 1f / distStrikerToGhost
                    val strikerDirX = toGhostX * invDistStrikerToGhost
                    val strikerDirY = toGhostY * invDistStrikerToGhost
                    val cutAngleCosine = strikerDirX * dirToPocketX + strikerDirY * dirToPocketY

                    // Only consider forward cut angles
                    if (cutAngleCosine < 0.22f) continue

                    // Fast obstruction check along path
                    var obstructed = false
                    val minClearance = target.radius + BoardGeometry.STRIKER_RADIUS - 2f
                    for (i in 0 until activeCount) {
                        val p = activePieces[i]
                        if (p.id == target.id) continue
                        val distToLine = pointToSegmentDistance(
                            p.x, p.y,
                            strikerPos.x, strikerPos.y,
                            ghostX, ghostY
                        )
                        if (distToLine < minClearance) {
                            obstructed = true
                            break
                        }
                    }

                    var shotScore = 1000f
                    if (obstructed) shotScore -= 750f

                    shotScore += cutAngleCosine * 500f
                    shotScore -= distToPocket * 0.4f
                    shotScore -= distStrikerToGhost * 0.3f

                    if (target.type == PieceType.QUEEN) shotScore += 400f

                    val totalDist = distStrikerToGhost + distToPocket
                    val requiredPower = ((totalDist / 900f) * 80f + (1f - cutAngleCosine) * 20f).coerceIn(40f, 100f)

                    if (shotScore > bestShot.score) {
                        bestShot = AIShotPlan(
                            baselineFraction = fraction,
                            aimAngle = atan2(toGhostY, toGhostX),
                            power = requiredPower,
                            targetPiece = target,
                            targetPocketId = pocket.id,
                            score = shotScore
                        )
                    }
                }
            }
        }

        // Apply calibrated difficulty jitter
        val (angleJitter, powerJitter) = when (difficulty) {
            AIDifficulty.EASY -> Pair((Random.nextFloat() - 0.5f) * 0.16f, (Random.nextFloat() - 0.5f) * 22f)
            AIDifficulty.MEDIUM -> Pair((Random.nextFloat() - 0.5f) * 0.04f, (Random.nextFloat() - 0.5f) * 8f)
            AIDifficulty.HARD -> Pair((Random.nextFloat() - 0.5f) * 0.01f, (Random.nextFloat() - 0.5f) * 2f)
        }

        return bestShot.copy(
            aimAngle = bestShot.aimAngle + angleJitter,
            power = (bestShot.power + powerJitter).coerceIn(30f, 100f)
        )
    }

    private fun pointToSegmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val abX = bx - ax
        val abY = by - ay
        val apX = px - ax
        val apY = py - ay
        val abLenSq = abX * abX + abY * abY
        if (abLenSq == 0f) return hypot(apX, apY)

        val t = ((apX * abX + apY * abY) / abLenSq).coerceIn(0f, 1f)
        val projX = ax + t * abX
        val projY = ay + t * abY
        return hypot(px - projX, py - projY)
    }
}

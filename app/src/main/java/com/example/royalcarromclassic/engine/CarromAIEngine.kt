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
        val activePieces = pieces.filter { !it.isPocketed }

        // 1. Determine target candidates
        var targetCandidates = if (queenNeedsCover && queenPottedByAI) {
            activePieces.filter { if (aiColor != null) it.type == aiColor else (it.type == PieceType.WHITE || it.type == PieceType.BLACK) }
        } else {
            activePieces.filter {
                if (it.type == PieceType.QUEEN) true
                else if (aiColor != null) it.type == aiColor
                else (it.type == PieceType.WHITE || it.type == PieceType.BLACK)
            }
        }

        if (targetCandidates.isEmpty()) {
            targetCandidates = activePieces
        }

        if (targetCandidates.isEmpty()) {
            return AIShotPlan(
                baselineFraction = 0.5f,
                aimAngle = PI.toFloat() / 2f,
                power = 60f,
                targetPiece = null,
                targetPocketId = 2,
                score = 0f
            )
        }

        var bestShot = AIShotPlan(
            baselineFraction = 0.5f,
            aimAngle = PI.toFloat() / 2f,
            power = 70f,
            targetPiece = targetCandidates.firstOrNull(),
            targetPocketId = 2,
            score = -99999f
        )

        val sampleCount = if (difficulty == AIDifficulty.HARD) 17 else 9

        for (s in 0 until sampleCount) {
            val fraction = 0.05f + (s.toFloat() / (sampleCount - 1).toFloat()) * 0.9f
            val strikerPos = BoardGeometry.getBaselineStrikerPos(fraction, isBottomPlayer = false) // AI on Top Baseline

            for (target in targetCandidates) {
                for (pocket in BoardGeometry.POCKETS) {
                    val toPocketX = pocket.x - target.x
                    val toPocketY = pocket.y - target.y
                    val distToPocket = hypot(toPocketX, toPocketY)
                    if (distToPocket < 10f) continue

                    val dirToPocketX = toPocketX / distToPocket
                    val dirToPocketY = toPocketY / distToPocket

                    // Ghost striker point behind target piece
                    val impactOffset = target.radius + BoardGeometry.STRIKER_RADIUS
                    val ghostX = target.x - dirToPocketX * impactOffset
                    val ghostY = target.y - dirToPocketY * impactOffset

                    val toGhostX = ghostX - strikerPos.x
                    val toGhostY = ghostY - strikerPos.y
                    val distStrikerToGhost = hypot(toGhostX, toGhostY)

                    if (distStrikerToGhost < 5f) continue
                    // Must shoot downwards (positive Y direction in virtual coords)
                    if (toGhostY < 10f) continue

                    val rawAngle = atan2(toGhostY, toGhostX)

                    val strikerDirX = toGhostX / distStrikerToGhost
                    val strikerDirY = toGhostY / distStrikerToGhost
                    val cutAngleCosine = strikerDirX * dirToPocketX + strikerDirY * dirToPocketY

                    // Forward cut angle
                    if (cutAngleCosine < 0.25f) continue

                    // Obstruction check
                    var obstructed = false
                    for (p in activePieces) {
                        if (p.id == target.id) continue
                        val distToLine = pointToSegmentDistance(
                            Vector2D(p.x, p.y),
                            strikerPos,
                            Vector2D(ghostX, ghostY)
                        )
                        if (distToLine < p.radius + BoardGeometry.STRIKER_RADIUS - 2f) {
                            obstructed = true
                            break
                        }
                    }

                    var shotScore = 1000f
                    if (obstructed) shotScore -= 800f

                    shotScore += cutAngleCosine * 500f
                    shotScore -= distToPocket * 0.4f
                    shotScore -= distStrikerToGhost * 0.3f

                    if (target.type == PieceType.QUEEN) shotScore += 400f

                    val totalDist = distStrikerToGhost + distToPocket
                    val requiredPower = ((totalDist / 900f) * 80f + (1f - cutAngleCosine) * 20f).coerceIn(40f, 100f)

                    if (shotScore > bestShot.score) {
                        bestShot = AIShotPlan(
                            baselineFraction = fraction,
                            aimAngle = rawAngle,
                            power = requiredPower,
                            targetPiece = target,
                            targetPocketId = pocket.id,
                            score = shotScore
                        )
                    }
                }
            }
        }

        // Apply difficulty jitter
        val (angleJitter, powerJitter) = when (difficulty) {
            AIDifficulty.EASY -> Pair((Random.nextFloat() - 0.5f) * 0.16f, (Random.nextFloat() - 0.5f) * 25f)
            AIDifficulty.MEDIUM -> Pair((Random.nextFloat() - 0.5f) * 0.05f, (Random.nextFloat() - 0.5f) * 10f)
            AIDifficulty.HARD -> Pair((Random.nextFloat() - 0.5f) * 0.012f, (Random.nextFloat() - 0.5f) * 3f)
        }

        return bestShot.copy(
            aimAngle = bestShot.aimAngle + angleJitter,
            power = (bestShot.power + powerJitter).coerceIn(30f, 100f)
        )
    }

    private fun pointToSegmentDistance(p: Vector2D, a: Vector2D, b: Vector2D): Float {
        val abX = b.x - a.x
        val abY = b.y - a.y
        val apX = p.x - a.x
        val apY = p.y - a.y
        val abLenSq = abX * abX + abY * abY
        if (abLenSq == 0f) return hypot(apX, apY)

        val t = ((apX * abX + apY * abY) / abLenSq).coerceIn(0f, 1f)
        val projX = a.x + t * abX
        val projY = a.y + t * abY
        return hypot(p.x - projX, p.y - projY)
    }
}

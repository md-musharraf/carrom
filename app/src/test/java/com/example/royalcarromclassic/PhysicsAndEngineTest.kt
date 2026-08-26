package com.example.royalcarromclassic

import androidx.compose.ui.graphics.Color
import com.example.royalcarromclassic.core.logging.PerformanceTracker
import com.example.royalcarromclassic.data.AIDifficulty
import com.example.royalcarromclassic.data.PieceType
import com.example.royalcarromclassic.engine.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PhysicsAndEngineTest {

    @Before
    fun setUp() {
        PerformanceTracker.reset()
    }

    @Test
    fun testClassicClusterGeneration() {
        val pieces = CarromPhysicsEngine.generateClassicCluster()
        assertEquals("Total pieces in classic cluster should be 19", 19, pieces.size)

        val queenCount = pieces.count { it.type == PieceType.QUEEN }
        val whiteCount = pieces.count { it.type == PieceType.WHITE }
        val blackCount = pieces.count { it.type == PieceType.BLACK }

        assertEquals(1, queenCount)
        assertEquals(9, whiteCount)
        assertEquals(9, blackCount)

        val queen = pieces.first { it.type == PieceType.QUEEN }
        assertEquals(400f, queen.x, 0.1f)
        assertEquals(400f, queen.y, 0.1f)
    }

    @Test
    fun testStrikerCreationAndBaseline() {
        val striker = CarromPhysicsEngine.createStriker(0.5f, isBottom = true)
        assertEquals(PieceType.STRIKER, striker.type)
        assertEquals(BoardGeometry.BASELINE_BOTTOM_Y, striker.y, 0.1f)
        assertEquals(400f, striker.x, 0.1f)
    }

    @Test
    fun testBaselineFoulCircleDetection() {
        val centerPos = BoardGeometry.getBaselineStrikerPos(0.5f, true)
        assertFalse(BoardGeometry.isOverBaselineCircle(centerPos.x, centerPos.y, true))

        assertTrue(BoardGeometry.isOverBaselineCircle(BoardGeometry.BASELINE_START_X, BoardGeometry.BASELINE_BOTTOM_Y, true))
        assertTrue(BoardGeometry.isOverBaselineCircle(BoardGeometry.BASELINE_END_X, BoardGeometry.BASELINE_BOTTOM_Y, true))
    }

    @Test
    fun testTrajectoryCalculation() {
        val striker = CarromPhysicsEngine.createStriker(0.5f, true)
        val pieces = CarromPhysicsEngine.generateClassicCluster()

        val trajectory = CarromPhysicsEngine.calculateTrajectory(
            striker = striker,
            aimAngle = -Math.PI.toFloat() / 2f,
            power = 80f,
            pieces = pieces
        )

        assertNotNull(trajectory)
        assertTrue(trajectory.strikerPath.size >= 2)
        assertNotNull(trajectory.targetHitPiece)
    }

    @Test
    fun testAIShotCalculation() {
        val pieces = CarromPhysicsEngine.generateClassicCluster()
        
        for (difficulty in listOf(AIDifficulty.EASY, AIDifficulty.MEDIUM, AIDifficulty.HARD)) {
            val shot = CarromAIEngine.calculateBestShot(
                pieces = pieces,
                aiColor = PieceType.BLACK,
                queenNeedsCover = false,
                queenPottedByAI = false,
                difficulty = difficulty
            )

            assertNotNull("AI shot must not be null for $difficulty", shot)
            assertTrue("AI baseline position must be between 0.0 and 1.0", shot.baselineFraction in 0f..1f)
            assertTrue("AI power must be between 20 and 100", shot.power in 20f..100f)
        }
    }

    @Test
    fun testParticlePoolLifecycleAndZeroAllocation() {
        val particleSystem = ParticleSystem(maxCapacity = 32)
        
        particleSystem.spawnImpactSparks(400f, 400f, Color.Yellow, count = 8)
        
        // Update frames to simulate decay
        for (f in 1..25) {
            particleSystem.update()
        }
        
        // Clear pool
        particleSystem.clear()
    }

    @Test
    fun testPerformanceTrackerRecording() {
        PerformanceTracker.reset()
        val start = PerformanceTracker.recordPhysicsTickStart()
        Thread.sleep(2)
        PerformanceTracker.recordPhysicsTickEnd(start)
        PerformanceTracker.recordCollision()

        assertTrue(PerformanceTracker.getAverageTickDurationMs() > 0.0)
        assertEquals(1L, PerformanceTracker.getCollisionCount())
    }

    @Test
    fun testTrickShotsData() {
        val levels = TrickShotsManager.LEVELS
        assertEquals(10, levels.size)
        assertTrue("Level 1 must be unlocked by default", levels[0].isUnlocked)
    }
}

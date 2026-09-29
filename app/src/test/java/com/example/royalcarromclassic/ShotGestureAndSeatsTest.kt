package com.example.royalcarromclassic

import com.example.royalcarromclassic.data.AIDifficulty
import com.example.royalcarromclassic.data.PieceType
import com.example.royalcarromclassic.data.Seat
import com.example.royalcarromclassic.engine.BoardGeometry
import com.example.royalcarromclassic.engine.CarromAIEngine
import com.example.royalcarromclassic.engine.CarromPhysicsEngine
import com.example.royalcarromclassic.engine.PieceFactory
import com.example.royalcarromclassic.ui.board.ShotGesture
import com.example.royalcarromclassic.ui.board.ShotGesture.Kind
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ShotGestureAndSeatsTest {

    /** A drag of length 20 at [degreesFromBack] degrees away from straight back, for [seat]. */
    private fun dragAt(seat: Seat, degreesFromBack: Float): Pair<Float, Float> {
        val back = kotlin.math.atan2(seat.outwardY, seat.outwardX)
        val a = back + degreesFromBack * BoardGeometry.DEG_TO_RAD
        return 20f * cos(a) to 20f * sin(a)
    }

    @Test
    fun diagonalPullsArePullsNotSlides() {
        // The old gesture slid the striker for any pull more than ~63° off straight back.
        for (seat in Seat.entries) {
            for (deg in listOf(0f, 30f, -30f, 50f, -50f)) {
                val (dx, dy) = dragAt(seat, deg)
                assertEquals("$seat at $deg°", Kind.PULL, ShotGesture.classify(dx, dy, seat))
            }
        }
    }

    @Test
    fun onlyDragsAlongTheBaselineSlide() {
        for (seat in Seat.entries) {
            for (deg in listOf(90f, -90f, 75f, -75f)) {
                val (dx, dy) = dragAt(seat, deg)
                assertEquals("$seat at $deg°", Kind.SLIDE, ShotGesture.classify(dx, dy, seat))
            }
        }
    }

    @Test
    fun forwardDragsAimInsteadOfFiringBackwards() {
        for (seat in Seat.entries) {
            val (dx, dy) = dragAt(seat, 180f)
            assertEquals(Kind.AIM, ShotGesture.classify(dx, dy, seat))
        }
    }

    @Test
    fun aSlideThatTurnsBackBecomesAPull() {
        val striker = BoardGeometry.strikerPos(0.5f, Seat.BOTTOM)
        assertFalse(ShotGesture.escalatesToPull(striker.x + 60f, striker.y + 10f, striker.x, striker.y, Seat.BOTTOM))
        assertTrue(ShotGesture.escalatesToPull(striker.x + 60f, striker.y + 45f, striker.x, striker.y, Seat.BOTTOM))
        val right = BoardGeometry.strikerPos(0.5f, Seat.RIGHT)
        assertTrue(ShotGesture.escalatesToPull(right.x + 45f, right.y, right.x, right.y, Seat.RIGHT))
    }

    @Test
    fun theStrikerIsAlwaysAtLeastAFingertipWide() {
        // On a small phone 30dp may be 70 board units: a touch 60 units away still grabs it.
        assertTrue(ShotGesture.touchesStriker(460f, 660f, 400f, 660f, 22f, minTouchRadius = 70f))
        assertFalse(ShotGesture.touchesStriker(480f, 660f, 400f, 660f, 22f, minTouchRadius = 70f))
    }

    @Test
    fun pullingSetsPowerAwayFromTheFingerAndShortPullsCancel() {
        assertNull(ShotGesture.pull(400f, 660f, 400f, 670f, 20f, 150f, null, 0f))
        val soft = ShotGesture.pull(400f, 660f, 400f, 690f, 20f, 150f, null, 0f)!!
        val hard = ShotGesture.pull(400f, 660f, 400f, 900f, 20f, 150f, null, 0f)!!
        assertEquals(-BoardGeometry.HALF_PI, soft.angle, 1e-4f) // Pulled down, flies up.
        assertTrue(soft.power < hard.power)
        assertEquals(BoardGeometry.MAX_POWER, hard.power, 1e-3f)
    }

    @Test
    fun aPullNearTheChosenAimKeepsItExactly() {
        val locked = -BoardGeometry.HALF_PI + 0.2f
        val magnet = 5f * BoardGeometry.DEG_TO_RAD
        // Pull 3° off the locked aim: snaps.
        val near = -BoardGeometry.HALF_PI + 0.2f + 3f * BoardGeometry.DEG_TO_RAD
        val fx = 400f - cos(near) * 80f
        val fy = 660f - sin(near) * 80f
        assertEquals(locked, ShotGesture.pull(400f, 660f, fx, fy, 20f, 150f, locked, magnet)!!.angle, 1e-5f)
        // Pull 10° off: the pull wins.
        val far = locked + 10f * BoardGeometry.DEG_TO_RAD
        val gx = 400f - cos(far) * 80f
        val gy = 660f - sin(far) * 80f
        assertEquals(far, ShotGesture.pull(400f, 660f, gx, gy, 20f, 150f, locked, magnet)!!.angle, 1e-4f)
    }

    @Test
    fun railTouchesFollowTheSeat() {
        assertTrue(ShotGesture.touchesRail(300f, 665f, Seat.BOTTOM, 30f))
        assertFalse(ShotGesture.touchesRail(300f, 600f, Seat.BOTTOM, 30f))
        assertTrue(ShotGesture.touchesRail(145f, 300f, Seat.LEFT, 30f))
        assertFalse(ShotGesture.touchesRail(300f, 665f, Seat.LEFT, 30f))
    }

    @Test
    fun everySeatHasItsOwnBaselineAndForwardAim() {
        assertEquals(BoardGeometry.BASELINE_BOTTOM_Y, BoardGeometry.strikerPos(0.5f, Seat.BOTTOM).y)
        assertEquals(BoardGeometry.BASELINE_TOP_Y, BoardGeometry.strikerPos(0.5f, Seat.TOP).y)
        assertEquals(BoardGeometry.BASELINE_LEFT_X, BoardGeometry.strikerPos(0.5f, Seat.LEFT).x)
        assertEquals(BoardGeometry.BASELINE_RIGHT_X, BoardGeometry.strikerPos(0.5f, Seat.RIGHT).x)
        for (seat in Seat.entries) {
            val from = BoardGeometry.strikerPos(0.5f, seat)
            val a = BoardGeometry.forwardAngle(seat)
            val ahead = from.x + cos(a) * 100f to from.y + sin(a) * 100f
            val before = abs(from.x - BoardGeometry.CENTER) + abs(from.y - BoardGeometry.CENTER)
            val after = abs(ahead.first - BoardGeometry.CENTER) + abs(ahead.second - BoardGeometry.CENTER)
            assertTrue("$seat forward points into the board", after < before)
            // Foul circles sit at both ends of every baseline.
            val end = BoardGeometry.strikerPos(0f, seat)
            assertTrue(BoardGeometry.isOverBaselineCircle(end.x, end.y, seat))
            assertFalse(BoardGeometry.isOverBaselineCircle(from.x, from.y, seat))
        }
    }

    @Test
    fun rotatingASeatOntoTheTopIsExact() {
        for (seat in Seat.entries) {
            val p = BoardGeometry.strikerPos(0.3f, seat)
            val top = BoardGeometry.rotateAboutCenter(p.x, p.y, BoardGeometry.rotationToTop(seat))
            assertEquals(BoardGeometry.BASELINE_TOP_Y, top.y, 0.01f)
        }
    }

    @Test
    fun theBotShootsIntoTheBoardFromEverySeat() {
        val pieces = CarromPhysicsEngine.generateClassicCluster()
        for (seat in Seat.entries) {
            val plan = CarromAIEngine.calculateBestShot(pieces, null, false, false, AIDifficulty.HARD, seat, Random(7))
            assertTrue(plan.baselineFraction in BoardGeometry.MIN_BASELINE_FRACTION..BoardGeometry.MAX_BASELINE_FRACTION)
            val forward = BoardGeometry.forwardAngle(seat)
            assertTrue("$seat aims forward", cos(plan.aimAngle - forward) > 0.2f)
            assertNotNull(plan.targetPiece)
            assertTrue(pieces.any { it === plan.targetPiece })
        }
    }

    @Test
    fun aSideSeatBotShotActuallyHitsTheRack() {
        val pieces = CarromPhysicsEngine.generateClassicCluster()
        val plan = CarromAIEngine.calculateBestShot(pieces, null, false, false, AIDifficulty.HARD, Seat.LEFT, Random(3))
        val striker = PieceFactory.createStriker(plan.baselineFraction, Seat.LEFT)
        val preview = CarromPhysicsEngine.calculateTrajectory(striker, plan.aimAngle, plan.power, pieces)
        assertNotNull("The left-seat bot's aim meets a disc", preview.targetHitPiece)
        assertTrue(preview.targetHitPiece!!.type != PieceType.STRIKER)
    }
}

package com.example.royalcarromclassic

import androidx.compose.ui.graphics.Color
import com.example.royalcarromclassic.data.PieceType
import com.example.royalcarromclassic.engine.*
import com.example.royalcarromclassic.ui.WheelMath
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class EngineBehaviourTest {

    /** Simulates 1.5 s of a full-power break at [hz] frames per second. */
    private fun breakShot(hz: Int): List<Pair<Float, Float>> {
        val pieces = CarromPhysicsEngine.generateClassicCluster()
        val striker = CarromPhysicsEngine.createStriker(0.5f, isBottom = true)
        striker.vy = -CarromPhysicsEngine.MAX_STRIKE_SPEED
        repeat(hz * 3 / 2) { CarromPhysicsEngine.updatePhysics(pieces, striker, 1f / hz) }
        return pieces.map { it.x to it.y }
    }

    @Test
    fun motionIsIdenticalAt60And120Hz() {
        val at60 = breakShot(60)
        val at120 = breakShot(120)
        for (i in at60.indices) {
            assertEquals("x of disc $i", at60[i].first, at120[i].first, 0.5f)
            assertEquals("y of disc $i", at60[i].second, at120[i].second, 0.5f)
        }
    }

    @Test
    fun headOnImpactConservesMomentum() {
        val puck = PieceFactory.createPiece("p", PieceType.WHITE, 400f, 400f)
        val striker = PieceFactory.createPiece("s", PieceType.STRIKER, 400f, 440f)
        striker.vy = -10f
        val before = striker.mass * striker.vy + puck.mass * puck.vy
        repeat(8) { CarromPhysicsEngine.updatePhysics(listOf(puck), striker, 1f / 240f) }
        assertTrue("Puck was struck", puck.vy < -5f)
        val after = striker.mass * striker.vy + puck.mass * puck.vy
        // Friction removes a little; the impulse exchange itself must conserve momentum.
        assertEquals(before, after, abs(before) * 0.05f)
    }

    @Test
    fun fullPowerStrikerCannotTunnelThroughADisc() {
        val puck = PieceFactory.createPiece("p", PieceType.BLACK, 400f, 560f)
        val striker = CarromPhysicsEngine.createStriker(0.5f, isBottom = true)
        striker.vy = -CarromPhysicsEngine.MAX_STRIKE_SPEED * 1.3f
        CarromPhysicsEngine.updatePhysics(listOf(puck), striker, 1f / 20f) // Worst-case frame hitch.
        assertTrue("Striker stays behind the disc", striker.y > puck.y)
        assertTrue("Disc was driven forward", puck.vy < 0f)
    }

    @Test
    fun pocketDropAnimationCompletesInAboutAThirdOfASecond() {
        val piece = PieceFactory.createPiece("p", PieceType.WHITE, 70f, 70f).apply {
            isPocketed = true
            pocketId = 0
        }
        var t = 0f
        while (CarromPhysicsEngine.advancePocketDrops(listOf(piece), null, 1f / 60f)) t += 1f / 60f
        assertEquals(0f, piece.pocketProgress, 0f)
        assertEquals(CarromPhysicsEngine.POCKET_DROP_SECONDS, t, 0.05f)
    }

    @Test
    fun queenIsRespottedClearOfDiscsOnTheCentre() {
        val blocker = PieceFactory.createPiece("b", PieceType.BLACK, BoardGeometry.CENTER + 4f, BoardGeometry.CENTER)
        val queen = PieceFactory.createPiece("q", PieceType.QUEEN, 0f, 0f)
        val spot = CarromPhysicsEngine.findFreeSpot(listOf(blocker, queen), queen.radius, ignore = queen)
        val gap = hypot(spot.x - blocker.x, spot.y - blocker.y)
        assertTrue("Spot is clear (gap $gap)", gap >= queen.radius + blocker.radius)
        assertTrue("Spot is near the centre", hypot(spot.x - BoardGeometry.CENTER, spot.y - BoardGeometry.CENTER) < 60f)
    }

    @Test
    fun aiChoreographyEasesToTheTargetAndReleasesOnce() {
        val director = AiShotDirector()
        val target = ShotAim(baselineOffset = 0.8f, angle = 2.2f, power = 74f)
        director.begin(from = ShotAim(0.5f, BoardGeometry.HALF_PI, 50f), to = target)

        var releases = 0
        var t = 0f
        while (director.isActive && t < 5f) {
            if (director.advance(1f / 60f)) releases++
            t += 1f / 60f
        }
        assertEquals(1, releases)
        assertTrue("Takes a natural moment, took $t s", t in 1.5f..3f)
        assertEquals(target.baselineOffset, director.current.baselineOffset, 1e-4f)
        assertEquals(target.power, director.current.power, 1e-4f)
        assertEquals(0f, BoardGeometry.normalizeAngle(director.current.angle - target.angle), 1e-4f)
    }

    @Test
    fun aiTurnsTheShortWayRound() {
        val turn = AiShotDirector.shortestTurn(from = 3.0f, to = -3.0f)
        assertTrue("Crosses ±π instead of sweeping the long way: $turn", abs(turn) < 0.3f)
    }

    @Test
    fun particlesExpireInRealTime() {
        val particles = ParticleSystem(maxCapacity = 32)
        particles.spawnCollisionDust(400f, 400f, intensity = 1f)
        particles.spawnPocketVortex(68f, 68f)
        particles.spawnImpactSparks(400f, 400f, Color.Yellow, count = 4)
        assertTrue(particles.hasActiveParticles)
        repeat(10) { particles.update(0.1f) }
        assertFalse(particles.hasActiveParticles)
    }

    @Test
    fun wheelLandsOnTheSegmentItAwards() {
        val count = 8
        for (index in 0 until count) {
            for (current in listOf(0f, 137f, 1_234.5f, -45f)) {
                for (jitter in listOf(-0.4f, 0f, 0.4f)) {
                    val target = WheelMath.targetRotation(current, index, count, fullSpins = 5, jitter = jitter)
                    assertEquals("index=$index current=$current jitter=$jitter", index, WheelMath.segmentAt(target, count))
                    assertTrue(target >= current + 5 * 360f)
                }
            }
        }
    }
}

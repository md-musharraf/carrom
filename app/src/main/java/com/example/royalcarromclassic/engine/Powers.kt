package com.example.royalcarromclassic.engine

import com.example.royalcarromclassic.data.BoardPower
import com.example.royalcarromclassic.data.CoinPower
import com.example.royalcarromclassic.data.DiceFace
import com.example.royalcarromclassic.data.DicePower
import com.example.royalcarromclassic.data.StrikerAbility
import com.example.royalcarromclassic.data.StrikerConfig
import com.example.royalcarromclassic.engine.CarromPhysicsEngine.PhysicsTuning
import kotlin.random.Random

/**
 * Turns loadout powers into physics and scoring numbers. Pure functions, so every power is easy to
 * reason about and to test.
 *
 * Friction powers scale the friction *loss* per frame (1 − glide factor): 0.75 means a quarter
 * less friction, so a disc glides roughly a third farther.
 */
object Powers {
    const val WIDE_POCKET_BOARD_SCALE = 1.12f
    const val WIDE_POCKET_DICE_SCALE = 1.3f
    const val JUMBO_RADIUS_SCALE = 1.13f
    const val ANCHOR_MASS = 1.35f
    const val MAGNET_PULL_SCALE = 1.9f
    const val POWER_SURGE = 1.3f
    const val MIDAS_COINS = 1.5f
    const val EAGLE_EYE_GUIDE = 1.6f
    const val BASE_GUIDE_BOUNCES = 2

    /** The table itself: board surface and cushions plus the coin set's feel. */
    fun tableTuning(board: BoardPower, coins: CoinPower): PhysicsTuning {
        var t = PhysicsTuning.STANDARD
        t = when (board) {
            BoardPower.TOURNAMENT -> t
            BoardPower.VELVET_TOUCH -> t.scaledFriction(discs = 1.3f, striker = 1.3f)
            BoardPower.WIDE_POCKETS -> t.copy(pocketScale = WIDE_POCKET_BOARD_SCALE)
            BoardPower.LIVELY_CUSHIONS -> t.copy(wallRestitution = 0.95f)
            BoardPower.HYPER_GLIDE -> t.scaledFriction(discs = 0.77f, striker = 0.77f)
        }
        t = when (coins) {
            CoinPower.GLIDE -> t.scaledFriction(discs = 0.75f, striker = 1f)
            CoinPower.MAGNET -> t.copy(discPocketPull = t.discPocketPull * MAGNET_PULL_SCALE)
            else -> t
        }
        return t
    }

    /** The shooter's own striker and die on top of the table. */
    fun shotTuning(table: PhysicsTuning, ability: StrikerAbility, face: DiceFace?): PhysicsTuning {
        var t = table
        if (ability == StrikerAbility.FRICTIONLESS) t = t.scaledFriction(discs = 1f, striker = 0.65f)
        if (face == DiceFace.WIDE_POCKETS) t = t.copy(pocketScale = t.pocketScale * WIDE_POCKET_DICE_SCALE)
        return t
    }

    fun discRadius(coins: CoinPower): Float =
        if (coins == CoinPower.JUMBO) BoardGeometry.PUCK_RADIUS * JUMBO_RADIUS_SCALE else BoardGeometry.PUCK_RADIUS

    fun discMass(coins: CoinPower): Float = if (coins == CoinPower.ANCHOR) ANCHOR_MASS else BoardGeometry.PUCK_MASS

    /** Only the Heavyweight striker plays heavier than regulation. */
    fun strikerMass(striker: StrikerConfig): Float =
        if (striker.ability == StrikerAbility.HEAVYWEIGHT) striker.weight else BoardGeometry.STRIKER_MASS

    fun powerMultiplier(striker: StrikerConfig, face: DiceFace?): Float =
        striker.powerMultiplier * if (face == DiceFace.POWER_SURGE) POWER_SURGE else 1f

    fun guideLength(striker: StrikerConfig, face: DiceFace?): Float =
        CarromPhysicsEngine.DEFAULT_GUIDE_LENGTH * striker.aimGuideLength * if (face == DiceFace.EAGLE_EYE) EAGLE_EYE_GUIDE else 1f

    fun guideBounces(ability: StrikerAbility, face: DiceFace?): Int {
        var bounces = BASE_GUIDE_BOUNCES
        if (ability == StrikerAbility.DRAGON_SIGHT || ability == StrikerAbility.PRECISION) bounces++
        if (face == DiceFace.EAGLE_EYE) bounces += 2
        return bounces
    }

    /** How strongly (degrees) a slingshot pull snaps onto the aim chosen by touching the board. */
    fun aimMagnetDegrees(ability: StrikerAbility): Float = if (ability == StrikerAbility.PRECISION) 9f else 5f

    fun winCoinMultiplier(coins: CoinPower): Float = if (coins == CoinPower.MIDAS) MIDAS_COINS else 1f

    /** Points multiplier for the shot about to be played. */
    fun scoreMultiplier(face: DiceFace?, die: DicePower): Int = when {
        face != DiceFace.DOUBLE -> 1
        die == DicePower.GOLDEN_DOUBLE -> 3
        else -> 2
    }

    /** Rolls [die]: a fair d6, or 2..6 for the No Blanks die. */
    fun roll(die: DicePower, random: Random): DiceFace {
        val faces = DiceFace.entries
        return if (die == DicePower.NO_BLANKS) faces[1 + random.nextInt(faces.size - 1)] else faces[random.nextInt(faces.size)]
    }

    fun rerollsPerMatch(die: DicePower): Int = if (die == DicePower.LUCKY_REROLL) 1 else 0

    private fun PhysicsTuning.scaledFriction(discs: Float, striker: Float) = copy(
        discFriction = 1f - (1f - discFriction) * discs,
        strikerFriction = 1f - (1f - strikerFriction) * striker
    )
}

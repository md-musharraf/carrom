package com.example.royalcarromclassic.data

import androidx.compose.ui.graphics.Color

/*
 * The loadout: striker, coin set ("goti"), dice and board. Every item has one power, stated in
 * plain words on its card. Powers only apply in offline matches with powers switched on; ranked
 * online play, trick shots and Lucky Shot always use the standard table.
 *
 * Board and coin powers change the table itself, so they apply to everyone playing on it.
 * Striker and dice powers belong to the people holding them; bots use the standard striker and a
 * fair die.
 */

/** A striker's signature ability. */
enum class StrikerAbility(val title: String, val summary: String) {
    BALANCED("Balanced", "The tournament standard. No surprises."),
    FIRE_SHOT("Fire Shot", "+15% strike power."),
    DRAGON_SIGHT("Dragon Sight", "Longer guide that follows one extra cushion rebound."),
    HEAVYWEIGHT("Heavyweight", "+25% power and a heavier body that drives through packs."),
    FRICTIONLESS("Frictionless", "Glides a third farther before powder friction stops it."),
    PRECISION("Precision", "+30% power, the longest guide, and a strong aim magnet.")
}

/** A board's character: how its surface, cushions and pockets play. */
enum class BoardPower(val title: String, val summary: String) {
    TOURNAMENT("Tournament", "Regulation surface. Standard glide, cushions and pockets."),
    VELVET_TOUCH("Velvet Touch", "Slower surface: discs stop sooner, for soft control."),
    WIDE_POCKETS("Wide Pockets", "Pockets play 12% wider. Friendly to cuts."),
    LIVELY_CUSHIONS("Lively Cushions", "Springier cushions: rebounds keep more speed."),
    HYPER_GLIDE("Hyper Glide", "Fast polished surface: everything slides farther.")
}

/** A coin set's power over the carrom men. */
enum class CoinPower(val title: String, val summary: String) {
    BALANCED("Balanced", "Regulation weight and size."),
    ANCHOR("Anchor", "35% heavier discs: packs stay tight and resist stray hits."),
    GLIDE("Glide", "Low-friction discs roll a quarter farther after a hit."),
    MAGNET("Magnet", "Pockets pull discs in harder (never the striker)."),
    JUMBO("Jumbo", "13% bigger discs: easier to see and easier to hit."),
    MIDAS("Midas Touch", "Match wins pay 50% more coins.")
}

/** A die's power in Dice Carrom. */
enum class DicePower(val title: String, val summary: String) {
    FAIR("Fair Roll", "Every face equally likely."),
    LUCKY_REROLL("Second Chance", "One free re-roll each match."),
    NO_BLANKS("No Blanks", "Never lands on Steady: every roll grants a power."),
    GOLDEN_DOUBLE("Golden Double", "The Double face pays triple instead.")
}

/** The six faces of the die in Dice Carrom, each a power for the shot about to be played. */
enum class DiceFace(val pips: Int, val title: String, val summary: String) {
    STEADY(1, "Steady", "A plain shot."),
    DOUBLE(2, "Double Points", "Points from this shot count double."),
    EAGLE_EYE(3, "Eagle Eye", "A long guide that follows extra rebounds."),
    WIDE_POCKETS(4, "Wide Pockets", "Pockets play 30% wider for this shot."),
    POWER_SURGE(5, "Power Surge", "+30% strike power for this shot."),
    BONUS_TURN(6, "Bonus Turn", "Shoot again even if nothing drops (not after a foul).")
}

/** A set of carrom men: its look and its power. */
data class CoinSet(
    val id: String,
    val name: String,
    val description: String,
    val price: Int,
    val isUnlocked: Boolean = false,
    val power: CoinPower,
    val whiteFace: Color,
    val whiteEdge: Color,
    val blackFace: Color,
    val blackEdge: Color,
    val queenFace: Color,
    val queenEdge: Color
)

/** A die: its look and its power. */
data class DiceSkin(
    val id: String,
    val name: String,
    val description: String,
    val price: Int,
    val isUnlocked: Boolean = false,
    val power: DicePower,
    val body: Color,
    val edge: Color,
    val pip: Color
)

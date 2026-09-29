package com.example.royalcarromclassic.data

import androidx.compose.ui.graphics.Color

enum class PieceType {
    STRIKER,
    WHITE,
    BLACK,
    QUEEN
}

enum class GameMode {
    VS_AI,
    CLASSIC,

    /** Each side owns a colour and races to pocket all of it, the queen covered first. */
    DISC_POOL,
    FREESTYLE,
    TRICK_SHOTS,

    /** Local party play: two to four seats, each a person or a bot, optionally as doubles. */
    PASS_AND_PLAY,
    PRACTICE,

    /** Race the bot to 120 points with a ten-second shot clock. */
    BLITZ,

    /** Daily mini-game: send the lucky disc into the prize rings. */
    LUCKY_SHOT,

    /** A server-authoritative match against another player. */
    ONLINE,

    /** Roll the die before every shot; each face grants a one-shot power. */
    DICE,

    /** Solo: pocket as many points as possible before the 90-second clock runs out. */
    TIME_ATTACK
}

enum class AIDifficulty {
    EASY,
    MEDIUM,
    HARD
}

enum class TurnState {
    PLACING_STRIKER,
    AIMING,
    MOVING
}

/**
 * The seats at the table, in the order play passes (counter-clockwise, to the shooter's right).
 * Each seat shoots from the baseline on its own side of the board.
 */
enum class Seat(
    /** True when the seat's baseline runs horizontally (bottom and top). */
    val horizontal: Boolean,
    /** Unit vector pointing from the board towards the player sitting here ("backwards"). */
    val outwardX: Float,
    val outwardY: Float
) {
    BOTTOM(true, 0f, 1f),
    RIGHT(false, 1f, 0f),
    TOP(true, 0f, -1f),
    LEFT(false, -1f, 0f);

    companion object {
        /** Where [count] players sit: two face each other; four take every side. */
        fun layoutFor(count: Int): List<Seat> = when (count) {
            1 -> listOf(BOTTOM)
            2 -> listOf(BOTTOM, TOP)
            3 -> listOf(BOTTOM, RIGHT, LEFT)
            else -> listOf(BOTTOM, RIGHT, TOP, LEFT)
        }
    }
}

/** Up to four players. Player 1 always sits at the bottom. */
enum class PlayerSlot {
    PLAYER1,
    PLAYER2,
    PLAYER3,
    PLAYER4;

    val index: Int get() = ordinal

    /** The other player of a two-player match. */
    val opponent: PlayerSlot get() = if (this == PLAYER1) PLAYER2 else PLAYER1

    /** The next player in turn order among [count] players. */
    fun next(count: Int): PlayerSlot = entries[(ordinal + 1) % count.coerceIn(1, entries.size)]

    companion object {
        fun of(index: Int): PlayerSlot = entries[index.coerceIn(0, entries.size - 1)]
    }
}

data class Vector2D(
    val x: Float,
    val y: Float
) {
    operator fun plus(other: Vector2D) = Vector2D(x + other.x, y + other.y)
    operator fun minus(other: Vector2D) = Vector2D(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Vector2D(x * scalar, y * scalar)
    operator fun div(scalar: Float) = if (scalar != 0f) Vector2D(x / scalar, y / scalar) else Vector2D(0f, 0f)
    fun length(): Float = kotlin.math.hypot(x, y)
    fun normalized(): Vector2D {
        val len = length()
        return if (len > 0f) Vector2D(x / len, y / len) else Vector2D(0f, 0f)
    }
}

/**
 * A physical disc on the board. Intentionally mutable: the physics engine integrates
 * positions and velocities in place every frame without allocating.
 */
data class Piece(
    val id: String,
    val type: PieceType,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    val radius: Float,
    val mass: Float,
    var isPocketed: Boolean = false,
    /** 1 → 0 while the disc drops into its pocket; 0 once it has disappeared. */
    var pocketProgress: Float = 1.0f,
    /** Index into BoardGeometry.POCKETS of the pocket the disc fell into, or -1. */
    var pocketId: Int = -1,
    val primaryColor: Color,
    val borderColor: Color,
    val points: Int
)

data class Pocket(
    val id: Int,
    val x: Float,
    val y: Float,
    val radius: Float,
    val name: String
)

data class StrikerConfig(
    val id: String,
    val name: String,
    val description: String,
    val price: Int,
    val isUnlocked: Boolean = false,
    val primaryColor: Color,
    val secondaryColor: Color,
    val glowColor: Color,
    val powerMultiplier: Float = 1.0f,
    val aimGuideLength: Float = 1.0f,
    /** The striker's mass in offline play (online matches always use the standard mass). */
    val weight: Float = 3.0f,
    val trailColor: Color = Color(0xFFFEF08A),
    val ability: StrikerAbility = StrikerAbility.BALANCED
)

data class BoardTheme(
    val id: String,
    val name: String,
    val description: String,
    val price: Int,
    val isUnlocked: Boolean = false,
    val woodColor: Color,
    val woodInner: Color,
    val feltColor: Color,
    val feltPatternColor: Color,
    val centerCircleColor: Color,
    val pocketRimColor: Color,
    val accentGold: Color,
    val power: BoardPower = BoardPower.TOURNAMENT
)

data class TrickShotLevel(
    val id: Int,
    val title: String,
    val description: String,
    val targetPockets: List<Int>,
    val pieces: List<Pair<PieceType, Vector2D>>,
    val strikerPos: Vector2D,
    val maxShots: Int,
    val stars: Int = 0,
    val isUnlocked: Boolean = false,
    val hint: String
)

data class PlayerStats(
    val coins: Int = 1200,
    val gems: Int = 25,
    val level: Int = 1,
    val xp: Int = 0,
    val xpToNextLevel: Int = 100,
    val matchesPlayed: Int = 0,
    val matchesWon: Int = 0,
    val totalPockets: Int = 0,
    val queenCovers: Int = 0,
    val trickShotsCompleted: Int = 0
)

data class PlayerData(
    val name: String,
    /** Short engraving shown on the player's medallion, e.g. "P1" or "AI". */
    val monogram: String,
    val score: Int = 0,
    /** The colour this player must pocket (Disc Pool), or null in points games. */
    val assignedColor: PieceType? = null,
    val fouls: Int = 0,
    /** True when the bot plays this seat. */
    val isBot: Boolean = false,
    /** 0 or 1 in doubles (partners sit opposite each other), otherwise -1. */
    val team: Int = -1
)

/** One seat in the local match set-up sheet. */
data class SeatSetup(val name: String, val isBot: Boolean)

/** A local match as chosen on the set-up sheet. */
data class MatchConfig(
    val mode: GameMode,
    val seats: List<SeatSetup>,
    val difficulty: AIDifficulty = AIDifficulty.MEDIUM,
    /** Four players as two teams of partners sitting opposite each other. */
    val doubles: Boolean = false
)

/** Progress through the current trick shot challenge. */
data class TrickShotStatus(
    val levelId: Int,
    val title: String,
    val hint: String,
    val shotsTaken: Int,
    val maxShots: Int
) {
    val shotsLeft: Int get() = (maxShots - shotsTaken).coerceAtLeast(0)
}

/** Discs still in play, for the scoreboard. */
data class BoardSummary(
    val whites: Int = 0,
    val blacks: Int = 0,
    val queenOnBoard: Boolean = false
)

/** A running shot clock: the turn ends at [deadlineMillis] (device clock) after [totalSeconds]. */
data class TurnClock(val deadlineMillis: Long, val totalSeconds: Float)

/** Today's Lucky Shot session. */
data class LuckyShotStatus(
    val attemptsLeft: Int,
    val coinsWon: Int = 0,
    /** Prize of the most recent attempt, or null before the first one. */
    val lastPrize: Int? = null
)

/** Dice Carrom: the current shooter's roll. */
data class DiceStatus(
    /** The face rolled for this turn, or null until the shooter rolls. */
    val face: DiceFace? = null,
    /** True while the die tumbles. */
    val rolling: Boolean = false,
    /** Re-rolls the human players have left this match (Lucky Clover die). */
    val rerollsLeft: Int = 0,
    /** Changes whenever a roll lands, so the UI can replay its landing animation. */
    val rollId: Int = 0
)

/** Time Attack: the clock, the points banked and the best run so far. */
data class TimeAttackStatus(
    val deadlineMillis: Long,
    val totalSeconds: Float,
    val best: Int,
    val pocketed: Int = 0
)

/** What the local player earned when an online match ended. */
data class OnlineResult(
    val reason: String,
    val coins: Int,
    val xp: Int,
    val ratingBefore: Int,
    val ratingAfter: Int,
    val leveledUp: Boolean
) {
    val ratingChange: Int get() = ratingAfter - ratingBefore
}

/** A quick-chat emote shown beside a player's seat. [id] distinguishes repeats of the same emote. */
data class EmoteBubble(val slot: PlayerSlot, val emote: String, val id: Long)

/** Live details of an online match. The local player is always [PlayerSlot.PLAYER1] (bottom). */
data class OnlineMatchStatus(
    val matchId: String,
    val ranked: Boolean,
    val arena: String?,
    val myRating: Int,
    val opponentRating: Int,
    val opponentPlayerId: String,
    val opponentConnected: Boolean = true,
    /** True while waiting for the server to confirm the shot just played. */
    val syncing: Boolean = false,
    val emote: EmoteBubble? = null,
    val result: OnlineResult? = null
)

data class GameState(
    val mode: GameMode = GameMode.VS_AI,
    val aiDifficulty: AIDifficulty = AIDifficulty.MEDIUM,
    val isGameOver: Boolean = false,
    val winner: PlayerSlot? = null,

    val currentTurn: PlayerSlot = PlayerSlot.PLAYER1,
    val turnState: TurnState = TurnState.PLACING_STRIKER,

    /** Everyone at the table, in turn order; player 1 sits at the bottom. */
    val players: List<PlayerData> = listOf(
        PlayerData("Player 1", "P1"),
        PlayerData("Bot Master", "AI", isBot = true)
    ),
    /** Where each of [players] sits. */
    val seats: List<Seat> = Seat.layoutFor(players.size),

    val queenPottedBy: PlayerSlot? = null,
    val queenNeedsCover: Boolean = false,
    val queenCovered: Boolean = false,

    /** Non-null while playing a trick shot challenge. */
    val trickShot: TrickShotStatus? = null,

    /** Non-null while playing Lucky Shot. */
    val luckyShot: LuckyShotStatus? = null,

    /** Non-null during an online match. */
    val online: OnlineMatchStatus? = null,

    /** Non-null in Dice Carrom. */
    val dice: DiceStatus? = null,

    /** Non-null in Time Attack. */
    val timeAttack: TimeAttackStatus? = null,

    /** The current turn's shot clock (Blitz and online play), or null when untimed. */
    val turnClock: TurnClock? = null,

    /** Coins paid out when this match ended (0 until then). */
    val matchCoins: Int = 0,

    /** Shots played so far this match (a match with none can simply be restarted). */
    val shotsPlayed: Int = 0,

    /** True when the equipped striker, coins, board and dice apply their powers this match. */
    val powersActive: Boolean = false,

    val strikerBaselineOffset: Float = 0.5f,
    val strikerAimAngle: Float = -kotlin.math.PI.toFloat() / 2f, // pointing up
    val strikerPower: Float = 50f,

    val selectedStrikerId: String = "classic_ivory",
    val selectedBoardId: String = "classic_teak",
    val selectedCoinSetId: String = "heritage_boxwood",
    val selectedDiceId: String = "ivory_die",
    /** The player's preference: powers on in offline matches. */
    val powersEnabled: Boolean = true,

    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,

    val toastMessage: String? = null
) {
    val player1: PlayerData get() = players[0]
    val player2: PlayerData get() = players.getOrElse(1) { PlayerData("Player 2", "P2") }

    val playerCount: Int get() = players.size

    /** Two teams of partners sitting opposite each other. */
    val isDoubles: Boolean get() = players.any { it.team >= 0 }

    /** The seat of whoever shoots now. */
    val currentSeat: Seat get() = seatOf(currentTurn)

    fun seatOf(slot: PlayerSlot): Seat = seats.getOrElse(slot.index) { Seat.BOTTOM }

    /** True while the bot controls the current shot. */
    val isAiTurn: Boolean
        get() = mode != GameMode.ONLINE && players.getOrNull(currentTurn.index)?.isBot == true

    /** True while the online opponent is shooting; their aim arrives over the network. */
    val isRemoteTurn: Boolean get() = mode == GameMode.ONLINE && currentTurn == PlayerSlot.PLAYER2

    /** Player 1 shoots from the bottom baseline, player 2 (or the bot) from the top. */
    val isBottomTurn: Boolean get() = currentSeat == Seat.BOTTOM

    /** True in Dice Carrom until the shooter has rolled for this turn. */
    val needsRoll: Boolean get() = dice != null && (dice.face == null || dice.rolling)

    /** True when a human may place, aim or fire the striker. */
    val canAim: Boolean
        get() = !isGameOver && !isAiTurn && !isRemoteTurn && turnState != TurnState.MOVING &&
            online?.syncing != true && !needsRoll

    fun player(slot: PlayerSlot): PlayerData = players.getOrElse(slot.index) { player2 }

    /** Team scores in doubles (index 0 and 1), empty otherwise. */
    val teamScores: List<Int>
        get() = if (!isDoubles) emptyList() else listOf(0, 1).map { t -> players.filter { it.team == t }.sumOf { it.score } }

    /** A copy with [slot]'s data replaced. */
    fun withPlayer(slot: PlayerSlot, data: PlayerData): GameState =
        copy(players = players.mapIndexed { i, p -> if (i == slot.index) data else p })
}

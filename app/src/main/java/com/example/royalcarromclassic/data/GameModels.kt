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
    DISC_POOL,
    FREESTYLE,
    TRICK_SHOTS,
    PASS_AND_PLAY,
    PRACTICE
}

enum class AIDifficulty {
    EASY,
    MEDIUM,
    HARD
}

enum class TurnState {
    PLACING_STRIKER,
    AIMING,
    SHOOTING,
    MOVING,
    TURN_EVALUATING
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
    var pocketProgress: Float = 1.0f, // 1.0f -> 0.0f shrinking into pocket
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
    val weight: Float = 3.0f,
    val trailColor: Color = Color(0xFFFEF08A)
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
    val accentGold: Color
)

data class TrickShotLevel(
    val id: Int,
    val title: String,
    val description: String,
    val targetPockets: List<Int>,
    val pieces: List<Pair<PieceType, Vector2D>>,
    val strikerPos: Vector2D,
    val maxShots: Int,
    var stars: Int = 0,
    var isUnlocked: Boolean = false,
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
    val avatar: String,
    var score: Int = 0,
    var assignedColor: PieceType? = null,
    var fouls: Int = 0
)

data class GameState(
    val mode: GameMode = GameMode.VS_AI,
    val aiDifficulty: AIDifficulty = AIDifficulty.MEDIUM,
    val isPaused: Boolean = false,
    val isGameOver: Boolean = false,
    val winner: String? = null,
    
    val currentTurn: String = "player1", // "player1", "player2", "ai"
    val turnState: TurnState = TurnState.PLACING_STRIKER,
    val turnTimer: Int = 30,
    
    val player1: PlayerData = PlayerData("Player 1", "👑"),
    val player2: PlayerData = PlayerData("Bot Master", "🤖"),
    
    val queenPottedBy: String? = null,
    val queenNeedsCover: Boolean = false,
    val queenCovered: Boolean = false,
    
    val strikerBaselineOffset: Float = 0.5f,
    val strikerAimAngle: Float = -kotlin.math.PI.toFloat() / 2f, // pointing up
    val strikerPower: Float = 50f,
    
    val selectedStrikerId: String = "classic_ivory",
    val selectedBoardId: String = "classic_teak",
    
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
    
    val toastMessage: String? = null
)

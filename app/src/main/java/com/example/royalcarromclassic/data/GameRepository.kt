package com.example.royalcarromclassic.data

/**
 * Interface defining the persistence contract for player progress, settings, and unlockables.
 * Follows the Dependency Inversion Principle (DIP).
 */
interface GameRepository {
    fun getPlayerStats(): PlayerStats
    fun savePlayerStats(stats: PlayerStats)
    fun isUnlocked(itemId: String, defaultUnlocked: Boolean): Boolean
    fun setUnlocked(itemId: String, unlocked: Boolean)
    fun getSelectedStriker(): String
    fun setSelectedStriker(id: String)
    fun getSelectedBoard(): String
    fun setSelectedBoard(id: String)
    fun getTrickShotStars(levelId: Int): Int
    fun setTrickShotStars(levelId: Int, stars: Int)

    /** How many times [counter] was used on [day]; 0 on any other day. */
    fun getDailyCount(counter: String, day: Long): Int
    fun setDailyCount(counter: String, day: Long, count: Int)
}

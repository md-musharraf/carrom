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

    /** The equipped item of a loadout [category] (e.g. "coins" or "dice"), or [default]. */
    fun getSelection(category: String, default: String): String
    fun setSelection(category: String, id: String)

    /** A stored on/off preference, e.g. whether loadout powers are on. */
    fun getFlag(key: String, default: Boolean): Boolean
    fun setFlag(key: String, value: Boolean)

    /** A personal best, e.g. the Time Attack record; 0 before the first run. */
    fun getBest(key: String): Int
    fun setBest(key: String, value: Int)
}

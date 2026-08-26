package com.example.royalcarromclassic.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Implementation of GameRepository backed by Android SharedPreferences.
 */
class PreferencesManager(context: Context) : GameRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences("royal_carrom_prefs", Context.MODE_PRIVATE)

    override fun getPlayerStats(): PlayerStats {
        return PlayerStats(
            coins = prefs.getInt("coins", 1200),
            gems = prefs.getInt("gems", 25),
            level = prefs.getInt("level", 1),
            xp = prefs.getInt("xp", 0),
            xpToNextLevel = prefs.getInt("xpToNextLevel", 100),
            matchesPlayed = prefs.getInt("matchesPlayed", 0),
            matchesWon = prefs.getInt("matchesWon", 0),
            totalPockets = prefs.getInt("totalPockets", 0),
            queenCovers = prefs.getInt("queenCovers", 0),
            trickShotsCompleted = prefs.getInt("trickShotsCompleted", 0)
        )
    }

    override fun savePlayerStats(stats: PlayerStats) {
        prefs.edit()
            .putInt("coins", stats.coins)
            .putInt("gems", stats.gems)
            .putInt("level", stats.level)
            .putInt("xp", stats.xp)
            .putInt("xpToNextLevel", stats.xpToNextLevel)
            .putInt("matchesPlayed", stats.matchesPlayed)
            .putInt("matchesWon", stats.matchesWon)
            .putInt("totalPockets", stats.totalPockets)
            .putInt("queenCovers", stats.queenCovers)
            .putInt("trickShotsCompleted", stats.trickShotsCompleted)
            .apply()
    }

    override fun isUnlocked(itemId: String, defaultUnlocked: Boolean): Boolean {
        return prefs.getBoolean("unlocked_$itemId", defaultUnlocked)
    }

    override fun setUnlocked(itemId: String, unlocked: Boolean) {
        prefs.edit().putBoolean("unlocked_$itemId", unlocked).apply()
    }

    override fun getSelectedStriker(): String {
        return prefs.getString("selected_striker", "classic_ivory") ?: "classic_ivory"
    }

    override fun setSelectedStriker(id: String) {
        prefs.edit().putString("selected_striker", id).apply()
    }

    override fun getSelectedBoard(): String {
        return prefs.getString("selected_board", "classic_teak") ?: "classic_teak"
    }

    override fun setSelectedBoard(id: String) {
        prefs.edit().putString("selected_board", id).apply()
    }

    override fun getTrickShotStars(levelId: Int): Int {
        return prefs.getInt("trick_stars_$levelId", 0)
    }

    override fun setTrickShotStars(levelId: Int, stars: Int) {
        val current = getTrickShotStars(levelId)
        if (stars > current) {
            prefs.edit().putInt("trick_stars_$levelId", stars).apply()
        }
    }
}

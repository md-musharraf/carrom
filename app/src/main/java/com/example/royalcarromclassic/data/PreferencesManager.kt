package com.example.royalcarromclassic.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.royalcarromclassic.core.logging.AppLogger

/**
 * Secure implementation of GameRepository backed by EncryptedSharedPreferences.
 * Prevents player data tampering (coin editing, score manipulation) via AES-256 encryption.
 * Falls back to standard SharedPreferences if encryption is unavailable on the device.
 */
class PreferencesManager(context: Context) : GameRepository {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "royal_carrom_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        AppLogger.w("PreferencesManager", { "Encrypted prefs unavailable, using standard prefs" }, e)
        context.getSharedPreferences("royal_carrom_prefs", Context.MODE_PRIVATE)
    }

    override fun getPlayerStats(): PlayerStats {
        return PlayerStats(
            coins = prefs.getInt("coins", 1200).coerceAtLeast(0),
            gems = prefs.getInt("gems", 25).coerceAtLeast(0),
            level = prefs.getInt("level", 1).coerceIn(1, 999),
            xp = prefs.getInt("xp", 0).coerceAtLeast(0),
            xpToNextLevel = prefs.getInt("xpToNextLevel", 100).coerceIn(50, 100_000),
            matchesPlayed = prefs.getInt("matchesPlayed", 0).coerceAtLeast(0),
            matchesWon = prefs.getInt("matchesWon", 0).coerceAtLeast(0),
            totalPockets = prefs.getInt("totalPockets", 0).coerceAtLeast(0),
            queenCovers = prefs.getInt("queenCovers", 0).coerceAtLeast(0),
            trickShotsCompleted = prefs.getInt("trickShotsCompleted", 0).coerceAtLeast(0)
        )
    }

    override fun savePlayerStats(stats: PlayerStats) {
        // Validate before persisting to prevent corrupted data
        val validatedStats = stats.copy(
            coins = stats.coins.coerceIn(0, 999_999),
            gems = stats.gems.coerceIn(0, 99_999),
            level = stats.level.coerceIn(1, 999),
            xp = stats.xp.coerceAtLeast(0),
            xpToNextLevel = stats.xpToNextLevel.coerceIn(50, 100_000),
            matchesPlayed = stats.matchesPlayed.coerceAtLeast(0),
            matchesWon = stats.matchesWon.coerceIn(0, stats.matchesPlayed.coerceAtLeast(0))
        )

        prefs.edit()
            .putInt("coins", validatedStats.coins)
            .putInt("gems", validatedStats.gems)
            .putInt("level", validatedStats.level)
            .putInt("xp", validatedStats.xp)
            .putInt("xpToNextLevel", validatedStats.xpToNextLevel)
            .putInt("matchesPlayed", validatedStats.matchesPlayed)
            .putInt("matchesWon", validatedStats.matchesWon)
            .putInt("totalPockets", validatedStats.totalPockets.coerceAtLeast(0))
            .putInt("queenCovers", validatedStats.queenCovers.coerceAtLeast(0))
            .putInt("trickShotsCompleted", validatedStats.trickShotsCompleted.coerceAtLeast(0))
            .apply()
    }

    override fun isUnlocked(itemId: String, defaultUnlocked: Boolean): Boolean {
        if (itemId.isBlank()) return defaultUnlocked
        return prefs.getBoolean("unlocked_$itemId", defaultUnlocked)
    }

    override fun setUnlocked(itemId: String, unlocked: Boolean) {
        if (itemId.isBlank()) return
        prefs.edit().putBoolean("unlocked_$itemId", unlocked).apply()
    }

    override fun getSelectedStriker(): String {
        return prefs.getString("selected_striker", "classic_ivory") ?: "classic_ivory"
    }

    override fun setSelectedStriker(id: String) {
        if (id.isBlank()) return
        prefs.edit().putString("selected_striker", id).apply()
    }

    override fun getSelectedBoard(): String {
        return prefs.getString("selected_board", "classic_teak") ?: "classic_teak"
    }

    override fun setSelectedBoard(id: String) {
        if (id.isBlank()) return
        prefs.edit().putString("selected_board", id).apply()
    }

    override fun getTrickShotStars(levelId: Int): Int {
        if (levelId < 1) return 0
        return prefs.getInt("trick_stars_$levelId", 0).coerceIn(0, 3)
    }

    override fun setTrickShotStars(levelId: Int, stars: Int) {
        if (levelId < 1) return
        val validStars = stars.coerceIn(0, 3)
        val current = getTrickShotStars(levelId)
        if (validStars > current) {
            prefs.edit().putInt("trick_stars_$levelId", validStars).apply()
        }
    }

    override fun getDailyCount(counter: String, day: Long): Int {
        if (prefs.getLong("daily_${counter}_day", -1L) != day) return 0
        return prefs.getInt("daily_${counter}_count", 0).coerceAtLeast(0)
    }

    override fun setDailyCount(counter: String, day: Long, count: Int) {
        if (counter.isBlank()) return
        prefs.edit()
            .putLong("daily_${counter}_day", day)
            .putInt("daily_${counter}_count", count.coerceAtLeast(0))
            .apply()
    }

    override fun getSelection(category: String, default: String): String =
        prefs.getString("selected_$category", default) ?: default

    override fun setSelection(category: String, id: String) {
        if (category.isBlank() || id.isBlank()) return
        prefs.edit().putString("selected_$category", id).apply()
    }

    override fun getFlag(key: String, default: Boolean): Boolean = prefs.getBoolean("flag_$key", default)

    override fun setFlag(key: String, value: Boolean) {
        if (key.isBlank()) return
        prefs.edit().putBoolean("flag_$key", value).apply()
    }

    override fun getBest(key: String): Int = prefs.getInt("best_$key", 0).coerceAtLeast(0)

    override fun setBest(key: String, value: Int) {
        if (key.isBlank()) return
        prefs.edit().putInt("best_$key", value.coerceIn(0, 999_999)).apply()
    }
}

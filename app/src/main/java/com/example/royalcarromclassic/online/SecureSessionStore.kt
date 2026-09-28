package com.example.royalcarromclassic.online

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.royalcarromclassic.core.logging.AppLogger
import kotlinx.serialization.SerializationException

/**
 * Keeps the signed-in session in EncryptedSharedPreferences (AES-256, key in the Android
 * Keystore), so refresh tokens never sit on disk in plain text. Backups are disabled in the
 * manifest, so the session never leaves the device either.
 */
class SecureSessionStore(context: Context) : SessionStore {

    private val prefs: SharedPreferences? = try {
        EncryptedSharedPreferences.create(
            context,
            FILE,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Without the Keystore we'd rather sign in again each launch than store tokens in the clear.
        AppLogger.w(TAG, { "Encrypted storage unavailable; the session won't persist" }, e)
        null
    }

    override fun load(): StoredSession? {
        val raw = prefs?.getString(KEY, null) ?: return null
        return try {
            OnlineJson.decodeFromString(StoredSession.serializer(), raw)
        } catch (e: SerializationException) {
            clear()
            null
        }
    }

    override fun save(session: StoredSession) {
        prefs?.edit()?.putString(KEY, OnlineJson.encodeToString(StoredSession.serializer(), session))?.apply()
    }

    override fun clear() {
        prefs?.edit()?.remove(KEY)?.apply()
    }

    private companion object {
        const val TAG = "SecureSessionStore"
        const val FILE = "royal_carrom_session"
        const val KEY = "session"
    }
}

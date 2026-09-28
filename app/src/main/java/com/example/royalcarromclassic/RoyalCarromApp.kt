package com.example.royalcarromclassic

import android.app.Application
import com.example.royalcarromclassic.online.OnlineHost
import com.example.royalcarromclassic.online.OnlineServices
import com.example.royalcarromclassic.online.SecureSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Owns the app-wide online stack, so the realtime connection outlives screen rotations. */
class RoyalCarromApp : Application(), OnlineHost {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val online: OnlineServices by lazy {
        OnlineServices(BuildConfig.API_BASE_URL, SecureSessionStore(this), appScope)
    }
}

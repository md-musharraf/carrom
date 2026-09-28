package com.example.royalcarromclassic.ui

import com.example.royalcarromclassic.online.SocialAuthProvider

/** Platform features the shared UI needs, supplied by the Activity. */
interface PlatformBridge {
    /** Google and Facebook sign-in, or null where they aren't available. */
    val socialAuth: SocialAuthProvider?

    /** Opens the system share sheet with [text]. */
    fun shareText(text: String)
}

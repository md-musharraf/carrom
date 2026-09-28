package com.example.royalcarromclassic.online

/** Obtains identity tokens from the platform's Google and Facebook sign-in flows. */
interface SocialAuthProvider {
    /** A Google ID token for the server's web client ID. */
    suspend fun googleIdToken(): String

    /** A Facebook user access token. */
    suspend fun facebookAccessToken(): String
}

/** Sign-in didn't produce a token. [cancelled] is true when the player simply backed out. */
class SocialAuthException(message: String, val cancelled: Boolean = false, cause: Throwable? = null) :
    Exception(message, cause)

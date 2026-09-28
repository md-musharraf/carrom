package com.example.royalcarromclassic.online

import androidx.activity.ComponentActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.royalcarromclassic.BuildConfig
import com.example.royalcarromclassic.R
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.FacebookSdk
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google sign-in through Credential Manager (the "Sign in with Google" sheet) and Facebook Login.
 * Both return tokens that the server verifies itself; nothing here is trusted on its own.
 */
class AndroidSocialAuth(private val activity: ComponentActivity) : SocialAuthProvider {

    private val facebookCallbacks: CallbackManager = CallbackManager.Factory.create()

    override suspend fun googleIdToken(): String {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (clientId.isBlank()) throw SocialAuthException("Google sign-in isn't set up in this build")

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        val credential = try {
            CredentialManager.create(activity).getCredential(activity, request).credential
        } catch (e: GetCredentialCancellationException) {
            throw SocialAuthException("Sign-in cancelled", cancelled = true, cause = e)
        } catch (e: GetCredentialException) {
            throw SocialAuthException("Google sign-in failed. Please try again.", cause = e)
        }

        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw SocialAuthException("Google sign-in returned an unexpected credential")
        }
        return try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            throw SocialAuthException("Google sign-in failed. Please try again.", cause = e)
        }
    }

    override suspend fun facebookAccessToken(): String {
        if (activity.getString(R.string.facebook_app_id) == UNCONFIGURED_APP_ID) {
            throw SocialAuthException("Facebook sign-in isn't set up in this build")
        }
        // Auto-initialisation is off in the manifest (no app events until the player opts in).
        if (!FacebookSdk.isFullyInitialized()) {
            FacebookSdk.setAutoInitEnabled(true)
            FacebookSdk.fullyInitialize()
        }

        val manager = LoginManager.getInstance()
        return suspendCancellableCoroutine { continuation ->
            manager.registerCallback(facebookCallbacks, object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    manager.unregisterCallback(facebookCallbacks)
                    if (continuation.isActive) continuation.resume(result.accessToken.token)
                }

                override fun onCancel() {
                    manager.unregisterCallback(facebookCallbacks)
                    if (continuation.isActive) continuation.resumeWithException(SocialAuthException("Sign-in cancelled", cancelled = true))
                }

                override fun onError(error: FacebookException) {
                    manager.unregisterCallback(facebookCallbacks)
                    if (continuation.isActive) {
                        continuation.resumeWithException(SocialAuthException("Facebook sign-in failed. Please try again.", cause = error))
                    }
                }
            })
            continuation.invokeOnCancellation { manager.unregisterCallback(facebookCallbacks) }
            manager.logInWithReadPermissions(activity, facebookCallbacks, listOf("public_profile"))
        }
    }

    private companion object {
        const val UNCONFIGURED_APP_ID = "0"
    }
}

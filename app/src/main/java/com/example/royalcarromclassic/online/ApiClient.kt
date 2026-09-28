package com.example.royalcarromclassic.online

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Shared JSON settings: tolerant of new server fields, compact on the wire. */
val OnlineJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** A failed API call. [code] is the server's stable error code (e.g. "insufficient_funds"). */
class ApiException(val status: Int, val code: String, message: String) : Exception(message) {
    val isNetwork: Boolean get() = status == NETWORK_ERROR

    companion object {
        const val NETWORK_ERROR = -1
    }
}

/** Tokens persisted between launches. */
@Serializable
data class StoredSession(
    val accessToken: String,
    val accessTokenExpiresAt: Long,
    val refreshToken: String,
    val refreshTokenExpiresAt: Long,
    val playerId: String
)

interface SessionStore {
    fun load(): StoredSession?
    fun save(session: StoredSession)
    fun clear()
}

class InMemorySessionStore(private var session: StoredSession? = null) : SessionStore {
    override fun load() = session
    override fun save(session: StoredSession) {
        this.session = session
    }
    override fun clear() {
        session = null
    }
}

@Serializable
private data class RefreshBody(val refreshToken: String)

private data class HttpResult(val status: Int, val body: String)

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
private const val REFRESH_MARGIN_MS = 30_000L

fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .build()

/**
 * JSON client for the Royal Carrom API. Attaches the access token, refreshes it before it expires
 * (single flight, so concurrent calls never race a rotating refresh token) and turns error
 * envelopes into [ApiException].
 */
class ApiClient(
    baseUrl: String,
    private val store: SessionStore,
    private val http: OkHttpClient = defaultHttpClient(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val json: Json = OnlineJson
) {
    val baseUrl: String = baseUrl.trimEnd('/')
    private val refreshMutex = Mutex()
    private val _session = MutableStateFlow(store.load())

    /** The signed-in session, or null when signed out. */
    val session: StateFlow<StoredSession?> = _session.asStateFlow()

    suspend fun <T> get(path: String, response: KSerializer<T>): T =
        execute("GET", path, null, response, authenticated = true)

    suspend fun <B, T> post(
        path: String,
        body: B,
        bodySerializer: KSerializer<B>,
        response: KSerializer<T>,
        authenticated: Boolean = true
    ): T = execute("POST", path, json.encodeToString(bodySerializer, body), response, authenticated)

    suspend fun <T> post(path: String, response: KSerializer<T>): T =
        execute("POST", path, "{}", response, authenticated = true)

    suspend fun <B, T> patch(path: String, body: B, bodySerializer: KSerializer<B>, response: KSerializer<T>): T =
        execute("PATCH", path, json.encodeToString(bodySerializer, body), response, authenticated = true)

    suspend fun <B> postNoContent(path: String, body: B, bodySerializer: KSerializer<B>, authenticated: Boolean = true) {
        execute<Unit>("POST", path, json.encodeToString(bodySerializer, body), null, authenticated)
    }

    suspend fun postNoContent(path: String) {
        execute<Unit>("POST", path, "{}", null, authenticated = true)
    }

    suspend fun delete(path: String) {
        execute<Unit>("DELETE", path, null, null, authenticated = true)
    }

    /** Stores the tokens of a successful sign-in. */
    fun adopt(auth: AuthResponse) {
        val session = StoredSession(
            accessToken = auth.accessToken,
            accessTokenExpiresAt = auth.accessTokenExpiresAt,
            refreshToken = auth.refreshToken,
            refreshTokenExpiresAt = auth.refreshTokenExpiresAt,
            playerId = auth.user.playerId
        )
        store.save(session)
        _session.value = session
    }

    fun signOutLocally() {
        store.clear()
        _session.value = null
    }

    /** A usable access token, refreshing it first if it is about to expire. */
    suspend fun accessToken(): String {
        val current = _session.value ?: throw signedOut()
        if (current.accessTokenExpiresAt - clock() > REFRESH_MARGIN_MS) return current.accessToken
        return refresh(rejected = current.accessToken)
    }

    /** Forces a refresh after the server rejected [rejected] (e.g. a socket handshake). */
    suspend fun refresh(rejected: String?): String = refreshMutex.withLock {
        val current = _session.value ?: throw signedOut()
        // Another caller already refreshed while we waited for the lock.
        if (current.accessToken != rejected && current.accessTokenExpiresAt - clock() > REFRESH_MARGIN_MS) {
            return@withLock current.accessToken
        }
        val result = call("POST", "/v1/auth/refresh", json.encodeToString(RefreshBody.serializer(), RefreshBody(current.refreshToken)), null)
        if (result.status !in 200..299) {
            if (result.status == 401) signOutLocally()
            throw toException(result)
        }
        val auth = decode(AuthResponse.serializer(), result)
        adopt(auth)
        auth.accessToken
    }

    private suspend fun <T> execute(
        method: String,
        path: String,
        body: String?,
        response: KSerializer<T>?,
        authenticated: Boolean
    ): T {
        val token = if (authenticated) accessToken() else null
        var result = call(method, path, body, token)
        if (result.status == 401 && authenticated) {
            val fresh = refresh(rejected = token)
            result = call(method, path, body, fresh)
        }
        if (result.status !in 200..299) throw toException(result)
        @Suppress("UNCHECKED_CAST")
        return if (response == null || result.body.isBlank()) Unit as T else decode(response, result)
    }

    private fun <T> decode(serializer: KSerializer<T>, result: HttpResult): T = try {
        json.decodeFromString(serializer, result.body)
    } catch (e: SerializationException) {
        throw ApiException(result.status, "bad_response", "Unexpected reply from the server. Please update the app.")
    }

    private suspend fun call(method: String, path: String, body: String?, token: String?): HttpResult {
        val request = Request.Builder()
            .url(baseUrl + path)
            .method(method, body?.toRequestBody(JSON_MEDIA))
            .header("Accept", "application/json")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(ApiException(ApiException.NETWORK_ERROR, "network", "Can't reach the server. Check your connection."))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { continuation.resume(HttpResult(it.code, it.body?.string().orEmpty())) }
                }
            })
        }
    }

    private fun toException(result: HttpResult): ApiException {
        val error = runCatching { json.decodeFromString(ErrorEnvelope.serializer(), result.body).error }.getOrNull()
        return ApiException(result.status, error?.code ?: "http_${result.status}", error?.message ?: "Something went wrong (${result.status})")
    }

    private fun signedOut() = ApiException(401, "signed_out", "Please sign in to play online")
}

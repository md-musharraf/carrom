package com.example.royalcarromclassic.online

import com.example.royalcarromclassic.core.logging.AppLogger
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.WebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject
import java.net.URI
import kotlin.coroutines.resume

/** Everything the server can push to this device. */
sealed interface RealtimeEvent {
    data class MatchStarted(val snapshot: SnapshotDto) : RealtimeEvent
    data class ShotPlayed(val shot: ShotEventDto) : RealtimeEvent
    data class TurnPassed(val turn: TurnEventDto) : RealtimeEvent
    data class OpponentAim(val aim: AimEventDto) : RealtimeEvent
    data class Emote(val emote: EmoteEventDto) : RealtimeEvent
    data class Presence(val presence: PresenceEventDto) : RealtimeEvent
    data class MatchEnded(val end: EndEventDto) : RealtimeEvent
    data class FriendRequest(val notice: FriendNoticeDto) : RealtimeEvent
    data class FriendAccepted(val notice: FriendNoticeDto) : RealtimeEvent
    data class FriendPresence(val presence: FriendPresenceDto) : RealtimeEvent
    data class Invite(val invite: InviteDto) : RealtimeEvent
    data class QueueCancelled(val reason: String) : RealtimeEvent
}

enum class ConnectionState { OFFLINE, CONNECTING, ONLINE }

private const val ACK_TIMEOUT_MS = 10_000L
private const val TAG = "RealtimeClient"
private val MALFORMED_REPLY = JsonObject(mapOf("ok" to JsonPrimitive(false)))

/**
 * Socket.IO connection to the game server. Server pushes arrive on [events]; client actions go
 * through [request] (acknowledged, typed result) or [send] (fire-and-forget, e.g. live aim).
 */
class RealtimeClient(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val json: Json = OnlineJson
) {
    @Volatile
    private var socket: Socket? = null

    /** Bumped by every connect/disconnect so stale async work can tell it has been superseded. */
    @Volatile
    private var generation = 0
    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
    private val _connection = MutableStateFlow(ConnectionState.OFFLINE)

    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    fun connect() {
        if (socket != null || _connection.value == ConnectionState.CONNECTING) return
        _connection.value = ConnectionState.CONNECTING
        val attempt = ++generation
        scope.launch {
            val token = runCatching { api.accessToken() }.getOrNull()
            if (attempt != generation) return@launch
            if (token == null) _connection.value = ConnectionState.OFFLINE else open(token, attempt)
        }
    }

    fun disconnect() {
        generation++
        socket?.let {
            it.off()
            it.disconnect()
        }
        socket = null
        _connection.value = ConnectionState.OFFLINE
    }

    private fun open(token: String, attempt: Int) {
        val options = IO.Options.builder()
            .setAuth(mapOf("token" to token))
            .setTransports(arrayOf(WebSocket.NAME))
            .setReconnection(true)
            .setForceNew(true)
            .build()
        val next = IO.socket(URI.create(api.baseUrl), options)
        socket = next

        next.on(Socket.EVENT_CONNECT) { if (socket === next) _connection.value = ConnectionState.ONLINE }
        next.on(Socket.EVENT_DISCONNECT) { if (socket === next) _connection.value = ConnectionState.CONNECTING }
        next.on(Socket.EVENT_CONNECT_ERROR) { args ->
            // An expired access token: refresh it and reconnect with a fresh handshake.
            if ((args.firstOrNull() as? Exception)?.message == "unauthorized" && socket === next) {
                next.off()
                next.disconnect()
                socket = null
                scope.launch {
                    val fresh = runCatching { api.refresh(rejected = token) }.getOrNull()
                    if (attempt != generation) return@launch
                    if (fresh != null) open(fresh, attempt) else _connection.value = ConnectionState.OFFLINE
                }
            }
        }

        listen(next, "match:start", SnapshotDto.serializer()) { RealtimeEvent.MatchStarted(it) }
        listen(next, "match:shot", ShotEventDto.serializer()) { RealtimeEvent.ShotPlayed(it) }
        listen(next, "match:turn", TurnEventDto.serializer()) { RealtimeEvent.TurnPassed(it) }
        listen(next, "match:aim", AimEventDto.serializer()) { RealtimeEvent.OpponentAim(it) }
        listen(next, "match:emote", EmoteEventDto.serializer()) { RealtimeEvent.Emote(it) }
        listen(next, "match:presence", PresenceEventDto.serializer()) { RealtimeEvent.Presence(it) }
        listen(next, "match:end", EndEventDto.serializer()) { RealtimeEvent.MatchEnded(it) }
        listen(next, "friends:request", FriendNoticeDto.serializer()) { RealtimeEvent.FriendRequest(it) }
        listen(next, "friends:accepted", FriendNoticeDto.serializer()) { RealtimeEvent.FriendAccepted(it) }
        listen(next, "friends:presence", FriendPresenceDto.serializer()) { RealtimeEvent.FriendPresence(it) }
        listen(next, "invite:received", InviteDto.serializer()) { RealtimeEvent.Invite(it) }
        listen(next, "queue:cancelled", QueueCancelledDto.serializer()) { RealtimeEvent.QueueCancelled(it.reason) }
        next.connect()
    }

    private fun <T> listen(socket: Socket, event: String, serializer: KSerializer<T>, wrap: (T) -> RealtimeEvent) {
        socket.on(event) { args ->
            val payload = args.firstOrNull() ?: return@on
            runCatching { json.decodeFromString(serializer, payload.toString()) }
                .onSuccess { _events.tryEmit(wrap(it)) }
                .onFailure { AppLogger.w(TAG, { "Dropped malformed '$event' from the server" }, it) }
        }
    }

    /**
     * Emits [event] and suspends for the server's acknowledgement. Returns the decoded `data`
     * (null for [response] == null) or throws [ApiException] with the server's error code.
     */
    suspend fun <T> request(event: String, payload: JsonElement?, response: KSerializer<T>?): T? {
        val active = socket?.takeIf { it.connected() } ?: throw ApiException(0, "offline", "You're offline. Reconnecting…")
        val args: Array<Any> = if (payload == null) emptyArray() else arrayOf(JSONObject(payload.toString()))
        val reply = withTimeoutOrNull(ACK_TIMEOUT_MS) {
            suspendCancellableCoroutine<JsonObject> { continuation ->
                active.emit(event, args) { ackArgs ->
                    // Runs on the socket's thread: never let a malformed reply escape there.
                    val body = runCatching { json.parseToJsonElement(ackArgs.firstOrNull()?.toString() ?: "{}").jsonObject }
                        .getOrElse { MALFORMED_REPLY }
                    if (continuation.isActive) continuation.resume(body)
                }
            }
        } ?: throw ApiException(0, "timeout", "The server didn't answer. Check your connection.")
        if (!reply.okFlag()) {
            val error = reply["error"] as? JsonObject
            throw ApiException(0, error.text("code") ?: "error", error.text("message") ?: "Something went wrong")
        }
        val data = reply["data"]
        if (response == null || data == null || data is JsonNull) return null
        return try {
            json.decodeFromJsonElement(response, data)
        } catch (e: SerializationException) {
            throw ApiException(0, "bad_response", "Unexpected reply from the server. Please update the app.")
        }
    }

    /** [request] for actions whose acknowledgement carries no data. */
    suspend fun command(event: String, payload: JsonElement?) {
        request<Unit>(event, payload, null)
    }

    /** Fire-and-forget emit (no acknowledgement awaited). */
    fun send(event: String, payload: JsonElement) {
        socket?.takeIf { it.connected() }?.emit(event, JSONObject(payload.toString()))
    }

    private fun JsonObject.okFlag() = (this["ok"] as? JsonPrimitive)?.booleanOrNull == true

    private fun JsonObject?.text(key: String) = (this?.get(key) as? JsonPrimitive)?.contentOrNull
}

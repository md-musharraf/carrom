package com.example.royalcarromclassic.online

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** What the game screen needs to play an online match; implemented over the realtime socket. */
interface OnlineMatchSource {
    val events: Flow<RealtimeEvent>
    val connection: StateFlow<ConnectionState>

    /** The signed-in player's ID, or null when signed out. */
    val playerId: String?

    /** Plays a shot in server coordinates. Throws [ApiException] if the server refuses it. */
    suspend fun shoot(matchId: String, turn: Int, input: ShotInputDto)

    /** Live aim preview for the opponent; dropped silently if offline. */
    fun aim(matchId: String, input: ShotInputDto)

    suspend fun emote(matchId: String, emote: String)

    suspend fun resign(matchId: String)

    /** The caller's live match, if any (after reconnecting or relaunching). */
    suspend fun resume(): SnapshotDto?
}

/** Lobby actions: ranked queue and private rooms. */
interface OnlineLobby {
    suspend fun joinQueue(mode: String, arena: String): QueueJoinedDto?
    suspend fun leaveQueue()
    suspend fun createRoom(mode: String): RoomDto?
    suspend fun joinRoom(code: String)
    suspend fun cancelRoom()
    suspend fun inviteToRoom(playerId: String)
}

private class SocketMatchSource(
    private val realtime: RealtimeClient,
    private val client: ApiClient
) : OnlineMatchSource, OnlineLobby {
    override val events: Flow<RealtimeEvent> = realtime.events
    override val connection: StateFlow<ConnectionState> = realtime.connection
    override val playerId: String? get() = client.session.value?.playerId

    override suspend fun shoot(matchId: String, turn: Int, input: ShotInputDto) {
        realtime.command("match:shoot", shotPayload(matchId, input) { put("turn", turn) })
    }

    override fun aim(matchId: String, input: ShotInputDto) {
        realtime.send("match:aim", shotPayload(matchId, input))
    }

    override suspend fun emote(matchId: String, emote: String) {
        realtime.command("match:emote", buildJsonObject { put("matchId", matchId); put("emote", emote) })
    }

    override suspend fun resign(matchId: String) {
        realtime.command("match:resign", buildJsonObject { put("matchId", matchId) })
    }

    override suspend fun resume(): SnapshotDto? = realtime.request("match:resume", JsonObject(emptyMap()), SnapshotDto.serializer())

    override suspend fun joinQueue(mode: String, arena: String) =
        realtime.request("queue:join", buildJsonObject { put("mode", mode); put("arena", arena) }, QueueJoinedDto.serializer())

    override suspend fun leaveQueue() {
        realtime.command("queue:leave", JsonObject(emptyMap()))
    }

    override suspend fun createRoom(mode: String) =
        realtime.request("room:create", buildJsonObject { put("mode", mode) }, RoomDto.serializer())

    override suspend fun joinRoom(code: String) {
        realtime.command("room:join", buildJsonObject { put("code", code) })
    }

    override suspend fun cancelRoom() {
        realtime.command("room:cancel", JsonObject(emptyMap()))
    }

    override suspend fun inviteToRoom(playerId: String) {
        realtime.command("room:invite", buildJsonObject { put("playerId", playerId) })
    }

    private fun shotPayload(matchId: String, input: ShotInputDto, extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) =
        buildJsonObject {
            put("matchId", matchId)
            put("offset", input.offset)
            put("angle", input.angle)
            put("power", input.power)
            extra()
        }
}

/**
 * Application-wide online stack: REST client, typed API, realtime socket and the match/lobby
 * facades. The socket connects whenever a session exists and disconnects on sign-out.
 */
class OnlineServices(baseUrl: String, sessionStore: SessionStore, scope: CoroutineScope) {
    val client = ApiClient(baseUrl, sessionStore)
    val api = OnlineApi(client)
    val realtime = RealtimeClient(client, scope)
    private val socketFacade = SocketMatchSource(realtime, client)
    val matches: OnlineMatchSource = socketFacade
    val lobby: OnlineLobby = socketFacade

    init {
        // Token refreshes keep the socket; signing in as someone else opens a new one.
        scope.launch {
            client.session.map { it?.playerId }.distinctUntilChanged().collect { playerId ->
                realtime.disconnect()
                if (playerId != null) realtime.connect()
            }
        }
    }
}

/** Implemented by the Application so ViewModels can reach the online stack. */
interface OnlineHost {
    val online: OnlineServices
}

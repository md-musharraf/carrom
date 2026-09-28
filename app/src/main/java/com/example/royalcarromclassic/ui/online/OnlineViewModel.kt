package com.example.royalcarromclassic.ui.online

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.royalcarromclassic.online.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** Where the player is in finding an online opponent. */
sealed interface LobbyState {
    data object Idle : LobbyState

    data class Searching(val mode: String, val arena: String, val entryFee: Int, val sinceMillis: Long) : LobbyState

    data class Hosting(val code: String, val mode: String, val expiresAtMillis: Long) : LobbyState

    /** Waiting for the server to start the match after joining a room. */
    data object Joining : LobbyState
}

/** Progress of a phone number sign-in (or link). */
sealed interface PhoneStep {
    data object EnterNumber : PhoneStep

    data class EnterCode(val phone: String, val codeLength: Int, val expiresAtMillis: Long) : PhoneStep
}

data class OnlineUiState(
    /** False when this build has no online stack (e.g. design previews). */
    val available: Boolean = false,
    val connection: ConnectionState = ConnectionState.OFFLINE,
    val profile: ProfileDto? = null,
    /** An account action (sign-in, rename…) is in progress. */
    val busy: Boolean = false,
    val phoneStep: PhoneStep = PhoneStep.EnterNumber,
    val friends: List<ProfileDto> = emptyList(),
    val requests: List<FriendRequestDto> = emptyList(),
    val board: Board = Board.RATING,
    val leaderboard: LeaderboardDto? = null,
    val leaderboardLoading: Boolean = false,
    val arenas: List<ArenaDto> = emptyList(),
    val history: List<MatchSummaryDto> = emptyList(),
    val daily: DailyStatusDto? = null,
    val lobby: LobbyState = LobbyState.Idle,
    val invite: InviteDto? = null,
    /** A one-off notice for the player (errors, confirmations); cleared with [OnlineViewModel.consumeMessage]. */
    val message: String? = null
) {
    val signedIn: Boolean get() = profile != null
}

/** Everything the online sheets can ask for; outcomes arrive through [OnlineUiState]. */
interface OnlineActions {
    fun signInAsGuest()
    fun signInWithGoogle(provider: SocialAuthProvider)
    fun signInWithFacebook(provider: SocialAuthProvider)
    fun startPhoneSignIn(phone: String)
    fun verifyPhone(code: String)
    fun editPhoneNumber()
    fun signOut()
    fun deleteAccount()
    fun rename(name: String)
    fun refreshProfile()
    fun loadHistory()
    fun loadFriends()
    fun sendFriendRequest(playerId: String)
    fun acceptFriendRequest(request: FriendRequestDto)
    fun declineFriendRequest(request: FriendRequestDto)
    fun removeFriend(friend: ProfileDto)
    fun selectBoard(board: Board)
    fun loadLobby()
    fun findMatch(mode: String, arena: ArenaDto)
    fun cancelSearch()
    fun createRoom(mode: String)
    fun joinRoom(code: String)
    fun cancelRoom()
    fun inviteFriend(friend: ProfileDto)
    fun acceptInvite()
    fun dismissInvite()
    fun claimDailyReward()
    fun consumeMessage()
}

/**
 * Accounts, friends, leaderboards and the lobby. The match itself is played by the game
 * ViewModel, which receives `match:start` from the same realtime connection.
 */
class OnlineViewModel @JvmOverloads constructor(
    application: Application,
    private val services: OnlineServices? = (application as? OnlineHost)?.online,
    private val clock: () -> Long = System::currentTimeMillis
) : AndroidViewModel(application), OnlineActions {

    private val _state = MutableStateFlow(OnlineUiState(available = services != null))
    val state: StateFlow<OnlineUiState> = _state.asStateFlow()

    private var leaderboardJob: Job? = null

    init {
        if (services != null) {
            viewModelScope.launch {
                services.client.session.map { it?.playerId }.distinctUntilChanged().collect { playerId ->
                    if (playerId != null) onSignedIn() else _state.update { signedOut(it) }
                }
            }
            viewModelScope.launch { services.realtime.connection.collect { c -> _state.update { it.copy(connection = c) } } }
            viewModelScope.launch { services.realtime.events.collect { onEvent(it) } }
        }
    }

    // region Account

    override fun signInAsGuest() = action { api.signInAsGuest(country()) }

    /** Signs in with Google; a guest keeps their progress by linking it instead. */
    override fun signInWithGoogle(provider: SocialAuthProvider) = action {
        val token = provider.googleIdToken()
        socialSignIn { link -> api.signInWithGoogle(token, link) }
    }

    override fun signInWithFacebook(provider: SocialAuthProvider) = action {
        val token = provider.facebookAccessToken()
        socialSignIn { link -> api.signInWithFacebook(token, link) }
    }

    /** Guests link the new identity; if it already belongs to another player, switch to that account. */
    private suspend fun socialSignIn(signIn: suspend (link: Boolean) -> AuthResponse) {
        val isGuest = _state.value.profile?.isGuest == true
        if (!isGuest) {
            signIn(false)
            return
        }
        try {
            signIn(true)
            // Same player, new identity: the session's player ID doesn't change, so refresh here.
            _state.update { it.copy(profile = api.me()) }
            notify("Account secured. Your progress is saved")
        } catch (e: ApiException) {
            if (e.code != "identity_in_use") throw e
            signIn(false)
            notify("Welcome back! Signed in to your existing account")
        }
    }

    override fun startPhoneSignIn(phone: String) = action {
        val started = api.startPhoneSignIn(phone.trim(), country())
        _state.update {
            it.copy(phoneStep = PhoneStep.EnterCode(started.phone, started.codeLength, clock() + started.expiresInSeconds * 1000L))
        }
    }

    override fun verifyPhone(code: String) = action {
        val step = _state.value.phoneStep as? PhoneStep.EnterCode ?: return@action
        val link = _state.value.profile?.isGuest == true
        api.verifyPhone(step.phone, code.trim(), country(), link = link)
        _state.update { it.copy(phoneStep = PhoneStep.EnterNumber) }
        if (link) {
            _state.update { it.copy(profile = api.me()) }
            notify("Phone number linked")
        }
    }

    override fun editPhoneNumber() = _state.update { it.copy(phoneStep = PhoneStep.EnterNumber) }

    override fun signOut() = action { api.signOut() }

    override fun deleteAccount() = action {
        api.deleteAccount()
        notify("Your account has been deleted")
    }

    override fun rename(name: String) = action {
        val profile = api.updateProfile(displayName = name.trim())
        _state.update { it.copy(profile = profile) }
        notify("Name updated")
    }

    override fun refreshProfile() {
        quietly {
            val profile = api.me()
            _state.update { it.copy(profile = profile) }
        }
    }

    override fun loadHistory() {
        quietly {
            val history = api.matchHistory(limit = 10)
            _state.update { it.copy(history = history) }
        }
    }

    // endregion

    // region Friends & leaderboards

    override fun loadFriends() {
        quietly { loadFriendsNow() }
    }

    override fun sendFriendRequest(playerId: String) = action {
        val id = playerId.trim().replace("-", "").uppercase()
        val result = api.sendFriendRequest(id)
        notify(if (result.status == "accepted") "You're now friends" else "Friend request sent")
        loadFriendsNow()
    }

    override fun acceptFriendRequest(request: FriendRequestDto) = action {
        api.acceptFriendRequest(request.id)
        notify("You and ${request.player.displayName} are now friends")
        loadFriendsNow()
    }

    override fun declineFriendRequest(request: FriendRequestDto) = action {
        api.declineFriendRequest(request.id)
        loadFriendsNow()
    }

    override fun removeFriend(friend: ProfileDto) = action {
        api.removeFriend(friend.playerId)
        loadFriendsNow()
    }

    override fun selectBoard(board: Board) {
        _state.update { it.copy(board = board) }
        leaderboardJob?.cancel()
        leaderboardJob = quietly {
            _state.update { it.copy(leaderboardLoading = true) }
            try {
                val leaderboard = api.leaderboard(board)
                _state.update { if (it.board == board) it.copy(leaderboard = leaderboard) else it }
            } finally {
                _state.update { if (it.board == board) it.copy(leaderboardLoading = false) else it }
            }
        }
    }

    private suspend fun loadFriendsNow() {
        val friends = api.friends()
        val requests = api.friendRequests()
        _state.update { it.copy(friends = friends, requests = requests) }
    }

    // endregion

    // region Lobby

    override fun loadLobby() {
        quietly {
            val arenas = api.arenas()
            val daily = api.dailyStatus()
            _state.update { it.copy(arenas = arenas, daily = daily) }
        }
    }

    override fun findMatch(mode: String, arena: ArenaDto) = action {
        val joined = lobby.joinQueue(mode, arena.id) ?: return@action
        _state.update { it.copy(lobby = LobbyState.Searching(joined.mode, joined.arena, joined.entryFee, clock())) }
    }

    override fun cancelSearch() = action {
        lobby.leaveQueue()
        _state.update { it.copy(lobby = LobbyState.Idle) }
    }

    override fun createRoom(mode: String) = action {
        val room = lobby.createRoom(mode) ?: return@action
        _state.update { it.copy(lobby = LobbyState.Hosting(room.code, room.mode, clock() + room.expiresInSeconds * 1000L)) }
    }

    override fun joinRoom(code: String) = action {
        _state.update { it.copy(lobby = LobbyState.Joining) }
        try {
            lobby.joinRoom(code.trim().uppercase())
        } catch (e: ApiException) {
            _state.update { it.copy(lobby = LobbyState.Idle) }
            throw e
        }
    }

    override fun cancelRoom() = action {
        lobby.cancelRoom()
        _state.update { it.copy(lobby = LobbyState.Idle) }
    }

    override fun inviteFriend(friend: ProfileDto) = action {
        lobby.inviteToRoom(friend.playerId)
        notify("Invite sent to ${friend.displayName}")
    }

    override fun acceptInvite() {
        val invite = _state.value.invite ?: return
        _state.update { it.copy(invite = null) }
        joinRoom(invite.code)
    }

    override fun dismissInvite() = _state.update { it.copy(invite = null) }

    override fun claimDailyReward() = action {
        val claim = api.claimDaily()
        notify("Day ${claim.streak} reward: +${claim.reward.coins} coins" + if (claim.reward.gems > 0) " · +${claim.reward.gems} gems" else "")
        _state.update {
            it.copy(
                daily = it.daily?.copy(claimedToday = true, streak = claim.streak),
                profile = it.profile?.copy(wallet = claim.balance)
            )
        }
    }

    override fun consumeMessage() = _state.update { it.copy(message = null) }

    // endregion

    private fun onEvent(event: RealtimeEvent) {
        when (event) {
            // The game screen takes over; the lobby is done.
            is RealtimeEvent.MatchStarted -> _state.update { it.copy(lobby = LobbyState.Idle, invite = null) }
            is RealtimeEvent.MatchEnded -> {
                refreshProfile()
                loadHistory()
            }
            is RealtimeEvent.QueueCancelled -> {
                _state.update { it.copy(lobby = LobbyState.Idle) }
                notify(
                    when (event.reason) {
                        "insufficient_funds" -> "Not enough coins for that arena"
                        else -> "Matchmaking stopped. Please try again"
                    }
                )
            }
            is RealtimeEvent.FriendRequest -> {
                notify("${event.notice.player.displayName} sent you a friend request")
                loadFriends()
            }
            is RealtimeEvent.FriendAccepted -> {
                notify("${event.notice.player.displayName} accepted your friend request")
                loadFriends()
            }
            is RealtimeEvent.FriendPresence -> _state.update { state ->
                state.copy(friends = state.friends.map { if (it.playerId == event.presence.playerId) it.copy(online = event.presence.online) else it })
            }
            is RealtimeEvent.Invite -> _state.update { it.copy(invite = event.invite) }
            else -> Unit
        }
    }

    private fun onSignedIn() {
        refreshProfile()
        loadFriends()
        loadLobby()
    }

    private fun signedOut(state: OnlineUiState) = OnlineUiState(
        available = state.available,
        connection = state.connection,
        message = state.message
    )

    private val api: OnlineApi get() = requireNotNull(services).api
    private val lobby: OnlineLobby get() = requireNotNull(services).lobby

    private fun notify(message: String) = _state.update { it.copy(message = message) }

    /** Runs a player-initiated action with a busy indicator; failures become a friendly message. */
    private fun action(block: suspend () -> Unit) {
        if (services == null) return notify("Online play isn't available in this build")
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: ApiException) {
                notify(e.message ?: "Something went wrong")
            } catch (e: SocialAuthException) {
                if (!e.cancelled) notify(e.message ?: "Sign-in failed")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /** Background refresh: failures are silent (the data simply stays as it was). */
    private fun quietly(block: suspend () -> Unit): Job = viewModelScope.launch {
        if (services == null || services.client.session.value == null) return@launch
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            // Offline or signed out meanwhile; the next refresh will catch up.
        }
    }

    private fun country(): String? = Locale.getDefault().country.takeIf { it.length == 2 }
}

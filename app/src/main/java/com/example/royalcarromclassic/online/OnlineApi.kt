package com.example.royalcarromclassic.online

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
private data class GuestBody(val country: String? = null)

@Serializable
private data class GoogleBody(val idToken: String)

@Serializable
private data class FacebookBody(val accessToken: String)

@Serializable
private data class PhoneStartBody(val phone: String, val country: String? = null)

@Serializable
private data class PhoneVerifyBody(val phone: String, val code: String, val country: String? = null)

@Serializable
private data class RefreshTokenBody(val refreshToken: String)

@Serializable
private data class ProfilePatch(val displayName: String? = null, val country: String? = null)

@Serializable
private data class PlayerIdBody(val playerId: String)

@Serializable
private data class ItemBody(val itemId: String)

/** Leaderboards the server offers. */
enum class Board(val path: String, val label: String) {
    RATING("rating", "Global"),
    WEEKLY("weekly", "This week"),
    COUNTRY("country", "Country"),
    FRIENDS("friends", "Friends")
}

/**
 * Every REST endpoint of the Royal Carrom server as a suspend function. Sign-in calls store the
 * returned session; the `link` variants attach a provider to the signed-in (e.g. guest) account.
 */
class OnlineApi(private val client: ApiClient) {

    val session get() = client.session

    // ---- Authentication ----------------------------------------------------------------------

    suspend fun signInAsGuest(country: String? = null): AuthResponse =
        client.post("/v1/auth/guest", GuestBody(country), GuestBody.serializer(), AuthResponse.serializer(), authenticated = false)
            .also(client::adopt)

    suspend fun signInWithGoogle(idToken: String, link: Boolean = false): AuthResponse =
        client.post(authPath("google", link), GoogleBody(idToken), GoogleBody.serializer(), AuthResponse.serializer(), authenticated = link)
            .also(client::adopt)

    suspend fun signInWithFacebook(accessToken: String, link: Boolean = false): AuthResponse =
        client.post(authPath("facebook", link), FacebookBody(accessToken), FacebookBody.serializer(), AuthResponse.serializer(), authenticated = link)
            .also(client::adopt)

    suspend fun startPhoneSignIn(phone: String, country: String? = null): PhoneStartResponse =
        client.post("/v1/auth/phone/start", PhoneStartBody(phone, country), PhoneStartBody.serializer(), PhoneStartResponse.serializer(), authenticated = false)

    suspend fun verifyPhone(phone: String, code: String, country: String? = null, link: Boolean = false): AuthResponse =
        client.post(
            if (link) "/v1/auth/link/phone/verify" else "/v1/auth/phone/verify",
            PhoneVerifyBody(phone, code, country),
            PhoneVerifyBody.serializer(),
            AuthResponse.serializer(),
            authenticated = link
        ).also(client::adopt)

    /** Revokes the session on the server (best effort) and forgets it locally. */
    suspend fun signOut() {
        val refreshToken = client.session.value?.refreshToken
        if (refreshToken != null) {
            runCatching { client.postNoContent("/v1/auth/logout", RefreshTokenBody(refreshToken), RefreshTokenBody.serializer(), authenticated = false) }
        }
        client.signOutLocally()
    }

    suspend fun deleteAccount() {
        client.delete("/v1/me")
        client.signOutLocally()
    }

    // ---- Profiles ------------------------------------------------------------------------------

    suspend fun me(): ProfileDto = client.get("/v1/me", ProfileDto.serializer())

    suspend fun updateProfile(displayName: String? = null, country: String? = null): ProfileDto =
        client.patch("/v1/me", ProfilePatch(displayName, country), ProfilePatch.serializer(), ProfileDto.serializer())

    suspend fun player(playerId: String): ProfileDto = client.get("/v1/players/${playerId.trim()}", ProfileDto.serializer())

    // ---- Friends ---------------------------------------------------------------------------------

    suspend fun friends(): List<ProfileDto> = client.get("/v1/friends", FriendsResponse.serializer()).friends

    suspend fun friendRequests(): List<FriendRequestDto> = client.get("/v1/friends/requests", FriendRequestsResponse.serializer()).requests

    suspend fun sendFriendRequest(playerId: String): FriendRequestResult =
        client.post("/v1/friends/requests", PlayerIdBody(playerId.trim()), PlayerIdBody.serializer(), FriendRequestResult.serializer())

    suspend fun acceptFriendRequest(id: String) = client.postNoContent("/v1/friends/requests/$id/accept")

    suspend fun declineFriendRequest(id: String) = client.postNoContent("/v1/friends/requests/$id/decline")

    suspend fun removeFriend(playerId: String) = client.delete("/v1/friends/$playerId")

    // ---- Leaderboards, rewards, shop, history ----------------------------------------------------

    suspend fun leaderboard(board: Board, limit: Int = 50): LeaderboardDto =
        client.get("/v1/leaderboards/${board.path}?limit=$limit", LeaderboardDto.serializer())

    suspend fun dailyStatus(): DailyStatusDto = client.get("/v1/rewards/daily", DailyStatusDto.serializer())

    suspend fun claimDaily(): DailyClaimDto = client.post("/v1/rewards/daily/claim", DailyClaimDto.serializer())

    suspend fun spin(): SpinDto = client.post("/v1/rewards/spin", SpinDto.serializer())

    suspend fun rewardOfflineWin(): OfflineWinDto = client.post("/v1/rewards/offline-win", OfflineWinDto.serializer())

    suspend fun arenas(): List<ArenaDto> = client.get("/v1/arenas", ArenasResponse.serializer()).arenas

    suspend fun matchHistory(limit: Int = 20): List<MatchSummaryDto> =
        client.get("/v1/matches?limit=$limit", MatchesResponse.serializer()).matches

    suspend fun purchase(itemId: String) {
        client.postNoContent("/v1/shop/purchase", ItemBody(itemId), ItemBody.serializer())
    }

    suspend fun equip(itemId: String) {
        client.postNoContent("/v1/shop/equip", ItemBody(itemId), ItemBody.serializer())
    }

    private fun authPath(provider: String, link: Boolean) = if (link) "/v1/auth/link/$provider" else "/v1/auth/$provider"

    private companion object {
        @Suppress("unused")
        val unitSerializer = Unit.serializer()
    }
}

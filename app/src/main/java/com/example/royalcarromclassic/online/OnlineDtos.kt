package com.example.royalcarromclassic.online

import kotlinx.serialization.Serializable

/*
 * Wire formats of the Royal Carrom server (see server/README.md). Every field that the server may
 * omit has a default, and unknown fields are ignored, so older clients keep working as the API grows.
 */

@Serializable
data class AuthResponse(
    val accessToken: String,
    val accessTokenExpiresAt: Long,
    val refreshToken: String,
    val refreshTokenExpiresAt: Long,
    val isNewUser: Boolean = false,
    val user: ProfileDto
)

@Serializable
data class StatsDto(
    val played: Int = 0,
    val won: Int = 0,
    val winRate: Int = 0,
    val pockets: Int = 0,
    val queenCovers: Int = 0
)

@Serializable
data class LinkedAccountsDto(
    val google: Boolean = false,
    val facebook: Boolean = false,
    /** Masked number, e.g. "•••• 3210", or null. */
    val phone: String? = null
)

@Serializable
data class BalanceDto(val coins: Int = 0, val gems: Int = 0)

@Serializable
data class ProgressDto(val level: Int = 1, val xp: Int = 0, val xpToNext: Int = 100)

@Serializable
data class InventoryDto(val strikers: List<String> = emptyList(), val boards: List<String> = emptyList())

@Serializable
data class EquippedDto(val striker: String = "classic_ivory", val board: String = "classic_teak")

/** A player as the server describes them; private fields are only present for the caller. */
@Serializable
data class ProfileDto(
    val id: String? = null,
    val playerId: String,
    val displayId: String = playerId,
    val displayName: String,
    val avatarUrl: String? = null,
    val country: String? = null,
    val level: Int = 1,
    val rating: Int = 1500,
    val stats: StatsDto = StatsDto(),
    val isGuest: Boolean = false,
    val linked: LinkedAccountsDto = LinkedAccountsDto(),
    val wallet: BalanceDto = BalanceDto(),
    val progress: ProgressDto = ProgressDto(),
    val inventory: InventoryDto = InventoryDto(),
    val equipped: EquippedDto = EquippedDto(),
    val online: Boolean = false,
    val isFriend: Boolean = false,
    val isMe: Boolean = false
)

@Serializable
data class PhoneStartResponse(val phone: String, val expiresInSeconds: Int, val codeLength: Int = 6)

@Serializable
data class FriendsResponse(val friends: List<ProfileDto>)

@Serializable
data class FriendRequestDto(val id: String, val direction: String, val player: ProfileDto, val createdAt: String)

@Serializable
data class FriendRequestsResponse(val requests: List<FriendRequestDto>)

@Serializable
data class FriendRequestResult(val status: String)

@Serializable
data class LeaderboardEntryDto(
    val rank: Int,
    val playerId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val country: String? = null,
    val level: Int = 1,
    val score: Int,
    val isMe: Boolean = false
)

@Serializable
data class LeaderboardDto(
    val board: String,
    val period: String? = null,
    val entries: List<LeaderboardEntryDto>,
    val me: LeaderboardEntryDto? = null
)

@Serializable
data class DailyStatusDto(
    val today: String,
    val streak: Int,
    val claimedToday: Boolean,
    val nextReward: BalanceDto,
    val rewards: List<BalanceDto>,
    val spinAvailable: Boolean
)

@Serializable
data class DailyClaimDto(val streak: Int, val reward: BalanceDto, val balance: BalanceDto)

@Serializable
data class SpinDto(val index: Int, val coins: Int, val balance: BalanceDto)

@Serializable
data class OfflineWinDto(val coins: Int, val xp: Int, val balance: BalanceDto, val remainingToday: Int)

@Serializable
data class ArenaDto(
    val id: String,
    val name: String,
    val entryFee: Int,
    val minLevel: Int,
    val unlocked: Boolean = true,
    val affordable: Boolean = true
)

@Serializable
data class ArenasResponse(val arenas: List<ArenaDto>)

@Serializable
data class OpponentDto(val playerId: String, val name: String, val score: Int)

@Serializable
data class MatchSummaryDto(
    val matchId: String,
    val mode: String,
    val arena: String? = null,
    val ranked: Boolean,
    val result: String,
    val endReason: String,
    val myScore: Int,
    val opponent: OpponentDto,
    val ratingChange: Int,
    val coinsWon: Int,
    val endedAt: String
)

@Serializable
data class MatchesResponse(val matches: List<MatchSummaryDto>)

@Serializable
data class ErrorBody(val code: String, val message: String)

@Serializable
data class ErrorEnvelope(val error: ErrorBody)

// ---- Realtime ------------------------------------------------------------------------------

@Serializable
data class PieceDto(val id: String, val type: String, val x: Float, val y: Float, val pocketed: Boolean)

@Serializable
data class SeatDto(
    val playerId: String,
    val name: String,
    val avatarUrl: String? = null,
    val level: Int = 1,
    val rating: Int = 1500,
    val strikerId: String = "classic_ivory",
    val score: Int = 0,
    val fouls: Int = 0,
    val connected: Boolean = true
)

@Serializable
data class QueenDto(val pottedBy: Int? = null, val awaitingCover: Boolean = false, val covered: Boolean = false)

/** Full authoritative match state. Seat 0 shoots from the bottom baseline, seat 1 from the top. */
@Serializable
data class SnapshotDto(
    val id: String,
    val mode: String,
    val arena: String? = null,
    val ranked: Boolean,
    val entryFee: Int = 0,
    val seats: List<SeatDto>,
    val pieces: List<PieceDto>,
    val queen: QueenDto = QueenDto(),
    val current: Int,
    val turn: Int,
    val deadline: Long,
    val serverTime: Long = deadline,
    val turnSeconds: Float = 20f,
    val phase: String,
    val winner: Int? = null,
    val endReason: String? = null
)

@Serializable
data class ShotInputDto(val offset: Float, val angle: Float, val power: Float)

@Serializable
data class ShotResultDto(
    val pocketed: List<String> = emptyList(),
    val strikerPocketed: Boolean = false,
    val scoreDelta: Int = 0,
    val foul: Boolean = false,
    val announcement: String? = null,
    val seconds: Float = 0f
)

@Serializable
data class ShotEventDto(
    val matchId: String,
    val turn: Int,
    val seat: Int,
    val input: ShotInputDto,
    val strikerPower: Float = 1f,
    val result: ShotResultDto,
    val snapshot: SnapshotDto
)

@Serializable
data class TurnEventDto(val reason: String, val timedOutSeat: Int, val snapshot: SnapshotDto)

@Serializable
data class AimEventDto(val seat: Int, val offset: Float, val angle: Float, val power: Float)

@Serializable
data class EmoteEventDto(val seat: Int, val emote: String)

@Serializable
data class PresenceEventDto(val seat: Int, val connected: Boolean)

@Serializable
data class RewardDto(
    val coins: Int = 0,
    val xp: Int = 0,
    val ratingBefore: Int = 1500,
    val ratingAfter: Int = 1500,
    val leveledUp: Boolean = false
)

@Serializable
data class EndEventDto(val snapshot: SnapshotDto, val winner: Int, val reason: String, val rewards: List<RewardDto>)

@Serializable
data class InviteDto(val code: String, val mode: String, val from: ProfileDto)

@Serializable
data class FriendNoticeDto(val id: String? = null, val player: ProfileDto)

@Serializable
data class FriendPresenceDto(val playerId: String, val online: Boolean)

@Serializable
data class QueueCancelledDto(val reason: String)

@Serializable
data class RoomDto(val code: String, val mode: String, val expiresInSeconds: Int)

@Serializable
data class QueueJoinedDto(val mode: String, val arena: String, val entryFee: Int, val rating: Int)

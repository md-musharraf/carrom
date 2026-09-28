# Royal Carrom Classic — Server

Node 22 · TypeScript · Express 5 · MongoDB (Mongoose) · Redis (ioredis) · Socket.IO 4

REST API for accounts, social and economy; Socket.IO for real-time matches with
**server-authoritative physics** (a TypeScript port of the Android engine simulates every shot).

## Run it

```bash
cp .env.example .env          # set JWT_SECRET at least
docker compose up --build     # API on :8080 with MongoDB 8 and Redis 7
# or, with local MongoDB/Redis:
npm install && npm run dev
```

`npm test` runs the integration suite against real MongoDB and Redis. It starts `mongod` and
`redis-server` on free ports (set `MONGOD_BIN` / `REDIS_SERVER_BIN` if they aren't on `PATH`), or
uses `MONGO_URL` / `REDIS_URL` when provided (as in CI).

## Authentication

Every sign-in returns the same shape:

```json
{ "accessToken": "…", "accessTokenExpiresAt": 1759069200000,
  "refreshToken": "rt_…", "refreshTokenExpiresAt": 1761657600000,
  "isNewUser": true, "user": { "playerId": "7K3P9QXA", "displayId": "7K3P-9QXA", … } }
```

* **Access tokens** are 15-minute HS256 JWTs — send `Authorization: Bearer <token>`.
* **Refresh tokens** are opaque, stored hashed, and rotate on every use. Re-using an old one is
  treated as theft and signs out that whole session family.

| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/v1/auth/guest` | `{ country? }` | New guest account (rate-limited per IP) |
| POST | `/v1/auth/google` | `{ idToken }` | Google ID token from Credential Manager; audience = `GOOGLE_CLIENT_IDS` |
| POST | `/v1/auth/facebook` | `{ accessToken }` | Verified with `debug_token` + `appsecret_proof` |
| POST | `/v1/auth/phone/start` | `{ phone, country? }` | Sends a 6-digit code (3 per 15 min per number) |
| POST | `/v1/auth/phone/verify` | `{ phone, code, country? }` | 5 attempts per code, 5-minute expiry |
| POST | `/v1/auth/refresh` | `{ refreshToken }` | Rotates the refresh token |
| POST | `/v1/auth/logout` | `{ refreshToken }` | Revokes the session |
| POST | `/v1/auth/link/{google\|facebook\|phone/verify}` | as above | Adds a provider to the signed-in account (upgrades guests); `409 identity_in_use` if taken |

## Player API (Bearer token required)

| Method | Path | Purpose |
|---|---|---|
| GET/PATCH/DELETE | `/v1/me` | Own profile · edit name/country/avatar · delete account |
| GET | `/v1/players/:playerId` | Public profile (accepts `7k3p-9qxa`, look-alike letters) |
| GET | `/v1/friends` | Friends with live `online` flag |
| GET/POST | `/v1/friends/requests` | List · send `{ playerId }` (mutual requests auto-accept) |
| POST | `/v1/friends/requests/:id/accept` · `/decline` | Respond |
| DELETE | `/v1/friends/:playerId` | Unfriend |
| GET | `/v1/leaderboards/{rating\|weekly\|country\|friends}` | Top N plus your own rank |
| GET | `/v1/rewards/daily` · POST `/v1/rewards/daily/claim` | 7-day login streak |
| POST | `/v1/rewards/spin` | Free daily wheel; server draws the segment index |
| POST | `/v1/rewards/offline-win` | Small capped reward for offline wins (10/day) |
| GET | `/v1/shop/catalog` · POST `/v1/shop/purchase` · `/v1/shop/equip` | `{ itemId }` |
| GET | `/v1/arenas` | Stakes ladder with unlock/affordability |
| GET | `/v1/matches` · `/v1/wallet/history` | Match history · coin ledger |

Errors are always `{ "error": { "code": "insufficient_funds", "message": "…", "details"? } }`.

## Realtime protocol (Socket.IO)

Connect with `io(url, { auth: { token: accessToken } })`. Client events take an acknowledgement
callback and receive `{ ok: true, data }` or `{ ok: false, error: { code, message } }`.

| Client → server | Payload | |
|---|---|---|
| `queue:join` / `queue:leave` | `{ mode: "classic" \| "freestyle", arena }` | Ranked quick match |
| `room:create` / `room:join` / `room:cancel` | `{ mode }` / `{ code }` | Private, unranked rooms |
| `room:invite` | `{ playerId }` | Invite a friend to your open room |
| `match:resume` | `{ matchId? }` | Snapshot of your live match (after reconnecting) |
| `match:aim` | `{ matchId, offset, angle, power }` | Live aim preview for the opponent (throttled) |
| `match:shoot` | `{ matchId, turn, offset, angle, power }` | Your shot; `turn` blocks replays |
| `match:emote` | `{ matchId, emote }` | Quick chat: `nice_shot`, `well_played`, `oops`, `thanks`, `good_luck`, `wow`, `hurry_up`, `gg` |
| `match:resign` | `{ matchId }` | Concede |

| Server → client | Payload |
|---|---|
| `match:start` | `MatchSnapshot` |
| `match:aim` | `{ seat, offset, angle, power }` |
| `match:shot` | `{ turn, seat, input, strikerPower, result: { pocketed, strikerPocketed, scoreDelta, foul, announcement, seconds }, snapshot }` |
| `match:turn` | `{ reason: "timeout", timedOutSeat, snapshot }` |
| `match:presence` · `match:emote` | `{ seat, connected }` · `{ seat, emote }` |
| `match:end` | `{ snapshot, winner, reason, rewards[seat]: { coins, xp, ratingBefore, ratingAfter, leveledUp } }` |
| `friends:request` · `friends:accepted` · `friends:presence` · `invite:received` · `queue:cancelled` | social notifications |

Board coordinates are in board units (800 × 800, y down). Seat 0 shoots from the bottom baseline,
seat 1 from the top; clients show their own seat at the bottom by rotating the board 180°.

A `MatchSnapshot` carries `deadline` (when the current turn times out) and `serverTime` (when the
snapshot was taken), both whole Unix milliseconds. Clients time the turn with
`deadline - serverTime` measured on their own clock, so a phone whose clock is off still shows
the right countdown.

## Architecture notes

* **Authoritative shots** — the server simulates the shot to rest (same constants and sub-stepping
  as the client) and broadcasts the input with the end state. Clients animate the input locally
  and settle onto the server's positions.
* **Horizontal scaling** — match state lives in Redis under a per-match lock; turn deadlines are a
  Redis sorted set swept by every node (`ZREM` claims each timeout once); the Socket.IO Redis
  Streams adapter fans out events and supports connection-state recovery.
* **Economy** — balances only change through single conditional MongoDB updates (no double claims
  or overspending) and every change is written to an append-only ledger.
* **Ratings** — Glicko-2 per ranked game; matchmaking widens the rating window while you wait.

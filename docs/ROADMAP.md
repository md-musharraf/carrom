# Royal Carrom Classic — Product & Technical Roadmap

## Vision

The most *authentic* carrom on mobile: a classic walnut-and-brass table that feels like the real
board, fair real-time play against friends and the world, and progression that rewards skill
rather than spending.

## Market research (September 2026)

What the category leaders ship, and what players expect:

| Area | What leaders do | Takeaway for us |
|------|-----------------|-----------------|
| Modes | Carrom, Disc Pool and Freestyle online; regional variants (Korona, Crokinole…) | Keep the three core modes online; variants later |
| Stakes | Arenas with rising coin entry fees, unlocked by level | Arena ladder with escrowed entry fees, winner takes the pot |
| Progression | Levels, victory chests, leagues | XP/levels exist; add ranked rating + chests next |
| Leaderboards | League, friends, country and world | Global, weekly, country and friends boards |
| Daily hooks | Free daily rewards, Lucky Shot, Golden Shot, lucky wheel | Server-authoritative daily streak + wheel; Lucky Shot mini-game |
| Social | Friends, invites, clubs with weekly boards and gifting, quick chat / voice | Friends, private rooms, invites, quick-chat emotes; clubs next |
| Monetisation | Season pass (Carrom Pass), cosmetic strikers/boards | Cosmetics already exist; season pass later |
| Team play | 4-player doubles | Planned (needs 4-seat turn order) |

Sources: [Carrom Pool on Google Play](https://play.google.com/store/apps/details?id=com.miniclip.carrom&hl=en_GB),
[Miniclip — Carrom Pool](https://www.miniclip.com/games/carrom),
[Carrom Pass guide](https://support.miniclip.com/hc/en-us/articles/360046263953-Carrom-Pass-Everything-You-Need-to-Know),
[Carrom League](https://play.google.com/store/apps/details?id=com.carrom.board.friends.freeonline.live&hl=en).

Engineering references: [Google ID token verification](https://developers.google.com/identity/sign-in/android/backend-auth),
[Facebook debug_token](https://developers.facebook.com/docs/graph-api/reference/debug_token/),
[Twilio Verify best practices](https://www.twilio.com/docs/verify/developer-best-practices),
[Socket.IO Redis Streams adapter](https://socket.io/docs/v4/redis-streams-adapter/),
[Refresh token rotation](https://developer.okta.com/docs/guides/refresh-tokens/main/),
[Glicko rating system](https://en.wikipedia.org/wiki/Glicko_rating_system).

## Phased roadmap

### Phase 1 — Online foundation (this iteration)
- **Accounts**: guest play, Google, Facebook and phone-number (OTP) sign-in; link any of them to
  a guest account; every player gets a short, shareable **Player ID** (e.g. `7K3P-9QXA`).
- **Sessions**: short-lived JWT access tokens, rotating refresh tokens with reuse detection,
  logout, account deletion (store requirement).
- **Profile & stats**: public profile by Player ID, lifetime stats, rating.
- **Friends**: add by Player ID, requests, accept/decline, remove, live online presence.
- **Leaderboards**: global rating, weekly wins, per-country, friends.
- **Economy (server-authoritative)**: wallet with an audit ledger, 7-day login streak, daily
  lucky wheel (server picks the prize), shop purchases and equip, capped offline-win rewards.
- **Real-time multiplayer** (Socket.IO): ranked quick match with rating-window matchmaking
  across arenas and entry fees, private rooms with invite codes, friend invites,
  **server-authoritative physics** (the server simulates every shot), live opponent aim,
  turn timers, quick-chat emotes, reconnection with state recovery, forfeits.
- **Ranking**: Glicko-2 ratings for ranked matches.
- **Client**: sign-in sheet, profile, friends, leaderboards, online lobby and online match flow;
  new offline modes **Lucky Shot** (daily target mini-game) and **Blitz** (timed turns vs bot).

### Phase 2 — Retention
- Victory chests and a chest queue; missions and achievements.
- Seasons with league tiers and rewards; season pass.
- Clubs: membership, club chat, weekly club leaderboard, gifting.
- Push notifications for invites and turns (FCM).

### Phase 3 — Depth
- 4-player doubles; spectator mode and replays (shots are deterministic inputs, so a match is
  just its shot list).
- Regional variants (Korona, Crokinole).
- Tournaments (bracketed, scheduled) and anti-cheat analytics.

## Architecture

```
Android (Compose)                         Server (Node 22 · TypeScript)
┌─────────────────────────┐   HTTPS/JSON  ┌──────────────────────────────┐
│ Auth sheet (Google, FB, │ ───────────▶ │ Express 5 REST API            │
│ phone OTP, guest)       │               │  auth · users · friends ·     │
│ Profile · Friends ·     │               │  leaderboards · economy ·     │
│ Leaderboards · Lobby    │   Socket.IO   │  matches                      │
│ Online match (local     │ ◀──────────▶ │ Socket.IO 4 realtime          │
│ animation, server truth)│               │  presence · matchmaking ·     │
└─────────────────────────┘               │  rooms · authoritative match  │
                                          └──────┬───────────────┬───────┘
                                                 │               │
                                          MongoDB (users,   Redis (sessions of
                                          sessions, matches, matchmaking, match
                                          friendships,      state, presence, OTP,
                                          ledger)           rate limits, boards,
                                                            Socket.IO streams)
```

### Why server-authoritative physics
The client sends only its *input* (baseline position, angle, power). The server runs the same
physics engine (TypeScript port), applies the rules and broadcasts the input plus the
authoritative end state. Both clients animate the shot locally from the input — so motion is
smooth and needs no position streaming — then settle onto the server's end state. A modified
client cannot move discs, fake pots or skip turns.

### Horizontal scaling
All match state lives in Redis with optimistic versioning, so any node can process any event.
Turn deadlines sit in a Redis sorted set that every node sweeps and claims atomically. The
Socket.IO Redis Streams adapter fans events out across nodes and supports connection-state
recovery for brief disconnects.

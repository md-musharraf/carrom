# Royal Carrom Classic - Project Guidelines & Architecture

## Overview
Royal Carrom Classic is an Android carrom game built with Jetpack Compose, Kotlin Coroutines, and custom physics, rendering and audio synthesis engines. The look is a classic walnut-and-brass table: a mitred wooden frame, a lacquered plywood surface with traditional markings, and turned-wood carrom men.

It plays offline (vs the bot, pass & play, Blitz, trick shots, Lucky Shot, practice) and online against real players through the backend in [`server/`](server/README.md) (Express, MongoDB, Redis, Socket.IO). The product plan and research are in [`docs/ROADMAP.md`](docs/ROADMAP.md).

## Game modes
The app opens on a **home page**: profile and coins, one-tap quick match against the bot, every mode as a card, your loadout and its powers, and your career. The table is its own screen, so the board gets the full width of the display.

| Mode | What it is |
|------|------------|
| Play Online | Ranked arenas with entry fees (winner takes the pot, Glicko-2 rating) or private rooms with friends |
| vs Bot | Three difficulty levels; the bot cuts, banks and covers the queen |
| Party | 2–4 players on one phone, each seat a person or a bot; four can play doubles (partners sit opposite and pool points) |
| Dice Carrom | Roll before every shot: Steady, Double Points, Eagle Eye, Wide Pockets, Power Surge or Bonus Turn. First to 200 |
| Disc Pool | Each side owns a colour and must pocket all nine; the queen must be covered before anyone's last disc |
| Classic · Freestyle | Points races (Freestyle ends at 160), against bots or friends |
| Blitz | Race the bot to 120 points with a 10-second shot clock |
| Time Attack | Score as much as you can in 90 seconds; fouls cost five seconds; personal best is kept |
| Trick Shots | Ten puzzles with star ratings |
| Lucky Shot | Three free daily flicks of the lucky disc into prize rings (up to 500 coins) |
| Practice | Unlimited solo shots |

## Loadout and powers
Every striker, coin set ("goti"), die and board has one power, shown on its card in **the Locker**. Powers apply to offline matches (from the next match) and can be switched off; online play, trick shots and Lucky Shot always use the standard table.

| Item | Powers |
|------|--------|
| Striker (yours only; bots use the standard one) | Balanced · Fire Shot (+15% power) · Dragon Sight (guide follows an extra rebound) · Heavyweight (+25% power, heavier) · Frictionless (glides farther) · Precision (longest guide, strong aim magnet, finer aim steps) |
| Coin set (the whole table) | Balanced · Anchor (heavier discs) · Glide (discs roll farther) · Magnet (pockets pull discs harder) · Jumbo (13% bigger discs) · Midas Touch (+50% coins for wins) |
| Die (Dice Carrom; bots roll fair) | Fair Roll · Second Chance (one re-roll a match) · No Blanks (never rolls Steady) · Golden Double (Double pays triple) |
| Board (the whole table) | Tournament · Velvet Touch (slower surface) · Wide Pockets · Lively Cushions · Hyper Glide (fast surface) |

## Architecture Guidelines
- **Game loop (`CarromViewModel`)**:
  - Frame-driven: the UI calls `onFrame(frameTimeNanos)` from `withFrameNanos` once per vsync while `needsFrames` is true. Physics, the bot's choreography and all effects advance there, on the main thread, so the board is always drawn from a consistent state.
  - `frameTick` is snapshot state, so the board layer redraws in the same frame the simulation advanced; nothing else recomposes during motion.
  - Scoring and turn order are delegated to the pure `CarromRules` (points races, 2–4 players, doubles, dice modifiers) and `DiscPoolRules`; the bot's turn is animated by `AiShotDirector`.
  - Up to four `Seat`s (bottom, right, top, left). Play passes to the right; every seat shoots from its own baseline.
  - `Powers` turns the loadout into numbers: a `PhysicsTuning` for the table (fixed when a match starts) plus the shooter's striker and die for each shot.
- **Online play (`online/`, `ui/online/`)**:
  - `ApiClient` (OkHttp + kotlinx.serialization) refreshes tokens single-flight; `RealtimeClient` wraps Socket.IO with typed events and acknowledged requests; `OnlineServices` wires them per app (`RoyalCarromApp`).
  - Online matches are server-authoritative. Each device animates shots locally from their inputs, then `SeatView` reconciles the board with the server's snapshot; seat 1 sees the board rotated so every player sits at the bottom. Server updates are queued and applied strictly in order.
  - `OnlineViewModel` drives accounts, friends, leaderboards and the lobby through the `OnlineActions` interface, so every sheet renders from plain state.
  - Sign-in: Google (Credential Manager), Facebook Login, phone OTP and guest. Sessions are stored with `SecureSessionStore` (EncryptedSharedPreferences).
- **Screens (`ui`)**: `CarromApp` is the shell (home page ↔ table, shared sheets, back handling, game loop); `HomeScreen`, `TableScreen`, `MatchSetupSheet` and `LockerSheet`.
- **Touch (`ui/board/ShotGesture`)**: pure, unit-tested rules for striker gestures. Only a drag along the baseline slides; any other drag from the striker pulls (a forward drag aims); a slide that turns backwards becomes a pull; the striker is always at least a fingertip wide; a pull close to the aim you set by touching the board keeps that aim exactly.
- **Rendering (`ui/board`)**:
  - `CarromBoardCanvas.kt`: two layers. The static board (`BoardPainter`) lives in an offscreen-composited layer that is only re-rasterised when the theme changes; the dynamic layer draws discs, the aim guide and effects each frame.
  - `PiecePainter.kt`: turned-wood discs and strikers with origin-centred, cached brushes (drawing a disc allocates nothing).
  - `AimGuidePainter.kt`: dotted, marching aim guide; the struck disc's path turns gold when it is predicted to drop.
- **Physics (`CarromPhysicsEngine`)**:
  - Sub-steps scale with real frame time (16 per 60 Hz frame), so motion is identical at 60, 90 or 120 Hz and discs never tunnel. The server runs a TypeScript port of the same engine.
  - Boric-powder glide friction, cushion damping, pocket guidance and a time-based pocket-drop animation.
  - `calculateTrajectory` uses the same friction model as the live simulation and predicts contacts, pots and striker fouls.
- **UI design system (`theme`, `ui/components`)**:
  - `CarromPalette` holds every colour; serif display typography for titles and scores.
  - `ClassicComponents.kt` (panels, brass buttons, medallions, sheets, selectors) and `Glyphs.kt` (vector icons) keep every screen consistent.
- **Audio & Haptics**:
  - `SoundSynthesizer.kt`: procedural acoustic synthesis (wood clacks, cushion thumps, pocket drops) via `SoundPool`.
  - `HapticController.kt`: scaled tactile feedback; the baseline rail ticks once per detent.
- **Bot AI**:
  - `CarromAIEngine.kt`: geometric ray-casting with obstruction checks, played from any seat by rotating the board until the bot sits at the top; `AiShotDirector.kt` eases it through think → slide → aim → draw back → release.

## Online configuration
The app reads these Gradle properties (e.g. in `~/.gradle/gradle.properties`, never committed):

| Property | Purpose | Default |
|----------|---------|---------|
| `carrom.apiBaseUrl` | Backend URL | `http://10.0.2.2:8080` (the emulator's view of your machine) |
| `carrom.googleWebClientId` | OAuth **web** client ID used to request Google ID tokens (also set in the server's `GOOGLE_CLIENT_IDS`) | empty: Google sign-in disabled |
| `carrom.facebookAppId`, `carrom.facebookClientToken` | Facebook Login | unset: Facebook sign-in disabled |

Cleartext HTTP is only allowed to `10.0.2.2`/`localhost` (see `res/xml/network_security_config.xml`); use HTTPS everywhere else.

## Development & Build Commands
- `./gradlew test`: Run unit tests.
- `./gradlew compileDebugSources`: Verify build compilation.
- `./gradlew assembleDebug`: Build debug APK.
- `CARROM_SERVER_URL=http://localhost:8080 ./gradlew test`: Also run `LiveServerTest`, where two devices play a ranked match against a running server.
- Backend: see [`server/README.md`](server/README.md) (`npm run dev`, `npm test`, `docker compose up`).

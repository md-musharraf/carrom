# Royal Carrom Classic - Project Guidelines & Architecture

## Overview
Royal Carrom Classic is an Android carrom game built with Jetpack Compose, Kotlin Coroutines, and custom physics, rendering and audio synthesis engines. The look is a classic walnut-and-brass table: a mitred wooden frame, a lacquered plywood surface with traditional markings, and turned-wood carrom men.

## Architecture Guidelines
- **Game loop (`CarromViewModel`)**:
  - Frame-driven: the UI calls `onFrame(frameTimeNanos)` from `withFrameNanos` once per vsync while `needsFrames` is true. Physics, the bot's choreography and all effects advance there, on the main thread, so the board is always drawn from a consistent state.
  - `frameTick` is snapshot state, so the board layer redraws in the same frame the simulation advanced; nothing else recomposes during motion.
  - Scoring and turn order are delegated to the pure `CarromRules`; the bot's turn is animated by `AiShotDirector`.
- **Rendering (`ui/board`)**:
  - `CarromBoardCanvas.kt`: two layers. The static board (`BoardPainter`) lives in an offscreen-composited layer that is only re-rasterised when the theme changes; the dynamic layer draws discs, the aim guide and effects each frame.
  - `PiecePainter.kt`: turned-wood discs and strikers with origin-centred, cached brushes (drawing a disc allocates nothing).
  - `AimGuidePainter.kt`: dotted, marching aim guide; the struck disc's path turns gold when it is predicted to drop.
- **Physics (`CarromPhysicsEngine`)**:
  - Sub-steps scale with real frame time (16 per 60 Hz frame), so motion is identical at 60, 90 or 120 Hz and discs never tunnel.
  - Boric-powder glide friction, cushion damping, pocket guidance and a time-based pocket-drop animation.
  - `calculateTrajectory` uses the same friction model as the live simulation and predicts contacts, pots and striker fouls.
- **UI design system (`theme`, `ui/components`)**:
  - `CarromPalette` holds every colour; serif display typography for titles and scores.
  - `ClassicComponents.kt` (panels, brass buttons, medallions, sheets, selectors) and `Glyphs.kt` (vector icons) keep every screen consistent.
- **Audio & Haptics**:
  - `SoundSynthesizer.kt`: procedural acoustic synthesis (wood clacks, cushion thumps, pocket drops) via `SoundPool`.
  - `HapticController.kt`: scaled tactile feedback; the baseline rail ticks once per detent.
- **Bot AI**:
  - `CarromAIEngine.kt`: geometric ray-casting with obstruction checks; `AiShotDirector.kt` eases it through think → slide → aim → draw back → release.

## Development & Build Commands
- `./gradlew test`: Run unit tests.
- `./gradlew compileDebugSources`: Verify build compilation.
- `./gradlew assembleDebug`: Build debug APK.

# Royal Carrom Classic - Project Guidelines & Architecture

## Overview
Royal Carrom Classic is a high-performance Android Carrom game built with Jetpack Compose, Kotlin Coroutines, and a custom physics and audio synthesis engine.

## Architecture Guidelines
- **UI & Rendering**:
  - `CarromBoardCanvas.kt`: Zero-allocation Canvas drawing loop. All brushes, shaders, and path effects are pre-cached.
  - Isolated physics tick collection via `physicsTickFlow: StateFlow<Long>` to isolate recompositions from outer UI hierarchies.
- **Physics Engine**:
  - `CarromPhysicsEngine.kt`: 16-substep physics integrator with boric acid powder glide friction, tangential cushion damping, and continuous collision detection (CCD).
- **Audio & Haptics**:
  - `SoundSynthesizer.kt`: Multi-harmonic procedural acoustic synthesis (composite wood clacks, cushion thumps, and velvet pocket drops) utilizing Android `SoundPool`.
  - `HapticController.kt`: Scaled tactile feedback for collisions, slider sliding, and pocket scores.
- **Bot AI**:
  - `CarromAIEngine.kt`: Geometric ray-casting with multi-bounce pathing and obstruction clearance checks.

## Development & Build Commands
- `./gradlew test`: Run unit tests.
- `./gradlew compileDebugSources`: Verify build compilation.
- `./gradlew assembleDebug`: Build debug APK.


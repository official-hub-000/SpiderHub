# SpiderHub — WEBVERSE: Multiverse (Native Android)

Native Android Kotlin + Jetpack Compose + OpenGL ES 3.0 mobile action game with all 3D models and procedural textures bundled directly inside the APK (`app/src/main/assets/models/`).

## Features
- **Zero Sign-In & Offline Ready:** All 9 3D models (`apocalyptic_city.glb`, `kage.obj`, `husk.obj`, `citizen.obj`, `citizen_b.obj`, `spire.obj`, `beacon.obj`, `portal.obj`, `shard.obj`) and `LICENSES.md` are packaged in `app/src/main/assets/models/`.
- **OpenGL ES 3.0 Renderer:** Real-time `.obj` and binary glTF 2.0 (`.glb`) parsing, mipmapped procedural PBR-style textures (tower facades, wet asphalt, carbon-fiber suit, Inkverse halftone hatching), Fresnel rim lighting, and height-based atmospheric fog.
- **Physics & Traversal:** Pendulum web-swinging, web-zip dash, rooftop landings, wall-running, combat strikes against hostile Husks, NPC Citizens & Wardens, Chrono-Shard pickups, and live `NEON-NOIR` ↔ `INKVERSE` dimension shifting.
- **Mobile Controls & Audio:** Jetpack Compose HUD with dual-touch analog thumbstick, camera drag look, action buttons (`SWING`, `JUMP`, `ZIP`, `STRIKE`, `SHIFT`), `3D ASSETS` inspector modal, and synthesized `AudioTrack` SFX.

## Build (Android Studio / Gradle)
- **JDK:** 21
- **Android SDK:** Compile/Target 36, Min SDK 24
- **Build Debug APK:** `./gradlew assembleDebug` (or open project root in Android Studio).

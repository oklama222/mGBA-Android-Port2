# mGBA Link — Android GBA Emulator with Dolphin Link

An Android port of mGBA 0.10.5 with Dolphin GameCube-link support (Four Swords Adventures, Pokémon Colosseum/XD, Crystal Chronicles, etc.).

## What this is

This is an **Android app** — it cannot run on Replit. Replit is used for code storage and editing only. To build an APK, follow **BUILD_GUIDE.md**.

## Stack

- **Language**: Kotlin (Android UI) + C (mGBA native core via JNI)
- **Build system**: Gradle (Kotlin DSL) + CMake for the native layer
- **AGP**: 8.5.2 | **Kotlin**: 1.9.24 | **compileSdk**: 35 | **minSdk**: 24
- **Target ABI**: `arm64-v8a` (covers all phones from the last ~7 years)
- **mGBA core**: bundled in `mgba-core/` — unmodified mGBA 0.10.5 source

## Project layout

```
app/src/main/
  cpp/
    CMakeLists.txt        — wires mgba-core into the Android build
    mgba_jni.c            — JNI bridge between Kotlin and the C core
  java/com/example/mgbalink/
    NativeBridge.kt       — JNI declarations
    MainActivity.kt       — ROM picking, lifecycle
    EmulatorCore.kt       — frame-timed emulation loop
    EmulatorView.kt       — Canvas-based frame renderer
    TouchControlsView.kt  — on-screen D-pad and buttons
    AudioPlayer.kt        — AudioTrack audio streaming
    DolphinLinkDialog.kt  — Dolphin IP connect UI
  res/
    layout/activity_main.xml
    values/strings.xml
  AndroidManifest.xml
mgba-core/                — mGBA 0.10.5 C source (bundled, unmodified)
build.gradle.kts          — top-level Gradle file
app/build.gradle.kts      — app module config
```

## How to build

See **BUILD_GUIDE.md** for the full step-by-step guide.

**Quick summary:**
1. Download project zip from Replit → unzip on your PC/Mac
2. Open the folder in Android Studio (2024.x+)
3. Install NDK 27.x and CMake 3.22.1+ via SDK Manager if prompted
4. Build → Build APK(s) → find the APK at `app/build/outputs/apk/debug/app-debug.apk`

## Code review notes (July 2026)

Two bugs were found and fixed in `app/src/main/cpp/mgba_jni.c`:

1. **Pixel channel swap** (`nativeRunFrameAndRender`): mGBA's native format is ABGR
   (R in low byte), but Android `ARGB_8888` needs ARGB (R in high byte). A direct
   `memcpy` produced red/blue-swapped colors. Fixed with a per-pixel R↔B swap loop.

2. **`blip_set_rates` called per audio frame** (`nativeRenderAudio`): This resets the
   resampler's state each call, corrupting buffered audio. Moved to ROM load time in
   `nativeLoadRom`.

No compile errors were found — all headers are present, API signatures match, CMake
paths are correct, and JNI function names align with Kotlin declarations.

## User preferences

_None recorded yet._

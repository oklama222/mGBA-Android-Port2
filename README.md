# mGBA Link — an Android port of mGBA 0.10.5

A from-scratch Android front end for mGBA's core, built specifically to expose
the **Dolphin GameCube-link** feature (Four Swords Adventures, Crystal
Chronicles, Pokémon Colosseum/XD, etc.) on a phone — since mGBA has no
official Android build to start from (confirmed: no `android/` folder in the
source, and mGBA's own FAQ asks for Android/iOS volunteers).

This was written and researched directly against the mGBA 0.10.5 source (the
tarball you uploaded), reusing the exact same core calls mGBA's own Qt
frontend uses — see the comments in `mgba_jni.c` for the specific files that
were used as reference.

**Important:** this was written in a sandbox with no Android SDK/NDK and no
internet access, so none of it has been compiled. Everything here is based on
careful reading of the actual mGBA source (function signatures, struct
layouts, the exact Dolphin-connect call sequence, etc.), not guesswork — but
the very first build will likely surface a small number of real compile
errors (a wrong header path, a version-specific API tweak). That's normal for
a project like this; send me the exact error text and we'll fix it.

## 1. Setup

mGBA's source (from your mgba-0_10_5_tar.gz) is already included in
`mgba-core/` — no extraction or copying needed on your end.

1. Unzip this project.
2. Open the `mgba-android/` folder in Android Studio (a recent version —
   2024.x or later; it'll prompt you to install a matching NDK/CMake if
   needed, just accept).
3. Let Gradle sync. First native build will take a few minutes (it's
   compiling zlib/libpng/sqlite3/lzma from source, plus the GBA core).
4. Run on a real device or an arm64 emulator image.

## 2. What's implemented

- Loading a GBA ROM via the system file picker
- Persistent battery save (SRAM), one file per ROM, kept in app-private storage
- Rendering (direct blit, no filtering — crisp pixels) and audio
- On-screen D-pad (supports diagonals) + A/B/L/R/Start/Select, multi-touch
- **Dolphin Link**: top-right button opens a dialog to enter Dolphin's IP and
  connect/disconnect — wired to the same `GBASIODolphinConnect` /
  `GBASIOSetDriver(..., SIO_JOYBUS)` calls the desktop Qt build uses.

### Using the Dolphin link
1. On your PC, run Dolphin with a GBA-link-compatible GameCube game, and set
   it to wait for a GBA connection (rather than "integrated"/emulated GBA).
2. Make sure your phone and PC are on the same Wi-Fi/LAN.
3. In the app, load a GBA ROM first (the link only attaches to a running GBA
   core), then tap **Dolphin Link** and enter your PC's LAN IP address.
4. Tap Connect. It uses mGBA's default ports (54970 data / 49420 clock) —
   same as the desktop version.

## 3. Known limitations (v1)

- **GBA ROMs only** — a GB/GBC ROM will be rejected rather than mis-rendered
  (the Dolphin link is GBA-only anyway, and this kept the video buffer logic
  simple). Lifting this is a reasonably small follow-up.
- One CPU architecture built for now (`arm64-v8a`) — covers effectively all
  phones from the last several years, but 32-bit devices need adding back to
  `abiFilters` in `app/build.gradle.kts`.
- No save states, cheats, rewind, `.zip` ROM support, or a proper app icon yet.
- No device-to-device link cable (only the Dolphin TCP link is wired up).
- Rendering is a plain Canvas blit; fine on any modern phone for a
  240x160-sourced image, but a GLSurfaceView path would be smoother on very
  low-end hardware — noted as a possible upgrade, not done here.

## 4. Project layout

```
mgba-android/
  mgba-core/                ← mGBA's own source (bundled as-is, unmodified)
  app/src/main/cpp/
    CMakeLists.txt          ← wires mgba-core's core lib into this app
    mgba_jni.c              ← the entire native bridge
  app/src/main/java/com/example/mgbalink/
    NativeBridge.kt         ← JNI declarations
    MainActivity.kt         ← ROM picking, lifecycle
    EmulatorCore.kt         ← the frame-timed emulation loop
    EmulatorView.kt         ← draws the current frame
    TouchControlsView.kt    ← on-screen D-pad/buttons
    AudioPlayer.kt          ← AudioTrack streaming
    DolphinLinkDialog.kt    ← the Dolphin connect UI
```

## 5. If the build fails

Almost certainly one of: a header mGBA's CMake generates that isn't on the
include path yet, an NDK/AGP version mismatch, or a third-party dep
(zlib/libpng/sqlite3/lzma) needing a small patch for the NDK toolchain. Paste
the exact error from Android Studio's Build output and we'll go through it.

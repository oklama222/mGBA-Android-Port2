# Build Guide — mGBA Link Android APK

This project is an Android app and must be built with **Android Studio** on
your PC/Mac. Replit is used only for code editing/storage — it cannot build
Android apps.

---

## Prerequisites

| Tool | Version | Where to get it |
|------|---------|-----------------|
| Android Studio | 2024.x or later ("Koala" or newer) | https://developer.android.com/studio |
| JDK | 17+ (bundled with Android Studio) | included |
| NDK | 27.x (Android Studio installs it for you — see Step 3) | via SDK Manager |
| CMake | 3.22.1+ (Android Studio installs it for you — see Step 3) | via SDK Manager |

A real Android phone **or** an arm64 Android emulator image (API 24+, Android 7.0+) is needed to run the app.

---

## Step 1 — Get the project onto your machine

Download this project from Replit (**three dots → Download as zip**), then
unzip it. You should have a folder that looks like:

```
mgba-android/
  app/
  mgba-core/        ← mGBA's C source (already bundled)
  build.gradle.kts
  ...
```

> ⚠️ Make sure `mgba-core/` is present after unzipping — the build will fail
> immediately without it.

---

## Step 2 — Open in Android Studio

1. Launch Android Studio.
2. **File → Open** (or "Open" on the welcome screen).
3. Navigate to the **`mgba-android/`** folder (the one with `build.gradle.kts` at the top level) and click **OK**.
4. Android Studio will load the project and begin a Gradle sync. Wait for it to finish (bottom status bar goes quiet).

---

## Step 3 — Install the NDK and CMake (first time only)

When the project first opens, Android Studio will likely show a banner saying
the NDK / CMake is missing. Accept any automatic install prompts, **or** do it
manually:

1. **Tools → SDK Manager → SDK Tools tab**
2. Check **NDK (Side by side)** — pick the latest 27.x version.
3. Check **CMake** — pick version **3.22.1** or any newer 3.x.
4. Click **OK / Apply** and let the downloads finish.

After installation, Gradle will re-sync automatically.

---

## Step 4 — Build the APK

### Debug APK (easiest — no signing needed)

In the menu bar:

```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

Android Studio compiles the Kotlin code **and** the native C core (mGBA). The
first native build takes several minutes (it compiles zlib, libpng, sqlite3,
lzma, and the GBA core from source via CMake). Subsequent builds are much
faster.

When it finishes you'll see a toast notification:

> **APK(s) generated successfully** — click "locate" to find the file.

The APK is at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Release APK (for distribution / sideloading without ADB)

1. **Build → Generate Signed Bundle / APK → APK → Next**
2. Create a keystore (or use an existing one).
3. Fill in the alias, passwords, and certificate info.
4. Choose **release** build variant → **Finish**.

Output: `app/build/outputs/apk/release/app-release.apk`

---

## Step 5 — Install on your phone

**Option A — Android Studio (USB cable or wireless debugging)**

Connect your phone (enable *Developer Options → USB Debugging*), then click
the green ▶ **Run** button. Android Studio installs and launches automatically.

**Option B — ADB (command line)**

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Option C — Manual sideload**

Copy the APK to your phone, open it with a file manager, and install it. You'll
need to allow installs from unknown sources in your phone settings first.

---

## If the build fails

The README notes this was written without a live Android SDK, so the first
build may surface a small number of compile errors. Common ones:

| Error message | Fix |
|---|---|
| `Can't find mGBA's source at .../mgba-core` | Make sure `mgba-core/` is present at the project root |
| `No version of NDK matched the requested version` | Install NDK 27.x via SDK Manager (Step 3) |
| `CMake '3.22.1' was not found` | Install CMake 3.22.1+ via SDK Manager (Step 3) |
| `error: unknown type name '...'` in a `.c` file | Paste the full error here — likely a missing include path, easy to fix |
| Gradle sync fails with `Could not resolve ...` | Check your internet connection; Android Studio needs to download Gradle plugins |

For any other native (`mgba_jni.c` / CMake) error, copy the full text from
**Build → Build Output** and share it — the error message will pinpoint
exactly what needs patching.

---

## What the app does once running

- **Load ROM**: tap the folder icon → pick a `.gba` file.
- **Controls**: on-screen D-pad + A/B/L/R/Start/Select (multi-touch).
- **Dolphin Link**: tap the top-right button → enter your PC's LAN IP →
  Connect. Requires Dolphin running on the same Wi-Fi with a GBA-link-
  compatible GameCube game (e.g. Four Swords Adventures, Pokémon Colosseum).
- **Saves**: battery saves (SRAM) are stored automatically in app-private
  storage, one file per ROM.

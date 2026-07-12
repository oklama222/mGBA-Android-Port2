#!/usr/bin/env bash
# Installs the Android SDK/NDK + Gradle (nothing here needs Android Studio)
# and builds the debug APK. Run from inside the mgba-android/ folder:
#   bash build.sh
set -e

echo "== JDK 17 =="
sudo apt-get update -y -qq
sudo apt-get install -y -qq openjdk-17-jdk

echo "== Android SDK command-line tools =="
export ANDROID_HOME="$HOME/android-sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"
if [ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
  cd /tmp
  curl -sL -o cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-14742923_latest.zip
  unzip -q cmdline-tools.zip
  mkdir -p "$ANDROID_HOME/cmdline-tools/latest"
  mv cmdline-tools/* "$ANDROID_HOME/cmdline-tools/latest/"
  cd -
fi
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "== SDK platform, build-tools, NDK, CMake (accepting licenses) =="
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;26.3.11579264" "cmake;3.22.1"

echo "== Gradle via SDKMAN =="
if [ ! -d "$HOME/.sdkman" ]; then
  curl -s "https://get.sdkman.io" | bash
fi
set +e
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install gradle 8.7
set -e

echo "== Building (this compiles zlib/libpng/sqlite3/lzma/the core — several minutes) =="
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
gradle assembleDebug

echo
echo "############################################################"
echo "Done. APK: $SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
echo "############################################################"

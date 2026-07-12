cmake_minimum_required(VERSION 3.22)
project(mgba_jni C)

# ---------------------------------------------------------------------------
# mGBA's own source is bundled directly at mgba-android/mgba-core/ (unmodified,
# straight from mgba-0_10_5_tar.gz) — nothing to extract or place manually.
# ---------------------------------------------------------------------------
get_filename_component(MGBA_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../mgba-core" ABSOLUTE)

if(NOT EXISTS "${MGBA_ROOT}/CMakeLists.txt")
    message(FATAL_ERROR
        "Can't find mGBA's source at ${MGBA_ROOT}. It should have shipped "
        "inside this project — check that mgba-core/ wasn't stripped out "
        "when unzipping.")
endif()

# ---------------------------------------------------------------------------
# Configure the mGBA core build BEFORE pulling it in. These are the same
# CACHE variables its own CMakeLists.txt defines with option()/set() — setting
# them first here means the values below win, and its own script won't
# clobber them since a cache entry with that name already exists.
#
# Goal: build ONLY the core (no Qt/SDL/CLI/tests), as a static library, with
# only the dependencies that are vendored in mGBA's own src/third-party (so
# nothing needs a separately cross-compiled system library for Android).
# ---------------------------------------------------------------------------
set(BUILD_SHARED OFF CACHE BOOL "" FORCE)
set(BUILD_STATIC ON CACHE BOOL "" FORCE)
set(M_CORE_GBA ON CACHE BOOL "" FORCE)
set(M_CORE_GB ON CACHE BOOL "" FORCE)

# Vendored under src/third-party — safe to leave on.
set(USE_ZLIB ON CACHE BOOL "" FORCE)
set(USE_PNG ON CACHE BOOL "" FORCE)
set(USE_LZMA ON CACHE BOOL "" FORCE)
set(USE_SQLITE3 ON CACHE BOOL "" FORCE)

# NOT vendored (need a system/external lib we don't have for Android) — off
# for this first build. None of these are required to play a game or use the
# Dolphin link; they can be revisited later.
set(USE_LIBZIP OFF CACHE BOOL "" FORCE)
set(USE_MINIZIP OFF CACHE BOOL "" FORCE)
set(USE_LUA OFF CACHE BOOL "" FORCE)
set(USE_FFMPEG OFF CACHE BOOL "" FORCE)
set(USE_EPOXY OFF CACHE BOOL "" FORCE)
set(USE_DEBUGGERS OFF CACHE BOOL "" FORCE)
set(USE_GDB_STUB OFF CACHE BOOL "" FORCE)
set(USE_EDITLINE OFF CACHE BOOL "" FORCE)
set(USE_ELF OFF CACHE BOOL "" FORCE)
set(BUILD_QT OFF CACHE BOOL "" FORCE)
set(BUILD_SDL OFF CACHE BOOL "" FORCE)

# mGBA's CMakeLists.txt unconditionally calls include(CPack) at the bottom.
# CPack requires these two files to exist or it errors out at configure time.
# Point them at real files from the mGBA source tree so the check passes.
# (We're not actually building packages — this is just to satisfy CPack's guards.)
set(CPACK_PACKAGE_DESCRIPTION_FILE "${MGBA_ROOT}/README.md")
set(CPACK_RESOURCE_FILE_LICENSE    "${MGBA_ROOT}/LICENSE")

# Pulling this in with EXCLUDE_FROM_ALL is what makes mGBA's own CMakeLists.txt
# take the "LIBMGBA_ONLY" path (it detects this via get_directory_property),
# skipping frontends/tools/tests and building just the core.
add_subdirectory(${MGBA_ROOT} mgba-core-build EXCLUDE_FROM_ALL)

# ---------------------------------------------------------------------------
# Our JNI bridge.
# ---------------------------------------------------------------------------
add_library(mgba-jni SHARED mgba_jni.c)

target_include_directories(mgba-jni PRIVATE
    ${MGBA_ROOT}/include
)

find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

# Target name is plain "mgba" (BINARY_NAME default) since BUILD_SHARED=OFF
# means mGBA's CMakeLists.txt defines it as add_library(mgba STATIC ...)
# rather than the "mgba-static" name it uses when building both variants.
target_link_libraries(mgba-jni
    mgba
    ${log-lib}
    ${android-lib}
    ${jnigraphics-lib}
)

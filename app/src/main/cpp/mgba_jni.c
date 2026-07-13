/*
 * JNI bridge between the Android/Kotlin side and mGBA's core library.
 *
 * Scope for this first version: GBA ROMs only (the Dolphin link is GBA-only
 * anyway, and it keeps the video buffer size fixed and simple). Loading a
 * GB/GBC ROM will cleanly fail rather than render incorrectly.
 *
 * This mirrors what the Qt frontend does (see src/platform/qt/CoreController.cpp
 * and DolphinConnector.cpp in the mGBA source) rather than inventing a new
 * integration path — same core calls, same order, just no Qt in between.
 */
#include <jni.h>
#include <stdlib.h>
#include <stdbool.h>
#include <string.h>
#include <fcntl.h>
#include <pthread.h>

#include <android/log.h>
#include <android/bitmap.h>

#include <mgba/core/core.h>
#include <mgba/core/config.h>
#include <mgba/core/blip_buf.h>
#include <mgba/internal/gba/gba.h>
#include <mgba/internal/gba/input.h>
#include <mgba/internal/gba/sio.h>
#include <mgba/internal/gba/sio/dolphin.h>
#include <mgba-util/vfs.h>
#include <mgba-util/socket.h>

#define TAG "mgba-jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define PORT "android"
#define AUDIO_SAMPLE_RATE 32768

// ---------------------------------------------------------------------------
// Global emulator state. This app runs one core instance at a time, so plain
// globals (guarded by a mutex) are simpler than threading a context pointer
// through every JNI call.
// ---------------------------------------------------------------------------
static struct mCore* g_core = NULL;
static color_t* g_videoBuffer = NULL;
static unsigned g_width = 0;
static unsigned g_height = 0;

static struct GBASIODolphin g_dolphin;
static bool g_dolphinCreated = false;
static bool g_dolphinAttached = false;

static pthread_mutex_t g_coreMutex = PTHREAD_MUTEX_INITIALIZER;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
static void closeCoreLocked(void) {
    if (g_dolphinAttached && g_core) {
        struct GBA* gba = (struct GBA*) g_core->board;
        GBASIOSetDriver(&gba->sio, NULL, SIO_JOYBUS);
        g_dolphinAttached = false;
    }
    if (g_dolphinCreated) {
        GBASIODolphinDestroy(&g_dolphin);
        g_dolphinCreated = false;
    }
    if (g_core) {
        g_core->unloadROM(g_core);
        g_core->deinit(g_core);
        g_core = NULL;
    }
    free(g_videoBuffer);
    g_videoBuffer = NULL;
    g_width = g_height = 0;
}

// ---------------------------------------------------------------------------
// Java_com_example_mgbalink_NativeBridge_nativeLoadRom
// ---------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL
Java_com_example_mgbalink_NativeBridge_nativeLoadRom(JNIEnv* env, jclass clazz,
                                                      jbyteArray romBytes,
                                                      jstring savePath) {
    (void) clazz;
    pthread_mutex_lock(&g_coreMutex);

    closeCoreLocked();

    jsize len = (*env)->GetArrayLength(env, romBytes);
    jbyte* bytes = (*env)->GetByteArrayElements(env, romBytes, NULL);
    if (!bytes) {
        pthread_mutex_unlock(&g_coreMutex);
        return JNI_FALSE;
    }

    // VFileMemChunk copies the data into its own buffer immediately, so it's
    // safe to release the JNI array right after this call.
    struct VFile* romVf = VFileMemChunk(bytes, (size_t) len);
    (*env)->ReleaseByteArrayElements(env, romBytes, bytes, JNI_ABORT);

    if (!romVf) {
        LOGE("VFileMemChunk failed");
        pthread_mutex_unlock(&g_coreMutex);
        return JNI_FALSE;
    }

    struct mCore* core = mCoreFindVF(romVf);
    if (!core) {
        LOGE("mCoreFindVF: unrecognized ROM format");
        romVf->close(romVf);
        pthread_mutex_unlock(&g_coreMutex);
        return JNI_FALSE;
    }

    if (!core->init(core)) {
        LOGE("core->init failed");
        romVf->close(romVf);
        pthread_mutex_unlock(&g_coreMutex);
        return JNI_FALSE;
    }

    if (core->platform(core) != mPLATFORM_GBA) {
        // Deliberately out of scope for this build: see file header comment.
        LOGE("Only GBA ROMs are supported in this build (got a non-GBA platform)");
        core->deinit(core);
        romVf->close(romVf);
        pthread_mutex_unlock(&g_coreMutex);
        return JNI_FALSE;
    }

    unsigned width = 0, height = 0;
    core->desiredVideoDimensions(core, &width, &height);
    color_t* videoBuffer = calloc((size_t) width * height, sizeof(color_t));
    if (!videoBuffer) {
        core->deinit(core);
        romVf->close(romVf);
        pthread_mutex_unlock(&g_coreMutex);
        return JNI_FALSE;
    }
    core->setVideoBuffer(core, videoBuffer, width);

    mCoreInitConfig(core, PORT);
    struct mCoreOptions opts = {0};
    opts.useBios = false; // force the built-in HLE BIOS; no bios file shipped
    opts.volume = 0x100;
    opts.audioSync = false;
    opts.videoSync = false;
    opts.rewindEnable = false;
    opts.audioBuffers = 1024;
    opts.logLevel = 0;
    mCoreConfigLoadDefaults(&core->config, &opts);
    mCoreLoadConfig(core);

    if (!core->loadROM(core, romVf)) {
        LOGE("core->loadROM failed");
        core->deinit(core);
        free(videoBuffer);
        pthread_mutex_unlock(&g_coreMutex);
        return JNI_FALSE;
    }

    // Persistent battery save, tied to a path Kotlin derives from the ROM
    // (see MainActivity) so progress survives closing the app.
    const char* savePathUtf8 = (*env)->GetStringUTFChars(env, savePath, NULL);
    struct VFile* saveVf = VFileOpen(savePathUtf8, O_CREAT | O_RDWR);
    (*env)->ReleaseStringUTFChars(env, savePath, savePathUtf8);
    if (saveVf) {
        core->loadSave(core, saveVf);
    } else {
        LOGE("Could not open save file — playing without persistent save");
    }

    core->reset(core);

    GBASIODolphinCreate(&g_dolphin);
    g_dolphinCreated = true;
    g_dolphinAttached = false;

    g_core = core;
    g_videoBuffer = videoBuffer;
    g_width = width;
    g_height = height;

    pthread_mutex_unlock(&g_coreMutex);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_example_mgbalink_NativeBridge_nativeUnloadRom(JNIEnv* env, jclass clazz) {
    (void) env; (void) clazz;
    pthread_mutex_lock(&g_coreMutex);
    closeCoreLocked();
    pthread_mutex_unlock(&g_coreMutex);
}

JNIEXPORT jint JNICALL
Java_com_example_mgbalink_NativeBridge_nativeGetWidth(JNIEnv* env, jclass clazz) {
    (void) env; (void) clazz;
    return (jint) g_width;
}

JNIEXPORT jint JNICALL
Java_com_example_mgbalink_NativeBridge_nativeGetHeight(JNIEnv* env, jclass clazz) {
    (void) env; (void) clazz;
    return (jint) g_height;
}

JNIEXPORT jint JNICALL
Java_com_example_mgbalink_NativeBridge_nativeGetSampleRate(JNIEnv* env, jclass clazz) {
    (void) env; (void) clazz;
    return AUDIO_SAMPLE_RATE;
}

// ---------------------------------------------------------------------------
// Runs exactly one video frame and blits it straight into the given Bitmap's
// native pixel memory (which must be ARGB_8888 and g_width x g_height).
// mGBA's 32-bit color_t layout (R in bits 0-7, G 8-15, B 16-23, A 24-31 — see
// include/mgba/core/interface.h) is byte-identical to Android's ARGB_8888
// buffer layout on a little-endian device, so this is a straight memcpy, no
// per-pixel conversion.
// ---------------------------------------------------------------------------
JNIEXPORT void JNICALL
Java_com_example_mgbalink_NativeBridge_nativeRunFrameAndRender(JNIEnv* env, jclass clazz,
                                                                jobject bitmap) {
    (void) clazz;
    pthread_mutex_lock(&g_coreMutex);
    if (!g_core) {
        pthread_mutex_unlock(&g_coreMutex);
        return;
    }

    g_core->runFrame(g_core);

    void* pixels = NULL;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) == ANDROID_BITMAP_RESULT_SUCCESS && pixels) {
        memcpy(pixels, g_videoBuffer, (size_t) g_width * g_height * sizeof(color_t));
        AndroidBitmap_unlockPixels(env, bitmap);
    }

    pthread_mutex_unlock(&g_coreMutex);
}

JNIEXPORT void JNICALL
Java_com_example_mgbalink_NativeBridge_nativeAddKey(JNIEnv* env, jclass clazz, jint keyBit) {
    (void) env; (void) clazz;
    pthread_mutex_lock(&g_coreMutex);
    if (g_core) {
        g_core->addKeys(g_core, 1u << keyBit);
    }
    pthread_mutex_unlock(&g_coreMutex);
}

JNIEXPORT void JNICALL
Java_com_example_mgbalink_NativeBridge_nativeClearKey(JNIEnv* env, jclass clazz, jint keyBit) {
    (void) env; (void) clazz;
    pthread_mutex_lock(&g_coreMutex);
    if (g_core) {
        g_core->clearKeys(g_core, 1u << keyBit);
    }
    pthread_mutex_unlock(&g_coreMutex);
}

// ---------------------------------------------------------------------------
// Pulls whatever audio has accumulated since the last call into outBuffer
// (interleaved stereo 16-bit PCM). Returns the number of stereo frames
// written (i.e. outBuffer must be at least 2x this many shorts).
// ---------------------------------------------------------------------------
JNIEXPORT jint JNICALL
Java_com_example_mgbalink_NativeBridge_nativeRenderAudio(JNIEnv* env, jclass clazz,
                                                          jshortArray outBuffer) {
    (void) clazz;
    pthread_mutex_lock(&g_coreMutex);
    if (!g_core) {
        pthread_mutex_unlock(&g_coreMutex);
        return 0;
    }

    jsize capacity = (*env)->GetArrayLength(env, outBuffer) / 2; // stereo frames
    blip_t* left = g_core->getAudioChannel(g_core, 0);
    blip_t* right = g_core->getAudioChannel(g_core, 1);
    int32_t clockRate = g_core->frequency(g_core);

    blip_set_rates(left, clockRate, AUDIO_SAMPLE_RATE);
    blip_set_rates(right, clockRate, AUDIO_SAMPLE_RATE);

    int available = blip_samples_avail(left);
    if (available > capacity) {
        available = (int) capacity;
    }

    jshort* out = (*env)->GetShortArrayElements(env, outBuffer, NULL);
    if (out) {
        if (available > 0) {
            blip_read_samples(left, (short*) out, available, 1);
            blip_read_samples(right, ((short*) out) + 1, available, 1);
        }
        (*env)->ReleaseShortArrayElements(env, outBuffer, out, 0);
    }

    pthread_mutex_unlock(&g_coreMutex);
    return (jint) available;
}

// ---------------------------------------------------------------------------
// Dolphin link — same three calls Qt's DolphinConnector/CoreController make.
// dataPort/clockPort of 0 means "use the defaults" (54970 / 49420).
//
// Two bugs fixed vs the naive implementation:
//
//  1. Byte-order: inet_pton stores the IPv4 address in NETWORK byte order,
//     but SocketConnectTCP does htonl(address->ipv4) expecting HOST byte
//     order — so passing an inet_pton result directly reverses the octets
//     (127.0.0.1 becomes 1.0.0.127).  SocketResolveHost stores host order,
//     matching what SocketConnectTCP wants.
//
//  2. Mutex deadlock: GBASIODolphinConnect calls SocketConnectTCP which
//     calls the blocking connect() syscall.  If Dolphin isn't reachable
//     this can block for up to ~20 s (OS TCP connect timeout), during which
//     the emulator frame loop (which also needs g_coreMutex) is completely
//     frozen.  We split the operation: hold the mutex only for the fast
//     driver-detach and driver-install steps; release it around the slow
//     TCP connect so frames keep rendering normally.
// ---------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL
Java_com_example_mgbalink_NativeBridge_nativeDolphinConnect(JNIEnv* env, jclass clazz,
                                                             jstring ip, jint dataPort,
                                                             jint clockPort) {
    (void) clazz;

    // --- Step 1: resolve address (may call getaddrinfo) — no mutex needed ---
    const char* ipUtf8 = (*env)->GetStringUTFChars(env, ip, NULL);
    struct Address address;
    int resolveErr = SocketResolveHost(ipUtf8, &address);
    (*env)->ReleaseStringUTFChars(env, ip, ipUtf8);
    if (resolveErr) {
        LOGE("SocketResolveHost failed: %d", resolveErr);
        return JNI_FALSE;
    }

    // --- Step 2: lock briefly to detach any existing connection, then
    //     reset the dolphin struct so the emulator won't touch it while
    //     we're in the slow TCP connect below. ---
    pthread_mutex_lock(&g_coreMutex);
    if (!g_core || !g_dolphinCreated) {
        pthread_mutex_unlock(&g_coreMutex);
        return JNI_FALSE;
    }
    if (g_dolphinAttached) {
        struct GBA* gba = (struct GBA*) g_core->board;
        GBASIOSetDriver(&gba->sio, NULL, SIO_JOYBUS);
        g_dolphinAttached = false;
    }
    // Destroy (closes any open sockets) and re-create (resets fn-ptrs and
    // sets sockets to INVALID_SOCKET) so the struct is clean for the connect.
    GBASIODolphinDestroy(&g_dolphin);
    GBASIODolphinCreate(&g_dolphin);
    pthread_mutex_unlock(&g_coreMutex);

    // --- Step 3: open TCP sockets — blocking, NO mutex ---
    // GBASIODolphinConnect substitutes the default ports when 0 is passed.
    bool ok = GBASIODolphinConnect(&g_dolphin, &address,
                                   (short) dataPort, (short) clockPort);
    if (!ok) {
        LOGE("GBASIODolphinConnect failed (Dolphin not running or wrong IP?)");
        return JNI_FALSE;
    }

    // --- Step 4: lock again to install the SIO driver ---
    // Re-check g_core: it might have been unloaded while we were connecting.
    pthread_mutex_lock(&g_coreMutex);
    if (!g_core || !g_dolphinCreated) {
        // Core gone — drop the sockets we just opened.
        GBASIODolphinDestroy(&g_dolphin);
        GBASIODolphinCreate(&g_dolphin);
        pthread_mutex_unlock(&g_coreMutex);
        return JNI_FALSE;
    }
    struct GBA* gba = (struct GBA*) g_core->board;
    GBASIOSetDriver(&gba->sio, &g_dolphin.d, SIO_JOYBUS);
    g_dolphinAttached = true;
    pthread_mutex_unlock(&g_coreMutex);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_example_mgbalink_NativeBridge_nativeDolphinDisconnect(JNIEnv* env, jclass clazz) {
    (void) env; (void) clazz;
    pthread_mutex_lock(&g_coreMutex);
    if (g_dolphinAttached && g_core) {
        struct GBA* gba = (struct GBA*) g_core->board;
        GBASIOSetDriver(&gba->sio, NULL, SIO_JOYBUS);
        g_dolphinAttached = false;
    }
    if (g_dolphinCreated) {
        GBASIODolphinDestroy(&g_dolphin);
        // Re-create immediately so the driver is ready for another connect
        // attempt without needing to reload the ROM.
        GBASIODolphinCreate(&g_dolphin);
    }
    pthread_mutex_unlock(&g_coreMutex);
}

JNIEXPORT jboolean JNICALL
Java_com_example_mgbalink_NativeBridge_nativeDolphinIsConnected(JNIEnv* env, jclass clazz) {
    (void) env; (void) clazz;
    pthread_mutex_lock(&g_coreMutex);
    bool connected = g_dolphinCreated && GBASIODolphinIsConnected(&g_dolphin);
    pthread_mutex_unlock(&g_coreMutex);
    return connected ? JNI_TRUE : JNI_FALSE;
}

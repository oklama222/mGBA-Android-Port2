package com.example.mgbalink

import android.graphics.Bitmap

/**
 * Thin wrapper over the native (mgba_jni.c) side. Every function here is
 * backed by a JNI implementation of the same name — see
 * app/src/main/cpp/mgba_jni.c.
 */
object NativeBridge {

    init {
        System.loadLibrary("mgba-jni")
    }

    // GBA key bit positions — must match enum GBAKey in
    // include/mgba/internal/gba/input.h exactly.
    const val KEY_A = 0
    const val KEY_B = 1
    const val KEY_SELECT = 2
    const val KEY_START = 3
    const val KEY_RIGHT = 4
    const val KEY_LEFT = 5
    const val KEY_UP = 6
    const val KEY_DOWN = 7
    const val KEY_R = 8
    const val KEY_L = 9

    const val DOLPHIN_DATA_PORT_DEFAULT = 0 // 0 == "use mGBA's default" (54970)
    const val DOLPHIN_CLOCK_PORT_DEFAULT = 0 // 0 == "use mGBA's default" (49420)

    @JvmStatic external fun nativeLoadRom(romBytes: ByteArray, savePath: String): Boolean
    @JvmStatic external fun nativeUnloadRom()

    @JvmStatic external fun nativeGetWidth(): Int
    @JvmStatic external fun nativeGetHeight(): Int
    @JvmStatic external fun nativeGetSampleRate(): Int

    @JvmStatic external fun nativeRunFrameAndRender(bitmap: Bitmap)
    @JvmStatic external fun nativeAddKey(keyBit: Int)
    @JvmStatic external fun nativeClearKey(keyBit: Int)
    @JvmStatic external fun nativeRenderAudio(outBuffer: ShortArray): Int

    @JvmStatic external fun nativeDolphinConnect(ip: String, dataPort: Int, clockPort: Int): Boolean
    @JvmStatic external fun nativeDolphinDisconnect()
    @JvmStatic external fun nativeDolphinIsConnected(): Boolean
}

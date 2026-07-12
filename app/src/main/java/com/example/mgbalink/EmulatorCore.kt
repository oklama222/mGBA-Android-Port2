package com.example.mgbalink

import android.util.Log

/**
 * Owns the "run one frame, blit it" loop on a dedicated thread. The GBA's
 * real refresh rate is ~59.7275 Hz; we pace to that rather than the display's
 * refresh rate so games run at their correct speed.
 */
class EmulatorCore(private val emulatorView: EmulatorView) {

    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile var paused = false

    private val audioPlayer = AudioPlayer()

    private val frameIntervalNanos = (1_000_000_000.0 / 59.7275).toLong()

    fun start() {
        if (running) return
        val width = NativeBridge.nativeGetWidth()
        val height = NativeBridge.nativeGetHeight()
        val bitmap = emulatorView.ensureBitmap(width, height)

        running = true
        paused = false
        audioPlayer.start()

        val t = Thread {
            var nextFrameAt = System.nanoTime()
            while (running) {
                if (paused) {
                    Thread.sleep(16)
                    nextFrameAt = System.nanoTime()
                    continue
                }
                NativeBridge.nativeRunFrameAndRender(bitmap)
                emulatorView.onFrameRendered()

                nextFrameAt += frameIntervalNanos
                val sleepNanos = nextFrameAt - System.nanoTime()
                if (sleepNanos > 0) {
                    try {
                        Thread.sleep(sleepNanos / 1_000_000, (sleepNanos % 1_000_000).toInt())
                    } catch (e: InterruptedException) {
                        Log.w("EmulatorCore", "frame sleep interrupted", e)
                    }
                } else {
                    // We're behind (dropped frame elsewhere) — don't try to catch up
                    // by burning through frames, just resync to now.
                    nextFrameAt = System.nanoTime()
                }
            }
        }
        t.name = "mgba-emu"
        t.isDaemon = true
        thread = t
        t.start()
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    /** Stops the loop and unloads the ROM (flushing the save file). Call before loading a new ROM. */
    fun stop() {
        running = false
        try {
            thread?.join(500)
        } catch (e: InterruptedException) {
            Log.w("EmulatorCore", "interrupted while stopping", e)
        }
        thread = null
        audioPlayer.stop()
        NativeBridge.nativeUnloadRom()
    }
}

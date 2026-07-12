package com.example.mgbalink

import android.util.Log

/**
 * Owns the "run one frame, blit it" loop on a dedicated thread.
 *
 * The GBA's real refresh rate is ~59.7275 Hz; we pace to that so games run at
 * the correct speed.
 *
 * Frame skipping: when the device can't keep up and [maxFrameSkip] > 0,
 * we still call nativeRunFrameAndRender (advancing the game state and writing
 * to the bitmap) but suppress the view-invalidate for up to [maxFrameSkip]
 * consecutive frames. This reduces Android's drawing overhead while the game
 * keeps ticking at full speed.
 */
class EmulatorCore(
    private val emulatorView: EmulatorView,
    private val maxFrameSkip: Int = 0,
    private val soundEnabled: Boolean = true
) {

    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile var paused = false

    private val audioPlayer = AudioPlayer(enabled = soundEnabled)

    private val frameIntervalNanos = (1_000_000_000.0 / 59.7275).toLong()

    fun start() {
        if (running) return
        val width  = NativeBridge.nativeGetWidth()
        val height = NativeBridge.nativeGetHeight()
        val bitmap = emulatorView.ensureBitmap(width, height)

        running = true
        paused  = false
        audioPlayer.start()

        val t = Thread {
            var nextFrameAt  = System.nanoTime()
            var skippedCount = 0

            while (running) {
                if (paused) {
                    Thread.sleep(16)
                    nextFrameAt = System.nanoTime()
                    continue
                }

                // Run the GBA frame (always — this advances game state).
                NativeBridge.nativeRunFrameAndRender(bitmap)
                nextFrameAt += frameIntervalNanos

                val sleepNanos = nextFrameAt - System.nanoTime()

                if (sleepNanos > 0) {
                    // On time — display the frame and sleep until the next one.
                    emulatorView.onFrameRendered()
                    skippedCount = 0
                    try {
                        Thread.sleep(sleepNanos / 1_000_000, (sleepNanos % 1_000_000).toInt())
                    } catch (e: InterruptedException) {
                        Log.w("EmulatorCore", "frame sleep interrupted", e)
                    }
                } else if (maxFrameSkip > 0 && skippedCount < maxFrameSkip) {
                    // Behind schedule and still within the skip budget — don't bother
                    // pushing this frame to the display; proceed immediately to the next frame.
                    skippedCount++
                } else {
                    // Behind and hit the skip limit (or skipping is disabled) — display
                    // the current frame and resync the clock.
                    emulatorView.onFrameRendered()
                    skippedCount = 0
                    nextFrameAt  = System.nanoTime()
                }
            }
        }
        t.name = "mgba-emu"
        t.isDaemon = true
        thread = t
        t.start()
    }

    fun pause()  { paused = true }
    fun resume() { paused = false }

    /** Stops the loop and unloads the ROM (flushing the save file). */
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

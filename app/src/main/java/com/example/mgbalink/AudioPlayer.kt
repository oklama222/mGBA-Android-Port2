package com.example.mgbalink

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Pulls PCM from the emulator core (via NativeBridge.nativeRenderAudio) and
 * streams it to an AudioTrack on a dedicated thread.
 *
 * Pass [enabled] = false to silence all audio output (the thread still runs but
 * discards samples, keeping the core's audio buffer drained so it doesn't stall).
 */
class AudioPlayer(private val enabled: Boolean = true) {

    private var thread: Thread? = null
    @Volatile private var running = false
    private var audioTrack: AudioTrack? = null

    fun start() {
        if (running) return
        running = true

        val sampleRate = NativeBridge.nativeGetSampleRate()

        val track: AudioTrack? = if (enabled) {
            val minBufBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferBytes = maxOf(minBufBytes, sampleRate / 2)

            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                .also { it.play() }
        } else null

        audioTrack = track

        val t = Thread {
            val chunk = ShortArray(4096)
            while (running) {
                val framesWritten = NativeBridge.nativeRenderAudio(chunk)
                if (framesWritten > 0) {
                    // Write to hardware if sound is on; otherwise just drain the buffer.
                    track?.write(chunk, 0, framesWritten * 2)
                } else {
                    Thread.sleep(2)
                }
            }
        }
        t.name = "mgba-audio"
        t.isDaemon = true
        thread = t
        t.start()
    }

    fun stop() {
        running = false
        try {
            thread?.join(500)
        } catch (e: InterruptedException) {
            Log.w("AudioPlayer", "interrupted while stopping", e)
        }
        thread = null
        audioTrack?.let {
            it.stop()
            it.release()
        }
        audioTrack = null
    }
}

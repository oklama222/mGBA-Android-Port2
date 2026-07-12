package com.example.mgbalink

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Pulls whatever PCM the core has generated (via NativeBridge.nativeRenderAudio)
 * and streams it to an AudioTrack, on its own thread so it isn't at the mercy
 * of the render thread's frame pacing.
 */
class AudioPlayer {

    private var thread: Thread? = null
    @Volatile private var running = false
    private var audioTrack: AudioTrack? = null

    fun start() {
        if (running) return
        val sampleRate = NativeBridge.nativeGetSampleRate()
        val minBufBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferBytes = maxOf(minBufBytes, sampleRate / 2) // at least ~0.5s of headroom

        val track = AudioTrack.Builder()
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
        audioTrack = track
        track.play()

        running = true
        val t = Thread {
            // Stereo shorts for roughly one video frame at a time.
            val chunk = ShortArray(4096)
            while (running) {
                val framesWritten = NativeBridge.nativeRenderAudio(chunk)
                if (framesWritten > 0) {
                    track.write(chunk, 0, framesWritten * 2)
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

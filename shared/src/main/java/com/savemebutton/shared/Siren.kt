package com.savemebutton.shared

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

private const val SAMPLE_RATE = 22_050

class Siren {
    @Volatile private var track: AudioTrack? = null
    @Volatile private var thread: Thread? = null

    @Synchronized
    fun start(sound: SirenSound, volume: Float = 1f) {
        stop()
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(SAMPLE_RATE / 4)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        t.setVolume(volume.coerceIn(0f, 1f))
        track = t
        t.play()
        val th = Thread({ feed(t, sound) }, "smb-siren").apply { isDaemon = true }
        thread = th
        th.start()
    }

    @Synchronized
    fun stop() {
        thread?.interrupt()
        thread = null
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        runCatching { track?.release() }
        track = null
    }

    private fun feed(track: AudioTrack, sound: SirenSound) {
        val chunk = SAMPLE_RATE / 20
        val buf = ShortArray(chunk)
        var phase = 0.0
        var t0 = 0.0
        try {
            while (!Thread.currentThread().isInterrupted) {
                for (i in 0 until chunk) {
                    val tNow = t0 + i.toDouble() / SAMPLE_RATE
                    val freq = freqAt(sound, tNow)
                    phase += 2.0 * PI * freq / SAMPLE_RATE
                    buf[i] = (sin(phase) * 0.85 * Short.MAX_VALUE).toInt().toShort()
                }
                t0 += chunk.toDouble() / SAMPLE_RATE
                val written = track.write(buf, 0, chunk)
                if (written < 0) break
            }
        } catch (_: Throwable) {
            // swallow — stop() will release the track
        }
    }

    private fun freqAt(sound: SirenSound, t: Double): Double = when (sound) {
        SirenSound.TWO_TONE -> {
            val phase = ((t * 1000.0) / 250.0).toInt() % 2
            if (phase == 0) 800.0 else 1200.0
        }
        SirenSound.KLAXON -> {
            val period = 0.35
            val frac = (t % period) / period
            600.0 + 900.0 * frac
        }
        SirenSound.WHOOP -> {
            val period = 1.5
            val frac = (t % period) / period
            400.0 + 1000.0 * frac
        }
        SirenSound.PULSE -> {
            val phase = ((t * 1000.0) / 300.0).toInt() % 2
            if (phase == 0) 1000.0 else 0.0
        }
    }
}

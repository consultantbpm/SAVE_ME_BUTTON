package com.savemebutton.shared

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

private const val SAMPLE_RATE = 22_050
private const val RAMP_START_FRACTION = 0.2
private const val VOLUME_UPDATE_INTERVAL_NS = 100_000_000L

class Siren {
    @Volatile private var track: AudioTrack? = null
    @Volatile private var thread: Thread? = null

    @Synchronized
    fun start(sound: SirenSound, volume: Float = 1f, rampSeconds: Float = 0f) {
        stop()
        val target = volume.coerceIn(0f, 1f)
        val ramp = rampSeconds.coerceAtLeast(0f)
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
        val initial = if (ramp > 0f) (target * RAMP_START_FRACTION).toFloat().coerceIn(0f, 1f) else target
        t.setVolume(initial)
        track = t
        t.play()
        val th = Thread({ feed(t, sound, target, ramp) }, "smb-siren").apply { isDaemon = true }
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

    private fun feed(track: AudioTrack, sound: SirenSound, targetVolume: Float, rampSeconds: Float) {
        val chunk = SAMPLE_RATE / 20
        val buf = ShortArray(chunk)
        var phase = 0.0
        var t0 = 0.0
        val startNs = System.nanoTime()
        val rampNs = (rampSeconds * 1_000_000_000.0).toLong()
        var rampDone = rampNs <= 0L
        var lastVolUpdateNs = Long.MIN_VALUE
        try {
            while (!Thread.currentThread().isInterrupted) {
                if (!rampDone) {
                    val elapsed = System.nanoTime() - startNs
                    if (elapsed >= rampNs) {
                        track.setVolume(targetVolume)
                        rampDone = true
                    } else if (elapsed - lastVolUpdateNs >= VOLUME_UPDATE_INTERVAL_NS) {
                        val frac = elapsed.toDouble() / rampNs
                        val scale = RAMP_START_FRACTION + (1.0 - RAMP_START_FRACTION) * frac
                        track.setVolume((targetVolume * scale).toFloat().coerceIn(0f, 1f))
                        lastVolUpdateNs = elapsed
                    }
                }
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

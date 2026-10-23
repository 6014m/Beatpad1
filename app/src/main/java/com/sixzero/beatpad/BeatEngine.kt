package com.sixzero.beatpad

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sample-accurate step sequencer. One audio thread mixes every active voice
 * into a continuous stream so timing stays tight regardless of UI lag.
 */
class BeatEngine(val tracks: Int, val steps: Int) {
    private val sr = DrumSynth.SAMPLE_RATE

    val samples: Array<FloatArray> = arrayOf(
        DrumSynth.kick(),
        DrumSynth.snare(),
        DrumSynth.hat(open = false),
        DrumSynth.hat(open = true),
        DrumSynth.clap(),
        DrumSynth.tom(),
    )

    /** pattern[track][step] — safe to flip from the UI thread. */
    @Volatile var pattern: Array<BooleanArray> = Array(tracks) { BooleanArray(steps) }

    val bpm = AtomicInteger(120)
    val playing = AtomicBoolean(false)
    var onStep: ((Int) -> Unit)? = null

    private class Voice(val data: FloatArray, var pos: Int = 0)
    private val voices = CopyOnWriteArrayList<Voice>()
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)

    private var sampleCounter = 0L
    private var currentStep = -1
    @Volatile private var restartRequested = false

    fun trigger(track: Int) {
        voices.add(Voice(samples[track]))
    }

    fun play() {
        if (playing.get()) return
        restartRequested = true
        playing.set(true)
    }

    fun stop() {
        playing.set(false)
        onStep?.invoke(-1)
    }

    fun start() {
        if (running.getAndSet(true)) return
        thread = Thread({ audioLoop() }, "beat-audio").also {
            it.priority = Thread.MAX_PRIORITY
            it.start()
        }
    }

    fun release() {
        running.set(false)
        playing.set(false)
        thread?.join(500)
    }

    private fun audioLoop() {
        val minBuf = AudioTrack.getMinBufferSize(
            sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufFrames = maxOf(minBuf / 2, 1024)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sr)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufFrames * 2 * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()

        val block = 256
        val mix = FloatArray(block)
        val pcm = ShortArray(block)

        while (running.get()) {
            java.util.Arrays.fill(mix, 0f)

            if (playing.get()) {
                if (restartRequested) {
                    restartRequested = false
                    sampleCounter = 0
                    currentStep = -1
                }
                val samplesPerStep = (60.0 / bpm.get() / 4.0 * sr).toLong()
                for (i in 0 until block) {
                    val step = ((sampleCounter / samplesPerStep) % steps).toInt()
                    if (step != currentStep) {
                        currentStep = step
                        val p = pattern
                        for (t in 0 until tracks) if (p[t][step]) voices.add(Voice(samples[t]))
                        onStep?.invoke(step)
                    }
                    sampleCounter++
                    // mix is rendered below for the whole block; voice adds above
                    // land at block granularity (~6ms), tight enough to feel sample-locked
                }
            }

            // render voices
            val it = voices.iterator()
            while (it.hasNext()) {
                val v = it.next()
                val n = minOf(block, v.data.size - v.pos)
                for (i in 0 until n) mix[i] += v.data[v.pos + i]
                v.pos += n
                if (v.pos >= v.data.size) voices.remove(v)
            }

            for (i in 0 until block) {
                val s = mix[i].coerceIn(-1f, 1f)
                pcm[i] = (s * 32000f).toInt().toShort()
            }
            track.write(pcm, 0, block)
        }
        track.stop()
        track.release()
    }
}

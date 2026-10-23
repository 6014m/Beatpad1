package com.sixzero.beatpad

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** The whole state of a project: patterns, mixer, tempo. */
class Project(val tracks: Int, val steps: Int, val slots: Int) {
    /** patterns[slot][track][step] */
    val patterns = Array(slots) { Array(tracks) { BooleanArray(steps) } }
    val volume = FloatArray(tracks) { 0.85f }
    val muted = BooleanArray(tracks)
    /** which slots are included when chain mode is on */
    val chainOn = BooleanArray(slots) { it == 0 }
    var bpm = 120
    var swing = 0          // 0..100
    var chain = false
    var slot = 0           // currently edited / played slot
}

/**
 * Sample-accurate step sequencer. One audio thread mixes every voice into a
 * continuous stream; triggers land on exact sample offsets within the block.
 */
class BeatEngine(val project: Project) {
    private val sr = DrumSynth.SAMPLE_RATE
    val samples: Array<FloatArray> = DrumSynth.all()

    val playing = AtomicBoolean(false)
    /** (slot, step) — step == -1 means stopped */
    var onStep: ((Int, Int) -> Unit)? = null

    private class Voice(val data: FloatArray, val gain: Float, var pos: Int = 0, var delay: Int = 0)
    private val voices = CopyOnWriteArrayList<Voice>()
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)
    @Volatile private var restartRequested = false

    fun trigger(track: Int) {
        voices.add(Voice(samples[track], project.volume[track]))
    }

    fun play() { restartRequested = true; playing.set(true) }
    fun stop() { playing.set(false); onStep?.invoke(project.slot, -1) }

    fun start() {
        if (running.getAndSet(true)) return
        thread = Thread({ audioLoop() }, "beat-audio").also {
            it.priority = Thread.MAX_PRIORITY
            it.start()
        }
    }

    fun release() {
        running.set(false); playing.set(false); thread?.join(500)
    }

    // ---- sequencing state (audio thread only) ----
    private var sampleCounter = 0L
    private var lastStepIndex = -1L
    private var playSlot = 0

    private fun samplesPerStep() = (60.0 / project.bpm / 4.0 * sr)

    /** Returns the sample offset of step `n` (global, not wrapped) with swing applied. */
    private fun stepStart(n: Long): Long {
        val sps = samplesPerStep()
        val base = n * sps
        val swingAmt = if (n % 2 == 1L) sps * (project.swing / 100.0) * 0.33 else 0.0
        return (base + swingAmt).toLong()
    }

    private fun nextSlotAfter(s: Int): Int {
        if (!project.chain) return s
        for (k in 1..project.slots) {
            val c = (s + k) % project.slots
            if (project.chainOn[c]) return c
        }
        return s
    }

    private fun schedule(blockStart: Long, block: Int) {
        // find any step whose start falls inside [blockStart, blockStart+block)
        val sps = samplesPerStep()
        var n = (blockStart / sps).toLong() - 1
        if (n < 0) n = 0
        while (true) {
            val st = stepStart(n)
            if (st >= blockStart + block) break
            if (st >= blockStart && n > lastStepIndex) {
                lastStepIndex = n
                val step = (n % project.steps).toInt()
                if (step == 0 && n > 0) playSlot = nextSlotAfter(playSlot)
                val pat = project.patterns[playSlot]
                for (t in 0 until project.tracks) {
                    if (pat[t][step] && !project.muted[t]) {
                        voices.add(Voice(samples[t], project.volume[t], delay = (st - blockStart).toInt()))
                    }
                }
                onStep?.invoke(playSlot, step)
            }
            n++
        }
    }

    private fun render(mix: FloatArray, block: Int) {
        java.util.Arrays.fill(mix, 0f, block, 0f)
        for (v in voices) {
            var i = 0
            if (v.delay > 0) { i = v.delay; v.delay = 0 }
            var p = v.pos
            while (i < block && p < v.data.size) { mix[i] += v.data[p] * v.gain; i++; p++ }
            v.pos = p
            if (p >= v.data.size) voices.remove(v)
        }
    }

    private fun audioLoop() {
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(minBuf, 4096))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()

        val block = 256
        val mix = FloatArray(block)
        val pcm = ShortArray(block)

        while (running.get()) {
            if (playing.get()) {
                if (restartRequested) {
                    restartRequested = false
                    sampleCounter = 0; lastStepIndex = -1
                    playSlot = if (project.chain) (project.chainOn.indexOfFirst { it }.takeIf { it >= 0 } ?: project.slot) else project.slot
                }
                schedule(sampleCounter, block)
                sampleCounter += block
            }
            render(mix, block)
            for (i in 0 until block) pcm[i] = (mix[i].coerceIn(-1f, 1f) * 32000f).toInt().toShort()
            track.write(pcm, 0, block)
        }
        track.stop(); track.release()
    }

    /** Offline-render the current slot (or chain) `bars` times to a 16-bit WAV. */
    fun exportWav(file: File, bars: Int) {
        val e = BeatEngine(project) // fresh instance so live playback isn't disturbed
        e.restartRequested = true
        e.sampleCounter = 0; e.lastStepIndex = -1
        e.playSlot = if (project.chain) (project.chainOn.indexOfFirst { it }.takeIf { it >= 0 } ?: project.slot) else project.slot
        val slotsInLoop = if (project.chain) project.chainOn.count { it }.coerceAtLeast(1) else 1
        val totalSteps = project.steps.toLong() * slotsInLoop * bars
        val totalSamples = e.stepStart(totalSteps) + sr / 2 // half-second tail
        val block = 256
        val mix = FloatArray(block)
        val bytes = ByteBuffer.allocate((totalSamples * 2).toInt()).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 0L
        while (pos < totalSamples) {
            if (pos < e.stepStart(totalSteps)) e.schedule(pos, block)
            e.render(mix, block)
            val n = minOf(block.toLong(), totalSamples - pos).toInt()
            for (i in 0 until n) bytes.putShort((mix[i].coerceIn(-1f, 1f) * 32000f).toInt().toShort())
            pos += block
        }
        val data = bytes.array()
        FileOutputStream(file).use { out ->
            val hdr = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            hdr.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVE".toByteArray())
            hdr.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(sr).putInt(sr * 2).putShort(2).putShort(16)
            hdr.put("data".toByteArray()).putInt(data.size)
            out.write(hdr.array()); out.write(data)
        }
    }
}

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
import kotlin.math.tanh

const val OFF: Byte = 0
const val HIT: Byte = 1
const val ACCENT: Byte = 2

/** The whole state of a project: patterns, mixer, tempo, kit. */
class Project(val tracks: Int, val steps: Int, val slots: Int) {
    /** patterns[slot][track][step] = OFF / HIT / ACCENT */
    val patterns = Array(slots) { Array(tracks) { ByteArray(steps) } }
    val volume = FloatArray(tracks) { 0.85f }
    val muted = BooleanArray(tracks)
    val chainOn = BooleanArray(slots) { it == 0 }
    var bpm = 120
    var swing = 0
    var chain = false
    var slot = 0
    var kit = "808"

    // ---- melodic lanes ----
    val lanes = 2
    val laneNames = listOf("BASS", "LEAD")
    /** notes[slot][lane] */
    val notes = Array(slots) { Array(lanes) { CopyOnWriteArrayList<Note>() } }
    val laneInstrument = intArrayOf(0, 1)
    val laneVolume = floatArrayOf(0.9f, 0.6f)
    val laneMuted = BooleanArray(lanes)
    var scale = 1      // index into Scales.list
    var root = 0       // 0 = C
}

data class Note(var pitch: Int, var start: Int, var len: Int)

/**
 * Sample-accurate step sequencer. One audio thread mixes every voice into a
 * continuous stream; triggers land on exact sample offsets within the block.
 */
class BeatEngine(val project: Project) {
    private val sr = DrumSynth.SAMPLE_RATE
    @Volatile var samples: Array<FloatArray> = DrumSynth.all()

    val playing = AtomicBoolean(false)
    /** (slot, step) — step == -1 means stopped */
    var onStep: ((Int, Int) -> Unit)? = null

    private val voices = CopyOnWriteArrayList<Voice>()
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)
    @Volatile private var restartRequested = false

    fun trigger(track: Int, accent: Boolean = false) {
        if (track >= samples.size) return
        voices.add(SampleVoice(samples[track], project.volume[track] * (if (accent) 1f else 0.72f)))
    }

    /** Preview a melodic note right now. */
    fun triggerNote(lane: Int, pitch: Int, lenSteps: Int = 2) {
        val len = (lenSteps * samplesPerStep()).toInt()
        voices.add(SynthVoice(project.laneInstrument[lane], pitch, len, project.laneVolume[lane]))
    }

    fun play() { restartRequested = true; playing.set(true) }
    fun stop() { playing.set(false); onStep?.invoke(project.slot, -1) }

    /** Fractional step position inside the current pattern (for live record quantize). */
    fun positionInSteps(): Double = (sampleCounter / samplesPerStep()) % project.steps

    fun start() {
        if (running.getAndSet(true)) return
        thread = Thread({ audioLoop() }, "beat-audio").also {
            it.priority = Thread.MAX_PRIORITY
            it.start()
        }
    }

    fun release() { running.set(false); playing.set(false); thread?.join(500) }

    // ---- sequencing state (audio thread only) ----
    @Volatile private var sampleCounter = 0L
    private var lastStepIndex = -1L
    private var playSlot = 0

    private fun samplesPerStep() = 60.0 / project.bpm / 4.0 * sr

    private fun stepStart(n: Long): Long {
        val sps = samplesPerStep()
        val swingAmt = if (n % 2 == 1L) sps * (project.swing / 100.0) * 0.33 else 0.0
        return (n * sps + swingAmt).toLong()
    }

    private fun nextSlotAfter(s: Int): Int {
        if (!project.chain) return s
        for (k in 1..project.slots) {
            val c = (s + k) % project.slots
            if (project.chainOn[c]) return c
        }
        return s
    }

    private fun firstSlot() =
        if (project.chain) (project.chainOn.indexOfFirst { it }.takeIf { it >= 0 } ?: project.slot) else project.slot

    private fun schedule(blockStart: Long, block: Int) {
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
                val smp = samples
                for (t in 0 until minOf(project.tracks, smp.size)) {
                    val v = pat[t][step]
                    if (v != OFF && !project.muted[t]) {
                        val g = project.volume[t] * (if (v == ACCENT) 1f else 0.72f)
                        voices.add(SampleVoice(smp[t], g).also { it.delay = (st - blockStart).toInt() })
                    }
                }
                for (l in 0 until project.lanes) {
                    if (project.laneMuted[l]) continue
                    val list = project.notes[playSlot][l]
                    for (k in list.indices) {
                        val note = list.getOrNull(k) ?: break
                        if (note.start == step) {
                            val len = (note.len * sps).toInt()
                            voices.add(SynthVoice(project.laneInstrument[l], note.pitch, len, project.laneVolume[l]).also { it.delay = (st - blockStart).toInt() })
                        }
                    }
                }
                onStep?.invoke(playSlot, step)
            }
            n++
        }
    }

    private fun render(mix: FloatArray, block: Int) {
        java.util.Arrays.fill(mix, 0, block, 0f)
        for (v in voices) {
            var from = 0
            if (v.delay > 0) { from = v.delay; v.delay = 0 }
            v.render(mix, from, block)
            if (v.done) voices.remove(v)
        }
        // gentle master soft-clip so stacked hits don't crackle
        for (i in 0 until block) mix[i] = tanh(mix[i] * 1.1f)
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
                    playSlot = firstSlot()
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
        val e = BeatEngine(project)
        e.samples = samples
        e.sampleCounter = 0; e.lastStepIndex = -1; e.playSlot = firstSlot()
        val slotsInLoop = if (project.chain) project.chainOn.count { it }.coerceAtLeast(1) else 1
        val totalSteps = project.steps.toLong() * slotsInLoop * bars
        val endOfSteps = e.stepStart(totalSteps)
        val totalSamples = endOfSteps + sr / 2
        val block = 256
        val mix = FloatArray(block)
        val bytes = ByteBuffer.allocate((totalSamples * 2).toInt()).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 0L
        while (pos < totalSamples) {
            if (pos < endOfSteps) e.schedule(pos, block)
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

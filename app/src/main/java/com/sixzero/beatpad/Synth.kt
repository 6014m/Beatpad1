package com.sixzero.beatpad

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/** Melodic voices rendered on the fly (no samples): pitch + length in. */
object Synth {
    const val SR = DrumSynth.SAMPLE_RATE
    val instruments = listOf("808", "PLUCK", "SAW", "PAD")

    fun hz(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)
    val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    fun name(midi: Int) = noteNames[midi % 12] + (midi / 12 - 1)
}

/** A voice that generates itself. `lengthSamples` = how long the note is held. */
class SynthVoice(val instrument: Int, midi: Int, val lengthSamples: Int, val gain: Float) : Voice {
    private val f = Synth.hz(midi)
    private var n = 0
    override var delay = 0
    private var phase = 0.0
    private var phase2 = 0.0
    private var lp = 0f
    private var lp2 = 0f
    private val release = (Synth.SR * if (instrument == 3) 0.25 else 0.08).toInt()
    private val total = lengthSamples + release + (if (instrument == 0) Synth.SR else 0)
    override val done get() = n >= total

    override fun render(mix: FloatArray, from: Int, to: Int) {
        var i = from
        val sr = Synth.SR.toDouble()
        while (i < to && n < total) {
            val t = n / sr
            val held = n < lengthSamples
            val rel = if (held) 1f else exp(-(n - lengthSamples) / release.toDouble() * 4).toFloat()
            val s: Float = when (instrument) {
                0 -> { // 808: sine sub with pitch snap, saturation, long decay
                    val freq = f * (1.0 + 1.5 * exp(-t * 60.0))
                    phase += 2 * PI * freq / sr
                    val env = exp(-t * 1.4)
                    (tanh(sin(phase) * 2.5 * env) * 0.95).toFloat() * (if (held) 1f else exp(-(n - lengthSamples) / (sr * 0.12)).toFloat())
                }
                1 -> { // pluck: bright saw hit through a closing lowpass
                    phase += f / sr; if (phase >= 1) phase -= 1
                    val saw = (phase * 2 - 1).toFloat()
                    val cutoff = (0.02 + 0.5 * exp(-t * 14.0)).toFloat()
                    lp += (saw - lp) * cutoff
                    lp * exp(-t * 2.5).toFloat() * 0.8f * rel
                }
                2 -> { // saw lead: two detuned saws, moderate lowpass
                    phase += f / sr; if (phase >= 1) phase -= 1
                    phase2 += f * 1.007 / sr; if (phase2 >= 1) phase2 -= 1
                    val saw = ((phase * 2 - 1) + (phase2 * 2 - 1)).toFloat() * 0.5f
                    lp += (saw - lp) * 0.18f
                    val att = minOf(1.0, t / 0.01).toFloat()
                    lp * 0.7f * att * rel
                }
                else -> { // pad: slow attack, soft detuned saws, dark lowpass
                    phase += f / sr; if (phase >= 1) phase -= 1
                    phase2 += f * 0.995 / sr; if (phase2 >= 1) phase2 -= 1
                    val saw = ((phase * 2 - 1) + (phase2 * 2 - 1)).toFloat() * 0.5f
                    lp += (saw - lp) * 0.06f
                    lp2 += (lp - lp2) * 0.06f
                    val att = minOf(1.0, t / 0.18).toFloat()
                    lp2 * 0.9f * att * rel
                }
            }
            mix[i] += s * gain
            i++; n++
        }
    }
}

interface Voice {
    var delay: Int
    val done: Boolean
    fun render(mix: FloatArray, from: Int, to: Int)
}

class SampleVoice(val data: FloatArray, val gain: Float) : Voice {
    private var pos = 0
    override var delay = 0
    override val done get() = pos >= data.size
    override fun render(mix: FloatArray, from: Int, to: Int) {
        var i = from
        while (i < to && pos < data.size) { mix[i] += data[pos] * gain; i++; pos++ }
    }
}

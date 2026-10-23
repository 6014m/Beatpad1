package com.sixzero.beatpad

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/** Generates short drum hits as float sample arrays. No audio files needed. */
object DrumSynth {
    const val SAMPLE_RATE = 44100

    val names = listOf("KICK", "SNARE", "CLAP", "HAT", "OPEN", "TOM", "RIM", "808")

    fun all(): Array<FloatArray> = arrayOf(
        kick(), snare(), clap(), hat(false), hat(true), tom(), rim(), bass808()
    )

    fun kick(): FloatArray {
        val len = (SAMPLE_RATE * 0.35).toInt()
        val out = FloatArray(len)
        var phase = 0.0
        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = 45.0 + 110.0 * exp(-t * 28.0)
            phase += 2 * PI * freq / SAMPLE_RATE
            val env = exp(-t * 9.0)
            val click = if (i < 100) (1.0 - i / 100.0) * 0.4 else 0.0
            out[i] = (tanh((sin(phase) * env + click) * 1.6) * 0.9).toFloat()
        }
        return out
    }

    fun snare(): FloatArray {
        val len = (SAMPLE_RATE * 0.22).toInt()
        val out = FloatArray(len)
        val rnd = Random(7)
        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val noise = (rnd.nextFloat() * 2f - 1f) * exp(-t * 18.0).toFloat()
            val tone = (sin(2 * PI * 190.0 * t) * exp(-t * 30.0)).toFloat()
            out[i] = (noise * 0.6f + tone * 0.5f) * 0.9f
        }
        return out
    }

    fun hat(open: Boolean): FloatArray {
        val dur = if (open) 0.32 else 0.07
        val len = (SAMPLE_RATE * dur).toInt()
        val out = FloatArray(len)
        val rnd = Random(if (open) 11 else 3)
        val decay = if (open) 9.0 else 60.0
        var prev = 0f
        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val white = rnd.nextFloat() * 2f - 1f
            val hp = white - prev
            prev = white
            out[i] = (hp * exp(-t * decay)).toFloat() * 0.5f
        }
        return out
    }

    fun clap(): FloatArray {
        val len = (SAMPLE_RATE * 0.25).toInt()
        val out = FloatArray(len)
        val rnd = Random(21)
        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val white = rnd.nextFloat() * 2f - 1f
            val burst = when {
                t < 0.010 -> 1.0
                t < 0.020 -> 0.0
                t < 0.030 -> 0.9
                t < 0.040 -> 0.0
                else -> exp(-(t - 0.04) * 14.0)
            }
            out[i] = (white * burst).toFloat() * 0.7f
        }
        return out
    }

    fun tom(): FloatArray {
        val len = (SAMPLE_RATE * 0.3).toInt()
        val out = FloatArray(len)
        var phase = 0.0
        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = 110.0 + 90.0 * exp(-t * 20.0)
            phase += 2 * PI * freq / SAMPLE_RATE
            out[i] = (sin(phase) * exp(-t * 10.0)).toFloat() * 0.8f
        }
        return out
    }

    fun rim(): FloatArray {
        val len = (SAMPLE_RATE * 0.06).toInt()
        val out = FloatArray(len)
        val rnd = Random(5)
        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val tone = sin(2 * PI * 1700.0 * t) * 0.6 + sin(2 * PI * 800.0 * t) * 0.4
            val noise = (rnd.nextFloat() * 2f - 1f) * 0.3
            out[i] = ((tone + noise) * exp(-t * 90.0)).toFloat() * 0.8f
        }
        return out
    }

    fun bass808(): FloatArray {
        val len = (SAMPLE_RATE * 0.8).toInt()
        val out = FloatArray(len)
        var phase = 0.0
        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = 41.2 + 60.0 * exp(-t * 40.0) // E1 with a pitch snap
            phase += 2 * PI * freq / SAMPLE_RATE
            val env = exp(-t * 3.2)
            out[i] = (tanh(sin(phase) * 2.2 * env) * 0.9).toFloat()
        }
        return out
    }
}

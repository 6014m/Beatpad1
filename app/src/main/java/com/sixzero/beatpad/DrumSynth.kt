package com.sixzero.beatpad

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Generates short drum hits as float sample arrays. No audio files needed. */
object DrumSynth {
    const val SAMPLE_RATE = 44100

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
            out[i] = ((sin(phase) * env + click) * 0.95).toFloat()
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
            // crude high-pass so it sounds metallic instead of like static
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
            // three quick bursts then a tail = classic clap
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
}

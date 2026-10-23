package com.sixzero.beatpad

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Kit(val name: String, val trackNames: List<String>, val samples: Array<FloatArray>)

object Kits {
    val names = listOf("808", "909", "707", "SYNTH")

    fun load(ctx: Context, name: String): Kit {
        if (name == "SYNTH") return Kit("SYNTH", DrumSynth.names, DrumSynth.all())
        val am = ctx.assets
        val labels = am.open("kits/$name/names.txt").bufferedReader().readLines().filter { it.isNotBlank() }
        val samples = Array(labels.size) { i -> readWav(am.open("kits/$name/$i.wav").readBytes()) }
        return Kit(name, labels, samples)
    }

    /** Minimal RIFF/WAV reader: 16-bit PCM mono (what we ship). */
    private fun readWav(bytes: ByteArray): FloatArray {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(12)
        var channels = 1
        while (bb.remaining() >= 8) {
            val id = String(ByteArray(4).also { bb.get(it) })
            val size = bb.int
            if (id == "fmt ") {
                bb.short; channels = bb.short.toInt()
                bb.position(bb.position() + size - 4)
            } else if (id == "data") {
                val frames = size / 2 / channels
                val out = FloatArray(frames)
                for (i in 0 until frames) {
                    var acc = 0f
                    for (c in 0 until channels) acc += bb.short / 32768f
                    out[i] = acc / channels
                }
                return out
            } else {
                bb.position(bb.position() + size + (size and 1))
            }
        }
        return FloatArray(0)
    }
}

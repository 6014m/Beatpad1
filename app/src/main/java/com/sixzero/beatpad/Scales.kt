package com.sixzero.beatpad

class Scale(val name: String, val steps: IntArray)

object Scales {
    val list = listOf(
        Scale("CHROM", intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)),
        Scale("MINOR", intArrayOf(0, 2, 3, 5, 7, 8, 10)),
        Scale("MAJOR", intArrayOf(0, 2, 4, 5, 7, 9, 11)),
        Scale("PENT-", intArrayOf(0, 3, 5, 7, 10)),
        Scale("PENT+", intArrayOf(0, 2, 4, 7, 9)),
        Scale("HARM-", intArrayOf(0, 2, 3, 5, 7, 8, 11)),
        Scale("PHRYG", intArrayOf(0, 1, 3, 5, 7, 8, 10)),
    )

    /** All MIDI pitches in the scale between lo..hi inclusive, ascending. */
    fun pitches(scale: Int, root: Int, lo: Int, hi: Int): IntArray {
        val st = list[scale].steps
        val out = ArrayList<Int>()
        for (m in lo..hi) if (((m - root) % 12 + 12) % 12 in st) out.add(m)
        return out.toIntArray()
    }
}

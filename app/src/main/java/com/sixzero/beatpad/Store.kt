package com.sixzero.beatpad

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Saves/loads the whole project as JSON in SharedPreferences. */
object Store {
    private const val KEY = "project_v5"

    fun save(ctx: Context, p: Project) {
        val o = JSONObject()
        o.put("bpm", p.bpm); o.put("swing", p.swing); o.put("chain", p.chain); o.put("slot", p.slot); o.put("kit", p.kit); o.put("bars", p.bars)
        o.put("volume", JSONArray(p.volume.map { it.toDouble() }))
        o.put("muted", JSONArray(p.muted.toList()))
        o.put("chainOn", JSONArray(p.chainOn.toList()))
        val pats = JSONArray()
        for (s in p.patterns) {
            val tr = JSONArray()
            for (t in s) tr.put(t.joinToString("") { it.toString() })
            pats.put(tr)
        }
        o.put("patterns", pats)
        o.put("laneInstrument", JSONArray(p.laneInstrument.toList()))
        o.put("laneVolume", JSONArray(p.laneVolume.map { it.toDouble() }))
        o.put("laneMuted", JSONArray(p.laneMuted.toList()))
        o.put("scale", p.scale); o.put("root", p.root)
        val notes = JSONArray()
        for (s in p.notes) {
            val lanes = JSONArray()
            for (l in s) lanes.put(JSONArray(l.map { JSONArray(listOf(it.pitch, it.start, it.len)) }))
            notes.put(lanes)
        }
        o.put("notes", notes)
        ctx.getSharedPreferences("beatpad", Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
    }

    fun load(ctx: Context, p: Project): Boolean {
        val s = ctx.getSharedPreferences("beatpad", Context.MODE_PRIVATE).getString(KEY, null) ?: return false
        return try {
            val o = JSONObject(s)
            p.bpm = o.optInt("bpm", 120); p.swing = o.optInt("swing", 0)
            p.chain = o.optBoolean("chain", false); p.kit = o.optString("kit", "808"); p.bars = o.optInt("bars", 1).let { if (it == 2 || it == 4) it else 1 }; p.slot = o.optInt("slot", 0).coerceIn(0, p.slots - 1)
            o.optJSONArray("volume")?.let { a -> for (i in 0 until minOf(a.length(), p.tracks)) p.volume[i] = a.getDouble(i).toFloat() }
            o.optJSONArray("muted")?.let { a -> for (i in 0 until minOf(a.length(), p.tracks)) p.muted[i] = a.getBoolean(i) }
            o.optJSONArray("chainOn")?.let { a -> for (i in 0 until minOf(a.length(), p.slots)) p.chainOn[i] = a.getBoolean(i) }
            o.optJSONArray("patterns")?.let { pats ->
                for (si in 0 until minOf(pats.length(), p.slots)) {
                    val tr = pats.getJSONArray(si)
                    for (ti in 0 until minOf(tr.length(), p.tracks)) {
                        val str = tr.getString(ti)
                        for (st in 0 until minOf(str.length, Project.MAX_STEPS)) p.patterns[si][ti][st] = (str[st] - '0').coerceIn(0, 2).toByte()
                    }
                }
            }
            p.scale = o.optInt("scale", 1); p.root = o.optInt("root", 0)
            o.optJSONArray("laneInstrument")?.let { a -> for (i in 0 until minOf(a.length(), p.lanes)) p.laneInstrument[i] = a.getInt(i) }
            o.optJSONArray("laneVolume")?.let { a -> for (i in 0 until minOf(a.length(), p.lanes)) p.laneVolume[i] = a.getDouble(i).toFloat() }
            o.optJSONArray("laneMuted")?.let { a -> for (i in 0 until minOf(a.length(), p.lanes)) p.laneMuted[i] = a.getBoolean(i) }
            o.optJSONArray("notes")?.let { ns ->
                for (si in 0 until minOf(ns.length(), p.slots)) {
                    val lanes = ns.getJSONArray(si)
                    for (li in 0 until minOf(lanes.length(), p.lanes)) {
                        val arr = lanes.getJSONArray(li)
                        for (k in 0 until arr.length()) {
                            val n = arr.getJSONArray(k)
                            p.notes[si][li].add(Note(n.getInt(0), n.getInt(1), n.getInt(2)))
                        }
                    }
                }
            }
            true
        } catch (e: Exception) { false }
    }
}

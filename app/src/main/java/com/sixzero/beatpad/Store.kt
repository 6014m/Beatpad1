package com.sixzero.beatpad

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Saves/loads the whole project as JSON in SharedPreferences. */
object Store {
    private const val KEY = "project_v2"

    fun save(ctx: Context, p: Project) {
        val o = JSONObject()
        o.put("bpm", p.bpm); o.put("swing", p.swing); o.put("chain", p.chain); o.put("slot", p.slot)
        o.put("volume", JSONArray(p.volume.map { it.toDouble() }))
        o.put("muted", JSONArray(p.muted.toList()))
        o.put("chainOn", JSONArray(p.chainOn.toList()))
        val pats = JSONArray()
        for (s in p.patterns) {
            val tr = JSONArray()
            for (t in s) tr.put(t.joinToString("") { if (it) "1" else "0" })
            pats.put(tr)
        }
        o.put("patterns", pats)
        ctx.getSharedPreferences("beatpad", Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
    }

    fun load(ctx: Context, p: Project): Boolean {
        val s = ctx.getSharedPreferences("beatpad", Context.MODE_PRIVATE).getString(KEY, null) ?: return false
        return try {
            val o = JSONObject(s)
            p.bpm = o.optInt("bpm", 120); p.swing = o.optInt("swing", 0)
            p.chain = o.optBoolean("chain", false); p.slot = o.optInt("slot", 0).coerceIn(0, p.slots - 1)
            o.optJSONArray("volume")?.let { a -> for (i in 0 until minOf(a.length(), p.tracks)) p.volume[i] = a.getDouble(i).toFloat() }
            o.optJSONArray("muted")?.let { a -> for (i in 0 until minOf(a.length(), p.tracks)) p.muted[i] = a.getBoolean(i) }
            o.optJSONArray("chainOn")?.let { a -> for (i in 0 until minOf(a.length(), p.slots)) p.chainOn[i] = a.getBoolean(i) }
            o.optJSONArray("patterns")?.let { pats ->
                for (si in 0 until minOf(pats.length(), p.slots)) {
                    val tr = pats.getJSONArray(si)
                    for (ti in 0 until minOf(tr.length(), p.tracks)) {
                        val str = tr.getString(ti)
                        for (st in 0 until minOf(str.length, p.steps)) p.patterns[si][ti][st] = str[st] == '1'
                    }
                }
            }
            true
        } catch (e: Exception) { false }
    }
}

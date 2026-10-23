package com.sixzero.beatpad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/** Tap-to-toggle step grid with a moving playhead. */
class SequencerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var tracks = 8
    var steps = 16
    var pattern: Array<BooleanArray> = Array(tracks) { BooleanArray(steps) }
        set(v) { field = v; invalidate() }
    var muted: BooleanArray = BooleanArray(tracks)
    var playhead = -1
        set(v) { field = v; postInvalidateOnAnimation() }
    var onToggle: ((track: Int, step: Int, on: Boolean) -> Unit)? = null

    val trackColors = intArrayOf(
        Color.parseColor("#FF5C5C"), // kick
        Color.parseColor("#FFB347"), // snare
        Color.parseColor("#C68CFF"), // clap
        Color.parseColor("#5CE1E6"), // hat
        Color.parseColor("#8FD3FF"), // open hat
        Color.parseColor("#7CFC9B"), // tom
        Color.parseColor("#FFE066"), // rim
        Color.parseColor("#FF8FD6"), // 808
    )

    private val cellOff = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2A2A32") }
    private val cellBeat = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#34343E") }
    private val cellOn = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 255, 255, 255)
    }
    private val rect = RectF()
    private val gap = 6f

    override fun onDraw(canvas: Canvas) {
        val cw = width.toFloat() / steps
        val ch = height.toFloat() / tracks
        for (t in 0 until tracks) {
            for (s in 0 until steps) {
                rect.set(s * cw + gap / 2, t * ch + gap / 2, (s + 1) * cw - gap / 2, (t + 1) * ch - gap / 2)
                val paint = when {
                    pattern[t][s] -> cellOn.apply {
                        val c = trackColors[t % trackColors.size]
                        color = if (muted[t]) Color.argb(70, Color.red(c), Color.green(c), Color.blue(c)) else c
                    }
                    s % 4 == 0 -> cellBeat
                    else -> cellOff
                }
                canvas.drawRoundRect(rect, 10f, 10f, paint)
            }
        }
        if (playhead in 0 until steps) {
            rect.set(playhead * cw, 0f, (playhead + 1) * cw, height.toFloat())
            canvas.drawRoundRect(rect, 10f, 10f, headPaint)
        }
    }

    private var dragState: Boolean? = null
    private var lastCell = -1

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val s = (e.x / (width.toFloat() / steps)).toInt().coerceIn(0, steps - 1)
        val t = (e.y / (height.toFloat() / tracks)).toInt().coerceIn(0, tracks - 1)
        val cell = t * steps + s
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragState = !pattern[t][s]
                apply(t, s)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> if (cell != lastCell) apply(t, s)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dragState = null; lastCell = -1 }
        }
        return true
    }

    private fun apply(t: Int, s: Int) {
        val on = dragState ?: return
        lastCell = t * steps + s
        if (pattern[t][s] != on) {
            pattern[t][s] = on
            onToggle?.invoke(t, s, on)
            invalidate()
        }
    }
}

package com.sixzero.beatpad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Step grid. Tap = toggle hit, drag = paint hits, long-press a cell = accent.
 */
class SequencerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var tracks = 8
    var steps = 16            // visible columns
    var offset = 0            // first visible step
        set(v) { field = v; invalidate() }
    var pattern: Array<ByteArray> = Array(tracks) { ByteArray(steps) }
        set(v) { field = v; invalidate() }
    var muted: BooleanArray = BooleanArray(tracks)
    var playhead = -1
        set(v) { field = v; postInvalidateOnAnimation() }
    var onChange: (() -> Unit)? = null

    val trackColors = Palette.tracks

    private val cellOff = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.cell }
    private val cellBeat = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.cellBeat }
    private val cellOn = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.WHITE
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) }
    private val rect = RectF()
    private val gap = 7f

    override fun onDraw(canvas: Canvas) {
        val cw = width.toFloat() / steps
        val ch = height.toFloat() / tracks
        for (t in 0 until tracks) {
            val c = trackColors[t % trackColors.size]
            for (s in 0 until steps) {
                rect.set(s * cw + gap / 2, t * ch + gap / 2, (s + 1) * cw - gap / 2, (t + 1) * ch - gap / 2)
                val v = pattern[t][offset + s]
                val paint = when {
                    v != OFF -> cellOn.apply {
                        val a = if (muted[t]) 60 else if (v == ACCENT) 255 else 170
                        color = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))
                    }
                    s % 4 == 0 -> cellBeat
                    else -> cellOff
                }
                canvas.drawRoundRect(rect, 8f, 8f, paint)
                if (v == ACCENT && !muted[t]) {
                    rect.inset(2f, 2f); canvas.drawRoundRect(rect, 6f, 6f, accentRing)
                }
            }
        }
        if (playhead in 0 until steps) {
            rect.set(playhead * cw, 0f, (playhead + 1) * cw, height.toFloat())
            canvas.drawRoundRect(rect, 8f, 8f, headPaint)
        }
    }

    private var paintValue: Byte? = null
    private var lastCell = -1
    private var downCell = -1
    private var moved = false
    private val longPress = Runnable {
        if (downCell >= 0 && !moved) {
            val t = downCell / steps; val s = offset + downCell % steps
            pattern[t][s] = if (pattern[t][s] == ACCENT) HIT else ACCENT
            paintValue = null
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            invalidate(); onChange?.invoke()
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val s = (e.x / (width.toFloat() / steps)).toInt().coerceIn(0, steps - 1)
        val t = (e.y / (height.toFloat() / tracks)).toInt().coerceIn(0, tracks - 1)
        val cell = t * steps + s
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downCell = cell; moved = false
                paintValue = if (pattern[t][offset + s] == OFF) HIT else OFF
                postDelayed(longPress, 380)
            }
            MotionEvent.ACTION_MOVE -> if (cell != downCell || cell != lastCell) {
                if (cell != downCell) moved = true
                if (moved) {
                    removeCallbacks(longPress)
                    if (lastCell == -1) applyAt(downCell / steps, downCell % steps)
                    applyAt(t, s)
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                if (!moved && paintValue != null) applyAt(t, s)
                reset()
            }
            MotionEvent.ACTION_CANCEL -> { removeCallbacks(longPress); reset() }
        }
        return true
    }

    private fun reset() { paintValue = null; lastCell = -1; downCell = -1 }

    private fun applyAt(t: Int, s: Int) {
        val v = paintValue ?: return
        lastCell = t * steps + s
        if (pattern[t][offset + s] != v) {
            pattern[t][offset + s] = v
            invalidate(); onChange?.invoke()
        }
    }
}

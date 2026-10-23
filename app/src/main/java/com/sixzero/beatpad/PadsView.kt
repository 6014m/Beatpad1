package com.sixzero.beatpad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/** 2x4 finger-drumming pads. Multi-touch. Lights up on hit. */
class PadsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var names: List<String> = List(8) { "" }
        set(v) { field = v; invalidate() }
    var onPad: ((Int) -> Unit)? = null
    var onPadLongPress: ((Int) -> Unit)? = null

    private val cols = 2
    private val rows = 4
    private val lit = LongArray(8)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 36f; textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        letterSpacing = 0.08f
    }
    private val rect = RectF()
    private val gap = 14f

    override fun onDraw(canvas: Canvas) {
        val cw = width.toFloat() / cols
        val ch = height.toFloat() / rows
        val now = System.currentTimeMillis()
        var needMore = false
        for (i in 0 until 8) {
            val r = i / cols; val c = i % cols
            rect.set(c * cw + gap / 2, r * ch + gap / 2, (c + 1) * cw - gap / 2, (r + 1) * ch - gap / 2)
            val col = Palette.tracks[i]
            val age = now - lit[i]
            val glow = if (age < 160) (1f - age / 160f) else 0f
            if (glow > 0) needMore = true
            val a = (40 + 215 * glow).toInt()
            fill.color = Color.argb(a, Color.red(col), Color.green(col), Color.blue(col))
            canvas.drawRoundRect(rect, 18f, 18f, fill)
            fill.color = Color.argb(255, Color.red(col), Color.green(col), Color.blue(col))
            fill.style = Paint.Style.STROKE; fill.strokeWidth = 3f
            canvas.drawRoundRect(rect, 18f, 18f, fill)
            fill.style = Paint.Style.FILL
            canvas.drawText(names.getOrElse(i) { "" }, rect.centerX(), rect.centerY() + 12f, text)
        }
        if (needMore) postInvalidateOnAnimation()
    }

    private fun padAt(x: Float, y: Float): Int {
        val c = (x / (width.toFloat() / cols)).toInt().coerceIn(0, cols - 1)
        val r = (y / (height.toFloat() / rows)).toInt().coerceIn(0, rows - 1)
        return r * cols + c
    }

    private val pressed = HashMap<Int, Int>() // pointerId -> pad
    private val longPressRunnables = HashMap<Int, Runnable>()

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val idx = e.actionIndex
                val pid = e.getPointerId(idx)
                val pad = padAt(e.getX(idx), e.getY(idx))
                pressed[pid] = pad
                lit[pad] = System.currentTimeMillis()
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                onPad?.invoke(pad)
                val r = Runnable { onPadLongPress?.invoke(pad) }
                longPressRunnables[pid] = r
                postDelayed(r, 450)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val pid = e.getPointerId(e.actionIndex)
                pressed.remove(pid)
                longPressRunnables.remove(pid)?.let { removeCallbacks(it) }
            }
        }
        return true
    }
}

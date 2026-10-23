package com.sixzero.beatpad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Piano roll over `steps` columns and a scrollable list of scale pitches.
 * Tap empty = add note. Tap note = delete. Drag from a note = stretch its length.
 * Tap the key column = preview that pitch.
 */
class PianoRollView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var steps = 16            // visible columns
    var totalSteps = 16
    var offset = 0
        set(v) { field = v; invalidate() }
    var notes: MutableList<Note> = ArrayList()
        set(v) { field = v; invalidate() }
    var pitches: IntArray = Scales.pitches(1, 0, 36, 84)
        set(v) { field = v; rowOffset = rowOffset.coerceIn(0, maxOf(0, v.size - visibleRows)); invalidate() }
    var rowOffset = 0
    var root = 0
    var visibleRows = 14
    var color = Palette.tracks[7]
    var playhead = -1
        set(v) { field = v; postInvalidateOnAnimation() }
    var onChange: (() -> Unit)? = null
    var onPreview: ((Int) -> Unit)? = null

    private val keyW = 44f * resources.displayMetrics.density
    private val rect = RectF()
    private val cellOff = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.cell }
    private val cellBeat = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.cellBeat }
    private val rootRow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#262630") }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.panel }
    private val keyText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.dim; textSize = 10f * resources.displayMetrics.density; textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val noteText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; textSize = 9f * resources.displayMetrics.density
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) }
    private val gap = 4f

    fun scrollRows(delta: Int) {
        rowOffset = (rowOffset + delta).coerceIn(0, maxOf(0, pitches.size - visibleRows)); invalidate()
    }

    /** Center the view on a reasonable octave on first show. */
    fun centerOn(midi: Int) {
        val idx = pitches.indexOfFirst { it >= midi }.takeIf { it >= 0 } ?: pitches.size / 2
        rowOffset = (idx - visibleRows / 2).coerceIn(0, maxOf(0, pitches.size - visibleRows)); invalidate()
    }

    private fun cw() = (width - keyW) / steps
    private fun ch() = height.toFloat() / visibleRows
    private fun rowOf(pitch: Int): Int = pitches.indexOf(pitch) - rowOffset  // 0 = bottom
    private fun yTop(row: Int) = height - (row + 1) * ch()

    override fun onDraw(canvas: Canvas) {
        val cw = cw(); val ch = ch()
        for (r in 0 until visibleRows) {
            val pi = r + rowOffset
            if (pi >= pitches.size) break
            val p = pitches[pi]
            val y = yTop(r)
            // key column
            rect.set(0f, y + gap / 2, keyW - gap, y + ch - gap / 2)
            keyPaint.color = if (p % 12 == root) Color.parseColor("#2A2A36") else Palette.panel
            canvas.drawRoundRect(rect, 6f, 6f, keyPaint)
            canvas.drawText(Synth.name(p), keyW / 2 - gap / 2, y + ch / 2 + keyText.textSize / 3, keyText)
            // cells
            for (s in 0 until steps) {
                rect.set(keyW + s * cw + gap / 2, y + gap / 2, keyW + (s + 1) * cw - gap / 2, y + ch - gap / 2)
                canvas.drawRoundRect(rect, 5f, 5f, if (s % 4 == 0) cellBeat else cellOff)
            }
        }
        // notes
        for (n in notes) {
            val r = rowOf(n.pitch)
            if (r < 0 || r >= visibleRows) continue
            val y = yTop(r)
            val ns = n.start - offset
            if (ns + n.len <= 0 || ns >= steps) continue
            rect.set(keyW + maxOf(ns, 0) * cw + gap / 2, y + gap / 2, keyW + minOf(ns + n.len, steps) * cw - gap / 2, y + ch - gap / 2)
            notePaint.color = color
            canvas.drawRoundRect(rect, 6f, 6f, notePaint)
            canvas.drawText(Synth.name(n.pitch), rect.left + 6f, rect.bottom - 6f, noteText)
        }
        if (playhead in 0 until steps) {
            rect.set(keyW + playhead * cw, 0f, keyW + (playhead + 1) * cw, height.toFloat())
            canvas.drawRoundRect(rect, 6f, 6f, headPaint)
        }
    }

    private var drag: Note? = null
    private var downX = 0f; private var downY = 0f
    private var moved = false
    private var downStep = -1; private var downPitch = -1

    private fun noteAt(step: Int, pitch: Int): Note? = notes.firstOrNull { it.pitch == pitch && step >= it.start && step < it.start + it.len }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x; val y = e.y
        val row = (visibleRows - 1 - (y / ch()).toInt()).coerceIn(0, visibleRows - 1)
        val pi = row + rowOffset
        val pitch = pitches.getOrNull(pi) ?: return true
        val step = offset + (((x - keyW) / cw()).toInt()).coerceIn(0, steps - 1)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = x; downY = y; moved = false; downStep = step; downPitch = pitch
                if (x < keyW) { onPreview?.invoke(pitch); drag = null; return true }
                drag = noteAt(step, pitch)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && (abs(x - downX) > 18f || abs(y - downY) > 18f)) moved = true
                val d = drag
                if (moved && d != null) {
                    val newLen = (step - d.start + 1).coerceIn(1, totalSteps - d.start)
                    if (newLen != d.len) { d.len = newLen; invalidate(); onChange?.invoke() }
                } else if (moved && d == null && downX >= keyW) {
                    // dragging on empty space: paint a note then stretch it
                    if (noteAt(downStep, downPitch) == null) {
                        val n = Note(downPitch, downStep, 1); notes.add(n); drag = n
                        onPreview?.invoke(downPitch); invalidate(); onChange?.invoke()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!moved && downX >= keyW) {
                    val d = drag
                    if (d != null) notes.remove(d)
                    else { notes.add(Note(pitch, step, 1)); onPreview?.invoke(pitch) }
                    invalidate(); onChange?.invoke()
                }
                drag = null
            }
            MotionEvent.ACTION_CANCEL -> drag = null
        }
        return true
    }
}

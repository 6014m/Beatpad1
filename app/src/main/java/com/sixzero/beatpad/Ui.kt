package com.sixzero.beatpad

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.TextView

object Palette {
    val bg = Color.parseColor("#0E0E12")
    val panel = Color.parseColor("#17171D")
    val cell = Color.parseColor("#22222A")
    val cellBeat = Color.parseColor("#2C2C36")
    val text = Color.parseColor("#EDEDF2")
    val dim = Color.parseColor("#7A7A88")
    val accent = Color.parseColor("#FF5C5C")
    val cyan = Color.parseColor("#5CE1E6")
    val tracks = intArrayOf(
        Color.parseColor("#FF5C5C"), Color.parseColor("#FFB347"), Color.parseColor("#C68CFF"),
        Color.parseColor("#5CE1E6"), Color.parseColor("#8FD3FF"), Color.parseColor("#7CFC9B"),
        Color.parseColor("#FFE066"), Color.parseColor("#FF8FD6"),
    )
}

/** A flat, DAW-looking button built from a TextView so we control every pixel. */
class Pill(ctx: Context) : TextView(ctx) {
    private val shape = GradientDrawable().apply { cornerRadius = 12f * ctx.resources.displayMetrics.density }
    var fillColor: Int = Palette.cell
        set(v) { field = v; shape.setColor(v) }
    var strokeColor: Int = Color.TRANSPARENT
        set(v) { field = v; shape.setStroke((1.5f * ctx.resources.displayMetrics.density).toInt(), v) }

    init {
        gravity = Gravity.CENTER
        setTextColor(Palette.text)
        textSize = 13f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        letterSpacing = 0.08f
        isClickable = true
        isFocusable = true
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(60, 255, 255, 255)), shape, null)
        fillColor = Palette.cell
        val p = (10 * ctx.resources.displayMetrics.density).toInt()
        setPadding(p, 0, p, 0)
    }

    fun style(fill: Int, textCol: Int = Palette.text, stroke: Int = Color.TRANSPARENT) {
        fillColor = fill; setTextColor(textCol); strokeColor = stroke
    }
}

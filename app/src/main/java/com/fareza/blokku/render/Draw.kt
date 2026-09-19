package com.fareza.blokku.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/** Central paint/draw helpers shared by every scene. */
object D {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    val tmpRect = RectF()
    private val tmpPath = Path()

    var density = 1f
    var scaledDensity = 1f

    fun dp(v: Float) = v * density
    fun sp(v: Float) = v * scaledDensity

    fun color(v: Long) = v.toInt()

    fun withAlpha(color: Int, alpha: Int) = Color.argb(
        alpha, Color.red(color), Color.green(color), Color.blue(color)
    )

    fun lighten(color: Int, f: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * f).toInt()
        val g = (Color.green(color) + (255 - Color.green(color)) * f).toInt()
        val b = (Color.blue(color) + (255 - Color.blue(color)) * f).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    fun darken(color: Int, f: Float): Int {
        return Color.rgb(
            (Color.red(color) * (1 - f)).toInt(),
            (Color.green(color) * (1 - f)).toInt(),
            (Color.blue(color) * (1 - f)).toInt(),
        )
    }

    fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float = 0f) {
        p.shader = null
        p.color = color
        if (radius <= 0f) c.drawRect(l, t, r, b, p)
        else { tmpRect.set(l, t, r, b); c.drawRoundRect(tmpRect, radius, radius, p) }
    }

    fun rectStroke(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, width: Float, radius: Float = 0f) {
        p.shader = null
        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        if (radius <= 0f) c.drawRect(l, t, r, b, p)
        else { tmpRect.set(l, t, r, b); c.drawRoundRect(tmpRect, radius, radius, p) }
        p.style = Paint.Style.FILL
    }

    fun gradientRect(c: Canvas, l: Float, t: Float, r: Float, b: Float, c1: Int, c2: Int, radius: Float = 0f) {
        p.shader = LinearGradient(l, t, l, b, c1, c2, Shader.TileMode.CLAMP)
        if (radius <= 0f) c.drawRect(l, t, r, b, p)
        else { tmpRect.set(l, t, r, b); c.drawRoundRect(tmpRect, radius, radius, p) }
        p.shader = null
    }

    fun glowCircle(c: Canvas, x: Float, y: Float, radius: Float, color: Int, innerAlpha: Int = 140) {
        p.shader = RadialGradient(
            x, y, radius, withAlpha(color, innerAlpha), withAlpha(color, 0), Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, radius, p)
        p.shader = null
    }

    fun circle(c: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        p.shader = null
        p.color = color
        c.drawCircle(x, y, radius, p)
    }

    fun text(
        c: Canvas, s: String, x: Float, y: Float, sizePx: Float, color: Int,
        align: Paint.Align = Paint.Align.CENTER, bold: Boolean = true,
        alpha: Int = 255,
    ) {
        txt.shader = null
        txt.textSize = sizePx
        txt.color = color
        txt.alpha = alpha
        txt.textAlign = align
        txt.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        c.drawText(s, x, y, txt)
        txt.alpha = 255
    }

    /** Draw text vertically centered in a rect. */
    fun textIn(c: Canvas, s: String, r: RectF, sizePx: Float, color: Int, bold: Boolean = true) {
        txt.textSize = sizePx
        txt.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val fm = txt.fontMetrics
        text(c, s, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, sizePx, color, Paint.Align.CENTER, bold)
    }

    fun textWidth(s: String, sizePx: Float, bold: Boolean = true): Float {
        txt.textSize = sizePx
        txt.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        return txt.measureText(s)
    }

    /** A juicy rounded block cell with bevel highlight — the game's signature look. */
    fun blockCell(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float, alpha: Int = 255) {
        val w = r - l
        val h = b - t
        p.alpha = alpha
        gradientRect(c, l, t, r, b, lighten(color, 0.18f), color, radius)
        // inner top highlight
        p.color = withAlpha(Color.WHITE, alpha * 40 / 255)
        tmpRect.set(l + w * 0.10f, t + h * 0.08f, r - w * 0.10f, t + h * 0.34f)
        tmpPath.reset()
        tmpPath.addRoundRect(tmpRect, radius * 0.6f, radius * 0.6f, Path.Direction.CW)
        c.drawPath(tmpPath, p)
        // bottom shadow edge
        p.color = withAlpha(Color.BLACK, alpha * 30 / 255)
        tmpRect.set(l + w * 0.10f, b - h * 0.26f, r - w * 0.10f, b - h * 0.08f)
        tmpPath.reset()
        tmpPath.addRoundRect(tmpRect, radius * 0.6f, radius * 0.6f, Path.Direction.CW)
        c.drawPath(tmpPath, p)
        p.alpha = 255
    }

    fun measure(c: Canvas, s: String, sizePx: Float): Float {
        txt.textSize = sizePx
        return txt.measureText(s)
    }
}

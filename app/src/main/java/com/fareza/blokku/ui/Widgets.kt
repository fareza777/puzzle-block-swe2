package com.fareza.blokku.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import com.fareza.blokku.data.Save
import com.fareza.blokku.render.Anim
import com.fareza.blokku.render.D
import com.fareza.blokku.render.Ease

/** Pressable button with squash animation and optional icon glyph. */
class UiButton(
    var rect: RectF,
    var label: String = "",
    var icon: String = "",
    var bg: Int = Color.WHITE,
    var fg: Int = Color.BLACK,
    var onTap: () -> Unit = {},
    var textScale: Float = 1f,
    var radius: Float = -1f,
    var enabled: Boolean = true,
    var sublabel: String = "",
    var inScroll: Boolean = false, // rendered inside a scrolled clip — hit-test needs offset
    var bgEnd: Int = 0, // when non-zero, gradient runs bgEnd (top) → bg (bottom) verbatim
) {
    var pressT = 0f
    var appear = Anim(0.35f, Ease.outBack)
    var appearDelay = 0f

    fun contains(x: Float, y: Float) = rect.contains(x, y)

    fun press() { pressT = 1f }

    fun tap(): Boolean {
        if (!enabled) return false
        onTap()
        return true
    }

    fun update(dt: Float) {
        appear.update(dt)
        pressT = (pressT - dt * 6f).coerceAtLeast(0f)
    }

    fun render(c: Canvas) {
        val a = appear.v
        if (a <= 0f) return
        val cx = rect.centerX()
        val cy = rect.centerY()
        val scale = a * (1f - 0.12f * pressT)
        val rr = if (radius >= 0) radius else D.dp(18f)
        c.save()
        c.scale(scale, scale, cx, cy)
        val pressed = pressT > 0.01f
        // deeper layered shadow, sinks when pressed
        val shadowDy = if (pressed) D.dp(1.5f) else D.dp(4f)
        D.rect(c, rect.left, rect.top + shadowDy, rect.right, rect.bottom + shadowDy + D.dp(1f), D.withAlpha(Color.BLACK, 88), rr)
        if (bgEnd != 0) D.gradientRect(c, rect.left, rect.top, rect.right, rect.bottom, bgEnd, bg, rr)
        else D.gradientRect(c, rect.left, rect.top, rect.right, rect.bottom, D.lighten(bg, 0.26f), D.darken(bg, 0.05f), rr)
        // glass sheen on upper half
        D.p.alpha = 255
        D.p.shader = android.graphics.LinearGradient(
            rect.left, rect.top, rect.left, rect.top + rect.height() * 0.55f,
            D.withAlpha(Color.WHITE, 40), D.withAlpha(Color.WHITE, 0), android.graphics.Shader.TileMode.CLAMP,
        )
        D.tmpRect.set(rect.left + 1.5f, rect.top + 1.5f, rect.right - 1.5f, rect.top + rect.height() * 0.55f)
        c.drawRoundRect(D.tmpRect, rr - 1.5f, rr - 1.5f, D.p)
        D.p.shader = null
        D.rectStroke(c, rect.left + 0.8f, rect.top + 0.8f, rect.right - 0.8f, rect.bottom - 0.8f, D.withAlpha(Color.WHITE, 36), 1.3f, rr)
        val alpha = if (enabled) 255 else 110
        var size = D.sp(17f) * textScale
        if (icon.isNotEmpty()) {
            val gap = D.dp(9f)
            val avail = rect.width() - D.dp(22f)
            val need = D.textWidth(label, size) + size + gap
            if (need > avail) size = size * avail / need
            val tw = D.textWidth(label, size) + size + gap
            val startX = cx - tw / 2f
            val baseY = if (sublabel.isNotEmpty()) cy - D.dp(8f) else cy
            val ty = baseY - (D.txt.fontMetrics.run { ascent + descent } / 2f)
            // icon in a tinted chip
            val chipR = size * 0.78f
            D.circle(c, startX + size / 2f, baseY, chipR, D.withAlpha(fg, if (fg == Color.WHITE) 45 else 60))
            D.rectStroke(c, startX + size / 2f - chipR, baseY - chipR, startX + size / 2f + chipR, baseY + chipR, D.withAlpha(fg, 70), 1.2f, chipR)
            if (icon.startsWith("g:")) {
                Glyph.draw(c, icon.substring(2), RectF(startX + size / 2f - chipR * 0.62f, baseY - chipR * 0.62f, startX + size / 2f + chipR * 0.62f, baseY + chipR * 0.62f), D.withAlpha(fg, alpha))
            } else {
                D.text(c, icon, startX + size / 2f, ty, size * 0.92f, fg, alpha = alpha)
            }
            D.text(c, label, startX + size + gap + D.textWidth(label, size) / 2f, ty, size, fg, alpha = alpha)
            if (sublabel.isNotEmpty()) {
                D.textFit(c, sublabel, cx, rect.bottom - D.dp(11f), D.sp(11f) * textScale, rect.width() - D.dp(20f), D.withAlpha(fg, 215), bold = false, alpha = alpha)
            }
        } else {
            if (sublabel.isNotEmpty()) {
                val fs = D.fitSize(label, size, rect.width() - D.dp(20f))
                D.textIn(c, label, RectF(rect.left, rect.top, rect.right, rect.centerY() + D.dp(4f)), fs, fg)
                D.textIn(c, sublabel, RectF(rect.left, rect.centerY() + D.dp(2f), rect.right, rect.bottom + D.dp(6f)), D.fitSize(sublabel, D.sp(11f) * textScale, rect.width() - D.dp(20f), false), D.withAlpha(fg, 210), bold = false)
            } else {
                D.textIn(c, label, rect, D.fitSize(label, size, rect.width() - D.dp(20f)), fg)
            }
        }
        c.restore()
    }
}

/** Small circular icon button (settings, pause, back). */
class UiIconButton(
    val cx: Float, val cy: Float, val r: Float,
    val glyph: String,
    var bg: Int = D.withAlpha(Color.WHITE, 30),
    var fg: Int = Color.WHITE,
    var onTap: () -> Unit = {},
) {
    var pressT = 0f
    fun contains(x: Float, y: Float): Boolean {
        val dx = x - cx; val dy = y - cy
        return dx * dx + dy * dy <= (r * 1.5f) * (r * 1.5f)
    }
    fun update(dt: Float) { pressT = (pressT - dt * 6f).coerceAtLeast(0f) }
    fun render(c: Canvas) {
        val s = 1f - 0.15f * pressT
        c.save()
        c.scale(s, s, cx, cy)
        D.circle(c, cx, cy, r, bg)
        if (glyph.startsWith("g:")) {
            Glyph.draw(c, glyph.substring(2), RectF(cx - r * 0.55f, cy - r * 0.55f, cx + r * 0.55f, cy + r * 0.55f), fg)
        } else {
            D.text(c, glyph, cx, cy + r * 0.36f, r * 1.05f, fg)
        }
        c.restore()
    }
}

/** Toggle switch row for settings. */
class UiToggle(
    val rect: RectF,
    val label: String,
    var get: () -> Boolean,
    var set: (Boolean) -> Unit,
    var fg: Int = Color.WHITE,
    var accent: Int = Color.GREEN,
) {
    var animPos = -1f // -1 = uninitialized

    fun contains(x: Float, y: Float) = rect.contains(x, y)
    fun tap() { set(!get()) }
    fun animating() = animPos >= 0f && kotlin.math.abs(animPos - (if (get()) 1f else 0f)) > 0.01f

    fun render(c: Canvas) {
        if (animPos < 0) animPos = if (get()) 1f else 0f
        val target = if (get()) 1f else 0f
        animPos += (target - animPos) * 0.25f

        val tw = D.dp(52f); val th = D.dp(30f)
        D.textFit(c, label, rect.left, rect.centerY() + D.sp(15f) * 0.36f, D.sp(15f), rect.width() - tw - D.dp(12f), fg, android.graphics.Paint.Align.LEFT)

        val tr = RectF(rect.right - tw, rect.centerY() - th / 2, rect.right, rect.centerY() + th / 2)
        D.rect(c, tr.left, tr.top, tr.right, tr.bottom, D.withAlpha(Color.BLACK, 60), th / 2)
        val trackColor = lerpColor(D.withAlpha(accent, 70), accent, animPos)
        D.rect(c, tr.left, tr.top, tr.right, tr.bottom, trackColor, th / 2)
        val knobX = tr.left + th / 2 + (tw - th) * animPos
        D.circle(c, knobX, tr.centerY(), th / 2 - D.dp(3f), Color.WHITE)
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        return Color.argb(
            Anim.lerpInt(Color.alpha(a), Color.alpha(b), t),
            Anim.lerpInt(Color.red(a), Color.red(b), t),
            Anim.lerpInt(Color.green(a), Color.green(b), t),
            Anim.lerpInt(Color.blue(a), Color.blue(b), t),
        )
    }
}

/** Coin pill shown at top of menus — tappable to reach shop. */
class CoinPill(val x: Float, val y: Float, var onTap: () -> Unit = {}) {
    private val rect = RectF()
    var bounce = 0f
    var pulse = 0f

    fun bump() { bounce = 1f }
    fun contains(px: Float, py: Float) = rect.contains(px, py)

    fun render(c: Canvas) {
        bounce = (bounce - 0.06f).coerceAtLeast(0f)
        pulse += 0.03f
        val s = 1f + 0.15f * bounce
        val w = D.dp(98f) * s
        val h = D.dp(36f) * s
        rect.set(x, y - h / 2, x + w, y + h / 2)
        D.gradientRect(c, rect.left, rect.top, rect.right, rect.bottom, D.withAlpha(Color.BLACK, 140), D.withAlpha(Color.BLACK, 90), h / 2)
        D.rectStroke(c, rect.left + 0.6f, rect.top + 0.6f, rect.right - 0.6f, rect.bottom - 0.6f, D.withAlpha(Color.WHITE, 40), 1.2f, h / 2)
        // coin icon
        val coinX = rect.left + h * 0.55f
        D.circle(c, coinX, y + h * 0.06f, h * 0.34f, D.withAlpha(Color.BLACK, 60))
        D.circle(c, coinX, y, h * 0.32f, 0xFFFFD166.toInt())
        D.circle(c, coinX, y, h * 0.22f, 0xFFFFE8A0.toInt())
        D.rectStroke(c, coinX - h * 0.24f, y - h * 0.24f, coinX + h * 0.24f, y + h * 0.24f, D.withAlpha(0xFF40260A.toInt(), 120), 1.4f, h * 0.24f)
        D.text(c, "${Save.coins}", coinX + h * 0.42f, y + D.sp(13f) * 0.36f, D.sp(13f), Color.WHITE, android.graphics.Paint.Align.LEFT)
        D.circle(c, rect.right - h * 0.38f, y, h * 0.22f, D.withAlpha(0xFF7BE495.toInt(), 60))
        D.text(c, "+", rect.right - h * 0.38f, y + D.sp(13f) * 0.36f, D.sp(13f), 0xFF7BE495.toInt())
    }
}

/** Floating text that rises and fades (score popups, combo text). */
class FloatText(var x: Float, var y: Float, val text: String, val color: Int, val size: Float, var life: Float = 1.0f) {
    var age = 0f
    var vx = 0f
    fun update(dt: Float): Boolean {
        age += dt
        y -= D.dp(46f) * dt
        x += vx * dt
        return age < life
    }
    fun render(c: Canvas) {
        val k = 1f - age / life
        val scale = if (age < 0.15f) 0.6f + 0.4f * (age / 0.15f) else 1f
        c.save()
        c.scale(scale, scale, x, y)
        D.text(c, text, x, y, size, color, alpha = (255 * k).toInt())
        c.restore()
    }
}

/** Utility glyph drawing for power-up icons etc. */
object Glyph {
    fun draw(c: Canvas, kind: String, r: RectF, color: Int) {
        val cx = r.centerX(); val cy = r.centerY(); val s = r.width() * 0.5f
        when (kind) {
            "undo" -> {
                D.p.color = color
                D.p.style = android.graphics.Paint.Style.STROKE
                D.p.strokeWidth = s * 0.22f
                c.drawArc(RectF(cx - s * 0.7f, cy - s * 0.7f, cx + s * 0.7f, cy + s * 0.7f), -30f, -260f, false, D.p)
                D.p.style = android.graphics.Paint.Style.FILL
                val path = android.graphics.Path()
                path.moveTo(cx - s * 0.75f, cy - s * 0.15f)
                path.lineTo(cx - s * 0.15f, cy - s * 0.5f)
                path.lineTo(cx - s * 0.1f, cy + s * 0.15f)
                path.close()
                c.drawPath(path, D.p)
            }
            "rotate" -> {
                D.p.color = color
                D.p.style = android.graphics.Paint.Style.STROKE
                D.p.strokeWidth = s * 0.2f
                c.drawArc(RectF(cx - s * 0.65f, cy - s * 0.65f, cx + s * 0.65f, cy + s * 0.65f), 40f, 280f, false, D.p)
                D.p.style = android.graphics.Paint.Style.FILL
                val path = android.graphics.Path()
                path.moveTo(cx + s * 0.72f, cy + s * 0.05f)
                path.lineTo(cx + s * 0.2f, cy + s * 0.42f)
                path.lineTo(cx + s * 0.1f, cy - s * 0.2f)
                path.close()
                c.drawPath(path, D.p)
            }
            "bomb" -> {
                D.circle(c, cx, cy + s * 0.1f, s * 0.62f, color)
                D.p.color = color
                D.p.style = android.graphics.Paint.Style.STROKE
                D.p.strokeWidth = s * 0.14f
                c.drawLine(cx + s * 0.3f, cy - s * 0.42f, cx + s * 0.55f, cy - s * 0.68f, D.p)
                D.p.style = android.graphics.Paint.Style.FILL
                D.circle(c, cx + s * 0.62f, cy - s * 0.74f, s * 0.16f, 0xFFFFD166.toInt())
            }
            "shuffle" -> {
                D.p.color = color
                D.p.style = android.graphics.Paint.Style.STROKE
                D.p.strokeWidth = s * 0.18f
                val p1 = android.graphics.Path()
                p1.moveTo(cx - s * 0.7f, cy - s * 0.35f)
                p1.cubicTo(cx - s * 0.1f, cy - s * 0.35f, cx + s * 0.1f, cy + s * 0.35f, cx + s * 0.55f, cy + s * 0.35f)
                c.drawPath(p1, D.p)
                val p2 = android.graphics.Path()
                p2.moveTo(cx - s * 0.7f, cy + s * 0.35f)
                p2.cubicTo(cx - s * 0.1f, cy + s * 0.35f, cx + s * 0.1f, cy - s * 0.35f, cx + s * 0.55f, cy - s * 0.35f)
                c.drawPath(p2, D.p)
                D.p.style = android.graphics.Paint.Style.FILL
                val head = android.graphics.Path()
                head.moveTo(cx + s * 0.75f, cy - s * 0.35f)
                head.lineTo(cx + s * 0.4f, cy - s * 0.55f)
                head.lineTo(cx + s * 0.4f, cy - s * 0.15f)
                head.close()
                c.drawPath(head, D.p)
                val head2 = android.graphics.Path()
                head2.moveTo(cx + s * 0.75f, cy + s * 0.35f)
                head2.lineTo(cx + s * 0.4f, cy + s * 0.15f)
                head2.lineTo(cx + s * 0.4f, cy + s * 0.55f)
                head2.close()
                c.drawPath(head2, D.p)
            }
            "coin" -> {
                D.circle(c, cx, cy, s * 0.7f, 0xFFFFD166.toInt())
                D.circle(c, cx, cy, s * 0.45f, 0xFFFFE8A0.toInt())
            }
            "play" -> {
                D.p.color = color
                D.p.style = android.graphics.Paint.Style.FILL
                val path = android.graphics.Path()
                path.moveTo(cx - s * 0.3f, cy - s * 0.5f)
                path.lineTo(cx + s * 0.5f, cy)
                path.lineTo(cx - s * 0.3f, cy + s * 0.5f)
                path.close()
                c.drawPath(path, D.p)
            }
            "star" -> {
                D.p.color = color
                D.p.style = android.graphics.Paint.Style.FILL
                val path = android.graphics.Path()
                for (i in 0 until 10) {
                    val ang = -Math.PI / 2 + i * Math.PI / 5
                    val rr = if (i % 2 == 0) s * 0.8f else s * 0.36f
                    val px = cx + (Math.cos(ang) * rr).toFloat()
                    val py = cy + (Math.sin(ang) * rr).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                c.drawPath(path, D.p)
            }
            "lock" -> {
                D.p.color = color
                D.p.style = android.graphics.Paint.Style.STROKE
                D.p.strokeWidth = s * 0.2f
                c.drawArc(RectF(cx - s * 0.32f, cy - s * 0.78f, cx + s * 0.32f, cy - s * 0.14f), 180f, 180f, false, D.p)
                D.p.style = android.graphics.Paint.Style.FILL
                D.rect(c, cx - s * 0.55f, cy - s * 0.18f, cx + s * 0.55f, cy + s * 0.72f, color, s * 0.16f)
                D.circle(c, cx, cy + s * 0.18f, s * 0.12f, D.color(0xFF201430))
            }
            "gift" -> {
                D.rect(c, cx - s * 0.62f, cy - s * 0.1f, cx + s * 0.62f, cy + s * 0.68f, color, s * 0.1f)
                D.rect(c, cx - s * 0.7f, cy - s * 0.42f, cx + s * 0.7f, cy - s * 0.1f, color, s * 0.1f)
                // ribbon
                D.rect(c, cx - s * 0.09f, cy - s * 0.42f, cx + s * 0.09f, cy + s * 0.68f, D.withAlpha(0xFF8A2E1D.toInt(), 200), 0f)
                // bow
                D.p.color = color
                D.p.style = android.graphics.Paint.Style.STROKE
                D.p.strokeWidth = s * 0.14f
                c.drawArc(RectF(cx - s * 0.6f, cy - s * 0.85f, cx, cy - s * 0.35f), 180f, 140f, false, D.p)
                c.drawArc(RectF(cx, cy - s * 0.85f, cx + s * 0.6f, cy - s * 0.35f), -40f, 140f, false, D.p)
                D.p.style = android.graphics.Paint.Style.FILL
            }
            "check" -> {
                D.p.color = color
                D.p.style = android.graphics.Paint.Style.STROKE
                D.p.strokeWidth = s * 0.22f
                D.p.strokeCap = android.graphics.Paint.Cap.ROUND
                D.p.strokeJoin = android.graphics.Paint.Join.ROUND
                c.drawLine(cx - s * 0.55f, cy + s * 0.05f, cx - s * 0.12f, cy + s * 0.5f, D.p)
                c.drawLine(cx - s * 0.12f, cy + s * 0.5f, cx + s * 0.6f, cy - s * 0.45f, D.p)
                D.p.style = android.graphics.Paint.Style.FILL
                D.p.strokeCap = android.graphics.Paint.Cap.BUTT
            }
            "hint" -> { // lightbulb
                D.circle(c, cx, cy - s * 0.12f, s * 0.5f, color)
                D.rect(c, cx - s * 0.2f, cy + s * 0.32f, cx + s * 0.2f, cy + s * 0.6f, color, s * 0.07f)
                D.rect(c, cx - s * 0.26f, cy + s * 0.52f, cx + s * 0.26f, cy + s * 0.62f, color, s * 0.05f)
                // bulb base notch (cut with bg-ish dark)
                D.rect(c, cx - s * 0.16f, cy + s * 0.3f, cx + s * 0.16f, cy + s * 0.44f, D.withAlpha(0xFF201430.toInt(), 90), 0f)
            }
            "gear" -> {
                D.p.color = color
                D.p.style = android.graphics.Paint.Style.FILL
                D.circle(c, cx, cy, s * 0.42f, color)
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4
                    val px = cx + (Math.cos(a) * s * 0.62f).toFloat()
                    val py = cy + (Math.sin(a) * s * 0.62f).toFloat()
                    D.rect(c, px - s * 0.11f, py - s * 0.11f, px + s * 0.11f, py + s * 0.11f, color, s * 0.04f)
                }
                D.circle(c, cx, cy, s * 0.16f, D.color(0xFF201430))
            }
            "info" -> {
                D.p.color = color
                D.p.style = android.graphics.Paint.Style.STROKE
                D.p.strokeWidth = s * 0.16f
                c.drawCircle(cx, cy, s * 0.72f, D.p)
                D.p.style = android.graphics.Paint.Style.FILL
                D.circle(c, cx, cy - s * 0.32f, s * 0.1f, color)
                D.rect(c, cx - s * 0.08f, cy - s * 0.05f, cx + s * 0.08f, cy + s * 0.45f, color, s * 0.04f)
            }
            "pause" -> {
                D.rect(c, cx - s * 0.42f, cy - s * 0.55f, cx - s * 0.08f, cy + s * 0.55f, color, s * 0.09f)
                D.rect(c, cx + s * 0.08f, cy - s * 0.55f, cx + s * 0.42f, cy + s * 0.55f, color, s * 0.09f)
            }
            "back" -> {
                D.p.color = color
                D.p.style = android.graphics.Paint.Style.STROKE
                D.p.strokeWidth = s * 0.24f
                D.p.strokeCap = android.graphics.Paint.Cap.ROUND
                c.drawLine(cx + s * 0.35f, cy - s * 0.55f, cx - s * 0.3f, cy, D.p)
                c.drawLine(cx - s * 0.3f, cy, cx + s * 0.35f, cy + s * 0.55f, D.p)
                D.p.style = android.graphics.Paint.Style.FILL
                D.p.strokeCap = android.graphics.Paint.Cap.BUTT
            }
            "stats" -> {
                D.rect(c, cx - s * 0.62f, cy + s * 0.05f, cx - s * 0.26f, cy + s * 0.6f, color, s * 0.06f)
                D.rect(c, cx - s * 0.14f, cy - s * 0.3f, cx + s * 0.22f, cy + s * 0.6f, color, s * 0.06f)
                D.rect(c, cx + s * 0.34f, cy - s * 0.62f, cx + s * 0.7f, cy + s * 0.6f, color, s * 0.06f)
            }
            "themes" -> { // 2×2 mini grid
                val g = s * 0.12f
                val q = (s * 1.3f - g) / 2f
                D.rect(c, cx - s * 0.65f, cy - s * 0.65f, cx - s * 0.65f + q, cy - s * 0.65f + q, color, s * 0.08f)
                D.rect(c, cx - s * 0.65f + q + g, cy - s * 0.65f, cx - s * 0.65f + q * 2 + g, cy - s * 0.65f + q, D.withAlpha(color, 170), s * 0.08f)
                D.rect(c, cx - s * 0.65f, cy - s * 0.65f + q + g, cx - s * 0.65f + q, cy - s * 0.65f + q * 2 + g, D.withAlpha(color, 170), s * 0.08f)
                D.rect(c, cx - s * 0.65f + q + g, cy - s * 0.65f + q + g, cx - s * 0.65f + q * 2 + g, cy - s * 0.65f + q * 2 + g, color, s * 0.08f)
            }
            "daily" -> { // calendar
                D.rect(c, cx - s * 0.6f, cy - s * 0.42f, cx + s * 0.6f, cy + s * 0.62f, color, s * 0.12f)
                D.rect(c, cx - s * 0.6f, cy - s * 0.42f, cx + s * 0.6f, cy - s * 0.18f, D.withAlpha(0xFF201430.toInt(), 120), s * 0.12f)
                D.rect(c, cx - s * 0.36f, cy - s * 0.62f, cx - s * 0.22f, cy - s * 0.32f, color, s * 0.05f)
                D.rect(c, cx + s * 0.22f, cy - s * 0.62f, cx + s * 0.36f, cy - s * 0.32f, color, s * 0.05f)
                D.circle(c, cx, cy + s * 0.22f, s * 0.14f, D.color(0xFF201430))
            }
            "share" -> {
                D.p.color = color
                D.p.style = android.graphics.Paint.Style.STROKE
                D.p.strokeWidth = s * 0.16f
                D.p.strokeCap = android.graphics.Paint.Cap.ROUND
                c.drawLine(cx - s * 0.3f, cy, cx + s * 0.42f, cy - s * 0.42f, D.p)
                c.drawLine(cx - s * 0.3f, cy, cx + s * 0.42f, cy + s * 0.42f, D.p)
                D.p.style = android.graphics.Paint.Style.FILL
                D.circle(c, cx - s * 0.45f, cy, s * 0.22f, color)
                D.circle(c, cx + s * 0.55f, cy - s * 0.5f, s * 0.22f, color)
                D.circle(c, cx + s * 0.55f, cy + s * 0.5f, s * 0.22f, color)
                D.p.strokeCap = android.graphics.Paint.Cap.BUTT
            }
            "clock" -> {
                D.p.color = color
                D.p.style = android.graphics.Paint.Style.STROKE
                D.p.strokeWidth = s * 0.16f
                c.drawCircle(cx, cy, s * 0.72f, D.p)
                c.drawLine(cx, cy, cx, cy - s * 0.42f, D.p)
                c.drawLine(cx, cy, cx + s * 0.34f, cy + s * 0.18f, D.p)
                D.p.style = android.graphics.Paint.Style.FILL
            }
            "x" -> {
                D.p.color = color
                D.p.style = android.graphics.Paint.Style.STROKE
                D.p.strokeWidth = s * 0.22f
                D.p.strokeCap = android.graphics.Paint.Cap.ROUND
                c.drawLine(cx - s * 0.4f, cy - s * 0.4f, cx + s * 0.4f, cy + s * 0.4f, D.p)
                c.drawLine(cx + s * 0.4f, cy - s * 0.4f, cx - s * 0.4f, cy + s * 0.4f, D.p)
                D.p.style = android.graphics.Paint.Style.FILL
                D.p.strokeCap = android.graphics.Paint.Cap.BUTT
            }
            "trophy" -> {
                D.rect(c, cx - s * 0.44f, cy - s * 0.62f, cx + s * 0.44f, cy + s * 0.15f, color, s * 0.14f)
                D.p.color = color
                D.p.style = android.graphics.Paint.Style.STROKE
                D.p.strokeWidth = s * 0.14f
                c.drawArc(RectF(cx - s * 0.75f, cy - s * 0.6f, cx - s * 0.3f, cy - s * 0.05f), 90f, 140f, false, D.p)
                c.drawArc(RectF(cx + s * 0.3f, cy - s * 0.6f, cx + s * 0.75f, cy - s * 0.05f), -50f, 140f, false, D.p)
                D.p.style = android.graphics.Paint.Style.FILL
                D.rect(c, cx - s * 0.09f, cy + s * 0.1f, cx + s * 0.09f, cy + s * 0.45f, color, 0f)
                D.rect(c, cx - s * 0.36f, cy + s * 0.45f, cx + s * 0.36f, cy + s * 0.62f, color, s * 0.06f)
            }
            "hold" -> { // parked piece slot — box with a downward chevron
                D.p.color = color
                D.p.style = android.graphics.Paint.Style.STROKE
                D.p.strokeWidth = s * 0.16f
                D.p.strokeCap = android.graphics.Paint.Cap.ROUND
                c.drawRoundRect(RectF(cx - s * 0.68f, cy - s * 0.5f, cx + s * 0.68f, cy + s * 0.55f), s * 0.16f, s * 0.16f, D.p)
                c.drawLine(cx - s * 0.3f, cy - s * 0.14f, cx, cy + s * 0.18f, D.p)
                c.drawLine(cx, cy + s * 0.18f, cx + s * 0.3f, cy - s * 0.14f, D.p)
                D.p.style = android.graphics.Paint.Style.FILL
                D.p.strokeCap = android.graphics.Paint.Cap.BUTT
            }
            "target" -> { // contract chip — concentric rings + dot
                D.p.color = color
                D.p.style = android.graphics.Paint.Style.STROKE
                D.p.strokeWidth = s * 0.13f
                c.drawCircle(cx, cy, s * 0.62f, D.p)
                c.drawCircle(cx, cy, s * 0.36f, D.p)
                D.p.style = android.graphics.Paint.Style.FILL
                c.drawCircle(cx, cy, s * 0.14f, D.p)
            }
            "zen" -> { // lotus-ish calm mark — ring + petal arc
                D.p.color = color
                D.p.style = android.graphics.Paint.Style.STROKE
                D.p.strokeWidth = s * 0.13f
                c.drawCircle(cx, cy, s * 0.66f, D.p)
                c.drawArc(RectF(cx - s * 0.4f, cy - s * 0.42f, cx + s * 0.4f, cy + s * 0.42f), -30f, 240f, false, D.p)
                D.p.style = android.graphics.Paint.Style.FILL
            }
            "rush" -> { // lightning bolt
                val p = android.graphics.Path()
                p.moveTo(cx + s * 0.18f, cy - s * 0.72f)
                p.lineTo(cx - s * 0.4f, cy + s * 0.12f)
                p.lineTo(cx - s * 0.04f, cy + s * 0.12f)
                p.lineTo(cx - s * 0.18f, cy + s * 0.72f)
                p.lineTo(cx + s * 0.4f, cy - s * 0.1f)
                p.lineTo(cx + s * 0.02f, cy - s * 0.1f)
                p.close()
                D.p.color = color
                D.p.style = android.graphics.Paint.Style.FILL
                c.drawPath(p, D.p)
            }
            "puzzle" -> { // jigsaw piece silhouette
                D.p.color = color
                c.drawRoundRect(RectF(cx - s * 0.6f, cy - s * 0.45f, cx + s * 0.6f, cy + s * 0.6f), s * 0.14f, s * 0.14f, D.p)
                c.drawCircle(cx, cy - s * 0.52f, s * 0.2f, D.p)
                c.drawCircle(cx + s * 0.66f, cy + s * 0.08f, s * 0.2f, D.p)
            }
        }
    }
}

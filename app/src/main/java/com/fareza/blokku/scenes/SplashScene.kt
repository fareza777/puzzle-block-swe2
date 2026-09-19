package com.fareza.blokku.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import com.fareza.blokku.audio.Audio
import com.fareza.blokku.render.D
import com.fareza.blokku.render.Ease
import kotlin.math.sin

/** Animated brand splash: emblem assembles, wordmark drops in, shimmer bar, then slides to menu. */
class SplashScene : BaseScene() {

    private var t = 0f
    private var left = false

    override fun onEnter() {
        Audio.play("spawn")
    }

    override fun update(dt: Float) {
        super.update(dt)
        t += dt
        if (!left && t >= 1.75f) {
            left = true
            scene().swapTo(MenuScene())
        }
    }

    override fun wantsFrame() = true

    override fun render(c: Canvas) {
        renderBackground(c)
        val w = host.width.toFloat()
        val h = host.height.toFloat()
        val cx = w / 2f
        val cy = h * 0.40f

        // halo bloom behind the emblem
        val bloom = Ease.outCubic((t / 0.7f).coerceIn(0f, 1f))
        D.glowCircle(c, cx, cy, D.dp(190f) * (0.7f + 0.3f * bloom), D.color(theme.accent), (52 * bloom).toInt())

        // emblem cluster — six colored tiles popping in like pieces locking
        val tile = D.dp(30f)
        val gap = D.dp(5f)
        val emblem = arrayOf(
            intArrayOf(0, 0, 5), intArrayOf(1, 0, 1), intArrayOf(2, 0, 3),
            intArrayOf(0, 1, 2), intArrayOf(1, 1, 0), intArrayOf(2, 1, 4),
        )
        val ex0 = cx - (tile * 3 + gap * 2) / 2f
        val ey0 = cy - tile * 2.1f
        for (i in emblem.indices) {
            val (col, row, ci) = emblem[i]
            val delay = 0.12f + i * 0.075f
            val k = ((t - delay) / 0.42f).coerceIn(0f, 1f)
            if (k <= 0f) continue
            val e = Ease.outBack(k)
            val s = tile * e
            val tileCx = ex0 + col * (tile + gap) + tile / 2f
            val tileCy = ey0 + row * (tile + gap) + tile / 2f
            val dropY = (1f - e) * -D.dp(30f)
            D.blockCell(c, tileCx - s / 2f, tileCy - s / 2f + dropY, tileCx + s / 2f, tileCy + s / 2f + dropY, D.color(theme.blockColors[ci]), s * 0.24f, (255 * k).toInt())
        }

        // wordmark — letters drop in on colored tiles
        val word = "BLOKKU"
        val size = D.sp(46f)
        val totalW = D.textWidth(word, size) * 1.10f
        var lx = cx - totalW / 2f
        val ly = cy + D.dp(52f)
        for (i in word.indices) {
            val ch = word[i].toString()
            val cw = D.textWidth(ch, size)
            val delay = 0.42f + i * 0.055f
            val k = ((t - delay) / 0.5f).coerceIn(0f, 1f)
            if (k <= 0f) { lx += cw * 1.10f; continue }
            val e = Ease.outBack(k)
            val yOff = (1f - e) * -D.dp(60f)
            val wob = sin(t * 2.2f + i) * D.dp(2.5f) * e
            val bs = size * 0.92f * e
            val bl = lx - (bs - cw) / 2f
            val bt = ly - bs / 2f + yOff + wob - size * 0.34f
            D.blockCell(c, bl, bt, bl + bs, bt + bs, D.color(theme.blockColors[i % theme.blockColors.size]), bs * 0.22f, (235 * e).toInt())
            D.text(c, ch, lx + cw / 2f, bt + bs / 2f + size * 0.32f, size * 0.9f, Color.WHITE, alpha = (255 * e).toInt())
            lx += cw * 1.10f
        }

        // tagline fades in
        val ta = ((t - 0.95f) / 0.5f).coerceIn(0f, 1f)
        if (ta > 0f) {
            D.labelText(c, "a block puzzle", cx, ly + D.dp(42f), D.sp(12f), D.withAlpha(D.color(theme.textPrimary), (190 * ta).toInt()))
        }

        // shimmer progress bar
        val bw = D.dp(150f)
        val bx = cx - bw / 2f
        val by = h * 0.80f
        val ba = ((t - 0.7f) / 0.4f).coerceIn(0f, 1f)
        if (ba > 0f) {
            D.rect(c, bx, by, bx + bw, by + D.dp(4f), D.withAlpha(Color.WHITE, (26 * ba).toInt()), D.dp(2f))
            val fillW = bw * ((t - 0.7f) / 1.0f).coerceIn(0f, 1f)
            D.gradientRect(c, bx, by, bx + fillW, by + D.dp(4f), D.lighten(D.color(theme.accent), 0.2f), D.color(theme.accent), D.dp(2f))
            // shimmer head
            if (fillW > 2f) D.glowCircle(c, bx + fillW, by + D.dp(2f), D.dp(10f), Color.WHITE, (120 * ba).toInt())
        }

        // fade out into the scene swap
        if (t > 1.55f) {
            val fo = ((t - 1.55f) / 0.2f).coerceIn(0f, 1f)
            D.rect(c, 0f, 0f, w, h, D.withAlpha(D.color(theme.bgBottom), (255 * fo).toInt()), 0f)
        }
        renderFx(c)
    }

    override fun onTouch(e: MotionEvent): Boolean {
        // tap to skip once the brand beat has played
        if (!left && t > 0.7f) { left = true; scene().swapTo(MenuScene()) }
        return true
    }

    override fun onBack(): Boolean = true // swallow back on splash
}

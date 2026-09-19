package com.fareza.blokku.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.fareza.blokku.R
import com.fareza.blokku.data.GameTheme
import com.fareza.blokku.data.Save
import com.fareza.blokku.data.Themes
import com.fareza.blokku.render.D
import com.fareza.blokku.render.FrameActive
import com.fareza.blokku.render.Particles
import com.fareza.blokku.render.Scene
import com.fareza.blokku.ui.CoinPill
import com.fareza.blokku.ui.FloatText
import com.fareza.blokku.ui.UiIconButton
import kotlin.math.sin
import kotlin.random.Random

abstract class BaseScene : Scene(), FrameActive {

    val theme: GameTheme get() = Themes.current()
    val particles = Particles()
    val floats = ArrayList<FloatText>()
    var coinPill: CoinPill? = null
    var backButton: UiIconButton? = null

    // ambient floating blocks in the background — pure decoration
    private val decoBlocks = ArrayList<Deco>()
    private var decoInit = false

    class Deco(var x: Float, var y: Float, var size: Float, var color: Int, var speed: Float, var drift: Float, var rot: Float)

    private fun ensureDeco() {
        if (decoInit) return
        decoInit = true
        val r = Random(42)
        repeat(14) {
            val c = theme.blockColors[r.nextInt(theme.blockColors.size)]
            decoBlocks.add(
                Deco(
                    r.nextFloat(), r.nextFloat(),
                    D.dp(14f) + r.nextFloat() * D.dp(30f),
                    D.withAlpha(c.toInt(), 26),
                    D.dp(10f) + r.nextFloat() * D.dp(26f),
                    (r.nextFloat() - 0.5f) * D.dp(14f),
                    r.nextFloat() * 360f,
                )
            )
        }
    }

    override fun update(dt: Float) {
        particles.update(dt)
        val it = floats.iterator()
        while (it.hasNext()) if (!it.next().update(dt)) it.remove()
        ensureDeco()
        for (d in decoBlocks) {
            d.y -= d.speed * dt / host.height
            d.x += d.drift * dt / host.width + sin(host.globalTime * 0.7f + d.size) * 0.0002f
            d.rot += dt * 12f
            if (d.y < -0.08f) { d.y = 1.08f; d.x = Random.nextFloat() }
        }
    }

    override fun wantsFrame(): Boolean =
        particles.isActive || floats.isNotEmpty()

    fun renderBackground(c: Canvas) {
        val w = host.width.toFloat()
        val h = host.height.toFloat()
        D.gradientRect(c, 0f, 0f, w, h, D.color(theme.bgTop), D.color(theme.bgBottom))
        // soft radial glow behind center
        D.glowCircle(c, w / 2f, h * 0.38f, w * 0.7f, D.color(theme.accent), 26)
        // floating deco blocks
        for (d in decoBlocks) {
            c.save()
            c.rotate(d.rot, d.x * w, d.y * h)
            val s = d.size
            D.rect(c, d.x * w - s / 2, d.y * h - s / 2, d.x * w + s / 2, d.y * h + s / 2, d.color, s * 0.22f)
            c.restore()
        }
    }

    /** Common top bar: back button left, title center, coins right. */
    fun renderTopBar(c: Canvas, title: String) {
        val w = host.width.toFloat()
        val cy = host.safeTop + D.dp(20f)
        D.text(c, title, w / 2f, cy + D.sp(10f), D.sp(20f), D.color(theme.textPrimary))
        if (coinPill == null) coinPill = CoinPill(w - D.dp(110f), cy) { onCoinsTap() }
        coinPill?.render(c)
        backButton?.render(c)
    }

    open fun onCoinsTap() {}

    fun makeBackButton(): UiIconButton {
        val b = UiIconButton(D.dp(40f), host.safeTop + D.dp(20f), D.dp(19f), "‹") { scene().pop() }
        backButton = b
        return b
    }

    fun renderFx(c: Canvas) {
        particles.render(c)
        for (f in floats) f.render(c)
    }

    fun addFloat(x: Float, y: Float, text: String, color: Int, size: Float) {
        floats.add(FloatText(x, y, text, color, size))
        host.wake()
    }
}

package com.fareza.blokku.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.fareza.blokku.R
import com.fareza.blokku.audio.Audio
import com.fareza.blokku.audio.Haptic
import com.fareza.blokku.data.Achievements
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
                    D.withAlpha(c.toInt(), 40),
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
        D.glowCircle(c, w / 2f, h * 0.36f, w * 0.72f, D.color(theme.accent), 36)
        // floating deco blocks
        for (d in decoBlocks) {
            c.save()
            c.rotate(d.rot, d.x * w, d.y * h)
            val s = d.size
            D.rect(c, d.x * w - s / 2, d.y * h - s / 2, d.x * w + s / 2, d.y * h + s / 2, d.color, s * 0.22f)
            c.restore()
        }
        // vignette for depth
        D.p.alpha = 255
        D.p.shader = android.graphics.RadialGradient(
            w / 2f, h * 0.45f, h * 0.75f,
            D.withAlpha(Color.BLACK, 0), D.withAlpha(Color.BLACK, 100), android.graphics.Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, w, h, D.p)
        D.p.shader = null
    }

    /** Common top bar: back button left, title center, coins right. */
    fun renderTopBar(c: Canvas, title: String) {
        val w = host.width.toFloat()
        val cy = host.safeTop + D.dp(20f)
        D.text(c, title, w / 2f, cy + D.sp(9f), D.sp(20f), D.color(theme.textPrimary))
        // accent underline under title
        val tw = D.textWidth(title, D.sp(20f))
        D.rect(c, w / 2f - tw / 2f + D.dp(2f), cy + D.sp(16f), w / 2f + tw / 2f - D.dp(2f), cy + D.sp(16f) + D.dp(2.5f), D.withAlpha(D.color(theme.accent), 170), D.dp(1.5f))
        if (coinPill == null) coinPill = CoinPill(w - D.dp(108f), cy) { onCoinsTap() }
        coinPill?.render(c)
        backButton?.render(c)
    }

    open fun onCoinsTap() {}

    fun makeBackButton(): UiIconButton {
        val b = UiIconButton(D.dp(40f), host.safeTop + D.dp(20f), D.dp(19f), "g:back") { scene().pop() }
        b.bg = D.withAlpha(Color.WHITE, 44)
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

    /** Pop a gold toast for every achievement unlocked since the last check. */
    fun celebrateAchievements() {
        for (a in Achievements.checkAll()) {
            Audio.play("reward")
            Haptic.big()
            addFloat(
                host.width / 2f, host.height * 0.16f,
                "${s(R.string.ach_unlocked)}: ${s(a.labelRes)}",
                0xFFFFD166.toInt(), D.sp(14f),
            )
        }
    }
}

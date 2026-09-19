package com.fareza.blokku.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.fareza.blokku.R
import com.fareza.blokku.audio.Audio
import com.fareza.blokku.audio.Haptic
import com.fareza.blokku.core.Piece
import com.fareza.blokku.data.Save
import com.fareza.blokku.render.Anim
import com.fareza.blokku.render.D
import com.fareza.blokku.render.Ease
import com.fareza.blokku.ui.UiButton
import kotlin.math.min

/** Four-step illustrated tutorial carousel. */
class TutorialScene : BaseScene() {

    private var page = 0
    private var pageT = 0f
    private var nextBtn: UiButton? = null
    private var skipBtn: UiButton? = null
    private var backStack = false

    private val texts = listOf(R.string.tut_1, R.string.tut_2, R.string.tut_3, R.string.tut_4)

    override fun onEnter() {
        backStack = scene().size > 1
        buildButtons()
    }

    private fun buildButtons() {
        val w = host.width.toFloat()
        val h = host.height.toFloat()
        nextBtn = UiButton(
            RectF(w / 2f - D.dp(110f), h - D.dp(120f), w / 2f + D.dp(110f), h - D.dp(120f) + D.dp(54f)),
            s(if (page == texts.size - 1) R.string.tut_done else R.string.tut_next),
            bg = D.color(theme.accent), fg = Color.WHITE, onTap = {
                Audio.play("click"); Haptic.tick()
                if (page == texts.size - 1) close() else { page++; pageT = 0f; buildButtons() }
            })
        skipBtn = UiButton(
            RectF(w - D.dp(120f), host.safeTop, w - D.dp(24f), host.safeTop + D.dp(40f)),
            s(R.string.tut_skip), bg = D.withAlpha(Color.WHITE, 30), fg = D.color(theme.textPrimary), onTap = { close() }, textScale = 0.8f)
    }

    private fun close() {
        Save.tutorialDone = true
        scene().pop()
    }

    override fun update(dt: Float) {
        super.update(dt)
        pageT += dt
        nextBtn?.update(dt)
        skipBtn?.update(dt)
    }

    override fun wantsFrame() = true // tutorial animates continuously

    override fun render(c: Canvas) {
        renderBackground(c)
        val w = host.width.toFloat()
        val h = host.height.toFloat()

        D.text(c, s(R.string.menu_tutorial), w / 2f, host.safeTop + D.dp(30f), D.sp(22f), D.color(theme.textPrimary))

        // illustration area
        val ill = RectF(D.dp(40f), host.safeTop + D.dp(70f), w - D.dp(40f), h - D.dp(190f))
        D.rect(c, ill.left, ill.top, ill.right, ill.bottom, D.color(theme.boardBg), D.dp(20f))
        c.save()
        c.clipRect(ill)
        when (page) {
            0 -> illustrateDrag(c, ill)
            1 -> illustrateClear(c, ill)
            2 -> illustrateCombo(c, ill)
            3 -> illustrateEnd(c, ill)
        }
        c.restore()

        // page dots
        for (i in texts.indices) {
            val dx = w / 2f + (i - (texts.size - 1) / 2f) * D.dp(22f)
            D.circle(c, dx, ill.bottom + D.dp(22f), D.dp(if (i == page) 6f else 4f), if (i == page) D.color(theme.accent) else D.withAlpha(Color.WHITE, 80))
        }

        // caption, wrapped to fit width
        val capSize = D.sp(15f)
        val maxW = w - D.dp(48f)
        val words = s(texts[page]).split(" ")
        val lines = ArrayList<String>()
        var cur = ""
        for (word in words) {
            val try_ = if (cur.isEmpty()) word else "$cur $word"
            if (D.textWidth(try_, capSize, bold = false) <= maxW || cur.isEmpty()) cur = try_
            else { lines.add(cur); cur = word }
        }
        if (cur.isNotEmpty()) lines.add(cur)
        val lh = D.dp(22f)
        var ly = ill.bottom + D.dp(50f) - (lines.size - 1) * lh / 2f
        for (line in lines) {
            D.text(c, line, w / 2f, ly, capSize, D.color(theme.textPrimary), bold = false)
            ly += lh
        }

        nextBtn?.render(c)
        skipBtn?.render(c)
        renderFx(c)
    }

    private fun miniCell(c: Canvas, x: Float, y: Float, s: Float, colorIdx: Int, alpha: Int = 255) {
        D.blockCell(c, x, y, x + s * 0.9f, y + s * 0.9f, D.color(theme.blockColors[colorIdx % 7]), s * 0.2f, alpha)
    }

    private fun illustrateDrag(c: Canvas, ill: RectF) {
        // mini board + a piece floating toward it
        val s = min(ill.width() / 10f, ill.height() / 10f)
        val bx = ill.centerX() - s * 4
        val by = ill.centerY() - s * 3
        for (r in 0 until 8) for (col in 0 until 8) {
            D.rect(c, bx + col * s, by + r * s, bx + col * s + s * 0.88f, by + r * s + s * 0.88f, D.color(theme.cellEmpty), s * 0.16f)
        }
        // hand path: L piece moves along sine
        val t = (pageT % 2f) / 2f
        val px = ill.left + ill.width() * 0.2f + (ill.width() * 0.55f) * t
        val py = ill.bottom - s * 3f - (by + s - (ill.bottom - s * 3f)) * t
        val ghost = intArrayOf(0, 1, 16)
        for (gc in ghost) {
            val gr = gc shr 4; val gcx = gc and 15
            miniCell(c, bx + (2 + gcx) * s, by + (1 + gr) * s, s, 0, alpha = 90)
        }
        for (pc in intArrayOf(0, 1, 16)) {
            val pr = pc shr 4; val pcc = pc and 15
            miniCell(c, px + pcc * s * 0.8f, py + pr * s * 0.8f, s * 0.8f, 3)
        }
    }

    private fun illustrateClear(c: Canvas, ill: RectF) {
        val s = min(ill.width() / 10f, ill.height() / 10f)
        val bx = ill.centerX() - s * 4
        val by = ill.centerY() - s * 4
        for (r in 0 until 8) for (col in 0 until 8) {
            D.rect(c, bx + col * s, by + r * s, bx + col * s + s * 0.88f, by + r * s + s * 0.88f, D.color(theme.cellEmpty), s * 0.16f)
        }
        // filled row except 2; blink clear
        val row = 3
        val phase = (pageT * 1.5f) % 2f
        for (col in 0 until 8) {
            val flash = phase > 1f
            miniCell(c, bx + col * s, by + row * s, s, 1, alpha = if (flash) 120 + (100 * (phase - 1f)).toInt() else 255)
        }
        if (phase > 1f) {
            for (col in 0 until 8) {
                particles.burst(bx + col * s + s / 2, by + row * s + s / 2, D.color(theme.accent), count = 1, speed = D.dp(80f), size = D.dp(5f), life = 0.5f, gravity = 0f)
            }
        }
        // plus 1 piece hovering
        val t = (phase * 2f).coerceAtMost(1f)
        miniCell(c, bx + 8.4f * s + t * -s * 1.4f, by + row * s, s, 5)
    }

    private fun illustrateCombo(c: Canvas, ill: RectF) {
        val t = pageT
        val pulse = 1f + 0.12f * kotlin.math.sin(t * 6f)
        val txt = "COMBO ×3"
        c.save()
        c.scale(pulse, pulse, ill.centerX(), ill.centerY() - D.dp(20f))
        D.text(c, txt, ill.centerX(), ill.centerY() - D.dp(20f), D.sp(36f), D.color(theme.accent))
        c.restore()
        // meter
        val mw = ill.width() * 0.6f
        val mr = RectF(ill.centerX() - mw / 2, ill.centerY() + D.dp(30f), ill.centerX() + mw / 2, ill.centerY() + D.dp(46f))
        D.rect(c, mr.left, mr.top, mr.right, mr.bottom, D.withAlpha(Color.BLACK, 90), mr.height() / 2)
        val fill = (t % 2f) / 2f
        D.rect(c, mr.left, mr.top, mr.left + mr.width() * fill, mr.bottom, D.color(theme.accent), mr.height() / 2)
        D.text(c, "+1 ⚡", ill.centerX(), mr.bottom + D.dp(30f), D.sp(15f), 0xFFFFD166.toInt())
    }

    private fun illustrateEnd(c: Canvas, ill: RectF) {
        // board mostly full, red tint pulsing
        val s = min(ill.width() / 10f, ill.height() / 10f)
        val bx = ill.centerX() - s * 4
        val by = ill.centerY() - s * 4
        var ci = 0
        for (r in 0 until 8) for (col in 0 until 8) {
            if ((r * 8 + col + r) % 3 != 0) {
                miniCell(c, bx + col * s, by + r * s, s, (ci++) % 7)
            } else {
                D.rect(c, bx + col * s, by + r * s, bx + col * s + s * 0.88f, by + r * s + s * 0.88f, D.color(theme.cellEmpty), s * 0.16f)
            }
        }
        val pulse = (0.5f + 0.5f * kotlin.math.sin(pageT * 3f))
        D.rect(c, ill.left, ill.top, ill.right, ill.bottom, D.withAlpha(0xFFFF5D73.toInt(), (pulse * 40).toInt()))
        D.text(c, "✕", ill.centerX(), ill.centerY(), D.sp(60f), D.withAlpha(Color.WHITE, (pulse * 200).toInt()))
    }

    override fun onTouch(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN) return true
        nextBtn?.let { if (it.contains(e.x, e.y)) { it.pressT = 1f; it.tap(); return true } }
        skipBtn?.let { if (it.contains(e.x, e.y)) { it.pressT = 1f; it.tap(); return true } }
        return true
    }
}

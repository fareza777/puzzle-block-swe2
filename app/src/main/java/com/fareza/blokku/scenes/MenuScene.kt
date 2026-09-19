package com.fareza.blokku.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.fareza.blokku.R
import com.fareza.blokku.ads.Ads
import com.fareza.blokku.audio.Audio
import com.fareza.blokku.audio.Haptic
import com.fareza.blokku.core.Daily
import com.fareza.blokku.core.GameEngine
import com.fareza.blokku.data.Missions
import com.fareza.blokku.data.Save
import com.fareza.blokku.render.Anim
import com.fareza.blokku.render.D
import com.fareza.blokku.render.Ease
import com.fareza.blokku.ui.UiButton
import com.fareza.blokku.ui.UiIconButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random

class MenuScene : BaseScene() {

    private val buttons = ArrayList<UiButton>()
    private var logoT = 0f
    private var rewardBtn: UiButton? = null
    private var settingsBtn: UiIconButton? = null
    private var infoBtn: UiIconButton? = null
    private var canClaimDaily = false
    private var claimedFlash = 0f
    private var built = false

    override fun onEnter() {
        canClaimDaily = canClaimDailyReward()
        built = false
        Audio.play("spawn")
    }

    private fun canClaimDailyReward(): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return Save.lastDailyReward != today
    }

    private fun buildButtons() {
        buttons.clear()
        built = true
        val w = host.width.toFloat()
        val h = host.height.toFloat()
        val cx = w / 2f
        val bw = w - D.dp(48f)
        val bx = cx - bw / 2f
        var y = h * 0.40f

        fun btn(labelRes: Int, sub: String = "", bg: Int, icon: String = "", hgt: Float = 62f, onTap: () -> Unit): UiButton {
            val b = UiButton(RectF(bx, y, bx + bw, y + D.dp(hgt)), s(labelRes), icon = icon, bg = bg, fg = Color.WHITE, sublabel = sub, onTap = {
                Audio.play("click"); Haptic.tick(); onTap()
            })
            b.appearDelay = buttons.size * 0.06f
            b.appear.t = -b.appearDelay
            buttons.add(b)
            return b
        }

        btn(R.string.menu_classic, bg = D.color(theme.accent), icon = "▶") {
            scene().push(GameScene(GameEngine.classic()))
        }
        y += D.dp(72f)
        // row of two
        val half = (bw - D.dp(12f)) / 2f
        val lvl = UiButton(RectF(bx, y, bx + half, y + D.dp(56f)), s(R.string.menu_levels), icon = "★", bg = D.color(theme.blockColors[5]).let { Color.rgb(Color.red(it), Color.green(it), Color.blue(it)) }, fg = Color.WHITE, onTap = {
            Audio.play("click"); Haptic.tick(); scene().push(LevelSelectScene())
        })
        lvl.appearDelay = 0.1f; lvl.appear.t = -0.1f
        val dailyBtn = UiButton(RectF(bx + half + D.dp(12f), y, bx + bw, y + D.dp(56f)), s(R.string.menu_daily), icon = "◆", bg = 0xFF38BDF8.toInt(), fg = Color.WHITE, onTap = {
            Audio.play("click"); Haptic.tick()
            val eng = Daily.todayEngine()
            scene().push(GameScene(eng, dailySeed = Daily.seedForToday()))
        })
        dailyBtn.appearDelay = 0.14f; dailyBtn.appear.t = -0.14f
        buttons.add(lvl); buttons.add(dailyBtn)
        y += D.dp(66f)

        // row of three smaller
        val third = (bw - D.dp(24f)) / 3f
        var xx = bx
        for ((res, ic, act) in listOf(
            Triple(R.string.menu_missions, "✓") { scene().push(MissionsScene()) },
            Triple(R.string.menu_stats, "▤") { scene().push(StatsScene()) },
            Triple(R.string.menu_themes, "◈") { scene().push(ThemesScene()) },
        )) {
            val b = UiButton(RectF(xx, y, xx + third, y + D.dp(52f)), s(res), icon = ic, bg = D.withAlpha(Color.WHITE, 32), fg = D.color(theme.textPrimary), onTap = {
                Audio.play("click"); Haptic.tick(); act()
            }, textScale = 0.8f)
            b.appearDelay = 0.18f + buttons.size * 0.02f
            b.appear.t = -b.appearDelay
            buttons.add(b)
            xx += third + D.dp(12f)
        }
        y += D.dp(64f)

        // daily reward card
        if (canClaimDaily) {
            val rb = UiButton(RectF(bx, y, bx + bw, y + D.dp(52f)), s(R.string.daily_reward), icon = "🎁", bg = 0xFFFFB84D.toInt(), fg = 0xFF40260A.toInt(), onTap = { claimDaily() })
            rb.appearDelay = 0.3f; rb.appear.t = -0.3f
            buttons.add(rb)
            rewardBtn = rb
        }

        settingsBtn = UiIconButton(D.dp(40f), host.safeTop + D.dp(20f), D.dp(19f), "⚙") { scene().push(SettingsScene()) }
        infoBtn = UiIconButton(D.dp(88f), host.safeTop + D.dp(20f), D.dp(19f), "?") { scene().push(TutorialScene()) }
    }

    private fun claimDaily() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        Save.lastDailyReward = today
        val reward = 80
        Save.coins += reward
        canClaimDaily = false
        rewardBtn = null
        buttons.removeAll { it.label == s(R.string.daily_reward) }
        Audio.play("reward")
        Haptic.success()
        coinPill?.bump()
        claimedFlash = 1f
        addFloat(host.width / 2f, host.height * 0.45f, "+$reward ${s(R.string.coins)}", 0xFFFFD166.toInt(), D.sp(22f))
    }

    override fun onResume() {
        // refresh daily state + coins
        canClaimDaily = canClaimDailyReward()
        if (host.width > 0) buildButtons() else built = false
    }

    override fun update(dt: Float) {
        super.update(dt)
        if (!built && host.width > 0) buildButtons()
        logoT += dt
        claimedFlash = (claimedFlash - dt).coerceAtLeast(0f)
        for (b in buttons) b.update(dt)
        settingsBtn?.update(dt)
        infoBtn?.update(dt)
    }

    override fun wantsFrame() = super.wantsFrame() || !built || logoT < 2f || buttons.any { !it.appear.done }

    override fun render(c: Canvas) {
        renderBackground(c)
        renderTopBar(c, "")
        renderLogo(c)
        for (b in buttons) b.render(c)
        settingsBtn?.render(c)
        infoBtn?.render(c)
        renderFx(c)
    }

    /** Animated logo: BLOKKU built from colored blocks dropping in. */
    private fun renderLogo(c: Canvas) {
        val w = host.width.toFloat()
        val cy = host.height * 0.24f
        val letters = "BLOKKU"
        val size = D.sp(52f)
        val totalW = D.textWidth(letters, size) * 1.08f
        var x = w / 2f - totalW / 2f
        val colors = theme.blockColors
        for (i in letters.indices) {
            val ch = letters[i].toString()
            val cw = D.textWidth(ch, size)
            val delay = i * 0.07f
            val k = ((logoT - delay) / 0.5f).coerceIn(0f, 1f)
            val e = Ease.outBack(k)
            val yOff = (1f - e) * -D.dp(80f)
            val wob = sin(logoT * 2f + i) * D.dp(3f) * e
            val col = D.color(colors[i % colors.size])
            // block square behind letter
            val bs = size * 0.95f * e
            if (e > 0f) {
                val l = x - (bs - cw) / 2f
                val t = cy - bs / 2f + yOff + wob - size * 0.35f
                D.blockCell(c, l, t, l + bs, t + bs, col, bs * 0.2f, (255 * e).toInt())
                D.text(c, ch, x + cw / 2f, t + bs / 2f + size * 0.32f, size * 0.9f, Color.WHITE, alpha = (255 * e).toInt())
            }
            x += cw * 1.08f
        }
        // tagline
        val a = ((logoT - 0.5f) / 0.6f).coerceIn(0f, 1f)
        D.text(c, "a block puzzle", w / 2f, cy + D.dp(52f), D.sp(14f), D.withAlpha(D.color(theme.textPrimary), (180 * a).toInt()), bold = false)
        // best score chip
        if (Save.bestClassic > 0) {
            val label = "★ ${s(R.string.best)}: ${Save.bestClassic}"
            val tw = D.textWidth(label, D.sp(13f)) + D.dp(24f)
            D.rect(c, w / 2f - tw / 2, cy + D.dp(66f), w / 2f + tw / 2, cy + D.dp(94f), D.withAlpha(Color.BLACK, 60), D.dp(14f))
            D.text(c, label, w / 2f, cy + D.dp(85f), D.sp(13f), 0xFFFFD166.toInt())
        }
    }

    override fun onTouch(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN) return true
        settingsBtn?.let { if (it.contains(e.x, e.y)) { it.pressT = 1f; it.onTap(); Audio.play("click"); return true } }
        infoBtn?.let { if (it.contains(e.x, e.y)) { it.pressT = 1f; it.onTap(); Audio.play("click"); return true } }
        coinPill?.let { if (it.contains(e.x, e.y)) { onCoinsTap(); return true } }
        for (b in buttons) {
            if (b.contains(e.x, e.y)) { b.pressT = 1f; b.tap(); return true }
        }
        return true
    }

    override fun onCoinsTap() {
        Audio.play("click")
        scene().push(ThemesScene())
    }

    override fun onBack(): Boolean = false // let app handle exit
}

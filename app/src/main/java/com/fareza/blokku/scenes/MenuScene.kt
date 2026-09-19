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
import com.fareza.blokku.ui.Glyph
import com.fareza.blokku.ui.UiButton
import com.fareza.blokku.ui.UiIconButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random

class MenuScene : BaseScene() {

    private data class Quad(val res: Int, val ic: String, val sub: Int, val col: Int, val act: () -> Unit)

    private val buttons = ArrayList<UiButton>()
    private var logoT = 0f
    private var rewardBtn: UiButton? = null
    private var settingsBtn: UiIconButton? = null
    private var infoBtn: UiIconButton? = null
    private var canClaimDaily = false
    private var claimedFlash = 0f
    private var built = false
    private var dailyCardRect = RectF()
    private var statsRect = RectF()
    private var parallaxX = 0f

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
        // Stats strip is pinned to the bottom; the button column is centered
        // in the space between the logo block and the strip so no dead space.
        val stripH = D.dp(64f)
        statsRect = RectF(D.dp(20f), h - D.dp(14f) - stripH, w - D.dp(20f), h - D.dp(14f))
        val contentTop = h * 0.36f
        val dailyH = D.dp(60f)
        val continueH = if (Save.hasSavedRun()) D.dp(52f) + D.dp(12f) else 0f
        val colH = continueH + D.dp(74f) + D.dp(16f) + D.dp(60f) + D.dp(14f) + D.dp(60f) + D.dp(14f) + D.dp(56f) + D.dp(18f) + dailyH
        var y = contentTop + maxOf(0f, (statsRect.top - D.dp(14f) - contentTop - colH) / 2f)

        // Continue saved classic run — slim banner above the hero
        if (Save.hasSavedRun()) {
            val saved = Save.runJson.split(';').getOrNull(1)?.toIntOrNull() ?: 0
            val cb = UiButton(RectF(bx, y, bx + bw, y + D.dp(52f)), s(R.string.continue_run, saved), icon = "g:play", bg = D.lighten(D.color(theme.boardBg), 0.16f), fg = D.color(theme.accent), onTap = {
                Audio.play("click"); Haptic.tick()
                val eng = GameEngine.fromJson(Save.runJson)
                if (eng != null) scene().push(GameScene(eng)) else scene().push(GameScene(GameEngine.classic()))
            }, textScale = 0.9f)
            cb.appearDelay = 0f; cb.appear.t = 0f
            buttons.add(cb)
            y += continueH
        }

        fun btn(labelRes: Int, sub: String = "", bg: Int, icon: String = "", hgt: Float = 62f, onTap: () -> Unit): UiButton {
            val b = UiButton(RectF(bx, y, bx + bw, y + D.dp(hgt)), s(labelRes), icon = icon, bg = bg, fg = Color.WHITE, sublabel = sub, onTap = {
                Audio.play("click"); Haptic.tick(); onTap()
            })
            b.appearDelay = buttons.size * 0.06f
            b.appear.t = -b.appearDelay
            buttons.add(b)
            return b
        }

        val hero = UiButton(RectF(bx, y, bx + bw, y + D.dp(74f)), s(R.string.menu_classic), icon = "g:play", bg = 0xFFF07818.toInt(), fg = Color.WHITE, sublabel = s(R.string.menu_classic_sub), onTap = {
            Audio.play("click"); Haptic.tick(); scene().push(GameScene(GameEngine.classic()))
        })
        hero.bgEnd = 0xFFFFC65C.toInt()
        hero.appearDelay = buttons.size * 0.06f; hero.appear.t = -hero.appearDelay
        buttons.add(hero)
        y += D.dp(90f)
        // row of two
        val half = (bw - D.dp(12f)) / 2f
        val lvl = UiButton(RectF(bx, y, bx + half, y + D.dp(60f)), s(R.string.menu_levels), icon = "g:trophy", bg = D.color(theme.blockColors[5]).let { Color.rgb(Color.red(it), Color.green(it), Color.blue(it)) }, fg = Color.WHITE, onTap = {
            Audio.play("click"); Haptic.tick(); scene().push(LevelSelectScene())
        })
        lvl.appearDelay = 0.1f; lvl.appear.t = -0.1f
        val dailySub = if (Save.dailyStreak > 0) s(R.string.day_streak, Save.dailyStreak) else ""
        val dailyBtn = UiButton(RectF(bx + half + D.dp(12f), y, bx + bw, y + D.dp(60f)), s(R.string.menu_daily), icon = "g:daily", bg = 0xFF38BDF8.toInt(), fg = Color.WHITE, sublabel = dailySub, onTap = {
            Audio.play("click"); Haptic.tick()
            val eng = Daily.todayEngine()
            scene().push(GameScene(eng, dailySeed = Daily.seedForToday()))
        })
        dailyBtn.appearDelay = 0.14f; dailyBtn.appear.t = -0.14f
        buttons.add(lvl); buttons.add(dailyBtn)
        y += D.dp(74f)

        // modes row of three — Zen | Rush | Puzzle
        val third = (bw - D.dp(24f)) / 3f
        var mx = bx
        for ((res, ic, sub, col, act) in listOf(
            Quad(R.string.menu_zen, "g:zen", R.string.menu_zen_sub, 0xFF2DD4BF.toInt()) { scene().push(GameScene(GameEngine.zen())) },
            Quad(R.string.menu_rush, "g:rush", R.string.menu_rush_sub, 0xFFFF5D73.toInt()) { scene().push(GameScene(GameEngine.rush())) },
            Quad(R.string.menu_puzzle, "g:puzzle", R.string.menu_puzzle_sub, 0xFFBA8DF5.toInt()) { scene().push(PuzzleSelectScene()) },
        )) {
            val b = UiButton(RectF(mx, y, mx + third, y + D.dp(60f)), s(res), icon = ic, bg = col, fg = Color.WHITE, sublabel = s(sub), onTap = {
                Audio.play("click"); Haptic.tick(); act()
            }, textScale = 0.85f)
            b.appearDelay = 0.16f + buttons.size * 0.02f
            b.appear.t = -b.appearDelay
            buttons.add(b)
            mx += third + D.dp(12f)
        }
        y += D.dp(74f)

        // row of three smaller
        val smallW = (bw - D.dp(24f)) / 3f
        var xx = bx
        for ((res, ic, act) in listOf(
            Triple(R.string.menu_missions, "g:check") { scene().push(MissionsScene()) },
            Triple(R.string.menu_stats, "g:stats") { scene().push(StatsScene()) },
            Triple(R.string.menu_themes, "g:themes") { scene().push(ThemesScene()) },
        )) {
            val b = UiButton(RectF(xx, y, xx + smallW, y + D.dp(56f)), s(res), icon = ic, bg = D.lighten(D.color(theme.boardBg), 0.10f), fg = D.color(theme.textPrimary), onTap = {
                Audio.play("click"); Haptic.tick(); act()
            }, textScale = 0.8f)
            b.appearDelay = 0.18f + buttons.size * 0.02f
            b.appear.t = -b.appearDelay
            buttons.add(b)
            xx += smallW + D.dp(12f)
        }
        y += D.dp(74f)

        // daily reward card — always visible: gradient CTA when claimable,
        // muted "claimed" card otherwise (keeps the layout symmetric)
        dailyCardRect = RectF(bx, y, bx + bw, y + dailyH)
        if (canClaimDaily) {
            val rb = UiButton(RectF(dailyCardRect), s(R.string.daily_reward), icon = "g:gift", bg = 0xFFE8890C.toInt(), fg = Color.WHITE, sublabel = "+80 ${s(R.string.coins)}", onTap = { claimDaily() })
            rb.bgEnd = 0xFFFFCF5C.toInt()
            rb.appearDelay = 0.3f; rb.appear.t = -0.3f
            buttons.add(rb)
            rewardBtn = rb
        }

        settingsBtn = UiIconButton(D.dp(40f), host.safeTop + D.dp(20f), D.dp(19f), "g:gear") { scene().push(SettingsScene()) }
        settingsBtn?.bg = D.withAlpha(Color.WHITE, 44)
        infoBtn = UiIconButton(D.dp(92f), host.safeTop + D.dp(20f), D.dp(19f), "g:info") { scene().push(TutorialScene()) }
        infoBtn?.bg = D.withAlpha(Color.WHITE, 44)
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

    override fun wantsFrame() = super.wantsFrame() || !built || logoT < 6f || buttons.any { !it.appear.done }

    override fun render(c: Canvas) {
        renderBackground(c)
        renderTopBar(c, "")
        renderLogo(c)
        for (b in buttons) b.render(c)
        settingsBtn?.render(c)
        infoBtn?.render(c)
        renderDailyCard(c)
        renderStatsStrip(c)
        renderFx(c)
    }

    /** Muted daily card shown after the reward is claimed (keeps layout symmetric). */
    private fun renderDailyCard(c: Canvas) {
        if (canClaimDaily || dailyCardRect.isEmpty) return
        val r = dailyCardRect
        D.card(c, r.left, r.top, r.right, r.bottom, D.color(theme.boardBg), D.dp(18f), elevated = false)
        val chipR = D.dp(17f)
        val icy = r.centerY()
        D.circle(c, r.left + D.dp(34f), icy, chipR, D.withAlpha(0xFF7BE495.toInt(), 40))
        Glyph.draw(c, "check", RectF(r.left + D.dp(34f) - chipR * 0.55f, icy - chipR * 0.55f, r.left + D.dp(34f) + chipR * 0.55f, icy + chipR * 0.55f), 0xFF7BE495.toInt())
        val maxTw = r.right - (r.left + D.dp(60f)) - D.dp(14f)
        D.labelTextFit(c, s(R.string.daily_reward), r.left + D.dp(60f), icy - D.sp(3f), D.sp(11f), maxTw, D.withAlpha(D.color(theme.textPrimary), 150), align = Paint.Align.LEFT)
        val status = if (Save.dailyStreak > 0) "${s(R.string.claimed)} • ${s(R.string.day_streak, Save.dailyStreak)}" else s(R.string.claimed)
        D.textFit(c, status, r.left + D.dp(60f), icy + D.sp(13f), D.sp(13f), maxTw, D.withAlpha(D.color(theme.textPrimary), 200), bold = false, align = Paint.Align.LEFT)
    }

    /** Bottom strip: best / games / best combo — taps through to Stats. */
    private fun renderStatsStrip(c: Canvas) {
        if (statsRect.isEmpty) return
        val r = statsRect
        D.card(c, r.left, r.top, r.right, r.bottom, D.color(theme.boardBg), D.dp(18f), elevated = false)
        val cols = arrayOf(
            Triple("star", s(R.string.best), "${Save.bestClassic}"),
            Triple("play", s(R.string.menu_stat_games), "${Save.gamesPlayed}"),
            Triple("x", s(R.string.combo), "${Save.lifetimeBestCombo}"),
        )
        val cw = r.width() / 3f
        for (i in cols.indices) {
            val (ic, label, value) = cols[i]
            val cx = r.left + cw * i + cw / 2f
            val tint = theme.blockColors[i * 2]
            val vs = D.fitSize(value, D.sp(15f), cw - D.dp(30f))
            Glyph.draw(c, ic, RectF(cx - D.textWidth(value, vs) / 2f - D.dp(16f), r.top + D.sp(26f) - D.dp(7f), cx - D.textWidth(value, vs) / 2f - D.dp(2f), r.top + D.sp(26f) + D.dp(7f)), D.color(tint))
            D.text(c, value, cx + D.dp(6f), r.top + D.sp(26f), vs, D.color(theme.textPrimary))
            D.labelTextFit(c, label, cx, r.top + D.sp(42f), D.sp(8.5f), cw - D.dp(10f), D.withAlpha(D.color(theme.textPrimary), 130))
            if (i > 0) D.rect(c, r.left + cw * i - 0.5f, r.top + D.dp(14f), r.left + cw * i + 0.5f, r.bottom - D.dp(14f), D.withAlpha(Color.WHITE, 22), 0f)
        }
    }

    /** Animated logo: BLOKKU built from colored blocks dropping in, with breathing halo. */
    private fun renderLogo(c: Canvas) {
        val w = host.width.toFloat()
        val cy = host.height * 0.235f
        // slow parallax deco — two big soft blocks drifting opposite ways
        val px = sin(host.globalTime * 0.22f) * w * 0.04f
        D.blockCell(c, w * 0.10f + px, cy - D.dp(34f), w * 0.10f + px + D.dp(46f), cy - D.dp(34f) + D.dp(46f), D.color(theme.blockColors[1]), D.dp(9f), 26)
        D.blockCell(c, w * 0.84f - px, cy + D.dp(6f), w * 0.84f - px + D.dp(36f), cy + D.dp(6f) + D.dp(36f), D.color(theme.blockColors[3]), D.dp(7f), 30)
        // soft halo behind the wordmark, breathing
        val breathe = 1f + 0.10f * sin(host.globalTime * 1.6f)
        D.glowCircle(c, w / 2f, cy, D.dp(150f) * breathe, D.color(theme.accent), 34)
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
        D.labelText(c, "a block puzzle", w / 2f, cy + D.dp(54f), D.sp(12f), D.withAlpha(D.color(theme.textPrimary), (200 * a).toInt()))
        // best score chip
        if (Save.bestClassic > 0) {
            val label = "${s(R.string.best)}: ${Save.bestClassic}"
            val sw = D.dp(14f)
            val tw = D.textWidth(label, D.sp(13f)) + D.dp(28f) + sw
            D.rect(c, w / 2f - tw / 2, cy + D.dp(70f), w / 2f + tw / 2 + D.dp(2f), cy + D.dp(100f) + D.dp(3f), D.withAlpha(Color.BLACK, 60), D.dp(16f))
            D.gradientRect(c, w / 2f - tw / 2, cy + D.dp(70f), w / 2f + tw / 2, cy + D.dp(100f), D.withAlpha(D.color(theme.boardBg), 220), D.withAlpha(D.darken(D.color(theme.boardBg), 0.15f), 220), D.dp(16f))
            D.rectStroke(c, w / 2f - tw / 2, cy + D.dp(70f), w / 2f + tw / 2, cy + D.dp(100f), D.withAlpha(0xFFFFD166.toInt(), 90), 1.4f, D.dp(16f))
            Glyph.draw(c, "star", RectF(w / 2f - tw / 2 + D.dp(10f), cy + D.dp(78f), w / 2f - tw / 2 + D.dp(10f) + sw, cy + D.dp(78f) + sw), 0xFFFFD166.toInt())
            D.text(c, label, w / 2f + sw / 2f, cy + D.dp(90f), D.sp(13f), 0xFFFFD166.toInt())
        }
    }

    override fun onTouch(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN) return true
        settingsBtn?.let { if (it.contains(e.x, e.y)) { it.pressT = 1f; it.onTap(); Audio.play("click"); return true } }
        infoBtn?.let { if (it.contains(e.x, e.y)) { it.pressT = 1f; it.onTap(); Audio.play("click"); return true } }
        coinPill?.let { if (it.contains(e.x, e.y)) { onCoinsTap(); return true } }
        if (statsRect.contains(e.x, e.y)) { Audio.play("click"); Haptic.tick(); scene().push(StatsScene()); return true }
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

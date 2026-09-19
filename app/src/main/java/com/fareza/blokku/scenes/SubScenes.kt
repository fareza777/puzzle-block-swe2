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
import com.fareza.blokku.core.GameEngine
import com.fareza.blokku.core.Levels
import com.fareza.blokku.data.Achievements
import com.fareza.blokku.data.Missions
import com.fareza.blokku.data.Save
import com.fareza.blokku.data.Themes
import com.fareza.blokku.render.D
import com.fareza.blokku.ui.UiButton
import com.fareza.blokku.ui.UiToggle
import kotlin.math.min

// ============================ LEVEL SELECT ============================

class LevelSelectScene : BaseScene() {
    private var scrollY = 0f
    private var maxScroll = 0f
    private var dragStart = 0f
    private var scrollStart = 0f
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private val cellRects = ArrayList<Pair<RectF, Int>>()

    override fun onEnter() { makeBackButton() }

    override fun render(c: Canvas) {
        renderBackground(c)
        renderTopBar(c, s(R.string.menu_levels))

        val w = host.width.toFloat()
        val cols = 5
        val gap = D.dp(10f)
        val cellW = (w - D.dp(40f) - gap * (cols - 1)) / cols
        val top = host.safeTop + D.dp(64f)
        val unlocked = Save.maxUnlockedLevel() + 1 // first unplayed level also open

        cellRects.clear()
        c.save()
        val clipTop = top - D.dp(8f)
        val clipBot = host.height.toFloat() - D.dp(12f)
        c.clipRect(0f, clipTop, w, clipBot)
        c.translate(0f, scrollY)

        var maxY = top
        for (i in 0 until Levels.COUNT) {
            val row = i / cols
            val col = i % cols
            val l = D.dp(20f) + col * (cellW + gap)
            val t = top + row * (cellW + gap)
            maxY = t + cellW
            val stars = Save.stars(i)
            val open = i <= unlocked
            val r = RectF(l, t, l + cellW, t + cellW)
            cellRects.add(r to i)

            val isNext = i == unlocked && open
            when {
                stars > 0 -> {
                    D.rect(c, r.left, r.top + D.dp(3f), r.right, r.bottom + D.dp(3f), D.withAlpha(Color.BLACK, 70), D.dp(14f))
                    D.gradientRect(c, r.left, r.top, r.right, r.bottom, D.lighten(D.color(theme.accent), 0.14f), D.darken(D.color(theme.accent), 0.10f), D.dp(14f))
                }
                open -> {
                    D.card(c, r.left, r.top, r.right, r.bottom, D.color(theme.boardBg), D.dp(14f))
                    if (isNext) D.rectStroke(c, r.left + 0.8f, r.top + 0.8f, r.right - 0.8f, r.bottom - 0.8f, D.color(theme.accent), 1.8f, D.dp(14f))
                }
                else -> {
                    D.insetCell(c, r.left, r.top, r.right, r.bottom, D.withAlpha(D.color(theme.boardBg), 200), D.dp(14f))
                }
            }
            val fg = if (open) D.color(theme.textPrimary) else D.withAlpha(D.color(theme.textPrimary), 130)
            D.textIn(c, if (open) "${i + 1}" else "🔒", r, if (open) D.sp(17f) else D.sp(14f), fg)
            if (stars > 0) {
                var sx = r.centerX() - (stars - 1) * D.dp(7f)
                for (k in 0 until stars) {
                    D.text(c, "★", sx, r.bottom - D.dp(5f), D.sp(10f), Color.WHITE)
                    sx += D.dp(14f)
                }
            }
        }
        c.restore()
        maxScroll = min(0f, host.height.toFloat() - D.dp(12f) - maxY - D.dp(10f))
    }

    override fun onTouch(e: MotionEvent): Boolean {
        backButton?.let { if (e.actionMasked == MotionEvent.ACTION_DOWN && it.contains(e.x, e.y)) { it.pressT = 1f; it.onTap(); Audio.play("click"); return true } }
        coinPill?.let { if (e.actionMasked == MotionEvent.ACTION_DOWN && it.contains(e.x, e.y)) { onCoinsTap(); return true } }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { dragging = true; moved = false; dragStart = e.y; scrollStart = scrollY; downX = e.x; downY = e.y }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val dy = e.y - dragStart
                    if (Math.abs(dy) > host.touchSlopPx) moved = true
                    scrollY = (scrollStart + dy).coerceIn(maxScroll, 0f)
                    host.wake()
                }
            }
            MotionEvent.ACTION_UP -> {
                dragging = false
                if (!moved) {
                    for ((r, i) in cellRects) {
                        val rr = RectF(r.left, r.top + scrollY, r.right, r.bottom + scrollY)
                        if (rr.contains(e.x, e.y)) {
                            if (i <= Save.maxUnlockedLevel() + 1) {
                                Audio.play("click"); Haptic.tick()
                                scene().push(GameScene(GameEngine.level(Levels.get(i)), i))
                            } else Audio.play("invalid")
                            return true
                        }
                    }
                }
            }
        }
        return true
    }
}

// ============================ MISSIONS ============================

class MissionsScene : BaseScene() {
    private var tab = 0 // 0 = daily, 1 = achievements
    private val buttons = ArrayList<UiButton>()
    private var tabRects = arrayOf(RectF(), RectF())

    override fun onEnter() { makeBackButton() }

    override fun update(dt: Float) {
        super.update(dt)
        for (b in buttons) b.update(dt)
    }

    override fun render(c: Canvas) {
        renderBackground(c)
        renderTopBar(c, s(R.string.menu_missions))
        val w = host.width.toFloat()
        val top = host.safeTop + D.dp(64f)
        buttons.clear()

        // tabs — segmented control on a track
        val tw = (w - D.dp(48f)) / 2f
        tabRects[0].set(D.dp(24f), top, D.dp(24f) + tw, top + D.dp(42f))
        tabRects[1].set(D.dp(24f) + tw, top, w - D.dp(24f), top + D.dp(42f))
        D.rect(c, D.dp(24f), top, w - D.dp(24f), top + D.dp(42f), D.withAlpha(Color.BLACK, 80), D.dp(14f))
        val sr = tabRects[tab]
        D.gradientRect(c, sr.left + 3, top + 3, sr.right - 3, top + D.dp(39f), D.lighten(D.color(theme.accent), 0.12f), D.darken(D.color(theme.accent), 0.08f), D.dp(11f))
        for (i in 0..1) {
            val r = tabRects[i]
            val sel = tab == i
            D.textIn(c, if (i == 0) s(R.string.missions_daily) else s(R.string.missions_achievements), r, D.sp(13f), if (sel) Color.WHITE else D.withAlpha(D.color(theme.textPrimary), 170))
        }

        var y = top + D.dp(56f)
        val cardH = D.dp(64f)
        val cw = w - D.dp(48f)

        if (tab == 0) {
            val missions = Missions.today()
            for (m in missions) {
                val prog = Missions.progress(m)
                val claimable = Missions.claimable(m)
                val done = Missions.done(m)
                drawCard(c, D.dp(24f), y, cw, cardH, m.label, "$prog/${m.target}", "+${m.reward}")
                if (claimable) {
                    val br = RectF(D.dp(24f) + cw - D.dp(80f), y + cardH / 2 - D.dp(17f), D.dp(24f) + cw - D.dp(10f), y + cardH / 2 + D.dp(17f))
                    buttons.add(UiButton(br, s(R.string.claim), bg = 0xFF62D97B.toInt(), fg = Color.WHITE, onTap = {
                        val got = Missions.claim(m)
                        Audio.play("coin")
                        coinPill?.bump()
                        addFloat(w / 2f, y, "+$got ${s(R.string.coins)}", 0xFFFFD166.toInt(), D.sp(18f))
                    }, textScale = 0.75f))
                } else if (done) {
                    D.text(c, s(R.string.claimed), D.dp(24f) + cw - D.dp(14f), y + cardH / 2 + D.sp(11f) * 0.35f, D.sp(11f), 0xFF62D97B.toInt(), Paint.Align.RIGHT)
                } else {
                    // progress bar
                    val pr = RectF(D.dp(38f), y + cardH - D.dp(17f), D.dp(24f) + cw - D.dp(24f), y + cardH - D.dp(9f))
                    D.rect(c, pr.left, pr.top, pr.right, pr.bottom, D.withAlpha(Color.BLACK, 90), pr.height() / 2)
                    if (prog > 0) {
                        val fr = min(pr.right, pr.left + pr.width() * prog / m.target)
                        D.gradientRect(c, pr.left, pr.top, fr, pr.bottom, D.lighten(D.color(theme.accent), 0.15f), D.darken(D.color(theme.accent), 0.12f), pr.height() / 2)
                    }
                }
                y += cardH + D.dp(12f)
            }
        } else {
            for (a in Achievements.ALL) {
                val done = Save.achievementDone(a.id)
                drawCard(c, D.dp(24f), y, cw, cardH, a.label, if (done) "✓" else "", "+${a.reward}")
                if (done) {
                    D.text(c, "✓", D.dp(24f) + cw - D.dp(18f), y + cardH / 2 + D.sp(16f) * 0.36f, D.sp(16f), 0xFF62D97B.toInt(), Paint.Align.RIGHT)
                }
                y += cardH + D.dp(10f)
            }
        }

        for (b in buttons) { b.appear.t = b.appear.duration; b.render(c) }
    }

    private fun drawCard(c: Canvas, l: Float, t: Float, w: Float, h: Float, title: String, prog: String, reward: String) {
        D.card(c, l, t, l + w, t + h, D.color(theme.boardBg), D.dp(16f))
        // coin chip
        val rw = D.textWidth(reward, D.sp(11f)) + D.dp(20f)
        D.rect(c, l + w - D.dp(10f) - rw, t + D.dp(10f), l + w - D.dp(10f), t + D.dp(30f), D.withAlpha(0xFFFFD166.toInt(), 40), D.dp(10f))
        D.text(c, reward, l + w - D.dp(10f) - rw / 2f, t + D.dp(24f), D.sp(11f), 0xFFFFD166.toInt())
        D.text(c, title, l + D.dp(16f), t + D.dp(26f), D.sp(13.5f), D.color(theme.textPrimary), Paint.Align.LEFT, bold = false)
        D.text(c, prog, l + D.dp(16f), t + D.dp(46f), D.sp(12f), D.withAlpha(D.color(theme.textPrimary), 170), Paint.Align.LEFT)
    }

    override fun onTouch(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN) return true
        backButton?.let { if (it.contains(e.x, e.y)) { it.pressT = 1f; it.onTap(); Audio.play("click"); return true } }
        coinPill?.let { if (it.contains(e.x, e.y)) { onCoinsTap(); return true } }
        for (i in 0..1) if (tabRects[i].contains(e.x, e.y)) { tab = i; Audio.play("click"); return true }
        for (b in buttons) if (b.contains(e.x, e.y)) { b.pressT = 1f; b.tap(); return true }
        return true
    }
}

// ============================ STATS ============================

class StatsScene : BaseScene() {
    override fun onEnter() { makeBackButton() }

    override fun render(c: Canvas) {
        renderBackground(c)
        renderTopBar(c, s(R.string.menu_stats))
        val w = host.width.toFloat()
        var y = host.safeTop + D.dp(84f)
        val rows = listOf(
            s(R.string.stats_games) to "${Save.gamesPlayed}",
            s(R.string.stats_best_classic) to "${Save.bestClassic}",
            s(R.string.stats_lines) to "${Save.totalLines}",
            s(R.string.stats_cells) to "${Save.totalCells}",
            s(R.string.stats_max_combo) to "×${Save.lifetimeBestCombo}",
            s(R.string.stats_dailies) to "${Save.dailiesDone}",
            s(R.string.stats_play_time) to s(R.string.stats_minutes, Save.playSeconds / 60),
        )
        val cw = w - D.dp(48f)
        for ((label, value) in rows) {
            D.card(c, D.dp(24f), y, D.dp(24f) + cw, y + D.dp(54f), D.color(theme.boardBg), D.dp(15f))
            D.text(c, label, D.dp(42f), y + D.dp(34f), D.sp(14f), D.withAlpha(D.color(theme.textPrimary), 200), Paint.Align.LEFT, bold = false)
            D.text(c, value, D.dp(24f) + cw - D.dp(18f), y + D.dp(35f), D.sp(17f), D.color(theme.accent), Paint.Align.RIGHT)
            y += D.dp(64f)
        }
    }

    override fun onTouch(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN) return true
        backButton?.let { if (it.contains(e.x, e.y)) { it.pressT = 1f; it.onTap(); Audio.play("click"); return true } }
        coinPill?.let { if (it.contains(e.x, e.y)) { onCoinsTap(); return true } }
        return true
    }
}

// ============================ THEMES / SHOP ============================

class ThemesScene : BaseScene() {
    private val buttons = ArrayList<UiButton>()
    private var scrollY = 0f
    private var dragging = false
    private var moved = false
    private var dragStart = 0f
    private var scrollStart = 0f
    private var maxScroll = 0f
    private val cardRects = ArrayList<Pair<RectF, String>>()

    override fun onEnter() { makeBackButton() }

    override fun update(dt: Float) {
        super.update(dt)
        for (b in buttons) b.update(dt)
    }

    override fun render(c: Canvas) {
        renderBackground(c)
        renderTopBar(c, s(R.string.themes_title))
        val w = host.width.toFloat()
        var y = host.safeTop + D.dp(70f)
        val cw = w - D.dp(48f)
        val cardH = D.dp(88f)
        buttons.clear()
        cardRects.clear()

        c.save()
        c.clipRect(0f, y - D.dp(8f), w, host.height.toFloat() - D.dp(76f))
        c.translate(0f, scrollY)

        for (t in Themes.ALL) {
            val unlocked = Save.themeUnlocked(t.id)
            val inUse = Save.selectedTheme == t.id
            D.rect(c, D.dp(24f), y + D.dp(5f), D.dp(24f) + cw, y + cardH + D.dp(5f), D.withAlpha(Color.BLACK, 70), D.dp(18f))
            D.gradientRect(c, D.dp(24f), y, D.dp(24f) + cw, y + cardH, D.color(t.bgTop), D.color(t.bgBottom), D.dp(18f))
            D.rectStroke(c, D.dp(24f) + 0.8f, y + 0.8f, D.dp(24f) + cw - 0.8f, y + cardH - 0.8f, D.withAlpha(Color.WHITE, 26), 1.3f, D.dp(18f))
            if (inUse) D.rectStroke(c, D.dp(24f), y, D.dp(24f) + cw, y + cardH, D.color(theme.accent), D.dp(2.5f), D.dp(18f))

            // preview: mini board + block samples
            val pv = RectF(D.dp(36f), y + D.dp(14f), D.dp(36f) + D.dp(60f), y + D.dp(74f))
            D.rect(c, pv.left, pv.top, pv.right, pv.bottom, D.color(t.boardBg), D.dp(8f))
            var bx = pv.left + D.dp(8f)
            for (i in 0..2) {
                val col = D.color(t.blockColors[i])
                D.blockCell(c, bx, pv.top + D.dp(12f), bx + D.dp(14f), pv.top + D.dp(26f), col, D.dp(4f))
                bx += D.dp(17f)
            }
            bx = pv.left + D.dp(8f)
            for (i in 3..5) {
                val col = D.color(t.blockColors[i])
                D.blockCell(c, bx, pv.top + D.dp(30f), bx + D.dp(14f), pv.top + D.dp(42f), col, D.dp(4f))
                bx += D.dp(17f)
            }

            D.text(c, t.displayName, pv.right + D.dp(14f), y + D.dp(30f), D.sp(16f), D.color(theme.textPrimary), Paint.Align.LEFT)
            val status = when {
                inUse -> s(R.string.theme_using)
                unlocked -> s(R.string.theme_unlocked)
                else -> s(R.string.theme_locked, t.price)
            }
            D.text(c, status, pv.right + D.dp(14f), y + D.dp(54f), D.sp(11f), D.withAlpha(D.color(theme.textPrimary), 180), Paint.Align.LEFT, bold = false)

            val btnR = RectF(D.dp(24f) + cw - D.dp(88f), y + cardH / 2 - D.dp(19f), D.dp(24f) + cw - D.dp(14f), y + cardH / 2 + D.dp(19f))
            if (!inUse) {
                val label = if (unlocked) s(R.string.theme_use) else "🪙 ${t.price}"
                val b = UiButton(btnR, label, bg = if (unlocked) D.color(theme.accent) else 0xFFFFB84D.toInt(), fg = if (unlocked) Color.WHITE else 0xFF40260A.toInt(), onTap = {
                    onThemeButton(t.id)
                }, textScale = 0.8f)
                b.inScroll = true
                buttons.add(b)
            }
            cardRects.add(RectF(D.dp(24f), y, D.dp(24f) + cw, y + cardH) to t.id)
            y += cardH + D.dp(14f)
        }

        // power-ups strip
        y += D.dp(10f)
        D.text(c, "Power-ups", D.dp(24f), y, D.sp(15f), D.color(theme.textPrimary), Paint.Align.LEFT)
        y += D.dp(10f)
        val puW = (cw - D.dp(24f)) / 4f
        val puKinds = com.fareza.blokku.data.PowerKind.entries
        val puGlyphs = arrayOf("undo", "rotate", "bomb", "shuffle")
        val puPrices = intArrayOf(30, 40, 60, 40)
        for (i in 0..3) {
            val l = D.dp(24f) + i * (puW + D.dp(8f))
            val r = RectF(l, y, l + puW, y + D.dp(78f))
            D.card(c, r.left, r.top, r.right, r.bottom, D.color(theme.boardBg), D.dp(15f))
            val pTint = intArrayOf(0xFF64B5F6.toInt(), 0xFF7BE495.toInt(), 0xFFFF8A65.toInt(), 0xFFBA8DF5.toInt())[i]
            com.fareza.blokku.ui.Glyph.draw(c, puGlyphs[i], RectF(r.left + D.dp(10f), r.top + D.dp(8f), r.right - D.dp(10f), r.top + D.dp(38f)), pTint)
            val price = puPrices[i]
            val have = Save.powerUps(puKinds[i])
            val buyBtn = UiButton(RectF(r.left + D.dp(8f), r.bottom - D.dp(30f), r.right - D.dp(8f), r.bottom - D.dp(6f)), "+1 • $price", bg = 0xFFFFB84D.toInt(), fg = 0xFF40260A.toInt(), textScale = 0.65f, onTap = {
                if (Save.coins >= price) {
                    Save.coins -= price
                    Save.addPowerUp(puKinds[i], 1)
                    Audio.play("coin")
                    Haptic.tick()
                    coinPill?.bump()
                } else {
                    Audio.play("invalid")
                    addFloat(host.width / 2f, host.height / 2f, s(R.string.not_enough_coins), 0xFFFF5D73.toInt(), D.sp(15f))
                }
            })
            buyBtn.inScroll = true
            buttons.add(buyBtn)
            D.text(c, "×$have", r.centerX(), r.top + D.dp(52f), D.sp(10f), D.withAlpha(D.color(theme.textPrimary), 180))
        }
        y += D.dp(92f)

        // scroll-area buttons render inside the translated clip
        for (b in buttons) if (b.inScroll) { b.appear.t = b.appear.duration; b.render(c) }
        maxScroll = min(0f, host.height.toFloat() - D.dp(92f) - y)
        c.restore()

        // free coins button
        val fcw = w - D.dp(48f)
        val fy = host.height.toFloat() - D.dp(64f)
        val fb = UiButton(RectF(D.dp(24f), fy, D.dp(24f) + fcw, fy + D.dp(48f)), s(R.string.watch_ad_coins, 40), icon = "🪙", bg = 0xFF62D97B.toInt(), fg = Color.WHITE, onTap = {
            Ads.showRewarded(host.context) { ok ->
                if (ok) {
                    Save.coins += 40
                    Audio.play("coin")
                    coinPill?.bump()
                }
            }
        }, textScale = 0.85f)
        buttons.add(fb)
        fb.appear.t = fb.appear.duration
        fb.render(c)
    }

    private fun onThemeButton(id: String) {
        val t = Themes.byId(id)
        if (Save.themeUnlocked(id)) {
            Save.selectedTheme = id
            Audio.play("reward")
            Haptic.success()
        } else if (Save.coins >= t.price) {
            Save.coins -= t.price
            Save.unlockTheme(id)
            Save.selectedTheme = id
            Audio.play("reward")
            Haptic.success()
            coinPill?.bump()
            Achievements.checkAll()
        } else {
            Audio.play("invalid")
            addFloat(host.width / 2f, host.height / 2f, s(R.string.not_enough_coins), 0xFFFF5D73.toInt(), D.sp(15f))
        }
    }

    override fun onTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                backButton?.let { if (it.contains(e.x, e.y)) { it.pressT = 1f; it.onTap(); Audio.play("click"); return true } }
                coinPill?.let { if (it.contains(e.x, e.y)) { onCoinsTap(); return true } }
                for (b in buttons) {
                    val off = if (b.inScroll) scrollY else 0f
                    val rr = RectF(b.rect.left, b.rect.top + off, b.rect.right, b.rect.bottom + off)
                    if (rr.contains(e.x, e.y)) { b.pressT = 1f; b.tap(); return true }
                }
                dragging = true; moved = false; dragStart = e.y; scrollStart = scrollY
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val dy = e.y - dragStart
                    if (Math.abs(dy) > host.touchSlopPx) moved = true
                    scrollY = (scrollStart + dy).coerceIn(maxScroll - D.dp(80f), 0f)
                    host.wake()
                }
            }
            MotionEvent.ACTION_UP -> dragging = false
        }
        return true
    }

    override fun onCoinsTap() {
        // already in shop — do nothing meaningful
    }
}

// ============================ SETTINGS ============================

class SettingsScene : BaseScene() {
    private val toggles = ArrayList<UiToggle>()
    private var langBtn: UiButton? = null
    private var removeAdsBtn: UiButton? = null
    private var restoreBtn: UiButton? = null

    override fun onEnter() {
        makeBackButton()
        val w = host.width.toFloat()
        var y = host.safeTop + D.dp(84f)
        val l = D.dp(32f); val r = w - D.dp(32f)
        toggles.clear()
        toggles.add(UiToggle(RectF(l, y, r, y + D.dp(44f)), s(R.string.settings_sound), { Save.soundOn }, { Save.soundOn = it; Audio.sync() }, accent = D.color(theme.accent)))
        y += D.dp(54f)
        toggles.add(UiToggle(RectF(l, y, r, y + D.dp(44f)), s(R.string.settings_music), { Save.musicOn }, { Save.musicOn = it; Audio.sync() }, accent = D.color(theme.accent)))
        y += D.dp(54f)
        toggles.add(UiToggle(RectF(l, y, r, y + D.dp(44f)), s(R.string.settings_vibration), { Save.vibrationOn }, { Save.vibrationOn = it }, accent = D.color(theme.accent)))
        y += D.dp(54f)
        toggles.add(UiToggle(RectF(l, y, r, y + D.dp(44f)), s(R.string.settings_colorblind), { Save.colorblind }, { Save.colorblind = it }, accent = D.color(theme.accent)))
        y += D.dp(64f)
        langBtn = UiButton(RectF(l, y, r, y + D.dp(48f)), "${s(R.string.settings_language)}: ${langLabel()}", bg = D.color(theme.boardBg), fg = D.color(theme.textPrimary), onTap = { cycleLang() })
        y += D.dp(60f)
        if (!Save.adsRemoved) {
            removeAdsBtn = UiButton(RectF(l, y, r, y + D.dp(48f)), s(R.string.settings_remove_ads), icon = "★", bg = 0xFFFFB84D.toInt(), fg = 0xFF40260A.toInt(), onTap = { Ads.buyRemoveAds(host) })
        }
    }

    private fun langLabel(): String = when (Save.language) {
        "in" -> "Indonesia"
        "en" -> "English"
        else -> s(R.string.lang_system)
    }

    private fun cycleLang() {
        val next = when (Save.language) {
            "" -> "en"
            "en" -> "in"
            else -> ""
        }
        Save.language = next
        Audio.play("click")
        // recreate activity to apply locale
        (host.context as? android.app.Activity)?.recreate()
    }

    override fun update(dt: Float) { super.update(dt) }

    override fun wantsFrame() = super.wantsFrame() || toggles.any { it.animating() }

    override fun render(c: Canvas) {
        renderBackground(c)
        renderTopBar(c, s(R.string.menu_settings))
        // grouped card behind the four toggles
        if (toggles.isNotEmpty()) {
            val gl = D.dp(24f); val gr = host.width.toFloat() - D.dp(24f)
            D.card(c, gl, toggles[0].rect.top - D.dp(10f), gr, toggles.last().rect.bottom + D.dp(8f), D.color(theme.boardBg), D.dp(18f))
        }
        for (t in toggles) t.render(c)
        langBtn?.let { it.appear.t = it.appear.duration; it.render(c) }
        if (!Save.adsRemoved) {
            removeAdsBtn?.let { it.appear.t = it.appear.duration; it.render(c) }
        } else {
            val w = host.width.toFloat()
            D.text(c, s(R.string.settings_remove_ads_done), w / 2f, host.safeTop + D.dp(84f) + D.dp(216f + 60f + 24f), D.sp(13f), 0xFF62D97B.toInt())
        }
    }

    override fun onTouch(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN) return true
        backButton?.let { if (it.contains(e.x, e.y)) { it.pressT = 1f; it.onTap(); Audio.play("click"); return true } }
        coinPill?.let { if (it.contains(e.x, e.y)) { onCoinsTap(); return true } }
        for (t in toggles) {
            if (t.contains(e.x, e.y)) { t.tap(); Audio.play("click"); Haptic.tick(); return true }
        }
        langBtn?.let { if (it.contains(e.x, e.y)) { it.tap(); return true } }
        removeAdsBtn?.let { if (it.contains(e.x, e.y)) { it.tap(); return true } }
        return true
    }
}

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
import com.fareza.blokku.core.Mode
import com.fareza.blokku.data.Achievements
import com.fareza.blokku.data.Missions
import com.fareza.blokku.data.Save
import com.fareza.blokku.data.Themes
import com.fareza.blokku.render.D
import com.fareza.blokku.ui.Glyph
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
            if (open) D.textIn(c, "${i + 1}", r, D.sp(17f), fg)
            else Glyph.draw(c, "lock", RectF(r.centerX() - D.dp(8f), r.centerY() - D.dp(8f), r.centerX() + D.dp(8f), r.centerY() + D.dp(8f)), fg)
            // level badges: timed = clock dot, preset = dot
            if (open && stars == 0) {
                val def = Levels.get(i)
                val badge = if (def.timedSec > 0) "clock" else if (def.presetFill > 0f) "themes" else ""
                if (badge.isNotEmpty()) Glyph.draw(c, badge, RectF(r.right - D.dp(16f), r.top + D.dp(4f), r.right - D.dp(4f), r.top + D.dp(16f)), D.withAlpha(Color.WHITE, 150))
            }
            if (stars > 0) {
                var sx = r.centerX() - (stars - 1) * D.dp(7f)
                for (k in 0 until stars) {
                    Glyph.draw(c, "star", RectF(sx - D.dp(5f), r.bottom - D.dp(13f), sx + D.dp(5f), r.bottom - D.dp(3f)), Color.WHITE)
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

// ============================ PUZZLE SELECT ============================

class PuzzleSelectScene : BaseScene() {
    private var scrollY = 0f
    private var maxScroll = 0f
    private var dragStart = 0f
    private var scrollStart = 0f
    private var dragging = false
    private var moved = false
    private val cellRects = ArrayList<Pair<RectF, Int>>()

    override fun onEnter() { makeBackButton() }

    override fun render(c: Canvas) {
        renderBackground(c)
        renderTopBar(c, s(R.string.puzzle_select))
        val w = host.width.toFloat()
        val cols = 5
        val gap = D.dp(10f)
        val cellW = (w - D.dp(40f) - gap * (cols - 1)) / cols
        val top = host.safeTop + D.dp(64f)
        val unlocked = Save.maxUnlockedPuzzle() + 1

        cellRects.clear()
        c.save()
        c.clipRect(0f, top - D.dp(8f), w, host.height.toFloat() - D.dp(12f))
        c.translate(0f, scrollY)

        // weekly puzzle card on top of the pack grid
        val weeklyDone = Save.weeklyPuzzleDone()
        val wph = D.dp(56f)
        val wpr = RectF(D.dp(20f), top, w - D.dp(20f), top + wph)
        cellRects.add(wpr to com.fareza.blokku.core.Puzzles.COUNT) // sentinel index = weekly
        D.rect(c, wpr.left, wpr.top + D.dp(4f), wpr.right, wpr.bottom + D.dp(4f), D.withAlpha(Color.BLACK, 80), D.dp(16f))
        D.gradientRect(c, wpr.left, wpr.top, wpr.right, wpr.bottom, 0xFF5B3FA8.toInt(), 0xFF3F2A7A.toInt(), D.dp(16f))
        D.rectStroke(c, wpr.left + 0.8f, wpr.top + 0.8f, wpr.right - 0.8f, wpr.bottom - 0.8f, D.withAlpha(0xFFBA8DF5.toInt(), 140), 1.3f, D.dp(16f))
        val wtagW = D.textWidth(s(R.string.weekly_tag), D.sp(9f)) + D.dp(14f)
        D.gradientRect(c, wpr.left + D.dp(14f), wpr.top + D.dp(10f), wpr.left + D.dp(14f) + wtagW, wpr.top + D.dp(26f), 0xFFBA8DF5.toInt(), 0xFF8A5CF6.toInt(), D.dp(8f))
        D.text(c, s(R.string.weekly_tag), wpr.left + D.dp(14f) + wtagW / 2f, wpr.top + D.dp(21f), D.sp(9f), Color.WHITE)
        D.text(c, s(R.string.weekly_puzzle), wpr.left + D.dp(14f), wpr.top + D.dp(45f), D.sp(14f), Color.WHITE, Paint.Align.LEFT)
        if (weeklyDone) {
            D.text(c, s(R.string.weekly_done), wpr.right - D.dp(16f), wpr.centerY() + D.sp(5f), D.sp(11f), 0xFF62D97B.toInt(), Paint.Align.RIGHT)
        } else {
            Glyph.draw(c, "star", RectF(wpr.right - D.dp(66f), wpr.centerY() - D.dp(8f), wpr.right - D.dp(50f), wpr.centerY() + D.dp(8f)), 0xFFFFD166.toInt())
            D.text(c, "+2", wpr.right - D.dp(46f), wpr.centerY() + D.sp(5f), D.sp(12f), 0xFFFFD166.toInt(), Paint.Align.LEFT)
        }

        var maxY = wpr.bottom + D.dp(14f)
        val gridTop = maxY
        for (i in 0 until com.fareza.blokku.core.Puzzles.COUNT) {
            val row = i / cols; val col = i % cols
            val l = D.dp(20f) + col * (cellW + gap)
            val t = gridTop + row * (cellW + gap)
            maxY = t + cellW
            val stars = Save.puzzleStars(i)
            val open = i <= unlocked
            val r = RectF(l, t, l + cellW, t + cellW)
            cellRects.add(r to i)
            val isNext = i == unlocked && open
            when {
                stars > 0 -> {
                    D.rect(c, r.left, r.top + D.dp(3f), r.right, r.bottom + D.dp(3f), D.withAlpha(Color.BLACK, 70), D.dp(14f))
                    D.gradientRect(c, r.left, r.top, r.right, r.bottom, D.lighten(0xFFBA8DF5.toInt(), 0.14f), D.darken(0xFFBA8DF5.toInt(), 0.12f), D.dp(14f))
                }
                open -> {
                    D.card(c, r.left, r.top, r.right, r.bottom, D.color(theme.boardBg), D.dp(14f))
                    if (isNext) D.rectStroke(c, r.left + 0.8f, r.top + 0.8f, r.right - 0.8f, r.bottom - 0.8f, 0xFFBA8DF5.toInt(), 1.8f, D.dp(14f))
                }
                else -> D.insetCell(c, r.left, r.top, r.right, r.bottom, D.withAlpha(D.color(theme.boardBg), 200), D.dp(14f))
            }
            val fg = if (open) D.color(theme.textPrimary) else D.withAlpha(D.color(theme.textPrimary), 130)
            if (open) D.textIn(c, "${i + 1}", r, D.sp(17f), fg)
            else Glyph.draw(c, "lock", RectF(r.centerX() - D.dp(8f), r.centerY() - D.dp(8f), r.centerX() + D.dp(8f), r.centerY() + D.dp(8f)), fg)
            if (stars > 0) {
                var sx = r.centerX() - (stars - 1) * D.dp(7f)
                for (k in 0 until stars) {
                    Glyph.draw(c, "star", RectF(sx - D.dp(5f), r.bottom - D.dp(13f), sx + D.dp(5f), r.bottom - D.dp(3f)), Color.WHITE)
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
            MotionEvent.ACTION_DOWN -> { dragging = true; moved = false; dragStart = e.y; scrollStart = scrollY }
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
                            if (i >= com.fareza.blokku.core.Puzzles.COUNT) {
                                Audio.play("click"); Haptic.tick()
                                scene().push(GameScene(GameEngine.puzzle(
                                    com.fareza.blokku.core.Puzzles.weeklyDef(Save.weekSeed())), i))
                            } else if (i <= Save.maxUnlockedPuzzle() + 1) {
                                Audio.play("click"); Haptic.tick()
                                scene().push(GameScene(GameEngine.puzzle(com.fareza.blokku.core.Puzzles.get(i)), i))
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
            // weekly mission — a gold banner card above the daily set
            val wm = Missions.thisWeek()
            val wprog = Missions.progress(wm)
            val wclaim = Missions.claimable(wm)
            val wdone = Missions.done(wm)
            val wh = cardH + D.dp(24f)
            D.rect(c, D.dp(24f), y + D.dp(5f), D.dp(24f) + cw, y + wh + D.dp(5f), D.withAlpha(Color.BLACK, 80), D.dp(16f))
            D.gradientRect(c, D.dp(24f), y, D.dp(24f) + cw, y + wh, 0xFF8A5A13.toInt(), 0xFF5E3C0C.toInt(), D.dp(16f))
            D.rectStroke(c, D.dp(24f) + 0.8f, y + 0.8f, D.dp(24f) + cw - 0.8f, y + wh - 0.8f, D.withAlpha(0xFFFFD166.toInt(), 120), 1.3f, D.dp(16f))
            val tagW = D.textWidth(s(R.string.weekly_tag), D.sp(9f)) + D.dp(14f)
            D.gradientRect(c, D.dp(40f), y + D.dp(10f), D.dp(40f) + tagW, y + D.dp(26f), 0xFFFFD166.toInt(), 0xFFE8A93C.toInt(), D.dp(8f))
            D.text(c, s(R.string.weekly_tag), D.dp(40f) + tagW / 2f, y + D.dp(21f), D.sp(9f), 0xFF3A2404.toInt())
            val wrw = D.textWidth("+${wm.reward}", D.sp(11f)) + D.dp(20f)
            D.rect(c, D.dp(24f) + cw - D.dp(10f) - wrw, y + D.dp(10f), D.dp(24f) + cw - D.dp(10f), y + D.dp(30f), D.withAlpha(0xFFFFD166.toInt(), 40), D.dp(10f))
            D.text(c, "+${wm.reward}", D.dp(24f) + cw - D.dp(10f) - wrw / 2f, y + D.dp(24f), D.sp(11f), 0xFFFFD166.toInt())
            D.textFit(c, s(wm.labelRes, wm.target), D.dp(40f), y + D.dp(46f), D.sp(13.5f), cw - D.dp(32f) - wrw, Color.WHITE, Paint.Align.LEFT, bold = false)
            if (wclaim) {
                val br = RectF(D.dp(24f) + cw - D.dp(80f), y + wh - D.dp(30f), D.dp(24f) + cw - D.dp(10f), y + wh - D.dp(2f))
                buttons.add(UiButton(br, s(R.string.claim), bg = 0xFFFFD166.toInt(), fg = 0xFF3A2404.toInt(), onTap = {
                    val got = Missions.claim(wm)
                    Audio.play("coin")
                    coinPill?.bump()
                    addFloat(w / 2f, y, "+$got ${s(R.string.coins)}", 0xFFFFD166.toInt(), D.sp(18f))
                }, textScale = 0.75f))
                D.text(c, "$wprog/${wm.target}", D.dp(40f), y + wh - D.dp(10f), D.sp(12f), 0xFFFFD166.toInt(), Paint.Align.LEFT)
            } else if (wdone) {
                D.text(c, s(R.string.claimed), D.dp(40f), y + wh - D.dp(12f), D.sp(12f), 0xFFFFD166.toInt(), Paint.Align.LEFT)
            } else {
                val pr = RectF(D.dp(40f), y + wh - D.dp(18f), D.dp(24f) + cw - D.dp(24f), y + wh - D.dp(10f))
                D.rect(c, pr.left, pr.top, pr.right, pr.bottom, D.withAlpha(Color.BLACK, 90), pr.height() / 2)
                if (wprog > 0) {
                    val fr = min(pr.right, pr.left + pr.width() * wprog / wm.target)
                    D.gradientRect(c, pr.left, pr.top, fr, pr.bottom, 0xFFFFD166.toInt(), 0xFFE8A93C.toInt(), pr.height() / 2)
                }
                D.text(c, "$wprog/${wm.target}", D.dp(40f), y + wh - D.dp(26f), D.sp(12f), D.withAlpha(Color.WHITE, 200), Paint.Align.LEFT)
            }
            y += wh + D.dp(14f)

            val missions = Missions.today()
            for (m in missions) {
                val prog = Missions.progress(m)
                val claimable = Missions.claimable(m)
                val done = Missions.done(m)
                drawCard(c, D.dp(24f), y, cw, cardH, s(m.labelRes, m.target), "$prog/${m.target}", "+${m.reward}")
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
                drawCard(c, D.dp(24f), y, cw, cardH, s(a.labelRes), "", "+${a.reward}")
                if (done) {
                    Glyph.draw(c, "check", RectF(D.dp(24f) + cw - D.dp(30f), y + cardH / 2 - D.dp(8f), D.dp(24f) + cw - D.dp(14f), y + cardH / 2 + D.dp(8f)), 0xFF62D97B.toInt())
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
        D.textFit(c, title, l + D.dp(16f), t + D.dp(26f), D.sp(13.5f), w - D.dp(16f) - rw - D.dp(20f), D.color(theme.textPrimary), Paint.Align.LEFT, bold = false)
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
    private var scrollY = 0f
    private var maxScroll = 0f
    private var dragStart = 0f
    private var scrollStart = 0f
    private var dragging = false

    override fun onEnter() { makeBackButton() }

    override fun render(c: Canvas) {
        renderBackground(c)
        renderTopBar(c, s(R.string.menu_stats))
        val w = host.width.toFloat()
        val top = host.safeTop + D.dp(70f)
        val cw = w - D.dp(48f)

        c.save()
        c.clipRect(0f, top - D.dp(8f), w, host.height.toFloat() - D.dp(12f))
        c.translate(0f, scrollY)
        var y = top

        // section: top runs — best five classic scores
        sectionHeader(c, s(R.string.stats_top_runs), y); y += D.dp(26f)
        val runs = Save.topRuns("CLASSIC")
        if (runs.isEmpty()) {
            D.card(c, D.dp(24f), y, D.dp(24f) + cw, y + D.dp(44f), D.color(theme.boardBg), D.dp(15f))
            D.text(c, s(R.string.stats_no_runs), D.dp(24f) + cw / 2f, y + D.dp(29f), D.sp(12f), D.withAlpha(D.color(theme.textPrimary), 160))
            y += D.dp(54f)
        } else {
            val rankCol = intArrayOf(0xFFFFD166.toInt(), 0xFFB7BCC9.toInt(), 0xFFCD8E4F.toInt(), 0xFF5AC8FA.toInt(), 0xFF5AC8FA.toInt())
            for (i in runs.indices) {
                D.card(c, D.dp(24f), y, D.dp(24f) + cw, y + D.dp(40f), D.color(theme.boardBg), D.dp(13f))
                D.circle(c, D.dp(24f) + D.dp(24f), y + D.dp(20f), D.dp(11f), D.withAlpha(rankCol[i], if (i == 0) 235 else 90))
                D.text(c, "${i + 1}", D.dp(24f) + D.dp(24f), y + D.dp(24.5f), D.sp(11f), if (i == 0) 0xFF3A2404.toInt() else D.color(theme.textPrimary))
                D.text(c, "${runs[i]}", D.dp(24f) + D.dp(46f), y + D.dp(26f), D.sp(15f), D.color(theme.textPrimary), Paint.Align.LEFT)
                val gi = GameEngine.grade(Mode.CLASSIC, runs[i])
                D.text(c, "${"SABC"[3 - gi]}", D.dp(24f) + cw - D.dp(20f), y + D.dp(26f), D.sp(14f), if (gi == 3) 0xFFFFD166.toInt() else D.withAlpha(D.color(theme.textPrimary), 170), Paint.Align.RIGHT)
                y += D.dp(46f)
            }
        }

        // section: 7-day score chart
        y += D.dp(8f)
        sectionHeader(c, s(R.string.stats_week_chart), y); y += D.dp(26f)
        val days = Save.last7DayScores()
        val chartH = D.dp(110f)
        D.card(c, D.dp(24f), y, D.dp(24f) + cw, y + chartH, D.color(theme.boardBg), D.dp(15f))
        val peak = days.max().coerceAtLeast(1)
        val bw = (cw - D.dp(32f)) / 7f
        for (i in 0..6) {
            val v = days[i]
            val bh = if (v > 0) (chartH - D.dp(38f)) * v / peak else D.dp(4f)
            val bx = D.dp(24f) + D.dp(16f) + i * bw + bw * 0.18f
            val bt = y + chartH - D.dp(22f) - bh
            val hot = i == 6
            D.gradientRect(c, bx, bt, bx + bw * 0.64f, y + chartH - D.dp(22f),
                if (hot) D.lighten(D.color(theme.accent), 0.15f) else D.withAlpha(D.color(theme.accent), 200),
                if (hot) D.darken(D.color(theme.accent), 0.12f) else D.withAlpha(D.darken(D.color(theme.accent), 0.2f), 200), D.dp(4f))
            D.circle(c, bx + bw * 0.32f, y + chartH - D.dp(10f), D.dp(1.6f), D.withAlpha(D.color(theme.textPrimary), 110))
            if (v > 0) D.text(c, "$v", bx + bw * 0.32f, bt - D.dp(4f), D.sp(8.5f), D.withAlpha(D.color(theme.textPrimary), 160))
        }
        y += chartH + D.dp(22f)

        // section: lifetime numbers
        sectionHeader(c, s(R.string.menu_stats), y); y += D.dp(26f)
        val rows = listOf(
            s(R.string.stats_games) to "${Save.gamesPlayed}",
            s(R.string.stats_best_classic) to "${Save.bestClassic}",
            s(R.string.stats_lines) to "${Save.totalLines}",
            s(R.string.stats_cells) to "${Save.totalCells}",
            s(R.string.stats_max_combo) to "×${Save.lifetimeBestCombo}",
            s(R.string.stats_dailies) to "${Save.dailiesDone}",
            s(R.string.stats_best_rush) to "${Save.bestRush}",
            s(R.string.stats_best_zen) to "${Save.bestZen}",
            s(R.string.stats_puzzles) to "${Save.puzzleSolved} · ${Save.totalPuzzleStars}★",
            s(R.string.stats_contracts) to "${Save.contractsDone}",
            s(R.string.stats_snug) to "${Save.snugFits}",
            s(R.string.stats_mono) to "${Save.monoLines}",
            s(R.string.stats_shards) to "${Save.shards}/5",
            s(R.string.stats_play_time) to s(R.string.stats_minutes, Save.playSeconds / 60),
        )
        for ((label, value) in rows) {
            D.card(c, D.dp(24f), y, D.dp(24f) + cw, y + D.dp(54f), D.color(theme.boardBg), D.dp(15f))
            D.textFit(c, label, D.dp(42f), y + D.dp(34f), D.sp(14f), cw - D.dp(140f), D.withAlpha(D.color(theme.textPrimary), 200), Paint.Align.LEFT, bold = false)
            D.textFit(c, value, D.dp(24f) + cw - D.dp(18f), y + D.dp(35f), D.sp(17f), cw - D.dp(140f), D.color(theme.accent), Paint.Align.RIGHT)
            y += D.dp(64f)
        }
        c.restore()
        // y is the content-space bottom; the last card must reach the bottom margin
        maxScroll = min(0f, host.height.toFloat() - D.dp(24f) - y)
        scrollY = scrollY.coerceIn(maxScroll, 0f)
    }

    private fun sectionHeader(c: Canvas, label: String, y: Float) {
        D.labelText(c, label, D.dp(24f), y, D.sp(10f), D.color(theme.accent), Paint.Align.LEFT)
        val end = D.dp(24f) + D.textWidth(label.uppercase(), D.sp(10f)) + label.length * D.sp(1.6f) + D.dp(10f)
        if (end < host.width.toFloat() - D.dp(30f))
            D.rect(c, end, y - D.sp(4f), host.width.toFloat() - D.dp(24f), y - D.sp(4f) + 1.2f, D.withAlpha(D.color(theme.textPrimary), 50), 0.6f)
    }

    override fun onTouch(e: MotionEvent): Boolean {
        backButton?.let { if (e.actionMasked == MotionEvent.ACTION_DOWN && it.contains(e.x, e.y)) { it.pressT = 1f; it.onTap(); Audio.play("click"); return true } }
        coinPill?.let { if (e.actionMasked == MotionEvent.ACTION_DOWN && it.contains(e.x, e.y)) { onCoinsTap(); return true } }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { dragging = true; dragStart = e.y; scrollStart = scrollY }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    scrollY = (scrollStart + (e.y - dragStart)).coerceIn(maxScroll, 0f)
                    host.wake()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
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

        // theme of the week: deterministic weekly discount on one locked theme
        val weekIdx = Save.weekSeed() % Themes.ALL.size
        for ((ti, t) in Themes.ALL.withIndex()) {
            val unlocked = Save.themeUnlocked(t.id)
            val inUse = Save.selectedTheme == t.id
            val onSale = !unlocked && ti == weekIdx
            val price = if (onSale) t.price / 2 else t.price
            D.rect(c, D.dp(24f), y + D.dp(5f), D.dp(24f) + cw, y + cardH + D.dp(5f), D.withAlpha(Color.BLACK, 70), D.dp(18f))
            D.gradientRect(c, D.dp(24f), y, D.dp(24f) + cw, y + cardH, D.color(t.bgTop), D.color(t.bgBottom), D.dp(18f))
            D.rectStroke(c, D.dp(24f) + 0.8f, y + 0.8f, D.dp(24f) + cw - 0.8f, y + cardH - 0.8f, D.withAlpha(Color.WHITE, 26), 1.3f, D.dp(18f))
            if (inUse) D.rectStroke(c, D.dp(24f), y, D.dp(24f) + cw, y + cardH, D.color(theme.accent), D.dp(2.5f), D.dp(18f))
            if (onSale) D.rectStroke(c, D.dp(24f), y, D.dp(24f) + cw, y + cardH, 0xFFFFD166.toInt(), D.dp(1.6f), D.dp(18f))

            // live preview: mini board with blocks cascading in
            val pv = RectF(D.dp(36f), y + D.dp(14f), D.dp(36f) + D.dp(60f), y + D.dp(74f))
            D.rect(c, pv.left, pv.top, pv.right, pv.bottom, D.color(t.boardBg), D.dp(8f))
            val ph = (host.globalTime * 0.8f + ti * 0.35f) % 2f // each theme's preview cycles out of phase
            var bx = pv.left + D.dp(8f)
            for (i in 0..2) {
                val kk = ((ph * 3f) - i).coerceIn(0f, 1f)
                if (kk > 0f) {
                    val col = D.color(t.blockColors[i])
                    val drop = (1f - kk) * D.dp(18f)
                    D.blockCell(c, bx, pv.top + D.dp(12f) - drop, bx + D.dp(14f), pv.top + D.dp(26f) - drop, col, D.dp(4f), (255 * kk).toInt())
                }
                bx += D.dp(17f)
            }
            bx = pv.left + D.dp(8f)
            for (i in 3..5) {
                val kk = ((ph * 3f) - i).coerceIn(0f, 1f)
                if (kk > 0f) {
                    val col = D.color(t.blockColors[i])
                    val drop = (1f - kk) * D.dp(18f)
                    D.blockCell(c, bx, pv.top + D.dp(30f) - drop, bx + D.dp(14f), pv.top + D.dp(42f) - drop, col, D.dp(4f), (255 * kk).toInt())
                }
                bx += D.dp(17f)
            }

            // text must stop before the action button (or card edge)
            val btnLeft = D.dp(24f) + cw - if (inUse) D.dp(16f) else D.dp(88f) - D.dp(10f)
            val nameW = D.fitSize(t.displayName, D.sp(16f), btnLeft - (pv.right + D.dp(14f)))
            if (onSale) {
                val swl = D.textWidth(s(R.string.sale), D.sp(9f)) + D.dp(12f)
                D.gradientRect(c, pv.right + D.dp(14f) + D.textWidth(t.displayName, nameW) + D.dp(8f), y + D.dp(16f), pv.right + D.dp(14f) + D.textWidth(t.displayName, nameW) + D.dp(8f) + swl, y + D.dp(32f), 0xFFFF5D73.toInt(), 0xFFE03A5C.toInt(), D.dp(8f))
                D.text(c, s(R.string.sale), pv.right + D.dp(14f) + D.textWidth(t.displayName, nameW) + D.dp(8f) + swl / 2f, y + D.dp(28f), D.sp(9f), Color.WHITE)
            }
            D.text(c, t.displayName, pv.right + D.dp(14f), y + D.dp(30f), nameW, D.color(theme.textPrimary), Paint.Align.LEFT)
            val status = when {
                inUse -> s(R.string.theme_using)
                unlocked -> s(R.string.theme_unlocked)
                onSale -> "${t.price} → $price ${s(R.string.coins)}"
                else -> s(R.string.theme_locked, t.price)
            }
            D.textFit(c, status, pv.right + D.dp(14f), y + D.dp(54f), D.sp(11f), btnLeft - (pv.right + D.dp(14f)), D.withAlpha(D.color(theme.textPrimary), 180), Paint.Align.LEFT, bold = false)

            val btnR = RectF(D.dp(24f) + cw - D.dp(88f), y + cardH / 2 - D.dp(19f), D.dp(24f) + cw - D.dp(14f), y + cardH / 2 + D.dp(19f))
            if (!inUse) {
                val b = UiButton(btnR, if (unlocked) s(R.string.theme_use) else "$price", icon = if (unlocked) "" else "g:coin", bg = if (unlocked) D.color(theme.accent) else 0xFFFFB84D.toInt(), fg = if (unlocked) Color.WHITE else 0xFF40260A.toInt(), onTap = {
                    onThemeButton(t.id, price)
                }, textScale = 0.8f)
                b.inScroll = true
                buttons.add(b)
            }
            cardRects.add(RectF(D.dp(24f), y, D.dp(24f) + cw, y + cardH) to t.id)
            y += cardH + D.dp(14f)
        }

        // power-ups strip — five kinds including hint
        y += D.dp(10f)
        D.textFit(c, "Power-ups", D.dp(24f), y, D.sp(15f), cw, D.color(theme.textPrimary), Paint.Align.LEFT)
        y += D.dp(10f)
        val puW = (cw - D.dp(32f)) / 5f
        val puKinds = com.fareza.blokku.data.PowerKind.entries
        val puGlyphs = arrayOf("undo", "rotate", "bomb", "shuffle", "hint")
        val puPrices = intArrayOf(30, 40, 60, 40, 25)
        for (i in puKinds.indices) {
            val l = D.dp(24f) + i * (puW + D.dp(8f))
            val r = RectF(l, y, l + puW, y + D.dp(78f))
            D.card(c, r.left, r.top, r.right, r.bottom, D.color(theme.boardBg), D.dp(15f))
            val pTint = intArrayOf(0xFF64B5F6.toInt(), 0xFF7BE495.toInt(), 0xFFFF8A65.toInt(), 0xFFBA8DF5.toInt(), 0xFFFFE066.toInt())[i]
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
        val fb = UiButton(RectF(D.dp(24f), fy, D.dp(24f) + fcw, fy + D.dp(48f)), s(R.string.watch_ad_coins, 40), icon = "g:coin", bg = 0xFF62D97B.toInt(), fg = Color.WHITE, onTap = {
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

    private fun onThemeButton(id: String, price: Int = -1) {
        val t = Themes.byId(id)
        val cost = if (price >= 0) price else t.price
        if (Save.themeUnlocked(id)) {
            Save.selectedTheme = id
            Audio.play("reward")
            Haptic.success()
        } else if (Save.coins >= cost) {
            Save.coins -= cost
            Save.unlockTheme(id)
            Save.selectedTheme = id
            Audio.play("reward")
            Haptic.success()
            coinPill?.bump()
            celebrateAchievements()
        } else {
            Audio.play("invalid")
            addFloat(host.width / 2f, host.height / 2f, s(R.string.not_enough_coins), 0xFFFF5D73.toInt(), D.sp(15f))
        }
    }

    override fun wantsFrame() = super.wantsFrame() || true // live theme previews cycle

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
    private var howtoBtn: UiButton? = null

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
        y += D.dp(54f)
        toggles.add(UiToggle(RectF(l, y, r, y + D.dp(44f)), s(R.string.settings_reminder), { Save.reminderOn }, {
            Save.reminderOn = it
            if (it) com.fareza.blokku.reminder.Reminder.schedule(host.context) else com.fareza.blokku.reminder.Reminder.cancel(host.context)
        }, accent = D.color(theme.accent)))
        y += D.dp(64f)
        langBtn = UiButton(RectF(l, y, r, y + D.dp(48f)), "${s(R.string.settings_language)}: ${langLabel()}", bg = D.color(theme.boardBg), fg = D.color(theme.textPrimary), onTap = { cycleLang() })
        y += D.dp(60f)
        howtoBtn = UiButton(RectF(l, y, r, y + D.dp(48f)), s(R.string.settings_howto), icon = "g:info", bg = D.color(theme.boardBg), fg = D.color(theme.textPrimary), onTap = {
            Audio.play("click"); scene().push(TutorialScene())
        })
        y += D.dp(60f)
        if (!Save.adsRemoved) {
            removeAdsBtn = UiButton(RectF(l, y, r, y + D.dp(48f)), s(R.string.settings_remove_ads), icon = "g:star", bg = 0xFFFFB84D.toInt(), fg = 0xFF40260A.toInt(), onTap = { Ads.buyRemoveAds(host) })
        }
    }

    private fun langLabel(): String = when (Save.language) {
        "in" -> "Indonesia"
        else -> "English"
    }

    private fun cycleLang() {
        val next = when (Save.language) {
            "en" -> "in"
            else -> "en"
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
        howtoBtn?.let { it.appear.t = it.appear.duration; it.render(c) }
        if (!Save.adsRemoved) {
            removeAdsBtn?.let { it.appear.t = it.appear.duration; it.render(c) }
        } else {
            val w = host.width.toFloat()
            D.textFit(c, s(R.string.settings_remove_ads_done), w / 2f, (howtoBtn?.rect?.bottom ?: 0f) + D.dp(40f), D.sp(13f), w - D.dp(48f), 0xFF62D97B.toInt())
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
        howtoBtn?.let { if (it.contains(e.x, e.y)) { it.tap(); return true } }
        removeAdsBtn?.let { if (it.contains(e.x, e.y)) { it.tap(); return true } }
        return true
    }
}

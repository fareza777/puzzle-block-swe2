package com.fareza.blokku.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.fareza.blokku.R
import com.fareza.blokku.audio.Audio
import com.fareza.blokku.audio.Haptic
import com.fareza.blokku.core.Campaign
import com.fareza.blokku.core.GameEngine
import com.fareza.blokku.core.Mosaics
import com.fareza.blokku.data.Save
import com.fareza.blokku.render.D
import com.fareza.blokku.ui.Glyph
import kotlin.math.abs
import kotlin.math.min

// ============================ MODE SELECT ============================

/** Grid of the extra game modes (Arcade tab). */
class ModeSelectScene : BaseScene() {

    private class Card(val label: Int, val sub: Int, val col: Int, val glyph: String, val launch: () -> Unit)

    private val cardRects = ArrayList<Pair<RectF, Card>>()

    private val cards = listOf(
        Card(R.string.mode_expedition, R.string.mode_expedition_sub, 0xFFBA8DF5.toInt(), "star") { scene().push(GameScene(GameEngine.expedition())) },
        Card(R.string.mode_versus, R.string.mode_versus_sub, 0xFFFF5D73.toInt(), "swords") { scene().push(GameScene(GameEngine.versus())) },
        Card(R.string.mode_boss, R.string.mode_boss_sub, 0xFF6C5CE7.toInt(), "skull") { scene().push(GameScene(GameEngine.boss(3), bossArg = 3)) },
        Card(R.string.mode_gambit, R.string.mode_gambit_sub, 0xFFFF9F1C.toInt(), "coin") { scene().push(GameScene(GameEngine.gambit())) },
        Card(R.string.mode_avalanche, R.string.mode_avalanche_sub, 0xFFFF8A3C.toInt(), "warn") { scene().push(GameScene(GameEngine.avalanche())) },
        Card(R.string.mode_merge, R.string.mode_merge_sub, 0xFF4D96FF.toInt(), "merge") { scene().push(GameScene(GameEngine.merge())) },
        Card(R.string.mode_gravity, R.string.mode_gravity_sub, 0xFF7BE495.toInt(), "drop") { scene().push(GameScene(GameEngine.gravity())) },
        Card(R.string.mode_mosaic, R.string.mode_mosaic_sub, 0xFF38BDF8.toInt(), "image") { scene().push(MosaicSelectScene()) },
    )

    override fun onEnter() { makeBackButton() }

    override fun render(c: Canvas) {
        renderBackground(c)
        renderTopBar(c, s(R.string.arcade_title))
        cardRects.clear()

        val w = host.width.toFloat()
        val gap = D.dp(12f)
        val cw = (w - D.dp(40f) - gap) / 2f
        val ch = D.dp(96f)
        // centre hero + grid vertically so the screen feels composed, not top-heavy
        val totalH = D.dp(86f) + gap + 4 * ch + 3 * gap
        val startTop = host.safeTop + D.dp(70f)
        var top = startTop + ((host.height - startTop - D.dp(18f) - totalH) / 2f).coerceAtLeast(0f)

        // campaign hero card spanning the full width
        val heroR = RectF(D.dp(20f), top, w - D.dp(20f), top + D.dp(86f))
        D.card(c, heroR.left, heroR.top, heroR.right, heroR.bottom, D.color(theme.boardBg), D.dp(20f))
        val prog = Save.campaignCleared.coerceAtMost(Campaign.COUNT)
        val heroTint = 0xFFFFD166.toInt()
        D.circle(c, heroR.left + D.dp(38f), heroR.centerY(), D.dp(22f), D.withAlpha(heroTint, 46))
        Glyph.draw(c, "map", RectF(heroR.left + D.dp(38f) - D.dp(13f), heroR.centerY() - D.dp(13f), heroR.left + D.dp(38f) + D.dp(13f), heroR.centerY() + D.dp(13f)), heroTint)
        D.textFit(c, s(R.string.campaign_title), heroR.left + D.dp(72f), heroR.top + D.dp(34f), D.sp(16f), heroR.width() - D.dp(90f), D.color(theme.textPrimary), align = Paint.Align.LEFT)
        D.textFit(c, s(R.string.campaign_prog, prog, Campaign.COUNT), heroR.left + D.dp(72f), heroR.top + D.dp(54f), D.sp(11.5f), heroR.width() - D.dp(90f), D.withAlpha(D.color(theme.textPrimary), 170), align = Paint.Align.LEFT, bold = false)
        // mini progress bar
        val pbY = heroR.bottom - D.dp(12f)
        D.rect(c, heroR.left + D.dp(72f), pbY, heroR.right - D.dp(16f), pbY + D.dp(5f), D.withAlpha(Color.BLACK, 90), D.dp(2.5f))
        D.rect(c, heroR.left + D.dp(72f), pbY, heroR.left + D.dp(72f) + (heroR.width() - D.dp(88f)) * prog / Campaign.COUNT, pbY + D.dp(5f), heroTint, D.dp(2.5f))
        cardRects.add(heroR to Card(0, 0, 0, "") { scene().push(CampaignScene()) })

        top += D.dp(86f) + gap
        cards.forEachIndexed { i, card ->
            val col = i % 2
            val row = i / 2
            val l = D.dp(20f) + col * (cw + gap)
            val t = top + row * (ch + gap)
            val r = RectF(l, t, l + cw, t + ch)
            cardRects.add(r to card)
            D.card(c, r.left, r.top, r.right, r.bottom, D.color(theme.boardBg), D.dp(18f))
            D.rectStroke(c, r.left + 0.8f, r.top + 0.8f, r.right - 0.8f, r.bottom - 0.8f, D.withAlpha(card.col, 110), 1.4f, D.dp(18f))
            val chipR = D.dp(16f)
            D.circle(c, r.left + D.dp(26f), r.top + D.dp(28f), chipR, D.withAlpha(card.col, 48))
            Glyph.draw(c, card.glyph, RectF(r.left + D.dp(26f) - chipR * 0.62f, r.top + D.dp(28f) - chipR * 0.62f, r.left + D.dp(26f) + chipR * 0.62f, r.top + D.dp(28f) + chipR * 0.62f), card.col)
            D.textFit(c, s(card.label), r.left + D.dp(48f), r.top + D.dp(22f), D.sp(13.5f), r.width() - D.dp(58f), D.color(theme.textPrimary), align = Paint.Align.LEFT)
            D.textFit(c, s(card.sub), r.left + D.dp(48f), r.top + D.dp(40f), D.sp(9.5f), r.width() - D.dp(58f), D.withAlpha(D.color(theme.textPrimary), 165), align = Paint.Align.LEFT, bold = false)
            // ghost best for endless variants
            val best = when (card.label) {
                R.string.mode_expedition -> "EXPEDITION"
                R.string.mode_avalanche -> "AVALANCHE"
                R.string.mode_merge -> "MERGE"
                R.string.mode_gravity -> "GRAVITY"
                R.string.mode_gambit -> "GAMBIT"
                else -> null
            }
            if (best != null) {
                val b = Save.topRuns(best).firstOrNull() ?: 0
                if (b > 0) D.textFit(c, "${s(R.string.best)} $b", r.left + D.dp(14f), r.bottom - D.dp(14f), D.sp(10f), r.width() - D.dp(28f), 0xFFFFD166.toInt(), align = Paint.Align.LEFT)
            }
        }
    }

    override fun onTouch(e: MotionEvent): Boolean {
        backButton?.let { if (e.actionMasked == MotionEvent.ACTION_DOWN && it.contains(e.x, e.y)) { it.pressT = 1f; it.onTap(); Audio.play("click"); return true } }
        coinPill?.let { if (e.actionMasked == MotionEvent.ACTION_DOWN && it.contains(e.x, e.y)) { onCoinsTap(); return true } }
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            for ((r, card) in cardRects) if (r.contains(e.x, e.y)) {
                Audio.play("click"); Haptic.tick(); card.launch(); return true
            }
        }
        return true
    }
}

// ============================ CAMPAIGN MAP ============================

/** Scrollable zig-zag node path; each node launches a themed mini-challenge. */
class CampaignScene : BaseScene() {

    private var scrollY = 0f
    private var maxScroll = 0f
    private var dragging = false
    private var dragStart = 0f
    private var scrollStart = 0f
    private var moved = false
    private val nodeRects = ArrayList<Pair<RectF, Int>>()
    private var pulse = 0f

    private val typeGlyph = mapOf(
        Campaign.NodeType.PUZZLE to "puzzle",
        Campaign.NodeType.LEVEL to "trophy",
        Campaign.NodeType.RUSH to "rush",
        Campaign.NodeType.BOSS to "skull",
        Campaign.NodeType.MOSAIC to "image",
        Campaign.NodeType.MERGE to "merge",
        Campaign.NodeType.AVALANCHE to "warn",
        Campaign.NodeType.GRAVITY to "drop",
        Campaign.NodeType.GAMBIT to "coin",
        Campaign.NodeType.EXPEDITION to "star",
        Campaign.NodeType.VERSUS to "swords",
    )

    private val typeColor = mapOf(
        Campaign.NodeType.PUZZLE to 0xFFBA8DF5.toInt(),
        Campaign.NodeType.LEVEL to 0xFF7BE495.toInt(),
        Campaign.NodeType.RUSH to 0xFFFF5D73.toInt(),
        Campaign.NodeType.BOSS to 0xFF6C5CE7.toInt(),
        Campaign.NodeType.MOSAIC to 0xFF38BDF8.toInt(),
        Campaign.NodeType.MERGE to 0xFF4D96FF.toInt(),
        Campaign.NodeType.AVALANCHE to 0xFFFF8A3C.toInt(),
        Campaign.NodeType.GRAVITY to 0xFF63E6BE.toInt(),
        Campaign.NodeType.GAMBIT to 0xFFFF9F1C.toInt(),
        Campaign.NodeType.EXPEDITION to 0xFFFFD166.toInt(),
        Campaign.NodeType.VERSUS to 0xFFFF5D73.toInt(),
    )

    override fun onEnter() { makeBackButton() }

    override fun update(dt: Float) {
        super.update(dt)
        pulse += dt
    }

    override fun wantsFrame() = true // path breathes

    override fun render(c: Canvas) {
        renderBackground(c)
        renderTopBar(c, s(R.string.campaign_title))

        val w = host.width.toFloat()
        val h = host.height.toFloat()
        val top = host.safeTop + D.dp(66f)
        val nodeR = D.dp(30f)
        val spacing = D.dp(78f)
        val pathH = Campaign.COUNT * spacing + D.dp(40f)
        val scrollableH = h - top - D.dp(16f)
        // keep the next playable node in view on entry
        if (maxScroll == 0f && scrollY == 0f) {
            val want = pathH - (Save.campaignCleared * spacing + D.dp(120f))
            scrollY = (-want).coerceIn(min(0f, scrollableH - pathH), 0f)
        }

        nodeRects.clear()
        c.save()
        c.clipRect(0f, top - D.dp(8f), w, h)
        c.translate(0f, scrollY)

        val baseY = top + pathH - D.dp(30f)
        val cleared = Save.campaignCleared
        val centres = Array(Campaign.COUNT) { FloatArray(2) }
        for (i in 0 until Campaign.COUNT) {
            val (xf, _) = Campaign.nodePos(i)
            val px = w * xf
            val py = baseY - i * spacing
            centres[i][0] = px; centres[i][1] = py
        }
        // connecting ribbon between consecutive nodes
        D.p.style = Paint.Style.STROKE
        D.p.strokeWidth = D.dp(4f)
        D.p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until Campaign.COUNT - 1) {
            val lit = i < cleared
            D.p.color = D.withAlpha(if (lit) 0xFFFFD166.toInt() else D.color(theme.textPrimary), if (lit) 170 else 50)
            c.drawLine(centres[i][0], centres[i][1], centres[i + 1][0], centres[i + 1][1], D.p)
        }
        D.p.style = Paint.Style.FILL

        for (i in 0 until Campaign.COUNT) {
            val node = Campaign.NODES[i]
            val px = centres[i][0]; val py = centres[i][1]
            val state = when {
                i < cleared -> 2   // done
                i == cleared -> 1  // next
                else -> 0          // locked
            }
            val col = typeColor[node.type] ?: D.color(theme.accent)
            val r = RectF(px - nodeR, py - nodeR, px + nodeR, py + nodeR)
            nodeRects.add(r to i)
            when (state) {
                2 -> {
                    D.circle(c, px, py, nodeR, D.withAlpha(col, 235))
                    D.rectStroke(c, px - nodeR + 1.5f, py - nodeR + 1.5f, px + nodeR - 1.5f, py + nodeR - 1.5f, D.withAlpha(Color.WHITE, 90), 1.4f, nodeR)
                    Glyph.draw(c, "check", RectF(px - D.dp(11f), py - D.dp(11f), px + D.dp(11f), py + D.dp(11f)), Color.WHITE)
                }
                1 -> {
                    val breathe = 1f + 0.08f * kotlin.math.sin(pulse * 3f)
                    D.glowCircle(c, px, py, nodeR * 1.9f * breathe, col, 90)
                    D.circle(c, px, py, nodeR * breathe, D.darken(col, 0.05f))
                    D.rectStroke(c, px - nodeR * breathe + 1.5f, py - nodeR * breathe + 1.5f, px + nodeR * breathe - 1.5f, py + nodeR * breathe - 1.5f, D.withAlpha(Color.WHITE, 200), 2f, nodeR)
                    Glyph.draw(c, typeGlyph[node.type] ?: "play", RectF(px - D.dp(11f), py - D.dp(11f), px + D.dp(11f), py + D.dp(11f)), Color.WHITE)
                }
                else -> {
                    D.circle(c, px, py, nodeR * 0.82f, D.withAlpha(D.color(theme.boardBg), 230))
                    D.rectStroke(c, px - nodeR * 0.82f + 1f, py - nodeR * 0.82f + 1f, px + nodeR * 0.82f - 1f, py + nodeR * 0.82f - 1f, D.withAlpha(Color.WHITE, 26), 1f, nodeR * 0.82f)
                    Glyph.draw(c, "lock", RectF(px - D.dp(8f), py - D.dp(8f), px + D.dp(8f), py + D.dp(8f)), D.withAlpha(D.color(theme.textPrimary), 90))
                }
            }
            if (state > 0) {
                D.labelTextFit(c, node.label, px, py + nodeR + D.dp(12f), D.sp(9f), D.dp(120f), D.withAlpha(D.color(theme.textPrimary), if (state == 1) 230 else 120))
            }
        }
        c.restore()
        // extra headroom so the active node's label can scroll fully above the nav bar
        maxScroll = min(0f, scrollableH - pathH - D.dp(80f))
    }

    override fun onTouch(e: MotionEvent): Boolean {
        backButton?.let { if (e.actionMasked == MotionEvent.ACTION_DOWN && it.contains(e.x, e.y)) { it.pressT = 1f; it.onTap(); Audio.play("click"); return true } }
        coinPill?.let { if (e.actionMasked == MotionEvent.ACTION_DOWN && it.contains(e.x, e.y)) { onCoinsTap(); return true } }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { dragging = true; moved = false; dragStart = e.y; scrollStart = scrollY }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val dy = e.y - dragStart
                    if (abs(dy) > host.touchSlopPx) moved = true
                    scrollY = (scrollStart + dy).coerceIn(maxScroll, 0f)
                    host.wake()
                }
            }
            MotionEvent.ACTION_UP -> {
                dragging = false
                if (!moved) {
                    for ((r, i) in nodeRects) {
                        val rr = RectF(r.left, r.top + scrollY, r.right, r.bottom + scrollY)
                        if (rr.contains(e.x, e.y) && i <= Save.campaignCleared) {
                            Audio.play("click"); Haptic.tick()
                            scene().push(GameScene(Campaign.buildEngine(i), campaignNode = i,
                                mosaicIndex = if (Campaign.NODES[i].type == Campaign.NodeType.MOSAIC) Campaign.NODES[i].arg else -1,
                                bossArg = if (Campaign.NODES[i].type == Campaign.NodeType.BOSS) Campaign.NODES[i].arg else 0))
                            return true
                        }
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }
}

// ============================ MOSAIC GALLERY ============================

/** Pick a mosaic picture to reveal; finished ones show full colour. */
class MosaicSelectScene : BaseScene() {

    private val cellRects = ArrayList<Pair<RectF, Int>>()

    override fun onEnter() { makeBackButton() }

    override fun render(c: Canvas) {
        renderBackground(c)
        renderTopBar(c, s(R.string.mode_mosaic))
        cellRects.clear()
        val w = host.width.toFloat()
        val cols = 3
        val gap = D.dp(12f)
        val cw = (w - D.dp(40f) - gap * (cols - 1)) / cols
        val ch = cw * 0.8f
        val rows = (Mosaics.COUNT + cols - 1) / cols
        val gridH = rows * ch + (rows - 1) * gap
        // centre the gallery vertically so the screen doesn't feel half-empty
        val introTop = host.safeTop + D.dp(72f)
        val avail = host.height - introTop - D.dp(20f)
        val top = introTop + ((avail - gridH) / 2f).coerceAtLeast(0f)
        D.textFit(c, s(R.string.mosaic_intro), w / 2f, introTop - D.dp(10f), D.sp(11f), w - D.dp(48f), D.withAlpha(D.color(theme.textPrimary), 160), bold = false)

        for (i in 0 until Mosaics.COUNT) {
            val col = i % cols; val row = i / cols
            val l = D.dp(20f) + col * (cw + gap)
            val t = top + row * (ch + gap)
            val r = RectF(l, t, l + cw, t + ch)
            cellRects.add(r to i)
            val done = Save.mosaicIsDone(i)
            D.card(c, r.left, r.top, r.right, r.bottom, D.color(theme.boardBg), D.dp(14f), elevated = !done)
            // thumbnail: the art itself — painted pixels where done, dim silhouette otherwise
            val art = Mosaics.art(i)
            val pad = D.dp(8f)
            val pc = minOf((r.width() - pad * 2) / 9f, (r.height() - pad * 2 - D.dp(14f)) / 9f)
            val ox = r.centerX() - pc * 4.5f
            val oy = r.top + pad
            for (p in art.indices) {
                val ac = art[p]
                if (ac == 0) continue
                val pr = p / 9; val pcc = p % 9
                D.rect(c, ox + pcc * pc, oy + pr * pc, ox + pcc * pc + pc * 0.9f, oy + pr * pc + pc * 0.9f, D.withAlpha(ac, if (done) 255 else 60), pc * 0.15f)
            }
            if (done) {
                D.labelTextFit(c, s(R.string.claimed), r.centerX(), r.bottom - D.dp(10f), D.sp(8f), r.width() - D.dp(12f), 0xFF7BE495.toInt())
            } else {
                Glyph.draw(c, "play", RectF(r.centerX() - D.dp(6f), r.bottom - D.dp(16f), r.centerX() + D.dp(6f), r.bottom - D.dp(4f)), D.color(theme.accent))
            }
        }
    }

    override fun onTouch(e: MotionEvent): Boolean {
        backButton?.let { if (e.actionMasked == MotionEvent.ACTION_DOWN && it.contains(e.x, e.y)) { it.pressT = 1f; it.onTap(); Audio.play("click"); return true } }
        coinPill?.let { if (e.actionMasked == MotionEvent.ACTION_DOWN && it.contains(e.x, e.y)) { onCoinsTap(); return true } }
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            for ((r, i) in cellRects) if (r.contains(e.x, e.y)) {
                Audio.play("click"); Haptic.tick()
                scene().push(GameScene(GameEngine.mosaic(i), mosaicIndex = i))
                return true
            }
        }
        return true
    }
}

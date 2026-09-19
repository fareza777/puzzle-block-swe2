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
import com.fareza.blokku.core.Board
import com.fareza.blokku.core.Daily
import com.fareza.blokku.core.GameEngine
import com.fareza.blokku.core.GoalType
import com.fareza.blokku.core.Levels
import com.fareza.blokku.core.Mode
import com.fareza.blokku.core.Piece
import com.fareza.blokku.data.Achievements
import com.fareza.blokku.data.MissionType
import com.fareza.blokku.data.Missions
import com.fareza.blokku.data.PowerKind
import com.fareza.blokku.data.Save
import com.fareza.blokku.data.Themes
import com.fareza.blokku.render.Anim
import com.fareza.blokku.render.D
import com.fareza.blokku.render.Ease
import com.fareza.blokku.ui.Glyph
import com.fareza.blokku.ui.UiButton
import com.fareza.blokku.ui.UiIconButton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

class GameScene(
    val engine: GameEngine,
    val levelIndex: Int = -1,
    val dailySeed: Long = 0L,
) : BaseScene() {

    // ---- layout ----
    private var boardRect = RectF()
    private var cell = 0f
    private var trayY = 0f
    private var trayCell = 0f
    private var hudTop = 0f
    private var meterRect = RectF()
    private var powerY = 0f
    private val powerRects = arrayOf(RectF(), RectF(), RectF(), RectF())
    private val powerKinds = arrayOf(PowerKind.UNDO, PowerKind.ROTATE, PowerKind.BOMB, PowerKind.SHUFFLE)
    private val powerGlyphs = arrayOf("undo", "rotate", "bomb", "shuffle")

    private var pauseBtn: UiIconButton? = null

    // ---- drag state ----
    private var dragIndex = -1
    private var dragX = 0f
    private var dragY = 0f
    private var dragOffY = 0f
    private var snapRow = -1
    private var snapCol = -1
    private var snapFits = false
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    // ---- armed power-up modes ----
    private var bombArmed = false
    private var rotateArmed = false
    private var bombHover = -1 // board index preview

    // ---- fx state ----
    private val cellAnim = HashMap<Int, Float>()       // board idx -> age since placed
    private val clearing = ArrayList<ClearFx>()
    private var trayPop = floatArrayOf(0f, 0f, 0f)
    private var comboBannerT = 0f
    private var comboBannerN = 0
    private var meterFlash = 0f
    private var boardShake = 0f
    private var trayRects = arrayOfNulls<RectF>(3)
    private var enterAnim = Anim(0.5f, Ease.outCubic)

    // ---- end overlays ----
    private var overlay = Overlay.NONE
    private var overlayAnim = Anim(0.35f, Ease.outBack)
    private var gameOverDelay = -1f
    private var sessionStart = System.currentTimeMillis()
    private var statsCommitted = false
    private var shownStars = 0
    private var starAnimT = 0f
    private var revived = false
    private var awardCoins = 0

    enum class Overlay { NONE, PAUSE, GAMEOVER, LEVEL_COMPLETE, QUIT_CONFIRM }

    class ClearFx(val cellsWithColor: IntArray, var age: Float = 0f) // packed idx | color<<16
    {
        val life = 0.34f
    }

    override fun onEnter() {
        Audio.play("spawn")
        computeLayout()
        pauseBtn = UiIconButton(D.dp(34f), hudTop - D.dp(4f), D.dp(17f), "Ⅱ") { pause() }
        pauseBtn?.fg = D.color(theme.textPrimary)
        for (i in 0..2) trayPop[i] = 0.6f
        if (!Save.tutorialDone) {
            scene().push(TutorialScene())
        }
    }

    private fun pause() {
        if (overlay == Overlay.NONE) {
            overlay = Overlay.PAUSE
            overlayAnim.reset()
            Audio.play("click")
        }
    }

    private fun computeLayout() {
        val w = host.width.toFloat()
        val h = host.height.toFloat()
        hudTop = host.safeTop + D.dp(34f)
        boardRect = host.boardArea()
        // shift board down a bit for goal/moves line in level modes
        if (engine.mode != Mode.CLASSIC) boardRect.offset(0f, D.dp(22f))
        cell = boardRect.width() / 9f

        val powerSize = D.dp(52f)
        val gap = D.dp(14f)
        val totalW = powerSize * 4 + gap * 3
        var px = (w - totalW) / 2f
        powerY = boardRect.bottom + D.dp(10f)
        for (i in 0..3) {
            powerRects[i].set(px, powerY, px + powerSize, powerY + powerSize)
            px += powerSize + gap
        }

        trayCell = cell * 0.62f
        trayY = powerY + powerSize + D.dp(12f)

        meterRect = RectF(w / 2f - D.dp(70f), hudTop + D.dp(30f), w / 2f + D.dp(70f), hudTop + D.dp(38f))
        coinPill = null
    }

    override fun onResume() {
        computeLayout()
    }

    // ============ update ============

    override fun update(dt: Float) {
        super.update(dt)
        enterAnim.update(dt)
        pauseBtn?.update(dt)
        for (i in 0..2) trayPop[i] = (trayPop[i] + dt * 2.2f).coerceAtMost(1f)
        comboBannerT = (comboBannerT - dt).coerceAtLeast(0f)
        meterFlash = (meterFlash - dt).coerceAtLeast(0f)
        boardShake = (boardShake - dt * 2.4f).coerceAtLeast(0f)

        val it = cellAnim.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.setValue(e.value + dt)
            if (e.value > 0.4f) it.remove()
        }
        val cit = clearing.iterator()
        while (cit.hasNext()) {
            val fx = cit.next()
            fx.age += dt
            if (fx.age > fx.life) cit.remove()
        }

        if (gameOverDelay > 0f) {
            gameOverDelay -= dt
            if (gameOverDelay <= 0f) {
                gameOverDelay = -1f
                endRound()
            }
        }
        if (overlay != Overlay.NONE) overlayAnim.update(dt)
        if (overlay == Overlay.LEVEL_COMPLETE) starAnimT += dt
    }

    override fun wantsFrame(): Boolean =
        super.wantsFrame() || clearing.isNotEmpty() || cellAnim.isNotEmpty() ||
            comboBannerT > 0 || meterFlash > 0 || boardShake > 0 ||
            dragIndex >= 0 || gameOverDelay > 0 || overlayAnim.let { !it.done && overlay != Overlay.NONE } ||
            enterAnim.t < enterAnim.duration || trayPop.any { it < 1f } ||
            (overlay == Overlay.LEVEL_COMPLETE && starAnimT < 2f)

    // ============ touch ============

    override fun onTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; downTime = System.currentTimeMillis()
                if (overlay != Overlay.NONE) { handleOverlayTap(e.x, e.y); return true }
                pauseBtn?.let { if (it.contains(e.x, e.y)) { it.pressT = 1f; it.onTap(); return true } }
                coinPill?.let { if (it.contains(e.x, e.y)) { onCoinsTap(); return true } }
                // power-ups
                for (i in 0..3) {
                    if (powerRects[i].contains(e.x, e.y)) { onPowerTap(i); return true }
                }
                if (bombArmed) {
                    val idx = boardIndexAt(e.x, e.y)
                    if (idx >= 0) { doBomb(idx); return true }
                }
                // pick up tray piece
                for (i in 0..2) {
                    val tr = trayRects[i] ?: continue
                    if (engine.tray[i] != null && tr.contains(e.x, e.y)) {
                        if (rotateArmed) {
                            doRotate(i)
                            return true
                        }
                        dragIndex = i
                        dragX = e.x
                        dragY = e.y
                        dragOffY = D.dp(72f)
                        Audio.play("pickup")
                        Haptic.tick()
                        updateSnap()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragIndex >= 0) {
                    dragX = e.x; dragY = e.y
                    updateSnap()
                    host.wake()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (overlay != Overlay.NONE) { dragIndex = -1; return true }
                if (dragIndex >= 0) {
                    val i = dragIndex
                    dragIndex = -1
                    if (snapFits && snapRow >= 0) {
                        doPlace(i, snapRow, snapCol)
                    } else {
                        Audio.play("invalid")
                        boardShake = 0.25f
                        Haptic.error()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> dragIndex = -1
        }
        return true
    }

    private fun handleOverlayTap(x: Float, y: Float) {
        // buttons are hit-tested in overlayButtons
        for (b in overlayButtons) {
            if (b.contains(x, y)) { b.pressT = 1f; b.tap(); return }
        }
        // click outside quit/pause dialog dismisses
        if (overlay == Overlay.PAUSE && !dialogRect.contains(x, y)) { overlay = Overlay.NONE }
    }

    private fun updateSnap() {
        val p = engine.tray[dragIndex] ?: return
        // piece top-left in board coords (finger holds piece center, offset up)
        val px = dragX - p.cols * cell / 2f
        val py = dragY - dragOffY - p.rows * cell / 2f
        snapCol = ((px - boardRect.left) / cell).roundToInt()
        snapRow = ((py - boardRect.top) / cell).roundToInt()
        snapFits = snapRow >= -1 && snapCol >= -1 && engine.board.fits(p, snapRow, snapCol)
        // also allow preview within tolerance: if slightly off, find nearest fit within 1 cell
        if (!snapFits) {
            for (dr in -1..1) for (dc in -1..1) {
                if (engine.board.fits(p, snapRow + dr, snapCol + dc)) {
                    snapRow += dr; snapCol += dc; snapFits = true; return
                }
            }
        }
    }

    private fun doPlace(i: Int, r: Int, cIdx: Int) {
        val piece = engine.tray[i] ?: return
        capturePlaced(piece, r, cIdx) // snapshot cells+color before engine consumes the piece
        val res = engine.place(i, r, cIdx)
        if (!res.placed) { Audio.play("invalid"); return }
        Audio.play("place")
        Haptic.tick()
        if (res.clearCells.isNotEmpty()) {
            // colors of cleared cells come from the pre-place board snapshot
            val fx = ClearFx(res.clearCells.map { idx -> idx or ((preBoard?.get(idx) ?: 1) shl 16) }.toIntArray())
            clearing.add(fx)
            val n = res.lines
            when {
                n >= 3 -> Audio.play("clear3")
                n == 2 -> Audio.play("clear2")
                else -> Audio.play("clear1")
            }
            Haptic.success()
            // particles at each cleared cell
            for (cc in res.clearCells) {
                val br = cc / 9; val bc = cc % 9
                particles.burst(
                    boardRect.left + (bc + 0.5f) * cell, boardRect.top + (br + 0.5f) * cell,
                    cellColor(preBoard?.get(cc) ?: 1), count = 4, speed = D.dp(180f), size = D.dp(8f),
                    life = 0.6f, gravity = D.dp(500f),
                )
            }
            // combo banner
            if (res.comboCount >= 2) {
                comboBannerN = res.comboCount
                comboBannerT = 1.1f
                Audio.play("combo")
            }
            addFloat(
                boardRect.centerX(), boardRect.top + boardRect.height() * 0.4f,
                "+${res.gained}", D.color(theme.accent), D.sp(30f),
            )
            Missions.track(MissionType.LINES_TOTAL, res.lines)
            Missions.track(MissionType.COMBO_ONCE, res.comboCount)
            if (res.meterFull) {
                meterFlash = 1f
                grantRandomPowerUp()
            }
        } else {
            Audio.play("place")
        }
        for (cc in lastPlacedCells) cellAnim[cc] = 0f
        Missions.track(MissionType.CELLS_TOTAL, lastPlacedCells.size)
        if (engine.trayEmpty()) for (k in 0..2) trayPop[k] = 0f
        for (k in 0..2) if (engine.tray[k] != null && trayPop[k] <= 0f) trayPop[k] = 0.01f
        afterMove()
    }

    private var preBoard: IntArray? = null
    private var lastPlacedCells = IntArray(0)

    private fun capturePlaced(p: Piece, r: Int, cIdx: Int) {
        preBoard = engine.board.cells.copyOf()
        val list = ArrayList<Int>()
        for (pc in p.cells) {
            list.add((r + (pc shr 4)) * 9 + cIdx + (pc and 15))
        }
        lastPlacedCells = list.toIntArray()
    }

    private fun afterMove() {
        Achievements.checkAll()
        if (engine.goalMet && engine.mode != Mode.CLASSIC) {
            Audio.play("win")
            awardCoins = 30 + levelIndex.coerceAtLeast(0)
            gameOverDelay = 0.6f
        } else if (engine.gameOver) {
            Audio.play("gameover")
            Haptic.error()
            boardShake = 0.5f
            gameOverDelay = 0.9f
        }
        host.wake()
    }

    private fun endRound() {
        commitStats()
        overlay = when {
            engine.goalMet && engine.mode != Mode.CLASSIC -> {
                shownStars = if (engine.mode == Mode.LEVEL) {
                    val def = Levels.get(levelIndex)
                    val st = Levels.stars(engine, def)
                    Save.setStars(levelIndex, st)
                    st
                } else 3
                if (engine.mode == Mode.DAILY) {
                    Save.dailiesDone++
                    Save.markDailyDone(dailySeed)
                    if (engine.score > Save.bestDailyScore) Save.bestDailyScore = engine.score
                }
                Save.coins += awardCoins
                starAnimT = 0f
                Overlay.LEVEL_COMPLETE
            }
            else -> Overlay.GAMEOVER
        }
        overlayAnim.reset()
        if (overlay == Overlay.GAMEOVER) {
            if (engine.score > Save.bestClassic && engine.mode == Mode.CLASSIC) {
                Save.bestClassic = engine.score
            }
        }
    }

    private fun commitStats() {
        if (statsCommitted) return
        statsCommitted = true
        Save.gamesPlayed++
        Save.totalLines += engine.linesCleared
        Save.totalCells += engine.cellsPlaced
        if (engine.bestCombo > Save.lifetimeBestCombo) Save.lifetimeBestCombo = engine.bestCombo
        if (engine.score > Save.bestSingleScore) Save.bestSingleScore = engine.score
        if (engine.mode == Mode.CLASSIC && engine.score > Save.bestClassic) Save.bestClassic = engine.score
        Save.playSeconds += (System.currentTimeMillis() - sessionStart) / 1000
        Missions.track(MissionType.PLAY_GAMES, 1)
        Missions.track(MissionType.SCORE_GAME, engine.score)
        Achievements.checkAll()
    }

    // ============ power-ups ============

    private fun onPowerTap(i: Int) {
        val kind = powerKinds[i]
        Audio.play("click")
        Haptic.tick()
        when (kind) {
            PowerKind.UNDO -> {
                if (!engine.canUndo) { toastPowerEmpty(); return }
                if (!Save.usePowerUp(kind)) { offerBuyPower(kind); return }
                engine.undo()
                clearing.clear()
                Audio.play("undo")
                addFloat(boardRect.centerX(), boardRect.centerY(), s(R.string.undo_done), Color.WHITE, D.sp(18f))
            }
            PowerKind.ROTATE -> {
                if (Save.powerUps(kind) <= 0) { offerBuyPower(kind); return }
                rotateArmed = !rotateArmed
                bombArmed = false
                addFloat(boardRect.centerX(), trayY - D.dp(6f), s(R.string.rotate_hint), Color.WHITE, D.sp(14f))
            }
            PowerKind.BOMB -> {
                if (Save.powerUps(kind) <= 0) { offerBuyPower(kind); return }
                bombArmed = !bombArmed
                rotateArmed = false
                if (bombArmed) addFloat(boardRect.centerX(), boardRect.top - D.dp(8f), s(R.string.bomb_hint), Color.WHITE, D.sp(14f))
            }
            PowerKind.SHUFFLE -> {
                if (!Save.usePowerUp(kind)) { offerBuyPower(kind); return }
                Save.powerupsUsed++
                engine.shuffleTray()
                for (k in 0..2) trayPop[k] = 0f
                Audio.play("shuffle")
                Missions.track(MissionType.USE_POWERUPS, 1)
            }
        }
    }

    private fun doRotate(i: Int) {
        if (engine.rotateTray(i)) {
            Save.usePowerUp(PowerKind.ROTATE)
            Save.powerupsUsed++
            rotateArmed = false
            trayPop[i] = 0f
            Audio.play("rotate")
            Missions.track(MissionType.USE_POWERUPS, 1)
        }
    }

    private fun doBomb(idx: Int) {
        val r = idx / 9; val cIdx = idx % 9
        preBoard = engine.board.cells.copyOf()
        Save.usePowerUp(PowerKind.BOMB)
        Save.powerupsUsed++
        val removed = engine.blastArea(r, cIdx)
        bombArmed = false
        Audio.play("bomb")
        Haptic.heavy()
        boardShake = 0.4f
        for (cc in removed) {
            val br = cc / 9; val bc = cc % 9
            particles.burst(
                boardRect.left + (bc + 0.5f) * cell, boardRect.top + (br + 0.5f) * cell,
                cellColor(preBoard?.get(cc) ?: 1), count = 5, speed = D.dp(240f), size = D.dp(9f),
            )
        }
        particles.ring(boardRect.left + (cIdx + 0.5f) * cell, boardRect.top + (r + 0.5f) * cell, D.color(theme.accent))
        Missions.track(MissionType.USE_POWERUPS, 1)
        afterMove()
    }

    private fun grantRandomPowerUp() {
        val kind = PowerKind.entries[Random.nextInt(4)]
        Save.addPowerUp(kind, 1)
        Audio.play("meter")
        val label = when (kind) {
            PowerKind.UNDO -> s(R.string.power_undo)
            PowerKind.ROTATE -> s(R.string.power_rotate)
            PowerKind.BOMB -> s(R.string.power_bomb)
            PowerKind.SHUFFLE -> s(R.string.power_shuffle)
        }
        addFloat(boardRect.centerX(), meterRect.bottom + D.dp(24f), "+1 $label", D.color(theme.accent), D.sp(17f))
    }

    private fun toastPowerEmpty() {
        addFloat(boardRect.centerX(), powerY - D.dp(8f), s(R.string.power_empty), Color.WHITE, D.sp(13f))
    }

    private fun offerBuyPower(kind: PowerKind) {
        toastPowerEmpty()
        Audio.play("invalid")
    }

    // ============ rendering ============

    override fun render(c: Canvas) {
        renderBackground(c)
        c.save()
        if (boardShake > 0f) {
            val dx = (Random.nextFloat() - 0.5f) * boardShake * D.dp(14f)
            val dy = (Random.nextFloat() - 0.5f) * boardShake * D.dp(14f)
            c.translate(dx, dy)
        }
        renderHud(c)
        renderBoard(c)
        renderPowerBar(c)
        renderTray(c)
        renderDrag(c)
        c.restore()
        renderFx(c)
        renderComboBanner(c)
        if (overlay != Overlay.NONE) renderOverlay(c)
    }

    private fun renderHud(c: Canvas) {
        val w = host.width.toFloat()
        pauseBtn?.render(c)
        // score
        D.text(c, "${engine.score}", w / 2f, hudTop + D.sp(14f), D.sp(30f), D.color(theme.textPrimary))
        val sub = when (engine.mode) {
            Mode.CLASSIC -> "${s(R.string.best)} ${max(Save.bestClassic, engine.score)}"
            Mode.LEVEL -> "${s(R.string.level)} ${levelIndex + 1} • ${goalLabel()} • ${s(R.string.moves_left)} ${engine.movesLeft}"
            Mode.DAILY -> "${s(R.string.menu_daily)} • ${goalLabel()} • ${s(R.string.moves_left)} ${engine.movesLeft}"
        }
        D.text(c, sub, w / 2f, hudTop + D.sp(14f) + D.sp(15f), D.sp(12f), D.withAlpha(D.color(theme.textPrimary), 200), bold = false)
        if (coinPill == null) coinPill = com.fareza.blokku.ui.CoinPill(w - D.dp(106f), hudTop - D.dp(4f)) { onCoinsTap() }
        coinPill?.render(c)
        // meter
        val mr = meterRect
        D.rect(c, mr.left, mr.top, mr.right, mr.bottom, D.withAlpha(Color.BLACK, 80), mr.height() / 2)
        val fill = engine.meter / 100f
        if (fill > 0) {
            val glowCol = if (meterFlash > 0) Color.WHITE else D.color(theme.accent)
            D.rect(c, mr.left + 2, mr.top + 2, mr.left + 2 + (mr.width() - 4) * fill, mr.bottom - 2, glowCol, mr.height() / 2)
        }
        if (meterFlash > 0) D.glowCircle(c, mr.centerX(), mr.centerY(), D.dp(60f) * meterFlash, D.color(theme.accent), (120 * meterFlash).toInt())
    }

    private fun goalLabel(): String = when (engine.goal.type) {
        GoalType.SCORE -> s(R.string.goal_score, engine.goal.target)
        GoalType.LINES -> s(R.string.goal_lines, engine.goal.target)
        GoalType.CELLS -> s(R.string.goal_cells, engine.goal.target)
        GoalType.NONE -> ""
    }

    private fun cellColor(colorIdx1: Int): Int {
        val idx = (colorIdx1 - 1).coerceIn(0, 6)
        return if (Save.colorblind) D.color(Themes.COLORBLIND_COLORS[idx])
        else D.color(theme.blockColors[idx])
    }

    private fun renderBoard(c: Canvas) {
        val a = enterAnim.v
        if (a <= 0f) return
        val cx = boardRect.centerX(); val cy = boardRect.centerY()
        c.save()
        c.scale(0.92f + 0.08f * a, 0.92f + 0.08f * a, cx, cy)

        // board panel
        val pad = D.dp(6f)
        D.rect(c, boardRect.left - pad, boardRect.top - pad + D.dp(4f), boardRect.right + pad, boardRect.bottom + pad, D.withAlpha(Color.BLACK, 70), D.dp(16f))
        D.rect(c, boardRect.left - pad, boardRect.top - pad, boardRect.right + pad, boardRect.bottom + pad, D.color(theme.boardBg), D.dp(16f))

        // empty cells
        for (r in 0 until 9) {
            for (col in 0 until 9) {
                val l = boardRect.left + col * cell + cell * 0.06f
                val t = boardRect.top + r * cell + cell * 0.06f
                D.rect(c, l, t, l + cell * 0.88f, t + cell * 0.88f, D.color(theme.cellEmpty), cell * 0.18f)
            }
        }

        // bomb hover preview
        if (bombArmed && bombHover >= 0) {
            val br = bombHover / 9; val bc = bombHover % 9
            D.rect(
                c,
                boardRect.left + (bc - 1) * cell, boardRect.top + (br - 1) * cell,
                boardRect.left + (bc + 2) * cell, boardRect.top + (br + 2) * cell,
                D.withAlpha(D.color(theme.accent), 60), cell * 0.2f,
            )
        }

        // ghost preview while dragging
        val dragPiece = if (dragIndex >= 0) engine.tray[dragIndex] else null
        var clearPreview: Board.ClearResult? = null
        if (dragPiece != null && snapFits && snapRow >= 0 && snapCol >= 0) {
            clearPreview = engine.board.findClears(dragPiece, snapRow, snapCol)
            // would-be cleared cells highlight
            for (cc in clearPreview.clearCells) {
                val cr = cc / 9; val ccx = cc % 9
                val l = boardRect.left + ccx * cell + cell * 0.05f
                val t = boardRect.top + cr * cell + cell * 0.05f
                D.rect(c, l, t, l + cell * 0.9f, t + cell * 0.9f, D.withAlpha(D.color(theme.accent), 110), cell * 0.18f)
            }
            // ghost cells
            for (pc in dragPiece.cells) {
                val gr = snapRow + (pc shr 4); val gc = snapCol + (pc and 15)
                val l = boardRect.left + gc * cell + cell * 0.07f
                val t = boardRect.top + gr * cell + cell * 0.07f
                D.blockCell(c, l, t, l + cell * 0.86f, t + cell * 0.86f, cellColor(dragPiece.colorIndex + 1), cell * 0.2f, alpha = 150)
            }
        }

        // placed blocks
        for (r in 0 until 9) {
            for (col in 0 until 9) {
                val v = engine.board.cells[r * 9 + col]
                if (v == 0) continue
                val idx = r * 9 + col
                if (isClearing(idx)) continue // drawn by fx layer instead
                val l = boardRect.left + col * cell + cell * 0.07f
                val t = boardRect.top + r * cell + cell * 0.07f
                val pop = cellAnim[idx]
                var scale = 1f
                if (pop != null) {
                    val k = (pop / 0.3f).coerceIn(0f, 1f)
                    scale = Ease.outBack(k)
                }
                drawCell(c, l, t, scale, cellColor(v), v)
            }
        }

        // clearing fx: shrinking blocks
        for (fx in clearing) {
            val k = (fx.age / fx.life).coerceIn(0f, 1f)
            val alpha = (255 * (1f - k)).toInt()
            val sc = 1f - Ease.inQuad(k) * 0.8f
            for (cc in fx.cellsWithColor) {
                val idx = cc and 0xFFFF
                val colr = (cc shr 16) and 0xFF
                val cr = idx / 9; val ccx = idx % 9
                val l = boardRect.left + ccx * cell + cell * 0.07f
                val t = boardRect.top + cr * cell + cell * 0.07f
                drawCell(c, l, t, sc, cellColor(colr), colr, alpha)
            }
        }
        c.restore()
    }

    private fun isClearing(idx: Int): Boolean {
        for (fx in clearing) for (cc in fx.cellsWithColor) if ((cc and 0xFFFF) == idx) return true
        return false
    }

    private fun drawCell(c: Canvas, l: Float, t: Float, scale: Float, color: Int, colorIdx1: Int, alpha: Int = 255) {
        val s = cell * 0.86f * scale
        val cx = l + cell * 0.43f; val cy = t + cell * 0.43f
        if (s <= 0.5f) return
        D.blockCell(c, cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2, color, s * 0.22f, alpha)
        if (Save.colorblind) {
            val idx = (colorIdx1 - 1).coerceIn(0, 6)
            D.text(c, Themes.COLORBLIND_SYMBOLS[idx], cx, cy + s * 0.12f, s * 0.36f, D.withAlpha(Color.WHITE, (alpha * 0.85f).toInt()))
        }
    }

    private fun renderPowerBar(c: Canvas) {
        for (i in 0..3) {
            val r = powerRects[i]
            val kind = powerKinds[i]
            val count = Save.powerUps(kind)
            val armed = (kind == PowerKind.BOMB && bombArmed) || (kind == PowerKind.ROTATE && rotateArmed)
            val canUndo = kind != PowerKind.UNDO || engine.canUndo
            val alpha = if (count <= 0 || !canUndo) 90 else 255
            // bg
            D.rect(c, r.left, r.top + D.dp(3f), r.right, r.bottom + D.dp(2f), D.withAlpha(Color.BLACK, 50), D.dp(14f))
            D.rect(c, r.left, r.top, r.right, r.bottom, if (armed) D.color(theme.accent) else D.color(theme.boardBg), D.dp(14f))
            if (armed) D.rectStroke(c, r.left, r.top, r.right, r.bottom, Color.WHITE, D.dp(1.5f), D.dp(14f))
            Glyph.draw(c, powerGlyphs[i], RectF(r.left + D.dp(8f), r.top + D.dp(8f), r.right - D.dp(8f), r.bottom - D.dp(8f)), D.withAlpha(D.color(theme.textPrimary), alpha))
            // count badge
            D.circle(c, r.right - D.dp(3f), r.top + D.dp(3f), D.dp(10f), if (count > 0) 0xFFFF5D73.toInt() else 0xFF555566.toInt())
            D.text(c, "$count", r.right - D.dp(3f), r.top + D.dp(3f) + D.sp(9f) * 0.36f, D.sp(9f), Color.WHITE)
        }
    }

    private fun renderTray(c: Canvas) {
        val w = host.width.toFloat()
        val slotW = w / 3f
        val trayTop = trayY
        val trayH = host.height - trayTop - D.dp(10f)
        for (i in 0..2) {
            val cx = slotW * i + slotW / 2f
            val cy = trayTop + trayH / 2f
            val p = engine.tray[i]
            // slot bg
            val slotRect = RectF(slotW * i + D.dp(6f), trayTop, slotW * (i + 1) - D.dp(6f), trayTop + trayH)
            trayRects[i] = slotRect
            if (p == null) {
                D.rect(c, slotRect.left, slotRect.top + slotRect.height() * 0.2f, slotRect.right, slotRect.bottom - slotRect.height() * 0.2f, D.withAlpha(Color.WHITE, 12), D.dp(14f))
                continue
            }
            val fitsAny = engine.board.anyFit(p)
            val popK = Ease.outBack(trayPop[i])
            val pieceW = p.cols * trayCell * popK
            val pieceH = p.rows * trayCell * popK
            val alpha = if (fitsAny) 255 else 110
            for (pc in p.cells) {
                val pr = pc shr 4; val pcc = pc and 15
                val l = cx - pieceW / 2 + pcc * trayCell * popK + trayCell * 0.05f * popK
                val t = cy - pieceH / 2 + pr * trayCell * popK + trayCell * 0.05f * popK
                D.blockCell(c, l, t, l + trayCell * 0.9f * popK, t + trayCell * 0.9f * popK, cellColor(p.colorIndex + 1), trayCell * 0.2f, alpha)
            }
            if (rotateArmed && fitsAny) {
                D.rectStroke(c, slotRect.left, slotRect.top, slotRect.right, slotRect.bottom, D.color(theme.accent), D.dp(2f), D.dp(14f))
            }
        }
    }

    private fun renderDrag(c: Canvas) {
        if (dragIndex < 0) return
        val p = engine.tray[dragIndex] ?: return
        val w = p.cols * cell
        val h = p.rows * cell
        val l0 = dragX - w / 2f
        val t0 = dragY - dragOffY - h / 2f
        // lift shadow
        for (pc in p.cells) {
            val pr = pc shr 4; val pcx = pc and 15
            val l = l0 + pcx * cell
            val t = t0 + pr * cell
            D.blockCell(c, l + cell * 0.05f, t + cell * 0.05f + D.dp(6f), l + cell * 0.95f, t + cell * 0.95f + D.dp(6f), Color.BLACK, cell * 0.2f, 60)
        }
        for (pc in p.cells) {
            val pr = pc shr 4; val pcx = pc and 15
            val l = l0 + pcx * cell + cell * 0.05f
            val t = t0 + pr * cell + cell * 0.05f
            D.blockCell(c, l, t, l + cell * 0.9f, t + cell * 0.9f, cellColor(p.colorIndex + 1), cell * 0.2f)
        }
    }

    private fun renderComboBanner(c: Canvas) {
        if (comboBannerT <= 0f) return
        val t = comboBannerT
        val appear = ((1.1f - t) / 0.25f).coerceIn(0f, 1f)
        val alpha = ((t / 0.4f).coerceIn(0f, 1f))
        val scale = Ease.outBack(appear)
        val w = host.width.toFloat()
        c.save()
        c.scale(scale, scale, w / 2f, boardRect.top + boardRect.height() * 0.45f)
        D.text(
            c, "COMBO ×$comboBannerN", w / 2f, boardRect.top + boardRect.height() * 0.45f,
            D.sp(34f), D.color(theme.accent), alpha = (255 * alpha).toInt(),
        )
        c.restore()
    }

    // ============ overlays ============

    private val overlayButtons = ArrayList<UiButton>()
    private val dialogRect = RectF()

    private fun renderOverlay(c: Canvas) {
        val a = overlayAnim.raw
        val w = host.width.toFloat(); val h = host.height.toFloat()
        D.rect(c, 0f, 0f, w, h, D.withAlpha(Color.BLACK, (160 * a).toInt()))
        val dw = min(w - D.dp(40f), D.dp(340f))
        val dh = when (overlay) {
            Overlay.GAMEOVER -> D.dp(300f)
            Overlay.LEVEL_COMPLETE -> D.dp(320f)
            else -> D.dp(240f)
        }
        val dl = (w - dw) / 2f
        val dt = (h - dh) / 2f
        dialogRect.set(dl, dt, dl + dw, dt + dh)
        c.save()
        c.scale(overlayAnim.v.coerceIn(0.01f, 1.2f), overlayAnim.v.coerceIn(0.01f, 1.2f), dialogRect.centerX(), dialogRect.centerY())
        D.rect(c, dialogRect.left, dialogRect.top + D.dp(6f), dialogRect.right, dialogRect.bottom + D.dp(6f), D.withAlpha(Color.BLACK, 80), D.dp(22f))
        D.gradientRect(c, dialogRect.left, dialogRect.top, dialogRect.right, dialogRect.bottom, D.lighten(D.color(theme.boardBg), 0.12f), D.color(theme.boardBg), D.dp(22f))
        c.restore()

        overlayButtons.clear()
        when (overlay) {
            Overlay.PAUSE -> renderPauseOverlay(c)
            Overlay.GAMEOVER -> renderGameOverOverlay(c)
            Overlay.LEVEL_COMPLETE -> renderLevelCompleteOverlay(c)
            Overlay.QUIT_CONFIRM -> renderQuitOverlay(c)
            Overlay.NONE -> {}
        }
        for (b in overlayButtons) b.render(c)
    }

    private fun overlayBtn(l: Float, t: Float, r: Float, b: Float, label: String, bg: Int, onTap: () -> Unit): UiButton {
        val btn = UiButton(RectF(l, t, r, b), label = label, bg = bg, fg = Color.WHITE, onTap = onTap)
        btn.appear.t = btn.appear.duration + btn.appear.delay // fully visible
        overlayButtons.add(btn)
        return btn
    }

    private fun renderPauseOverlay(c: Canvas) {
        D.text(c, s(R.string.paused), dialogRect.centerX(), dialogRect.top + D.dp(44f), D.sp(24f), D.color(theme.textPrimary))
        val bw = dialogRect.width() - D.dp(48f)
        val bx = dialogRect.left + D.dp(24f)
        var by = dialogRect.top + D.dp(70f)
        overlayBtn(bx, by, bx + bw, by + D.dp(50f), s(R.string.resume), D.color(theme.accent)) {
            overlay = Overlay.NONE; Audio.play("click")
        }
        by += D.dp(60f)
        overlayBtn(bx, by, bx + bw, by + D.dp(50f), s(R.string.restart), D.color(theme.gridLine)) {
            restart(); Audio.play("click")
        }
        by += D.dp(60f)
        overlayBtn(bx, by, bx + bw, by + D.dp(50f), s(R.string.quit), D.withAlpha(Color.WHITE, 40)) {
            overlay = Overlay.QUIT_CONFIRM; overlayAnim.reset()
        }
    }

    private fun renderQuitOverlay(c: Canvas) {
        D.text(c, s(R.string.quit_confirm), dialogRect.centerX(), dialogRect.top + D.dp(50f), D.sp(14f), D.color(theme.textPrimary), bold = false)
        val bw = (dialogRect.width() - D.dp(60f)) / 2f
        val by = dialogRect.bottom - D.dp(70f)
        overlayBtn(dialogRect.left + D.dp(24f), by, dialogRect.left + D.dp(24f) + bw, by + D.dp(48f), s(R.string.no), D.withAlpha(Color.WHITE, 40)) {
            overlay = Overlay.PAUSE; overlayAnim.reset()
        }
        overlayBtn(dialogRect.right - D.dp(24f) - bw, by, dialogRect.right - D.dp(24f), by + D.dp(48f), s(R.string.yes), 0xFFFF5D73.toInt()) {
            commitStats()
            scene().pop()
            Ads.maybeInterstitial(host.context)
        }
    }

    private fun renderGameOverOverlay(c: Canvas) {
        val cx = dialogRect.centerX()
        D.text(c, s(R.string.game_over), cx, dialogRect.top + D.dp(40f), D.sp(22f), D.color(theme.textPrimary))
        val isBest = engine.mode == Mode.CLASSIC && engine.score >= Save.bestClassic && engine.score > 0
        if (isBest) D.text(c, s(R.string.new_best), cx, dialogRect.top + D.dp(64f), D.sp(14f), D.color(theme.accent))
        D.text(c, "${engine.score}", cx, dialogRect.top + D.dp(100f), D.sp(40f), D.color(theme.textPrimary))
        val bw = dialogRect.width() - D.dp(48f)
        val bx = dialogRect.left + D.dp(24f)
        var by = dialogRect.top + D.dp(130f)
        if (!revived) {
            val label = if (!Save.adsRemoved && Ads.rewardedReady) s(R.string.watch_ad_continue) else "${s(R.string.continue_game)} — 🪙50"
            overlayBtn(bx, by, bx + bw, by + D.dp(48f), label, 0xFF62D97B.toInt()) {
                revive()
            }
            by += D.dp(58f)
        }
        overlayBtn(bx, by, bx + bw, by + D.dp(48f), s(R.string.restart), D.color(theme.accent)) {
            restart(); Audio.play("click")
        }
        by += D.dp(58f)
        overlayBtn(bx, by, bx + bw, by + D.dp(48f), s(R.string.quit), D.withAlpha(Color.WHITE, 40)) {
            commitStats(); scene().pop(); Ads.maybeInterstitial(host.context)
        }
    }

    private fun renderLevelCompleteOverlay(c: Canvas) {
        val cx = dialogRect.centerX()
        val title = if (engine.mode == Mode.DAILY) s(R.string.daily_complete) else s(R.string.level_complete)
        D.text(c, title, cx, dialogRect.top + D.dp(42f), D.sp(21f), D.color(theme.textPrimary))
        // stars
        for (i in 0..2) {
            val sx = cx + (i - 1) * D.dp(56f)
            val sy = dialogRect.top + D.dp(95f)
            val appearK = ((starAnimT - 0.25f * i) / 0.4f).coerceIn(0f, 1f)
            val sc = Ease.outBack(appearK)
            val lit = i < shownStars
            c.save()
            c.scale(sc.coerceAtLeast(0.01f), sc.coerceAtLeast(0.01f), sx, sy)
            drawStar(c, sx, sy, D.dp(20f), if (lit) 0xFFFFD166.toInt() else D.withAlpha(Color.WHITE, 50))
            c.restore()
        }
        D.text(c, "+$awardCoins ${s(R.string.coins)}", cx, dialogRect.top + D.dp(145f), D.sp(16f), 0xFFFFD166.toInt())
        val bw = dialogRect.width() - D.dp(48f)
        val bx = dialogRect.left + D.dp(24f)
        var by = dialogRect.top + D.dp(168f)
        if (engine.mode == Mode.LEVEL && levelIndex + 1 < Levels.COUNT) {
            overlayBtn(bx, by, bx + bw, by + D.dp(50f), s(R.string.next_level), D.color(theme.accent)) {
                val next = GameScene(GameEngine.level(Levels.get(levelIndex + 1)), levelIndex + 1)
                scene().swapTo(next)
                Audio.play("click")
            }
            by += D.dp(60f)
        }
        overlayBtn(bx, by, bx + bw, by + D.dp(50f), s(R.string.menu_play), D.withAlpha(Color.WHITE, 40)) {
            scene().pop()
        }
    }

    private fun drawStar(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        val path = android.graphics.Path()
        for (i in 0 until 10) {
            val ang = -Math.PI / 2 + i * Math.PI / 5
            val rr = if (i % 2 == 0) r else r * 0.45f
            val px = x + (Math.cos(ang) * rr).toFloat()
            val py = y + (Math.sin(ang) * rr).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        D.p.color = color
        D.p.style = android.graphics.Paint.Style.FILL
        c.drawPath(path, D.p)
    }

    private fun restart() {
        commitStats()
        val fresh = when (engine.mode) {
            Mode.CLASSIC -> GameScene(GameEngine.classic())
            Mode.LEVEL -> GameScene(GameEngine.level(Levels.get(levelIndex)), levelIndex)
            Mode.DAILY -> GameScene(Daily.todayEngine(), dailySeed = dailySeed)
        }
        scene().swapTo(fresh)
        Ads.maybeInterstitial(host.context)
    }

    private fun revive() {
        // rewarded ad path first; fall back to coins
        if (!Save.adsRemoved && Ads.rewardedReady) {
            Ads.showRewarded(host.context) { ok ->
                if (ok) doRevive()
            }
            return
        }
        if (Save.coins >= 50) {
            Save.coins -= 50
            doRevive()
        } else {
            toastPowerEmpty()
        }
    }

    private fun doRevive() {
        val removed = engine.reviveClear()
        revived = true
        overlay = Overlay.NONE
        statsCommitted = false
        sessionStart = System.currentTimeMillis()
        Audio.play("reward")
        for (cc in removed) {
            val br = cc / 9; val bc = cc % 9
            particles.burst(
                boardRect.left + (bc + 0.5f) * cell, boardRect.top + (br + 0.5f) * cell,
                D.color(theme.accent), count = 5, speed = D.dp(200f), size = D.dp(8f),
            )
        }
        particles.ring(boardRect.centerX(), boardRect.centerY(), D.color(theme.accent))
    }

    private fun boardIndexAt(x: Float, y: Float): Int {
        if (!boardRect.contains(x, y)) {
            if (bombArmed) { /* tapping outside board while armed = cancel */ bombArmed = false }
            return -1
        }
        val col = ((x - boardRect.left) / cell).toInt().coerceIn(0, 8)
        val row = ((y - boardRect.top) / cell).toInt().coerceIn(0, 8)
        return row * 9 + col
    }

    override fun onCoinsTap() {
        scene().push(ThemesScene())
    }

    override fun onBack(): Boolean {
        if (overlay != Overlay.NONE) { overlay = Overlay.NONE; return true }
        if (dragIndex >= 0) { dragIndex = -1; return true }
        pause()
        return true
    }
}

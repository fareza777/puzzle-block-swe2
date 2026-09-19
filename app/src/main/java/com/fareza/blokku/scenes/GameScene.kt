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
    private val powerRects = Array(5) { RectF() }
    private val powerKinds = arrayOf(PowerKind.UNDO, PowerKind.ROTATE, PowerKind.BOMB, PowerKind.SHUFFLE, PowerKind.HINT)
    private val powerGlyphs = arrayOf("undo", "rotate", "bomb", "shuffle", "hint")
    private val powerTints = intArrayOf(0xFF64B5F6.toInt(), 0xFF7BE495.toInt(), 0xFFFF8A65.toInt(), 0xFFBA8DF5.toInt(), 0xFFFFE066.toInt())
    private val powerNameRes = intArrayOf(R.string.power_undo_name, R.string.power_rotate_name, R.string.power_bomb_name, R.string.power_shuffle_name, R.string.power_hint_name)
    private val powerDescRes = intArrayOf(R.string.power_undo_desc, R.string.power_rotate_desc, R.string.power_bomb_desc, R.string.power_shuffle_desc, R.string.power_hint_desc)

    private var pauseBtn: UiIconButton? = null

    // ---- hint power-up ----
    private var hintMove: Triple<Int, Int, Int>? = null // (slot,row,col)
    private var hintT = 0f

    // ---- danger pulse ----
    private var fitsRemaining = 99
    private var dangerPulse = 0f

    // ---- hold-to-undo ----
    private var undoHeld = false
    private var undoHoldT = 0f
    private var undoRepT = 0f

    // ---- timed levels ----
    private var timeLeft = -1f

    // ---- multi-touch drag tracking ----
    private var dragPointerId = -1

    // ---- drag state ----
    private var dragIndex = -1
    private var dragX = 0f
    private var dragY = 0f
    private var dragOffY = 0f
    private var snapRow = -1
    private var snapCol = -1
    private var snapFits = false
    // where inside the piece bounds the finger grabbed it (0..1)
    private var grabFracX = 0.5f
    // smoothed render position + grow-from-tray scale for the lifted piece
    private var dragVisX = 0f
    private var dragVisY = 0f
    private var dragScale = 0f
    // return-flight animation when a drop is rejected
    private var retPiece: Piece? = null
    private var retX = 0f; private var retY = 0f
    private var retX0 = 0f; private var retY0 = 0f
    private var retX1 = 0f; private var retY1 = 0f
    private var retT = 0f
    private var lastSnapR = -2
    private var lastSnapC = -2
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
    private var feverBannerT = 0f
    private var perfectBannerT = 0f
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

    enum class Overlay { NONE, PAUSE, GAMEOVER, LEVEL_COMPLETE, QUIT_CONFIRM, POWERS }

    class ClearFx(val cellsWithColor: IntArray, var age: Float = 0f) // packed idx | color<<16
    {
        val life = 0.34f
    }

    override fun onEnter() {
        Audio.play("spawn")
        Audio.setMusicMode(1)
        computeLayout()
        pauseBtn = UiIconButton(D.dp(34f), hudTop - D.dp(4f), D.dp(17f), "g:pause") { pause() }
        pauseBtn?.fg = D.color(theme.textPrimary)
        for (i in 0..2) trayPop[i] = -i * 0.12f // staggered pop-in
        if (engine.timeLimitSec > 0) timeLeft = engine.timeLimitSec.toFloat()
        refreshDanger()
        if (!Save.tutorialDone) {
            scene().push(TutorialScene())
        } else if (engine.mode == Mode.CLASSIC && !Save.powersSeen) {
            Save.powersSeen = true
            overlay = Overlay.POWERS
            overlayAnim.reset()
        }
    }

    override fun onExit() {
        Audio.setMusicMode(0)
        saveRun()
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

        val powerSize = D.dp(46f)
        val gap = D.dp(10f)
        val totalW = powerSize * 5 + gap * 4
        var px = (w - totalW) / 2f
        powerY = boardRect.bottom + D.dp(10f)
        for (i in 0..4) {
            powerRects[i].set(px, powerY, px + powerSize, powerY + powerSize)
            px += powerSize + gap
        }

        trayCell = cell * 0.62f
        trayY = powerY + powerSize + D.dp(20f)

        meterRect = RectF(w / 2f - D.dp(76f), hudTop + D.dp(52f), w / 2f + D.dp(76f), hudTop + D.dp(64f))
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
        feverBannerT = (feverBannerT - dt).coerceAtLeast(0f)
        perfectBannerT = (perfectBannerT - dt).coerceAtLeast(0f)
        if (engine.feverT > 0f) {
            engine.feverT -= dt
            if (engine.feverT < 0f) engine.feverT = 0f
            host.wake()
        }

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
        updateDragVisual(dt)

        // hint ghost timer
        if (hintT > 0f) { hintT -= dt; if (hintT <= 0f) hintMove = null }
        // danger breathing
        if (fitsRemaining <= 4 && overlay == Overlay.NONE) dangerPulse += dt * 4f else dangerPulse = 0f
        // hold-to-undo: after 0.45s, repeats every 0.18s
        if (undoHeld) {
            undoHoldT += dt
            if (undoHoldT > 0.45f) {
                undoRepT += dt
                if (undoRepT >= 0.18f) {
                    undoRepT = 0f
                    if (Save.powerUps(PowerKind.UNDO) > 0 && engine.canUndo) {
                        Save.usePowerUp(PowerKind.UNDO)
                        engine.undo()
                        clearing.clear()
                        Audio.play("undo", 1.05f)
                        Haptic.soft()
                        refreshDanger()
                    } else undoHeld = false
                }
            }
        }
        // timed level countdown
        if (engine.timeLimitSec > 0 && overlay == Overlay.NONE && gameOverDelay <= 0f && !engine.gameOver && !engine.goalMet) {
            timeLeft -= dt
            if (timeLeft <= 0f) {
                timeLeft = 0f
                engine.onTimeExpired()
                afterMove()
            }
        }
    }

    private fun refreshDanger() {
        fitsRemaining = 0
        for (i in 0..2) engine.tray[i]?.let { fitsRemaining += engine.board.fitCount(it) }
    }

    /** Smooth-follow the lifted piece — it floats above the finger keeping the
     * grab point; the board ghost shows exactly where it will land. */
    private fun updateDragVisual(dt: Float) {
        val p = engine.tray.getOrNull(dragIndex)
        if (dragIndex >= 0 && p != null) {
            dragScale = (dragScale + dt * 8f).coerceAtMost(1f)
            val cur = trayCell + (cell - trayCell) * Ease.outCubic(dragScale)
            val pw = p.cols * cur
            val ph = p.rows * cur
            val tx = dragX - pw * grabFracX
            // keep the piece's bottom edge just above the fingertip
            val ty = dragY - dragOffY - ph / 2f
            val k = min(1f, dt * 30f)
            dragVisX += (tx - dragVisX) * k
            dragVisY += (ty - dragVisY) * k
        }
        // rejected drop flies back to its tray slot
        if (retPiece != null) {
            retT += dt / 0.16f
            val e = Ease.outCubic(retT.coerceIn(0f, 1f))
            retX = retX0 + (retX1 - retX0) * e
            retY = retY0 + (retY1 - retY0) * e
            if (retT >= 1f) retPiece = null
        }
    }

    override fun wantsFrame(): Boolean =
        super.wantsFrame() || clearing.isNotEmpty() || cellAnim.isNotEmpty() ||
            comboBannerT > 0 || meterFlash > 0 || boardShake > 0 ||
            dragIndex >= 0 || retPiece != null || gameOverDelay > 0 || overlayAnim.let { !it.done && overlay != Overlay.NONE } ||
            enterAnim.t < enterAnim.duration || trayPop.any { it < 1f } ||
            feverBannerT > 0 || perfectBannerT > 0 || engine.feverT > 0 ||
            hintT > 0 || dangerPulse > 0 || undoHeld ||
            (engine.timeLimitSec > 0 && overlay == Overlay.NONE && !engine.gameOver) ||
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
                for (i in 0..4) {
                    if (powerRects[i].contains(e.x, e.y)) {
                        onPowerTap(i)
                        if (powerKinds[i] == PowerKind.UNDO) { undoHeld = true; undoHoldT = 0f; undoRepT = 0f }
                        return true
                    }
                }
                if (bombArmed) {
                    val idx = boardIndexAt(e.x, e.y)
                    if (idx >= 0) { doBomb(idx); return true }
                }
                // pick up tray piece (only when no drag is active — ignore 2nd finger)
                if (dragIndex < 0) for (i in 0..2) {
                    val tr = trayRects[i] ?: continue
                    if (engine.tray[i] != null && tr.contains(e.x, e.y)) {
                        if (rotateArmed) {
                            doRotate(i)
                            return true
                        }
                        dragIndex = i
                        dragPointerId = e.getPointerId(e.actionIndex)
                        dragX = e.x
                        dragY = e.y
                        dragOffY = D.dp(80f)
                        // preserve the grab point inside the piece so it
                        // doesn't jump to center under the finger
                        val p0 = engine.tray[i]
                        val slotCx = (host.width / 3f) * i + host.width / 6f
                        val pw0 = (p0?.cols ?: 1) * trayCell
                        grabFracX = ((e.x - (slotCx - pw0 / 2f)) / pw0).coerceIn(0f, 1f)
                        dragScale = 0f
                        dragVisX = e.x - pw0 * grabFracX
                        dragVisY = e.y - dragOffY - (p0?.rows ?: 1) * trayCell / 2f
                        Audio.playVaried("pickup")
                        Haptic.tick()
                        hintMove = null; hintT = 0f
                        updateSnap()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragIndex >= 0) {
                    val pi = e.findPointerIndex(dragPointerId)
                    if (pi >= 0) { dragX = e.getX(pi); dragY = e.getY(pi) }
                    updateSnap()
                    host.wake()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // a second finger lifting mid-drag shouldn't drop the piece
                if (dragIndex >= 0 && e.getPointerId(e.actionIndex) == dragPointerId) {
                    dropDragged()
                }
            }
            MotionEvent.ACTION_UP -> {
                undoHeld = false
                if (overlay != Overlay.NONE) { dragIndex = -1; return true }
                if (dragIndex >= 0) dropDragged()
            }
            MotionEvent.ACTION_CANCEL -> { dragIndex = -1; undoHeld = false }
        }
        return true
    }

    private fun handleOverlayTap(x: Float, y: Float) {
        // buttons are hit-tested in overlayButtons
        for (b in overlayButtons) {
            if (b.contains(x, y)) { b.pressT = 1f; b.tap(); return }
        }
        // click outside quit/pause/powers dialog dismisses
        if ((overlay == Overlay.PAUSE || overlay == Overlay.POWERS) && !dialogRect.contains(x, y)) { overlay = Overlay.NONE }
    }

    /** Drop whatever is being dragged at the current snap position. */
    private fun dropDragged() {
        val i = dragIndex
        val piece = engine.tray[i]
        dragIndex = -1
        dragPointerId = -1
        if (snapFits && snapRow >= 0 && snapCol >= 0) {
            doPlace(i, snapRow, snapCol)
        } else {
            Audio.play("invalid")
            boardShake = 0.25f
            Haptic.error()
            // fly the piece back to its tray slot instead of vanishing
            if (piece != null) {
                retPiece = piece
                retX0 = dragVisX; retY0 = dragVisY
                retX = retX0; retY = retY0
                val slotCx = (host.width / 3f) * i + host.width / 6f
                retX1 = slotCx - piece.cols * trayCell / 2f
                retY1 = trayY + (host.height - trayY - D.dp(10f)) / 2f - piece.rows * trayCell / 2f
                retT = 0f
                host.wake()
            }
        }
    }

    private fun updateSnap() {
        val p = engine.tray[dragIndex] ?: return
        // piece top-left in board coords (finger keeps its grab point, offset up)
        val px = dragX - p.cols * cell * grabFracX
        val py = dragY - dragOffY - p.rows * cell / 2f
        snapCol = ((px - boardRect.left) / cell).roundToInt()
        snapRow = ((py - boardRect.top) / cell).roundToInt()
        snapFits = snapRow >= -1 && snapCol >= -1 && engine.board.fits(p, snapRow, snapCol)
        // also allow preview within tolerance: if slightly off, find nearest fit within 1 cell
        if (!snapFits) {
            for (dr in -1..1) for (dc in -1..1) {
                if (engine.board.fits(p, snapRow + dr, snapCol + dc)) {
                    snapRow += dr; snapCol += dc; snapFits = true; break
                }
            }
        }
        // light tick whenever the landing cell changes — feels tactile
        if (snapFits && (snapRow != lastSnapR || snapCol != lastSnapC)) {
            if (lastSnapR != -2) Haptic.tick()
            lastSnapR = snapRow; lastSnapC = snapCol
        }
        if (!snapFits) { lastSnapR = -2; lastSnapC = -2 }
    }

    private fun doPlace(i: Int, r: Int, cIdx: Int) {
        val piece = engine.tray[i] ?: return
        capturePlaced(piece, r, cIdx) // snapshot cells+color before engine consumes the piece
        val res = engine.place(i, r, cIdx)
        if (!res.placed) { Audio.play("invalid"); return }
        Audio.playVaried("place")
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
            if (n >= 2) Haptic.big() else Haptic.success()
            // particles at each cleared cell
            for (cc in res.clearCells) {
                val br = cc / 9; val bc = cc % 9
                particles.burst(
                    boardRect.left + (bc + 0.5f) * cell, boardRect.top + (br + 0.5f) * cell,
                    cellColor(preBoard?.get(cc) ?: 1), count = 4, speed = D.dp(180f), size = D.dp(8f),
                    life = 0.6f, gravity = D.dp(500f),
                )
            }
            // combo banner — pitch climbs with the combo level
            if (res.comboCount >= 2) {
                comboBannerN = res.comboCount
                comboBannerT = 1.1f
                Audio.play("combo", (1f + (res.comboCount - 1) * 0.08f).coerceAtMost(1.5f))
            }
            addFloat(
                boardRect.centerX(), boardRect.top + boardRect.height() * 0.4f,
                "+${res.gained}", D.color(theme.accent), D.sp(30f),
            )
            Missions.track(MissionType.LINES_TOTAL, res.lines)
            Missions.track(MissionType.COMBO_ONCE, res.comboCount)
            if (res.meterFull) {
                meterFlash = 1f
                grantNeededPowerUp()
                feverBannerT = 1.4f
                Audio.play("win", 1.2f)
            }
            if (res.gemsCollected > 0) {
                val coins = res.gemsCollected * 10
                Save.coins += coins
                addFloat(boardRect.centerX(), boardRect.top + boardRect.height() * 0.24f, "+$coins", 0xFFFFD75E.toInt(), D.sp(22f))
                Audio.play("coin")
            }
            if (res.perfectClear) {
                perfectBannerT = 1.6f
                Audio.play("clear3", 1.1f)
                Haptic.big()
                particles.burst(boardRect.centerX(), boardRect.centerY(), 0xFFFFD75E.toInt(), count = 40, speed = D.dp(320f), size = D.dp(10f), life = 0.9f, gravity = D.dp(300f))
            }
        } else {
            Audio.playVaried("place")
        }
        for (cc in lastPlacedCells) cellAnim[cc] = 0f
        Missions.track(MissionType.CELLS_TOTAL, lastPlacedCells.size)
        if (engine.trayEmpty()) {
            for (k in 0..2) trayPop[k] = -k * 0.12f // staggered spawn pop
            Audio.play("spawn")
        }
        refreshDanger()
        saveRun()
        celebrateAchievements()
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

    /** Persist a resumable classic run; cleared when the round ends. */
    private fun saveRun() {
        if (engine.mode == Mode.CLASSIC && !engine.gameOver && !engine.goalMet) {
            Save.runJson = engine.toJson()
        } else {
            Save.clearSavedRun()
        }
    }

    private fun afterMove() {
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
                    awardCoins += min(50, Save.bumpDailyStreak(dailySeed) * 5)
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
            Save.clearSavedRun()
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
        celebrateAchievements()
    }

    /** Build a shareable score card and fire a share intent. */
    private fun shareScore() {
        Audio.play("click")
        try {
            val ctx = host.context
            val S = 1080
            val bc = android.graphics.Bitmap.createBitmap(S, S, android.graphics.Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(bc)
            val th = theme
            // background
            D.gradientRect(cv, 0f, 0f, S.toFloat(), S.toFloat(), D.color(th.bgTop), D.color(th.bgBottom))
            D.glowCircle(cv, S / 2f, S * 0.34f, S * 0.6f, D.color(th.accent), 60)
            // emblem
            val esz = 150f
            var ex = S / 2f - esz * 1.5f
            for (i in 0 until 6) {
                val col = D.color(th.blockColors[i % 7])
                D.blockCell(cv, ex + i * esz * 0.72f, S * 0.16f, ex + i * esz * 0.72f + esz * 0.6f, S * 0.16f + esz * 0.6f, col, esz * 0.12f)
            }
            D.text(cv, "BLOKKU", S / 2f, S * 0.34f, 130f, Color.WHITE)
            D.text(cv, "${engine.score}", S / 2f, S * 0.52f, 200f, D.color(th.accent))
            D.labelText(cv, s(R.string.score), S / 2f, S * 0.60f, 40f, D.withAlpha(D.color(th.textPrimary), 180))
            D.text(cv, "${s(R.string.stats_lines)}: ${engine.linesCleared}   ${s(R.string.stats_max_combo)}: ×${engine.bestCombo}", S / 2f, S * 0.72f, 52f, D.color(th.textPrimary), bold = false)
            // brand footer
            D.rectStroke(cv, S * 0.3f, S * 0.84f, S * 0.7f, S * 0.84f + 4, D.withAlpha(D.color(th.accent), 200), 2f, 2f)
            val dir = java.io.File(ctx.cacheDir, "share").apply { mkdirs() }
            val f = java.io.File(dir, "blokku_score.png")
            java.io.FileOutputStream(f).use { bc.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_TEXT, "BLOKKU — ${engine.score}!")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(android.content.Intent.createChooser(intent, s(R.string.share_score)))
        } catch (ex: Exception) {
            addFloat(boardRect.centerX(), boardRect.centerY(), "✕", Color.WHITE, D.sp(20f))
        }
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
                refreshDanger()
                saveRun()
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
                if (bombArmed) addFloat(boardRect.centerX(), trayY - D.dp(6f), s(R.string.bomb_hint), Color.WHITE, D.sp(14f))
            }
            PowerKind.SHUFFLE -> {
                if (!Save.usePowerUp(kind)) { offerBuyPower(kind); return }
                Save.powerupsUsed++
                engine.shuffleTray()
                for (k in 0..2) trayPop[k] = -k * 0.12f
                Audio.play("shuffle")
                refreshDanger()
                saveRun()
                Missions.track(MissionType.USE_POWERUPS, 1)
            }
            PowerKind.HINT -> {
                if (!Save.usePowerUp(kind)) { offerBuyPower(kind); return }
                Save.powerupsUsed++
                hintMove = engine.bestMove()
                hintT = 2.4f
                Audio.play("hint")
                Haptic.soft()
                addFloat(boardRect.centerX(), trayY - D.dp(6f), s(R.string.hint_hint), Color.WHITE, D.sp(14f))
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
        creditGems(boardRect.left + (cIdx + 0.5f) * cell, boardRect.top + (r + 0.5f) * cell)
        Missions.track(MissionType.USE_POWERUPS, 1)
        afterMove()
    }

    /** Coins for gems destroyed by blast/revive. */
    private fun creditGems(x: Float, y: Float) {
        if (engine.lastGemsCollected > 0) {
            val coins = engine.lastGemsCollected * 10
            Save.coins += coins
            addFloat(x, y - D.dp(30f), "+$coins", 0xFFFFD75E.toInt(), D.sp(20f))
            Audio.play("coin")
        }
    }

    /** Meter-full grant: the power-up with the lowest stock + a coin drip. */
    private fun grantNeededPowerUp() {
        val kind = powerKinds.minBy { Save.powerUps(it) }
        Save.addPowerUp(kind, 1)
        Save.coins += 5
        Audio.play("meter")
        val label = when (kind) {
            PowerKind.UNDO -> s(R.string.power_undo)
            PowerKind.ROTATE -> s(R.string.power_rotate)
            PowerKind.BOMB -> s(R.string.power_bomb)
            PowerKind.SHUFFLE -> s(R.string.power_shuffle)
            PowerKind.HINT -> s(R.string.power_hint)
        }
        addFloat(boardRect.centerX(), meterRect.bottom + D.dp(24f), "+1 $label · +5 ${s(R.string.coins)}", D.color(theme.accent), D.sp(16f))
        coinPill?.bump()
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
        renderTimerChip(c)
        renderPowerBar(c)
        renderTray(c)
        renderReturn(c)
        renderDrag(c)
        c.restore()
        renderComboBanner(c)
        renderBigBanner(c)
        if (overlay != Overlay.NONE) renderOverlay(c)
        renderFx(c) // floats/particles on top of the dim so celebrations read
    }

    /** Countdown chip drawn over the board's top-right corner on timed levels. */
    private fun renderTimerChip(c: Canvas) {
        if (engine.timeLimitSec <= 0 || timeLeft < 0f) return
        val tl = timeLeft.toInt()
        val urgent = tl <= 10
        val pw2 = D.dp(66f); val ph2 = D.dp(26f)
        val px = boardRect.right - D.dp(10f) - pw2; val py = boardRect.top - ph2 / 2f
        val pulse = if (urgent) 0.7f + 0.3f * kotlin.math.sin(host.globalTime * 10f) else 1f
        val tcol = if (urgent) 0xFFFF5D73.toInt() else D.color(theme.accent)
        D.rect(c, px, py + D.dp(2f), px + pw2, py + ph2 + D.dp(2f), D.withAlpha(Color.BLACK, 90), ph2 / 2)
        D.gradientRect(c, px, py, px + pw2, py + ph2, D.withAlpha(tcol, (235 * pulse).toInt()), D.withAlpha(D.darken(tcol, 0.25f), (255 * pulse).toInt()), ph2 / 2)
        Glyph.draw(c, "clock", RectF(px + D.dp(7f), py + ph2 * 0.22f, px + D.dp(7f) + ph2 * 0.56f, py + ph2 * 0.78f), Color.WHITE)
        D.text(c, "$tl", px + D.dp(16f) + pw2 / 2f, py + ph2 * 0.70f, D.sp(13f), Color.WHITE)
    }

    /** On the game-over overlay, preview which cells the revive would clear. */
    private fun renderRevivePreview(c: Canvas) {
        if (overlay != Overlay.GAMEOVER || revived) return
        val pulse = 0.55f + 0.45f * kotlin.math.sin(host.globalTime * 6f)
        for (cc in engine.peekReviveCells()) {
            val cr = cc / 9; val cx2 = cc % 9
            val l = boardRect.left + cx2 * cell
            val t = boardRect.top + cr * cell
            D.rectStroke(c, l + 1f, t + 1f, l + cell - 1f, t + cell - 1f, D.withAlpha(0xFF62D97B.toInt(), (200 * pulse).toInt()), 2f, cell * 0.18f)
        }
    }

    private fun renderHud(c: Canvas) {
        val w = host.width.toFloat()
        pauseBtn?.render(c)
        // score block: small caps label over big number
        D.labelText(c, s(R.string.score), w / 2f, hudTop - D.sp(4f), D.sp(10f), D.withAlpha(D.color(theme.textPrimary), 160))
        D.text(c, "${engine.score}", w / 2f, hudTop + D.sp(22f), D.sp(30f), D.color(theme.textPrimary))
        val sub = when (engine.mode) {
            Mode.CLASSIC -> "${s(R.string.best)} ${max(Save.bestClassic, engine.score)}"
            Mode.LEVEL -> if (engine.timeLimitSec > 0)
                "${s(R.string.level)} ${levelIndex + 1} • ${s(R.string.timed_badge)} • ${goalLabel()}"
            else "${s(R.string.level)} ${levelIndex + 1} • ${goalLabel()} • ${s(R.string.moves_left)} ${engine.movesLeft}"
            Mode.DAILY -> "${s(R.string.menu_daily)} • ${goalLabel()} • ${s(R.string.moves_left)} ${engine.movesLeft}"
        }
        D.text(c, sub, w / 2f, hudTop + D.sp(22f) + D.sp(15f), D.sp(11f), D.withAlpha(D.color(theme.textPrimary), 190), bold = false)
        if (coinPill == null) coinPill = com.fareza.blokku.ui.CoinPill(w - D.dp(106f), hudTop - D.dp(4f)) { onCoinsTap() }
        coinPill?.render(c)
        // meter — taller pill with gradient fill
        val mr = meterRect
        D.rect(c, mr.left, mr.top + D.dp(2f), mr.right, mr.bottom + D.dp(3f), D.withAlpha(Color.BLACK, 80), mr.height() / 2)
        D.rect(c, mr.left, mr.top, mr.right, mr.bottom, D.withAlpha(Color.BLACK, 100), mr.height() / 2)
        D.rectStroke(c, mr.left + 0.6f, mr.top + 0.6f, mr.right - 0.6f, mr.bottom - 0.6f, D.withAlpha(Color.WHITE, 30), 1f, mr.height() / 2)
        val fill = engine.meter / 100f
        if (fill > 0) {
            val fr = mr.left + 3 + (mr.width() - 6) * fill
            val c1 = if (meterFlash > 0) Color.WHITE else D.lighten(D.color(theme.accent), 0.15f)
            val c2 = if (meterFlash > 0) Color.WHITE else D.darken(D.color(theme.accent), 0.12f)
            D.gradientRect(c, mr.left + 3, mr.top + 3, fr, mr.bottom - 3, c1, c2, mr.height() / 2)
        }
        if (meterFlash > 0) D.glowCircle(c, mr.centerX(), mr.centerY(), D.dp(60f) * meterFlash, D.color(theme.accent), (120 * meterFlash).toInt())
        // Fever Rush active — meter pill becomes a ×2 countdown chip
        if (engine.feverT > 0f) {
            val fp = 0.7f + 0.3f * kotlin.math.sin(host.globalTime * 10f)
            val fcol = 0xFFFFB300.toInt()
            D.gradientRect(c, mr.left, mr.top, mr.right, mr.bottom, D.withAlpha(fcol, (235 * fp).toInt()), D.withAlpha(D.darken(fcol, 0.2f), (255 * fp).toInt()), mr.height() / 2)
            D.text(c, "×2 ${engine.feverT.toInt() + 1}", mr.centerX(), mr.centerY() + D.sp(4.5f), D.sp(11f), Color.WHITE)
        }
    }

    /** Small spark diamond marking a gem cell. */
    private fun drawGem(c: Canvas, cx: Float, cy: Float, r: Float) {
        val tw = 0.75f + 0.25f * kotlin.math.sin(host.globalTime * 6f)
        val col = 0xFF7DF9FF.toInt()
        val p = android.graphics.Path()
        p.moveTo(cx, cy - r); p.lineTo(cx + r, cy); p.lineTo(cx, cy + r); p.lineTo(cx - r, cy); p.close()
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = D.withAlpha(col, (235 * tw).toInt())
        c.drawPath(p, paint)
        paint.color = D.withAlpha(Color.WHITE, (200 * tw).toInt())
        val ir = r * 0.4f
        val ip = android.graphics.Path()
        ip.moveTo(cx, cy - ir); ip.lineTo(cx + ir, cy); ip.lineTo(cx, cy + ir); ip.lineTo(cx - ir, cy); ip.close()
        c.drawPath(ip, paint)
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

        // board panel — elevated card
        val pad = D.dp(7f)
        D.card(c, boardRect.left - pad, boardRect.top - pad, boardRect.right + pad, boardRect.bottom + pad, D.color(theme.boardBg), D.dp(18f))

        // empty cells — inset slot look
        for (r in 0 until 9) {
            for (col in 0 until 9) {
                val l = boardRect.left + col * cell + cell * 0.06f
                val t = boardRect.top + r * cell + cell * 0.06f
                D.insetCell(c, l, t, l + cell * 0.88f, t + cell * 0.88f, D.color(theme.cellEmpty), cell * 0.18f)
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

        // ghost preview while dragging — pulses softly so the landing spot reads
        val dragPiece = if (dragIndex >= 0) engine.tray[dragIndex] else null
        var clearPreview: Board.ClearResult? = null
        if (dragPiece != null && snapFits && snapRow >= 0 && snapCol >= 0) {
            clearPreview = engine.board.findClears(dragPiece, snapRow, snapCol)
            val pulse = 0.75f + 0.25f * kotlin.math.sin(host.globalTime * 8f)
            // would-be cleared cells highlight
            for (cc in clearPreview.clearCells) {
                val cr = cc / 9; val ccx = cc % 9
                val l = boardRect.left + ccx * cell + cell * 0.05f
                val t = boardRect.top + cr * cell + cell * 0.05f
                D.rect(c, l, t, l + cell * 0.9f, t + cell * 0.9f, D.withAlpha(D.color(theme.accent), (150 * pulse).toInt()), cell * 0.18f)
            }
            // ghost cells
            val ga = (160 + 60 * pulse).toInt()
            for (pc in dragPiece.cells) {
                val gr = snapRow + (pc shr 4); val gc = snapCol + (pc and 15)
                if (gr < 0 || gr > 8 || gc < 0 || gc > 8) continue
                val l = boardRect.left + gc * cell + cell * 0.07f
                val t = boardRect.top + gr * cell + cell * 0.07f
                D.blockCell(c, l, t, l + cell * 0.86f, t + cell * 0.86f, cellColor(dragPiece.colorIndex + 1), cell * 0.2f, alpha = ga)
                D.rectStroke(c, l + 1f, t + 1f, l + cell * 0.86f - 1f, t + cell * 0.86f - 1f, D.withAlpha(Color.WHITE, (140 * pulse).toInt()), 1.4f, cell * 0.2f)
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
                if (engine.board.gems[idx]) drawGem(c, l + cell * 0.43f, t + cell * 0.43f, cell * 0.26f)
            }
        }

        // clear sweep shimmer — a bright bar slides along each cleared row/col
        for (fx in clearing) {
            val k = (fx.age / fx.life).coerceIn(0f, 1f)
            val sa = (200 * (1f - k)).toInt()
            for (cc in fx.cellsWithColor) {
                val idx = cc and 0xFFFF
                val cr = idx / 9; val ccx = idx % 9
                val l = boardRect.left + ccx * cell
                val t = boardRect.top + cr * cell
                val shift = k * cell
                D.rect(c, l + shift * 0.3f, t, l + cell + shift * 0.3f, t + cell, D.withAlpha(Color.WHITE, sa / 3), cell * 0.18f)
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
        // fever tint — warm glow pulses over the board while Fever Rush runs
        if (engine.feverT > 0f) {
            val fp = 0.5f + 0.5f * kotlin.math.sin(host.globalTime * 9f)
            D.rectStroke(c, boardRect.left - D.dp(7f), boardRect.top - D.dp(7f), boardRect.right + D.dp(7f), boardRect.bottom + D.dp(7f), D.withAlpha(0xFFFFB300.toInt(), (140 * fp).toInt()), D.dp(3f), D.dp(20f))
        }
        // danger vignette — board edge breathes red when almost nothing fits
        if (fitsRemaining <= 4 && fitsRemaining >= 0 && overlay == Overlay.NONE) {
            val dp2 = 0.5f + 0.5f * kotlin.math.sin(dangerPulse)
            D.rectStroke(c, boardRect.left - D.dp(7f), boardRect.top - D.dp(7f), boardRect.right + D.dp(7f), boardRect.bottom + D.dp(7f), D.withAlpha(0xFFFF5D73.toInt(), (90 * dp2).toInt()), D.dp(3f), D.dp(20f))
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
        for (i in 0..4) {
            val r = powerRects[i]
            val kind = powerKinds[i]
            val tint = powerTints[i]
            val count = Save.powerUps(kind)
            val armed = (kind == PowerKind.BOMB && bombArmed) || (kind == PowerKind.ROTATE && rotateArmed)
            val canUndo = kind != PowerKind.UNDO || engine.canUndo
            val usable = count > 0 && canUndo
            val flash = (kind == PowerKind.HINT && hintT > 0f)
            val alpha = if (usable) 255 else 110
            // card
            if (armed || flash) {
                D.rect(c, r.left, r.top + D.dp(3f), r.right, r.bottom + D.dp(3f), D.withAlpha(Color.BLACK, 80), D.dp(15f))
                D.gradientRect(c, r.left, r.top, r.right, r.bottom, D.lighten(tint, 0.12f), D.darken(tint, 0.12f), D.dp(15f))
                D.rectStroke(c, r.left + 0.8f, r.top + 0.8f, r.right - 0.8f, r.bottom - 0.8f, D.withAlpha(Color.WHITE, 200), D.dp(1.5f), D.dp(15f))
            } else {
                D.card(c, r.left, r.top, r.right, r.bottom, D.color(theme.boardBg), D.dp(15f), elevated = usable)
                D.rectStroke(c, r.left + 0.8f, r.top + 0.8f, r.right - 0.8f, r.bottom - 0.8f, D.withAlpha(tint, if (usable) 90 else 40), 1.2f, D.dp(15f))
            }
            // icon on tinted circle chip
            val ic = r.centerX(); val icy = r.centerY() - D.dp(2f)
            val chipR = D.dp(14.5f)
            D.circle(c, ic, icy, chipR, D.withAlpha(tint, if (usable) 46 else 24))
            Glyph.draw(c, powerGlyphs[i], RectF(ic - chipR * 0.72f, icy - chipR * 0.72f, ic + chipR * 0.72f, icy + chipR * 0.72f), D.withAlpha(if (armed) Color.WHITE else tint, alpha))
            // count chip at bottom-right corner
            val badgeCol = if (count > 0) tint else 0xFF777788.toInt()
            D.circle(c, r.right - D.dp(6f), r.top + D.dp(6f), D.dp(10f), D.withAlpha(Color.BLACK, 90))
            D.circle(c, r.right - D.dp(6f), r.top + D.dp(6f), D.dp(8.5f), badgeCol)
            D.text(c, "$count", r.right - D.dp(6f), r.top + D.dp(6f) + D.sp(9f) * 0.36f, D.sp(9f), Color.WHITE)
            // name label under the button
            D.labelText(c, s(powerNameRes[i]), r.centerX(), r.bottom + D.dp(10.5f), D.sp(8f), D.withAlpha(D.color(theme.textPrimary), if (usable) 200 else 110), spacing = 0.1f)
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
            // slot card — always visible for visual rhythm
            val slotRect = RectF(slotW * i + D.dp(7f), trayTop + D.dp(4f), slotW * (i + 1) - D.dp(7f), trayTop + trayH)
            trayRects[i] = slotRect
            val sr = RectF(slotRect.left, slotRect.top + slotRect.height() * 0.14f, slotRect.right, slotRect.bottom - slotRect.height() * 0.1f)
            if (p == null) {
                D.insetCell(c, sr.left, sr.top, sr.right, sr.bottom, D.withAlpha(D.color(theme.cellEmpty), 110), D.dp(16f))
                continue
            }
            D.card(c, sr.left, sr.top, sr.right, sr.bottom, D.color(theme.boardBg), D.dp(16f), elevated = true)
            val fitsAny = engine.board.anyFit(p)
            val popK = Ease.outBack(trayPop[i].coerceIn(0f, 1f))
            val pieceW = p.cols * trayCell * popK
            val pieceH = p.rows * trayCell * popK
            val alpha = if (fitsAny) 255 else 110
            if (trayPop[i] > 0f) for (pc in p.cells) {
                val pr = pc shr 4; val pcc = pc and 15
                val l = cx - pieceW / 2 + pcc * trayCell * popK + trayCell * 0.05f * popK
                val t = cy - pieceH / 2 + pr * trayCell * popK + trayCell * 0.05f * popK
                D.blockCell(c, l, t, l + trayCell * 0.9f * popK, t + trayCell * 0.9f * popK, cellColor(p.colorIndex + 1), trayCell * 0.2f, alpha)
                if (pc == engine.trayGem[i]) drawGem(c, l + trayCell * 0.45f * popK, t + trayCell * 0.45f * popK, trayCell * 0.24f)
            }
            if (rotateArmed && fitsAny) {
                D.rectStroke(c, slotRect.left, slotRect.top, slotRect.right, slotRect.bottom, D.color(theme.accent), D.dp(2f), D.dp(14f))
            }
        }
        // hint ghost — pulsing suggested placement (slot + board cell)
        val hm = hintMove
        if (hm != null && hintT > 0f) {
            val (slot, hr, hc) = hm
            val hp = engine.tray[slot]
            if (hp != null) {
                val pulse = 0.55f + 0.45f * kotlin.math.sin(host.globalTime * 10f)
                for (pc in hp.cells) {
                    val gr = hr + (pc shr 4); val gc = hc + (pc and 15)
                    val l = boardRect.left + gc * cell + cell * 0.08f
                    val t = boardRect.top + gr * cell + cell * 0.08f
                    D.blockCell(c, l, t, l + cell * 0.84f, t + cell * 0.84f, D.color(theme.accent), cell * 0.2f, (120 * pulse).toInt())
                    D.rectStroke(c, l, t, l + cell * 0.84f, t + cell * 0.84f, D.withAlpha(D.color(theme.accent), (230 * pulse).toInt()), 1.6f, cell * 0.2f)
                }
                // glow ring on the tray slot that owns the suggested piece
                trayRects[slot]?.let { sr ->
                    D.rectStroke(c, sr.left + D.dp(2f), sr.top + D.dp(2f), sr.right - D.dp(2f), sr.bottom - D.dp(2f), D.withAlpha(0xFFFFE066.toInt(), (220 * pulse).toInt()), D.dp(2.5f), D.dp(14f))
                }
            }
        }
    }

    private fun renderDrag(c: Canvas) {
        if (dragIndex < 0) return
        val p = engine.tray[dragIndex] ?: return
        val cur = trayCell + (cell - trayCell) * Ease.outCubic(dragScale)
        val l0 = dragVisX
        val t0 = dragVisY
        val docked = snapFits && snapRow >= 0 && snapCol >= 0
        val liftShadow = if (docked) D.dp(2f) else D.dp(7f)
        // lift shadow
        for (pc in p.cells) {
            val pr = pc shr 4; val pcx = pc and 15
            val l = l0 + pcx * cur
            val t = t0 + pr * cur
            D.blockCell(c, l + cur * 0.05f, t + cur * 0.05f + liftShadow, l + cur * 0.95f, t + cur * 0.95f + liftShadow, Color.BLACK, cur * 0.2f, if (docked) 30 else 60)
        }
        for (pc in p.cells) {
            val pr = pc shr 4; val pcx = pc and 15
            val l = l0 + pcx * cur + cur * 0.05f
            val t = t0 + pr * cur + cur * 0.05f
            D.blockCell(c, l, t, l + cur * 0.9f, t + cur * 0.9f, cellColor(p.colorIndex + 1), cur * 0.2f)
            if (pc == engine.trayGem[dragIndex]) drawGem(c, l + cur * 0.45f, t + cur * 0.45f, cur * 0.24f)
        }
    }

    /** Piece flying back to its slot after an invalid drop. */
    private fun renderReturn(c: Canvas) {
        val p = retPiece ?: return
        val k = retT.coerceIn(0f, 1f)
        val cur = cell + (trayCell - cell) * Ease.outCubic(k)
        for (pc in p.cells) {
            val pr = pc shr 4; val pcx = pc and 15
            val l = retX + pcx * cur + cur * 0.05f
            val t = retY + pr * cur + cur * 0.05f
            D.blockCell(c, l, t, l + cur * 0.9f, t + cur * 0.9f, cellColor(p.colorIndex + 1), cur * 0.2f, alpha = (230 * (1f - k * 0.3f)).toInt())
        }
    }

    /** Fever Rush + Perfect Clear celebrations — big centered banners. */
    private fun renderBigBanner(c: Canvas) {
        val w = host.width.toFloat()
        if (feverBannerT > 0f) {
            val appear = ((1.4f - feverBannerT) / 0.25f).coerceIn(0f, 1f)
            val alpha = (feverBannerT / 0.4f).coerceIn(0f, 1f)
            c.save()
            c.scale(Ease.outBack(appear), Ease.outBack(appear), w / 2f, boardRect.top + boardRect.height() * 0.3f)
            D.text(c, s(R.string.fever_banner), w / 2f, boardRect.top + boardRect.height() * 0.3f, D.sp(30f), 0xFFFFB300.toInt(), alpha = (255 * alpha).toInt())
            c.restore()
        }
        if (perfectBannerT > 0f) {
            val appear = ((1.6f - perfectBannerT) / 0.3f).coerceIn(0f, 1f)
            val alpha = (perfectBannerT / 0.45f).coerceIn(0f, 1f)
            c.save()
            c.scale(Ease.outBack(appear), Ease.outBack(appear), w / 2f, boardRect.top + boardRect.height() * 0.56f)
            D.text(c, s(R.string.perfect_banner), w / 2f, boardRect.top + boardRect.height() * 0.56f, D.sp(30f), 0xFFFFD75E.toInt(), alpha = (255 * alpha).toInt())
            c.restore()
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
        renderRevivePreview(c)
        val dw = min(w - D.dp(40f), D.dp(340f))
        val dh = when (overlay) {
            Overlay.GAMEOVER -> D.dp(368f)
            Overlay.LEVEL_COMPLETE -> D.dp(320f)
            Overlay.POWERS -> D.dp(396f)
            else -> D.dp(240f)
        }
        val dl = (w - dw) / 2f
        val dt = (h - dh) / 2f
        dialogRect.set(dl, dt, dl + dw, dt + dh)
        c.save()
        c.scale(overlayAnim.v.coerceIn(0.01f, 1.2f), overlayAnim.v.coerceIn(0.01f, 1.2f), dialogRect.centerX(), dialogRect.centerY())
        D.rect(c, dialogRect.left, dialogRect.top + D.dp(8f), dialogRect.right, dialogRect.bottom + D.dp(9f), D.withAlpha(Color.BLACK, 110), D.dp(24f))
        D.gradientRect(c, dialogRect.left, dialogRect.top, dialogRect.right, dialogRect.bottom, D.lighten(D.color(theme.boardBg), 0.16f), D.darken(D.color(theme.boardBg), 0.04f), D.dp(24f))
        D.rectStroke(c, dialogRect.left + 0.8f, dialogRect.top + 0.8f, dialogRect.right - 0.8f, dialogRect.bottom - 0.8f, D.withAlpha(Color.WHITE, 40), 1.4f, D.dp(24f))
        c.restore()

        overlayButtons.clear()
        when (overlay) {
            Overlay.PAUSE -> renderPauseOverlay(c)
            Overlay.GAMEOVER -> renderGameOverOverlay(c)
            Overlay.LEVEL_COMPLETE -> renderLevelCompleteOverlay(c)
            Overlay.QUIT_CONFIRM -> renderQuitOverlay(c)
            Overlay.POWERS -> renderPowersOverlay(c)
            Overlay.NONE -> {}
        }
        for (b in overlayButtons) b.render(c)
    }

    private fun overlayBtn(l: Float, t: Float, r: Float, b: Float, label: String, bg: Int, textScale: Float = 1f, onTap: () -> Unit): UiButton {
        val btn = UiButton(RectF(l, t, r, b), label = label, bg = bg, fg = Color.WHITE, onTap = onTap, textScale = textScale)
        btn.appear.t = btn.appear.duration + btn.appear.delay // fully visible
        overlayButtons.add(btn)
        return btn
    }

    private fun overlayTitle(c: Canvas, title: String, y: Float, size: Float = D.sp(23f)) {
        val cx = dialogRect.centerX()
        D.text(c, title, cx, y, size, D.color(theme.textPrimary))
        val tw = D.textWidth(title, size)
        D.rect(c, cx - tw / 2f + D.dp(3f), y + D.sp(7f), cx + tw / 2f - D.dp(3f), y + D.sp(7f) + D.dp(2.5f), D.color(theme.accent), D.dp(1.5f))
    }

    private fun renderPauseOverlay(c: Canvas) {
        overlayTitle(c, s(R.string.paused), dialogRect.top + D.dp(44f))
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
        overlayBtn(bx, by, bx + bw * 0.62f, by + D.dp(50f), s(R.string.quit), D.lighten(D.color(theme.boardBg), 0.12f)) {
            overlay = Overlay.QUIT_CONFIRM; overlayAnim.reset()
        }
        val qw = D.dp(50f)
        overlayBtn(bx + bw - qw, by, bx + bw, by + D.dp(50f), "?", D.lighten(D.color(theme.boardBg), 0.2f)) {
            overlay = Overlay.POWERS; overlayAnim.reset(); Audio.play("click")
        }
    }

    private fun renderPowersOverlay(c: Canvas) {
        overlayTitle(c, s(R.string.powers_title), dialogRect.top + D.dp(40f), D.sp(20f))
        var ry = dialogRect.top + D.dp(62f)
        for (i in 0..4) {
            val tint = powerTints[i]
            val cx = dialogRect.left + D.dp(40f)
            val chipR = D.dp(15f)
            D.circle(c, cx, ry + chipR, chipR, D.withAlpha(tint, 46))
            Glyph.draw(c, powerGlyphs[i], RectF(cx - chipR * 0.66f, ry + chipR - chipR * 0.66f, cx + chipR * 0.66f, ry + chipR + chipR * 0.66f), tint)
            D.labelText(c, s(powerNameRes[i]), cx + chipR + D.dp(10f), ry + D.sp(11f), D.sp(10.5f), tint, align = Paint.Align.LEFT)
            D.text(c, s(powerDescRes[i]), cx + chipR + D.dp(10f), ry + D.sp(11f) + D.sp(13f), D.sp(11.5f), D.withAlpha(D.color(theme.textPrimary), 200), align = Paint.Align.LEFT, bold = false)
            ry += D.dp(44f)
        }
        var hy = ry + D.dp(4f)
        for (line in s(R.string.powers_hint).split('\n')) {
            D.text(c, line, dialogRect.centerX(), hy, D.sp(9.8f), D.withAlpha(D.color(theme.textPrimary), 150), bold = false)
            hy += D.dp(15f)
        }
        overlayBtn(dialogRect.left + D.dp(24f), dialogRect.bottom - D.dp(56f), dialogRect.right - D.dp(24f), dialogRect.bottom - D.dp(12f), s(R.string.got_it), D.color(theme.accent)) {
            overlay = Overlay.NONE; Audio.play("click")
        }
    }

    private fun renderQuitOverlay(c: Canvas) {
        overlayTitle(c, s(R.string.quit_title), dialogRect.top + D.dp(42f), D.sp(20f))
        D.text(c, s(if (engine.mode == Mode.CLASSIC) R.string.quit_confirm else R.string.quit_confirm_level), dialogRect.centerX(), dialogRect.top + D.dp(78f), D.sp(13f), D.withAlpha(D.color(theme.textPrimary), 190), bold = false)
        val bw = (dialogRect.width() - D.dp(60f)) / 2f
        val by = dialogRect.bottom - D.dp(70f)
        overlayBtn(dialogRect.left + D.dp(24f), by, dialogRect.left + D.dp(24f) + bw, by + D.dp(48f), s(R.string.no), D.lighten(D.color(theme.boardBg), 0.12f)) {
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
        overlayTitle(c, s(R.string.game_over), dialogRect.top + D.dp(36f), D.sp(22f))
        val isBest = engine.mode == Mode.CLASSIC && engine.score >= Save.bestClassic && engine.score > 0
        if (isBest) {
            // little crown pop over the score
            val tw = D.textWidth(s(R.string.new_best), D.sp(14f)) + D.dp(26f)
            D.rect(c, cx - tw / 2f, dialogRect.top + D.dp(50f), cx + tw / 2f, dialogRect.top + D.dp(50f) + D.dp(20f), D.withAlpha(0xFFFFD166.toInt(), 40), D.dp(10f))
            D.rectStroke(c, cx - tw / 2f + 0.6f, dialogRect.top + D.dp(50f) + 0.6f, cx + tw / 2f - 0.6f, dialogRect.top + D.dp(70f) - 0.6f, D.withAlpha(0xFFFFD166.toInt(), 140), 1f, D.dp(10f))
            D.text(c, s(R.string.new_best), cx, dialogRect.top + D.dp(65f), D.sp(13f), 0xFFFFD166.toInt())
        }
        D.text(c, "${engine.score}", cx, dialogRect.top + D.dp(104f), D.sp(42f), D.color(theme.textPrimary))
        // stat row: lines / combo / best
        val sy = dialogRect.top + D.dp(136f)
        val stats = arrayOf(
            Triple("stats", s(R.string.stats_lines), "${engine.linesCleared}"),
            Triple("themes", s(R.string.stats_max_combo), "×${engine.bestCombo}"),
            Triple("star", s(R.string.best), "${max(Save.bestClassic, engine.score)}"),
        )
        val sw2 = dialogRect.width() / 3f
        for (i in stats.indices) {
            val (g, lab, v) = stats[i]
            val sx = dialogRect.left + sw2 * i + sw2 / 2f
            Glyph.draw(c, g, RectF(sx - D.dp(7f), sy - D.dp(7f), sx + D.dp(7f), sy + D.dp(7f)), D.color(theme.accent))
            D.text(c, v, sx, sy + D.sp(22f), D.sp(15f), D.color(theme.textPrimary))
            D.labelText(c, lab, sx, sy + D.sp(36f), D.sp(7.5f), D.withAlpha(D.color(theme.textPrimary), 130))
        }
        val bw = dialogRect.width() - D.dp(48f)
        val bx = dialogRect.left + D.dp(24f)
        var by = dialogRect.top + D.dp(196f)
        if (!revived) {
            val label = if (!Save.adsRemoved && Ads.rewardedReady) s(R.string.watch_ad_continue) else "${s(R.string.continue_game)} — 50"
            val rb = overlayBtn(bx, by, bx + bw, by + D.dp(46f), label, 0xFF62D97B.toInt()) {
                revive()
            }
            rb.icon = "g:coin"
            by += D.dp(54f)
        }
        val half = (bw - D.dp(10f)) / 2f
        val shareB = overlayBtn(bx, by, bx + half, by + D.dp(44f), s(R.string.share_score), D.lighten(D.color(theme.boardBg), 0.12f), textScale = 0.85f) {
            shareScore()
        }
        shareB.icon = "g:share"
        overlayBtn(bx + half + D.dp(10f), by, bx + bw, by + D.dp(44f), s(R.string.restart), D.color(theme.accent), textScale = 0.9f) {
            restart(); Audio.play("click")
        }
        by += D.dp(52f)
        overlayBtn(bx, by, bx + bw, by + D.dp(44f), s(R.string.quit), D.lighten(D.color(theme.boardBg), 0.12f), textScale = 0.9f) {
            commitStats(); scene().pop(); Ads.maybeInterstitial(host.context)
        }
    }

    private fun renderLevelCompleteOverlay(c: Canvas) {
        val cx = dialogRect.centerX()
        val title = if (engine.mode == Mode.DAILY) s(R.string.daily_complete) else s(R.string.level_complete)
        overlayTitle(c, title, dialogRect.top + D.dp(42f), D.sp(21f))
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
        overlayBtn(bx, by, bx + bw, by + D.dp(50f), s(R.string.menu_play), D.lighten(D.color(theme.boardBg), 0.12f)) {
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
        Save.clearSavedRun()
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
        creditGems(boardRect.centerX(), boardRect.centerY())
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

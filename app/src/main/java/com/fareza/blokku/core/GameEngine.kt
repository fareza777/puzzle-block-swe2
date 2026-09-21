package com.fareza.blokku.core

import kotlin.random.Random

enum class GoalType { NONE, SCORE, LINES, CELLS, STONES }

class Goal(val type: GoalType, var target: Int)

enum class Mode { CLASSIC, LEVEL, DAILY, ZEN, RUSH, PUZZLE, GRAVITY, AVALANCHE, MERGE, BOSS, EXPEDITION, GAMBIT, VERSUS, MOSAIC }

/** A short in-run objective (classic/zen): e.g. clear 2 lines within 4 placements. */
class Contract(
    val type: Int,        // 0 lines-in-moves, 1 reach-combo, 2 detonate-bomb, 3 snug-fits
    val target: Int,
    var prog: Int,
    var movesLeft: Int,
    val reward: Int,
)

data class LevelDef(
    val index: Int,
    val goal: Goal,
    val moveLimit: Int,
    val seed: Long,
    val timedSec: Int = 0,   // >0: countdown instead of a move limit
    val presetFill: Float = 0f, // >0: board starts pre-seeded (lower rows)
)

class PlaceResult(
    val placed: Boolean,
    val clearCells: IntArray = IntArray(0),
    val lines: Int = 0,
    val gained: Int = 0,
    val comboCount: Int = 0,
    val meterFull: Boolean = false,
    val gemsCollected: Int = 0,
    val perfectClear: Boolean = false,
    /** Extra cells removed by bomb detonations (subset of clearCells' neighbors). */
    val boomCells: IntArray = IntArray(0),
    val bombsDetonated: Int = 0,
    /** Lines cleared that were a single color (MONO bonus). */
    val monoLines: Int = 0,
    /** Piece landed snug — every cell touched a filled neighbour or the rim. */
    val snug: Boolean = false,
    /** A ×3 cell was inside a cleared line — score for the clear was tripled. */
    val multHit: Boolean = false,
)

/**
 * Pure game rules — no Android deps so it runs in unit tests.
 * 9x9 board, 3-piece tray, row+column clears, combo/streak scoring,
 * charge meter granting a power-up, undo snapshots, move-limited goals.
 */
class GameEngine(
    val board: Board = Board(9),
    private val rng: Random,
    var mode: Mode = Mode.CLASSIC,
    var goal: Goal = Goal(GoalType.NONE, 0),
    var movesLeft: Int = -1, // -1 = unlimited
    var colorCount: Int = 6,
    var timeLimitSec: Int = 0, // timed levels: >0 counts down in the UI layer
    var bigBias: Float = 0f,   // extra weight for chunky pieces (difficulty ramp)
    var gemChance: Float = 0.2f, // daily modifier can boost this (Gem Rush)
    var dailyModifier: Int = 0,  // 0 none · 1 blocked floor · 2 gem rush
) {

    val tray = arrayOfNulls<Piece>(3)
    var score = 0
        private set
    var combo = 0
        private set
    var streak = 0
        private set
    var bestCombo = 0
        private set
    var linesCleared = 0
        private set
    var cellsPlaced = 0
        private set
    var meter = 0
        private set
    var gameOver = false
        private set
    var goalMet = false
        private set
    var goalFailed = false
        private set

    /** Seconds of Fever Rush left (score ×2). Ticked down by the UI layer. */
    var feverT = 0f
    /** Gem embedded in tray slot i — packed piece-local cell (r*16+c), -1 = none. */
    val trayGem = intArrayOf(-1, -1, -1)
    /** Bomb embedded in tray slot i — packed piece-local cell (r*16+c), -1 = none. */
    val trayBomb = intArrayOf(-1, -1, -1)
    /** ×3 cell embedded in tray slot i — same packing. */
    val trayMult = intArrayOf(-1, -1, -1)
    /** Merge mode: numbered cell embedded in tray slot i (value always starts 2). */
    val trayNum = intArrayOf(-1, -1, -1)

    // ---- next-tray preview ----
    /** The three pieces that will refill the tray when it empties. */
    val nextTray = arrayOfNulls<Piece>(3)
    val nextGem = intArrayOf(-1, -1, -1)
    val nextBomb = intArrayOf(-1, -1, -1)
    val nextMult = intArrayOf(-1, -1, -1)
    val nextNum = intArrayOf(-1, -1, -1)

    // ---- avalanche (rising floor) ----
    /** Placements left before the next junk row pushes up. */
    var avalancheIn = 0
        private set
    var avalancheCount = 0
        private set
    /** True for a frame after a junk row arrived — UI shakes/flashes. */
    var avalancheJustHit = false

    // ---- boss (multi-phase stone pattern) ----
    var bossPhase = 0      // 1-indexed while fighting
        private set
    var bossPhases = 0
        private set
    var bossPhaseJustAdvanced = false

    // ---- expedition (roguelike perk draft) ----
    /** Milestone at which the next perk offer opens. */
    var expeditionNextAt = 1400
    var expeditionOffer = false
        private set
    val expeditionPerks = HashSet<Int>()
    // perk effects (see applyPerk for ids)
    var perkFeverBonus = 0f; var perkGracePlus = 0; var perkColor = -1
    var perkGemBoost = 0f; var perkMultBoost = 0f; var perkBombBonus = 0
    var perkScoreBoost = 0f; var perkFreeRevive = false

    // ---- gambit (tray shop) ----
    var gambitOpen = false
        private set
    val gambitShop = arrayOfNulls<Piece>(5)
    val gambitCost = IntArray(5)
    var gambitPicks = 0
        private set

    // ---- ghost rivals (pace curve of past top runs) ----
    /** Score after each placement — recorded so future runs can race it. */
    val scoreCurve = ArrayList<Int>()

    // ---- gravity ----
    /** Cascade depth of the last place() — 1 = normal clear, 2+ = gravity chain. */
    var lastCascade = 0
        private set

    // ---- hold slot ----
    var holdPiece: Piece? = null
        private set
    var holdGem = -1; var holdBomb = -1; var holdMult = -1
    /** Tetris rule: one hold per placement. */
    var holdLocked = false
        private set

    // ---- combo grace ----
    /** Free non-clearing placements left before the combo drops. */
    var comboGrace = 0
        private set

    // ---- contracts (classic/zen micro-objectives) ----
    var contract: Contract? = null
        private set
    /** Score at which the next contract is offered. */
    var nextContractAt = 400
        private set
    /** Set when the active contract just completed — UI pays out + toasts. */
    var contractJustDone: Contract? = null
    var contractJustFailed = false
    /** Gems collected by the last destructive op (blast/revive), for coin award. */
    var lastGemsCollected = 0
        private set
    /** Gems collected by bomb blasts in the current place() call. */
    private var boomGems = 0

    private val undoStack = ArrayDeque<Snapshot>()

    init {
        refillTray()
        refillNext()
    }

    class Snapshot(
        val cells: IntArray,
        val tray: Array<Piece?>,
        val score: Int,
        val combo: Int,
        val streak: Int,
        val bestCombo: Int,
        val lines: Int,
        val cellsPlaced: Int,
        val meter: Int,
        val movesLeft: Int,
        val gameOver: Boolean,
        val goalMet: Boolean,
        val goalFailed: Boolean,
        val gems: BooleanArray,
        val trayGem: IntArray,
        val bombs: BooleanArray,
        val trayBomb: IntArray,
        val mults: BooleanArray,
        val trayMult: IntArray,
        val stones: BooleanArray,
        val holdPiece: Piece?,
        val holdGem: Int,
        val holdBomb: Int,
        val holdMult: Int,
        val holdLocked: Boolean,
        val comboGrace: Int,
        val nums: IntArray,
        val trayNum: IntArray,
        val curveLen: Int,
    )

    private fun snapshot(): Snapshot {
        val c = IntArray(board.cells.size)
        board.cells.copyInto(c)
        val g = BooleanArray(board.gems.size)
        board.gems.copyInto(g)
        val b = BooleanArray(board.bombs.size)
        board.bombs.copyInto(b)
        val m = BooleanArray(board.mults.size)
        board.mults.copyInto(m)
        val st = BooleanArray(board.stones.size)
        board.stones.copyInto(st)
        val n = IntArray(board.nums.size)
        board.nums.copyInto(n)
        return Snapshot(
            c, tray.copyOf(), score, combo, streak, bestCombo,
            linesCleared, cellsPlaced, meter, movesLeft, gameOver, goalMet, goalFailed,
            g, trayGem.copyOf(), b, trayBomb.copyOf(),
            m, trayMult.copyOf(), st,
            holdPiece, holdGem, holdBomb, holdMult, holdLocked, comboGrace,
            n, trayNum.copyOf(), scoreCurve.size,
        )
    }

    private fun restore(s: Snapshot) {
        s.cells.copyInto(board.cells)
        for (i in tray.indices) tray[i] = s.tray[i]
        score = s.score; combo = s.combo; streak = s.streak; bestCombo = s.bestCombo
        linesCleared = s.lines; cellsPlaced = s.cellsPlaced; meter = s.meter
        movesLeft = s.movesLeft; gameOver = s.gameOver; goalMet = s.goalMet; goalFailed = s.goalFailed
        s.gems.copyInto(board.gems)
        s.trayGem.copyInto(trayGem)
        s.bombs.copyInto(board.bombs)
        s.trayBomb.copyInto(trayBomb)
        s.mults.copyInto(board.mults)
        s.trayMult.copyInto(trayMult)
        s.stones.copyInto(board.stones)
        holdPiece = s.holdPiece; holdGem = s.holdGem; holdBomb = s.holdBomb; holdMult = s.holdMult
        holdLocked = s.holdLocked
        comboGrace = s.comboGrace
        s.nums.copyInto(board.nums)
        s.trayNum.copyInto(trayNum)
        while (scoreCurve.size > s.curveLen) scoreCurve.removeAt(scoreCurve.size - 1)
    }

    val canUndo get() = undoStack.isNotEmpty()

    fun undo(): Boolean {
        val s = undoStack.removeLastOrNull() ?: return false
        restore(s)
        return true
    }

    /** Lucky Break: when the board is ≥80% full the game pities the player —
     *  friendlier (smaller) pieces and denser bonus cells. UI shows LAST STAND. */
    val lastStand: Boolean
        get() = board.filledCount() >= board.size * board.size * 4 / 5

    private fun effGemChance() = (if (lastStand) (gemChance * 2.2f).coerceAtMost(0.6f) else gemChance) + perkGemBoost

    fun refillTray() {
        // full refill consumes the previewed next-tray so the preview is honest
        for (i in 0..2) {
            if (tray[i] == null && nextTray[i] != null) {
                tray[i] = nextTray[i]; nextTray[i] = null
                trayGem[i] = nextGem[i]; trayBomb[i] = nextBomb[i]; trayMult[i] = nextMult[i]; trayNum[i] = nextNum[i]
                nextGem[i] = -1; nextBomb[i] = -1; nextMult[i] = -1; nextNum[i] = -1
            }
        }
        for (i in 0..2) if (tray[i] == null) { tray[i] = nextPiece(); rollSpecial(i) }
        if (trayEmpty()) refillNext() else if (nextTray.all { it == null }) refillNext()
        // fairness: if nothing fits, keep re-rolling (bounded) so death feels earned, not cheap
        var tries = 0
        while (!anyTrayFit() && tries < 30) {
            for (i in 0..2) if (tray[i] != null && !board.anyFit(tray[i]!!)) {
                tray[i] = nextPiece(); rollSpecial(i)
            }
            tries++
        }
    }

    /** Generates the upcoming tray shown in the NEXT preview strip. */
    private fun refillNext() {
        for (i in 0..2) if (nextTray[i] == null) {
            nextTray[i] = nextPiece()
            val p = nextTray[i]!!
            nextGem[i] = if (rng.nextFloat() < effGemChance()) p.cells[rng.nextInt(p.cells.size)] else -1
            nextBomb[i] = if (rng.nextFloat() < (if (lastStand) 0.22f else 0.13f)) {
                var pick = p.cells[rng.nextInt(p.cells.size)]
                var tries = 0
                while (pick == nextGem[i] && tries++ < 4) pick = p.cells[rng.nextInt(p.cells.size)]
                pick
            } else -1
            nextMult[i] = if (rng.nextFloat() < 0.07f) {
                var pick = p.cells[rng.nextInt(p.cells.size)]
                var tries = 0
                while ((pick == nextGem[i] || pick == nextBomb[i]) && tries++ < 4) pick = p.cells[rng.nextInt(p.cells.size)]
                pick
            } else -1
            nextNum[i] = if (mode == Mode.MERGE && rng.nextFloat() < 0.4f) {
                var pick = p.cells[rng.nextInt(p.cells.size)]
                var tries = 0
                while ((pick == nextGem[i] || pick == nextBomb[i] || pick == nextMult[i]) && tries++ < 4) pick = p.cells[rng.nextInt(p.cells.size)]
                pick
            } else -1
        }
    }

    /** ~1-in-5 fresh pieces carry a gem cell worth coins when its line clears,
     *  ~1-in-8 carry a bomb cell that detonates 3x3 when its line clears. */
    private fun rollSpecial(i: Int) {
        val p = tray[i]
        trayGem[i] = if (p != null && rng.nextFloat() < effGemChance()) p.cells[rng.nextInt(p.cells.size)] else -1
        trayBomb[i] = if (p != null && rng.nextFloat() < (if (lastStand) 0.22f else 0.13f)) {
            // prefer a different cell than the gem so the piece reads clearly
            var pick = p.cells[rng.nextInt(p.cells.size)]
            var tries = 0
            while (pick == trayGem[i] && tries++ < 4) pick = p.cells[rng.nextInt(p.cells.size)]
            pick
        } else -1
        trayMult[i] = if (p != null && rng.nextFloat() < 0.07f + perkMultBoost) {
            var pick = p.cells[rng.nextInt(p.cells.size)]
            var tries = 0
            while ((pick == trayGem[i] || pick == trayBomb[i]) && tries++ < 4) pick = p.cells[rng.nextInt(p.cells.size)]
            pick
        } else -1
        trayNum[i] = if (mode == Mode.MERGE && p != null && rng.nextFloat() < 0.4f) {
            var pick = p.cells[rng.nextInt(p.cells.size)]
            var tries = 0
            while ((pick == trayGem[i] || pick == trayBomb[i] || pick == trayMult[i]) && tries++ < 4) pick = p.cells[rng.nextInt(p.cells.size)]
            pick
        } else -1
    }

    private fun nextPiece(): Piece {
        // Zen stays gentle: small shapes dominate so the board rarely jams.
        // Lucky Break adds its own small-piece bias when the board is nearly full.
        val sb = (if (mode == Mode.ZEN) 1.6f else 0f) + (if (lastStand) 1.1f else 0f)
        return Shapes.randomPiece(rng, colorCount, bigBias, sb)
    }

    /** Re-roll tray entries that don't fit (bounded) — used after preset board fills. */
    fun ensureTrayFits() {
        var tries = 0
        while (!anyTrayFit() && tries < 30) {
            for (i in 0..2) if (tray[i] != null && !board.anyFit(tray[i]!!)) { tray[i] = nextPiece(); rollSpecial(i) }
            tries++
        }
    }

    /** Timed levels + Rush mode: called when the countdown hits zero. */
    fun onTimeExpired() {
        if (gameOver || goalMet) return
        if (mode == Mode.RUSH) { gameOver = true; return }
        if (goal.type != GoalType.NONE) { goalFailed = true; gameOver = true }
    }

    /**
     * Hold slot — park tray[i] for later; the held piece comes back in exchange.
     * One hold per placement (resets inside place()).
     */
    fun hold(i: Int): Boolean {
        if (holdLocked || gameOver) return false
        val p = tray[i] ?: return false
        undoStack.addLast(snapshot())
        val prev = holdPiece
        holdPiece = p
        val g = holdGem; val b = holdBomb; val m = holdMult
        holdGem = trayGem[i]; holdBomb = trayBomb[i]; holdMult = trayMult[i]
        tray[i] = prev
        trayGem[i] = g; trayBomb[i] = b; trayMult[i] = m
        holdLocked = true
        // holding the last playable piece leaves an empty tray — refill so play continues
        if (trayEmpty()) onTrayEmpty()
        return true
    }

    /** Hint power-up: best (slot,row,col) move across the tray, or null. */
    fun bestMove(): Triple<Int, Int, Int>? {
        var best: Triple<Int, Int, Int>? = null
        var bestScore = Int.MIN_VALUE
        for (i in 0..2) {
            val p = tray[i] ?: continue
            for (r in 0..board.size - p.rows) for (c in 0..board.size - p.cols) {
                if (!board.fits(p, r, c)) continue
                val clears = board.findClears(p, r, c).lineCount
                var adj = 0
                for (pc in p.cells) {
                    val rr = r + (pc shr 4); val cc = c + (pc and 15)
                    if (rr > 0 && board.cells[(rr - 1) * board.size + cc] != 0) adj++
                    if (rr < board.size - 1 && board.cells[(rr + 1) * board.size + cc] != 0) adj++
                    if (cc > 0 && board.cells[rr * board.size + cc - 1] != 0) adj++
                    if (cc < board.size - 1 && board.cells[rr * board.size + cc + 1] != 0) adj++
                    if (rr == board.size - 1) adj++
                }
                val score = clears * 1000 + adj * 4 + r + p.size
                if (score > bestScore) { bestScore = score; best = Triple(i, r, c) }
            }
        }
        return best
    }

    /** Cells the revive would clear (3×3 densest) — for previewing before paying. */
    fun peekReviveCells(): IntArray {
        val (r, c) = densestArea()
        val out = ArrayList<Int>()
        for (dr in -1..1) for (dc in -1..1) {
            val rr = r + dr; val cc = c + dc
            if (rr in 0 until board.size && cc in 0 until board.size) out.add(rr * board.size + cc)
        }
        return out.toIntArray()
    }

    fun anyTrayFit(): Boolean = tray.any { it != null && board.anyFit(it) }

    fun trayEmpty(): Boolean = tray.all { it == null }

    fun fitsTray(i: Int): Boolean = tray[i]?.let { board.anyFit(it) } == true

    /** Attempt to place tray piece i at (row,col). */
    fun place(i: Int, row: Int, col: Int): PlaceResult {
        if (gameOver || gambitOpen || expeditionOffer) return PlaceResult(false)
        val p = tray[i] ?: return PlaceResult(false)
        if (!board.fits(p, row, col)) return PlaceResult(false)

        avalancheJustHit = false
        bossPhaseJustAdvanced = false

        undoStack.addLast(snapshot())
        if (undoStack.size > 20) undoStack.removeFirst()

        holdLocked = false // a placed piece unlocks the hold slot again

        board.place(p, row, col)
        // special cells embedded in the piece land on the board
        val g = trayGem[i]
        if (g >= 0) board.gems[(row + (g shr 4)) * board.size + col + (g and 15)] = true
        trayGem[i] = -1
        val bb = trayBomb[i]
        if (bb >= 0) board.bombs[(row + (bb shr 4)) * board.size + col + (bb and 15)] = true
        trayBomb[i] = -1
        val mm = trayMult[i]
        if (mm >= 0) board.mults[(row + (mm shr 4)) * board.size + col + (mm and 15)] = true
        trayMult[i] = -1
        val nm = trayNum[i]
        if (nm >= 0) board.nums[(row + (nm shr 4)) * board.size + col + (nm and 15)] = 2
        trayNum[i] = -1
        tray[i] = null
        cellsPlaced += p.size
        if (movesLeft > 0) movesLeft--

        // SNUG — every cell of the piece touches a filled neighbour or the rim
        var snug = true
        outer@ for (pc in p.cells) {
            val pr = row + (pc shr 4); val pcx = col + (pc and 15)
            var contact = pr == 0 || pr == board.size - 1 || pcx == 0 || pcx == board.size - 1
            if (!contact) {
                if (pr > 0 && board.cells[(pr - 1) * board.size + pcx] != 0) contact = true
                if (pr < board.size - 1 && board.cells[(pr + 1) * board.size + pcx] != 0) contact = true
                if (pcx > 0 && board.cells[pr * board.size + pcx - 1] != 0) contact = true
                if (pcx < board.size - 1 && board.cells[pr * board.size + pcx + 1] != 0) contact = true
            }
            if (!contact) { snug = false; break@outer }
        }

        val res = boardPostClears()

        var gained = p.size * 2
        var meterFullNow = false
        var gems = 0
        var perfect = false
        var boom = IntArray(0)
        var bombsN = 0
        var monoLines = 0
        var multHit = false
        var preForPerk: IntArray? = null
        if (!res.isEmpty) {
            for (cc in res.clearCells) if (board.gems[cc]) gems++
            // MONO — a cleared line of a single colour scores +150% extra
            val pre = board.cells.copyOf()
            preForPerk = pre
            for (lr in res.rows) {
                var v = -1; var same = true
                for (cc in 0 until board.size) {
                    val x = pre[lr * board.size + cc]
                    if (v < 0) v = x else if (x != v) { same = false; break }
                }
                if (same) monoLines++
            }
            for (lc in res.cols) {
                var v = -1; var same = true
                for (rr in 0 until board.size) {
                    val x = pre[rr * board.size + lc]
                    if (v < 0) v = x else if (x != v) { same = false; break }
                }
                if (same) monoLines++
            }
            // ×3 cells inside the clear triple its score
            for (cc in res.clearCells) if (board.mults[cc]) { multHit = true; break }
            // bombs caught in cleared lines detonate 3x3 and can chain
            boomGems = 0
            val det = detonate(res.clearCells)
            boom = det.first; bombsN = det.second; gems += boomGems
            board.applyClear(res.clearCells)
            val n = res.lineCount
            linesCleared += n
            combo++
            streak++
            comboGrace = 1 + perkGracePlus // free pass(es) before the chain drops
            if (combo > bestCombo) bestCombo = combo
            gained += (res.clearCells.size + boom.size) * 10 + n * n * 40 + bombsN * (60 + perkBombBonus)
            gained += (gained * (0.5f * monoLines)).toInt()
            if (multHit) gained *= 3
            gained = (gained * (1f + 0.5f * (combo - 1))).toInt()
            // PERFECT CLEAR — emptied the whole board
            if (board.filledCount() == 0) { perfect = true; gained += 400 + combo * 100 }
            if (feverT > 0f) gained *= 2
            meter += n * 12 + (if (combo > 1) 8 * combo else 0) + bombsN * 10
            if (mode == Mode.RUSH) meter += n * 10 // fever charges ~2x in Rush
            if (meter >= 100) { meter -= 100; meterFullNow = true; feverT = 8f + perkFeverBonus }
        } else {
            // combo grace: the chain survives one non-clearing placement, then drops
            if (combo > 0) {
                if (comboGrace > 0) comboGrace-- else combo = 0
            }
            streak = 0
            meter += 2
        }

        // ---- GRAVITY / MERGE physics loop ----
        // After the initial clear, cells settle downward (Gravity) and equal
        // numbered neighbours merge (Merge). Either can complete new lines,
        // which clear and settle again — chains until the board is stable.
        var cascades = 0
        if (mode == Mode.GRAVITY || mode == Mode.MERGE) {
            var moved = board.settle()
            if (mode == Mode.MERGE) {
                val mg = mergePass()
                gained += mg
                moved = moved || mg > 0
            }
            var nx = boardPostClears()
            while ((!nx.isEmpty || moved) && cascades < 12) {
                if (!nx.isEmpty) {
                    cascades++
                    for (cc in nx.clearCells) if (board.gems[cc]) gems++
                    for (cc in nx.clearCells) if (board.mults[cc]) multHit = true
                    val det2 = detonate(nx.clearCells)
                    boom = boom + det2.first; bombsN += det2.second; gems += boomGems
                    board.applyClear(nx.clearCells)
                    linesCleared += nx.lineCount
                    var cg = (nx.clearCells.size + det2.first.size) * 10 +
                        nx.lineCount * nx.lineCount * 40 + det2.second * (60 + perkBombBonus)
                    cg += cg * cascades / 2 // deeper cascades pay more
                    if (multHit) cg *= 3
                    gained += cg
                }
                moved = board.settle()
                if (mode == Mode.MERGE) {
                    val mg = mergePass()
                    gained += mg
                    moved = moved || mg > 0
                }
                nx = boardPostClears()
            }
            if (cascades > 0) {
                meter += cascades * 6
                if (!perfect && board.filledCount() == 0) { perfect = true; gained += 400 + combo * 100 }
            }
        }
        lastCascade = cascades

        if (snug) gained += 25 + p.size * 5
        if (perkScoreBoost > 0f) gained += (gained * perkScoreBoost).toInt()
        if (perkColor >= 0 && preForPerk != null) {
            // Color Surge perk: +15 per cleared cell matching the chosen colour
            var n = 0
            for (cc in res.clearCells) if (preForPerk[cc] == perkColor + 1) n++
            gained += n * 15
        }
        lastGemsCollected = gems
        score += gained
        scoreCurve.add(score)
        tickContract(res.lineCount, bombsN, snug)

        // classic difficulty ramp: bigger pieces weigh more over time and a
        // 7th color joins once the player is in flow
        if (mode == Mode.CLASSIC) {
            bigBias = score / 1800f
            colorCount = if (score >= 1500) 7 else 6
        }

        // AVALANCHE — the floor rises every few placements
        if (mode == Mode.AVALANCHE && !gameOver) {
            avalancheIn--
            if (avalancheIn <= 0) {
                avalancheJustHit = true
                val gapA = rng.nextInt(board.size)
                var gapB = rng.nextInt(board.size)
                if (gapB == gapA) gapB = (gapB + 1) % board.size
                val fill = BooleanArray(board.size) { it != gapA && it != gapB && rng.nextFloat() < 0.62f }
                if (!board.pushJunkRow(fill, 1 + rng.nextInt(6))) gameOver = true
                avalancheCount++
                avalancheIn = (7 - avalancheCount / 3).coerceAtLeast(3)
            }
        }

        // EXPEDITION — hitting a score milestone opens the perk draft
        if (mode == Mode.EXPEDITION && !gameOver && score >= expeditionNextAt) {
            expeditionOffer = true
            expeditionNextAt += 1200 + rng.nextInt(500)
        }

        if (trayEmpty()) onTrayEmpty()
        evaluateEnd()
        return PlaceResult(true, res.clearCells, res.lineCount, gained, combo, meterFullNow, gems, perfect, boom, bombsN, monoLines, snug, multHit)
    }

    private fun onTrayEmpty() {
        if (mode == Mode.GAMBIT) openGambitShop() else refillTray()
    }

    /**
     * Merge mode: scan for orthogonally adjacent equal numbered cells and fuse
     * them into the lower/right cell (value doubles). Returns total merged score.
     */
    fun mergePass(): Int {
        var total = 0
        val consumed = HashSet<Int>()
        for (r in 0 until board.size) for (c in 0 until board.size) {
            val i = r * board.size + c
            val v = board.nums[i]
            if (v == 0 || i in consumed) continue
            val down = if (r + 1 < board.size) (r + 1) * board.size + c else -1
            val right = if (c + 1 < board.size) r * board.size + c + 1 else -1
            for (t in intArrayOf(down, right)) {
                if (t < 0 || t in consumed) continue
                if (board.nums[t] == v) {
                    board.nums[t] = v * 2
                    board.cells[i] = 0; board.nums[i] = 0
                    board.gems[i] = false; board.bombs[i] = false
                    board.mults[i] = false; board.stones[i] = false
                    consumed.add(t)
                    total += v * 2
                    break
                }
            }
        }
        return total
    }

    // ---------- expedition perks ----------

    /** Three perk ids not yet owned (empty when everything is taken). */
    fun perkChoices(): IntArray {
        val pool = (0..7).filter { it !in expeditionPerks }
        if (pool.size <= 3) return pool.toIntArray()
        return pool.shuffled(rng).take(3).toIntArray()
    }

    fun applyPerk(id: Int) {
        expeditionPerks.add(id)
        when (id) {
            0 -> perkFeverBonus += 4f      // Long Fever: fever lasts 12s
            1 -> perkGracePlus += 1        // Sticky Combo: 2 free misses
            2 -> perkColor = rng.nextInt(colorCount) // Color Surge
            3 -> perkGemBoost += 0.18f     // Gem Magnet
            4 -> perkMultBoost += 0.13f    // Lucky ×3
            5 -> perkBombBonus += 80       // Big Boom pays more
            6 -> perkScoreBoost += 0.2f    // High Roller +20% score
            7 -> perkFreeRevive = true     // Second Wind
        }
        expeditionOffer = false
        evaluateEnd()
    }

    /** Dismiss the draft without taking a perk (offer exhausted). */
    fun closeOffer() { expeditionOffer = false }

    // ---------- gambit (tray shop) ----------

    /** Tray emptied in Gambit mode → open the shop instead of auto-refilling. */
    private fun openGambitShop() {
        gambitOpen = true
        gambitPicks = 0
        for (k in 0..4) {
            val p = nextPiece()
            gambitShop[k] = p
            gambitCost[k] = p.size * 14 + rng.nextInt(15)
        }
        // mercy rule: if the player can't afford the cheapest card, it's free
        var cheapest = 0
        for (k in 1..4) if (gambitCost[k] < gambitCost[cheapest]) cheapest = k
        if (score < gambitCost[cheapest]) gambitCost[cheapest] = 0
    }

    /** Buy shop card [k] into the next free tray slot. 3 buys close the shop. */
    fun gambitBuy(k: Int): Boolean {
        if (!gambitOpen) return false
        val p = gambitShop[k] ?: return false
        if (score < gambitCost[k]) return false
        score -= gambitCost[k]
        val slot = (0..2).firstOrNull { tray[it] == null } ?: return false
        tray[slot] = p
        rollSpecial(slot)
        gambitShop[k] = null
        val done = ++gambitPicks >= 3 || gambitShop.all { it == null } ||
            // broke: nothing left is affordable — remaining slots fill free
            !gambitShop.indices.any { gambitShop[it] != null && gambitCost[it] <= score }
        if (done) {
            gambitOpen = false
            while (true) {
                val free = (0..2).firstOrNull { tray[it] == null } ?: break
                tray[free] = nextPiece(); rollSpecial(free)
            }
            if (nextTray.all { it == null }) refillNext()
        }
        evaluateEnd()
        return true
    }

    // ---------- avalanche / versus junk rows ----------

    /** Push [pressure]≈lines-worth of junk up from the bottom (Versus attacks,
     *  Avalanche surges). Returns false when the push tops the player out. */
    fun takeJunkRow(pressure: Int): Boolean {
        val fill = BooleanArray(board.size)
        val gap = rng.nextInt(board.size)
        var n = (pressure + 2).coerceIn(3, board.size - 1)
        for (c in (0 until board.size).shuffled(rng)) {
            if (n <= 0) break
            if (c != gap) { fill[c] = true; n-- }
        }
        val ok = board.pushJunkRow(fill, 1 + rng.nextInt(6))
        if (!ok) gameOver = true else evaluateEnd()
        return ok
    }

    /** One step of the Versus bot: place the best-scoring tray piece.
     *  Returns lines cleared (caller converts ≥2 into a junk attack), or -1. */
    fun botStep(): Int {
        val mv = bestMove() ?: return -1
        val r = place(mv.first, mv.second, mv.third)
        return if (r.placed) r.lines else -1
    }

    /** Seed for boss patterns — set by the boss() factory so runs differ. */
    var bossSeed: Long = 0

    /** Boss fight: spawn the next stone pattern and top up the move budget. */
    private fun spawnBossPhase() {
        bossPhase++
        val pr = Random(91_000_000L + bossSeed + bossPhase * 6271 + avalancheCount)
        var stones = 0
        // chunky seeded blob: centroid + clustered neighbours, harder per phase
        val wanted = 10 + bossPhase * 5 + (if (bossPhases > 0) bossPhases * 2 else 0)
        var guard = 0
        val cx = 1 + pr.nextInt(board.size - 2)
        val cy = 1 + pr.nextInt(board.size - 4)
        while (stones < wanted && guard++ < 400) {
            val r = (cy + (pr.nextFloat() * 4.6f).toInt() - 2).coerceIn(0, board.size - 1)
            val c = (cx + (pr.nextFloat() * 6.4f).toInt() - 3).coerceIn(0, board.size - 1)
            val i = r * board.size + c
            if (!board.stones[i]) {
                board.stones[i] = true
                if (board.cells[i] == 0) board.cells[i] = 1 + pr.nextInt(6)
                stones++
            }
        }
        goal.target = stones
        movesLeft += 6 + bossPhase * 2
        bossPhaseJustAdvanced = true
        ensureTrayFits()
    }

    /** Versus win/lose helper — the bot topped out, so the player wins now. */
    fun forceWin() { goalMet = true }

    /** Avalanche countdown accessor for UI (placements until the next rise). */
    fun avalancheInterval() = (7 - avalancheCount / 3).coerceAtLeast(3)

    // ---------- micro-contracts (classic only — Zen is pressure-free) ----------

    private fun tickContract(lines: Int, bombsN: Int, snug: Boolean) {
        if (mode != Mode.CLASSIC) return
        val c = contract
        if (c == null) {
            if (score >= nextContractAt) {
                contract = rollContract()
                nextContractAt = score + 350 + rng.nextInt(200)
            }
            return
        }
        when (c.type) {
            0 -> c.prog += lines
            1 -> if (combo > c.prog) c.prog = combo
            2 -> c.prog += bombsN
            3 -> if (snug) c.prog++
        }
        c.movesLeft--
        if (c.prog >= c.target) {
            contractJustDone = c
            contract = null
        } else if (c.movesLeft <= 0) {
            contract = null
            contractJustFailed = true
        }
    }

    private fun rollContract(): Contract {
        val kind = rng.nextInt(4)
        val r = rng.nextInt(3)
        return when (kind) {
            0 -> Contract(0, 2 + r, 0, 5 + r, 40 + r * 20)          // N lines within M placements
            1 -> Contract(1, 3 + r, 0, 6, 50 + r * 30)               // reach combo N
            2 -> Contract(2, 1 + r / 2, 0, 8, 60 + r * 20)           // detonate N bombs
            else -> Contract(3, 2 + r, 0, 8, 45 + r * 25)            // snug N fits
        }
    }

    private fun boardPostClears(): Board.ClearResult {
        // find full rows/cols on the CURRENT board (piece already placed)
        val rowsFull = ArrayList<Int>()
        val colsFull = ArrayList<Int>()
        val size = board.size
        for (r in 0 until size) {
            var full = true
            for (c in 0 until size) if (board.cells[r * size + c] == 0) { full = false; break }
            if (full) rowsFull.add(r)
        }
        for (c in 0 until size) {
            var full = true
            for (r in 0 until size) if (board.cells[r * size + c] == 0) { full = false; break }
            if (full) colsFull.add(c)
        }
        if (rowsFull.isEmpty() && colsFull.isEmpty()) return Board.ClearResult(rowsFull, colsFull, IntArray(0))
        val set = HashSet<Int>()
        for (r in rowsFull) for (c in 0 until size) set.add(r * size + c)
        for (c in colsFull) for (r in 0 until size) set.add(r * size + c)
        return Board.ClearResult(rowsFull, colsFull, set.toIntArray())
    }

    /** Rotate a tray piece in place (costs a rotate power-up at UI level). */
    fun rotateTray(i: Int): Boolean {
        val p = tray[i] ?: return false
        tray[i] = p.rotated()
        return true
    }

    /** Replace all unplaced tray pieces with fresh ones (shuffle power-up). */
    fun shuffleTray() {
        undoStack.addLast(snapshot())
        for (i in 0..2) if (tray[i] != null) { tray[i] = nextPiece(); rollSpecial(i) }
        evaluateEnd()
    }

    /**
     * Detonates every bomb cell in [seed] — each clears its 3x3 neighborhood
     * (collecting gems) and chains into any bombs caught in the blast.
     * Returns (extra cells removed, bombs detonated).
     */
    private fun detonate(seed: IntArray): Pair<IntArray, Int> {
        val queue = ArrayDeque<Int>()
        val seen = HashSet<Int>()
        for (idx in seed) if (board.bombs[idx] && seen.add(idx)) queue.addLast(idx)
        if (queue.isEmpty()) return IntArray(0) to 0
        var detonated = 0
        val removed = ArrayList<Int>()
        val seedSet = seed.toHashSet()
        while (queue.isNotEmpty()) {
            val b = queue.removeFirst()
            detonated++
            val br = b / board.size; val bc = b % board.size
            board.bombs[b] = false
            for (dr in -1..1) for (dc in -1..1) {
                val rr = br + dr; val cc = bc + dc
                if (rr !in 0 until board.size || cc !in 0 until board.size) continue
                val idx = rr * board.size + cc
                if (board.bombs[idx] && seen.add(idx)) queue.addLast(idx)
                if (board.cells[idx] != 0 && !seedSet.contains(idx)) {
                    if (board.gems[idx]) boomGems++
                    board.cells[idx] = 0
                    board.gems[idx] = false
                    board.bombs[idx] = false
                    board.nums[idx] = 0
                    removed.add(idx)
                }
            }
        }
        return removed.toIntArray() to detonated
    }

    /** Clear a radius-1 (3x3) area centered at row/col (bomb power-up). */
    fun blastArea(row: Int, col: Int): IntArray {
        undoStack.addLast(snapshot())
        lastGemsCollected = 0; boomGems = 0
        // bombs inside the blast detonate first so they chain
        val seeds = ArrayList<Int>()
        for (r in (row - 1)..(row + 1)) for (c in (col - 1)..(col + 1)) {
            if (r in 0 until board.size && c in 0 until board.size) {
                val idx = r * board.size + c
                if (board.bombs[idx]) seeds.add(idx)
                if (board.gems[idx]) lastGemsCollected++
            }
        }
        val boom = if (seeds.isNotEmpty()) detonate(seeds.toIntArray()).first else IntArray(0)
        lastGemsCollected += boomGems
        val removed = board.clearArea(row, col, 1)
        if (trayEmpty()) onTrayEmpty()
        evaluateEnd()
        return removed + boom
    }

    /** Continue after game over: clears the densest 3x3 region and resumes. */
    fun reviveClear(): IntArray {
        val best = densestArea()
        lastGemsCollected = 0; boomGems = 0
        val seeds = ArrayList<Int>()
        for (r in (best.first - 1)..(best.first + 1)) for (c in (best.second - 1)..(best.second + 1)) {
            if (r in 0 until board.size && c in 0 until board.size) {
                val idx = r * board.size + c
                if (board.bombs[idx]) seeds.add(idx)
                if (board.gems[idx]) lastGemsCollected++
            }
        }
        val boom = if (seeds.isNotEmpty()) detonate(seeds.toIntArray()).first else IntArray(0)
        lastGemsCollected += boomGems
        val removed = board.clearArea(best.first, best.second, 1)
        gameOver = false
        evaluateEnd()
        return removed + boom
    }

    private fun densestArea(): Pair<Int, Int> {
        var best = 1 to 1
        var bestN = -1
        for (r in 1 until board.size - 1) for (c in 1 until board.size - 1) {
            var n = 0
            for (dr in -1..1) for (dc in -1..1) {
                if (board.cells[(r + dr) * board.size + c + dc] != 0) n++
            }
            if (n > bestN) { bestN = n; best = r to c }
        }
        return best
    }

    private fun evaluateEnd() {
        if (gameOver) return
        if (mode == Mode.ZEN) {
            // never game over: nothing fits → gently clear the lowest row and reroll dead pieces
            if (!anyTrayFit()) {
                var guard = 0
                zenRelief()
                while (!anyTrayFit() && guard++ < 30) {
                    for (i in 0..2) if (tray[i] != null && !board.anyFit(tray[i]!!)) { tray[i] = nextPiece(); rollSpecial(i) }
                    if (board.filledCount() == 0 && !anyTrayFit()) break
                }
            }
            return
        }
        if (mode == Mode.BOSS && board.stoneCount() == 0 && bossPhase < bossPhases) {
            spawnBossPhase() // next wave of stones — keeps the goal alive
        }
        when (goal.type) {
            GoalType.SCORE -> if (score >= goal.target) goalMet = true
            GoalType.LINES -> if (linesCleared >= goal.target) goalMet = true
            GoalType.CELLS -> if (cellsPlaced >= goal.target) goalMet = true
            GoalType.STONES -> if (board.stoneCount() == 0) goalMet = true
            GoalType.NONE -> {}
        }
        if (goalMet) return
        if (movesLeft == 0 && goal.type != GoalType.NONE) { goalFailed = true; gameOver = true; return }
        // an empty tray is not a loss while the gambit shop is deciding the refill
        if (!anyTrayFit() && !gambitOpen) gameOver = true
    }

    /** Counts how many times Zen auto-relief fired (UI shows a notice). */
    var zenReliefCount = 0
        private set

    /** Zen relief: fade the lowest occupied row so the board never jams. */
    private fun zenRelief() {
        zenReliefCount++
        var lowest = -1
        for (r in board.size - 1 downTo 0) {
            var any = false
            for (c in 0 until board.size) if (board.cells[r * board.size + c] != 0) { any = true; break }
            if (any) { lowest = r; break }
        }
        if (lowest < 0) return
        for (c in 0 until board.size) {
            val i = lowest * board.size + c
            board.cells[i] = 0; board.gems[i] = false; board.bombs[i] = false
            board.mults[i] = false; board.stones[i] = false; board.nums[i] = 0
        }
    }

    fun goalProgress(): Int = when (goal.type) {
        GoalType.SCORE -> score
        GoalType.LINES -> linesCleared
        GoalType.CELLS -> cellsPlaced
        GoalType.STONES -> goal.target - board.stoneCount()
        GoalType.NONE -> 0
    }

    // ---------- saved-run serialization (Continue feature) ----------

    /**
     * Compact ';'-separated run snapshot (no org.json so it stays unit-testable).
     * Fields: mode;score;combo;streak;bestCombo;lines;cells;meter;moves;over;met;
     * failed;colors;bias;time;goalType;goalTarget;boardCsv;tray1/tray2/tray3
     * where a tray entry is empty for null or "color:c1.c2..." packed cells.
     */
    fun toJson(): String {
        val sb = StringBuilder()
        sb.append(mode.name).append(';')
        sb.append(score).append(';').append(combo).append(';').append(streak).append(';')
        sb.append(bestCombo).append(';').append(linesCleared).append(';').append(cellsPlaced).append(';')
        sb.append(meter).append(';').append(movesLeft).append(';')
        sb.append(if (gameOver) 1 else 0).append(';')
        sb.append(if (goalMet) 1 else 0).append(';')
        sb.append(if (goalFailed) 1 else 0).append(';')
        sb.append(colorCount).append(';').append(bigBias).append(';').append(timeLimitSec).append(';')
        sb.append(goal.type.name).append(';').append(goal.target).append(';')
        for (i in board.cells.indices) { if (i > 0) sb.append(','); sb.append(board.cells[i]) }
        sb.append(';')
        for (i in 0..2) {
            if (i > 0) sb.append('/')
            val p = tray[i] ?: continue
            sb.append(p.colorIndex).append(':')
            for (j in p.cells.indices) { if (j > 0) sb.append('.'); sb.append(p.cells[j]) }
        }
        sb.append(';')
        for (i in board.gems.indices) { if (i > 0) sb.append(','); sb.append(if (board.gems[i]) 1 else 0) }
        sb.append(';')
        for (i in 0..2) { if (i > 0) sb.append(','); sb.append(trayGem[i]) }
        sb.append(';')
        for (i in board.bombs.indices) { if (i > 0) sb.append(','); sb.append(if (board.bombs[i]) 1 else 0) }
        sb.append(';')
        for (i in 0..2) { if (i > 0) sb.append(','); sb.append(trayBomb[i]) }
        sb.append(';')
        sb.append(gemChance).append(';').append(dailyModifier)
        // v7 tail: hold piece (or empty);holdGem;holdBomb;holdMult;holdLocked;comboGrace;
        //   contract (type;target;prog;moves;reward or empty);stones csv;mults csv;trayMult
        val hp = holdPiece
        if (hp != null) {
            sb.append(';').append(hp.colorIndex).append(':')
            for (j in hp.cells.indices) { if (j > 0) sb.append('.'); sb.append(hp.cells[j]) }
        } else sb.append(';')
        sb.append(';').append(holdGem).append(';').append(holdBomb).append(';').append(holdMult)
            .append(';').append(if (holdLocked) 1 else 0).append(';').append(comboGrace)
        val c = contract
        if (c != null) sb.append(';').append(c.type).append(',').append(c.target).append(',')
            .append(c.prog).append(',').append(c.movesLeft).append(',').append(c.reward)
        else sb.append(';')
        sb.append(';')
        for (i in board.stones.indices) { if (i > 0) sb.append(','); sb.append(if (board.stones[i]) 1 else 0) }
        sb.append(';')
        for (i in board.mults.indices) { if (i > 0) sb.append(','); sb.append(if (board.mults[i]) 1 else 0) }
        sb.append(';')
        for (i in 0..2) { if (i > 0) sb.append(','); sb.append(trayMult[i]) }
        // v8 tail: nums csv; trayNum; avalanche; boss; scoreCurve
        sb.append(';')
        for (i in board.nums.indices) { if (i > 0) sb.append(','); sb.append(board.nums[i]) }
        sb.append(';')
        for (i in 0..2) { if (i > 0) sb.append(','); sb.append(trayNum[i]) }
        sb.append(';').append(avalancheIn).append(',').append(avalancheCount)
        sb.append(';').append(bossPhase).append(',').append(bossPhases)
        sb.append(';')
        for (i in scoreCurve.indices) { if (i > 0) sb.append(','); sb.append(scoreCurve[i]) }
        return sb.toString()
    }

    companion object {
        /**
         * End-of-run letter grade: 0=C, 1=B, 2=A, 3=S.
         * Thresholds are per-mode — Rush scores run lower than Classic.
         */
        fun grade(mode: Mode, score: Int): Int {
            val t = when (mode) {
                Mode.CLASSIC -> intArrayOf(1500, 3500, 6500)
                Mode.ZEN -> intArrayOf(1200, 3000, 5500)
                Mode.RUSH -> intArrayOf(700, 1500, 2800)
                Mode.DAILY -> intArrayOf(500, 1200, 2200)
                Mode.EXPEDITION -> intArrayOf(1800, 4200, 8000)
                Mode.GAMBIT -> intArrayOf(1200, 3000, 5500)
                Mode.AVALANCHE -> intArrayOf(800, 2000, 4000)
                Mode.MERGE -> intArrayOf(1200, 3000, 6000)
                Mode.GRAVITY -> intArrayOf(1000, 2600, 5200)
                else -> intArrayOf(800, 2000, 4000)
            }
            return when {
                score >= t[2] -> 3
                score >= t[1] -> 2
                score >= t[0] -> 1
                else -> 0
            }
        }

        fun classic(rng: Random = Random.Default) =
            GameEngine(Board(9), rng, Mode.CLASSIC, Goal(GoalType.NONE, 0), -1)

        fun level(def: LevelDef, rng: Random = Random(def.seed)): GameEngine {
            val e = GameEngine(Board(9), rng, Mode.LEVEL, def.goal, def.moveLimit)
            e.timeLimitSec = def.timedSec
            if (def.presetFill > 0f) {
                val pr = Random(def.seed * 3 + 11)
                // seed the lower rows with a noisy floor so every level feels hand-made
                val fillRows = 3
                for (r in e.board.size - fillRows until e.board.size) {
                    for (c in 0 until e.board.size) {
                        if (pr.nextFloat() < def.presetFill) e.board.cells[r * e.board.size + c] = pr.nextInt(6) + 1
                    }
                }
                e.ensureTrayFits()
            }
            return e
        }

        fun daily(seed: Long, goal: Goal, moves: Int) =
            GameEngine(Board(9), Random(seed), Mode.DAILY, goal, moves)

        fun zen(rng: Random = Random.Default) =
            GameEngine(Board(9), rng, Mode.ZEN, Goal(GoalType.NONE, 0), -1)

        fun rush(rng: Random = Random.Default): GameEngine {
            val e = GameEngine(Board(9), rng, Mode.RUSH, Goal(GoalType.NONE, 0), -1, bigBias = 0.6f)
            e.timeLimitSec = 90
            return e
        }

        fun puzzle(def: LevelDef): GameEngine {
            val e = GameEngine(Board(9), Random(def.seed), Mode.PUZZLE, def.goal, def.moveLimit)
            // weekly defs (index >= COUNT) salt the board layout with their seed
            if (def.index >= Puzzles.COUNT) Puzzles.seedBoard(e, def.index, def.seed)
            else Puzzles.seedBoard(e, def.index)
            e.ensureTrayFits()
            return e
        }

        /** Gravity — cleared cells leave gaps everything else falls through. */
        fun gravity(rng: Random = Random.Default) =
            GameEngine(Board(9), rng, Mode.GRAVITY, Goal(GoalType.NONE, 0), -1)

        /** Avalanche — the floor pushes a junk row up every few placements. */
        fun avalanche(rng: Random = Random.Default): GameEngine {
            val e = GameEngine(Board(9), rng, Mode.AVALANCHE, Goal(GoalType.NONE, 0), -1)
            e.avalancheIn = 7
            return e
        }

        /** Merge — numbered cells fuse like 2048 between line clears. */
        fun merge(rng: Random = Random.Default) =
            GameEngine(Board(9), rng, Mode.MERGE, Goal(GoalType.NONE, 0), -1)

        /** Boss — clear seeded stone blobs; each cleared wave spawns a bigger one. */
        fun boss(phases: Int = 3, seed: Long = Random.nextLong()): GameEngine {
            val e = GameEngine(Board(9), Random(seed), Mode.BOSS, Goal(GoalType.STONES, 0), 20 + phases * 8)
            e.bossPhases = phases
            e.bossSeed = seed % 1_000_000
            e.spawnBossPhase()
            e.ensureTrayFits()
            return e
        }

        /** Expedition — classic run punctuated by perk drafts at score milestones. */
        fun expedition(rng: Random = Random.Default) =
            GameEngine(Board(9), rng, Mode.EXPEDITION, Goal(GoalType.NONE, 0), -1)

        /** Gambit — the tray is a shop: buy 3 pieces with your own score. */
        fun gambit(rng: Random = Random.Default) =
            GameEngine(Board(9), rng, Mode.GAMBIT, Goal(GoalType.NONE, 0), -1)

        /** Versus — the player's engine; the bot is a second classic engine. */
        fun versus(rng: Random = Random.Default) =
            GameEngine(Board(9), rng, Mode.VERSUS, Goal(GoalType.NONE, 0), -1)

        /** Mosaic — clear stones to reveal pixel art. */
        fun mosaic(artIndex: Int, seed: Long = Random.nextLong()): GameEngine {
            val e = GameEngine(Board(9), Random(seed), Mode.MOSAIC, Goal(GoalType.STONES, 0), -1)
            val art = Mosaics.art(artIndex)
            val pr = Random(seed)
            var stones = 0
            for (i in art.indices) {
                if (art[i] != 0) {
                    e.board.stones[i] = true
                    e.board.cells[i] = 1 + pr.nextInt(6)
                    stones++
                }
            }
            e.goal.target = stones
            e.movesLeft = stones + 14
            e.ensureTrayFits()
            return e
        }

        fun fromJson(json: String): GameEngine? {
            return try {
                val f = json.split(';')
                if (f.size < 19) return null
                var i = 0
                fun nx() = f[i++]
                val mode = Mode.valueOf(nx())
                val score = nx().toInt(); val combo = nx().toInt(); val streak = nx().toInt()
                val bestCombo = nx().toInt(); val lines = nx().toInt(); val cells = nx().toInt()
                val meter = nx().toInt(); val moves = nx().toInt()
                val over = nx() == "1"; val met = nx() == "1"; val failed = nx() == "1"
                val colors = nx().toInt(); val bias = nx().toFloat(); val time = nx().toInt()
                val goal = Goal(GoalType.valueOf(nx()), nx().toInt())
                val bs = nx()
                val trayStr = if (i < f.size) nx() else ""
                val e = GameEngine(Board(9), Random.Default, mode, goal, moves, colors, time, bias)
                val bc = bs.split(',')
                if (bc.size != e.board.cells.size) return null
                for (k in e.board.cells.indices) e.board.cells[k] = bc[k].toInt()
                val tp = trayStr.split('/')
                for (k in 0..2) {
                    val s = tp.getOrNull(k)
                    if (s.isNullOrEmpty()) { e.tray[k] = null; continue }
                    val ci = s.substringBefore(':').toInt()
                    val pc = s.substringAfter(':').split('.').map { it.toInt() }.toIntArray()
                    e.tray[k] = Piece(pc, ci)
                }
                e.score = score; e.combo = combo; e.streak = streak
                e.bestCombo = bestCombo; e.linesCleared = lines; e.cellsPlaced = cells
                e.meter = meter; e.gameOver = over; e.goalMet = met; e.goalFailed = failed
                // optional trailing fields: board gems csv + tray gem indices
                if (i < f.size) {
                    val gc = f[i++].split(',')
                    if (gc.size == e.board.gems.size) for (k in gc.indices) e.board.gems[k] = gc[k] == "1"
                }
                if (i < f.size) {
                    val tg = f[i].split(',')
                    for (k in 0..2) e.trayGem[k] = tg.getOrNull(k)?.toIntOrNull() ?: -1
                }
                i++
                if (i < f.size) {
                    val bc2 = f[i++].split(',')
                    if (bc2.size == e.board.bombs.size) for (k in bc2.indices) e.board.bombs[k] = bc2[k] == "1"
                }
                if (i < f.size) {
                    val tb = f[i].split(',')
                    for (k in 0..2) e.trayBomb[k] = tb.getOrNull(k)?.toIntOrNull() ?: -1
                }
                i++
                if (i < f.size) e.gemChance = f[i++].toFloatOrNull() ?: 0.2f
                if (i < f.size) e.dailyModifier = f[i++].toIntOrNull() ?: 0
                // v7 tail — hold piece, specials, grace, contract, stones, mults
                if (i < f.size) {
                    val hs = f[i++]
                    if (hs.isNotEmpty()) {
                        e.holdPiece = Piece(hs.substringAfter(':').split('.').map { it.toInt() }.toIntArray(), hs.substringBefore(':').toInt())
                    }
                }
                if (i < f.size) e.holdGem = f[i++].toIntOrNull() ?: -1
                if (i < f.size) e.holdBomb = f[i++].toIntOrNull() ?: -1
                if (i < f.size) e.holdMult = f[i++].toIntOrNull() ?: -1
                if (i < f.size) e.holdLocked = f[i++] == "1"
                if (i < f.size) e.comboGrace = f[i++].toIntOrNull() ?: 0
                if (i < f.size) {
                    val cs = f[i++]
                    if (cs.isNotEmpty()) {
                        val cp = cs.split(',')
                        if (cp.size == 5) e.contract = Contract(cp[0].toInt(), cp[1].toInt(), cp[2].toInt(), cp[3].toInt(), cp[4].toInt())
                    }
                }
                if (i < f.size) {
                    val sc = f[i++].split(',')
                    if (sc.size == e.board.stones.size) for (k in sc.indices) e.board.stones[k] = sc[k] == "1"
                }
                if (i < f.size) {
                    val mc = f[i++].split(',')
                    if (mc.size == e.board.mults.size) for (k in mc.indices) e.board.mults[k] = mc[k] == "1"
                }
                if (i < f.size) {
                    val tm = f[i++].split(',')
                    for (k in 0..2) e.trayMult[k] = tm.getOrNull(k)?.toIntOrNull() ?: -1
                }
                // v8 tail
                if (i < f.size) {
                    val nc = f[i++].split(',')
                    if (nc.size == e.board.nums.size) for (k in nc.indices) e.board.nums[k] = nc[k].toIntOrNull() ?: 0
                }
                if (i < f.size) {
                    val tn = f[i++].split(',')
                    for (k in 0..2) e.trayNum[k] = tn.getOrNull(k)?.toIntOrNull() ?: -1
                }
                if (i < f.size) {
                    val av = f[i++].split(',')
                    e.avalancheIn = av.getOrNull(0)?.toIntOrNull() ?: 0
                    e.avalancheCount = av.getOrNull(1)?.toIntOrNull() ?: 0
                }
                if (i < f.size) {
                    val bp = f[i++].split(',')
                    e.bossPhase = bp.getOrNull(0)?.toIntOrNull() ?: 0
                    e.bossPhases = bp.getOrNull(1)?.toIntOrNull() ?: 0
                }
                if (i < f.size) {
                    for (s in f[i].split(',')) s.toIntOrNull()?.let { e.scoreCurve.add(it) }
                }
                e
            } catch (ex: Exception) { null }
        }
    }
}

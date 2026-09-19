package com.fareza.blokku.core

import kotlin.random.Random

enum class GoalType { NONE, SCORE, LINES, CELLS, STONES }

class Goal(val type: GoalType, val target: Int)

enum class Mode { CLASSIC, LEVEL, DAILY, ZEN, RUSH, PUZZLE }

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

    // ---- next-tray preview ----
    /** The three pieces that will refill the tray when it empties. */
    val nextTray = arrayOfNulls<Piece>(3)
    val nextGem = intArrayOf(-1, -1, -1)
    val nextBomb = intArrayOf(-1, -1, -1)
    val nextMult = intArrayOf(-1, -1, -1)

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
        return Snapshot(
            c, tray.copyOf(), score, combo, streak, bestCombo,
            linesCleared, cellsPlaced, meter, movesLeft, gameOver, goalMet, goalFailed,
            g, trayGem.copyOf(), b, trayBomb.copyOf(),
            m, trayMult.copyOf(), st,
            holdPiece, holdGem, holdBomb, holdMult, holdLocked, comboGrace,
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
    }

    val canUndo get() = undoStack.isNotEmpty()

    fun undo(): Boolean {
        val s = undoStack.removeLastOrNull() ?: return false
        restore(s)
        return true
    }

    fun refillTray() {
        // full refill consumes the previewed next-tray so the preview is honest
        for (i in 0..2) {
            if (tray[i] == null && nextTray[i] != null) {
                tray[i] = nextTray[i]; nextTray[i] = null
                trayGem[i] = nextGem[i]; trayBomb[i] = nextBomb[i]; trayMult[i] = nextMult[i]
                nextGem[i] = -1; nextBomb[i] = -1; nextMult[i] = -1
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
            nextGem[i] = if (rng.nextFloat() < gemChance) p.cells[rng.nextInt(p.cells.size)] else -1
            nextBomb[i] = if (rng.nextFloat() < 0.13f) {
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
        }
    }

    /** ~1-in-5 fresh pieces carry a gem cell worth coins when its line clears,
     *  ~1-in-8 carry a bomb cell that detonates 3x3 when its line clears. */
    private fun rollSpecial(i: Int) {
        val p = tray[i]
        trayGem[i] = if (p != null && rng.nextFloat() < gemChance) p.cells[rng.nextInt(p.cells.size)] else -1
        trayBomb[i] = if (p != null && rng.nextFloat() < 0.13f) {
            // prefer a different cell than the gem so the piece reads clearly
            var pick = p.cells[rng.nextInt(p.cells.size)]
            var tries = 0
            while (pick == trayGem[i] && tries++ < 4) pick = p.cells[rng.nextInt(p.cells.size)]
            pick
        } else -1
        trayMult[i] = if (p != null && rng.nextFloat() < 0.07f) {
            var pick = p.cells[rng.nextInt(p.cells.size)]
            var tries = 0
            while ((pick == trayGem[i] || pick == trayBomb[i]) && tries++ < 4) pick = p.cells[rng.nextInt(p.cells.size)]
            pick
        } else -1
    }

    private fun nextPiece(): Piece =
        // Zen stays gentle: small shapes dominate so the board rarely jams
        Shapes.randomPiece(rng, colorCount, bigBias, if (mode == Mode.ZEN) 1.6f else 0f)

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
        if (trayEmpty()) refillTray()
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
        if (gameOver) return PlaceResult(false)
        val p = tray[i] ?: return PlaceResult(false)
        if (!board.fits(p, row, col)) return PlaceResult(false)

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
        if (!res.isEmpty) {
            for (cc in res.clearCells) if (board.gems[cc]) gems++
            // MONO — a cleared line of a single colour scores +150% extra
            val pre = board.cells.copyOf()
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
            comboGrace = 1 // one free pass before the chain drops
            if (combo > bestCombo) bestCombo = combo
            gained += (res.clearCells.size + boom.size) * 10 + n * n * 40 + bombsN * 60
            gained += (gained * (0.5f * monoLines)).toInt()
            if (multHit) gained *= 3
            gained = (gained * (1f + 0.5f * (combo - 1))).toInt()
            // PERFECT CLEAR — emptied the whole board
            if (board.filledCount() == 0) { perfect = true; gained += 400 + combo * 100 }
            if (feverT > 0f) gained *= 2
            meter += n * 12 + (if (combo > 1) 8 * combo else 0) + bombsN * 10
            if (mode == Mode.RUSH) meter += n * 10 // fever charges ~2x in Rush
            if (meter >= 100) { meter -= 100; meterFullNow = true; feverT = 8f }
        } else {
            // combo grace: the chain survives one non-clearing placement, then drops
            if (combo > 0) {
                if (comboGrace > 0) comboGrace-- else combo = 0
            }
            streak = 0
            meter += 2
        }
        if (snug) gained += 25 + p.size * 5
        lastGemsCollected = gems
        score += gained
        tickContract(res.lineCount, bombsN, snug)

        // classic difficulty ramp: bigger pieces weigh more over time and a
        // 7th color joins once the player is in flow
        if (mode == Mode.CLASSIC) {
            bigBias = score / 1800f
            colorCount = if (score >= 1500) 7 else 6
        }

        if (trayEmpty()) refillTray()
        evaluateEnd()
        return PlaceResult(true, res.clearCells, res.lineCount, gained, combo, meterFullNow, gems, perfect, boom, bombsN, monoLines, snug, multHit)
    }

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
        if (trayEmpty()) refillTray()
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
        when (goal.type) {
            GoalType.SCORE -> if (score >= goal.target) goalMet = true
            GoalType.LINES -> if (linesCleared >= goal.target) goalMet = true
            GoalType.CELLS -> if (cellsPlaced >= goal.target) goalMet = true
            GoalType.STONES -> if (board.stoneCount() == 0) goalMet = true
            GoalType.NONE -> {}
        }
        if (goalMet) return
        if (movesLeft == 0 && goal.type != GoalType.NONE) { goalFailed = true; gameOver = true; return }
        if (!anyTrayFit()) gameOver = true
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
            board.cells[i] = 0; board.gems[i] = false; board.bombs[i] = false; board.mults[i] = false
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
        return sb.toString()
    }

    companion object {
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
            Puzzles.seedBoard(e, def.index)
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
                    val tm = f[i].split(',')
                    for (k in 0..2) e.trayMult[k] = tm.getOrNull(k)?.toIntOrNull() ?: -1
                }
                e
            } catch (ex: Exception) { null }
        }
    }
}

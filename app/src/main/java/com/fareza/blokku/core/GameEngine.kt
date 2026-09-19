package com.fareza.blokku.core

import kotlin.random.Random

enum class GoalType { NONE, SCORE, LINES, CELLS }

class Goal(val type: GoalType, val target: Int)

enum class Mode { CLASSIC, LEVEL, DAILY }

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
    /** Gems collected by the last destructive op (blast/revive), for coin award. */
    var lastGemsCollected = 0
        private set

    private val undoStack = ArrayDeque<Snapshot>()

    init {
        refillTray()
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
    )

    private fun snapshot(): Snapshot {
        val c = IntArray(board.cells.size)
        board.cells.copyInto(c)
        val g = BooleanArray(board.gems.size)
        board.gems.copyInto(g)
        return Snapshot(
            c, tray.copyOf(), score, combo, streak, bestCombo,
            linesCleared, cellsPlaced, meter, movesLeft, gameOver, goalMet, goalFailed,
            g, trayGem.copyOf(),
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
    }

    val canUndo get() = undoStack.isNotEmpty()

    fun undo(): Boolean {
        val s = undoStack.removeLastOrNull() ?: return false
        restore(s)
        return true
    }

    fun refillTray() {
        for (i in 0..2) if (tray[i] == null) { tray[i] = nextPiece(); rollGem(i) }
        // fairness: if nothing fits, keep re-rolling (bounded) so death feels earned, not cheap
        var tries = 0
        while (!anyTrayFit() && tries < 30) {
            for (i in 0..2) if (tray[i] != null && !board.anyFit(tray[i]!!)) {
                tray[i] = nextPiece(); rollGem(i)
            }
            tries++
        }
    }

    /** ~1-in-5 fresh pieces carry a gem cell worth coins when its line clears. */
    private fun rollGem(i: Int) {
        val p = tray[i]
        trayGem[i] = if (p != null && rng.nextFloat() < 0.2f) p.cells[rng.nextInt(p.cells.size)] else -1
    }

    private fun nextPiece(): Piece = Shapes.randomPiece(rng, colorCount, bigBias)

    /** Re-roll tray entries that don't fit (bounded) — used after preset board fills. */
    fun ensureTrayFits() {
        var tries = 0
        while (!anyTrayFit() && tries < 30) {
            for (i in 0..2) if (tray[i] != null && !board.anyFit(tray[i]!!)) { tray[i] = nextPiece(); rollGem(i) }
            tries++
        }
    }

    /** Timed levels: called when the countdown hits zero. */
    fun onTimeExpired() {
        if (gameOver || goalMet) return
        if (goal.type != GoalType.NONE) { goalFailed = true; gameOver = true }
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

        board.place(p, row, col)
        // gem inside the piece lands on the board
        val g = trayGem[i]
        if (g >= 0) board.gems[(row + (g shr 4)) * board.size + col + (g and 15)] = true
        trayGem[i] = -1
        tray[i] = null
        cellsPlaced += p.size
        if (movesLeft > 0) movesLeft--

        val res = boardPostClears()

        var gained = p.size * 2
        var meterFullNow = false
        var gems = 0
        var perfect = false
        if (!res.isEmpty) {
            for (cc in res.clearCells) if (board.gems[cc]) gems++
            board.applyClear(res.clearCells)
            val n = res.lineCount
            linesCleared += n
            combo++
            streak++
            if (combo > bestCombo) bestCombo = combo
            gained += res.clearCells.size * 10 + n * n * 40
            gained = (gained * (1f + 0.5f * (combo - 1))).toInt()
            // PERFECT CLEAR — emptied the whole board
            if (board.filledCount() == 0) { perfect = true; gained += 400 + combo * 100 }
            if (feverT > 0f) gained *= 2
            meter += n * 12 + (if (combo > 1) 8 * combo else 0)
            if (meter >= 100) { meter -= 100; meterFullNow = true; feverT = 8f }
        } else {
            if (combo > 0) combo = 0
            streak = 0
            meter += 2
        }
        lastGemsCollected = gems
        score += gained

        // classic difficulty ramp: bigger pieces weigh more over time and a
        // 7th color joins once the player is in flow
        if (mode == Mode.CLASSIC) {
            bigBias = score / 1800f
            colorCount = if (score >= 1500) 7 else 6
        }

        if (trayEmpty()) refillTray()
        evaluateEnd()
        return PlaceResult(true, res.clearCells, res.lineCount, gained, combo, meterFullNow, gems, perfect)
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
        for (i in 0..2) if (tray[i] != null) { tray[i] = nextPiece(); rollGem(i) }
        evaluateEnd()
    }

    /** Clear a radius-1 (3x3) area centered at row/col (bomb power-up). */
    fun blastArea(row: Int, col: Int): IntArray {
        undoStack.addLast(snapshot())
        lastGemsCollected = 0
        for (r in (row - 1)..(row + 1)) for (c in (col - 1)..(col + 1)) {
            if (r in 0 until board.size && c in 0 until board.size && board.gems[r * board.size + c]) lastGemsCollected++
        }
        val removed = board.clearArea(row, col, 1)
        if (trayEmpty()) refillTray()
        evaluateEnd()
        return removed
    }

    /** Continue after game over: clears the densest 3x3 region and resumes. */
    fun reviveClear(): IntArray {
        val best = densestArea()
        lastGemsCollected = 0
        for (r in (best.first - 1)..(best.first + 1)) for (c in (best.second - 1)..(best.second + 1)) {
            if (r in 0 until board.size && c in 0 until board.size && board.gems[r * board.size + c]) lastGemsCollected++
        }
        val removed = board.clearArea(best.first, best.second, 1)
        gameOver = false
        evaluateEnd()
        return removed
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
        when (goal.type) {
            GoalType.SCORE -> if (score >= goal.target) goalMet = true
            GoalType.LINES -> if (linesCleared >= goal.target) goalMet = true
            GoalType.CELLS -> if (cellsPlaced >= goal.target) goalMet = true
            GoalType.NONE -> {}
        }
        if (goalMet) return
        if (movesLeft == 0 && goal.type != GoalType.NONE) { goalFailed = true; gameOver = true; return }
        if (!anyTrayFit()) gameOver = true
    }

    fun goalProgress(): Int = when (goal.type) {
        GoalType.SCORE -> score
        GoalType.LINES -> linesCleared
        GoalType.CELLS -> cellsPlaced
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
                e
            } catch (ex: Exception) { null }
        }
    }
}

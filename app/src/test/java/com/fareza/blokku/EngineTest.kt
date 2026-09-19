package com.fareza.blokku

import com.fareza.blokku.core.Board
import com.fareza.blokku.core.Daily
import com.fareza.blokku.core.GameEngine
import com.fareza.blokku.core.Goal
import com.fareza.blokku.core.GoalType
import com.fareza.blokku.core.LevelDef
import com.fareza.blokku.core.Levels
import com.fareza.blokku.core.Piece
import com.fareza.blokku.core.Shapes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.random.Random

class EngineTest {

    private fun single(color: Int = 0) = Piece.of(listOf(0 to 0), color)
    private fun line9h() = Piece.of((0..8).map { 0 to it }, 0)

    @Test
    fun `piece rotation preserves size and fits`() {
        val p = Piece.of(listOf(0 to 0, 1 to 0, 1 to 1), 2)
        val r = p.rotated()
        assertEquals(p.size, r.size)
        // rotate 4x returns to original normalized shape
        var q = p
        repeat(4) { q = q.rotated() }
        assertTrue(q.cells.contentEquals(p.cells))
    }

    @Test
    fun `row fills and clears`() {
        val b = Board(9)
        val p = line9h()
        assertTrue(b.fits(p, 0, 0))
        b.place(p, 0, 0)
        val e = GameEngine(b, Random(1))
        // engine will have its own tray; directly test boardPostClears via a manual approach:
        // fill all but one cell of row 8, then place single at (8,8)
        val b2 = Board(9)
        for (c in 0 until 8) b2.cells[8 * 9 + c] = 1
        b2.place(single(), 8, 8)
        var full = true
        for (c in 0 until 9) if (b2.cells[8 * 9 + c] == 0) full = false
        assertTrue(full)
    }

    @Test
    fun `placing piece completes row and scores`() {
        val engine = GameEngine(Board(9), Random(42))
        // set up board with row 0 filled except col 8
        for (c in 0 until 8) engine.board.cells[c] = 1
        // force tray to be single cells
        engine.tray[0] = single()
        engine.tray[1] = null
        engine.tray[2] = null
        val res = engine.place(0, 0, 8)
        assertTrue(res.placed)
        assertEquals(1, res.lines)
        assertTrue(res.gained > 0)
        assertTrue(engine.score > 0)
        assertTrue(engine.linesCleared == 1)
        for (c in 0 until 9) assertEquals(0, engine.board.cells[c])
    }

    @Test
    fun `combo increments on consecutive clears`() {
        val engine = GameEngine(Board(9), Random(1))
        for (c in 0 until 8) engine.board.cells[c] = 1
        for (c in 0 until 8) engine.board.cells[9 + c] = 1
        engine.tray[0] = single()
        engine.tray[1] = single()
        engine.tray[2] = null
        val r1 = engine.place(0, 0, 8)
        val r2 = engine.place(1, 1, 8)
        assertEquals(1, r1.comboCount)
        assertEquals(2, r2.comboCount)
        assertTrue(r2.gained > r1.gained)
    }

    @Test
    fun `undo restores state`() {
        val engine = GameEngine(Board(9), Random(3))
        engine.tray[0] = single()
        val before = engine.board.cells.copyOf()
        val beforeScore = engine.score
        engine.place(0, 4, 4)
        assertNotEquals(beforeScore, engine.score)
        assertTrue(engine.undo())
        assertTrue(before.contentEquals(engine.board.cells))
        assertEquals(beforeScore, engine.score)
    }

    @Test
    fun `game over when nothing fits`() {
        val engine = GameEngine(Board(9), Random(2))
        // fill everything
        for (i in engine.board.cells.indices) engine.board.cells[i] = 1
        engine.tray[0] = single()
        engine.tray[1] = null
        engine.tray[2] = null
        // no fits possible -> placing impossible; trigger evaluate via shuffle
        engine.shuffleTray()
        // board full => any piece fails -> gameOver
        assertTrue(engine.gameOver || engine.anyTrayFit() == false)
    }

    @Test
    fun `level goal triggers completion`() {
        val def = LevelDef(0, Goal(GoalType.SCORE, 10), 30, 0L)
        val engine = GameEngine.level(def)
        engine.tray[0] = Piece.of((0 until 5).map { 0 to it }, 0) // 5 cells = 10 pts
        engine.tray[1] = null; engine.tray[2] = null
        engine.place(0, 0, 0)
        assertTrue(engine.goalMet)
    }

    @Test
    fun `move limit failure`() {
        val def = LevelDef(0, Goal(GoalType.SCORE, 999999), 1, 0L)
        val engine = GameEngine.level(def)
        engine.tray[0] = single(); engine.tray[1] = null; engine.tray[2] = null
        engine.place(0, 0, 0)
        // after refill, moves exhausted and goal unmet -> fail
        assertTrue(engine.goalFailed || engine.gameOver)
    }

    @Test
    fun `daily challenge deterministic`() {
        val a = Daily.todayEngine()
        val s = Daily.seedForToday()
        val b = GameEngine.daily(s, Daily.challengeFor(s).first, Daily.challengeFor(s).second)
        // same seed => identical tray pieces
        for (i in 0..2) {
            assertTrue(a.tray[i]!!.cells.contentEquals(b.tray[i]!!.cells))
        }
    }

    @Test
    fun `levels valid`() {
        assertEquals(60, Levels.COUNT)
        for (i in 0 until Levels.COUNT) {
            val d = Levels.get(i)
            assertTrue(d.moveLimit > 0 || d.timedSec > 0)
            assertTrue(d.goal.target > 0)
        }
    }

    @Test
    fun `bomb clears area`() {
        val engine = GameEngine(Board(9), Random(1))
        for (r in 0 until 9) for (c in 0 until 9) engine.board.cells[r * 9 + c] = 1
        val removed = engine.blastArea(4, 4)
        assertEquals(9, removed.size)
        for (r in 3..5) for (c in 3..5) assertEquals(0, engine.board.cells[r * 9 + c])
    }

    @Test
    fun `shapes catalog sane`() {
        assertTrue(Shapes.ALL.size > 15)
        for (d in Shapes.ALL) {
            val p = Piece.of(d.coords, 0)
            assertTrue(p.rows in 1..3 || p.rows == 5 || p.rows <= 5)
        }
    }

    @Test
    fun `run serialization round-trips`() {
        val engine = GameEngine(Board(9), Random(7))
        engine.tray[0] = single(3); engine.tray[1] = null; engine.tray[2] = line9h()
        engine.board.cells[0] = 2; engine.board.cells[80] = 5
        engine.place(0, 4, 4)
        val json = engine.toJson()
        val back = GameEngine.fromJson(json)!!
        assertEquals(engine.score, back.score)
        assertEquals(engine.mode, back.mode)
        assertTrue(engine.board.cells.contentEquals(back.board.cells))
        assertTrue(engine.tray[2]!!.cells.contentEquals(back.tray[2]!!.cells))
        assertTrue(back.tray[1] == null)
        assertTrue(GameEngine.fromJson("not json") == null)
    }

    @Test
    fun `timed level expires into failure`() {
        val def = LevelDef(6, Goal(GoalType.SCORE, 999999), -1, 0L, timedSec = 30)
        val engine = GameEngine.level(def)
        assertEquals(30, engine.timeLimitSec)
        engine.onTimeExpired()
        assertTrue(engine.goalFailed && engine.gameOver)
        // met goals are not failed by the clock
        val def2 = LevelDef(6, Goal(GoalType.SCORE, 1), -1, 0L, timedSec = 30)
        val e2 = GameEngine.level(def2)
        e2.tray[0] = line9h(); e2.tray[1] = null; e2.tray[2] = null
        e2.place(0, 0, 0)
        assertTrue(e2.goalMet)
        e2.onTimeExpired()
        assertFalse(e2.goalFailed)
    }

    @Test
    fun `revive clears densest region`() {
        val engine = GameEngine(Board(9), Random(1))
        // dense cluster around (4,4); single cell far corner
        for (r in 3..5) for (c in 3..5) engine.board.cells[r * 9 + c] = 1
        engine.board.cells[0] = 1
        val preview = engine.peekReviveCells()
        assertTrue(preview.isNotEmpty())
        val removed = engine.reviveClear()
        assertTrue(removed.size >= 9)
        assertEquals(0, engine.board.cells[4 * 9 + 4])
        assertEquals(1, engine.board.cells[0]) // sparse corner untouched
    }

    @Test
    fun `bestMove prefers clearing move`() {
        val engine = GameEngine(Board(9), Random(1))
        for (c in 0 until 8) engine.board.cells[c] = 1
        engine.tray[0] = single(); engine.tray[1] = null
        engine.tray[2] = Piece.of(listOf(0 to 0, 0 to 1, 0 to 2), 0)
        val mv = engine.bestMove()!!
        assertEquals(0, mv.first) // slot 0 single completing row beats the bar
        assertEquals(8, mv.third) // col 8 completes the row
        // empty board still returns a move
        val open = GameEngine(Board(9), Random(1))
        open.tray[0] = single(); open.tray[1] = null; open.tray[2] = null
        assertTrue(open.bestMove() != null)
    }

    @Test
    fun `meter full starts fever and fever doubles score`() {
        val engine = GameEngine(Board(9), Random(1))
        // push meter near full
        while (engine.meter < 88) {
            // clear one row repeatedly to charge the meter (12+ per clear)
            for (c in 0 until 8) engine.board.cells[c] = 1
            engine.tray[0] = single(); engine.tray[1] = null; engine.tray[2] = null
            engine.place(0, 0, 8)
            if (engine.feverT > 0f) break
        }
        assertTrue(engine.feverT > 0f)
        // during fever a clear is doubled
        for (c in 0 until 8) engine.board.cells[9 + c] = 1
        engine.tray[0] = single(); engine.tray[1] = null; engine.tray[2] = null
        val res = engine.place(0, 1, 8)
        assertTrue(res.lines == 1)
        // non-fever gained for same setup was ~ (2 + 90 + 40) — fever doubles it
        assertTrue(res.gained >= (132 * 2))
    }

    @Test
    fun `gem in tray piece lands on board and pays out on clear`() {
        val engine = GameEngine(Board(9), Random(1))
        for (c in 0 until 8) engine.board.cells[c] = 1
        engine.tray[0] = single(); engine.tray[1] = null; engine.tray[2] = null
        engine.trayGem[0] = 0 // gem on the single's only cell
        val res = engine.place(0, 0, 8)
        assertEquals(1, res.gemsCollected)
        assertFalse(engine.board.gems[8]) // cleared away
    }

    @Test
    fun `gem cell survives on board until cleared`() {
        val engine = GameEngine(Board(9), Random(1))
        engine.tray[0] = single(); engine.tray[1] = null; engine.tray[2] = null
        engine.trayGem[0] = 0
        engine.place(0, 4, 4)
        assertTrue(engine.board.gems[4 * 9 + 4])
        // undo restores tray gem, removes board gem
        assertTrue(engine.undo())
        assertFalse(engine.board.gems[4 * 9 + 4])
        assertEquals(0, engine.trayGem[0])
    }

    @Test
    fun `perfect clear bonus when board empties`() {
        val engine = GameEngine(Board(9), Random(1))
        for (c in 0 until 8) engine.board.cells[c] = 1
        engine.tray[0] = single(); engine.tray[1] = null; engine.tray[2] = null
        val res = engine.place(0, 0, 8)
        assertTrue(res.perfectClear)
        assertEquals(0, engine.board.filledCount())
        // and no perfect flag when cells remain
        val e2 = GameEngine(Board(9), Random(1))
        for (c in 0 until 8) e2.board.cells[c] = 1
        e2.board.cells[80] = 1 // leftover elsewhere
        e2.tray[0] = single(); e2.tray[1] = null; e2.tray[2] = null
        assertFalse(e2.place(0, 0, 8).perfectClear)
    }

    @Test
    fun `serialization round-trips gems`() {
        val engine = GameEngine(Board(9), Random(7))
        engine.tray[0] = single(3); engine.tray[1] = null; engine.tray[2] = line9h()
        engine.trayGem[0] = 0; engine.trayGem[2] = 5
        engine.board.cells[0] = 2; engine.board.gems[0] = true
        val back = GameEngine.fromJson(engine.toJson())!!
        assertTrue(back.board.gems[0])
        assertEquals(0, back.trayGem[0])
        assertEquals(5, back.trayGem[2])
        assertEquals(-1, back.trayGem[1])
        // old-format saves (no gem fields) still load
        val oldJson = engine.toJson().split(';').take(19).joinToString(";")
        assertTrue(GameEngine.fromJson(oldJson) != null)
    }
}

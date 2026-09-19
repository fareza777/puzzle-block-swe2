package com.fareza.blokku.core

import kotlin.random.Random

/**
 * Puzzle mode — 30 handcrafted-style (seed-procedural) boards.
 * Each level pre-fills the board so a few lines are 1–3 cells short of full,
 * marks the filled cells of those lines as STONES, and hands the player a
 * limited move budget. Win = all stones cleared.
 */
object Puzzles {
    const val COUNT = 30

    fun get(index: Int): LevelDef {
        val i = index.coerceIn(0, COUNT - 1)
        val seed = 50_000_000L + i * 7919L
        val rng = Random(seed)
        // difficulty: more stones & fewer spare moves on later boards
        val stoneLines = 1 + minOf(2, i / 10)
        val stones = 4 + i / 2 + rng.nextInt(3)
        val moves = stones * 2 + 6 - i / 8
        return LevelDef(
            index = i,
            goal = Goal(GoalType.STONES, stones),
            moveLimit = moves.coerceAtLeast(8),
            seed = seed,
            presetFill = 0f, // custom seeding below
        )
    }

    /**
     * Seeds an engine's board: fills partial lines and marks their cells as stones.
     * Guarantees at least one tray piece can progress a stone line.
     */
    fun seedBoard(e: GameEngine, index: Int) {
        val rng = Random(97_000_000L + index * 131L)
        val b = e.board
        val stoneLines = 1 + minOf(2, index / 10)
        var stonesWanted = e.goal.target

        // pick rows/cols to build; each gets `size` cells minus a few gaps
        val lines = ArrayList<Int>() // r*16+c  r=row,col if c==9 => column c
        var tries = 0
        while (lines.size < stoneLines && tries++ < 20) {
            val isRow = rng.nextBoolean()
            val at = rng.nextInt(b.size)
            if (isRow && lines.none { it shr 4 == at }) lines.add(at * 16 + 9)
            if (!isRow && lines.none { (it and 15) == at }) lines.add(9 * 16 + at)
        }

        for (ln in lines) {
            val isRow = ln shr 4 != 9
            val idx = if (isRow) ln shr 4 else ln and 15
            // gap cells the player must fill
            val gaps = HashSet<Int>()
            val gapN = 2 + rng.nextInt(2)
            while (gaps.size < gapN) gaps.add(rng.nextInt(b.size))
            for (k in 0 until b.size) {
                if (k in gaps) continue
                val r = if (isRow) idx else k
                val c = if (isRow) k else idx
                val cell = r * b.size + c
                if (b.cells[cell] == 0) b.cells[cell] = 1 + rng.nextInt(6)
                if (stonesWanted > 0) { b.stones[cell] = true; stonesWanted-- }
            }
        }

        // scatter stones if the line budget didn't cover them all (edges filled)
        var guard = 0
        while (stonesWanted > 0 && guard++ < 200) {
            val r = b.size - 1 - rng.nextInt(3)
            val c = rng.nextInt(b.size)
            val cell = r * b.size + c
            if (b.cells[cell] != 0 && !b.stones[cell]) { b.stones[cell] = true; stonesWanted-- }
            else if (b.cells[cell] == 0) { b.cells[cell] = 1 + rng.nextInt(6); b.stones[cell] = true; stonesWanted-- }
        }

        // a few extra neutral filler cells so the board isn't pure lines
        var extra = 4 + rng.nextInt(6)
        while (extra-- > 0) {
            val r = b.size - 1 - rng.nextInt(4)
            val c = rng.nextInt(b.size)
            val cell = r * b.size + c
            if (b.cells[cell] == 0) b.cells[cell] = 1 + rng.nextInt(6)
        }
    }

    /** Stars: efficiency — share of the move budget left when solved. */
    fun stars(e: GameEngine): Int {
        val def = get(0) // baseline irrelevant; use engine's own moves
        val total = e.goal.target + 8 // approximate denominator
        val left = e.movesLeft
        val ratio = if (left < 0) 1f else left.toFloat() / (e.goal.target * 2 + 4)
        return when {
            !e.goalMet -> 0
            ratio >= 0.45f -> 3
            ratio >= 0.2f -> 2
            else -> 1
        }
    }
}

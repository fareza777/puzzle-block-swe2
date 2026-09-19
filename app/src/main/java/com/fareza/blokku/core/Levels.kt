package com.fareza.blokku.core

object Levels {

    const val COUNT = 60

    private val defs: List<LevelDef> = (0 until COUNT).map { i ->
        val t = i + 1
        // difficulty ramps: more moves & goals scale
        val goal = when (i % 3) {
            0 -> Goal(GoalType.SCORE, 300 + i * 90)
            1 -> Goal(GoalType.LINES, minOf(3 + i / 3, 14))
            else -> Goal(GoalType.CELLS, minOf(20 + i * 2, 90))
        }
        val moves = when (i % 3) {
            0 -> 25 + (i / 8)
            1 -> 18 + (i / 6)
            else -> 14 + (i / 6)
        }
        LevelDef(i, goal, moves, seed = 90210L + i * 7919)
    }

    fun get(i: Int): LevelDef = defs[i.coerceIn(0, COUNT - 1)]

    /** Star rating 1-3 given leftovers: more moves remaining & higher combo = more stars. */
    fun stars(engine: GameEngine, def: LevelDef): Int {
        if (!engine.goalMet) return 0
        val efficiency = engine.movesLeft.toFloat() / def.moveLimit.toFloat()
        return when {
            efficiency >= 0.35f || engine.bestCombo >= 4 -> 3
            efficiency >= 0.12f || engine.bestCombo >= 2 -> 2
            else -> 1
        }
    }
}

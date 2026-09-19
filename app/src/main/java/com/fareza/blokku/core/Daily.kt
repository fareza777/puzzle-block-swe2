package com.fareza.blokku.core

import java.util.Calendar
import kotlin.random.Random

object Daily {

    fun seedForToday(): Long = seedFor(Calendar.getInstance())

    fun seedFor(cal: Calendar): Long {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return y * 10000L + m * 100 + d
    }

    fun challengeFor(seed: Long): Pair<Goal, Int> {
        val r = Random(seed * 31 + 7)
        return when (r.nextInt(3)) {
            0 -> Goal(GoalType.SCORE, 600 + r.nextInt(6) * 100) to 28 + r.nextInt(8)
            1 -> Goal(GoalType.LINES, 6 + r.nextInt(5)) to 22 + r.nextInt(8)
            else -> Goal(GoalType.CELLS, 35 + r.nextInt(20)) to 20 + r.nextInt(8)
        }
    }

    fun dateKey(): Long = seedForToday()

    fun todayEngine(): GameEngine {
        val seed = seedForToday()
        val (goal, moves) = challengeFor(seed)
        return GameEngine.daily(seed, goal, moves)
    }
}

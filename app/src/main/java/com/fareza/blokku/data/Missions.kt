package com.fareza.blokku.data

import com.fareza.blokku.R
import com.fareza.blokku.core.Daily
import com.fareza.blokku.core.GameEngine
import kotlin.random.Random

enum class MissionType { SCORE_GAME, LINES_TOTAL, CELLS_TOTAL, COMBO_ONCE, PLAY_GAMES, USE_POWERUPS }

class Mission(
    val id: String,
    val type: MissionType,
    val target: Int,
    val reward: Int,
    val labelRes: Int,
)

/**
 * Three rotating daily missions (date-seeded, offline) + fixed achievements.
 */
object Missions {

    class Template(val type: MissionType, val min: Int, val max: Int, val labelRes: Int, val rewardBase: Int)

    private val pool = listOf(
        Template(MissionType.SCORE_GAME, 400, 1500, R.string.mission_score_game, 40),
        Template(MissionType.LINES_TOTAL, 10, 40, R.string.mission_lines_total, 30),
        Template(MissionType.CELLS_TOTAL, 50, 180, R.string.mission_cells_total, 30),
        Template(MissionType.COMBO_ONCE, 2, 5, R.string.mission_combo_once, 50),
        Template(MissionType.PLAY_GAMES, 2, 6, R.string.mission_play_games, 25),
        Template(MissionType.USE_POWERUPS, 1, 4, R.string.mission_use_powerups, 35),
    )

    /** One long-horizon mission per week — bigger targets, bigger payout. */
    private val weekPool = listOf(
        Template(MissionType.LINES_TOTAL, 60, 140, R.string.mission_lines_total, 220),
        Template(MissionType.CELLS_TOTAL, 300, 700, R.string.mission_cells_total, 220),
        Template(MissionType.COMBO_ONCE, 4, 7, R.string.mission_combo_once, 280),
        Template(MissionType.USE_POWERUPS, 6, 12, R.string.mission_use_powerups, 240),
        Template(MissionType.PLAY_GAMES, 8, 16, R.string.mission_play_games, 200),
        Template(MissionType.SCORE_GAME, 1500, 4000, R.string.mission_score_game, 300),
    )

    /** This week's mission (id "w"); rotates with the calendar week seed. */
    fun thisWeek(): Mission {
        val wk = Save.weekSeed().toLong()
        if (Save.missionsJson.optLong("_week") != wk) {
            Save.missionsJson.remove("w_p")
            Save.missionsJson.remove("w_done")
            Save.missionsJson.put("_week", wk)
            Save.saveMissions()
        }
        val r = Random(wk * 31 + 5)
        val t = weekPool[r.nextInt(weekPool.size)]
        val target = t.min + r.nextInt(t.max - t.min + 1)
        return Mission("w", t.type, target, t.rewardBase + target / 15, t.labelRes)
    }

    fun today(): List<Mission> {
        val key = Daily.seedForToday()
        if (Save.missionDate() != key) {
            // fresh day — wipe progress for the new mission set
            for (t in pool.indices) {
                Save.missionsJson.remove("d${t}_p")
                Save.missionsJson.remove("d${t}_done")
            }
            Save.setMissionDate(key)
        }
        val r = Random(key * 97 + 13)
        val indices = pool.indices.shuffled(r).take(3)
        return indices.mapIndexed { slot, pi ->
            val t = pool[pi]
            val target = t.min + r.nextInt(t.max - t.min + 1)
            Mission("d$pi", t.type, target, t.rewardBase + target / 20, t.labelRes)
        }
    }

    fun progress(m: Mission): Int = Save.missionProgress(m.id).coerceAtMost(m.target)
    fun done(m: Mission) = Save.missionDone(m.id)
    fun claimable(m: Mission) = progress(m) >= m.target && !done(m)

    fun claim(m: Mission): Int {
        if (!claimable(m)) return 0
        Save.claimMission(m.id)
        Save.coins += m.reward
        return m.reward
    }

    /** Feed a game event into mission progress (daily set + weekly). */
    fun track(type: MissionType, amount: Int, engine: GameEngine? = null) {
        for (m in today() + thisWeek()) {
            if (m.type != type || done(m)) continue
            val cur = Save.missionProgress(m.id)
            val newV = when (type) {
                MissionType.SCORE_GAME -> maxOf(cur, amount)
                MissionType.COMBO_ONCE -> maxOf(cur, amount)
                else -> cur + amount
            }
            if (newV != cur) Save.setMissionProgress(m.id, newV)
        }
    }
}

class Achievement(val id: String, val labelRes: Int, val reward: Int, val check: () -> Boolean)

object Achievements {

    val ALL: List<Achievement> = listOf(
        Achievement("first_game", R.string.ach_first_game, 30) { Save.gamesPlayed >= 1 },
        Achievement("score_1k", R.string.ach_score_1k, 60) { Save.bestSingleScore >= 1000 },
        Achievement("score_5k", R.string.ach_score_5k, 150) { Save.bestSingleScore >= 5000 },
        Achievement("combo_4", R.string.ach_combo_4, 60) { Save.lifetimeBestCombo >= 4 },
        Achievement("combo_7", R.string.ach_combo_7, 120) { Save.lifetimeBestCombo >= 7 },
        Achievement("lines_100", R.string.ach_lines_100, 50) { Save.totalLines >= 100 },
        Achievement("lines_1000", R.string.ach_lines_1000, 150) { Save.totalLines >= 1000 },
        Achievement("cells_1000", R.string.ach_cells_1000, 50) { Save.totalCells >= 1000 },
        Achievement("daily_3", R.string.ach_daily_3, 80) { Save.dailiesDone >= 3 },
        Achievement("level_10", R.string.ach_level_10, 80) { Save.stars(9) > 0 },
        Achievement("themes_3", R.string.ach_themes_3, 60) { countOwnedThemes() >= 3 },
        Achievement("coins_500", R.string.ach_coins_500, 50) { Save.coins >= 500 },
    )

    private fun countOwnedThemes() = Themes.ALL.count { Save.themeUnlocked(it.id) }

    /** Returns newly-unlocked achievements since last check. */
    fun checkAll(): List<Achievement> {
        val fresh = ArrayList<Achievement>()
        for (a in ALL) {
            if (!Save.achievementDone(a.id) && a.check()) {
                Save.grantAchievement(a.id)
                Save.coins += a.reward
                fresh.add(a)
            }
        }
        return fresh
    }
}

package com.fareza.blokku.data

import com.fareza.blokku.core.Daily
import com.fareza.blokku.core.GameEngine
import kotlin.random.Random

enum class MissionType { SCORE_GAME, LINES_TOTAL, CELLS_TOTAL, COMBO_ONCE, PLAY_GAMES, USE_POWERUPS }

class Mission(
    val id: String,
    val type: MissionType,
    val target: Int,
    val reward: Int,
    val label: String,
)

/**
 * Three rotating daily missions (date-seeded, offline) + fixed achievements.
 */
object Missions {

    class Template(val type: MissionType, val min: Int, val max: Int, val labelFmt: String, val rewardBase: Int)

    private val pool = listOf(
        Template(MissionType.SCORE_GAME, 400, 1500, "Score %d+ in one game", 40),
        Template(MissionType.LINES_TOTAL, 10, 40, "Clear %d lines", 30),
        Template(MissionType.CELLS_TOTAL, 50, 180, "Place %d blocks", 30),
        Template(MissionType.COMBO_ONCE, 2, 5, "Reach a %d× combo", 50),
        Template(MissionType.PLAY_GAMES, 2, 6, "Play %d games", 25),
        Template(MissionType.USE_POWERUPS, 1, 4, "Use %d power-ups", 35),
    )

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
            Mission("d$pi", t.type, target, t.rewardBase + target / 20, String.format(t.labelFmt, target))
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

    /** Feed a game event into mission progress (accumulative types). */
    fun track(type: MissionType, amount: Int, engine: GameEngine? = null) {
        for (m in today()) {
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

class Achievement(val id: String, val label: String, val reward: Int, val check: () -> Boolean)

object Achievements {

    val ALL: List<Achievement> = listOf(
        Achievement("first_game", "Play your first game", 30) { Save.gamesPlayed >= 1 },
        Achievement("score_1k", "Score 1,000 in a game", 60) { Save.bestSingleScore >= 1000 },
        Achievement("score_5k", "Score 5,000 in a game", 150) { Save.bestSingleScore >= 5000 },
        Achievement("combo_4", "Reach a 4× combo", 60) { Save.lifetimeBestCombo >= 4 },
        Achievement("combo_7", "Reach a 7× combo", 120) { Save.lifetimeBestCombo >= 7 },
        Achievement("lines_100", "Clear 100 lines total", 50) { Save.totalLines >= 100 },
        Achievement("lines_1000", "Clear 1,000 lines total", 150) { Save.totalLines >= 1000 },
        Achievement("cells_1000", "Place 1,000 blocks", 50) { Save.totalCells >= 1000 },
        Achievement("daily_3", "Finish 3 daily challenges", 80) { Save.dailiesDone >= 3 },
        Achievement("level_10", "Beat level 10", 80) { Save.stars(9) > 0 },
        Achievement("themes_3", "Own 3 themes", 60) { countOwnedThemes() >= 3 },
        Achievement("coins_500", "Hold 500 coins", 50) { Save.coins >= 500 },
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

package com.fareza.blokku.data

import android.content.Context
import android.content.SharedPreferences
import com.fareza.blokku.core.Levels
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local-only persistence. No login, no backend — everything lives in SharedPreferences.
 */
object Save {

    private lateinit var p: SharedPreferences

    fun init(context: Context) {
        if (::p.isInitialized) return
        p = context.getSharedPreferences("blokku_save", Context.MODE_PRIVATE)
        loadJsonState()
    }

    // ---- settings ----
    var soundOn get() = p.getBoolean("soundOn", true); set(v) = p.edit().putBoolean("soundOn", v).apply()
    var musicOn get() = p.getBoolean("musicOn", true); set(v) = p.edit().putBoolean("musicOn", v).apply()
    var vibrationOn get() = p.getBoolean("vibrationOn", true); set(v) = p.edit().putBoolean("vibrationOn", v).apply()
    var colorblind get() = p.getBoolean("colorblind", false); set(v) = p.edit().putBoolean("colorblind", v).apply()
    var language get() = p.getString("lang", "")!!; set(v) = p.edit().putString("lang", v).apply()
    var tutorialDone get() = p.getBoolean("tutDone", false); set(v) = p.edit().putBoolean("tutDone", v).apply()

    // ---- economy ----
    var coins get() = p.getInt("coins", 120); set(v) = p.edit().putInt("coins", v).apply()
    var adsRemoved get() = p.getBoolean("adsRemoved", false); set(v) = p.edit().putBoolean("adsRemoved", v).apply()
    var lastDailyReward get() = p.getString("lastDailyReward", "")!!; set(v) = p.edit().putString("lastDailyReward", v).apply()

    // ---- power-up inventory ----
    fun powerUps(kind: PowerKind) = p.getInt("pw_${kind.name.lowercase()}", defaultStock(kind))
    fun setPowerUps(kind: PowerKind, n: Int) = p.edit().putInt("pw_${kind.name.lowercase()}", n).apply()
    fun addPowerUp(kind: PowerKind, n: Int = 1) = setPowerUps(kind, powerUps(kind) + n)
    fun usePowerUp(kind: PowerKind): Boolean {
        val n = powerUps(kind)
        if (n <= 0) return false
        setPowerUps(kind, n - 1)
        return true
    }

    private fun defaultStock(kind: PowerKind) = when (kind) {
        PowerKind.UNDO -> 3; PowerKind.ROTATE -> 2; PowerKind.BOMB -> 1; PowerKind.SHUFFLE -> 1; PowerKind.HINT -> 2
    }

    // ---- records ----
    var bestClassic get() = p.getInt("bestClassic", 0); set(v) = p.edit().putInt("bestClassic", v).apply()
    var bestDailyScore get() = p.getInt("bestDaily", 0); set(v) = p.edit().putInt("bestDaily", v).apply()

    // ---- stats ----
    var gamesPlayed get() = p.getInt("s_games", 0); set(v) = p.edit().putInt("s_games", v).apply()
    var totalLines get() = p.getInt("s_lines", 0); set(v) = p.edit().putInt("s_lines", v).apply()
    var totalCells get() = p.getInt("s_cells", 0); set(v) = p.edit().putInt("s_cells", v).apply()
    var lifetimeBestCombo get() = p.getInt("s_combo", 0); set(v) = p.edit().putInt("s_combo", v).apply()
    var playSeconds get() = p.getLong("s_time", 0); set(v) = p.edit().putLong("s_time", v).apply()
    var dailiesDone get() = p.getInt("s_dailies", 0); set(v) = p.edit().putInt("s_dailies", v).apply()
    var powerupsUsed get() = p.getInt("s_pu", 0); set(v) = p.edit().putInt("s_pu", v).apply()
    var bestSingleScore get() = p.getInt("s_bestSingle", 0); set(v) = p.edit().putInt("s_bestSingle", v).apply()

    // ---- ad pacing ----
    var gamesSinceAd get() = p.getInt("ads_since", 0); set(v) = p.edit().putInt("ads_since", v).apply()
    var lastAdTime get() = p.getLong("ads_time", 0); set(v) = p.edit().putLong("ads_time", v).apply()

    // ---- level stars ----
    fun stars(levelIdx: Int) = starsArr.getOrElse(levelIdx) { 0 }
    fun setStars(levelIdx: Int, n: Int) {
        if (levelIdx !in 0 until Levels.COUNT) return
        if (n <= starsArr[levelIdx]) return
        starsArr[levelIdx] = n
        p.edit().putString("stars", starsArr.joinToString(",")).apply()
    }
    val totalStars get() = starsArr.sum()
    fun maxUnlockedLevel(): Int {
        var max = 0
        for (i in 0 until Levels.COUNT) if (starsArr[i] > 0) max = i + 1
        return max.coerceAtMost(Levels.COUNT - 1)
    }

    // ---- themes ----
    var selectedTheme get() = p.getString("theme", "sunrise")!!; set(v) = p.edit().putString("theme", v).apply()
    fun themeUnlocked(id: String) = unlockedThemes.contains(id)
    fun unlockTheme(id: String) {
        unlockedThemes.add(id)
        p.edit().putString("themes", unlockedThemes.joinToString(",")).apply()
    }

    // ---- missions / achievements (JSON blob) ----
    private lateinit var starsArr: IntArray
    private lateinit var unlockedThemes: MutableSet<String>
    lateinit var missionsJson: JSONObject
    lateinit var achJson: JSONObject

    private fun loadJsonState() {
        val starStr = p.getString("stars", "")!!
        starsArr = IntArray(Levels.COUNT)
        if (starStr.isNotEmpty()) {
            starStr.split(",").forEachIndexed { i, s -> s.toIntOrNull()?.let { starsArr[i] = it } }
        }
        unlockedThemes = p.getString("themes", "sunrise")!!.split(",").filter { it.isNotEmpty() }.toMutableSet()
        unlockedThemes.add("sunrise")

        missionsJson = try { JSONObject(p.getString("missions", "{}")!!) } catch (e: Exception) { JSONObject() }
        achJson = try { JSONObject(p.getString("achievements", "{}")!!) } catch (e: Exception) { JSONObject() }
    }

    fun saveMissions() = p.edit().putString("missions", missionsJson.toString()).apply()
    fun saveAchievements() = p.edit().putString("achievements", achJson.toString()).apply()

    fun missionDone(id: String) = missionsJson.optBoolean("${id}_done")
    fun missionProgress(id: String) = missionsJson.optInt("${id}_p")
    fun setMissionProgress(id: String, v: Int) { missionsJson.put("${id}_p", v); saveMissions() }
    fun claimMission(id: String) { missionsJson.put("${id}_done", true); saveMissions() }
    fun missionDate() = missionsJson.optLong("_date")
    fun setMissionDate(d: Long) { missionsJson.put("_date", d); saveMissions() }

    fun achievementDone(id: String) = achJson.optBoolean(id)
    fun grantAchievement(id: String) { achJson.put(id, true); saveAchievements() }

    // ---- daily challenge record ----
    fun dailyDone(dateKey: Long) = p.getString("dailyDone", "")!!.split(",").contains(dateKey.toString())
    fun markDailyDone(dateKey: Long) {
        val list = p.getString("dailyDone", "")!!.split(",").filter { it.isNotEmpty() }.toMutableList()
        if (!list.contains(dateKey.toString())) list.add(dateKey.toString())
        p.edit().putString("dailyDone", list.takeLast(400).joinToString(",")).apply()
    }

    // ---- daily streak ----
    var dailyStreak get() = p.getInt("dStreak", 0); set(v) = p.edit().putInt("dStreak", v).apply()
    var lastDailyDoneKey get() = p.getLong("dLastDone", 0L); set(v) = p.edit().putLong("dLastDone", v).apply()

    /** Call when a daily challenge is completed; returns the new streak length. */
    fun bumpDailyStreak(todayKey: Long): Int {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = System.currentTimeMillis()
        cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
        val y = cal.get(java.util.Calendar.YEAR)
        val m = cal.get(java.util.Calendar.MONTH) + 1
        val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val yesterdayKey = y * 10000L + m * 100 + d
        dailyStreak = if (lastDailyDoneKey == yesterdayKey) dailyStreak + 1 else 1
        lastDailyDoneKey = todayKey
        return dailyStreak
    }

    // ---- saved run (Continue feature) ----
    var runJson get() = p.getString("runJson", "")!!; set(v) = p.edit().putString("runJson", v).apply()
    fun hasSavedRun() = runJson.isNotEmpty()
    fun clearSavedRun() { runJson = "" }

    // ---- daily reminder notification ----
    var reminderOn get() = p.getBoolean("reminder", false); set(v) = p.edit().putBoolean("reminder", v).apply()

    // ---- theme-of-the-week sale ----
    fun weekSeed(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.YEAR) * 100 + cal.get(java.util.Calendar.WEEK_OF_YEAR)
    }
}

enum class PowerKind { UNDO, ROTATE, BOMB, SHUFFLE, HINT }

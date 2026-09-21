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
    // default English; user can switch to Indonesian in Settings
    var language get() = p.getString("lang", "en")!!; set(v) = p.edit().putString("lang", v).apply()
    var tutorialDone get() = p.getBoolean("tutDone", false); set(v) = p.edit().putBoolean("tutDone", v).apply()
    var powersSeen get() = p.getBoolean("powersSeen", false); set(v) = p.edit().putBoolean("powersSeen", v).apply()

    // ---- economy ----
    var coins get() = p.getInt("coins", 120); set(v) = p.edit().putInt("coins", v).apply()
    /** Star shards: earned on big moments (fever/perfect/level win); every 5 → +200 coins. */
    var shards get() = p.getInt("shards", 0); set(v) = p.edit().putInt("shards", v).apply()
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
    var bestRush get() = p.getInt("bestRush", 0); set(v) = p.edit().putInt("bestRush", v).apply()
    var bestZen get() = p.getInt("bestZen", 0); set(v) = p.edit().putInt("bestZen", v).apply()

    // ---- stats ----
    var gamesPlayed get() = p.getInt("s_games", 0); set(v) = p.edit().putInt("s_games", v).apply()
    var totalLines get() = p.getInt("s_lines", 0); set(v) = p.edit().putInt("s_lines", v).apply()
    var totalCells get() = p.getInt("s_cells", 0); set(v) = p.edit().putInt("s_cells", v).apply()
    var lifetimeBestCombo get() = p.getInt("s_combo", 0); set(v) = p.edit().putInt("s_combo", v).apply()
    var playSeconds get() = p.getLong("s_time", 0); set(v) = p.edit().putLong("s_time", v).apply()
    var dailiesDone get() = p.getInt("s_dailies", 0); set(v) = p.edit().putInt("s_dailies", v).apply()
    var powerupsUsed get() = p.getInt("s_pu", 0); set(v) = p.edit().putInt("s_pu", v).apply()
    var bestSingleScore get() = p.getInt("s_bestSingle", 0); set(v) = p.edit().putInt("s_bestSingle", v).apply()
    var puzzleSolved get() = p.getInt("s_puzzleSolved", 0); set(v) = p.edit().putInt("s_puzzleSolved", v).apply()
    var contractsDone get() = p.getInt("s_contracts", 0); set(v) = p.edit().putInt("s_contracts", v).apply()
    var snugFits get() = p.getInt("s_snug", 0); set(v) = p.edit().putInt("s_snug", v).apply()
    var monoLines get() = p.getInt("s_mono", 0); set(v) = p.edit().putInt("s_mono", v).apply()

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

    // ---- puzzle stars ----
    fun puzzleStars(idx: Int) = puzzleStarsArr.getOrElse(idx) { 0 }
    fun setPuzzleStars(idx: Int, n: Int) {
        if (idx !in puzzleStarsArr.indices || n <= puzzleStarsArr[idx]) return
        puzzleStarsArr[idx] = n
        p.edit().putString("pstars", puzzleStarsArr.joinToString(",")).apply()
    }
    val totalPuzzleStars get() = puzzleStarsArr.sum()
    fun maxUnlockedPuzzle(): Int {
        var max = 0
        for (i in puzzleStarsArr.indices) if (puzzleStarsArr[i] > 0) max = i + 1
        return max.coerceAtMost(puzzleStarsArr.size - 1)
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
    private lateinit var puzzleStarsArr: IntArray
    private lateinit var unlockedThemes: MutableSet<String>
    lateinit var missionsJson: JSONObject
    lateinit var achJson: JSONObject

    private fun loadJsonState() {
        val starStr = p.getString("stars", "")!!
        starsArr = IntArray(Levels.COUNT)
        if (starStr.isNotEmpty()) {
            starStr.split(",").forEachIndexed { i, s -> s.toIntOrNull()?.let { starsArr[i] = it } }
        }
        puzzleStarsArr = IntArray(com.fareza.blokku.core.Puzzles.COUNT)
        val pStarStr = p.getString("pstars", "")!!
        if (pStarStr.isNotEmpty()) {
            pStarStr.split(",").forEachIndexed { i, s -> s.toIntOrNull()?.let { if (i < puzzleStarsArr.size) puzzleStarsArr[i] = it } }
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

    // ---- saved run (Continue feature; classic + zen) ----
    var runJson get() = p.getString("runJson", "")!!; set(v) = p.edit().putString("runJson", v).apply()
    fun hasSavedRun() = runJson.isNotEmpty()
    fun clearSavedRun() { runJson = "" }

    // ---- top-5 runs per mode + daily score history ----
    /** Best N scores for a mode, sorted desc. mode is Mode.name.
     *  Seeds from the legacy single best so existing players see their record. */
    fun topRuns(mode: String): IntArray {
        val s = p.getString("top_$mode", "")!!
        if (s.isEmpty()) {
            val legacy = when (mode) {
                "CLASSIC" -> bestClassic
                "ZEN" -> bestZen
                "RUSH" -> bestRush
                else -> 0
            }
            return if (legacy > 0) intArrayOf(legacy) else intArrayOf()
        }
        return s.split(',').mapNotNull { it.toIntOrNull() }.take(5).toIntArray()
    }

    /** Records a finished run: updates the mode's top-5 and today's best.
     *  [curve] = the run's score-after-each-placement list — saved alongside the
     *  top-5 entry so future runs can pace against it (Ghost Rivals). */
    fun recordRun(mode: String, score: Int, curve: List<Int>? = null) {
        if (score <= 0) return
        val list = topRuns(mode).toMutableList()
        list.add(score)
        list.sortDescending()
        val kept = list.take(5)
        // index of this run inside the kept list — first position it lands on
        val insertAt = kept.indexOf(score)
        val curves = topCurves(mode).toMutableList()
        if (curve != null && insertAt >= 0) {
            while (curves.size < kept.size) curves.add("")
            curves.add(insertAt, curve.joinToString("."))
            while (curves.size > kept.size) curves.removeAt(curves.lastIndex)
            p.edit().putString("topc_$mode", curves.joinToString(";")).apply()
        }
        p.edit().putString("top_$mode", kept.joinToString(",")).apply()
        recordDayScore(score)
    }

    /** Raw curve strings aligned with topRuns (empty string = no curve). */
    private fun topCurves(mode: String): List<String> {
        val s = p.getString("topc_$mode", "")!!
        if (s.isEmpty()) return emptyList()
        return s.split(';')
    }

    /** Score curve of the k-th best run for [mode] (0 = best), or null. */
    fun topCurve(mode: String, rank: Int): IntArray? {
        val c = topCurves(mode).getOrNull(rank) ?: return null
        if (c.isEmpty()) return null
        return c.split('.').mapNotNull { it.toIntOrNull() }.toIntArray().takeIf { it.isNotEmpty() }
    }

    private fun todayKey(): String {
        val cal = java.util.Calendar.getInstance()
        return "%04d%02d%02d".format(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
    }

    /** Daily best scores, newest 30 kept. Storage: "yyyyMMdd=score,..." */
    fun recordDayScore(score: Int) {
        val key = todayKey()
        val map = LinkedHashMap<String, Int>()
        p.getString("dayScores", "")!!.split(',').forEach { e ->
            val kv = e.split('=')
            if (kv.size == 2) kv[1].toIntOrNull()?.let { map[kv[0]] = it }
        }
        map[key] = maxOf(map[key] ?: 0, score)
        while (map.size > 30) map.remove(map.keys.first())
        p.edit().putString("dayScores", map.entries.joinToString(",") { "${it.key}=${it.value}" }).apply()
    }

    /** Scores of the last 7 calendar days, oldest→newest (0 for days not played). */
    fun last7DayScores(): IntArray {
        val map = HashMap<String, Int>()
        p.getString("dayScores", "")!!.split(',').forEach { e ->
            val kv = e.split('=')
            if (kv.size == 2) kv[1].toIntOrNull()?.let { map[kv[0]] = it }
        }
        val out = IntArray(7)
        val cal = java.util.Calendar.getInstance()
        for (i in 6 downTo 0) {
            val k = "%04d%02d%02d".format(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
            out[6 - i] = map[k] ?: 0
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
        }
        return out
    }

    // ---- campaign map ----
    /** Highest campaign node cleared (index of the next playable node). */
    var campaignCleared get() = p.getInt("campaign", 0); set(v) = p.edit().putInt("campaign", v).apply()

    /** Bitmask of finished mosaic pictures. */
    var mosaicDone get() = p.getInt("mosaicDone", 0); set(v) = p.edit().putInt("mosaicDone", v).apply()
    fun markMosaicDone(i: Int) { mosaicDone = mosaicDone or (1 shl i) }
    fun mosaicIsDone(i: Int) = (mosaicDone shr i) and 1 == 1

    // ---- weekly puzzle ----
    var weeklyPuzzleKey get() = p.getInt("weeklyPuzzle", 0); set(v) = p.edit().putInt("weeklyPuzzle", v).apply()
    fun weeklyPuzzleDone() = weeklyPuzzleKey == weekSeed()

    // ---- daily reminder notification ----
    var reminderOn get() = p.getBoolean("reminder", false); set(v) = p.edit().putBoolean("reminder", v).apply()

    // ---- theme-of-the-week sale ----
    fun weekSeed(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.YEAR) * 100 + cal.get(java.util.Calendar.WEEK_OF_YEAR)
    }
}

enum class PowerKind { UNDO, ROTATE, BOMB, SHUFFLE, HINT }

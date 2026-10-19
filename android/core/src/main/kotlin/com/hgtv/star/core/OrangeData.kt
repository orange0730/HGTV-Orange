// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.core

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

// 橘子版新增的三份本機資料：使用者檔案、每部劇的個人設定、觀看統計。
// 這裡只有資料形狀、編碼與計算（純 Kotlin，可在 JVM 上測試）；存到哪裡由 app 端決定。
// 三份資料都只存在這台電視上，不上傳。

/** 一位使用者（家裡每個人一份收藏、紀錄與設定）。 */
data class Profile(val id: String, val name: String)

object ProfileList {
    const val MAX = 6
    const val MAX_NAME = 12
    /** 預設使用者：升級前的資料就是它的，id 固定，不能刪。 */
    val DEFAULT = Profile("default", "我")
    private val ID = Regex("default|p[0-9a-z]{1,16}")

    fun validName(name: String) = name.trim().let { it.isNotEmpty() && it.length <= MAX_NAME && it.none(Char::isISOControl) }

    /** 讀清單：壞掉的項目跳過，預設使用者一定在第一個。 */
    fun decode(text: String?): List<Profile> {
        val parsed = runCatching { JSONArray(text ?: "[]") }.getOrNull() ?: JSONArray()
        val list = (0 until parsed.length()).mapNotNull { parsed.optJSONObject(it) }
            .mapNotNull { o ->
                val id = o.optString("id"); val name = if (o.isNull("name")) "" else o.optString("name").trim()
                if (ID.matches(id) && validName(name)) Profile(id, name) else null
            }
            .distinctBy { it.id }
        val default = list.firstOrNull { it.id == DEFAULT.id } ?: DEFAULT
        return (listOf(default) + list.filter { it.id != DEFAULT.id }).take(MAX)
    }

    fun encode(list: List<Profile>): String =
        JSONArray().also { array -> list.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name)) } }.toString()

    /** 新使用者的 id：不和現有的重複。 */
    fun newId(existing: List<Profile>, seed: Long): String {
        var n = seed and 0x7fffffffffffL
        while (true) {
            val id = "p" + n.toString(36)
            if (existing.none { it.id == id }) return id
            n++
        }
    }
}

/**
 * 一部劇的個人設定：播放中改的清晰度、倍速只記在這部劇上；片頭、片尾要跳過的長度。
 * `null` / 0 表示「跟著全域設定」。
 */
data class SeriesSetting(
    val quality: Int? = null,
    val speed: Float? = null,
    val introMs: Long = 0,
    val outroMs: Long = 0
) {
    val isEmpty get() = quality == null && speed == null && introMs == 0L && outroMs == 0L
}

object SeriesSettings {
    const val MAX_SERIES = 500
    /** 片頭、片尾最多記 10 分鐘：短劇一集通常一兩分鐘，超過這個長度多半是按錯。 */
    const val MAX_SKIP_MS = 10 * 60 * 1000L

    fun clampSkip(ms: Long) = ms.coerceIn(0, MAX_SKIP_MS)

    fun decode(text: String?): LinkedHashMap<String, SeriesSetting> {
        val result = LinkedHashMap<String, SeriesSetting>()
        val root = runCatching { JSONObject(text ?: "{}") }.getOrNull() ?: return result
        for (id in root.keys()) {
            val o = root.optJSONObject(id) ?: continue
            val quality = o.optInt("q", 0).takeIf { it in PlaybackQuality.options }
            val speed = o.optDouble("s", 0.0).toFloat().takeIf { it in PlaybackSpeed.options }
            val setting = SeriesSetting(quality, speed, clampSkip(o.optLong("in", 0)), clampSkip(o.optLong("out", 0)))
            if (id.isNotBlank() && !setting.isEmpty) result[id] = setting
        }
        return result
    }

    /** 編碼；超過上限時丟掉最舊的（Map 的順序就是最後修改的順序）。 */
    fun encode(map: Map<String, SeriesSetting>): String {
        val root = JSONObject()
        map.entries.toList().takeLast(MAX_SERIES).forEach { (id, s) ->
            if (s.isEmpty) return@forEach
            root.put(id, JSONObject().apply {
                s.quality?.let { put("q", it) }
                s.speed?.let { put("s", it.toDouble()) }
                if (s.introMs > 0) put("in", s.introMs)
                if (s.outroMs > 0) put("out", s.outroMs)
            })
        }
        return root.toString()
    }
}

/** 觀看統計：每天看了多久、看完幾集；每部劇累計多久。 */
class WatchStats(
    private val days: MutableMap<String, Day> = sortedMapOf(),
    private val series: MutableMap<String, SeriesTotal> = linkedMapOf()
) {
    data class Day(val seconds: Long, val episodes: Int)
    data class SeriesTotal(val title: String, val seconds: Long, val episodes: Int, val lastAt: Long)
    data class Summary(
        val todaySeconds: Long,
        val weekSeconds: Long,
        val monthSeconds: Long,
        val totalSeconds: Long,
        val totalEpisodes: Int,
        /** 連續幾天有看（含今天；今天還沒看就從昨天往回算）。 */
        val streakDays: Int,
        val top: List<SeriesTotal>
    )

    companion object {
        const val KEEP_DAYS = 400L
        const val KEEP_SERIES = 300

        fun decode(text: String?): WatchStats {
            val root = runCatching { JSONObject(text ?: "{}") }.getOrNull() ?: return WatchStats()
            val days = sortedMapOf<String, Day>()
            root.optJSONObject("days")?.let { o ->
                for (key in o.keys()) {
                    val d = o.optJSONObject(key) ?: continue
                    if (runCatching { LocalDate.parse(key) }.isSuccess)
                        days[key] = Day(d.optLong("t").coerceAtLeast(0), d.optInt("e").coerceAtLeast(0))
                }
            }
            val series = linkedMapOf<String, SeriesTotal>()
            root.optJSONObject("series")?.let { o ->
                for (key in o.keys()) {
                    val s = o.optJSONObject(key) ?: continue
                    series[key] = SeriesTotal(if (s.isNull("n")) "" else s.optString("n"), s.optLong("t").coerceAtLeast(0), s.optInt("e").coerceAtLeast(0), s.optLong("at"))
                }
            }
            return WatchStats(days, series)
        }
    }

    fun encode(today: LocalDate): String {
        prune(today)
        val root = JSONObject()
        root.put("days", JSONObject().also { o -> days.forEach { (k, d) -> o.put(k, JSONObject().put("t", d.seconds).put("e", d.episodes)) } })
        root.put("series", JSONObject().also { o -> series.forEach { (k, s) -> o.put(k, JSONObject().put("n", s.title).put("t", s.seconds).put("e", s.episodes).put("at", s.lastAt)) } })
        return root.toString()
    }

    fun addSeconds(today: LocalDate, seriesId: String, title: String, seconds: Long, now: Long) {
        if (seconds <= 0) return
        val key = today.toString()
        val day = days[key] ?: Day(0, 0)
        days[key] = day.copy(seconds = day.seconds + seconds)
        val total = series.remove(seriesId) ?: SeriesTotal(title, 0, 0, now)
        series[seriesId] = total.copy(title = title.ifBlank { total.title }, seconds = total.seconds + seconds, lastAt = now)
    }

    fun episodeFinished(today: LocalDate, seriesId: String, title: String, now: Long) {
        val key = today.toString()
        val day = days[key] ?: Day(0, 0)
        days[key] = day.copy(episodes = day.episodes + 1)
        val total = series.remove(seriesId) ?: SeriesTotal(title, 0, 0, now)
        series[seriesId] = total.copy(title = title.ifBlank { total.title }, episodes = total.episodes + 1, lastAt = now)
    }

    fun summary(today: LocalDate): Summary {
        fun since(daysBack: Long) = days.filterKeys { key -> runCatching { !LocalDate.parse(key).isBefore(today.minusDays(daysBack - 1)) && !LocalDate.parse(key).isAfter(today) }.getOrDefault(false) }
            .values.sumOf { it.seconds }
        var streak = 0
        var cursor = if ((days[today.toString()]?.seconds ?: 0) > 0) today else today.minusDays(1)
        while ((days[cursor.toString()]?.seconds ?: 0) > 0) { streak++; cursor = cursor.minusDays(1) }
        return Summary(
            todaySeconds = days[today.toString()]?.seconds ?: 0,
            weekSeconds = since(7),
            monthSeconds = since(30),
            totalSeconds = days.values.sumOf { it.seconds },
            totalEpisodes = days.values.sumOf { it.episodes },
            streakDays = streak,
            top = series.values.sortedByDescending { it.seconds }.take(5)
        )
    }

    private fun prune(today: LocalDate) {
        val oldest = today.minusDays(KEEP_DAYS)
        days.keys.toList().forEach { key -> if (runCatching { LocalDate.parse(key).isBefore(oldest) }.getOrDefault(true)) days.remove(key) }
        while (series.size > KEEP_SERIES) {
            val oldestSeries = series.minByOrNull { it.value.lastAt }?.key ?: break
            series.remove(oldestSeries)
        }
    }
}

/** 統計畫面上的時間長度：「2 小時 05 分」「12 分鐘」「不到 1 分鐘」。 */
fun watchDuration(seconds: Long): String = when {
    seconds < 60 -> "不到 1 分钟"
    seconds < 3600 -> "${seconds / 60} 分钟"
    else -> "${seconds / 3600} 小时 %02d 分".format((seconds % 3600) / 60)
}

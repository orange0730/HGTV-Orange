// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.data

import android.content.Context
import com.hgtv.star.core.Profile
import com.hgtv.star.core.ProfileList
import com.hgtv.star.core.SeriesSetting
import com.hgtv.star.core.SeriesSettings
import com.hgtv.star.core.WatchStats
import java.time.LocalDate

// 橘子版的三份本機資料怎麼存（資料形狀與計算在 core 的 OrangeData.kt）。
//
// 檔案配置：
//   orange-device-v1            整台裝置共用：使用者清單、目前使用者、介面語言
//   native-library-v1[-<id>]    每位使用者一份：收藏、觀看紀錄、播放偏好（預設使用者沿用舊檔名，升級不丟資料）
//   orange-series-v1-<id>       每位使用者一份：每部劇的清晰度 / 倍速 / 片頭片尾
//   orange-stats-v1-<id>        每位使用者一份：觀看統計
// 平台的裝置身分與帳號參數在 native-session-v1，整台共用，換使用者不會重新註冊裝置。

/** 使用者檔案：家裡每個人一份收藏與紀錄。 */
internal class Profiles(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("orange-device-v1", Context.MODE_PRIVATE)

    fun list(): List<Profile> = ProfileList.decode(prefs.getString("profiles", null))

    fun current(): Profile {
        val all = list()
        val id = prefs.getString("profile", ProfileList.DEFAULT.id)
        return all.firstOrNull { it.id == id } ?: all.first()
    }

    /** 換使用者；用 commit（同步寫入）：呼叫端接著重建畫面，新畫面要讀到新值。 */
    fun switchTo(id: String) {
        prefs.edit().putString("profile", id).commit()
    }

    /** 新增使用者；名字不合格或已滿 [ProfileList.MAX] 位時回傳 null。 */
    fun add(name: String): Profile? {
        val all = list()
        if (all.size >= ProfileList.MAX || !ProfileList.validName(name)) return null
        val profile = Profile(ProfileList.newId(all, System.currentTimeMillis()), name.trim())
        save(all + profile)
        return profile
    }

    fun rename(id: String, name: String): Boolean {
        if (!ProfileList.validName(name)) return false
        save(list().map { if (it.id == id) it.copy(name = name.trim()) else it })
        return true
    }

    /** 刪除使用者連同他的收藏、紀錄、設定與統計；預設使用者不能刪。 */
    fun remove(id: String) {
        if (id == ProfileList.DEFAULT.id) return
        save(list().filter { it.id != id })
        if (prefs.getString("profile", null) == id) switchTo(ProfileList.DEFAULT.id)
        listOf(libraryFile(id), seriesFile(id), statsFile(id)).forEach { app.deleteSharedPreferences(it) }
    }

    private fun save(list: List<Profile>) {
        prefs.edit().putString("profiles", ProfileList.encode(list)).commit()
    }

    companion object {
        fun libraryFile(id: String) = if (id == ProfileList.DEFAULT.id) "native-library-v1" else "native-library-v1-$id"
        fun seriesFile(id: String) = "orange-series-v1-$id"
        fun statsFile(id: String) = "orange-stats-v1-$id"
    }
}

/** 每部劇的個人設定（清晰度、倍速、片頭片尾）與「自動跳過片頭片尾」開關。 */
internal class SeriesStore(context: Context, profileId: String) {
    // getSharedPreferences 會在背景開始讀檔，第一次真正取值時才可能等它讀完。
    private val prefs = context.applicationContext.getSharedPreferences(Profiles.seriesFile(profileId), Context.MODE_PRIVATE)
    private val map by lazy { SeriesSettings.decode(prefs.getString("series", null)) }

    fun get(seriesId: String): SeriesSetting = map[seriesId] ?: SeriesSetting()

    fun update(seriesId: String, change: (SeriesSetting) -> SeriesSetting) {
        val next = change(get(seriesId))
        // 先拿掉再放回：Map 的順序就是「最後修改」的順序，超過上限時丟最舊的。
        map.remove(seriesId)
        if (!next.isEmpty) map[seriesId] = next
        prefs.edit().putString("series", SeriesSettings.encode(map)).apply()
    }

    var autoSkip: Boolean
        get() = prefs.getBoolean("auto-skip", true)
        set(value) { prefs.edit().putBoolean("auto-skip", value).apply() }
}

/** 觀看統計：播放中每秒累計一次，每 30 秒或離開畫面時寫入。 */
internal class StatsStore(context: Context, profileId: String) {
    private val prefs = context.applicationContext.getSharedPreferences(Profiles.statsFile(profileId), Context.MODE_PRIVATE)
    private var loaded: WatchStats? = null
    private fun stats() = loaded ?: WatchStats.decode(prefs.getString("stats", null)).also { loaded = it }
    private var pending = 0

    fun tick(seriesId: String, title: String) {
        stats().addSeconds(LocalDate.now(), seriesId, title, 1, System.currentTimeMillis())
        if (++pending >= 30) flush()
    }

    fun finished(seriesId: String, title: String) {
        stats().episodeFinished(LocalDate.now(), seriesId, title, System.currentTimeMillis())
        pending++
        flush()
    }

    fun flush() {
        if (pending == 0) return
        pending = 0
        prefs.edit().putString("stats", stats().encode(LocalDate.now())).apply()
    }

    fun summary(): WatchStats.Summary = stats().summary(LocalDate.now())

    fun clear() {
        pending = 0
        loaded = WatchStats()
        prefs.edit().clear().apply()
    }
}

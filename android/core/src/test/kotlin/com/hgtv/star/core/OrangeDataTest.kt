// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OrangeDataTest {
    @Test fun profilesAlwaysStartWithDefaultAndDropBadEntries() {
        assertEquals(listOf(ProfileList.DEFAULT), ProfileList.decode(null))
        assertEquals(listOf(ProfileList.DEFAULT), ProfileList.decode("not json"))
        val list = ProfileList.decode("""[{"id":"pabc","name":"爸爸"},{"id":"default","name":"東橙"},{"id":"../x","name":"壞"},{"id":"pabc","name":"重複"},{"id":"pq","name":""}]""")
        assertEquals(listOf(Profile("default", "東橙"), Profile("pabc", "爸爸")), list)
        assertEquals(list, ProfileList.decode(ProfileList.encode(list)))
    }

    @Test fun profileListIsCappedAndIdsAreUnique() {
        val many = (1..10).map { Profile("p$it", "人$it") }
        assertEquals(ProfileList.MAX, ProfileList.decode(ProfileList.encode(many)).size)
        val existing = listOf(ProfileList.DEFAULT, Profile("p0", "a"))
        val id = ProfileList.newId(existing, 0)
        assertTrue(existing.none { it.id == id })
        assertTrue(ProfileList.validName("小孩"))
        assertTrue(!ProfileList.validName("   "))
        assertTrue(!ProfileList.validName("x".repeat(ProfileList.MAX_NAME + 1)))
    }

    @Test fun seriesSettingsRoundTripAndRejectUnknownValues() {
        val map = linkedMapOf(
            "a" to SeriesSetting(quality = 720, speed = 1.5f, introMs = 45_000, outroMs = 30_000),
            "b" to SeriesSetting(speed = 2f),
            "empty" to SeriesSetting()
        )
        val decoded = SeriesSettings.decode(SeriesSettings.encode(map))
        assertEquals(setOf("a", "b"), decoded.keys)
        assertEquals(map["a"], decoded["a"])
        val weird = SeriesSettings.decode("""{"x":{"q":999,"s":3.3,"in":99999999}}""")
        assertEquals(SeriesSetting(introMs = SeriesSettings.MAX_SKIP_MS), weird["x"])
        assertTrue(SeriesSettings.decode("broken").isEmpty())
    }

    @Test fun seriesSettingsKeepTheMostRecentWhenOverTheLimit() {
        val map = linkedMapOf<String, SeriesSetting>()
        (1..SeriesSettings.MAX_SERIES + 5).forEach { map["s$it"] = SeriesSetting(speed = 2f) }
        val decoded = SeriesSettings.decode(SeriesSettings.encode(map))
        assertEquals(SeriesSettings.MAX_SERIES, decoded.size)
        assertTrue("s1" !in decoded)
        assertTrue("s${SeriesSettings.MAX_SERIES + 5}" in decoded)
    }

    @Test fun watchStatsSummariseDaysWeeksAndStreak() {
        val today = LocalDate.of(2026, 10, 20)
        val stats = WatchStats()
        stats.addSeconds(today, "s1", "霸道总裁", 600, 3)
        stats.addSeconds(today.minusDays(1), "s1", "霸道总裁", 300, 2)
        stats.addSeconds(today.minusDays(2), "s2", "重生", 1200, 1)
        stats.addSeconds(today.minusDays(10), "s2", "重生", 60, 0)
        stats.episodeFinished(today, "s1", "霸道总裁", 4)
        val summary = WatchStats.decode(stats.encode(today)).summary(today)
        assertEquals(600, summary.todaySeconds)
        assertEquals(2100, summary.weekSeconds)
        assertEquals(2160, summary.monthSeconds)
        assertEquals(2160, summary.totalSeconds)
        assertEquals(1, summary.totalEpisodes)
        assertEquals(3, summary.streakDays)
        assertEquals(listOf("重生", "霸道总裁"), summary.top.map { it.title })
    }

    @Test fun streakCountsFromYesterdayWhenTodayIsEmpty() {
        val today = LocalDate.of(2026, 10, 20)
        val stats = WatchStats()
        stats.addSeconds(today.minusDays(1), "s", "t", 10, 0)
        stats.addSeconds(today.minusDays(2), "s", "t", 10, 0)
        assertEquals(2, stats.summary(today).streakDays)
        assertEquals(0, WatchStats().summary(today).streakDays)
    }

    @Test fun oldDaysArePruned() {
        val today = LocalDate.of(2026, 10, 20)
        val stats = WatchStats()
        stats.addSeconds(today.minusDays(WatchStats.KEEP_DAYS + 5), "s", "t", 100, 0)
        stats.addSeconds(today, "s", "t", 5, 1)
        val decoded = WatchStats.decode(stats.encode(today))
        assertEquals(5, decoded.summary(today).totalSeconds)
    }

    @Test fun durationsReadNaturally() {
        assertEquals("不到 1 分钟", watchDuration(30))
        assertEquals("12 分钟", watchDuration(12 * 60 + 5))
        assertEquals("2 小时 05 分", watchDuration(2 * 3600 + 5 * 60))
    }
}
